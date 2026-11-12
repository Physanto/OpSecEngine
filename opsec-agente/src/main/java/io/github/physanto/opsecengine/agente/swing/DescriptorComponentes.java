package io.github.physanto.opsecengine.agente.swing;

import io.github.physanto.opsecengine.protocolo.dto.NodoArbolDto;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.accessibility.AccessibleContext;
import javax.swing.AbstractButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPasswordField;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JRootPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/**
 * Describe componentes Swing como {@link NodoArbolDto}, sin exponer los objetos del toolkit.
 *
 * <p>Incluye rutas con los contenedores internos y las acciones aplicables al tipo de control. El
 * texto de un {@code JPasswordField} nunca se incluye. Se debe llamar desde el EDT.
 */
final class DescriptorComponentes {

    private DescriptorComponentes() {}

    /**
     * Describe un componente y, si se pide, todos sus descendientes.
     *
     * @param componente componente o ventana
     * @param conHijos si se describen también los hijos
     * @return el nodo del componente
     */
    static NodoArbolDto describir(Component componente, boolean conHijos) {
        List<NodoArbolDto> hijos = Collections.emptyList();
        if (conHijos && componente instanceof Container) {
            hijos = new ArrayList<>();
            for (Component hijo : ((Container) componente).getComponents()) {
                hijos.add(describir(hijo, true));
            }
        }
        return new NodoArbolDto(
                componente.getClass().getName(),
                texto(componente),
                componente.getName(),
                nombreAccesible(componente),
                componente.isShowing(),
                componente.isEnabled(),
                componente instanceof JTextComponent && ((JTextComponent) componente).isEditable(),
                hijos,
                ruta(componente),
                alcanzable(componente),
                acciones(componente));
    }

    /**
     * Obtiene una ruta que conserva todos los ancestros hasta la ventana.
     *
     * @param componente control o ventana
     * @return localizador con índices entre hermanos de la misma clase; null si no hay ventana
     */
    static String ruta(Component componente) {
        if (componente instanceof Window) {
            String titulo = texto(componente);
            if (segmentoValido(titulo)) {
                return titulo;
            }
            String nombre = componente.getName();
            return segmentoValido(nombre) ? nombre : componente.getClass().getSimpleName();
        }
        Container padre = componente.getParent();
        if (padre == null) {
            return null;
        }
        String prefijo = ruta(padre);
        if (prefijo == null) {
            return null;
        }
        String clase = componente.getClass().getSimpleName();
        int indice = 0;
        for (Component hermano : padre.getComponents()) {
            if (hermano == componente) {
                return prefijo + "/" + clase + "[" + indice + "]";
            }
            if (clase.equals(hermano.getClass().getSimpleName())) {
                indice++;
            }
        }
        return null;
    }

    private static boolean segmentoValido(String texto) {
        return texto != null && !texto.trim().isEmpty() && texto.indexOf('/') < 0;
    }

    /**
     * Comprueba visibilidad, estado y área dentro de la ventana, sin depender de la pantalla.
     *
     * @param componente control o ventana
     * @return true si tiene área dentro de la ventana, está visible y está habilitado
     */
    static boolean alcanzable(Component componente) {
        if (!componente.isShowing()
                || !componente.isEnabled()
                || componente.getWidth() <= 0
                || componente.getHeight() <= 0) {
            return false;
        }
        Component actual = componente;
        while (!(actual instanceof Window)) {
            if (!actual.isVisible() || actual.getParent() == null) {
                return false;
            }
            actual = actual.getParent();
        }
        Window ventana = (Window) actual;
        Rectangle limites = new Rectangle(0, 0, ventana.getWidth(), ventana.getHeight());
        Rectangle area =
                componente == ventana
                        ? limites
                        : SwingUtilities.convertRectangle(
                                componente.getParent(), componente.getBounds(), ventana);
        return limites.intersects(area);
    }

    /**
     * Enumera las acciones del control; los contenedores internos y el glass pane no ofrecen
     * ninguna.
     *
     * @param componente control que se describe
     * @return acciones en orden estable, incluso cuando el control está deshabilitado u oculto
     */
    static List<String> acciones(Component componente) {
        List<String> acciones = new ArrayList<>();
        if (interno(componente)) {
            return acciones;
        }
        if (componente instanceof AbstractButton) {
            acciones.add("PULSAR");
        }
        if (componente instanceof JTextComponent && ((JTextComponent) componente).isEditable()) {
            acciones.add("LLENAR");
        }
        if (componente instanceof JComboBox
                || componente instanceof JList
                || componente instanceof JTable
                || componente instanceof JTabbedPane) {
            acciones.add("SELECCIONAR");
        }
        if (componente instanceof JToggleButton
                || componente instanceof JCheckBoxMenuItem
                || componente instanceof JRadioButtonMenuItem) {
            acciones.add("MARCAR");
        }
        if (componente instanceof JMenuItem) {
            acciones.add("IR_A");
        }
        return acciones;
    }

    private static boolean interno(Component componente) {
        if (componente instanceof JRootPane || componente instanceof JLayeredPane) {
            return true;
        }
        Window ventana = SwingUtilities.getWindowAncestor(componente);
        if (ventana instanceof RootPaneContainer) {
            Component glass = ((RootPaneContainer) ventana).getGlassPane();
            for (Component actual = componente; actual != null; actual = actual.getParent()) {
                if (actual == glass) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Devuelve el texto visible de un componente: el título de una ventana, el texto de un botón o
     * de una etiqueta, o el contenido de un campo de texto que no sea de contraseña.
     *
     * @param componente componente o ventana
     * @return el texto, o null si el componente no tiene texto visible
     */
    static String texto(Component componente) {
        if (componente instanceof Frame) {
            return ((Frame) componente).getTitle();
        }
        if (componente instanceof Dialog) {
            return ((Dialog) componente).getTitle();
        }
        if (componente instanceof AbstractButton) {
            return ((AbstractButton) componente).getText();
        }
        if (componente instanceof JLabel) {
            return ((JLabel) componente).getText();
        }
        if (componente instanceof JTextComponent && !(componente instanceof JPasswordField)) {
            return ((JTextComponent) componente).getText();
        }
        return null;
    }

    /**
     * Devuelve el nombre accesible de un componente.
     *
     * @param componente componente o ventana
     * @return el nombre accesible, o null si no tiene
     */
    static String nombreAccesible(Component componente) {
        try {
            AccessibleContext contexto = componente.getAccessibleContext();
            return contexto == null ? null : contexto.getAccessibleName();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
