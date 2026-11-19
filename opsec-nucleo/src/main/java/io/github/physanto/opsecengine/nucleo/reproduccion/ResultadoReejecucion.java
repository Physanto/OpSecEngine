package io.github.physanto.opsecengine.nucleo.reproduccion;

import io.github.physanto.opsecengine.dominio.resultado.ResultadoSuite;
import java.util.List;
import java.util.Objects;

/**
 * Resultado de repetir una ejecución.
 *
 * <p>Es inmutable y seguro entre hilos.
 *
 * @param resultado resultado de la nueva ejecución; no puede ser null
 * @param discrepancias diferencias entre el manifiesto guardado y las condiciones actuales; si es
 *     null queda vacía
 */
public record ResultadoReejecucion(ResultadoSuite resultado, List<Discrepancia> discrepancias) {

    /**
     * Crea el resultado.
     *
     * @throws NullPointerException si {@code resultado} es null
     */
    public ResultadoReejecucion {
        Objects.requireNonNull(resultado, "El resultado no puede ser null");
        discrepancias = discrepancias == null ? List.of() : List.copyOf(discrepancias);
    }
}
