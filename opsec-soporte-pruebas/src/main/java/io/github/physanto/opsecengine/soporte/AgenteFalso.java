package io.github.physanto.opsecengine.soporte;

import io.github.physanto.opsecengine.protocolo.CanalMensajes;
import io.github.physanto.opsecengine.protocolo.CodecMensajes;
import io.github.physanto.opsecengine.protocolo.ExcepcionProtocolo;
import io.github.physanto.opsecengine.protocolo.Mensaje;
import io.github.physanto.opsecengine.protocolo.TipoMensaje;
import io.github.physanto.opsecengine.protocolo.dto.ArbolDto;
import io.github.physanto.opsecengine.protocolo.dto.CapturaDto;
import io.github.physanto.opsecengine.protocolo.dto.CodigoResultado;
import io.github.physanto.opsecengine.protocolo.dto.ErrorDto;
import io.github.physanto.opsecengine.protocolo.dto.EventoDto;
import io.github.physanto.opsecengine.protocolo.dto.HolaDto;
import io.github.physanto.opsecengine.protocolo.dto.PasoDto;
import io.github.physanto.opsecengine.protocolo.dto.ResultadoPasoDto;
import io.github.physanto.opsecengine.protocolo.dto.SolicitudCapturaDto;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

/**
 * Agente falso que habla el protocolo real y responde según un guion programable.
 *
 * <p>Sirve para probar el núcleo, los oráculos y los informes sin el agente real. Se conecta al
 * puerto del motor, envía {@code HOLA} con el token y atiende los mensajes en un hilo propio:
 *
 * <ul>
 *   <li>{@code EJECUTAR_PASO}: busca la primera regla del guion cuyo predicado acepte el paso,
 *       envía sus eventos y luego su resultado. Si ninguna regla aplica, responde la {@linkplain
 *       #respuestaPorDefecto(ResultadoPasoDto) respuesta por defecto} ({@code NO_ENCONTRADO}).
 *   <li>{@code ARBOL_SOLICITUD}: responde el {@linkplain #conArbol(ArbolDto) árbol configurado}
 *       (vacío si no se configuró).
 *   <li>{@code CAPTURAR}: responde una imagen PNG pequeña generada en memoria.
 *   <li>{@code CONFIG_GANCHOS} y {@code MODO_INTERACCION}: las registra y responde {@code
 *       RESULTADO_PASO} {@code OK}.
 *   <li>{@code ERROR}: solo lo registra.
 *   <li>{@code CERRAR}: termina la conversación y cierra la conexión.
 *   <li>Cualquier otro tipo: responde {@code ERROR} con el código {@code TIPO_NO_SOPORTADO}.
 * </ul>
 *
 * <p>Todos los mensajes recibidos quedan registrados para hacer aserciones. Ejemplo de uso:
 *
 * <pre>{@code
 * try (MotorFalso motor = new MotorFalso();
 *         AgenteFalso agente = new AgenteFalso()) {
 *     EventoDto ventana = new EventoDto(0, 0, "VENTANA_ABIERTA",
 *             Map.of("ventanaId", "v1", "clase", "app.Menu"));
 *     agente.cuandoPaso(paso -> paso.getAccion().equals("PULSAR"))
 *             .emitir(ventana)
 *             .responder(new ResultadoPasoDto(CodigoResultado.OK, null, 5));
 *     agente.conectar(motor.puerto(), motor.token());
 *     motor.esperarHola(Duration.ofSeconds(5));
 *     ResultadoPasoDto resultado = motor.enviarPaso(pasoPulsar);
 * }
 * }</pre>
 *
 * <p>El guion se puede configurar antes o durante la conversación; los métodos de configuración y
 * de consulta son seguros entre hilos.
 */
public final class AgenteFalso implements AutoCloseable {

    /** Versión del agente que se informa en el {@code HOLA}. */
    public static final String VERSION = "agente-falso";

    /**
     * Respuesta inicial cuando ningún guion acepta el paso: {@code NO_ENCONTRADO} con un detalle
     * que lo explica.
     */
    public static final ResultadoPasoDto SIN_GUION =
            new ResultadoPasoDto(
                    CodigoResultado.NO_ENCONTRADO,
                    "El guion del agente falso no tiene respuesta para este paso",
                    0);

