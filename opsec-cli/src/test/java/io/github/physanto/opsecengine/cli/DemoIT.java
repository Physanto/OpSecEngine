package io.github.physanto.opsecengine.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.awt.GraphicsEnvironment;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Humo de integración: ejecuta el jar de la CLI contra la app real, con F6 y F8 activos y
 * corregidos, y comprueba los códigos, los veredictos y la evidencia de los informes JSON.
 *
 * <p>Se omite sin pantalla en local. En la CI Linux falla si no hay pantalla, para no ocultar
 * regresiones por una configuración de Xvfb rota. La ejecuta failsafe con las propiedades {@code
 * opsec.cli.jar} (jar empaquetado de la CLI) y {@code opsec.demo} (carpeta {@code demo/} del
 * repositorio). Los resultados quedan en {@code target/registros-it/}.
 */
class DemoIT {

    @ParameterizedTest(name = "{0}: vulnerable y corregida")
    @MethodSource("fallosSembrados")
    void laAppVulnerableNoCumpleYLaCorregidaCumple(
            String fallo, Path carpeta, String suite, String caso, String oraculo, String cwe)
            throws Exception {
        if ("true".equalsIgnoreCase(System.getenv("CI"))
                && System.getProperty("os.name").startsWith("Linux")) {
            assertThat(GraphicsEnvironment.isHeadless())
                    .as("La suite de humo de la CI Linux requiere Xvfb")
                    .isFalse();
        }
        assumeThat(GraphicsEnvironment.isHeadless()).as("necesita pantalla").isFalse();
        Path resultados =
                Path.of("target", "registros-it", "humo-" + fallo + "-" + System.nanoTime())
                        .toAbsolutePath();

        Ejecucion vulnerable =
                ejecutar(carpeta.resolve("proyecto.yaml"), resultados.resolve("vulnerable"));
        assertThat(vulnerable.codigo()).as(vulnerable.salida()).isEqualTo(3);
        assertThat(vulnerable.salida())
                .contains(caso)
                .contains("NO_CUMPLE")
                .contains(oraculo)
                .doesNotContain("Admin#2026");
        comprobarInforme(resultados.resolve("vulnerable"), suite, caso, "NO_CUMPLE", oraculo, cwe);

        Ejecucion corregida =
                ejecutar(
                        carpeta.resolve("proyecto-corregido.yaml"),
                        resultados.resolve("corregida"));
        assertThat(corregida.codigo()).as(corregida.salida()).isZero();
        assertThat(corregida.salida()).contains(caso).contains("CUMPLE");
        comprobarInforme(resultados.resolve("corregida"), suite, caso, "CUMPLE", null, null);
    }

    private static Stream<Arguments> fallosSembrados() {
        Path demo = Path.of(System.getProperty("opsec.demo")).toAbsolutePath().normalize();
        Path ejemploF8 =
                demo.getParent()
                        .resolve("apps-practica")
                        .resolve("clientes-vulnerable")
                        .resolve("ejemplos")
                        .resolve("f8");
        return Stream.of(
                Arguments.of(
                        "F6", demo, "login", "LOGIN-ADMIN", "CONTRASENA-EN-CONSOLA", "CWE-532"),
                Arguments.of("F8", ejemploF8, "f8", "F8-CASO", "EXCEPCION-NO-CAPTURADA", null));
    }

    private static void comprobarInforme(
            Path resultados, String suite, String idCaso, String estado, String oraculo, String cwe)
            throws Exception {
        Path archivo = resultados.resolve(suite).resolve("resultado.json");
        assertThat(archivo).isRegularFile();
        JsonNode informe =
                JsonMapper.builder()
                        .build()
                        .readTree(Files.readString(archivo, StandardCharsets.UTF_8));
        assertThat(informe.path("nombreSuite").asText()).isEqualTo(suite);
        assertThat(informe.path("casos").size()).isEqualTo(1);
        JsonNode caso = informe.path("casos").get(0);
        assertThat(caso.path("casoId").asText()).isEqualTo(idCaso);
        JsonNode veredicto = caso.path("veredicto");
        assertThat(veredicto.path("estado").asText()).isEqualTo(estado);
        JsonNode hallazgos = veredicto.path("hallazgos");
        assertThat(hallazgos.isArray()).isTrue();
        if (oraculo == null) {
            assertThat(hallazgos.size()).isZero();
            assertThat(veredicto.path("motivo").isNull()).isTrue();
            return;
        }
        assertThat(hallazgos.size()).isPositive();
        List<Long> secuencias = new ArrayList<>();
        for (JsonNode evento : caso.path("traza").path("eventos")) {
            secuencias.add(evento.path("secuencia").asLong());
        }
        for (JsonNode hallazgo : hallazgos) {
            assertThat(hallazgo.path("oraculoId").asText()).isEqualTo(oraculo);
            if (cwe == null) {
                assertThat(hallazgo.path("cwe").isNull()).isTrue();
            } else {
                assertThat(hallazgo.path("cwe").asText()).isEqualTo(cwe);
            }
            assertThat(hallazgo.path("evidencia").size()).isPositive();
            for (JsonNode evidencia : hallazgo.path("evidencia")) {
                assertThat(secuencias).contains(evidencia.asLong());
            }
        }
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
                                "--resultados",
                                resultados.toString())
                        .redirectErrorStream(true)
                        .redirectOutput(registro.toFile())
                        .start();
        if (!proceso.waitFor(2, TimeUnit.MINUTES)) {
            proceso.descendants().forEach(ProcessHandle::destroyForcibly);
            proceso.destroyForcibly();
            throw new AssertionError("La CLI no terminó en 2 minutos");
        }
        return new Ejecucion(
                proceso.exitValue(), Files.readString(registro, StandardCharsets.UTF_8));
    }

    /** Código de salida y lo que escribió la CLI. */
    private record Ejecucion(int codigo, String salida) {}
}
