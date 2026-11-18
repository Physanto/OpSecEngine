package io.github.physanto.opsecengine.soporte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.physanto.opsecengine.protocolo.ExcepcionProtocolo;
import io.github.physanto.opsecengine.protocolo.Mensaje;
import io.github.physanto.opsecengine.protocolo.TipoMensaje;
import io.github.physanto.opsecengine.protocolo.dto.ArbolDto;
import io.github.physanto.opsecengine.protocolo.dto.CapturaDto;
import io.github.physanto.opsecengine.protocolo.dto.CodigoResultado;
import io.github.physanto.opsecengine.protocolo.dto.ConfigGanchosDto;
import io.github.physanto.opsecengine.protocolo.dto.CriterioDto;
import io.github.physanto.opsecengine.protocolo.dto.ErrorDto;
import io.github.physanto.opsecengine.protocolo.dto.EventoDto;
import io.github.physanto.opsecengine.protocolo.dto.HolaDto;
import io.github.physanto.opsecengine.protocolo.dto.LocalizadorDto;
import io.github.physanto.opsecengine.protocolo.dto.NodoArbolDto;
import io.github.physanto.opsecengine.protocolo.dto.PasoDto;
import io.github.physanto.opsecengine.protocolo.dto.ResultadoPasoDto;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Conversación completa entre {@link AgenteFalso} y {@link MotorFalso} por un socket real. */
class ConversacionFalsosTest {

    private static final Duration ESPERA = Duration.ofSeconds(5);

    private static final EventoDto VENTANA =
            new EventoDto(
                    0,
                    1_793_350_800_000L,
                    "VENTANA_ABIERTA",
                    Map.of("ventanaId", "v2", "clase", "GUI_Local.EscogerBD"));

    @Test
    void conversanConElGuionCompleto() throws Exception {
        ArbolDto arbol =
                new ArbolDto(
                        List.of(
                                new NodoArbolDto(
                                        "javax.swing.JFrame",
                                        "Portada",
                                        null,
                                        null,
                                        true,
                                        true,
                                        false,
                                        List.of())));
        try (MotorFalso motor = new MotorFalso();
                AgenteFalso agente = new AgenteFalso()) {
            agente.cuandoPaso(paso -> paso.getAccion().equals("PULSAR"))
                    .emitir(VENTANA)
                    .responder(new ResultadoPasoDto(CodigoResultado.OK, null, 12))
                    .conArbol(arbol);
            agente.conectar(motor.puerto(), motor.token());

            HolaDto hola = motor.esperarHola(ESPERA);
            ResultadoPasoDto pulsar = motor.enviarPaso(paso("PULSAR", "Avanzar"));
            List<EventoDto> eventos = motor.eventos();
            ResultadoPasoDto sinGuion = motor.enviarPaso(paso("LLENAR", "Usuario"));
            ArbolDto arbolRecibido = motor.pedirArbol();
            CapturaDto captura = motor.pedirCaptura("v1");
            boolean cerro = motor.cerrar();

            assertThat(hola.getToken()).isEqualTo(motor.token());
            assertThat(hola.getVersionAgente()).isEqualTo(AgenteFalso.VERSION);
            assertThat(pulsar).isEqualTo(new ResultadoPasoDto(CodigoResultado.OK, null, 12));
            assertThat(eventos).containsExactly(VENTANA);
            assertThat(sinGuion).isEqualTo(AgenteFalso.SIN_GUION);
            assertThat(arbolRecibido).isEqualTo(arbol);
            assertThat(captura.getVentana()).isEqualTo("v1");
            assertThat(captura.getFormato()).isEqualTo("PNG");
            assertThat(Base64.getDecoder().decode(captura.getBase64()))
                    .isEqualTo(AgenteFalso.imagenPng())
                    .startsWith(0x89, 'P', 'N', 'G');
            assertThat(cerro).isTrue();
            assertThat(agente.esperarFin(ESPERA)).isTrue();
            assertThat(agente.mensajesRecibidos())
                    .extracting(Mensaje::getTipo)
                    .containsExactly(
                            TipoMensaje.EJECUTAR_PASO,
                            TipoMensaje.EJECUTAR_PASO,
                            TipoMensaje.ARBOL_SOLICITUD,
                            TipoMensaje.CAPTURAR,
                            TipoMensaje.CERRAR);
            assertThat(motor.mensajesRecibidos())
                    .extracting(Mensaje::getTipo)
                    .startsWith(TipoMensaje.HOLA, TipoMensaje.EVENTO, TipoMensaje.RESULTADO_PASO);
        }
    }

