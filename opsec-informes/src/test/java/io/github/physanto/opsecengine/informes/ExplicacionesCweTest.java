package io.github.physanto.opsecengine.informes;

import static io.github.physanto.opsecengine.informes.Resultados.caso;
import static io.github.physanto.opsecengine.informes.Resultados.hallazgo;
import static io.github.physanto.opsecengine.informes.Resultados.resultado;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
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
 * Pruebas de las explicaciones de CWE en los informes HTML y SARIF, a partir del catálogo oficial.
 */
class ExplicacionesCweTest {

    @TempDir Path destino;

    @Test
    void elHtmlExplicaElCweDeCadaHallazgoYComoCorregirlo() throws Exception {
        new ReporteroHtml().escribir(DatosEjemplo.resultadoSuite(), destino);

        String html =
                Files.readString(destino.resolve(ReporteroHtml.ARCHIVO), StandardCharsets.UTF_8);
        String nombre = CatalogoCwe.oficial().buscar("CWE-306").orElseThrow().nombre();
        assertThat(html).contains("<details class=\"cwe\"><summary>¿Qué es CWE-306 (");
        assertThat(html).contains("title=\"" + Html.escapar(nombre) + "\">CWE-306</span>");
        assertThat(html).contains("<strong>Cómo corregirlo:</strong>");
        assertThat(html)
                .contains(CatalogoCwe.oficial().buscar("CWE-306").orElseThrow().recomendacion());
        assertThat(html).as("sigue autocontenido").doesNotContain("https://", "http://");
    }

    @Test
    void unCweQueNoEstaEnElCatalogoSoloMuestraSuIdentificador() throws Exception {
        ResultadoSuite suite =
                resultado(
                        "s",
                        caso(
                                "C1",
                                Veredicto.noCumple(
                                        List.of(
                                                hallazgo("X", "CWE-99999", Confianza.PROBABLE, "t"),
                                                hallazgo("Y", null, Confianza.PROBABLE, "u")))));

        new ReporteroHtml().escribir(suite, destino);

        String html =
                Files.readString(destino.resolve(ReporteroHtml.ARCHIVO), StandardCharsets.UTF_8);
        assertThat(html).contains("<span class=\"etiqueta\">CWE-99999</span>");
        assertThat(html).doesNotContain("<details class=\"cwe\">");
    }

    @Test
    void laReglaSarifLlevaLaAyudaDelCatalogoYElEnlaceAMitre() throws Exception {
        new ReporteroSarif().escribir(DatosEjemplo.resultadoSuite(), destino);

        JsonNode regla = sarif().at("/runs/0/tool/driver/rules/0");
        assertThat(regla.get("helpUri").asText())
                .isEqualTo("https://cwe.mitre.org/data/definitions/306.html");
        assertThat(regla.at("/help/text").asText())
                .startsWith("CWE-306 (")
                .contains("Cómo corregirlo:");
        assertThat(regla.at("/help/markdown").asText())
                .startsWith("**CWE-306: ")
                .contains("**Cómo corregirlo:**")
                .contains("](https://cwe.mitre.org/data/definitions/306.html)");
    }

    @Test
    void unaReglaSinCweConocidoNoLlevaAyuda() throws Exception {
        ResultadoSuite suite =
                resultado(
                        "s",
                        caso(
                                "C1",
                                Veredicto.noCumple(
                                        List.of(
                                                hallazgo(
                                                        "X",
                                                        "CWE-99999",
                                                        Confianza.PROBABLE,
                                                        "t")))));

        new ReporteroSarif().escribir(suite, destino);

        JsonNode regla = sarif().at("/runs/0/tool/driver/rules/0");
        assertThat(regla.has("help")).isFalse();
        assertThat(regla.has("helpUri")).isFalse();
    }

    private JsonNode sarif() throws Exception {
        return JsonMapper.builder()
                .build()
                .readTree(
                        Files.readString(
                                destino.resolve(ReporteroSarif.ARCHIVO), StandardCharsets.UTF_8));
    }
}
