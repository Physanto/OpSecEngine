package io.github.physanto.opsecengine.practica.datos;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

/**
 * Consultas sobre la tabla {@code usuario}, que guarda las credenciales y el rol.
 *
 * <p>Es seguro entre hilos: cada método abre y cierra su propia conexión.
 */
public final class UsuarioRepositorio {

    private final BaseDatos base;

    /**
     * Crea el repositorio sobre una base.
     *
     * @param base base abierta
     * @throws NullPointerException si la base es {@code null}
     */
    public UsuarioRepositorio(BaseDatos base) {
        this.base = Objects.requireNonNull(base, "base");
    }

    /**
     * Busca un usuario por su nombre, sin mirar la contraseña.
     *
     * @param nombre nombre de usuario; distingue mayúsculas
     * @return el usuario, o vacío si no existe
     * @throws SQLException si falla la consulta
     */
    public Optional<Usuario> buscarPorNombre(String nombre) throws SQLException {
        try (Connection conexion = base.conectar();
                PreparedStatement sentencia =
                        conexion.prepareStatement(
                                "SELECT usuario, contrasena, rol FROM usuario WHERE usuario = ?")) {
            sentencia.setString(1, nombre);
            return primero(sentencia);
        }
    }

    /**
     * Busca el usuario cuyo nombre y contraseña coinciden con los dados.
     *
     * @param nombre nombre de usuario; distingue mayúsculas
     * @param contrasena contraseña; distingue mayúsculas
     * @return el usuario, o vacío si las credenciales no coinciden
     * @throws SQLException si falla la consulta
     */
    public Optional<Usuario> buscarPorCredenciales(String nombre, String contrasena)
            throws SQLException {
        try (Connection conexion = base.conectar();
                PreparedStatement sentencia =
                        conexion.prepareStatement(
                                "SELECT usuario, contrasena, rol FROM usuario"
                                        + " WHERE usuario = ? AND contrasena = ?")) {
            sentencia.setString(1, nombre);
            sentencia.setString(2, contrasena);
            return primero(sentencia);
        }
    }

    private static Optional<Usuario> primero(PreparedStatement sentencia) throws SQLException {
        try (ResultSet filas = sentencia.executeQuery()) {
            return filas.next() ? Optional.of(leer(filas)) : Optional.empty();
        }
    }

    private static Usuario leer(ResultSet filas) throws SQLException {
        return new Usuario(
                filas.getString("usuario"),
                filas.getString("contrasena"),
                Rol.valueOf(filas.getString("rol")));
    }
}
