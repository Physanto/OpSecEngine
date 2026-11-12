package io.github.physanto.opsecengine.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import java.awt.GraphicsEnvironment;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Prueba de integración de {@code opsec verificar}: ejecuta el jar de la CLI contra la app de
 * práctica real de {@code demo/} y comprueba que el agente se conecta.
 *
 * <p>Abre la aplicación unos segundos, así que se omite si el entorno no tiene pantalla. La ejecuta
 * failsafe con las propiedades {@code opsec.cli.jar} y {@code opsec.demo}. La salida queda en
 * {@code target/registros-it/}.
 */
class VerificarIT {

    @Test
    void laAppDeLaDemoSeConectaConElAgente() throws Exception {
        assumeThat(GraphicsEnvironment.isHeadless()).as("necesita pantalla").isFalse();
        Path demo = Path.of(System.getProperty("opsec.demo"));

        Ejecucion ejecucion = ejecutar(demo.resolve("proyecto.yaml"), "verificar-ok");

        assertThat(ejecucion.codigo()).as(ejecucion.salida()).isZero();
        assertThat(ejecucion.salida())
                .contains("[OK] Proyecto demo-clientes-vulnerable: válido")
                .contains("[OK] Suite login: válida")
                .contains("[OK] Conexión: el agente saludó")
                .contains("Verificación completa: todo listo para ejecutar.");
    }

    @Test
    void sinElJarDeLaAplicacionNoSeAbreNada() throws Exception {
        Path carpeta = Path.of("target", "registros-it", "verificar-sin-jar-" + System.nanoTime());
        Files.createDirectories(carpeta);
        Path proyecto =
                Files.writeString(
                        carpeta.resolve("proyecto.yaml"), "nombre: x\napp: {jar: falta.jar}\n");

        Ejecucion ejecucion = ejecutar(proyecto, "verificar-sin-jar");

        assertThat(ejecucion.codigo()).as(ejecucion.salida()).isEqualTo(1);
        assertThat(ejecucion.salida()).contains("[FALLO] No existe el JAR de la aplicación");
    }

    private static Ejecucion ejecutar(Path proyecto, String nombre) throws Exception {
        Path jar = Path.of(System.getProperty("opsec.cli.jar"));
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        Path carpeta = Path.of("target", "registros-it", nombre + "-" + System.nanoTime());
        Files.createDirectories(carpeta);
        Path registro = carpeta.resolve("cli.log");
        Process proceso =
                new ProcessBuilder(
                                java.toString(),
                                "-jar",
                                jar.toString(),
                                "verificar",
                                proyecto.toString())
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