    private static final Logger REGISTRO = Logger.getLogger(AgenteFalso.class.getName());
    private static final Duration ESPERA_LECTURA = Duration.ofMillis(250);
    private static final byte[] PNG = generarPng();

    private final CodecMensajes codec = new CodecMensajes();
    private final List<ReglaFija> reglas = new CopyOnWriteArrayList<>();
    private final List<Mensaje> recibidos = new CopyOnWriteArrayList<>();
    private final Object aviso = new Object();
    private volatile ResultadoPasoDto respuestaPorDefecto = SIN_GUION;
    private volatile ArbolDto arbol = new ArbolDto(List.of());
    private volatile CanalMensajes canal;
    private volatile Thread hilo;
    private volatile boolean terminado;

    /** Crea un agente falso sin reglas, con árbol vacío y sin conectar. */
    public AgenteFalso() {}

    /**
     * Empieza una regla del guion para los pasos que cumplen una condición.
     *
     * <p>La regla se agrega al guion cuando se llama a {@link ReglaPaso#responder}. Las reglas se
     * evalúan en el orden en que se agregaron y gana la primera que acepta el paso.
     *
     * @param condicion predicado sobre el paso recibido; no puede ser null
     * @return la regla en construcción
     * @throws NullPointerException si {@code condicion} es null
     */
    public ReglaPaso cuandoPaso(Predicate<PasoDto> condicion) {
        return new ReglaPaso(Objects.requireNonNull(condicion, "La condición no puede ser null"));
    }

    /**
     * Cambia la respuesta para los pasos que ninguna regla acepta.
     *
     * @param resultado resultado que se enviará; no puede ser null
     * @return este agente, para encadenar llamadas
     * @throws NullPointerException si {@code resultado} es null
     */
    public AgenteFalso respuestaPorDefecto(ResultadoPasoDto resultado) {
        this.respuestaPorDefecto =
                Objects.requireNonNull(resultado, "La respuesta por defecto no puede ser null");
        return this;
    }

    /**
     * Fija el árbol que se responde a cada {@code ARBOL_SOLICITUD}.
     *
     * @param arbol árbol de componentes; no puede ser null
     * @return este agente, para encadenar llamadas
     * @throws NullPointerException si {@code arbol} es null
     */
    public AgenteFalso conArbol(ArbolDto arbol) {
        this.arbol = Objects.requireNonNull(arbol, "El árbol no puede ser null");
        return this;
    }

    /**
     * Se conecta al motor en {@code 127.0.0.1}, envía {@code HOLA} y empieza a atender mensajes en
     * un hilo propio.
     *
     * <p>La conexión y el saludo ocurren en el hilo que llama, así que un error de conexión se
     * informa aquí. Solo se puede llamar una vez.
     *
     * @param puerto puerto en el que escucha el motor
     * @param token token de la sesión que se envía en el saludo; no puede ser null
     * @throws IOException si no se puede conectar o enviar el saludo
     * @throws IllegalStateException si el agente ya se conectó
     * @throws NullPointerException si {@code token} es null
     */
    public synchronized void conectar(int puerto, String token) throws IOException {
        Objects.requireNonNull(token, "El token no puede ser null");
        if (canal != null) {
            throw new IllegalStateException("El agente falso ya está conectado");
        }
        CanalMensajes nuevo =
                new CanalMensajes(new Socket(InetAddress.getLoopbackAddress(), puerto), codec);
        try {
            nuevo.enviar(
                    codec.crear(
                            TipoMensaje.HOLA,
                            new HolaDto(
                                    token,
                                    VERSION,
                                    System.getProperty("java.version"),
                                    System.getProperty("os.name"))));
        } catch (IOException e) {
            nuevo.cerrar();
            throw e;
        }
        canal = nuevo;
        Thread lector = new Thread(this::atender, "agente-falso");
        lector.setDaemon(true);
        hilo = lector;
        lector.start();
    }

    /**
     * Devuelve los mensajes recibidos del motor hasta ahora, en orden de llegada.
     *
     * @return copia inmutable de los mensajes recibidos
     */
    public List<Mensaje> mensajesRecibidos() {
        return List.copyOf(recibidos);
    }

