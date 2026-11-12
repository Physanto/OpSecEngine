package io.github.physanto.opsecengine.agente.swing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Pruebas de las capturas pintando componentes, incluso sin pantalla. */
class CapturasTest {

    @Test
    void pintaElComponenteYSusHijosSinMostrarlo() throws Exception {
        AtomicReference<BufferedImage> imagen = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            JPanel panel = new JPanel(new BorderLayout());
            JPanel rojo = new JPanel();
            rojo.setBackground(Color.RED);
            panel.add(rojo, BorderLayout.CENTER);
            panel.setSize(40, 30);
            panel.doLayout();
            imagen.set(Capturas.pintar(panel));
        });

        assertThat(imagen.get().getWidth()).isEqualTo(40);
        assertThat(imagen.get().getHeight()).isEqualTo(30);
        assertThat(new Color(imagen.get().getRGB(20, 15))).isEqualTo(Color.RED);
    }

    @Test
    void rechazaUnComponenteSinTamano() throws Exception {
        SwingUtilities.invokeAndWait(() ->
                assertThatThrownBy(() -> Capturas.pintar(new JPanel()))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("0x0"));
    }

    @Test
    void codificaEnPngYBase64() throws Exception {
        BufferedImage original = new BufferedImage(7, 5, BufferedImage.TYPE_INT_RGB);
        original.setRGB(3, 2, Color.BLUE.getRGB());

        byte[] png = Base64.getDecoder().decode(Capturas.codificarPng(original));
        BufferedImage leida = ImageIO.read(new ByteArrayInputStream(png));

        assertThat(png).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
        assertThat(leida.getWidth()).isEqualTo(7);
        assertThat(leida.getHeight()).isEqualTo(5);
        assertThat(new Color(leida.getRGB(3, 2))).isEqualTo(Color.BLUE);
    }

    @Test
    void noEligeNingunaVentanaSiNoHayVentanasVisibles() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertThat(Capturas.elegirVentana("Inexistente")).isNull();
            assertThat(Capturas.elegirVentana(null)).isNull();
        });
    }
}
