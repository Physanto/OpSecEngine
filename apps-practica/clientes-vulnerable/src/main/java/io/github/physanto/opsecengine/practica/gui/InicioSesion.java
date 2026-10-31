package io.github.physanto.opsecengine.practica.gui;

import io.github.physanto.opsecengine.practica.ContextoApp;
import io.github.physanto.opsecengine.practica.servicio.ResultadoLogin;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.sql.SQLException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

/** Formulario de inicio de sesión con los campos Usuario y Contraseña. */
public final class InicioSesion extends JFrame {

    private static final long serialVersionUID = 1L;

    private final transient ContextoApp contexto;

    /** Campo con el nombre de usuario. */
    private final JTextField campoUsuario = new JTextField(16);

    /** Campo con la contraseña. */
    private final JPasswordField campoContrasena = new JPasswordField(16);

    /**
     * Crea el formulario. Se debe llamar en el hilo de eventos de Swing.
     *
     * @param contexto contexto de la app
     */
    public InicioSesion(ContextoApp contexto) {
        this.contexto = contexto;
        setTitle("Iniciar sesión");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // El campo Usuario tiene nombre y etiqueta asociada; Contraseña solo etiqueta asociada
        campoUsuario.setName("campoUsuario");
        JLabel etiquetaUsuario = new JLabel("Usuario");
        etiquetaUsuario.setLabelFor(campoUsuario);
        JLabel etiquetaContrasena = new JLabel("Contraseña");
        etiquetaContrasena.setLabelFor(campoContrasena);

        JPanel campos = new JPanel(new GridLayout(2, 2, 8, 8));
        campos.add(etiquetaUsuario);
        campos.add(campoUsuario);
        campos.add(etiquetaContrasena);
        campos.add(campoContrasena);

        JButton iniciar = new JButton("Iniciar Sesion");
        iniciar.setName("botonIniciarSesion");
        iniciar.addActionListener(e -> iniciarSesion());
        JButton volver = new JButton("Volver");
        volver.addActionListener(e -> Navegacion.cambiar(this, new EscogerModo(contexto)));

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        botones.add(volver);
        botones.add(iniciar);

        JPanel contenido = new JPanel(new BorderLayout(0, 16));
        contenido.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        contenido.add(campos, BorderLayout.CENTER);
        contenido.add(botones, BorderLayout.SOUTH);
        setContentPane(contenido);
        getRootPane().setDefaultButton(iniciar);

        pack();
        setLocationRelativeTo(null);
    }

    private void iniciarSesion() {
        String usuario = campoUsuario.getText();
        String contrasena = new String(campoContrasena.getPassword());
        ResultadoLogin resultado;
        try {
            resultado = contexto.login().iniciarSesion(usuario, contrasena);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(
                    this,
                    "No se pudo consultar la base de datos.",
                    "Iniciar sesión",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (resultado.esExitoso()) {
            Navegacion.abrirMenu(this, contexto, resultado.sesion().orElseThrow());
        } else {
            campoContrasena.setText("");
            JOptionPane.showMessageDialog(
                    this,
                    resultado.mensaje().orElse(""),
                    "Iniciar sesión",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}
