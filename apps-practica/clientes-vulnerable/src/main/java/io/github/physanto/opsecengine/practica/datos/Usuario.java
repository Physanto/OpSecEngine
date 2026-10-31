package io.github.physanto.opsecengine.practica.datos;

import java.util.Objects;

/**
 * Fila de la tabla {@code usuario}: nombre de usuario, contraseña y rol.
 *
 * <p>Es inmutable y segura entre hilos.
 */
public final class Usuario {

    private final String nombre;
    private final String contrasena;
    private final Rol rol;

    /**
     * Crea un usuario.
     *
     * @param nombre nombre con el que inicia sesión
     * @param contrasena contraseña guardada en la base
     * @param rol rol del usuario
     * @throws NullPointerException si algún argumento es {@code null}
     */
    public Usuario(String nombre, String contrasena, Rol rol) {
        this.nombre = Objects.requireNonNull(nombre, "nombre");
        this.contrasena = Objects.requireNonNull(contrasena, "contrasena");
        this.rol = Objects.requireNonNull(rol, "rol");
    }

    /**
     * Devuelve el nombre con el que el usuario inicia sesión.
     *
     * @return nombre de usuario
     */
    public String nombre() {
        return nombre;
    }

    /**
     * Devuelve la contraseña guardada en la base.
     *
     * @return contraseña
     */
    public String contrasena() {
        return contrasena;
    }

    /**
     * Devuelve el rol del usuario.
     *
     * @return rol
     */
    public Rol rol() {
        return rol;
    }

    /** Muestra el nombre y el rol, nunca la contraseña. */
    @Override
    public String toString() {
        return "Usuario[" + nombre + ", " + rol + "]";
    }
}
