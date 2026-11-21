package io.github.physanto.opsecengine.nucleo.sesion;

import io.github.physanto.opsecengine.protocolo.CanalMensajes;
import io.github.physanto.opsecengine.protocolo.CodecMensajes;
import io.github.physanto.opsecengine.protocolo.ExcepcionProtocolo;
import io.github.physanto.opsecengine.protocolo.Mensaje;
import io.github.physanto.opsecengine.protocolo.TipoMensaje;
import io.github.physanto.opsecengine.protocolo.dto.ArbolDto;
import io.github.physanto.opsecengine.protocolo.dto.CapturaDto;
import io.github.physanto.opsecengine.protocolo.dto.ConfigGanchosDto;
import io.github.physanto.opsecengine.protocolo.dto.ErrorDto;
import io.github.physanto.opsecengine.protocolo.dto.EventoDto;
import io.github.physanto.opsecengine.protocolo.dto.HolaDto;
import io.github.physanto.opsecengine.protocolo.dto.ModoInteraccionDto;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lado del motor del protocolo: espera la conexión del agente y le envía comandos.
 *
 * <p>Al crearlo abre un {@code ServerSocket} en un puerto libre de {@code 127.0.0.1} y genera un
 * token aleatorio; el puerto y el token se le pasan al agente al lanzar la aplicación. {@link
 * #esperarHola(Duration)} acepta la conexión y valida el saludo; a partir de ahí un hilo propio lee
 * todo lo que llega: las respuestas despiertan al comando que las espera y los {@code EVENTO} se
 * acumulan hasta que se piden con {@link #tomarEventos()}. Como el agente envía los eventos de un
 * paso antes de su resultado, cuando {@link #enviarPaso(PasoDto)} vuelve, esos eventos ya están
 * acumulados.
 *
 * <p>Ejemplo:
 *
 * <pre>{@code
 * try (ClienteAgente cliente = new ClienteAgente()) {
 *     lanzar(app, cliente.puerto(), cliente.token());
 *     cliente.esperarHola(Duration.ofSeconds(30));
 *     ResultadoPasoDto resultado = cliente.enviarPaso(paso);
 *     List<EventoDto> eventos = cliente.tomarEventos();
 *     cliente.cerrar();
 * }
 * }</pre>
 *
 * <p>Los comandos se pueden enviar desde varios hilos. Si el agente responde {@code ERROR}, el
 * comando lanza {@link ExcepcionErrorAgente}.
 */
public final class ClienteAgente implements AutoCloseable {

    /** Tiempo máximo por defecto para esperar una respuesta del agente. */
    public static final Duration TIEMPO_MAXIMO_POR_DEFECTO = Duration.ofSeconds(10);

    private static final Logger REGISTRO = Logger.getLogger(ClienteAgente.class.getName());
    private static final Duration ESPERA_LECTURA = Duration.ofMillis(250);
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final CodecMensajes codec = new CodecMensajes();
    private final ServerSocket servidor;
    private final String token;
    private final Duration tiempoMaximo;
    private final Map<String, CompletableFuture<Mensaje>> pendientes = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<EventoDto> eventos = new ConcurrentLinkedQueue<>();
    private final CountDownLatch desconexion = new CountDownLatch(1);
    private volatile CanalMensajes canal;
    private volatile Thread lector;
    private volatile boolean cerrado;

    /**
     * Abre el servidor con el tiempo máximo de respuesta por defecto.
     *
     * @throws IOException si no se puede abrir el servidor
     */
    public ClienteAgente() throws IOException {
        this(TIEMPO_MAXIMO_POR_DEFECTO);
    }

    /**
     * Abre el servidor en un puerto libre de {@code 127.0.0.1} con un token aleatorio.
     *
     * @param tiempoMaximo tiempo máximo para esperar cada respuesta; en {@link
     *     #enviarPaso(PasoDto)} se le suma la espera del paso; debe ser positivo
     * @throws IOException si no se puede abrir el servidor
     * @throws NullPointerException si {@code tiempoMaximo} es null
     * @throws IllegalArgumentException si {@code tiempoMaximo} no es positivo
     */
    public ClienteAgente(Duration tiempoMaximo) throws IOException {
        Objects.requireNonNull(tiempoMaximo, "El tiempo máximo no puede ser null");
        if (tiempoMaximo.isZero() || tiempoMaximo.isNegative()) {
            throw new IllegalArgumentException("El tiempo máximo debe ser positivo");
        }
        this.tiempoMaximo = tiempoMaximo;
        this.token = tokenAleatorio();
        this.servidor = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
    }

    /**
     * Devuelve el puerto en el que espera al agente.
     *
     * @return puerto local de {@code 127.0.0.1}
     */
    public int puerto() {
        return servidor.getLocalPort();
    }

    /**
     * Devuelve el token que el agente debe enviar en el {@code HOLA}.
     *
     * @return token aleatorio de la sesión
     */
    public String token() {
        return token;
    }

    /**
     * Espera la conexión del agente y valida su saludo.
     *
     * <p>El primer mensaje debe ser {@code HOLA}, con la versión {@link Mensaje#VERSION_PROTOCOLO}
     * y el token de la sesión (comparado en tiempo constante). Si algo no coincide, responde {@code
     * ERROR} con {@code MENSAJE_INVALIDO}, {@code VERSION_INCOMPATIBLE} o {@code TOKEN_INVALIDO},
     * cierra la conexión y lanza {@link ExcepcionProtocolo}. Si el saludo es válido no responde
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
            throw new IllegalStateException("El cliente del agente ya recibió un saludo");
        }
        Objects.requireNonNull(espera, "La espera no puede ser null");
        long limite = System.nanoTime() + espera.toNanos();
        servidor.setSoTimeout((int) Math.max(1, espera.toMillis()));
        Socket socket;
        try {
            socket = servidor.accept();
        } catch (SocketTimeoutException e) {
            SocketTimeoutException sinConexion =
                    new SocketTimeoutException(
                            "El agente no se conectó en " + espera.toMillis() + " ms");
            sinConexion.initCause(e);
            throw sinConexion;
        }
        CanalMensajes nuevo = new CanalMensajes(socket, codec);
        try {
            Duration restante = Duration.ofNanos(Math.max(1, limite - System.nanoTime()));
            Mensaje mensaje =
                    nuevo.recibir(restante)
                            .orElseThrow(() ->
                                    new ExcepcionProtocolo(
                                            "El agente se conectó pero no envió HOLA a"
                                                    + " tiempo"));
            HolaDto hola = validarSaludo(nuevo, mensaje);
            canal = nuevo;
            Thread hilo = new Thread(this::leer, "opsec-cliente-agente-" + puerto());
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
     * @throws ExcepcionErrorAgente si el agente responde {@code ERROR}
     * @throws ExcepcionProtocolo si no hay respuesta a tiempo o es de otro tipo
     * @throws IOException si falla la conexión
     * @throws IllegalStateException si todavía no se recibió el saludo
     */
    public ResultadoPasoDto enviarPaso(PasoDto paso) throws IOException {
        Objects.requireNonNull(paso, "El paso no puede ser null");
        return solicitar(
                TipoMensaje.EJECUTAR_PASO,
                paso,
                TipoMensaje.RESULTADO_PASO,
                ResultadoPasoDto.class,
                tiempoMaximo.plusMillis(paso.getEsperaMaximaMs()));
    }

    /**
     * Envía la configuración de los ganchos del agente y espera su confirmación. Cada envío
     * reemplaza por completo la configuración anterior.
     *
     * @param config operaciones sensibles, canarios y fallos a inyectar; no puede ser null
     * @return la confirmación del agente, un resultado con código {@code OK}
     * @throws ExcepcionErrorAgente si el agente responde {@code ERROR}, por ejemplo porque no
     *     conoce el mensaje
     * @throws ExcepcionProtocolo si no hay respuesta a tiempo o es de otro tipo
     * @throws IOException si falla la conexión
     * @throws IllegalStateException si todavía no se recibió el saludo
     */
    public ResultadoPasoDto configurarGanchos(ConfigGanchosDto config) throws IOException {
        Objects.requireNonNull(config, "La configuración no puede ser null");
        return solicitar(
                TipoMensaje.CONFIG_GANCHOS,
                config,
                TipoMensaje.RESULTADO_PASO,
                ResultadoPasoDto.class,
                tiempoMaximo);
    }

    /**
     * Activa en el agente el modo de inspección o de grabación, o los desactiva, y espera la
     * confirmación. Un modo nuevo reemplaza al anterior. Los eventos {@code
     * COMPONENTE_INSPECCIONADO} y {@code ACCION_GRABADA} llegan después por {@link
     * #tomarEventos()}.
     *
     * @param modo modo pedido; no puede ser null
     * @return la confirmación del agente, un resultado con código {@code OK}
     * @throws ExcepcionErrorAgente si el agente responde {@code ERROR}, por ejemplo porque no
     *     conoce el mensaje
     * @throws ExcepcionProtocolo si no hay respuesta a tiempo o es de otro tipo
     * @throws IOException si falla la conexión
     * @throws IllegalStateException si todavía no se recibió el saludo
     */
    public ResultadoPasoDto modoInteraccion(ModoInteraccionDto modo) throws IOException {
        Objects.requireNonNull(modo, "El modo no puede ser null");
        return solicitar(
                TipoMensaje.MODO_INTERACCION,
                modo,
                TipoMensaje.RESULTADO_PASO,
                ResultadoPasoDto.class,
                tiempoMaximo);
    }

    /**
     * Pide el árbol de componentes de las ventanas visibles.
     *
     * @return el árbol que respondió el agente
     * @throws ExcepcionErrorAgente si el agente responde {@code ERROR}
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
     * @throws ExcepcionErrorAgente si el agente responde {@code ERROR}
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
     * Devuelve los eventos recibidos desde la última llamada y los quita del cliente.
     *
     * @return eventos en orden de llegada; lista vacía si no llegó ninguno
     */
    public List<EventoDto> tomarEventos() {
        List<EventoDto> tomados = new ArrayList<>();
        EventoDto evento;
        while ((evento = eventos.poll()) != null) {
            tomados.add(evento);
        }
        return tomados;
    }

    /**
     * Indica si la conexión con el agente terminó, porque el agente la cerró o porque se llamó a
     * {@link #close()}.
     *
     * @return true si ya no se pueden enviar comandos
     */
    public boolean desconectado() {
        return desconexion.getCount() == 0;
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
        try {
            return desconexion.await(tiempoMaximo.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Se interrumpió la espera del cierre del agente");
        } finally {
            close();
        }
    }

    /** Cierra la conexión y el servidor sin enviar {@code CERRAR}. Se puede llamar varias veces. */
    @Override
    public void close() {
        cerrado = true;
        try {
            servidor.close();
        } catch (IOException e) {
            REGISTRO.log(Level.FINE, "Error al cerrar el servidor del cliente del agente", e);
        }
        CanalMensajes actual = canal;
        if (actual != null) {
            try {
                actual.cerrar();
            } catch (IOException e) {
                REGISTRO.log(Level.FINE, "Error al cerrar el canal con el agente", e);
            }
        }
        Thread hilo = lector;
        if (hilo != null && hilo != Thread.currentThread()) {
            try {
                hilo.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else if (hilo == null) {
            desconexion.countDown();
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
        CanalMensajes actual = canalConectado();
        Mensaje solicitud = codec.crear(tipo, cuerpo);
        CompletableFuture<Mensaje> futura = new CompletableFuture<>();
        pendientes.put(solicitud.getId(), futura);
        if (desconectado()) {
            futura.completeExceptionally(new EOFException("El agente ya cerró la conexión"));
        }
        Mensaje respuesta;
        try {
            actual.enviar(solicitud);
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
            throw new ExcepcionErrorAgente(codec.leerCuerpo(respuesta, ErrorDto.class));
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
                    REGISTRO.log(Level.WARNING, "Se recibió un mensaje inválido del agente", e);
                }
            }
        } catch (EOFException e) {
            REGISTRO.fine("El agente cerró la conexión");
        } catch (IOException e) {
            causa = e;
            if (!cerrado) {
                REGISTRO.log(Level.FINE, "La conexión con el agente terminó", e);
            }
        } finally {
            desconexion.countDown();
            for (CompletableFuture<Mensaje> futura : pendientes.values()) {
                futura.completeExceptionally(causa);
            }
        }
    }

    private void atender(Mensaje mensaje) throws ExcepcionProtocolo {
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
        REGISTRO.fine(() -> "Se recibió del agente un mensaje que nadie esperaba: " + mensaje);
    }

    private static String tokenAleatorio() {
        byte[] bytes = new byte[24];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
