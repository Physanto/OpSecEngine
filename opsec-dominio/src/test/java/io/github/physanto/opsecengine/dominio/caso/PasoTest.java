package io.github.physanto.opsecengine.dominio.caso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PasoTest {

    @Test
    void creaUnPasoCompleto() {
        Paso paso =
                new Paso(
                        1,
                        Accion.LLENAR,
                        Localizador.porEtiqueta("Usuario"),
                        "Admin",
                        Duration.ofSeconds(2),
                        null);

        assertThat(paso.valor()).isEqualTo("Admin");
        assertThat(paso.extensiones()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(
            value = Accion.class,
            names = {"ESPERAR", "CAPTURAR"})
    void esperarYCapturarAdmitenObjetivoNull(Accion accion) {
        assertThat(new Paso(1, accion, null, null, null, null).objetivo()).isNull();
    }

    @ParameterizedTest
    @EnumSource(
            value = Accion.class,
            names = {"ESPERAR", "CAPTURAR"},
            mode = EnumSource.Mode.EXCLUDE)
    void lasDemasAccionesExigenObjetivo(Accion accion) {
        assertThatThrownBy(() -> new Paso(1, accion, null, null, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("objetivo");
    }

    @Test
    void validaAccionOrdenYEspera() {
        Localizador objetivo = Localizador.porTexto("Aceptar");

        assertThatThrownBy(() -> new Paso(1, null, objetivo, null, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Paso(0, Accion.PULSAR, objetivo, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Paso(1, Accion.PULSAR, objetivo, null, Duration.ZERO, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        new Paso(1, Accion.PULSAR, objetivo, null, Duration.ofSeconds(-1), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lasExtensionesSonInmutables() {
        Map<String, Object> extensiones = new HashMap<>();
        extensiones.put("lento", true);
        Paso paso =
                new Paso(
                        1, Accion.PULSAR, Localizador.porTexto("Aceptar"), null, null, extensiones);

        extensiones.put("otro", 1);

        assertThat(paso.extensiones()).containsOnlyKeys("lento");
        assertThatThrownBy(() -> paso.extensiones().put("x", 1))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
