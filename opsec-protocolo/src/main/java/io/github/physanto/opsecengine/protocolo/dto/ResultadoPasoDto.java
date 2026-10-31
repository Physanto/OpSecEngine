package io.github.physanto.opsecengine.protocolo.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Resultado de un paso que el agente envía en un mensaje {@code RESULTADO_PASO}.
 *
 * <p>Es inmutable y seguro entre hilos.
 */
public final class ResultadoPasoDto {

    private final CodigoResultado codigo;
    private final String detalle;
    private final long duracionMs;

    /**
     * Crea un resultado de paso.
     *
     * @param codigo código de resultado; no puede ser null
     * @param detalle explicación del resultado, sobre todo si no es {@code OK}; puede ser null
     * @param duracionMs tiempo que tardó el paso en milisegundos; cero o más
     * @throws NullPointerException si {@code codigo} es null
     * @throws IllegalArgumentException si {@code duracionMs} es negativo
     */
    @JsonCreator
    public ResultadoPasoDto(
            @JsonProperty("codigo") CodigoResultado codigo,
            @JsonProperty("detalle") String detalle,
            @JsonProperty("duracionMs") long duracionMs) {
        this.codigo = Objects.requireNonNull(codigo, "El código del resultado no puede ser null");
        this.detalle = detalle;
        if (duracionMs < 0) {
            throw new IllegalArgumentException(
                    "La duración del paso no puede ser negativa: " + duracionMs);
        }
        this.duracionMs = duracionMs;
    }

    /**
     * Devuelve el código de resultado.
     *
     * @return código; nunca es null
     */
    public CodigoResultado getCodigo() {
        return codigo;
    }

    /**
     * Devuelve la explicación del resultado.
     *
     * @return detalle, o null si no hay
     */
    public String getDetalle() {
        return detalle;
    }

    /**
     * Devuelve el tiempo que tardó el paso.
     *
     * @return milisegundos, cero o más
     */
    public long getDuracionMs() {
        return duracionMs;
    }

    /**
     * Compara este resultado con otro objeto campo por campo.
     *
     * @param otro objeto a comparar
     * @return {@code true} si es un resultado con los mismos campos
     */
    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (!(otro instanceof ResultadoPasoDto)) {
            return false;
        }
        ResultadoPasoDto resultado = (ResultadoPasoDto) otro;
        return duracionMs == resultado.duracionMs
                && codigo == resultado.codigo
                && Objects.equals(detalle, resultado.detalle);
    }

    /**
     * Calcula el hash a partir de todos los campos.
     *
     * @return hash del resultado
     */
    @Override
    public int hashCode() {
        return Objects.hash(codigo, detalle, duracionMs);
    }

    /**
     * Devuelve una representación legible del resultado.
     *
     * @return texto con todos los campos
     */
    @Override
    public String toString() {
        return "ResultadoPasoDto[codigo="
                + codigo
                + ", detalle="
                + detalle
                + ", duracionMs="
                + duracionMs
                + "]";
    }
}
