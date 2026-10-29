package io.github.physanto.opsecengine.protocolo.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Paso que el motor pide ejecutar al agente en un mensaje {@code EJECUTAR_PASO}.
 *
 * <p>Es inmutable y seguro entre hilos.
 */
public final class PasoDto {

    private final String accion;
    private final LocalizadorDto localizador;
    private final String valor;
    private final String modo;
    private final long esperaMaximaMs;

    /**
     * Crea un paso.
     *
     * @param accion nombre de la acción, con los valores de {@code Accion} del dominio, por ejemplo
     *     {@code "PULSAR"}; no puede ser null ni estar vacía
     * @param localizador componente o ventana sobre la que se actúa; puede ser null en las acciones
     *     que no lo necesitan, como {@code ESPERAR} y {@code CAPTURAR}
     * @param valor dato que usa la acción, por ejemplo el texto a escribir; puede ser null
     * @param modo mecanismo de ejecución: {@code "SEMANTICO"}, {@code "EVENTOS"} o {@code "ROBOT"};
     *     no puede ser null ni estar vacío
     * @param esperaMaximaMs tiempo máximo del paso en milisegundos; mayor que cero
     * @throws NullPointerException si {@code accion} o {@code modo} son null
     * @throws IllegalArgumentException si {@code accion} o {@code modo} están vacíos, o si {@code
     *     esperaMaximaMs} no es positivo
     */
    @JsonCreator
    public PasoDto(
            @JsonProperty("accion") String accion,
            @JsonProperty("localizador") LocalizadorDto localizador,
            @JsonProperty("valor") String valor,
            @JsonProperty("modo") String modo,
            @JsonProperty("esperaMaximaMs") long esperaMaximaMs) {
        this.accion = Copias.texto(accion, "La acción del paso");
        this.localizador = localizador;
        this.valor = valor;
        this.modo = Copias.texto(modo, "El modo del paso");
        if (esperaMaximaMs <= 0) {
            throw new IllegalArgumentException(
                    "La espera máxima del paso debe ser positiva: " + esperaMaximaMs);
        }
        this.esperaMaximaMs = esperaMaximaMs;
    }

    /**
     * Devuelve el nombre de la acción.
     *
     * @return acción; nunca es null
     */
    public String getAccion() {
        return accion;
    }

    /**
     * Devuelve el localizador del componente o la ventana.
     *
     * @return localizador, o null si la acción no lo necesita
     */
    public LocalizadorDto getLocalizador() {
        return localizador;
    }

    /**
     * Devuelve el dato que usa la acción.
     *
     * @return valor, o null si la acción no lo necesita
     */
    public String getValor() {
        return valor;
    }

    /**
     * Devuelve el mecanismo de ejecución.
     *
     * @return modo; nunca es null
     */
    public String getModo() {
        return modo;
    }

    /**
     * Devuelve el tiempo máximo del paso.
     *
     * @return milisegundos, mayor que cero
     */
    public long getEsperaMaximaMs() {
        return esperaMaximaMs;
    }

    /**
     * Compara este paso con otro objeto campo por campo.
     *
     * @param otro objeto a comparar
     * @return {@code true} si es un paso con los mismos campos
     */
    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (!(otro instanceof PasoDto)) {
            return false;
        }
        PasoDto paso = (PasoDto) otro;
        return esperaMaximaMs == paso.esperaMaximaMs
                && accion.equals(paso.accion)
                && Objects.equals(localizador, paso.localizador)
                && Objects.equals(valor, paso.valor)
                && modo.equals(paso.modo);
    }

    /**
     * Calcula el hash a partir de todos los campos.
     *
     * @return hash del paso
     */
    @Override
    public int hashCode() {
        return Objects.hash(accion, localizador, valor, modo, esperaMaximaMs);
    }

    /**
     * Devuelve una representación legible del paso.
     *
     * @return texto con todos los campos
     */
    @Override
    public String toString() {
        return "PasoDto[accion="
                + accion
                + ", localizador="
                + localizador
                + ", valor="
                + valor
                + ", modo="
                + modo
                + ", esperaMaximaMs="
                + esperaMaximaMs
                + "]";
    }
}
