package io.github.physanto.opsecengine.practica.servicio;

import io.github.physanto.opsecengine.practica.datos.Rol;
import java.util.Objects;
import java.util.Optional;

/**
 * Estado de la sesión que las ventanas se pasan entre sí: quién inició sesión y con qué rol.
 *
 * <p>Es inmutable y segura entre hilos.
 */
public final class Sesion {

    private static final Sesion SIN_INICIAR = new Sesion(null, null, false);
    private static final Sesion LOCAL = new Sesion(null, null, true);

    private final String usuario;
    private final Rol rol;
    private final boolean modoLocal;

    private Sesion(String usuario, Rol rol, boolean modoLocal) {
        this.usuario = usuario;
        this.rol = rol;
        this.modoLocal = modoLocal;
    }

    /**
     * Devuelve la sesión de quien todavía no ha iniciado sesión.
     *
     * @return sesión sin usuario ni rol
     */
    public static Sesion sinIniciar() {
        return SIN_INICIAR;
    }

    /**
     * Devuelve la sesión del modo "Local", que no tiene usuario autenticado.
     *
     * <p>Solo se usa con el fallo F2 activo, cuando "Local" abre el menú del administrador sin
     * iniciar sesión.
     *
     * @return sesión del modo local, sin usuario ni rol
     */
    public static Sesion local() {
        return LOCAL;
    }

    /**
     * Crea la sesión de un usuario que se autenticó.
     *
     * @param usuario nombre del usuario
     * @param rol rol del usuario
     * @return sesión iniciada
     * @throws NullPointerException si algún argumento es {@code null}
     */
    public static Sesion iniciada(String usuario, Rol rol) {
        return new Sesion(
                Objects.requireNonNull(usuario, "usuario"),
                Objects.requireNonNull(rol, "rol"),
                false);
    }

    /**
     * Indica si alguien inició sesión.
     *
     * @return {@code true} si hay un usuario autenticado
     */
    public boolean estaIniciada() {
        return usuario != null;
    }

    /**
     * Indica si la sesión pertenece a un administrador autenticado.
     *
     * @return {@code true} si hay sesión iniciada con rol {@link Rol#ADMINISTRADOR}
     */
    public boolean esAdministrador() {
        return rol == Rol.ADMINISTRADOR;
    }

    /**
     * Indica si es la sesión del modo "Local" (fallo F2).
     *
     * @return {@code true} si la sesión viene del botón "Local" sin iniciar sesión
     */
    public boolean esModoLocal() {
        return modoLocal;
    }

    /**
     * Devuelve el nombre del usuario autenticado.
     *
     * @return el usuario, o vacío si no hay sesión iniciada
     */
    public Optional<String> usuario() {
        return Optional.ofNullable(usuario);
    }

    /**
     * Devuelve el rol del usuario autenticado.
     *
     * @return el rol, o vacío si no hay sesión iniciada
     */
    public Optional<Rol> rol() {
        return Optional.ofNullable(rol);
    }

    @Override
    public String toString() {
        if (estaIniciada()) {
            return "Sesion[" + usuario + ", " + rol + "]";
        }
        return modoLocal ? "Sesion[modo local]" : "Sesion[sin iniciar]";
    }
}
