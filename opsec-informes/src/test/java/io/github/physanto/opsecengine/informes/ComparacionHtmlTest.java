package io.github.physanto.opsecengine.informes;

import static io.github.physanto.opsecengine.informes.Resultados.INICIO;
import static io.github.physanto.opsecengine.informes.Resultados.caso;
import static io.github.physanto.opsecengine.informes.Resultados.hallazgo;
import static io.github.physanto.opsecengine.informes.Resultados.resultado;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.physanto.opsecengine.dominio.resultado.Confianza;
import io.github.physanto.opsecengine.dominio.resultado.Manifiesto;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoCaso;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoSuite;
import io.github.physanto.opsecengine.dominio.resultado.Veredicto;
import io.github.physanto.opsecengine.soporte.DatosEjemplo;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pruebas de {@link ComparacionHtml}: contenido del informe con dos resultados, orden de los casos,
 * avisos de comparabilidad y escape del texto.
 */
class ComparacionHtmlTest {

    @TempDir Path destino;

    @Test
    void comparaElResultadoDeEjemploConUnaVersionPosterior() throws Exception {
        ResultadoSuite antes = DatosEjemplo.resultadoSuite();
        List<ResultadoCaso> casos = new ArrayList<>(antes.casos());
        ResultadoCaso n3 = casos.get(0);
        casos.set(
                0,
                new ResultadoCaso(
                        n3.casoId(),
                        Veredicto.cumple(),
                        n3.traza(),
                        n3.duracion(),
                        n3.intentos(),
                        n3.inestable()));
        casos.add(caso("NUEVO", Veredicto.cumple()));
        ResultadoSuite ahora =
                new ResultadoSuite(
                        antes.nombreSuite(),
                        antes.manifiesto(),
                        casos,
                        antes.hallazgosSuite(),
                        antes.inicio().plusSeconds(3600),
                        antes.fin().plusSeconds(3600));

        String html = generar(antes, ahora);

        assertThat(html).contains("Comparación de la suite ejemplo");
        assertThat(html).contains("No empeoró nada y se resolvió algo.");
        assertThat(html).contains("<tr class=\"mejoro\"><td><strong>N3</strong></td>");
        assertThat(html).contains("<tr class=\"nuevo\"><td><strong>NUEVO</strong></td>");
        assertThat(html).contains("<tr class=\"igual\"><td><strong>C15</strong></td>");
        assertThat(html).contains("SIN-SESION", "Eliminación de una persona sin iniciar sesión");
    }

    @Test
    void losCasosQueEmpeoraronVanPrimeroYElResumenAvisa() throws Exception {
        ResultadoSuite antes =
                resultado(
                        "s",
                        caso("IGUAL", Veredicto.cumple()),
                        caso("ROTO", Veredicto.cumple()),
                        caso("ARREGLADO", Veredicto.noCumple(List.of(unHallazgo()))));
        ResultadoSuite ahora =
                resultado(
                        "s",
                        caso("IGUAL", Veredicto.cumple()),
                        caso("ROTO", Veredicto.noCumple(List.of(unHallazgo()))),
                        caso("ARREGLADO", Veredicto.cumple()));

        String html = generar(antes, ahora);

        assertThat(html).contains("Hay cambios que empeoran el resultado");
        assertThat(html.indexOf("<strong>ROTO</strong>"))
                .isLessThan(html.indexOf("<strong>ARREGLADO</strong>"));
        assertThat(html.indexOf("<strong>ARREGLADO</strong>"))
                .isLessThan(html.indexOf("<strong>IGUAL</strong>"));
        assertThat(html).contains("<tr class=\"empeoro\">", "<tr class=\"mejoro\">");
    }

    @Test
    void sinCambiosLoDiceYNoAvisaDeComparabilidad() throws Exception {
        ResultadoSuite suite = resultado("s", caso("C", Veredicto.cumple()));

        String html = generar(suite, suite);

        assertThat(html).contains("No hay cambios en los veredictos ni en los hallazgos.");
        assertThat(html).doesNotContain("Ojo al comparar");
    }

    @Test
    void avisaSiCambiaLaAplicacionLaSuiteOElMotor() throws Exception {
        ResultadoSuite antes = resultado("s", caso("C", Veredicto.cumple()));
        Manifiesto otro =
                new Manifiesto("sha256:otro", "0.2.0", "21", "Linux", 42, "sha256:otra", INICIO);
        ResultadoSuite ahora =
                new ResultadoSuite(
                        "s", otro, antes.casos(), List.of(), antes.inicio(), antes.fin());

        String html = generar(antes, ahora);

        assertThat(html)
                .contains("Ojo al comparar")
                .contains("La aplicación cambió", "La suite cambió", "Cambió la versión");
        assertThat(html).contains("<tr class=\"cambio\"><td>Hash del JAR</td>");
        assertThat(html).contains("sha256:otro ⚠");
    }

    @Test
    void muestraLosHallazgosDeSuiteNuevosYResueltos() throws Exception {
        ResultadoSuite antes =
                resultado("s", List.of(hallazgo("VIEJO", null, Confianza.INFO, "Se fue")));
        ResultadoSuite ahora =
                resultado("s", List.of(hallazgo("NUEVO", null, Confianza.INFO, "Llegó")));

        String html = generar(antes, ahora);

        assertThat(html).contains("<h2>Hallazgos de la suite</h2>", "Se fue", "Llegó");
    }

    @Test
    void escapaLosTextosYNoUsaJavaScript() throws Exception {
        ResultadoSuite antes = resultado("s <x>", caso("<b>C</b>", Veredicto.cumple()));
        ResultadoSuite ahora =
                resultado(
                        "s <x>",
                        caso(
                                "<b>C</b>",
                                Veredicto.noCumple(
                                        List.of(
                                                hallazgo(
                                                        "<script>",
                                                        null,
                                                        Confianza.PROBABLE,
                                                        "<img src=x onerror=1>")))));

        String html = generar(antes, ahora);

        assertThat(html).doesNotContain("<script", "<b>C</b>", "<img src=x");
        assertThat(html).contains("&lt;b&gt;C&lt;/b&gt;", "&lt;img src=x onerror=1&gt;");
        assertThat(html).contains("Content-Security-Policy");
    }

    @Test
    void reemplazaElArchivoAnterior() throws Exception {
        Files.writeString(destino.resolve(ComparacionHtml.ARCHIVO), "anterior");

        String html = generar(resultado("s"), resultado("s"));

        assertThat(html).doesNotContain("anterior").contains("Ninguna de las dos ejecuciones");
    }

    @Test
    void rechazaArgumentosNulos() {
        ResultadoSuite suite = resultado("s");
        assertThatThrownBy(() -> ComparacionHtml.escribir(null, suite, destino))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ComparacionHtml.escribir(suite, null, destino))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ComparacionHtml.escribir(suite, suite, null))
                .isInstanceOf(NullPointerException.class);
    }

    private String generar(ResultadoSuite antes, ResultadoSuite ahora) throws Exception {
        ComparacionHtml.escribir(antes, ahora, destino);
        return Files.readString(destino.resolve(ComparacionHtml.ARCHIVO), StandardCharsets.UTF_8);
    }

    private static io.github.physanto.opsecengine.dominio.resultado.Hallazgo unHallazgo() {
        return hallazgo("R", "CWE-89", Confianza.CONFIRMADO, "Un fallo");
    }
}
