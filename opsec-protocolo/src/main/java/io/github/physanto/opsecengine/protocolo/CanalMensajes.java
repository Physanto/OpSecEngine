package io.github.physanto.opsecengine.protocolo;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Canal de mensajes sobre un socket TCP: envía y recibe {@link Mensaje} en formato JSON Lines.
 *
 * <p><b>Conexión y saludo.</b> El motor abre un {@code ServerSocket} en {@code 127.0.0.1} con un
 * puerto aleatorio, genera un token de sesión y lanza la aplicación con {@code
 * -javaagent:opsec-agente.jar=puerto=<puerto>,token=<token>}. El saludo lo inicia siempre el
 * agente:
 *
 * <ol>
 *   <li>El agente se conecta y envía {@link TipoMensaje#HOLA} como primer mensaje, con el token que
 *       recibió.
 *   <li>El motor comprueba que el token coincida exactamente con el de la sesión (conviene comparar
 *       con {@code MessageDigest.isEqual} para no filtrar información por tiempos) y que la versión
 *       del mensaje sea {@link Mensaje#VERSION_PROTOCOLO}. Si algo no coincide, responde {@link
 *       TipoMensaje#ERROR} con el código {@code TOKEN_INVALIDO} o {@code VERSION_INCOMPATIBLE} y
 *       cierra el canal.
 *   <li>Si el saludo es válido, el motor empieza a enviar comandos. Cada respuesta lleva en su
 *       correlación el id del comando, y los {@link TipoMensaje#EVENTO} pueden llegar en cualquier
 *       momento.
 *   <li>{@link TipoMensaje#CERRAR} termina la sesión de forma ordenada.
 * </ol>
 *
 * <p><b>Hilos.</b> {@link #enviar(Mensaje)} es seguro entre hilos: varios hilos pueden enviar a la
 * vez y cada mensaje se escribe completo. {@link #recibir(Duration)} también está sincronizado,
 * pero lo normal es que un solo hilo lea. {@link #cerrar()} puede llamarse desde cualquier hilo y
 * despierta al hilo que esté esperando un mensaje.
 */
public final class CanalMensajes implements Closeable {

    /** Longitud máxima de una línea en bytes (64 MiB), suficiente para una captura grande. */
    public static final int LONGITUD_MAXIMA_LINEA = 64 * 1024 * 1024;

    private static final int FIN_DE_LINEA = '\n';

    private final Socket socket;
    private final CodecMensajes codec;
    private final InputStream entrada;
    private final OutputStream salida;
    private final Object candadoEnvio = new Object();
    private final Object candadoRecepcion = new Object();
    private final ByteArrayOutputStream lineaPendiente = new ByteArrayOutputStream();
    private boolean descartandoLinea;

    /**
     * Crea un canal sobre un socket ya conectado.
     *
     * @param socket socket conectado; el canal pasa a ser su dueño y lo cierra en {@link
     *     #cerrar()}; no puede ser null
     * @param codec codec de mensajes; no puede ser null
     * @throws NullPointerException si algún parámetro es null
     * @throws IOException si no se pueden obtener los flujos del socket
     */
    public CanalMensajes(Socket socket, CodecMensajes codec) throws IOException {
        this.socket = Objects.requireNonNull(socket, "El socket no puede ser null");
        this.codec = Objects.requireNonNull(codec, "El codec no puede ser null");
        socket.setTcpNoDelay(true);
        this.entrada = new BufferedInputStream(socket.getInputStream());
        this.salida = new BufferedOutputStream(socket.getOutputStream());
    }

    /**
     * Envía un mensaje completo, seguido de {@link CodecMensajes#SEPARADOR}.
     *
     * @param mensaje mensaje a enviar; no puede ser null
     * @throws NullPointerException si {@code mensaje} es null
     * @throws ExcepcionProtocolo si el mensaje no se puede serializar
     * @throws IOException si el socket está cerrado o falla la escritura
     */
    public void enviar(Mensaje mensaje) throws IOException {
        byte[] linea = codec.codificarLinea(mensaje);
        synchronized (candadoEnvio) {
            salida.write(linea);
            salida.flush();
        }
    }

    /**
     * Espera el siguiente mensaje durante un tiempo máximo.
     *
     * <p>Si el tiempo se agota a mitad de una línea, los bytes ya leídos se conservan y la
     * siguiente llamada continúa desde ahí. Las líneas vacías se ignoran.
     *
     * @param tiempoMaximo tiempo máximo de espera en total; debe ser positivo
     * @return el mensaje recibido, o vacío si se agotó el tiempo
     * @throws NullPointerException si {@code tiempoMaximo} es null
     * @throws IllegalArgumentException si {@code tiempoMaximo} no es positivo
     * @throws ExcepcionProtocolo si la línea recibida no es un mensaje válido o supera {@link
     *     #LONGITUD_MAXIMA_LINEA}; la línea se descarta y el canal sigue utilizable
     * @throws EOFException si el otro extremo cerró la conexión
     * @throws IOException si el socket está cerrado o falla la lectura
     */
    public Optional<Mensaje> recibir(Duration tiempoMaximo) throws IOException {
        Objects.requireNonNull(tiempoMaximo, "El tiempo máximo no puede ser null");
        if (tiempoMaximo.isZero() || tiempoMaximo.isNegative()) {
            throw new IllegalArgumentException(
                    "El tiempo máximo debe ser positivo: " + tiempoMaximo);
        }
        long limite = System.nanoTime() + tiempoMaximo.toNanos();
        synchronized (candadoRecepcion) {
            while (true) {
                if (entrada.available() == 0) {
                    long restanteMs = (limite - System.nanoTime()) / 1_000_000L;
                    if (restanteMs <= 0) {
                        return Optional.empty();
                    }
                    socket.setSoTimeout((int) Math.min(restanteMs, Integer.MAX_VALUE));
                }
                int dato;
                try {
                    dato = entrada.read();
                } catch (SocketTimeoutException e) {
                    return Optional.empty();
                }
                if (dato == -1) {
                    throw new EOFException("El otro extremo cerró la conexión");
                }
                if (descartandoLinea) {
                    descartandoLinea = dato != FIN_DE_LINEA;
                } else if (dato == FIN_DE_LINEA) {
                    String linea =
                            new String(lineaPendiente.toByteArray(), CodecMensajes.CODIFICACION);
                    lineaPendiente.reset();
                    if (!linea.trim().isEmpty()) {
                        return Optional.of(codec.deserializar(linea));
                    }
                } else {
                    lineaPendiente.write(dato);
                    if (lineaPendiente.size() > LONGITUD_MAXIMA_LINEA) {
                        lineaPendiente.reset();
                        descartandoLinea = true;
                        throw new ExcepcionProtocolo(
                                "La línea supera el máximo de " + LONGITUD_MAXIMA_LINEA + " bytes");
                    }
                }
            }
        }
    }

    /**
     * Cierra el canal y su socket. Llamarlo más de una vez no tiene efecto.
     *
     * @throws IOException si falla el cierre del socket
     */
    public void cerrar() throws IOException {
        socket.close();
    }

    /**
     * Igual que {@link #cerrar()}, para usar el canal en un {@code try} con recursos.
     *
     * @throws IOException si falla el cierre del socket
     */
    @Override
    public void close() throws IOException {
        cerrar();
    }
}
