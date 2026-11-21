package io.github.physanto.opsecengine.agente.swing;

import io.github.physanto.opsecengine.protocolo.dto.CriterioDto;
import io.github.physanto.opsecengine.protocolo.dto.LocalizadorDto;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/**
 * Calcula los localizadores con que se puede volver a encontrar un componente: es la operación
 * inversa de {@link BuscadorComponentes}. Lo usan el inspector y la grabadora.
 *
 * <p>Propone, en este orden, el nombre del componente ({@code NOMBRE_COMPONENTE}), la etiqueta que
 * lo acompaña ({@code ETIQUETA}, solo para controles que se llenan o se eligen), su texto ({@code
 * TEXTO}), su nombre accesible ({@code NOMBRE_ACCESIBLE}) y, al final, su ruta ({@code RUTA}). Cada
 * propuesta se comprueba con el propio buscador contra las ventanas visibles y solo se conserva si
 * el buscador resuelve ese componente (ante un texto repetido el buscador elige el primero), así
 * que el resultado sirve tal cual en un paso. La ruta siempre se incluye si el componente está en
 * una ventana.
 *
 * <p>No tiene estado. Todos sus métodos deben llamarse desde el EDT.
 */
final class LocalizadoresComponente {

    /** Máximo de etiquetas de la ventana que se prueban como {@code ETIQUETA}. */
    private static final int MAXIMO_ETIQUETAS = 60;

    private LocalizadoresComponente() {}

    /**
     * Calcula los criterios verificados que identifican el componente.
     *
     * @param componente componente que se quiere localizar
     * @return los criterios en orden de preferencia, el último {@code RUTA}; vacío si no se pudo
     *     encontrar ninguno
     */
    static List<CriterioDto> para(Component componente) {
        List<CriterioDto> criterios = new ArrayList<>();
        agregar(criterios, componente, "NOMBRE_COMPONENTE", nombrePropio(componente));
        if (admiteEtiqueta(componente)) {
            for (String etiqueta : etiquetasDeLaVentana(componente)) {
                if (agregar(criterios, componente, "ETIQUETA", etiqueta)) {
                    break;
                }
            }
        }
        String texto = DescriptorComponentes.texto(componente);
        agregar(
                criterios,
                componente,
                "TEXTO",
                noBlanco(texto) && !esCampo(componente) ? texto : null);
        String accesible = DescriptorComponentes.nombreAccesible(componente);
        if (noBlanco(accesible) && !accesible.equals(texto)) {
            agregar(criterios, componente, "NOMBRE_ACCESIBLE", accesible);
        }
        String ruta = DescriptorComponentes.ruta(componente);
        if (ruta != null) {
            criterios.add(new CriterioDto("RUTA", ruta));
        }
        return criterios;
    }

    /**
     * Arma los datos comunes de los eventos {@code COMPONENTE_INSPECCIONADO} y {@code
     * ACCION_GRABADA}: ventana, clase del componente y localizador.
     *
     * @param componente componente descrito
     * @return un mapa nuevo y modificable, con los campos del catálogo de eventos
     */
    static Map<String, Object> datos(Component componente) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("ventana", nombreVentana(componente));
        datos.put("componente", componente.getClass().getName());
        datos.put("localizador", comoLista(para(componente)));
        return datos;
    }

    /**
     * Arma los datos del evento {@code COMPONENTE_INSPECCIONADO}: los comunes más la ruta y el
     * texto visible, si los hay.
     *
     * @param componente componente inspeccionado
     * @return un mapa nuevo y modificable, con los campos del catálogo de eventos
     */
    static Map<String, Object> datosDeInspeccion(Component componente) {
        Map<String, Object> datos = datos(componente);
        Map<String, Object> completos = new LinkedHashMap<>();
        completos.put("ventana", datos.get("ventana"));
        completos.put("componente", datos.get("componente"));
        String ruta = DescriptorComponentes.ruta(componente);
        if (ruta != null) {
            completos.put("ruta", ruta);
        }
        String texto = DescriptorComponentes.texto(componente);
        if (noBlanco(texto)) {
            completos.put("texto", texto);
        }
        completos.put("localizador", datos.get("localizador"));
        return completos;
    }

    /**
     * Devuelve el nombre con que se identifica la ventana del componente.
     *
     * @param componente componente o ventana
     * @return el título de la ventana, o su nombre, o su clase simple
     */
    static String nombreVentana(Component componente) {
        Window ventana =
                componente instanceof Window
                        ? (Window) componente
                        : SwingUtilities.getWindowAncestor(componente);
        return ventana == null ? "" : DescriptorComponentes.ruta(ventana);
    }

    /** Convierte los criterios en la lista de mapas que lleva el evento. */
    static List<Map<String, Object>> comoLista(List<CriterioDto> criterios) {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (CriterioDto criterio : criterios) {
            Map<String, Object> elemento = new LinkedHashMap<>();
            elemento.put("estrategia", criterio.getEstrategia());
            elemento.put("valor", criterio.getValor());
            lista.add(elemento);
        }
        return lista;
    }

    /** Agrega el criterio si encuentra solo ese componente; devuelve si lo agregó. */
    private static boolean agregar(
            List<CriterioDto> criterios, Component componente, String estrategia, String valor) {
        if (!noBlanco(valor) || valor.indexOf('\n') >= 0) {
            return false;
        }
        LocalizadorDto candidato =
                new LocalizadorDto(Collections.singletonList(new CriterioDto(estrategia, valor)));
        BuscadorComponentes.Busqueda busqueda = BuscadorComponentes.buscar(candidato);
        if (busqueda.componente() != componente) {
            return false;
        }
        criterios.add(new CriterioDto(estrategia, valor));
        return true;
    }

    /** Nombre puesto con {@code setName}, sin los nombres internos que Swing da a sus piezas. */
    private static String nombrePropio(Component componente) {
        String nombre = componente.getName();
        return nombre == null || nombre.startsWith("null.") ? null : nombre;
    }

    /** Controles que se localizan por la etiqueta que los acompaña. */
    private static boolean admiteEtiqueta(Component componente) {
        return esCampo(componente)
                || componente instanceof JSpinner
                || componente instanceof JComboBox
                || componente instanceof JList
                || componente instanceof JTable
                || componente instanceof JTree;
    }

    /** Campos de texto y similares, cuyo texto es contenido y no sirve para localizarlos. */
    private static boolean esCampo(Component componente) {
        return componente instanceof JTextComponent;
    }

    /** Textos de las etiquetas de la ventana del componente, sin repetir, en orden de aparición. */
    private static Set<String> etiquetasDeLaVentana(Component componente) {
        Set<String> textos = new LinkedHashSet<>();
        Window ventana = SwingUtilities.getWindowAncestor(componente);
        if (ventana != null) {
            recogerEtiquetas(ventana, textos);
        }
        return textos;
    }

    private static void recogerEtiquetas(Component componente, Set<String> textos) {
        if (textos.size() >= MAXIMO_ETIQUETAS) {
            return;
        }
        if (componente instanceof JLabel) {
            String texto = ((JLabel) componente).getText();
            if (noBlanco(texto)) {
                textos.add(texto.trim());
            }
        }
        if (componente instanceof Container && !(componente instanceof AbstractButton)) {
            for (Component hijo : ((Container) componente).getComponents()) {
                recogerEtiquetas(hijo, textos);
            }
        }
    }

    private static boolean noBlanco(String texto) {
        return texto != null && !texto.trim().isEmpty();
    }
}
