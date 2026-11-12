package io.github.physanto.opsecengine.agente.swing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Pruebas de la selección de opciones en los dos modos, incluso sin pantalla. */
class SeleccionTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void seleccionaEnUnComboYAvisaALaAplicacion(boolean eventos) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JComboBox<String> combo = new JComboBox<>(new String[] {"Cliente", "Admin"});
            List<Object> elegidos = new ArrayList<>();
            combo.addActionListener(e -> elegidos.add(combo.getSelectedItem()));

            Seleccion.Opcion opcion = Seleccion.buscar(combo, " Admin ");

            assertThat(opcion).isNotNull();
            assertThat(Seleccion.seleccionar(opcion, eventos)).isTrue();
            assertThat(combo.getSelectedItem()).isEqualTo("Admin");
            assertThat(elegidos).contains("Admin");
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void seleccionaEnUnaLista(boolean eventos) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JList<String> lista = new JList<>(new String[] {"Ana", "Luis", "Marta"});
            lista.setSize(200, 100);

            Seleccion.Opcion opcion = Seleccion.buscar(lista, "Marta");

            assertThat(Seleccion.seleccionar(opcion, eventos)).isTrue();
            assertThat(lista.getSelectedValue()).isEqualTo("Marta");
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void seleccionaLaFilaDeUnaTablaConUnaCeldaDeEseTexto(boolean eventos) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTable tabla =
                    new JTable(
                            new Object[][] {{1, "Ana"}, {2, "Luis"}, {3, "Marta"}},
                            new Object[] {"Id", "Nombre"});
            tabla.setSize(200, 100);

            Seleccion.Opcion opcion = Seleccion.buscar(tabla, "Luis");

            assertThat(Seleccion.seleccionar(opcion, eventos)).isTrue();
            assertThat(tabla.getSelectedRow()).isEqualTo(1);
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void seleccionaUnNodoDentroDeUnaRamaContraida(boolean eventos) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DefaultMutableTreeNode raiz = new DefaultMutableTreeNode("Inicio");
            DefaultMutableTreeNode clientes = new DefaultMutableTreeNode("Clientes");
            DefaultMutableTreeNode ana = new DefaultMutableTreeNode("Ana");
            clientes.add(ana);
            raiz.add(clientes);
            JTree arbol = new JTree(raiz);
            arbol.collapseRow(0);
            arbol.setSize(200, 200);

            Seleccion.Opcion opcion = Seleccion.buscar(arbol, "Ana");

            assertThat(Seleccion.seleccionar(opcion, eventos)).isTrue();
            assertThat(arbol.getSelectionPath())
                    .isEqualTo(new TreePath(new Object[] {raiz, clientes, ana}));
        });
    }

    @Test
    void noEncuentraLaRaizOcultaDeUnArbol() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTree arbol = new JTree(new DefaultMutableTreeNode("Inicio"));
            arbol.setRootVisible(false);

            assertThat(Seleccion.buscar(arbol, "Inicio")).isNull();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"Gerente", ""})
    void noEncuentraUnaOpcionQueNoExiste(String valor) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JComboBox<String> combo = new JComboBox<>(new String[] {"Cliente", "Admin"});

            assertThat(Seleccion.buscar(combo, valor)).isNull();
        });
    }

    @Test
    void soloAdmiteCombosListasTablasYArboles() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertThat(Seleccion.admite(new JTextField())).isFalse();
            assertThat(Seleccion.buscar(new JTextField("Admin"), "Admin")).isNull();
            assertThat(Seleccion.admite(new JComboBox<String>())).isTrue();
            assertThat(Seleccion.admite(new JList<String>())).isTrue();
            assertThat(Seleccion.admite(new JTable())).isTrue();
            assertThat(Seleccion.admite(new JTree())).isTrue();
        });
    }
}
