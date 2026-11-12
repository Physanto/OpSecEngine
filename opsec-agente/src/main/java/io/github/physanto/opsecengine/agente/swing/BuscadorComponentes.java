package io.github.physanto.opsecengine.agente.swing;

import io.github.physanto.opsecengine.agente.api.ResultadoBusqueda;
import io.github.physanto.opsecengine.protocolo.dto.CriterioDto;
import io.github.physanto.opsecengine.protocolo.dto.LocalizadorDto;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.Window;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/**
 * Busca componentes en las ventanas visibles con los criterios de un localizador.
 *
 * <p>Prueba los criterios en el orden del localizador. Gana el primer criterio que encuentra un
 * componente alcanzable: visible, habilitado y no tapado por un diálogo modal de otra ventana. Si
 * ningún criterio encuentra uno alcanzable pero alguno encontró un componente, el resultado es
 * {@code NO_ALCANZABLE}; si ninguno encontró nada, {@code NO_ENCONTRADO}.
 *
 * <p>Estrategias soportadas, con comparación exacta del texto sin espacios al inicio ni al final;
 * si no hay coincidencias, TEXTO, ETIQUETA, NOMBRE_ACCESIBLE y TITULO_VENTANA ignoran tildes,
 * mayúsculas y espacios repetidos. Las coincidencias flexibles múltiples son ambiguas:
 *
 * <ul>
 *   <li>{@code ETIQUETA}: un {@code JLabel} con ese texto y un componente asociado con {@code
 *       setLabelFor}; si no hay asociación explícita, el control editable hermano más cercano a la
 *       derecha en la misma fila o, en su defecto, debajo en la misma columna. Un empate se rechaza
 *       como ambiguo.
 *   <li>{@code TEXTO}: botones y etiquetas con ese texto (no el contenido de los campos).
 *   <li>{@code NOMBRE_COMPONENTE}: el nombre puesto con {@code setName}.
 *   <li>{@code NOMBRE_ACCESIBLE}: el nombre accesible.
 *   <li>{@code TIPO}: el nombre completo o simple de la clase del componente.
 *   <li>{@code TITULO_VENTANA} y {@code CLASE_VENTANA}: una ventana visible.
 * </ul>
 *
 * <p>{@code RUTA} usa {@code Ventana/Clase[indice]/Clase[indice]/...}: el primer segmento coincide
 * con el título, nombre o clase simple de la ventana; cada segmento posterior nombra un hijo
 * directo por su clase simple. El índice opcional empieza en cero entre hermanos de esa clase. Sin
 * índice se consideran todos y se rechazan resultados múltiples como ambiguos. La ruta incluye los
 * contenedores internos de Swing. Las estrategias desconocidas no encuentran nada.
 *
 * <p>Si se pide atravesar pestañas (lo usa {@code IR_A}), un componente que solo está oculto porque
 * su pestaña no es la elegida cuenta como alcanzable, y {@code TEXTO} también encuentra el
 * contenido de una pestaña por su título. Se debe llamar desde el EDT.
 */
final class BuscadorComponentes {

    private BuscadorComponentes() {}

    /** Resultado de una búsqueda: el componente encontrado, si hay, y su descripción. */
    static final class Busqueda {

        private final Component componente;
        private final ResultadoBusqueda resultado;

        Busqueda(Component componente, ResultadoBusqueda resultado) {
            this.componente = componente;
            this.resultado = resultado;
        }

        /** Componente encontrado, alcanzable o no; null si no se encontró. */
        Component componente() {
            return componente;
        }

        /** Resultado de la búsqueda con su código. */
        ResultadoBusqueda resultado() {
            return resultado;
        }
    }

    /**
     * Busca el componente del localizador en las ventanas visibles.
     *
     * @param localizador localizador del componente
     * @return el resultado de la búsqueda; nunca null
     */
    static Busqueda buscar(LocalizadorDto localizador) {
        return buscar(localizador, false);
    }

