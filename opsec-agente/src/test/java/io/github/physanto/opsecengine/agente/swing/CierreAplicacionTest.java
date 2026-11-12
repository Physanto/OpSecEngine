package io.github.physanto.opsecengine.agente.swing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pruebas del cierre de la aplicación sin ventanas. El pedido de cierre a las ventanas reales se
 * prueba en {@code AgenteIT}.
 */
class CierreAplicacionTest {

    @Test
    void terminaConCodigoCeroDespuesDelPlazo() {
        List<Integer> codigos = new ArrayList<>();
        List<String> mensajes = new ArrayList<>();
        long inicio = System.nanoTime();

        new CierreAplicacion(200, codigos::add, mensajes::add).run();

        assertThat((System.nanoTime() - inicio) / 1_000_000).isGreaterThanOrEqualTo(200);
        assertThat(codigos).containsExactly(0);
        assertThat(mensajes).isEmpty();
    }

    @Test
    void terminaAunqueSeInterrumpaLaEspera() {
        List<Integer> codigos = new ArrayList<>();
        Thread.currentThread().interrupt();
        try {
            new CierreAplicacion(60_000, codigos::add, mensaje -> {}).run();

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
        assertThat(codigos).containsExactly(0);
    }
}
