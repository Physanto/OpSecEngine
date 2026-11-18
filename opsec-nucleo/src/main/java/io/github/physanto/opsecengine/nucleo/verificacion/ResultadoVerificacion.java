package io.github.physanto.opsecengine.nucleo.verificacion;

import io.github.physanto.opsecengine.nucleo.verificacion.Comprobacion.Estado;
import java.util.List;
import java.util.Optional;

/**
 * Resultado de la autoverificación: las comprobaciones en el orden en que se hicieron. Es
 * inmutable.
 *
 * @param comprobaciones comprobaciones realizadas; no puede ser null ni tener elementos null
 */
public record ResultadoVerificacion(List<Comprobacion> comprobaciones) {

    /**
     * Crea el resultado con una copia de la lista.
     *
     * @throws NullPointerException si la lista o alguno de sus elementos es null
     */
    public ResultadoVerificacion {
        comprobaciones = List.copyOf(comprobaciones);
    }

    /**
     * Indica si se puede ejecutar.
     *
     * @return true si ninguna comprobación terminó en {@link Estado#FALLO}
     */
    public boolean listo() {
        return fallo().isEmpty();
    }

    /**
     * Devuelve la comprobación que detuvo la verificación.
     *
     * @return la comprobación con {@link Estado#FALLO}, o vacío si no hubo ninguna
     */
    public Optional<Comprobacion> fallo() {
        return comprobaciones.stream().filter(c -> c.estado() == Estado.FALLO).findFirst();
    }
}
