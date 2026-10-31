package io.github.physanto.opsecengine.cli;

import io.github.physanto.opsecengine.dominio.caso.Suite;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.nucleo.proyecto.ExcepcionConfiguracion;
import io.github.physanto.opsecengine.nucleo.proyecto.ProyectoService;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * Subcomando {@code opsec validar <proyecto.yaml>}: comprueba un proyecto de pruebas y sus suites
 * sin ejecutar nada.
 *
 * <p>Usa {@link ProyectoService} para cargar el proyecto y las suites de la carpeta {@code suites/}
 * que está junto a él, si existe. Si todo es válido escribe un resumen y termina con código 0; si
 * no, escribe los errores en la salida de error, cada uno con la ruta del campo, y termina con
 * código 1.
 */
@Command(
        name = "validar",
        description = "Comprueba proyecto.yaml y las suites de su carpeta suites/.",
        synopsisHeading = "Uso: ",
        descriptionHeading = "%n",
        parameterListHeading = "%nParámetros:%n")
public final class ComandoValidar implements Callable<Integer> {

    /** Código de salida cuando el proyecto o una suite no son válidos. */
    public static final int CODIGO_INVALIDO = 1;

    @Spec private CommandSpec spec;

    @Parameters(
            index = "0",
            paramLabel = "<proyecto.yaml>",
            description = "Archivo del proyecto de pruebas.")
    private Path archivo;

    /** Crea el comando; lo usa picocli. */
    public ComandoValidar() {}

    /**
     * Valida el proyecto y sus suites.
     *
     * @return 0 si todo es válido; {@value #CODIGO_INVALIDO} si hay errores o no se puede leer un
     *     archivo
     */
    @Override
    public Integer call() {
        PrintWriter salida = spec.commandLine().getOut();
        PrintWriter errores = spec.commandLine().getErr();
        ProyectoService servicio = new ProyectoService();
        try {
            Proyecto proyecto = servicio.cargarProyecto(archivo);
            Path carpetaSuites =
                    archivo.toAbsolutePath().getParent().resolve(ProyectoService.CARPETA_SUITES);
            List<Suite> suites = servicio.cargarSuites(carpetaSuites, proyecto);
            salida.println(
                    "Proyecto "
                            + proyecto.nombre()
                            + ": válido ("
                            + proyecto.roles().size()
                            + " roles, "
                            + proyecto.pasosLogin().size()
                            + " pasos de inicio de sesión)");
            if (!Files.isDirectory(carpetaSuites)) {
                salida.println(
                        "No hay carpeta " + ProyectoService.CARPETA_SUITES + "/ junto al proyecto");
            }
            for (Suite suite : suites) {
                salida.println(
                        "Suite "
                                + suite.nombre()
                                + ": válida ("
                                + suite.casos().size()
                                + " casos)");
            }
            salida.flush();
            return 0;
        } catch (ExcepcionConfiguracion e) {
            errores.println(e.getMessage());
        } catch (NoSuchFileException e) {
            errores.println("No existe el archivo " + e.getFile());
        } catch (IOException e) {
            errores.println("No se pudo leer " + archivo + ": " + e.getMessage());
        }
        errores.flush();
        return CODIGO_INVALIDO;
    }
}
