package io.github.physanto.opsecengine.agente.swing;

import io.github.physanto.opsecengine.agente.api.EmisorEventos;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPasswordField;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JRootPane;
import javax.swing.JScrollBar;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/**
 * Grabadora: observa lo que hace el usuario en la aplicación y emite un evento {@code
 * ACCION_GRABADA} por cada acción, sin alterar el comportamiento de la aplicación.
 *
 * <p>Escucha los eventos de ratón, teclado y foco de AWT y los traduce a las acciones de los pasos:
 *
 * <ul>
 *   <li>{@code PULSAR}: clic con el botón izquierdo (o espacio) sobre un botón o un elemento de
 *       menú, y Enter en un campo de una línea cuando la ventana tiene un botón por defecto.
 *   <li>{@code MARCAR}: lo mismo sobre un botón de alternancia, casilla o botón de opción; el valor
 *       es el estado en que quedó.
 *   <li>{@code LLENAR}: texto escrito o pegado en un campo; se emite una sola vez, con el texto
 *       completo, al perder el foco, al pulsar Enter o antes de cualquier otra acción. Si el campo
 *       es de contraseña, el evento lleva {@code sensible=true}.
 *   <li>{@code SELECCIONAR}: opción elegida en un combo o en una lista; solo si cambió.
 * </ul>
 *
 * <p>Las piezas internas de otros controles (flechas de un combo o de un spinner, barras de
 * desplazamiento, pestañas) no se graban como pulsaciones. Las pestañas, las tablas y los árboles
 * no se graban todavía. Cada evento lleva el localizador verificado del componente (ver {@link
 * LocalizadoresComponente}).
 *
 * <p>Todos sus métodos deben llamarse desde el EDT; no es seguro entre hilos.
 */
final class Grabadora {

    private static final long MASCARA =
            AWTEvent.MOUSE_EVENT_MASK | AWTEvent.KEY_EVENT_MASK | AWTEvent.FOCUS_EVENT_MASK;

    private final EmisorEventos emisor;
    private final Consumer<String> errores;

    /** Texto que tenía cada campo editado la última vez que se grabó o ganó el foco. */
    private final Map<JTextComponent, String> bases = new LinkedHashMap<>();

    /** Campos en los que se pulsó alguna tecla desde la última vez que se grabaron. */
    private final Set<JTextComponent> editados = new HashSet<>();

    /** Última opción conocida de cada combo o lista, para grabar solo los cambios. */
    private final Map<Component, String> opciones = new WeakHashMap<>();

    private final AWTEventListener oyente = this::alEvento;
    private boolean activa;

    /**
     * Crea la grabadora, todavía inactiva.
     *
     * @param emisor por donde salen los eventos {@code ACCION_GRABADA}
     * @param errores recibe los problemas que no impiden seguir
     */
    Grabadora(EmisorEventos emisor, Consumer<String> errores) {
        this.emisor = emisor;
        this.errores = errores;
    }

    /** Empieza a grabar. No hace nada si ya está grabando. */
    void iniciar() {
        if (activa) {
            return;
        }
        activa = true;
        Toolkit.getDefaultToolkit().addAWTEventListener(oyente, MASCARA);
        Component foco = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (foco instanceof JTextComponent) {
            bases.put((JTextComponent) foco, valorDe((JTextComponent) foco));
        }
    }

    /** Termina de grabar; antes emite el texto que quedó pendiente en algún campo. */
    void detener() {
        if (!activa) {
            return;
        }
        Toolkit.getDefaultToolkit().removeAWTEventListener(oyente);
        try {
            volcarTextos();
        } finally {
            activa = false;
            bases.clear();
            editados.clear();
            opciones.clear();
        }
    }

    /**
     * Indica si está grabando.
     *
     * @return true entre {@link #iniciar()} y {@link #detener()}
     */
    boolean activa() {
        return activa;
    }

    private void alEvento(AWTEvent evento) {
        if (!activa || !(evento.getSource() instanceof Component)) {
            return;
        }
        Component origen = (Component) evento.getSource();
        if (origen instanceof Window) {
            // El evento original llega a la ventana; el que importa es el que se reenvía al
            // componente ligero.
            return;
        }
        try {
            if (evento instanceof FocusEvent) {
                enFoco((FocusEvent) evento, origen);
            } else if (evento instanceof MouseEvent) {
                enRaton((MouseEvent) evento, origen);
            } else if (evento instanceof KeyEvent) {
                enTecla((KeyEvent) evento, origen);
            }
        } catch (RuntimeException e) {
            errores.accept("La grabadora no pudo procesar un evento: " + e);
        }
    }

