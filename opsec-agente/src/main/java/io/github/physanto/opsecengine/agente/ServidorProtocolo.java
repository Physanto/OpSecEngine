package io.github.physanto.opsecengine.agente;

import io.github.physanto.opsecengine.agente.api.AdaptadorToolkit;
import io.github.physanto.opsecengine.protocolo.CanalMensajes;
import io.github.physanto.opsecengine.protocolo.CodecMensajes;
import io.github.physanto.opsecengine.protocolo.ExcepcionProtocolo;
import io.github.physanto.opsecengine.protocolo.Mensaje;
import io.github.physanto.opsecengine.protocolo.TipoMensaje;
import io.github.physanto.opsecengine.protocolo.dto.ArbolDto;
import io.github.physanto.opsecengine.protocolo.dto.ErrorDto;
import io.github.physanto.opsecengine.protocolo.dto.HolaDto;
import io.github.physanto.opsecengine.protocolo.dto.PasoDto;
import io.github.physanto.opsecengine.protocolo.dto.ResultadoPasoDto;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.time.Duration;
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
 *   <li>{@code CERRAR}: termina la conversación. Todavía no cierra la aplicación; el motor la
 *       detiene.
 *   <li>{@code ERROR} del motor: lo registra y termina la conversación.
 *   <li>Cualquier otro mensaje, como {@code CAPTURAR} o {@code CONFIG_GANCHOS}: responde {@code
 *       ERROR} con el código {@code NO_IMPLEMENTADO}.
 * </ul>
 *
 * <p>También termina cuando el motor cierra la conexión. Corre en un solo hilo y nunca propaga
 * errores hacia la aplicación probada.
 */
final class ServidorProtocolo implements Runnable {

    /** Tiempo de cada espera de lectura; al vencer, se vuelve a esperar. */
    private static final Duration ESPERA_LECTURA = Duration.ofSeconds(30);

    private final ArgumentosAgente argumentos;
    private final String versionAgente;
    private final AdaptadorToolkit adaptador;
    private final EmisorProtocolo emisor;
    private final CodecMensajes codec;

    /**
     * Crea el servidor.
     *
     * @param argumentos puerto y token del motor
     * @param versionAgente versión que se informa en el {@code HOLA}
     * @param adaptador adaptador que ejecuta las acciones sobre la interfaz
     * @param emisor emisor de eventos que se conecta después del saludo
     * @param codec codec del protocolo
     */
    ServidorProtocolo(
            ArgumentosAgente argumentos,
            String versionAgente,
            AdaptadorToolkit adaptador,
            EmisorProtocolo emisor,
            CodecMensajes codec) {
        this.argumentos = argumentos;
        this.versionAgente = versionAgente;
        this.adaptador = adaptador;
        this.emisor = emisor;
        this.codec = codec;
    }

    /** Conversa con el motor; registra los errores y nunca los propaga. */
    @Override
    public void run() {
        try {
            conversar();
        } catch (IOException | RuntimeException e) {
            RegistroAgente.error("La conversación con el motor terminó: " + e);
        } finally {
            emisor.desconectar();
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
                return false;
            case ERROR:
                RegistroAgente.error("El motor respondió un error: " + mensaje);
                return false;
            case EJECUTAR_PASO:
                ejecutarPaso(canal, mensaje);
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
