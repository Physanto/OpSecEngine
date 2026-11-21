package io.github.physanto.opsecengine.nucleo.cobertura;

import java.util.Objects;

/**
 * Cobertura de una clase de la aplicación.
 *
 * <p>Es inmutable y seguro entre hilos.
 *
 * @param nombre nombre completo de la clase, con puntos; no puede ser null
 * @param instrucciones instrucciones de bytecode; no puede ser null
 * @param ramas ramas de decisión; no puede ser null
 * @param lineas líneas de código; no puede ser null
 */
public record ClaseCobertura(
        String nombre, Contador instrucciones, Contador ramas, Contador lineas) {

    /** Valida los campos. */
    public ClaseCobertura {
        Objects.requireNonNull(nombre, "El nombre no puede ser null");
        Objects.requireNonNull(instrucciones, "Las instrucciones no pueden ser null");
        Objects.requireNonNull(ramas, "Las ramas no pueden ser null");
        Objects.requireNonNull(lineas, "Las líneas no pueden ser null");
    }
}
