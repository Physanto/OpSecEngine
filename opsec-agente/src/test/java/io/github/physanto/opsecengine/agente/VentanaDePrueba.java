package io.github.physanto.opsecengine.agente;

import java.awt.GridLayout;
import javax.swing.JButton;
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
            JOptionPane.showOptionDialog(
                    marco,
                    saludo,
                    "Saludo",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.INFORMATION_MESSAGE,
                    null,
                    new Object[] {"Cerrar"},
                    "Cerrar");
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
        marco.setVisible(true);
    }
}
