package io.github.physanto.opsecengine.oraculos;

import io.github.physanto.opsecengine.dominio.extension.Contexto;
import io.github.physanto.opsecengine.dominio.resultado.Confianza;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.traza.Evento;
import io.github.physanto.opsecengine.dominio.traza.TipoEvento;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import java.util.ArrayList;
import java.util.List;

/** Rastreo local compartido por los oráculos de canarios en SQL y Firestore, sin estado. */
final class RastreadorCanarioBackend {
    private RastreadorCanarioBackend() {}

    /** Devuelve el canario declarado en el caso (contratos v1.2), o null si no hay. */
    static String canario(Contexto contexto) {
        return contexto == null || contexto.caso() == null ? null : contexto.caso().canario();
    }

    static List<Hallazgo> evaluar(Traza traza, Contexto contexto, String id, boolean sql) {
        String marca = canario(contexto);
        if (traza == null || marca == null || !traza.casoId().equals(contexto.caso().id())) {
            return List.of();
        }
        Evento origen = null;
        List<Hallazgo> resultado = new ArrayList<>();
        for (Evento evento : traza.eventos()) {
            if (!traza.casoId().equals(evento.contexto().casoId())) {
                continue;
            }
            if (evento.tipo() == TipoEvento.PASO_INICIO
                    && evento.texto("accion").orElse("").equals("LLENAR")
                    && evento.texto("valor").orElse("").contains(marca)) {
                origen = evento;
            }
            boolean destino =
                    sql
                            ? evento.tipo() == TipoEvento.SQL
                                    && evento.texto("tipoSentencia").orElse("").equals("STATEMENT")
                            : evento.tipo() == TipoEvento.FIRESTORE;
            if (origen == null
                    || !destino
                    || !evento.texto(sql ? "sql" : "ruta").orElse("").contains(marca)) {
                continue;
            }
            resultado.add(
                    new Hallazgo(
                            id,
                            "CWE-89",
                            Confianza.PROBABLE,
                            sql
                                    ? "Canario de entrada en SQL concatenado"
                                    : "Canario de entrada en una ruta Firestore",
                            "El canario declarado del payload de un LLENAR aparece posteriormente en "
                                    + (sql
                                            ? "el texto de una sentencia STATEMENT."
                                            : "la ruta de una operación Firestore.")
                                    + " La coincidencia indica propagación de la entrada; no demuestra una explotación ni un cambio en los datos.",
                            List.of(origen.secuencia(), evento.secuencia())));
        }
        return List.copyOf(resultado);
    }
}
