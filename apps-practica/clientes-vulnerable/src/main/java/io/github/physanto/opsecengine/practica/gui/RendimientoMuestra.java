package io.github.physanto.opsecengine.practica.gui;

import io.github.physanto.opsecengine.practica.ContextoApp;
import io.github.physanto.opsecengine.practica.fallos.Fallo;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/** Diagnósticos finitos F11–F13, activados solo por sus botones; construir y usar en el EDT. */
public final class RendimientoMuestra extends JFrame {
    private static final long serialVersionUID = 1L;
    private final List<SwingWorker<Void, Void>> trabajos = new ArrayList<>();
    private final JLabel resultado = new JLabel("Listo.");

    /**
     * Construye los controles de rendimiento con carga simulada de tres segundos.
     *
     * @param contexto interruptores y navegación de la app
     */
    public RendimientoMuestra(ContextoApp contexto) {
        this(
                contexto,
                () -> {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                    }
                });
    }

    RendimientoMuestra(ContextoApp contexto, Runnable carga) {
        setTitle("Rendimiento y robustez");
        setName("ventanaRendimiento");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        resultado.setName("resultadoRendimiento");
        JLabel retenidas = new JLabel("Ventanas retenidas: 0");
        retenidas.setName("resultadoVentanasRetenidas");
        JButton edt = boton("Probar EDT", "botonProbarEdt");
        edt.addActionListener(e ->
                ejecutar(
                        edt,
                        carga,
                        !contexto.interruptores().estaActivo(Fallo.F11),
                        "EDT terminado."));
        JButton lenta = boton("Probar acción lenta", "botonProbarAccionLenta");
        lenta.addActionListener(e ->
                ejecutar(
                        lenta,
                        contexto.interruptores().estaActivo(Fallo.F13) ? carga : () -> {},
                        true,
                        "Acción terminada."));
        JButton ventana = boton("Abrir y cerrar ventana", "botonProbarFugaVentanas");
        ventana.addActionListener(e -> {
            JDialog auxiliar = new JDialog(this, "Ventana temporal");
            auxiliar.setName("ventanaTemporal");
            auxiliar.add(new JLabel("Ventana temporal de prueba."));
            auxiliar.pack();
            auxiliar.setLocationRelativeTo(this);
            auxiliar.setVisible(true);
            auxiliar.setVisible(false);
            if (!contexto.interruptores().estaActivo(Fallo.F12)) auxiliar.dispose();
            retenidas.setText(
                    "Ventanas retenidas: "
                            + Arrays.stream(getOwnedWindows())
                                    .filter(Window::isDisplayable)
                                    .count());
        });
        JButton volver = boton("Volver", "botonVolverRendimiento");
        volver.addActionListener(e -> Navegacion.cambiar(this, new Portada(contexto)));
        JPanel contenido = new JPanel(new GridLayout(0, 1, 8, 8));
        contenido.setBorder(BorderFactory.createEmptyBorder(16, 24, 16, 24));
        contenido.add(new JLabel("Cada botón ejecuta un diagnóstico independiente."));
        contenido.add(edt);
        contenido.add(lenta);
        contenido.add(ventana);
        contenido.add(resultado);
        contenido.add(retenidas);
        contenido.add(volver);
        add(contenido, BorderLayout.CENTER);
        pack();
        setLocationRelativeTo(null);
    }

    private static JButton boton(String texto, String nombre) {
        JButton boton = new JButton(texto);
        boton.setName(nombre);
        return boton;
    }

    private void ejecutar(JButton boton, Runnable carga, boolean segundoPlano, String mensaje) {
        boton.setEnabled(false);
        resultado.setText("Trabajando...");
        if (!segundoPlano) {
            try {
                carga.run();
                resultado.setText(mensaje);
            } finally {
                boton.setEnabled(true);
            }
            return;
        }
        SwingWorker<Void, Void> trabajo =
                new SwingWorker<>() {
                    @Override
                    protected Void doInBackground() {
                        carga.run();
                        return null;
                    }

                    @Override
                    protected void done() {
                        trabajos.remove(this);
                        if (isCancelled() || !isDisplayable()) return;
                        try {
                            get();
                            resultado.setText(mensaje);
                        } catch (CancellationException error) {
                            resultado.setText("Operación cancelada.");
                        } catch (InterruptedException error) {
                            Thread.currentThread().interrupt();
                        } catch (ExecutionException error) {
                            resultado.setText("No se pudo completar el diagnóstico.");
                        } finally {
                            boton.setEnabled(true);
                        }
                    }
                };
        trabajos.add(trabajo);
        trabajo.execute();
    }

    /** Libera las ventanas propias y cancela los trabajos en curso; llamar en el EDT. */
    @Override
    public void dispose() {
        new ArrayList<>(trabajos).forEach(trabajo -> trabajo.cancel(true));
        trabajos.clear();
        super.dispose();
    }

    /**
     * Abre directamente los diagnósticos sin navegar por el login.
     *
     * @param args no usados
     */
    public static void main(String[] args) {
        ContextoApp contexto = Navegacion.iniciarContexto();
        SwingUtilities.invokeLater(() -> new RendimientoMuestra(contexto).setVisible(true));
    }
}
