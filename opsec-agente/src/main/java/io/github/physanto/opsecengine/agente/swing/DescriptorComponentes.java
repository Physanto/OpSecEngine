package io.github.physanto.opsecengine.agente.swing;

import io.github.physanto.opsecengine.protocolo.dto.NodoArbolDto;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Frame;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.accessibility.AccessibleContext;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.text.JTextComponent;

/**
 * Describe componentes Swing como {@link NodoArbolDto}, sin exponer los objetos del toolkit.
 *
 * <p>El texto de un {@code JPasswordField} nunca se incluye. Se debe llamar desde el EDT.
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
                hijos);
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
