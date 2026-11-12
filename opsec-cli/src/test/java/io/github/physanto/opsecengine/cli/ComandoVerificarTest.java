package io.github.physanto.opsecengine.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.physanto.opsecengine.nucleo.sesion.LanzadorProceso;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * Pruebas de {@code opsec verificar} que no abren ninguna aplicación: errores de configuración,
 * jars que faltan y una conexión que falla. La conexión correcta con el agente real está en {@link
 * VerificarIT}.
 */
class ComandoVerificarTest {

    private final StringWriter salida = new StringWriter();
    private final StringWriter errores = new StringWriter();

    @TempDir Path carpeta;

    @Test
    void unProyectoQueNoExisteTerminaConError() {
        int codigo = verificar(carpeta.resolve("no-existe.yaml"), null, noDebeArrancar());

        assertThat(codigo).isEqualTo(ComandoVerificar.CODIGO_INVALIDO);
        assertThat(errores.toString()).contains("[FALLO] No existe el archivo");
    }

    @Test
    void unProyectoInvalidoTerminaConErrorYNoArranca() throws Exception {
        Path proyecto = escribir("proyecto.yaml", "app: {}\n");

        int codigo = verificar(proyecto, null, noDebeArrancar());

        assertThat(codigo).isEqualTo(ComandoVerificar.CODIGO_INVALIDO);
        assertThat(errores.toString()).startsWith("[FALLO]").contains("'jar'");
    }

    @Test
    void unaSuiteInvalidaTerminaConError() throws Exception {
        Path proyecto = proyectoConJar();
        Files.createDirectories(carpeta.resolve("suites"));
        escribir("suites/mala.yaml", "suite: mala\ncasos: 3\n");

        int codigo = verificar(proyecto, null, noDebeArrancar());

        assertThat(codigo).isEqualTo(ComandoVerificar.CODIGO_INVALIDO);
        assertThat(errores.toString()).startsWith("[FALLO]").contains("mala.yaml");
    }

    @Test
    void siFaltaElJarDeLaAplicacionAvisaSinArrancar() throws Exception {
        Path proyecto = escribir("proyecto.yaml", "nombre: demo\napp: {jar: falta.jar}\n");

        int codigo = verificar(proyecto, null, noDebeArrancar());

        assertThat(codigo).isEqualTo(ComandoVerificar.CODIGO_INVALIDO);
        assertThat(salida.toString()).contains("[OK] Proyecto demo: válido");
        assertThat(errores.toString())
                .contains("[FALLO] No existe el JAR de la aplicación")
                .contains("falta.jar")
                .contains("¿falta compilarla?");
    }

    @Test
    void siElAgenteIndicadoNoExisteTerminaConError() throws Exception {
        Path proyecto = proyectoConJar();

        int codigo = verificar(proyecto, carpeta.resolve("no-esta.jar"), noDebeArrancar());

        assertThat(codigo).isEqualTo(ComandoVerificar.CODIGO_INVALIDO);
        assertThat(salida.toString()).contains("[OK] Aplicación:");
        assertThat(errores.toString())
                .contains("[FALLO] No existe el archivo")
                .contains("no-esta.jar");
    }

    @Test
    void siNoHaySuitesAvisaPeroContinua() throws Exception {
        Path proyecto = proyectoConJar();
        Path agente = escribir("agente.jar", "x");

        int codigo = verificar(proyecto, agente, (jar, registros) -> fallaAlArrancar("boom"));

        assertThat(codigo).isEqualTo(ComandoVerificar.CODIGO_SIN_CONEXION);
        assertThat(salida.toString()).contains("[AVISO] No hay suites en suites/");
    }

    @Test
    void siLaAplicacionNoArrancaDiceElMotivoYDondeEstanLosRegistros() throws Exception {
        Path proyecto = proyectoConJar();
        Path agente = escribir("agente.jar", "x");
        AtomicReference<Path> registros = new AtomicReference<>();

        int codigo =
                verificar(
                        proyecto,
                        agente,
                        (jar, carpetaRegistros) -> {
                            registros.set(carpetaRegistros);
                            return fallaAlArrancar("No arrancó");
                        });

        try {
            assertThat(codigo).isEqualTo(ComandoVerificar.CODIGO_SIN_CONEXION);
            assertThat(salida.toString())
                    .contains("[OK] Agente: ")
                    .contains("Probando la conexión con el agente")
                    .doesNotContain("Verificación completa");
            assertThat(errores.toString())
                    .contains("[FALLO] La aplicación no se conectó con el agente: No arrancó")
                    .contains("Registros completos en " + registros.get());
            assertThat(registros.get()).isDirectory();
        } finally {
            borrar(registros.get());
        }
    }

