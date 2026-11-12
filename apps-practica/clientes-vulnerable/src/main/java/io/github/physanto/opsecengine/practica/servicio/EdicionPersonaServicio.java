package io.github.physanto.opsecengine.practica.servicio;

import io.github.physanto.opsecengine.practica.datos.*;
import io.github.physanto.opsecengine.practica.fallos.*;
import java.sql.*;
import java.util.Objects;

/** Registro y modificación del laboratorio. Cada operación abre su conexión; seguro entre hilos. */
public final class EdicionPersonaServicio {
    private final BaseDatos base;
    private final Interruptores interruptores;

    /**
     * Crea el servicio.
     *
     * @param base base de práctica
     * @param interruptores configuración de fallos
     * @throws NullPointerException si algún argumento es null
     */
    public EdicionPersonaServicio(BaseDatos base, Interruptores interruptores) {
        this.base = Objects.requireNonNull(base, "base");
        this.interruptores = Objects.requireNonNull(interruptores, "interruptores");
    }

    /**
     * Registra una persona; solo el administrador puede hacerlo.
     *
     * @param persona datos a registrar
     * @param sesion sesión actual
     * @throws IllegalArgumentException si F9 está corregido y hay campos inválidos
     * @throws SecurityException si no es administrador
     * @throws SQLException si falla la base, incluido un UID duplicado
     */
    public void registrar(Persona persona, Sesion sesion) throws SQLException {
        exigirAdministrador(sesion);
        if (!interruptores.estaActivo(Fallo.F9)) validar(persona);
        try (Connection conexion = base.conectar();
                PreparedStatement sentencia =
                        conexion.prepareStatement(
                                "INSERT INTO persona (uid,nombre,documento,correo) VALUES (?,?,?,?)")) {
            sentencia.setString(1, persona.uid());
            sentencia.setString(2, persona.nombre());
            sentencia.setString(3, persona.documento());
            sentencia.setString(4, persona.correo());
            sentencia.executeUpdate();
        }
    }

    /**
     * Modifica una persona existente. F10 permite hacerlo sin rol de administrador.
     *
     * @param persona nuevos datos; siempre se validan
     * @param sesion sesión actual
     * @return true si existía el UID
     * @throws IllegalArgumentException si los datos son inválidos
     * @throws SecurityException si F10 está corregido y no es administrador
     * @throws SQLException si falla la base
     */
    public boolean modificar(Persona persona, Sesion sesion) throws SQLException {
        Objects.requireNonNull(sesion, "sesion");
        if (!interruptores.estaActivo(Fallo.F10)) exigirAdministrador(sesion);
        validar(persona);
        try (Connection conexion = base.conectar();
                PreparedStatement sentencia =
                        conexion.prepareStatement(
                                "UPDATE persona SET nombre=?, documento=?, correo=? WHERE uid=?")) {
            sentencia.setString(1, persona.nombre());
            sentencia.setString(2, persona.documento());
            sentencia.setString(3, persona.correo());
            sentencia.setString(4, persona.uid());
            return sentencia.executeUpdate() > 0;
        }
    }

    private static void exigirAdministrador(Sesion sesion) {
        if (!Objects.requireNonNull(sesion, "sesion").esAdministrador())
            throw new SecurityException("Acceso denegado: se requiere administrador.");
    }

    private static void validar(Persona persona) {
        Objects.requireNonNull(persona, "persona");
        if (persona.uid().isBlank()
                || persona.uid().length() > 20
                || persona.nombre().isBlank()
                || persona.nombre().length() > 100
                || !persona.documento().matches("[0-9]{5,20}")
                || persona.correo().length() > 100
                || !persona.correo().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new IllegalArgumentException(
                    "Revise Uid (1–20), Nombre (1–100), Documento (5–20 dígitos) y Correo.");
    }
}
