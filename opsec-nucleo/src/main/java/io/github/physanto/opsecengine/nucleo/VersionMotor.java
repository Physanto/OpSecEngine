package io.github.physanto.opsecengine.nucleo;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Versión del motor y del entorno en que corre.
 *
 * <p>La versión del motor sale del recurso {@code version.properties}, que Maven completa al
 * construir el módulo, así que coincide con la versión del {@code pom.xml}. La clase no tiene
 * estado mutable y es segura entre hilos.
 */
public final class VersionMotor {

    private static final String RECURSO = "version.properties";
    private static final String VERSION = leerVersion();

    private VersionMotor() {}

    /**
     * Devuelve la versión del motor.
     *
     * @return versión del motor, por ejemplo {@code 0.1.0-SNAPSHOT}
     */
    public static String motor() {
        return VERSION;
    }

    /**
     * Devuelve la versión de Java que ejecuta el motor.
     *
     * @return valor de la propiedad {@code java.version}, por ejemplo {@code 17.0.12}
     */
    public static String java() {
        return System.getProperty("java.version");
    }

    /**
     * Devuelve el nombre y la versión del sistema operativo.
     *
     * @return texto con {@code os.name}, {@code os.version} y {@code os.arch}
     */
    public static String sistemaOperativo() {
        return System.getProperty("os.name")
                + " "
                + System.getProperty("os.version")
                + " ("
                + System.getProperty("os.arch")
                + ")";
    }

    private static String leerVersion() {
        try (InputStream flujo = VersionMotor.class.getResourceAsStream(RECURSO)) {
            if (flujo == null) {
                throw new IllegalStateException(
                        "Falta el recurso " + RECURSO + "; reconstruya el módulo opsec-nucleo");
            }
            Properties propiedades = new Properties();
            try (Reader lector = new InputStreamReader(flujo, StandardCharsets.UTF_8)) {
                propiedades.load(lector);
            }
            return propiedades.getProperty("version");
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + RECURSO, e);
        }
    }
}
