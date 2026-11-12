package io.github.physanto.opsecengine.oraculos;

import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.PoliticaValidacion;
import io.github.physanto.opsecengine.dominio.extension.Contexto;
import io.github.physanto.opsecengine.dominio.extension.Momento;
import io.github.physanto.opsecengine.dominio.extension.Oraculo;
import io.github.physanto.opsecengine.dominio.resultado.Confianza;
import io.github.physanto.opsecengine.dominio.resultado.EstadoVeredicto;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.resultado.Veredicto;
import io.github.physanto.opsecengine.dominio.traza.Evento;
import io.github.physanto.opsecengine.dominio.traza.TipoEvento;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Oráculo {@code ENTRADA-INVALIDA-ACEPTADA} (CWE-20): la aplicación acepta un valor que la política
 * de validación del caso declara inválido.
 *
 * <p>Solo aplica a casos con {@link Caso#validacion()} (contratos v1.2). Exige una escritura {@code
 * LLENAR} única con el valor declarado y un {@code PASO_FIN OK} antes de un envío {@code PULSAR}
 * único. Solo considera señales posteriores al envío, del mismo caso y paso, antes de que empiece
 * otra acción. El {@code OK} del envío no prueba aceptación. La aceptación prevalece aunque también
 * haya un rechazo y se informa como {@code PROBABLE}, porque la causalidad depende de la política.
 * Da {@code CUMPLE} solo si hay rechazo explícito y el envío terminó {@code OK}; si falta evidencia
 * responde {@code NO_CONCLUYENTE}.
 *
 * <p>No tiene estado y es seguro entre hilos.
 */
public final class OraculoEntradaInvalidaAceptada implements Oraculo {

    /** Identificador del catálogo. */
    public static final String ID = "ENTRADA-INVALIDA-ACEPTADA";

    /** Debilidad asociada: validación incorrecta de entradas. */
    public static final String CWE = "CWE-20";

