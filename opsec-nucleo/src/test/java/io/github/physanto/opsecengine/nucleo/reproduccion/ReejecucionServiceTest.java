package io.github.physanto.opsecengine.nucleo.reproduccion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.physanto.opsecengine.dominio.caso.Accion;
import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.ConfigEjecucion;
import io.github.physanto.opsecengine.dominio.caso.Criterio;
import io.github.physanto.opsecengine.dominio.caso.EstrategiaLocalizacion;
import io.github.physanto.opsecengine.dominio.caso.Localizador;
import io.github.physanto.opsecengine.dominio.caso.ModoAccion;
import io.github.physanto.opsecengine.dominio.caso.Paso;
import io.github.physanto.opsecengine.dominio.caso.Suite;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.dominio.resultado.EstadoVeredicto;
import io.github.physanto.opsecengine.dominio.resultado.Manifiesto;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoSuite;
import io.github.physanto.opsecengine.nucleo.ejecucion.EjecucionService;
import io.github.physanto.opsecengine.nucleo.evaluacion.EvaluacionService;
import io.github.physanto.opsecengine.nucleo.extensiones.RegistroExtensiones;
import io.github.physanto.opsecengine.nucleo.generacion.SuiteYaml;
import io.github.physanto.opsecengine.nucleo.sesion.ArranqueApp;
import io.github.physanto.opsecengine.nucleo.sesion.SesionService;
import io.github.physanto.opsecengine.protocolo.dto.CodigoResultado;
import io.github.physanto.opsecengine.protocolo.dto.ResultadoPasoDto;
import io.github.physanto.opsecengine.soporte.AgenteFalso;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pruebas del manifiesto guardado y de la re-ejecución exacta, contra {@link AgenteFalso}. */
class ReejecucionServiceTest {

    private static final Duration ESPERA = Duration.ofSeconds(5);

    @TempDir Path carpeta;

    private final List<AgenteFalso> agentes = new CopyOnWriteArrayList<>();
    private final ArranqueApp arranque =
            (proyecto, puerto, token) -> {
                AgenteFalso agente = new AgenteFalso();
                agente.cuandoPaso(paso -> true)
                        .responder(new ResultadoPasoDto(CodigoResultado.OK, null, 4));
                agentes.add(agente);
                agente.conectar(puerto, token);
                return agente::close;
            };

    @AfterEach
    void cerrarAgentes() {
        agentes.forEach(AgenteFalso::close);
    }

    @Test
    void repetirUnResultadoDaElMismoVeredictoYElMismoManifiesto() throws Exception {
        Proyecto proyecto = proyecto(jar("app-v1"));
        Suite suite = suite(7L);
        Path resultados = carpeta.resolve("resultados");
        ReejecucionService servicio = new ReejecucionService();
        try (SesionService sesiones = new SesionService(arranque, ESPERA, ESPERA)) {
            EjecucionService ejecucion = ejecucion(proyecto, sesiones);
            ResultadoSuite original = ejecucion.ejecutar(suite);
            servicio.registrar(original, suite, resultados);

            ResultadoReejecucion repetido =
                    servicio.reejecutar(resultados, proyecto, ejecucion, false);

            assertThat(repetido.discrepancias()).isEmpty();
            assertThat(ReejecucionService.casosConVeredictoDistinto(original, repetido.resultado()))
                    .isEmpty();
            assertThat(repetido.resultado().casos())
                    .allSatisfy(caso ->
                            assertThat(caso.veredicto().estado())
                                    .isEqualTo(EstadoVeredicto.CUMPLE));
            assertThat(repetido.resultado().manifiesto().hashSuite())
                    .isEqualTo(original.manifiesto().hashSuite());
            assertThat(repetido.resultado().manifiesto().hashJar())
                    .isEqualTo(original.manifiesto().hashJar());
            assertThat(repetido.resultado().manifiesto().semilla()).isEqualTo(7L);
        }
    }

    @Test
    void unaSuiteQueNoCabeEnYamlSeRechazaConLosPasosYSinEscribirNada() throws Exception {
        Paso llenarPorNombre =
                new Paso(
                        2,
                        Accion.LLENAR,
                        new Localizador(
                                List.of(
                                        new Criterio(
                                                EstrategiaLocalizacion.NOMBRE_COMPONENTE,
                                                "campo"))),
                        "x",
                        null,
                        null);
        Paso pulsar = new Paso(1, Accion.PULSAR, Localizador.porTexto("Avanzar"), null, null, null);
        Suite suite =
                new Suite(
                        "no-cabe",
                        "1",
                        null,
                        null,
                        List.of(
                                new Caso(
                                        "K1",
                                        null,
                                        null,
                                        List.of(pulsar, llenarPorNombre),
                                        null,
                                        null,
                                        null)),
                        null);
        Path resultados = carpeta.resolve("rechazada");
        try (SesionService sesiones = new SesionService(arranque, ESPERA, ESPERA)) {
            ResultadoSuite original = ejecucion(proyecto(jar("app-v1")), sesiones).ejecutar(suite);

            assertThat(SuiteYaml.noExpresables(suite))
                    .hasSize(1)
                    .first()
                    .asString()
                    .contains("K1", "paso 2");
            assertThatThrownBy(
                            () -> new ReejecucionService().registrar(original, suite, resultados))
                    .isInstanceOf(ExcepcionReejecucion.class)
                    .hasMessageContaining("no se escribió nada")
                    .hasMessageContaining("K1");
            assertThat(resultados).doesNotExist();
        }
    }

    @Test
    void unaSuiteNormalNoTienePasosNoExpresables() {
        assertThat(SuiteYaml.noExpresables(suite(1L))).isEmpty();
    }

