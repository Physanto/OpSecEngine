package io.github.physanto.opsecengine.informes;

import static io.github.physanto.opsecengine.informes.Resultados.caso;
import static io.github.physanto.opsecengine.informes.Resultados.hallazgo;
import static io.github.physanto.opsecengine.informes.Resultados.resultado;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.physanto.opsecengine.dominio.resultado.Confianza;
import io.github.physanto.opsecengine.dominio.resultado.EstadoVeredicto;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoSuite;
import io.github.physanto.opsecengine.dominio.resultado.Veredicto;
import io.github.physanto.opsecengine.informes.ComparadorResultados.Cambio;
import io.github.physanto.opsecengine.informes.ComparadorResultados.CambioCaso;
import io.github.physanto.opsecengine.informes.ComparadorResultados.Comparacion;
import io.github.physanto.opsecengine.soporte.DatosEjemplo;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pruebas de {@link ComparadorResultados}: clasificación de los cambios y de los hallazgos. */
class ComparadorResultadosTest {

    private static final Hallazgo A = hallazgo("A", "CWE-89", Confianza.CONFIRMADO, "Fallo A");
    private static final Hallazgo B = hallazgo("B", "CWE-209", Confianza.PROBABLE, "Fallo B");

    @Test
    void clasificaCadaCasoSegunLaGravedadDelVeredicto() {
        ResultadoSuite antes =
                resultado(
                        "s",
                        caso("ARREGLADO", Veredicto.noCumple(List.of(A))),
                        caso("ROTO", Veredicto.cumple()),
                        caso("IGUAL", Veredicto.cumple()),
                        caso("ELIMINADO", Veredicto.cumple()));
        ResultadoSuite ahora =
                resultado(
                        "s",
                        caso("ARREGLADO", Veredicto.cumple()),
                        caso("ROTO", Veredicto.noCumple(List.of(B))),
                        caso("IGUAL", Veredicto.cumple()),
                        caso("NUEVO", Veredicto.cumple()));

        Comparacion c = ComparadorResultados.comparar(antes, ahora);

        assertThat(cambioDe(c, "ARREGLADO")).isEqualTo(Cambio.MEJORO);
        assertThat(cambioDe(c, "ROTO")).isEqualTo(Cambio.EMPEORO);
        assertThat(cambioDe(c, "IGUAL")).isEqualTo(Cambio.IGUAL);
        assertThat(cambioDe(c, "NUEVO")).isEqualTo(Cambio.NUEVO);
        assertThat(cambioDe(c, "ELIMINADO")).isEqualTo(Cambio.ELIMINADO);
        assertThat(c.cuenta(Cambio.MEJORO)).isEqualTo(1);
        assertThat(c.casos())
                .extracting(CambioCaso::casoId)
                .containsExactly("ARREGLADO", "ROTO", "IGUAL", "NUEVO", "ELIMINADO");
    }

    @Test
    void ordenaLaGravedadComoCumpleNoConcluyenteNoCumple() {
        ResultadoSuite antes =
                resultado(
                        "s",
                        caso("X", Veredicto.noConcluyente("no se pudo")),
                        caso("Y", Veredicto.noCumple(List.of(A))),
                        caso("Z", Veredicto.noConcluyente("no se pudo")));
        ResultadoSuite ahora =
                resultado(
                        "s",
                        caso("X", Veredicto.noCumple(List.of(A))),
                        caso("Y", Veredicto.noConcluyente("ahora no se pudo")),
                        caso("Z", Veredicto.cumple()));

        Comparacion c = ComparadorResultados.comparar(antes, ahora);

        assertThat(cambioDe(c, "X")).isEqualTo(Cambio.EMPEORO);
        assertThat(cambioDe(c, "Y")).isEqualTo(Cambio.MEJORO);
        assertThat(cambioDe(c, "Z")).isEqualTo(Cambio.MEJORO);
    }

    @Test
    void conElMismoVeredictoSeSiguenViendoLosHallazgosNuevosYResueltos() {
        ResultadoSuite antes = resultado("s", caso("C", Veredicto.noCumple(List.of(A, B))));
        ResultadoSuite ahora =
                resultado(
                        "s",
                        caso(
                                "C",
                                Veredicto.noCumple(
                                        List.of(
                                                hallazgo(
                                                        "B",
                                                        "CWE-209",
                                                        Confianza.CONFIRMADO,
                                                        "Fallo B"),
                                                hallazgo("N", null, Confianza.INFO, "Fallo N")))));

        CambioCaso cambio = ComparadorResultados.comparar(antes, ahora).casos().get(0);

        assertThat(cambio.cambio()).isEqualTo(Cambio.IGUAL);
        assertThat(cambio.resueltos()).extracting(Hallazgo::oraculoId).containsExactly("A");
        assertThat(cambio.nuevos()).extracting(Hallazgo::oraculoId).containsExactly("N");
    }

