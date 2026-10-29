package io.github.physanto.opsecengine.protocolo.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Pedido de captura que el motor envía en un mensaje {@code CAPTURAR}.
 *
 * <p>Es inmutable y seguro entre hilos.
 */
public final class SolicitudCapturaDto {

    private final String ventana;

    /**
     * Crea un pedido de captura.
     *
     * @param ventana título o clase de la ventana que se captura; puede ser null para capturar la
     *     ventana activa
     */
    @JsonCreator
    public SolicitudCapturaDto(@JsonProperty("ventana") String ventana) {
        this.ventana = ventana;
    }

    /**
     * Devuelve la ventana que se captura.
     *
     * @return título o clase de la ventana, o null para la ventana activa
     */
    public String getVentana() {
        return ventana;
    }

    /**
     * Compara este pedido con otro objeto.
     *
     * @param otro objeto a comparar
     * @return {@code true} si es un pedido para la misma ventana
     */
    @Override
    public boolean equals(Object otro) {
        return this == otro
                || otro instanceof SolicitudCapturaDto
                        && Objects.equals(ventana, ((SolicitudCapturaDto) otro).ventana);
    }

    /**
     * Calcula el hash a partir de la ventana.
     *
     * @return hash del pedido
     */
    @Override
    public int hashCode() {
        return Objects.hashCode(ventana);
    }

    /**
     * Devuelve una representación legible del pedido.
     *
     * @return texto con la ventana
     */
    @Override
    public String toString() {
        return "SolicitudCapturaDto[ventana=" + ventana + "]";
    }
}
