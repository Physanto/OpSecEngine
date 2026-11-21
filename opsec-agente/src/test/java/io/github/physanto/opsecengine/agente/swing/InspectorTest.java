package io.github.physanto.opsecengine.agente.swing;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Component;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Pruebas del inspector sobre una ventana real; se omiten sin pantalla. */
class InspectorTest {

    private VentanaLoginPrueba login;
    private Inspector inspector;

    @BeforeEach
    void abrir() throws Exception {
        login = VentanaLoginPrueba.abrir();
        inspector = new Inspector(login.emisor(), mensaje -> {});
    }

    @AfterEach
    void cerrar() throws Exception {
        if (inspector != null) {
            VentanaLoginPrueba.enEdt(() -> {
                inspector.detener();
                return null;
            });
        }
        if (login != null) {
            login.cerrar();
        }
    }

    private Component panelDeCristal() {
        return login.ventana.getRootPane().getGlassPane();
    }

    private void iniciar() throws Exception {
        VentanaLoginPrueba.enEdt(() -> {
            inspector.iniciar();
            login.ventana.validate();
            return null;
        });
    }

    /** Envía un evento de ratón al panel de cristal, en el centro del componente indicado. */
    private void enviar(int tipo, Component sobre) throws Exception {
        VentanaLoginPrueba.enEdt(() -> {
            Component cristal = panelDeCristal();
            java.awt.Point centro =
                    SwingUtilities.convertPoint(
                            sobre, sobre.getWidth() / 2, sobre.getHeight() / 2, cristal);
            int botones = tipo == MouseEvent.MOUSE_PRESSED ? MouseEvent.BUTTON1_DOWN_MASK : 0;
            cristal.dispatchEvent(
                    new MouseEvent(
                            cristal,
                            tipo,
                            System.currentTimeMillis(),
                            botones,
                            centro.x,
                            centro.y,
                            1,
                            false,
                            tipo == MouseEvent.MOUSE_MOVED
                                    ? MouseEvent.NOBUTTON
                                    : MouseEvent.BUTTON1));
            return null;
        });
    }

    @Test
    void cubreLaVentanaConUnPanelPropioYLoDevuelveAlDetenerse() throws Exception {
        Component original = panelDeCristal();
        boolean visibleAntes = original.isVisible();

        iniciar();
        Component durante = panelDeCristal();
        VentanaLoginPrueba.enEdt(() -> {
            inspector.detener();
            return null;
        });

        assertThat(durante).isNotSameAs(original);
        assertThat(durante.isVisible()).isTrue();
        assertThat(panelDeCristal()).isSameAs(original);
        assertThat(original.isVisible()).isEqualTo(visibleAntes);
        assertThat(inspector.activo()).isFalse();
    }

    @Test
    void elClicEligeElComponenteYNoLlegaALaAplicacion() throws Exception {
        iniciar();

        enviar(MouseEvent.MOUSE_MOVED, login.ingresar);
        assertThat(login.eventos).as("pasar el cursor solo resalta").isEmpty();
        enviar(MouseEvent.MOUSE_PRESSED, login.ingresar);
        enviar(MouseEvent.MOUSE_RELEASED, login.ingresar);
        VentanaLoginPrueba.vaciarEdt();

        assertThat(login.pulsaciones).hasValue(0);
        assertThat(login.eventos).hasSize(1);
        Map<String, Object> evento = login.eventos.get(0);
        assertThat(evento)
                .containsEntry("_tipo", "COMPONENTE_INSPECCIONADO")
                .containsEntry("ventana", "Inicio de sesión")
                .containsEntry("componente", "javax.swing.JButton")
                .containsEntry("texto", "Iniciar sesion")
                .containsKey("ruta");
        List<?> localizador = (List<?>) evento.get("localizador");
        assertThat(localizador.get(0))
                .isEqualTo(Map.of("estrategia", "NOMBRE_COMPONENTE", "valor", "botonIngresar"));
    }

    @Test
    void siguienteInspeccionandoDespuesDeElegir() throws Exception {
        iniciar();

        enviar(MouseEvent.MOUSE_PRESSED, login.usuario);
        enviar(MouseEvent.MOUSE_PRESSED, login.cancelar);

        assertThat(login.eventos).hasSize(2);
        assertThat(login.eventos.get(0)).containsEntry("componente", "javax.swing.JTextField");
        assertThat(login.eventos.get(1)).containsEntry("texto", "Cancelar");
    }

    @Test
    void despuesDeDetenerseElClicVuelveALaAplicacion() throws Exception {
        iniciar();
        VentanaLoginPrueba.enEdt(() -> {
            inspector.detener();
            return null;
        });

        VentanaLoginPrueba.enEdt(() -> {
            login.ingresar.doClick(0);
            return null;
        });

        assertThat(login.pulsaciones).hasValue(1);
        assertThat(login.eventos).isEmpty();
    }

    @Test
    void cubreTambienLasVentanasQueSeAbrenDespues() throws Exception {
        iniciar();
        JFrame[] nueva = new JFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            nueva[0] = new JFrame("Nueva");
            nueva[0].setSize(200, 100);
            nueva[0].setLocation(520, 80);
            nueva[0].setVisible(true);
        });
        try {
            Component original = nueva[0].getRootPane().getGlassPane();
            long limite = System.nanoTime() + 3_000_000_000L;
            boolean cubierta = false;
            while (!cubierta && System.nanoTime() < limite) {
                Thread.sleep(50);
                cubierta =
                        VentanaLoginPrueba.enEdt(
                                () -> nueva[0].getRootPane().getGlassPane() != original);
            }

            assertThat(cubierta).isTrue();
        } finally {
            SwingUtilities.invokeAndWait(nueva[0]::dispose);
        }
    }
}
