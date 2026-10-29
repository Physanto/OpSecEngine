package io.github.physanto.opsecengine.dominio.traza;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrazaTest {

    private static Evento evento(long secuencia, TipoEvento tipo) {
        return new Evento(
                secuencia,
                Instant.EPOCH.plusMillis(secuencia),
                tipo,
                new ContextoEvento("N3", 1, null, false),
                null);
    }

    private static final Traza TRAZA =
            new Traza(
                    "N3",
                    List.of(
                            evento(4, TipoEvento.PASO_FIN),
                            evento(1, TipoEvento.PASO_INICIO),
                            evento(3, TipoEvento.SQL),
                            evento(2, TipoEvento.SQL)));

    @Test
    void ordenaLosEventosPorSecuencia() {
        assertThat(TRAZA.eventos()).extracting(Evento::secuencia).containsExactly(1L, 2L, 3L, 4L);
    }

    @Test
    void rechazaSecuenciasRepetidas() {
        List<Evento> eventos = List.of(evento(1, TipoEvento.SQL), evento(1, TipoEvento.DIALOGO));

        assertThatThrownBy(() -> new Traza("N3", eventos))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1");
    }

    @Test
    void deTipoDevuelveLosEventosDeEseTipoEnOrden() {
        assertThat(TRAZA.deTipo(TipoEvento.SQL))
                .extracting(Evento::secuencia)
                .containsExactly(2L, 3L);
        assertThat(TRAZA.deTipo(TipoEvento.EXCEPCION)).isEmpty();
    }

    @Test
    void primeroDevuelveElDeMenorSecuencia() {
        assertThat(TRAZA.primero(TipoEvento.SQL)).map(Evento::secuencia).contains(2L);
        assertThat(TRAZA.primero(TipoEvento.DIALOGO)).isEmpty();
    }

    @Test
    void entreIncluyeAmbosExtremos() {
        assertThat(TRAZA.entre(2, 3)).extracting(Evento::secuencia).containsExactly(2L, 3L);
        assertThat(TRAZA.entre(0, 100)).hasSize(4);
        assertThat(TRAZA.entre(3, 2)).isEmpty();
    }

    @Test
    void estaVacia() {
        assertThat(new Traza("C1", null).estaVacia()).isTrue();
        assertThat(TRAZA.estaVacia()).isFalse();
    }

    @Test
    void esInmutable() {
        List<Evento> eventos = new ArrayList<>(List.of(evento(1, TipoEvento.SQL)));
        Traza traza = new Traza("C1", eventos);

        eventos.add(evento(2, TipoEvento.SQL));

        assertThat(traza.eventos()).hasSize(1);
        assertThatThrownBy(() -> traza.eventos().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> traza.deTipo(TipoEvento.SQL).clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void validaElIdDelCaso() {
        assertThatThrownBy(() -> new Traza(null, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Traza(" ", null)).isInstanceOf(IllegalArgumentException.class);
    }
}
