package io.github.physanto.opsecengine.soporte;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.physanto.opsecengine.dominio.mapa.MapaApp;
import io.github.physanto.opsecengine.dominio.mapa.Ventana;
import io.github.physanto.opsecengine.dominio.resultado.EstadoVeredicto;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoCaso;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoSuite;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de {@link DatosEjemplo}: el mapa y el resultado de ejemplo se cargan y son coherentes.
 */
class DatosEjemploTest {

    @Test
    void elMapaTieneLosCuatroContextosYLaRamaLocalAlcanzaEliminar() throws IOException {
        MapaApp mapa = DatosEjemplo.mapaClientes();

        assertThat(mapa.contextos())
                .containsExactly(
                        MapaApp.SIN_SESION,
                        MapaApp.contextoRol("cliente"),
                        MapaApp.contextoRol("admin"),
                        MapaApp.contextoEntrada("GUI_Admin.EliminarPersonaAdministrador"));
        assertThat(mapa.alcanzablesDesde("Portada", MapaApp.SIN_SESION))
                .extracting(Ventana::id)
                .contains("EliminarPersona")
                .doesNotContain("MenuAdministrador");
        assertThat(mapa.alcanzablesDesde("Portada", MapaApp.contextoRol("cliente")))
                .extracting(Ventana::id)
                .doesNotContain("EliminarPersonaAdministrador");
        assertThat(mapa.alcanzablesDesde("Portada", MapaApp.contextoRol("admin")))
                .extracting(Ventana::id)
                .contains("EliminarPersonaAdministrador");
    }

    @Test
    void elResultadoTieneUnCasoDeCadaEstado() throws IOException {
        ResultadoSuite resultado = DatosEjemplo.resultadoSuite();

        assertThat(resultado.resumen())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(
                                EstadoVeredicto.CUMPLE, 1,
                                EstadoVeredicto.NO_CUMPLE, 1,
                                EstadoVeredicto.NO_CONCLUYENTE, 1));
        ResultadoCaso n3 = resultado.casos().get(0);
        assertThat(n3.veredicto().hallazgos())
                .singleElement()
                .satisfies(
                        hallazgo -> {
                            assertThat(hallazgo.oraculoId()).isEqualTo("SIN-SESION");
                            assertThat(hallazgo.evidencia())
                                    .allMatch(
                                            secuencia ->
                                                    n3.traza().eventos().stream()
                                                            .anyMatch(
                                                                    e ->
                                                                            e.secuencia()
                                                                                    == secuencia));
                        });
        assertThat(n3.duracion()).isEqualTo(Duration.ofMillis(2800));
        assertThat(resultado.casos()).anyMatch(ResultadoCaso::inestable);
    }
}
