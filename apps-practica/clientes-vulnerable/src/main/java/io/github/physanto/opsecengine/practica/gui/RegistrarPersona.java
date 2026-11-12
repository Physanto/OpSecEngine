package io.github.physanto.opsecengine.practica.gui;

import io.github.physanto.opsecengine.practica.ContextoApp;
import io.github.physanto.opsecengine.practica.datos.Persona;
import io.github.physanto.opsecengine.practica.servicio.*;
import java.awt.*;
import java.sql.SQLException;
import javax.swing.*;

/** Formulario de registro (nivel 2). Usar únicamente en el EDT. */
public final class RegistrarPersona extends JFrame {
    private static final long serialVersionUID = 1L;
    private final JTextField uid = new JTextField(20);
    private final JTextField nombre = new JTextField(20);
    private final JTextField documento = new JTextField(20);
    private final JTextField correo = new JTextField(20);
    private final JLabel resultado = new JLabel(" ");

    /**
     * Crea el formulario con componentes identificables.
     *
     * @param contexto contexto de práctica
     * @param sesion sesión del usuario
     */
    public RegistrarPersona(ContextoApp contexto, Sesion sesion) {
        setTitle("Registrar persona");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        JPanel campos = new JPanel(new GridLayout(0, 2, 8, 8));
        campo(campos, "Uid", uid);
        campo(campos, "Nombre", nombre);
        campo(campos, "Documento", documento);
        campo(campos, "Correo", correo);
        resultado.setName("resultadoRegistrarPersona");
        JButton guardar = new JButton("Guardar");
        guardar.setName("botonGuardarRegistrarPersona");
        guardar.addActionListener(e -> {
            EdicionPersonaServicio servicio =
                    new EdicionPersonaServicio(contexto.base(), contexto.interruptores());
            Persona persona =
                    new Persona(
                            uid.getText(), nombre.getText(), documento.getText(), correo.getText());
            try {
                servicio.registrar(persona, sesion);
                resultado.setText("Persona registrada.");
            } catch (IllegalArgumentException | SecurityException error) {
                resultado.setText(error.getMessage());
            } catch (SQLException error) {
                resultado.setText("No se pudo guardar la persona.");
            }
        });
        JButton volver = new JButton("Volver");
        volver.addActionListener(e -> Navegacion.abrirMenu(this, contexto, sesion));
        JPanel botones = new JPanel();
        botones.add(guardar);
        botones.add(volver);
        JPanel pie = new JPanel(new BorderLayout());
        pie.add(resultado, BorderLayout.NORTH);
        pie.add(botones, BorderLayout.SOUTH);
        JPanel contenido = new JPanel(new BorderLayout(8, 8));
        contenido.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        contenido.add(campos, BorderLayout.CENTER);
        contenido.add(pie, BorderLayout.SOUTH);
        setContentPane(contenido);
        pack();
        setLocationRelativeTo(null);
    }

    private static void campo(JPanel panel, String texto, JTextField campo) {
        campo.setName("campo" + texto);
        JLabel etiqueta = new JLabel(texto);
        etiqueta.setLabelFor(campo);
        panel.add(etiqueta);
        panel.add(campo);
    }
}
