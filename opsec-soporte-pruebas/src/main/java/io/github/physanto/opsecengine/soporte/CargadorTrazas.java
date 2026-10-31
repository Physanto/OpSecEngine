package io.github.physanto.opsecengine.soporte;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.physanto.opsecengine.dominio.traza.ContextoEvento;
import io.github.physanto.opsecengine.dominio.traza.Evento;
import io.github.physanto.opsecengine.dominio.traza.TipoEvento;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Lee archivos de traza JSON Lines y los convierte en objetos {@link Traza} del dominio.
 *
 * <p>El formato de cada línea es el de {@code contratos/esquemas/traza-evento.schema.json}: un
 * evento por línea, en UTF-8. Las reglas que aplica el cargador son:
 *
 * <ul>
 *   <li>Las líneas en blanco se ignoran.
 *   <li>Las secuencias deben ser estrictamente crecientes en todo el archivo.
 *   <li>Cada evento debe tener {@code contexto.casoId}: una traza pertenece a un caso.
 *   <li>Un archivo puede tener eventos de varios casos; se devuelve una traza por caso, en el orden
 *       en que aparece cada caso por primera vez.
 * </ul>
 *
 * <p>Ejemplo:
 *
 * <pre>{@code
 * CargadorTrazas cargador = new CargadorTrazas();
 * List<Traza> desdeArchivo = cargador.leer(Path.of("resultados", "traza.jsonl"));
 * List<Traza> c01c02 = cargador.cargarEjemplo(TrazaEjemplo.C01_C02_MENSAJES);
 * Traza n3 = cargador.cargarEjemploUnico(TrazaEjemplo.N3_LOCAL_SIN_LOGIN);
 * }</pre>
 *
 * <p>No tiene estado mutable y es seguro entre hilos.
 */
public final class CargadorTrazas {

    private static final TypeReference<Map<String, Object>> MAPA = new TypeReference<>() {};

    private final JsonMapper mapper =
            JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();

    /** Crea un cargador. */
    public CargadorTrazas() {}

    /**
     * Lee un archivo de traza.
     *
     * @param archivo ruta del archivo JSONL; no puede ser null
     * @return una traza por caso, en orden de aparición; vacía si el archivo no tiene eventos
     * @throws IOException si no se puede leer el archivo
     * @throws IllegalArgumentException si una línea no cumple el formato, con el archivo y el
     *     número de línea en el mensaje
     */
    public List<Traza> leer(Path archivo) throws IOException {
        Objects.requireNonNull(archivo, "El archivo no puede ser null");
        try (Reader lector = Files.newBufferedReader(archivo, StandardCharsets.UTF_8)) {
            return leer(lector, archivo.toString());
        }
    }

    /**
     * Lee una traza desde un lector de texto.
     *
     * @param lector lector del contenido JSONL; no se cierra; no puede ser null
     * @param origen nombre del origen para los mensajes de error, por ejemplo la ruta del archivo
     * @return una traza por caso, en orden de aparición; vacía si no hay eventos
     * @throws IOException si no se puede leer
     * @throws IllegalArgumentException si una línea no cumple el formato
     */
    public List<Traza> leer(Reader lector, String origen) throws IOException {
        Objects.requireNonNull(lector, "El lector no puede ser null");
        BufferedReader lineas =
                lector instanceof BufferedReader buffer ? buffer : new BufferedReader(lector);
        Map<String, List<Evento>> porCaso = new LinkedHashMap<>();
        long anterior = -1;
        int numero = 0;
        for (String linea = lineas.readLine(); linea != null; linea = lineas.readLine()) {
            numero++;
            if (linea.isBlank()) {
                continue;
            }
            Evento evento = convertir(linea, origen + ":" + numero);
            if (evento.secuencia() <= anterior) {
                throw new IllegalArgumentException(
                        origen
                                + ":"
                                + numero
                                + ": la secuencia "
                                + evento.secuencia()
                                + " no es mayor que la anterior ("
                                + anterior
                                + ")");
            }
            anterior = evento.secuencia();
            porCaso.computeIfAbsent(evento.contexto().casoId(), id -> new ArrayList<>())
                    .add(evento);
        }
        List<Traza> trazas = new ArrayList<>();
        porCaso.forEach((casoId, eventos) -> trazas.add(new Traza(casoId, eventos)));
        return List.copyOf(trazas);
    }

