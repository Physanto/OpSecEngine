package io.github.physanto.opsecengine.nucleo.exploracion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.physanto.opsecengine.dominio.mapa.MapaApp;
import io.github.physanto.opsecengine.dominio.mapa.Ventana;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Guarda y lee el mapa de la aplicación como {@code mapa.json}, con el mismo formato que el mapa de
 * ejemplo de {@code opsec-soporte-pruebas}. Las ventanas se escriben ordenadas por id para que el
 * archivo sea estable entre exploraciones. No tiene estado; es seguro entre hilos.
 */
public final class MapaJson {

    /** Nombre del archivo del mapa dentro de la carpeta de un proyecto de pruebas. */
    public static final String ARCHIVO = "mapa.json";

    private static final ObjectMapper MAPPER =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private MapaJson() {}

    /**
     * Escribe el mapa en UTF-8, reemplazando el archivo si existe.
     *
     * @param mapa mapa a guardar; no puede ser null
     * @param destino archivo de destino; su carpeta debe existir; no puede ser null
     * @throws IOException si no se puede escribir
     * @throws NullPointerException si algún parámetro es null
     */
    public static void guardar(MapaApp mapa, Path destino) throws IOException {
        Objects.requireNonNull(mapa, "El mapa no puede ser null");
        Objects.requireNonNull(destino, "El destino no puede ser null");
        List<Ventana> ventanas =
                mapa.ventanas().stream().sorted(Comparator.comparing(Ventana::id)).toList();
        try (Writer escritor = Files.newBufferedWriter(destino, StandardCharsets.UTF_8)) {
            MAPPER.writeValue(
                    escritor, Map.of("ventanas", ventanas, "transiciones", mapa.transiciones()));
        }
    }

    /**
     * Lee un mapa guardado.
     *
     * @param origen archivo {@code mapa.json}; no puede ser null
     * @return el mapa, validado por el constructor de {@link MapaApp}
     * @throws IOException si no se puede leer o su formato no es válido
     * @throws NullPointerException si {@code origen} es null
     */
    public static MapaApp leer(Path origen) throws IOException {
        Objects.requireNonNull(origen, "El origen no puede ser null");
        try (Reader lector = Files.newBufferedReader(origen, StandardCharsets.UTF_8)) {
            return MAPPER.readValue(lector, MapaApp.class);
        } catch (IllegalArgumentException e) {
            throw new IOException("El mapa no es válido: " + e.getMessage(), e);
        }
    }
}
