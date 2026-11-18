package io.github.physanto.opsecengine.agente;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Aplicación de prueba sin interfaz que usa JDBC con la base H2 en memoria que trae el jar de la
 * app de práctica. Espera una línea por la entrada estándar (para que la prueba configure los
 * ganchos antes) y después ejecuta sentencias con {@code Statement} y {@code PreparedStatement}.
 */
public final class AplicacionJdbcDePrueba {

    /** Línea que escribe al terminar las sentencias. */
    public static final String LINEA_LISTO = "SQL-LISTO";

    /** Texto que la prueba busca dentro de la consulta concatenada. */
    public static final String CANARIO = "OPSECCANARIO42";

    private AplicacionJdbcDePrueba() {}

    /**
     * Ejecuta las sentencias y se queda esperando a que lo detengan.
     *
     * @param args sin uso
     * @throws Exception si falla algo distinto de la sentencia errónea
     */
    public static void main(String[] args) throws Exception {
        esperarOrden();
        try (Connection conexion =
                DriverManager.getConnection("jdbc:h2:mem:opsec;USER=sa;PASSWORD=secreta")) {
            try (Statement sentencia = conexion.createStatement()) {
                sentencia.execute("CREATE TABLE personas (id INT, nombre VARCHAR(50))");
                sentencia.executeUpdate("INSERT INTO personas VALUES (1, 'Ana')");
                sentencia.executeQuery("SELECT * FROM personas WHERE nombre = '" + CANARIO + "'");
                try {
                    sentencia.executeQuery("SELEC mal escrito");
                } catch (SQLException esperada) {
                    System.out.println("Falló como se esperaba");
                }
            }
            try (PreparedStatement preparada =
                    conexion.prepareStatement("DELETE FROM personas WHERE id = ? AND nombre = ?")) {
                preparada.setInt(1, 1);
                preparada.setString(2, "Ana");
                preparada.executeUpdate();
            }
        }
        System.out.println(LINEA_LISTO);
        Thread.sleep(60_000);
    }

    private static void esperarOrden() throws IOException {
        while (System.in.read() != '\n') {
            // espera la orden de la prueba
        }
    }
}
