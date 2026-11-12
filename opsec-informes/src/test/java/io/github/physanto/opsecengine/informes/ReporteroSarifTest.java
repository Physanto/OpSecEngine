package io.github.physanto.opsecengine.informes;

import static io.github.physanto.opsecengine.informes.Resultados.caso;
import static io.github.physanto.opsecengine.informes.Resultados.hallazgo;
import static io.github.physanto.opsecengine.informes.Resultados.resultado;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.physanto.opsecengine.dominio.extension.Reportero;
import io.github.physanto.opsecengine.dominio.resultado.Confianza;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoSuite;
import io.github.physanto.opsecengine.dominio.resultado.Veredicto;
import io.github.physanto.opsecengine.soporte.DatosEjemplo;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pruebas de {@link ReporteroSarif}: contenido con el resultado de ejemplo, correspondencia entre
 * confianza y nivel, coherencia entre reglas y resultados, y una comprobación estructural de las
 * propiedades que SARIF 2.1.0 exige.
 *
 * <p>No valida contra el esquema JSON oficial de SARIF: ese archivo no está en el repositorio. La
 * comprobación de {@link #exigeLoObligatorioDeSarif(JsonNode)} cubre las propiedades obligatorias y
 * los valores permitidos que el esquema define para lo que este reportero escribe.
 */
class ReporteroSarifTest {

    private static final JsonMapper LECTOR = JsonMapper.builder().build();

    private final ReporteroSarif reportero = new ReporteroSarif();

    @TempDir Path destino;

    @Test
    void elResultadoDeEjemploProduceUnSarifCompleto() throws Exception {
        reportero.escribir(DatosEjemplo.resultadoSuite(), destino);

        JsonNode sarif = leer();
        assertThat(sarif.get("version").asText()).isEqualTo("2.1.0");
        assertThat(sarif.get("$schema").asText()).endsWith("sarif-schema-2.1.0.json");
        JsonNode ejecucion = sarif.get("runs").get(0);
        assertThat(ejecucion.at("/tool/driver/name").asText()).isEqualTo("OpSecEngine");
        assertThat(ejecucion.at("/tool/driver/version").asText()).isEqualTo("0.0.1-SNAPSHOT");

        JsonNode reglas = ejecucion.at("/tool/driver/rules");
        assertThat(reglas).hasSize(1);
        assertThat(reglas.get(0).get("id").asText()).isEqualTo("SIN-SESION");
        assertThat(texto(reglas.get(0).at("/properties/tags")))
                .containsExactly("security", "external/cwe/cwe-306");

        JsonNode resultados = ejecucion.get("results");
        assertThat(resultados).hasSize(1);
        JsonNode r = resultados.get(0);
        assertThat(r.get("ruleId").asText()).isEqualTo("SIN-SESION");
        assertThat(r.get("ruleIndex").asInt()).isZero();
        assertThat(r.get("level").asText()).isEqualTo("error");
        assertThat(r.at("/message/text").asText())
                .contains("Eliminación de una persona sin iniciar sesión")
                .contains("La sentencia DELETE se ejecutó sin una sesión iniciada");
        assertThat(r.at("/properties/cwe").asText()).isEqualTo("CWE-306");
        assertThat(r.at("/properties/confianza").asText()).isEqualTo("CONFIRMADO");
        assertThat(r.at("/properties/caso").asText()).isEqualTo("N3");
        assertThat(texto(r.at("/properties/eventosDeEvidencia"))).containsExactly("2", "3", "5");
    }

    @Test
    void laUbicacionEsLaSuiteYElCasoComoUbicacionLogica() throws Exception {
        reportero.escribir(DatosEjemplo.resultadoSuite(), destino);

        JsonNode ubicacion = leer().at("/runs/0/results/0/locations/0");
        assertThat(ubicacion.at("/physicalLocation/artifactLocation/uri").asText())
                .isEqualTo("suites/ejemplo.yaml");
        assertThat(ubicacion.at("/physicalLocation/region/startLine").asInt()).isEqualTo(1);
        assertThat(ubicacion.at("/logicalLocations/0/name").asText()).isEqualTo("N3");
        assertThat(ubicacion.at("/logicalLocations/0/fullyQualifiedName").asText())
                .isEqualTo("ejemplo/N3");
    }

    @Test
    void unCasoNoConcluyenteEsUnaNotificacionYNoUnResultado() throws Exception {
        reportero.escribir(DatosEjemplo.resultadoSuite(), destino);

        JsonNode invocacion = leer().at("/runs/0/invocations/0");
        assertThat(invocacion.get("executionSuccessful").asBoolean()).isTrue();
        assertThat(invocacion.get("startTimeUtc").asText()).isEqualTo("2026-10-30T10:00:00Z");
        assertThat(invocacion.get("endTimeUtc").asText()).isEqualTo("2026-10-30T10:00:20Z");
        JsonNode avisos = invocacion.get("toolExecutionNotifications");
        assertThat(avisos).hasSize(1);
        assertThat(avisos.get(0).get("level").asText()).isEqualTo("warning");
        assertThat(avisos.get(0).at("/message/text").asText())
                .contains("C15", "no fue concluyente", "TIEMPO_AGOTADO");
    }

    @Test
    void laConfianzaDeterminaElNivel() throws Exception {
        Veredicto veredicto =
                Veredicto.noCumple(
                        List.of(
                                hallazgo("R1", "CWE-89", Confianza.CONFIRMADO, "Uno"),
                                hallazgo("R2", "CWE-204", Confianza.PROBABLE, "Dos"),
                                hallazgo("R3", null, Confianza.INFO, "Tres")));

        reportero.escribir(resultado("s", caso("C1", veredicto)), destino);

        JsonNode resultados = leer().at("/runs/0/results");
        assertThat(resultados.get(0).get("level").asText()).isEqualTo("error");
        assertThat(resultados.get(1).get("level").asText()).isEqualTo("warning");
        assertThat(resultados.get(2).get("level").asText()).isEqualTo("note");
        assertThat(resultados.get(2).at("/properties").has("cwe")).isFalse();
    }

    @Test
    void cadaHallazgoApuntaASuReglaYLasReglasNoSeRepiten() throws Exception {
        Hallazgo a1 = hallazgo("REGLA-A", "CWE-89", Confianza.CONFIRMADO, "A en C1");
        Hallazgo b = hallazgo("REGLA-B", "CWE-209", Confianza.PROBABLE, "B en C1");
        Hallazgo a2 = hallazgo("REGLA-A", "CWE-89", Confianza.CONFIRMADO, "A en C2");
        ResultadoSuite suite =
                resultado(
                        "s",
                        List.of(hallazgo("REGLA-C", null, Confianza.PROBABLE, "C de suite")),
                        caso("C1", Veredicto.noCumple(List.of(a1, b))),
                        caso("C2", Veredicto.noCumple(List.of(a2))));

        reportero.escribir(suite, destino);

        JsonNode ejecucion = leer().at("/runs/0");
        JsonNode reglas = ejecucion.at("/tool/driver/rules");
        assertThat(ids(reglas)).containsExactly("REGLA-A", "REGLA-B", "REGLA-C");
        JsonNode resultados = ejecucion.get("results");
        assertThat(resultados).hasSize(4);
        for (JsonNode r : resultados) {
            assertThat(reglas.get(r.get("ruleIndex").asInt()).get("id").asText())
                    .isEqualTo(r.get("ruleId").asText());
        }
        JsonNode deSuite = resultados.get(3);
        assertThat(deSuite.at("/locations/0/logicalLocations/0/name").asText()).isEqualTo("s");
        assertThat(deSuite.at("/properties").has("caso")).isFalse();
    }

    @Test
    void laHuellaEsEstableYDistingueCasos() throws Exception {
        Hallazgo h = hallazgo("R", "CWE-89", Confianza.CONFIRMADO, "Mismo");
        ResultadoSuite suite =
                resultado(
                        "s",
                        caso("C1", Veredicto.noCumple(List.of(h))),
                        caso("C2", Veredicto.noCumple(List.of(h))));

        reportero.escribir(suite, destino);
        JsonNode primera = leer().at("/runs/0/results");
        reportero.escribir(suite, destino);
        JsonNode segunda = leer().at("/runs/0/results");

        String huellaC1 = primera.get(0).at("/partialFingerprints/opsecengine~1v1").asText();
        String huellaC2 = primera.get(1).at("/partialFingerprints/opsecengine~1v1").asText();
        assertThat(huellaC1).matches("[0-9a-f]{64}").isNotEqualTo(huellaC2);
        assertThat(segunda.get(0).at("/partialFingerprints/opsecengine~1v1").asText())
                .isEqualTo(huellaC1);
    }

    @Test
    void conservaTildesYEscapaLosCaracteresEspecialesDeJson() throws Exception {
        Hallazgo h =
                new Hallazgo(
                        "R",
                        "CWE-79",
                        Confianza.PROBABLE,
                        "Inyección \"<script>\" & ñandú",
                        "línea 1\nlínea 2 \\ fin",
                        List.of());

        reportero.escribir(
                resultado("sesión ñ", caso("C1", Veredicto.noCumple(List.of(h)))), destino);

        assertThat(
                        Files.readString(
                                destino.resolve(ReporteroSarif.ARCHIVO), StandardCharsets.UTF_8))
                .contains("ñandú", "sesión ñ");
        assertThat(leer().at("/runs/0/results/0/message/text").asText())
                .isEqualTo("Inyección \"<script>\" & ñandú. línea 1\nlínea 2 \\ fin");
    }

    @Test
    void unaSuiteSinHallazgosTieneResultadosYReglasVacios() throws Exception {
        reportero.escribir(resultado("limpia", caso("C1", Veredicto.cumple())), destino);

        JsonNode ejecucion = leer().at("/runs/0");
        assertThat(ejecucion.get("results")).isEmpty();
        assertThat(ejecucion.at("/tool/driver/rules")).isEmpty();
        assertThat(ejecucion.at("/invocations/0").has("toolExecutionNotifications")).isFalse();
    }

    @Test
    void elDocumentoCumpleLoObligatorioDeSarif() throws Exception {
        Hallazgo h = hallazgo("R", "CWE-89", Confianza.CONFIRMADO, "T");
        reportero.escribir(
                resultado(
                        "s",
                        List.of(hallazgo("S", null, Confianza.INFO, "De suite")),
                        caso("C1", Veredicto.noCumple(List.of(h))),
                        caso("C2", Veredicto.noConcluyente("sin respuesta"))),
                destino);

        exigeLoObligatorioDeSarif(leer());
        reportero.escribir(DatosEjemplo.resultadoSuite(), destino);
        exigeLoObligatorioDeSarif(leer());
    }

    @Test
    void reemplazaElArchivoAnterior() throws Exception {
        Files.writeString(destino.resolve(ReporteroSarif.ARCHIVO), "anterior");

        reportero.escribir(DatosEjemplo.resultadoSuite(), destino);

        assertThat(leer().get("version").asText()).isEqualTo("2.1.0");
    }

    @Test
    void estaRegistradoParaServiceLoaderConElFormatoSarif() {
        assertThat(ServiceLoader.load(Reportero.class))
                .filteredOn(ReporteroSarif.class::isInstance)
                .singleElement()
                .extracting(Reportero::formato)
                .isEqualTo("sarif");
    }

    /**
     * Comprueba las propiedades que el esquema de SARIF 2.1.0 marca como obligatorias y los valores
     * permitidos de {@code level} para todo lo que escribe el reportero.
     */
    private static void exigeLoObligatorioDeSarif(JsonNode sarif) {
        assertThat(sarif.get("version").asText()).isEqualTo("2.1.0");
        assertThat(sarif.get("runs").isArray()).isTrue();
        assertThat(sarif.get("runs")).isNotEmpty();
        for (JsonNode ejecucion : sarif.get("runs")) {
            assertThat(ejecucion.at("/tool/driver/name").asText()).isNotBlank();
            JsonNode reglas = ejecucion.at("/tool/driver/rules");
            List<String> idsDeReglas = ids(reglas);
            assertThat(idsDeReglas).doesNotHaveDuplicates();
            for (JsonNode regla : reglas) {
                assertThat(regla.get("id").asText()).isNotBlank();
                assertThat(regla.at("/shortDescription/text").asText()).isNotBlank();
            }
            for (JsonNode r : ejecucion.get("results")) {
                assertThat(r.at("/message/text").asText()).isNotBlank();
                assertThat(Set.of("none", "note", "warning", "error"))
                        .contains(r.get("level").asText());
                assertThat(idsDeReglas).contains(r.get("ruleId").asText());
                assertThat(r.get("locations").isArray()).isTrue();
                assertThat(r.get("locations")).isNotEmpty();
                assertThat(r.at("/locations/0/physicalLocation/artifactLocation/uri").asText())
                        .isNotBlank();
                assertThat(r.at("/locations/0/physicalLocation/region/startLine").asInt())
                        .isGreaterThanOrEqualTo(1);
            }
            for (JsonNode aviso :
                    ejecucion.at("/invocations/0").path("toolExecutionNotifications")) {
                assertThat(aviso.at("/message/text").asText()).isNotBlank();
                assertThat(Set.of("none", "note", "warning", "error"))
                        .contains(aviso.get("level").asText());
            }
            assertThat(ejecucion.at("/invocations/0/executionSuccessful").isBoolean()).isTrue();
        }
    }

    private JsonNode leer() throws Exception {
        return LECTOR.readTree(
                Files.readString(destino.resolve(ReporteroSarif.ARCHIVO), StandardCharsets.UTF_8));
    }

    private static List<String> ids(JsonNode reglas) {
        List<String> ids = new ArrayList<>();
        reglas.forEach(regla -> ids.add(regla.get("id").asText()));
        return ids;
    }

    private static List<String> texto(JsonNode lista) {
        List<String> valores = new ArrayList<>();
        lista.forEach(valor -> valores.add(valor.asText()));
        return valores;
    }
}
