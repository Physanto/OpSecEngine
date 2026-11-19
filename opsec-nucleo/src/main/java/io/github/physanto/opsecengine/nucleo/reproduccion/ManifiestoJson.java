package io.github.physanto.opsecengine.nucleo.reproduccion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.physanto.opsecengine.dominio.resultado.Manifiesto;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/**
 * Lee y escribe el {@link Manifiesto} como {@code manifiesto.json}.
 *
 * <p>El archivo lleva {@code formato}, {@code hashJar}, {@code versionMotor}, {@code versionJava},
 * {@code sistemaOperativo}, {@code semilla}, {@code hashSuite} y {@code fecha} (ISO-8601). No tiene
 * estado y es segura entre hilos.
 */
public final class ManifiestoJson {

    /** Versión del formato del archivo. */
    public static final int FORMATO = 1;

    private static final ObjectMapper MAPPER =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private ManifiestoJson() {}

    /**
     * Escribe el manifiesto en un archivo UTF-8, reemplazándolo si existe.
     *
     * @param manifiesto manifiesto a escribir; no puede ser null
     * @param destino archivo de destino; su carpeta debe existir; no puede ser null
     * @throws IOException si no se puede escribir
     * @throws NullPointerException si algún parámetro es null
     */
    public static void escribir(Manifiesto manifiesto, Path destino) throws IOException {
        Objects.requireNonNull(manifiesto, "El manifiesto no puede ser null");
        ObjectNode nodo = MAPPER.createObjectNode();
        nodo.put("formato", FORMATO);
        nodo.put("hashJar", manifiesto.hashJar());
        nodo.put("versionMotor", manifiesto.versionMotor());
        nodo.put("versionJava", manifiesto.versionJava());
        nodo.put("sistemaOperativo", manifiesto.sistemaOperativo());
        nodo.put("semilla", manifiesto.semilla());
        nodo.put("hashSuite", manifiesto.hashSuite());
        nodo.put("fecha", manifiesto.fecha().toString());
        try (Writer escritor = Files.newBufferedWriter(destino, StandardCharsets.UTF_8)) {
            MAPPER.writeValue(escritor, nodo);
        }
    }

    /**
     * Lee un manifiesto.
     *
     * @param archivo archivo {@code manifiesto.json}; no puede ser null
     * @return el manifiesto
     * @throws IOException si no se puede leer o no tiene el formato esperado
     * @throws NullPointerException si {@code archivo} es null
     */
    public static Manifiesto leer(Path archivo) throws IOException {
        Objects.requireNonNull(archivo, "El archivo no puede ser null");
        JsonNode nodo;
        try (Reader lector = Files.newBufferedReader(archivo, StandardCharsets.UTF_8)) {
            nodo = MAPPER.readTree(lector);
        }
        if (nodo == null || !nodo.isObject()) {
            throw new IOException("El manifiesto " + archivo + " no es un objeto JSON");
        }
        try {
            return new Manifiesto(
                    texto(nodo, "hashJar"),
                    texto(nodo, "versionMotor"),
                    texto(nodo, "versionJava"),
                    texto(nodo, "sistemaOperativo"),
                    nodo.path("semilla").asLong(0L),
                    texto(nodo, "hashSuite"),
                    Instant.parse(texto(nodo, "fecha")));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new IOException(
                    "El manifiesto " + archivo + " no es válido: " + e.getMessage(), e);
        }
    }

    private static String texto(JsonNode nodo, String campo) {
        JsonNode valor = nodo.get(campo);
        if (valor == null || !valor.isTextual()) {
            throw new IllegalArgumentException("falta el campo " + campo);
        }
        return valor.asText();
    }
}
