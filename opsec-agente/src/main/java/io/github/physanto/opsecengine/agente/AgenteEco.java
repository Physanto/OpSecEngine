package io.github.physanto.opsecengine.agente;

import io.github.physanto.opsecengine.protocolo.CanalMensajes;
import io.github.physanto.opsecengine.protocolo.CodecMensajes;
import io.github.physanto.opsecengine.protocolo.ExcepcionProtocolo;
import io.github.physanto.opsecengine.protocolo.Mensaje;
import io.github.physanto.opsecengine.protocolo.TipoMensaje;
import io.github.physanto.opsecengine.protocolo.dto.ErrorDto;
import io.github.physanto.opsecengine.protocolo.dto.HolaDto;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.Optional;

/**
 * Agente "eco": la conversación mínima con el motor, sin tocar la aplicación.
 *
 * <p>Se conecta al motor en {@code 127.0.0.1}, envía {@code HOLA} con el token, responde {@code
 * ERROR} con el código {@code NO_IMPLEMENTADO} a cualquier otro mensaje y termina al recibir {@code
 * CERRAR}, al recibir {@code ERROR} del motor o cuando el motor cierra la conexión.
 *
 * <p>Es un esqueleto temporal para validar el lanzamiento con {@code -javaagent} y el saludo; el
 * dueño del módulo lo reemplazará por el servidor del protocolo real. No cierra la aplicación al
 * recibir {@code CERRAR}: solo termina la conversación.
 */
final class AgenteEco implements Runnable {

    /** Tiempo de cada espera de lectura; al vencer, se vuelve a esperar. */
    private static final Duration ESPERA_LECTURA = Duration.ofSeconds(30);

    private final ArgumentosAgente argumentos;
    private final String versionAgente;
    private final CodecMensajes codec = new CodecMensajes();

    /**
     * Crea el agente eco.
     *
     * @param argumentos puerto y token del motor
     * @param versionAgente versión que se informa en el {@code HOLA}
     */
    AgenteEco(ArgumentosAgente argumentos, String versionAgente) {
        this.argumentos = argumentos;
        this.versionAgente = versionAgente;
    }

    /** Conversa con el motor; registra los errores en la salida de error y nunca los propaga. */
    @Override
    public void run() {
        try {
            conversar();
        } catch (IOException | RuntimeException e) {
            System.err.println("[opsec-agente] La conversación con el motor terminó: " + e);
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
        if (mensaje.getTipo() == TipoMensaje.CERRAR) {
            return false;
        }
        if (mensaje.getTipo() == TipoMensaje.ERROR) {
            System.err.println("[opsec-agente] El motor respondió un error: " + mensaje);
            return false;
        }
        canal.enviar(
                codec.responder(
                        mensaje,
                        TipoMensaje.ERROR,
                        new ErrorDto(
                                ErrorDto.NO_IMPLEMENTADO,
                                "El agente eco solo implementa el saludo; "
                                        + mensaje.getTipo()
                                        + " no está implementado")));
        return true;
    }
}
