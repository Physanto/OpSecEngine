package io.github.physanto.opsecengine.nucleo.exploracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.dominio.mapa.MapaApp;
import io.github.physanto.opsecengine.dominio.mapa.Ventana;
import io.github.physanto.opsecengine.nucleo.proyecto.ProyectoService;
import io.github.physanto.opsecengine.nucleo.sesion.LanzadorProceso;
import io.github.physanto.opsecengine.nucleo.sesion.SesionService;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Prueba de integración: explora la app de práctica real con el agente real y comprueba que el mapa
 * contiene las ventanas conocidas, incluida la rama local sin sesión.
 *
 * <p>La ejecuta failsafe; se omite sin pantalla. Necesita la app de práctica construida (la
 * construcción con {@code -am} la deja lista) y el jar del agente en {@code opsec.agente.jar}.
 */
class ExploracionServiceIT {

    @Test
    void encuentraLasVentanasConocidasDeLaAppDePractica() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "necesita pantalla");
        Path jarAgente = Path.of(System.getProperty("opsec.agente.jar"));
        Path proyectoYaml = Path.of("..", "demo", "sec-001", "proyecto.yaml").normalize();
        Proyecto proyecto = new ProyectoService().cargarProyecto(proyectoYaml);
        assertThat(jarAgente).isRegularFile();
        assertThat(proyecto.jar())
                .as("Construir también la app de práctica con -am")
                .isRegularFile();
        Path registros =
                Files.createDirectories(
                        Path.of("target", "registros-it", "exploracion-" + System.nanoTime()));
        List<String> avisos = new ArrayList<>();
        Duration espera = Duration.ofSeconds(30);

        MapaApp mapa;
        try (SesionService sesiones =
                new SesionService(new LanzadorProceso(jarAgente, registros), espera, espera)) {
            mapa =
                    new ExploracionService(proyecto, sesiones, avisos::add)
                            .explorar(ConfigExploracion.porDefecto());
        }

        assertThat(mapa.ventanas())
                .extracting(Ventana::id)
                .contains("Portada", "EscogerModo", "InicioSesion", "MenuAdministrador");
        assertThat(mapa.transiciones())
                .as("la rama local llega al menú de administrador sin iniciar sesión")
                .anyMatch(t ->
                        t.contexto().equals(MapaApp.SIN_SESION)
                                && t.destino().equals("MenuAdministrador"));
        assertThat(mapa.contextos()).contains(MapaApp.SIN_SESION);
        MapaJson.guardar(mapa, registros.resolve(MapaJson.ARCHIVO));
    }
}
