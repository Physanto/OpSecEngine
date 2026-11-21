package io.github.physanto.opsecengine.agente.swing;

import io.github.physanto.opsecengine.agente.api.EmisorEventos;
import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;

/**
 * Inspector de componentes: resalta el componente que está bajo el cursor y, al hacer clic, informa
 * su localizador sin que el clic llegue a la aplicación.
 *
 * <p>Cubre cada ventana visible con un panel transparente en lugar de su panel de cristal (glass
 * pane); al detenerse, devuelve el panel original tal como estaba. Las ventanas que se abren
 * mientras está activo también se cubren, en cuanto la aplicación las muestra. Cada clic con el
 * botón izquierdo emite un evento {@code COMPONENTE_INSPECCIONADO} y el inspector sigue activo
 * hasta {@link #detener()}. El teclado no se bloquea. No cubre los menús desplegables ni las
 * ventanas emergentes pesadas.
 *
 * <p>Todos sus métodos deben llamarse desde el EDT; no es seguro entre hilos.
 */
final class Inspector {

    private static final Color COLOR_BORDE = new Color(255, 140, 0);
    private static final Color COLOR_RELLENO = new Color(255, 140, 0, 50);

    private final EmisorEventos emisor;
    private final Consumer<String> errores;
    private final Map<Window, Superposicion> instaladas = new LinkedHashMap<>();
    private final AWTEventListener ventanasNuevas = this::alCambiarVentanas;
    private boolean activo;

    /**
     * Crea el inspector, todavía inactivo.
     *
     * @param emisor por donde salen los eventos {@code COMPONENTE_INSPECCIONADO}
     * @param errores recibe los problemas que no impiden seguir
     */
    Inspector(EmisorEventos emisor, Consumer<String> errores) {
        this.emisor = emisor;
        this.errores = errores;
    }

    /** Activa la inspección en todas las ventanas visibles. No hace nada si ya está activa. */
    void iniciar() {
        if (activo) {
            return;
        }
        activo = true;
        Toolkit.getDefaultToolkit()
                .addAWTEventListener(
                        ventanasNuevas, AWTEvent.WINDOW_EVENT_MASK | AWTEvent.COMPONENT_EVENT_MASK);
        for (Window ventana : Window.getWindows()) {
            instalar(ventana);
        }
    }

    /** Desactiva la inspección y devuelve a cada ventana su panel de cristal original. */
    void detener() {
        if (!activo) {
            return;
        }
        activo = false;
        Toolkit.getDefaultToolkit().removeAWTEventListener(ventanasNuevas);
        for (Superposicion superposicion : new ArrayList<>(instaladas.values())) {
            superposicion.retirar();
        }
        instaladas.clear();
    }

    /**
     * Indica si está activo.
     *
     * @return true entre {@link #iniciar()} y {@link #detener()}
     */
    boolean activo() {
        return activo;
    }

    private void alCambiarVentanas(AWTEvent evento) {
        if (!(evento.getSource() instanceof Window)) {
            return;
        }
        try {
            Window ventana = (Window) evento.getSource();
            switch (evento.getID()) {
                case WindowEvent.WINDOW_OPENED:
                case WindowEvent.WINDOW_ACTIVATED:
                // Algunos gestores de ventanas tardan en avisar de que se abrió.
                case ComponentEvent.COMPONENT_SHOWN:
                    // Llega en cuanto la aplicación muestra la ventana, sin esperar al sistema.
                    instalar(ventana);
                    break;
                case WindowEvent.WINDOW_CLOSED:
                    Superposicion superposicion = instaladas.remove(ventana);
                    if (superposicion != null) {
                        superposicion.retirar();
                    }
                    break;
                default:
                    break;
            }
        } catch (RuntimeException e) {
            errores.accept("El inspector no pudo cubrir una ventana: " + e);
        }
    }

    private void instalar(Window ventana) {
        if (!(ventana instanceof Frame || ventana instanceof Dialog)
                || !(ventana instanceof RootPaneContainer)
                || !ventana.isShowing()
                || instaladas.containsKey(ventana)) {
            return;
        }
        Superposicion superposicion =
                new Superposicion(((RootPaneContainer) ventana).getRootPane());
        instaladas.put(ventana, superposicion);
        superposicion.instalar();
    }

    private void elegir(Component componente) {
        try {
            emisor.emitir(
                    "COMPONENTE_INSPECCIONADO",
                    LocalizadoresComponente.datosDeInspeccion(componente));
        } catch (RuntimeException e) {
            errores.accept("No se pudo informar el componente inspeccionado: " + e);
        }
    }

    /** Panel transparente que resalta el componente bajo el cursor y atrapa los clics. */
    private final class Superposicion extends JComponent {

        private static final long serialVersionUID = 1L;

        private final transient JRootPane raiz;
        private transient Component previo;
        private boolean previoVisible;
        private transient Component resaltado;

        Superposicion(JRootPane raiz) {
            this.raiz = raiz;
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            MouseAdapter raton =
                    new MouseAdapter() {
                        @Override
                        public void mouseMoved(MouseEvent e) {
                            resaltar(e.getPoint());
                        }

                        @Override
                        public void mouseEntered(MouseEvent e) {
                            resaltar(e.getPoint());
                        }

                        @Override
                        public void mouseExited(MouseEvent e) {
                            resaltar(null);
                        }

                        @Override
                        public void mousePressed(MouseEvent e) {
                            if (SwingUtilities.isLeftMouseButton(e)) {
                                Component componente = componenteEn(e.getPoint());
                                if (componente != null) {
                                    elegir(componente);
                                }
                            }
                        }
                    };
            addMouseListener(raton);
            addMouseMotionListener(raton);
        }

        void instalar() {
            previo = raiz.getGlassPane();
            previoVisible = previo.isVisible();
            raiz.setGlassPane(this);
            setVisible(true);
        }

        void retirar() {
            if (raiz.getGlassPane() == this && previo != null) {
                raiz.setGlassPane(previo);
                previo.setVisible(previoVisible);
            }
        }

        private Component componenteEn(Point punto) {
            Point enCapas = SwingUtilities.convertPoint(this, punto, raiz.getLayeredPane());
            return SwingUtilities.getDeepestComponentAt(
                    raiz.getLayeredPane(), enCapas.x, enCapas.y);
        }

        private void resaltar(Point punto) {
            Component nuevo = punto == null ? null : componenteEn(punto);
            if (nuevo != resaltado) {
                resaltado = nuevo;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Component destino = resaltado;
            if (destino == null || !destino.isShowing()) {
                return;
            }
            Rectangle area =
                    SwingUtilities.convertRectangle(
                            destino,
                            new Rectangle(0, 0, destino.getWidth(), destino.getHeight()),
                            this);
            Graphics2D pincel = (Graphics2D) g.create();
            try {
                pincel.setColor(COLOR_RELLENO);
                pincel.fillRect(area.x, area.y, area.width, area.height);
                pincel.setColor(COLOR_BORDE);
                pincel.drawRect(
                        area.x, area.y, Math.max(0, area.width - 1), Math.max(0, area.height - 1));
                pincel.drawRect(
                        area.x + 1,
                        area.y + 1,
                        Math.max(0, area.width - 3),
                        Math.max(0, area.height - 3));
            } finally {
                pincel.dispose();
            }
        }
    }
}
