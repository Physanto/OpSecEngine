package io.github.physanto.opsecengine.nucleo.reproduccion;

import io.github.physanto.opsecengine.dominio.caso.Suite;
import io.github.physanto.opsecengine.dominio.resultado.Manifiesto;
import java.util.Objects;

/**
 * Lo que se guardó de una ejecución para poder repetirla: su manifiesto y la suite exacta.
 *
 * <p>Es inmutable y seguro entre hilos.
 *
 * @param manifiesto manifiesto de la ejecución original; no puede ser null
 * @param suite suite que se ejecutó, con su semilla; no puede ser null
 */
public record Reproduccion(Manifiesto manifiesto, Suite suite) {

    /**
     * Crea la reproducción.
     *
     * @throws NullPointerException si algún parámetro es null
     */
    public Reproduccion {
        Objects.requireNonNull(manifiesto, "El manifiesto no puede ser null");
        Objects.requireNonNull(suite, "La suite no puede ser null");
    }
}
