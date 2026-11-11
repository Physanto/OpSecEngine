package io.github.physanto.opsecengine.oraculos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import io.github.physanto.opsecengine.dominio.caso.Accion;
import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.Localizador;
import io.github.physanto.opsecengine.dominio.caso.Paso;
import io.github.physanto.opsecengine.dominio.caso.Suite;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.dominio.extension.Contexto;
import io.github.physanto.opsecengine.dominio.extension.Momento;
import io.github.physanto.opsecengine.dominio.extension.Oraculo;
import io.github.physanto.opsecengine.dominio.resultado.Confianza;
import io.github.physanto.opsecengine.dominio.resultado.EstadoVeredicto;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.traza.Evento;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import io.github.physanto.opsecengine.soporte.CargadorTrazas;
import io.github.physanto.opsecengine.soporte.TrazaEjemplo;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

class EvaluadorOraculosTest {
    private final CargadorTrazas cargador = new CargadorTrazas();

    @Test
    void ejecutaCadaMomentoEnSuFronteraSinVerEventosFuturos() throws Exception {
        Traza traza = cargador.cargarEjemploUnico(TrazaEjemplo.N3_LOCAL_SIN_LOGIN);
        Contexto contexto = contexto(traza.casoId(), List.of());
        List<Traza> prefijos = new ArrayList<>();
        List<Traza> casos = new ArrayList<>();
        List<Contexto> suites = new ArrayList<>();
        EvaluadorOraculos evaluador =
                new EvaluadorOraculos(
                        List.of(
                                oraculo(
                                        "EVENTO",
                                        Momento.EVENTO,
                                        (t, c) -> {
                                            prefijos.add(t);
                                            return List.of();
                                        }),
                                oraculo(
                                        "CASO",
                                        Momento.CASO,
                                        (t, c) -> {
                                            casos.add(t);
                                            return List.of();
                                        }),
                                oraculo(
                                        "SUITE",
                                        Momento.SUITE,
                                        (t, c) -> {
                                            assertThat(t).isNull();
                                            assertThat(c.caso()).isNull();
                                            suites.add(c);
                                            return List.of();
                                        })));
        var sesion = evaluador.iniciarCaso(contexto);
        assertThat(prefijos).isEmpty();
        for (int i = 0; i < traza.eventos().size(); i++) {
            sesion.registrarEvento(traza.eventos().get(i));
            assertThat(prefijos).hasSize(i + 1);
            assertThat(prefijos.getLast().eventos())
                    .containsExactlyElementsOf(traza.eventos().subList(0, i + 1));
            assertThat(casos).isEmpty();
            assertThat(suites).isEmpty();
        }
        assertThat(sesion.terminar(null).estado()).isEqualTo(EstadoVeredicto.CUMPLE);
        assertThat(prefijos).hasSize(traza.eventos().size());
        assertThat(casos).containsExactly(traza);
        assertThat(suites).isEmpty();
        Contexto suite =
                Contexto.deSuite(contexto.proyecto(), contexto.suite(), List.of(sesion.traza()));
        assertThat(evaluador.evaluarSuite(suite).estado()).isEqualTo(EstadoVeredicto.CUMPLE);
        assertThat(suites).containsExactly(suite);
    }

    @Test
    void conservaContrasenasObservadasSinDuplicarHallazgos() throws Exception {
        Traza traza;
        try (var lector =
                new InputStreamReader(
                        getClass().getResourceAsStream("contrasena-en-consola.jsonl"),
                        StandardCharsets.UTF_8)) {
            traza = cargador.leer(lector, "contrasena-en-consola.jsonl").getFirst();
        }
        var evaluador = new EvaluadorOraculos(List.of(new OraculoContrasenaEnConsola()));
        var sesion = evaluador.iniciarCaso(contexto(traza.casoId(), List.of()));
        for (Evento evento : traza.eventos()) {
            var resultado = sesion.registrarEvento(evento);
            if (evento.secuencia() < 11) {
                assertThat(resultado.hallazgos()).isEmpty();
            } else {
                assertThat(resultado.estado()).isEqualTo(EstadoVeredicto.NO_CUMPLE);
                assertThat(resultado.hallazgos())
                        .singleElement()
                        .satisfies(h -> assertThat(h.evidencia()).containsExactly(11L));
            }
        }
        assertThat(sesion.terminar(null).hallazgos()).hasSize(1);
    }

    @Test
    void elCasoLimpioNoActivaLosOraculosIncluidos() throws Exception {
        Traza traza = cargador.cargarEjemploUnico(TrazaEjemplo.CASO_LIMPIO);
        var evaluador =
                new EvaluadorOraculos(
                        List.of(
                                new OraculoContrasenaEnConsola(),
                                new OraculoExcepcionNoCapturada()));
        var sesion = evaluador.iniciarCaso(contexto(traza.casoId(), List.of()));
        traza.eventos().forEach(sesion::registrarEvento);
        assertThat(sesion.terminar(null).estado()).isEqualTo(EstadoVeredicto.CUMPLE);
    }

