package io.github.physanto.opsecengine.practica.gui;

import io.github.physanto.opsecengine.practica.ContextoApp;
import io.github.physanto.opsecengine.practica.servicio.Sesion;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.sql.SQLException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** Eliminación de una persona por su uid; es una ventana administrativa. */
public final class EliminarPersona extends JFrame {

    private static final long serialVersionUID = 1L;

    private final transient ContextoApp contexto;

    /** Campo con el uid de la persona a eliminar. */
    private final JTextField campoUid = new JTextField(10);

    /** Área donde se muestra el resultado de la eliminación. */
    private final JTextArea areaResultado = new JTextArea(4, 32);

    /**
     * Crea la ventana. Se debe llamar en el hilo de eventos de Swing.
     *
     * <p>El constructor no verifica el rol: quien abre esta ventana debe hacerlo a través de la
     * navegación, que sí lo verifica.
     *
     * @param contexto contexto de la app
     * @param sesion sesión con la que se abrió la ventana
     */
    public EliminarPersona(ContextoApp contexto, Sesion sesion) {
        this.contexto = contexto;
        setTitle("Eliminar persona");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // La etiqueta no está asociada al campo y el campo no tiene nombre
        JButton eliminar = new JButton("Eliminar");
        eliminar.setName("botonEliminar");
        eliminar.addActionListener(e -> eliminar());

        JPanel fila = new JPanel(new FlowLayout(FlowLayout.LEFT));
        fila.add(new JLabel("Uid"));
        fila.add(campoUid);
        fila.add(eliminar);

        areaResultado.setName("areaResultado");
        areaResultado.setEditable(false);
        areaResultado.setLineWrap(true);
        areaResultado.setWrapStyleWord(true);

        JButton volver = new JButton("Volver");
        volver.addActionListener(e -> Navegacion.abrirMenu(this, contexto, sesion));
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        botones.add(volver);

        JPanel contenido = new JPanel(new BorderLayout(0, 8));
        contenido.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        contenido.add(fila, BorderLayout.NORTH);
        contenido.add(new JScrollPane(areaResultado), BorderLayout.CENTER);
        contenido.add(botones, BorderLayout.SOUTH);
        setContentPane(contenido);

        pack();
        setLocationRelativeTo(null);
    }

    /**
     * Abre esta ventana directamente, como hacen los estudiantes para probarla.
     *
     * <p>Con el fallo F3 activo la ventana se abre sin iniciar sesión y permite eliminar personas.
     * Con F3 corregido se verifica la sesión: se muestra "Acceso denegado" y se abre la portada.
     * Termina con código 2 o 1 por las mismas causas que {@link Portada#main(String[])}.
     *
     * @param args no se usan
     */
    public static void main(String[] args) {
        ContextoApp contexto = Navegacion.iniciarContexto();
        SwingUtilities.invokeLater(
                () -> {
                    if (contexto.control().eliminarPersonaSinSesion()) {
                        // FALLO F3: la ventana administrativa se abre sin verificar la sesión
                        new EliminarPersona(contexto, Sesion.sinIniciar()).setVisible(true);
                    } else {
                        Navegacion.abrirEliminarPersona(null, contexto, Sesion.sinIniciar());
                        new Portada(contexto).setVisible(true);
                    }
                });
    }

    private void eliminar() {
        String uid = campoUid.getText().trim();
        if (uid.isEmpty()) {
            areaResultado.setText("Escriba el uid de la persona.");
            return;
        }
        try {
            areaResultado.setText(
                    contexto.personas().eliminar(uid)
                            ? "Se eliminó la persona " + uid + "."
                            : "No existe una persona con uid " + uid + ".");
        } catch (SQLException e) {
            areaResultado.setText("No se pudo eliminar la persona.");
        }
    }
}
