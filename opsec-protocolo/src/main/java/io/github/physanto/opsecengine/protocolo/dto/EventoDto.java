package io.github.physanto.opsecengine.protocolo.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.Objects;

/**
 * Evento que el agente envía en un mensaje {@code EVENTO}.
 *
 * <p>El agente no conoce el caso ni el paso en curso: el núcleo agrega ese contexto al traducir el
 * evento al dominio. Es inmutable y seguro entre hilos; los valores de {@code datos} se copian de
 * forma superficial.
 */
public final class EventoDto {

    private final long secuencia;
    private final long instanteEpochMs;
    private final String tipo;
    private final Map<String, Object> datos;

    /**
     * Crea un evento.
     *
     * @param secuencia número de orden que asigna el agente, creciente dentro de la conexión; cero
     *     o más
     * @param instanteEpochMs momento del evento en milisegundos desde 1970-01-01T00:00:00Z
     * @param tipo nombre del tipo, con los valores de {@code TipoEvento} del dominio, por ejemplo
     *     {@code "SQL"}; no puede ser null ni estar vacío
     * @param datos campos propios del tipo, según {@code contratos/catalogos/eventos.yaml}; puede
     *     ser null; las entradas con valor null se descartan y no admite claves null
     * @throws NullPointerException si {@code tipo} es null o si {@code datos} tiene claves null
     * @throws IllegalArgumentException si {@code secuencia} es negativa o si {@code tipo} está
     *     vacío
     */
    @JsonCreator
    public EventoDto(
            @JsonProperty("secuencia") long secuencia,
            @JsonProperty("instanteEpochMs") long instanteEpochMs,
            @JsonProperty("tipo") String tipo,
            @JsonProperty("datos") Map<String, Object> datos) {
        if (secuencia < 0) {
            throw new IllegalArgumentException(
                    "La secuencia del evento no puede ser negativa: " + secuencia);
        }
        this.secuencia = secuencia;
        this.instanteEpochMs = instanteEpochMs;
        this.tipo = Copias.texto(tipo, "El tipo del evento");
        this.datos = Copias.mapaSinNulos(datos, "Los datos del evento");
    }

    /**
     * Devuelve el número de orden del evento.
     *
     * @return secuencia, cero o más
     */
    public long getSecuencia() {
        return secuencia;
    }

    /**
     * Devuelve el momento del evento.
     *
     * @return milisegundos desde 1970-01-01T00:00:00Z
     */
    public long getInstanteEpochMs() {
        return instanteEpochMs;
    }

    /**
     * Devuelve el nombre del tipo de evento.
     *
     * @return tipo; nunca es null
     */
    public String getTipo() {
        return tipo;
    }

    /**
     * Devuelve los campos propios del tipo de evento.
     *
     * @return mapa inmutable en el orden original, sin valores null; nunca es null
     */
    public Map<String, Object> getDatos() {
        return datos;
    }

    /**
     * Compara este evento con otro objeto campo por campo.
     *
     * @param otro objeto a comparar
     * @return {@code true} si es un evento con los mismos campos
     */
    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (!(otro instanceof EventoDto)) {
            return false;
        }
        EventoDto evento = (EventoDto) otro;
        return secuencia == evento.secuencia
                && instanteEpochMs == evento.instanteEpochMs
                && tipo.equals(evento.tipo)
                && datos.equals(evento.datos);
    }

    /**
     * Calcula el hash a partir de todos los campos.
     *
     * @return hash del evento
     */
    @Override
    public int hashCode() {
        return Objects.hash(secuencia, instanteEpochMs, tipo, datos);
    }

    /**
     * Devuelve una representación legible del evento.
     *
     * @return texto con todos los campos
     */
    @Override
    public String toString() {
        return "EventoDto[secuencia="
                + secuencia
                + ", instanteEpochMs="
                + instanteEpochMs
                + ", tipo="
                + tipo
                + ", datos="
                + datos
                + "]";
    }
}
