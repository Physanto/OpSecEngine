package io.github.physanto.opsecengine.practica;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.physanto.opsecengine.practica.datos.BaseDatos;
import io.github.physanto.opsecengine.practica.datos.BasesDePrueba;
import io.github.physanto.opsecengine.practica.fallos.Fallo;
import io.github.physanto.opsecengine.practica.fallos.Interruptores;
import io.github.physanto.opsecengine.practica.servicio.Sesion;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

/** Pruebas de {@link ContextoApp}: los interruptores llegan a cada servicio. */
class ContextoAppTest {

    private static final PrintStream CONSOLA =
            new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);

    @Test
    void elInterruptorF4LlegaAlRepositorioDeUsuarios() throws SQLException {
        try (BaseDatos base = BasesDePrueba.nueva()) {
            ContextoApp vulnerable = ContextoApp.crear(base, Interruptores.todosActivos(), CONSOLA);
            ContextoApp corregido =
                    ContextoApp.crear(base, Interruptores.corrigiendo(Fallo.F4), CONSOLA);

            assertThat(vulnerable.login().iniciarSesion("Admin' --", "x").esExitoso()).isTrue();
            assertThat(corregido.login().iniciarSesion("Admin' --", "x").esExitoso()).isFalse();
        }
    }

    @Test
    void losInterruptoresLleganAlControlDeAcceso() throws SQLException {
        try (BaseDatos base = BasesDePrueba.nueva()) {
            ContextoApp contexto =
                    ContextoApp.crear(base, Interruptores.corrigiendo(Fallo.F2), CONSOLA);

            assertThat(contexto.interruptores().estaActivo(Fallo.F2)).isFalse();
            assertThat(contexto.control().permiteAdministracion(Sesion.local())).isFalse();
            assertThat(contexto.control().eliminarPersonaSinSesion()).isTrue();
            assertThat(contexto.base()).isSameAs(base);
            assertThat(contexto.personas().listar()).hasSize(5);
        }
    }
}
