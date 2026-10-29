package io.github.physanto.opsecengine.dominio.resultado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import io.github.physanto.opsecengine.dominio.traza.Traza;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResultadoSuiteTest {

    private static final Instant INICIO = Instant.parse("2026-10-28T15:30:00Z");
    private static final Instant FIN = INICIO.plusSeconds(90);
    private static final Manifiesto MANIFIESTO =
            new Manifiesto("ab12", "0.0.1-SNAPSHOT", "17.0.12", "Linux 6.8", 0L, "cd34", INICIO);

    private static ResultadoCaso resultado(String id, Veredicto veredicto) {
        return new ResultadoCaso(
                id, veredicto, new Traza(id, null), Duration.ofSeconds(3), 1, false);
    }

    private static ResultadoSuite suite(List<ResultadoCaso> casos) {
        return new ResultadoSuite("seguridad", MANIFIESTO, casos, null, INICIO, FIN);
    }

    @Test
    void resumenCuentaLosCasosPorEstado() {
        ResultadoSuite suite =
                suite(
                        List.of(
                                resultado("A1", Veredicto.cumple()),
                                resultado(
                                        "N3", Veredicto.noCumple(List.of(VeredictoTest.HALLAZGO))),
                                resultado(
                                        "C13", Veredicto.noCumple(List.of(VeredictoTest.HALLAZGO))),
                                resultado("C01", Veredicto.noConcluyente("Sin backend"))));

        assertThat(suite.resumen())
                .containsEntry(EstadoVeredicto.CUMPLE, 1)
                .containsEntry(EstadoVeredicto.NO_CUMPLE, 2)
                .containsEntry(EstadoVeredicto.NO_CONCLUYENTE, 1);
    }

    @Test
    void resumenIncluyeEstadosEnCeroEnOrden() {
        assertThat(suite(null).resumen())
                .containsExactly(
                        entry(EstadoVeredicto.CUMPLE, 0),
                        entry(EstadoVeredicto.NO_CUMPLE, 0),
                        entry(EstadoVeredicto.NO_CONCLUYENTE, 0));
        assertThatThrownBy(() -> suite(null).resumen().put(EstadoVeredicto.CUMPLE, 5))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void validaIdsUnicosYFechas() {
        List<ResultadoCaso> repetidos =
                List.of(resultado("A1", Veredicto.cumple()), resultado("A1", Veredicto.cumple()));

        assertThatThrownBy(() -> suite(repetidos))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A1");
        assertThatThrownBy(() -> new ResultadoSuite("s", MANIFIESTO, null, null, FIN, INICIO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResultadoSuite("s", null, null, null, INICIO, FIN))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void resultadoCasoValidaSusDatos() {
        Traza traza = new Traza("A1", null);
        Duration duracion = Duration.ofSeconds(1);
        Veredicto cumple = Veredicto.cumple();

        assertThat(new ResultadoCaso("A1", cumple, traza, duracion, 2, true).inestable()).isTrue();
        assertThatThrownBy(() -> new ResultadoCaso("B2", cumple, traza, duracion, 1, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResultadoCaso("A1", cumple, traza, duracion, 0, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResultadoCaso("A1", cumple, traza, duracion, 1, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new ResultadoCaso(
                                        "A1", cumple, traza, Duration.ofSeconds(-1), 1, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void manifiestoExigeSusCampos() {
        assertThatThrownBy(() -> new Manifiesto(null, "v", "17", "Linux", 0, "h", INICIO))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Manifiesto("h", " ", "17", "Linux", 0, "h", INICIO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Manifiesto("h", "v", "17", "Linux", 0, "h", null))
                .isInstanceOf(NullPointerException.class);
    }
}
