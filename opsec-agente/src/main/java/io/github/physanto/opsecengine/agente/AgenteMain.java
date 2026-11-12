package io.github.physanto.opsecengine.agente;

import io.github.physanto.opsecengine.agente.swing.AdaptadorSwing;
import io.github.physanto.opsecengine.agente.swing.CierreAplicacion;
import io.github.physanto.opsecengine.agente.swing.ObservadorExcepciones;
import io.github.physanto.opsecengine.agente.swing.ObservadorVentanas;
import io.github.physanto.opsecengine.protocolo.CodecMensajes;
import java.awt.AWTError;
import java.lang.instrument.Instrumentation;

/**
 * Punto de entrada del agente de OpSecEngine.
 *
 * <p>La JVM de la aplicación probada invoca esta clase antes del método {@code main} de la
 * aplicación cuando se arranca con {@code
 * -javaagent:opsec-agente.jar=puerto=<puerto>,token=<token>}.
 *
 * <p>Arranque, en el hilo principal y antes del {@code main} de la aplicación:
 *
 * <ol>
 *   <li>Lee los argumentos.
 *   <li>Instala el {@link ObservadorConsola}, para que la aplicación tome las salidas observadas.
 *   <li>Instala el {@link ObservadorExcepciones} como manejador por defecto de excepciones no
 *       capturadas.
 *   <li>Registra el {@link ObservadorVentanas}.
 *   <li>Arranca el {@link ServidorProtocolo} con el {@link AdaptadorSwing} y el {@link
 *       CierreAplicacion} en un hilo daemon, que se conecta con el motor sin retrasar a la
 *       aplicación.
 * </ol>
 *
 * <p>Los eventos anteriores a la conexión se guardan y se envían después del saludo. La clase no
 * tiene estado y no se instancia.
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
     * arranque sin agente. Si no se puede observar alguna parte de la interfaz, lo registra y sigue
     * con el resto; nunca lanza excepciones.
     *
     * @param argumentos cadena de argumentos que sigue al signo {@code =} en la opción {@code
     *     -javaagent}, con el formato {@code puerto=<puerto>,token=<token>}; puede ser {@code null}
     * @param instrumentacion servicio de instrumentación que entrega la JVM; todavía no se usa
     */
    public static void premain(String argumentos, Instrumentation instrumentacion) {
        ArgumentosAgente leidos;
        try {
            leidos = ArgumentosAgente.leer(argumentos);
        } catch (RuntimeException e) {
            RegistroAgente.error("El agente no se inició: " + e.getMessage());
            return;
        }
        CodecMensajes codec = new CodecMensajes();
        EmisorProtocolo emisor = new EmisorProtocolo(codec);
        RegistroAgente.fijarSalida(System.err);
        try {
            ObservadorConsola.instalar(emisor);
        } catch (RuntimeException e) {
            RegistroAgente.error("No se pudo observar la consola: " + e);
        }
        try {
            ObservadorExcepciones.instalar(emisor, RegistroAgente::error);
        } catch (RuntimeException e) {
            RegistroAgente.error("No se pudieron observar las excepciones: " + e);
        }
        try {
            ObservadorVentanas.instalar(emisor, RegistroAgente::error);
        } catch (RuntimeException | AWTError | LinkageError e) {
            RegistroAgente.error("No se pudieron observar las ventanas: " + e);
        }
        try {
            Thread hilo =
                    new Thread(
                            new ServidorProtocolo(
                                    leidos,
                                    version(),
                                    new AdaptadorSwing(emisor),
                                    emisor,
                                    codec,
                                    new CierreAplicacion(RegistroAgente::error)),
                            NOMBRE_HILO);
            hilo.setDaemon(true);
            hilo.start();
        } catch (RuntimeException e) {
            RegistroAgente.error("El agente no se inició: " + e);
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
