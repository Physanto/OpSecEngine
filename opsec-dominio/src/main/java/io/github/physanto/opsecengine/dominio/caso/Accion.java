package io.github.physanto.opsecengine.dominio.caso;

/**
 * Acción genérica que ejecuta un {@link Paso} sobre la aplicación probada.
 *
 * <p>Las acciones no dependen de ninguna aplicación concreta: siempre actúan sobre el componente o
 * la ventana que indica el localizador del paso.
 */
public enum Accion {
    /** Pulsa un botón, un elemento de menú o cualquier componente que se pueda activar. */
    PULSAR,
    /** Escribe el valor del paso en un campo de texto, reemplazando su contenido. */
    LLENAR,
    /** Selecciona el valor del paso en una lista, un combo, una tabla o un árbol. */
    SELECCIONAR,
    /**
     * Fija el estado de una casilla o un botón de opción; el valor del paso es {@code "true"} o
     * {@code "false"}.
     */
    MARCAR,
    /**
     * Espera a que aparezca el objetivo o, sin objetivo, a que la interfaz quede inactiva; es la
     * única acción que no actúa sobre la aplicación.
     */
    ESPERAR,
    /** Lleva el foco a una ventana o pestaña concreta, por ejemplo para cambiar de pantalla. */
    IR_A,
    /** Cierra la ventana o el diálogo que indica el objetivo. */
    CERRAR_VENTANA,
    /** Toma una captura del objetivo o, sin objetivo, de todas las ventanas visibles. */
    CAPTURAR;

    /**
     * Indica si la acción puede ejecutarse sin un localizador objetivo.
     *
     * @return {@code true} solo para {@link #ESPERAR} y {@link #CAPTURAR}
     */
    public boolean admiteSinObjetivo() {
        return this == ESPERAR || this == CAPTURAR;
    }
}
