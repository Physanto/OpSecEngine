package io.github.physanto.opsecengine.informes;

import static io.github.physanto.opsecengine.informes.Resultados.INICIO;
import static io.github.physanto.opsecengine.informes.Resultados.caso;
import static io.github.physanto.opsecengine.informes.Resultados.evento;
import static io.github.physanto.opsecengine.informes.Resultados.resultado;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.physanto.opsecengine.dominio.resultado.ResultadoSuite;
import io.github.physanto.opsecengine.dominio.resultado.Veredicto;
import io.github.physanto.opsecengine.dominio.traza.ContextoEvento;
import io.github.physanto.opsecengine.dominio.traza.Evento;
import io.github.physanto.opsecengine.dominio.traza.TipoEvento;
import io.github.physanto.opsecengine.soporte.DatosEjemplo;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pruebas de la sección «Rendimiento y robustez» del informe HTML ({@link RendimientoHtml}): con el
 * resultado de ejemplo y con trazas que traen tiempos, bloqueos de la interfaz y ventanas.
 */
class RendimientoHtmlTest {

    @TempDir Path destino;

    @Test
    void conElResultadoDeEjemploMuestraLosPasosMasLentos() throws Exception {
        String html = generar(DatosEjemplo.resultadoSuite());

        assertThat(html).contains("<h2>Rendimiento y robustez</h2>", "<h3>Pasos más lentos</h3>");
        assertThat(html)
                .contains(
                        "<tr><td>C15</td><td class=\"num\">3</td><td>LLENAR etiqueta=Usuario</td>")
                .contains("10000 ms");
        assertThat(html.indexOf("<td>C15</td>")).isLessThan(html.indexOf("<td>N3</td>"));
        assertThat(html).contains("La traza no trae mediciones de tiempo de respuesta");
        assertThat(html).doesNotContain("<h3>Interfaz bloqueada</h3>", "<h3>Ventanas</h3>");
    }

    @Test
    void agrupaLosTiemposDeRespuestaPorAccionConMedianaYP95() throws Exception {
        ResultadoSuite suite =
                resultado(
                        "s",
                        casoConRespuesta("C1", 100),
                        casoConRespuesta("C2", 300),
                        casoConRespuesta("C3", 200));

        String html = generar(suite);

        assertThat(html)
                .contains(
                        "<tr><td>PULSAR texto=Guardar</td><td class=\"num\">3</td>"
                                + "<td class=\"num\">200 ms</td><td class=\"num\">300 ms</td>"
                                + "<td class=\"num\">300 ms</td><td class=\"num\">250 ms</td>");
        assertThat(html).as("p95 por encima del umbral").contains("<span class=\"alto\"");
    }

    @Test
    void elPercentil95UsaElRangoMasProximoComoElOraculo() {
        List<Long> veinte = new ArrayList<>();
        for (long i = 1; i <= 20; i++) {
            veinte.add(i);
        }

        assertThat(RendimientoHtml.percentil95(veinte)).isEqualTo(19);
        assertThat(RendimientoHtml.percentil95(List.of(7L))).isEqualTo(7);
        assertThat(RendimientoHtml.percentil95(List.of(1L, 2L, 3L))).isEqualTo(3);
    }

    @Test
    void laMedianaPromediaLosDosCentralesSiHacePar() {
        assertThat(RendimientoHtml.mediana(List.of(1L, 2L, 9L))).isEqualTo("2");
        assertThat(RendimientoHtml.mediana(List.of(2L, 4L))).isEqualTo("3");
        assertThat(RendimientoHtml.mediana(List.of(1L, 2L))).isEqualTo("1.5");
    }

