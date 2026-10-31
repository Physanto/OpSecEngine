package io.github.physanto.opsecengine.nucleo.sesion;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class LanzadorProcesoTest {

    private static final Path JAVA = Path.of("jdk", "bin", "java");
    private static final Path AGENTE = Path.of("agente.jar");
    private static final Path JAR = Path.of("dist", "app.jar");

    private final LanzadorProceso lanzador =
            new LanzadorProceso(JAVA, AGENTE, Path.of("resultados"));

    @Test
    void usaElClasspathCuandoElProyectoIndicaLaClasePrincipal() {
        Proyecto proyecto = proyecto("app.Portada", List.of("-Xmx256m", "-Dmodo=prueba"));

        List<String> comando = lanzador.comando(proyecto, 4321, "secreto");

        assertThat(comando)
                .containsExactly(
                        JAVA.toString(),
                        "-Xmx256m",
                        "-Dmodo=prueba",
                        "-javaagent:" + AGENTE.toAbsolutePath() + "=puerto=4321,token=secreto",
                        "-cp",
                        JAR.toAbsolutePath().toString(),
                        "app.Portada");
    }

    @Test
    void usaElManifiestoCuandoNoHayClasePrincipal() {
        List<String> comando = lanzador.comando(proyecto(null, List.of()), 80, "t");

        assertThat(comando.subList(comando.size() - 2, comando.size()))
                .containsExactly("-jar", JAR.toAbsolutePath().toString());
    }

    @Test
    void elJavaPorDefectoEsElDeLaJvmActual() {
        assertThat(LanzadorProceso.javaActual())
                .isEqualTo(Path.of(System.getProperty("java.home"), "bin", "java"));
    }

    private static Proyecto proyecto(String clasePrincipal, List<String> argumentosJvm) {
        return new Proyecto(
                "app", JAR, clasePrincipal, argumentosJvm, null, null, null, null, null, null);
    }
}
