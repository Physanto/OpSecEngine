package io.github.physanto.opsecengine.practica.datos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Pruebas de {@link BaseDatos}: semilla, restauración, aislamiento y cierre. */
class BaseDatosTest {

    @Test
    void cargaLosDatosSemillaAlCrearse() throws SQLException {
        try (BaseDatos base = BasesDePrueba.nueva()) {
            assertThat(contar(base, "usuario")).isEqualTo(2);
            assertThat(contar(base, "persona")).isEqualTo(5);
            assertThat(base.url()).startsWith("jdbc:h2:mem:prueba_");
        }
    }

    @Test
    void restaurarDevuelveLosDatosSemilla() throws SQLException {
        try (BaseDatos base = BasesDePrueba.nueva()) {
            PersonaRepositorio personas = new PersonaRepositorio(base);
            personas.eliminar("P001");
            personas.eliminar("P002");
            assertThat(contar(base, "persona")).isEqualTo(3);

            base.restaurar();

            assertThat(contar(base, "persona")).isEqualTo(5);
            assertThat(personas.buscar("P001")).isPresent();
        }
    }

    @Test
    void dosBasesConNombresDistintosSonIndependientes() throws SQLException {
        try (BaseDatos primera = BasesDePrueba.nueva();
                BaseDatos segunda = BasesDePrueba.nueva()) {
            new PersonaRepositorio(primera).eliminar("P003");

            assertThat(contar(primera, "persona")).isEqualTo(4);
            assertThat(contar(segunda, "persona")).isEqualTo(5);
        }
    }

    @Test
    void laBaseDesapareceAlCerrarse() throws SQLException {
        BaseDatos base = BasesDePrueba.nueva();
        String url = base.url();
        base.close();

        // Una conexión nueva con la misma URL crea una base vacía, sin las tablas semilla
        try (Connection conexion = DriverManager.getConnection(url);
                Statement sentencia = conexion.createStatement()) {
            assertThatThrownBy(() -> sentencia.executeQuery("SELECT COUNT(*) FROM persona"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void laSemillaConservaLasTildes() throws SQLException {
        try (BaseDatos base = BasesDePrueba.nueva()) {
            assertThat(new PersonaRepositorio(base).buscar("P001"))
                    .map(Persona::nombre)
                    .contains("Ana María Gómez");
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"con espacio", "con;punto", "a'b"})
    void rechazaNombresNoValidos(String nombre) {
        assertThatThrownBy(() -> BaseDatos.enMemoria(nombre))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static int contar(BaseDatos base, String tabla) throws SQLException {
        try (Connection conexion = base.conectar();
                Statement sentencia = conexion.createStatement();
                ResultSet filas = sentencia.executeQuery("SELECT COUNT(*) FROM " + tabla)) {
            filas.next();
            return filas.getInt(1);
        }
    }
}
