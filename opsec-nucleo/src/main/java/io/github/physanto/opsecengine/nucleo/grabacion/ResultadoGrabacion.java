package io.github.physanto.opsecengine.nucleo.grabacion;

import io.github.physanto.opsecengine.dominio.caso.Suite;
import java.util.List;
import java.util.Objects;

/**
 * Resultado de convertir una grabación: la suite y los avisos de lo que no se pudo conservar.
 *
 * <p>Es inmutable y seguro entre hilos.
 *
 * @param suite suite con un solo caso que reproduce lo grabado; no puede ser null
 * @param advertencias avisos en español, en el orden de la grabación: acciones que no se incluyeron
 *     porque el formato de suites no las puede expresar y datos delicados que quedaron en claro; si
 *     es null queda vacía
 */
public record ResultadoGrabacion(Suite suite, List<String> advertencias) {

    /** Valida la suite y hace inmutable la lista de avisos. */
    public ResultadoGrabacion {
        Objects.requireNonNull(suite, "La suite no puede ser null");
        advertencias = advertencias == null ? List.of() : List.copyOf(advertencias);
    }
}
