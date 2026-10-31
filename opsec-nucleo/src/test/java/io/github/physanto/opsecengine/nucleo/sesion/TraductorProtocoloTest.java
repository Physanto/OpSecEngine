package io.github.physanto.opsecengine.nucleo.sesion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.physanto.opsecengine.dominio.caso.Accion;
import io.github.physanto.opsecengine.dominio.caso.ConfigEjecucion;
import io.github.physanto.opsecengine.dominio.caso.Criterio;
import io.github.physanto.opsecengine.dominio.caso.EstrategiaLocalizacion;
import io.github.physanto.opsecengine.dominio.caso.Localizador;
import io.github.physanto.opsecengine.dominio.caso.ModoAccion;
import io.github.physanto.opsecengine.dominio.caso.Paso;
import io.github.physanto.opsecengine.dominio.traza.ContextoEvento;
import io.github.physanto.opsecengine.dominio.traza.Evento;
import io.github.physanto.opsecengine.dominio.traza.TipoEvento;
import io.github.physanto.opsecengine.protocolo.dto.CriterioDto;
import io.github.physanto.opsecengine.protocolo.dto.EventoDto;
import io.github.physanto.opsecengine.protocolo.dto.LocalizadorDto;
import io.github.physanto.opsecengine.protocolo.dto.PasoDto;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TraductorProtocoloTest {

    private static final Localizador USUARIO =
            new Localizador(
                    List.of(
                            new Criterio(EstrategiaLocalizacion.ETIQUETA, "Usuario"),
                            new Criterio(EstrategiaLocalizacion.NOMBRE_COMPONENTE, "txtUsuario")));

    private static final ConfigEjecucion CONFIG =
            new ConfigEjecucion(ModoAccion.SEMANTICO, false, Duration.ofSeconds(4), 0, 0L, 1);

    @Test
    void conviertePasoEnDtoConElModoYLaEsperaDeLaConfiguracion() {
        Paso paso = new Paso(3, Accion.LLENAR, USUARIO, "Admin", null, null);

        PasoDto dto = TraductorProtocolo.aPasoDto(paso, CONFIG);

        assertThat(dto.getAccion()).isEqualTo("LLENAR");
        assertThat(dto.getValor()).isEqualTo("Admin");
        assertThat(dto.getModo()).isEqualTo("SEMANTICO");
        assertThat(dto.getEsperaMaximaMs()).isEqualTo(4000);
        assertThat(dto.getLocalizador().getCriterios())
                .containsExactly(
                        new CriterioDto("ETIQUETA", "Usuario"),
                        new CriterioDto("NOMBRE_COMPONENTE", "txtUsuario"));
    }

    @Test
    void laEsperaDelPasoReemplazaALaDeLaConfiguracion() {
        Paso paso = new Paso(1, Accion.PULSAR, USUARIO, null, Duration.ofMillis(1500), null);

        assertThat(TraductorProtocolo.aPasoDto(paso, CONFIG).getEsperaMaximaMs()).isEqualTo(1500);
    }

    @Test
    void elPasoVaYVuelveSinCambios() {
        Paso paso = new Paso(2, Accion.LLENAR, USUARIO, "Admin", Duration.ofSeconds(4), null);

        assertThat(TraductorProtocolo.aPaso(TraductorProtocolo.aPasoDto(paso, CONFIG), 2))
                .isEqualTo(paso);
    }

    @Test
    void unPasoSinObjetivoViajaSinLocalizador() {
        Paso captura = new Paso(1, Accion.CAPTURAR, null, null, null, null);

        PasoDto dto = TraductorProtocolo.aPasoDto(captura, CONFIG);

        assertThat(dto.getLocalizador()).isNull();
        assertThat(TraductorProtocolo.aPaso(dto, 1).objetivo()).isNull();
    }

    @Test
    void rechazaEstrategiasQueElDominioNoConoce() {
        LocalizadorDto dto = new LocalizadorDto(List.of(new CriterioDto("XPATH", "//boton")));

        assertThatThrownBy(() -> TraductorProtocolo.aLocalizador(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El dominio no conoce la estrategia de localización XPATH");
    }

    @Test
    void convierteEventoConLaSecuenciaYElContextoDelMotor() {
        EventoDto dto =
                new EventoDto(57, 1_793_287_800_123L, "SQL", Map.of("sql", "DELETE FROM personas"));
        ContextoEvento contexto = new ContextoEvento("A1", 4, "admin", true);

        Evento evento = TraductorProtocolo.aEvento(dto, 9, contexto);

        assertThat(evento.secuencia()).isEqualTo(9);
        assertThat(evento.instante()).isEqualTo(Instant.ofEpochMilli(1_793_287_800_123L));
        assertThat(evento.tipo()).isEqualTo(TipoEvento.SQL);
        assertThat(evento.contexto()).isEqualTo(contexto);
        assertThat(evento.texto("sql")).contains("DELETE FROM personas");
    }

    @Test
    void rechazaTiposDeEventoDesconocidos() {
        EventoDto dto = new EventoDto(0, 0, "TELEPATIA", Map.of());

        assertThatThrownBy(() -> TraductorProtocolo.aEvento(dto, 0, ContextoEvento.fueraDeCaso()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El dominio no conoce el tipo de evento TELEPATIA");
    }

    @Test
    void describeElLocalizadorComoEnElYaml() {
        assertThat(TraductorProtocolo.describir(USUARIO))
                .isEqualTo("etiqueta=Usuario, nombreComponente=txtUsuario");
        assertThat(TraductorProtocolo.describir(Localizador.porTexto("Avanzar")))
                .isEqualTo("texto=Avanzar");
    }
}
