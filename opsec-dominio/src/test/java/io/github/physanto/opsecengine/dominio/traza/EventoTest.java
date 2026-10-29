package io.github.physanto.opsecengine.dominio.traza;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EventoTest {

    private static final Instant INSTANTE = Instant.parse("2026-10-28T15:30:00Z");

    private static Evento sql(Map<String, Object> datos) {
        return new Evento(1, INSTANTE, TipoEvento.SQL, ContextoEvento.fueraDeCaso(), datos);
    }

    @Test
    void leeCadaTipoDeDato() {
        Evento evento =
                sql(
                        Map.of(
                                "sql",
                                "DELETE FROM personas WHERE id = 3",
                                "filasAfectadas",
                                1,
                                "duracionMs",
                                12.5,
                                "exito",
                                true));

        assertThat(evento.texto("sql")).contains("DELETE FROM personas WHERE id = 3");
        assertThat(evento.numero("filasAfectadas"))
                .hasValueSatisfying(n -> assertThat(n.intValue()).isEqualTo(1));
        assertThat(evento.numero("duracionMs"))
                .hasValueSatisfying(n -> assertThat(n.doubleValue()).isEqualTo(12.5));
        assertThat(evento.booleano("exito")).contains(true);
    }

    @Test
    void lasLecturasDevuelvenVacioSiFaltaOElTipoNoCoincide() {
        Evento evento = sql(Map.of("filasAfectadas", "uno", "sql", 42, "lista", List.of("a")));

        assertThat(evento.texto("ausente")).isEmpty();
        assertThat(evento.texto("sql")).isEmpty();
        assertThat(evento.numero("filasAfectadas")).isEmpty();
        assertThat(evento.booleano("lista")).isEmpty();
    }

    @Test
    void lasLecturasRechazanClaveNull() {
        assertThatThrownBy(() -> sql(null).texto(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void validaSusCampos() {
        ContextoEvento contexto = ContextoEvento.fueraDeCaso();

        assertThatThrownBy(() -> new Evento(-1, INSTANTE, TipoEvento.SQL, contexto, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Evento(0, null, TipoEvento.SQL, contexto, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Evento(0, INSTANTE, null, contexto, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Evento(0, INSTANTE, TipoEvento.SQL, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void losDatosSonInmutables() {
        Map<String, Object> datos = new HashMap<>(Map.of("sql", "SELECT 1"));
        Evento evento = sql(datos);

        datos.put("otro", 1);

        assertThat(evento.datos()).containsOnlyKeys("sql");
        assertThatThrownBy(() -> evento.datos().put("x", 1))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void contextoValidaPasoYTextos() {
        assertThat(new ContextoEvento("A1", 2, "admin", true).sesionActiva()).isTrue();
        assertThatThrownBy(() -> new ContextoEvento("A1", -1, null, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ContextoEvento(" ", 1, null, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ContextoEvento("A1", 1, "", false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
