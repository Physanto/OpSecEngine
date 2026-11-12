package io.github.physanto.opsecengine.cli;

import io.github.physanto.opsecengine.dominio.caso.Suite;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.nucleo.proyecto.ExcepcionConfiguracion;
import io.github.physanto.opsecengine.nucleo.proyecto.ProyectoService;
import io.github.physanto.opsecengine.nucleo.sesion.ArranqueApp;
import io.github.physanto.opsecengine.nucleo.sesion.LanzadorProceso;
import io.github.physanto.opsecengine.nucleo.sesion.SesionService;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * Subcomando {@code opsec verificar <proyecto.yaml>}: comprueba, sin ejecutar ningún caso, que todo
 * está listo para ejecutar.
 *
 * <p>Hace las comprobaciones en este orden y se detiene en la primera que falla:
 *
 * <ol>
 *   <li>El proyecto y las suites de su carpeta {@code suites/} son válidos.
 *   <li>El JAR de la aplicación existe.
 *   <li>Se encuentra el jar del agente (el que trae la CLI o el indicado con {@code --agente}).
 *   <li>El agente se conecta: se arranca la aplicación con el agente, se espera su saludo y se
 *       cierra. Esta comprobación abre la aplicación unos segundos.
 * </ol>
 *
 * <p>Cada comprobación escribe una línea que empieza con {@code [OK]}, {@code [AVISO]} o {@code
 * [FALLO]}. Si la conexión falla, se muestran las últimas líneas de la salida de error de la
 * aplicación y se conservan sus registros completos en una carpeta temporal cuya ruta se indica.
 *
 * <p>Códigos de salida: 0 si todo está listo; {@value #CODIGO_INVALIDO} si el proyecto, una suite,
 * el JAR de la aplicación o el jar del agente no son válidos o no se pueden leer; {@value
 * #CODIGO_SIN_CONEXION} si la aplicación no arranca o el agente no saluda a tiempo; 2 si los
 * argumentos no son válidos (lo decide picocli).
 *
 * <p>Mientras no exista una autoverificación en el núcleo, este comando solo prueba lo anterior; no
 * comprueba que los localizadores de las suites existan en la aplicación.
 */
@Command(
        name = "verificar",
        description =
                "Comprueba, sin ejecutar casos, que el proyecto, la aplicación y el agente"
                        + " están listos. Abre la aplicación unos segundos.",
        synopsisHeading = "Uso: ",
        descriptionHeading = "%n",
        parameterListHeading = "%nParámetros:%n",
        optionListHeading = "%nOpciones:%n")
public final class ComandoVerificar implements Callable<Integer> {

    /** Código de salida cuando el proyecto, una suite o un jar no son válidos o no existen. */
    public static final int CODIGO_INVALIDO = 1;

    /** Código de salida cuando la aplicación no arranca o el agente no saluda a tiempo. */
    public static final int CODIGO_SIN_CONEXION = 5;

    /** Líneas del final de la salida de error de la aplicación que se muestran al fallar. */
    static final int LINEAS_DE_ERROR = 8;

    @Spec private CommandSpec spec;

    @Parameters(
            index = "0",
            paramLabel = "<proyecto.yaml>",
            description = "Archivo del proyecto de pruebas.")
    private Path archivo;

    @Option(
            names = "--agente",
            paramLabel = "<jar>",
            description = "Jar del agente a usar en lugar del que trae la CLI.")
    private Path agente;

    /** Crea el comando; lo usa picocli. */
    public ComandoVerificar() {}

    /**
     * Crea el arranque de la aplicación que se usa para probar la conexión.
     *
     * <p>La CLI usa {@link LanzadorProceso}; las pruebas pueden pasar otro arranque.
     */
    @FunctionalInterface
    interface FabricaArranque {

        /**
         * Crea el arranque.
         *
         * @param jarAgente jar del agente; nunca es null
         * @param carpetaRegistros carpeta, que ya existe, donde se escribe la salida de la
         *     aplicación; nunca es null
         * @return el arranque de la aplicación
         */
        ArranqueApp crear(Path jarAgente, Path carpetaRegistros);
    }

    /**
     * Verifica el proyecto y muestra el resultado de cada comprobación.
     *
     * @return el código de salida (ver la descripción de la clase)
     */
    @Override
    public Integer call() {
        return verificar(
                archivo,
                agente,
                spec.commandLine().getOut(),
                spec.commandLine().getErr(),
                LanzadorProceso::new);
    }

    /**
     * Ejecuta las comprobaciones.
     *
     * @param archivo archivo del proyecto; no puede ser null
     * @param agente jar del agente; si es null se usa el que trae la CLI
     * @param salida donde se escriben las comprobaciones correctas y los avisos
     * @param errores donde se escriben los fallos
     * @param fabrica crea el arranque de la aplicación para la prueba de conexión
     * @return el código de salida (ver la descripción de la clase)
     */
    static int verificar(
            Path archivo,
            Path agente,
            PrintWriter salida,
            PrintWriter errores,
            FabricaArranque fabrica) {
        try {
            salida.println("Verificando " + archivo);
            Proyecto proyecto = verificarConfiguracion(archivo, salida);
            if (!Files.isRegularFile(proyecto.jar())) {
                return fallo(
                        errores,
                        CODIGO_INVALIDO,
                        "No existe el JAR de la aplicación: "
                                + proyecto.jar()
                                + " (¿falta compilarla?)");
            }
            salida.println("[OK] Aplicación: " + proyecto.jar());
            Path jarAgente = UbicadorAgente.ubicar(agente);
            salida.println("[OK] Agente: " + (agente == null ? "el que trae la CLI" : jarAgente));
            return verificarConexion(proyecto, jarAgente, salida, errores, fabrica);
        } catch (ExcepcionConfiguracion e) {
            return fallo(errores, CODIGO_INVALIDO, e.getMessage());
        } catch (NoSuchFileException e) {
            return fallo(errores, CODIGO_INVALIDO, "No existe el archivo " + e.getFile());
        } catch (IOException e) {
            return fallo(errores, CODIGO_INVALIDO, "No se pudo verificar: " + e.getMessage());
        } finally {
            salida.flush();
            errores.flush();
        }
    }

    /** Carga el proyecto y las suites y escribe una línea por cada uno. */
    private static Proyecto verificarConfiguracion(Path archivo, PrintWriter salida)
            throws IOException, ExcepcionConfiguracion {
        ProyectoService servicio = new ProyectoService();
        Proyecto proyecto = servicio.cargarProyecto(archivo);
        salida.println(
                "[OK] Proyecto "
                        + proyecto.nombre()
                        + ": válido ("
                        + contar(proyecto.roles().size(), "rol", "roles")
                        + ", "
                        + contar(proyecto.pasosLogin().size(), "paso", "pasos")
                        + " de inicio de sesión)");
        Path carpetaSuites =
                archivo.toAbsolutePath().getParent().resolve(ProyectoService.CARPETA_SUITES);
        List<Suite> suites = servicio.cargarSuites(carpetaSuites, proyecto);
        if (suites.isEmpty()) {
            salida.println(
                    "[AVISO] No hay suites en "
                            + ProyectoService.CARPETA_SUITES
                            + "/ junto al proyecto");
        }
        for (Suite suite : suites) {
            salida.println(
                    "[OK] Suite "
                            + suite.nombre()
                            + ": válida ("
                            + contar(suite.casos().size(), "caso", "casos")
                            + ")");
        }
        return proyecto;
    }

    /** Arranca la aplicación con el agente, espera el saludo y la cierra. */
    private static int verificarConexion(
            Proyecto proyecto,
            Path jarAgente,
            PrintWriter salida,
            PrintWriter errores,
            FabricaArranque fabrica)
            throws IOException {
        Path registros = Files.createTempDirectory("opsec-verificar-");
        salida.println(
                "Probando la conexión con el agente (espera hasta "
                        + SesionService.ESPERA_HOLA_POR_DEFECTO.toSeconds()
                        + " s)...");
        salida.flush();
        try (SesionService sesiones = new SesionService(fabrica.crear(jarAgente, registros))) {
            sesiones.abrir(proyecto);
            salida.println(
                    "[OK] Conexión: el agente saludó (agente "
                            + sesiones.saludo().getVersionAgente()
                            + ", Java "
                            + sesiones.saludo().getVersionJava()
                            + ")");
        } catch (IOException e) {
            fallo(
                    errores,
                    CODIGO_SIN_CONEXION,
                    "La aplicación no se conectó con el agente: " + e.getMessage());
            mostrarSalidaDeError(errores, registros);
            errores.println("       Registros completos en " + registros);
            return CODIGO_SIN_CONEXION;
        }
        limpiar(registros);
        salida.println("Verificación completa: todo listo para ejecutar.");
        return 0;
    }

    /** Muestra el final de la salida de error de la aplicación, si hay algo que mostrar. */
    private static void mostrarSalidaDeError(PrintWriter errores, Path registros) {
        for (String linea : ultimasLineas(registros.resolve(LanzadorProceso.ARCHIVO_ERRORES))) {
            errores.println("       | " + linea);
        }
    }

    /**
     * Lee en UTF-8 las últimas líneas de un archivo de texto.
     *
     * @return hasta {@value #LINEAS_DE_ERROR} líneas no vacías del final, o una lista vacía si el
     *     archivo no existe o no se puede leer
     */
    static List<String> ultimasLineas(Path archivo) {
        try {
            List<String> lineas =
                    Files.readAllLines(archivo, StandardCharsets.UTF_8).stream()
                            .filter(linea -> !linea.isBlank())
                            .toList();
            return lineas.subList(Math.max(0, lineas.size() - LINEAS_DE_ERROR), lineas.size());
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }

    /** Borra los registros de una verificación correcta; ignora lo que no se pueda borrar. */
    private static void limpiar(Path registros) {
        for (String nombre :
                List.of(LanzadorProceso.ARCHIVO_SALIDA, LanzadorProceso.ARCHIVO_ERRORES)) {
            try {
                Files.deleteIfExists(registros.resolve(nombre));
            } catch (IOException e) {
                return;
            }
        }
        try {
            Files.deleteIfExists(registros);
        } catch (IOException e) {
            // Una carpeta temporal que queda no afecta al resultado.
        }
    }

    private static int fallo(PrintWriter errores, int codigo, String mensaje) {
        errores.println("[FALLO] " + mensaje);
        return codigo;
    }

    /** Escribe una cantidad con el sustantivo en singular o plural, por ejemplo "1 caso". */
    private static String contar(int cantidad, String singular, String plural) {
        return cantidad + " " + (cantidad == 1 ? singular : plural);
    }
}
