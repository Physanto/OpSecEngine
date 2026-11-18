package io.github.physanto.opsecengine.agente.swing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Pruebas de la decisión de qué caracteres se escriben con teclas y cuáles se pegan. */
class AccionesRobotTest {

    @Test
    void letrasDigitosYEspacioSeEscribenConTeclas() {
        assertThat("aZ09 ".codePoints()).allMatch(AccionesRobot::esTecleable);
    }

    @Test
    void tildesSimbolosYEmojisSePegan() {
        assertThat("áñÜ!@€😀".codePoints()).noneMatch(AccionesRobot::esTecleable);
    }
}
