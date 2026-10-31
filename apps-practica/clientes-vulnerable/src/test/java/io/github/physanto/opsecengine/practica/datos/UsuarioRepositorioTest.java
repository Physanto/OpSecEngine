package io.github.physanto.opsecengine.practica.datos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Pruebas de {@link UsuarioRepositorio} en sus dos modos: seguro y con SQL concatenado (F4). */
class UsuarioRepositorioTest {

    private BaseDatos base;

    @BeforeEach
    void crearBase() throws SQLException {
        base = BasesDePrueba.nueva();
    }

    @AfterEach
    void cerrarBase() throws SQLException {
        base.close();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void buscaPorNombreEnAmbosModos(boolean concatenado) throws SQLException {
        UsuarioRepositorio usuarios = new UsuarioRepositorio(base, concatenado);

        assertThat(usuarios.buscarPorNombre("Admin")).map(Usuario::rol).contains(Rol.ADMINISTRADOR);
        assertThat(usuarios.buscarPorNombre("Cliente")).map(Usuario::rol).contains(Rol.CLIENTE);
        assertThat(usuarios.buscarPorNombre("NoExiste")).isEmpty();
        assertThat(usuarios.buscarPorNombre("admin")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void buscaPorCredencialesEnAmbosModos(boolean concatenado) throws SQLException {
        UsuarioRepositorio usuarios = new UsuarioRepositorio(base, concatenado);

        assertThat(usuarios.buscarPorCredenciales("Admin", "Admin#2026"))
                .map(Usuario::nombre)
                .contains("Admin");
        assertThat(usuarios.buscarPorCredenciales("Cliente", "Cliente#2026"))
                .map(Usuario::nombre)
                .contains("Cliente");
        assertThat(usuarios.buscarPorCredenciales("Admin", "Cliente#2026")).isEmpty();
        assertThat(usuarios.buscarPorCredenciales("Admin", "admin#2026")).isEmpty();
    }

    @Test
    void conSqlConcatenadoLaInyeccionSaltaLaContrasena() throws SQLException {
        UsuarioRepositorio usuarios = new UsuarioRepositorio(base, true);

        assertThat(usuarios.buscarPorCredenciales("Admin' --", "cualquiera"))
                .map(Usuario::nombre)
                .contains("Admin");
        assertThat(usuarios.buscarPorCredenciales("Admin", "' OR '1'='1")).isPresent();
        assertThat(usuarios.buscarPorNombre("x' OR '1'='1")).isPresent();
    }

    @Test
    void conSqlConcatenadoUnaComillaSueltaRompeLaSentencia() {
        UsuarioRepositorio usuarios = new UsuarioRepositorio(base, true);

        assertThatThrownBy(() -> usuarios.buscarPorCredenciales("'", "x"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void conPreparedStatementLaInyeccionSeTrataComoDato() throws SQLException {
        UsuarioRepositorio usuarios = new UsuarioRepositorio(base, false);

        assertThat(usuarios.buscarPorCredenciales("Admin' --", "cualquiera")).isEmpty();
        assertThat(usuarios.buscarPorCredenciales("Admin", "' OR '1'='1")).isEmpty();
        assertThat(usuarios.buscarPorNombre("x' OR '1'='1")).isEmpty();
        assertThat(usuarios.buscarPorCredenciales("'", "x")).isEmpty();
    }

    @Test
    void elTextoDelUsuarioNoMuestraLaContrasena() throws SQLException {
        Usuario admin = new UsuarioRepositorio(base, false).buscarPorNombre("Admin").orElseThrow();

        assertThat(admin.contrasena()).isEqualTo("Admin#2026");
        assertThat(admin.toString()).doesNotContain("Admin#2026");
    }
}
