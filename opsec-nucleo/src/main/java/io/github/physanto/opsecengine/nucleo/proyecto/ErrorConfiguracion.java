package io.github.physanto.opsecengine.nucleo.proyecto;

import java.util.Objects;

/**
 * Un error encontrado al leer un archivo de configuración.
 *
 * <p>Es inmutable y seguro entre hilos.
 *
 * @param ruta ruta del campo con el error en formato JSON Pointer, por ejemplo {@code
 *     /casos/0/pasos/2}; cadena vacía para el documento completo; no puede ser null
 * @param mensaje explicación en español; no puede ser null
 */
public record ErrorConfiguracion(String ruta, String mensaje) {

    /**
     * Crea el error y valida sus datos.
     *
     * @throws NullPointerException si algún parámetro es null
     */
    public ErrorConfiguracion {
        Objects.requireNonNull(ruta, "La ruta del error no puede ser null");
        Objects.requireNonNull(mensaje, "El mensaje del error no puede ser null");
    }

    /**
     * Devuelve el error como una línea legible.
     *
     * @return texto con el formato {@code <ruta>: <mensaje>}; la raíz se escribe {@code /}
     */
    @Override
    public String toString() {
        return (ruta.isEmpty() ? "/" : ruta) + ": " + mensaje;
    }
}
