package io.github.physanto.opsecengine.informes;

import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoCaso;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoSuite;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

/**
 * Filtros del informe HTML por veredicto, oráculo y CWE, hechos solo con CSS.
 *
 * <p>Cada filtro es un grupo de botones de opción ({@code <input type="radio">}) y cada opción
 * oculta con una regla CSS {@code body:has(#opción:checked)} los casos que no la cumplen. Así el
 * informe sigue sin JavaScript y con su política de seguridad de contenido intacta. Los filtros se
 * combinan: un caso se muestra solo si cumple todos los que están activos. Los hallazgos de suite
 * se filtran por oráculo y CWE, pero no por veredicto, que es una propiedad de los casos.
 *
 * <p>Las opciones de oráculo y de CWE son las que aparecen en el resultado. Se identifican por su
 * posición en una lista ordenada, no por su nombre, para que ningún texto de la traza llegue a un
 * identificador o a un selector CSS.
 *
 * <p>Requiere un navegador con soporte del selector {@code :has()} (todos los actuales desde 2023);
 * en uno antiguo los controles no tienen efecto y el informe se ve completo.
 *
 * <p>Es inmutable una vez creado y seguro entre hilos.
 */
final class Filtros {

    /** Clase CSS de los hallazgos de suite, para que los filtros de oráculo y CWE los alcancen. */
    static final String CLASE_HALLAZGO_SUITE = "hallazgo-suite";

    private static final String[][] VEREDICTOS = {
        {"cumple", "Cumple"}, {"no-cumple", "No cumple"}, {"no-concluyente", "No concluyente"}
    };

    private final List<String> oraculos;
    private final List<String> cwes;

    /**
     * Crea los filtros con los oráculos y los CWE de un resultado.
     *
     * @param resultado resultado de la suite; no puede ser null
     */
    Filtros(ResultadoSuite resultado) {
        TreeSet<String> ids = new TreeSet<>();
        TreeSet<String> debilidades = new TreeSet<>();
        for (ResultadoCaso caso : resultado.casos()) {
            for (Hallazgo hallazgo : caso.veredicto().hallazgos()) {
                ids.add(hallazgo.oraculoId());
                if (hallazgo.cwe() != null) {
                    debilidades.add(hallazgo.cwe());
                }
            }
        }
        for (Hallazgo hallazgo : resultado.hallazgosSuite()) {
            ids.add(hallazgo.oraculoId());
            if (hallazgo.cwe() != null) {
                debilidades.add(hallazgo.cwe());
            }
        }
        this.oraculos = List.copyOf(ids);
        this.cwes = List.copyOf(debilidades);
    }

    /**
     * Escribe los controles de los filtros.
     *
     * @param h destino del HTML
     */
    void controles(StringBuilder h) {
        h.append("<section class=\"filtros\">\n<h2>Filtros</h2>\n");
        h.append("<p class=\"sub\">Se combinan: un caso se muestra si cumple todos.</p>\n");
        h.append("<div class=\"grupos\">\n");
        grupo(
                h,
                "fv",
                "Veredicto",
                Arrays.stream(VEREDICTOS).map(v -> v[0]).toList(),
                Arrays.stream(VEREDICTOS).map(v -> v[1]).toList());
        if (!oraculos.isEmpty()) {
            grupo(h, "fo", "Oráculo", indices(oraculos.size()), oraculos);
        }
        if (!cwes.isEmpty()) {
            grupo(h, "fw", "CWE", indices(cwes.size()), cwes);
        }
        h.append("</div>\n</section>\n");
    }

    /**
     * Devuelve las reglas CSS que aplican los filtros.
     *
     * @return reglas listas para ir dentro de {@code <style>}
     */
    String css() {
        StringBuilder css = new StringBuilder();
        for (String[] veredicto : VEREDICTOS) {
            css.append("body:has(#fv-").append(veredicto[0]).append(":checked) ");
            css.append("details.caso:not(.").append(veredicto[0]).append("){display:none}\n");
        }
        for (int i = 0; i < oraculos.size(); i++) {
            regla(css, "fo", "o", i);
        }
        for (int i = 0; i < cwes.size(); i++) {
            regla(css, "fw", "w", i);
        }
        return css.toString();
    }

    /**
     * Devuelve las clases CSS de un caso según los oráculos y CWE de sus hallazgos.
     *
     * @param caso caso del resultado; no puede ser null
     * @return clases precedidas de un espacio, o cadena vacía si el caso no tiene hallazgos
     */
    String clases(ResultadoCaso caso) {
        return clases(caso.veredicto().hallazgos());
    }

    /**
     * Devuelve las clases CSS de un hallazgo de suite.
     *
     * @param hallazgo hallazgo de suite; no puede ser null
     * @return clases precedidas de un espacio, incluida {@value #CLASE_HALLAZGO_SUITE}
     */
    String clases(Hallazgo hallazgo) {
        return " " + CLASE_HALLAZGO_SUITE + clases(List.of(hallazgo));
    }

    private String clases(List<Hallazgo> hallazgos) {
        TreeSet<Integer> deOraculo = new TreeSet<>();
        TreeSet<Integer> deCwe = new TreeSet<>();
        for (Hallazgo hallazgo : hallazgos) {
            deOraculo.add(oraculos.indexOf(hallazgo.oraculoId()));
            if (hallazgo.cwe() != null) {
                deCwe.add(cwes.indexOf(hallazgo.cwe()));
            }
        }
        StringBuilder clases = new StringBuilder();
        deOraculo.forEach(i -> clases.append(" o").append(i));
        deCwe.forEach(i -> clases.append(" w").append(i));
        return clases.toString();
    }

    private static void regla(StringBuilder css, String prefijoId, String prefijoClase, int i) {
        css.append("body:has(#").append(prefijoId).append('-').append(i).append(":checked) ");
        css.append("details.caso:not(.").append(prefijoClase).append(i).append("),\n");
        css.append("body:has(#").append(prefijoId).append('-').append(i).append(":checked) ");
        css.append("li.").append(CLASE_HALLAZGO_SUITE).append(":not(.").append(prefijoClase);
        css.append(i).append("){display:none}\n");
    }

    /**
     * Escribe un grupo de opciones.
     *
     * @param sufijos sufijo del id de cada opción; el de la opción «Todos» es siempre {@code todos}
     * @param etiquetas texto visible de cada opción, en el mismo orden que {@code sufijos}
     */
    private static void grupo(
            StringBuilder h,
            String id,
            String titulo,
            List<String> sufijos,
            List<String> etiquetas) {
        h.append("<fieldset><legend>").append(titulo).append("</legend>\n");
        opcion(h, id, "todos", "Todos", true);
        for (int i = 0; i < sufijos.size(); i++) {
            opcion(h, id, sufijos.get(i), etiquetas.get(i), false);
        }
        h.append("</fieldset>\n");
    }

    private static void opcion(
            StringBuilder h, String grupo, String sufijo, String etiqueta, boolean marcada) {
        h.append("<label><input type=\"radio\" name=\"").append(grupo).append("\" id=\"");
        h.append(grupo).append('-').append(sufijo).append('"').append(marcada ? " checked" : "");
        h.append("> ").append(Html.escapar(etiqueta)).append("</label>\n");
    }

    /** Sufijos numéricos {@code 0..n-1}, uno por opción. */
    private static List<String> indices(int cantidad) {
        List<String> indices = new ArrayList<>();
        for (int i = 0; i < cantidad; i++) {
            indices.add(String.valueOf(i));
        }
        return indices;
    }
}
