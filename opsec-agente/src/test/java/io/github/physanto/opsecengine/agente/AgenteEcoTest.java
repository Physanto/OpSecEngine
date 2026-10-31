package io.github.physanto.opsecengine.agente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.physanto.opsecengine.protocolo.dto.ErrorDto;
import io.github.physanto.opsecengine.protocolo.dto.HolaDto;
import io.github.physanto.opsecengine.protocolo.dto.PasoDto;
import io.github.physanto.opsecengine.soporte.ExcepcionErrorRemoto;
import io.github.physanto.opsecengine.soporte.MotorFalso;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Pruebas del agente eco en el mismo proceso, contra {@link MotorFalso}. */
class AgenteEcoTest {

    private static final Duration ESPERA = Duration.ofSeconds(5);

    @Test
    void saludaRespondeNoImplementadoYTerminaConCerrar() throws Exception {
        try (MotorFalso motor = new MotorFalso()) {
            Thread hilo = iniciarEco(motor.argumentosAgente());

            HolaDto hola = motor.esperarHola(ESPERA);
            assertThat(hola.getToken()).isEqualTo(motor.token());
            assertThat(hola.getVersionAgente()).isEqualTo("desarrollo");
            assertThat(hola.getVersionJava()).isEqualTo(System.getProperty("java.version"));

            assertThatThrownBy(
                            () ->
                                    motor.enviarPaso(
                                            new PasoDto("ESPERAR", null, null, "EVENTOS", 100)))
                    .isInstanceOfSatisfying(
                            ExcepcionErrorRemoto.class,
                            error ->
                                    assertThat(error.getError().getCodigo())
                                            .isEqualTo(ErrorDto.NO_IMPLEMENTADO));
            assertThatThrownBy(motor::pedirArbol).isInstanceOf(ExcepcionErrorRemoto.class);
            assertThatThrownBy(() -> motor.pedirCaptura(null))
                    .isInstanceOf(ExcepcionErrorRemoto.class);

            assertThat(motor.cerrar()).isTrue();
            hilo.join(ESPERA.toMillis());
            assertThat(hilo.isAlive()).isFalse();
        }
    }

    @Test
    void terminaSiElMotorRechazaElToken() throws Exception {
        try (MotorFalso motor = new MotorFalso("token-del-motor")) {
            Thread hilo = iniciarEco("puerto=" + motor.puerto() + ",token=otro");

            assertThatThrownBy(() -> motor.esperarHola(ESPERA))
                    .hasMessageContaining(ErrorDto.TOKEN_INVALIDO);
            hilo.join(ESPERA.toMillis());
            assertThat(hilo.isAlive()).isFalse();
        }
    }

    @Test
    void premainNoFallaConArgumentosInvalidos() {
        AgenteMain.premain("sin-argumentos-validos", null);
        AgenteMain.premain(null, null);
    }

    private static Thread iniciarEco(String argumentos) {
        Thread hilo =
                new Thread(
                        new AgenteEco(ArgumentosAgente.leer(argumentos), AgenteMain.version()),
                        "agente-eco-prueba");
        hilo.setDaemon(true);
        hilo.start();
        return hilo;
    }
}
