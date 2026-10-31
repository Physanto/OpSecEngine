package io.github.physanto.opsecengine.nucleo.evaluacion;

import static org.assertj.core.api.Assertions.assertThat;

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
import io.github.physanto.opsecengine.dominio.resultado.Veredicto;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import io.github.physanto.opsecengine.nucleo.extensiones.RegistroExtensiones;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvaluacionServiceTest {

    private static final Proyecto PROYECTO =
            new Proyecto("p", Path.of("app.jar"), null, null, null, null, null, null, null, null);
    private static final Paso PASO =
            new Paso(1, Accion.PULSAR, Localizador.porTexto("Avanzar"), null, null, null);
    private static final Traza TRAZA = new Traza("C1", List.of());

    @Test
    void sinHallazgosNiMotivosElCasoCumple() {
        Veredicto veredicto = evaluar(List.of(fijo("A", Momento.CASO, null)), caso(), null);

        assertThat(veredicto.estado()).isEqualTo(EstadoVeredicto.CUMPLE);
    }

    @Test
    void unHallazgoProbableHaceQueNoCumplaAunqueLaEjecucionEsteIncompleta() {
        Veredicto veredicto =
                evaluar(
                        List.of(fijo("A", Momento.EVENTO, Confianza.PROBABLE)),
                        caso(),
                        "El paso 2 terminó con NO_ENCONTRADO");

        assertThat(veredicto.estado()).isEqualTo(EstadoVeredicto.NO_CUMPLE);
        assertThat(veredicto.hallazgos()).extracting(Hallazgo::oraculoId).containsExactly("A");
        assertThat(veredicto.motivo()).contains("NO_ENCONTRADO");
    }

    @Test
    void unaEjecucionIncompletaSinFallosNoEsConcluyente() {
        Veredicto veredicto =
                evaluar(List.of(fijo("A", Momento.CASO, Confianza.INFO)), caso(), "sin sesión");

        assertThat(veredicto.estado()).isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
        assertThat(veredicto.motivo()).isEqualTo("sin sesión");
        assertThat(veredicto.hallazgos()).hasSize(1);
    }

    @Test
    void unOraculoQueFallaDejaElCasoNoConcluyente() {
        Oraculo roto =
                new OraculoFijo("ROTO", Momento.CASO, null) {
                    @Override
                    public List<Hallazgo> evaluar(Traza traza, Contexto ctx) {
                        throw new IllegalStateException("dato inesperado");
                    }
                };

        Veredicto veredicto = evaluar(List.of(roto), caso(), null);

        assertThat(veredicto.estado()).isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
        assertThat(veredicto.motivo())
                .contains("Falló el oráculo ROTO")
                .contains("dato inesperado");
    }

    @Test
    void unOraculoPedidoQueNoEstaRegistradoDejaElCasoNoConcluyente() {
        Veredicto veredicto = evaluar(List.of(), caso("SIN-SESION"), null);

        assertThat(veredicto.estado()).isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
        assertThat(veredicto.motivo())
                .isEqualTo("El caso pide el oráculo SIN-SESION, que no está registrado");
    }

    @Test
    void evaluaLosOraculosPedidosAunqueNoApliquen() {
        OraculoFijo noAplica =
                new OraculoFijo("PEDIDO", Momento.CASO, Confianza.CONFIRMADO) {
                    @Override
                    public boolean aplica(Caso caso, Contexto ctx) {
                        return false;
                    }
                };

        assertThat(evaluar(List.of(noAplica), caso(), null).estado())
                .isEqualTo(EstadoVeredicto.CUMPLE);
        assertThat(evaluar(List.of(noAplica), caso("PEDIDO"), null).estado())
                .isEqualTo(EstadoVeredicto.NO_CUMPLE);
    }

    @Test
    void losOraculosDeSuiteSoloSeEvaluanAlFinal() {
        EvaluacionService servicio =
                servicio(
                        List.of(
                                fijo("DE-CASO", Momento.CASO, Confianza.CONFIRMADO),
                                fijo("DE-SUITE", Momento.SUITE, Confianza.CONFIRMADO)));
        Caso caso = caso();
        Suite suite = suite(caso);

        Veredicto veredicto =
                servicio.evaluarCaso(TRAZA, Contexto.deCaso(PROYECTO, suite, caso), null);
        List<Hallazgo> deSuite =
                servicio.evaluarSuite(Contexto.deSuite(PROYECTO, suite, List.of(TRAZA)));

        assertThat(veredicto.hallazgos())
                .extracting(Hallazgo::oraculoId)
                .containsExactly("DE-CASO");
        assertThat(deSuite).extracting(Hallazgo::oraculoId).containsExactly("DE-SUITE");
    }

    private static Veredicto evaluar(List<Oraculo> oraculos, Caso caso, String motivo) {
        return servicio(oraculos)
                .evaluarCaso(TRAZA, Contexto.deCaso(PROYECTO, suite(caso), caso), motivo);
    }

    private static EvaluacionService servicio(List<Oraculo> oraculos) {
        return new EvaluacionService(
                new RegistroExtensiones(oraculos, List.of(), List.of(), List.of()));
    }

    private static Caso caso(String... oraculos) {
        return new Caso("C1", null, null, List.of(PASO), null, List.of(oraculos), null);
    }

    private static Suite suite(Caso caso) {
        return new Suite("s", null, null, null, List.of(caso), null);
    }

    private static Oraculo fijo(String id, Momento momento, Confianza confianza) {
        return new OraculoFijo(id, momento, confianza);
    }

    /** Oráculo que siempre aplica y devuelve un hallazgo con la confianza dada, o ninguno. */
    private static class OraculoFijo implements Oraculo {

        private final String id;
        private final Momento momento;
        private final Confianza confianza;

        OraculoFijo(String id, Momento momento, Confianza confianza) {
            this.id = id;
            this.momento = momento;
            this.confianza = confianza;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Momento momento() {
            return momento;
        }

        @Override
        public boolean aplica(Caso caso, Contexto ctx) {
            return true;
        }

        @Override
        public List<Hallazgo> evaluar(Traza traza, Contexto ctx) {
            return confianza == null
                    ? List.of()
                    : List.of(new Hallazgo(id, null, confianza, "Hallazgo de " + id, "", null));
        }
    }
}
