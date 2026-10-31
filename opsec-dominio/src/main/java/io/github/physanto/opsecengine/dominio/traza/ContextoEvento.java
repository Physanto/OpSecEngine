package io.github.physanto.opsecengine.dominio.traza;

/**
 * Contexto en el que ocurre un evento: caso, paso, rol y estado de la sesión.
 *
 * <p>Es inmutable y seguro entre hilos.
 *
 * @param casoId id del caso en ejecución; puede ser null para eventos fuera de un caso, por ejemplo
 *     durante la exploración
 * @param numeroPaso orden del paso en ejecución, empezando en 1; vale 0 fuera de un paso, por
 *     ejemplo al arrancar la aplicación o durante el inicio de sesión
 * @param rol nombre del rol activo; puede ser null si no hay rol
 * @param sesionActiva {@code true} si el motor considera que hay una sesión iniciada
 */
public record ContextoEvento(String casoId, int numeroPaso, String rol, boolean sesionActiva) {

    /**
     * Crea un contexto y valida sus datos.
     *
     * @throws IllegalArgumentException si {@code numeroPaso} es negativo, o si {@code casoId} o
     *     {@code rol} no son null y están en blanco
     */
    public ContextoEvento {
        if (numeroPaso < 0) {
            throw new IllegalArgumentException(
                    "El número de paso no puede ser negativo: " + numeroPaso);
        }
        if (casoId != null && casoId.isBlank()) {
            throw new IllegalArgumentException(
                    "El id del caso no puede estar en blanco; use null fuera de un caso");
        }
        if (rol != null && rol.isBlank()) {
            throw new IllegalArgumentException(
                    "El rol no puede estar en blanco; use null si no hay rol");
        }
    }

    /**
     * Devuelve el contexto de un evento que ocurre fuera de cualquier caso, paso y sesión.
     *
     * @return contexto sin caso, con paso 0, sin rol y sin sesión
     */
    public static ContextoEvento fueraDeCaso() {
        return new ContextoEvento(null, 0, null, false);
    }
}
