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
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Prueba de integración de los puntos de entrada alternativos: explora la app de práctica real
 * desde su portada y, además, lanzando directamente {@code EliminarPersona}, que con F3 activo abre
 * la ventana sin iniciar sesión. A profundidad 1 la exploración desde la portada no llega a esa
 * ventana; solo el punto de entrada la encuentra.
 *
 * <p>La ejecuta failsafe; se omite sin pantalla. Necesita la app de práctica construida y el jar
 * del agente en {@code opsec.agente.jar}.
 */
class ExploracionEntradasIT {

    private static final String ENTRADA =
            "io.github.physanto.opsecengine.practica.gui.EliminarPersona";

    @Test
    void encuentraLaVentanaQueSoloSeAbreLanzandoLaClaseDeEntrada() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "necesita pantalla");
        Path jarAgente = Path.of(System.getProperty("opsec.agente.jar"));
        Proyecto base =
                new ProyectoService()
                        .cargarProyecto(
                                Path.of("..", "demo", "sec-001", "proyecto.yaml").normalize());
        assertThat(base.jar()).as("Construir también la app de práctica con -am").isRegularFile();
        // Sin roles: solo interesan el contexto sin sesión y el punto de entrada.
        Proyecto proyecto =
                new Proyecto(
                        "entradas",
                        base.jar(),
                        base.clasePrincipal(),
                        base.argumentosJvm(),
                        base.javaApp(),
                        base.datos(),
                        List.of(),
                        List.of(),
                        base.sensibles(),
                        base.politica(),
                        base.extensiones());
        Path registros =
                Files.createDirectories(
                        Path.of("target", "registros-it", "entradas-" + System.nanoTime()));
        ConfigExploracion config =
                new ConfigExploracion(
                                1,
                                Duration.ofMinutes(3),
                                ConfigExploracion.DESTRUCTIVOS_POR_DEFECTO,
                                true,
                                ConfigExploracion.ESPERA_TRANSICION_POR_DEFECTO)
                        .conEntradas(List.of(ENTRADA));
        Duration espera = Duration.ofSeconds(30);

        MapaApp mapa;
        try (SesionService sesiones =
                new SesionService(new LanzadorProceso(jarAgente, registros), espera, espera)) {
            mapa = new ExploracionService(proyecto, sesiones).explorar(config);
        }

        String contexto = MapaApp.contextoEntrada(ENTRADA);
        assertThat(mapa.contextos()).containsExactlyInAnyOrder(MapaApp.SIN_SESION, contexto);
        assertThat(mapa.ventanas()).extracting(Ventana::id).contains("Portada", "EliminarPersona");
        assertThat(mapa.transiciones())
                .as("desde la portada, a profundidad 1, no se llega a EliminarPersona")
                .noneMatch(t ->
                        t.contexto().equals(MapaApp.SIN_SESION)
                                && t.destino().equals("EliminarPersona"));
        MapaJson.guardar(mapa, registros.resolve(MapaJson.ARCHIVO));
    }
}
