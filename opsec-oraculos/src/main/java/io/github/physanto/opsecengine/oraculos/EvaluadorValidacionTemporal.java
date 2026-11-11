package io.github.physanto.opsecengine.oraculos;

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
 * Evaluación local de una entrada declarada inválida, pendiente de aprobación de contrato (#38).
 *
 * <p>No implementa Oraculo ni se registra con ServiceLoader: el contrato actual solo devuelve
 * hallazgos y perdería el NO_CONCLUYENTE por falta de evidencia. La integración debe conservar el
 * Veredicto completo. El id y el CWE usados son propuestas, no altas en los catálogos oficiales. No
 * tiene estado mutable y es seguro entre hilos.
 */
public final class EvaluadorValidacionTemporal {
    /** Identificador propuesto, todavía no reservado en el catálogo. */
    public static final String ID_PROPUESTO = "ENTRADA-INVALIDA-ACEPTADA";

    /** CWE propuesto para validación incorrecta de entradas. */
    public static final String CWE_PROPUESTO = "CWE-20";

    /** Crea el evaluador local. */
    public EvaluadorValidacionTemporal() {}

    /**
     * Evalúa un caso usando exclusivamente una política explícita.
     *
     * <p>Exige una escritura LLENAR única con el valor declarado y un PASO_FIN OK antes de un envío
     * PULSAR único. Solo considera señales posteriores al envío, atribuidas al mismo caso y paso,
     * antes de que comience otra acción. El OK del envío no prueba aceptación. La aceptación
     * prevalece aunque también se muestre un rechazo; se informa como PROBABLE porque la causalidad
     * depende de la política aportada. Solo da CUMPLE si hay rechazo explícito y el envío terminó
     * OK.
     *
     * @param traza traza del caso, posiblemente incompleta o null
     * @param politica política para ese caso; puede ser null si todavía no está disponible
     * @return NO_CUMPLE por aceptación, CUMPLE por rechazo observado o NO_CONCLUYENTE si falta
     *     evidencia
     */
    public Veredicto evaluar(Traza traza, PoliticaValidacionTemporal politica) {
        if (traza == null || politica == null) {
            return Veredicto.noConcluyente("Falta la traza o la política explícita de validación");
        }
        if (!traza.casoId().equals(politica.casoId())) {
            return Veredicto.noConcluyente("La política corresponde a otro caso");
        }
        List<Evento> eventos =
                traza.eventos().stream()
                        .filter(e -> politica.casoId().equals(e.contexto().casoId()))
                        .toList();
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
                            ID_PROPUESTO,
                            CWE_PROPUESTO,
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
