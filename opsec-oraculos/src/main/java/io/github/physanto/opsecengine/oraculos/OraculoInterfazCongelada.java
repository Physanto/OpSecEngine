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

/** Detecta bloqueo del EDT superior al umbral declarado. Sin estado y seguro entre hilos. */
public final class OraculoInterfazCongelada implements Oraculo {
    /** Crea el oráculo para su descubrimiento mediante ServiceLoader. */
    public OraculoInterfazCongelada() {}

    /**
     * Devuelve el identificador del catálogo.
     *
     * @return INTERFAZ-CONGELADA
     */
    @Override
    public String id() {
        return "INTERFAZ-CONGELADA";
    }

    /**
     * Devuelve el momento de evaluación.
     *
     * @return EVENTO
     */
    @Override
    public Momento momento() {
        return Momento.EVENTO;
    }

    /**
     * Indica que cualquier caso puede bloquear el EDT.
     *
     * @param caso caso evaluado; puede ser null
     * @param contexto contexto de evaluación; puede ser null
     * @return true
     */
    @Override
    public boolean aplica(Caso caso, Contexto contexto) {
        return true;
    }

    /**
     * Evalúa medidas enteras no negativas con umbral positivo explícito; la igualdad no activa.
     *
     * @param traza traza del caso; puede ser null
     * @param contexto contexto opcional; su caso debe coincidir si está presente
     * @return hallazgos confirmados por el evento de medida, sin CWE ni datos de la aplicación
     */
    @Override
    public List<Hallazgo> evaluar(Traza traza, Contexto contexto) {
        if (traza == null
                || (contexto != null
                        && contexto.caso() != null
                        && !traza.casoId().equals(contexto.caso().id()))) {
            return List.of();
        }
        return traza.eventos().stream()
                .filter(e ->
                        traza.casoId().equals(e.contexto().casoId())
                                && e.tipo() == TipoEvento.TIEMPO
                                && e.texto("medida").orElse("").equals("EDT_BLOQUEADO"))
                .filter(e -> {
                    Long duracion = MedidasTiempo.entero(e.datos().get("duracionMs"));
                    Long umbral = MedidasTiempo.entero(e.datos().get("umbralMs"));
                    return duracion != null && umbral != null && umbral > 0 && duracion > umbral;
                })
                .map(e ->
                        new Hallazgo(
                                id(),
                                null,
                                Confianza.CONFIRMADO,
                                "Hilo de interfaz bloqueado",
                                "La medida EDT_BLOQUEADO supera el umbral positivo declarado en el evento. Revise el trabajo ejecutado en el hilo de interfaz.",
                                List.of(e.secuencia())))
                .toList();
    }
}
