package io.github.physanto.opsecengine.agente;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;

/** Aplicación de prueba sin interfaz que intenta conectarse y ejecutar con fallos inyectados. */
public final class AplicacionFalloJdbcDePrueba {

    private AplicacionFalloJdbcDePrueba() {}

    /**
     * Intenta la conexión y una sentencia, y cuenta qué excepción recibió.
     *
     * @param args sin uso
     * @throws Exception si falla algo inesperado
     */
    public static void main(String[] args) throws Exception {
        Connection abierta = DriverManager.getConnection("jdbc:h2:mem:fallo2");
        esperarOrden();
        try {
            DriverManager.getConnection("jdbc:h2:mem:fallo");
            System.out.println("conexion: sin fallo");
        } catch (Exception e) {
            System.out.println("conexion: " + e.getClass().getName() + " " + e.getMessage());
        }
        try {
            abierta.createStatement().execute("SELECT 1");
            System.out.println("ejecucion: sin fallo");
        } catch (Exception e) {
            System.out.println("ejecucion: " + e.getClass().getName() + " " + e.getMessage());
        }
        System.out.println("FALLO-LISTO");
        Thread.sleep(60_000);
    }

    private static void esperarOrden() throws IOException {
        while (System.in.read() != '\n') {
            // espera la orden de la prueba
        }
    }
}
