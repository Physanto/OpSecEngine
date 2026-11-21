package io.github.physanto.opsecengine.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.awt.GraphicsEnvironment;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Integración v0.1: ejecuta la suite SEC-001 de {@code demo/sec-001/} con el jar de la CLI y el
 * agente real, sobre la app de práctica vulnerable y corregida, y comprueba los veredictos, los
 * oráculos que los deciden y el informe HTML.
 *
 * <p>Se omite sin pantalla en local; en la CI Linux falla si no hay pantalla. Usa las mismas
 * propiedades que {@link DemoIT}. Los resultados quedan en {@code target/registros-it/}.
 */
class Sec001IT {

    @Test
    void laAppVulnerableNoCumpleYLaCorregidaCumple() throws Exception {
        if ("true".equalsIgnoreCase(System.getenv("CI"))
                && System.getProperty("os.name").startsWith("Linux")) {
            assertThat(GraphicsEnvironment.isHeadless())
                    .as("La suite de humo de la CI Linux requiere Xvfb")
                    .isFalse();
        }
        assumeThat(GraphicsEnvironment.isHeadless()).as("necesita pantalla").isFalse();
        Path carpeta =
                Path.of(System.getProperty("opsec.demo"))
                        .toAbsolutePath()
                        .normalize()
                        .resolve("sec-001");
        Path resultados =
                Path.of("target", "registros-it", "sec-001-" + System.nanoTime()).toAbsolutePath();

        Path vulnerable = resultados.resolve("vulnerable");
        Ejecucion ejecucion = ejecutar(carpeta.resolve("proyecto.yaml"), vulnerable);
        assertThat(ejecucion.codigo()).as(ejecucion.salida()).isEqualTo(3);
        Map<String, JsonNode> casos = leerCasos(vulnerable);
        assertThat(casos).hasSize(8);
        assertThat(oraculos(casos.get("C04"))).contains("TRAZA-EN-DIALOGO");
        assertThat(oraculos(casos.get("C07"))).contains("CONTRASENA-EN-CONSOLA");
        assertThat(oraculos(casos.get("C13"))).contains("SQLI-CANARIO");
        assertThat(oraculosSuite(vulnerable)).contains("ENUMERACION-USUARIOS");
        assertThat(ejecucion.salida()).doesNotContain("Admin#2026");
        comprobarHtml(vulnerable);

        Path corregida = resultados.resolve("corregida");
        ejecucion = ejecutar(carpeta.resolve("proyecto-corregido.yaml"), corregida);
        assertThat(ejecucion.codigo()).as(ejecucion.salida()).isZero();
        assertThat(oraculosSuite(corregida)).isEmpty();
        for (JsonNode caso : leerCasos(corregida).values()) {
            assertThat(caso.path("veredicto").path("estado").asText())
                    .as(caso.path("casoId").asText())
                    .isEqualTo("CUMPLE");
        }
        comprobarHtml(corregida);
    }

    private static Map<String, JsonNode> leerCasos(Path resultados) throws Exception {
        Path archivo = resultados.resolve("sec-001").resolve("resultado.json");
        assertThat(archivo).isRegularFile();
        JsonNode informe =
                JsonMapper.builder()
                        .build()
                        .readTree(Files.readString(archivo, StandardCharsets.UTF_8));
        Map<String, JsonNode> casos = new HashMap<>();
        for (JsonNode caso : informe.path("casos")) {
            casos.put(caso.path("casoId").asText(), caso);
        }
        return casos;
    }

    private static Set<String> oraculosSuite(Path resultados) throws Exception {
        Path archivo = resultados.resolve("sec-001").resolve("resultado.json");
        JsonNode informe =
                JsonMapper.builder()
                        .build()
                        .readTree(Files.readString(archivo, StandardCharsets.UTF_8));
        Set<String> ids = new HashSet<>();
        for (JsonNode hallazgo : informe.path("hallazgosSuite")) {
            ids.add(hallazgo.path("oraculoId").asText());
        }
        return ids;
    }

    private static Set<String> oraculos(JsonNode caso) {
        Set<String> ids = new HashSet<>();
        for (JsonNode hallazgo : caso.path("veredicto").path("hallazgos")) {
            ids.add(hallazgo.path("oraculoId").asText());
        }
        return ids;
    }

    private static void comprobarHtml(Path resultados) throws Exception {
        Path html = resultados.resolve("sec-001").resolve("informe.html");
        assertThat(html).isRegularFile();
        assertThat(Files.readString(html, StandardCharsets.UTF_8)).contains("C01").contains("C07");
    }

    private static Ejecucion ejecutar(Path proyecto, Path resultados) throws Exception {
        Path jar = Path.of(System.getProperty("opsec.cli.jar"));
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        Files.createDirectories(resultados);
        Path registro = resultados.resolve("cli.log");
        Process proceso =
                new ProcessBuilder(
                                java.toString(),
                                "-Dfile.encoding=UTF-8",
                                "-jar",
                                jar.toString(),
                                "ejecutar",
                                proyecto.toString(),
                                "--formato",
                                "html,json",
                                "--resultados",
                                resultados.toString())
                        .redirectErrorStream(true)
                        .redirectOutput(registro.toFile())
                        .start();
        if (!proceso.waitFor(4, TimeUnit.MINUTES)) {
            proceso.descendants().forEach(ProcessHandle::destroyForcibly);
            proceso.destroyForcibly();
            throw new AssertionError("La CLI no terminó en 4 minutos");
        }
        return new Ejecucion(
                proceso.exitValue(), Files.readString(registro, StandardCharsets.UTF_8));
    }

    /** Código de salida y lo que escribió la CLI. */
    private record Ejecucion(int codigo, String salida) {}
}
