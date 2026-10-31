package io.github.physanto.opsecengine.practica.servicio;

/**
 * Decide quién puede abrir las ventanas administrativas (menú del administrador y eliminación de
 * personas).
 *
 * <p>Es inmutable y seguro entre hilos.
 */
public final class ControlAcceso {

    /** Crea el control de acceso. */
    public ControlAcceso() {
        // Sin estado
    }

    /**
     * Indica si la sesión puede abrir una ventana administrativa.
     *
     * @param sesion sesión actual
     * @return {@code true} si la sesión es de un administrador autenticado
     */
    public boolean permiteAdministracion(Sesion sesion) {
        return sesion.esAdministrador();
    }
}
