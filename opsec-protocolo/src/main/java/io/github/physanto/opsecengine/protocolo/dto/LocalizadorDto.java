package io.github.physanto.opsecengine.protocolo.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Localizador de un componente: criterios ordenados por prioridad, al menos uno.
 *
 * <p>El agente prueba los criterios en orden y usa el primero que encuentra un componente. Es
 * inmutable y seguro entre hilos.
 */
public final class LocalizadorDto {

    private final List<CriterioDto> criterios;

    /**
     * Crea un localizador con una copia inmutable de los criterios.
     *
     * @param criterios criterios de mayor a menor prioridad; al menos uno y sin elementos null
     * @throws IllegalArgumentException si {@code criterios} es null o está vacía
     * @throws NullPointerException si algún criterio es null
     */
    @JsonCreator
    public LocalizadorDto(@JsonProperty("criterios") List<CriterioDto> criterios) {
        this.criterios = Copias.lista(criterios, "Los criterios del localizador");
        if (this.criterios.isEmpty()) {
            throw new IllegalArgumentException("El localizador necesita al menos un criterio");
        }
    }

    /**
     * Devuelve los criterios en orden de prioridad.
     *
     * @return lista inmutable con al menos un criterio
     */
    public List<CriterioDto> getCriterios() {
        return criterios;
    }

    /**
     * Compara este localizador con otro objeto.
     *
     * @param otro objeto a comparar
     * @return {@code true} si es un localizador con los mismos criterios en el mismo orden
     */
    @Override
    public boolean equals(Object otro) {
        return this == otro
                || otro instanceof LocalizadorDto
                        && criterios.equals(((LocalizadorDto) otro).criterios);
    }

    /**
     * Calcula el hash a partir de los criterios.
     *
     * @return hash del localizador
     */
    @Override
    public int hashCode() {
        return criterios.hashCode();
    }

    /**
     * Devuelve una representación legible del localizador.
     *
     * @return texto con los criterios
     */
    @Override
    public String toString() {
        return "LocalizadorDto" + criterios;
    }
}