    @Test
    void unCasoNuevoOEliminadoMuestraTodosSusHallazgosComoNuevosOResueltos() {
        ResultadoSuite antes = resultado("s", caso("VIEJO", Veredicto.noCumple(List.of(A))));
        ResultadoSuite ahora = resultado("s", caso("NUEVO", Veredicto.noCumple(List.of(B))));

        Comparacion c = ComparadorResultados.comparar(antes, ahora);

        CambioCaso nuevo = c.casos().get(0);
        assertThat(nuevo.cambio()).isEqualTo(Cambio.NUEVO);
        assertThat(nuevo.anterior()).isNull();
        assertThat(nuevo.actual()).isEqualTo(EstadoVeredicto.NO_CUMPLE);
        assertThat(nuevo.nuevos()).containsExactly(B);
        CambioCaso eliminado = c.casos().get(1);
        assertThat(eliminado.cambio()).isEqualTo(Cambio.ELIMINADO);
        assertThat(eliminado.actual()).isNull();
        assertThat(eliminado.resueltos()).containsExactly(A);
    }

    @Test
    void comparaLosHallazgosDeSuite() {
        ResultadoSuite antes = resultado("s", List.of(A), caso("C", Veredicto.cumple()));
        ResultadoSuite ahora = resultado("s", List.of(B), caso("C", Veredicto.cumple()));

        Comparacion c = ComparadorResultados.comparar(antes, ahora);

        assertThat(c.resueltosSuite()).containsExactly(A);
        assertThat(c.nuevosSuite()).containsExactly(B);
        assertThat(c.empeoro()).isTrue();
    }

    @Test
    void empeoroSoloSiAlgoSeVolvioPeor() {
        Comparacion igual = ComparadorResultados.comparar(base(), base());
        assertThat(igual.empeoro()).isFalse();

        Comparacion empeora =
                ComparadorResultados.comparar(
                        base(), resultado("s", caso("C", Veredicto.noCumple(List.of(A)))));
        assertThat(empeora.empeoro()).isTrue();

        Comparacion nuevoQueFalla =
                ComparadorResultados.comparar(
                        base(),
                        resultado(
                                "s",
                                caso("C", Veredicto.cumple()),
                                caso("D", Veredicto.noCumple(List.of(A)))));
        assertThat(nuevoQueFalla.empeoro()).isTrue();

        Comparacion nuevoQueCumple =
                ComparadorResultados.comparar(
                        base(),
                        resultado(
                                "s", caso("C", Veredicto.cumple()), caso("D", Veredicto.cumple())));
        assertThat(nuevoQueCumple.empeoro()).isFalse();

        Comparacion hallazgoDeSuiteNuevo =
                ComparadorResultados.comparar(
                        base(), resultado("s", List.of(A), caso("C", Veredicto.cumple())));
        assertThat(hallazgoDeSuiteNuevo.empeoro()).isTrue();
    }

    @Test
    void compararElResultadoDeEjemploConSiMismoNoCambiaNada() throws Exception {
        ResultadoSuite ejemplo = DatosEjemplo.resultadoSuite();

        Comparacion c = ComparadorResultados.comparar(ejemplo, ejemplo);

        assertThat(c.cuenta(Cambio.IGUAL)).isEqualTo(3);
        assertThat(c.casos())
                .allSatisfy(caso -> {
                    assertThat(caso.resueltos()).isEmpty();
                    assertThat(caso.nuevos()).isEmpty();
                });
        assertThat(c.empeoro()).isFalse();
    }

    @Test
    void rechazaResultadosNulos() {
        assertThat(base()).isNotNull();
        assertThatThrownBy(() -> ComparadorResultados.comparar(null, base()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ComparadorResultados.comparar(base(), null))
                .isInstanceOf(NullPointerException.class);
    }

    private static ResultadoSuite base() {
        return resultado("s", caso("C", Veredicto.cumple()));
    }

    private static Cambio cambioDe(Comparacion comparacion, String casoId) {
        return comparacion.casos().stream()
                .filter(c -> c.casoId().equals(casoId))
                .findFirst()
                .orElseThrow()
                .cambio();
    }
}
