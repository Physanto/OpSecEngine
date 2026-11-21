package io.github.physanto.opsecengine.agente;

import io.github.physanto.opsecengine.agente.api.AdaptadorToolkit;
import io.github.physanto.opsecengine.agente.cobertura.CoberturaJacoco;
import io.github.physanto.opsecengine.agente.ganchos.ConfiguracionGanchos;
import io.github.physanto.opsecengine.agente.swing.ControlInteraccion;
import io.github.physanto.opsecengine.protocolo.CanalMensajes;
import io.github.physanto.opsecengine.protocolo.CodecMensajes;
import io.github.physanto.opsecengine.protocolo.ExcepcionProtocolo;
import io.github.physanto.opsecengine.protocolo.Mensaje;
import io.github.physanto.opsecengine.protocolo.TipoMensaje;
import io.github.physanto.opsecengine.protocolo.dto.ArbolDto;
import io.github.physanto.opsecengine.protocolo.dto.CapturaDto;
import io.github.physanto.opsecengine.protocolo.dto.CodigoResultado;
import io.github.physanto.opsecengine.protocolo.dto.ConfigGanchosDto;
import io.github.physanto.opsecengine.protocolo.dto.ErrorDto;
import io.github.physanto.opsecengine.protocolo.dto.HolaDto;
import io.github.physanto.opsecengine.protocolo.dto.ModoInteraccionDto;
import io.github.physanto.opsecengine.protocolo.dto.PasoDto;
import io.github.physanto.opsecengine.protocolo.dto.ResultadoPasoDto;
import io.github.physanto.opsecengine.protocolo.dto.SolicitudCapturaDto;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Lado del agente del protocolo: conversa con el motor y delega las acciones en el {@link
 * AdaptadorToolkit}.
 *
 * <p>Se conecta al motor en {@code 127.0.0.1}, envía {@code HOLA} con el token y conecta el {@link
 * EmisorProtocolo}, que envía los eventos guardados hasta ese momento. Después atiende un mensaje a
 * la vez:
 *
 * <ul>
 *   <li>{@code EJECUTAR_PASO}: ejecuta el paso y responde {@code RESULTADO_PASO}. Los eventos que
 *       produce el paso se envían antes que el resultado.
 *   <li>{@code ARBOL_SOLICITUD}: responde {@code ARBOL}.
 *   <li>{@code CAPTURAR}: responde {@code CAPTURA} con la imagen PNG de la ventana pedida. Si
 *       ninguna ventana visible coincide, o si la captura falla, responde {@code ERROR} con el
 *       código {@code ERROR_INTERNO} y la causa.
 *   <li>{@code CERRAR}: si JaCoCo está cargado, escribe en su archivo la cobertura de la sesión
 *       ({@link CoberturaJacoco}); emite el evento {@code APP_TERMINADA} con {@code esperada=true}
 *       y {@code causa=CERRAR}, cierra la conexión (esa es la respuesta: el motor la espera) y
 *       después ejecuta el cierre de la aplicación que recibió en el constructor.
 *   <li>{@code ERROR} del motor: lo registra y termina la conversación.
 *   <li>{@code CONFIG_GANCHOS}: reemplaza la configuración de los ganchos ({@link
 *       ConfiguracionGanchos}) y responde {@code RESULTADO_PASO} con código {@code OK} para
 *       confirmar que ya está vigente. Si el cuerpo no es válido responde {@code ERROR}.
 *   <li>{@code MODO_INTERACCION}: activa el inspector o la grabadora del {@link ControlInteraccion}
 *       (o los desactiva) y responde {@code RESULTADO_PASO}: {@code OK} cuando el modo ya está
 *       vigente, {@code ERROR} con la causa si no se pudo activar. Sin control de interacción
 *       responde {@code ERROR} con el código {@code NO_IMPLEMENTADO}.
 *   <li>Cualquier otro mensaje: responde {@code ERROR} con el código {@code NO_IMPLEMENTADO}.
 * </ul>
 *
 * <p>También termina cuando el motor cierra la conexión; en ese caso, y ante un {@code ERROR} del
 * motor, no cierra la aplicación. Corre en un solo hilo y nunca propaga errores hacia la aplicación
 * probada.
 */
final class ServidorProtocolo implements Runnable {

    /** Tiempo de cada espera de lectura; al vencer, se vuelve a esperar. */
    private static final Duration ESPERA_LECTURA = Duration.ofSeconds(30);

    private final ArgumentosAgente argumentos;
    private final String versionAgente;
    private final AdaptadorToolkit adaptador;
    private final EmisorProtocolo emisor;
    private final CodecMensajes codec;
    private final Runnable cierre;
    private final ControlInteraccion interaccion;
    private final CoberturaJacoco cobertura;
    private boolean cierrePedido;
    private boolean interaccionUsada;

