package io.github.physanto.opsecengine.dominio.resultado;

/** Estado de un {@link Veredicto}. */
public enum EstadoVeredicto {
    /** No se encontraron fallos en lo probado. No significa que la aplicación sea segura. */
    CUMPLE,
    /** Se encontró al menos un fallo. */
    NO_CUMPLE,
    /** El motor no pudo decidir, por ejemplo porque el caso no llegó a ejecutarse. */
    NO_CONCLUYENTE
}
