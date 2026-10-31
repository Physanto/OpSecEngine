package io.github.physanto.opsecengine.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.physanto.opsecengine.nucleo.VersionMotor;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Prueba de integración: ejecuta el jar con dependencias en otra JVM, como lo haría una persona.
 *
 * <p>La ejecuta failsafe después de empaquetar; la ruta del jar llega en la propiedad {@code
 * opsec.cli.jar}.
 */
class JarEjecutableIT {

    @Test
    void elJarEjecutaElComandoVersion() throws Exception {
        Path jar = Path.of(System.getProperty("opsec.cli.jar"));
        assertThat(jar).isRegularFile();
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");

        Process proceso =
                new ProcessBuilder(java.toString(), "-jar", jar.toString(), "version")
                        .redirectErrorStream(true)
                        .start();
        String salida =
                new String(proceso.getInputStream().readAllBytes(), Charset.defaultCharset());

        assertThat(proceso.waitFor(30, TimeUnit.SECONDS)).isTrue();
        assertThat(proceso.exitValue()).isZero();
        assertThat(salida)
                .contains("OpSecEngine " + VersionMotor.motor())
                .contains("Java ")
                .doesNotContain("SLF4J");
    }

    @Test
    void elJarValidaElProyectoDeEjemplo() throws Exception {
        Path jar = Path.of(System.getProperty("opsec.cli.jar"));
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        Path proyecto = Path.of("..", "contratos", "ejemplos", "proyecto.yaml");

        Process proceso =
                new ProcessBuilder(
                                java.toString(),
                                "-jar",
                                jar.toString(),
                                "validar",
                                proyecto.toString())
                        .redirectErrorStream(true)
                        .start();
        String salida =
                new String(proceso.getInputStream().readAllBytes(), Charset.defaultCharset());

        assertThat(proceso.waitFor(30, TimeUnit.SECONDS)).isTrue();
        assertThat(proceso.exitValue()).as(salida).isZero();
        assertThat(salida).contains("Suite login");
    }
}
