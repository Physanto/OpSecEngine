package io.github.physanto.opsecengine.nucleo.ejecucion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.physanto.opsecengine.dominio.caso.Accion;
import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.Localizador;
import io.github.physanto.opsecengine.dominio.caso.Paso;
import io.github.physanto.opsecengine.dominio.caso.Suite;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.dominio.extension.Contexto;
import io.github.physanto.opsecengine.dominio.extension.Momento;
import io.github.physanto.opsecengine.dominio.extension.Oraculo;
import io.github.physanto.opsecengine.dominio.resultado.Confianza;
import io.github.physanto.opsecengine.dominio.resultado.EstadoVeredicto;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoCaso;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoSuite;
import io.github.physanto.opsecengine.dominio.traza.TipoEvento;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import io.github.physanto.opsecengine.nucleo.evaluacion.EvaluacionService;
import io.github.physanto.opsecengine.nucleo.extensiones.RegistroExtensiones;
import io.github.physanto.opsecengine.nucleo.sesion.SesionService;
import io.github.physanto.opsecengine.protocolo.TipoMensaje;
import io.github.physanto.opsecengine.protocolo.dto.CodigoResultado;
import io.github.physanto.opsecengine.protocolo.dto.EventoDto;
import io.github.physanto.opsecengine.protocolo.dto.ResultadoPasoDto;
import io.github.physanto.opsecengine.soporte.AgenteFalso;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Prueba las esperas y sus reintentos sin repetir acciones de la aplicación. */
class EsperasEjecucionServiceTest {

    private static final Proyecto PROYECTO =
            new Proyecto(
                    "prueba", Path.of("app.jar"), null, null, null, null, null, null, null, null);
    private static final Duration PLAZO = Duration.ofSeconds(2);
    private static final ResultadoPasoDto OK = new ResultadoPasoDto(CodigoResultado.OK, null, 1);
    private static final ResultadoPasoDto AGOTADO =
            new ResultadoPasoDto(CodigoResultado.TIEMPO_AGOTADO, "No apareció el objetivo", 10);
    private static final Paso ESPERAR =
            new Paso(1, Accion.ESPERAR, Localizador.porTexto("Menú"), null, PLAZO, null);
    private static final Paso PULSAR =
            new Paso(2, Accion.PULSAR, Localizador.porTexto("Continuar"), null, PLAZO, null);
    private final List<ProgresoEjecucion> avisos = new ArrayList<>();

    @Test
    void aceptaUnaRespuestaTardiaDentroDelPlazoDeLaCondicion() {
        AtomicInteger recibidos = new AtomicInteger();
        ResultadoSuite resultado =
                ejecutar(
                        0,
                        Duration.ofMillis(50),
                        agente ->
                                agente.cuandoPaso(
                                                paso -> {
                                                    recibidos.incrementAndGet();
                                                    assertThat(paso.getEsperaMaximaMs())
                                                            .isEqualTo(PLAZO.toMillis());
                                                    demorar(150);
                                                    return true;
                                                })
                                        .responder(OK),
                        List.of(),
                        ESPERAR);

        assertThat(resultado.casos().get(0).veredicto().estado()).isEqualTo(EstadoVeredicto.CUMPLE);
        assertThat(recibidos).hasValue(1);
    }

    @Test
    void conservaElTimeoutYReintentaEnLaMismaSesionHastaObtenerOk() {
        AtomicInteger recibidos = new AtomicInteger();
        ResultadoSuite resultado =
                ejecutar(
                        1,
                        PLAZO,
                        agente -> {
                            agente.cuandoPaso(paso -> recibidos.incrementAndGet() == 1)
                                    .responder(AGOTADO);
                            agente.cuandoPaso(paso -> true).responder(OK);
                        },
                        List.of(),
                        ESPERAR,
                        PULSAR);

        ResultadoCaso caso = resultado.casos().get(0);
        assertThat(caso.veredicto().estado()).isEqualTo(EstadoVeredicto.CUMPLE);
        assertThat(caso.intentos()).isEqualTo(1);
        assertThat(caso.inestable()).isFalse();
        assertThat(recibidos).hasValue(3);
        assertThat(caso.traza().deTipo(TipoEvento.PASO_FIN))
                .extracting(evento -> evento.datos().get("codigo"))
                .containsExactly("TIEMPO_AGOTADO", "OK", "OK");
        assertThat(caso.traza().deTipo(TipoEvento.PASO_INICIO))
                .extracting(evento -> evento.contexto().numeroPaso())
                .containsExactly(1, 1, 2);
        assertThat(avisos.stream().filter(aviso -> aviso.tipo() == ProgresoEjecucion.Tipo.PASO_FIN))
                .extracting(ProgresoEjecucion::codigo)
                .containsExactly(
                        CodigoResultado.TIEMPO_AGOTADO, CodigoResultado.OK, CodigoResultado.OK);
    }

