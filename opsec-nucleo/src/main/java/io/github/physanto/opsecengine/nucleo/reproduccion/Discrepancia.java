package io.github.physanto.opsecengine.nucleo.reproduccion;

import java.util.Objects;

/**
 * Diferencia entre el manifiesto guardado de una ejecución y las condiciones actuales.
 *
 * <p>Es inmutable y seguro entre hilos.
 *
 * @param campo nombre del campo del manifiesto que difiere, por ejemplo {@code hashJar}; no puede
 *     ser null
 * @param guardado valor del manifiesto guardado; no puede ser null
 * @param actual valor actual; no puede ser null
 * @param bloqueante si la diferencia impide una re-ejecución exacta (la aplicación, la suite o la
 *     semilla cambiaron); las diferencias de versiones o de sistema solo avisan
 */
public record Discrepancia(String campo, String guardado, String actual, boolean bloqueante) {

    /**
     * Crea la discrepancia.
     *
     * @throws NullPointerException si algún texto es null
     */
    public Discrepancia {
        Objects.requireNonNull(campo, "El campo no puede ser null");
        Objects.requireNonNull(guardado, "El valor guardado no puede ser null");
        Objects.requireNonNull(actual, "El valor actual no puede ser null");
    }
}