    /** Crea el oráculo para su descubrimiento mediante ServiceLoader. */
    public OraculoEntradaInvalidaAceptada() {}

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
     * Aplica a los casos que declaran una política de validación.
     *
     * @param caso caso que se evalúa; puede ser null
     * @param ctx contexto de la evaluación; no se usa
     * @return {@code true} si el caso tiene {@link Caso#validacion()}
     */
    @Override
    public boolean aplica(Caso caso, Contexto ctx) {
        return caso != null && caso.validacion() != null;
    }

    /**
     * Devuelve los hallazgos de {@link #evaluarResultado(Traza, Contexto)}.
     *
     * @param traza traza del caso; puede ser null
     * @param ctx contexto con el caso y su política
     * @return un hallazgo {@code PROBABLE} si se observó aceptación; vacía en otro caso
     */
    @Override
    public List<Hallazgo> evaluar(Traza traza, Contexto ctx) {
        return evaluarResultado(traza, ctx).hallazgos();
    }

    /**
     * Evalúa el caso con su política de validación.
     *
     * @param traza traza del caso, posiblemente incompleta o null
     * @param ctx contexto con el caso y su política
     * @return {@code NO_CUMPLE} por aceptación, {@code CUMPLE} por rechazo observado o {@code
     *     NO_CONCLUYENTE} si falta evidencia
     */
    @Override
    public Veredicto evaluarResultado(Traza traza, Contexto ctx) {
        if (ctx == null || ctx.caso() == null || ctx.caso().validacion() == null) {
            return Veredicto.noConcluyente("El caso no declara una política de validación");
        }
        PoliticaValidacion politica = ctx.caso().validacion();
        String casoId = ctx.caso().id();
        if (traza == null) {
            return Veredicto.noConcluyente("Falta la traza del caso");
        }
        if (!traza.casoId().equals(casoId)) {
            return Veredicto.noConcluyente("La traza corresponde a otro caso");
        }
        List<Evento> eventos =
                traza.eventos().stream().filter(e -> casoId.equals(e.contexto().casoId())).toList();
        List<Evento> entradas = inicios(eventos, politica.pasoEntrada());
        List<Evento> envios = inicios(eventos, politica.pasoEnvio());
        if (entradas.size() != 1 || envios.size() != 1) {
            return Veredicto.noConcluyente(
                    "La escritura y el envío deben identificarse sin ambigüedad");
        }
        Evento entrada = entradas.getFirst();
        Evento envio = envios.getFirst();
        if (!entrada.texto("accion").orElse("").equals("LLENAR")
                || !entrada.texto("valor").filter(politica.valorInvalido()::equals).isPresent()
                || entrada.texto("objetivo").filter(objetivo -> !objetivo.isBlank()).isEmpty()
                || !envio.texto("accion").orElse("").equals("PULSAR")
                || entrada.secuencia() >= envio.secuencia()) {
            return Veredicto.noConcluyente(
                    "No se ha demostrado la escritura y el envío de la entrada declarada inválida");
        }
        List<Evento> finEntrada =
                finales(eventos, politica.pasoEntrada(), entrada.secuencia(), envio.secuencia());
        if (!terminoBien(finEntrada)) {
            return Veredicto.noConcluyente(
                    "No se ha confirmado que terminara la escritura del valor inválido");
        }
        if (eventos.stream()
                .anyMatch(e ->
                        e.tipo() == TipoEvento.PASO_INICIO
                                && e.secuencia() > entrada.secuencia()
                                && e.secuencia() < envio.secuencia()
                                && e.texto("accion").orElse("").equals("LLENAR")
                                && e.texto("objetivo").equals(entrada.texto("objetivo")))) {
            return Veredicto.noConcluyente("El campo volvió a escribirse antes del envío");
        }
        long limite =
                eventos.stream()
                        .filter(e ->
                                e.tipo() == TipoEvento.PASO_INICIO
                                        && e.secuencia() > envio.secuencia())
                        .mapToLong(Evento::secuencia)
                        .min()
                        .orElse(-1L);
        List<Evento> observados =
                eventos.stream()
                        .filter(e ->
                                e.secuencia() > envio.secuencia()
                                        && (limite < 0 || e.secuencia() < limite)
                                        && e.contexto().numeroPaso() == politica.pasoEnvio())
                        .toList();
        List<Evento> aceptados =
                observados.stream()
                        .filter(e -> politica.aceptacion().stream().anyMatch(s -> s.coincide(e)))
                        .toList();
        boolean finEnvio =
                terminoBien(finales(eventos, politica.pasoEnvio(), envio.secuencia(), limite));
        if (!aceptados.isEmpty()) {
            LinkedHashSet<Long> evidencia = new LinkedHashSet<>();
            evidencia.add(entrada.secuencia());
            evidencia.add(finEntrada.getFirst().secuencia());
            evidencia.add(envio.secuencia());
            aceptados.forEach(e -> evidencia.add(e.secuencia()));
            Hallazgo hallazgo =
                    new Hallazgo(
                            ID,
                            CWE,
                            Confianza.PROBABLE,
                            "Aceptación de una entrada declarada inválida",
                            "Tras escribir y enviar la entrada que la política declara inválida, se observó"
                                    + " una señal configurada como aceptación. Revise la política y confirme"
                                    + " el efecto de la operación antes de considerar confirmado el fallo.",
                            List.copyOf(evidencia));
            return new Veredicto(
                    EstadoVeredicto.NO_CUMPLE,
                    List.of(hallazgo),
                    finEnvio ? null : "No se ha confirmado la finalización correcta del envío");
        }
        if (!finEnvio) {
            return Veredicto.noConcluyente(
                    "No se ha confirmado la finalización correcta del envío");
        }
        if (limite >= 0
                && eventos.stream()
                        .anyMatch(e ->
                                e.secuencia() >= limite
                                        && e.contexto().numeroPaso() == politica.pasoEnvio()
                                        && politica.aceptacion().stream()
                                                .anyMatch(s -> s.coincide(e)))) {
            return Veredicto.noConcluyente(
                    "Hay una aceptación tardía cuya relación con el envío es ambigua");
        }
        if (observados.stream()
                .anyMatch(e -> politica.rechazo().stream().anyMatch(s -> s.coincide(e)))) {
            return Veredicto.cumple();
        }
        return Veredicto.noConcluyente(
                "No hay evidencia explícita de aceptación ni de rechazo de la entrada");
    }

    private static List<Evento> inicios(List<Evento> eventos, int paso) {
        return eventos.stream()
                .filter(e ->
                        e.tipo() == TipoEvento.PASO_INICIO && e.contexto().numeroPaso() == paso)
                .toList();
    }

    private static List<Evento> finales(List<Evento> eventos, int paso, long desde, long hasta) {
        return eventos.stream()
                .filter(e ->
                        e.tipo() == TipoEvento.PASO_FIN
                                && e.contexto().numeroPaso() == paso
                                && e.secuencia() > desde
                                && (hasta < 0 || e.secuencia() < hasta))
                .toList();
    }

    private static boolean terminoBien(List<Evento> finales) {
        return finales.size() == 1 && finales.getFirst().texto("codigo").orElse("").equals("OK");
    }
}
