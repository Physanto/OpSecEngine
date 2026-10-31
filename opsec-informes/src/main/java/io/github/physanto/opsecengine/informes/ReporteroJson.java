package io.github.physanto.opsecengine.informes;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.physanto.opsecengine.dominio.extension.Reportero;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoSuite;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Reportero del formato {@value #FORMATO}: escribe el resultado completo de la suite en {@value
 * #ARCHIVO}, en JSON legible.
 *
 * <p>El JSON usa los nombres de los componentes de los records del dominio, los enums por su nombre
 * y los instantes y las duraciones en ISO-8601 ({@code 2026-10-30T10:00:00Z}, {@code PT2.5S}); los
 * campos sin valor se escriben como {@code null}. Es el mismo formato de {@code
 * resultados/ejemplo.json} en {@code opsec-soporte-pruebas}, así que el archivo se puede volver a
 * leer como un {@link ResultadoSuite}. Se escribe en UTF-8 con sangría de dos espacios.
 *
 * <p>Es el ejemplo de referencia para escribir reporteros: no tiene estado mutable, se registra en
 * {@code META-INF/services/io.github.physanto.opsecengine.dominio.extension.Reportero} y es seguro
 * entre hilos.
 */
public final class ReporteroJson implements Reportero {

    /** Formato que produce. */
    public static final String FORMATO = "json";

    /** Archivo que escribe en la carpeta de resultados. */
    public static final String ARCHIVO = "resultado.json";

    private final JsonMapper mapper =
            JsonMapper.builder()
                    .addModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
                    .enable(SerializationFeature.INDENT_OUTPUT)
                    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .build();

    /** Crea el reportero; lo usa {@link java.util.ServiceLoader}. */
    public ReporteroJson() {}

    /**
     * Devuelve el formato del reportero.
     *
     * @return {@value #FORMATO}
     */
    @Override
    public String formato() {
        return FORMATO;
    }

    /**
     * Escribe {@value #ARCHIVO} en la carpeta de destino; lo reemplaza si ya existe.
     *
     * @param resultado resultado de la suite; no puede ser null
     * @param destino carpeta de resultados, que ya existe; no puede ser null
     * @throws IOException si no puede escribir el archivo
     */
    @Override
    public void escribir(ResultadoSuite resultado, Path destino) throws IOException {
        Objects.requireNonNull(resultado, "El resultado no puede ser null");
        Objects.requireNonNull(destino, "La carpeta de destino no puede ser null");
        try (Writer escritor =
                Files.newBufferedWriter(destino.resolve(ARCHIVO), StandardCharsets.UTF_8)) {
            mapper.writeValue(escritor, resultado);
        }
    }
}
