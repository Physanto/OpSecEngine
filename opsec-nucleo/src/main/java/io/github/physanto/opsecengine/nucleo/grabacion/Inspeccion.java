package io.github.physanto.opsecengine.nucleo.grabacion;

import io.github.physanto.opsecengine.dominio.caso.Localizador;
import java.util.Objects;

/**
 * Un componente que el usuario eligió con el inspector: lo que el agente informó en un evento
 * {@code COMPONENTE_INSPECCIONADO}.
 *
 * <p>Es inmutable y seguro entre hilos.
 *
 * @param ventana título, nombre o clase simple de la ventana que lo contiene; no puede ser null
 * @param clase nombre completo de la clase del componente; no puede ser null
 * @param ruta ruta del componente en el árbol de la ventana; null si no se conoce
 * @param texto texto visible del componente; null si no tiene
 * @param localizador criterios con que se vuelve a encontrar el componente, de más a menos legible;
 *     no puede ser null
 */
public record Inspeccion(
        String ventana, String clase, String ruta, String texto, Localizador localizador) {

    /** Valida los campos obligatorios. */
    public Inspeccion {
        Objects.requireNonNull(ventana, "La ventana no puede ser null");
        Objects.requireNonNull(clase, "La clase no puede ser null");
        Objects.requireNonNull(localizador, "El localizador no puede ser null");
    }
}
