package io.github.physanto.opsecengine.practica.datos;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Consultas y borrados sobre la tabla {@code persona}.
 *
 * <p>Usa siempre {@link PreparedStatement}. Es seguro entre hilos: cada método abre y cierra su
 * propia conexión.
 */
public final class PersonaRepositorio {

    private final BaseDatos base;

    /**
     * Crea el repositorio sobre una base.
     *
     * @param base base abierta
     * @throws NullPointerException si la base es {@code null}
     */
    public PersonaRepositorio(BaseDatos base) {
        this.base = Objects.requireNonNull(base, "base");
    }

    /**
     * Busca una persona por su uid.
     *
     * @param uid identificador; se compara tal cual, sin recortar espacios
     * @return la persona, o vacío si no existe
     * @throws SQLException si falla la consulta
     */
    public Optional<Persona> buscar(String uid) throws SQLException {
        try (Connection conexion = base.conectar();
                PreparedStatement sentencia =
                        conexion.prepareStatement(
                                "SELECT uid, nombre, documento, correo FROM persona"
                                        + " WHERE uid = ?")) {
            sentencia.setString(1, uid);
            try (ResultSet filas = sentencia.executeQuery()) {
                return filas.next() ? Optional.of(leer(filas)) : Optional.empty();
            }
        }
    }

    /**
     * Devuelve todas las personas, ordenadas por uid.
     *
     * @return lista inmutable de personas
     * @throws SQLException si falla la consulta
     */
    public List<Persona> listar() throws SQLException {
        try (Connection conexion = base.conectar();
                PreparedStatement sentencia =
                        conexion.prepareStatement(
                                "SELECT uid, nombre, documento, correo FROM persona"
                                        + " ORDER BY uid");
                ResultSet filas = sentencia.executeQuery()) {
            List<Persona> personas = new ArrayList<>();
            while (filas.next()) {
                personas.add(leer(filas));
            }
            return List.copyOf(personas);
        }
    }

    /**
     * Elimina una persona por su uid.
     *
     * @param uid identificador de la persona
     * @return {@code true} si se eliminó; {@code false} si no existía
     * @throws SQLException si falla el borrado
     */
    public boolean eliminar(String uid) throws SQLException {
        try (Connection conexion = base.conectar();
                PreparedStatement sentencia =
                        conexion.prepareStatement("DELETE FROM persona WHERE uid = ?")) {
            sentencia.setString(1, uid);
            return sentencia.executeUpdate() > 0;
        }
    }

    private static Persona leer(ResultSet filas) throws SQLException {
        return new Persona(
                filas.getString("uid"),
                filas.getString("nombre"),
                filas.getString("documento"),
                filas.getString("correo"));
    }
}
