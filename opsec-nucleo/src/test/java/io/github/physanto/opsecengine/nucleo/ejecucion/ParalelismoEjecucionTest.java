package io.github.physanto.opsecengine.nucleo.ejecucion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import io.github.physanto.opsecengine.dominio.resultado.ResultadoSuite;
import io.github.physanto.opsecengine.nucleo.evaluacion.EvaluacionService;
import io.github.physanto.opsecengine.nucleo.extensiones.RegistroExtensiones;
import io.github.physanto.opsecengine.nucleo.sesion.ArranqueApp;
import io.github.physanto.opsecengine.nucleo.sesion.SesionService;
import io.github.physanto.opsecengine.protocolo.dto.CodigoResultado;
import io.github.physanto.opsecengine.protocolo.dto.ResultadoPasoDto;
import io.github.physanto.opsecengine.soporte.AgenteFalso;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Pruebas de la ejecución de casos en paralelo, cada uno con su propia sesión. */
class ParalelismoEjecucionTest {

    private static final Duration ESPERA = Duration.ofSeconds(10);
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
    void conParalelismoTresLosTresCasosEstanAbiertosALaVez() {
        // La barrera solo se supera si las tres aplicaciones se arrancan al mismo tiempo.
        CyclicBarrier barrera = new CyclicBarrier(3);
        Escenario escenario = new Escenario(barrera);

        ResultadoSuite resultado =
                escenario.ejecutar(
                        3, ModoAccion.EVENTOS, caso("A", null), caso("B", null), caso("C", null));

        assertThat(resultado.casos())
                .extracting(ResultadoCaso::casoId)
                .containsExactly("A", "B", "C");
        assertThat(resultado.casos())
                .extracting(c -> c.veredicto().estado())
                .containsOnly(EstadoVeredicto.CUMPLE);
        assertThat(escenario.maximoSimultaneo.get()).isEqualTo(3);
    }

    @Test
    void conParalelismoUnoLosCasosSeEjecutanDeAUno() {
        Escenario escenario = new Escenario(null);

        ResultadoSuite resultado =
                escenario.ejecutar(
                        1, ModoAccion.EVENTOS, caso("A", null), caso("B", null), caso("C", null));

        assertThat(resultado.casos()).hasSize(3);
        assertThat(escenario.maximoSimultaneo.get()).isEqualTo(1);
    }

    @Test
    void elResultadoConservaElOrdenDeLaSuiteAunqueTerminenDesordenados() {
        Escenario escenario = new Escenario(null);
        // El primer caso tarda más que los demás en abrir su sesión.
        escenario.demoraPorCaso.put(0, 300L);

        ResultadoSuite resultado =
                escenario.ejecutar(
                        3, ModoAccion.EVENTOS, caso("A", null), caso("B", null), caso("C", null));

        assertThat(resultado.casos())
                .extracting(ResultadoCaso::casoId)
                .containsExactly("A", "B", "C");
    }

    @Test
    void losCasosDelMismoGrupoVanEnOrdenYEnElMismoHilo() {
        Escenario escenario = new Escenario(null);

        escenario.ejecutar(
                3,
                ModoAccion.EVENTOS,
                caso("A", "uno"),
                caso("X", null),
                caso("B", "uno"),
                caso("C", "uno"));

        assertThat(escenario.hiloPorCaso.get("A"))
                .isEqualTo(escenario.hiloPorCaso.get("B"))
                .isEqualTo(escenario.hiloPorCaso.get("C"));
        assertThat(escenario.ordenDeInicio.indexOf("A"))
                .isLessThan(escenario.ordenDeInicio.indexOf("B"));
        assertThat(escenario.ordenDeInicio.indexOf("B"))
                .isLessThan(escenario.ordenDeInicio.indexOf("C"));
    }

    @Test
    void conModoRobotNoHayParalelismo() {
        Escenario escenario = new Escenario(null);

        ResultadoSuite resultado =
                escenario.ejecutar(
                        3, ModoAccion.ROBOT, caso("A", null), caso("B", null), caso("C", null));

        assertThat(resultado.casos()).hasSize(3);
        assertThat(escenario.maximoSimultaneo.get()).isEqualTo(1);
    }

    @Test
    void noHayMasHilosQueUnidadesIndependientes() {
        Escenario escenario = new Escenario(null);

        escenario.ejecutar(4, ModoAccion.EVENTOS, caso("A", "g"), caso("B", "g"), caso("C", "g"));

        assertThat(escenario.maximoSimultaneo.get()).isEqualTo(1);
    }

