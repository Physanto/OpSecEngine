package io.github.physanto.opsecengine.nucleo.sesion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.protocolo.TipoMensaje;
import io.github.physanto.opsecengine.soporte.AgenteFalso;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SesionServiceTest {

    private static final Duration ESPERA = Duration.ofSeconds(5);
    private static final Proyecto PROYECTO =
            new Proyecto("app", Path.of("app.jar"), null, null, null, null, null, null, null, null);

    private final AgenteFalso agente = new AgenteFalso();
    private final AtomicInteger detenciones = new AtomicInteger();

    private final ArranqueApp conAgenteFalso =
            (proyecto, puerto, token) -> {
                agente.conectar(puerto, token);
                return detenciones::incrementAndGet;
            };

    @AfterEach
    void cerrarAgente() {
        agente.close();
    }

    @Test
    void abreLaSesionConElAgenteYLaCierraConCerrar() throws Exception {
        try (SesionService sesiones = new SesionService(conAgenteFalso, ESPERA, ESPERA)) {
            ClienteAgente cliente = sesiones.abrir(PROYECTO);

            assertThat(sesiones.abierta()).isTrue();
            assertThat(sesiones.cliente()).isSameAs(cliente);
            assertThat(sesiones.saludo().getVersionAgente()).isEqualTo(AgenteFalso.VERSION);

            sesiones.cerrar();

            assertThat(sesiones.abierta()).isFalse();
            assertThat(agente.esperarMensaje(TipoMensaje.CERRAR, ESPERA)).isPresent();
            assertThat(detenciones).hasValue(1);
        }
    }

    @Test
    void noAbreDosSesionesALaVez() throws Exception {
        try (SesionService sesiones = new SesionService(conAgenteFalso, ESPERA, ESPERA)) {
            sesiones.abrir(PROYECTO);

            assertThatThrownBy(() -> sesiones.abrir(PROYECTO))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void detieneLaAplicacionSiElAgenteNoSaluda() {
        ArranqueApp sinAgente = (proyecto, puerto, token) -> detenciones::incrementAndGet;
        SesionService sesiones = new SesionService(sinAgente, Duration.ofMillis(200), ESPERA);

        assertThatThrownBy(() -> sesiones.abrir(PROYECTO)).isInstanceOf(IOException.class);
        assertThat(sesiones.abierta()).isFalse();
        assertThat(detenciones).hasValue(1);
    }

    @Test
    void propagaElErrorDeArranque() {
        ArranqueApp fallido =
                (proyecto, puerto, token) -> {
                    throw new IOException("no existe java");
                };
        SesionService sesiones = new SesionService(fallido, ESPERA, ESPERA);

        assertThatThrownBy(() -> sesiones.abrir(PROYECTO)).hasMessage("no existe java");
        assertThat(sesiones.abierta()).isFalse();
    }

    @Test
    void cerrarSinSesionNoHaceNada() {
        SesionService sesiones = new SesionService(conAgenteFalso, ESPERA, ESPERA);

        sesiones.cerrar();

        assertThat(detenciones).hasValue(0);
        assertThatThrownBy(sesiones::cliente).isInstanceOf(IllegalStateException.class);
    }
}
