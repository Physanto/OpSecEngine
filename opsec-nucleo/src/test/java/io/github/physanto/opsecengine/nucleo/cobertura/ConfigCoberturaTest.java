package io.github.physanto.opsecengine.nucleo.cobertura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.nucleo.sesion.LanzadorProceso;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pruebas de la configuración de cobertura y de cómo el lanzador la usa. */
class ConfigCoberturaTest {

    private static final Path JACOCO = Path.of("lib", "jacocoagent.jar");
    private static final Path AGENTE = Path.of("lib", "opsec-agente.jar");
    private static final Path CARPETA = Path.of("resultados", "corrida");

    private static Proyecto proyecto() {
        return new Proyecto(
                "clientes",
                Path.of("app", "app.jar"),
                "app.Portada",
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    @Test
    void elArgumentoCargaJacocoConElArchivoDeDatosYSinMedirElAgenteDelMotor() {
        ConfigCobertura config = ConfigCobertura.en(JACOCO, CARPETA, null);

        String argumento = config.argumentoAgente();

        assertThat(argumento)
                .startsWith("-javaagent:" + JACOCO.toAbsolutePath() + "=destfile=")
                .contains(CARPETA.resolve(ConfigCobertura.ARCHIVO_EXEC).toAbsolutePath().toString())
                .contains("append=true", "dumponexit=true", "output=file")
                .contains("excludes=" + ConfigCobertura.EXCLUIDAS)
                .doesNotContain("includes=");
    }

    @Test
    void elVolcadoAlSalirSePuedeDesactivarParaComprobarElVolcadoDeCerrar() {
        ConfigCobertura config = ConfigCobertura.en(JACOCO, CARPETA, null);

        assertThat(config.argumentoAgente()).contains("dumponexit=true");
        assertThat(config.sinVolcadoAlSalir().argumentoAgente()).contains("dumponexit=false");
        assertThat(config.sinVolcadoAlSalir().archivoExec()).isEqualTo(config.archivoExec());
    }

    @Test
    void losPatronesDeClasesSeUnenConDosPuntos() {
        ConfigCobertura config = ConfigCobertura.en(JACOCO, CARPETA, List.of("a.*", "b.C"));

        assertThat(config.argumentoAgente()).endsWith(",includes=a.*:b.C");
    }

    @Test
    void rechazaLoQueJacocoNoPuedeLeer() {
        assertThatThrownBy(() -> ConfigCobertura.en(Path.of("lib,x", "j.jar"), CARPETA, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("comas");
        assertThatThrownBy(() -> ConfigCobertura.en(Path.of("lib=x", "j.jar"), CARPETA, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("signo igual");
        assertThatThrownBy(() -> ConfigCobertura.en(JACOCO, CARPETA, List.of(" ")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConfigCobertura.en(JACOCO, CARPETA, List.of("a,b")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elLanzadorCargaJacocoAntesQueElAgenteDelMotor() {
        LanzadorProceso lanzador =
                new LanzadorProceso(AGENTE, CARPETA)
                        .conCobertura(ConfigCobertura.en(JACOCO, CARPETA, List.of("app.*")));

        List<String> comando = lanzador.comando(proyecto(), 1234, "tok");

        List<String> agentes = comando.stream().filter(a -> a.startsWith("-javaagent:")).toList();
        assertThat(agentes).hasSize(2);
        assertThat(agentes.get(0)).contains("jacocoagent.jar", "includes=app.*");
        assertThat(agentes.get(1)).contains("opsec-agente.jar", "puerto=1234", "token=tok");
        assertThat(comando.indexOf(agentes.get(1))).isLessThan(comando.indexOf("-cp"));
    }

    @Test
    void sinCoberturaElComandoQuedaIgualYElLanzadorOriginalNoCambia() {
        LanzadorProceso original = new LanzadorProceso(AGENTE, CARPETA);

        LanzadorProceso conCobertura =
                original.conCobertura(ConfigCobertura.en(JACOCO, CARPETA, null));

        assertThat(original.comando(proyecto(), 1, "t"))
                .filteredOn(a -> a.startsWith("-javaagent:"))
                .hasSize(1);
        assertThat(conCobertura.comando(proyecto(), 1, "t"))
                .filteredOn(a -> a.startsWith("-javaagent:"))
                .hasSize(2);
        assertThat(conCobertura.conCobertura(null).comando(proyecto(), 1, "t"))
                .isEqualTo(original.comando(proyecto(), 1, "t"));
    }
}
