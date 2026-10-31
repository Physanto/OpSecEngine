package io.github.physanto.opsecengine.dominio.resultado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class VeredictoTest {

    static final Hallazgo HALLAZGO =
            new Hallazgo(
                    "SIN-SESION",
                    "CWE-306",
                    Confianza.PROBABLE,
                    "Eliminación sin sesión",
                    "Se ejecutó un DELETE sin haber iniciado sesión.",
                    List.of(7L));

    @Test
    void cumpleNoTieneHallazgosNiMotivo() {
        Veredicto veredicto = Veredicto.cumple();

        assertThat(veredicto.estado()).isEqualTo(EstadoVeredicto.CUMPLE);
        assertThat(veredicto.hallazgos()).isEmpty();
        assertThat(veredicto.motivo()).isNull();
    }

    @Test
    void noCumpleConservaSusHallazgos() {
        Veredicto veredicto = Veredicto.noCumple(List.of(HALLAZGO));

        assertThat(veredicto.estado()).isEqualTo(EstadoVeredicto.NO_CUMPLE);
        assertThat(veredicto.hallazgos()).containsExactly(HALLAZGO);
    }

    @Test
    void noCumpleExigeAlMenosUnHallazgo() {
        assertThatThrownBy(() -> Veredicto.noCumple(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Veredicto.noCumple(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Veredicto(EstadoVeredicto.NO_CUMPLE, List.of(), "motivo"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noConcluyenteExigeMotivo() {
        assertThat(Veredicto.noConcluyente("La aplicación no arrancó").motivo())
                .isEqualTo("La aplicación no arrancó");
        assertThatThrownBy(() -> Veredicto.noConcluyente(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Veredicto.noConcluyente("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cumplePuedeLlevarHallazgosInformativos() {
        Hallazgo info =
                new Hallazgo("TIEMPO-LENTO", null, Confianza.INFO, "Respuesta lenta", "", null);

        assertThat(new Veredicto(EstadoVeredicto.CUMPLE, List.of(info), null).hallazgos())
                .containsExactly(info);
    }

    @Test
    void exigeEstado() {
        assertThatThrownBy(() -> new Veredicto(null, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void esInmutable() {
        List<Hallazgo> hallazgos = new ArrayList<>(List.of(HALLAZGO));
        Veredicto veredicto = Veredicto.noCumple(hallazgos);

        hallazgos.clear();

        assertThat(veredicto.hallazgos()).hasSize(1);
        assertThatThrownBy(() -> veredicto.hallazgos().add(HALLAZGO))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void hallazgoValidaElFormatoCwe() {
        assertThat(HALLAZGO.evidencia()).containsExactly(7L);
        assertThatThrownBy(
                        () -> new Hallazgo("SIN-SESION", "306", Confianza.PROBABLE, "t", "e", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Hallazgo(" ", "CWE-306", Confianza.PROBABLE, "t", "e", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Hallazgo("SIN-SESION", null, null, "t", "e", null))
                .isInstanceOf(NullPointerException.class);
    }
}
