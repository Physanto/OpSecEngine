package io.github.physanto.opsecengine.protocolo.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Imagen que el agente envía en un mensaje {@code CAPTURA}.
 *
 * <p>Es inmutable y seguro entre hilos. {@link #toString()} no incluye la imagen.
 */
public final class CapturaDto {

    private final String ventana;
    private final String formato;
    private final String base64;

    /**
     * Crea una captura.
     *
     * @param ventana título o clase de la ventana capturada; puede ser null si no se pudo
     *     identificar
     * @param formato formato de la imagen en minúsculas, por ejemplo {@code "png"}; no puede ser
     *     null ni estar vacío
     * @param base64 contenido de la imagen codificado en Base64; no puede ser null ni estar vacío
     * @throws NullPointerException si {@code formato} o {@code base64} son null
     * @throws IllegalArgumentException si {@code formato} o {@code base64} están vacíos
     */
    @JsonCreator
    public CapturaDto(
            @JsonProperty("ventana") String ventana,
            @JsonProperty("formato") String formato,
            @JsonProperty("base64") String base64) {
        this.ventana = ventana;
        this.formato = Copias.texto(formato, "El formato de la captura");
        this.base64 = Copias.texto(base64, "El contenido de la captura");
    }

    /**
     * Devuelve la ventana capturada.
     *
     * @return título o clase de la ventana, o null si no se pudo identificar
     */
    public String getVentana() {
        return ventana;
    }

    /**
     * Devuelve el formato de la imagen.
     *
     * @return formato en minúsculas; nunca es null
     */
    public String getFormato() {
        return formato;
    }

    /**
     * Devuelve el contenido de la imagen.
     *
     * @return imagen codificada en Base64; nunca es null
     */
    public String getBase64() {
        return base64;
    }

    /**
     * Compara esta captura con otro objeto campo por campo.
     *
     * @param otro objeto a comparar
     * @return {@code true} si es una captura con los mismos campos
     */
    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (!(otro instanceof CapturaDto)) {
            return false;
        }
        CapturaDto captura = (CapturaDto) otro;
        return Objects.equals(ventana, captura.ventana)
                && formato.equals(captura.formato)
                && base64.equals(captura.base64);
    }

    /**
     * Calcula el hash a partir de todos los campos.
     *
     * @return hash de la captura
     */
    @Override
    public int hashCode() {
        return Objects.hash(ventana, formato, base64);
    }

    /**
     * Devuelve una representación breve de la captura, sin la imagen.
     *
     * @return texto con la ventana, el formato y el largo del contenido
     */
    @Override
    public String toString() {
        return "CapturaDto[ventana="
                + ventana
                + ", formato="
                + formato
                + ", base64="
                + base64.length()
                + " caracteres]";
    }
}
