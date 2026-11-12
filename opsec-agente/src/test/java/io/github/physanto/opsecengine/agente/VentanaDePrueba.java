package io.github.physanto.opsecengine.agente;

import java.awt.GridLayout;
import java.awt.event.ItemEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/**
 * Aplicación Swing mínima para las pruebas de integración del agente.
 *
 * <p>Muestra la ventana "Ventana de prueba" con un campo "Nombre" (asociado a su etiqueta), un
 * botón "Saludar" que escribe "Hola, &lt;nombre&gt;" en la consola y lo muestra en un diálogo modal
 * con el botón "Cerrar", un botón "Fallar" que lanza una excepción y un botón deshabilitado
 * "Bloqueado". Termina sola después de 60 segundos.
 *
 * <p>Para las acciones de selección y navegación tiene además un combo "Rol" (Cliente o Admin), una
 * casilla "Recordar", unas pestañas "General" y "Avanzado" (esta con el botón "Detalle") y un botón
 * "Abrir" que muestra el diálogo no modal "Auxiliar", que se destruye al cerrarlo. Cada cambio se
 * escribe en la consola.
 *
 * <p>Para las excepciones que nadie captura tiene el botón "Fallar después", que lanza una
 * excepción en una tarea posterior del EDT, y "Fallar en hilo", que la lanza en el hilo
 * "trabajador".
 *
 * <p>Ni la ventana ni el diálogo pueden recibir el foco. Con pantalla real, el gestor de ventanas
 * le daría el foco al diálogo, y una tecla Espacio o Enter de quien esté usando el equipo pulsaría
 * "Cerrar" y cambiaría el resultado de la prueba. El agente no necesita el foco en los modos
 * semántico y de eventos sintéticos.
 */
public final class VentanaDePrueba {

    /** Título de la ventana. */
    public static final String TITULO = "Ventana de prueba";

    private VentanaDePrueba() {}

    /**
     * Abre la ventana.
     *
     * @param argumentos no se usan
     * @throws Exception si no se puede crear la ventana
     */
    public static void main(String[] argumentos) throws Exception {
        SwingUtilities.invokeAndWait(VentanaDePrueba::crear);
        Thread.sleep(60_000);
        System.exit(0);
    }

    private static void crear() {
        JFrame marco = new JFrame(TITULO);
        JLabel etiqueta = new JLabel("Nombre");
        JTextField campo = new JTextField(12);
        campo.addKeyListener(
                new KeyAdapter() {
                    @Override
                    public void keyTyped(KeyEvent evento) {
                        System.out.println("Tecla en Nombre");
                    }
                });
        etiqueta.setLabelFor(campo);
        JButton saludar = new JButton("Saludar");
        saludar.addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent evento) {
                        System.out.println("Ratón en Saludar");
                    }
                });
        saludar.addActionListener(e -> {
            String saludo = "Hola, " + campo.getText();
            System.out.println(saludo);
            JOptionPane panel =
                    new JOptionPane(
                            saludo,
                            JOptionPane.INFORMATION_MESSAGE,
                            JOptionPane.DEFAULT_OPTION,
                            null,
                            new Object[] {"Cerrar"},
                            "Cerrar");
            JDialog dialogo = panel.createDialog(marco, "Saludo");
            dialogo.setFocusableWindowState(false);
            dialogo.setVisible(true);
            dialogo.dispose();
        });
        JButton fallar = new JButton("Fallar");
        fallar.addActionListener(e -> {
            throw new IllegalStateException("Fallo sembrado");
        });
        JButton bloqueado = new JButton("Bloqueado");
        bloqueado.setEnabled(false);
        JLabel etiquetaRol = new JLabel("Rol");
        JComboBox<String> rol = new JComboBox<>(new String[] {"Cliente", "Admin"});
        rol.addActionListener(e -> System.out.println("Rol: " + rol.getSelectedItem()));
        etiquetaRol.setLabelFor(rol);
        JCheckBox recordar = new JCheckBox("Recordar");
        recordar.addItemListener(
                e -> System.out.println("Recordar: " + (e.getStateChange() == ItemEvent.SELECTED)));
        JTabbedPane pestanas = new JTabbedPane();
        pestanas.addTab("General", new JPanel());
        JPanel avanzado = new JPanel();
        JButton detalle = new JButton("Detalle");
        detalle.addActionListener(e -> System.out.println("Detalle pulsado"));
        avanzado.add(detalle);
        pestanas.addTab("Avanzado", avanzado);
        pestanas.addChangeListener(e ->
                System.out.println("Pestaña: " + pestanas.getTitleAt(pestanas.getSelectedIndex())));
        JButton abrir = new JButton("Abrir");
        abrir.addActionListener(e -> {
            JDialog auxiliar = new JDialog(marco, "Auxiliar", false);
            auxiliar.add(new JLabel("Ventana auxiliar"));
            auxiliar.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            auxiliar.setFocusableWindowState(false);
            auxiliar.pack();
            auxiliar.setVisible(true);
        });
        JButton fallarDespues = new JButton("Fallar después");
        fallarDespues.addActionListener(e ->
                SwingUtilities.invokeLater(() -> {
                    throw new IllegalStateException("Fallo en el EDT");
                }));
        JButton fallarEnHilo = new JButton("Fallar en hilo");
        fallarEnHilo.addActionListener(e ->
                new Thread(
                                () -> {
                                    throw new IllegalArgumentException("Fallo en el trabajador");
                                },
                                "trabajador")
                        .start());
        marco.setLayout(new GridLayout(0, 2));
        marco.add(etiqueta);
        marco.add(campo);
        marco.add(saludar);
        marco.add(fallar);
        marco.add(bloqueado);
        marco.add(abrir);
        marco.add(etiquetaRol);
        marco.add(rol);
        marco.add(recordar);
        marco.add(pestanas);
        marco.add(fallarDespues);
        marco.add(fallarEnHilo);
        marco.pack();
        marco.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        marco.setFocusableWindowState(false);
        marco.setVisible(true);
    }
}