    /**
     * Carga una traza de ejemplo de los recursos del módulo.
     *
     * @param ejemplo traza de ejemplo; no puede ser null
     * @return una traza por caso del ejemplo
     * @throws IOException si no se puede leer el recurso
     */
    public List<Traza> cargarEjemplo(TrazaEjemplo ejemplo) throws IOException {
        Objects.requireNonNull(ejemplo, "El ejemplo no puede ser null");
        try (InputStream flujo = ejemplo.abrir();
                Reader lector = new InputStreamReader(flujo, StandardCharsets.UTF_8)) {
            return leer(lector, ejemplo.archivo());
        }
    }

    /**
     * Carga una traza de ejemplo que tiene un solo caso.
     *
     * @param ejemplo traza de ejemplo de un solo caso; no puede ser null
     * @return la traza del caso
     * @throws IOException si no se puede leer el recurso
     * @throws IllegalArgumentException si el ejemplo tiene más de un caso, como {@link
     *     TrazaEjemplo#C01_C02_MENSAJES}
     */
    public Traza cargarEjemploUnico(TrazaEjemplo ejemplo) throws IOException {
        List<Traza> trazas = cargarEjemplo(ejemplo);
        if (trazas.size() != 1) {
            throw new IllegalArgumentException(
                    "El ejemplo " + ejemplo + " tiene " + trazas.size() + " casos, no uno");
        }
        return trazas.get(0);
    }

    private Evento convertir(String linea, String ubicacion) {
        JsonNode nodo;
        try {
            nodo = mapper.readTree(linea);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    ubicacion + ": la línea no es JSON válido: " + e.getOriginalMessage(), e);
        }
        try {
            JsonNode contexto = exigir(nodo, "contexto");
            String casoId = texto(contexto, "casoId");
            if (casoId == null) {
                throw new IllegalArgumentException(
                        "el evento no tiene contexto.casoId; una traza pertenece a un caso");
            }
            return new Evento(
                    exigirEntero(nodo, "secuencia"),
                    Instant.parse(exigir(nodo, "instante").asText()),
                    tipo(exigir(nodo, "tipo").asText()),
                    new ContextoEvento(
                            casoId,
                            (int) exigirEntero(contexto, "numeroPaso"),
                            texto(contexto, "rol"),
                            exigirBooleano(contexto, "sesionActiva")),
                    nodo.has("datos") ? mapper.convertValue(nodo.get("datos"), MAPA) : Map.of());
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new IllegalArgumentException(ubicacion + ": " + e.getMessage(), e);
        }
    }

    private static JsonNode exigir(JsonNode nodo, String campo) {
        JsonNode valor = nodo.get(campo);
        if (valor == null || valor.isNull()) {
            throw new IllegalArgumentException("falta el campo " + campo);
        }
        return valor;
    }

    private static long exigirEntero(JsonNode nodo, String campo) {
        JsonNode valor = exigir(nodo, campo);
        if (!valor.canConvertToLong() || !valor.isIntegralNumber()) {
            throw new IllegalArgumentException("el campo " + campo + " debe ser un entero");
        }
        return valor.asLong();
    }

    private static boolean exigirBooleano(JsonNode nodo, String campo) {
        JsonNode valor = exigir(nodo, campo);
        if (!valor.isBoolean()) {
            throw new IllegalArgumentException("el campo " + campo + " debe ser true o false");
        }
        return valor.asBoolean();
    }

    private static TipoEvento tipo(String nombre) {
        for (TipoEvento tipo : TipoEvento.values()) {
            if (tipo.name().equals(nombre)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("tipo de evento desconocido: " + nombre);
    }

    private static String texto(JsonNode nodo, String campo) {
        JsonNode valor = nodo.get(campo);
        return valor == null || valor.isNull() ? null : valor.asText();
    }
}
