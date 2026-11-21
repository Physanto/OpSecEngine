package io.github.physanto.opsecengine.nucleo.ejecucion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.Suite;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoCaso;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoSuite;
import io.github.physanto.opsecengine.dominio.traza.Evento;
import io.github.physanto.opsecengine.dominio.traza.TipoEvento;
import io.github.physanto.opsecengine.nucleo.evaluacion.EvaluacionService;
import io.github.physanto.opsecengine.nucleo.extensiones.RegistroExtensiones;
import io.github.physanto.opsecengine.nucleo.proyecto.ProyectoService;
import io.github.physanto.opsecengine.nucleo.sesion.LanzadorProceso;
import io.github.physanto.opsecengine.nucleo.sesion.SesionService;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Prueba de aceptación de la inyección de fallos (#16) con la app de práctica real: un fallo de
 * conexión a la base, inyectado mientras la app muestra su inicio de sesión, llega al código de la
 * aplicación y se ve en su diálogo de error.
 *
 * <p>La ejecuta failsafe; se omite sin pantalla. Usa {@code demo/robustez}. Con la app vulnerable
 * el diálogo muestra la pila de llamadas (F7) y con la corregida un mensaje genérico; en ambos
 * casos los cinco pasos terminan en {@code OK}, es decir, el fallo no mata la app en su arranque.
 */
class InyeccionFallosIT {

    private static final Duration ESPERA = Duration.ofSeconds(30);

    @Test
    void elFalloDeConexionInyectadoSeVeEnElDialogoDeLaAppVulnerable() throws Exception {
        List<Evento> eventos = ejecutarR01("proyecto.yaml");

        assertThat(eventos)
                .filteredOn(e -> e.tipo() == TipoEvento.ESTADO && e.datos().containsKey("fallo"))
                .as("el agente informa que inyectó el fallo")
                .anySatisfy(e -> {
                    assertThat(e.datos()).containsEntry("fallo", "SQL_CONEXION");
                    assertThat(e.datos().get("excepcion").toString())
                            .contains("java.sql.SQLException");
                });
        assertThat(textosDeDialogo(eventos))
                .as("con F7 activo el diálogo expone la excepción inyectada")
                .anyMatch(t -> t.contains("SQLException") && t.contains("conexión inyectada"));
    }

    @Test
    void laAppCorregidaMuestraUnMensajeGenericoConElMismoFallo() throws Exception {
        List<Evento> eventos = ejecutarR01("proyecto-corregido.yaml");

        assertThat(textosDeDialogo(eventos))
                .containsExactly("No se pudo consultar la base de datos.");
    }

    private static List<String> textosDeDialogo(List<Evento> eventos) {
        return eventos.stream()
                .filter(e -> e.tipo() == TipoEvento.DIALOGO)
                .map(e -> e.datos().get("texto").toString())
                .toList();
    }

    /** Ejecuta el caso R01 de {@code demo/robustez} y devuelve los eventos de su traza. */
    private static List<Evento> ejecutarR01(String archivoProyecto) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "necesita pantalla");
        Path jarAgente = Path.of(System.getProperty("opsec.agente.jar"));
        Path carpetaDemo = Path.of("..", "demo", "robustez").normalize();
        ProyectoService proyectos = new ProyectoService();
        Proyecto proyecto = proyectos.cargarProyecto(carpetaDemo.resolve(archivoProyecto));
        assertThat(proyecto.jar())
                .as("Construir también la app de práctica con -am")
                .isRegularFile();
        Suite suite =
                proyectos.cargarSuite(
                        carpetaDemo.resolve("suites").resolve("robustez.yaml"), proyecto);
        Caso caso = suite.casos().get(0);
        assertThat(caso.fallos())
                .containsExactly("SQL_CONEXION:java.sql.SQLException:conexión inyectada");
        Path registros =
                Files.createDirectories(
                        Path.of("target", "registros-it", "robustez-" + System.nanoTime()));

        ResultadoSuite resultado;
        try (SesionService sesiones =
                new SesionService(new LanzadorProceso(jarAgente, registros), ESPERA, ESPERA)) {
            resultado =
                    new EjecucionService(
                                    proyecto,
                                    sesiones,
                                    new EvaluacionService(RegistroExtensiones.descubrir()))
                            .ejecutar(suite);
        }
        ResultadoCaso r01 = resultado.casos().get(0);
        assertThat(r01.traza().eventos())
                .filteredOn(e -> e.tipo() == TipoEvento.PASO_FIN)
                .as("los cinco pasos llegan a ejecutarse: el fallo no mata la app al arrancar")
                .hasSize(5)
                .allSatisfy(e -> assertThat(e.datos()).containsEntry("codigo", "OK"));
        return r01.traza().eventos();
    }
}
