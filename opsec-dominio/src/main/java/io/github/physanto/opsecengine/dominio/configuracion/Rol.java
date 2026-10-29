package io.github.physanto.opsecengine.dominio.configuracion;

import java.util.Objects;

/**
 * Rol de la aplicación probada con las credenciales de prueba con que se inicia sesión.
 *
 * <p>Es inmutable y seguro entre hilos. {@link #toString()} oculta la contraseña para que no
 * aparezca en registros ni informes.
 *
 * @param nombre nombre del rol dentro del proyecto, por ejemplo {@code "admin"}; no puede ser null
 *     ni estar en blanco
 * @param usuario usuario de prueba; no puede ser null
 * @param contrasena contraseña de prueba; no puede ser null
 */
public record Rol(String nombre, String usuario, String contrasena) {

    /**
     * Crea un rol y valida sus datos.
     *
     * @throws NullPointerException si algún parámetro es null
     * @throws IllegalArgumentException si {@code nombre} está en blanco
     */
    public Rol {
        Objects.requireNonNull(nombre, "El nombre del rol no puede ser null");
        if (nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre del rol no puede estar en blanco");
        }
        Objects.requireNonNull(usuario, "El usuario del rol " + nombre + " no puede ser null");
        Objects.requireNonNull(
                contrasena, "La contraseña del rol " + nombre + " no puede ser null");
    }

    /**
     * Devuelve una representación del rol con la contraseña oculta.
     *
     * @return texto con el nombre y el usuario del rol, sin la contraseña
     */
    @Override
    public String toString() {
        return "Rol[nombre=" + nombre + ", usuario=" + usuario + ", contrasena=****]";
    }
}
