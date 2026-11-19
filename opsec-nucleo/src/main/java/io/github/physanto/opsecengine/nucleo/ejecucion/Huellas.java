package io.github.physanto.opsecengine.nucleo.ejecucion;

import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.Criterio;
import io.github.physanto.opsecengine.dominio.caso.Paso;
import io.github.physanto.opsecengine.dominio.caso.Suite;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Calcula las huellas SHA-256 que identifican la aplicación probada y la suite ejecutada.
 *
 * <p>Son las mismas que se guardan en el manifiesto de cada ejecución, así que sirven para saber si
 * una re-ejecución usa exactamente lo mismo que la original. Los hashes se escriben como {@code
 * sha256:<hex>}. La clase no tiene estado y es segura entre hilos.
 */
public final class Huellas {

    private static final Logger REGISTRO = Logger.getLogger(Huellas.class.getName());
    private static final String PREFIJO_HASH = "sha256:";

    private Huellas() {}

    /**
     * Calcula el hash del contenido de un JAR.
     *
     * @param jar ruta del JAR; no puede ser null
     * @return {@code sha256:<hex>}, o {@link EjecucionService#HASH_NO_DISPONIBLE} si el archivo no
     *     existe o no se puede leer
     * @throws NullPointerException si {@code jar} es null
     */
    public static String jar(Path jar) {
        Objects.requireNonNull(jar, "El JAR no puede ser null");
        if (!Files.isRegularFile(jar)) {
            return EjecucionService.HASH_NO_DISPONIBLE;
        }
        MessageDigest digest = sha256();
        try (InputStream flujo = new DigestInputStream(Files.newInputStream(jar), digest)) {
            flujo.transferTo(OutputStream.nullOutputStream());
        } catch (IOException e) {
            REGISTRO.log(Level.WARNING, "No se pudo calcular el hash de " + jar, e);
            return EjecucionService.HASH_NO_DISPONIBLE;
        }
        return PREFIJO_HASH + HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Calcula el hash del contenido traducido de la suite, con un orden estable: nombre, versión, y
     * por caso su id, su rol y sus pasos (acción, localizador, valor y espera).
     *
     * @param suite suite a resumir; no puede ser null
     * @return {@code sha256:<hex>}
     * @throws NullPointerException si {@code suite} es null
     */
    public static String suite(Suite suite) {
        Objects.requireNonNull(suite, "La suite no puede ser null");
        StringBuilder texto =
                new StringBuilder(suite.nombre()).append('\n').append(suite.version()).append('\n');
        for (Caso caso : suite.casos()) {
            texto.append(caso.id()).append('|').append(caso.rol()).append('\n');
            for (Paso paso : caso.pasos()) {
                texto.append(paso.orden()).append('|').append(paso.accion()).append('|');
                if (paso.objetivo() != null) {
                    for (Criterio criterio : paso.objetivo().criterios()) {
                        texto.append(criterio.estrategia()).append('=').append(criterio.valor());
                        texto.append(';');
                    }
                }
                texto.append('|').append(paso.valor()).append('|').append(paso.esperaMaxima());
                texto.append('\n');
            }
        }
        return PREFIJO_HASH
                + HexFormat.of()
                        .formatHex(
                                sha256().digest(texto.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("La JVM no tiene SHA-256", e);
        }
    }
}