    @Test
    void lasNotificacionesDeProgresoNuncaSeSolapan() {
        Escenario escenario = new Escenario(null);
        AtomicInteger dentro = new AtomicInteger();
        AtomicInteger solapadas = new AtomicInteger();
        AtomicInteger recibidas = new AtomicInteger();

        escenario.ejecutarConObservador(
                progreso -> {
                    if (dentro.incrementAndGet() > 1) {
                        solapadas.incrementAndGet();
                    }
                    recibidas.incrementAndGet();
                    try {
                        Thread.sleep(2);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    dentro.decrementAndGet();
                },
                4,
                caso("A", null),
                caso("B", null),
                caso("C", null),
                caso("D", null));

        assertThat(solapadas.get()).isZero();
        // Suite (2) + caso (2 por cada uno) + paso (2 por cada uno) = 18.
        assertThat(recibidas.get()).isEqualTo(18);
    }

    @Test
    void siUnHiloFallaSeCancelanLosDemasSinEsperarAlMasLento() {
        Escenario escenario = new Escenario(null);
        // El primer arranque tarda 5 s; el fallo de otro hilo no debe esperar a que termine.
        escenario.demoraPorCaso.put(0, 5_000L);
        long comienzo = System.nanoTime();

        assertThatThrownBy(() ->
                        escenario.ejecutarConObservador(
                                progreso -> {
                                    if (progreso.tipo() == ProgresoEjecucion.Tipo.CASO_INICIO
                                            && "B".equals(progreso.casoId())) {
                                        throw new AssertionError("fallo simulado");
                                    }
                                },
                                2,
                                caso("A", null),
                                caso("B", null)))
                .isInstanceOf(AssertionError.class)
                .hasMessage("fallo simulado");

        assertThat(Duration.ofNanos(System.nanoTime() - comienzo))
                .isLessThan(Duration.ofSeconds(3));
    }

    private static Caso caso(String id, String grupo) {
        Paso paso = new Paso(1, Accion.PULSAR, Localizador.porTexto("Avanzar"), id, null, null);
        Map<String, Object> extensiones =
                grupo == null ? Map.of() : Map.of(EjecucionService.EXTENSION_GRUPO, grupo);
        return new Caso(id, null, null, List.of(paso), null, null, extensiones);
    }

    /** Arma las sesiones con agentes falsos y registra cómo se usaron. */
    private final class Escenario {

        private final CyclicBarrier barrera;
        private final AtomicInteger activos = new AtomicInteger();
        private final AtomicInteger maximoSimultaneo = new AtomicInteger();
        private final AtomicInteger arranques = new AtomicInteger();
        private final Map<Integer, Long> demoraPorCaso = new ConcurrentHashMap<>();
        private final Map<String, String> hiloPorCaso = new ConcurrentHashMap<>();
        private final List<String> ordenDeInicio = new CopyOnWriteArrayList<>();

        Escenario(CyclicBarrier barrera) {
            this.barrera = barrera;
        }

        ResultadoSuite ejecutar(int paralelismo, ModoAccion modo, Caso... casos) {
            return ejecutarConObservador(ObservadorProgreso.VACIO, paralelismo, modo, casos);
        }

        ResultadoSuite ejecutarConObservador(
                ObservadorProgreso observador, int paralelismo, Caso... casos) {
            return ejecutarConObservador(observador, paralelismo, ModoAccion.EVENTOS, casos);
        }

        private ResultadoSuite ejecutarConObservador(
                ObservadorProgreso observador, int paralelismo, ModoAccion modo, Caso... casos) {
            ArranqueApp arranque =
                    (proyecto, puerto, token) -> {
                        int numero = arranques.getAndIncrement();
                        String hilo = Thread.currentThread().getName();
                        int ahora = activos.incrementAndGet();
                        maximoSimultaneo.accumulateAndGet(ahora, Math::max);
                        try {
                            if (barrera != null) {
                                barrera.await(ESPERA.toSeconds(), TimeUnit.SECONDS);
                            }
                            Thread.sleep(demoraPorCaso.getOrDefault(numero, 20L));
                        } catch (Exception e) {
                            activos.decrementAndGet();
                            throw new java.io.IOException("No se pudo arrancar", e);
                        }
                        AgenteFalso agente = new AgenteFalso();
                        agente.cuandoPaso(
                                        paso -> {
                                            hiloPorCaso.put(paso.getValor(), hilo);
                                            ordenDeInicio.add(paso.getValor());
                                            return true;
                                        })
                                .responder(new ResultadoPasoDto(CodigoResultado.OK, null, 1));
                        agentes.add(agente);
                        agente.conectar(puerto, token);
                        return () -> {
                            activos.decrementAndGet();
                            agente.close();
                        };
                    };
            ConfigEjecucion config = new ConfigEjecucion(modo, false, ESPERA, 0, 0L, paralelismo);
            Suite suite =
                    new Suite("paralela", "1", null, config, new ArrayList<>(List.of(casos)), null);
            EvaluacionService evaluacion =
                    new EvaluacionService(
                            new RegistroExtensiones(List.of(), List.of(), List.of(), List.of()));
            try (SesionService sesiones = new SesionService(arranque, ESPERA, ESPERA)) {
                return new EjecucionService(PROYECTO, sesiones, evaluacion, observador)
                        .ejecutar(suite);
            }
        }
    }
}
