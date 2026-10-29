package io.github.physanto.opsecengine.protocolo.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Criterio de búsqueda de un componente: estrategia y valor.
 *
 * <p>Es inmutable y seguro entre hilos.
 */
public final class CriterioDto {

    private final String estrategia;
    private final String valor;

    /**
     * Crea un criterio.
     *
     * @param estrategia nombre de la estrategia, con los valores de {@code EstrategiaLocalizacion}
     *     del dominio, por ejemplo {@code "TEXTO"} o {@code "ETIQUETA"}; no puede ser null ni estar
     *     vacía
     * @param valor valor buscado; no puede ser null ni estar vacío
     * @throws NullPointerException si algún parámetro es null
     * @throws IllegalArgumentException si algún parámetro está vacío
     */
    @JsonCreator
    public CriterioDto(
            @JsonProperty("estrategia") String estrategia, @JsonProperty("valor") String valor) {
        this.estrategia = Copias.texto(estrategia, "La estrategia del criterio");
        this.valor = Copias.texto(valor, "El valor del criterio");
    }

    /**
     * Devuelve el nombre de la estrategia.
     *
     * @return estrategia; nunca es null
     */
    public String getEstrategia() {
        return estrategia;
    }

    /**
     * Devuelve el valor buscado.
     *
     * @return valor; nunca es null
     */
    public String getValor() {
        return valor;
    }

    /**
     * Compara este criterio con otro objeto campo por campo.
     *
     * @param otro objeto a comparar
     * @return {@code true} si es un criterio con los mismos campos
     */
    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (!(otro instanceof CriterioDto)) {
            return false;
        }
        CriterioDto criterio = (CriterioDto) otro;
        return estrategia.equals(criterio.estrategia) && valor.equals(criterio.valor);
    }

    /**
     * Calcula el hash a partir de todos los campos.
     *
     * @return hash del criterio
     */
    @Override
    public int hashCode() {
        return Objects.hash(estrategia, valor);
    }

    /**
     * Devuelve una representación legible del criterio.
     *
     * @return texto con la estrategia y el valor
     */
    @Override
    public String toString() {
        return "CriterioDto[estrategia=" + estrategia + ", valor=" + valor + "]";
    }
}
