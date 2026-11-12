package io.github.physanto.opsecengine.oraculos;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.path.PathType;
import io.github.physanto.opsecengine.dominio.extension.Oraculo;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Carga reglas YAML como oráculos, validando el esquema y los catálogos oficiales empaquetados. No
 * necesita acceso a la red ni al directorio del repositorio en ejecución. Es seguro entre hilos.
 */
public final class CargadorReglas {
    private static final String BASE_ID =
            "https://raw.githubusercontent.com/Physanto/OpSecEngine/main/contratos/esquemas/";
    private static final String RECURSOS = "/io/github/physanto/opsecengine/oraculos/";
    private final YAMLMapper mapper =
            YAMLMapper.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .disable(StreamReadFeature.AUTO_CLOSE_SOURCE)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .build();
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
                                            .schemas(id ->
                                                    id.startsWith(BASE_ID)
                                                            ? recurso(
                                                                    "esquemas/"
                                                                            + id.substring(
                                                                                    BASE_ID
                                                                                            .length()))
                                                            : null))
                    .getSchema(SchemaLocation.of(BASE_ID + "oraculo-regla.schema.json"));
    private final Set<String> reservados;
    private final Set<String> cwes;

    /**
     * Crea el cargador con los catálogos incluidos en el módulo.
     *
     * @throws IllegalStateException si faltan los recursos oficiales o no son válidos
     */
    public CargadorReglas() {
        reservados = catalogo("oraculos");
        cwes = catalogo("cwe");
    }

    /**
     * Lee un archivo YAML en UTF-8 y convierte sus reglas a oráculos.
     *
     * @param archivo archivo que se lee; no puede ser null
     * @return oráculos en el orden del archivo, en una lista inmutable
     * @throws IOException si no puede leerse el archivo
     * @throws IllegalArgumentException si el YAML, las reglas o sus identificadores son inválidos
     */
    public List<Oraculo> leer(Path archivo) throws IOException {
        Objects.requireNonNull(archivo, "El archivo no puede ser null");
        try (Reader lector = Files.newBufferedReader(archivo, StandardCharsets.UTF_8)) {
            return leer(lector, archivo.toString());
        }
    }

    /**
     * Lee reglas de un lector sin cerrarlo.
     *
     * @param lector texto YAML; no puede ser null
     * @param origen nombre del origen para los mensajes de error
     * @return oráculos en una lista inmutable, sin ids repetidos ni reservados
     * @throws IOException si falla la lectura
     * @throws IllegalArgumentException si el YAML o las reglas no cumplen el contrato
     */
    public List<Oraculo> leer(Reader lector, String origen) throws IOException {
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
            errores.add(error.getInstanceLocation() + ": " + error.getMessage());
        }
        if (errores.isEmpty()) {
            Set<String> ids = new LinkedHashSet<>();
            for (int i = 0; i < raiz.size(); i++) {
                JsonNode regla = raiz.get(i);
                String id = regla.get("id").asText();
                if (!ids.add(id)) {
                    errores.add("/" + i + "/id: id repetido " + id);
                }
                if (reservados.contains(id)) {
                    errores.add("/" + i + "/id: id reservado " + id);
                }
                JsonNode cwe = regla.get("entonces").get("cwe");
                if (cwe != null && !cwes.contains(cwe.asText())) {
                    errores.add("/" + i + "/entonces/cwe: CWE fuera del catálogo " + cwe.asText());
                }
            }
        }
        if (!errores.isEmpty()) {
            throw new IllegalArgumentException(
                    origen
                            + ": reglas inválidas:"
                            + System.lineSeparator()
                            + String.join(System.lineSeparator(), errores));
        }
        List<Oraculo> reglas = new ArrayList<>();
        raiz.forEach(regla -> reglas.add(new OraculoRegla(regla)));
        return List.copyOf(reglas);
    }

    /**
     * Carga los archivos {@code *.yaml} inmediatos de una carpeta, en orden de nombre.
     *
     * @param carpeta carpeta de reglas, por ejemplo {@code proyecto/oraculos}
     * @return reglas en orden de archivo y de declaración; vacía si no hay archivos YAML
     * @throws IOException si no puede listarse la carpeta o leerse algún archivo
     * @throws IllegalArgumentException si un archivo es inválido o dos archivos repiten un id
     */
    public List<Oraculo> cargarCarpeta(Path carpeta) throws IOException {
        List<Path> archivos;
        try (var entradas = Files.list(carpeta)) {
            archivos =
                    entradas.filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().endsWith(".yaml"))
                            .sorted()
                            .toList();
        }
        List<Oraculo> todos = new ArrayList<>();
        Map<String, Path> origenes = new HashMap<>();
        for (Path archivo : archivos) {
            for (Oraculo regla : leer(archivo)) {
                Path anterior = origenes.putIfAbsent(regla.id(), archivo);
                if (anterior != null) {
                    throw new IllegalArgumentException(
                            "Id repetido " + regla.id() + " en " + anterior + " y " + archivo);
                }
                todos.add(regla);
            }
        }
        return List.copyOf(todos);
    }

    private Set<String> catalogo(String nombre) {
        try {
            Set<String> ids = new LinkedHashSet<>();
            mapper.readTree(recurso("catalogos/" + nombre + ".yaml"))
                    .path(nombre)
                    .forEach(nodo -> ids.add(nodo.get("id").asText()));
            return Set.copyOf(ids);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el catálogo " + nombre, e);
        }
    }

    private static String recurso(String nombre) {
        try (InputStream flujo = CargadorReglas.class.getResourceAsStream(RECURSOS + nombre)) {
            if (flujo == null) {
                throw new IllegalStateException("Falta el recurso oficial " + nombre);
            }
            return new String(flujo.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el recurso " + nombre, e);
        }
    }
}
