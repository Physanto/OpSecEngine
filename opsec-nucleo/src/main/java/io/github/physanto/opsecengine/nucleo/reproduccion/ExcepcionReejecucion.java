package io.github.physanto.opsecengine.nucleo.reproduccion;

/**
 * No se puede repetir una ejecución: faltan sus archivos, no son válidos o las condiciones actuales
 * impiden una re-ejecución exacta.
 */
public class ExcepcionReejecucion extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción.
     *
     * @param mensaje motivo, en español
     */
    public ExcepcionReejecucion(String mensaje) {
        super(mensaje);
    }

    /**
     * Crea la excepción con su causa.
     *
     * @param mensaje motivo, en español
     * @param causa error original
     */
    public ExcepcionReejecucion(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
