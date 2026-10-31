package io.github.physanto.opsecengine.dominio.mapa;

import java.util.Objects;

/**
 * Ventana o diálogo de la aplicación probada: un nodo del {@link MapaApp}.
 *
 * <p>Es inmutable y seguro entre hilos.
 *
 * @param id identificador de la ventana dentro del mapa, único y estable entre exploraciones; no
 *     puede ser null ni estar en blanco
 * @param titulo título visible de la ventana; puede ser null si no tiene
 * @param clase nombre completo de la clase de la ventana; no puede ser null ni estar en blanco
 */
public record Ventana(String id, String titulo, String clase) {

    /**
     * Crea una ventana y valida sus datos.
     *
     * @throws NullPointerException si {@code id} o {@code clase} son null
     * @throws IllegalArgumentException si {@code id} o {@code clase} están en blanco
     */
    public Ventana {
        Objects.requireNonNull(id, "El id de la ventana no puede ser null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("El id de la ventana no puede estar en blanco");
        }
        Objects.requireNonNull(clase, "La clase de la ventana " + id + " no puede ser null");
        if (clase.isBlank()) {
            throw new IllegalArgumentException(
                    "La clase de la ventana " + id + " no puede estar en blanco");
        }
    }
}
