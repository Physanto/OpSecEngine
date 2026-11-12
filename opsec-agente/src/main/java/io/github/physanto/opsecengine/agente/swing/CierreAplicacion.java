package io.github.physanto.opsecengine.agente.swing;

import java.awt.AWTError;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Cierra la aplicación probada de forma ordenada cuando el motor envía {@code CERRAR}.
 *
 * <p>Primero le pide a cada ventana visible que se cierre, como lo haría el usuario con el botón de
 * cierre: encola un {@code WINDOW_CLOSING} por ventana, así que se respetan la operación de cierre
 * por defecto y los escuchadores de la aplicación. Si la aplicación no termina sola dentro del
 * plazo (porque sus ventanas solo se ocultan, pide confirmación, tiene otros hilos vivos o no tiene
 * ventanas), termina la JVM con {@code System.exit(0)}.
 *
 * <p>El plazo por defecto ({@link #PLAZO_POR_DEFECTO_MS} ms) es menor que la espera del motor antes
 * de detener el proceso, para que la aplicación siempre alcance a terminar por dentro.
 *
 * <p>Se ejecuta en el hilo del agente, nunca en el EDT, y no lanza excepciones: si no puede pedir
 * el cierre de las ventanas, lo registra y sigue con la salida. Cada instancia se usa una sola vez.
 */
public final class CierreAplicacion implements Runnable {

    /** Milisegundos que se espera a que la aplicación termine sola antes de forzar la salida. */
    public static final long PLAZO_POR_DEFECTO_MS = 1000;

    private final long plazoMs;
    private final IntConsumer salida;
    private final Consumer<String> registro;

    /**
     * Crea el cierre con el plazo por defecto y {@code System.exit} como salida.
     *
     * @param registro destino de los mensajes de diagnóstico del agente; no puede ser null
     */
    public CierreAplicacion(Consumer<String> registro) {
        this(PLAZO_POR_DEFECTO_MS, System::exit, registro);
    }

    /**
     * Crea el cierre con un plazo y una salida propios; lo usan las pruebas.
     *
     * @param plazoMs milisegundos que se espera antes de forzar la salida
     * @param salida acción que termina la JVM con un código de salida
     * @param registro destino de los mensajes de diagnóstico del agente
     */
    CierreAplicacion(long plazoMs, IntConsumer salida, Consumer<String> registro) {
        this.plazoMs = plazoMs;
        this.salida = salida;
        this.registro = registro;
    }

    /**
     * Pide el cierre de las ventanas, espera el plazo y, si la JVM sigue viva, la termina con
     * código 0. Si la aplicación termina antes, este método no llega a volver.
     */
    @Override
    public void run() {
        try {
            pedirCierreDeVentanas();
        } catch (RuntimeException | AWTError | LinkageError e) {
            registro.accept("No se pudo pedir el cierre de las ventanas: " + e);
        }
        try {
            Thread.sleep(plazoMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        salida.accept(0);
    }

    /** Encola, desde el EDT, un {@code WINDOW_CLOSING} para cada ventana visible. */
    private static void pedirCierreDeVentanas() {
        if (Window.getWindows().length == 0) {
            return;
        }
        EventQueue.invokeLater(() -> {
            EventQueue cola = Toolkit.getDefaultToolkit().getSystemEventQueue();
            for (Window ventana : Window.getWindows()) {
                if (ventana.isShowing()) {
                    cola.postEvent(new WindowEvent(ventana, WindowEvent.WINDOW_CLOSING));
                }
            }
        });
    }
}
