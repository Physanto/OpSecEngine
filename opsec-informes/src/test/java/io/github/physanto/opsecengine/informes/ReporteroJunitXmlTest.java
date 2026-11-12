package io.github.physanto.opsecengine.informes;

import static io.github.physanto.opsecengine.informes.Resultados.caso;
import static io.github.physanto.opsecengine.informes.Resultados.casoInestable;
import static io.github.physanto.opsecengine.informes.Resultados.hallazgo;
import static io.github.physanto.opsecengine.informes.Resultados.resultado;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.physanto.opsecengine.dominio.extension.Reportero;
import io.github.physanto.opsecengine.dominio.resultado.Confianza;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.resultado.Veredicto;
import io.github.physanto.opsecengine.soporte.DatosEjemplo;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Pruebas de {@link ReporteroJunitXml}: se lee el XML escrito con un analizador estándar para
 * comprobar que es válido y que cada caso y cada hallazgo quedan donde las herramientas de CI los
 * esperan.
 */
class ReporteroJunitXmlTest {

    private final ReporteroJunitXml reportero = new ReporteroJunitXml();

    @TempDir Path destino;

    @Test
    void elResultadoDeEjemploProduceUnXmlValidoConLosConteosCorrectos() throws Exception {
        reportero.escribir(DatosEjemplo.resultadoSuite(), destino);

        Document xml = leer();
        Element suites = xml.getDocumentElement();
        assertThat(suites.getTagName()).isEqualTo("testsuites");
        Element suite = (Element) suites.getElementsByTagName("testsuite").item(0);
        assertThat(suite.getAttribute("name")).isEqualTo("ejemplo");
        assertThat(suite.getAttribute("tests")).isEqualTo("3");
        assertThat(suite.getAttribute("failures")).isEqualTo("1");
        assertThat(suite.getAttribute("errors")).isEqualTo("0");
        assertThat(suite.getAttribute("skipped")).isEqualTo("1");
        assertThat(suite.getAttribute("time")).isEqualTo("20.000");
        assertThat(suite.getAttribute("timestamp")).isEqualTo("2026-10-30T10:00:00");
        assertThat(suites.getAttribute("tests")).isEqualTo("3");
        assertThat(xml.getElementsByTagName("testcase").getLength()).isEqualTo(3);
    }

    @Test
    void unCasoQueNoCumpleLlevaUnFailurePorHallazgoConSuCwe() throws Exception {
        reportero.escribir(DatosEjemplo.resultadoSuite(), destino);

        Element n3 = casoDe(leer(), "N3");
        Element fallo = (Element) n3.getElementsByTagName("failure").item(0);
        assertThat(n3.getAttribute("classname")).isEqualTo("ejemplo");
        assertThat(n3.getAttribute("time")).isEqualTo("2.800");
        assertThat(fallo.getAttribute("message"))
                .isEqualTo("Eliminación de una persona sin iniciar sesión");
        assertThat(fallo.getAttribute("type")).isEqualTo("CWE-306");
        assertThat(fallo.getTextContent())
                .contains("Oráculo: SIN-SESION", "Confianza: CONFIRMADO", "Eventos de evidencia");
    }

    @Test
    void unCasoNoConcluyenteSeOmiteConSuMotivoYUnoQueCumpleNoLlevaNada() throws Exception {
        reportero.escribir(DatosEjemplo.resultadoSuite(), destino);

        Document xml = leer();
        Element c15 = casoDe(xml, "C15");
        assertThat(c15.getElementsByTagName("skipped").getLength()).isEqualTo(1);
        assertThat(((Element) c15.getElementsByTagName("skipped").item(0)).getAttribute("message"))
                .contains("TIEMPO_AGOTADO");
        Element a1 = casoDe(xml, "A1");
        assertThat(a1.getElementsByTagName("failure").getLength()).isZero();
        assertThat(a1.getElementsByTagName("skipped").getLength()).isZero();
        assertThat(a1.getElementsByTagName("system-out").item(0).getTextContent())
                .contains("Inestable", "2 intentos");
    }

    @Test
    void variosHallazgosDanVariosFailureEnElMismoCaso() throws Exception {
        Veredicto veredicto =
                Veredicto.noCumple(
                        List.of(
                                hallazgo("A", "CWE-89", Confianza.CONFIRMADO, "Primero"),
                                hallazgo("B", null, Confianza.PROBABLE, "Segundo")));

        reportero.escribir(resultado("s", caso("C1", veredicto)), destino);

        NodeList fallos = casoDe(leer(), "C1").getElementsByTagName("failure");
        assertThat(fallos.getLength()).isEqualTo(2);
        assertThat(((Element) fallos.item(0)).getAttribute("type")).isEqualTo("CWE-89");
        assertThat(((Element) fallos.item(1)).getAttribute("type")).isEqualTo("B");
        assertThat(leer().getDocumentElement().getAttribute("failures")).isEqualTo("1");
    }

