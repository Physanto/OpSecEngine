package io.github.physanto.opsecengine.agente;

import io.github.physanto.opsecengine.agente.api.EmisorEventos;
import io.github.physanto.opsecengine.protocolo.CanalMensajes;
import io.github.physanto.opsecengine.protocolo.CodecMensajes;
import io.github.physanto.opsecengine.protocolo.TipoMensaje;
import io.github.physanto.opsecengine.protocolo.dto.EventoDto;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Emisor de eventos que los envía al motor como mensajes {@code EVENTO}.
 *
 * <p>Los observadores se instalan antes de que el agente se conecte con el motor, así que los
 * eventos que llegan antes de {@link #conectar(CanalMensajes)} se guardan, hasta {@value
 * #MAXIMO_PENDIENTES}, y se envían en orden apenas hay conexión. Después de {@link #desconectar()}
 * los eventos se descartan.
 *
 * <p>La secuencia empieza en 0 y crece de uno en uno en el orden en que se envían los eventos.
 *
 * <p>Es seguro entre hilos y nunca lanza excepciones: si un envío falla, lo registra una vez y
 * descarta los eventos siguientes.
 */
final class EmisorProtocolo implements EmisorEventos {

    /** Máximo de eventos guardados mientras el agente no está conectado. */
    static final int MAXIMO_PENDIENTES = 10_000;

    private final CodecMensajes codec;
    private final Object candado = new Object();
    private final List<EventoDto> pendientes = new ArrayList<>();
    private CanalMensajes canal;
    private boolean terminado;
    private long secuencia;

    /**
     * Crea el emisor, todavía sin conexión.
     *
     * @param codec codec del protocolo
     */
    EmisorProtocolo(CodecMensajes codec) {
        this.codec = codec;
    }

    /**
     * Emite un evento: lo envía si hay conexión o lo guarda si todavía no la hay.
     *
     * @param tipo nombre del tipo de evento; si es null o vacío, el evento se descarta
     * @param datos campos del evento; puede ser null. Los valores null se omiten
     */
    @Override
    public void emitir(String tipo, Map<String, Object> datos) {
        if (tipo == null || tipo.trim().isEmpty()) {
            return;
        }
        synchronized (candado) {
            if (terminado) {
                return;
            }
            try {
                EventoDto evento =
                        new EventoDto(secuencia, System.currentTimeMillis(), tipo, datos);
                if (canal == null) {
                    if (pendientes.size() < MAXIMO_PENDIENTES) {
                        pendientes.add(evento);
                        secuencia++;
                    }
                    return;
                }
                canal.enviar(codec.crear(TipoMensaje.EVENTO, evento));
                secuencia++;
            } catch (IOException | RuntimeException e) {
                RegistroAgente.error("No se pudo enviar un evento " + tipo + ": " + e);
                terminar();
            }
        }
    }

    /**
     * Conecta el emisor con el motor y envía los eventos guardados, en orden.
     *
     * @param nuevo canal ya saludado con el motor
     * @throws IOException si falla el envío de un evento guardado
     */
    void conectar(CanalMensajes nuevo) throws IOException {
        synchronized (candado) {
            if (terminado) {
                return;
            }
            for (EventoDto evento : pendientes) {
                nuevo.enviar(codec.crear(TipoMensaje.EVENTO, evento));
            }
            pendientes.clear();
            canal = nuevo;
        }
    }

    /** Desconecta el emisor; desde aquí los eventos se descartan. */
    void desconectar() {
        synchronized (candado) {
            terminar();
        }
    }

    private void terminar() {
        terminado = true;
        canal = null;
        pendientes.clear();
    }
}
