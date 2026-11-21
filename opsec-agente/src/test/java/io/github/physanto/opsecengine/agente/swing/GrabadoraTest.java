package io.github.physanto.opsecengine.agente.swing;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Component;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.AbstractButton;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Pruebas de la grabadora con eventos de AWT despachados a una ventana real. */
class GrabadoraTest {

    private VentanaLoginPrueba login;
    private Grabadora grabadora;

    @BeforeEach
    void abrir() throws Exception {
        login = VentanaLoginPrueba.abrir();
        grabadora = new Grabadora(login.emisor(), mensaje -> {});
        VentanaLoginPrueba.enEdt(() -> {
            grabadora.iniciar();
            return null;
        });
    }

    @AfterEach
    void cerrar() throws Exception {
        VentanaLoginPrueba.enEdt(() -> {
            grabadora.detener();
            return null;
        });
        if (login != null) {
            login.cerrar();
        }
    }

    private void enEdt(Runnable tarea) throws Exception {
        VentanaLoginPrueba.enEdt(() -> {
            tarea.run();
            return null;
        });
        VentanaLoginPrueba.vaciarEdt();
    }

    /** Simula que el usuario escribe: avisa de la tecla y deja el texto resultante. */
    private void escribir(JTextComponent campo, String texto) throws Exception {
        enEdt(() -> {
            campo.dispatchEvent(
                    new KeyEvent(
                            campo,
                            KeyEvent.KEY_TYPED,
                            System.currentTimeMillis(),
                            0,
                            KeyEvent.VK_UNDEFINED,
                            texto.charAt(0)));
            campo.setText(texto);
        });
    }

    private void clic(Component componente) throws Exception {
        enEdt(() -> {
            int x = componente.getWidth() / 2;
            int y = componente.getHeight() / 2;
            long ahora = System.currentTimeMillis();
            componente.dispatchEvent(
                    new MouseEvent(
                            componente,
                            MouseEvent.MOUSE_PRESSED,
                            ahora,
                            MouseEvent.BUTTON1_DOWN_MASK,
                            x,
                            y,
                            1,
                            false,
                            MouseEvent.BUTTON1));
            componente.dispatchEvent(
                    new MouseEvent(
                            componente,
                            MouseEvent.MOUSE_RELEASED,
                            ahora,
                            0,
                            x,
                            y,
                            1,
                            false,
                            MouseEvent.BUTTON1));
        });
    }

    private void tecla(Component componente, int id, int codigo) throws Exception {
        enEdt(() ->
                componente.dispatchEvent(
                        new KeyEvent(
                                componente,
                                id,
                                System.currentTimeMillis(),
                                0,
                                codigo,
                                KeyEvent.CHAR_UNDEFINED)));
    }

    private List<String> acciones() {
        return login.eventos.stream()
                .map(e -> e.get("accion") + ":" + e.get("valor"))
                .collect(Collectors.toList());
    }

    @Test
    void unaGrabacionDeLoginProduceLenarLenarYPulsarEnOrden() throws Exception {
        escribir(login.usuario, "Admin");
        escribir(login.contrasena, "12345");
        clic(login.ingresar);

        assertThat(login.eventos)
                .extracting(e -> e.get("accion"))
                .containsExactly("LLENAR", "LLENAR", "PULSAR");
        Map<String, Object> usuario = login.eventos.get(0);
        assertThat(usuario)
                .containsEntry("_tipo", "ACCION_GRABADA")
                .containsEntry("valor", "Admin")
                .containsEntry("ventana", "Inicio de sesión")
                .containsEntry("componente", "javax.swing.JTextField")
                .doesNotContainKey("sensible");
        assertThat(((List<?>) usuario.get("localizador")).get(0))
                .isEqualTo(Map.of("estrategia", "ETIQUETA", "valor", "Usuario"));
        Map<String, Object> contrasena = login.eventos.get(1);
        assertThat(contrasena).containsEntry("valor", "12345").containsEntry("sensible", true);
        assertThat(((List<?>) contrasena.get("localizador")).get(0))
                .isEqualTo(Map.of("estrategia", "ETIQUETA", "valor", "Contraseña"));
        Map<String, Object> pulsar = login.eventos.get(2);
        assertThat(pulsar).doesNotContainKey("valor");
        assertThat(((List<?>) pulsar.get("localizador")).get(0))
                .isEqualTo(Map.of("estrategia", "NOMBRE_COMPONENTE", "valor", "botonIngresar"));
        assertThat(login.pulsaciones).as("la aplicación sigue funcionando").hasValue(1);
    }

    @Test
    void elTextoEscritoSeEmiteUnaSolaVezAunqueSeEscribaPorPartes() throws Exception {
        escribir(login.usuario, "Ad");
        escribir(login.usuario, "Admin");
        clic(login.cancelar);

        assertThat(acciones()).containsExactly("LLENAR:Admin", "PULSAR:null");
    }

    @Test
    void elTextoSeEmiteAlPerderElFocoYNoSeRepiteSiNoCambio() throws Exception {
        enEdt(() ->
                login.usuario.dispatchEvent(
                        new FocusEvent(login.usuario, FocusEvent.FOCUS_GAINED)));
        enEdt(() -> login.usuario.setText("Cliente"));
        enEdt(() ->
                login.usuario.dispatchEvent(new FocusEvent(login.usuario, FocusEvent.FOCUS_LOST)));
        enEdt(() ->
                login.usuario.dispatchEvent(new FocusEvent(login.usuario, FocusEvent.FOCUS_LOST)));

        assertThat(acciones()).containsExactly("LLENAR:Cliente");
    }

