package io.github.physanto.opsecengine.dominio.extension;

import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.resultado.Confianza;
import io.github.physanto.opsecengine.dominio.resultado.EstadoVeredicto;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.resultado.Veredicto;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import java.util.List;

/**
 * Regla que decide si una traza muestra un fallo y produce los hallazgos correspondientes.
 *
 * <p><b>Cuándo se llama.</b> Después de ejecutar cada caso, el núcleo llama a {@link #aplica(Caso,
 * Contexto)} con los oráculos de momento {@link Momento#EVENTO} y {@link Momento#CASO}, y a {@link
 * #evaluar(Traza, Contexto)} con los que aplican. Al terminar la suite hace lo mismo con los de
 * momento {@link Momento#SUITE}. Un oráculo que no aplica no se evalúa, lo que evita falsas
 * alarmas.
 *
 * <p><b>Datos inesperados.</b> Las trazas vienen de aplicaciones ajenas y pueden estar incompletas
 * o traer campos ausentes. El oráculo no debe lanzar excepciones por eso: si no puede decidir,
 * devuelve una lista vacía. Si aun así lanza una excepción, el núcleo la registra como fallo del
 * oráculo y el veredicto del caso queda {@code NO_CONCLUYENTE}.
 *
 * <p><b>Hilos.</b> Las implementaciones deben ser sin estado o seguras entre hilos: el núcleo puede
 * evaluar varios casos a la vez con la misma instancia.
 *
 * <p><b>Registro.</b> Con {@link java.util.ServiceLoader}, declarando la implementación en {@code
 * META-INF/services/io.github.physanto.opsecengine.dominio.extension.Oraculo}. Necesita un
 * constructor público sin parámetros.
 */
public interface Oraculo {

    /**
     * Devuelve el identificador del oráculo.
     *
     * @return id único y estable, en mayúsculas y con guiones, por ejemplo {@code "SIN-SESION"};
     *     debe estar en el catálogo de oráculos
     */
    String id();

    /**
     * Devuelve el momento en que se evalúa el oráculo.
     *
     * @return momento de evaluación; siempre el mismo para una implementación
     */
    Momento momento();

    /**
     * Indica si el oráculo tiene sentido para un caso; por ejemplo, un oráculo de SQL no aplica a
     * una aplicación sin base de datos.
     *
     * @param caso caso que se va a evaluar; es null en el momento {@link Momento#SUITE}
     * @param ctx contexto de la evaluación; nunca es null
     * @return {@code true} si el oráculo debe evaluarse
     */
    boolean aplica(Caso caso, Contexto ctx);

    /**
     * Evalúa la traza y devuelve los hallazgos encontrados.
     *
     * <p>En los momentos {@link Momento#EVENTO} y {@link Momento#CASO} recibe la traza del caso,
     * que puede estar incompleta si la ejecución se interrumpió. Un oráculo de momento {@code
     * EVENTO} revisa cada evento por separado. En el momento {@link Momento#SUITE}, {@code traza}
     * puede ser null y las trazas se leen de {@link Contexto#trazasSuite()}.
     *
     * @param traza traza del caso; puede ser null en el momento {@link Momento#SUITE}
     * @param ctx contexto de la evaluación; nunca es null
     * @return hallazgos encontrados, con las secuencias de los eventos como evidencia; lista vacía,
     *     nunca null, si no hay hallazgos
     */
    List<Hallazgo> evaluar(Traza traza, Contexto ctx);

    /**
     * Evalúa la traza y devuelve un veredicto completo, que puede ser {@code NO_CONCLUYENTE}.
     *
     * <p>Una lista de hallazgos vacía no distingue entre «se comprobó y está bien» y «no hubo
     * evidencia suficiente para decidir». Los oráculos que necesitan esa distinción sobrescriben
     * este método y devuelven {@code NO_CONCLUYENTE} con un motivo cuando falta evidencia. El
     * evaluador del motor llama a este método, no a {@link #evaluar(Traza, Contexto)}. Desde
     * contratos v1.2.
     *
     * <p>La implementación por defecto llama a {@link #evaluar(Traza, Contexto)}: devuelve {@code
     * NO_CUMPLE} si algún hallazgo tiene confianza distinta de {@link Confianza#INFO} y, si no,
     * {@code CUMPLE} con los hallazgos {@code INFO} que hubiera.
     *
     * @param traza traza del caso; puede ser null en el momento {@link Momento#SUITE}
     * @param ctx contexto de la evaluación; nunca es null
     * @return veredicto del oráculo; nunca null
     */
    default Veredicto evaluarResultado(Traza traza, Contexto ctx) {
        List<Hallazgo> hallazgos = evaluar(traza, ctx);
        boolean falla = hallazgos.stream().anyMatch(h -> h.confianza() != Confianza.INFO);
        return new Veredicto(
                falla ? EstadoVeredicto.NO_CUMPLE : EstadoVeredicto.CUMPLE, hallazgos, null);
    }
}
