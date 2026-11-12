package io.github.physanto.opsecengine.dominio.extension;

import io.github.physanto.opsecengine.dominio.resultado.Confianza;
import io.github.physanto.opsecengine.dominio.resultado.Veredicto;
import io.github.physanto.opsecengine.dominio.traza.Evento;
import io.github.physanto.opsecengine.dominio.traza.Traza;

/**
 * Sesión que evalúa los eventos de un caso y cierra su evaluación una sola vez.
 *
 * <p>Contrato aditivo de la versión 1.3. No es segura entre hilos; cada sesión se usa desde un solo
 * hilo y no comparte eventos, hallazgos ni motivos con otras sesiones.
 *
 * <p>Los hallazgos se acumulan en orden de aparición y los iguales se conservan una sola vez. Un
 * hallazgo con confianza distinta de {@link Confianza#INFO} determina NO_CUMPLE, conservando los
 * motivos de ejecución incompleta o evaluación fallida. Sin esos hallazgos, un motivo pendiente
 * determina NO_CONCLUYENTE; de lo contrario, CUMPLE. Los hallazgos INFO se conservan en los tres
 * estados.
 *
 * <p>Un fallo de ejecución de un oráculo y un id solicitado pero ausente son motivos persistentes.
 * La falta de evidencia de un oráculo EVENTO puede resolverse con nuevos eventos: se conserva el
 * motivo de su evaluación más reciente, sin borrar los errores persistentes ni los hallazgos
 * anteriores. Los oráculos CASO se ejecutan únicamente al terminar.
 */
public interface EvaluacionCaso {

    /**
     * Registra un evento y evalúa los oráculos de momento {@link Momento#EVENTO}.
     *
     * <p>Cada oráculo recibe el prefijo completo hasta ese evento, sin eventos futuros, para
     * conservar las correlaciones con pasos anteriores. No se modifica la secuencia del evento.
     * Validar el evento precede a incorporarlo: un evento rechazado deja la sesión intacta.
     *
     * @param evento evento nuevo; no puede ser null; su secuencia debe crecer estrictamente y su
     *     caso, si está indicado, debe coincidir con el de la sesión
     * @return veredicto acumulado hasta ese evento; no es el resultado final; nunca null
     * @throws NullPointerException si el evento es null
     * @throws IllegalArgumentException si pertenece a otro caso o su secuencia no crece
     * @throws IllegalStateException si la sesión ya terminó
     */
    Veredicto registrarEvento(Evento evento);

    /**
     * Termina la sesión y evalúa exclusivamente los oráculos de momento {@link Momento#CASO}.
     *
     * <p>Combina sus respuestas con los hallazgos y motivos acumulados. El motivo de ejecución
     * incompleta se conserva aunque haya hallazgos que determinen NO_CUMPLE. Un motivo inválido se
     * rechaza sin cerrar la sesión. Una sesión sin eventos también puede terminarse.
     *
     * @param motivoIncompleto explicación si la ejecución no terminó; null si terminó; no puede
     *     estar en blanco
     * @return veredicto final del caso; nunca null
     * @throws IllegalArgumentException si el motivo no es null pero está en blanco
     * @throws IllegalStateException si la sesión ya terminó
     */
    Veredicto terminar(String motivoIncompleto);

    /**
     * Devuelve una copia inmutable de los eventos observados, incluso después del cierre.
     *
     * @return traza del caso en orden de secuencia; nunca null
     */
    Traza traza();
}
