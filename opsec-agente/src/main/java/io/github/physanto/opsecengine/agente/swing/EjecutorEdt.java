package io.github.physanto.opsecengine.agente.swing;

import java.awt.Dialog;
import java.awt.EventQueue;
import java.awt.Window;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Ejecuta tareas en el hilo de eventos de Swing (EDT) desde el hilo del agente, con tiempo máximo.
 *
 * <p>A diferencia de {@code invokeAndWait}, nunca espera para siempre: si el EDT está ocupado, la
 * espera termina al vencer el tiempo. Una acción que abre un diálogo modal no termina hasta que el
 * diálogo se cierra; en ese caso {@link #actuar(Runnable, long)} devuelve {@link
 * EstadoAccion#BLOQUEADA_POR_MODAL} apenas el diálogo aparece, y las tareas siguientes se siguen
 * ejecutando dentro del ciclo de eventos del diálogo.
 *
 * <p>No tiene estado; es seguro entre hilos. No se debe llamar desde el EDT.
 */
final class EjecutorEdt {

    /** Intervalo con que se revisa si la acción terminó o abrió un diálogo modal. */
    private static final long INTERVALO_MS = 25;

    /** Cómo terminó una acción. */
    enum EstadoAccion {
        /** La acción terminó. */
        TERMINADA,
        /** La acción abrió un diálogo modal y sigue esperando a que se cierre. */
        BLOQUEADA_POR_MODAL,
        /** La acción no terminó dentro del tiempo máximo. */
        TIEMPO_AGOTADO
    }

    private EjecutorEdt() {}

    /**
     * Ejecuta una lectura en el EDT y devuelve su resultado.
     *
     * @param <T> tipo del resultado
     * @param tarea lectura que se ejecuta en el EDT
     * @param esperaMs tiempo máximo de espera, en milisegundos
     * @return el resultado de la tarea
     * @throws TimeoutException si la tarea no terminó a tiempo
     * @throws ExecutionException si la tarea lanzó una excepción
     * @throws InterruptedException si se interrumpe la espera
     */
    static <T> T leer(Callable<T> tarea, long esperaMs)
            throws TimeoutException, ExecutionException, InterruptedException {
        FutureTask<T> futura = new FutureTask<>(tarea);
        EventQueue.invokeLater(futura);
        return futura.get(Math.max(1, esperaMs), TimeUnit.MILLISECONDS);
    }

    /**
     * Ejecuta una acción en el EDT y espera a que termine o a que abra un diálogo modal.
     *
     * @param accion acción sobre la interfaz
     * @param esperaMs tiempo máximo de espera, en milisegundos
     * @return cómo terminó la acción
     * @throws ExecutionException si la acción lanzó una excepción
     * @throws InterruptedException si se interrumpe la espera
     */
    static EstadoAccion actuar(Runnable accion, long esperaMs)
            throws ExecutionException, InterruptedException {
        AtomicReference<Set<Window>> modalesAntes = new AtomicReference<>();
        FutureTask<Void> futura =
                new FutureTask<>(
                        () -> {
                            modalesAntes.set(modalesVisibles());
                            accion.run();
                            return null;
                        });
        EventQueue.invokeLater(futura);
        long limite = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(esperaMs);
        while (true) {
            try {
                futura.get(INTERVALO_MS, TimeUnit.MILLISECONDS);
                return EstadoAccion.TERMINADA;
            } catch (TimeoutException e) {
                Set<Window> antes = modalesAntes.get();
                if (antes != null && hayModalNuevo(antes)) {
                    return EstadoAccion.BLOQUEADA_POR_MODAL;
                }
                if (System.nanoTime() > limite) {
                    return EstadoAccion.TIEMPO_AGOTADO;
                }
            }
        }
    }

    /** Diálogos modales visibles en este momento. */
    static Set<Window> modalesVisibles() {
        Set<Window> modales = Collections.newSetFromMap(new IdentityHashMap<Window, Boolean>());
        for (Window ventana : Window.getWindows()) {
            if (ventana instanceof Dialog && ((Dialog) ventana).isModal() && ventana.isShowing()) {
                modales.add(ventana);
            }
        }
        return modales;
    }

    private static boolean hayModalNuevo(Set<Window> antes) {
        for (Window modal : modalesVisibles()) {
            if (!antes.contains(modal)) {
                return true;
            }
        }
        return false;
    }
}
