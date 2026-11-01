package io.github.physanto.opsecengine.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Obtiene el jar del agente que se pasa a la aplicación probada con {@code -javaagent}.
 *
 * <p>El jar de la CLI trae el agente como recurso ({@value #RECURSO}, junto a esta clase). Como la
 * JVM necesita un archivo, el recurso se copia a un archivo temporal que se borra al terminar la
 * CLI. También se puede indicar un jar propio, por ejemplo uno recién compilado por el dueño del
 * agente.
 *
 * <p>No tiene estado; es seguro entre hilos.
 */
final class UbicadorAgente {

    /** Nombre del recurso con el jar del agente, en el paquete de esta clase. */
    static final String RECURSO = "opsec-agente.jar";

    private UbicadorAgente() {}

    /**
     * Devuelve la ruta del jar del agente.
     *
     * @param propio jar indicado por el usuario; si es null se usa el que trae la CLI
     * @return ruta de un archivo jar que existe
     * @throws NoSuchFileException si el jar indicado no existe
     * @throws IOException si la CLI no trae el agente (por ejemplo, al ejecutarla desde las clases
     *     sin empaquetar) o si no se puede copiar a un archivo temporal
     */
    static Path ubicar(Path propio) throws IOException {
        if (propio != null) {
            if (!Files.isRegularFile(propio)) {
                throw new NoSuchFileException(propio.toString());
            }
            return propio;
        }
        try (InputStream recurso = UbicadorAgente.class.getResourceAsStream(RECURSO)) {
            if (recurso == null) {
                throw new IOException(
                        "Esta CLI no trae el jar del agente; constrúyala con ./mvnw package o"
                                + " indique uno con --agente");
            }
            Path temporal = Files.createTempFile("opsec-agente-", ".jar");
            temporal.toFile().deleteOnExit();
            Files.copy(recurso, temporal, StandardCopyOption.REPLACE_EXISTING);
            return temporal;
        }
    }
}
