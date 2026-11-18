package io.github.physanto.opsecengine.agente.ganchos;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Pruebas de las funciones puras del gestor JDBC: no necesitan instrumentación. */
class GestorJdbcTest {

    @Test
    void laOperacionEsLaPrimeraPalabraEnMayusculas() {
        assertThat(GestorJdbc.operacion("  delete from personas")).isEqualTo("DELETE");
        assertThat(GestorJdbc.operacion("Select * from x")).isEqualTo("SELECT");
        assertThat(GestorJdbc.operacion("   ")).isNull();
        assertThat(GestorJdbc.operacion("(select 1)")).isNull();
    }

    @Test
    void laUrlPierdeUsuarioYContrasena() {
        assertThat(
                        GestorJdbc.sinCredenciales(
                                "jdbc:mysql://host:3306/bd?user=root&password=x&ssl=0"))
                .isEqualTo("jdbc:mysql://host:3306/bd?ssl=0");
        assertThat(GestorJdbc.sinCredenciales("jdbc:h2:mem:opsec;USER=sa;PASSWORD=secreta"))
                .isEqualTo("jdbc:h2:mem:opsec");
        assertThat(GestorJdbc.sinCredenciales("jdbc:postgresql://admin:clave@host/bd"))
                .isEqualTo("jdbc:postgresql://host/bd");
        assertThat(GestorJdbc.sinCredenciales("jdbc:sqlite:datos.db"))
                .isEqualTo("jdbc:sqlite:datos.db");
        assertThat(GestorJdbc.sinCredenciales(null)).isNull();
    }

    @Test
    void sinConfiguracionNingunaOperacionEsSensible() {
        ConfiguracionGanchos.reemplazar(null);

        assertThat(ConfiguracionGanchos.esSqlSensible("DELETE")).isFalse();
        assertThat(ConfiguracionGanchos.esSqlSensible(null)).isFalse();
    }
}
