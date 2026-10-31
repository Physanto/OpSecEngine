package io.github.physanto.opsecengine.protocolo.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Error que se envía en un mensaje {@code ERROR} cuando otro mensaje no se pudo atender.
 *
 * <p>Es inmutable y seguro entre hilos.
 */
public final class ErrorDto {

    /** El token del saludo no coincide con el de la sesión. */
    public static final String TOKEN_INVALIDO = "TOKEN_INVALIDO";

    /** La versión del protocolo del mensaje no es compatible. */
    public static final String VERSION_INCOMPATIBLE = "VERSION_INCOMPATIBLE";

    /** El mensaje no es JSON válido o su cuerpo no tiene el formato de su tipo. */
    public static final String MENSAJE_INVALIDO = "MENSAJE_INVALIDO";

    /** El receptor no sabe atender ese tipo de mensaje. */
    public static final String TIPO_NO_SOPORTADO = "TIPO_NO_SOPORTADO";

    /**
     * El receptor reconoce el tipo de mensaje pero todavía no lo implementa, por ejemplo un agente
     * en construcción.
     */
    public static final String NO_IMPLEMENTADO = "NO_IMPLEMENTADO";

    /** Ocurrió un error inesperado en el receptor. */
    public static final String ERROR_INTERNO = "ERROR_INTERNO";

    private final String codigo;
    private final String mensaje;

    /**
     * Crea un error.
     *
     * @param codigo código del error, de preferencia una de las constantes de esta clase; no puede
     *     ser null ni estar vacío
     * @param mensaje explicación legible del error; puede ser null
     * @throws NullPointerException si {@code codigo} es null
     * @throws IllegalArgumentException si {@code codigo} está vacío
     */
    @JsonCreator
    public ErrorDto(
            @JsonProperty("codigo") String codigo, @JsonProperty("mensaje") String mensaje) {
        this.codigo = Copias.texto(codigo, "El código del error");
        this.mensaje = mensaje;
    }

    /**
     * Devuelve el código del error.
     *
     * @return código; nunca es null
     */
    public String getCodigo() {
        return codigo;
    }

    /**
     * Devuelve la explicación del error.
     *
     * @return explicación, o null si no hay
     */
    public String getMensaje() {
        return mensaje;
    }

    /**
     * Compara este error con otro objeto campo por campo.
     *
     * @param otro objeto a comparar
     * @return {@code true} si es un error con los mismos campos
     */
    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (!(otro instanceof ErrorDto)) {
            return false;
        }
        ErrorDto error = (ErrorDto) otro;
        return codigo.equals(error.codigo) && Objects.equals(mensaje, error.mensaje);
    }

    /**
     * Calcula el hash a partir de todos los campos.
     *
     * @return hash del error
     */
    @Override
    public int hashCode() {
        return Objects.hash(codigo, mensaje);
    }

    /**
     * Devuelve una representación legible del error.
     *
     * @return texto con el código y la explicación
     */
    @Override
    public String toString() {
        return "ErrorDto[codigo=" + codigo + ", mensaje=" + mensaje + "]";
    }
}
