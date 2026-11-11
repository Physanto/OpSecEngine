package io.github.physanto.opsecengine.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.physanto.opsecengine.nucleo.VersionMotor;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class ComandoOpsecTest {

    private static final Path PROYECTO_EJEMPLO =
            Path.of("..", "contratos", "ejemplos", "proyecto.yaml");

    private final StringWriter salida = new StringWriter();
    private final StringWriter errores = new StringWriter();

    @TempDir Path carpeta;

    @Test
    void versionMuestraLaVersionDelMotorYLaDeJava() {
        int codigo = ejecutar("version");

        assertThat(codigo).isZero();
        assertThat(salida.toString())
                .contains("OpSecEngine " + VersionMotor.motor())
                .contains("Java " + System.getProperty("java.version"));
    }

    @Test
    void laOpcionVersionMuestraLoMismo() {
        assertThat(ejecutar("--version")).isZero();
        assertThat(salida.toString()).contains("OpSecEngine " + VersionMotor.motor());
    }

    @Test
    void sinSubcomandoMuestraLaAyudaEnEspanol() {
        assertThat(ejecutar()).isZero();
        assertThat(salida.toString())
                .contains("Uso: opsec")
                .contains("Comandos:")
                .contains("validar");
    }

    @Test
    void validarAceptaElProyectoDeEjemploYSusSuites() {
        int codigo = ejecutar("validar", PROYECTO_EJEMPLO.toString());

        assertThat(codigo).isZero();
        assertThat(salida.toString())
                .contains("Proyecto clientes: válido (2 roles, 5 pasos de inicio de sesión)")
                .contains("Suite login: válida (5 casos)");
        assertThat(errores.toString()).isEmpty();
    }

    @Test
    void validarUsaElSingularCuandoHayUnoSolo() throws Exception {
        Path archivo =
                Files.writeString(
                        carpeta.resolve("proyecto.yaml"),
                        "app: {jar: a.jar}\n"
                                + "roles:\n  admin: {usuario: Admin, contrasena: \"12345\"}\n"
                                + "login:\n  pasos:\n    - pulsar: Entrar\n",
                        StandardCharsets.UTF_8);
        Files.createDirectory(carpeta.resolve("suites"));
        Files.writeString(
                carpeta.resolve("suites").resolve("una.yaml"),
                "suite: una\ncasos:\n  - id: A\n    pasos:\n      - esperar: 10\n",
                StandardCharsets.UTF_8);

        assertThat(ejecutar("validar", archivo.toString())).isZero();
        assertThat(salida.toString())
                .contains("válido (1 rol, 1 paso de inicio de sesión)")
                .contains("Suite una: válida (1 caso)");
    }

    @Test
    void validarMuestraLosErroresConSuRuta() throws Exception {
        Path archivo =
                Files.writeString(
                        carpeta.resolve("proyecto.yaml"), "app: {}\n", StandardCharsets.UTF_8);

        int codigo = ejecutar("validar", archivo.toString());

        assertThat(codigo).isEqualTo(ComandoValidar.CODIGO_INVALIDO);
        assertThat(errores.toString())
                .contains("tiene 1 error")
                .contains("/app: no se encontró la propiedad requerida 'jar'");
    }

    @Test
    void validarInformaUnaSuiteInvalida() throws Exception {
        Path archivo =
                Files.writeString(
                        carpeta.resolve("proyecto.yaml"),
                        "app: {jar: a.jar}\n",
                        StandardCharsets.UTF_8);
        Files.createDirectory(carpeta.resolve("suites"));
        Files.writeString(
                carpeta.resolve("suites").resolve("mala.yaml"),
                "suite: x\n",
                StandardCharsets.UTF_8);

        assertThat(ejecutar("validar", archivo.toString()))
                .isEqualTo(ComandoValidar.CODIGO_INVALIDO);
        assertThat(errores.toString())
                .contains("mala.yaml")
                .contains("no se encontró la propiedad requerida 'casos'");
    }

    @Test
    void validarAvisaSiElArchivoNoExiste() {
        int codigo = ejecutar("validar", carpeta.resolve("no-existe.yaml").toString());

        assertThat(codigo).isEqualTo(ComandoValidar.CODIGO_INVALIDO);
        assertThat(errores.toString()).contains("No existe el archivo");
    }

    @Test
    void validarSinArchivoEsUnErrorDeUso() {
        assertThat(ejecutar("validar")).isEqualTo(CommandLine.ExitCode.USAGE);
    }

    private int ejecutar(String... argumentos) {
        CommandLine linea = ComandoOpsec.linea();
        // Sin colores: en Windows picocli los activa si detecta una terminal tipo xterm
        linea.setColorScheme(CommandLine.Help.defaultColorScheme(CommandLine.Help.Ansi.OFF));
        linea.setOut(new PrintWriter(salida, true));
        linea.setErr(new PrintWriter(errores, true));
        return linea.execute(argumentos);
    }
}
