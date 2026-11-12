package io.github.physanto.opsecengine.informes;

import io.github.physanto.opsecengine.dominio.resultado.ResultadoCaso;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoSuite;
import io.github.physanto.opsecengine.dominio.traza.Evento;
import io.github.physanto.opsecengine.dominio.traza.TipoEvento;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sección «Rendimiento y robustez» del informe HTML: tiempos, bloqueos de la interfaz y ventanas
 * que no se liberan, en tablas y barras hechas solo con CSS.
 *
 * <p>Muestra datos, no decide: los veredictos los dan los oráculos ({@code RESPUESTA-LENTA}, {@code
 * INTERFAZ-CONGELADA} y {@code VENTANA-NO-LIBERADA}). Las fuentes son los eventos del contrato:
 *
 * <ul>
 *   <li><b>Tiempos de respuesta por acción:</b> eventos {@code TIEMPO} con medida {@code
 *       RESPUESTA_ACCION}, agrupados por la acción y el objetivo de su paso ({@code PASO_INICIO}).
 *       Muestra el número de muestras, la mediana, el percentil 95 (por rango más próximo, igual
 *       que el oráculo) y el máximo.
 *   <li><b>Pasos más lentos:</b> la duración de cada paso ({@code duracionMs} de {@code PASO_FIN}),
 *       que el motor registra siempre.
 *   <li><b>Interfaz bloqueada:</b> eventos {@code TIEMPO} con medida {@code EDT_BLOQUEADO}, con su
 *       umbral si lo trae.
 *   <li><b>Ventanas:</b> cada ventana abierta en un caso ({@code VENTANA_ABIERTA}) y cómo terminó:
 *       liberada, cerrada sin liberar ({@code liberada=false}), cerrada sin dato o abierta al
 *       terminar el caso. Solo se listan las que no consta que se liberaran.
 * </ul>
 *
 * <p>Cada parte aparece solo si la traza trae sus datos. Todo texto se escapa. No tiene estado y no
 * se instancia; es seguro entre hilos.
 */
final class RendimientoHtml {

    /** Cuántos pasos se muestran en «Pasos más lentos». */
    static final int PASOS_LENTOS = 10;

    /** Estilos de la sección, además de los del informe. */
    static final String ESTILO =
            """
            .barra{background:var(--borde);border-radius:3px;height:10px;min-width:120px}
            .barra span{display:block;height:10px;border-radius:3px;background:var(--info)}
            .barra span.alto{background:var(--mal)}
            td.num{text-align:right;white-space:nowrap;font-variant-numeric:tabular-nums}
            .estado-fuga{color:var(--mal);font-weight:700}.estado-duda{color:var(--duda)}
            """;

    private RendimientoHtml() {}

    /** Mediciones de un paso: su acción legible y su duración. */
    private record Medida(String caso, int paso, String accion, long ms, Long umbralMs) {}

    /** Ventana de un caso y cómo terminó. */
    private record VentanaCaso(String caso, String nombre, String estado, boolean fuga) {}