    @Test
    void losHallazgosDeSuiteSonCasosAparteQueFallan() throws Exception {
        Hallazgo deSuite = hallazgo("ENUMERACION", "CWE-204", Confianza.PROBABLE, "Mensajes");

        reportero.escribir(
                resultado("s", List.of(deSuite), caso("C1", Veredicto.cumple())), destino);

        Document xml = leer();
        Element suite = (Element) xml.getElementsByTagName("testsuite").item(0);
        assertThat(suite.getAttribute("tests")).isEqualTo("2");
        assertThat(suite.getAttribute("failures")).isEqualTo("1");
        Element deLaSuite = casoDe(xml, "(suite) ENUMERACION");
        assertThat(
                        ((Element) deLaSuite.getElementsByTagName("failure").item(0))
                                .getAttribute("type"))
                .isEqualTo("CWE-204");
    }

    @Test
    void unHallazgoInformativoDeUnCasoQueCumpleVaEnSystemOut() throws Exception {
        Veredicto veredicto =
                new Veredicto(
                        io.github.physanto.opsecengine.dominio.resultado.EstadoVeredicto.CUMPLE,
                        List.of(hallazgo("INFO-1", null, Confianza.INFO, "Observación")),
                        null);

        reportero.escribir(resultado("s", caso("C1", veredicto)), destino);

        Element c1 = casoDe(leer(), "C1");
        assertThat(c1.getElementsByTagName("failure").getLength()).isZero();
        assertThat(c1.getElementsByTagName("system-out").item(0).getTextContent())
                .contains("Observación", "INFO-1");
    }

    @Test
    void escapaLosTextosYDescartaLosCaracteresQueXmlNoAdmite() throws Exception {
        String titulo = "<b>\"x\" & 'y'</b> con nulo \u0000 y control \u0001";
        Veredicto veredicto =
                Veredicto.noCumple(List.of(hallazgo("XSS", "CWE-79", Confianza.PROBABLE, titulo)));

        reportero.escribir(resultado("suite <&>", caso("C&1", veredicto)), destino);

        String texto = Files.readString(destino.resolve(ReporteroJunitXml.ARCHIVO));
        assertThat(texto).doesNotContain("\u0000", "\u0001", "<b>");
        Document xml = leer();
        Element suite = (Element) xml.getElementsByTagName("testsuite").item(0);
        assertThat(suite.getAttribute("name")).isEqualTo("suite <&>");
        Element fallo = (Element) casoDe(xml, "C&1").getElementsByTagName("failure").item(0);
        assertThat(fallo.getAttribute("message"))
                .isEqualTo("<b>\"x\" & 'y'</b> con nulo  y control ");
    }

    @Test
    void unaSuiteSinCasosEsUnXmlValido() throws Exception {
        reportero.escribir(resultado("vacia"), destino);

        Element suite = (Element) leer().getElementsByTagName("testsuite").item(0);
        assertThat(suite.getAttribute("tests")).isEqualTo("0");
    }

    @Test
    void reemplazaElArchivoAnteriorYEscribeUtf8() throws Exception {
        Files.writeString(destino.resolve(ReporteroJunitXml.ARCHIVO), "anterior");

        reportero.escribir(DatosEjemplo.resultadoSuite(), destino);

        String texto =
                Files.readString(
                        destino.resolve(ReporteroJunitXml.ARCHIVO), StandardCharsets.UTF_8);
        assertThat(texto).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        assertThat(texto).doesNotContain("anterior").contains("Eliminación");
    }

    @Test
    void casoInestableSinHallazgosTambienDejaSuNota() throws Exception {
        reportero.escribir(resultado("s", casoInestable("C1", Veredicto.cumple(), 3)), destino);

        assertThat(casoDe(leer(), "C1").getElementsByTagName("system-out").item(0).getTextContent())
                .contains("3 intentos");
    }

    @Test
    void estaRegistradoParaServiceLoaderConElFormatoJunit() {
        assertThat(ServiceLoader.load(Reportero.class))
                .filteredOn(ReporteroJunitXml.class::isInstance)
                .singleElement()
                .extracting(Reportero::formato)
                .isEqualTo("junit");
    }

    private Document leer() throws Exception {
        String texto =
                Files.readString(
                        destino.resolve(ReporteroJunitXml.ARCHIVO), StandardCharsets.UTF_8);
        DocumentBuilderFactory fabrica = DocumentBuilderFactory.newInstance();
        fabrica.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        fabrica.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return fabrica.newDocumentBuilder().parse(new InputSource(new StringReader(texto)));
    }

    private static Element casoDe(Document xml, String nombre) {
        NodeList casos = xml.getElementsByTagName("testcase");
        for (int i = 0; i < casos.getLength(); i++) {
            Element caso = (Element) casos.item(i);
            if (nombre.equals(caso.getAttribute("name"))) {
                return caso;
            }
        }
        throw new AssertionError("No hay un testcase llamado " + nombre);
    }
}
