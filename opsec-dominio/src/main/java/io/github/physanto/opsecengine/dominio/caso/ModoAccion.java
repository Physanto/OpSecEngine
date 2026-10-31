package io.github.physanto.opsecengine.dominio.caso;

/** Mecanismo con el que el agente ejecuta las acciones sobre los componentes Swing. */
public enum ModoAccion {
    /**
     * Llama directamente a los métodos del componente, como {@code setText} o {@code doClick}. Es
     * el más rápido, pero no recorre los manejadores de teclado y ratón.
     */
    SEMANTICO,
    /**
     * Envía eventos sintéticos de teclado y ratón con {@code dispatchEvent}. Es el modo por
     * defecto: se parece a un usuario real y no mueve el cursor.
     */
    EVENTOS,
    /**
     * Usa {@code java.awt.Robot}, que mueve el cursor y escribe de verdad. Es el más fiel, pero
     * bloquea el equipo y exige que la ventana esté visible.
     */
    ROBOT
}
