package io.github.physanto.opsecengine.practica.datos;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;

/**
 * Base H2 embebida en memoria con los datos semilla de la app.
 *
 * <p>La base vive mientras este objeto esté abierto: guarda una conexión de anclaje y, al cerrarlo,
 * la base desaparece. Cada operación pide su propia conexión con {@link #conectar()}, así que el
 * objeto es seguro entre hilos.
 */
public final class BaseDatos implements AutoCloseable {

    /** Nombre de la base que usa la app al ejecutarse. */
    public static final String NOMBRE_APP = "clientes";

    private static final String SEMILLA =
            "classpath:/io/github/physanto/opsecengine/practica/datos/semilla.sql";
    private static final Pattern NOMBRE_VALIDO = Pattern.compile("[A-Za-z0-9_-]+");

    private final String url;
    private final Connection ancla;

    private BaseDatos(String url, Connection ancla) {
        this.url = url;
        this.ancla = ancla;
    }

    /**
     * Crea una base en memoria con el nombre dado y le carga los datos semilla.
     *
     * <p>Dos bases con nombres distintos son independientes; las pruebas usan un nombre por prueba.
     *
     * @param nombre nombre de la base: letras, dígitos, guiones o guiones bajos
     * @return la base abierta, con los datos semilla
     * @throws IllegalArgumentException si el nombre no es válido
     * @throws SQLException si no se puede crear la base o cargar la semilla
     */
    public static BaseDatos enMemoria(String nombre) throws SQLException {
        if (nombre == null || !NOMBRE_VALIDO.matcher(nombre).matches()) {
            throw new IllegalArgumentException("Nombre de base no válido: " + nombre);
        }
        String url = "jdbc:h2:mem:" + nombre;
        BaseDatos base = new BaseDatos(url, DriverManager.getConnection(url));
        try {
            base.restaurar();
        } catch (SQLException e) {
            base.close();
            throw e;
        }
        return base;
    }

    /**
     * Abre una conexión nueva a la base. Quien la pide debe cerrarla.
     *
     * @return conexión abierta
     * @throws SQLException si la base ya está cerrada o no responde
     */
    public Connection conectar() throws SQLException {
        return DriverManager.getConnection(url);
    }

    /**
     * Borra las tablas y vuelve a cargar los datos semilla.
     *
     * @throws SQLException si falla el script de semilla
     */
    public void restaurar() throws SQLException {
        try (Connection conexion = conectar();
                Statement sentencia = conexion.createStatement()) {
            sentencia.execute("RUNSCRIPT FROM '" + SEMILLA + "' CHARSET 'UTF-8'");
        }
    }

    /**
     * Devuelve la URL JDBC de la base.
     *
     * @return URL, por ejemplo {@code jdbc:h2:mem:clientes}
     */
    public String url() {
        return url;
    }

    /**
     * Cierra la conexión de anclaje; la base desaparece cuando no quedan conexiones abiertas.
     *
     * @throws SQLException si falla el cierre
     */
    @Override
    public void close() throws SQLException {
        ancla.close();
    }
}