    @Test
    void muestraLosBloqueosDeLaInterfazYMarcaLosQueSuperanSuUmbral() throws Exception {
        Evento bloqueo =
                evento(
                        1,
                        TipoEvento.TIEMPO,
                        Map.of("medida", "EDT_BLOQUEADO", "duracionMs", 900, "umbralMs", 500));

        String html = generar(resultado("s", caso("C1", Veredicto.cumple(), bloqueo)));

        assertThat(html).contains("<h3>Interfaz bloqueada</h3>");
        assertThat(html).contains("<td class=\"num\">900 ms</td><td class=\"num\">500 ms</td>");
        assertThat(html).contains("<span class=\"alto\" style=\"width:100%\">");
    }

    @Test
    void listaLasVentanasSinConstanciaDeLiberacion() throws Exception {
        ResultadoSuite suite =
                resultado(
                        "s",
                        caso(
                                "C1",
                                Veredicto.cumple(),
                                ventana(1, TipoEvento.VENTANA_ABIERTA, "v1", "Editar", null),
                                ventana(2, TipoEvento.VENTANA_CERRADA, "v1", "Editar", false),
                                ventana(3, TipoEvento.VENTANA_ABIERTA, "v2", "Buscar", null),
                                ventana(4, TipoEvento.VENTANA_CERRADA, "v2", "Buscar", true),
                                ventana(5, TipoEvento.VENTANA_ABIERTA, "v3", "Menú", null),
                                ventana(6, TipoEvento.VENTANA_ABIERTA, "v4", "Aviso", null),
                                ventana(7, TipoEvento.VENTANA_CERRADA, "v4", "Aviso", null)));

        String html = generar(suite);

        assertThat(html)
                .contains("4 ventanas abiertas en la suite; 3 sin constancia de liberación");
        assertThat(html)
                .contains(
                        "<td>Editar (javax.swing.JDialog)</td><td class=\"estado-fuga\">"
                                + "Cerrada sin liberar (posible fuga)</td>")
                .contains("<td class=\"estado-duda\">Abierta al terminar el caso</td>")
                .contains("<td class=\"estado-duda\">Cerrada, sin dato de liberación</td>");
        assertThat(html).doesNotContain("<td>Buscar (javax.swing.JDialog)</td>");
    }

    @Test
    void siTodasLasVentanasSeLiberaronLoDice() throws Exception {
        ResultadoSuite suite =
                resultado(
                        "s",
                        caso(
                                "C1",
                                Veredicto.cumple(),
                                ventana(1, TipoEvento.VENTANA_ABIERTA, "v1", "Editar", null),
                                ventana(2, TipoEvento.VENTANA_CERRADA, "v1", "Editar", true)));

        assertThat(generar(suite)).contains("1 ventana abierta en la suite; consta que todas se");
    }

    @Test
    void lasMedidasDelInicioDeSesionSeIdentificanComoTales() throws Exception {
        Evento login =
                new Evento(
                        1,
                        INICIO,
                        TipoEvento.TIEMPO,
                        new ContextoEvento("C1", 0, "admin", false),
                        Map.of("medida", "RESPUESTA_ACCION", "duracionMs", 40));

        String html = generar(resultado("s", caso("C1", Veredicto.cumple(), login)));

        assertThat(html).contains("<tr><td>inicio de sesión</td><td class=\"num\">1</td>");
    }

    @Test
    void sinDatosDeRendimientoNoHaySeccion() throws Exception {
        assertThat(generar(resultado("s", caso("C1", Veredicto.cumple()))))
                .doesNotContain("Rendimiento y robustez");
    }

    @Test
    void escapaLosTitulosDeVentanaYSigueSinJavaScript() throws Exception {
        ResultadoSuite suite =
                resultado(
                        "s",
                        caso(
                                "C1",
                                Veredicto.cumple(),
                                ventana(
                                        1,
                                        TipoEvento.VENTANA_ABIERTA,
                                        "v1",
                                        "<script>x</script>",
                                        null)));

        String html = generar(suite);

        assertThat(html).doesNotContain("<script").contains("&lt;script&gt;x&lt;/script&gt;");
    }

