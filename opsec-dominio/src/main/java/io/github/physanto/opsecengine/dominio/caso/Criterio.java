package io.github.physanto.opsecengine.dominio.caso;

import java.util.Objects;

/**
 * Criterio de búsqueda de un componente: una estrategia junto con el valor que se busca.
 *
 * <p>Es inmutable y seguro entre hilos.
 *
 * @param estrategia estrategia de localización; no puede ser null
 * @param valor valor que se busca con la estrategia, por ejemplo el texto de un botón; no puede ser
 *     null ni estar en blanco
 */
public record Criterio(EstrategiaLocalizacion estrategia, String valor) {

    /**
     * Crea un criterio y valida sus datos.
     *
     * @throws NullPointerException si {@code estrategia} o {@code valor} son null
     * @throws IllegalArgumentException si {@code valor} está en blanco
     */
    public Criterio {
        Objects.requireNonNull(estrategia, "La estrategia del criterio no puede ser null");
        Objects.requireNonNull(valor, "El valor del criterio no puede ser null");
        if (valor.isBlank()) {
            throw new IllegalArgumentException("El valor del criterio no puede estar en blanco");
        }
    }
}
