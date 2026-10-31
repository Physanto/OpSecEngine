package io.github.physanto.opsecengine.nucleo.sesion;

import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Lanza la aplicación probada en otra JVM con el agente del motor.
 *
 * <p>El comando es {@code java [argumentosJvm] -javaagent:<jar del agente>=puerto=<p>,token=<t>}
 * seguido de {@code -cp <jar> <clase principal>} si el proyecto indica la clase principal, o de
 * {@code -jar <jar>} si no (la clase sale entonces del manifiesto del JAR). Por defecto usa el
 * {@code java} de la JVM que ejecuta el motor. Las rutas se arman con {@link Path} y el classpath
 * se une con {@link File#pathSeparator}, así que el comando sirve en Windows, macOS y Linux sin
 * pasar por una consola del sistema.
 *
 * <p>La salida estándar y la de error de la aplicación se agregan al final de {@value
 * #ARCHIVO_SALIDA} y {@value #ARCHIVO_ERRORES} en la carpeta de resultados, que se crea si no
 * existe.
 *
 * <p>Es inmutable y seguro entre hilos.
 */
public final class LanzadorProceso implements ArranqueApp {

    /** Archivo de la carpeta de resultados que recibe la salida estándar de la aplicación. */
    public static final String ARCHIVO_SALIDA = "app-salida.log";

    /** Archivo de la carpeta de resultados que recibe la salida de error de la aplicación. */
    public static final String ARCHIVO_ERRORES = "app-errores.log";

    /** Tiempo que se espera a que la aplicación termine sola antes de detenerla. */
    public static final Duration ESPERA_CIERRE = Duration.ofSeconds(2);

    private static final Logger REGISTRO = Logger.getLogger(LanzadorProceso.class.getName());
    private static final Duration ESPERA_DETENCION = Duration.ofSeconds(5);

    private final Path java;
    private final Path jarAgente;
    private final Path carpetaResultados;

    /**
     * Crea un lanzador que usa el {@code java} de la JVM actual.
     *
     * @param jarAgente ruta del jar del agente ({@code opsec-agente-<versión>-agente.jar}); no
     *     puede ser null
     * @param carpetaResultados carpeta donde se escriben las salidas de la aplicación; no puede ser
     *     null
     * @throws NullPointerException si algún parámetro es null
     */
    public LanzadorProceso(Path jarAgente, Path carpetaResultados) {
        this(javaActual(), jarAgente, carpetaResultados);
    }

    /**
     * Crea un lanzador con un ejecutable de Java propio, por ejemplo un JDK 8 para apps antiguas.
     *
     * @param java ruta del ejecutable {@code java}; no puede ser null
     * @param jarAgente ruta del jar del agente; no puede ser null
     * @param carpetaResultados carpeta donde se escriben las salidas de la aplicación; no puede ser
     *     null
     * @throws NullPointerException si algún parámetro es null
     */
    public LanzadorProceso(Path java, Path jarAgente, Path carpetaResultados) {
        this.java = Objects.requireNonNull(java, "La ruta de java no puede ser null");
        this.jarAgente =
                Objects.requireNonNull(jarAgente, "La ruta del jar del agente no puede ser null");
        this.carpetaResultados =
                Objects.requireNonNull(
                        carpetaResultados, "La carpeta de resultados no puede ser null");
    }

    /**
     * Devuelve el ejecutable {@code java} de la JVM que ejecuta el motor.
     *
     * @return ruta {@code <java.home>/bin/java}; en Windows el sistema agrega {@code .exe}
     */
    public static Path javaActual() {
        return Path.of(System.getProperty("java.home"), "bin", "java");
    }

    /**
     * Arma el comando que lanza la aplicación, sin ejecutarlo.
     *
     * @param proyecto proyecto con el JAR, la clase principal y los argumentos de la JVM; no puede
     *     ser null
     * @param puerto puerto donde el motor espera al agente
     * @param token token de la sesión; no puede ser null
     * @return el comando, un elemento por argumento
     * @throws NullPointerException si {@code proyecto} o {@code token} son null
     */
    public List<String> comando(Proyecto proyecto, int puerto, String token) {
        Objects.requireNonNull(proyecto, "El proyecto no puede ser null");
        Objects.requireNonNull(token, "El token no puede ser null");
        List<String> comando = new ArrayList<>();
        comando.add(java.toString());
        comando.addAll(proyecto.argumentosJvm());
        comando.add(
                "-javaagent:"
                        + jarAgente.toAbsolutePath()
                        + "=puerto="
                        + puerto
                        + ",token="
                        + token);
        Path jar = proyecto.jar().toAbsolutePath();
        if (proyecto.clasePrincipal() != null) {
            comando.add("-cp");
            comando.add(classpath(List.of(jar)));
            comando.add(proyecto.clasePrincipal());
        } else {
            comando.add("-jar");
            comando.add(jar.toString());
        }
        return comando;
    }

    /**
     * Lanza la aplicación y devuelve su proceso, sin esperar el saludo del agente.
     *
     * @param proyecto proyecto con la aplicación; no puede ser null
     * @param puerto puerto donde el motor espera al agente
     * @param token token de la sesión; no puede ser null
     * @return el proceso de la aplicación
     * @throws IOException si no se puede crear la carpeta de resultados o lanzar el proceso
     * @throws NullPointerException si {@code proyecto} o {@code token} son null
     */
    public Process lanzar(Proyecto proyecto, int puerto, String token) throws IOException {
        List<String> comando = comando(proyecto, puerto, token);
        Files.createDirectories(carpetaResultados);
        REGISTRO.fine(() -> "Lanzando la aplicación " + proyecto.nombre() + ": " + comando);
        return new ProcessBuilder(comando)
                .redirectOutput(
                        ProcessBuilder.Redirect.appendTo(
                                carpetaResultados.resolve(ARCHIVO_SALIDA).toFile()))
                .redirectError(
                        ProcessBuilder.Redirect.appendTo(
                                carpetaResultados.resolve(ARCHIVO_ERRORES).toFile()))
                .start();
    }

    /**
     * Lanza la aplicación y devuelve la acción que la detiene con {@link #detener(Process)}.
     *
     * @param proyecto proyecto con la aplicación; no puede ser null
     * @param puerto puerto donde el motor espera al agente
     * @param token token de la sesión; no puede ser null
     * @return acción que detiene la aplicación
     * @throws IOException si no se puede lanzar la aplicación
     */
    @Override
    public Closeable arrancar(Proyecto proyecto, int puerto, String token) throws IOException {
        Process proceso = lanzar(proyecto, puerto, token);
        return () -> detener(proceso);
    }

    /**
     * Detiene un proceso: espera {@link #ESPERA_CIERRE} a que termine solo, luego le pide que
     * termine y, si sigue vivo, lo termina a la fuerza.
     *
     * @param proceso proceso de la aplicación; no puede ser null
     * @throws InterruptedIOException si se interrumpe la espera
     */
    public static void detener(Process proceso) throws InterruptedIOException {
        Objects.requireNonNull(proceso, "El proceso no puede ser null");
        try {
            if (proceso.waitFor(ESPERA_CIERRE.toMillis(), TimeUnit.MILLISECONDS)) {
                return;
            }
            proceso.destroy();
            if (!proceso.waitFor(ESPERA_DETENCION.toMillis(), TimeUnit.MILLISECONDS)) {
                proceso.destroyForcibly();
                proceso.waitFor(ESPERA_DETENCION.toMillis(), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            proceso.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Se interrumpió la detención de la aplicación");
        }
    }

    private static String classpath(List<Path> entradas) {
        List<String> textos = new ArrayList<>();
        for (Path entrada : entradas) {
            textos.add(entrada.toString());
        }
        return String.join(File.pathSeparator, textos);
    }
}