    private void enFoco(FocusEvent evento, Component origen) {
        if (!(origen instanceof JTextComponent)) {
            return;
        }
        JTextComponent campo = (JTextComponent) origen;
        if (evento.getID() == FocusEvent.FOCUS_GAINED) {
            // Un aviso de foco tardío no debe borrar lo que el usuario ya empezó a escribir.
            if (editados.contains(campo)) {
                bases.putIfAbsent(campo, valorDe(campo));
            } else {
                bases.put(campo, valorDe(campo));
            }
        } else if (evento.getID() == FocusEvent.FOCUS_LOST) {
            volcar(campo);
        }
    }

    private void enRaton(MouseEvent evento, Component origen) {
        boolean suelta = evento.getID() == MouseEvent.MOUSE_RELEASED;
        if ((!suelta && evento.getID() != MouseEvent.MOUSE_PRESSED)
                || !SwingUtilities.isLeftMouseButton(evento)) {
            return;
        }
        Component control = controlDeOpciones(origen);
        if (control != null) {
            // La opción puede cambiar al presionar (listas) o al soltar (lista de un combo).
            volcarTextos();
            revisarOpcion(control);
            return;
        }
        if (!suelta) {
            return;
        }
        AbstractButton boton = botonDe(origen);
        if (boton != null && activadoPorElRaton(boton, evento)) {
            volcarTextos();
            grabarActivacion(boton);
        }
    }

    private void enTecla(KeyEvent evento, Component origen) {
        if (origen instanceof JTextComponent) {
            enTeclaEnCampo(evento, (JTextComponent) origen);
            return;
        }
        if (evento.getID() == KeyEvent.KEY_PRESSED) {
            Component control = controlDeOpciones(origen);
            if (control != null && evento.getKeyCode() != KeyEvent.VK_TAB) {
                revisarOpcion(control);
            }
        } else if (evento.getID() == KeyEvent.KEY_RELEASED
                && evento.getKeyCode() == KeyEvent.VK_SPACE
                && origen instanceof AbstractButton
                && !esPiezaInterna(origen)) {
            AbstractButton boton = (AbstractButton) origen;
            if (boton.isEnabled() && boton.getModel().isArmed() && boton.getModel().isPressed()) {
                volcarTextos();
                grabarActivacion(boton);
            }
        }
    }

    private void enTeclaEnCampo(KeyEvent evento, JTextComponent campo) {
        if (evento.getID() == KeyEvent.KEY_PRESSED || evento.getID() == KeyEvent.KEY_TYPED) {
            // El evento llega antes de que el campo lo procese: este es el texto previo.
            bases.putIfAbsent(campo, valorDe(campo));
            editados.add(campo);
        }
        if (evento.getID() == KeyEvent.KEY_PRESSED
                && evento.getKeyCode() == KeyEvent.VK_ENTER
                && campo instanceof JTextField) {
            volcarTextos();
            JRootPane raiz = SwingUtilities.getRootPane(campo);
            JButton porDefecto = raiz == null ? null : raiz.getDefaultButton();
            if (porDefecto != null && porDefecto.isEnabled() && porDefecto.isShowing()) {
                emitir("PULSAR", porDefecto, null, false);
            }
        }
    }

    /** Botón que recibió el clic, sin contar las piezas internas de otros controles. */
    private static AbstractButton botonDe(Component origen) {
        Component actual = origen;
        while (actual != null && !(actual instanceof Window)) {
            if (actual instanceof AbstractButton) {
                return esPiezaInterna(actual) ? null : (AbstractButton) actual;
            }
            actual = actual.getParent();
        }
        return null;
    }

    private static boolean esPiezaInterna(Component componente) {
        return SwingUtilities.getAncestorOfClass(JSpinner.class, componente) != null
                || SwingUtilities.getAncestorOfClass(JScrollBar.class, componente) != null
                || SwingUtilities.getAncestorOfClass(JTabbedPane.class, componente) != null
                || SwingUtilities.getAncestorOfClass(JComboBox.class, componente) != null;
    }

