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

/** Menú del cliente: solo permite buscar personas. */
public final class MenuCliente extends JFrame {

    private static final long serialVersionUID = 1L;

    /**
     * Crea el menú. Se debe llamar en el hilo de eventos de Swing.
     *
     * @param contexto contexto de la app
     * @param sesion sesión del cliente
     */
    public MenuCliente(ContextoApp contexto, Sesion sesion) {
        setTitle("Menú cliente");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JButton buscar = new JButton("Buscar Persona");
        buscar.addActionListener(
                e -> Navegacion.cambiar(this, new BuscarPersona(contexto, sesion)));
        JButton cerrarSesion = new JButton("Cerrar Sesion");
        cerrarSesion.addActionListener(e -> Navegacion.cambiar(this, new Portada(contexto)));

        JPanel botones = new JPanel(new GridLayout(2, 1, 0, 8));
        botones.add(buscar);
        botones.add(cerrarSesion);

        JPanel contenido = new JPanel(new BorderLayout(0, 16));
        contenido.setBorder(BorderFactory.createEmptyBorder(20, 32, 20, 32));
        contenido.add(
                new JLabel("Bienvenido, " + sesion.usuario().orElse(""), SwingConstants.CENTER),
                BorderLayout.NORTH);
        contenido.add(botones, BorderLayout.CENTER);
        setContentPane(contenido);

        pack();
        setLocationRelativeTo(null);
    }
}