    /**
     * Espera a que llegue un mensaje de un tipo, o devuelve el primero que ya haya llegado.
     *
     * @param tipo tipo de mensaje esperado; no puede ser null
     * @param espera tiempo máximo de espera; no puede ser null
     * @return el primer mensaje de ese tipo, o vacío si no llegó a tiempo
     * @throws InterruptedException si se interrumpe el hilo mientras espera
     * @throws NullPointerException si algún parámetro es null
     */
    public Optional<Mensaje> esperarMensaje(TipoMensaje tipo, Duration espera)
            throws InterruptedException {
        Objects.requireNonNull(tipo, "El tipo no puede ser null");
        long limite =
                System.nanoTime()
                        + Objects.requireNonNull(espera, "La espera no puede ser null").toNanos();
        synchronized (aviso) {
            while (true) {
                for (Mensaje mensaje : recibidos) {
                    if (mensaje.getTipo() == tipo) {
                        return Optional.of(mensaje);
                    }
                }
                long restante = limite - System.nanoTime();
                if (restante <= 0 || terminado) {
                    return Optional.empty();
                }
                aviso.wait(Math.max(1, Duration.ofNanos(restante).toMillis()));
            }
        }
    }

    /**
     * Espera a que termine la conversación, por {@code CERRAR} o porque el motor cerró la conexión.
     *
     * @param espera tiempo máximo de espera; no puede ser null
     * @return true si la conversación terminó a tiempo
     * @throws InterruptedException si se interrumpe el hilo mientras espera
     */
    public boolean esperarFin(Duration espera) throws InterruptedException {
        Thread lector = hilo;
        if (lector != null) {
            lector.join(Math.max(1, espera.toMillis()));
        }
        return terminado;
    }

    /**
     * Indica si la conversación ya terminó.
     *
     * @return true después de {@code CERRAR}, de que el motor cierre la conexión o de {@link
     *     #close()}
     */
    public boolean terminado() {
        return terminado;
    }

    /**
     * Devuelve la imagen PNG que el agente falso envía en cada captura.
     *
     * @return copia de los bytes de la imagen
     */
    public static byte[] imagenPng() {
        return PNG.clone();
    }

