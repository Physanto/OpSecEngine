package io.github.physanto.opsecengine.nucleo.proyecto;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Un archivo de configuración ({@code proyecto.yaml} o una suite) no es válido.
 *
 * <p>Reúne todos los errores encontrados, cada uno con la ruta del campo y un mensaje en español,
 * para que se puedan corregir de una vez. El mensaje de la excepción los lista uno por línea.
 */
public class ExcepcionConfiguracion extends Exception {

    private static final long serialVersionUID = 1L;

    /** Archivo con los errores. */
    private final transient Path archivo;

    /** Errores encontrados, en el orden del documento. */
    private final transient List<ErrorConfiguracion> errores;

    /**
     * Crea la excepción.
     *
     * @param archivo archivo con los errores; no puede ser null
     * @param errores errores encontrados; no puede ser null ni estar vacía
     * @throws NullPointerException si algún parámetro es null
     * @throws IllegalArgumentException si {@code errores} está vacía
     */
    public ExcepcionConfiguracion(Path archivo, List<ErrorConfiguracion> errores) {
        super(describir(archivo, errores));
        this.archivo = archivo;
        this.errores = List.copyOf(errores);
    }

    /**
     * Devuelve el archivo con los errores.
     *
     * @return ruta del archivo
     */
    public Path archivo() {
        return archivo;
    }

    /**
     * Devuelve los errores encontrados.
     *
     * @return lista inmutable, nunca vacía
     */
    public List<ErrorConfiguracion> errores() {
        return errores;
    }

    private static String describir(Path archivo, List<ErrorConfiguracion> errores) {
        Objects.requireNonNull(archivo, "El archivo no puede ser null");
        Objects.requireNonNull(errores, "Los errores no pueden ser null");
        if (errores.isEmpty()) {
            throw new IllegalArgumentException("Una excepción de configuración necesita errores");
        }
        StringBuilder texto =
                new StringBuilder("El archivo ")
                        .append(archivo)
                        .append(
                                errores.size() == 1
                                        ? " tiene 1 error:"
                                        : " tiene " + errores.size() + " errores:");
        for (ErrorConfiguracion error : errores) {
            texto.append(System.lineSeparator()).append("  ").append(error);
        }
        return texto.toString();
    }
}
