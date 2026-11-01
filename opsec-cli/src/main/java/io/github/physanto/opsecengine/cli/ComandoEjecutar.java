package io.github.physanto.opsecengine.cli;

import io.github.physanto.opsecengine.dominio.caso.Suite;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.dominio.resultado.EstadoVeredicto;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoCaso;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoSuite;
import io.github.physanto.opsecengine.nucleo.ejecucion.EjecucionService;
import io.github.physanto.opsecengine.nucleo.evaluacion.EvaluacionService;
import io.github.physanto.opsecengine.nucleo.extensiones.RegistroExtensiones;
import io.github.physanto.opsecengine.nucleo.informe.InformeService;
import io.github.physanto.opsecengine.nucleo.proyecto.ExcepcionConfiguracion;
import io.github.physanto.opsecengine.nucleo.proyecto.ProyectoService;
import io.github.physanto.opsecengine.nucleo.sesion.LanzadorProceso;
import io.github.physanto.opsecengine.nucleo.sesion.SesionService;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * Subcomando {@code opsec ejecutar <proyecto.yaml>}: ejecuta suites contra la aplicación probada y
 * escribe los resultados.
 *
 * <p>Carga el proyecto y las suites (las indicadas con {@code --suite} o, si no hay, todas las de
 * la carpeta {@code suites/} junto al proyecto). Cada suite se ejecuta con {@link
 * EjecucionService}, lanzando la aplicación con el agente que trae la CLI, y su resultado se
 * escribe con los reporteros registrados en {@code <resultados>/<suite>/}. Por defecto {@code
 * <resultados>} es {@code resultados/<AAAA-MM-DD_HHmmss>} junto al proyecto. En la consola se
 * muestra el veredicto de cada caso y sus hallazgos.
 *
 * <p>Códigos de salida: 0 si todos los casos cumplen; {@value #CODIGO_ERROR} si el proyecto o una
 * suite no son válidos o no se pudo ejecutar; {@value #CODIGO_NO_CUMPLE} si algún caso no cumple;
 * {@value #CODIGO_NO_CONCLUYENTE} si ninguno falla pero alguno quedó no concluyente.
 */
@Command(
        name = "ejecutar",
        description =
                "Ejecuta las suites de un proyecto contra la aplicación y escribe los resultados.",
        synopsisHeading = "Uso: ",
        descriptionHeading = "%n",
        parameterListHeading = "%nParámetros:%n",
        optionListHeading = "%nOpciones:%n")
public final class ComandoEjecutar implements Callable<Integer> {

    /** Código de salida cuando el proyecto o una suite no son válidos, o algo impide ejecutar. */
    public static final int CODIGO_ERROR = 1;

    /** Código de salida cuando algún caso no cumple. */
    public static final int CODIGO_NO_CUMPLE = 3;

    /** Código de salida cuando ningún caso falla pero alguno quedó no concluyente. */
    public static final int CODIGO_NO_CONCLUYENTE = 4;

    /** Formatos de informe que se generan. */
    static final List<String> FORMATOS = List.of("json");

    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss", Locale.ROOT);

    @Spec private CommandSpec spec;

    @Parameters(
            index = "0",
            paramLabel = "<proyecto.yaml>",
            description = "Archivo del proyecto de pruebas.")
    private Path archivo;

    @Option(
            names = {"-s", "--suite"},
            paramLabel = "<suite.yaml>",
            description = "Suite a ejecutar; se puede repetir. Por defecto, todas las de suites/.")
    private List<Path> suites = new ArrayList<>();

    @Option(
            names = {"-r", "--resultados"},
            paramLabel = "<carpeta>",
            description =
                    "Carpeta de resultados. Por defecto, resultados/<fecha> junto al proyecto.")
    private Path resultados;

    @Option(
            names = "--agente",
            paramLabel = "<jar>",
            description = "Jar del agente a usar en lugar del que trae la CLI.")
    private Path agente;

    /** Crea el comando; lo usa picocli. */
    public ComandoEjecutar() {}

    /**
     * Ejecuta las suites y muestra el resumen.
     *
     * @return el código de salida según los veredictos (ver la descripción de la clase)
     */
    @Override
    public Integer call() {
        PrintWriter salida = spec.commandLine().getOut();
        PrintWriter errores = spec.commandLine().getErr();
        try {
            return ejecutar(salida);
        } catch (ExcepcionConfiguracion e) {
            errores.println(e.getMessage());
        } catch (NoSuchFileException e) {
            errores.println("No existe el archivo " + e.getFile());
        } catch (IOException e) {
            errores.println("No se pudo ejecutar: " + e.getMessage());
        } finally {
            salida.flush();
        }
        errores.flush();
        return CODIGO_ERROR;
    }

    private int ejecutar(PrintWriter salida) throws IOException, ExcepcionConfiguracion {
        ProyectoService servicio = new ProyectoService();
        Proyecto proyecto = servicio.cargarProyecto(archivo);
        Path carpetaProyecto = archivo.toAbsolutePath().getParent();
        List<Suite> cargadas = new ArrayList<>();
        if (suites.isEmpty()) {
            cargadas.addAll(
                    servicio.cargarSuites(
                            carpetaProyecto.resolve(ProyectoService.CARPETA_SUITES), proyecto));
        } else {
            for (Path suite : suites) {
                cargadas.add(servicio.cargarSuite(suite, proyecto));
            }
        }
        if (cargadas.isEmpty()) {
            throw new IOException(
                    "No hay suites que ejecutar: use --suite o cree la carpeta "
                            + ProyectoService.CARPETA_SUITES
                            + "/ junto al proyecto");
        }
        Path jarAgente = UbicadorAgente.ubicar(agente);
        Path carpetaResultados =
                resultados != null
                        ? resultados
                        : carpetaProyecto
                                .resolve("resultados")
                                .resolve(LocalDateTime.now().format(FECHA));
        RegistroExtensiones registro = RegistroExtensiones.descubrir();
        Resumen resumen = new Resumen();
        for (Suite suite : cargadas) {
            Path carpetaSuite = carpetaResultados.resolve(suite.nombre());
            ResultadoSuite resultado;
            try (SesionService sesiones =
                    new SesionService(new LanzadorProceso(jarAgente, carpetaSuite))) {
                resultado =
                        new EjecucionService(proyecto, sesiones, new EvaluacionService(registro))
                                .ejecutar(suite);
            }
            new InformeService(registro).generar(resultado, carpetaSuite, FORMATOS);
            mostrar(salida, resultado, carpetaSuite, resumen);
        }
        salida.println();
        salida.println(
                "Resumen: "
                        + resumen.cumplen
                        + " cumplen, "
                        + resumen.noCumplen
                        + " no cumplen, "
                        + resumen.noConcluyentes
                        + " no concluyentes");
        if (resumen.noCumplen > 0) {
            return CODIGO_NO_CUMPLE;
        }
        return resumen.noConcluyentes > 0 ? CODIGO_NO_CONCLUYENTE : 0;
    }

    private static void mostrar(
            PrintWriter salida, ResultadoSuite resultado, Path carpeta, Resumen resumen) {
        salida.println();
        salida.println(
                "Suite "
                        + resultado.nombreSuite()
                        + " ("
                        + resultado.casos().size()
                        + (resultado.casos().size() == 1 ? " caso" : " casos")
                        + "); resultados en "
                        + carpeta);
        for (ResultadoCaso caso : resultado.casos()) {
            EstadoVeredicto estado = caso.veredicto().estado();
            resumen.contar(estado);
            salida.printf(
                    Locale.ROOT,
                    "  %-16s %-15s %.1f s%n",
                    caso.casoId(),
                    estado,
                    caso.duracion().toMillis() / 1000.0);
            if (caso.veredicto().motivo() != null) {
                salida.println("      Motivo: " + caso.veredicto().motivo());
            }
            for (Hallazgo hallazgo : caso.veredicto().hallazgos()) {
                mostrar(salida, hallazgo);
            }
        }
        for (Hallazgo hallazgo : resultado.hallazgosSuite()) {
            mostrar(salida, hallazgo);
        }
    }

    private static void mostrar(PrintWriter salida, Hallazgo hallazgo) {
        salida.println(
                "      - "
                        + hallazgo.oraculoId()
                        + (hallazgo.cwe() == null ? "" : " (" + hallazgo.cwe() + ")")
                        + ": "
                        + hallazgo.titulo());
        salida.println("        " + hallazgo.explicacion());
    }

    /** Cuenta de casos por veredicto. */
    private static final class Resumen {

        private int cumplen;
        private int noCumplen;
        private int noConcluyentes;

        void contar(EstadoVeredicto estado) {
            switch (estado) {
                case CUMPLE -> cumplen++;
                case NO_CUMPLE -> noCumplen++;
                case NO_CONCLUYENTE -> noConcluyentes++;
            }
        }
    }
}
