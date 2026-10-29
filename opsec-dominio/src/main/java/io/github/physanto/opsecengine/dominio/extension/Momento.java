package io.github.physanto.opsecengine.dominio.extension;

/** Momento en que se evalúa un {@link Oraculo}. */
public enum Momento {
    /** Revisa cada evento por separado, sin relacionarlo con otros; por ejemplo, una excepción. */
    EVENTO,
    /** Revisa la secuencia completa de un caso; por ejemplo, una operación sensible sin sesión. */
    CASO,
    /** Compara varios casos de la suite; por ejemplo, lo que alcanza un rol frente a otro. */
    SUITE
}
