package io.github.physanto.opsecengine.verificacion;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Acceso a la carpeta {@code contratos/} de la raíz del repositorio para las pruebas.
 *
 * <p>Lee YAML, JSON y JSON Lines en UTF-8, con detección de claves repetidas, y valida documentos
 * contra los esquemas de {@code contratos/esquemas/}. Los {@code $id} de los esquemas se resuelven
 * contra los archivos locales, así que las pruebas no usan la red.
 */
final class Contratos {

    /** Carpeta {@code contratos/}, relativa al directorio del módulo donde corren las pruebas. */
    static final Path RAIZ = Path.of("..", "contratos");

    /** Carpeta de los esquemas JSON. */
    static final Path ESQUEMAS = RAIZ.resolve("esquemas");

    /** Carpeta de los catálogos YAML. */
    static final Path CATALOGOS = RAIZ.resolve("catalogos");

    /** Carpeta de los ejemplos. */
    static final Path EJEMPLOS = RAIZ.resolve("ejemplos");

    /** Prefijo común de los {@code $id} de los esquemas. */
    static final String BASE_ID =
            "https://raw.githubusercontent.com/Physanto/OpSecEngine/main/contratos/esquemas/";

    /** Esquema de {@code proyecto.yaml}. */
    static final String PROYECTO = "proyecto.schema.json";

    /** Esquema de las suites. */
    static final String SUITE = "suite.schema.json";

    /** Esquema de los archivos de payloads. */
    static final String PAYLOAD = "payload.schema.json";

    /** Esquema de los archivos de reglas de oráculo. */
    static final String ORACULO_REGLA = "oraculo-regla.schema.json";

    /** Esquema de una línea de traza. */
    static final String TRAZA_EVENTO = "traza-evento.schema.json";

    private static final YAMLMapper YAML =
            YAMLMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();

    private static final JsonMapper JSON =
            JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();

    private static final SchemaRegistry REGISTRO =
            SchemaRegistry.withDefaultDialect(
                    SpecificationVersion.DRAFT_2020_12,
                    constructor ->
                            constructor
                                    .schemaRegistryConfig(
                                            SchemaRegistryConfig.builder()
                                                    .formatAssertionsEnabled(true)
                                                    .locale(Locale.ROOT)
                                                    .build())
                                    .schemas(Contratos::textoDeEsquemaLocal));

    private Contratos() {}

    /**
     * Lee un archivo YAML.
     *
     * @param archivo ruta del archivo
     * @return el árbol leído
     */
    static JsonNode leerYaml(Path archivo) {
        try (Reader lector = Files.newBufferedReader(archivo, StandardCharsets.UTF_8)) {
            return YAML.readTree(lector);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + archivo, e);
        }
    }

    /**
     * Lee un archivo JSON.
     *
     * @param archivo ruta del archivo
     * @return el árbol leído
     */
    static JsonNode leerJson(Path archivo) {
        try (Reader lector = Files.newBufferedReader(archivo, StandardCharsets.UTF_8)) {
            return JSON.readTree(lector);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + archivo, e);
        }
    }

    /**
     * Lee un archivo JSON Lines; ignora las líneas en blanco.
     *
     * @param archivo ruta del archivo
     * @return un árbol por línea, en orden
     */
    static List<JsonNode> leerJsonLineas(Path archivo) {
        try {
            List<JsonNode> lineas = new ArrayList<>();
            for (String linea : Files.readAllLines(archivo, StandardCharsets.UTF_8)) {
                if (!linea.isBlank()) {
                    lineas.add(JSON.readTree(linea));
                }
            }
            return lineas;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + archivo, e);
        }
    }

    /**
     * Lee un documento YAML escrito en el texto de una prueba.
     *
     * @param texto el documento
     * @return el árbol leído
     */
    static JsonNode yaml(String texto) {
        try {
            return YAML.readTree(texto);
        } catch (IOException e) {
            throw new UncheckedIOException("YAML inválido en la prueba", e);
        }
    }

    /**
     * Devuelve un esquema de {@code contratos/esquemas/}.
     *
     * @param archivo nombre del archivo del esquema, por ejemplo {@link #SUITE}
     * @return el esquema listo para validar
     */
    static Schema esquema(String archivo) {
        return REGISTRO.getSchema(SchemaLocation.of(BASE_ID + archivo));
    }

    /**
     * Devuelve el metaesquema de JSON Schema draft 2020-12.
     *
     * @return el metaesquema, incluido en la librería de validación
     */
    static Schema metaesquema() {
        return REGISTRO.getSchema(
                SchemaLocation.of(SpecificationVersion.DRAFT_2020_12.getDialectId()));
    }

    /**
     * Valida un documento contra un esquema.
     *
     * @param archivoEsquema nombre del archivo del esquema
     * @param documento documento a validar
     * @return los errores, uno por línea; vacío si el documento es válido
     */
    static List<String> errores(String archivoEsquema, JsonNode documento) {
        return describir(esquema(archivoEsquema).validate(documento));
    }

    /**
     * Describe los errores de una validación.
     *
     * @param errores errores devueltos por el validador
     * @return un texto por error, con la ubicación y el mensaje
     */
    static List<String> describir(List<Error> errores) {
        return errores.stream().map(Error::toString).collect(Collectors.toList());
    }

    /**
     * Lista los archivos de una carpeta y sus subcarpetas, ordenados.
     *
     * @param carpeta carpeta raíz
     * @return rutas de todos los archivos
     */
    static List<Path> archivos(Path carpeta) {
        try (Stream<Path> rutas = Files.walk(carpeta)) {
            return rutas.filter(Files::isRegularFile).sorted().collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo recorrer " + carpeta, e);
        }
    }

    /**
     * Convierte el nombre de una constante de enum a camelCase, por ejemplo {@code
     * NOMBRE_ACCESIBLE} a {@code nombreAccesible}.
     *
     * @param constante nombre de la constante
     * @return el nombre en camelCase
     */
    static String camelCase(Enum<?> constante) {
        String[] partes = constante.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder resultado = new StringBuilder(partes[0]);
        for (int i = 1; i < partes.length; i++) {
            resultado.append(Character.toUpperCase(partes[i].charAt(0)));
            resultado.append(partes[i].substring(1));
        }
        return resultado.toString();
    }

    private static String textoDeEsquemaLocal(String id) {
        if (!id.startsWith(BASE_ID)) {
            return null;
        }
        Path archivo = ESQUEMAS.resolve(id.substring(BASE_ID.length()));
        try {
            return Files.readString(archivo, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el esquema " + archivo, e);
        }
    }
}