    @Test
    void alFallarLaConexionMuestraElFinalDeLaSalidaDeErrorDeLaAplicacion() throws Exception {
        Path proyecto = proyectoConJar();
        Path agente = escribir("agente.jar", "x");
        AtomicReference<Path> registros = new AtomicReference<>();

        int codigo =
                verificar(
                        proyecto,
                        agente,
                        (jar, carpetaRegistros) -> {
                            registros.set(carpetaRegistros);
                            return (p, puerto, token) -> {
                                Files.writeString(
                                        carpetaRegistros.resolve(LanzadorProceso.ARCHIVO_ERRORES),
                                        "Exception in thread main\n\n"
                                                + "java.lang.NoClassDefFoundError: Falta\n",
                                        StandardCharsets.UTF_8);
                                throw new IOException("La aplicación terminó");
                            };
                        });

        try {
            assertThat(codigo).isEqualTo(ComandoVerificar.CODIGO_SIN_CONEXION);
            assertThat(errores.toString())
                    .contains("| Exception in thread main")
                    .contains("| java.lang.NoClassDefFoundError: Falta");
        } finally {
            borrar(registros.get());
        }
    }

    @Test
    void ultimasLineasConservaSoloLasDelFinalYOmiteLasVacias() throws Exception {
        StringBuilder texto = new StringBuilder();
        for (int i = 1; i <= 12; i++) {
            texto.append("linea ").append(i).append("\n\n");
        }
        Path registro =
                Files.writeString(carpeta.resolve("errores.log"), texto, StandardCharsets.UTF_8);

        List<String> lineas = ComandoVerificar.ultimasLineas(registro);

        assertThat(lineas).hasSize(ComandoVerificar.LINEAS_DE_ERROR);
        assertThat(lineas.get(0)).isEqualTo("linea 5");
        assertThat(lineas.get(lineas.size() - 1)).isEqualTo("linea 12");
    }

    @Test
    void ultimasLineasDeUnArchivoQueNoExisteEsVacio() {
        assertThat(ComandoVerificar.ultimasLineas(carpeta.resolve("no-existe.log"))).isEmpty();
    }

    @Test
    void elComandoEstaRegistradoYMuestraSuAyuda() {
        CommandLine linea = ComandoOpsec.linea();

        assertThat(linea.getSubcommands()).containsKey("verificar");
        assertThat(
                        linea.getSubcommands()
                                .get("verificar")
                                .getUsageMessage(CommandLine.Help.Ansi.OFF))
                .contains("<proyecto.yaml>")
                .contains("--agente=<jar>");
    }

    @Test
    void conLaCliUnProyectoQueNoExisteDaCodigoUnoYSinArgumentosDaCodigoDos() {
        assertThat(ejecutarCli("verificar", carpeta.resolve("no-existe.yaml").toString()))
                .isEqualTo(ComandoVerificar.CODIGO_INVALIDO);
        assertThat(ejecutarCli("verificar")).isEqualTo(2);
    }

    private int verificar(Path proyecto, Path agente, ComandoVerificar.FabricaArranque fabrica) {
        return ComandoVerificar.verificar(
                proyecto,
                agente,
                new PrintWriter(salida, true),
                new PrintWriter(errores, true),
                fabrica);
    }

    private int ejecutarCli(String... argumentos) {
        CommandLine linea = ComandoOpsec.linea();
        linea.setOut(new PrintWriter(salida, true));
        linea.setErr(new PrintWriter(errores, true));
        return linea.execute(argumentos);
    }

    private static ComandoVerificar.FabricaArranque noDebeArrancar() {
        return (jar, registros) -> {
            throw new AssertionError("No debía arrancar la aplicación");
        };
    }

    private static io.github.physanto.opsecengine.nucleo.sesion.ArranqueApp fallaAlArrancar(
            String mensaje) {
        return (proyecto, puerto, token) -> {
            throw new IOException(mensaje);
        };
    }

    /** Escribe un proyecto válido cuyo JAR de aplicación existe (vacío, no se ejecuta). */
    private Path proyectoConJar() throws IOException {
        escribir("app.jar", "x");
        return escribir("proyecto.yaml", "nombre: demo\napp: {jar: app.jar}\n");
    }

    private Path escribir(String nombre, String contenido) throws IOException {
        Path destino = carpeta.resolve(nombre);
        Files.createDirectories(destino.getParent());
        return Files.writeString(destino, contenido, StandardCharsets.UTF_8);
    }

    private static void borrar(Path registros) throws IOException {
        if (registros == null) {
            return;
        }
        try (var archivos = Files.list(registros)) {
            for (Path archivo : archivos.toList()) {
                Files.deleteIfExists(archivo);
            }
        }
        Files.deleteIfExists(registros);
    }
}