    /**
     * Busca el componente del localizador en las ventanas visibles.
     *
     * @param localizador localizador del componente
     * @param atravesarPestanas si un componente de una pestaña no elegida cuenta como alcanzable y
     *     {@code TEXTO} encuentra pestañas por su título
     * @return el resultado de la búsqueda; nunca null
     */
    static Busqueda buscar(LocalizadorDto localizador, boolean atravesarPestanas) {
        List<Window> ventanas = ventanasVisibles();
        Set<Window> modales = EjecutorEdt.modalesVisibles();
        Busqueda primeraNoAlcanzable = null;
        List<CriterioDto> criterios = localizador.getCriterios();
        for (int i = 0; i < criterios.size(); i++) {
            CriterioDto criterio = criterios.get(i);
            List<Component> coincidencias =
                    candidatos(criterio, ventanas, atravesarPestanas, false);
            boolean flexible = false;
            if (coincidencias.isEmpty() && admiteFlexible(criterio.getEstrategia())) {
                coincidencias = candidatos(criterio, ventanas, atravesarPestanas, true);
                flexible = true;
            }
            if (coincidencias.size() > 1
                    && (flexible
                            || "ETIQUETA".equals(criterio.getEstrategia())
                            || "RUTA".equals(criterio.getEstrategia()))) {
                return new Busqueda(
                        null,
                        ResultadoBusqueda.noEncontrado(
                                "Localizador ambiguo: " + describir(localizador)));
            }
            for (Component candidato : coincidencias) {
                String motivo = motivoNoAlcanzable(candidato, modales, atravesarPestanas);
                if (motivo == null) {
                    return new Busqueda(
                            candidato,
                            ResultadoBusqueda.encontrado(
                                    DescriptorComponentes.describir(candidato, false), i));
                }
                if (primeraNoAlcanzable == null) {
                    primeraNoAlcanzable =
                            new Busqueda(
                                    candidato,
                                    ResultadoBusqueda.noAlcanzable(
                                            DescriptorComponentes.describir(candidato, false),
                                            i,
                                            motivo));
                }
            }
        }
        if (primeraNoAlcanzable != null) {
            return primeraNoAlcanzable;
        }
        return new Busqueda(
                null,
                ResultadoBusqueda.noEncontrado(
                        "Ningún componente visible cumple " + describir(localizador)));
    }

    /**
     * Escribe un localizador como texto, por ejemplo {@code etiqueta=Usuario, texto=Usuario}.
     *
     * @param localizador localizador
     * @return el texto
     */
    static String describir(LocalizadorDto localizador) {
        StringBuilder texto = new StringBuilder();
        for (CriterioDto criterio : localizador.getCriterios()) {
            if (texto.length() > 0) {
                texto.append(", ");
            }
            texto.append(criterio.getEstrategia()).append('=').append(criterio.getValor());
        }
        return texto.toString();
    }

    /** Ventanas visibles en orden de creación. */
    static List<Window> ventanasVisibles() {
        List<Window> visibles = new ArrayList<>();
        for (Window ventana : Window.getWindows()) {
            if (ventana.isShowing()) {
                visibles.add(ventana);
            }
        }
        return visibles;
    }

    private static List<Component> candidatos(
            CriterioDto criterio,
            List<Window> ventanas,
            boolean atravesarPestanas,
            boolean flexible) {
        String valor = criterio.getValor().trim();
        List<Component> encontrados = new ArrayList<>();
        switch (criterio.getEstrategia()) {
            case "RUTA":
                return porRuta(valor, ventanas);
            case "ETIQUETA":
                return porEtiqueta(valor, ventanas, flexible);
            case "TITULO_VENTANA":
                for (Window ventana : ventanas) {
                    if (iguales(valor, DescriptorComponentes.texto(ventana), flexible)) {
                        encontrados.add(ventana);
                    }
                }
                return encontrados;
            case "CLASE_VENTANA":
                for (Window ventana : ventanas) {
                    if (esDeClase(ventana, valor)) {
                        encontrados.add(ventana);
                    }
                }
                return encontrados;
            default:
                for (Window ventana : ventanas) {
                    recorrer(
                            ventana,
                            criterio.getEstrategia(),
                            valor,
                            atravesarPestanas,
                            flexible,
                            encontrados);
                }
                return encontrados;
        }
    }

