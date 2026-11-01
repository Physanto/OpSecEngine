package io.github.physanto.opsecengine.agente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.physanto.opsecengine.agente.api.AdaptadorToolkit;
import io.github.physanto.opsecengine.agente.api.ResultadoBusqueda;
import io.github.physanto.opsecengine.protocolo.CodecMensajes;
import io.github.physanto.opsecengine.protocolo.dto.CapturaDto;
import io.github.physanto.opsecengine.protocolo.dto.CodigoResultado;
import io.github.physanto.opsecengine.protocolo.dto.ErrorDto;
import io.github.physanto.opsecengine.protocolo.dto.EventoDto;
import io.github.physanto.opsecengine.protocolo.dto.HolaDto;
import io.github.physanto.opsecengine.protocolo.dto.LocalizadorDto;
import io.github.physanto.opsecengine.protocolo.dto.NodoArbolDto;
import io.github.physanto.opsecengine.protocolo.dto.PasoDto;
import io.github.physanto.opsecengine.protocolo.dto.ResultadoPasoDto;
import io.github.physanto.opsecengine.soporte.ExcepcionErrorRemoto;
import io.github.physanto.opsecengine.soporte.MotorFalso;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Pruebas del servidor del protocolo en el mismo proceso, contra {@link MotorFalso}. */
class ServidorProtocoloTest {

    private static final Duration ESPERA = Duration.ofSeconds(5);
    private static final CodecMensajes CODEC = new CodecMensajes();

    @Test
    void saludaEnviaLosEventosGuardadosYTerminaConCerrar() throws Exception {
        EmisorProtocolo emisor = new EmisorProtocolo(CODEC);
        emisor.emitir("CONSOLA", Map.of("flujo", "SALIDA", "texto", "antes de conectar"));
        try (MotorFalso motor = new MotorFalso()) {
            Thread hilo = iniciar(motor.argumentosAgente(), new AdaptadorFijo(emisor), emisor);

            HolaDto hola = motor.esperarHola(ESPERA);
            assertThat(hola.getToken()).isEqualTo(motor.token());
            assertThat(hola.getVersionAgente()).isEqualTo("desarrollo");

            ResultadoPasoDto resultado =
                    motor.enviarPaso(new PasoDto("ESPERAR", null, "5", "SEMANTICO", 1000));
            assertThat(resultado.getCodigo()).isEqualTo(CodigoResultado.OK);
            assertThat(motor.eventos())
                    .extracting(EventoDto::getSecuencia, evento -> evento.getDatos().get("texto"))
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(0L, "antes de conectar"),
                            org.assertj.core.groups.Tuple.tuple(1L, "durante el paso"));

            assertThat(motor.pedirArbol().getVentanas()).isEmpty();
            assertThatThrownBy(() -> motor.pedirCaptura(null))
                    .isInstanceOfSatisfying(
                            ExcepcionErrorRemoto.class,
                            error ->
                                    assertThat(error.getError().getCodigo())
                                            .isEqualTo(ErrorDto.NO_IMPLEMENTADO));

            assertThat(motor.cerrar()).isTrue();
            hilo.join(ESPERA.toMillis());
            assertThat(hilo.isAlive()).isFalse();
        }
        emisor.emitir("CONSOLA", Map.of("flujo", "SALIDA", "texto", "después de cerrar"));
    }

    @Test
    void terminaSiElMotorRechazaElToken() throws Exception {
        EmisorProtocolo emisor = new EmisorProtocolo(CODEC);
        try (MotorFalso motor = new MotorFalso("token-del-motor")) {
            Thread hilo =
                    iniciar(
                            "puerto=" + motor.puerto() + ",token=otro",
                            new AdaptadorFijo(emisor),
                            emisor);

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

    private static Thread iniciar(
            String argumentos, AdaptadorToolkit adaptador, EmisorProtocolo emisor) {
        Thread hilo =
                new Thread(
                        new ServidorProtocolo(
                                ArgumentosAgente.leer(argumentos),
                                AgenteMain.version(),
                                adaptador,
                                emisor,
                                CODEC),
                        "servidor-prueba");
        hilo.setDaemon(true);
        hilo.start();
        return hilo;
    }

    /** Adaptador que responde OK a todo y emite un evento durante cada paso. */
    private static final class AdaptadorFijo implements AdaptadorToolkit {

        private final EmisorProtocolo emisor;

        AdaptadorFijo(EmisorProtocolo emisor) {
            this.emisor = emisor;
        }

        @Override
        public List<NodoArbolDto> arbol() {
            return List.of();
        }

        @Override
        public ResultadoBusqueda resolver(LocalizadorDto localizador) {
            return ResultadoBusqueda.noEncontrado(null);
        }

        @Override
        public ResultadoPasoDto ejecutar(PasoDto paso) {
            emisor.emitir("CONSOLA", Map.of("flujo", "SALIDA", "texto", "durante el paso"));
            return new ResultadoPasoDto(CodigoResultado.OK, null, 1);
        }

        @Override
        public CapturaDto capturar(String ventana) {
            throw new UnsupportedOperationException();
        }
    }
}