    /**
     * Crea el servidor.
     *
     * @param argumentos puerto y token del motor
     * @param versionAgente versión que se informa en el {@code HOLA}
     * @param adaptador adaptador que ejecuta las acciones sobre la interfaz
     * @param emisor emisor de eventos que se conecta después del saludo
     * @param codec codec del protocolo
     * @param cierre acción que cierra la aplicación después de {@code CERRAR}; se ejecuta en el
     *     hilo del servidor, con la conexión ya cerrada
     */
    ServidorProtocolo(
            ArgumentosAgente argumentos,
            String versionAgente,
            AdaptadorToolkit adaptador,
            EmisorProtocolo emisor,
            CodecMensajes codec,
            Runnable cierre) {
        this(argumentos, versionAgente, adaptador, emisor, codec, cierre, null);
    }

    /**
     * Crea el servidor con inspector y grabadora.
     *
     * @param argumentos puerto y token del motor
     * @param versionAgente versión que se informa en el {@code HOLA}
     * @param adaptador adaptador que ejecuta las acciones sobre la interfaz
     * @param emisor emisor de eventos que se conecta después del saludo
     * @param codec codec del protocolo
     * @param cierre acción que cierra la aplicación después de {@code CERRAR}; se ejecuta en el
     *     hilo del servidor, con la conexión ya cerrada
     * @param interaccion atiende {@code MODO_INTERACCION}; puede ser null si no está disponible
     */
    ServidorProtocolo(
            ArgumentosAgente argumentos,
            String versionAgente,
            AdaptadorToolkit adaptador,
            EmisorProtocolo emisor,
            CodecMensajes codec,
            Runnable cierre,
            ControlInteraccion interaccion) {
        this(
                argumentos,
                versionAgente,
                adaptador,
                emisor,
                codec,
                cierre,
                interaccion,
                new CoberturaJacoco(RegistroAgente::error));
    }

    /**
     * Crea el servidor con inspector, grabadora y un volcador de cobertura propio.
     *
     * @param argumentos puerto y token del motor
     * @param versionAgente versión que se informa en el {@code HOLA}
     * @param adaptador adaptador que ejecuta las acciones sobre la interfaz
     * @param emisor emisor de eventos que se conecta después del saludo
     * @param codec codec del protocolo
     * @param cierre acción que cierra la aplicación después de {@code CERRAR}
     * @param interaccion atiende {@code MODO_INTERACCION}; puede ser null si no está disponible
     * @param cobertura escribe la cobertura de JaCoCo al recibir {@code CERRAR}; no puede ser null
     */
    ServidorProtocolo(
            ArgumentosAgente argumentos,
            String versionAgente,
            AdaptadorToolkit adaptador,
            EmisorProtocolo emisor,
            CodecMensajes codec,
            Runnable cierre,
            ControlInteraccion interaccion,
            CoberturaJacoco cobertura) {
        this.cobertura = cobertura;
        this.interaccion = interaccion;
        this.argumentos = argumentos;
        this.versionAgente = versionAgente;
        this.adaptador = adaptador;
        this.emisor = emisor;
        this.codec = codec;
        this.cierre = cierre;
    }

    /**
     * Conversa con el motor y, si pidió {@code CERRAR}, cierra la aplicación; registra los errores
     * y nunca los propaga.
     */
    @Override
    public void run() {
        try {
            conversar();
        } catch (IOException | RuntimeException e) {
            RegistroAgente.error("La conversación con el motor terminó: " + e);
        } finally {
            if (interaccionUsada && !cierrePedido) {
                // El motor se fue sin pedir CERRAR: la aplicación sigue, sin inspector ni
                // grabadora.
                interaccion.detener();
            }
            emisor.desconectar();
        }
        if (cierrePedido) {
            try {
                cierre.run();
            } catch (RuntimeException e) {
                RegistroAgente.error("No se pudo cerrar la aplicación: " + e);
            }
        }
    }

    /**
     * Conversa con el motor hasta {@code CERRAR}, un {@code ERROR} del motor o el cierre de la
     * conexión.
     *
     * @throws IOException si falla la conexión
     */
    void conversar() throws IOException {
        Socket socket = new Socket(InetAddress.getLoopbackAddress(), argumentos.puerto());
        try (CanalMensajes canal = new CanalMensajes(socket, codec)) {
            canal.enviar(
                    codec.crear(
                            TipoMensaje.HOLA,
                            new HolaDto(
                                    argumentos.token(),
                                    versionAgente,
                                    System.getProperty("java.version"),
                                    System.getProperty("os.name"))));
            emisor.conectar(canal);
            while (true) {
                Optional<Mensaje> recibido;
                try {
                    recibido = canal.recibir(ESPERA_LECTURA);
                } catch (EOFException e) {
                    return;
                } catch (ExcepcionProtocolo e) {
                    canal.enviar(
                            codec.crear(
                                    TipoMensaje.ERROR,
                                    new ErrorDto(ErrorDto.MENSAJE_INVALIDO, e.getMessage())));
                    continue;
                }
                if (recibido.isPresent() && !atender(canal, recibido.get())) {
                    return;
                }
            }
        }
    }

