package io.github.physanto.opsecengine.agente.swing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import io.github.physanto.opsecengine.protocolo.dto.CodigoResultado;
import io.github.physanto.opsecengine.protocolo.dto.CriterioDto;
import io.github.physanto.opsecengine.protocolo.dto.LocalizadorDto;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.util.Collections;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Pruebas de localización sobre ventanas reales, ejecutadas en el EDT. */
class BuscadorComponentesTest {

    private JFrame ventana;
    private JPanel panel;

    @BeforeEach
    void crearVentana() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "necesita pantalla");
        SwingUtilities.invokeAndWait(() -> {
            ventana = new JFrame("InicioSesion");
            ventana.setName("ventana-login");
            ventana.setFocusableWindowState(false);
            panel = new JPanel(null);
            ventana.setContentPane(panel);
            ventana.setSize(500, 350);
            ventana.setVisible(true);
        });
    }

    @AfterEach
    void cerrarVentana() throws Exception {
        if (ventana != null) {
            SwingUtilities.invokeAndWait(() -> ventana.dispose());
        }
    }

    @Test
    void asociaLaEtiquetaAlCampoMasCercanoDeLaDerechaAntesQueAlInferior() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            agregar(new JLabel("Usuario"), 20, 20, 80, 20);
            JTextField cercano = agregar(new JTextField(), 120, 20, 90, 20);
            agregar(new JTextField(), 240, 20, 90, 20);
            agregar(new JTextField(), 20, 45, 80, 20);
            assertThat(buscar("ETIQUETA", "Usuario").componente()).isSameAs(cercano);
        });
    }

    @Test
    void asociaLaEtiquetaAlCampoInferiorEnLaMismaColumna() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            agregar(new JLabel("Usuario"), 20, 20, 100, 20);
            JTextField inferior = agregar(new JTextField(), 20, 60, 100, 20);
            agregar(new JTextField(), 240, 60, 100, 20);
            assertThat(buscar("ETIQUETA", "Usuario").componente()).isSameAs(inferior);
        });
    }

    @Test
    void rechazaElEmpateDeDistanciasSinElegirUnCampo() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            agregar(new JLabel("Usuario"), 20, 20, 80, 40);
            agregar(new JTextField(), 120, 20, 80, 20);
            agregar(new JTextField(), 120, 40, 80, 20);
            ambiguo("ETIQUETA", "Usuario");
        });
    }

    @Test
    void respetaLabelForAntesQueLaCercania() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JLabel etiqueta = agregar(new JLabel("Usuario"), 20, 20, 80, 20);
            agregar(new JTextField(), 120, 20, 80, 20);
            JTextField asociado = agregar(new JTextField(), 260, 20, 80, 20);
            etiqueta.setLabelFor(asociado);
            assertThat(buscar("ETIQUETA", "Usuario").componente()).isSameAs(asociado);
        });
    }

    @Test
    void noAsociaCamposDeOtroContenedorNiCamposNoEditables() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            agregar(new JLabel("Usuario"), 20, 20, 80, 20);
            JTextField lectura = agregar(new JTextField(), 120, 20, 80, 20);
            lectura.setEditable(false);
            JPanel otro = agregar(new JPanel(), 220, 20, 100, 50);
            otro.add(new JTextField(5));
            assertThat(buscar("ETIQUETA", "Usuario").resultado().getCodigo())
                    .isEqualTo(CodigoResultado.NO_ENCONTRADO);
        });
    }

    @Test
    void resuelveRutasConIndicesOpcionalesYDistintasIdentidadesDeVentana() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTextField campo = agregar(new JTextField(), 20, 20, 80, 20);
            String sufijo = "/JRootPane/JLayeredPane/JPanel/JTextField";
            assertThat(buscar("RUTA", "InicioSesion" + sufijo).componente()).isSameAs(campo);
            assertThat(buscar("RUTA", "ventana-login" + sufijo).componente()).isSameAs(campo);
            assertThat(buscar("RUTA", "JFrame" + sufijo).componente()).isSameAs(campo);
            agregar(new JButton("Otro tipo"), 20, 80, 80, 20);
            JTextField segundo = agregar(new JTextField(), 20, 120, 80, 20);
            assertThat(buscar("RUTA", "InicioSesion" + sufijo + "[1]").componente())
                    .isSameAs(segundo);
            ambiguo("RUTA", "InicioSesion" + sufijo);
        });
    }

    @Test
    void unaRutaInexistenteOMalFormadaNoEncuentraNada() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            agregar(new JTextField(), 20, 20, 80, 20);
            for (String ruta :
                    new String[] {
                        "OtraVentana/JRootPane",
                        "InicioSesion/JTextField",
                        "InicioSesion/",
                        "InicioSesion/JRootPane[-1]",
                        "InicioSesion/JRootPane[999999999999999]",
                        "InicioSesion/JRootPane[1]"
                    }) {
                assertThat(buscar("RUTA", ruta).resultado().getCodigo())
                        .as(ruta)
                        .isEqualTo(CodigoResultado.NO_ENCONTRADO);
            }
        });
    }

    @Test
    void comparaSinTildesMayusculasNiEspaciosRepetidos() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            agregar(new JLabel("Contraseña"), 20, 20, 100, 20);
            JTextField campo = agregar(new JTextField(), 140, 20, 100, 20);
            campo.getAccessibleContext().setAccessibleName("Código personal");
            JButton boton = agregar(new JButton("Iniciar Sesión"), 20, 80, 180, 20);
            ventana.setTitle("Inicio de Sesión");
            assertThat(buscar("ETIQUETA", "contrasena").componente()).isSameAs(campo);
            assertThat(buscar("TEXTO", "  iniciar   sesion ").componente()).isSameAs(boton);
            assertThat(buscar("NOMBRE_ACCESIBLE", "codigo PERSONAL").componente()).isSameAs(campo);
            assertThat(buscar("TITULO_VENTANA", "inicio DE sesion").componente()).isSameAs(ventana);
        });
    }

    @Test
    void prefiereLaCoincidenciaExactaYRechazaLaFlexibleMultiple() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JButton exacto = agregar(new JButton("Sesión"), 20, 20, 100, 20);
            agregar(new JButton("SESION"), 20, 60, 100, 20);
            assertThat(buscar("TEXTO", "Sesión").componente()).isSameAs(exacto);
            ambiguo("TEXTO", "sesion");
        });
    }

    private <T extends Component> T agregar(T componente, int x, int y, int ancho, int alto) {
        componente.setBounds(x, y, ancho, alto);
        panel.add(componente);
        return componente;
    }

    private static BuscadorComponentes.Busqueda buscar(String estrategia, String valor) {
        return BuscadorComponentes.buscar(
                new LocalizadorDto(Collections.singletonList(new CriterioDto(estrategia, valor))));
    }

    private static void ambiguo(String estrategia, String valor) {
        BuscadorComponentes.Busqueda busqueda = buscar(estrategia, valor);
        assertThat(busqueda.componente()).isNull();
        assertThat(busqueda.resultado().getCodigo()).isEqualTo(CodigoResultado.NO_ENCONTRADO);
        assertThat(busqueda.resultado().getDetalle()).contains("ambiguo");
    }
}
