package io.github.physanto.opsecengine.agente.swing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import io.github.physanto.opsecengine.protocolo.dto.CriterioDto;
import io.github.physanto.opsecengine.protocolo.dto.LocalizadorDto;
import io.github.physanto.opsecengine.protocolo.dto.NodoArbolDto;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JRootPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Comprueba metadatos, rutas y alcance sin exponer contraseñas ni acciones internas. */
class DescriptorComponentesTest {

    private JFrame ventana;

    @AfterEach
    void cerrarVentana() throws Exception {
        if (ventana != null) {
            SwingUtilities.invokeAndWait(() -> ventana.dispose());
        }
    }

    @Test
    void enumeraLasAccionesAplicablesEnOrdenEstable() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            acciones(new JButton(), "PULSAR");
            acciones(new JTextField(), "LLENAR");
            acciones(new JPasswordField(), "LLENAR");
            acciones(new JComboBox<>(), "SELECCIONAR");
            acciones(new JList<>(), "SELECCIONAR");
            acciones(new JTable(), "SELECCIONAR");
            acciones(new JTabbedPane(), "SELECCIONAR");
            acciones(new JCheckBox(), "PULSAR", "MARCAR");
            acciones(new JRadioButton(), "PULSAR", "MARCAR");
            acciones(new JToggleButton(), "PULSAR", "MARCAR");
            acciones(new JMenuItem(), "PULSAR", "IR_A");
            acciones(new JCheckBoxMenuItem(), "PULSAR", "MARCAR", "IR_A");
            acciones(new JRadioButtonMenuItem(), "PULSAR", "MARCAR", "IR_A");
            acciones(new JLabel());
            acciones(new JPanel());
            acciones(new JRootPane());
            acciones(new JLayeredPane());
        });
    }

    @Test
    void conservaLasAccionesDeUnControlDeshabilitadoPeroNoPermiteLlenarUnoDeLectura()
            throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JButton boton = new JButton();
            boton.setEnabled(false);
            acciones(boton, "PULSAR");
            JTextField lectura = new JTextField();
            lectura.setEditable(false);
            acciones(lectura);
        });
    }

    @Test
    void noExponeLaContrasenaNiInventaUnaRutaParaUnControlSinVentana() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPasswordField campo = new JPasswordField("secreto");
            NodoArbolDto nodo = DescriptorComponentes.describir(campo, true);
            assertThat(nodo.getTexto()).isNull();
            assertThat(nodo.getRuta()).isNull();
            assertThat(nodo.isAlcanzable()).isFalse();
        });
    }

    @Test
    void lasRutasConservanContenedoresInternosYSeResuelvenSinAmbiguedad() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "necesita pantalla");
        SwingUtilities.invokeAndWait(() -> {
            JPanel panel = crearVentana();
            JTextField primero = agregar(panel, new JTextField(), 20, 20, 100, 30);
            agregar(panel, new JLabel("Etiqueta"), 20, 60, 100, 30);
            JTextField segundo = agregar(panel, new JTextField(), 20, 100, 100, 30);
            assertThat(DescriptorComponentes.ruta(primero))
                    .isEqualTo("Arbol/JRootPane[0]/JLayeredPane[0]/JPanel[0]/JTextField[0]");
            assertThat(DescriptorComponentes.ruta(segundo)).endsWith("/JTextField[1]");
            for (Component componente : List.of(primero, segundo)) {
                String ruta = DescriptorComponentes.ruta(componente);
                assertThat(
                                BuscadorComponentes.buscar(
                                                new LocalizadorDto(
                                                        List.of(new CriterioDto("RUTA", ruta))))
                                        .componente())
                        .isSameAs(componente);
            }
            ventana.setTitle("Título/con separador");
            ventana.setName("ventana-arbol");
            assertThat(DescriptorComponentes.ruta(primero)).startsWith("ventana-arbol/");
            ventana.setName(null);
            assertThat(DescriptorComponentes.ruta(primero)).startsWith("JFrame/");
        });
    }

    @Test
    void alcanzableExigeVisibilidadEstadoTamanoYAreaDentroDeLaVentana() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "necesita pantalla");
        SwingUtilities.invokeAndWait(() -> {
            JPanel panel = crearVentana();
            JButton boton = agregar(panel, new JButton("Acción"), 20, 20, 100, 30);
            assertThat(DescriptorComponentes.alcanzable(boton)).isTrue();
            ventana.setLocation(10000, 10000);
            assertThat(DescriptorComponentes.alcanzable(boton)).isTrue();
            boton.setEnabled(false);
            assertThat(DescriptorComponentes.alcanzable(boton)).isFalse();
            boton.setEnabled(true);
            boton.setVisible(false);
            assertThat(DescriptorComponentes.alcanzable(boton)).isFalse();
            boton.setVisible(true);
            panel.setVisible(false);
            assertThat(DescriptorComponentes.alcanzable(boton)).isFalse();
            panel.setVisible(true);
            boton.setSize(0, 30);
            assertThat(DescriptorComponentes.alcanzable(boton)).isFalse();
            boton.setSize(100, 0);
            assertThat(DescriptorComponentes.alcanzable(boton)).isFalse();
            boton.setBounds(1000, 20, 100, 30);
            assertThat(DescriptorComponentes.alcanzable(boton)).isFalse();
        });
    }

    @Test
    void elGlassPaneYSusHijosNoOfrecenAcciones() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "necesita pantalla");
        SwingUtilities.invokeAndWait(() -> {
            crearVentana();
            JPanel glass = new JPanel();
            JButton interno = new JButton("Interno");
            glass.add(interno);
            ventana.setGlassPane(glass);
            glass.setVisible(true);
            acciones(glass);
            acciones(interno);
            assertThat(DescriptorComponentes.ruta(interno)).contains("/JRootPane[0]/JPanel[0]/");
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void elArbolIncluyeDialogosYMenusEmergentesLigerosOPesados(boolean ligero) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "necesita pantalla");
        JPopupMenu menu = new JPopupMenu();
        SwingUtilities.invokeAndWait(() -> {
            JPanel panel = crearVentana();
            JButton boton = agregar(panel, new JButton("Abrir"), 20, 20, 100, 30);
            JDialog dialogo = new JDialog(ventana, "Diálogo", false);
            dialogo.add(new JLabel("Contenido"));
            dialogo.pack();
            dialogo.setFocusableWindowState(false);
            dialogo.setVisible(true);
            menu.setLightWeightPopupEnabled(ligero);
            menu.add(new JMenuItem("Opción emergente"));
            menu.show(boton, 0, boton.getHeight());
        });
        try {
            List<NodoArbolDto> raices = new AdaptadorSwing((tipo, datos) -> {}).arbol();
            assertThat(raices).extracting(NodoArbolDto::getTexto).contains("Arbol", "Diálogo");
            List<NodoArbolDto> nodos = new ArrayList<>();
            for (NodoArbolDto raiz : raices) {
                aplanar(raiz, nodos);
            }
            assertThat(nodos)
                    .anySatisfy(nodo -> {
                        assertThat(nodo.getTexto()).isEqualTo("Opción emergente");
                        assertThat(nodo.getRuta()).isNotBlank();
                        assertThat(nodo.getAcciones()).containsExactly("PULSAR", "IR_A");
                        assertThat(nodo.isAlcanzable()).isTrue();
                    });
        } finally {
            SwingUtilities.invokeAndWait(() -> menu.setVisible(false));
        }
    }

    private JPanel crearVentana() {
        ventana = new JFrame("Arbol");
        ventana.setFocusableWindowState(false);
        JPanel panel = new JPanel(null);
        ventana.setContentPane(panel);
        ventana.setSize(400, 300);
        ventana.setVisible(true);
        return panel;
    }

    private static <T extends Component> T agregar(
            JPanel panel, T componente, int x, int y, int ancho, int alto) {
        componente.setBounds(x, y, ancho, alto);
        panel.add(componente);
        return componente;
    }

    private static void acciones(Component componente, String... esperadas) {
        assertThat(DescriptorComponentes.describir(componente, false).getAcciones())
                .containsExactly(esperadas);
    }

    private static void aplanar(NodoArbolDto nodo, List<NodoArbolDto> nodos) {
        nodos.add(nodo);
        for (NodoArbolDto hijo : nodo.getHijos()) {
            aplanar(hijo, nodos);
        }
    }
}
