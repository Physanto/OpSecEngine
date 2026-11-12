package io.github.physanto.opsecengine.oraculos;

import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.extension.Contexto;
import io.github.physanto.opsecengine.dominio.extension.Momento;
import io.github.physanto.opsecengine.dominio.extension.Oraculo;
import io.github.physanto.opsecengine.dominio.resultado.Confianza;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.traza.TipoEvento;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import java.util.List;
import java.util.Optional;

/**
 * Detecta que la aplicación terminó sin que el motor lo pidiera.
 *
 * <p>Produce un hallazgo confirmado por cada evento {@code APP_TERMINADA} cuyo campo {@code
 * esperada} sea el booleano false. No infiere el cierre por ventanas cerradas, por la causa ni por
 * el código de salida: incluso una salida con código cero puede ser inesperada. Ignora eventos sin
 * el booleano requerido o con datos de otro tipo. No tiene CWE asociado.
 *
 * <p>No tiene estado mutable y es seguro entre hilos.
 */
public final class OraculoAppTerminada implements Oraculo {
    /** Identificador reservado en el catálogo de oráculos. */
    public static final String ID = "APP-TERMINADA";

    /** Crea el oráculo para su descubrimiento con {@link java.util.ServiceLoader}. */
    public OraculoAppTerminada() {}

    /**
     * Devuelve el identificador del catálogo.
     *
     * @return {@value #ID}
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Devuelve el momento de evaluación.
     *
     * @return {@link Momento#EVENTO}; cada terminación se juzga por separado
     */
    @Override
    public Momento momento() {
        return Momento.EVENTO;
    }

    /**
     * Indica si el oráculo aplica al caso.
     *
     * @param caso caso evaluado
     * @param contexto contexto de evaluación
     * @return siempre true; cualquier caso puede interrumpirse por un cierre inesperado
     */
    @Override
    public boolean aplica(Caso caso, Contexto contexto) {
        return true;
    }

    /**
     * Busca eventos que declaran una terminación inesperada.
     *
     * @param traza traza observada; puede ser null
     * @param contexto contexto de evaluación; no se utiliza
     * @return un hallazgo por cada cierre inesperado, o una lista vacía
     */
    @Override
    public List<Hallazgo> evaluar(Traza traza, Contexto contexto) {
        if (traza == null) {
            return List.of();
        }
        return traza.deTipo(TipoEvento.APP_TERMINADA).stream()
                .filter(evento -> evento.booleano("esperada").equals(Optional.of(false)))
                .map(evento ->
                        new Hallazgo(
                                ID,
                                null,
                                Confianza.CONFIRMADO,
                                "Cierre inesperado de la aplicación",
                                "La aplicación terminó sin que el motor solicitara su cierre. El evento"
                                        + " APP_TERMINADA declara esperada=false; revise la causa de la"
                                        + " terminación y evite interrumpir el caso antes de completarlo.",
                                List.of(evento.secuencia())))
                .toList();
    }
}
