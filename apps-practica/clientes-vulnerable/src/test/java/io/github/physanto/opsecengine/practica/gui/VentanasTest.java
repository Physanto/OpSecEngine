package io.github.physanto.opsecengine.practica.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.physanto.opsecengine.practica.ContextoApp;
import io.github.physanto.opsecengine.practica.datos.BaseDatos;
import io.github.physanto.opsecengine.practica.datos.BasesDePrueba;
import io.github.physanto.opsecengine.practica.datos.Rol;
import io.github.physanto.opsecengine.practica.fallos.Fallo;
import io.github.physanto.opsecengine.practica.fallos.Interruptores;
import io.github.physanto.opsecengine.practica.servicio.Sesion;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.swing.AbstractButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de las ventanas: textos, nombres de componentes y etiquetas asociadas.
 *
 * <p>Se omiten si el entorno no tiene pantalla. Las ventanas se construyen en el hilo de eventos
 * sin mostrarse.
 */
class VentanasTest {

    private static final Sesion ADMIN = Sesion.iniciada("Admin", Rol.ADMINISTRADOR);

    private BaseDatos base;
    private ContextoApp contexto;
    private final List<JFrame> abiertas = new ArrayList<>();

    @BeforeEach
    void crearContexto() throws SQLException {
        Assumptions.assumeFalse(
                GraphicsEnvironment.isHeadless(), "Entorno sin pantalla: se omiten las ventanas");
        base = BasesDePrueba.nueva();
        contexto =
                ContextoApp.crear(
                        base,
                        Interruptores.todosActivos(),
                        new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void cerrar() throws Exception {
        enEdt(() -> {
            abiertas.forEach(JFrame::dispose);
            return null;
        });
        if (base != null) {
            base.close();
        }
    }

    @Test
    void laPortadaTieneElBotonAvanzar() throws Exception {
        JFrame portada = crear(() -> new Portada(contexto));

        assertThat(portada.getTitle()).isEqualTo("Portada");
        assertThat(textosDeBotones(portada)).containsExactly("Avanzar");
    }

    @Test
    void escogerModoOfreceNubeConNombreYLocalSinNombre() throws Exception {
        JFrame ventana = crear(() -> new EscogerModo(contexto));

        assertThat(boton(ventana, "Nube").getName()).isEqualTo("botonNube");
        assertThat(boton(ventana, "Local").getName()).isNull();
    }

    @Test
    void inicioSesionAsociaLasEtiquetasASusCampos() throws Exception {
        JFrame ventana = crear(() -> new InicioSesion(contexto));

        assertThat(ventana.getTitle()).isEqualTo("Iniciar sesión");
        Component usuario = etiqueta(ventana, "Usuario").getLabelFor();
        Component contrasena = etiqueta(ventana, "Contraseña").getLabelFor();
        assertThat(usuario).isInstanceOf(JTextField.class);
        assertThat(usuario.getName()).isEqualTo("campoUsuario");
        assertThat(contrasena).isInstanceOf(JPasswordField.class);
        assertThat(contrasena.getName()).isNull();
        assertThat(boton(ventana, "Iniciar Sesion").getName()).isEqualTo("botonIniciarSesion");
        assertThat(ventana.getRootPane().getDefaultButton().getText()).isEqualTo("Iniciar Sesion");
    }

    @Test
    void elBotonDeDiagnosticoDejaEscaparF8SoloCuandoEstaActivo() throws Exception {
        JFrame vulnerable = crear(() -> new InicioSesion(contexto));
        ContextoApp corregido =
                ContextoApp.crear(
                        base,
                        Interruptores.corrigiendo(Fallo.F8),
                        new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        JFrame reparada = crear(() -> new InicioSesion(corregido));

        enEdt(() -> {
            assertThatThrownBy(() -> boton(vulnerable, "Probar excepción").doClick())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("F8");
            assertThatCode(() -> boton(reparada, "Probar excepción").doClick())
                    .doesNotThrowAnyException();
            return null;
        });
    }

    @Test
    void losMenusMuestranLasOpcionesDeCadaRol() throws Exception {
        JFrame cliente =
                crear(() -> new MenuCliente(contexto, Sesion.iniciada("Cliente", Rol.CLIENTE)));
        JFrame admin = crear(() -> new MenuAdministrador(contexto, ADMIN));

        assertThat(textosDeBotones(cliente))
                .containsExactly("Buscar Persona", "Modificar Persona", "Cerrar Sesion");
        assertThat(textosDeBotones(admin))
                .containsExactly(
                        "Buscar Persona",
                        "Registrar Persona",
                        "Modificar Persona",
                        "Eliminar Persona",
                        "Cerrar Sesion");
        assertThat(boton(admin, "Eliminar Persona").getName()).isEqualTo("botonEliminarPersona");
    }

    @Test
    void losFormulariosDeRegistroYModificacionExponenSusCampos() throws Exception {
        JFrame registro = crear(() -> new RegistrarPersona(contexto, ADMIN));
        JFrame modificacion =
                crear(() ->
                        new ModificarPersona(contexto, Sesion.iniciada("Cliente", Rol.CLIENTE)));

        for (JFrame ventana : List.of(registro, modificacion)) {
            assertThat(ventana.getTitle()).isIn("Registrar persona", "Modificar persona");
            assertThat(boton(ventana, "Guardar").getName())
                    .isIn("botonGuardarRegistrarPersona", "botonGuardarModificarPersona");
            assertThat(etiqueta(ventana, "Uid").getLabelFor()).isInstanceOf(JTextField.class);
            assertThat(etiqueta(ventana, "Nombre").getLabelFor()).isInstanceOf(JTextField.class);
            assertThat(etiqueta(ventana, "Documento").getLabelFor()).isInstanceOf(JTextField.class);
            assertThat(etiqueta(ventana, "Correo").getLabelFor()).isInstanceOf(JTextField.class);
        }
    }

    @Test
    void eliminarPersonaTieneUnCampoSinNombreNiEtiqueta() throws Exception {
        JFrame ventana = crear(() -> new EliminarPersona(contexto, ADMIN));

        JTextField campo = unico(ventana, JTextField.class);
        assertThat(campo.getName()).isNull();
        assertThat(etiqueta(ventana, "Uid").getLabelFor()).isNull();
        assertThat(boton(ventana, "Eliminar").getName()).isEqualTo("botonEliminar");
        assertThat(unico(ventana, JTextArea.class).getName()).isEqualTo("areaResultado");
    }

    @Test
    void eliminarPersonaBorraLaFilaYMuestraElResultado() throws Exception {
        JFrame ventana = crear(() -> new EliminarPersona(contexto, ADMIN));

        String texto =
                enEdt(() -> {
                    unico(ventana, JTextField.class).setText(" P003 ");
                    boton(ventana, "Eliminar").doClick();
                    return unico(ventana, JTextArea.class).getText();
                });

        assertThat(texto).isEqualTo("Se eliminó la persona P003.");
        assertThat(contexto.personas().buscar("P003")).isEmpty();
    }

    @Test
    void eliminarPersonaAvisaSiElUidNoExisteOEstaVacio() throws Exception {
        JFrame ventana = crear(() -> new EliminarPersona(contexto, ADMIN));

        List<String> textos =
                enEdt(() -> {
                    List<String> resultado = new ArrayList<>();
                    JTextField campo = unico(ventana, JTextField.class);
                    AbstractButton eliminar = boton(ventana, "Eliminar");
                    campo.setText("P999");
                    eliminar.doClick();
                    resultado.add(unico(ventana, JTextArea.class).getText());
                    campo.setText("  ");
                    eliminar.doClick();
                    resultado.add(unico(ventana, JTextArea.class).getText());
                    return resultado;
                });

        assertThat(textos)
                .containsExactly(
                        "No existe una persona con uid P999.", "Escriba el uid de la persona.");
        assertThat(contexto.personas().listar()).hasSize(5);
    }

    @Test
    void buscarPersonaMuestraLosDatosDeLaPersona() throws Exception {
        JFrame ventana = crear(() -> new BuscarPersona(contexto, ADMIN));

        String texto =
                enEdt(() -> {
                    JTextField campo = (JTextField) etiqueta(ventana, "Uid").getLabelFor();
                    campo.setText("P005");
                    boton(ventana, "Buscar").doClick();
                    return unico(ventana, JTextArea.class).getText();
                });

        assertThat(texto).contains("Uid: P005", "Nombre: Valentina Castaño");
        assertThat(unico(ventana, JTextArea.class).getName()).isNull();
    }

    private JFrame crear(Supplier<JFrame> fabrica) throws Exception {
        JFrame ventana = enEdt(fabrica::get);
        abiertas.add(ventana);
        assertThat(ventana.isVisible()).isFalse();
        return ventana;
    }

    private static <T> T enEdt(Supplier<T> accion)
            throws InterruptedException, InvocationTargetException {
        AtomicReference<T> resultado = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> resultado.set(accion.get()));
        return resultado.get();
    }

    private static List<Component> componentes(Container raiz) {
        List<Component> todos = new ArrayList<>();
        for (Component hijo : raiz.getComponents()) {
            todos.add(hijo);
            if (hijo instanceof Container) {
                todos.addAll(componentes((Container) hijo));
            }
        }
        return todos;
    }

    private static <T extends Component> List<T> deTipo(JFrame ventana, Class<T> tipo) {
        return componentes(ventana.getContentPane()).stream()
                .filter(tipo::isInstance)
                .map(tipo::cast)
                .collect(Collectors.toList());
    }

    private static <T extends Component> T unico(JFrame ventana, Class<T> tipo) {
        List<T> encontrados = deTipo(ventana, tipo);
        assertThat(encontrados).hasSize(1);
        return encontrados.get(0);
    }

    private static List<String> textosDeBotones(JFrame ventana) {
        return deTipo(ventana, AbstractButton.class).stream()
                .map(AbstractButton::getText)
                .filter(texto -> !texto.equals("Volver"))
                .collect(Collectors.toList());
    }

    private static AbstractButton boton(JFrame ventana, String texto) {
        return deTipo(ventana, AbstractButton.class).stream()
                .filter(b -> texto.equals(b.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No hay botón " + texto));
    }

    private static JLabel etiqueta(JFrame ventana, String texto) {
        return deTipo(ventana, JLabel.class).stream()
                .filter(l -> texto.equals(l.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No hay etiqueta " + texto));
    }
}
