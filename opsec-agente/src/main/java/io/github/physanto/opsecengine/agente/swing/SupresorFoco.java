package io.github.physanto.opsecengine.agente.swing;

import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.Timer;

/**
 * Mantiene las ventanas no enfocables y fuera de todas las pantallas en modo oculto.
 *
 * <p>Observa aperturas, activaciones y cambios de posición o visibilidad en el EDT. No intercepta
 * bytecode ni exige foco nativo para las acciones semánticas o los eventos sintéticos. Los eventos
 * de apertura pueden llegar después de la primera presentación nativa de una ventana. Una revisión
 * cada 100 ms en el EDT corrige los cambios nativos que no dejan una notificación de movimiento
 * utilizable. La revisión se detiene al cerrar el supresor.
 */
public final class SupresorFoco implements AWTEventListener, AutoCloseable {

    private final Consumer<String> registro;
    private final boolean registrado;
    private final Timer vigilancia;
    private volatile boolean cerrado;

    private SupresorFoco(Consumer<String> registro, boolean registrado) {
        this.registro = Objects.requireNonNull(registro, "El registro no puede ser null");
        this.registrado = registrado;
        this.vigilancia = registrado ? new Timer(100, evento -> prepararVentanas()) : null;
    }

    /**
     * Instala el supresor antes del main de la aplicación y prepara las ventanas ya creadas.
     *
     * <p>Puede llamarse desde cualquier hilo; la preparación se encola en el EDT. Sin pantalla
     * devuelve un supresor inactivo. Los errores al atender ventanas se registran sin propagarlos.
     *
     * @param registro destino de errores; no puede ser null
     * @return supresor registrado o inactivo en un entorno headless
     * @throws NullPointerException si registro es null
     */
    public static SupresorFoco instalar(Consumer<String> registro) {
        boolean activo = !GraphicsEnvironment.isHeadless();
        SupresorFoco supresor = new SupresorFoco(registro, activo);
        if (activo) {
            Toolkit.getDefaultToolkit()
                    .addAWTEventListener(
                            supresor,
                            AWTEvent.WINDOW_EVENT_MASK
                                    | AWTEvent.WINDOW_FOCUS_EVENT_MASK
                                    | AWTEvent.COMPONENT_EVENT_MASK);
            EventQueue.invokeLater(() -> {
                if (!supresor.cerrado) {
                    supresor.prepararVentanas();
                    supresor.vigilancia.start();
                }
            });
        }
        return supresor;
    }

    /**
     * Impide el foco y recoloca las ventanas al abrirse, activarse, mostrarse o moverse.
     *
     * @param evento evento recibido en el EDT
     */
    @Override
    public void eventDispatched(AWTEvent evento) {
        if (cerrado || !(evento.getSource() instanceof Window)) {
            return;
        }
        int id = evento.getID();
        if (id == WindowEvent.WINDOW_OPENED
                || id == WindowEvent.WINDOW_ACTIVATED
                || id == WindowEvent.WINDOW_GAINED_FOCUS
                || id == ComponentEvent.COMPONENT_SHOWN
                || id == ComponentEvent.COMPONENT_MOVED) {
            preparar((Window) evento.getSource());
        }
    }

    private void prepararVentanas() {
        if (!cerrado) {
            for (Window ventana : Window.getWindows()) {
                preparar(ventana);
            }
        }
    }

    private void preparar(Window ventana) {
        try {
            ventana.setAutoRequestFocus(false);
            ventana.setFocusableWindowState(false);
            List<Rectangle> pantallas = new ArrayList<>();
            for (GraphicsDevice dispositivo :
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
                for (GraphicsConfiguration configuracion : dispositivo.getConfigurations()) {
                    pantallas.add(configuracion.getBounds());
                }
            }
            Point posicion = posicionOculta(pantallas);
            if (!posicion.equals(ventana.getLocation())) {
                ventana.setLocation(posicion);
            }
        } catch (RuntimeException e) {
            registro.accept("No se pudo ocultar una ventana: " + e);
        }
    }

    /**
     * Calcula una posición a la derecha de todas las pantallas, incluidas coordenadas negativas.
     */
    static Point posicionOculta(List<Rectangle> pantallas) {
        double derecha = 0;
        for (Rectangle pantalla : pantallas) {
            derecha = Math.max(derecha, pantalla.getMaxX());
        }
        return new Point((int) Math.min(Integer.MAX_VALUE, derecha + 100), 0);
    }

    /** Retira el observador desde cualquier hilo; no restaura las ventanas ya preparadas. */
    @Override
    public void close() {
        cerrado = true;
        if (registrado) {
            vigilancia.stop();
            Toolkit.getDefaultToolkit().removeAWTEventListener(this);
        }
    }
}