    @Test
    void comparaLasDosTrazasDeSuiteSinEjecutarseAntes() throws Exception {
        List<Traza> trazas = cargador.cargarEjemplo(TrazaEjemplo.C01_C02_MENSAJES);
        var regla =
                oraculo(
                        "SUITE",
                        Momento.SUITE,
                        (t, c) -> {
                            assertThat(c.trazasSuite()).containsExactlyElementsOf(trazas);
                            return List.of(hallazgo("SUITE", Confianza.PROBABLE, 1));
                        });
        var evaluador = new EvaluadorOraculos(List.of(regla));
        for (Traza traza : trazas) {
            var sesion = evaluador.iniciarCaso(contexto(traza.casoId(), List.of()));
            traza.eventos().forEach(sesion::registrarEvento);
            assertThat(sesion.terminar(null).hallazgos()).isEmpty();
        }
        Contexto c = contexto(trazas.getFirst().casoId(), List.of());
        assertThat(
                        evaluador
                                .evaluarSuite(Contexto.deSuite(c.proyecto(), c.suite(), trazas))
                                .estado())
                .isEqualTo(EstadoVeredicto.NO_CUMPLE);
    }

    @Test
    void respetaAplicabilidadYLaSeleccionExplicitaSinCambiarElMomento() {
        var ignorado =
                new OraculoPrueba(
                        "IGNORADO",
                        Momento.CASO,
                        false,
                        (t, c) -> {
                            throw new AssertionError("No debe evaluarse");
                        });
        var solicitado =
                new OraculoPrueba(
                        "PEDIDO",
                        Momento.CASO,
                        false,
                        (t, c) -> List.of(hallazgo("PEDIDO", Confianza.CONFIRMADO, 1)));
        var sesion =
                new EvaluadorOraculos(List.of(ignorado, solicitado))
                        .iniciarCaso(contexto("X", List.of("PEDIDO")));
        assertThat(sesion.terminar(null).hallazgos())
                .extracting(Hallazgo::oraculoId)
                .containsExactly("PEDIDO");
    }

    @Test
    void unFalloNoImpideEvaluarLosDemasYNoSeConfundeConCumple() {
        Oraculo roto =
                oraculo(
                        "ROTO",
                        Momento.CASO,
                        (t, c) -> {
                            throw new IllegalStateException("fallo");
                        });
        Oraculo informativo =
                oraculo(
                        "INFO",
                        Momento.CASO,
                        (t, c) -> List.of(hallazgo("INFO", Confianza.INFO, 1)));
        var sesion =
                new EvaluadorOraculos(List.of(roto, informativo))
                        .iniciarCaso(contexto("X", List.of("AUSENTE")));
        var resultado = sesion.terminar("Ejecución interrumpida");
        assertThat(resultado.estado()).isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
        assertThat(resultado.hallazgos()).hasSize(1);
        assertThat(resultado.motivo()).contains("ROTO", "AUSENTE", "Ejecución interrumpida");
    }

    @Test
    void losFallosDeAplicabilidadYDeSuiteTambienSonNoConcluyentes() {
        Oraculo roto =
                new Oraculo() {
                    @Override
                    public String id() {
                        return "ROTO";
                    }

                    @Override
                    public Momento momento() {
                        return Momento.EVENTO;
                    }

                    @Override
                    public boolean aplica(Caso caso, Contexto c) {
                        throw new IllegalStateException();
                    }

                    @Override
                    public List<Hallazgo> evaluar(Traza t, Contexto c) {
                        throw new AssertionError();
                    }
                };
        var sesion = new EvaluadorOraculos(List.of(roto)).iniciarCaso(contexto("X", List.of()));
        assertThat(sesion.registrarEvento(evento("X", 1)).estado())
                .isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
        assertThat(sesion.terminar(null).estado()).isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
        var evaluador =
                new EvaluadorOraculos(
                        List.of(
                                oraculo(
                                        "SUITE",
                                        Momento.SUITE,
                                        (t, c) -> {
                                            throw new IllegalStateException();
                                        })));
        Contexto c = contexto("X", List.of());
        assertThat(
                        evaluador
                                .evaluarSuite(Contexto.deSuite(c.proyecto(), c.suite(), List.of()))
                                .estado())
                .isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
    }

