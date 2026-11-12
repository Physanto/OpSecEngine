package io.github.physanto.opsecengine.oraculos;

import io.github.physanto.opsecengine.dominio.caso.Senal;
import io.github.physanto.opsecengine.dominio.resultado.Confianza;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.resultado.Veredicto;
import io.github.physanto.opsecengine.dominio.traza.Evento;
import io.github.physanto.opsecengine.dominio.traza.TipoEvento;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import java.util.ArrayList;
import java.util.List;

/**
 * Evalúa el resultado de un escenario ejecutado de doble clic, cierre o fallo inyectado. No ejecuta
 * acciones ni inyecta fallos; es inmutable y seguro entre hilos.
 */
public final class EvaluadorEscenarioRobustez {
    /** Crea el evaluador local, no registrado por SPI. */
    public EvaluadorEscenarioRobustez() {}

    /**
     * Observa un intervalo de pasos explícito y comprueba señales de resultado de la aplicación. El
     * llamador acredita que el estímulo o fallo previsto se ejecutó en ese intervalo.
     *
     * @param traza traza del escenario; puede ser null
     * @param pasoInicio primera acción del escenario
     * @param pasoFinal última acción que solicita el resultado
     * @param esperado señal esperada de respuesta controlada, atribuida al paso final
     * @param fallo señal explícita de resultado incorrecto, atribuida al paso final
     * @return fallo ante evidencia explícita; CUMPLE solo con final OK y respuesta esperada
     * @throws IllegalArgumentException si los pasos o señales son incoherentes
     */
    public Veredicto evaluar(
            Traza traza, int pasoInicio, int pasoFinal, Senal esperado, Senal fallo) {
        if (pasoInicio < 1
                || pasoFinal < pasoInicio
                || esperado == null
                || fallo == null
                || esperado.equals(fallo)) {
            throw new IllegalArgumentException("Se requieren pasos ordenados y señales distintas");
        }
        if (traza == null) {
            return Veredicto.noConcluyente("Falta la traza del escenario");
        }
        List<Evento> eventos =
                traza.eventos().stream()
                        .filter(e -> traza.casoId().equals(e.contexto().casoId()))
                        .toList();
        List<Evento> comienzos = inicios(eventos, pasoInicio);
        List<Evento> finales = inicios(eventos, pasoFinal);
        if (comienzos.size() != 1
                || finales.size() != 1
                || comienzos.getFirst().secuencia() > finales.getFirst().secuencia()) {
            return Veredicto.noConcluyente(
                    "Los pasos del escenario deben identificarse sin reintentos ambiguos");
        }
        long desde = comienzos.getFirst().secuencia();
        long envio = finales.getFirst().secuencia();
        long hasta =
                eventos.stream()
                        .filter(e -> e.tipo() == TipoEvento.PASO_INICIO && e.secuencia() > envio)
                        .mapToLong(Evento::secuencia)
                        .min()
                        .orElse(Long.MAX_VALUE);
        List<Evento> intervalo =
                eventos.stream()
                        .filter(e -> e.secuencia() >= desde && e.secuencia() < hasta)
                        .toList();
        Traza parcial = new Traza(traza.casoId(), intervalo);
        List<Hallazgo> hallazgos = new ArrayList<>();
        hallazgos.addAll(new OraculoExcepcionNoCapturada().evaluar(parcial, null));
        hallazgos.addAll(new OraculoAppTerminada().evaluar(parcial, null));
        List<Evento> respuestas =
                intervalo.stream()
                        .filter(e ->
                                e.secuencia() > envio && e.contexto().numeroPaso() == pasoFinal)
                        .toList();
        for (Evento evento : respuestas) {
            if (fallo.coincide(evento)) {
                hallazgos.add(
                        new Hallazgo(
                                "RESULTADO-ROBUSTEZ-INCORRECTO",
                                null,
                                Confianza.PROBABLE,
                                "Resultado incorrecto tras el escenario de robustez",
                                "Se observó la señal configurada como resultado incorrecto tras el estímulo declarado. Confirme el efecto y las condiciones del escenario.",
                                List.of(desde, evento.secuencia())));
            }
        }
        if (!hallazgos.isEmpty()) {
            return Veredicto.noCumple(hallazgos);
        }
        if (eventos.stream()
                .anyMatch(e ->
                        e.secuencia() >= hasta
                                && e.contexto().numeroPaso() == pasoFinal
                                && (esperado.coincide(e) || fallo.coincide(e)))) {
            return Veredicto.noConcluyente(
                    "Hay respuestas tardías fuera del intervalo del escenario");
        }
        List<Evento> fin =
                respuestas.stream().filter(e -> e.tipo() == TipoEvento.PASO_FIN).toList();
        if (fin.size() != 1
                || !fin.getFirst().texto("codigo").orElse("").equals("OK")
                || !terminaronBien(intervalo)
                || respuestas.stream().noneMatch(esperado::coincide)) {
            return Veredicto.noConcluyente(
                    "Falta una finalización correcta y una respuesta controlada explícita");
        }
        return Veredicto.cumple();
    }

    private static boolean terminaronBien(List<Evento> eventos) {
        List<Evento> acciones =
                eventos.stream().filter(e -> e.tipo() == TipoEvento.PASO_INICIO).toList();
        for (int i = 0; i < acciones.size(); i++) {
            Evento inicio = acciones.get(i);
            long limite =
                    i + 1 < acciones.size() ? acciones.get(i + 1).secuencia() : Long.MAX_VALUE;
            List<Evento> finales =
                    eventos.stream()
                            .filter(e ->
                                    e.tipo() == TipoEvento.PASO_FIN
                                            && e.contexto().numeroPaso()
                                                    == inicio.contexto().numeroPaso()
                                            && e.secuencia() > inicio.secuencia()
                                            && e.secuencia() < limite)
                            .toList();
            if (finales.size() != 1
                    || !finales.getFirst().texto("codigo").orElse("").equals("OK")) {
                return false;
            }
        }
        return true;
    }

    private static List<Evento> inicios(List<Evento> eventos, int paso) {
        return eventos.stream()
                .filter(e ->
                        e.tipo() == TipoEvento.PASO_INICIO && e.contexto().numeroPaso() == paso)
                .toList();
    }
}
