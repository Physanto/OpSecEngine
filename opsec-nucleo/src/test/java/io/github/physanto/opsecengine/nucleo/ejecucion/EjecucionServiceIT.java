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
import io.github.physanto.opsecengine.dominio.resultado.ResultadoSuite;
import io.github.physanto.opsecengine.dominio.traza.TipoEvento;
import io.github.physanto.opsecengine.nucleo.evaluacion.EvaluacionService;
import io.github.physanto.opsecengine.nucleo.extensiones.RegistroExtensiones;
import io.github.physanto.opsecengine.nucleo.sesion.ClienteAgente;
import io.github.physanto.opsecengine.nucleo.sesion.LanzadorProceso;
import io.github.physanto.opsecengine.nucleo.sesion.SesionService;
import io.github.physanto.opsecengine.protocolo.dto.HolaDto;
import io.github.physanto.opsecengine.soporte.AplicacionDePrueba;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

/**
 * Prueba de integración: lanza {@link AplicacionDePrueba} en otra JVM con el agente real.
 *
 * <p>La ejecuta failsafe en la fase {@code integration-test}. La ruta del jar del agente llega en
 * la propiedad del sistema {@code opsec.agente.jar}. {@link AplicacionDePrueba} no tiene ventanas,
 * así que el agente no encuentra el botón del paso y el caso queda {@code NO_CONCLUYENTE}; la línea
 * que la aplicación escribe al arrancar llega a la traza como evento {@code CONSOLA}.
 */
class EjecucionServiceIT {

    private static final Duration ESPERA = Duration.ofSeconds(30);

    private Path resultados;

    @BeforeEach
    void crearCarpeta(TestInfo prueba) throws IOException {
        resultados = carpetaRegistros(prueba);
    }

    @Test
    void ejecutaUnaSuiteContraLaAplicacionConElAgente() throws Exception {
        Paso pulsar = new Paso(1, Accion.PULSAR, Localizador.porTexto("Avanzar"), null, null, null);
        Suite suite =
                new Suite(
                        "humo",
                        null,
                        null,
                        new ConfigEjecucion(
                                ModoAccion.SEMANTICO, false, Duration.ofSeconds(1), 0, 0L, 1),
                        List.of(new Caso("HUMO", null, null, List.of(pulsar), null, null, null)),
                        null);
        EvaluacionService evaluacion = new EvaluacionService(RegistroExtensiones.descubrir());

        ResultadoSuite resultado;
        try (SesionService sesiones =
                new SesionService(new LanzadorProceso(jarAgente(), resultados), ESPERA, ESPERA)) {
            resultado = new EjecucionService(proyecto(), sesiones, evaluacion).ejecutar(suite);
        }

        ResultadoCaso caso = resultado.casos().get(0);
        assertThat(caso.veredicto().estado()).isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
        assertThat(caso.veredicto().motivo()).contains("NO_ENCONTRADO", "Avanzar");
        assertThat(caso.traza().eventos())
                .extracting(evento -> evento.tipo())
                .containsSubsequence(TipoEvento.PASO_INICIO, TipoEvento.PASO_FIN)
                .contains(TipoEvento.CONSOLA);
        assertThat(resultado.manifiesto().hashJar()).startsWith("sha256:").hasSize(71);
        assertThat(resultados.resolve(LanzadorProceso.ARCHIVO_ERRORES)).isRegularFile();
        assertThat(
                        Files.readString(
                                resultados.resolve(LanzadorProceso.ARCHIVO_ERRORES),
                                StandardCharsets.UTF_8))
                .contains(AplicacionDePrueba.LINEA_INICIO);
    }

    @Test
    void elLanzadorArrancaLaAplicacionConElJavaDelProyectoYLaDetiene() throws Exception {
        LanzadorProceso lanzador = new LanzadorProceso(jarAgente(), resultados);

        try (ClienteAgente cliente = new ClienteAgente()) {
            Proyecto conJavaPropio =
                    new Proyecto(
                            "eco",
                            proyecto().jar(),
                            AplicacionDePrueba.class.getName(),
                            null,
                            Path.of(System.getProperty("java.home")),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null);
            Process proceso = lanzador.lanzar(conJavaPropio, cliente.puerto(), cliente.token());
            try {
                HolaDto hola = cliente.esperarHola(ESPERA);

                assertThat(hola.getVersionAgente()).isNotEqualTo("desarrollo");
                assertThat(cliente.cerrar()).isTrue();
            } finally {
                LanzadorProceso.detener(proceso);
            }
            assertThat(proceso.isAlive()).isFalse();
        }
    }

    private static Path jarAgente() {
        Path jar = Path.of(System.getProperty("opsec.agente.jar"));
        assertThat(jar).isRegularFile();
        return jar;
    }

    /** Proyecto cuya aplicación es {@link AplicacionDePrueba}, que solo usa el JDK. */
    private static Proyecto proyecto() throws Exception {
        Path clases =
                Path.of(
                        AplicacionDePrueba.class
                                .getProtectionDomain()
                                .getCodeSource()
                                .getLocation()
                                .toURI());
        return new Proyecto(
                "humo",
                clases,
                AplicacionDePrueba.class.getName(),
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    /**
     * Carpeta para la salida de la aplicación lanzada, dentro de {@code target/registros-it}.
     *
     * <p>No se usa {@code @TempDir}: en Windows el archivo de salida puede seguir bloqueado unos
     * instantes después de detener el proceso, y JUnit falla al borrar la carpeta temporal.
     */
    private static Path carpetaRegistros(TestInfo prueba) throws IOException {
        String nombre = prueba.getTestMethod().map(metodo -> metodo.getName()).orElse("prueba");
        return Files.createDirectories(
                Path.of("target", "registros-it", nombre + "-" + System.nanoTime()));
    }
}
