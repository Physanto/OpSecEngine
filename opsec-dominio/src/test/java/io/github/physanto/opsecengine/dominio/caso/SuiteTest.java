package io.github.physanto.opsecengine.dominio.caso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SuiteTest {

    private static Caso caso(String id) {
        return new Caso(id, null, null, null, null, null, null);
    }

    @Test
    void usaValoresPorDefecto() {
        Suite suite = new Suite("seguridad", null, null, null, null, null);

        assertThat(suite.config()).isEqualTo(ConfigEjecucion.porDefecto());
        assertThat(suite.enfoques()).isEmpty();
        assertThat(suite.casos()).isEmpty();
        assertThat(suite.extensiones()).isEmpty();
    }

    @Test
    void exigeIdsDeCasoUnicos() {
        List<Caso> casos = List.of(caso("SEC-001"), caso("SEC-002"), caso("SEC-001"));

        assertThatThrownBy(() -> new Suite("seguridad", "1.0", null, null, casos, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SEC-001");
    }

    @Test
    void conservaElOrdenDeLosCasos() {
        Suite suite =
                new Suite(
                        "login", "1.0", null, null, List.of(caso("B"), caso("A"), caso("C")), null);

        assertThat(suite.casos()).extracting(Caso::id).containsExactly("B", "A", "C");
    }

    @Test
    void esInmutable() {
        Set<Enfoque> enfoques = EnumSet.of(Enfoque.SEGURIDAD);
        Suite suite = new Suite("seguridad", null, enfoques, null, null, null);

        enfoques.add(Enfoque.FUNCIONAL);

        assertThat(suite.enfoques()).containsExactly(Enfoque.SEGURIDAD);
        assertThatThrownBy(() -> suite.enfoques().add(Enfoque.REGRESION))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void validaElNombre() {
        assertThatThrownBy(() -> new Suite(null, null, null, null, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Suite("", null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void configPorDefectoUsaEventosVisiblesYUnaSesion() {
        ConfigEjecucion config = ConfigEjecucion.porDefecto();

        assertThat(config.modo()).isEqualTo(ModoAccion.EVENTOS);
        assertThat(config.oculto()).isFalse();
        assertThat(config.esperaMaximaPaso()).isEqualTo(Duration.ofSeconds(10));
        assertThat(config.reintentos()).isZero();
        assertThat(config.paralelismo()).isEqualTo(1);
    }

    @Test
    void configValidaSusLimites() {
        Duration espera = Duration.ofSeconds(1);

        assertThatThrownBy(() -> new ConfigEjecucion(null, false, espera, 0, 0, 1))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ConfigEjecucion(ModoAccion.ROBOT, false, null, 0, 0, 1))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () -> new ConfigEjecucion(ModoAccion.ROBOT, false, Duration.ZERO, 0, 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConfigEjecucion(ModoAccion.ROBOT, false, espera, -1, 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConfigEjecucion(ModoAccion.ROBOT, false, espera, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