    @Test
    void guardaElManifiestoYLaSuiteEnLaCarpeta() throws Exception {
        Proyecto proyecto = proyecto(jar("app-v1"));
        Suite suite = suite(3L);
        Path resultados = carpeta.resolve("nueva").resolve("r1");
        try (SesionService sesiones = new SesionService(arranque, ESPERA, ESPERA)) {
            ResultadoSuite original = ejecucion(proyecto, sesiones).ejecutar(suite);
            new ReejecucionService().registrar(original, suite, resultados);

            Manifiesto leido =
                    ManifiestoJson.leer(resultados.resolve(ReejecucionService.ARCHIVO_MANIFIESTO));
            assertThat(leido).isEqualTo(original.manifiesto());
            assertThat(resultados.resolve(ReejecucionService.ARCHIVO_SUITE)).isRegularFile();
        }
    }

    @Test
    void noRepiteSiCambioLaAplicacion() throws Exception {
        Path jar = jar("app-v1");
        Proyecto proyecto = proyecto(jar);
        Suite suite = suite(1L);
        Path resultados = carpeta.resolve("resultados");
        ReejecucionService servicio = new ReejecucionService();
        try (SesionService sesiones = new SesionService(arranque, ESPERA, ESPERA)) {
            EjecucionService ejecucion = ejecucion(proyecto, sesiones);
            servicio.registrar(ejecucion.ejecutar(suite), suite, resultados);
            Files.writeString(jar, "app-v2", StandardCharsets.UTF_8);
            int sesionesAntes = agentes.size();

            assertThatThrownBy(() -> servicio.reejecutar(resultados, proyecto, ejecucion, false))
                    .isInstanceOf(ExcepcionReejecucion.class)
                    .hasMessageContaining("hashJar");
            assertThat(agentes).hasSize(sesionesAntes);

            ResultadoReejecucion forzado =
                    servicio.reejecutar(resultados, proyecto, ejecucion, true);
            assertThat(forzado.discrepancias())
                    .singleElement()
                    .satisfies(d -> {
                        assertThat(d.campo()).isEqualTo("hashJar");
                        assertThat(d.bloqueante()).isTrue();
                    });
        }
    }

    @Test
    void noRepiteSiSeAlteroLaSuiteGuardada() throws Exception {
        Proyecto proyecto = proyecto(jar("app-v1"));
        Suite suite = suite(1L);
        Path resultados = carpeta.resolve("resultados");
        ReejecucionService servicio = new ReejecucionService();
        try (SesionService sesiones = new SesionService(arranque, ESPERA, ESPERA)) {
            EjecucionService ejecucion = ejecucion(proyecto, sesiones);
            servicio.registrar(ejecucion.ejecutar(suite), suite, resultados);
            Path archivoSuite = resultados.resolve(ReejecucionService.ARCHIVO_SUITE);
            Files.writeString(
                    archivoSuite,
                    Files.readString(archivoSuite, StandardCharsets.UTF_8)
                            .replace("Avanzar", "Otro"),
                    StandardCharsets.UTF_8);

            assertThatThrownBy(() -> servicio.reejecutar(resultados, proyecto, ejecucion, false))
                    .isInstanceOf(ExcepcionReejecucion.class)
                    .hasMessageContaining("hashSuite");
        }
    }

    @Test
    void faltarUnArchivoGuardadoEsUnErrorClaro() throws Exception {
        Proyecto proyecto = proyecto(jar("app-v1"));
        assertThatThrownBy(() -> new ReejecucionService().cargar(carpeta, proyecto))
                .isInstanceOf(ExcepcionReejecucion.class)
                .hasMessageContaining(ReejecucionService.ARCHIVO_MANIFIESTO);
    }

    @Test
    void lasVersionesDistintasSoloAvisan() {
        Manifiesto guardado =
                new Manifiesto(
                        "sha256:a",
                        "0.1.0",
                        "17.0.1",
                        "Linux 6",
                        2L,
                        "sha256:b",
                        java.time.Instant.parse("2026-11-19T10:00:00Z"));
        Manifiesto actual =
                new Manifiesto(
                        "sha256:a", "0.2.0", "21.0.1", "Linux 7", 2L, "sha256:b", guardado.fecha());
        assertThat(ReejecucionService.comparar(guardado, actual))
                .extracting(Discrepancia::campo)
                .containsExactly("versionMotor", "versionJava", "sistemaOperativo");
        assertThat(ReejecucionService.comparar(guardado, actual))
                .noneMatch(Discrepancia::bloqueante);
    }

    private EjecucionService ejecucion(Proyecto proyecto, SesionService sesiones) {
        return new EjecucionService(
                proyecto,
                sesiones,
                new EvaluacionService(
                        new RegistroExtensiones(List.of(), List.of(), List.of(), List.of())));
    }

    private Path jar(String contenido) throws IOException {
        Path jar = carpeta.resolve("app.jar");
        Files.writeString(jar, contenido, StandardCharsets.UTF_8);
        return jar;
    }

    private static Proyecto proyecto(Path jar) {
        return new Proyecto(
                "clientes", jar, "app.Portada", null, null, null, null, null, null, null);
    }

    private static Suite suite(long semilla) {
        ConfigEjecucion config =
                new ConfigEjecucion(ModoAccion.SEMANTICO, false, ESPERA, 0, semilla, 1);
        Paso paso = new Paso(1, Accion.PULSAR, Localizador.porTexto("Avanzar"), null, null, null);
        return new Suite(
                "repetible",
                "1",
                null,
                config,
                List.of(
                        new Caso("A", null, null, List.of(paso), null, null, null),
                        new Caso("B", null, null, List.of(paso), null, null, null)),
                null);
    }
}
