package io.github.physanto.opsecengine.nucleo.verificacion;

import java.util.Objects;

/**
 * Resultado de una comprobación de la autoverificación.
 *
 * @param nombre qué se comprobó, por ejemplo {@code "Proyecto"}; no puede ser null
 * @param estado cómo terminó; no puede ser null
 * @param detalle explicación para la persona usuaria; puede tener varias líneas; no puede ser null
 */
public record Comprobacion(String nombre, Estado estado, String detalle) {

    /** Cómo terminó una comprobación. */
    public enum Estado {
        /** La comprobación pasó. */
        OK,
        /** Algo merece atención, pero no impide ejecutar. */
        AVISO,
        /** Algo impide ejecutar; la verificación se detiene. */
        FALLO
    }

    /**
     * Crea la comprobación.
     *
     * @throws NullPointerException si algún componente es null
     */
    public Comprobacion {
        Objects.requireNonNull(nombre, "El nombre no puede ser null");
        Objects.requireNonNull(estado, "El estado no puede ser null");
        Objects.requireNonNull(detalle, "El detalle no puede ser null");
    }
}
