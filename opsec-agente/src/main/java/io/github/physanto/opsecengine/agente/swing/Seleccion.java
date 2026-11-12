package io.github.physanto.opsecengine.agente.swing;

import java.awt.Component;
import java.awt.Rectangle;
import javax.accessibility.Accessible;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.ListModel;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

/**
 * Busca y selecciona una opción por su texto en un combo, una lista, una tabla o un árbol. Se usa
 * exclusivamente desde el EDT.
 *
 * <p>El texto de cada opción es el {@code toString()} de su valor (en el árbol, el que devuelve
 * {@code convertValueToText}), comparado exactamente y sin espacios al inicio ni al final. En una
 * tabla se selecciona la primera fila con una celda que tenga ese texto; en un árbol, el primer
 * nodo en profundidad, aunque esté dentro de una rama contraída.
 */
final class Seleccion {

    /** Opción encontrada: el componente y la posición de la opción. */
    static final class Opcion {

        private final Component componente;
        private final int fila;
        private final int columna;
        private final TreePath ruta;

        private Opcion(Component componente, int fila, int columna, TreePath ruta) {
            this.componente = componente;
            this.fila = fila;
            this.columna = columna;
            this.ruta = ruta;
        }
    }

    private Seleccion() {}

    /**
     * Indica si el componente admite la acción {@code SELECCIONAR}.
     *
     * @param componente componente
     * @return true para combos, listas, tablas y árboles
     */
    static boolean admite(Component componente) {
        return componente instanceof JComboBox
                || componente instanceof JList
                || componente instanceof JTable
                || componente instanceof JTree;
    }

    /**
     * Busca la opción con el texto dado.
     *
     * @param componente combo, lista, tabla o árbol
     * @param valor texto de la opción
     * @return la opción, o null si el componente no la tiene o no admite selección
     */
    static Opcion buscar(Component componente, String valor) {
        String buscado = valor == null ? "" : valor.trim();
        if (componente instanceof JComboBox) {
            JComboBox<?> combo = (JComboBox<?>) componente;
            for (int i = 0; i < combo.getItemCount(); i++) {
                if (coincide(combo.getItemAt(i), buscado)) {
                    return new Opcion(combo, i, -1, null);
                }
            }
        } else if (componente instanceof JList) {
            ListModel<?> modelo = ((JList<?>) componente).getModel();
            for (int i = 0; i < modelo.getSize(); i++) {
                if (coincide(modelo.getElementAt(i), buscado)) {
                    return new Opcion(componente, i, -1, null);
                }
            }
        } else if (componente instanceof JTable) {
            JTable tabla = (JTable) componente;
            for (int fila = 0; fila < tabla.getRowCount(); fila++) {
                for (int columna = 0; columna < tabla.getColumnCount(); columna++) {
                    if (coincide(tabla.getValueAt(fila, columna), buscado)) {
                        return new Opcion(tabla, fila, columna, null);
                    }
                }
            }
        } else if (componente instanceof JTree) {
            JTree arbol = (JTree) componente;
            Object raiz = arbol.getModel().getRoot();
            if (raiz != null) {
                TreePath ruta = buscarNodo(arbol, new TreePath(raiz), buscado);
                if (ruta != null) {
                    return new Opcion(arbol, -1, -1, ruta);
                }
            }
        }
        return null;
    }

    /**
     * Selecciona la opción.
     *
     * <p>En modo semántico usa la API del componente ({@code setSelectedIndex}, {@code
     * changeSelection} o {@code setSelectionPath}). En modo de eventos lleva la opción a la vista y
     * envía un clic sobre ella; en un combo, la marca en la lista de su menú desplegable y envía la
     * liberación del ratón sobre esa lista, sin mostrar el menú.
     *
     * @param opcion opción encontrada con {@link #buscar(Component, String)}
     * @param eventos si se usan eventos sintéticos
     * @return true si la opción quedó seleccionada
     */
    static boolean seleccionar(Opcion opcion, boolean eventos) {
        Component componente = opcion.componente;
        if (componente instanceof JComboBox) {
            JComboBox<?> combo = (JComboBox<?>) componente;
            if (eventos) {
                seleccionarConEventos(combo, opcion.fila);
            } else {
                combo.setSelectedIndex(opcion.fila);
            }
            return combo.getSelectedIndex() == opcion.fila;
        }
        if (componente instanceof JList) {
            JList<?> lista = (JList<?>) componente;
            lista.ensureIndexIsVisible(opcion.fila);
            if (eventos) {
                Rectangle celda = lista.getCellBounds(opcion.fila, opcion.fila);
                if (celda == null) {
                    return false;
                }
                AccionesEventos.clic(lista, celda);
            } else {
                lista.setSelectedIndex(opcion.fila);
            }
            return lista.isSelectedIndex(opcion.fila);
        }
        if (componente instanceof JTable) {
            JTable tabla = (JTable) componente;
            Rectangle celda = tabla.getCellRect(opcion.fila, opcion.columna, true);
            tabla.scrollRectToVisible(celda);
            if (eventos) {
                AccionesEventos.clic(tabla, celda);
            } else {
                tabla.changeSelection(opcion.fila, opcion.columna, false, false);
            }
            return tabla.isRowSelected(opcion.fila);
        }
        JTree arbol = (JTree) componente;
        arbol.makeVisible(opcion.ruta);
        arbol.scrollPathToVisible(opcion.ruta);
        if (eventos) {
            Rectangle nodo = arbol.getPathBounds(opcion.ruta);
            if (nodo == null) {
                return false;
            }
            AccionesEventos.clic(arbol, nodo);
        } else {
            arbol.setSelectionPath(opcion.ruta);
        }
        return arbol.isPathSelected(opcion.ruta);
    }

    private static void seleccionarConEventos(JComboBox<?> combo, int indice) {
        Accessible hijo = combo.getUI().getAccessibleChild(combo, 0);
        if (!(hijo instanceof ComboPopup)) {
            return;
        }
        JList<?> lista = ((ComboPopup) hijo).getList();
        lista.setSelectedIndex(indice);
        Rectangle celda = lista.getCellBounds(indice, indice);
        AccionesEventos.soltar(lista, celda == null ? new Rectangle() : celda);
    }

    private static TreePath buscarNodo(JTree arbol, TreePath ruta, String buscado) {
        Object nodo = ruta.getLastPathComponent();
        TreeModel modelo = arbol.getModel();
        boolean esRaiz = ruta.getParentPath() == null;
        if (!(esRaiz && !arbol.isRootVisible())) {
            String texto =
                    arbol.convertValueToText(nodo, false, false, modelo.isLeaf(nodo), -1, false);
            if (texto != null && buscado.equals(texto.trim())) {
                return ruta;
            }
        }
        for (int i = 0; i < modelo.getChildCount(nodo); i++) {
            TreePath encontrada =
                    buscarNodo(arbol, ruta.pathByAddingChild(modelo.getChild(nodo, i)), buscado);
            if (encontrada != null) {
                return encontrada;
            }
        }
        return null;
    }

    private static boolean coincide(Object valor, String buscado) {
        return valor != null && buscado.equals(String.valueOf(valor).trim());
    }
}
