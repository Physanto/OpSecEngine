package io.github.physanto.opsecengine.practica.gui;

import io.github.physanto.opsecengine.practica.ContextoApp;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/** Primera ventana de la app: el título y el botón "Avanzar". */
public final class Portada extends JFrame {

    private static final long serialVersionUID = 1L;

    /**
     * Crea la portada. Se debe llamar en el hilo de eventos de Swing.
     *
     * @param contexto contexto de la app
     */
    public Portada(ContextoApp contexto) {
        setTitle("Portada");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JLabel titulo = new JLabel("Gestión de Clientes", SwingConstants.CENTER);
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 22f));
        JLabel subtitulo = new JLabel("App de práctica de OpSecEngine", SwingConstants.CENTER);

        JPanel textos = new JPanel(new GridLayout(2, 1, 0, 6));
        textos.add(titulo);
        textos.add(subtitulo);

        JButton avanzar = new JButton("Avanzar");
        avanzar.addActionListener(e -> Navegacion.cambiar(this, new EscogerModo(contexto)));
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.CENTER));
        botones.add(avanzar);
        JButton componentes = new JButton("Componentes");
        componentes.setName("botonComponentes");
        componentes.addActionListener(
                e -> Navegacion.cambiar(this, new ComponentesMuestra(contexto)));
        botones.add(componentes);

        JPanel contenido = new JPanel(new BorderLayout(0, 16));
        contenido.setBorder(BorderFactory.createEmptyBorder(24, 32, 24, 32));
        contenido.add(textos, BorderLayout.CENTER);
        contenido.add(botones, BorderLayout.SOUTH);
        setContentPane(contenido);

        pack();
        setLocationRelativeTo(null);
    }

    /**
     * Punto de entrada de la app: lee los interruptores de los fallos, crea la base con los datos
     * semilla y muestra la portada.
     *
     * <p>Si la propiedad {@code practica.corregir} contiene un fallo desconocido, termina con
     * código 2; si no se puede crear la base, con código 1.
     *
     * @param args no se usan
     */
    public static void main(String[] args) {
        ContextoApp contexto = Navegacion.iniciarContexto();
        SwingUtilities.invokeLater(() -> new Portada(contexto).setVisible(true));
    }
}