    /** Combo o lista sobre la que se hizo el gesto, incluida la lista desplegable de un combo. */
    private static Component controlDeOpciones(Component origen) {
        JPopupMenu emergente =
                origen instanceof JPopupMenu
                        ? (JPopupMenu) origen
                        : (JPopupMenu) SwingUtilities.getAncestorOfClass(JPopupMenu.class, origen);
        if (emergente != null && emergente.getInvoker() instanceof JComboBox) {
            return emergente.getInvoker();
        }
        Component combo =
                origen instanceof JComboBox
                        ? origen
                        : SwingUtilities.getAncestorOfClass(JComboBox.class, origen);
        if (combo != null) {
            return combo;
        }
        return origen instanceof JList ? origen : null;
    }

    /** Indica si el botón se va a activar con este soltar del ratón. */
    private static boolean activadoPorElRaton(AbstractButton boton, MouseEvent evento) {
        if (!boton.isEnabled()) {
            return false;
        }
        if (boton instanceof JMenuItem) {
            return true;
        }
        return boton.getModel().isArmed() && boton.getModel().isPressed();
    }

    private void grabarActivacion(AbstractButton boton) {
        if (esDeAlternancia(boton)) {
            // El estado cambia al procesar el evento; se lee cuando ya terminó.
            SwingUtilities.invokeLater(() -> {
                if (activa) {
                    emitirSeguro("MARCAR", boton, String.valueOf(boton.isSelected()));
                }
            });
        } else {
            emitir("PULSAR", boton, null, false);
        }
    }

    private static boolean esDeAlternancia(AbstractButton boton) {
        return boton instanceof JToggleButton
                || boton instanceof JCheckBoxMenuItem
                || boton instanceof JRadioButtonMenuItem;
    }

    /**
     * Graba la opción elegida después de que el control procese el evento, si cambió. El evento
     * todavía no se procesó: la primera vez que se ve un control, su opción actual es la previa.
     */
    private void revisarOpcion(Component control) {
        opciones.computeIfAbsent(control, c -> textoDe(opcionElegida(c)));
        SwingUtilities.invokeLater(() -> {
            if (!activa) {
                return;
            }
            String nueva = opcionElegida(control);
            if (nueva != null && !nueva.equals(opciones.get(control))) {
                opciones.put(control, nueva);
                emitirSeguro("SELECCIONAR", control, nueva);
            }
        });
    }

    private static String textoDe(String opcion) {
        return opcion == null ? "" : opcion;
    }

    private static String opcionElegida(Component control) {
        Object elegida = null;
        if (control instanceof JComboBox) {
            elegida = ((JComboBox<?>) control).getSelectedItem();
        } else if (control instanceof JList) {
            elegida = ((JList<?>) control).getSelectedValue();
        }
        return elegida == null ? null : elegida.toString();
    }

    private void volcarTextos() {
        for (JTextComponent campo : new ArrayList<>(bases.keySet())) {
            volcar(campo);
        }
    }

    /** Emite {@code LLENAR} si el texto del campo cambió desde la última vez. */
    private void volcar(JTextComponent campo) {
        String actual = valorDe(campo);
        editados.remove(campo);
        String base = bases.put(campo, actual);
        if (base != null && !base.equals(actual)) {
            emitir("LLENAR", campo, actual, campo instanceof JPasswordField);
        }
    }

    private static String valorDe(JTextComponent campo) {
        if (campo instanceof JPasswordField) {
            return new String(((JPasswordField) campo).getPassword());
        }
        return campo.getText();
    }

    private void emitirSeguro(String accion, Component componente, String valor) {
        try {
            emitir(accion, componente, valor, false);
        } catch (RuntimeException e) {
            errores.accept("La grabadora no pudo informar una acción: " + e);
        }
    }

    private void emitir(String accion, Component componente, String valor, boolean sensible) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("accion", accion);
        datos.putAll(LocalizadoresComponente.datos(componente));
        if (valor != null) {
            datos.put("valor", valor);
        }
        if (sensible) {
            datos.put("sensible", true);
        }
        emisor.emitir("ACCION_GRABADA", datos);
    }
}