    private static void recorrer(
            Component componente,
            String estrategia,
            String valor,
            boolean atravesarPestanas,
            boolean flexible,
            List<Component> encontrados) {
        Component coincidencia = coincide(componente, estrategia, valor, flexible);
        if (coincidencia != null) {
            encontrados.add(coincidencia);
        }
        if (atravesarPestanas && "TEXTO".equals(estrategia) && componente instanceof JTabbedPane) {
            JTabbedPane pestanas = (JTabbedPane) componente;
            for (int i = 0; i < pestanas.getTabCount(); i++) {
                Component contenido = pestanas.getComponentAt(i);
                if (contenido != null && iguales(valor, pestanas.getTitleAt(i), flexible)) {
                    encontrados.add(contenido);
                }
            }
        }
        if (componente instanceof Container) {
            for (Component hijo : ((Container) componente).getComponents()) {
                recorrer(hijo, estrategia, valor, atravesarPestanas, flexible, encontrados);
            }
        }
    }

    /** Devuelve el componente que corresponde si el criterio coincide con este, o null. */
    private static Component coincide(
            Component componente, String estrategia, String valor, boolean flexible) {
        switch (estrategia) {
            case "TEXTO":
                if (componente instanceof Window || componente instanceof JTextComponent) {
                    return null;
                }
                return iguales(valor, DescriptorComponentes.texto(componente), flexible)
                        ? componente
                        : null;
            case "NOMBRE_COMPONENTE":
                return valor.equals(componente.getName()) ? componente : null;
            case "NOMBRE_ACCESIBLE":
                return iguales(valor, DescriptorComponentes.nombreAccesible(componente), flexible)
                        ? componente
                        : null;
            case "TIPO":
                return esDeClase(componente, valor) ? componente : null;
            default:
                return null;
        }
    }

    /** Motivo por el que el usuario no podría usar el componente, o null si puede. */
    private static String motivoNoAlcanzable(
            Component componente, Set<Window> modales, boolean atravesarPestanas) {
        if (!componente.isShowing() && !(atravesarPestanas && ocultoSoloPorPestanas(componente))) {
            return "El componente no está visible";
        }
        if (!componente.isEnabled()) {
            return "El componente está deshabilitado";
        }
        Window ventana =
                componente instanceof Window
                        ? (Window) componente
                        : SwingUtilities.getWindowAncestor(componente);
        for (Window modal : modales) {
            if (modal != ventana && !esDuena(ventana, modal)) {
                return "Un diálogo modal tapa la ventana del componente: "
                        + DescriptorComponentes.texto(modal);
            }
        }
        return null;
    }

    /**
     * Indica si el componente se vería con solo elegir las pestañas que lo contienen: todos sus
     * contenedores son visibles, salvo el contenido de pestañas habilitadas no elegidas, y su
     * ventana se muestra.
     */
    private static boolean ocultoSoloPorPestanas(Component componente) {
        Component actual = componente;
        while (actual != null && !(actual instanceof Window)) {
            Container padre = actual.getParent();
            if (padre instanceof JTabbedPane) {
                int indice = ((JTabbedPane) padre).indexOfComponent(actual);
                if (indice < 0 || !((JTabbedPane) padre).isEnabledAt(indice)) {
                    return false;
                }
            } else if (!actual.isVisible()) {
                return false;
            }
            actual = padre;
        }
        return actual != null && actual.isShowing();
    }

    /** Indica si {@code modal} es dueña, directa o indirecta, de {@code ventana}. */
    private static boolean esDuena(Window ventana, Window modal) {
        for (Window actual = ventana; actual != null; actual = actual.getOwner()) {
            if (actual == modal) {
                return true;
            }
        }
        return false;
    }

    private static boolean esDeClase(Component componente, String valor) {
        Class<?> clase = componente.getClass();
        return valor.equals(clase.getName()) || valor.equals(clase.getSimpleName());
    }

    private static boolean admiteFlexible(String estrategia) {
        return "TEXTO".equals(estrategia)
                || "ETIQUETA".equals(estrategia)
                || "NOMBRE_ACCESIBLE".equals(estrategia)
                || "TITULO_VENTANA".equals(estrategia);
    }

    private static boolean iguales(String valor, String texto, boolean flexible) {
        return texto != null
                && (flexible
                        ? normalizar(valor).equals(normalizar(texto))
                        : valor.equals(texto.trim()));
    }

