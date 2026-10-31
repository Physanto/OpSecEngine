package io.github.physanto.opsecengine.practica.datos;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Optional;

/**
 * Consultas sobre la tabla {@code usuario}, que guarda las credenciales y el rol.
 *
 * <p>Tiene dos modos: con {@link PreparedStatement} (seguro) o concatenando el texto recibido en un
 * {@link Statement} (fallo sembrado F4, inyección SQL). Es seguro entre hilos: cada método abre y
 * cierra su propia conexión.
 */
public final class UsuarioRepositorio {

    private static final String COLUMNAS = "SELECT usuario, contrasena, rol FROM usuario";

    private final BaseDatos base;
    private final boolean sqlConcatenado;

    /**
     * Crea el repositorio sobre una base.
     *
     * @param base base abierta
     * @param sqlConcatenado {@code true} para concatenar el texto en un {@link Statement} (fallo
     *     F4); {@code false} para usar {@link PreparedStatement}
     * @throws NullPointerException si la base es {@code null}
     */
    public UsuarioRepositorio(BaseDatos base, boolean sqlConcatenado) {
        this.base = Objects.requireNonNull(base, "base");
        this.sqlConcatenado = sqlConcatenado;
    }

    /**
     * Busca un usuario por su nombre, sin mirar la contraseña.
     *
     * @param nombre nombre de usuario; distingue mayúsculas
     * @return el usuario, o vacío si no existe
     * @throws SQLException si falla la consulta, por ejemplo por un texto inyectado mal formado
     */
    public Optional<Usuario> buscarPorNombre(String nombre) throws SQLException {
        if (sqlConcatenado) {
            // FALLO F4: el texto del campo se concatena en la sentencia
            return consultarConcatenado(COLUMNAS + " WHERE usuario = '" + nombre + "'");
        }
        try (Connection conexion = base.conectar();
                PreparedStatement sentencia =
                        conexion.prepareStatement(COLUMNAS + " WHERE usuario = ?")) {
            sentencia.setString(1, nombre);
            return primero(sentencia.executeQuery());
        }
    }

    /**
     * Busca el usuario cuyo nombre y contraseña coinciden con los dados.
     *
     * @param nombre nombre de usuario; distingue mayúsculas
     * @param contrasena contraseña; distingue mayúsculas
     * @return el usuario, o vacío si las credenciales no coinciden
     * @throws SQLException si falla la consulta, por ejemplo por un texto inyectado mal formado
     */
    public Optional<Usuario> buscarPorCredenciales(String nombre, String contrasena)
            throws SQLException {
        if (sqlConcatenado) {
            // FALLO F4: el texto de los campos se concatena en la sentencia
            return consultarConcatenado(
                    COLUMNAS
                            + " WHERE usuario = '"
                            + nombre
                            + "' AND contrasena = '"
                            + contrasena
                            + "'");
        }
        try (Connection conexion = base.conectar();
                PreparedStatement sentencia =
                        conexion.prepareStatement(
                                COLUMNAS + " WHERE usuario = ? AND contrasena = ?")) {
            sentencia.setString(1, nombre);
            sentencia.setString(2, contrasena);
            return primero(sentencia.executeQuery());
        }
    }

    private Optional<Usuario> consultarConcatenado(String sql) throws SQLException {
        try (Connection conexion = base.conectar();
                Statement sentencia = conexion.createStatement()) {
            return primero(sentencia.executeQuery(sql));
        }
    }

    private static Optional<Usuario> primero(ResultSet resultado) throws SQLException {
        try (ResultSet filas = resultado) {
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