    @Test
    void unHallazgoConfirmadoTienePrioridadSobreLosErrores() {
        var evaluador =
                new EvaluadorOraculos(
                        List.of(
                                oraculo(
                                        "FALLO",
                                        Momento.CASO,
                                        (t, c) ->
                                                List.of(
                                                        hallazgo(
                                                                "FALLO",
                                                                Confianza.CONFIRMADO,
                                                                1)))));
        var resultado =
                evaluador.iniciarCaso(contexto("X", List.of("AUSENTE"))).terminar("Interrumpido");
        assertThat(resultado.estado()).isEqualTo(EstadoVeredicto.NO_CUMPLE);
        assertThat(resultado.motivo()).contains("AUSENTE", "Interrumpido");
    }

    @Test
    void aislaLosCasosYRechazaEventosRepetidosAjenosOTardios() {
        var evaluador =
                new EvaluadorOraculos(
                        List.of(
                                oraculo(
                                        "EVENTO",
                                        Momento.EVENTO,
                                        (t, c) ->
                                                List.of(
                                                        hallazgo(
                                                                "EVENTO",
                                                                Confianza.PROBABLE,
                                                                t.eventos()
                                                                        .getLast()
                                                                        .secuencia())))));
        var primera = evaluador.iniciarCaso(contexto("X", List.of()));
        var segunda = evaluador.iniciarCaso(contexto("Y", List.of()));
        primera.registrarEvento(evento("X", 1));
        Traza instantanea = primera.traza();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> primera.registrarEvento(evento("X", 1)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> primera.registrarEvento(evento("X", 0)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> primera.registrarEvento(evento("Y", 2)));
        primera.registrarEvento(evento("X", 2));
        assertThat(instantanea.eventos()).hasSize(1);
        assertThat(primera.terminar(null).hallazgos()).hasSize(2);
        assertThat(segunda.terminar(null).estado()).isEqualTo(EstadoVeredicto.CUMPLE);
        assertThat(segunda.traza().eventos()).isEmpty();
        assertThatIllegalStateException().isThrownBy(() -> primera.registrarEvento(evento("X", 3)));
        assertThatIllegalStateException().isThrownBy(() -> primera.terminar(null));
    }

    @Test
    void rechazaIdsDuplicadosYContextosDelMomentoEquivocado() {
        Oraculo o = oraculo("X", Momento.CASO, (t, c) -> List.of());
        assertThatIllegalArgumentException().isThrownBy(() -> new EvaluadorOraculos(List.of(o, o)));
        Contexto c = contexto("X", List.of());
        var evaluador = new EvaluadorOraculos(List.of());
        assertThatIllegalArgumentException().isThrownBy(() -> evaluador.evaluarSuite(c));
        var sesion = evaluador.iniciarCaso(c);
        assertThatIllegalArgumentException().isThrownBy(() -> sesion.terminar(" "));
        assertThat(sesion.terminar(null).estado()).isEqualTo(EstadoVeredicto.CUMPLE);
    }

    private static Oraculo oraculo(
            String id, Momento momento, BiFunction<Traza, Contexto, List<Hallazgo>> funcion) {
        return new OraculoPrueba(id, momento, true, funcion);
    }

    private record OraculoPrueba(
            String id,
            Momento momento,
            boolean aplicable,
            BiFunction<Traza, Contexto, List<Hallazgo>> funcion)
            implements Oraculo {
        @Override
        public boolean aplica(Caso caso, Contexto contexto) {
            return aplicable;
        }

        @Override
        public List<Hallazgo> evaluar(Traza traza, Contexto contexto) {
            return funcion.apply(traza, contexto);
        }
    }

    private static Hallazgo hallazgo(String id, Confianza confianza, long secuencia) {
        return new Hallazgo(
                id,
                null,
                confianza,
                "Hallazgo de prueba",
                "Evidencia observada",
                List.of(secuencia));
    }

    private static Evento evento(String caso, long secuencia) {
        return new Evento(
                secuencia,
                java.time.Instant.EPOCH,
                io.github.physanto.opsecengine.dominio.traza.TipoEvento.CONSOLA,
                new io.github.physanto.opsecengine.dominio.traza.ContextoEvento(
                        caso, 0, null, false),
                null);
    }

    private static Contexto contexto(String id, List<String> oraculos) {
        Proyecto proyecto =
                new Proyecto(
                        "clientes",
                        Path.of("app.jar"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        Caso caso =
                new Caso(
                        id,
                        null,
                        null,
                        List.of(
                                new Paso(
                                        1,
                                        Accion.PULSAR,
                                        Localizador.porTexto("Buscar"),
                                        null,
                                        null,
                                        null)),
                        null,
                        oraculos,
                        null);
        return Contexto.deCaso(
                proyecto, new Suite("ejemplo", null, null, null, List.of(caso), null), caso);
    }
}
