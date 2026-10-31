package io.github.physanto.opsecengine.nucleo.evaluacion;

import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.extension.Contexto;
import io.github.physanto.opsecengine.dominio.extension.Momento;
import io.github.physanto.opsecengine.dominio.extension.Oraculo;
import io.github.physanto.opsecengine.dominio.resultado.Confianza;
import io.github.physanto.opsecengine.dominio.resultado.EstadoVeredicto;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.resultado.Veredicto;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import io.github.physanto.opsecengine.nucleo.extensiones.RegistroExtensiones;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Aplica los oráculos a las trazas y calcula los veredictos.
 *
 * <p><b>Por caso</b> ({@link #evaluarCaso(Traza, Contexto, String)}): se evalúan los oráculos de
 * momento {@link Momento#EVENTO} y {@link Momento#CASO} que aplican al caso, más los que el caso
 * pide por id aunque no apliquen. El veredicto sigue la regla "confiable antes que completo":
 *
 * <ol>
 *   <li>{@code NO_CUMPLE} si algún hallazgo tiene confianza {@code CONFIRMADO} o {@code PROBABLE},
 *       aunque la ejecución haya quedado incompleta (el motivo se conserva);
 *   <li>si no, {@code NO_CONCLUYENTE} si la ejecución quedó incompleta, si un oráculo falló o si el
 *       caso pide un oráculo que no está registrado;
 *   <li>si no, {@code CUMPLE}. Los hallazgos {@code INFO} se conservan en cualquier veredicto.
 * </ol>
 *
 * <p><b>Por suite</b> ({@link #evaluarSuite(Contexto)}): se evalúan los oráculos de momento {@link
 * Momento#SUITE} con las trazas de todos los casos.
 *
 * <p>Si un oráculo lanza una excepción se registra como fallo del oráculo y no detiene la
 * evaluación. Es seguro entre hilos si los oráculos lo son, como pide su contrato.
 *
 * <p>Pendiente para la v0.1 (sprints 1 y 2): los oráculos de momento {@code EVENTO} reciben hoy la
 * traza completa al terminar el caso; el evaluador por evento de P2 los aplicará a medida que
 * llegan los eventos, y las reglas YAML de {@code oraculos/} se sumarán a los oráculos registrados.
 */
public final class EvaluacionService {

    private static final Logger REGISTRO = Logger.getLogger(EvaluacionService.class.getName());

    private final RegistroExtensiones registro;

    /**
     * Crea el servicio con los oráculos de un registro.
     *
     * @param registro registro de extensiones; no puede ser null
     * @throws NullPointerException si {@code registro} es null
     */
    public EvaluacionService(RegistroExtensiones registro) {
        this.registro = Objects.requireNonNull(registro, "El registro no puede ser null");
    }

    /**
     * Evalúa la traza de un caso y calcula su veredicto.
     *
     * @param traza traza del caso, completa o no; no puede ser null
     * @param contexto contexto del caso, con {@link Contexto#caso()} no null; no puede ser null
     * @param motivoIncompleto por qué la ejecución no terminó, por ejemplo un paso con {@code
     *     NO_ENCONTRADO}; null si se ejecutaron todos los pasos
     * @return el veredicto con todos los hallazgos
     * @throws NullPointerException si {@code traza}, {@code contexto} o su caso son null
     */
    public Veredicto evaluarCaso(Traza traza, Contexto contexto, String motivoIncompleto) {
        Objects.requireNonNull(traza, "La traza no puede ser null");
        Objects.requireNonNull(contexto, "El contexto no puede ser null");
        Caso caso = Objects.requireNonNull(contexto.caso(), "El contexto no tiene caso");
        List<Hallazgo> hallazgos = new ArrayList<>();
        List<String> motivos = new ArrayList<>();
        if (motivoIncompleto != null) {
            motivos.add(motivoIncompleto);
        }
        for (String id : caso.oraculos()) {
            if (registro.oraculo(id).isEmpty()) {
                motivos.add("El caso pide el oráculo " + id + ", que no está registrado");
            }
        }
        for (Oraculo oraculo : registro.oraculos()) {
            if (oraculo.momento() == Momento.SUITE) {
                continue;
            }
            try {
                if (caso.oraculos().contains(oraculo.id()) || oraculo.aplica(caso, contexto)) {
                    hallazgos.addAll(oraculo.evaluar(traza, contexto));
                }
            } catch (RuntimeException e) {
                REGISTRO.log(Level.WARNING, "Falló el oráculo " + oraculo.id(), e);
                motivos.add("Falló el oráculo " + oraculo.id() + ": " + e);
            }
        }
        return veredicto(hallazgos, motivos);
    }

    /**
     * Evalúa los oráculos de suite con las trazas de todos los casos.
     *
     * @param contexto contexto de la suite, con {@link Contexto#caso()} null y las trazas en {@link
     *     Contexto#trazasSuite()}; no puede ser null
     * @return hallazgos de suite; lista vacía si no hay oráculos de suite o no encontraron nada
     * @throws NullPointerException si {@code contexto} es null
     */
    public List<Hallazgo> evaluarSuite(Contexto contexto) {
        Objects.requireNonNull(contexto, "El contexto no puede ser null");
        List<Hallazgo> hallazgos = new ArrayList<>();
        for (Oraculo oraculo : registro.oraculos()) {
            if (oraculo.momento() != Momento.SUITE) {
                continue;
            }
            try {
                if (oraculo.aplica(null, contexto)) {
                    hallazgos.addAll(oraculo.evaluar(null, contexto));
                }
            } catch (RuntimeException e) {
                REGISTRO.log(Level.WARNING, "Falló el oráculo de suite " + oraculo.id(), e);
            }
        }
        return List.copyOf(hallazgos);
    }

    private static Veredicto veredicto(List<Hallazgo> hallazgos, List<String> motivos) {
        boolean falla =
                hallazgos.stream().anyMatch(hallazgo -> hallazgo.confianza() != Confianza.INFO);
        String motivo = motivos.isEmpty() ? null : String.join("; ", motivos);
        if (falla) {
            return new Veredicto(EstadoVeredicto.NO_CUMPLE, hallazgos, motivo);
        }
        if (motivo != null) {
            return new Veredicto(EstadoVeredicto.NO_CONCLUYENTE, hallazgos, motivo);
        }
        return new Veredicto(EstadoVeredicto.CUMPLE, hallazgos, null);
    }
}
