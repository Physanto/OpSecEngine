package io.github.physanto.opsecengine.agente;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.physanto.opsecengine.protocolo.dto.ConfigGanchosDto;
import io.github.physanto.opsecengine.protocolo.dto.EventoDto;
import io.github.physanto.opsecengine.soporte.MotorFalso;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Prueba de integración del gancho de Firestore con dobles de las clases de Firestore. */
class GanchoFirestoreIT {

    private static final Duration ESPERA = Duration.ofSeconds(30);

    @Test
    void lasOperacionesLlegaConRutaCamposSinValoresYSensible() throws Exception {
        Path jarAgente = Path.of(System.getProperty("opsec.agente.jar"));
        assertThat(jarAgente).isRegularFile();
        Path carpeta =
                Files.createDirectories(
                        Path.of("target", "registros-it", "firestore-" + System.nanoTime()));
        Path salida = carpeta.resolve("aplicacion.log");
        String clases =
                Path.of(
                                AplicacionFirestoreDePrueba.class
                                        .getProtectionDomain()
                                        .getCodeSource()
                                        .getLocation()
                                        .toURI())
                        .toString();
        try (MotorFalso motor = new MotorFalso()) {
            Process proceso =
                    new ProcessBuilder(
                                    Path.of(System.getProperty("java.home"), "bin", "java")
                                            .toString(),
                                    "-Dstdout.encoding=UTF-8",
                                    "-javaagent:" + jarAgente + "=" + motor.argumentosAgente(),
                                    "-cp",
                                    clases,
                                    AplicacionFirestoreDePrueba.class.getName())
                            .redirectErrorStream(true)
                            .redirectOutput(salida.toFile())
                            .start();
            try {
                motor.esperarHola(ESPERA);
                motor.configurarGanchos(
                        new ConfigGanchosDto(
                                List.of(), List.of("delete", "SET"), List.of(), List.of()));
                proceso.getOutputStream().write('\n');
                proceso.getOutputStream().flush();
                long limite = System.nanoTime() + ESPERA.toNanos();
                while (System.nanoTime() < limite
                        && !Files.readString(salida, StandardCharsets.UTF_8)
                                .contains(AplicacionFirestoreDePrueba.LINEA_LISTO)) {
                    Thread.sleep(50);
                }
                Thread.sleep(300);
                List<EventoDto> eventos =
                        motor.eventos().stream()
                                .filter(e -> e.getTipo().equals("FIRESTORE"))
                                .collect(Collectors.toList());
                assertThat(eventos).hasSize(4);
                Map<String, Object> escritura = eventos.get(0).getDatos();
                assertThat(escritura)
                        .containsEntry("operacion", "set")
                        .containsEntry("ruta", "personas/123")
                        .containsEntry("sensible", true)
                        .doesNotContainKey("lote");
                assertThat(String.valueOf(escritura.get("campos")))
                        .contains("nombre", "clave")
                        .doesNotContain("Ana")
                        .doesNotContain("secreta");
                assertThat(eventos.get(1).getDatos())
                        .containsEntry("operacion", "delete")
                        .containsEntry("ruta", "personas/fallo")
                        .containsEntry("error", "sin permiso");
                assertThat(eventos.get(2).getDatos())
                        .containsEntry("ruta", "personas/9")
                        .containsEntry("lote", true);
                assertThat(eventos.get(3).getDatos())
                        .containsEntry("operacion", "commit")
                        .containsEntry("lote", true)
                        .doesNotContainKey("sensible");
                assertThat(motor.cerrar()).isTrue();
            } finally {
                proceso.destroy();
                if (!proceso.waitFor(10, TimeUnit.SECONDS)) {
                    proceso.destroyForcibly();
                }
            }
        }
    }
}
