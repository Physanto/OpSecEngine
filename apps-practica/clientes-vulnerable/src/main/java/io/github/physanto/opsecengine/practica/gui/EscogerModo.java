package io.github.physanto.opsecengine.practica.gui;

import io.github.physanto.opsecengine.practica.ContextoApp;
import io.github.physanto.opsecengine.practica.servicio.Sesion;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * Selección del modo de trabajo: "Nube" o "Local".
 *
 * <p>"Nube" lleva al inicio de sesión. "Local" abre el menú del administrador sin iniciar sesión si
 * el fallo F2 está activo; si está corregido, avisa y lleva al inicio de sesión.
 */
public final class EscogerModo extends JFrame {

    private static final long serialVersionUID = 1L;

    /** Mensaje que se muestra al pulsar "Local" cuando el modo local exige iniciar sesión. */
    static final String MENSAJE_LOCAL_REQUIERE_SESION =
            "El modo local también requiere iniciar sesión.";

    /**
     * Crea la ventana. Se debe llamar en el hilo de eventos de Swing.
     *
     * @param contexto contexto de la app
     */
    public EscogerModo(ContextoApp contexto) {
        setTitle("Escoger modo");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JButton nube = new JButton("Nube");
        nube.setName("botonNube");
        nube.addActionListener(e -> Navegacion.cambiar(this, new InicioSesion(contexto)));

        JButton local = new JButton("Local");
        local.addActionListener(e -> abrirLocal(contexto));

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        botones.add(nube);
        botones.add(local);

        JPanel contenido = new JPanel(new BorderLayout(0, 16));
        contenido.setBorder(BorderFactory.createEmptyBorder(24, 32, 24, 32));
        contenido.add(
                new JLabel("¿Dónde están los datos?", SwingConstants.CENTER), BorderLayout.CENTER);
        contenido.add(botones, BorderLayout.SOUTH);
        setContentPane(contenido);

        pack();
        setLocationRelativeTo(null);
    }

    private void abrirLocal(ContextoApp contexto) {
        if (contexto.control().modoLocalSinSesion()) {
            // FALLO F2: el modo local abre el menú del administrador sin iniciar sesión
            Navegacion.abrirMenuAdministrador(this, contexto, Sesion.local());
            return;
        }
        JOptionPane.showMessageDialog(
                this, MENSAJE_LOCAL_REQUIERE_SESION, "Modo local", JOptionPane.INFORMATION_MESSAGE);
        Navegacion.cambiar(this, new InicioSesion(contexto));
    }
}
