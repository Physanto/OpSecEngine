package io.github.physanto.opsecengine.agente;

import java.io.PrintStream;

/**
 * Registro de los mensajes propios del agente.
 *
 * <p>Escribe en la salida de error original de la JVM, la que existía antes de que {@link
 * ObservadorConsola} la reemplazara. Así los mensajes del agente no se confunden con los de la
 * aplicación ni vuelven al motor como eventos {@code CONSOLA}. Cada línea empieza con {@value
 * #PREFIJO}.
 *
 * <p>Es seguro entre hilos y nunca lanza excepciones.
 */
final class RegistroAgente {

    /** Prefijo de cada línea que escribe el agente. */
    static final String PREFIJO = "[opsec-agente] ";

    private static volatile PrintStream salida = System.err;

    private RegistroAgente() {}

    /**
     * Fija la salida donde se escriben los mensajes del agente.
     *
     * @param nueva salida de error original de la JVM; si es null no cambia nada
     */
    static void fijarSalida(PrintStream nueva) {
        if (nueva != null) {
            salida = nueva;
        }
    }

    /**
     * Escribe un mensaje del agente.
     *
     * @param mensaje texto del mensaje
     */
    static void error(String mensaje) {
        try {
            salida.println(PREFIJO + mensaje);
        } catch (RuntimeException e) {
            // El registro nunca debe afectar a la aplicación probada
        }
    }
}
