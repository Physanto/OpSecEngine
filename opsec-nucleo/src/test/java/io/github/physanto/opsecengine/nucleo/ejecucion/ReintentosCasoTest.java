package io.github.physanto.opsecengine.nucleo.ejecucion;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.physanto.opsecengine.dominio.caso.Accion;
import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.ConfigEjecucion;
import io.github.physanto.opsecengine.dominio.caso.Localizador;
import io.github.physanto.opsecengine.dominio.caso.ModoAccion;
import io.github.physanto.opsecengine.dominio.caso.Paso;
import io.github.physanto.opsecengine.dominio.caso.Suite;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.dominio.resultado.EstadoVeredicto;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoCaso;
import io.github.physanto.opsecengine.nucleo.evaluacion.EvaluacionService;
import io.github.physanto.opsecengine.nucleo.extensiones.RegistroExtensiones;
import io.github.physanto.opsecengine.nucleo.sesion.ArranqueApp;
import io.github.physanto.opsecengine.nucleo.sesion.SesionService;
import io.github.physanto.opsecengine.protocolo.dto.CodigoResultado;
import io.github.physanto.opsecengine.protocolo.dto.ResultadoPasoDto;
import io.github.physanto.opsecengine.soporte.AgenteFalso;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Pruebas de la repetición de casos que no cumplen y de la marca de inestables. */
class ReintentosCasoTest {

    private static final Duration ESPERA = Duration.ofSeconds(5);
    private static final Proyecto PROYECTO =
            new Proyecto(
                    "clientes",
                    Path.of("no-existe", "app.jar"),
                    "app.Portada",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);

    private final List<AgenteFalso> agentes = new CopyOnWriteArrayList<>();

    @AfterEach
    void cerrarAgentes() {
        agentes.forEach(AgenteFalso::close);
    }

    @Test
    void sinReintentosCadaCasoSeEjecutaUnaVez() {
        ResultadoCaso caso = ejecutar(0, CodigoResultado.ERROR);
        assertThat(caso.intentos()).isEqualTo(1);
        assertThat(caso.inestable()).isFalse();
        assertThat(caso.veredicto().estado()).isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
        assertThat(agentes).hasSize(1);
    }

    @Test
    void unCasoQueFallaYLuegoCumpleEsInestableYConservaElIntentoGrave() {
        ResultadoCaso caso = ejecutar(2, CodigoResultado.ERROR, CodigoResultado.OK);
        assertThat(caso.intentos()).isEqualTo(2);
        assertThat(caso.inestable()).isTrue();
        assertThat(caso.veredicto().estado()).isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
        assertThat(agentes).hasSize(2);
    }

    @Test
    void unCasoQueFallaSiempreSeRepiteTodasLasVecesYNoEsInestable() {
        ResultadoCaso caso = ejecutar(2, CodigoResultado.ERROR);
        assertThat(caso.intentos()).isEqualTo(3);
        assertThat(caso.inestable()).isFalse();
        assertThat(caso.veredicto().estado()).isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
        assertThat(agentes).hasSize(3);
    }

    @Test
    void unCasoQueCumpleNoSeRepite() {
        ResultadoCaso caso = ejecutar(3, CodigoResultado.OK);
        assertThat(caso.intentos()).isEqualTo(1);
        assertThat(caso.inestable()).isFalse();
        assertThat(caso.veredicto().estado()).isEqualTo(EstadoVeredicto.CUMPLE);
        assertThat(agentes).hasSize(1);
    }

    /** Ejecuta un caso; el intento n responde con el código n (el último se repite). */
    private ResultadoCaso ejecutar(int reintentos, CodigoResultado... codigos) {
        AtomicInteger intento = new AtomicInteger();
        ArranqueApp arranque =
                (proyecto, puerto, token) -> {
                    int n = Math.min(intento.getAndIncrement(), codigos.length - 1);
                    AgenteFalso agente = new AgenteFalso();
                    agente.cuandoPaso(paso -> true)
                            .responder(new ResultadoPasoDto(codigos[n], "respuesta " + n, 3));
                    agentes.add(agente);
                    agente.conectar(puerto, token);
                    return agente::close;
                };
        ConfigEjecucion config =
                new ConfigEjecucion(ModoAccion.SEMANTICO, false, ESPERA, reintentos, 0L, 1);
        Paso paso = new Paso(1, Accion.PULSAR, Localizador.porTexto("Avanzar"), null, null, null);
        Suite suite =
                new Suite(
                        "repeticion",
                        "1",
                        null,
                        config,
                        List.of(new Caso("A", null, null, List.of(paso), null, null, null)),
                        null);
        EvaluacionService evaluacion =
                new EvaluacionService(
                        new RegistroExtensiones(List.of(), List.of(), List.of(), List.of()));
        try (SesionService sesiones = new SesionService(arranque, ESPERA, ESPERA)) {
            return new EjecucionService(PROYECTO, sesiones, evaluacion)
                    .ejecutar(suite)
                    .casos()
                    .get(0);
        }
    }
}
