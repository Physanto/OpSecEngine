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

    private static final Sesion SIN_INICIAR = new Sesion(null, null);

    private final String usuario;
    private final Rol rol;

    private Sesion(String usuario, Rol rol) {
        this.usuario = usuario;
        this.rol = rol;
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
     * Crea la sesión de un usuario que se autenticó.
     *
     * @param usuario nombre del usuario
     * @param rol rol del usuario
     * @return sesión iniciada
     * @throws NullPointerException si algún argumento es {@code null}
     */
    public static Sesion iniciada(String usuario, Rol rol) {
        return new Sesion(
                Objects.requireNonNull(usuario, "usuario"), Objects.requireNonNull(rol, "rol"));
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
        return estaIniciada() ? "Sesion[" + usuario + ", " + rol + "]" : "Sesion[sin iniciar]";
    }
}
