package io.github.physanto.opsecengine.practica.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.physanto.opsecengine.practica.datos.BaseDatos;
import io.github.physanto.opsecengine.practica.datos.BasesDePrueba;
import io.github.physanto.opsecengine.practica.datos.Persona;
import io.github.physanto.opsecengine.practica.datos.PersonaRepositorio;
import io.github.physanto.opsecengine.practica.datos.Rol;
import io.github.physanto.opsecengine.practica.fallos.Fallo;
import io.github.physanto.opsecengine.practica.fallos.Interruptores;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Comprueba F9 y F10 con controles positivos y negativos. */
class EdicionPersonaServicioTest {

    private BaseDatos base;
    private PersonaRepositorio personas;

    @BeforeEach
    void crearBase() throws SQLException {
        base = BasesDePrueba.nueva();
        personas = new PersonaRepositorio(base);
    }

    @AfterEach
    void cerrarBase() throws SQLException {
        base.close();
    }

    @Test
    void f9AceptaDatosInvalidosYLaCorreccionLosRechaza() throws SQLException {
        Persona invalida = new Persona("P006", "", "abc", "correo-invalido");
        EdicionPersonaServicio vulnerable =
                new EdicionPersonaServicio(base, Interruptores.todosActivos());
        EdicionPersonaServicio corregido =
                new EdicionPersonaServicio(base, Interruptores.corrigiendo(Fallo.F9));

        vulnerable.registrar(invalida, Sesion.iniciada("Admin", Rol.ADMINISTRADOR));
        assertThat(personas.buscar("P006")).contains(invalida);
        assertThatThrownBy(() ->
                        corregido.registrar(
                                new Persona("P007", "", "abc", "correo-invalido"),
                                Sesion.iniciada("Admin", Rol.ADMINISTRADOR)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Documento");
        assertThat(personas.buscar("P007")).isEmpty();
    }

    @Test
    void f10PermiteModificarAlClienteYLaCorreccionExigeAdministrador() throws SQLException {
        Persona modificada = new Persona("P001", "Nombre editado", "123456", "nuevo@ejemplo.com");
        EdicionPersonaServicio vulnerable =
                new EdicionPersonaServicio(base, Interruptores.todosActivos());
        EdicionPersonaServicio corregido =
                new EdicionPersonaServicio(base, Interruptores.corrigiendo(Fallo.F10));
        Sesion cliente = Sesion.iniciada("Cliente", Rol.CLIENTE);

        assertThat(vulnerable.modificar(modificada, cliente)).isTrue();
        assertThat(personas.buscar("P001")).contains(modificada);
        assertThatThrownBy(() -> corregido.modificar(modificada, cliente))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("administrador");
    }
}
