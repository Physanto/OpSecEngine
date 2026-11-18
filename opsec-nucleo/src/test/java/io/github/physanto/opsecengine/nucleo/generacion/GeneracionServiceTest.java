package io.github.physanto.opsecengine.nucleo.generacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.physanto.opsecengine.dominio.caso.Accion;
import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.Enfoque;
import io.github.physanto.opsecengine.dominio.caso.Localizador;
import io.github.physanto.opsecengine.dominio.caso.Suite;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.dominio.mapa.MapaApp;
import io.github.physanto.opsecengine.dominio.mapa.Objetivo;
import io.github.physanto.opsecengine.dominio.mapa.Transicion;
import io.github.physanto.opsecengine.dominio.mapa.Ventana;
import io.github.physanto.opsecengine.nucleo.extensiones.RegistroExtensiones;
import io.github.physanto.opsecengine.nucleo.proyecto.ProyectoService;
import io.github.physanto.opsecengine.soporte.DatosEjemplo;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pruebas de {@link GeneracionService} con un generador de enfoque ROBUSTEZ registrado por SPI. */
class GeneracionServiceTest {

    private static final Path PROYECTO_EJEMPLO =
            Path.of("..", "contratos", "ejemplos", "proyecto.yaml");

    private final ProyectoService proyectos = new ProyectoService();
    private final GeneracionService servicio =
            new GeneracionService(RegistroExtensiones.descubrir(), proyectos);

    @TempDir Path carpeta;

    @Test
    void generaCasosDelGeneradorRegistradoParaCadaObjetivoConIdsUnicos() throws Exception {
        Proyecto proyecto = proyectos.cargarProyecto(PROYECTO_EJEMPLO);
        List<Objetivo> objetivos =
                List.of(
                        new Objetivo("uno", "Portada", null),
                        new Objetivo("dos", "Portada", null),
                        new Objetivo("tres", "Portada", null));

        Suite suite =
                servicio.generar(
                        proyecto, DatosEjemplo.mapaClientes(), Set.of(Enfoque.ROBUSTEZ), objetivos);

        assertThat(suite.casos())
                .extracting(Caso::id)
                .containsExactly("ROB-1", "ROB-1-2", "ROB-1-3");
        assertThat(suite.enfoques()).containsExactly(Enfoque.ROBUSTEZ);
        assertThat(suite.nombre()).isEqualTo("generada-robustez");
    }

    @Test
    void sinObjetivosUsaTodaLaAplicacion() throws Exception {
        Proyecto proyecto = proyectos.cargarProyecto(PROYECTO_EJEMPLO);

        Suite suite =
                servicio.generar(
                        proyecto, DatosEjemplo.mapaClientes(), Set.of(Enfoque.ROBUSTEZ), List.of());

        assertThat(suite.casos()).hasSize(1);
        assertThat(suite.casos().get(0).descripcion()).contains("Toda la aplicación");
    }

    @Test
    void sinObjetivosDerivaUnoPorCadaCampoQueElMapaSabeLlenar() {
        Ventana ventana = new Ventana("Login", "Login", "App.Login");
        Localizador usuario = Localizador.porEtiqueta("Usuario");
        MapaApp mapa =
                new MapaApp(
                        Set.of(ventana),
                        List.of(
                                new Transicion(
                                        "Login",
                                        "Login",
                                        Accion.LLENAR,
                                        usuario,
                                        MapaApp.SIN_SESION),
                                new Transicion(
                                        "Login", "Login", Accion.LLENAR, usuario, "rol:admin")));

        List<Objetivo> objetivos = GeneracionService.derivarObjetivos(mapa);

        assertThat(objetivos).hasSize(1);
        assertThat(objetivos.get(0).ventanaId()).isEqualTo("Login");
        assertThat(objetivos.get(0).control()).isEqualTo(usuario);
    }

    @Test
    void unEnfoqueSinGeneradorEsUnErrorClaro() throws Exception {
        Proyecto proyecto = proyectos.cargarProyecto(PROYECTO_EJEMPLO);

        assertThatThrownBy(() ->
                        servicio.generar(
                                proyecto,
                                DatosEjemplo.mapaClientes(),
                                Set.of(Enfoque.ACCESIBILIDAD),
                                List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ACCESIBILIDAD");
    }

    @Test
    void sinEnfoquesFalla() throws Exception {
        Proyecto proyecto = proyectos.cargarProyecto(PROYECTO_EJEMPLO);

        assertThatThrownBy(() ->
                        servicio.generar(
                                proyecto, DatosEjemplo.mapaClientes(), Set.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void guardaLaSuiteYLaDevuelveValidadaDesdeElArchivo() throws Exception {
        Proyecto proyecto = proyectos.cargarProyecto(PROYECTO_EJEMPLO);
        Suite generada =
                servicio.generar(
                        proyecto, DatosEjemplo.mapaClientes(), Set.of(Enfoque.ROBUSTEZ), List.of());
        Path destino = carpeta.resolve("generada.yaml");

        Suite leida = servicio.guardar(generada, proyecto, destino);

        assertThat(destino).isRegularFile();
        assertThat(Files.readString(destino, StandardCharsets.UTF_8)).contains("ROB-1");
        assertThat(leida.casos()).extracting(Caso::id).containsExactly("ROB-1");
    }

    @Test
    void unaSuiteSinCasosNoSeGuarda() throws Exception {
        Proyecto proyecto = proyectos.cargarProyecto(PROYECTO_EJEMPLO);
        Suite vacia =
                new Suite(
                        "vacia",
                        "1",
                        Set.of(),
                        io.github.physanto.opsecengine.dominio.caso.ConfigEjecucion.porDefecto(),
                        List.of(),
                        null,
                        java.util.Map.of());

        assertThatThrownBy(() -> servicio.guardar(vacia, proyecto, carpeta.resolve("v.yaml")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(carpeta.resolve("v.yaml")).doesNotExist();
    }

    @Test
    void laSuiteDeEjemploSobreviveAEscribirlaYLeerla() throws Exception {
        Proyecto proyecto = proyectos.cargarProyecto(PROYECTO_EJEMPLO);
        Suite original =
                proyectos.cargarSuite(
                        Path.of("..", "contratos", "ejemplos", "suites", "login.yaml"), proyecto);
        Path destino = carpeta.resolve("copia.yaml");

        SuiteYaml.guardar(original, destino);
        Suite copia = proyectos.cargarSuite(destino, proyecto);

        assertThat(copia).isEqualTo(original);
    }
}
