package io.github.physanto.opsecengine.protocolo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.Objects;

/**
 * Sobre de un mensaje del protocolo: versión, tipo, id, correlación y cuerpo.
 *
 * <p>El cuerpo es un JSON cuyo formato depende del {@link TipoMensaje}; {@link CodecMensajes}
 * permite leerlo como el DTO que corresponde. Es inmutable y seguro entre hilos: el cuerpo se copia
 * al construir el mensaje y cada vez que se lee.
 */
public final class Mensaje {

    /** Versión del protocolo que implementa este módulo. */
    public static final int VERSION_PROTOCOLO = 1;

    private final int version;
    private final TipoMensaje tipo;
    private final String id;
    private final String correlacion;
    private final JsonNode cuerpo;

    /**
     * Crea un mensaje y valida sus datos.
     *
     * @param version versión del protocolo con que se escribió el mensaje; 1 o más
     * @param tipo tipo del mensaje; no puede ser null
     * @param id identificador único del mensaje; no puede ser null ni estar vacío
     * @param correlacion id del mensaje al que responde; puede ser null si no responde a ninguno
     * @param cuerpo contenido del mensaje, que debe ser un objeto JSON; si es null o JSON {@code
     *     null} se usa un objeto vacío
     * @throws IllegalArgumentException si {@code version} es menor que 1, si {@code id} está vacío
     *     o si {@code cuerpo} no es un objeto JSON
     * @throws NullPointerException si {@code tipo} o {@code id} son null
     */
    @JsonCreator
    public Mensaje(
            @JsonProperty("version") int version,
            @JsonProperty("tipo") TipoMensaje tipo,
            @JsonProperty("id") String id,
            @JsonProperty("correlacion") String correlacion,
            @JsonProperty("cuerpo") JsonNode cuerpo) {
        if (version < 1) {
            throw new IllegalArgumentException(
                    "La versión del mensaje debe ser 1 o más: " + version);
        }
        this.version = version;
        this.tipo = Objects.requireNonNull(tipo, "El tipo del mensaje no puede ser null");
        this.id = Objects.requireNonNull(id, "El id del mensaje no puede ser null");
        if (id.trim().isEmpty()) {
            throw new IllegalArgumentException("El id del mensaje no puede estar vacío");
        }
        this.correlacion = correlacion;
        if (cuerpo == null || cuerpo.isNull()) {
            this.cuerpo = JsonNodeFactory.instance.objectNode();
        } else if (cuerpo.isObject()) {
            this.cuerpo = cuerpo.deepCopy();
        } else {
            throw new IllegalArgumentException(
                    "El cuerpo del mensaje debe ser un objeto JSON: " + cuerpo.getNodeType());
        }
    }

    /**
     * Devuelve la versión del protocolo con que se escribió el mensaje.
     *
     * @return versión, 1 o más
     */
    @JsonProperty("version")
    public int getVersion() {
        return version;
    }

    /**
     * Devuelve el tipo del mensaje.
     *
     * @return tipo; nunca es null
     */
    @JsonProperty("tipo")
    public TipoMensaje getTipo() {
        return tipo;
    }

    /**
     * Devuelve el identificador único del mensaje.
     *
     * @return id; nunca es null
     */
    @JsonProperty("id")
    public String getId() {
        return id;
    }

    /**
     * Devuelve el id del mensaje al que responde este.
     *
     * @return id del mensaje original, o null si no responde a ninguno
     */
    @JsonProperty("correlacion")
    public String getCorrelacion() {
        return correlacion;
    }

    /**
     * Devuelve una copia del cuerpo del mensaje.
     *
     * @return copia del cuerpo; nunca es null
     */
    @JsonProperty("cuerpo")
    public JsonNode getCuerpo() {
        return cuerpo.deepCopy();
    }

    /**
     * Compara este mensaje con otro objeto campo por campo.
     *
     * @param otro objeto a comparar
     * @return {@code true} si es un mensaje con los mismos campos
     */
    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (!(otro instanceof Mensaje)) {
            return false;
        }
        Mensaje mensaje = (Mensaje) otro;
        return version == mensaje.version
                && tipo == mensaje.tipo
                && id.equals(mensaje.id)
                && Objects.equals(correlacion, mensaje.correlacion)
                && cuerpo.equals(mensaje.cuerpo);
    }

    /**
     * Calcula el hash a partir de todos los campos.
     *
     * @return hash del mensaje
     */
    @Override
    public int hashCode() {
        return Objects.hash(version, tipo, id, correlacion, cuerpo);
    }

    /**
     * Devuelve una representación breve del mensaje, sin el cuerpo, que puede contener datos
     * sensibles o muy largos.
     *
     * @return texto con la versión, el tipo, el id y la correlación
     */
    @Override
    public String toString() {
        return "Mensaje[version="
                + version
                + ", tipo="
                + tipo
                + ", id="
                + id
                + ", correlacion="
                + correlacion
                + "]";
    }
}
