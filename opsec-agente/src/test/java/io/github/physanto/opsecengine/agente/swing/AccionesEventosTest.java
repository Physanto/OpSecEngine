package io.github.physanto.opsecengine.agente.swing;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Pruebas de eventos sintéticos y de las validaciones de la aplicación, incluso sin pantalla. */
class AccionesEventosTest {

    @Test
    void pulsarEntregaLosEventosDeRatonYDisparaLaAccionUnaVez() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JButton boton = new JButton("Aceptar");
            boton.setSize(120, 30);
            List<Integer> eventos = new ArrayList<>();
            AtomicInteger acciones = new AtomicInteger();
            boton.addActionListener(e -> acciones.incrementAndGet());
            boton.addMouseListener(
                    new MouseAdapter() {
                        @Override
                        public void mousePressed(MouseEvent evento) {
                            eventos.add(evento.getID());
                            assertThat(evento.getButton()).isEqualTo(MouseEvent.BUTTON1);
                            assertThat(SwingUtilities.isEventDispatchThread()).isTrue();
                        }

                        @Override
                        public void mouseReleased(MouseEvent evento) {
                            eventos.add(evento.getID());
                        }

                        @Override
                        public void mouseClicked(MouseEvent evento) {
                            eventos.add(evento.getID());
                        }
                    });

            AccionesEventos.pulsar(boton);

            assertThat(eventos)
                    .containsExactly(
                            MouseEvent.MOUSE_PRESSED,
                            MouseEvent.MOUSE_RELEASED,
                            MouseEvent.MOUSE_CLICKED);
            assertThat(acciones).hasValue(1);
            assertThat(boton.getModel().isPressed()).isFalse();
            assertThat(boton.getModel().isArmed()).isFalse();
        });
    }

    @Test
    void llenarReemplazaElTextoYEntregaLasTeclasSinFoco() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTextField campo = new JTextField("Anterior");
            StringBuilder escritas = new StringBuilder();
            campo.addKeyListener(
                    new KeyAdapter() {
                        @Override
                        public void keyTyped(KeyEvent evento) {
                            escritas.append(evento.getKeyChar());
                            assertThat(SwingUtilities.isEventDispatchThread()).isTrue();
                        }
                    });

            AccionesEventos.llenar(campo, "Ana á漢");

            assertThat(campo.isFocusOwner()).isFalse();
            assertThat(campo.getText()).isEqualTo("Ana á漢");
            assertThat(escritas).hasToString("Ana á漢");
            AccionesEventos.llenar(campo, "");
            assertThat(campo.getText()).isEmpty();
        });
    }

    @Test
    void llenarRespetaLasTeclasConsumidasPorLaAplicacion() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTextField campo = new JTextField();
            campo.addKeyListener(
                    new KeyAdapter() {
                        @Override
                        public void keyTyped(KeyEvent evento) {
                            if (!Character.isDigit(evento.getKeyChar())) {
                                evento.consume();
                            }
                        }
                    });

            AccionesEventos.llenar(campo, "a1b2");

            assertThat(campo.getText()).isEqualTo("12");
        });
    }

    @Test
    void llenaContrasenasYTextoMultilinea() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPasswordField contrasena = new JPasswordField("anterior");
            JTextArea texto = new JTextArea();

            AccionesEventos.llenar(contrasena, "Clave#2026");
            AccionesEventos.llenar(texto, "primera\nsegunda");

            assertThat(contrasena.getPassword()).containsExactly("Clave#2026".toCharArray());
            assertThat(texto.getText()).isEqualTo("primera\nsegunda");
        });
    }
}
