package io.github.physanto.opsecengine.practica.servicio;

import io.github.physanto.opsecengine.practica.fallos.Fallo;
import io.github.physanto.opsecengine.practica.fallos.Interruptores;
import java.util.Objects;

/**
 * Decide quién puede abrir las ventanas administrativas (menú del administrador y eliminación de
 * personas).
 *
 * <p>Con los fallos F2 y F3 corregidos, solo un administrador autenticado las abre. Es inmutable y
 * seguro entre hilos.
 */
public final class ControlAcceso {

    private final Interruptores interruptores;

    /**
     * Crea el control de acceso.
     *
     * @param interruptores fallos activos y corregidos
     * @throws NullPointerException si los interruptores son {@code null}
     */
    public ControlAcceso(Interruptores interruptores) {
        this.interruptores = Objects.requireNonNull(interruptores, "interruptores");
    }

    /**
     * Indica si el botón "Local" abre directamente el menú del administrador (fallo F2).
     *
     * @return {@code true} si el fallo F2 está activo; {@code false} si "Local" exige iniciar
     *     sesión
     */
    public boolean modoLocalSinSesion() {
        return interruptores.estaActivo(Fallo.F2);
    }

    /**
     * Indica si el {@code main} de la ventana EliminarPersona la abre sin verificar la sesión
     * (fallo F3).
     *
     * @return {@code true} si el fallo F3 está activo
     */
    public boolean eliminarPersonaSinSesion() {
        return interruptores.estaActivo(Fallo.F3);
    }

    /**
     * Indica si la sesión puede abrir una ventana administrativa.
     *
     * @param sesion sesión actual
     * @return {@code true} si la sesión es de un administrador autenticado o, con el fallo F2
     *     activo, si es la sesión del modo local
     */
    public boolean permiteAdministracion(Sesion sesion) {
        if (sesion.esAdministrador()) {
            return true;
        }
        return sesion.esModoLocal() && interruptores.estaActivo(Fallo.F2);
    }
}
