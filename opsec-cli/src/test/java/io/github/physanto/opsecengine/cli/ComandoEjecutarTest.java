package io.github.physanto.opsecengine.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * Pruebas de {@code opsec ejecutar} que no lanzan la aplicación: errores de configuración y el jar
 * del agente que trae la CLI. La ejecución de punta a punta está en las pruebas de integración.
 */
class ComandoEjecutarTest {

    private final StringWriter salida = new StringWriter();
    private final StringWriter errores = new StringWriter();

    @TempDir Path carpeta;

    @Test
    void sinSuitesTerminaConError() throws Exception {
        Path proyecto = escribir("proyecto.yaml", "app: {jar: app.jar}\n");

        assertThat(ejecutar("ejecutar", proyecto.toString()))
                .isEqualTo(ComandoEjecutar.CODIGO_ERROR);
        assertThat(errores.toString()).contains("No hay suites que ejecutar");
    }

    @Test
    void unProyectoInvalidoTerminaConError() throws Exception {
        Path proyecto = escribir("proyecto.yaml", "app: {}\n");

        assertThat(ejecutar("ejecutar", proyecto.toString()))
                .isEqualTo(ComandoEjecutar.CODIGO_ERROR);
        assertThat(errores.toString()).contains("'jar'");
    }

    @Test
    void unAgenteQueNoExisteTerminaConError() throws Exception {
        Path proyecto = escribir("proyecto.yaml", "app: {jar: app.jar}\n");
        Path suite =
                escribir(
                        "suite.yaml",
                        "suite: s\ncasos:\n  - id: A\n    pasos:\n      - esperar: 10\n");

        int codigo =
                ejecutar(
                        "ejecutar",
                        proyecto.toString(),
                        "--suite",
                        suite.toString(),
                        "--agente",
                        carpeta.resolve("no-existe.jar").toString());

        assertThat(codigo).isEqualTo(ComandoEjecutar.CODIGO_ERROR);
        assertThat(errores.toString()).contains("No existe el archivo").contains("no-existe.jar");
    }

    @Test
    void laCliTraeElJarDelAgente() throws Exception {
        Path jar = UbicadorAgente.ubicar(null);

        try (JarFile agente = new JarFile(jar.toFile())) {
            assertThat(agente.getManifest().getMainAttributes().getValue("Premain-Class"))
                    .endsWith(".agente.AgenteMain");
        }
    }

    @Test
    void laAyudaMuestraElComando() {
        assertThat(ejecutar()).isZero();
        assertThat(salida.toString()).contains("ejecutar");
    }

    private Path escribir(String nombre, String contenido) throws Exception {
        return Files.writeString(carpeta.resolve(nombre), contenido, StandardCharsets.UTF_8);
    }

    private int ejecutar(String... argumentos) {
        CommandLine linea = ComandoOpsec.linea();
        linea.setColorScheme(CommandLine.Help.defaultColorScheme(CommandLine.Help.Ansi.OFF));
        linea.setOut(new PrintWriter(salida, true));
        linea.setErr(new PrintWriter(errores, true));
        return linea.execute(argumentos);
    }
}
