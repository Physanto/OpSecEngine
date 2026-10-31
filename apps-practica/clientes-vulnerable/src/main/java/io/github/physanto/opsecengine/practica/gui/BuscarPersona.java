package io.github.physanto.opsecengine.practica.gui;

import io.github.physanto.opsecengine.practica.ContextoApp;
import io.github.physanto.opsecengine.practica.datos.Persona;
import io.github.physanto.opsecengine.practica.servicio.Sesion;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.sql.SQLException;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/** Búsqueda de una persona por su uid; disponible para clientes y administradores. */
public final class BuscarPersona extends JFrame {

    private static final long serialVersionUID = 1L;

    private final transient ContextoApp contexto;

    /** Campo con el uid de la persona a buscar. */
    private final JTextField campoUid = new JTextField(10);

    /** Área donde se muestran los datos encontrados. */
    private final JTextArea resultado = new JTextArea(5, 32);

    /**
     * Crea la ventana. Se debe llamar en el hilo de eventos de Swing.
     *
     * @param contexto contexto de la app
     * @param sesion sesión actual; decide a qué menú vuelve el botón "Volver"
     */
    public BuscarPersona(ContextoApp contexto, Sesion sesion) {
        this.contexto = contexto;
        setTitle("Buscar persona");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // Campo con nombre y etiqueta asociada; el área de resultado no tiene nombre
        campoUid.setName("campoUid");
        JLabel etiquetaUid = new JLabel("Uid");
        etiquetaUid.setLabelFor(campoUid);
        JButton buscar = new JButton("Buscar");
        buscar.addActionListener(e -> buscar());

        JPanel fila = new JPanel(new FlowLayout(FlowLayout.LEFT));
        fila.add(etiquetaUid);
        fila.add(campoUid);
        fila.add(buscar);

        resultado.setEditable(false);
        resultado.setLineWrap(true);
        resultado.setWrapStyleWord(true);

        JButton volver = new JButton("Volver");
        volver.addActionListener(e -> Navegacion.abrirMenu(this, contexto, sesion));
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        botones.add(volver);

        JPanel contenido = new JPanel(new BorderLayout(0, 8));
        contenido.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        contenido.add(fila, BorderLayout.NORTH);
        contenido.add(new JScrollPane(resultado), BorderLayout.CENTER);
        contenido.add(botones, BorderLayout.SOUTH);
        setContentPane(contenido);
        getRootPane().setDefaultButton(buscar);

        pack();
        setLocationRelativeTo(null);
    }

    private void buscar() {
        String uid = campoUid.getText().trim();
        if (uid.isEmpty()) {
            resultado.setText("Escriba el uid de la persona.");
            return;
        }
        try {
            Optional<Persona> persona = contexto.personas().buscar(uid);
            resultado.setText(
                    persona.map(BuscarPersona::describir)
                            .orElse("No existe una persona con uid " + uid + "."));
        } catch (SQLException e) {
            resultado.setText("No se pudo consultar la base de datos.");
        }
    }

    private static String describir(Persona persona) {
        return "Uid: "
                + persona.uid()
                + "\nNombre: "
                + persona.nombre()
                + "\nDocumento: "
                + persona.documento()
                + "\nCorreo: "
                + persona.correo();
    }
}
