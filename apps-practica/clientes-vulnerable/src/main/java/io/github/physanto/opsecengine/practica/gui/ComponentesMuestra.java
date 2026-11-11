package io.github.physanto.opsecengine.practica.gui;

import io.github.physanto.opsecengine.practica.ContextoApp;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

/** Componentes de práctica para grabar acciones e inspeccionar localizadores; usar en el EDT. */
public final class ComponentesMuestra extends JFrame {
    private static final long serialVersionUID = 1L;

    /**
     * Crea una pantalla independiente de los datos del CRUD, sin fallos de seguridad añadidos.
     *
     * @param contexto contexto al que se vuelve desde la portada
     */
    public ComponentesMuestra(ContextoApp contexto) {
        setTitle("Componentes de práctica");
        setName("ventanaComponentes");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JTabbedPane pestanas = new JTabbedPane();
        pestanas.setName("pestanasPractica");
        JLabel resultado = new JLabel("Seleccione una persona o categoría.");
        resultado.setName("resultadoComponentes");

        DefaultTableModel modelo =
                new DefaultTableModel(
                        new Object[][] {{"P001", "Ana"}, {"P002", "Luis"}, {"P003", "María"}},
                        new String[] {"Uid", "Nombre"}) {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public boolean isCellEditable(int fila, int columna) {
                        return false;
                    }
                };
        JTable tabla = new JTable(modelo);
        tabla.setName("tablaPersonas");
        tabla.getAccessibleContext().setAccessibleName("Personas de muestra");
        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabla.getSelectionModel()
                .addListSelectionListener(e -> {
                    if (!e.getValueIsAdjusting() && tabla.getSelectedRow() >= 0) {
                        resultado.setText(
                                "Persona seleccionada: "
                                        + tabla.getValueAt(tabla.getSelectedRow(), 0));
                    }
                });
        tabla.setRowSelectionInterval(0, 0);
        JPanel personas = new JPanel(new BorderLayout());
        personas.add(new JScrollPane(tabla), BorderLayout.CENTER);
        personas.add(
                boton(
                        "Siguiente persona",
                        "botonSiguientePersona",
                        () -> {
                            int fila = (tabla.getSelectedRow() + 1) % tabla.getRowCount();
                            tabla.setRowSelectionInterval(fila, fila);
                        }),
                BorderLayout.SOUTH);
        pestanas.addTab("Tabla", personas);

        JList<String> lista = new JList<>(new String[] {"Ventas", "Soporte", "Administración"});
        lista.setName("listaCategorias");
        lista.getAccessibleContext().setAccessibleName("Categorías");
        lista.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lista.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && lista.getSelectedValue() != null) {
                resultado.setText("Categoría seleccionada: " + lista.getSelectedValue());
            }
        });
        JLabel etiqueta = new JLabel("Categorías");
        etiqueta.setLabelFor(lista);
        JPanel categorias = new JPanel(new BorderLayout());
        categorias.add(etiqueta, BorderLayout.NORTH);
        categorias.add(new JScrollPane(lista), BorderLayout.CENTER);
        pestanas.addTab("Lista", categorias);

        JPanel botones = new JPanel(new FlowLayout());
        botones.add(boton("Ver tabla", "botonVerTabla", () -> pestanas.setSelectedIndex(0)));
        botones.add(boton("Ver lista", "botonVerLista", () -> pestanas.setSelectedIndex(1)));
        botones.add(
                boton(
                        "Abrir detalle",
                        "botonAbrirDetalle",
                        () -> crearDetalle(this).setVisible(true)));
        botones.add(
                boton(
                        "Volver",
                        "botonVolverComponentes",
                        () -> Navegacion.cambiar(this, new Portada(contexto))));
        JPanel contenido = new JPanel(new BorderLayout(8, 8));
        contenido.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        contenido.add(pestanas, BorderLayout.CENTER);
        contenido.add(resultado, BorderLayout.NORTH);
        contenido.add(botones, BorderLayout.SOUTH);
        setContentPane(contenido);
        pack();
        setLocationRelativeTo(null);
    }

    static JDialog crearDetalle(Window padre) {
        JDialog dialogo = dialogo(padre, "Detalle de práctica", "dialogoDetalle");
        dialogo.add(new JLabel("Detalle de la persona seleccionada."), BorderLayout.CENTER);
        JPanel botones = new JPanel();
        botones.add(
                boton(
                        "Abrir confirmación",
                        "botonAbrirConfirmacion",
                        () -> crearConfirmacion(dialogo).setVisible(true)));
        botones.add(boton("Cerrar detalle", "botonCerrarDetalle", dialogo::dispose));
        dialogo.add(botones, BorderLayout.SOUTH);
        dialogo.pack();
        dialogo.setLocationRelativeTo(padre);
        return dialogo;
    }

    static JDialog crearConfirmacion(Window padre) {
        JDialog dialogo = dialogo(padre, "Confirmación de práctica", "dialogoConfirmacion");
        dialogo.add(new JLabel("Confirmación anidada, sin modificar datos."), BorderLayout.CENTER);
        dialogo.add(
                boton("Aceptar confirmación", "botonAceptarConfirmacion", dialogo::dispose),
                BorderLayout.SOUTH);
        dialogo.pack();
        dialogo.setLocationRelativeTo(padre);
        return dialogo;
    }

    private static JDialog dialogo(Window padre, String titulo, String nombre) {
        JDialog dialogo = new JDialog(padre, titulo, Dialog.ModalityType.DOCUMENT_MODAL);
        dialogo.setName(nombre);
        dialogo.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        return dialogo;
    }

    private static JButton boton(String texto, String nombre, Runnable accion) {
        JButton boton = new JButton(texto);
        boton.setName(nombre);
        boton.addActionListener(e -> accion.run());
        return boton;
    }
}
