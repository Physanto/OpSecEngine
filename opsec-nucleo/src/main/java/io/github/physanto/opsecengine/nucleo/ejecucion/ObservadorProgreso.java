package io.github.physanto.opsecengine.nucleo.ejecucion;

/**
 * Recibe el avance de una ejecución para que la presentación pueda mostrarlo.
 *
 * <p>Las llamadas son síncronas, en el hilo que ejecuta la suite y en orden de ejecución. La
 * implementación debe regresar pronto y trasladar las actualizaciones gráficas al hilo de su
 * interfaz. No debe invocar de nuevo el servicio. Sus excepciones de ejecución se registran sin
 * detener la suite. No necesita ser segura entre hilos si se usa con un solo servicio.
 */
@FunctionalInterface
public interface ObservadorProgreso {

    /** Observador sin efectos, usado cuando la presentación no necesita mostrar avance. */
    ObservadorProgreso VACIO = progreso -> {};

    /**
     * Recibe una notificación inmutable, sin valores de pasos ni credenciales.
     *
     * @param progreso avance de suite, caso o paso; nunca null
     */
    void actualizar(ProgresoEjecucion progreso);
}
