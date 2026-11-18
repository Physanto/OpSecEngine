package io.github.physanto.opsecengine.nucleo.ejecucion;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.physanto.opsecengine.dominio.caso.Accion;
import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.ConfigEjecucion;
import io.github.physanto.opsecengine.dominio.caso.Localizador;
import io.github.physanto.opsecengine.dominio.caso.ModoAccion;
import io.github.physanto.opsecengine.dominio.caso.Paso;
import io.github.physanto.opsecengine.dominio.caso.Suite;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Pruebas de la generación determinista de canarios. */
class CanariosTest {

    @Test
    void elCanarioEsDeterministaYDependeDeLaSemillaYDelCaso() {
        assertThat(Canarios.generar(42, "C13")).isEqualTo(Canarios.generar(42, "C13"));
        assertThat(Canarios.generar(42, "C13")).isNotEqualTo(Canarios.generar(43, "C13"));
        assertThat(Canarios.generar(42, "C13")).isNotEqualTo(Canarios.generar(42, "C14"));
        assertThat(Canarios.generar(42, "C13")).matches("OPSEC[0-9A-F]{12}");
    }

    @Test
    void reemplazaLaVariableYDeclaraElCanarioSoloDondeSeUsa() {
        Caso usa = caso("A", "{canario}' OR 1=1");
        Caso noUsa = caso("B", "texto");
        Suite suite = suite(7, usa, noUsa);

        Suite preparada = Canarios.preparar(suite);

        String canario = Canarios.generar(7, "A");
        assertThat(preparada.casos().get(0).canario()).isEqualTo(canario);
        assertThat(preparada.casos().get(0).pasos().get(0).valor()).isEqualTo(canario + "' OR 1=1");
        assertThat(preparada.casos().get(1)).isSameAs(noUsa);
    }

    @Test
    void respetaElCanarioQueElCasoYaDeclara() {
        Caso declarado =
                new Caso(
                        "A",
                        null,
                        null,
                        List.of(paso("x{canario}")),
                        null,
                        null,
                        "MARCA-PROPIA",
                        null,
                        null,
                        Map.of());

        Suite preparada = Canarios.preparar(suite(1, declarado));

        assertThat(preparada.casos().get(0).canario()).isEqualTo("MARCA-PROPIA");
        assertThat(preparada.casos().get(0).pasos().get(0).valor()).isEqualTo("xMARCA-PROPIA");
    }

    @Test
    void sinLaVariableDevuelveLaMismaSuite() {
        Suite suite = suite(1, caso("A", "normal"));

        assertThat(Canarios.preparar(suite)).isSameAs(suite);
    }

    private static Caso caso(String id, String valor) {
        return new Caso(id, null, null, List.of(paso(valor)), null, null, null);
    }

    private static Paso paso(String valor) {
        return new Paso(1, Accion.LLENAR, Localizador.porEtiqueta("Usuario"), valor, null, null);
    }

    private static Suite suite(long semilla, Caso... casos) {
        return new Suite(
                "s",
                "1",
                Set.of(),
                new ConfigEjecucion(
                        ModoAccion.EVENTOS, false, Duration.ofSeconds(1), 0, semilla, 1),
                List.of(casos),
                null,
                Map.of());
    }
}
