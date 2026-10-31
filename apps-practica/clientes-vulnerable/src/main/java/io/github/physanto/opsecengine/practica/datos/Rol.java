package io.github.physanto.opsecengine.practica.datos;

/** Rol de un usuario de la app, guardado como texto en la columna {@code rol}. */
public enum Rol {
    /** Puede buscar y eliminar personas. */
    ADMINISTRADOR,
    /** Solo puede buscar personas. */
    CLIENTE
}
