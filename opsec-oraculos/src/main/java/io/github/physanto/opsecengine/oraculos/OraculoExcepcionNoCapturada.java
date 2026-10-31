package io.github.physanto.opsecengine.oraculos;

import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.extension.Contexto;
import io.github.physanto.opsecengine.dominio.extension.Momento;
import io.github.physanto.opsecengine.dominio.extension.Oraculo;
import io.github.physanto.opsecengine.dominio.resultado.Confianza;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.traza.Evento;
import io.github.physanto.opsecengine.dominio.traza.TipoEvento;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Oráculo {@value #ID}: una excepción llegó al manejador del hilo sin que la aplicación la
 * capturara.
 *
 * <p>Revisa cada evento {@code EXCEPCION} de la traza y produce un hallazgo {@code CONFIRMADO} por
 * cada uno cuyo campo {@code capturada} sea {@code false}, con la secuencia del evento como
 * evidencia. Los eventos sin ese campo, o con un valor que no es booleano, se ignoran: el oráculo
 * no adivina. No tiene CWE asociado (ver {@code contratos/catalogos/oraculos.yaml}).
 *
 * <p>Es el ejemplo de referencia para escribir oráculos: no tiene estado, no lanza excepciones por
 * datos ausentes y se registra en {@code
 * META-INF/services/io.github.physanto.opsecengine.dominio.extension.Oraculo}. Es seguro entre
 * hilos.
 */
public final class OraculoExcepcionNoCapturada implements Oraculo {

    /** Id del oráculo en el catálogo. */
    public static final String ID = "EXCEPCION-NO-CAPTURADA";

    /** Crea el oráculo; lo usa {@link java.util.ServiceLoader}. */
    public OraculoExcepcionNoCapturada() {}

    /**
     * Devuelve el id del oráculo.
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
     * @return {@link Momento#EVENTO}, porque cada excepción se juzga por separado
     */
    @Override
    public Momento momento() {
        return Momento.EVENTO;
    }

    /**
     * Indica si el oráculo aplica al caso.
     *
     * @param caso caso que se evalúa
     * @param ctx contexto de la evaluación
     * @return siempre {@code true}: una excepción sin capturar es un fallo en cualquier caso
     */
    @Override
    public boolean aplica(Caso caso, Contexto ctx) {
        return true;
    }

    /**
     * Busca excepciones no capturadas en la traza.
     *
     * @param traza traza del caso; si es null no hay nada que evaluar
     * @param ctx contexto de la evaluación
     * @return un hallazgo por cada evento {@code EXCEPCION} con {@code capturada=false}; lista
     *     vacía si no hay ninguno
     */
    @Override
    public List<Hallazgo> evaluar(Traza traza, Contexto ctx) {
        if (traza == null) {
            return List.of();
        }
        List<Hallazgo> hallazgos = new ArrayList<>();
        for (Evento evento : traza.deTipo(TipoEvento.EXCEPCION)) {
            if (evento.booleano("capturada").equals(Optional.of(false))) {
                hallazgos.add(hallazgo(evento));
            }
        }
        return hallazgos;
    }

    private static Hallazgo hallazgo(Evento evento) {
        String clase = evento.texto("clase").orElse("desconocida");
        String hilo = evento.texto("hilo").orElse("desconocido");
        String mensaje = evento.texto("mensaje").map(texto -> " (" + texto + ")").orElse("");
        return new Hallazgo(
                ID,
                null,
                Confianza.CONFIRMADO,
                "Excepción no capturada: " + clase,
                "La excepción "
                        + clase
                        + mensaje
                        + " llegó al manejador del hilo "
                        + hilo
                        + " sin que la aplicación la capturara. El usuario puede ver la"
                        + " aplicación detenida o en un estado inconsistente; la aplicación debe"
                        + " capturarla y mostrar un mensaje claro.",
                List.of(evento.secuencia()));
    }
}
