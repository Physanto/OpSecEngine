package io.github.physanto.opsecengine.cli;

import io.github.physanto.opsecengine.nucleo.VersionMotor;
import java.io.PrintWriter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * Subcomando {@code opsec version}: muestra la versión del motor y la de Java que lo ejecuta.
 *
 * <p>Ejemplo de salida:
 *
 * <pre>
 * OpSecEngine 0.0.1-SNAPSHOT
 * Java 21.0.9 (Eclipse Adoptium) en Linux 6.8.0 (amd64)
 * </pre>
 */
@Command(
        name = "version",
        description = "Muestra la versión del motor y la de Java.",
        synopsisHeading = "Uso: ",
        descriptionHeading = "%n")
public final class ComandoVersion implements Runnable {

    @Spec private CommandSpec spec;

    /** Crea el comando; lo usa picocli. */
    public ComandoVersion() {}

    /**
     * Devuelve las líneas que muestra el comando.
     *
     * @return la versión del motor y la de Java, una por línea
     */
    public static String[] lineas() {
        return new String[] {
            "OpSecEngine " + VersionMotor.motor(),
            "Java "
                    + VersionMotor.java()
                    + " ("
                    + System.getProperty("java.vendor")
                    + ") en "
                    + VersionMotor.sistemaOperativo()
        };
    }

    /** Escribe la versión en la salida del comando. */
    @Override
    public void run() {
        PrintWriter salida = spec.commandLine().getOut();
        for (String linea : lineas()) {
            salida.println(linea);
        }
        salida.flush();
    }
}
