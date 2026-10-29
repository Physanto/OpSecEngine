package io.github.physanto.opsecengine.agente;

import java.lang.instrument.Instrumentation;

/**
 * Punto de entrada del agente de OpSecEngine.
 *
 * <p>La JVM de la aplicación probada invoca esta clase antes del método {@code main} de la
 * aplicación cuando se arranca con {@code -javaagent:opsec-agente.jar=<argumentos>}. Por ahora es
 * un esqueleto: la implementación real (lectura de argumentos, apertura del socket del protocolo,
 * instalación de observadores y ganchos) corresponde al módulo del agente.
 *
 * <p>La clase no tiene estado y no se instancia.
 */
public final class AgenteMain {

    private AgenteMain() {}

    /**
     * Arranca el agente antes de que se ejecute la aplicación probada.
     *
     * <p>Lo invoca la JVM en el hilo principal, una sola vez, antes del {@code main} de la
     * aplicación. En esta versión no hace nada y nunca lanza excepciones.
     *
     * @param argumentos cadena de argumentos que sigue al signo {@code =} en la opción {@code
     *     -javaagent}; puede ser {@code null} si no se indicó ninguno
     * @param instrumentacion servicio de instrumentación que entrega la JVM, usado para instalar
     *     los ganchos sobre las clases de la aplicación
     */
    public static void premain(String argumentos, Instrumentation instrumentacion) {
        // La implementación real corresponde al módulo del agente.
    }
}
