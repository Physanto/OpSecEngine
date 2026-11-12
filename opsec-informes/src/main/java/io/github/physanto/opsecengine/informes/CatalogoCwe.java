package io.github.physanto.opsecengine.informes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Explicaciones en español de las debilidades CWE, tomadas del catálogo oficial {@code
 * contratos/catalogos/cwe.yaml}.
 *
 * <p>El catálogo no se copia a mano en este módulo: el build lo toma del contrato y lo deja como
 * recurso en {@value #RECURSO}, así que siempre coincide con la versión de {@code main}. Los
 * reporteros lo usan para explicar cada hallazgo: qué es la debilidad, por qué importa y cómo
 * corregirla.
 *
 * <p>Una búsqueda de un CWE que no está en el catálogo devuelve vacío; los reporteros muestran
 * entonces solo el identificador.
 *
 * <p>Es inmutable y seguro entre hilos.
 */
public final class CatalogoCwe {

    /** Ruta del recurso con el catálogo, relativa a esta clase. */
    static final String RECURSO = "catalogos/cwe.yaml";

    private final Map<String, DescripcionCwe> entradas;

    /**
     * Descripción de una debilidad CWE.
     *
     * @param id identificador con el formato {@code CWE-<número>}
     * @param nombre nombre corto en español
     * @param explicacion qué es y por qué importa, en lenguaje sencillo
     * @param recomendacion cómo corregirla
     */
    public record DescripcionCwe(
            String id, String nombre, String explicacion, String recomendacion) {

        /** Crea la descripción y exige todos sus campos. */
        public DescripcionCwe {
            Objects.requireNonNull(id, "El id del CWE no puede ser null");
            Objects.requireNonNull(nombre, "El nombre del " + id + " no puede ser null");
            Objects.requireNonNull(explicacion, "La explicación del " + id + " no puede ser null");
            Objects.requireNonNull(
                    recomendacion, "La recomendación del " + id + " no puede ser null");
        }

        /**
         * Devuelve la página de la debilidad en el sitio de MITRE.
         *
         * @return por ejemplo {@code https://cwe.mitre.org/data/definitions/306.html}
         */
        public String enlace() {
            return "https://cwe.mitre.org/data/definitions/" + id.substring(4) + ".html";
        }
    }

    private CatalogoCwe(Map<String, DescripcionCwe> entradas) {
        this.entradas = Collections.unmodifiableMap(entradas);
    }

    /**
     * Devuelve el catálogo oficial que trae el módulo.
     *
     * @return el catálogo leído una sola vez
     * @throws IllegalStateException si falta el recurso o no tiene el formato del contrato; indica
     *     un problema del build, no de los datos de una ejecución
     */
    public static CatalogoCwe oficial() {
        return Oficial.CATALOGO;
    }

    /**
     * Busca la descripción de un CWE.
     *
     * @param cwe identificador como {@code "CWE-306"}, sin importar mayúsculas; puede ser null
     * @return la descripción, o vacío si {@code cwe} es null o no está en el catálogo
     */
    public Optional<DescripcionCwe> buscar(String cwe) {
        return cwe == null
                ? Optional.empty()
                : Optional.ofNullable(entradas.get(cwe.strip().toUpperCase(Locale.ROOT)));
    }

    /**
     * Devuelve todas las entradas.
     *
     * @return mapa inmutable de id a descripción, en el orden del catálogo
     */
    public Map<String, DescripcionCwe> entradas() {
        return entradas;
    }

    /** Lee el catálogo con el formato de {@code cwe.yaml}: una lista {@code cwe} de entradas. */
    static CatalogoCwe leer(InputStream yaml) throws IOException {
        JsonNode raiz = new YAMLMapper().readTree(yaml);
        JsonNode lista = raiz == null ? null : raiz.get("cwe");
        if (lista == null || !lista.isArray()) {
            throw new IOException("El catálogo de CWE no tiene la lista 'cwe'");
        }
        Map<String, DescripcionCwe> entradas = new LinkedHashMap<>();
        for (JsonNode nodo : lista) {
            DescripcionCwe descripcion =
                    new DescripcionCwe(
                            texto(nodo, "id"),
                            texto(nodo, "nombre"),
                            texto(nodo, "explicacion"),
                            texto(nodo, "recomendacion"));
            entradas.put(descripcion.id(), descripcion);
        }
        return new CatalogoCwe(entradas);
    }

    private static String texto(JsonNode nodo, String campo) throws IOException {
        JsonNode valor = nodo.get(campo);
        if (valor == null || !valor.isTextual() || valor.asText().isBlank()) {
            throw new IOException("Una entrada del catálogo de CWE no tiene '" + campo + "'");
        }
        return valor.asText().strip();
    }

    /** Carga perezosa y única del catálogo oficial. */
    private static final class Oficial {

        private static final CatalogoCwe CATALOGO = cargar();

        private static CatalogoCwe cargar() {
            try (InputStream flujo = CatalogoCwe.class.getResourceAsStream(RECURSO)) {
                if (flujo == null) {
                    throw new IllegalStateException(
                            "Falta el recurso "
                                    + RECURSO
                                    + "; se copia de contratos/catalogos/cwe.yaml al construir");
                }
                return leer(flujo);
            } catch (IOException e) {
                throw new IllegalStateException(
                        "No se pudo leer el catálogo de CWE: " + e.getMessage(),
                        new UncheckedIOException(e));
            }
        }
    }
}
