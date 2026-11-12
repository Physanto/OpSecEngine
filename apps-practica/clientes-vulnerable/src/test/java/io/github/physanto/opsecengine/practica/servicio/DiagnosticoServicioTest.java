package io.github.physanto.opsecengine.practica.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.physanto.opsecengine.practica.fallos.Fallo;
import io.github.physanto.opsecengine.practica.fallos.Interruptores;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

/** Controles positivos y negativos de los fallos F7 y F8 sin necesitar pantalla. */
class DiagnosticoServicioTest {

    @Test
    void f7ExponeLaPilaYLaCorreccionOcultaLosDetalles() {
        SQLException error = new SQLException("SELECT secreto FROM usuario");
        DiagnosticoServicio vulnerable = new DiagnosticoServicio(Interruptores.todosActivos());
        DiagnosticoServicio corregido =
                new DiagnosticoServicio(Interruptores.corrigiendo(Fallo.F7));

        assertThat(vulnerable.mensajeConsulta(error))
                .contains("java.sql.SQLException", "SELECT secreto", "\tat ");
        assertThat(corregido.mensajeConsulta(error))
                .isEqualTo("No se pudo consultar la base de datos.")
                .doesNotContain("SQLException", "SELECT", "\tat ");
    }

    @Test
    void f8EscapaAlLlamadorYLaCorreccionPermiteTerminarLaAccion() {
        DiagnosticoServicio vulnerable = new DiagnosticoServicio(Interruptores.todosActivos());
        DiagnosticoServicio corregido =
                new DiagnosticoServicio(Interruptores.corrigiendo(Fallo.F8));

        assertThatThrownBy(vulnerable::probarExcepcion)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("F8");
        assertThatCode(corregido::probarExcepcion).doesNotThrowAnyException();
    }

    @Test
    void losInterruptoresSonIndependientes() {
        DiagnosticoServicio soloF7Corregido =
                new DiagnosticoServicio(Interruptores.corrigiendo(Fallo.F7));
        DiagnosticoServicio soloF8Corregido =
                new DiagnosticoServicio(Interruptores.corrigiendo(Fallo.F8));

        assertThatThrownBy(soloF7Corregido::probarExcepcion).hasMessageContaining("F8");
        assertThat(soloF8Corregido.mensajeConsulta(new SQLException("detalle")))
                .contains("SQLException");
    }
}