    @Test
    void unaCasillaSeGrabaComoMarcarConElEstadoEnQueQuedo() throws Exception {
        clic(login.recordar);
        clic(login.recordar);

        assertThat(acciones()).containsExactly("MARCAR:true", "MARCAR:false");
        assertThat(login.eventos.get(0)).containsEntry("componente", "javax.swing.JCheckBox");
    }

    @Test
    void unaOpcionElegidaEnUnComboSeGrabaComoSeleccionar() throws Exception {
        enEdt(() -> {
            login.rol.dispatchEvent(
                    new KeyEvent(
                            login.rol,
                            KeyEvent.KEY_PRESSED,
                            System.currentTimeMillis(),
                            0,
                            KeyEvent.VK_ENTER,
                            KeyEvent.CHAR_UNDEFINED));
            // La elección ocurre mientras el combo procesa la tecla.
            login.rol.setSelectedIndex(1);
        });

        assertThat(acciones()).containsExactly("SELECCIONAR:Admin");
        assertThat(((List<?>) login.eventos.get(0).get("localizador")).get(0))
                .isEqualTo(Map.of("estrategia", "ETIQUETA", "valor", "Rol"));
    }

    @Test
    void enterEnUnCampoGrabaElTextoYLaPulsacionDelBotonPorDefecto() throws Exception {
        escribir(login.usuario, "Admin");
        tecla(login.usuario, KeyEvent.KEY_PRESSED, KeyEvent.VK_ENTER);

        assertThat(acciones()).containsExactly("LLENAR:Admin", "PULSAR:null");
        assertThat(((List<?>) login.eventos.get(1).get("localizador")).get(0))
                .isEqualTo(Map.of("estrategia", "NOMBRE_COMPONENTE", "valor", "botonIngresar"));
    }

    @Test
    void alDetenerseEmiteElTextoQueQuedoPendiente() throws Exception {
        escribir(login.usuario, "Admin");
        assertThat(login.eventos).isEmpty();

        enEdt(() -> grabadora.detener());

        assertThat(acciones()).containsExactly("LLENAR:Admin");
    }

    @Test
    void nadaSeGrabaDespuesDeDetenerse() throws Exception {
        enEdt(() -> grabadora.detener());

        clic(login.cancelar);
        escribir(login.usuario, "Admin");
        enEdt(() -> grabadora.detener());

        assertThat(login.eventos).isEmpty();
        assertThat(grabadora.activa()).isFalse();
    }

    @Test
    void unBotonDeshabilitadoNoSeGraba() throws Exception {
        enEdt(() -> login.cancelar.setEnabled(false));

        clic(login.cancelar);

        assertThat(login.eventos).isEmpty();
    }

    @Test
    void lasFlechasInternasDeUnSpinnerNoSeGrabanComoPulsaciones() throws Exception {
        JSpinner spinner = new JSpinner();
        JFrame[] otra = new JFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            otra[0] = new JFrame("Con spinner");
            otra[0].add(spinner);
            otra[0].setSize(200, 100);
            otra[0].setLocation(520, 80);
            otra[0].setVisible(true);
        });
        try {
            AbstractButton flecha =
                    VentanaLoginPrueba.enEdt(() -> {
                        for (Component hijo : spinner.getComponents()) {
                            if (hijo instanceof AbstractButton) {
                                return (AbstractButton) hijo;
                            }
                        }
                        return null;
                    });
            assertThat(flecha).isNotNull();

            clic(flecha);

            assertThat(login.eventos).isEmpty();
        } finally {
            SwingUtilities.invokeAndWait(otra[0]::dispose);
        }
    }

    @Test
    void unaListaGrabaLaOpcionQueSeEligeAlPresionar() throws Exception {
        JList<String> lista = new JList<>(new String[] {"Uno", "Dos", "Tres"});
        JFrame[] otra = new JFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            JPanel panel = new JPanel(new java.awt.BorderLayout());
            panel.add(new JScrollPane(lista));
            otra[0] = new JFrame("Con lista");
            otra[0].add(panel);
            otra[0].setSize(200, 200);
            otra[0].setLocation(520, 80);
            otra[0].setVisible(true);
        });
        try {
            VentanaLoginPrueba.vaciarEdt();
            enEdt(() -> {
                java.awt.Rectangle fila = lista.getCellBounds(1, 1);
                long ahora = System.currentTimeMillis();
                lista.dispatchEvent(
                        new MouseEvent(
                                lista,
                                MouseEvent.MOUSE_PRESSED,
                                ahora,
                                MouseEvent.BUTTON1_DOWN_MASK,
                                fila.x + 5,
                                fila.y + 5,
                                1,
                                false,
                                MouseEvent.BUTTON1));
                lista.dispatchEvent(
                        new MouseEvent(
                                lista,
                                MouseEvent.MOUSE_RELEASED,
                                ahora,
                                0,
                                fila.x + 5,
                                fila.y + 5,
                                1,
                                false,
                                MouseEvent.BUTTON1));
            });

            assertThat(acciones()).containsExactly("SELECCIONAR:Dos");
        } finally {
            SwingUtilities.invokeAndWait(otra[0]::dispose);
        }
    }
}
