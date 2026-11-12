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
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Detecta operaciones sensibles de SQL o Firestore observadas sin sesión activa.
 *
 * <p>Usa las operaciones configuradas en el proyecto y la sesión del contexto de cada evento. Una
 * cantidad entera positiva de filas afectadas confirma el efecto SQL; sin ella el hallazgo es
 * probable. Descarta operaciones con error. No infiere autenticación por el rol ni relaciona
 * estados de backend sin una asociación explícita. No conserva estado entre evaluaciones.
 */
public final class OraculoSinSesion implements Oraculo {
    /** Identificador reservado en el catálogo de oráculos. */
    public static final String ID = "SIN-SESION";

    private static final Pattern PRIMERA_PALABRA =
            Pattern.compile(
                    "\\A(?:\\s+|--[^\\r\\n]*(?:\\r?\\n|\\z)|/\\*[\\s\\S]*?\\*/)*([a-zA-Z]+)\\b");

    /** Crea el oráculo para su descubrimiento con {@link java.util.ServiceLoader}. */
    public OraculoSinSesion() {}

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
     * @return {@link Momento#CASO}
     */
    @Override
    public Momento momento() {
        return Momento.CASO;
    }

    /**
     * Comprueba que hay un proyecto con operaciones sensibles configuradas.
     *
     * @param caso caso evaluado
     * @param contexto contexto del proyecto
     * @return true si hay al menos una operación sensible
     */
    @Override
    public boolean aplica(Caso caso, Contexto contexto) {
        return contexto != null
                && contexto.proyecto() != null
                && (!contexto.proyecto().sensibles().sql().isEmpty()
                        || !contexto.proyecto().sensibles().firestore().isEmpty());
    }

    /**
     * Evalúa las operaciones del propio caso, sin incluir consultas ni errores de backend.
     *
     * @param traza eventos observados; puede ser null
     * @param contexto contexto con las operaciones sensibles del proyecto
     * @return un hallazgo por operación sensible sin sesión; evidencia sin datos del backend
     */
    @Override
    public List<Hallazgo> evaluar(Traza traza, Contexto contexto) {
        if (traza == null || !aplica(null, contexto)) {
            return List.of();
        }
        List<Hallazgo> hallazgos = new ArrayList<>();
        for (Evento evento : traza.eventos()) {
            if (!traza.casoId().equals(evento.contexto().casoId())
                    || evento.contexto().sesionActiva()
                    || (evento.datos().containsKey("error")
                            && !evento.texto("error").map(String::isBlank).orElse(false))) {
                continue;
            }
            boolean sql = evento.tipo() == TipoEvento.SQL;
            Set<String> sensibles;
            if (sql) {
                sensibles = contexto.proyecto().sensibles().sql();
            } else if (evento.tipo() == TipoEvento.FIRESTORE) {
                sensibles = contexto.proyecto().sensibles().firestore();
            } else {
                continue;
            }
            String operacion = evento.texto("operacion").orElse("").strip();
            if (sql && operacion.isEmpty()) {
                var coincidencia = PRIMERA_PALABRA.matcher(evento.texto("sql").orElse(""));
                operacion = coincidencia.find() ? coincidencia.group(1) : "";
            }
            String normalizada = operacion.toUpperCase(Locale.ROOT);
            if (normalizada.isEmpty()
                    || sensibles.stream()
                            .noneMatch(valor ->
                                    valor.strip().toUpperCase(Locale.ROOT).equals(normalizada))) {
                continue;
            }
            boolean confirmado =
                    sql
                            && evento.numero("filasAfectadas")
                                    .map(n ->
                                            n.doubleValue() > 0
                                                    && Double.isFinite(n.doubleValue())
                                                    && n.doubleValue()
                                                            == Math.rint(n.doubleValue()))
                                    .orElse(false);
            hallazgos.add(
                    new Hallazgo(
                            ID,
                            "CWE-306",
                            confirmado ? Confianza.CONFIRMADO : Confianza.PROBABLE,
                            "Operación sensible sin sesión",
                            "Se observó una operación sensible de "
                                    + (sql ? "SQL" : "Firestore")
                                    + " con sesionActiva=false. "
                                    + (confirmado
                                            ? "El backend informa filas afectadas. "
                                            : "El evento no confirma el efecto sobre los datos. ")
                                    + "Compruebe la autenticación antes de permitir la operación.",
                            List.of(evento.secuencia())));
        }
        return List.copyOf(hallazgos);
    }
}
