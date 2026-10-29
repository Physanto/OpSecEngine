package io.github.physanto.opsecengine.dominio.caso;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Paso de un caso de prueba: una acción genérica sobre un componente encontrado por un localizador.
 *
 * <p>Es inmutable y seguro entre hilos. Los valores de {@code extensiones} se copian de forma
 * superficial: quien los crea debe usar valores inmutables.
 *
 * @param orden posición del paso dentro de su caso, empezando en 1; sirve para identificarlo en la
 *     traza y en los informes
 * @param accion acción que se ejecuta; no puede ser null
 * @param objetivo localizador del componente o la ventana sobre la que actúa la acción; puede ser
 *     null solo cuando la acción es {@link Accion#ESPERAR} o {@link Accion#CAPTURAR}
 * @param valor dato que usa la acción, por ejemplo el texto que escribe {@link Accion#LLENAR};
 *     puede ser null cuando la acción no lo necesita
 * @param esperaMaxima tiempo máximo que se espera a que el paso termine; puede ser null para usar
 *     el valor de {@link ConfigEjecucion#esperaMaximaPaso()}
 * @param extensiones campos adicionales para cambios aditivos del contrato; si es null queda vacío,
 *     y no admite claves ni valores null
 */
public record Paso(
        int orden,
        Accion accion,
        Localizador objetivo,
        String valor,
        Duration esperaMaxima,
        Map<String, Object> extensiones) {

    /**
     * Crea un paso y valida sus datos.
     *
     * @throws NullPointerException si {@code accion} es null, si falta el objetivo en una acción
     *     que lo necesita o si {@code extensiones} contiene claves o valores null
     * @throws IllegalArgumentException si {@code orden} es menor que 1 o si {@code esperaMaxima} es
     *     cero o negativa
     */
    public Paso {
        if (orden < 1) {
            throw new IllegalArgumentException(
                    "El orden del paso debe ser mayor o igual que 1: " + orden);
        }
        Objects.requireNonNull(accion, "La acción del paso no puede ser null");
        if (!accion.admiteSinObjetivo()) {
            Objects.requireNonNull(objetivo, "La acción " + accion + " necesita un objetivo");
        }
        if (esperaMaxima != null && (esperaMaxima.isZero() || esperaMaxima.isNegative())) {
            throw new IllegalArgumentException(
                    "La espera máxima del paso debe ser positiva: " + esperaMaxima);
        }
        extensiones = extensiones == null ? Map.of() : Map.copyOf(extensiones);
    }
}
