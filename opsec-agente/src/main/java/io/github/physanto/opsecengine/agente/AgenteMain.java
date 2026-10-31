package io.github.physanto.opsecengine.agente;

import java.lang.instrument.Instrumentation;

/**
 * Punto de entrada del agente de OpSecEngine.
 *
 * <p>La JVM de la aplicación probada invoca esta clase antes del método {@code main} de la
 * aplicación cuando se arranca con {@code
 * -javaagent:opsec-agente.jar=puerto=<puerto>,token=<token>}.
 *
 * <p><b>Esqueleto temporal ("agente eco").</b> Por ahora solo hace el saludo: lee los argumentos,
 * se conecta al motor, envía {@code HOLA}, responde {@code ERROR} con el código {@code
 * NO_IMPLEMENTADO} a cualquier otro mensaje y termina la conversación con {@code CERRAR}. Todo
 * ocurre en un hilo daemon, así que no bloquea ni retrasa la aplicación. El dueño del módulo
 * reemplazará este esqueleto por el agente real (servidor del protocolo, adaptador Swing,
 * observadores y ganchos).
 *
 * <p>La clase no tiene estado y no se instancia.
 */
public final class AgenteMain {

    /** Nombre del hilo del agente. */
    static final String NOMBRE_HILO = "opsec-agente";

    private AgenteMain() {}

    /**
     * Arranca el agente antes de que se ejecute la aplicación probada.
     *
     * <p>Lo invoca la JVM en el hilo principal, una sola vez, antes del {@code main} de la
     * aplicación. Vuelve enseguida: la conversación con el motor corre en un hilo daemon. Si los
     * argumentos no son válidos, escribe el motivo en la salida de error y deja que la aplicación
     * arranque sin agente; nunca lanza excepciones.
     *
     * @param argumentos cadena de argumentos que sigue al signo {@code =} en la opción {@code
     *     -javaagent}, con el formato {@code puerto=<puerto>,token=<token>}; puede ser {@code null}
     * @param instrumentacion servicio de instrumentación que entrega la JVM; el agente eco no lo
     *     usa
     */
    public static void premain(String argumentos, Instrumentation instrumentacion) {
        try {
            Thread hilo =
                    new Thread(
                            new AgenteEco(ArgumentosAgente.leer(argumentos), version()),
                            NOMBRE_HILO);
            hilo.setDaemon(true);
            hilo.start();
        } catch (RuntimeException e) {
            System.err.println("[opsec-agente] El agente no se inició: " + e.getMessage());
        }
    }

    /**
     * Devuelve la versión del agente, leída del manifiesto del jar.
     *
     * @return la versión, o {@code "desarrollo"} si las clases no vienen de un jar con versión
     */
    static String version() {
        String version = AgenteMain.class.getPackage().getImplementationVersion();
        return version == null ? "desarrollo" : version;
    }
}