    private static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("(?U)\\s+", " ")
                .trim();
    }

    private static List<Component> porEtiqueta(
            String valor, List<Window> ventanas, boolean flexible) {
        List<Component> etiquetas = new ArrayList<>();
        List<Component> asociados = new ArrayList<>();
        for (Window ventana : ventanas) {
            recorrer(ventana, "TEXTO", valor, false, flexible, etiquetas);
        }
        for (Component componente : etiquetas) {
            if (componente instanceof JLabel && ((JLabel) componente).getLabelFor() != null) {
                agregarUnico(asociados, ((JLabel) componente).getLabelFor());
            }
        }
        if (!asociados.isEmpty()) {
            return asociados;
        }
        for (Component componente : etiquetas) {
            if (componente instanceof JLabel && componente.getParent() != null) {
                for (Component cercano : cercanos((JLabel) componente)) {
                    agregarUnico(asociados, cercano);
                }
            }
        }
        return asociados;
    }

    private static void agregarUnico(List<Component> componentes, Component componente) {
        if (!componentes.contains(componente)) {
            componentes.add(componente);
        }
    }

    private static List<Component> cercanos(JLabel etiqueta) {
        List<Component> resultado = new ArrayList<>();
        Window ventana = SwingUtilities.getWindowAncestor(etiqueta);
        Rectangle origen =
                SwingUtilities.convertRectangle(
                        etiqueta.getParent(), etiqueta.getBounds(), ventana);
        for (int direccion = 0; direccion < 2; direccion++) {
            double minima = Double.POSITIVE_INFINITY;
            for (Component hermano : etiqueta.getParent().getComponents()) {
                if (!editable(hermano)) {
                    continue;
                }
                Rectangle destino =
                        SwingUtilities.convertRectangle(
                                hermano.getParent(), hermano.getBounds(), ventana);
                boolean alineado =
                        direccion == 0
                                ? destino.x >= origen.x + origen.width
                                        && destino.getCenterY() >= origen.y
                                        && destino.getCenterY() <= origen.y + origen.height
                                : destino.y >= origen.y + origen.height
                                        && destino.getCenterX() >= origen.x
                                        && destino.getCenterX() <= origen.x + origen.width;
                if (!alineado) {
                    continue;
                }
                double dx =
                        Math.max(
                                0,
                                Math.max(
                                        origen.x - destino.getMaxX(),
                                        destino.x - origen.getMaxX()));
                double dy =
                        Math.max(
                                0,
                                Math.max(
                                        origen.y - destino.getMaxY(),
                                        destino.y - origen.getMaxY()));
                double distancia = dx * dx + dy * dy;
                if (distancia < minima) {
                    resultado.clear();
                    minima = distancia;
                }
                if (distancia == minima) {
                    resultado.add(hermano);
                }
            }
            if (!resultado.isEmpty()) {
                break;
            }
        }
        return resultado;
    }

    private static boolean editable(Component componente) {
        return (componente instanceof JTextComponent && ((JTextComponent) componente).isEditable())
                || componente instanceof JComboBox
                || componente instanceof JSpinner
                || componente instanceof JToggleButton;
    }

    private static List<Component> porRuta(String valor, List<Window> ventanas) {
        String[] segmentos = valor.split("/", -1);
        List<Component> actuales = new ArrayList<>();
        for (Window ventana : ventanas) {
            if (segmentos[0].equals(DescriptorComponentes.texto(ventana))
                    || segmentos[0].equals(ventana.getName())
                    || segmentos[0].equals(ventana.getClass().getSimpleName())) {
                actuales.add(ventana);
            }
        }
        for (int i = 1; i < segmentos.length; i++) {
            String segmento = segmentos[i];
            if (!segmento.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\[[0-9]+\\])?")) {
                return new ArrayList<>();
            }
            int apertura = segmento.indexOf('[');
            String clase = apertura < 0 ? segmento : segmento.substring(0, apertura);
            int indice = -1;
            if (apertura >= 0) {
                try {
                    indice =
                            Integer.parseInt(
                                    segmento.substring(apertura + 1, segmento.length() - 1));
                } catch (NumberFormatException e) {
                    return new ArrayList<>();
                }
            }
            List<Component> siguientes = new ArrayList<>();
            for (Component actual : actuales) {
                if (actual instanceof Container) {
                    int numero = 0;
                    for (Component hijo : ((Container) actual).getComponents()) {
                        if (clase.equals(hijo.getClass().getSimpleName())) {
                            if (indice < 0 || numero == indice) {
                                siguientes.add(hijo);
                            }
                            numero++;
                        }
                    }
                }
            }
            actuales = siguientes;
        }
        return actuales;
    }
}
