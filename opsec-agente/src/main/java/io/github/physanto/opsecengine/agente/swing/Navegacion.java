package io.github.physanto.opsecengine.agente.swing;

import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

/**
 * Acciones de navegación entre ventanas y pestañas: {@code IR_A} y {@code CERRAR_VENTANA}. Se usa
 * exclusivamente desde el EDT.
 */
final class Navegacion {

    private Navegacion() {}

    /**
     * Lleva al usuario hasta el componente: elige, de afuera hacia adentro, cada pestaña que lo
     * contiene, restaura su ventana si está minimizada, la trae al frente y, si el destino no es
     * una ventana, le pide el foco dentro de ella.
     *
     * <p>En modo semántico las pestañas se eligen con {@code setSelectedIndex}; en modo de eventos,
     * con un clic sintético sobre cada pestaña (o con {@code setSelectedIndex} si la pestaña no
     * tiene una zona visible donde hacer clic).
     *
     * @param destino componente o ventana de destino
     * @param eventos si se usan eventos sintéticos
     * @return true si el destino quedó visible
     */
    static boolean irA(Component destino, boolean eventos) {
        Window ventana = ventanaDe(destino);
        for (Pestana pestana : pestanasPorElegir(destino)) {
            if (pestana.panel.getSelectedIndex() != pestana.indice) {
                Rectangle zona = eventos ? pestana.panel.getBoundsAt(pestana.indice) : null;
                if (zona != null) {
                    AccionesEventos.clic(pestana.panel, zona);
                } else {
                    pestana.panel.setSelectedIndex(pestana.indice);
                }
            }
            if (ventana != null) {
                ventana.validate();
            }
        }
        if (ventana instanceof Frame) {
            Frame marco = (Frame) ventana;
            if ((marco.getExtendedState() & Frame.ICONIFIED) != 0) {
                marco.setExtendedState(marco.getExtendedState() & ~Frame.ICONIFIED);
            }
        }
        if (ventana != null) {
            ventana.toFront();
        }
        if (!(destino instanceof Window)) {
            destino.requestFocusInWindow();
        }
        return destino.isShowing();
    }

    /**
     * Pide cerrar la ventana del componente como lo haría el usuario con el botón de cierre: envía
     * {@code WINDOW_CLOSING}, así que se respetan la operación de cierre por defecto y los
     * escuchadores de la aplicación, que pueden pedir confirmación, ignorar el pedido o terminar la
     * aplicación.
     *
     * @param componente ventana o componente dentro de ella
     * @return la ventana a la que se le pidió cerrar
     */
    static Window cerrarVentana(Component componente) {
        Window ventana = ventanaDe(componente);
        if (ventana != null) {
            ventana.dispatchEvent(new WindowEvent(ventana, WindowEvent.WINDOW_CLOSING));
        }
        return ventana;
    }

    private static Window ventanaDe(Component componente) {
        return componente instanceof Window
                ? (Window) componente
                : SwingUtilities.getWindowAncestor(componente);
    }

    /** Pestañas que contienen al componente, de la más externa a la más interna. */
    private static List<Pestana> pestanasPorElegir(Component componente) {
        List<Pestana> pestanas = new ArrayList<>();
        Component actual = componente;
        while (actual != null && !(actual instanceof Window)) {
            Container padre = actual.getParent();
            if (padre instanceof JTabbedPane) {
                int indice = ((JTabbedPane) padre).indexOfComponent(actual);
                if (indice >= 0) {
                    pestanas.add(new Pestana((JTabbedPane) padre, indice));
                }
            }
            actual = padre;
        }
        Collections.reverse(pestanas);
        return pestanas;
    }

    /** Una pestaña de un {@code JTabbedPane}. */
    private static final class Pestana {

        private final JTabbedPane panel;
        private final int indice;

        Pestana(JTabbedPane panel, int indice) {
            this.panel = panel;
            this.indice = indice;
        }
    }
}
