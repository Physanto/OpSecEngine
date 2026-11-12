package io.github.physanto.opsecengine.nucleo.sesion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    void transmiteElModoOcultoSoloCuandoSeSolicita() {
        Proyecto proyecto = proyecto(null, List.of());
        assertThat(lanzador.comando(proyecto, 4321, "secreto", true))
                .contains(
                        "-javaagent:"
                                + AGENTE.toAbsolutePath()
                                + "=puerto=4321,token=secreto,oculto=true");
        assertThat(lanzador.comando(proyecto, 4321, "secreto", false))
                .isEqualTo(lanzador.comando(proyecto, 4321, "secreto"));
    }

    @Test
    void informaSuCarpetaDeResultados() {
        assertThat(lanzador.carpetaResultados()).contains(Path.of("resultados"));
    }

    @Test
    void usaElManifiestoCuandoNoHayClasePrincipal() {
        List<String> comando = lanzador.comando(proyecto(null, List.of()), 80, "t");

        assertThat(comando.subList(comando.size() - 2, comando.size()))
                .containsExactly("-jar", JAR.toAbsolutePath().toString());
    }

    @Test
    void usaElJavaDelProyectoSiLoIndica() {
        Path jdk8 = Path.of("jdks", "jdk8");
        Proyecto proyecto =
                new Proyecto("app", JAR, null, null, jdk8, null, null, null, null, null, null);

        List<String> comando = lanzador.comando(proyecto, 80, "t");

        assertThat(comando.get(0)).isEqualTo(jdk8.resolve("bin").resolve("java").toString());
    }

    @Test
    void noLanzaSiLaCarpetaDeJavaNoTieneJava(@TempDir Path carpeta) {
        Proyecto proyecto =
                new Proyecto(
                        "app",
                        JAR,
                        null,
                        null,
                        carpeta.resolve("sin-java"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);

        assertThatThrownBy(() -> lanzador.lanzar(proyecto, 80, "t"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("app.java");
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
