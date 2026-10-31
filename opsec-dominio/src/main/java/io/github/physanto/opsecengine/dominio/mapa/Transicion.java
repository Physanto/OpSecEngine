package io.github.physanto.opsecengine.dominio.mapa;

import io.github.physanto.opsecengine.dominio.caso.Accion;
import io.github.physanto.opsecengine.dominio.caso.Localizador;
import java.util.Objects;

/**
 * Transición entre dos ventanas: una arista del {@link MapaApp}.
 *
 * <p>Indica que, en un contexto dado, ejecutar una acción sobre un control de la ventana de origen
 * lleva a la ventana de destino. Es inmutable y seguro entre hilos.
 *
 * @param origen id de la ventana de origen; no puede ser null ni estar en blanco
 * @param destino id de la ventana de destino; no puede ser null ni estar en blanco
 * @param accion acción que produce la transición; no puede ser null
 * @param control localizador del control sobre el que se ejecuta la acción; puede ser null solo si
 *     la acción admite ejecutarse sin objetivo ({@link Accion#admiteSinObjetivo()})
 * @param contexto contexto en que se observó la transición: {@value MapaApp#SIN_SESION}, {@code
 *     "rol:<nombre>"} o {@code "entrada:<clase>"} (ver {@link MapaApp}); no puede ser null ni estar
 *     en blanco
 */
public record Transicion(
        String origen, String destino, Accion accion, Localizador control, String contexto) {

    /**
     * Crea una transición y valida sus datos.
     *
     * @throws NullPointerException si {@code origen}, {@code destino}, {@code accion} o {@code
     *     contexto} son null, o si falta el control en una acción que lo necesita
     * @throws IllegalArgumentException si {@code origen}, {@code destino} o {@code contexto} están
     *     en blanco
     */
    public Transicion {
        Objects.requireNonNull(origen, "El origen de la transición no puede ser null");
        Objects.requireNonNull(destino, "El destino de la transición no puede ser null");
        Objects.requireNonNull(accion, "La acción de la transición no puede ser null");
        Objects.requireNonNull(contexto, "El contexto de la transición no puede ser null");
        if (origen.isBlank() || destino.isBlank() || contexto.isBlank()) {
            throw new IllegalArgumentException(
                    "El origen, el destino y el contexto de la transición no pueden estar en"
                            + " blanco");
        }
        if (!accion.admiteSinObjetivo()) {
            Objects.requireNonNull(
                    control, "La acción " + accion + " de la transición necesita un control");
        }
    }
}