    @Test
    void agotarLosReintentosDetieneElCasoAntesDelPasoSiguiente() {
        AtomicInteger recibidos = new AtomicInteger();
        ResultadoSuite resultado =
                ejecutar(
                        2,
                        PLAZO,
                        agente ->
                                agente.cuandoPaso(
                                                paso -> {
                                                    recibidos.incrementAndGet();
                                                    return true;
                                                })
                                        .responder(AGOTADO),
                        List.of(),
                        ESPERAR,
                        PULSAR);

        ResultadoCaso caso = resultado.casos().get(0);
        assertThat(recibidos).hasValue(3);
        assertThat(caso.veredicto().estado()).isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
        assertThat(caso.veredicto().motivo()).contains("TIEMPO_AGOTADO", "No apareció el objetivo");
        assertThat(caso.traza().deTipo(TipoEvento.PASO_FIN)).hasSize(3);
        assertThat(caso.traza().eventos())
                .allSatisfy(evento -> assertThat(evento.contexto().numeroPaso()).isEqualTo(1));
    }

    @Test
    void elConstructorOriginalNoReintenta() {
        try (AgenteFalso agente = new AgenteFalso();
                SesionService sesiones = sesiones(agente, PLAZO)) {
            agente.respuestaPorDefecto(AGOTADO);
            ResultadoSuite resultado =
                    new EjecucionService(PROYECTO, sesiones, evaluacion(List.of()))
                            .ejecutar(suite(ESPERAR));
            assertThat(resultado.casos().get(0).traza().deTipo(TipoEvento.PASO_FIN)).hasSize(1);
            assertThat(
                            agente.mensajesRecibidos().stream()
                                    .filter(mensaje ->
                                            mensaje.getTipo() == TipoMensaje.EJECUTAR_PASO))
                    .hasSize(1);
        }
    }

    @ParameterizedTest
    @EnumSource(
            value = CodigoResultado.class,
            names = {"ERROR", "NO_ENCONTRADO", "NO_ALCANZABLE"})
    void noReintentaOtrosErroresDeEspera(CodigoResultado codigo) {
        ResultadoSuite resultado =
                ejecutar(
                        2,
                        PLAZO,
                        agente ->
                                agente.respuestaPorDefecto(
                                        new ResultadoPasoDto(codigo, "fallo real", 1)),
                        List.of(),
                        ESPERAR,
                        PULSAR);

        ResultadoCaso caso = resultado.casos().get(0);
        assertThat(caso.traza().deTipo(TipoEvento.PASO_FIN)).hasSize(1);
        assertThat(caso.veredicto().motivo()).contains(codigo.name(), "fallo real");
    }

    @Test
    void noRepiteUnClicQueAgotaElTiempo() {
        ResultadoSuite resultado =
                ejecutar(
                        3, PLAZO, agente -> agente.respuestaPorDefecto(AGOTADO), List.of(), PULSAR);

        assertThat(resultado.casos().get(0).traza().deTipo(TipoEvento.PASO_FIN)).hasSize(1);
    }

    @Test
    void noReintentaSiLaRespuestaNoLlegaDentroDelPlazoDeComunicacion() {
        Paso esperaBreve =
                new Paso(1, Accion.ESPERAR, ESPERAR.objetivo(), null, Duration.ofMillis(10), null);
        ResultadoSuite resultado =
                ejecutar(
                        2,
                        Duration.ofMillis(50),
                        agente ->
                                agente.cuandoPaso(
                                                paso -> {
                                                    demorar(500);
                                                    return true;
                                                })
                                        .responder(OK),
                        List.of(),
                        esperaBreve);

        ResultadoCaso caso = resultado.casos().get(0);
        assertThat(caso.veredicto().estado()).isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
        assertThat(caso.traza().deTipo(TipoEvento.PASO_FIN))
                .singleElement()
                .satisfies(evento -> assertThat(evento.datos()).containsEntry("codigo", "ERROR"));
    }

