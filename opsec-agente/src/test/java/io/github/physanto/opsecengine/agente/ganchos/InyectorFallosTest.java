package io.github.physanto.opsecengine.agente.ganchos;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.physanto.opsecengine.protocolo.dto.ConfigGanchosDto;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Pruebas del inyector de fallos sin instrumentación. */
class InyectorFallosTest {

    private final List<Map<String, Object>> estados = new ArrayList<>();

    @AfterEach
    void limpiar() {
        ConfiguracionGanchos.reemplazar(null);
    }

    private void fallos(String... fallos) {
        InyectorFallos.configurar((tipo, datos) -> estados.add(datos), mensaje -> {});
        ConfiguracionGanchos.reemplazar(
                new ConfigGanchosDto(List.of(), List.of(), List.of(), List.of(fallos)));
    }

    @Test
    void creaLaExcepcionIndicadaConSuMensajeYAvisaConUnEstado() {
        fallos("SQL_CONEXION:java.sql.SQLException:sin red");

        Throwable fallo = InyectorFallos.pendiente(InyectorFallos.SQL_CONEXION);

        assertThat(fallo).isInstanceOf(SQLException.class).hasMessage("sin red");
        assertThat(estados)
                .singleElement()
                .satisfies(datos ->
                        assertThat(datos)
                                .containsEntry("fallo", "SQL_CONEXION")
                                .containsEntry("excepcion", "java.sql.SQLException"));
    }

    @Test
    void unaClaseInexistenteSeSustituyePorUnaRuntimeException() {
        fallos("SQL_EJECUTAR:no.existe.Fallo");

        Throwable fallo = InyectorFallos.pendiente(InyectorFallos.SQL_EJECUTAR);

        assertThat(fallo).isExactlyInstanceOf(RuntimeException.class);
        assertThat(fallo.getMessage()).contains("SQL_EJECUTAR");
    }

    @Test
    void sinFalloParaElPuntoNoHaceNada() {
        fallos("SQL_CONEXION:java.sql.SQLException");

        assertThat(InyectorFallos.pendiente(InyectorFallos.SQL_EJECUTAR)).isNull();
        assertThat(estados).isEmpty();
    }
}
