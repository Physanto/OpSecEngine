package io.github.physanto.opsecengine.dominio.resultado;

/** Grado de certeza de un {@link Hallazgo}. */
public enum Confianza {
    /** La regla se cumple y además se verificó el efecto real, por ejemplo en el backend. */
    CONFIRMADO,
    /** La regla se cumple, pero no se pudo verificar el efecto real. */
    PROBABLE,
    /** Observación informativa que no indica por sí sola un fallo. */
    INFO
}
