package io.github.physanto.opsecengine.practica.gui;

import io.github.physanto.opsecengine.practica.ContextoApp;
import io.github.physanto.opsecengine.practica.servicio.Sesion;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/** Menú del administrador: buscar y eliminar personas. */
public final class MenuAdministrador extends JFrame {

    private static final long serialVersionUID = 1L;

    /**
     * Crea el menú. Se debe llamar en el hilo de eventos de Swing.
     *
     * <p>El constructor no verifica el rol: quien abre esta ventana debe hacerlo a través de la
     * navegación, que sí lo verifica.
     *
     * @param contexto contexto de la app
     * @param sesion sesión con la que se abrió el menú
     */
    public MenuAdministrador(ContextoApp contexto, Sesion sesion) {
        setTitle("Menú administrador");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JButton buscar = new JButton("Buscar Persona");
        buscar.addActionListener(
                e -> Navegacion.cambiar(this, new BuscarPersona(contexto, sesion)));
        JButton eliminar = new JButton("Eliminar Persona");
        eliminar.setName("botonEliminarPersona");
        eliminar.addActionListener(e -> Navegacion.abrirEliminarPersona(this, contexto, sesion));
        JButton cerrarSesion = new JButton("Cerrar Sesion");
        cerrarSesion.addActionListener(e -> Navegacion.cambiar(this, new Portada(contexto)));

        JPanel botones = new JPanel(new GridLayout(3, 1, 0, 8));
        botones.add(buscar);
        botones.add(eliminar);
        botones.add(cerrarSesion);

        JPanel contenido = new JPanel(new BorderLayout(0, 16));
        contenido.setBorder(BorderFactory.createEmptyBorder(20, 32, 20, 32));
        contenido.add(
                new JLabel(
                        "Bienvenido, " + sesion.usuario().orElse("invitado"),
                        SwingConstants.CENTER),
                BorderLayout.NORTH);
        contenido.add(botones, BorderLayout.CENTER);
        setContentPane(contenido);

        pack();
        setLocationRelativeTo(null);
    }
}
