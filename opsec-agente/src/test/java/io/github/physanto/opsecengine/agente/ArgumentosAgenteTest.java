package io.github.physanto.opsecengine.agente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Pruebas de {@link ArgumentosAgente}. */
class ArgumentosAgenteTest {

    @Test
    void leePuertoYTokenEIgnoraClavesDesconocidas() {
        ArgumentosAgente argumentos =
                ArgumentosAgente.leer(" puerto=51234 , token=a-b_c=, modo=oculto");

        assertThat(argumentos.puerto()).isEqualTo(51234);
        assertThat(argumentos.token()).isEqualTo("a-b_c=");
    }

    @Test
    void elModoOcultoEsOpcionalYExigeUnBooleano() {
        assertThat(ArgumentosAgente.leer("puerto=1234,token=x").oculto()).isFalse();
        assertThat(ArgumentosAgente.leer("puerto=1234,token=x,oculto=false").oculto()).isFalse();
        assertThat(ArgumentosAgente.leer("puerto=1234,token=x,oculto=true").oculto()).isTrue();
        assertThatThrownBy(() -> ArgumentosAgente.leer("puerto=1234,token=secreto,oculto=quizas"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("secreto");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "token=abc",
                "puerto=1234",
                "puerto=1234,token=",
                "puerto=abc,token=x",
                "puerto=0,token=x",
                "puerto=70000,token=x",
                "puerto"
            })
    void rechazaArgumentosIncompletosOInvalidos(String texto) {
        assertThatThrownBy(() -> ArgumentosAgente.leer(texto))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noMuestraElTokenEnLosMensajesDeError() {
        assertThatThrownBy(() -> ArgumentosAgente.leer("token=secreto"))
                .hasMessageNotContaining("secreto")
                .hasMessageContaining("token=****");
    }
}
