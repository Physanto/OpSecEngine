package io.github.physanto.opsecengine.nucleo.sesion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.physanto.opsecengine.protocolo.ExcepcionProtocolo;
import io.github.physanto.opsecengine.protocolo.TipoMensaje;
import io.github.physanto.opsecengine.protocolo.dto.CodigoResultado;
import io.github.physanto.opsecengine.protocolo.dto.EventoDto;
import io.github.physanto.opsecengine.protocolo.dto.HolaDto;
import io.github.physanto.opsecengine.protocolo.dto.PasoDto;
import io.github.physanto.opsecengine.protocolo.dto.ResultadoPasoDto;
import io.github.physanto.opsecengine.soporte.AgenteFalso;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClienteAgenteTest {

    private static final Duration ESPERA = Duration.ofSeconds(5);
    private static final PasoDto PULSAR = new PasoDto("PULSAR", null, null, "EVENTOS", 1000);

    @Test
    void validaElSaludoYRecibeLosEventosDelPasoAntesDeSuResultado() throws Exception {
        EventoDto ventana = new EventoDto(0, 0, "VENTANA_ABIERTA", Map.of("ventanaId", "v1"));
        try (ClienteAgente cliente = new ClienteAgente();
                AgenteFalso agente = new AgenteFalso()) {
            agente.cuandoPaso(paso -> true)
                    .emitir(ventana)
                    .responder(new ResultadoPasoDto(CodigoResultado.OK, null, 5));
            agente.conectar(cliente.puerto(), cliente.token());

            HolaDto hola = cliente.esperarHola(ESPERA);
            ResultadoPasoDto resultado = cliente.enviarPaso(PULSAR);

            assertThat(hola.getVersionAgente()).isEqualTo(AgenteFalso.VERSION);
            assertThat(resultado.getCodigo()).isEqualTo(CodigoResultado.OK);
            assertThat(cliente.tomarEventos()).containsExactly(ventana);
            assertThat(cliente.tomarEventos()).isEmpty();
        }
    }

    @Test
    void pideArbolYCapturaYCierraLaConversacion() throws Exception {
        try (ClienteAgente cliente = new ClienteAgente();
                AgenteFalso agente = new AgenteFalso()) {
            agente.conectar(cliente.puerto(), cliente.token());
            cliente.esperarHola(ESPERA);

            assertThat(cliente.pedirArbol().getVentanas()).isEmpty();
            assertThat(Base64.getDecoder().decode(cliente.pedirCaptura(null).getBase64()))
                    .isEqualTo(AgenteFalso.imagenPng());
            assertThat(cliente.cerrar()).isTrue();
            assertThat(agente.esperarMensaje(TipoMensaje.CERRAR, ESPERA)).isPresent();
            assertThat(cliente.desconectado()).isTrue();
        }
    }

    @Test
    void rechazaUnAgenteConOtroToken() throws Exception {
        try (ClienteAgente cliente = new ClienteAgente();
                AgenteFalso agente = new AgenteFalso()) {
            agente.conectar(cliente.puerto(), "otro-token");

            assertThatThrownBy(() -> cliente.esperarHola(ESPERA))
                    .isInstanceOf(ExcepcionProtocolo.class)
                    .hasMessageContaining("TOKEN_INVALIDO");
            assertThat(agente.esperarMensaje(TipoMensaje.ERROR, ESPERA)).isPresent();
        }
    }

    @Test
    void avisaSiElAgenteNoSeConectaATiempo() throws Exception {
        try (ClienteAgente cliente = new ClienteAgente()) {
            assertThatThrownBy(() -> cliente.esperarHola(Duration.ofMillis(200)))
                    .isInstanceOf(SocketTimeoutException.class)
                    .hasMessage("El agente no se conectó en 200 ms");
        }
    }

    @Test
    void generaUnTokenDistintoEnCadaCliente() throws Exception {
        try (ClienteAgente uno = new ClienteAgente();
                ClienteAgente otro = new ClienteAgente()) {
            assertThat(uno.token()).isNotEqualTo(otro.token()).hasSizeGreaterThanOrEqualTo(32);
            assertThat(uno.puerto()).isNotEqualTo(otro.puerto());
        }
    }
}
