package io.github.physanto.opsecengine.nucleo.cobertura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import io.github.physanto.opsecengine.dominio.caso.Accion;
import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.ConfigEjecucion;
import io.github.physanto.opsecengine.dominio.caso.Localizador;
import io.github.physanto.opsecengine.dominio.caso.ModoAccion;
import io.github.physanto.opsecengine.dominio.caso.Paso;
import io.github.physanto.opsecengine.dominio.caso.Suite;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoSuite;
import io.github.physanto.opsecengine.nucleo.ejecucion.EjecucionService;
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
 * Prueba de aceptación de la cobertura (#19): ejecuta una suite contra la app de práctica real con
 * el agente de JaCoCo cargado junto al del motor y comprueba que el informe muestra la cobertura de
 * la app, sumando lo que hizo cada caso en su propia aplicación.
 *
 * <p>La ejecuta failsafe; se omite sin pantalla. Necesita la app de práctica construida, el jar del
 * agente del motor ({@code opsec.agente.jar}) y el de JaCoCo ({@code opsec.jacoco.jar}).
 */
class CoberturaServiceIT {

    private static final String PRACTICA = "io.github.physanto.opsecengine.practica.";
    private static final Duration ESPERA = Duration.ofSeconds(30);

    private static Paso pulsar(int orden, String texto) {
        return new Paso(orden, Accion.PULSAR, Localizador.porTexto(texto), null, null, null);
    }

    private static Caso caso(String id, String... botones) {
        List<Paso> pasos = new java.util.ArrayList<>();
        for (String boton : botones) {
            pasos.add(pulsar(pasos.size() + 1, boton));
        }
        return new Caso(id, null, null, pasos, null, null, null);
    }

    @Test
    void elInformeMuestraLaCoberturaDeLaAppDePractica() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "necesita pantalla");
        Path jarAgente = Path.of(System.getProperty("opsec.agente.jar"));
        Path jarJacoco = Path.of(System.getProperty("opsec.jacoco.jar"));
        Proyecto proyecto =
                new ProyectoService()
                        .cargarProyecto(
                                Path.of("..", "demo", "sec-001", "proyecto.yaml").normalize());
        assertThat(proyecto.jar())
                .as("Construir también la app de práctica con -am")
                .isRegularFile();
        Path carpeta =
                Files.createDirectories(
                        Path.of("target", "registros-it", "cobertura-" + System.nanoTime()));
        // Sin el volcado de JaCoCo al salir: los datos solo pueden venir del volcado en CERRAR.
        ConfigCobertura cobertura =
                ConfigCobertura.en(jarJacoco, carpeta, List.of(PRACTICA + "*")).sinVolcadoAlSalir();
        // Cada caso arranca su propia aplicación: uno llega al inicio de sesión y el otro al menú.
        Suite suite =
                new Suite(
                        "cobertura",
                        null,
                        null,
                        new ConfigEjecucion(
                                ModoAccion.EVENTOS, false, Duration.ofSeconds(10), 0, 0L, 1),
                        List.of(caso("NUBE", "Avanzar", "Nube"), caso("LOCAL", "Avanzar", "Local")),
                        null);

        ResultadoSuite resultado;
        try (SesionService sesiones =
                new SesionService(
                        new LanzadorProceso(jarAgente, carpeta).conCobertura(cobertura),
                        ESPERA,
                        ESPERA)) {
            resultado =
                    new EjecucionService(
                                    proyecto,
                                    sesiones,
                                    new EvaluacionService(RegistroExtensiones.descubrir()))
                            .ejecutar(suite);
        }

        assertThat(resultado.casos()).hasSize(2);
        assertThat(cobertura.archivoExec()).isRegularFile();
        assertThat(Files.size(cobertura.archivoExec())).isPositive();

        Path fuentes = Path.of("..", "apps-practica", "clientes-vulnerable", "src", "main", "java");
        ResumenCobertura resumen =
                new CoberturaService()
                        .generar(
                                cobertura.archivoExec(),
                                proyecto.jar(),
                                List.of(PRACTICA + "*"),
                                fuentes,
                                carpeta.resolve("cobertura"));

        assertThat(resumen.clases()).isNotEmpty();
        assertThat(resumen.clases()).allSatisfy(c -> assertThat(c.nombre()).startsWith(PRACTICA));
        assertThat(resumen.lineas().cubiertos()).isPositive();
        assertThat(resumen.lineas().perdidos()).as("el resto de la app no se visitó").isPositive();
        assertThat(cubiertas(resumen, PRACTICA + "gui.Portada")).isPositive();
        assertThat(cubiertas(resumen, PRACTICA + "gui.InicioSesion"))
                .as("solo el caso NUBE llega al inicio de sesión")
                .isPositive();
        assertThat(cubiertas(resumen, PRACTICA + "gui.MenuAdministrador"))
                .as("solo el caso LOCAL llega al menú: los datos de los dos se sumaron")
                .isPositive();
        assertThat(cubiertas(resumen, PRACTICA + "gui.EliminarPersona"))
                .as("nadie abrió esta pantalla")
                .isZero();
        assertThat(resumen.indiceHtml()).isRegularFile();
        assertThat(Files.readString(resumen.indiceHtml())).contains("io.github.physanto");
        assertThat(resumen.xml()).isRegularFile();
    }

    private static int cubiertas(ResumenCobertura resumen, String clase) {
        return resumen.clases().stream()
                .filter(c -> c.nombre().equals(clase))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Falta la clase " + clase))
                .lineas()
                .cubiertos();
    }
}
