package io.github.physanto.opsecengine.agente.swing;

import io.github.physanto.opsecengine.agente.api.ResultadoBusqueda;
import io.github.physanto.opsecengine.protocolo.dto.CriterioDto;
import io.github.physanto.opsecengine.protocolo.dto.LocalizadorDto;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.swing.JLabel;
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
 * <p>Estrategias soportadas, con comparación exacta del texto sin espacios al inicio ni al final:
 *
 * <ul>
 *   <li>{@code ETIQUETA}: un {@code JLabel} con ese texto y un componente asociado con {@code
 *       setLabelFor}; el resultado es el componente asociado.
 *   <li>{@code TEXTO}: botones y etiquetas con ese texto (no el contenido de los campos).
 *   <li>{@code NOMBRE_COMPONENTE}: el nombre puesto con {@code setName}.
 *   <li>{@code NOMBRE_ACCESIBLE}: el nombre accesible.
 *   <li>{@code TIPO}: el nombre completo o simple de la clase del componente.
 *   <li>{@code TITULO_VENTANA} y {@code CLASE_VENTANA}: una ventana visible.
 * </ul>
 *
 * <p>{@code RUTA} y las estrategias desconocidas no encuentran nada. Se debe llamar desde el EDT.
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
        List<Window> ventanas = ventanasVisibles();
        Set<Window> modales = EjecutorEdt.modalesVisibles();
        Busqueda primeraNoAlcanzable = null;
        List<CriterioDto> criterios = localizador.getCriterios();
        for (int i = 0; i < criterios.size(); i++) {
            for (Component candidato : candidatos(criterios.get(i), ventanas)) {
                String motivo = motivoNoAlcanzable(candidato, modales);
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

    private static List<Component> candidatos(CriterioDto criterio, List<Window> ventanas) {
        String valor = criterio.getValor().trim();
        List<Component> encontrados = new ArrayList<>();
        switch (criterio.getEstrategia()) {
            case "TITULO_VENTANA":
                for (Window ventana : ventanas) {
                    if (valor.equals(recortar(DescriptorComponentes.texto(ventana)))) {
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
                    recorrer(ventana, criterio.getEstrategia(), valor, encontrados);
                }
                return encontrados;
        }
    }

    private static void recorrer(
            Component componente, String estrategia, String valor, List<Component> encontrados) {
        Component coincidencia = coincide(componente, estrategia, valor);
        if (coincidencia != null) {
            encontrados.add(coincidencia);
        }
        if (componente instanceof Container) {
            for (Component hijo : ((Container) componente).getComponents()) {
                recorrer(hijo, estrategia, valor, encontrados);
            }
        }
    }

    /** Devuelve el componente que corresponde si el criterio coincide con este, o null. */
    private static Component coincide(Component componente, String estrategia, String valor) {
        switch (estrategia) {
            case "ETIQUETA":
                if (componente instanceof JLabel
                        && ((JLabel) componente).getLabelFor() != null
                        && valor.equals(recortar(((JLabel) componente).getText()))) {
                    return ((JLabel) componente).getLabelFor();
                }
                return null;
            case "TEXTO":
                if (componente instanceof Window || componente instanceof JTextComponent) {
                    return null;
                }
                return valor.equals(recortar(DescriptorComponentes.texto(componente)))
                        ? componente
                        : null;
            case "NOMBRE_COMPONENTE":
                return valor.equals(componente.getName()) ? componente : null;
            case "NOMBRE_ACCESIBLE":
                return valor.equals(recortar(DescriptorComponentes.nombreAccesible(componente)))
                        ? componente
                        : null;
            case "TIPO":
                return esDeClase(componente, valor) ? componente : null;
            default:
                return null;
        }
    }

    /** Motivo por el que el usuario no podría usar el componente, o null si puede. */
    private static String motivoNoAlcanzable(Component componente, Set<Window> modales) {
        if (!componente.isShowing()) {
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

    private static String recortar(String texto) {
        return texto == null ? null : texto.trim();
    }
}
