package io.github.physanto.opsecengine.agente.api;

import java.util.Map;

/**
 * Envía al motor los eventos que observan los ganchos y los observadores del agente.
 *
 * <p>El emisor asigna la secuencia y el instante de cada evento y lo envía como un mensaje {@code
 * EVENTO}. Los campos de {@code datos} de cada tipo se documentan en {@code
 * contratos/catalogos/eventos.yaml}.
 *
 * <p><b>Hilos y errores.</b> Se llama desde cualquier hilo de la aplicación probada, incluido el
 * EDT y el código instrumentado. La implementación debe ser segura entre hilos, no debe bloquear al
 * que llama más de lo imprescindible y nunca debe lanzar excepciones: si no puede enviar, el evento
 * se descarta y el fallo se registra.
 */
public interface EmisorEventos {

    /**
     * Emite un evento.
     *
     * @param tipo nombre del tipo de evento, con los valores de {@code TipoEvento} del dominio, por
     *     ejemplo {@code "SQL"}; si es null o vacío, el evento se descarta
     * @param datos campos propios del tipo; puede ser null. El emisor los copia antes de volver,
     *     así que quien llama puede reutilizar el mapa
     */
    void emitir(String tipo, Map<String, Object> datos);
}
