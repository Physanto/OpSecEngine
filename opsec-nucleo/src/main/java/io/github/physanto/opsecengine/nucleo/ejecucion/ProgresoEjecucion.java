package io.github.physanto.opsecengine.nucleo.ejecucion;

import io.github.physanto.opsecengine.protocolo.dto.CodigoResultado;

/**
 * Notificación inmutable del avance de una ejecución; es segura entre hilos.
 *
 * <p>Los pasos de login se identifican con {@code login=true} y el orden de su plantilla, a
 * diferencia del número 0 que llevan en la traza. Los totales describen el trabajo previsto: un
 * fallo puede impedir ejecutar todos los pasos. Un caso no ejecutable emite inicio y fin de caso,
 * sin notificaciones de pasos. El fin de suite se emite después de la evaluación y del manifiesto;
 * no se garantiza una notificación de fin si la ejecución lanza una excepción.
 *
 * @param tipo transición notificada
 * @param nombreSuite nombre de la suite
 * @param totalCasos cantidad de casos previstos
 * @param casoId identificador del caso; null para las notificaciones de suite
 * @param numeroPaso orden del paso; 0 para las notificaciones de caso o suite
 * @param totalPasos pasos previstos del caso o del login; 0 para la suite
 * @param login indica si se trata de un paso del login
 * @param codigo resultado del paso; solo presente en {@link Tipo#PASO_FIN}, null en los demás
 */
public record ProgresoEjecucion(
        Tipo tipo,
        String nombreSuite,
        int totalCasos,
        String casoId,
        int numeroPaso,
        int totalPasos,
        boolean login,
        CodigoResultado codigo) {

    /** Transiciones de suite, caso y paso, en su orden real de ejecución. */
    public enum Tipo {
        /** Comienza la suite. */
        SUITE_INICIO,
        /** Termina la suite y su resultado está listo. */
        SUITE_FIN,
        /** Comienza un caso. */
        CASO_INICIO,
        /** Termina un caso, incluida su evaluación. */
        CASO_FIN,
        /** Comienza un paso del caso o del login. */
        PASO_INICIO,
        /** Termina un paso con un código de resultado. */
        PASO_FIN
    }
}
