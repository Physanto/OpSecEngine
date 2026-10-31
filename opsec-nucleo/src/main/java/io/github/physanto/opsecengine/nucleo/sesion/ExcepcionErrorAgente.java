package io.github.physanto.opsecengine.nucleo.sesion;

import io.github.physanto.opsecengine.protocolo.ExcepcionProtocolo;
import io.github.physanto.opsecengine.protocolo.dto.ErrorDto;
import java.util.Objects;

/**
 * El agente respondió un mensaje {@code ERROR} a un comando del motor.
 *
 * <p>Indica que el agente recibió el comando pero no pudo atenderlo, por ejemplo porque no lo
 * implementa ({@link ErrorDto#NO_IMPLEMENTADO}). El error recibido está en {@link #error()}.
 */
public class ExcepcionErrorAgente extends ExcepcionProtocolo {

    private static final long serialVersionUID = 1L;

    /** Error que respondió el agente. */
    private final transient ErrorDto error;

    /**
     * Crea la excepción.
     *
     * @param error error que respondió el agente; no puede ser null
     * @throws NullPointerException si {@code error} es null
     */
    public ExcepcionErrorAgente(ErrorDto error) {
        super(
                "El agente respondió ERROR "
                        + Objects.requireNonNull(error, "El error no puede ser null").getCodigo()
                        + (error.getMensaje() == null ? "" : ": " + error.getMensaje()));
        this.error = error;
    }

    /**
     * Devuelve el error que respondió el agente.
     *
     * @return el error, con su código y su mensaje
     */
    public ErrorDto error() {
        return error;
    }
}