    /** Atiende un mensaje; devuelve false si la conversación debe terminar. */
    private boolean atender(CanalMensajes canal, Mensaje mensaje) throws IOException {
        switch (mensaje.getTipo()) {
            case CERRAR:
                cobertura.volcar();
                avisarCierre();
                cierrePedido = true;
                return false;
            case ERROR:
                RegistroAgente.error("El motor respondió un error: " + mensaje);
                return false;
            case EJECUTAR_PASO:
                ejecutarPaso(canal, mensaje);
                return true;
            case CAPTURAR:
                capturar(canal, mensaje);
                return true;
            case CONFIG_GANCHOS:
                configurarGanchos(canal, mensaje);
                return true;
            case MODO_INTERACCION:
                if (interaccion == null) {
                    canal.enviar(
                            codec.responder(
                                    mensaje,
                                    TipoMensaje.ERROR,
                                    new ErrorDto(
                                            ErrorDto.NO_IMPLEMENTADO,
                                            "El agente no tiene inspector ni grabadora")));
                } else {
                    cambiarModo(canal, mensaje);
                }
                return true;
            case ARBOL_SOLICITUD:
                canal.enviar(
                        codec.responder(
                                mensaje, TipoMensaje.ARBOL, new ArbolDto(adaptador.arbol())));
                return true;
            default:
                canal.enviar(
                        codec.responder(
                                mensaje,
                                TipoMensaje.ERROR,
                                new ErrorDto(
                                        ErrorDto.NO_IMPLEMENTADO,
                                        mensaje.getTipo()
                                                + " todavía no está implementado en el agente")));
                return true;
        }
    }

    /** Emite {@code APP_TERMINADA} antes de cortar la conexión, mientras el motor todavía lee. */
    private void avisarCierre() {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("esperada", true);
        datos.put("causa", "CERRAR");
        emisor.emitir("APP_TERMINADA", datos);
    }

    private void capturar(CanalMensajes canal, Mensaje mensaje) throws IOException {
        SolicitudCapturaDto solicitud;
        try {
            solicitud = codec.leerCuerpo(mensaje, SolicitudCapturaDto.class);
        } catch (ExcepcionProtocolo e) {
            canal.enviar(
                    codec.responder(
                            mensaje,
                            TipoMensaje.ERROR,
                            new ErrorDto(ErrorDto.MENSAJE_INVALIDO, e.getMessage())));
            return;
        }
        CapturaDto captura;
        String problema;
        try {
            captura = adaptador.capturar(solicitud.getVentana());
            problema =
                    solicitud.getVentana() == null
                            ? "No hay ninguna ventana visible para capturar"
                            : "No hay ninguna ventana visible con el título o la clase "
                                    + solicitud.getVentana();
        } catch (RuntimeException e) {
            captura = null;
            problema = "No se pudo tomar la captura: " + e.getMessage();
        }
        if (captura == null) {
            canal.enviar(
                    codec.responder(
                            mensaje,
                            TipoMensaje.ERROR,
                            new ErrorDto(ErrorDto.ERROR_INTERNO, problema)));
            return;
        }
        canal.enviar(codec.responder(mensaje, TipoMensaje.CAPTURA, captura));
    }

    private void cambiarModo(CanalMensajes canal, Mensaje mensaje) throws IOException {
        ModoInteraccionDto modo;
        try {
            modo = codec.leerCuerpo(mensaje, ModoInteraccionDto.class);
        } catch (ExcepcionProtocolo e) {
            canal.enviar(
                    codec.responder(
                            mensaje,
                            TipoMensaje.ERROR,
                            new ErrorDto(ErrorDto.MENSAJE_INVALIDO, e.getMessage())));
            return;
        }
        interaccionUsada = true;
        canal.enviar(
                codec.responder(
                        mensaje, TipoMensaje.RESULTADO_PASO, interaccion.activar(modo.getModo())));
    }

    private void configurarGanchos(CanalMensajes canal, Mensaje mensaje) throws IOException {
        ConfigGanchosDto config;
        try {
            config = codec.leerCuerpo(mensaje, ConfigGanchosDto.class);
        } catch (ExcepcionProtocolo e) {
            canal.enviar(
                    codec.responder(
                            mensaje,
                            TipoMensaje.ERROR,
                            new ErrorDto(ErrorDto.MENSAJE_INVALIDO, e.getMessage())));
            return;
        }
        ConfiguracionGanchos.reemplazar(config);
        canal.enviar(
                codec.responder(
                        mensaje,
                        TipoMensaje.RESULTADO_PASO,
                        new ResultadoPasoDto(CodigoResultado.OK, null, 0)));
    }

    private void ejecutarPaso(CanalMensajes canal, Mensaje mensaje) throws IOException {
        PasoDto paso;
        try {
            paso = codec.leerCuerpo(mensaje, PasoDto.class);
        } catch (ExcepcionProtocolo e) {
            canal.enviar(
                    codec.responder(
                            mensaje,
                            TipoMensaje.ERROR,
                            new ErrorDto(ErrorDto.MENSAJE_INVALIDO, e.getMessage())));
            return;
        }
        ResultadoPasoDto resultado = adaptador.ejecutar(paso);
        canal.enviar(codec.responder(mensaje, TipoMensaje.RESULTADO_PASO, resultado));
    }
}
