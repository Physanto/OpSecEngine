package io.github.physanto.opsecengine.dominio.caso;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuración con la que se ejecuta una suite.
 *
 * <p>Es inmutable y seguro entre hilos.
 *
 * @param modo mecanismo con el que el agente ejecuta las acciones; no puede ser null
 * @param oculto {@code true} para ejecutar sin mostrar las ventanas ni robar el foco
 * @param esperaMaximaPaso tiempo máximo por paso cuando el paso no define el suyo; no puede ser
 *     null y debe ser positivo
 * @param reintentos veces que se repite un caso fallido antes de darlo por fallido; cero o más
 * @param semilla semilla de los generadores aleatorios, para poder repetir una ejecución
 * @param paralelismo número de sesiones de la aplicación que se ejecutan a la vez; uno o más
 */
public record ConfigEjecucion(
        ModoAccion modo,
        boolean oculto,
        Duration esperaMaximaPaso,
        int reintentos,
        long semilla,
        int paralelismo) {

    /** Espera máxima por paso que usa {@link #porDefecto()}. */
    public static final Duration ESPERA_MAXIMA_PASO_POR_DEFECTO = Duration.ofSeconds(10);

    /**
     * Crea una configuración y valida sus datos.
     *
     * @throws NullPointerException si {@code modo} o {@code esperaMaximaPaso} son null
     * @throws IllegalArgumentException si {@code esperaMaximaPaso} no es positiva, si {@code
     *     reintentos} es negativo o si {@code paralelismo} es menor que 1
     */
    public ConfigEjecucion {
        Objects.requireNonNull(modo, "El modo de acción no puede ser null");
        Objects.requireNonNull(esperaMaximaPaso, "La espera máxima por paso no puede ser null");
        if (esperaMaximaPaso.isZero() || esperaMaximaPaso.isNegative()) {
            throw new IllegalArgumentException(
                    "La espera máxima por paso debe ser positiva: " + esperaMaximaPaso);
        }
        if (reintentos < 0) {
            throw new IllegalArgumentException(
                    "Los reintentos no pueden ser negativos: " + reintentos);
        }
        if (paralelismo < 1) {
            throw new IllegalArgumentException(
                    "El paralelismo debe ser mayor o igual que 1: " + paralelismo);
        }
    }

    /**
     * Devuelve la configuración por defecto: eventos sintéticos, ventanas visibles, 10 segundos por
     * paso, sin reintentos, semilla 0 y una sola sesión.
     *
     * @return configuración por defecto
     */
    public static ConfigEjecucion porDefecto() {
        return new ConfigEjecucion(
                ModoAccion.EVENTOS, false, ESPERA_MAXIMA_PASO_POR_DEFECTO, 0, 0L, 1);
    }
}
