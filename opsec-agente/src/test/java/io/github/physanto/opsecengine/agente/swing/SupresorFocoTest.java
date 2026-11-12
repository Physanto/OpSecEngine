package io.github.physanto.opsecengine.agente.swing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Comprueba el cálculo de posición y la supresión del foco de ventanas y diálogos. */
class SupresorFocoTest {

    @Test
    void colocaLasVentanasADerechaDeTodasLasPantallasIncluidasLasNegativas() {
        List<Rectangle> pantallas =
                List.of(
                        new Rectangle(-1920, 0, 1920, 1080),
                        new Rectangle(0, 0, 1920, 1080),
                        new Rectangle(1920, -200, 2560, 1440));
        Point posicion = SupresorFoco.posicionOculta(pantallas);
        assertThat(posicion).isEqualTo(new Point(4580, 0));
        assertThat(pantallas)
                .allSatisfy(pantalla -> assertThat(pantalla.contains(posicion)).isFalse());
    }

    @Test
    void elModoOcultoSinPantallaEsInactivo() {
        org.junit.jupiter.api.Assumptions.assumeTrue(GraphicsEnvironment.isHeadless());
        try (SupresorFoco supresor = SupresorFoco.instalar(mensaje -> {})) {
            assertThat(supresor).isNotNull();
        }
    }

    @Test
    void ocultaVentanasYDialogosYLosRecolocaSiLaAppLosCentra() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "necesita pantalla");
        List<String> errores = new ArrayList<>();
        JFrame[] marco = new JFrame[1];
        JDialog[] dialogo = new JDialog[1];
        try (SupresorFoco supresor = SupresorFoco.instalar(errores::add)) {
            SwingUtilities.invokeAndWait(() -> {
                marco[0] = new JFrame("Oculta");
                marco[0].add(new JLabel("Contenido"));
                marco[0].pack();
                marco[0].setVisible(true);
                dialogo[0] = new JDialog(marco[0], "Diálogo oculto", false);
                dialogo[0].add(new JLabel("Contenido"));
                dialogo[0].pack();
                dialogo[0].setVisible(true);
            });
            esperarOcultas(marco[0], dialogo[0]);
            SwingUtilities.invokeAndWait(() -> {
                marco[0].setLocationRelativeTo(null);
                dialogo[0].setLocationRelativeTo(marco[0]);
                marco[0].toFront();
                marco[0].requestFocus();
            });
            esperarOcultas(marco[0], dialogo[0]);
            SwingUtilities.invokeAndWait(() -> assertThat(errores).isEmpty());
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (marco[0] != null) {
                    marco[0].dispose();
                }
            });
        }
    }

    /** Espera las notificaciones nativas de movimiento sin bloquear el EDT. */
    private static void esperarOcultas(Window... ventanas) throws Exception {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        AtomicBoolean completas = new AtomicBoolean();
        do {
            SwingUtilities.invokeAndWait(() -> {
                boolean ocultas = true;
                for (Window ventana : ventanas) {
                    ocultas &= estaOculta(ventana);
                }
                completas.set(ocultas);
            });
            if (completas.get()) {
                return;
            }
            Thread.sleep(20);
        } while (System.nanoTime() < limite);
        SwingUtilities.invokeAndWait(() -> {
            for (Window ventana : ventanas) {
                comprobar(ventana);
            }
        });
    }

    private static boolean estaOculta(Window ventana) {
        if (ventana.getFocusableWindowState()
                || ventana.isAutoRequestFocus()
                || ventana.isFocused()) {
            return false;
        }
        for (GraphicsDevice dispositivo :
                GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            for (java.awt.GraphicsConfiguration configuracion : dispositivo.getConfigurations()) {
                if (ventana.getBounds().intersects(configuracion.getBounds())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void comprobar(Window ventana) {
        assertThat(ventana.getFocusableWindowState()).isFalse();
        assertThat(ventana.isAutoRequestFocus()).isFalse();
        assertThat(ventana.isFocused()).isFalse();
        for (GraphicsDevice dispositivo :
                GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            for (java.awt.GraphicsConfiguration configuracion : dispositivo.getConfigurations()) {
                assertThat(ventana.getBounds().intersects(configuracion.getBounds()))
                        .as(
                                "Ventana %s en %s; pantalla %s",
                                ventana.getClass().getSimpleName(),
                                ventana.getBounds(),
                                configuracion.getBounds())
                        .isFalse();
            }
        }
    }
}