    /**
     * Escribe la sección si el resultado tiene algún dato de rendimiento o de ventanas.
     *
     * @param h destino del HTML
     * @param resultado resultado de la suite
     */
    static void seccion(StringBuilder h, ResultadoSuite resultado) {
        List<Medida> respuestas = new ArrayList<>();
        List<Medida> pasos = new ArrayList<>();
        List<Medida> bloqueos = new ArrayList<>();
        List<VentanaCaso> ventanas = new ArrayList<>();
        int ventanasTotales = 0;
        for (ResultadoCaso caso : resultado.casos()) {
            Map<Integer, String> acciones = acciones(caso);
            for (Evento e : caso.traza().eventos()) {
                int paso = e.contexto().numeroPaso();
                String accion = acciones.getOrDefault(paso, nombrePaso(paso));
                if (e.tipo() == TipoEvento.PASO_FIN && e.numero("duracionMs").isPresent()) {
                    pasos.add(medida(caso, paso, accion, e));
                } else if (e.tipo() == TipoEvento.TIEMPO && e.numero("duracionMs").isPresent()) {
                    String tipo = e.texto("medida").orElse("");
                    if (tipo.equals("RESPUESTA_ACCION")) {
                        respuestas.add(medida(caso, paso, accion, e));
                    } else if (tipo.equals("EDT_BLOQUEADO")) {
                        bloqueos.add(medida(caso, paso, accion, e));
                    }
                }
            }
            List<VentanaCaso> delCaso = ventanas(caso);
            ventanasTotales += delCaso.size();
            delCaso.stream().filter(v -> !v.estado().equals("Liberada")).forEach(ventanas::add);
        }
        if (respuestas.isEmpty() && pasos.isEmpty() && bloqueos.isEmpty() && ventanasTotales == 0) {
            return;
        }
        h.append("<section class=\"rendimiento\">\n<h2>Rendimiento y robustez</h2>\n");
        h.append("<p class=\"sub\">Datos medidos en la ejecución. Los veredictos sobre ellos los ");
        h.append(
                "dan los oráculos (respuesta lenta, interfaz congelada, ventana no liberada).</p>\n");
        respuestas(h, respuestas);
        pasosLentos(h, pasos);
        bloqueos(h, bloqueos);
        ventanas(h, ventanas, ventanasTotales);
        h.append("</section>\n");
    }

    private static void respuestas(StringBuilder h, List<Medida> medidas) {
        h.append("<h3>Tiempos de respuesta por acción</h3>\n");
        if (medidas.isEmpty()) {
            h.append("<p class=\"vacio\">La traza no trae mediciones de tiempo de respuesta ");
            h.append("(eventos TIEMPO con medida RESPUESTA_ACCION).</p>\n");
            return;
        }
        Map<String, List<Medida>> porAccion = new LinkedHashMap<>();
        medidas.forEach(m -> porAccion.computeIfAbsent(m.accion(), k -> new ArrayList<>()).add(m));
        long maximo = medidas.stream().mapToLong(Medida::ms).max().orElse(1);
        h.append("<div class=\"tabla\"><table>\n<thead><tr><th>Acción</th><th>Muestras</th>");
        h.append("<th>Mediana</th><th>p95</th><th>Máximo</th><th>Umbral</th><th></th>");
        h.append("</tr></thead>\n<tbody>\n");
        for (Map.Entry<String, List<Medida>> grupo : porAccion.entrySet()) {
            List<Long> valores = grupo.getValue().stream().map(Medida::ms).sorted().toList();
            long p95 = percentil95(valores);
            Long umbral =
                    grupo.getValue().stream()
                            .map(Medida::umbralMs)
                            .filter(u -> u != null)
                            .max(Comparator.naturalOrder())
                            .orElse(null);
            h.append("<tr><td>").append(Html.escapar(grupo.getKey())).append("</td>");
            h.append("<td class=\"num\">").append(valores.size()).append("</td>");
            h.append("<td class=\"num\">").append(mediana(valores)).append(" ms</td>");
            h.append("<td class=\"num\">").append(p95).append(" ms</td>");
            h.append("<td class=\"num\">").append(valores.get(valores.size() - 1));
            h.append(" ms</td><td class=\"num\">").append(umbral == null ? "—" : umbral + " ms");
            h.append("</td><td>").append(barra(p95, maximo, umbral != null && p95 > umbral));
            h.append("</td></tr>\n");
        }
        h.append("</tbody></table></div>\n");
        h.append("<p class=\"sub\">La mediana y el p95 solo son comparables entre repeticiones ");
        h.append("de la misma acción en las mismas condiciones.</p>\n");
    }

