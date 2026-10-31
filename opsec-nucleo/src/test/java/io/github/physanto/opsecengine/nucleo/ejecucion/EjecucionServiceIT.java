package io.github.physanto.opsecengine.nucleo.ejecucion;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.physanto.opsecengine.dominio.caso.Accion;
import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.Localizador;
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
import io.github.physanto.opsecengine.protocolo.dto.ErrorDto;
import io.github.physanto.opsecengine.protocolo.dto.HolaDto;
import io.github.physanto.opsecengine.soporte.AplicacionDePrueba;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Prueba de integración: lanza {@link AplicacionDePrueba} en otra JVM con el agente eco real.
 *
 * <p>La ejecuta failsafe en la fase {@code integration-test}. La ruta del jar del agente llega en
 * la propiedad del sistema {@code opsec.agente.jar}. El agente eco saluda pero responde {@code
 * ERROR} con {@code NO_IMPLEMENTADO} a cada paso, así que el caso queda {@code NO_CONCLUYENTE}.
 */
class EjecucionServiceIT {

    private static final Duration ESPERA = Duration.ofSeconds(30);

    @TempDir Path resultados;

    @Test
    void ejecutaUnaSuiteContraLaAplicacionConElAgenteEco() throws Exception {
        Paso pulsar = new Paso(1, Accion.PULSAR, Localizador.porTexto("Avanzar"), null, null, null);
        Suite suite =
                new Suite(
                        "eco",
                        null,
                        null,
                        null,
                        List.of(new Caso("ECO", null, null, List.of(pulsar), null, null, null)),
                        null);
        EvaluacionService evaluacion = new EvaluacionService(RegistroExtensiones.descubrir());

        ResultadoSuite resultado;
        try (SesionService sesiones =
                new SesionService(new LanzadorProceso(jarAgente(), resultados), ESPERA, ESPERA)) {
            resultado = new EjecucionService(proyecto(), sesiones, evaluacion).ejecutar(suite);
        }

        ResultadoCaso caso = resultado.casos().get(0);
        assertThat(caso.veredicto().estado()).isEqualTo(EstadoVeredicto.NO_CONCLUYENTE);
        assertThat(caso.veredicto().motivo()).contains(ErrorDto.NO_IMPLEMENTADO);
        assertThat(caso.traza().eventos())
                .extracting(evento -> evento.tipo())
                .containsExactly(TipoEvento.PASO_INICIO, TipoEvento.PASO_FIN);
        assertThat(resultado.manifiesto().hashJar()).startsWith("sha256:").hasSize(71);
        assertThat(resultados.resolve(LanzadorProceso.ARCHIVO_ERRORES)).isRegularFile();
        assertThat(
                        Files.readString(
                                resultados.resolve(LanzadorProceso.ARCHIVO_ERRORES),
                                StandardCharsets.UTF_8))
                .contains(AplicacionDePrueba.LINEA_INICIO);
    }

    @Test
    void elLanzadorArrancaLaAplicacionYLaDetiene() throws Exception {
        LanzadorProceso lanzador = new LanzadorProceso(jarAgente(), resultados);

        try (ClienteAgente cliente = new ClienteAgente()) {
            Process proceso = lanzador.lanzar(proyecto(), cliente.puerto(), cliente.token());
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
                "eco",
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
}
