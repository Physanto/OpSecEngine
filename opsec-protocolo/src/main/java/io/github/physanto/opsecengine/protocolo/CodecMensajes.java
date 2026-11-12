package io.github.physanto.opsecengine.protocolo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * Serializa y deserializa los mensajes del protocolo con Jackson.
 *
 * <p>Formato: JSON Lines, un mensaje por línea, en UTF-8 y con {@link #SEPARADOR} {@code "\n"} en
 * todas las plataformas. Un mensaje serializado nunca contiene saltos de línea, porque JSON los
 * escapa dentro de los textos. Los campos null se omiten al escribir y los campos desconocidos se
 * ignoran al leer, para que versiones distintas del motor y del agente puedan entenderse.
 *
 * <p>Los nodos de ARBOL conservan ruta, alcanzable y acciones. Al leer nodos antiguos, esos campos
 * toman los valores null, false y lista vacía definidos por el DTO.
 *
 * <p>Es inmutable y seguro entre hilos; conviene compartir una sola instancia.
 */
public final class CodecMensajes {

    /** Separador de mensajes: siempre {@code "\n"}, sin importar el sistema operativo. */
    public static final String SEPARADOR = "\n";

    /** Codificación de los mensajes. */
    public static final Charset CODIFICACION = StandardCharsets.UTF_8;

    private final ObjectMapper mapper;

    /** Crea un codec con la configuración del protocolo. */
    public CodecMensajes() {
        this.mapper =
                JsonMapper.builder()
                        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .disable(SerializationFeature.INDENT_OUTPUT)
                        .defaultPropertyInclusion(
                                JsonInclude.Value.construct(
                                        JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
                        .build();
    }

    /**
     * Crea un mensaje nuevo con un id aleatorio y la versión actual del protocolo.
     *
     * @param tipo tipo del mensaje; no puede ser null
     * @param cuerpo DTO del cuerpo, por ejemplo un {@code PasoDto}; puede ser null para un cuerpo
     *     vacío
     * @return mensaje sin correlación, con el cuerpo en la misma forma en que lo leerá el otro
     *     extremo
     * @throws NullPointerException si {@code tipo} es null
     * @throws IllegalArgumentException si {@code cuerpo} no se puede convertir en un objeto JSON
     */
    public Mensaje crear(TipoMensaje tipo, Object cuerpo) {
        return new Mensaje(
                Mensaje.VERSION_PROTOCOLO, tipo, UUID.randomUUID().toString(), null, arbol(cuerpo));
    }

    /**
     * Crea un mensaje que responde a otro: su correlación es el id del original.
     *
     * @param original mensaje al que se responde; no puede ser null
     * @param tipo tipo de la respuesta; no puede ser null
     * @param cuerpo DTO del cuerpo; puede ser null para un cuerpo vacío
     * @return mensaje con un id aleatorio y la correlación del original
     * @throws NullPointerException si {@code original} o {@code tipo} son null
     * @throws IllegalArgumentException si {@code cuerpo} no se puede convertir en un objeto JSON
     */
    public Mensaje responder(Mensaje original, TipoMensaje tipo, Object cuerpo) {
        Objects.requireNonNull(original, "El mensaje original no puede ser null");
        return new Mensaje(
                Mensaje.VERSION_PROTOCOLO,
                tipo,
                UUID.randomUUID().toString(),
                original.getId(),
                arbol(cuerpo));
    }

    /**
     * Convierte un mensaje en una línea JSON, sin el separador final.
     *
     * @param mensaje mensaje a serializar; no puede ser null
     * @return JSON del mensaje en una sola línea
     * @throws NullPointerException si {@code mensaje} es null
     * @throws ExcepcionProtocolo si el mensaje no se puede serializar
     */
    public String serializar(Mensaje mensaje) throws ExcepcionProtocolo {
        Objects.requireNonNull(mensaje, "El mensaje a serializar no puede ser null");
        try {
            return mapper.writeValueAsString(mensaje);
        } catch (JsonProcessingException e) {
            throw new ExcepcionProtocolo("No se pudo serializar el mensaje " + mensaje, e);
        }
    }

    /**
     * Convierte un mensaje en los bytes que se envían por el socket: la línea JSON en UTF-8 seguida
     * de {@link #SEPARADOR}.
     *
     * @param mensaje mensaje a codificar; no puede ser null
     * @return bytes listos para enviar
     * @throws NullPointerException si {@code mensaje} es null
     * @throws ExcepcionProtocolo si el mensaje no se puede serializar
     */
    public byte[] codificarLinea(Mensaje mensaje) throws ExcepcionProtocolo {
        return (serializar(mensaje) + SEPARADOR).getBytes(CODIFICACION);
    }

    /**
     * Lee un mensaje desde su JSON. Acepta JSON en una línea o con formato en varias líneas.
     *
     * @param json texto JSON del mensaje; no puede ser null
     * @return mensaje leído
     * @throws NullPointerException si {@code json} es null
     * @throws ExcepcionProtocolo si el texto no es JSON válido, le faltan campos obligatorios o el
     *     tipo de mensaje es desconocido
     */
    public Mensaje deserializar(String json) throws ExcepcionProtocolo {
        Objects.requireNonNull(json, "El JSON a deserializar no puede ser null");
        try {
            return mapper.readValue(json, Mensaje.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new ExcepcionProtocolo("Mensaje inválido: " + descripcion(e), e);
        }
    }

    /**
     * Lee el cuerpo de un mensaje como un DTO.
     *
     * @param <T> tipo del DTO
     * @param mensaje mensaje cuyo cuerpo se lee; no puede ser null
     * @param tipoCuerpo clase del DTO que corresponde al tipo del mensaje, por ejemplo {@code
     *     PasoDto.class}; no puede ser null
     * @return el cuerpo convertido en el DTO
     * @throws NullPointerException si algún parámetro es null
     * @throws ExcepcionProtocolo si el cuerpo no tiene el formato del DTO
     */
    public <T> T leerCuerpo(Mensaje mensaje, Class<T> tipoCuerpo) throws ExcepcionProtocolo {
        Objects.requireNonNull(mensaje, "El mensaje no puede ser null");
        Objects.requireNonNull(tipoCuerpo, "La clase del cuerpo no puede ser null");
        try {
            return mapper.treeToValue(mensaje.getCuerpo(), tipoCuerpo);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new ExcepcionProtocolo(
                    "El cuerpo del mensaje "
                            + mensaje.getTipo()
                            + " no es un "
                            + tipoCuerpo.getSimpleName()
                            + " válido: "
                            + descripcion(e),
                    e);
        }
    }

    private static String descripcion(Exception e) {
        return e instanceof JsonProcessingException
                ? ((JsonProcessingException) e).getOriginalMessage()
                : e.getMessage();
    }

    private JsonNode arbol(Object cuerpo) {
        if (cuerpo == null) {
            return null;
        }
        // Se pasa por el texto JSON para que el cuerpo quede igual al que leerá el otro extremo
        // (por ejemplo, un long pequeño se lee como entero y no como long).
        try {
            return mapper.readTree(mapper.writeValueAsBytes(cuerpo));
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "El cuerpo no se puede convertir en JSON: " + cuerpo.getClass().getName(), e);
        }
    }
}
