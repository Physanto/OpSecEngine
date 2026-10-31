package io.github.physanto.opsecengine.protocolo;

import java.io.IOException;

/**
 * Error del protocolo: una línea que no es un mensaje válido, un cuerpo que no tiene el formato de
 * su tipo o una línea demasiado larga.
 *
 * <p>Extiende {@link IOException} porque siempre ocurre al leer o escribir mensajes. Después de
 * esta excepción, el {@link CanalMensajes} sigue utilizable: la línea defectuosa se descarta.
 */
public class ExcepcionProtocolo extends IOException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción con un mensaje.
     *
     * @param mensaje explicación del error
     */
    public ExcepcionProtocolo(String mensaje) {
        super(mensaje);
    }

    /**
     * Crea la excepción con un mensaje y la causa original.
     *
     * @param mensaje explicación del error
     * @param causa excepción que la provocó
     */
    public ExcepcionProtocolo(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
