package io.github.physanto.opsecengine.informes;

import static io.github.physanto.opsecengine.informes.Resultados.caso;
import static io.github.physanto.opsecengine.informes.Resultados.hallazgo;
import static io.github.physanto.opsecengine.informes.Resultados.resultado;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.physanto.opsecengine.dominio.resultado.Confianza;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoSuite;
import io.github.physanto.opsecengine.dominio.resultado.Veredicto;
import io.github.physanto.opsecengine.soporte.DatosEjemplo;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pruebas de los filtros por veredicto, oráculo y CWE del informe HTML. Como los filtros son CSS
 * puro, se comprueba que los controles, las reglas y las clases de cada caso estén de acuerdo.
 */
class FiltrosHtmlTest {

    private final ReporteroHtml reportero = new ReporteroHtml();

    @TempDir Path destino;

    @Test
    void elInformeDeEjemploTieneLosTresGruposDeFiltros() throws Exception {
        String html = generar(DatosEjemplo.resultadoSuite());

        assertThat(html).contains("<h2>Filtros</h2>");
        assertThat(html)
                .contains("<legend>Veredicto</legend>", "<legend>Oráculo</legend>")
                .contains("<legend>CWE</legend>");
        assertThat(html).contains("type=\"radio\" name=\"fv\" id=\"fv-todos\" checked");
        assertThat(html).contains("id=\"fv-no-cumple\"", "id=\"fo-0\"", "id=\"fw-0\"");
        assertThat(html).contains("> SIN-SESION</label>", "> CWE-306</label>");
    }

    @Test
    void cadaOpcionTieneSuReglaQueOcultaLosCasosQueNoLaCumplen() throws Exception {
        String html = generar(DatosEjemplo.resultadoSuite());

        assertThat(html)
                .contains("body:has(#fv-cumple:checked) details.caso:not(.cumple){display:none}")
                .contains("body:has(#fv-no-cumple:checked) details.caso:not(.no-cumple)")
                .contains("body:has(#fv-no-concluyente:checked) details.caso:not(.no-concluyente)")
                .contains("body:has(#fo-0:checked) details.caso:not(.o0)")
                .contains("body:has(#fw-0:checked) details.caso:not(.w0)");
    }

    @Test
    void losCasosLlevanLasClasesDeSusOraculosYCwe() throws Exception {
        String html = generar(DatosEjemplo.resultadoSuite());

        assertThat(html).contains("<details class=\"caso no-cumple o0 w0\" id=\"c0\"");
        assertThat(html).contains("<details class=\"caso cumple\" id=\"c1\"");
        assertThat(html).contains("<details class=\"caso no-concluyente\" id=\"c2\"");
    }

    @Test
    void lasOpcionesSeNumeranEnOrdenAlfabeticoYCadaCasoApuntaALasSuyas() throws Exception {
        ResultadoSuite suite =
                resultado(
                        "s",
                        caso(
                                "C1",
                                Veredicto.noCumple(
                                        List.of(
                                                hallazgo(
                                                        "ZETA",
                                                        "CWE-89",
                                                        Confianza.CONFIRMADO,
                                                        "z"),
                                                hallazgo(
                                                        "ALFA",
                                                        "CWE-20",
                                                        Confianza.PROBABLE,
                                                        "a")))),
                        caso(
                                "C2",
                                Veredicto.noCumple(
                                        List.of(hallazgo("ZETA", null, Confianza.INFO, "z2")))));

        String html = generar(suite);

        assertThat(html).contains("id=\"fo-0\"> ALFA</label>", "id=\"fo-1\"> ZETA</label>");
        assertThat(html).contains("id=\"fw-0\"> CWE-20</label>", "id=\"fw-1\"> CWE-89</label>");
        assertThat(html).contains("<details class=\"caso no-cumple o0 o1 w0 w1\" id=\"c0\"");
        assertThat(html).contains("<details class=\"caso no-cumple o1\" id=\"c1\"");
    }

    @Test
    void losHallazgosDeSuiteTambienSeFiltranPorOraculoYCwe() throws Exception {
        ResultadoSuite suite =
                resultado(
                        "s",
                        List.of(hallazgo("ENUM", "CWE-204", Confianza.PROBABLE, "Mensajes")),
                        caso("C1", Veredicto.cumple()));

        String html = generar(suite);

        assertThat(html).contains("<li class=\"hallazgo hallazgo-suite o0 w0\">");
        assertThat(html).contains("li.hallazgo-suite:not(.o0){display:none}");
        assertThat(html).contains("li.hallazgo-suite:not(.w0){display:none}");
    }

    @Test
    void sinHallazgosSoloQuedaElFiltroDeVeredicto() throws Exception {
        String html = generar(resultado("limpia", caso("C1", Veredicto.cumple())));

        assertThat(html).contains("<legend>Veredicto</legend>");
        assertThat(html).doesNotContain("<legend>Oráculo</legend>", "<legend>CWE</legend>");
        assertThat(html).doesNotContain("body:has(#fo-", "body:has(#fw-");
    }

    @Test
    void sinCasosNoHayFiltros() throws Exception {
        assertThat(generar(resultado("vacia"))).doesNotContain("<h2>Filtros</h2>");
    }

    @Test
    void elTextoDeUnOraculoNuncaLlegaAlCssNiAUnIdentificador() throws Exception {
        String malo = "X</style><script>alert(1)</script>";
        ResultadoSuite suite =
                resultado(
                        "s",
                        caso(
                                "C1",
                                Veredicto.noCumple(
                                        List.of(
                                                hallazgo(
                                                        malo, "CWE-1", Confianza.PROBABLE, "t")))));

        String html = generar(suite);

        assertThat(html).doesNotContain("<script>alert", "X</style>");
        assertThat(html).contains("&lt;/style&gt;&lt;script&gt;alert(1)&lt;/script&gt;");
        assertThat(html.indexOf("</style>")).isEqualTo(html.lastIndexOf("</style>"));
    }

    @Test
    void elInformeSigueSinJavaScript() throws Exception {
        assertThat(generar(DatosEjemplo.resultadoSuite())).doesNotContain("<script", "onclick");
    }

    private String generar(ResultadoSuite suite) throws Exception {
        reportero.escribir(suite, destino);
        return Files.readString(destino.resolve(ReporteroHtml.ARCHIVO), StandardCharsets.UTF_8);
    }
}
