package io.github.physanto.opsecengine.agente.swing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.GraphicsEnvironment;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Pruebas del vigilante del EDT; necesitan una ventana visible, así que se omiten sin pantalla. */
class VigilanteEdtTest {

    private static final long UMBRAL_MS = 200;

    private final List<Map<String, Object>> eventos = new CopyOnWriteArrayList<>();
    private JFrame ventana;
    private VigilanteEdt vigilante;

    @BeforeEach
    void abrirVentana() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(() -> {
            ventana = new JFrame("Vigilado");
            ventana.setSize(200, 100);
            ventana.setLocation(50, 50);
            ventana.setVisible(true);
        });
        vigilante =
                VigilanteEdt.iniciar(
                        (tipo, datos) -> {
                            assertThat(tipo).isEqualTo("TIEMPO");
                            eventos.add(datos);
                        },
                        UMBRAL_MS,
                        mensaje -> {});
    }

    @AfterEach
    void cerrar() throws Exception {
        if (vigilante != null) {
            vigilante.detener();
        }
        if (ventana != null) {
            SwingUtilities.invokeAndWait(ventana::dispose);
        }
    }

    @Test
    void informaCuandoElEdtSeBloqueaMasDelUmbral() throws Exception {
        bloquearEdt(800);

        esperarEvento();

        Map<String, Object> evento = eventos.get(0);
        assertThat(evento).containsEntry("medida", "EDT_BLOQUEADO");
        assertThat(evento).containsEntry("umbralMs", UMBRAL_MS);
        assertThat((Long) evento.get("duracionMs")).isGreaterThan(UMBRAL_MS);
        assertThat(eventos).as("un solo evento por bloqueo").hasSize(1);
    }

    @Test
    void noInformaSiElEdtResponde() throws Exception {
        Thread.sleep(700);

        assertThat(eventos).isEmpty();
    }

    @Test
    void noInformaUnBloqueoMasCortoQueElUmbral() throws Exception {
        bloquearEdt(50);
        Thread.sleep(500);

        assertThat(eventos).isEmpty();
    }

    @Test
    void rechazaUnUmbralNoPositivo() {
        assertThatThrownBy(() -> new VigilanteEdt((tipo, datos) -> {}, 0, mensaje -> {}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void bloquearEdt(long milisegundos) throws InterruptedException {
        CountDownLatch bloqueado = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            bloqueado.countDown();
            try {
                Thread.sleep(milisegundos);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertThat(bloqueado.await(5, TimeUnit.SECONDS)).isTrue();
    }

    private void esperarEvento() throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (eventos.isEmpty() && System.nanoTime() < limite) {
            Thread.sleep(25);
        }
        assertThat(eventos).as("el vigilante debe informar el bloqueo").isNotEmpty();
        Thread.sleep(300);
    }
}
