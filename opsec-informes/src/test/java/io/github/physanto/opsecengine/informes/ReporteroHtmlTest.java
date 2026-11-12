package io.github.physanto.opsecengine.informes;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.physanto.opsecengine.dominio.extension.Reportero;
import io.github.physanto.opsecengine.dominio.resultado.Confianza;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.resultado.Manifiesto;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoCaso;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoSuite;
import io.github.physanto.opsecengine.dominio.resultado.Veredicto;
import io.github.physanto.opsecengine.dominio.traza.ContextoEvento;
import io.github.physanto.opsecengine.dominio.traza.Evento;
import io.github.physanto.opsecengine.dominio.traza.TipoEvento;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import io.github.physanto.opsecengine.soporte.DatosEjemplo;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pruebas de {@link ReporteroHtml}: contenido del informe con el resultado de ejemplo, escape del
 * texto que viene de la traza, incrustación de capturas y registro con {@link ServiceLoader}.
 */
class ReporteroHtmlTest {

    private static final Instant INICIO = Instant.parse("2026-10-30T10:00:00Z");

    private static final byte[] PNG = {
        (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0
    };

    private final ReporteroHtml reportero = new ReporteroHtml();

    @TempDir Path destino;

    @Test
    void elResultadoDeEjemploProduceUnInformeCompleto() throws Exception {
        reportero.escribir(DatosEjemplo.resultadoSuite(), destino);

        String html = leer();
        assertThat(html).startsWith("<!DOCTYPE html>");
        assertThat(html).contains("Informe de la suite ejemplo");
        assertThat(html).contains("NO CUMPLE", "CUMPLE", "NO CONCLUYENTE");
        assertThat(html).contains("SIN-SESION", "CWE-306", "CONFIRMADO");
        assertThat(html).contains("Eliminación de una persona sin iniciar sesión");
        assertThat(html).contains("DELETE FROM persona WHERE id = 3");
        assertThat(html).contains("inestable");
        assertThat(html).contains("TIEMPO_AGOTADO");
        assertThat(html).contains("Hash del JAR");
    }

    @Test
    void escribeFilaSingularOPluralSegunLasFilasAfectadas() throws Exception {
        Evento una = evento(1, TipoEvento.SQL, Map.of("sql", "DELETE FROM a", "filasAfectadas", 1));
        Evento varias =
                evento(2, TipoEvento.SQL, Map.of("sql", "DELETE FROM b", "filasAfectadas", 3));

        reportero.escribir(resultado("s", caso("C1", Veredicto.cumple(), una, varias)), destino);

        assertThat(leer()).contains("DELETE FROM a (1 fila)", "DELETE FROM b (3 filas)");
    }

    @Test
    void laEvidenciaDelHallazgoSeEnlazaYSeResaltaEnLaTraza() throws Exception {
        reportero.escribir(DatosEjemplo.resultadoSuite(), destino);

        String html = leer();
        assertThat(html).contains("<a href=\"#c0-e2\">#2</a>");
        assertThat(html).contains("<tr id=\"c0-e2\" class=\"evidencia\">");
        assertThat(html).contains("<tr id=\"c0-e1\">");
    }

    @Test
    void esAutocontenidoYSinJavaScript() throws Exception {
        reportero.escribir(DatosEjemplo.resultadoSuite(), destino);

        String html = leer();
        assertThat(html).doesNotContain("<script", "<link", "http://", "https://");
        assertThat(html).contains("Content-Security-Policy");
    }

    @Test
    void escapaElTextoQueVieneDeLaTraza() throws Exception {
        String ataque = "<script>alert(1)</script>";
        Evento consola =
                evento(
                        1,
                        TipoEvento.CONSOLA,
                        Map.of("flujo", "OUT", "texto", "<img src=x onerror=alert(1)>"));
        Hallazgo hallazgo =
                new Hallazgo(
                        "XSS-PRUEBA",
                        "CWE-79",
                        Confianza.PROBABLE,
                        ataque,
                        "a & b \"c\" 'd'",
                        List.of(1L));
        ResultadoSuite resultado =
                resultado(
                        "suite " + ataque,
                        caso("C1", Veredicto.noCumple(List.of(hallazgo)), consola));

        reportero.escribir(resultado, destino);

        String html = leer();
        assertThat(html).doesNotContain(ataque, "<img src=x");
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
        assertThat(html).contains("&lt;img src=x onerror=alert(1)&gt;");
        assertThat(html).contains("a &amp; b &quot;c&quot; &#39;d&#39;");
    }

    @Test
    void incrustaLaCapturaCuandoElArchivoExiste() throws Exception {
        Files.createDirectories(destino.resolve("capturas"));
        Files.write(destino.resolve("capturas/C1-1.png"), PNG);
        Evento captura =
                evento(
                        1,
                        TipoEvento.CAPTURA,
                        Map.of("archivo", "capturas/C1-1.png", "formato", "PNG"));

        reportero.escribir(resultado("s", caso("C1", Veredicto.cumple(), captura)), destino);

        assertThat(leer()).contains("src=\"data:image/png;base64,iVBORw0KGgo");
    }

    @Test
    void avisaCuandoLaCapturaNoExiste() throws Exception {
        Evento captura =
                evento(
                        1,
                        TipoEvento.CAPTURA,
                        Map.of("archivo", "capturas/falta.png", "formato", "PNG"));

        reportero.escribir(resultado("s", caso("C1", Veredicto.cumple(), captura)), destino);

        String html = leer();
        assertThat(html).contains("Captura no disponible: capturas/falta.png");
        assertThat(html).doesNotContain("data:image");
    }

    @Test
    void noIncrustaArchivosFueraDeLaCarpetaDeResultadosNiQueNoSeanPng() throws Exception {
        Path carpeta = Files.createDirectories(destino.resolve("resultados"));
        Files.write(destino.resolve("secreto.png"), PNG);
        Files.writeString(
                carpeta.resolve("texto.png"), "no soy una imagen", StandardCharsets.UTF_8);

        assertThat(ReporteroHtml.imagenBase64(carpeta, "../secreto.png")).isEmpty();
        assertThat(ReporteroHtml.imagenBase64(carpeta, "texto.png")).isEmpty();
        assertThat(ReporteroHtml.imagenBase64(carpeta, "")).isEmpty();
        assertThat(ReporteroHtml.imagenBase64(carpeta, null)).isEmpty();
    }

    @Test
    void unaSuiteSinFallosNoDiceQueLaAplicacionEsSegura() throws Exception {
        reportero.escribir(resultado("limpia", caso("C1", Veredicto.cumple())), destino);

        String html = leer();
        assertThat(html).contains("No se encontraron fallos en lo probado.");
        assertThat(html).contains("no significa que la aplicación sea segura");
        assertThat(html).doesNotContain("La aplicación es segura");
    }

    @Test
    void unCasoNoConcluyenteMuestraSuMotivo() throws Exception {
        ResultadoSuite resultado =
                resultado("s", caso("C1", Veredicto.noConcluyente("El agente no respondió")));

        reportero.escribir(resultado, destino);

        String html = leer();
        assertThat(html).contains("<strong>Motivo:</strong> El agente no respondió");
        assertThat(html).contains("1 caso no se pudo decidir");
    }

    @Test
    void unaSuiteSinCasosNoFalla() throws Exception {
        reportero.escribir(resultado("vacia"), destino);

        assertThat(leer()).contains("La suite no tiene casos.", "No se ejecutó ningún caso.");
    }

    @Test
    void muestraLosHallazgosDeSuite() throws Exception {
        Hallazgo hallazgo =
                new Hallazgo(
                        "ENUMERACION-USUARIOS",
                        "CWE-204",
                        Confianza.PROBABLE,
                        "Mensajes distintos",
                        "Los mensajes difieren.",
                        List.of());
        ResultadoSuite base = resultado("s", caso("C1", Veredicto.cumple()));
        ResultadoSuite resultado =
                new ResultadoSuite(
                        base.nombreSuite(),
                        base.manifiesto(),
                        base.casos(),
                        List.of(hallazgo),
                        base.inicio(),
                        base.fin());

        reportero.escribir(resultado, destino);

        assertThat(leer()).contains("Hallazgos de la suite", "ENUMERACION-USUARIOS", "CWE-204");
    }

    @Test
    void reemplazaElInformeAnterior() throws Exception {
        Files.writeString(
                destino.resolve(ReporteroHtml.ARCHIVO), "anterior", StandardCharsets.UTF_8);

        reportero.escribir(DatosEjemplo.resultadoSuite(), destino);

        assertThat(leer()).doesNotContain("anterior");
    }

    @Test
    void estaRegistradoParaServiceLoaderConElFormatoHtml() {
        assertThat(ServiceLoader.load(Reportero.class))
                .filteredOn(ReporteroHtml.class::isInstance)
                .singleElement()
                .extracting(Reportero::formato)
                .isEqualTo("html");
    }

    @Test
    void elReporteroJsonSigueRegistrado() {
        assertThat(ServiceLoader.load(Reportero.class))
                .extracting(Reportero::formato)
                .contains("json", "html");
    }

    private String leer() throws IOException {
        return Files.readString(destino.resolve(ReporteroHtml.ARCHIVO), StandardCharsets.UTF_8);
    }

    private static Evento evento(long secuencia, TipoEvento tipo, Map<String, Object> datos) {
        return new Evento(
                secuencia,
                INICIO.plusMillis(secuencia * 10),
                tipo,
                new ContextoEvento("C1", 1, null, false),
                datos);
    }

    private static ResultadoCaso caso(String id, Veredicto veredicto, Evento... eventos) {
        return new ResultadoCaso(
                id, veredicto, new Traza(id, List.of(eventos)), Duration.ofMillis(500), 1, false);
    }

    private static ResultadoSuite resultado(String nombre, ResultadoCaso... casos) {
        Manifiesto manifiesto =
                new Manifiesto("sha256:aa", "0.1.0", "21", "Linux", 42, "sha256:bb", INICIO);
        return new ResultadoSuite(
                nombre, manifiesto, List.of(casos), List.of(), INICIO, INICIO.plusSeconds(2));
    }
}