    private static void pasosLentos(StringBuilder h, List<Medida> pasos) {
        if (pasos.isEmpty()) {
            return;
        }
        List<Medida> lentos =
                pasos.stream()
                        .sorted(Comparator.comparingLong(Medida::ms).reversed())
                        .limit(PASOS_LENTOS)
                        .toList();
        long maximo = lentos.get(0).ms();
        h.append("<h3>Pasos más lentos</h3>\n<div class=\"tabla\"><table>\n<thead><tr>");
        h.append("<th>Caso</th><th>Paso</th><th>Acción</th><th>Duración</th><th></th>");
        h.append("</tr></thead>\n<tbody>\n");
        for (Medida m : lentos) {
            fila(h, m);
            h.append("<td class=\"num\">").append(m.ms()).append(" ms</td><td>");
            h.append(barra(m.ms(), maximo, false)).append("</td></tr>\n");
        }
        h.append("</tbody></table></div>\n");
        h.append("<p class=\"sub\">Duración de cada paso medida por el motor, ");
        h.append(pasos.size() > PASOS_LENTOS ? "los " + PASOS_LENTOS + " más lentos de " : "");
        h.append(pasos.size()).append(pasos.size() == 1 ? " paso" : " pasos").append(".</p>\n");
    }

    private static void bloqueos(StringBuilder h, List<Medida> bloqueos) {
        if (bloqueos.isEmpty()) {
            return;
        }
        long maximo = bloqueos.stream().mapToLong(Medida::ms).max().orElse(1);
        h.append("<h3>Interfaz bloqueada</h3>\n<div class=\"tabla\"><table>\n<thead><tr>");
        h.append("<th>Caso</th><th>Paso</th><th>Acción</th><th>Bloqueo</th><th>Umbral</th>");
        h.append("<th></th></tr></thead>\n<tbody>\n");
        for (Medida m : bloqueos) {
            boolean supera = m.umbralMs() != null && m.ms() > m.umbralMs();
            fila(h, m);
            h.append("<td class=\"num\">").append(m.ms()).append(" ms</td><td class=\"num\">");
            h.append(m.umbralMs() == null ? "—" : m.umbralMs() + " ms").append("</td><td>");
            h.append(barra(m.ms(), maximo, supera)).append("</td></tr>\n");
        }
        h.append("</tbody></table></div>\n");
    }

    private static void ventanas(StringBuilder h, List<VentanaCaso> sinLiberar, int total) {
        if (total == 0) {
            return;
        }
        h.append("<h3>Ventanas</h3>\n<p>").append(total);
        h.append(
                total == 1 ? " ventana abierta en la suite; " : " ventanas abiertas en la suite; ");
        if (sinLiberar.isEmpty()) {
            h.append("consta que todas se liberaron.</p>\n");
            return;
        }
        h.append(sinLiberar.size()).append(" sin constancia de liberación:</p>\n");
        h.append("<div class=\"tabla\"><table>\n<thead><tr><th>Caso</th><th>Ventana</th>");
        h.append("<th>Cómo terminó</th></tr></thead>\n<tbody>\n");
        for (VentanaCaso v : sinLiberar) {
            h.append("<tr><td>").append(Html.escapar(v.caso())).append("</td><td>");
            h.append(Html.escapar(v.nombre())).append("</td><td class=\"");
            h.append(v.fuga() ? "estado-fuga" : "estado-duda").append("\">");
            h.append(Html.escapar(v.estado())).append("</td></tr>\n");
        }
        h.append("</tbody></table></div>\n");
        h.append("<p class=\"sub\">Ocultar o cerrar una ventana no demuestra que liberó sus ");
        h.append("recursos; una ventana principal abierta al terminar suele ser normal.</p>\n");
    }

    /** Ventanas abiertas en un caso, en orden de apertura, con su estado al final del caso. */
    private static List<VentanaCaso> ventanas(ResultadoCaso caso) {
        Map<String, String> nombres = new LinkedHashMap<>();
        Map<String, String> estados = new HashMap<>();
        for (Evento e : caso.traza().eventos()) {
            if (e.tipo() == TipoEvento.VENTANA_ABIERTA) {
                String clave = clave(e);
                nombres.putIfAbsent(clave, nombreVentana(e));
                estados.put(clave, "Abierta al terminar el caso");
            } else if (e.tipo() == TipoEvento.VENTANA_CERRADA && nombres.containsKey(clave(e))) {
                estados.put(
                        clave(e),
                        e.booleano("liberada")
                                .map(l -> l ? "Liberada" : "Cerrada sin liberar (posible fuga)")
                                .orElse("Cerrada, sin dato de liberación"));
            }
        }
        List<VentanaCaso> ventanas = new ArrayList<>();
        for (Map.Entry<String, String> v : nombres.entrySet()) {
            String estado = estados.get(v.getKey());
            ventanas.add(
                    new VentanaCaso(
                            caso.casoId(), v.getValue(), estado, estado.startsWith("Cerrada sin")));
        }
        return ventanas;
    }