    @Test
    void conservaLosHallazgosDeUnIntentoAunqueLaEsperaSeRecupere() {
        AtomicInteger recibidos = new AtomicInteger();
        EventoDto excepcion = new EventoDto(0, 1, "EXCEPCION", Map.of("mensaje", "fallo real"));
        ResultadoSuite resultado =
                ejecutar(
                        1,
                        PLAZO,
                        agente -> {
                            agente.cuandoPaso(paso -> recibidos.incrementAndGet() == 1)
                                    .emitir(excepcion)
                                    .responder(AGOTADO);
                            agente.cuandoPaso(paso -> true).responder(OK);
                        },
                        List.of(new OraculoExcepciones()),
                        ESPERAR);

        ResultadoCaso caso = resultado.casos().get(0);
        assertThat(caso.veredicto().estado()).isEqualTo(EstadoVeredicto.NO_CUMPLE);
        assertThat(caso.veredicto().hallazgos())
                .singleElement()
                .satisfies(hallazgo -> assertThat(hallazgo.evidencia()).containsExactly(1L));
        assertThat(caso.traza().deTipo(TipoEvento.PASO_FIN))
                .extracting(evento -> evento.datos().get("codigo"))
                .containsExactly("TIEMPO_AGOTADO", "OK");
    }

    @Test
    void unaInterrupcionImpideReintentarYSeConserva() {
        try (AgenteFalso agente = new AgenteFalso();
                SesionService sesiones = sesiones(agente, PLAZO)) {
            agente.respuestaPorDefecto(AGOTADO);
            ObservadorProgreso observador =
                    aviso -> {
                        if (aviso.tipo() == ProgresoEjecucion.Tipo.PASO_FIN) {
                            Thread.currentThread().interrupt();
                        }
                    };
            ResultadoSuite resultado =
                    new EjecucionService(PROYECTO, sesiones, evaluacion(List.of()), observador, 2)
                            .ejecutar(suite(ESPERAR));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(resultado.casos().get(0).traza().deTipo(TipoEvento.PASO_FIN)).hasSize(1);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void rechazaUnNumeroNegativoDeReintentos() {
        assertThatThrownBy(() -> new EjecucionService(PROYECTO, null, null, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negativos");
    }

    private ResultadoSuite ejecutar(
            int reintentos,
            Duration margen,
            Consumer<AgenteFalso> configurar,
            List<Oraculo> oraculos,
            Paso... pasos) {
        try (AgenteFalso agente = new AgenteFalso();
                SesionService sesiones = sesiones(agente, margen)) {
            configurar.accept(agente);
            return new EjecucionService(
                            PROYECTO, sesiones, evaluacion(oraculos), avisos::add, reintentos)
                    .ejecutar(suite(pasos));
        }
    }

    private static SesionService sesiones(AgenteFalso agente, Duration margen) {
        return new SesionService(
                (proyecto, puerto, token) -> {
                    agente.conectar(puerto, token);
                    return agente::close;
                },
                PLAZO,
                margen);
    }

    private static EvaluacionService evaluacion(List<Oraculo> oraculos) {
        return new EvaluacionService(
                new RegistroExtensiones(oraculos, List.of(), List.of(), List.of()));
    }

    private static Suite suite(Paso... pasos) {
        return new Suite(
                "esperas",
                "1",
                null,
                null,
                List.of(new Caso("C1", null, null, List.of(pasos), null, null, null)),
                null);
    }

    private static void demorar(long milisegundos) {
        try {
            TimeUnit.MILLISECONDS.sleep(milisegundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Se interrumpió la respuesta del agente falso", e);
        }
    }

    /** Detecta excepciones incluso si aparecen en un intento de espera recuperado. */
    private static final class OraculoExcepciones implements Oraculo {
        @Override
        public String id() {
            return "EXCEPCION-PRUEBA";
        }

        @Override
        public Momento momento() {
            return Momento.EVENTO;
        }

        @Override
        public boolean aplica(Caso caso, Contexto contexto) {
            return true;
        }

        @Override
        public List<Hallazgo> evaluar(Traza traza, Contexto contexto) {
            return traza.deTipo(TipoEvento.EXCEPCION).stream()
                    .map(evento ->
                            new Hallazgo(
                                    id(),
                                    null,
                                    Confianza.CONFIRMADO,
                                    "Excepción",
                                    "Fallo de la aplicación",
                                    List.of(evento.secuencia())))
                    .toList();
        }
    }
}
