package io.github.physanto.opsecengine.agente.swing;

import io.github.physanto.opsecengine.agente.api.EmisorEventos;
import java.awt.EventQueue;
import java.awt.Window;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Vigila que el hilo de eventos de Swing (EDT) de la aplicación probada no se quede bloqueado.
 *
 * <p>Desde un hilo daemon propio, cada {@value #INTERVALO_MS} ms publica una tarea mínima en la
 * cola del EDT y mide cuánto tarda en ejecutarse. Si la demora supera el umbral, emite un evento
 * {@code TIEMPO} con {@code medida=EDT_BLOQUEADO}, {@code duracionMs} (la demora medida) y {@code
 * umbralMs} (el umbral configurado), que es lo que necesita el oráculo {@code INTERFAZ-CONGELADA}.
 *
 * <p>Detalles que conviene conocer:
 *
 * <ul>
 *   <li>El evento se emite desde el propio EDT en cuanto vuelve a atender la cola, así que llega a
 *       la traza antes que lo que el EDT haga después. Si el EDT nunca se recupera, no se emite
 *       nada: el paso del motor termina entonces con {@code TIEMPO_AGOTADO}.
 *   <li>No toca la cola hasta que haya una ventana visible, para no crear el EDT antes de que la
 *       aplicación lo haga.
 *   <li>Un diálogo modal no cuenta como bloqueo: el EDT sigue atendiendo eventos dentro de él.
 *   <li>Cada bloqueo genera un solo evento, porque la siguiente medida empieza cuando termina la
 *       anterior.
 * </ul>
 *
 * <p>Es seguro entre hilos. Nunca lanza excepciones hacia la aplicación: los errores se informan al
 * registro del agente.
 */
public final class VigilanteEdt implements Runnable {

    /** Umbral por defecto, en milisegundos, a partir del cual se informa un bloqueo. */
    public static final long UMBRAL_POR_DEFECTO_MS = 1_000;

    /** Pausa entre una medida y la siguiente, en milisegundos. */
    static final long INTERVALO_MS = 100;

    /** Medida del evento {@code TIEMPO} que informa un bloqueo del EDT. */
    public static final String MEDIDA_EDT_BLOQUEADO = "EDT_BLOQUEADO";

    private final EmisorEventos emisor;
    private final long umbralMs;
    private final Consumer<String> registro;
    private volatile Thread hilo;

    /**
     * Crea el vigilante sin arrancarlo.
     *
     * @param emisor emisor de los eventos; no puede ser null
     * @param umbralMs demora, en milisegundos, que se considera bloqueo; debe ser positiva
     * @param registro destino de los mensajes de error del agente; no puede ser null
     * @throws IllegalArgumentException si {@code umbralMs} no es positivo
     */
    public VigilanteEdt(EmisorEventos emisor, long umbralMs, Consumer<String> registro) {
        if (umbralMs < 1) {
            throw new IllegalArgumentException("El umbral del EDT debe ser positivo: " + umbralMs);
        }
        this.emisor = emisor;
        this.umbralMs = umbralMs;
        this.registro = registro;
    }

    /**
     * Crea el vigilante y arranca su hilo daemon.
     *
     * @param emisor emisor de los eventos; no puede ser null
     * @param umbralMs demora, en milisegundos, que se considera bloqueo; debe ser positiva
     * @param registro destino de los mensajes de error del agente; no puede ser null
     * @return el vigilante en marcha
     * @throws IllegalArgumentException si {@code umbralMs} no es positivo
     */
    public static VigilanteEdt iniciar(
            EmisorEventos emisor, long umbralMs, Consumer<String> registro) {
        VigilanteEdt vigilante = new VigilanteEdt(emisor, umbralMs, registro);
        Thread nuevo = new Thread(vigilante, "opsec-vigilante-edt");
        nuevo.setDaemon(true);
        vigilante.hilo = nuevo;
        nuevo.start();
        return vigilante;
    }

    /** Detiene el hilo del vigilante. Es idempotente; sin hilo arrancado no hace nada. */
    public void detener() {
        Thread actual = hilo;
        if (actual != null) {
            actual.interrupt();
        }
    }

    /** Mide el EDT en bucle hasta que se interrumpe el hilo. */
    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    if (hayVentanaVisible()) {
                        medir();
                    }
                } catch (RuntimeException e) {
                    registro.accept("El vigilante del EDT falló: " + e);
                }
                Thread.sleep(INTERVALO_MS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Publica una tarea en el EDT y espera a que se ejecute; informa si tardó más del umbral. */
    void medir() throws InterruptedException {
        CountDownLatch atendida = new CountDownLatch(1);
        long envio = System.nanoTime();
        EventQueue.invokeLater(() -> {
            try {
                long demora = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - envio);
                if (demora > umbralMs) {
                    informar(demora);
                }
            } catch (RuntimeException e) {
                registro.accept("No se pudo informar el bloqueo del EDT: " + e);
            } finally {
                atendida.countDown();
            }
        });
        atendida.await();
    }

    private void informar(long demoraMs) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("medida", MEDIDA_EDT_BLOQUEADO);
        datos.put("duracionMs", demoraMs);
        datos.put("umbralMs", umbralMs);
        emisor.emitir("TIEMPO", datos);
    }

    private static boolean hayVentanaVisible() {
        for (Window ventana : Window.getWindows()) {
            if (ventana.isShowing()) {
                return true;
            }
        }
        return false;
    }
}
