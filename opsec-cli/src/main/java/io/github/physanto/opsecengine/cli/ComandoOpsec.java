package io.github.physanto.opsecengine.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Comando raíz {@code opsec} de la línea de comandos.
 *
 * <p>Sin subcomando muestra la ayuda. Subcomandos: {@code version} ({@link ComandoVersion}), {@code
 * validar} ({@link ComandoValidar}) y {@code ejecutar} ({@link ComandoEjecutar}). Códigos de
 * salida: 0 si todo salió bien, 1 si el comando encontró errores (por ejemplo, un proyecto
 * inválido), 2 si los argumentos no son válidos, y en {@code ejecutar}, 3 si algún caso no cumple y
 * 4 si alguno quedó no concluyente.
 *
 * <p>Ejemplo:
 *
 * <pre>{@code
 * java -jar opsec-cli/target/opsec-<versión>.jar version
 * java -jar opsec-cli/target/opsec-<versión>.jar validar pruebas/proyecto.yaml
 * java -jar opsec-cli/target/opsec-<versión>.jar ejecutar pruebas/proyecto.yaml
 * }</pre>
 */
@Command(
        name = "opsec",
        description =
                "OpSecEngine: motor libre de pruebas GUI y de seguridad para aplicaciones Java Swing.",
        subcommands = {ComandoVersion.class, ComandoValidar.class, ComandoEjecutar.class},
        versionProvider = ComandoOpsec.Version.class,
        synopsisHeading = "Uso: ",
        descriptionHeading = "%n",
        parameterListHeading = "%nParámetros:%n",
        optionListHeading = "%nOpciones:%n",
        commandListHeading = "%nComandos:%n")
public final class ComandoOpsec implements Runnable {

    @Spec private CommandSpec spec;

    @Option(
            names = {"-h", "--help"},
            usageHelp = true,
            description = "Muestra esta ayuda y termina.")
    private boolean ayuda;

    @Option(
            names = {"-V", "--version"},
            versionHelp = true,
            description = "Muestra la versión del motor y termina.")
    private boolean version;

    /** Crea el comando; lo usa picocli. */
    public ComandoOpsec() {}

    /**
     * Punto de entrada de la línea de comandos.
     *
     * @param argumentos argumentos de la línea de comandos
     */
    public static void main(String[] argumentos) {
        System.exit(linea().execute(argumentos));
    }

    /**
     * Crea la línea de comandos con el comando raíz y sus subcomandos.
     *
     * @return la línea de comandos lista para {@link CommandLine#execute(String...)}
     */
    public static CommandLine linea() {
        return new CommandLine(new ComandoOpsec());
    }

    /** Sin subcomando, muestra la ayuda. */
    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }

    /** Versión que muestra la opción {@code --version}. */
    static final class Version implements CommandLine.IVersionProvider {

        @Override
        public String[] getVersion() {
            return ComandoVersion.lineas();
        }
    }
}
