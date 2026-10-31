package io.github.physanto.opsecengine.soporte;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Pruebas de {@link AplicacionDePrueba} sin lanzar otro proceso. */
class AplicacionDePruebaTest {

    @Test
    void terminaDespuesDeLaEsperaIndicada() {
        assertThatCode(() -> AplicacionDePrueba.main(new String[] {"0"}))
                .doesNotThrowAnyException();
    }

    @Test
    void rechazaUnaEsperaQueNoEsUnNumero() {
        assertThatThrownBy(() -> AplicacionDePrueba.main(new String[] {"mucho"}))
                .isInstanceOf(NumberFormatException.class);
    }
}
