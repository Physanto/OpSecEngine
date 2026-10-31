package io.github.physanto.opsecengine.practica.datos;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Pruebas de {@link PersonaRepositorio} sobre una base con los datos semilla. */
class PersonaRepositorioTest {

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
    void buscaUnaPersonaExistente() throws SQLException {
        assertThat(personas.buscar("P002"))
                .contains(
                        new Persona(
                                "P002",
                                "Carlos Pérez Ruiz",
                                "1020202020",
                                "carlos.perez@ejemplo.com"));
    }

    @Test
    void buscarUnUidInexistenteDevuelveVacio() throws SQLException {
        assertThat(personas.buscar("P999")).isEmpty();
        assertThat(personas.buscar("p001")).isEmpty();
    }

    @Test
    void listaTodasOrdenadasPorUid() throws SQLException {
        assertThat(personas.listar())
                .extracting(Persona::uid)
                .containsExactly("P001", "P002", "P003", "P004", "P005");
    }

    @Test
    void eliminaUnaPersonaExistente() throws SQLException {
        assertThat(personas.eliminar("P004")).isTrue();

        assertThat(personas.buscar("P004")).isEmpty();
        assertThat(personas.listar()).hasSize(4);
    }

    @Test
    void eliminarUnUidInexistenteNoBorraNada() throws SQLException {
        assertThat(personas.eliminar("P999")).isFalse();
        assertThat(personas.listar()).hasSize(5);
    }

    @Test
    void elUidSeTrataComoDatoYNoComoSql() throws SQLException {
        assertThat(personas.eliminar("' OR '1'='1")).isFalse();
        assertThat(personas.listar()).hasSize(5);
    }
}
