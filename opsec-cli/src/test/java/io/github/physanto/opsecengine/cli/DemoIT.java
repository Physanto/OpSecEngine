package io.github.physanto.opsecengine.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import java.awt.GraphicsEnvironment;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

/**
 * Prueba de integración de la demo de {@code demo/}: ejecuta el jar de la CLI contra la app de
 * práctica real, con el fallo F6 activo y corregido.
 *
 * <p>Abre ventanas, así que se omite si el entorno no tiene pantalla. La ejecuta failsafe con las
 * propiedades {@code opsec.cli.jar} (jar empaquetado de la CLI) y {@code opsec.demo} (carpeta
 * {@code demo/} del repositorio). Los resultados quedan en {@code target/registros-it/}.
 */
class DemoIT {

    @Test
    void laAppVulnerableNoCumpleYLaCorregidaCumple(TestInfo prueba) throws Exception {
        assumeThat(GraphicsEnvironment.isHeadless()).as("necesita pantalla").isFalse();
        Path demo = Path.of(System.getProperty("opsec.demo"));
        Path resultados =
                Path.of("target", "registros-it", "demo-" + System.nanoTime()).toAbsolutePath();

        Ejecucion vulnerable =
                ejecutar(demo.resolve("proyecto.yaml"), resultados.resolve("vulnerable"));
        assertThat(vulnerable.codigo()).as(vulnerable.salida()).isEqualTo(3);
        assertThat(vulnerable.salida())
                .contains("LOGIN-ADMIN")
                .contains("NO_CUMPLE")
                .contains("CONTRASENA-EN-CONSOLA (CWE-532)")
                .doesNotContain("Admin#2026");
        assertThat(resultados.resolve("vulnerable").resolve("login").resolve("resultado.json"))
                .isRegularFile();

        Ejecucion corregida =
                ejecutar(demo.resolve("proyecto-corregido.yaml"), resultados.resolve("corregida"));
        assertThat(corregida.codigo()).as(corregida.salida()).isZero();
        assertThat(corregida.salida()).contains("LOGIN-ADMIN").contains("CUMPLE");
    }

    private static Ejecucion ejecutar(Path proyecto, Path resultados) throws Exception {
        Path jar = Path.of(System.getProperty("opsec.cli.jar"));
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        Files.createDirectories(resultados);
        Path registro = resultados.resolve("cli.log");
        Process proceso =
                new ProcessBuilder(
                                java.toString(),
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
            proceso.destroyForcibly();
            throw new AssertionError("La CLI no terminó en 2 minutos");
        }
        return new Ejecucion(
                proceso.exitValue(), Files.readString(registro, Charset.defaultCharset()));
    }

    /** Código de salida y lo que escribió la CLI. */
    private record Ejecucion(int codigo, String salida) {}
}
