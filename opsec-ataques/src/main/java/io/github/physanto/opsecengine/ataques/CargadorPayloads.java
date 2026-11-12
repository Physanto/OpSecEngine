package io.github.physanto.opsecengine.ataques;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.path.PathType;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Lee archivos de payloads en YAML y los valida contra {@code payload.schema.json}.
 *
 * <p>Cada archivo es una lista de payloads (ver {@code contratos/esquemas/payload.schema.json}).
 * Antes de convertirlo, el archivo se valida contra el esquema, que viaja como recurso del módulo,
 * y se comprueba que los ids no se repitan. Si algo falla se lanza {@link IllegalArgumentException}
 * con todos los errores, cada uno con su ruta en formato JSON Pointer y un mensaje en español.
 *
 * <p>Los archivos incluidos en el módulo están en {@link #INCLUIDOS}. Ejemplo:
 *
 * <pre>{@code
 * List<Payload> vacios = new CargadorPayloads().cargarIncluido("vacios.yaml");
 * List<Payload> propios = new CargadorPayloads().leer(Path.of("ataques", "propios.yaml"));
 * }</pre>
 *
 * <p>No tiene estado mutable y es seguro entre hilos.
 */
public final class CargadorPayloads {

    /** Archivos de payloads incluidos en el módulo. */
    public static final List<String> INCLUIDOS =
            List.of(
                    "vacios.yaml",
                    "longitud.yaml",
                    "unicode.yaml",
                    "numericos.yaml",
                    "inyeccion-sql.yaml",
                    "firestore.yaml",
                    "html-swing.yaml",
                    "cadenas-formato.yaml",
                    "rutas-archivo.yaml",
                    "formulas-csv.yaml",
                    "comandos.yaml",
                    "credenciales-debiles.yaml");

    private static final String BASE_ID =
            "https://raw.githubusercontent.com/Physanto/OpSecEngine/main/contratos/esquemas/";
    private static final String ESQUEMA = "payload.schema.json";
    private static final String RECURSOS = "/io/github/physanto/opsecengine/ataques/";
    private static final TypeReference<Map<String, Object>> MAPA = new TypeReference<>() {};

    private final YAMLMapper mapper =
            YAMLMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    private final Schema esquema =
            SchemaRegistry.withDefaultDialect(
                            SpecificationVersion.DRAFT_2020_12,
                            constructor ->
                                    constructor
                                            .schemaRegistryConfig(
                                                    SchemaRegistryConfig.builder()
                                                            .locale(Locale.forLanguageTag("es"))
                                                            .pathType(PathType.JSON_POINTER)
                                                            .build())
                                            .schemas(CargadorPayloads::leerEsquema))
                    .getSchema(SchemaLocation.of(BASE_ID + ESQUEMA));

    /** Crea el cargador. */
    public CargadorPayloads() {}

    /**
     * Lee un archivo de payloads.
     *
     * @param archivo ruta del archivo YAML; no puede ser null
     * @return los payloads, en el orden del archivo
     * @throws IOException si no se puede leer el archivo
     * @throws IllegalArgumentException si el archivo no es YAML válido, no cumple el esquema o
     *     repite ids
     */
    public List<Payload> leer(Path archivo) throws IOException {
        Objects.requireNonNull(archivo, "El archivo no puede ser null");
        try (Reader lector = Files.newBufferedReader(archivo, StandardCharsets.UTF_8)) {
            return leer(lector, archivo.toString());
        }
    }

    /**
     * Lee payloads desde un lector.
     *
     * @param lector texto YAML; no puede ser null y no se cierra
     * @param origen nombre del origen para los mensajes de error, por ejemplo el archivo
     * @return los payloads, en el orden del texto
     * @throws IOException si no se puede leer el texto
     * @throws IllegalArgumentException si el texto no es YAML válido, no cumple el esquema o repite
     *     ids
     */
    public List<Payload> leer(Reader lector, String origen) throws IOException {
        Objects.requireNonNull(lector, "El lector no puede ser null");
        JsonNode raiz;
        try {
            raiz = mapper.readTree(lector);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    origen + ": el YAML no es válido: " + e.getOriginalMessage(), e);
        }
        if (raiz == null || raiz.isMissingNode() || raiz.isNull()) {
            throw new IllegalArgumentException(origen + ": el archivo está vacío");
        }
        Set<String> errores = new LinkedHashSet<>();
        for (Error error : esquema.validate(raiz)) {
            errores.add(ruta(error.getInstanceLocation().toString()) + ": " + error.getMessage());
        }
        if (errores.isEmpty()) {
            Map<String, Integer> posiciones = new HashMap<>();
            for (int i = 0; i < raiz.size(); i++) {
                Integer anterior = posiciones.putIfAbsent(raiz.get(i).get("id").asText(), i);
                if (anterior != null) {
                    errores.add(
                            "/"
                                    + i
                                    + "/id: el id "
                                    + raiz.get(i).get("id").asText()
                                    + " ya se usa en /"
                                    + anterior
                                    + "/id");
                }
            }
        }
        if (!errores.isEmpty()) {
            throw new IllegalArgumentException(
                    origen
                            + " no es un archivo de payloads válido:"
                            + System.lineSeparator()
                            + "  "
                            + String.join(System.lineSeparator() + "  ", errores));
        }
        List<Payload> payloads = new ArrayList<>();
        raiz.forEach(nodo -> payloads.add(convertir(nodo)));
        return List.copyOf(payloads);
    }

    /**
     * Lee uno de los archivos incluidos en el módulo.
     *
     * @param nombre nombre del archivo, uno de {@link #INCLUIDOS}
     * @return los payloads del archivo
     * @throws IOException si el recurso no existe o no se puede leer
     * @throws IllegalArgumentException si el archivo no es válido
     */
    public List<Payload> cargarIncluido(String nombre) throws IOException {
        Objects.requireNonNull(nombre, "El nombre no puede ser null");
        try (InputStream flujo =
                CargadorPayloads.class.getResourceAsStream(RECURSOS + "payloads/" + nombre)) {
            if (flujo == null) {
                throw new FileNotFoundException("No hay un archivo de payloads incluido " + nombre);
            }
            return leer(new InputStreamReader(flujo, StandardCharsets.UTF_8), nombre);
        }
    }

    /**
     * Lee todos los archivos incluidos en el módulo.
     *
     * @return los payloads de todos los archivos de {@link #INCLUIDOS}, en ese orden
     * @throws IOException si un recurso no se puede leer
     * @throws IllegalArgumentException si un archivo no es válido o dos archivos repiten un id
     */
    public List<Payload> cargarIncluidos() throws IOException {
        List<Payload> todos = new ArrayList<>();
        Map<String, String> archivoPorId = new HashMap<>();
        for (String nombre : INCLUIDOS) {
            for (Payload payload : cargarIncluido(nombre)) {
                String anterior = archivoPorId.putIfAbsent(payload.id(), nombre);
                if (anterior != null) {
                    throw new IllegalArgumentException(
                            "El payload " + payload.id() + " está en " + anterior + " y " + nombre);
                }
                todos.add(payload);
            }
        }
        return List.copyOf(todos);
    }

    private Payload convertir(JsonNode nodo) {
        Set<TipoCampo> aplicaA = EnumSet.noneOf(TipoCampo.class);
        nodo.path("aplica_a").forEach(tipo -> aplicaA.add(TipoCampo.valueOf(tipo.asText())));
        List<String> oraculos = new ArrayList<>();
        nodo.path("oraculos").forEach(oraculo -> oraculos.add(oraculo.asText()));
        return new Payload(
                nodo.get("id").asText(),
                CategoriaPayload.valueOf(nodo.get("categoria").asText()),
                aplicaA,
                nodo.get("payload").asText(),
                nodo.path("usa_canario").asBoolean(false),
                oraculos,
                nodo.has("cwe") ? nodo.get("cwe").asText() : null,
                nodo.has("descripcion") ? nodo.get("descripcion").asText() : null,
                nodo.has("extensiones")
                        ? mapper.convertValue(nodo.get("extensiones"), MAPA)
                        : Map.of());
    }

    private static String ruta(String ruta) {
        return ruta.isEmpty() ? "/" : ruta;
    }

    private static String leerEsquema(String id) {
        if (!id.startsWith(BASE_ID)) {
            return null;
        }
        String recurso = RECURSOS + "esquemas/" + id.substring(BASE_ID.length());
        try (InputStream flujo = CargadorPayloads.class.getResourceAsStream(recurso)) {
            if (flujo == null) {
                throw new IllegalStateException(
                        "Falta el esquema " + recurso + "; reconstruya el módulo opsec-ataques");
            }
            return new String(flujo.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el esquema " + recurso, e);
        }
    }
}
