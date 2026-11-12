package io.github.physanto.opsecengine.agente.swing;

import io.github.physanto.opsecengine.agente.api.EmisorEventos;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

/**
 * Observa las ventanas de la aplicación probada y emite {@code VENTANA_ABIERTA}, {@code
 * VENTANA_CERRADA} y, para los diálogos con un {@code JOptionPane}, {@code DIALOGO}.
 *
 * <p>Se registra con {@code Toolkit.addAWTEventListener}, así que no modifica la aplicación. Cada
 * ventana recibe un identificador estable durante la sesión, por ejemplo {@code InicioSesion-3}.
 *
 * <p>Lleva el estado de cada ventana (abierta, oculta o liberada) y emite un evento solo cuando ese
 * estado cambia:
 *
 * <ul>
 *   <li>{@code VENTANA_ABIERTA} cuando la ventana se muestra ({@code WINDOW_OPENED} o {@code
 *       COMPONENT_SHOWN}), también cuando se vuelve a mostrar una ventana oculta o liberada; los
 *       diálogos con {@code JOptionPane} emiten además {@code DIALOGO}.
 *   <li>{@code VENTANA_CERRADA} con {@code liberada=false} cuando una ventana abierta se oculta con
 *       {@code setVisible(false)} y conserva sus recursos nativos.
 *   <li>{@code VENTANA_CERRADA} con {@code liberada=true} cuando una ventana abierta u oculta se
 *       destruye con {@code dispose} ({@code isDisplayable()} es false). Una ventana que se oculta
 *       y después se destruye emite dos {@code VENTANA_CERRADA}; el último de cada {@code
 *       ventanaId} dice si quedó liberada. El estado se mira al atender el evento: una ventana que
 *       se oculta y se destruye en la misma tarea del EDT emite un solo {@code VENTANA_CERRADA},
 *       con {@code liberada=true}.
 * </ul>
 *
 * <p>Recibe los eventos en el EDT. Nunca lanza excepciones hacia la aplicación: los errores se
 * informan al registro del agente.
 */
public final class ObservadorVentanas implements AWTEventListener {

    private final EmisorEventos emisor;
    private final Consumer<String> registro;
    private final Map<Window, String> identificadores = new WeakHashMap<>();
    private final Map<Window, Estado> estados = new WeakHashMap<>();
    private int contador;

    /**
     * Crea el observador sin registrarlo.
     *
     * @param emisor emisor de los eventos; no puede ser null
     * @param registro destino de los mensajes de error del agente; no puede ser null
     */
    public ObservadorVentanas(EmisorEventos emisor, Consumer<String> registro) {
        this.emisor = emisor;
        this.registro = registro;
    }

    /**
     * Registra el observador en el toolkit de AWT.
     *
     * @param emisor emisor de los eventos; no puede ser null
     * @param registro destino de los mensajes de error del agente; no puede ser null
     * @return el observador registrado
     */
    public static ObservadorVentanas instalar(EmisorEventos emisor, Consumer<String> registro) {
        ObservadorVentanas observador = new ObservadorVentanas(emisor, registro);
        Toolkit.getDefaultToolkit()
                .addAWTEventListener(
                        observador, AWTEvent.WINDOW_EVENT_MASK | AWTEvent.COMPONENT_EVENT_MASK);
        return observador;
    }

    /**
     * Atiende un evento de AWT; solo le interesan las ventanas que se abren, se muestran, se
     * ocultan o se cierran.
     *
     * @param evento evento de AWT
     */
    @Override
    public void eventDispatched(AWTEvent evento) {
        try {
            int id = evento.getID();
            Object origen = evento.getSource();
            if (!(origen instanceof Window)) {
                return;
            }
            Window ventana = (Window) origen;
            if (id == WindowEvent.WINDOW_OPENED || id == ComponentEvent.COMPONENT_SHOWN) {
                abierta(ventana);
            } else if (id == ComponentEvent.COMPONENT_HIDDEN || id == WindowEvent.WINDOW_CLOSED) {
                cerrada(ventana);
            }
        } catch (RuntimeException e) {
            registro.accept("No se pudo observar una ventana: " + e);
        }
    }

    private void abierta(Window ventana) {
        if (!cambiarEstado(ventana, Estado.ABIERTA)) {
            return;
        }
        String id = identificador(ventana);
        Map<String, Object> datos = datosVentana(ventana, id);
        if (ventana instanceof Dialog) {
            datos.put("modal", ((Dialog) ventana).isModal());
        }
        emisor.emitir("VENTANA_ABIERTA", datos);
        JOptionPane panel = buscarPanel(ventana);
        if (panel != null) {
            emisor.emitir("DIALOGO", datosDialogo(ventana, panel, id));
        }
    }

