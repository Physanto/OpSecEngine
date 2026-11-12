package io.github.physanto.opsecengine.oraculos;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.Suite;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.dominio.extension.Contexto;
import io.github.physanto.opsecengine.dominio.extension.Momento;
import io.github.physanto.opsecengine.dominio.extension.Oraculo;
import io.github.physanto.opsecengine.dominio.resultado.Confianza;
import io.github.physanto.opsecengine.dominio.resultado.EstadoVeredicto;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.traza.ContextoEvento;
import io.github.physanto.opsecengine.dominio.traza.Evento;
import io.github.physanto.opsecengine.dominio.traza.TipoEvento;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import io.github.physanto.opsecengine.soporte.CargadorTrazas;
import io.github.physanto.opsecengine.soporte.TrazaEjemplo;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OraculoAppTerminadaTest {
    private final OraculoAppTerminada oraculo = new OraculoAppTerminada();

    @Test
    void detectaLaTerminacionInesperadaInclusoConCodigoCero() throws Exception {
        Traza traza = cargar("app-terminada-inesperada.jsonl");
        assertThat(oraculo.evaluar(traza, contexto()))
                .singleElement()
                .satisfies(h -> {
                    assertThat(h.oraculoId()).isEqualTo("APP-TERMINADA");
                    assertThat(h.cwe()).isNull();
                    assertThat(h.confianza()).isEqualTo(Confianza.CONFIRMADO);
                    assertThat(h.evidencia()).containsExactly(1L);
                });
        var sesion = new EvaluadorOraculos(List.of(oraculo)).iniciarCaso(contexto());
        assertThat(sesion.registrarEvento(traza.eventos().getFirst()).hallazgos()).isEmpty();
        assertThat(sesion.registrarEvento(traza.eventos().getLast()).estado())
                .isEqualTo(EstadoVeredicto.NO_CUMPLE);
        var resultado = sesion.terminar("La app terminó durante el paso");
        assertThat(resultado.estado()).isEqualTo(EstadoVeredicto.NO_CUMPLE);
        assertThat(resultado.hallazgos()).hasSize(1);
        assertThat(resultado.motivo()).contains("durante el paso");
    }

    @Test
    void noActivaConElCierreSolicitadoNiConUnaTrazaLimpia() throws Exception {
        assertThat(oraculo.evaluar(cargar("app-terminada-esperada.jsonl"), contexto())).isEmpty();
        assertThat(
                        oraculo.evaluar(
                                new CargadorTrazas().cargarEjemploUnico(TrazaEjemplo.CASO_LIMPIO),
                                contexto()))
                .isEmpty();
        assertThat(oraculo.evaluar(null, contexto())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"SYSTEM_EXIT", "FIN_NORMAL", "PROCESO_TERMINADO", "CERRAR"})
    void elBooleanoEsperadaEsLaFuenteDeDecision(String causa) {
        assertThat(
                        oraculo.evaluar(
                                traza(
                                        TipoEvento.APP_TERMINADA,
                                        Map.of("esperada", false, "causa", causa)),
                                contexto()))
                .hasSize(1);
        assertThat(
                        oraculo.evaluar(
                                traza(
                                        TipoEvento.APP_TERMINADA,
                                        Map.of(
                                                "esperada",
                                                true,
                                                "causa",
                                                causa,
                                                "codigoSalida",
                                                1)),
                                contexto()))
                .isEmpty();
    }

    @Test
    void noInfiereCierresDeCamposAusentesOMalTipadosNiDeVentanas() {
        for (Map<String, Object> datos :
                List.<Map<String, Object>>of(
                        Map.of(),
                        Map.of("esperada", "false"),
                        Map.of("esperada", 0),
                        Map.of("causa", "SYSTEM_EXIT", "codigoSalida", 1))) {
            assertThat(oraculo.evaluar(traza(TipoEvento.APP_TERMINADA, datos), contexto()))
                    .isEmpty();
        }
        assertThat(
                        oraculo.evaluar(
                                traza(TipoEvento.VENTANA_CERRADA, Map.of("esperada", false)),
                                contexto()))
                .isEmpty();
        assertThat(
                        oraculo.evaluar(
                                traza(TipoEvento.APP_TERMINADA, Map.of("esperada", false)),
                                contexto()))
                .hasSize(1);
    }

    @Test
    void produceUnaEvidenciaPorEventoSinEstadoEntreEvaluaciones() {
        Evento primero =
                traza(TipoEvento.APP_TERMINADA, Map.of("esperada", false)).eventos().getFirst();
        Evento segundo =
                new Evento(2, Instant.EPOCH, primero.tipo(), primero.contexto(), primero.datos());
        Traza traza = new Traza("CIERRE", List.of(primero, segundo));
        assertThat(oraculo.evaluar(traza, contexto()))
                .extracting(Hallazgo::evidencia)
                .containsExactly(List.of(1L), List.of(2L));
        assertThat(oraculo.evaluar(new Traza("CIERRE", List.of()), contexto())).isEmpty();
    }

    @Test
    void seEvaluaPorEventoYEstaRegistradoParaServiceLoader() {
        assertThat(oraculo.id()).isEqualTo("APP-TERMINADA");
        assertThat(oraculo.momento()).isEqualTo(Momento.EVENTO);
        assertThat(oraculo.aplica(contexto().caso(), contexto())).isTrue();
        assertThat(ServiceLoader.load(Oraculo.class).stream())
                .extracting(ServiceLoader.Provider::type)
                .contains(OraculoAppTerminada.class);
    }

    private static Traza cargar(String archivo) throws Exception {
        try (var lector =
                new InputStreamReader(
                        OraculoAppTerminadaTest.class.getResourceAsStream(archivo),
                        StandardCharsets.UTF_8)) {
            return new CargadorTrazas().leer(lector, archivo).getFirst();
        }
    }

    private static Traza traza(TipoEvento tipo, Map<String, Object> datos) {
        return new Traza(
                "CIERRE",
                List.of(
                        new Evento(
                                1,
                                Instant.EPOCH,
                                tipo,
                                new ContextoEvento("CIERRE", 1, null, false),
                                datos)));
    }

    private static Contexto contexto() {
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
        Caso caso = new Caso("CIERRE", null, null, null, null, null, null);
        return Contexto.deCaso(
                proyecto, new Suite("cierres", null, null, null, List.of(caso), null), caso);
    }
}
