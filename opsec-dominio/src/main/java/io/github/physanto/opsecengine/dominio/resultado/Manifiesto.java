package io.github.physanto.opsecengine.dominio.resultado;

import java.time.Instant;
import java.util.Objects;

/**
 * Datos que identifican una ejecución para poder repetirla exactamente.
 *
 * <p>Es inmutable y seguro entre hilos.
 *
 * @param hashJar hash SHA-256 en hexadecimal del JAR probado; no puede ser null ni estar en blanco
 * @param versionMotor versión de OpSecEngine que hizo la ejecución; no puede ser null ni estar en
 *     blanco
 * @param versionJava versión de Java de la aplicación probada; no puede ser null ni estar en blanco
 * @param sistemaOperativo nombre y versión del sistema operativo; no puede ser null ni estar en
 *     blanco
 * @param semilla semilla usada por los generadores aleatorios
 * @param hashSuite hash SHA-256 en hexadecimal de la suite ejecutada; no puede ser null ni estar en
 *     blanco
 * @param fecha momento en que empezó la ejecución; no puede ser null
 */
public record Manifiesto(
        String hashJar,
        String versionMotor,
        String versionJava,
        String sistemaOperativo,
        long semilla,
        String hashSuite,
        Instant fecha) {

    /**
     * Crea un manifiesto y valida sus datos.
     *
     * @throws NullPointerException si algún campo de texto o {@code fecha} son null
     * @throws IllegalArgumentException si algún campo de texto está en blanco
     */
    public Manifiesto {
        exigirTexto(hashJar, "el hash del JAR");
        exigirTexto(versionMotor, "la versión del motor");
        exigirTexto(versionJava, "la versión de Java");
        exigirTexto(sistemaOperativo, "el sistema operativo");
        exigirTexto(hashSuite, "el hash de la suite");
        Objects.requireNonNull(fecha, "La fecha del manifiesto no puede ser null");
    }

    private static void exigirTexto(String valor, String descripcion) {
        Objects.requireNonNull(valor, "El manifiesto necesita " + descripcion);
        if (valor.isBlank()) {
            throw new IllegalArgumentException(
                    "En el manifiesto, " + descripcion + " no puede estar en blanco");
        }
    }
}