    @Test
    void elAgenteConfirmaLaConfiguracionDeLosGanchos() throws Exception {
        ConfigGanchosDto config =
                new ConfigGanchosDto(List.of("DELETE"), List.of(), List.of("OPSEC-X"), List.of());
        try (MotorFalso motor = new MotorFalso();
                AgenteFalso agente = new AgenteFalso()) {
            agente.conectar(motor.puerto(), motor.token());
            motor.esperarHola(ESPERA);

            ResultadoPasoDto confirmacion = motor.configurarGanchos(config);

            assertThat(confirmacion.getCodigo()).isEqualTo(CodigoResultado.OK);
            assertThat(agente.mensajesRecibidos())
                    .extracting(Mensaje::getTipo)
                    .containsExactly(TipoMensaje.CONFIG_GANCHOS);
        }
    }

    @Test
    void laPrimeraReglaQueAceptaElPasoGanaYLaRespuestaPorDefectoSePuedeCambiar() throws Exception {
        ResultadoPasoDto noAlcanzable =
                new ResultadoPasoDto(CodigoResultado.NO_ALCANZABLE, "deshabilitado", 3);
        ResultadoPasoDto porDefecto = new ResultadoPasoDto(CodigoResultado.OK, null, 1);
        try (MotorFalso motor = new MotorFalso();
                AgenteFalso agente = new AgenteFalso()) {
            agente.cuandoPaso(paso -> paso.getAccion().equals("PULSAR"))
                    .responder(noAlcanzable)
                    .cuandoPaso(paso -> true)
                    .responder(new ResultadoPasoDto(CodigoResultado.ERROR, "no debe usarse", 0))
                    .respuestaPorDefecto(porDefecto);
            agente.conectar(motor.puerto(), motor.token());
            motor.esperarHola(ESPERA);

            assertThat(motor.enviarPaso(paso("PULSAR", "Eliminar"))).isEqualTo(noAlcanzable);
        }
        try (MotorFalso motor = new MotorFalso();
                AgenteFalso agente = new AgenteFalso().respuestaPorDefecto(porDefecto)) {
            agente.conectar(motor.puerto(), motor.token());
            motor.esperarHola(ESPERA);

            assertThat(motor.enviarPaso(paso("PULSAR", "Eliminar"))).isEqualTo(porDefecto);
        }
    }

    @Test
    void elMotorRechazaUnTokenIncorrectoYElAgenteRecibeElError() throws Exception {
        try (MotorFalso motor = new MotorFalso("token-correcto");
                AgenteFalso agente = new AgenteFalso()) {
            agente.conectar(motor.puerto(), "token-incorrecto");

            assertThatThrownBy(() -> motor.esperarHola(ESPERA))
                    .isInstanceOf(ExcepcionProtocolo.class)
                    .hasMessageContaining(ErrorDto.TOKEN_INVALIDO);
            Mensaje error = agente.esperarMensaje(TipoMensaje.ERROR, ESPERA).orElseThrow();
            assertThat(error.getCuerpo().path("codigo").asText())
                    .isEqualTo(ErrorDto.TOKEN_INVALIDO);
            assertThat(agente.esperarFin(ESPERA)).isTrue();
        }
    }

    @Test
    void esperarHolaFallaSiNadieSeConecta() throws Exception {
        try (MotorFalso motor = new MotorFalso()) {
            assertThatThrownBy(() -> motor.esperarHola(Duration.ofMillis(200)))
                    .isInstanceOf(SocketTimeoutException.class);
        }
    }

    @Test
    void losComandosNecesitanElSaludoYFallanSiElAgenteSeDesconecta() throws Exception {
        try (MotorFalso motor = new MotorFalso()) {
            assertThatThrownBy(motor::pedirArbol).isInstanceOf(IllegalStateException.class);
        }
        try (MotorFalso motor = new MotorFalso();
                AgenteFalso agente = new AgenteFalso()) {
            agente.conectar(motor.puerto(), motor.token());
            motor.esperarHola(ESPERA);
            agente.close();

            assertThatThrownBy(motor::pedirArbol).isInstanceOf(ExcepcionProtocolo.class);
        }
    }

    @Test
    void elArgumentoDelAgenteLlevaPuertoYToken() throws Exception {
        try (MotorFalso motor = new MotorFalso("abc")) {
            assertThat(motor.argumentosAgente())
                    .isEqualTo("puerto=" + motor.puerto() + ",token=abc");
        }
    }

    private static PasoDto paso(String accion, String texto) {
        return new PasoDto(
                accion,
                new LocalizadorDto(List.of(new CriterioDto("TEXTO", texto))),
                null,
                "EVENTOS",
                1000);
    }
}
