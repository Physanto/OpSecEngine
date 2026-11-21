package io.github.physanto.opsecengine.agente.swing;

import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Assumptions;

/**
 * Ventana de inicio de sesión de las pruebas del inspector y la grabadora: etiquetas junto a sus
 * campos, una casilla, un combo y dos botones, uno con nombre y otro sin él.
 */
final class VentanaLoginPrueba {

    final JFrame ventana = new JFrame("Inicio de sesión");
    final JTextField usuario = new JTextField(12);
    final JPasswordField contrasena = new JPasswordField(12);
    final JComboBox<String> rol = new JComboBox<>(new String[] {"Cliente", "Admin"});
    final JCheckBox recordar = new JCheckBox("Recordarme");
    final JButton ingresar = new JButton("Iniciar sesion");
    final JButton cancelar = new JButton("Cancelar");
    final AtomicInteger pulsaciones = new AtomicInteger();

    /** Eventos que recibió el emisor de la prueba: tipo y datos. */
    final List<Map<String, Object>> eventos = new CopyOnWriteArrayList<>();

    private VentanaLoginPrueba() {}

    /** Abre la ventana; omite la prueba si no hay pantalla. */
    static VentanaLoginPrueba abrir() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        VentanaLoginPrueba login = new VentanaLoginPrueba();
        SwingUtilities.invokeAndWait(login::construir);
        return login;
    }

    private void construir() {
        ingresar.setName("botonIngresar");
        ingresar.addActionListener(e -> pulsaciones.incrementAndGet());
        JPanel panel = new JPanel(new GridLayout(0, 2, 6, 6));
        panel.add(new JLabel("Usuario"));
        panel.add(usuario);
        panel.add(new JLabel("Contraseña"));
        panel.add(contrasena);
        panel.add(new JLabel("Rol"));
        panel.add(rol);
        panel.add(recordar);
        panel.add(new JLabel());
        panel.add(ingresar);
        panel.add(cancelar);
        ventana.add(panel);
        ventana.getRootPane().setDefaultButton(ingresar);
        ventana.setSize(420, 260);
        ventana.setLocation(80, 80);
        ventana.setVisible(true);
    }

    /** Registra en {@link #eventos} lo que se emite. */
    io.github.physanto.opsecengine.agente.api.EmisorEventos emisor() {
        return (tipo, datos) -> {
            Map<String, Object> evento = new java.util.LinkedHashMap<>(datos);
            evento.put("_tipo", tipo);
            eventos.add(evento);
        };
    }

    /** Ejecuta una tarea en el EDT y devuelve su resultado. */
    static <T> T enEdt(Callable<T> tarea) throws Exception {
        Object[] resultado = new Object[1];
        Exception[] fallo = new Exception[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                resultado[0] = tarea.call();
            } catch (Exception e) {
                fallo[0] = e;
            }
        });
        if (fallo[0] != null) {
            throw fallo[0];
        }
        @SuppressWarnings("unchecked")
        T valor = (T) resultado[0];
        return valor;
    }

    /** Espera a que el EDT procese lo que ya tiene en cola. */
    static void vaciarEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {});
        SwingUtilities.invokeAndWait(() -> {});
    }

    void cerrar() throws Exception {
        SwingUtilities.invokeAndWait(ventana::dispose);
    }
}
