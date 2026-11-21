package io.github.physanto.opsecengine.agente.cobertura;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Pruebas del volcado de la cobertura de JaCoCo con un doble de {@code RT}. */
class CoberturaJacocoTest {

    private final List<String> errores = new ArrayList<>();

    @AfterEach
    void limpiar() {
        RtFalso.reiniciar();
    }

    @Test
    void escribeLosDatosYReiniciaLosContadores() {
        CoberturaJacoco cobertura = new CoberturaJacoco(RtFalso.class.getName(), errores::add);

        assertThat(cobertura.volcar()).isTrue();

        assertThat(RtFalso.VOLCADOS).containsExactly(true);
        assertThat(errores).isEmpty();
    }

    @Test
    void sinJacocoCargadoNoHaceNadaNiAvisa() {
        CoberturaJacoco cobertura = new CoberturaJacoco("no.existe.Rt", errores::add);

        assertThat(cobertura.volcar()).isFalse();

        assertThat(errores).isEmpty();
    }

    @Test
    void siJacocoFallaAlEscribirInformaYNoLanzaExcepcion() {
        RtFalso.fallar = true;
        CoberturaJacoco cobertura = new CoberturaJacoco(RtFalso.class.getName(), errores::add);

        assertThat(cobertura.volcar()).isFalse();

        assertThat(errores).hasSize(1);
        assertThat(errores.get(0)).contains("JaCoCo", "sin archivo de destino");
    }

    @Test
    void siLaClaseNoTieneGetAgentInformaYNoLanzaExcepcion() {
        CoberturaJacoco cobertura = new CoberturaJacoco(String.class.getName(), errores::add);

        assertThat(cobertura.volcar()).isFalse();

        assertThat(errores).hasSize(1);
    }
}
