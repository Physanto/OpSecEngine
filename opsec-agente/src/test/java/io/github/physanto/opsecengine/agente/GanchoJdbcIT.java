package io.github.physanto.opsecengine.agente;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.physanto.opsecengine.protocolo.dto.CodigoResultado;
import io.github.physanto.opsecengine.protocolo.dto.ConfigGanchosDto;
import io.github.physanto.opsecengine.protocolo.dto.EventoDto;
import io.github.physanto.opsecengine.soporte.MotorFalso;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Prueba de integración del gancho JDBC: lanza una JVM con el agente real y la base H2 del jar de
 * la app de práctica, y comprueba los eventos {@code SQL} que llegan al motor.
 */
class GanchoJdbcIT {

    private static final Duration ESPERA = Duration.ofSeconds(30);

    @Test
    void cadaSentenciaJdbcLlegaComoEventoSqlConSusDatos() throws Exception {
        Path jarAgente = Path.of(System.getProperty("opsec.agente.jar"));
        Path jarApp =
                Path.of(
                                "..",
                                "apps-practica",
                                "clientes-vulnerable",
                                "target",
                                "clientes-vulnerable.jar")
                        .toAbsolutePath()
                        .normalize();
        assertThat(jarAgente).isRegularFile();
        assertThat(jarApp).as("Construir también la app de práctica con -am").isRegularFile();
        Path carpeta =
                Files.createDirectories(
                        Path.of("target", "registros-it", "jdbc-" + System.nanoTime()));
        Path salida = carpeta.resolve("aplicacion.log");
        String claseApp = AplicacionJdbcDePrueba.class.getName();
        String clases =
                Path.of(
                                AplicacionJdbcDePrueba.class
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
                                    "-Dstderr.encoding=UTF-8",
                                    "-javaagent:" + jarAgente + "=" + motor.argumentosAgente(),
                                    "-cp",
                                    String.join(File.pathSeparator, jarApp.toString(), clases),
                                    claseApp)
                            .redirectErrorStream(true)
                            .redirectOutput(salida.toFile())
                            .start();
            try {
                motor.esperarHola(ESPERA);
                assertThat(
                                motor.configurarGanchos(
                                                new ConfigGanchosDto(
                                                        List.of("DELETE", "insert"),
                                                        List.of(),
                                                        List.of(AplicacionJdbcDePrueba.CANARIO),
                                                        List.of()))
                                        .getCodigo())
                        .isEqualTo(CodigoResultado.OK);
                proceso.getOutputStream().write('\n');
                proceso.getOutputStream().flush();
                esperarLinea(salida, AplicacionJdbcDePrueba.LINEA_LISTO);
                esperarEventos(motor, 5);

                List<EventoDto> sql =
                        motor.eventos().stream()
                                .filter(e -> e.getTipo().equals("SQL"))
                                .collect(Collectors.toList());
                EventoDto insercion = con(sql, "INSERT INTO personas");
                assertThat(insercion.getDatos())
                        .containsEntry("tipoSentencia", "STATEMENT")
                        .containsEntry("operacion", "INSERT")
                        .containsEntry("filasAfectadas", 1)
                        .containsEntry("sensible", true);
                assertThat(String.valueOf(insercion.getDatos().get("conexion")))
                        .startsWith("jdbc:h2:mem:opsec")
                        .doesNotContain("secreta")
                        .doesNotContain("PASSWORD");
                EventoDto consulta = con(sql, "SELECT * FROM personas");
                assertThat(String.valueOf(consulta.getDatos().get("sql")))
                        .contains("nombre = '" + AplicacionJdbcDePrueba.CANARIO + "'");
                assertThat(consulta.getDatos())
                        .containsEntry("tipoSentencia", "STATEMENT")
                        .doesNotContainKey("sensible");
                assertThat(con(sql, "SELEC mal").getDatos()).containsKey("error");
                List<EventoDto> borrados =
                        sql.stream()
                                .filter(e ->
                                        String.valueOf(e.getDatos().get("sql"))
                                                .startsWith("DELETE"))
                                .collect(Collectors.toList());
                assertThat(borrados)
                        .as("sin duplicados por llamadas internas del driver")
                        .hasSize(1);
                assertThat(borrados.get(0).getDatos())
                        .containsEntry("tipoSentencia", "PREPARED")
                        .containsEntry("operacion", "DELETE")
                        .containsEntry("parametros", List.of("1", "Ana"))
                        .containsEntry("filasAfectadas", 1)
                        .containsEntry("sensible", true);
                assertThat(motor.cerrar()).isTrue();
            } finally {
                proceso.destroy();
                if (!proceso.waitFor(10, TimeUnit.SECONDS)) {
                    proceso.destroyForcibly();
                }
            }
        }
    }

    @Test
    void unFalloInyectadoHaceQueLaConexionYLasSentenciasLancenLaExcepcion() throws Exception {
        Path jarAgente = Path.of(System.getProperty("opsec.agente.jar"));
        Path jarApp =
                Path.of(
                                "..",
                                "apps-practica",
                                "clientes-vulnerable",
                                "target",
                                "clientes-vulnerable.jar")
                        .toAbsolutePath()
                        .normalize();
        assertThat(jarApp).as("Construir también la app de práctica con -am").isRegularFile();
        Path carpeta =
                Files.createDirectories(
                        Path.of("target", "registros-it", "fallo-" + System.nanoTime()));
        Path salida = carpeta.resolve("aplicacion.log");
        String clases =
                Path.of(
                                AplicacionFalloJdbcDePrueba.class
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
                                    String.join(File.pathSeparator, jarApp.toString(), clases),
                                    AplicacionFalloJdbcDePrueba.class.getName())
                            .redirectErrorStream(true)
                            .redirectOutput(salida.toFile())
                            .start();
            try {
                motor.esperarHola(ESPERA);
                motor.configurarGanchos(
                        new ConfigGanchosDto(
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of(
                                        "SQL_CONEXION:java.sql.SQLException:sin red",
                                        "SQL_EJECUTAR:java.lang.IllegalStateException:disco lleno")));
                proceso.getOutputStream().write('\n');
                proceso.getOutputStream().flush();
                esperarLinea(salida, "FALLO-LISTO");
                String texto = Files.readString(salida, StandardCharsets.UTF_8);
                assertThat(texto)
                        .contains("conexion: java.sql.SQLException sin red")
                        .contains("ejecucion: java.lang.IllegalStateException disco lleno");
                Thread.sleep(300);
                assertThat(
                                motor.eventos().stream()
                                        .filter(e -> e.getTipo().equals("ESTADO"))
                                        .map(e -> e.getDatos().get("fallo"))
                                        .collect(Collectors.toList()))
                        .contains("SQL_CONEXION", "SQL_EJECUTAR");
                assertThat(motor.cerrar()).isTrue();
            } finally {
                proceso.destroy();
                if (!proceso.waitFor(10, TimeUnit.SECONDS)) {
                    proceso.destroyForcibly();
                }
            }
        }
    }

    private static EventoDto con(List<EventoDto> eventos, String texto) {
        return eventos.stream()
                .filter(e -> String.valueOf(e.getDatos().get("sql")).contains(texto))
                .findFirst()
                .orElseThrow(() ->
                        new AssertionError("No llegó un evento SQL con " + texto + ": " + eventos));
    }

    private static void esperarLinea(Path salida, String linea) throws Exception {
        long limite = System.nanoTime() + ESPERA.toNanos();
        while (System.nanoTime() < limite) {
            if (Files.exists(salida)
                    && Files.readString(salida, StandardCharsets.UTF_8).contains(linea)) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("La aplicación no escribió " + linea);
    }

    private static void esperarEventos(MotorFalso motor, int minimo) throws InterruptedException {
        long limite = System.nanoTime() + ESPERA.toNanos();
        while (System.nanoTime() < limite) {
            if (motor.eventos().stream().filter(e -> e.getTipo().equals("SQL")).count() >= minimo) {
                return;
            }
            Thread.sleep(50);
        }
    }
}
