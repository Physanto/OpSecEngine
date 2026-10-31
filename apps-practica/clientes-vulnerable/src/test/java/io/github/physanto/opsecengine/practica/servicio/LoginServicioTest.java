package io.github.physanto.opsecengine.practica.servicio;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.physanto.opsecengine.practica.datos.BaseDatos;
import io.github.physanto.opsecengine.practica.datos.BasesDePrueba;
import io.github.physanto.opsecengine.practica.datos.Rol;
import io.github.physanto.opsecengine.practica.datos.UsuarioRepositorio;
import io.github.physanto.opsecengine.practica.fallos.Fallo;
import io.github.physanto.opsecengine.practica.fallos.Interruptores;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Pruebas de {@link LoginServicio} con cada fallo activo y corregido. */
class LoginServicioTest {

    private BaseDatos base;
    private ByteArrayOutputStream salida;

    @BeforeEach
    void crearBase() throws SQLException {
        base = BasesDePrueba.nueva();
        salida = new ByteArrayOutputStream();
    }

    @AfterEach
    void cerrarBase() throws SQLException {
        base.close();
    }

    private LoginServicio servicio(Interruptores interruptores) {
        UsuarioRepositorio usuarios =
                new UsuarioRepositorio(base, interruptores.estaActivo(Fallo.F4));
        return new LoginServicio(
                usuarios, interruptores, new PrintStream(salida, true, StandardCharsets.UTF_8));
    }

    private String consola() {
        return salida.toString(StandardCharsets.UTF_8);
    }

    @Test
    void lasCredencialesDeLaBaseInicianSesionConSuRol() throws SQLException {
        for (Interruptores interruptores :
                new Interruptores[] {
                    Interruptores.todosActivos(), Interruptores.todosCorregidos()
                }) {
            LoginServicio login = servicio(interruptores);

            ResultadoLogin admin = login.iniciarSesion("Admin", "Admin#2026");
            ResultadoLogin cliente = login.iniciarSesion("Cliente", "Cliente#2026");

            assertThat(admin.esExitoso()).isTrue();
            assertThat(admin.mensaje()).isEmpty();
            assertThat(admin.sesion()).get().extracting(Sesion::esAdministrador).isEqualTo(true);
            assertThat(cliente.sesion().flatMap(Sesion::rol)).contains(Rol.CLIENTE);
            assertThat(cliente.sesion().flatMap(Sesion::usuario)).contains("Cliente");
        }
    }

    @ParameterizedTest
    @CsvSource(
            value = {"'',Admin#2026", "Admin,''", "NULL,Admin#2026", "Admin,NULL"},
            nullValues = "NULL")
    void losCamposVaciosNoConsultanLaBase(String usuario, String contrasena) throws SQLException {
        ResultadoLogin resultado =
                servicio(Interruptores.todosActivos()).iniciarSesion(usuario, contrasena);

        assertThat(resultado.esExitoso()).isFalse();
        assertThat(resultado.sesion()).isEmpty();
        assertThat(resultado.mensaje()).contains(LoginServicio.MENSAJE_CAMPOS_VACIOS);
    }

    @Test
    void conF1ActivoLaCuentaDeSoporteEntraComoAdministrador() throws SQLException {
        ResultadoLogin resultado =
                servicio(Interruptores.todosActivos()).iniciarSesion("soporte", "soporte123");

        assertThat(resultado.sesion().flatMap(Sesion::usuario)).contains("soporte");
        assertThat(resultado.sesion().flatMap(Sesion::rol)).contains(Rol.ADMINISTRADOR);
    }

    @Test
    void conF1CorregidoLaCuentaDeSoporteNoExiste() throws SQLException {
        ResultadoLogin resultado =
                servicio(Interruptores.corrigiendo(Fallo.F1))
                        .iniciarSesion("soporte", "soporte123");

        assertThat(resultado.esExitoso()).isFalse();
        assertThat(resultado.mensaje()).contains(LoginServicio.MENSAJE_USUARIO_INEXISTENTE);
    }

    @Test
    void conF4ActivoLaInyeccionSqlIniciaSesion() throws SQLException {
        LoginServicio login = servicio(Interruptores.todosActivos());

        assertThat(login.iniciarSesion("Admin' --", "x").sesion().flatMap(Sesion::usuario))
                .contains("Admin");
        assertThat(login.iniciarSesion("Admin", "' OR '1'='1").esExitoso()).isTrue();
    }

    @Test
    void conF4CorregidoLaInyeccionSqlFalla() throws SQLException {
        LoginServicio login = servicio(Interruptores.corrigiendo(Fallo.F4));

        assertThat(login.iniciarSesion("Admin' --", "x").esExitoso()).isFalse();
        assertThat(login.iniciarSesion("Admin", "' OR '1'='1").mensaje())
                .contains(LoginServicio.MENSAJE_CONTRASENA_INCORRECTA);
    }

    @Test
    void conF5ActivoLosMensajesRevelanSiElUsuarioExiste() throws SQLException {
        LoginServicio login = servicio(Interruptores.todosActivos());

        assertThat(login.iniciarSesion("NoExiste", "x").mensaje())
                .contains(LoginServicio.MENSAJE_USUARIO_INEXISTENTE);
        assertThat(login.iniciarSesion("Admin", "x").mensaje())
                .contains(LoginServicio.MENSAJE_CONTRASENA_INCORRECTA);
    }

    @Test
    void conF5CorregidoElMensajeEsGenerico() throws SQLException {
        LoginServicio login = servicio(Interruptores.corrigiendo(Fallo.F5));

        assertThat(login.iniciarSesion("NoExiste", "x").mensaje())
                .contains(LoginServicio.MENSAJE_GENERICO);
        assertThat(login.iniciarSesion("Admin", "x").mensaje())
                .contains(LoginServicio.MENSAJE_GENERICO);
    }

    @Test
    void conF6ActivoLaContrasenaSaleEnLaConsola() throws SQLException {
        servicio(Interruptores.todosActivos()).iniciarSesion("Admin", "Admin#2026");

        assertThat(consola())
                .contains("Intento de inicio de sesión: usuario=Admin, contraseña=Admin#2026");
    }

    @Test
    void conF6CorregidoLaConsolaSoloMuestraElUsuario() throws SQLException {
        LoginServicio login = servicio(Interruptores.corrigiendo(Fallo.F6));
        login.iniciarSesion("Admin", "Admin#2026");
        login.iniciarSesion("Cliente", "otraClave");

        assertThat(consola())
                .contains("Intento de inicio de sesión del usuario Admin")
                .doesNotContain("Admin#2026")
                .doesNotContain("otraClave");
    }

    @Test
    void conTodoCorregidoNingunAtaqueFunciona() throws SQLException {
        LoginServicio login = servicio(Interruptores.todosCorregidos());

        assertThat(login.iniciarSesion("soporte", "soporte123").mensaje())
                .contains(LoginServicio.MENSAJE_GENERICO);
        assertThat(login.iniciarSesion("Admin' --", "x").mensaje())
                .contains(LoginServicio.MENSAJE_GENERICO);
        assertThat(login.iniciarSesion("NoExiste", "x").mensaje())
                .contains(LoginServicio.MENSAJE_GENERICO);
        assertThat(consola()).doesNotContain("soporte123");
    }
}
