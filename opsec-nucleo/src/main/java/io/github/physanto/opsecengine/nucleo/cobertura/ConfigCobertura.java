package io.github.physanto.opsecengine.nucleo.cobertura;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Configuración con que el lanzador carga el agente de JaCoCo junto al del motor.
 *
 * <p>Todas las sesiones de una ejecución escriben en el mismo archivo {@code jacoco.exec}: JaCoCo
 * añade los datos de cada una (con un bloqueo del archivo, así que también sirve con casos en
 * paralelo), y la cobertura final es la de la suite completa. Las clases del propio agente del
 * motor no se miden.
 *
 * <p>Es inmutable y seguro entre hilos.
 *
 * @param jarJacoco ruta del agente de JaCoCo ({@code org.jacoco.agent-<versión>-runtime.jar}); no
 *     puede ser null
 * @param archivoExec archivo donde JaCoCo guarda los datos; no puede ser null
 * @param incluir patrones de nombres de clase que se miden, con {@code *} y {@code ?} y puntos como
 *     separador (por ejemplo {@code miapp.*}); si es null o está vacío se miden todas las clases de
 *     la aplicación
 * @throws IllegalArgumentException si una ruta contiene una coma, que JaCoCo no admite en sus
 *     opciones, o si el jar contiene un signo igual, que la JVM no admite en la ruta de un agente;
 *     o si un patrón está en blanco
 */
public record ConfigCobertura(Path jarJacoco, Path archivoExec, List<String> incluir) {

    /** Nombre del archivo de datos dentro de la carpeta de resultados. */
    public static final String ARCHIVO_EXEC = "jacoco.exec";

    /** Clases del agente del motor, que nunca se miden. */
    public static final String EXCLUIDAS = "io.github.physanto.opsecengine.agente.*";

    /** Valida las rutas y los patrones y hace inmutable la lista. */
    public ConfigCobertura {
        Objects.requireNonNull(jarJacoco, "El jar de JaCoCo no puede ser null");
        Objects.requireNonNull(archivoExec, "El archivo de datos no puede ser null");
        incluir = incluir == null ? List.of() : List.copyOf(incluir);
        String jar = jarJacoco.toAbsolutePath().toString();
        String exec = archivoExec.toAbsolutePath().toString();
        if (jar.indexOf(',') >= 0 || exec.indexOf(',') >= 0) {
            throw new IllegalArgumentException(
                    "JaCoCo no admite comas en las rutas: " + jar + " / " + exec);
        }
        if (jar.indexOf('=') >= 0) {
            throw new IllegalArgumentException(
                    "La ruta del agente de JaCoCo no puede tener un signo igual: " + jar);
        }
        for (String patron : incluir) {
            if (patron.isBlank() || patron.indexOf(',') >= 0) {
                throw new IllegalArgumentException(
                        "Patrón de clases no válido (vacío o con coma): \"" + patron + "\"");
            }
        }
    }

    /**
     * Crea la configuración con el archivo de datos dentro de la carpeta de resultados.
     *
     * @param jarJacoco ruta del agente de JaCoCo; no puede ser null
     * @param carpetaResultados carpeta de resultados de la ejecución; no puede ser null
     * @param incluir patrones de clases que se miden; vacío o null para todas
     * @return la configuración, con el archivo {@value #ARCHIVO_EXEC} en esa carpeta
     */
    public static ConfigCobertura en(Path jarJacoco, Path carpetaResultados, List<String> incluir) {
        Objects.requireNonNull(carpetaResultados, "La carpeta de resultados no puede ser null");
        return new ConfigCobertura(jarJacoco, carpetaResultados.resolve(ARCHIVO_EXEC), incluir);
    }

    /**
     * Devuelve el argumento {@code -javaagent} que carga JaCoCo.
     *
     * <p>Los datos se añaden al archivo ({@code append=true}) al volcarlos el agente del motor al
     * recibir {@code CERRAR} y, por si la aplicación termina sola, al salir la JVM.
     *
     * @return el argumento para la línea de comandos de la JVM
     */
    public String argumentoAgente() {
        StringBuilder argumento =
                new StringBuilder("-javaagent:")
                        .append(jarJacoco.toAbsolutePath())
                        .append("=destfile=")
                        .append(archivoExec.toAbsolutePath())
                        .append(",append=true,dumponexit=true,output=file,excludes=")
                        .append(EXCLUIDAS);
        if (!incluir.isEmpty()) {
            argumento.append(",includes=").append(String.join(":", incluir));
        }
        return argumento.toString();
    }
}
