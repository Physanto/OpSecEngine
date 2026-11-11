package io.github.physanto.opsecengine.agente;

import java.awt.GridLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Aplicación Swing mínima para las pruebas de integración del agente.
 *
 * <p>Muestra la ventana "Ventana de prueba" con un campo "Nombre" (asociado a su etiqueta), un
 * botón "Saludar" que escribe "Hola, &lt;nombre&gt;" en la consola y lo muestra en un diálogo modal
 * con el botón "Cerrar", un botón "Fallar" que lanza una excepción y un botón deshabilitado
 * "Bloqueado". Termina sola después de 60 segundos.
 *
 * <p>Ni la ventana ni el diálogo pueden recibir el foco. Con pantalla real, el gestor de ventanas
 * le daría el foco al diálogo, y una tecla Espacio o Enter de quien esté usando el equipo pulsaría
 * "Cerrar" y cambiaría el resultado de la prueba. El agente no necesita el foco: en el modo
 * semántico usa {@code doClick} y {@code setText}.
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
        etiqueta.setLabelFor(campo);
        JButton saludar = new JButton("Saludar");
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
        marco.setLayout(new GridLayout(0, 2));
        marco.add(etiqueta);
        marco.add(campo);
        marco.add(saludar);
        marco.add(fallar);
        marco.add(bloqueado);
        marco.pack();
        marco.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        marco.setFocusableWindowState(false);
        marco.setVisible(true);
    }
}