    /**
     * Informa una ventana que se ocultó o se destruyó. Al atender el evento la destrucción ya
     * terminó, así que {@code isDisplayable()} dice si quedó liberada.
     */
    private void cerrada(Window ventana) {
        boolean liberada = !ventana.isDisplayable();
        Estado anterior = estado(ventana);
        if (anterior == null
                || anterior == Estado.LIBERADA
                || (anterior == Estado.OCULTA && !liberada)) {
            return;
        }
        cambiarEstado(ventana, liberada ? Estado.LIBERADA : Estado.OCULTA);
        Map<String, Object> datos = datosVentana(ventana, identificador(ventana));
        datos.put("liberada", liberada);
        emisor.emitir("VENTANA_CERRADA", datos);
    }

    private synchronized Estado estado(Window ventana) {
        return estados.get(ventana);
    }

    /** Cambia el estado de la ventana; devuelve false si ya estaba en ese estado. */
    private synchronized boolean cambiarEstado(Window ventana, Estado nuevo) {
        return estados.put(ventana, nuevo) != nuevo;
    }

    private synchronized String identificador(Window ventana) {
        String id = identificadores.get(ventana);
        if (id == null) {
            String nombre = ventana.getClass().getSimpleName();
            id = (nombre.isEmpty() ? "Ventana" : nombre) + "-" + (++contador);
            identificadores.put(ventana, id);
        }
        return id;
    }

    private static Map<String, Object> datosVentana(Window ventana, String id) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("ventanaId", id);
        datos.put("clase", ventana.getClass().getName());
        datos.put("titulo", DescriptorComponentes.texto(ventana));
        return datos;
    }

    /** Último estado informado de una ventana. */
    private enum Estado {
        ABIERTA,
        OCULTA,
        LIBERADA
    }

    private static Map<String, Object> datosDialogo(Window ventana, JOptionPane panel, String id) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("texto", textoMensaje(panel.getMessage()));
        datos.put("titulo", DescriptorComponentes.texto(ventana));
        datos.put("tipoMensaje", tipoMensaje(panel.getMessageType()));
        List<String> opciones = new ArrayList<>();
        juntarBotones(panel, opciones);
        datos.put("opciones", opciones);
        datos.put("ventanaId", id);
        return datos;
    }

    private static String textoMensaje(Object mensaje) {
        if (mensaje == null) {
            return "";
        }
        if (mensaje instanceof Object[]) {
            StringBuilder texto = new StringBuilder();
            for (Object parte : (Object[]) mensaje) {
                String linea = textoMensaje(parte);
                if (!linea.isEmpty()) {
                    texto.append(texto.length() > 0 ? "\n" : "").append(linea);
                }
            }
            return texto.toString();
        }
        if (mensaje instanceof Component) {
            List<String> textos = new ArrayList<>();
            juntarEtiquetas((Component) mensaje, textos);
            return String.join("\n", textos);
        }
        return String.valueOf(mensaje);
    }

    private static String tipoMensaje(int tipo) {
        switch (tipo) {
            case JOptionPane.ERROR_MESSAGE:
                return "ERROR";
            case JOptionPane.INFORMATION_MESSAGE:
                return "INFORMACION";
            case JOptionPane.WARNING_MESSAGE:
                return "ADVERTENCIA";
            case JOptionPane.QUESTION_MESSAGE:
                return "PREGUNTA";
            default:
                return "PLANO";
        }
    }

    private static JOptionPane buscarPanel(Component componente) {
        if (componente instanceof JOptionPane) {
            return (JOptionPane) componente;
        }
        if (componente instanceof Container) {
            for (Component hijo : ((Container) componente).getComponents()) {
                JOptionPane panel = buscarPanel(hijo);
                if (panel != null) {
                    return panel;
                }
            }
        }
        return null;
    }

    private static void juntarBotones(Component componente, List<String> textos) {
        if (componente instanceof AbstractButton) {
            String texto = ((AbstractButton) componente).getText();
            if (texto != null && !texto.isEmpty()) {
                textos.add(texto);
            }
        }
        if (componente instanceof Container) {
            for (Component hijo : ((Container) componente).getComponents()) {
                juntarBotones(hijo, textos);
            }
        }
    }

    private static void juntarEtiquetas(Component componente, List<String> textos) {
        if (componente instanceof JLabel) {
            String texto = ((JLabel) componente).getText();
            if (texto != null && !texto.isEmpty()) {
                textos.add(texto);
            }
        }
        if (componente instanceof Container) {
            for (Component hijo : ((Container) componente).getComponents()) {
                juntarEtiquetas(hijo, textos);
            }
        }
    }
}
