package io.github.physanto.opsecengine.soporte;

import io.github.physanto.opsecengine.protocolo.CanalMensajes;
import io.github.physanto.opsecengine.protocolo.CodecMensajes;
import io.github.physanto.opsecengine.protocolo.ExcepcionProtocolo;
import io.github.physanto.opsecengine.protocolo.Mensaje;
import io.github.physanto.opsecengine.protocolo.TipoMensaje;
import io.github.physanto.opsecengine.protocolo.dto.ArbolDto;
import io.github.physanto.opsecengine.protocolo.dto.CapturaDto;
import io.github.physanto.opsecengine.protocolo.dto.ErrorDto;
import io.github.physanto.opsecengine.protocolo.dto.EventoDto;
import io.github.physanto.opsecengine.protocolo.dto.HolaDto;
import io.github.physanto.opsecengine.protocolo.dto.PasoDto;
import io.github.physanto.opsecengine.protocolo.dto.ResultadoPasoDto;
import io.github.physanto.opsecengine.protocolo.dto.SolicitudCapturaDto;
import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Motor falso: el lado del motor del protocolo, mínimo, para probar un agente sin el núcleo.
 *
 * <p>Abre un {@code ServerSocket} en un puerto libre de {@code 127.0.0.1} y genera un token
 * aleatorio. {@link #esperarHola(Duration)} acepta la conexión del agente y valida su saludo; a
 * partir de ahí un hilo propio lee todo lo que llega: las respuestas despiertan al comando que las
 * espera y los {@code EVENTO} se acumulan en {@link #eventos()}. Como el agente envía los eventos
 * de un paso antes de su resultado, cuando {@link #enviarPaso(PasoDto)} vuelve, esos eventos ya
 * están registrados.
 *
 * <p>Ejemplo con el agente real lanzado en otro proceso:
 *
 * <pre>{@code
 * try (MotorFalso motor = new MotorFalso()) {
 *     Process app = new ProcessBuilder(java, "-javaagent:" + jarAgente + "="
 *             + motor.argumentosAgente(), "-jar", jarApp).start();
 *     HolaDto hola = motor.esperarHola(Duration.ofSeconds(30));
 *     ResultadoPasoDto resultado = motor.enviarPaso(paso);
 *     List<EventoDto> eventos = motor.eventos();
 *     motor.cerrar();
 * }
 * }</pre>
 *
 * <p>Los comandos se pueden enviar desde varios hilos. Si el agente responde {@code ERROR}, el
 * comando lanza {@link ExcepcionErrorRemoto}.
 */
public final class MotorFalso implements AutoCloseable {

    /** Tiempo máximo por defecto para esperar una respuesta del agente. */
    public static final Duration TIEMPO_MAXIMO_POR_DEFECTO = Duration.ofSeconds(10);

    private static final Logger REGISTRO = Logger.getLogger(MotorFalso.class.getName());
    private static final Duration ESPERA_LECTURA = Duration.ofMillis(250);
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final CodecMensajes codec = new CodecMensajes();
    private final ServerSocket servidor;
    private final String token;
    private final Map<String, CompletableFuture<Mensaje>> pendientes = new ConcurrentHashMap<>();
    private final List<Mensaje> recibidos = new CopyOnWriteArrayList<>();
    private final List<EventoDto> eventos = new CopyOnWriteArrayList<>();
    private final CountDownLatch desconexion = new CountDownLatch(1);
    private volatile Duration tiempoMaximo = TIEMPO_MAXIMO_POR_DEFECTO;
    private volatile CanalMensajes canal;
    private volatile Thread lector;
    private volatile boolean cerrado;

    /**
     * Abre el servidor en un puerto libre de {@code 127.0.0.1} con un token aleatorio.
     *
     * @throws IOException si no se puede abrir el servidor
     */
    public MotorFalso() throws IOException {
        this(tokenAleatorio());
    }

    /**
     * Abre el servidor en un puerto libre de {@code 127.0.0.1} con un token dado.
     *
     * @param token token de la sesión que debe enviar el agente; no puede ser null ni estar vacío
     * @throws IOException si no se puede abrir el servidor
     * @throws NullPointerException si {@code token} es null
     * @throws IllegalArgumentException si {@code token} está vacío
     */
    public MotorFalso(String token) throws IOException {
        this.token = Objects.requireNonNull(token, "El token no puede ser null");
        if (token.isBlank()) {
            throw new IllegalArgumentException("El token no puede estar vacío");
        }
        this.servidor = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
    }

    /**
     * Devuelve el puerto en el que escucha el motor falso.
     *
     * @return puerto local
     */
    public int puerto() {
        return servidor.getLocalPort();
    }

    /**
     * Devuelve el token que el agente debe enviar en el {@code HOLA}.
     *
     * @return token de la sesión
     */
    public String token() {
        return token;
    }

    /**
     * Devuelve los argumentos que se pasan al agente después de {@code -javaagent:<jar>=}.
     *
     * @return texto con el formato {@code puerto=<puerto>,token=<token>}
     */
    public String argumentosAgente() {
        return "puerto=" + puerto() + ",token=" + token;
    }

    /**
     * Cambia el tiempo máximo de espera de las respuestas; en {@link #enviarPaso(PasoDto)} se suma
     * la espera máxima del paso.
     *
     * @param tiempoMaximo tiempo máximo; debe ser positivo
     * @return este motor, para encadenar llamadas
     * @throws NullPointerException si {@code tiempoMaximo} es null
     * @throws IllegalArgumentException si no es positivo
     */
    public MotorFalso tiempoMaximoRespuesta(Duration tiempoMaximo) {
        Objects.requireNonNull(tiempoMaximo, "El tiempo máximo no puede ser null");
        if (tiempoMaximo.isZero() || tiempoMaximo.isNegative()) {
            throw new IllegalArgumentException("El tiempo máximo debe ser positivo");
        }
        this.tiempoMaximo = tiempoMaximo;
        return this;
    }

    /**
     * Espera la conexión del agente y valida su saludo.
     *
     * <p>El primer mensaje debe ser {@code HOLA}, con la versión {@link Mensaje#VERSION_PROTOCOLO}
     * y el token de la sesión (comparado en tiempo constante). Si algo no coincide, responde {@code
     * ERROR} con {@code MENSAJE_INVALIDO}, {@code VERSION_INCOMPATIBLE} o {@code TOKEN_INVALIDO},
     * cierra la conexión y lanza {@link ExcepcionProtocolo}. Si el saludo es válido, no responde
     * nada y empieza a leer en un hilo propio. Solo se puede llamar una vez.
     *
     * @param espera tiempo máximo para la conexión y el saludo juntos; no puede ser null
     * @return el saludo recibido
     * @throws SocketTimeoutException si el agente no se conecta a tiempo
     * @throws ExcepcionProtocolo si el saludo no llega a tiempo o no es válido
     * @throws IOException si falla la conexión
     * @throws IllegalStateException si ya se recibió un saludo
     */
    public synchronized HolaDto esperarHola(Duration espera) throws IOException {
        if (canal != null) {
            throw new IllegalStateException("El motor falso ya recibió un saludo");
        }
        Objects.requireNonNull(espera, "La espera no puede ser null");
        long limite = System.nanoTime() + espera.toNanos();
        servidor.setSoTimeout((int) Math.max(1, espera.toMillis()));
        Socket socket = servidor.accept();
        CanalMensajes nuevo = new CanalMensajes(socket, codec);
        try {
            Duration restante = Duration.ofNanos(Math.max(1, limite - System.nanoTime()));
            Mensaje mensaje =
                    nuevo.recibir(restante)
                            .orElseThrow(() ->
                                    new ExcepcionProtocolo(
                                            "El agente se conectó pero no envió HOLA a"
                                                    + " tiempo"));
            recibidos.add(mensaje);
            HolaDto hola = validarSaludo(nuevo, mensaje);
            canal = nuevo;
            Thread hilo = new Thread(this::leer, "motor-falso");
            hilo.setDaemon(true);
            lector = hilo;
            hilo.start();
            return hola;
        } catch (IOException | RuntimeException e) {
            nuevo.cerrar();
            throw e;
        }
    }

    /**
     * Envía un paso y espera su resultado.
     *
     * @param paso paso a ejecutar; no puede ser null
     * @return el resultado que respondió el agente
     * @throws ExcepcionErrorRemoto si el agente responde {@code ERROR}
     * @throws ExcepcionProtocolo si no hay respuesta a tiempo o es de otro tipo
     * @throws IOException si falla la conexión
     * @throws IllegalStateException si todavía no se recibió el saludo
     */
    public ResultadoPasoDto enviarPaso(PasoDto paso) throws IOException {
        Objects.requireNonNull(paso, "El paso no puede ser null");
        Duration espera = tiempoMaximo.plusMillis(paso.getEsperaMaximaMs());
        return solicitar(
                TipoMensaje.EJECUTAR_PASO,
                paso,
                TipoMensaje.RESULTADO_PASO,
                ResultadoPasoDto.class,
                espera);
    }

    /**
     * Pide el árbol de componentes de las ventanas visibles.
     *
     * @return el árbol que respondió el agente
     * @throws ExcepcionErrorRemoto si el agente responde {@code ERROR}
     * @throws ExcepcionProtocolo si no hay respuesta a tiempo o es de otro tipo
     * @throws IOException si falla la conexión
     * @throws IllegalStateException si todavía no se recibió el saludo
     */
    public ArbolDto pedirArbol() throws IOException {
        return solicitar(
                TipoMensaje.ARBOL_SOLICITUD, null, TipoMensaje.ARBOL, ArbolDto.class, tiempoMaximo);
    }

    /**
     * Pide una captura de una ventana.
     *
     * @param ventana id de la ventana; null para la ventana activa
     * @return la captura que respondió el agente
     * @throws ExcepcionErrorRemoto si el agente responde {@code ERROR}
     * @throws ExcepcionProtocolo si no hay respuesta a tiempo o es de otro tipo
     * @throws IOException si falla la conexión
     * @throws IllegalStateException si todavía no se recibió el saludo
     */
    public CapturaDto pedirCaptura(String ventana) throws IOException {
        return solicitar(
                TipoMensaje.CAPTURAR,
                new SolicitudCapturaDto(ventana),
                TipoMensaje.CAPTURA,
                CapturaDto.class,
                tiempoMaximo);
    }

    /**
     * Envía {@code CERRAR}, espera a que el agente cierre la conexión y luego libera todo.
     *
     * @return true si el agente cerró la conexión antes del tiempo máximo de respuesta
     * @throws IOException si no se puede enviar {@code CERRAR}
     * @throws IllegalStateException si todavía no se recibió el saludo
     */
    public boolean cerrar() throws IOException {
        canalConectado().enviar(codec.crear(TipoMensaje.CERRAR, null));
        boolean cerroElAgente;
        try {
            cerroElAgente = desconexion.await(tiempoMaximo.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Se interrumpió la espera del cierre del agente");
        } finally {
            close();
        }
        return cerroElAgente;
    }

    /**
     * Devuelve los eventos recibidos hasta ahora, en orden de llegada.
     *
     * @return copia inmutable de los eventos
     */
    public List<EventoDto> eventos() {
        return List.copyOf(eventos);
    }

    /**
     * Devuelve todos los mensajes recibidos hasta ahora, incluido el {@code HOLA}, en orden de
     * llegada.
     *
     * @return copia inmutable de los mensajes
     */
    public List<Mensaje> mensajesRecibidos() {
        return List.copyOf(recibidos);
    }

    /** Cierra la conexión y el servidor sin enviar {@code CERRAR}. Se puede llamar varias veces. */
    @Override
    public void close() {
        cerrado = true;
        try {
            servidor.close();
        } catch (IOException e) {
            REGISTRO.log(Level.FINE, "Error al cerrar el servidor del motor falso", e);
        }
        CanalMensajes actual = canal;
        if (actual != null) {
            try {
                actual.cerrar();
            } catch (IOException e) {
                REGISTRO.log(Level.FINE, "Error al cerrar el canal del motor falso", e);
            }
        }
        Thread hilo = lector;
        if (hilo != null && hilo != Thread.currentThread()) {
            try {
                hilo.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private HolaDto validarSaludo(CanalMensajes nuevo, Mensaje mensaje) throws IOException {
        if (mensaje.getTipo() != TipoMensaje.HOLA) {
            rechazar(
                    nuevo,
                    mensaje,
                    ErrorDto.MENSAJE_INVALIDO,
                    "El primer mensaje debe ser HOLA y llegó " + mensaje.getTipo());
        }
        if (mensaje.getVersion() != Mensaje.VERSION_PROTOCOLO) {
            rechazar(
                    nuevo,
                    mensaje,
                    ErrorDto.VERSION_INCOMPATIBLE,
                    "Se esperaba la versión "
                            + Mensaje.VERSION_PROTOCOLO
                            + " del protocolo y llegó "
                            + mensaje.getVersion());
        }
        HolaDto hola = codec.leerCuerpo(mensaje, HolaDto.class);
        boolean tokenValido =
                MessageDigest.isEqual(
                        hola.getToken().getBytes(StandardCharsets.UTF_8),
                        token.getBytes(StandardCharsets.UTF_8));
        if (!tokenValido) {
            rechazar(
                    nuevo,
                    mensaje,
                    ErrorDto.TOKEN_INVALIDO,
                    "El token no coincide con el de la sesión");
        }
        return hola;
    }

    private void rechazar(CanalMensajes nuevo, Mensaje mensaje, String codigo, String motivo)
            throws IOException {
        nuevo.enviar(codec.responder(mensaje, TipoMensaje.ERROR, new ErrorDto(codigo, motivo)));
        throw new ExcepcionProtocolo("Saludo rechazado (" + codigo + "): " + motivo);
    }

    private <T> T solicitar(
            TipoMensaje tipo,
            Object cuerpo,
            TipoMensaje tipoRespuesta,
            Class<T> claseRespuesta,
            Duration espera)
            throws IOException {
        Mensaje solicitud = codec.crear(tipo, cuerpo);
        CompletableFuture<Mensaje> futura = new CompletableFuture<>();
        pendientes.put(solicitud.getId(), futura);
        if (desconexion.getCount() == 0) {
            futura.completeExceptionally(new EOFException("El agente ya cerró la conexión"));
        }
        Mensaje respuesta;
        try {
            canalConectado().enviar(solicitud);
            respuesta = futura.get(espera.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new ExcepcionProtocolo(
                    "El agente no respondió " + tipo + " en " + espera.toMillis() + " ms");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Se interrumpió la espera de la respuesta a " + tipo);
        } catch (ExecutionException e) {
            throw new ExcepcionProtocolo(
                    "La conexión terminó antes de la respuesta a " + tipo, e.getCause());
        } finally {
            pendientes.remove(solicitud.getId());
        }
        if (respuesta.getTipo() == TipoMensaje.ERROR) {
            throw new ExcepcionErrorRemoto(codec.leerCuerpo(respuesta, ErrorDto.class));
        }
        if (respuesta.getTipo() != tipoRespuesta) {
            throw new ExcepcionProtocolo(
                    "Se esperaba " + tipoRespuesta + " y llegó " + respuesta.getTipo());
        }
        return codec.leerCuerpo(respuesta, claseRespuesta);
    }

    private CanalMensajes canalConectado() {
        CanalMensajes actual = canal;
        if (actual == null) {
            throw new IllegalStateException(
                    "El agente todavía no se conectó; llame antes a esperarHola");
        }
        return actual;
    }

    private void leer() {
        IOException causa = new EOFException("El agente cerró la conexión");
        try {
            while (!cerrado) {
                try {
                    Optional<Mensaje> recibido = canal.recibir(ESPERA_LECTURA);
                    if (recibido.isPresent()) {
                        atender(recibido.get());
                    }
                } catch (ExcepcionProtocolo e) {
                    REGISTRO.log(Level.WARNING, "El motor falso recibió un mensaje inválido", e);
                }
            }
        } catch (EOFException e) {
            REGISTRO.fine("El agente cerró la conexión con el motor falso");
        } catch (IOException e) {
            causa = e;
            if (!cerrado) {
                REGISTRO.log(Level.FINE, "La conexión del motor falso terminó", e);
            }
        } finally {
            desconexion.countDown();
            for (CompletableFuture<Mensaje> futura : pendientes.values()) {
                futura.completeExceptionally(causa);
            }
        }
    }

    private void atender(Mensaje mensaje) throws ExcepcionProtocolo {
        recibidos.add(mensaje);
        if (mensaje.getTipo() == TipoMensaje.EVENTO) {
            eventos.add(codec.leerCuerpo(mensaje, EventoDto.class));
            return;
        }
        if (mensaje.getCorrelacion() != null) {
            CompletableFuture<Mensaje> futura = pendientes.get(mensaje.getCorrelacion());
            if (futura != null) {
                futura.complete(mensaje);
                return;
            }
        }
        REGISTRO.fine(() -> "El motor falso recibió un mensaje que nadie esperaba: " + mensaje);
    }

    private static String tokenAleatorio() {
        byte[] bytes = new byte[24];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