    /** Identifica una ventana por su id; si el agente no lo envía, por título y clase. */
    private static String clave(Evento e) {
        return e.texto("ventanaId")
                .orElse(e.texto("titulo").orElse("") + "\u001f" + e.texto("clase").orElse(""));
    }

    private static String nombreVentana(Evento e) {
        String titulo = e.texto("titulo").filter(t -> !t.isBlank()).orElse(null);
        String clase = e.texto("clase").orElse(null);
        if (titulo != null && clase != null) {
            return titulo + " (" + clase + ")";
        }
        return titulo != null ? titulo : clase != null ? clase : e.texto("ventanaId").orElse("?");
    }

    /** Acción legible de cada paso del caso, tomada de su primer {@code PASO_INICIO}. */
    private static Map<Integer, String> acciones(ResultadoCaso caso) {
        Map<Integer, String> acciones = new HashMap<>();
        for (Evento e : caso.traza().deTipo(TipoEvento.PASO_INICIO)) {
            int paso = e.contexto().numeroPaso();
            if (paso > 0) {
                acciones.putIfAbsent(
                        paso,
                        (e.texto("accion").orElse("?") + " " + e.texto("objetivo").orElse(""))
                                .strip());
            }
        }
        return acciones;
    }

    private static String nombrePaso(int paso) {
        return paso == 0 ? "inicio de sesión" : "paso " + paso;
    }

    private static Medida medida(ResultadoCaso caso, int paso, String accion, Evento e) {
        return new Medida(
                caso.casoId(),
                paso,
                accion,
                e.numero("duracionMs").orElseThrow().longValue(),
                e.numero("umbralMs").map(Number::longValue).orElse(null));
    }

    private static void fila(StringBuilder h, Medida m) {
        h.append("<tr><td>").append(Html.escapar(m.caso())).append("</td><td class=\"num\">");
        h.append(m.paso() == 0 ? "login" : String.valueOf(m.paso())).append("</td><td>");
        h.append(Html.escapar(m.accion())).append("</td>");
    }

    /** Barra horizontal proporcional al máximo; roja si supera su umbral. */
    private static String barra(long valor, long maximo, boolean alta) {
        long porcentaje = maximo <= 0 ? 0 : Math.max(1, Math.min(100, valor * 100 / maximo));
        return "<div class=\"barra\"><span"
                + (alta ? " class=\"alto\"" : "")
                + " style=\"width:"
                + porcentaje
                + "%\"></span></div>";
    }

    /**
     * Mediana de valores ordenados.
     *
     * @return el valor central, o la media de los dos centrales con un decimal
     */
    static String mediana(List<Long> ordenados) {
        int n = ordenados.size();
        if (n % 2 == 1) {
            return String.valueOf(ordenados.get(n / 2));
        }
        double media = (ordenados.get(n / 2 - 1) + ordenados.get(n / 2)) / 2.0;
        return media == Math.rint(media)
                ? String.valueOf((long) media)
                : String.format(Locale.ROOT, "%.1f", media);
    }

    /**
     * Percentil 95 por rango más próximo, como el oráculo {@code RESPUESTA-LENTA}: el valor en la
     * posición {@code ceil(0,95 * n)}, empezando en uno.
     *
     * @param ordenados valores ordenados de menor a mayor; al menos uno
     * @return el percentil 95
     */
    static long percentil95(List<Long> ordenados) {
        int n = ordenados.size();
        return ordenados.get((int) ((95L * n + 99) / 100 - 1));
    }
}