    /**
     * Cierra la conexión y espera a que termine el hilo del agente. Se puede llamar varias veces.
     */
    @Override
    public void close() {
        CanalMensajes actual = canal;
        if (actual != null) {
            try {
                actual.cerrar();
            } catch (IOException e) {
                REGISTRO.log(Level.FINE, "Error al cerrar el canal del agente falso", e);
            }
        }
        Thread lector = hilo;
        if (lector != null && lector != Thread.currentThread()) {
            try {
                lector.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        marcarTerminado();
    }

    private void atender() {
        try {
            while (!terminado) {
                Optional<Mensaje> recibido;
                try {
                    recibido = canal.recibir(ESPERA_LECTURA);
                } catch (ExcepcionProtocolo e) {
                    REGISTRO.log(Level.WARNING, "El agente falso recibió una línea inválida", e);
                    continue;
                }
                if (recibido.isPresent() && !responder(recibido.get())) {
                    return;
                }
            }
        } catch (EOFException e) {
            REGISTRO.fine("El motor cerró la conexión con el agente falso");
        } catch (IOException e) {
            if (!terminado) {
                REGISTRO.log(Level.FINE, "La conexión del agente falso terminó", e);
            }
        } finally {
            try {
                canal.cerrar();
            } catch (IOException e) {
                REGISTRO.log(Level.FINE, "Error al cerrar el canal del agente falso", e);
            }
            marcarTerminado();
        }
    }

    /** Atiende un mensaje; devuelve false si la conversación debe terminar. */
    private boolean responder(Mensaje mensaje) throws IOException {
        recibidos.add(mensaje);
        avisar();
        switch (mensaje.getTipo()) {
            case EJECUTAR_PASO:
                ejecutarPaso(mensaje);
                return true;
            case ARBOL_SOLICITUD:
                canal.enviar(codec.responder(mensaje, TipoMensaje.ARBOL, arbol));
                return true;
            case CAPTURAR:
                SolicitudCapturaDto solicitud =
                        codec.leerCuerpo(mensaje, SolicitudCapturaDto.class);
                CapturaDto captura =
                        new CapturaDto(
                                solicitud.getVentana(),
                                "PNG",
                                Base64.getEncoder().encodeToString(PNG));
                canal.enviar(codec.responder(mensaje, TipoMensaje.CAPTURA, captura));
                return true;
            case CONFIG_GANCHOS:
            case MODO_INTERACCION:
                canal.enviar(
                        codec.responder(
                                mensaje,
                                TipoMensaje.RESULTADO_PASO,
                                new ResultadoPasoDto(CodigoResultado.OK, null, 0)));
                return true;
            case ERROR:
                return true;
            case CERRAR:
                return false;
            default:
                canal.enviar(
                        codec.responder(
                                mensaje,
                                TipoMensaje.ERROR,
                                new ErrorDto(
                                        ErrorDto.TIPO_NO_SOPORTADO,
                                        "El agente falso no atiende " + mensaje.getTipo())));
                return true;
        }
    }

    private void ejecutarPaso(Mensaje mensaje) throws IOException {
        PasoDto paso = codec.leerCuerpo(mensaje, PasoDto.class);
        ResultadoPasoDto resultado = respuestaPorDefecto;
        List<EventoDto> eventos = List.of();
        for (ReglaFija regla : reglas) {
            if (regla.condicion().test(paso)) {
                resultado = regla.resultado();
                eventos = regla.eventos();
                break;
            }
        }
        for (EventoDto evento : eventos) {
            canal.enviar(codec.crear(TipoMensaje.EVENTO, evento));
        }
        canal.enviar(codec.responder(mensaje, TipoMensaje.RESULTADO_PASO, resultado));
    }

    private void marcarTerminado() {
        terminado = true;
        avisar();
    }

    private void avisar() {
        synchronized (aviso) {
            aviso.notifyAll();
        }
    }

    private static byte[] generarPng() {
        BufferedImage imagen = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < imagen.getWidth(); x++) {
            for (int y = 0; y < imagen.getHeight(); y++) {
                imagen.setRGB(x, y, ((x + y) % 2 == 0 ? Color.WHITE : Color.BLUE).getRGB());
            }
        }
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try {
            ImageIO.write(imagen, "png", salida);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar la imagen PNG en memoria", e);
        }
        return salida.toByteArray();
    }

    /**
     * Regla del guion en construcción: los eventos que se emiten y el resultado que se responde
     * cuando un paso cumple la condición.
     *
     * <p>No es segura entre hilos; se construye en una sola cadena de llamadas.
     */
    public final class ReglaPaso {

        private final Predicate<PasoDto> condicion;
        private final List<EventoDto> eventos = new ArrayList<>();

        private ReglaPaso(Predicate<PasoDto> condicion) {
            this.condicion = condicion;
        }

        /**
         * Agrega eventos que se envían, en orden, antes del resultado del paso.
         *
         * @param eventos eventos a emitir; no admite null
         * @return esta regla, para encadenar llamadas
         * @throws NullPointerException si el arreglo o alguno de sus eventos es null
         */
        public ReglaPaso emitir(EventoDto... eventos) {
            this.eventos.addAll(Arrays.asList(Objects.requireNonNull(eventos)));
            this.eventos.forEach(evento -> Objects.requireNonNull(evento, "Evento null"));
            return this;
        }

        /**
         * Fija el resultado del paso y agrega la regla al guion del agente.
         *
         * @param resultado resultado que se responde; no puede ser null
         * @return el agente, para seguir configurándolo
         * @throws NullPointerException si {@code resultado} es null
         */
        public AgenteFalso responder(ResultadoPasoDto resultado) {
            Objects.requireNonNull(resultado, "El resultado no puede ser null");
            reglas.add(new ReglaFija(condicion, List.copyOf(eventos), resultado));
            return AgenteFalso.this;
        }
    }

    /** Regla ya agregada al guion. */
    private record ReglaFija(
            Predicate<PasoDto> condicion, List<EventoDto> eventos, ResultadoPasoDto resultado) {}
}
