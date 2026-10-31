package io.github.physanto.opsecengine.agente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.physanto.opsecengine.protocolo.dto.HolaDto;
import io.github.physanto.opsecengine.soporte.AplicacionDePrueba;
import io.github.physanto.opsecengine.soporte.ExcepcionErrorRemoto;
import io.github.physanto.opsecengine.soporte.MotorFalso;
import java.io.File;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Prueba de integración: lanza {@link AplicacionDePrueba} en otra JVM con {@code -javaagent}
 * apuntando al jar empaquetado del agente y verifica el saludo en {@link MotorFalso}.
 *
 * <p>La ejecuta failsafe en la fase {@code integration-test}, después de empaquetar el agente. La
 * ruta del jar llega en la propiedad del sistema {@code opsec.agente.jar}.
 */
class AgenteEcoIT {

    private static final Duration ESPERA = Duration.ofSeconds(30);

    @Test
    void elAgenteEmpaquetadoSaludaDesdeOtroProceso(@TempDir Path carpeta) throws Exception {
        Path jarAgente = Path.of(System.getProperty("opsec.agente.jar"));
        assertThat(jarAgente).isRegularFile();
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        String classpath = String.join(File.pathSeparator, classpathDeLaAplicacion());
        Path salida = carpeta.resolve("aplicacion.log");

        try (MotorFalso motor = new MotorFalso()) {
            Process proceso =
                    new ProcessBuilder(
                                    java.toString(),
                                    "-javaagent:" + jarAgente + "=" + motor.argumentosAgente(),
                                    "-cp",
                                    classpath,
                                    AplicacionDePrueba.class.getName(),
                                    "60")
                            .redirectErrorStream(true)
                            .redirectOutput(salida.toFile())
                            .start();
            try {
                HolaDto hola = motor.esperarHola(ESPERA);

                assertThat(hola.getToken()).isEqualTo(motor.token());
                assertThat(hola.getVersionAgente()).isNotEqualTo("desarrollo");
                assertThatThrownBy(motor::pedirArbol).isInstanceOf(ExcepcionErrorRemoto.class);
                assertThat(motor.cerrar()).isTrue();
                assertThat(proceso.isAlive())
                        .as("el agente eco no termina la aplicación con CERRAR")
                        .isTrue();
            } finally {
                proceso.destroy();
                if (!proceso.waitFor(10, TimeUnit.SECONDS)) {
                    proceso.destroyForcibly();
                }
            }
        }
        assertThat(Files.readString(salida, StandardCharsets.UTF_8))
                .contains(AplicacionDePrueba.LINEA_INICIO)
                .doesNotContain("[opsec-agente]");
    }

    /** Solo la ubicación de las clases de {@link AplicacionDePrueba}, que usa solo el JDK. */
    private static List<String> classpathDeLaAplicacion() throws URISyntaxException {
        return List.of(
                Path.of(
                                AplicacionDePrueba.class
                                        .getProtectionDomain()
                                        .getCodeSource()
                                        .getLocation()
                                        .toURI())
                        .toString());
    }
}
