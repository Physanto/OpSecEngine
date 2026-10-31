package io.github.physanto.opsecengine.soporte;

import io.github.physanto.opsecengine.protocolo.ExcepcionProtocolo;
import io.github.physanto.opsecengine.protocolo.dto.ErrorDto;
import java.util.Objects;

/**
 * El otro extremo respondió un mensaje {@code ERROR} en lugar de la respuesta esperada.
 *
 * <p>La lanza {@link MotorFalso} cuando el agente no puede atender un comando. El {@link ErrorDto}
 * recibido queda disponible para hacer aserciones sobre su código:
 *
 * <pre>{@code
 * ExcepcionErrorRemoto error =
 *         assertThrows(ExcepcionErrorRemoto.class, () -> motor.pedirArbol());
 * assertEquals(ErrorDto.NO_IMPLEMENTADO, error.getError().getCodigo());
 * }</pre>
 */
public class ExcepcionErrorRemoto extends ExcepcionProtocolo {

    private static final long serialVersionUID = 1L;

    /** Error recibido; {@code ErrorDto} no es serializable, así que no viaja con la excepción. */
    private final transient ErrorDto error;

    /**
     * Crea la excepción con el error recibido.
     *
     * @param error cuerpo del mensaje {@code ERROR}; no puede ser null
     * @throws NullPointerException si {@code error} es null
     */
    public ExcepcionErrorRemoto(ErrorDto error) {
        super(
                "El otro extremo respondió ERROR "
                        + Objects.requireNonNull(error, "El error no puede ser null").getCodigo()
                        + ": "
                        + error.getMensaje());
        this.error = error;
    }

    /**
     * Devuelve el error recibido.
     *
     * @return el cuerpo del mensaje {@code ERROR}; null solo si la excepción se deserializó
     */
    public ErrorDto getError() {
        return error;
    }
}
