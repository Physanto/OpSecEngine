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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Compara operaciones alcanzadas por los contextos de una suite con la política del proyecto. Es
 * inmutable y seguro entre hilos; no infiere sesiones ni jerarquías entre roles.
 */
public final class OraculoAutorizacionDiferencial implements Oraculo {
    private static final String ID = "AUTORIZACION-DIFERENCIAL";

    /** Crea el oráculo para su descubrimiento mediante ServiceLoader. */
    public OraculoAutorizacionDiferencial() {}

    /**
     * Devuelve el identificador reservado.
     *
     * @return identificador del catálogo
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Devuelve el momento de comparación.
     *
     * @return SUITE
     */
    @Override
    public Momento momento() {
        return Momento.SUITE;
    }

    /**
     * Comprueba que el contexto corresponde a una suite.
     *
     * @param caso caso recibido; no se utiliza
     * @param contexto contexto de evaluación; puede ser null
     * @return true cuando hay contexto sin caso
     */
    @Override
    public boolean aplica(Caso caso, Contexto contexto) {
        return contexto != null && contexto.caso() == null;
    }

    /**
     * Compara títulos de ventanas y ejecuciones de acciones con texto de operación exacto,
     * ignorando mayúsculas y espacios exteriores. Sin política solo informa coincidencias entre
     * contextos distintos. Un SQL del mismo paso con filas positivas confirma el efecto.
     *
     * @param traza traza individual; se ignora en favor de las trazas del contexto
     * @param contexto contexto con todas las trazas ejecutadas
     * @return hallazgos con caso y secuencias, sin consultas ni valores de la aplicación
     */
    @Override
    public List<Hallazgo> evaluar(Traza traza, Contexto contexto) {
        if (!aplica(null, contexto)) {
            return List.of();
        }
        Map<String, List<String>> politica = new HashMap<>();
        contexto.proyecto()
                .politica()
                .forEach((operacion, roles) ->
                        politica.computeIfAbsent(normalizar(operacion), clave -> new ArrayList<>())
                                .addAll(roles));
        List<Observacion> observaciones = new ArrayList<>();
        Set<String> casos = new HashSet<>();
        contexto.suite().casos().forEach(c -> casos.add(c.id()));
        for (Traza actual : contexto.trazasSuite()) {
            if (!casos.contains(actual.casoId())) {
                continue;
            }
            observaciones.addAll(observar(actual, contexto));
        }
        Map<String, Set<String>> contextos = new HashMap<>();
        observaciones.forEach(o ->
                contextos.computeIfAbsent(o.operacion(), clave -> new HashSet<>()).add(o.rol()));
        List<Hallazgo> resultado = new ArrayList<>();
        for (Observacion observacion : observaciones) {
            List<String> roles = politica.get(observacion.operacion());
            boolean candidato = politica.isEmpty();
            if (candidato) {
                if (contextos.get(observacion.operacion()).size() < 2) {
                    continue;
                }
            } else if (roles == null
                    || (observacion.rol() != null && roles.contains(observacion.rol()))) {
                continue;
            }
            resultado.add(
                    new Hallazgo(
                            ID,
                            "CWE-862",
                            candidato
                                    ? Confianza.INFO
                                    : observacion.confirmada()
                                            ? Confianza.CONFIRMADO
                                            : Confianza.PROBABLE,
                            candidato
                                    ? "Operación compartida entre contextos"
                                    : "Operación fuera de la política de roles",
                            "Caso "
                                    + observacion.casoId()
                                    + ": "
                                    + (candidato
                                            ? "se alcanzó una operación también observada en otro contexto. Defina la política antes de juzgar su autorización."
                                            : "se alcanzó una operación sin un rol autorizado por la política. "
                                                    + (observacion.confirmada()
                                                            ? "SQL confirma filas afectadas en el mismo paso."
                                                            : "No se ha confirmado el efecto sobre los datos.")),
                            observacion.evidencia()));
        }
        return List.copyOf(resultado);
    }

    private static List<Observacion> observar(Traza traza, Contexto contexto) {
        Map<Integer, Evento> acciones = new HashMap<>();
        Map<OperacionRol, Observacion> resultado = new LinkedHashMap<>();
        for (Evento evento : traza.eventos()) {
            if (!traza.casoId().equals(evento.contexto().casoId())) {
                continue;
            }
            int paso = evento.contexto().numeroPaso();
            if (evento.tipo() == TipoEvento.PASO_INICIO) {
                acciones.clear();
                if (evento.texto("accion").orElse("").equals("PULSAR")) {
                    acciones.put(paso, evento);
                }
            }
            if (evento.tipo() == TipoEvento.PASO_FIN) {
                acciones.remove(paso);
            }
            String operacion = "";
            List<Long> evidencia = List.of(evento.secuencia());
            boolean confirmada = false;
            if (evento.tipo() == TipoEvento.VENTANA_ABIERTA) {
                operacion = evento.texto("titulo").orElse("");
            } else if (evento.tipo() == TipoEvento.SQL || evento.tipo() == TipoEvento.FIRESTORE) {
                var sensibles =
                        evento.tipo() == TipoEvento.SQL
                                ? contexto.proyecto().sensibles().sql()
                                : contexto.proyecto().sensibles().firestore();
                if (sensibles.stream()
                        .noneMatch(s ->
                                s.equalsIgnoreCase(evento.texto("operacion").orElse("").strip()))) {
                    continue;
                }
                Evento accion = acciones.get(paso);
                if (accion == null
                        || !Objects.equals(accion.contexto(), evento.contexto())
                        || (evento.datos().containsKey("error")
                                && !evento.texto("error").map(String::isBlank).orElse(false))) {
                    continue;
                }
                String objetivo = accion.texto("objetivo").orElse("");
                if (objetivo.startsWith("texto=")) {
                    operacion = objetivo.substring("texto=".length());
                }
                confirmada =
                        evento.tipo() == TipoEvento.SQL
                                && evento.numero("filasAfectadas")
                                        .map(n ->
                                                Double.isFinite(n.doubleValue())
                                                        && n.doubleValue() > 0
                                                        && n.doubleValue()
                                                                == Math.rint(n.doubleValue()))
                                        .orElse(false);
                evidencia = List.of(accion.secuencia(), evento.secuencia());
            }
            operacion = normalizar(operacion);
            if (operacion.isEmpty()) {
                continue;
            }
            String rol = evento.contexto().sesionActiva() ? evento.contexto().rol() : null;
            OperacionRol clave = new OperacionRol(operacion, rol);
            Observacion previa = resultado.get(clave);
            if (previa == null || (!previa.confirmada() && confirmada)) {
                resultado.put(
                        clave,
                        new Observacion(traza.casoId(), operacion, rol, confirmada, evidencia));
            }
        }
        return List.copyOf(resultado.values());
    }

    private static String normalizar(String texto) {
        return texto.strip().toLowerCase(Locale.ROOT);
    }

    private record Observacion(
            String casoId,
            String operacion,
            String rol,
            boolean confirmada,
            List<Long> evidencia) {}

    private record OperacionRol(String operacion, String rol) {}
}