    @Test
    void noPresentaDuracionesInvalidasComoMediciones() throws Exception {
        for (Number invalida :
                List.<Number>of(
                        -1,
                        1.5,
                        Double.NaN,
                        Double.POSITIVE_INFINITY,
                        new BigInteger("9223372036854775808"))) {
            Evento medida =
                    evento(
                            1,
                            TipoEvento.TIEMPO,
                            Map.of("medida", "RESPUESTA_ACCION", "duracionMs", invalida));
            assertThat(generar(resultado("s", caso("C1", Veredicto.cumple(), medida))))
                    .as("duración inválida: %s", invalida)
                    .doesNotContain("Rendimiento y robustez");
        }
    }

    @Test
    void calculaLaMedianaYLaBarraSinDesbordarLong() throws Exception {
        assertThat(RendimientoHtml.mediana(List.of(Long.MAX_VALUE - 1, Long.MAX_VALUE)))
                .isEqualTo("9223372036854775806.5");
        Evento medida =
                evento(
                        1,
                        TipoEvento.TIEMPO,
                        Map.of("medida", "RESPUESTA_ACCION", "duracionMs", Long.MAX_VALUE));
        assertThat(generar(resultado("s", caso("C1", Veredicto.cumple(), medida))))
                .contains("style=\"width:100%\"");
    }

    @Test
    void unaDuracionCeroTieneBarraVaciaAunqueExistanOtrasMediciones() throws Exception {
        Evento cero = evento(1, TipoEvento.PASO_FIN, Map.of("duracionMs", 0));
        Evento otro = evento(2, TipoEvento.PASO_FIN, Map.of("duracionMs", 100));
        assertThat(generar(resultado("s", caso("C1", Veredicto.cumple(), cero, otro))))
                .contains("style=\"width:0%\"");
    }

    @Test
    void noMezclaUmbralesDistintosAlAgruparUnaAccion() throws Exception {
        var bajo = casoConRespuesta("C1", 400);
        var alto =
                caso(
                        "C2",
                        Veredicto.cumple(),
                        evento(
                                1,
                                TipoEvento.PASO_INICIO,
                                Map.of("accion", "PULSAR", "objetivo", "texto=Guardar")),
                        evento(
                                2,
                                TipoEvento.TIEMPO,
                                Map.of(
                                        "medida",
                                        "RESPUESTA_ACCION",
                                        "duracionMs",
                                        100,
                                        "umbralMs",
                                        1000)));
        String html = generar(resultado("s", bajo, alto));
        assertThat(html).contains("<span class=\"alto\"");
        assertThat(html)
                .contains("<td class=\"num\">250 ms</td>", "<td class=\"num\">1000 ms</td>");
    }

    private String generar(ResultadoSuite suite) throws Exception {
        new ReporteroHtml().escribir(suite, destino);
        return Files.readString(destino.resolve(ReporteroHtml.ARCHIVO), StandardCharsets.UTF_8);
    }

    /** Caso con un paso «PULSAR texto=Guardar» y su tiempo de respuesta, con umbral de 250 ms. */
    private static io.github.physanto.opsecengine.dominio.resultado.ResultadoCaso casoConRespuesta(
            String id, int ms) {
        return caso(
                id,
                Veredicto.cumple(),
                evento(
                        1,
                        TipoEvento.PASO_INICIO,
                        Map.of("accion", "PULSAR", "objetivo", "texto=Guardar", "modo", "EVENTOS")),
                evento(
                        2,
                        TipoEvento.TIEMPO,
                        Map.of("medida", "RESPUESTA_ACCION", "duracionMs", ms, "umbralMs", 250)));
    }

    private static Evento ventana(
            long secuencia, TipoEvento tipo, String id, String titulo, Boolean liberada) {
        Map<String, Object> datos =
                new java.util.HashMap<>(
                        Map.of("ventanaId", id, "titulo", titulo, "clase", "javax.swing.JDialog"));
        if (liberada != null) {
            datos.put("liberada", liberada);
        }
        return evento(secuencia, tipo, datos);
    }
}
