package io.github.physanto.opsecengine.verificacion;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.Suite;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.dominio.extension.Contexto;
import io.github.physanto.opsecengine.dominio.resultado.EstadoVeredicto;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.resultado.Veredicto;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import io.github.physanto.opsecengine.nucleo.evaluacion.EvaluacionService;
import io.github.physanto.opsecengine.nucleo.extensiones.RegistroExtensiones;
import io.github.physanto.opsecengine.nucleo.proyecto.ProyectoService;
import io.github.physanto.opsecengine.soporte.CargadorTrazas;
import io.github.physanto.opsecengine.soporte.TrazaEjemplo;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Comprueba de punta a punta los contratos v1.2: la suite de ejemplo se lee desde YAML con el
 * núcleo y los oráculos reales, descubiertos con {@code ServiceLoader}, usan sus secciones nuevas
 * sobre las trazas de ejemplo.
 */
class SuiteSeguridadEjemploTest {

    private static final Path EJEMPLOS = Path.of("..", "contratos", "ejemplos");

    private static Proyecto proyecto;
    private static Suite suite;
    private static EvaluacionService evaluacion;

    @BeforeAll
    static void cargar() throws Exception {
        ProyectoService servicio = new ProyectoService();
        proyecto = servicio.cargarProyecto(EJEMPLOS.resolve("proyecto.yaml"));
        suite = servicio.cargarSuite(EJEMPLOS.resolve("suites").resolve("login.yaml"), proyecto);
        evaluacion = new EvaluacionService(RegistroExtensiones.descubrir());
    }

    @Test
    void elCanarioDeclaradoEnElYamlActivaSqliCanario() throws Exception {
        Traza traza = new CargadorTrazas().cargarEjemplo(TrazaEjemplo.C13_SQLI_LOGIN).getFirst();
        Caso caso = caso("C13");

        Veredicto veredicto =
                evaluacion.evaluarCaso(traza, Contexto.deCaso(proyecto, suite, caso), null);

        assertThat(veredicto.estado()).isEqualTo(EstadoVeredicto.NO_CUMPLE);
        assertThat(veredicto.hallazgos()).extracting(Hallazgo::oraculoId).contains("SQLI-CANARIO");
    }

    @Test
    void laComparacionDeLaSuiteActivaEnumeracionDeUsuarios() throws Exception {
        List<Traza> trazas = new CargadorTrazas().cargarEjemplo(TrazaEjemplo.C01_C02_MENSAJES);

        List<Hallazgo> hallazgos =
                evaluacion.evaluarSuite(Contexto.deSuite(proyecto, suite, trazas));

        assertThat(hallazgos)
                .extracting(Hallazgo::oraculoId)
                .containsExactly("ENUMERACION-USUARIOS");
    }

    private static Caso caso(String id) {
        return suite.casos().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }
}
