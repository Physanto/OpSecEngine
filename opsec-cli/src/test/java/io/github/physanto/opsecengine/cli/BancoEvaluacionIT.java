package io.github.physanto.opsecengine.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.awt.GraphicsEnvironment;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Comprueba el script real del banco: métricas, errores, rutas y reproducción de las tablas. */
class BancoEvaluacionIT {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @TempDir Path temporal;

    @Test
    void resumeDeteccionesFalsosPositivosAusenciasYErroresSinInventarCeros() throws Exception {
        Path salida = crearResultadosControlados();

        Ejecucion ejecucion = ejecutar("resumir", salida.toString());

        assertThat(ejecucion.codigo()).as(ejecucion.salida()).isEqualTo(1);
        List<String> filas =
                Files.readAllLines(salida.resolve("tabla.csv"), StandardCharsets.UTF_8);
        assertThat(filas).hasSize(6);
        String app = "\"Aplicación, \"\"A\"\"\",\"v1\",";
        assertThat(filas.get(1))
                .isEqualTo(
                        "\"tp\","
                                + app
                                + "\"1\",\"1\",\"0\",\"0\",\"0\",\"250\",\"125\",\"3\",\"OK\",\"\"");
        assertThat(filas.get(2))
                .isEqualTo(
                        "\"fp\","
                                + app
                                + "\"0\",\"0\",\"1\",\"0\",\"0\",\"250\",\"125\",\"3\",\"REGRESION\",\"\"");
        assertThat(filas.get(3))
                .isEqualTo(
                        "\"fn\","
                                + app
                                + "\"1\",\"0\",\"0\",\"1\",\"0\",\"250\",\"125\",\"0\",\"REGRESION\",\"\"");
        assertThat(filas.get(4))
                .isEqualTo(
                        "\"nc\","
                                + app
                                + "\"1\",\"0\",\"0\",\"0\",\"1\",\"250\",\"125\",\"4\",\"NO_CONCLUYENTE\",\"\"");
        assertThat(filas.get(5))
                .isEqualTo(
                        "\"error\","
                                + app
                                + "\"1\",\"\",\"\",\"\",\"\",\"250\",\"\",\"1\",\"ERROR\",\"Fallo de ejecución simulado\"");
        byte[] csv = Files.readAllBytes(salida.resolve("tabla.csv"));
        byte[] markdown = Files.readAllBytes(salida.resolve("tabla.md"));
        assertThat(ejecutar("resumir", salida.toString()).codigo()).isEqualTo(1);
        assertThat(Files.readAllBytes(salida.resolve("tabla.csv"))).isEqualTo(csv);
        assertThat(Files.readAllBytes(salida.resolve("tabla.md"))).isEqualTo(markdown);
    }

    @Test
    void rechazaUnInformeAlteradoEnLugarDeUsarloParaMedir() throws Exception {
        Path salida = crearResultadosControlados();
        Files.writeString(salida.resolve("tp/resultado.json"), "{}", StandardCharsets.UTF_8);

        Ejecucion ejecucion = ejecutar("resumir", salida.toString());

        assertThat(ejecucion.codigo()).isEqualTo(1);
        String fila =
                Files.readAllLines(salida.resolve("tabla.csv"), StandardCharsets.UTF_8).get(1);
        assertThat(fila).contains("\"ERROR\"", "El informe cambió después de ejecutar");
    }

    @Test
    void exigeVerdadParaTodosLosCasosAntesDeEjecutar() throws Exception {
        Path raiz = raiz();
        ObjectNode banco = JSON.createObjectNode().put("version", 1);
        ObjectNode escenario =
                banco.putArray("escenarios")
                        .addObject()
                        .put("id", "incompleto")
                        .put("aplicacion", "app")
                        .put("version", "v1")
                        .put("proyecto", raiz.resolve("demo").resolve("proyecto.yaml").toString())
                        .put(
                                "suite",
                                raiz.resolve("demo")
                                        .resolve("suites")
                                        .resolve("login.yaml")
                                        .toString());
        escenario.putObject("esperados").putArray("CASO-QUE-NO-EXISTE");
        Path configuracion = temporal.resolve("banco con espacios.json");
        Files.writeString(configuracion, banco.toString(), StandardCharsets.UTF_8);
        Path salida = temporal.resolve("salida sin crear");

        Ejecucion ejecucion =
                ejecutar("ejecutar", configuracion.toString(), "--salida", salida.toString());

        assertThat(ejecucion.codigo()).isEqualTo(1);
        assertThat(ejecucion.salida()).contains("declare esperados para cada caso");
        assertThat(salida).doesNotExist();
    }

    @Test
    void ejecutaLasVersionesRealesYReproduceLasTablasByteAByte() throws Exception {
        assumeThat(GraphicsEnvironment.isHeadless()).as("necesita pantalla").isFalse();
        Path banco = raiz().resolve("apps-practica").resolve("laboratorio").resolve("banco.json");
        Path salida = temporal.resolve("mediciones con espacios");

        Ejecucion ejecucion = ejecutar("ejecutar", banco.toString(), "--salida", salida.toString());

        assertThat(ejecucion.codigo()).as(ejecucion.salida()).isZero();
        List<String> filas =
                Files.readAllLines(salida.resolve("tabla.csv"), StandardCharsets.UTF_8);
        assertThat(filas).hasSize(5);
        assertThat(filas.get(1))
                .startsWith(
                        "\"f6-vulnerable\",\"clientes-vulnerable\",\"F6 activo\",\"1\",\"1\",\"0\",\"0\",\"0\",");
        assertThat(filas.get(2))
                .startsWith(
                        "\"f6-corregido\",\"clientes-vulnerable\",\"F6 corregido\",\"0\",\"0\",\"0\",\"0\",\"0\",");
        assertThat(filas.get(3))
                .startsWith(
                        "\"f8-vulnerable\",\"clientes-vulnerable\",\"F8 activo\",\"1\",\"1\",\"0\",\"0\",\"0\",");
        assertThat(filas.get(4))
                .startsWith(
                        "\"f8-corregido\",\"clientes-vulnerable\",\"F8 corregido\",\"0\",\"0\",\"0\",\"0\",\"0\",");
        byte[] csv = Files.readAllBytes(salida.resolve("tabla.csv"));
        byte[] markdown = Files.readAllBytes(salida.resolve("tabla.md"));
        assertThat(ejecutar("resumir", salida.toString()).codigo()).isZero();
        assertThat(Files.readAllBytes(salida.resolve("tabla.csv"))).isEqualTo(csv);
        assertThat(Files.readAllBytes(salida.resolve("tabla.md"))).isEqualTo(markdown);
        Ejecucion repetida = ejecutar("ejecutar", banco.toString(), "--salida", salida.toString());
        assertThat(repetida.codigo()).isEqualTo(1);
        assertThat(repetida.salida()).contains("carpeta de salida debe estar vacía");
        assertThat(Files.readAllBytes(salida.resolve("tabla.csv"))).isEqualTo(csv);
    }

    private Path crearResultadosControlados() throws Exception {
        Path salida = temporal.resolve("resultados controlados");
        Files.createDirectories(salida);
        ObjectNode banco = JSON.createObjectNode().put("version", 1);
        ArrayNode escenarios = banco.putArray("escenarios");
        ArrayNode registros = JSON.createArrayNode();
        for (String id : List.of("tp", "fp", "fn", "nc", "error")) {
            ObjectNode escenario =
                    escenarios
                            .addObject()
                            .put("id", id)
                            .put("aplicacion", "Aplicación, \"A\"")
                            .put("version", "v1")
                            .put("proyecto", "no-se-ejecuta.yaml")
                            .put("suite", "no-se-ejecuta.yaml");
            ArrayNode esperados = escenario.putObject("esperados").putArray("CASO");
            if (!id.equals("fp")) esperados.add("ORACULO-A");
            Path informe = salida.resolve(id).resolve("resultado.json");
            Files.createDirectories(informe.getParent());
            boolean detectado = id.equals("tp") || id.equals("fp");
            int codigo = id.equals("error") ? 1 : id.equals("nc") ? 4 : detectado ? 3 : 0;
            ObjectNode resultado = JSON.createObjectNode().put("nombreSuite", "prueba");
            resultado.putArray("hallazgosSuite");
            ObjectNode caso =
                    resultado
                            .putArray("casos")
                            .addObject()
                            .put("casoId", "CASO")
                            .put("duracion", "PT0.125S");
            ObjectNode veredicto =
                    caso.putObject("veredicto")
                            .put(
                                    "estado",
                                    id.equals("nc")
                                            ? "NO_CONCLUYENTE"
                                            : detectado ? "NO_CUMPLE" : "CUMPLE");
            if (id.equals("nc")) veredicto.put("motivo", "Oráculo no disponible");
            else veredicto.putNull("motivo");
            ArrayNode hallazgos = veredicto.putArray("hallazgos");
            if (detectado) {
                hallazgos.addObject().put("oraculoId", "ORACULO-A").put("confianza", "CONFIRMADO");
                hallazgos.addObject().put("oraculoId", "ORACULO-A").put("confianza", "PROBABLE");
                hallazgos.addObject().put("oraculoId", "ORACULO-INFO").put("confianza", "INFO");
            }
            Files.writeString(informe, resultado.toString(), StandardCharsets.UTF_8);
            ObjectNode registro =
                    registros
                            .addObject()
                            .put("id", id)
                            .put("codigoCli", codigo)
                            .put("tiempoProcesoMs", 250)
                            .put("nombreSuite", "prueba")
                            .put("sha256Proyecto", "prueba")
                            .put("sha256Suite", "prueba")
                            .put("sha256App", "prueba")
                            .put("informe", id + "/resultado.json")
                            .put("sha256Informe", hash(informe));
            if (id.equals("error")) registro.put("error", "Fallo de ejecución simulado");
            else registro.putNull("error");
        }
        Path configuracion = salida.resolve("banco.json");
        Files.writeString(configuracion, banco.toString(), StandardCharsets.UTF_8);
        ObjectNode ejecucion =
                JSON.createObjectNode()
                        .put("version", 1)
                        .put("inicio", "2026-11-11T00:00:00Z")
                        .put("java", "21")
                        .put("sistema", "prueba")
                        .put("sha256Cli", "prueba")
                        .put("sha256Configuracion", hash(configuracion));
        ejecucion.set("registros", registros);
        Files.writeString(
                salida.resolve("ejecucion.json"), ejecucion.toString(), StandardCharsets.UTF_8);
        return salida;
    }

    private Ejecucion ejecutar(String... argumentos) throws Exception {
        List<String> comando =
                new ArrayList<>(
                        List.of(
                                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                                "-Dfile.encoding=UTF-8",
                                "-Dstdout.encoding=UTF-8",
                                "-Dstderr.encoding=UTF-8",
                                "--source",
                                "21",
                                "--class-path",
                                Path.of(System.getProperty("opsec.cli.jar"))
                                        .toAbsolutePath()
                                        .toString(),
                                raiz().resolve("apps-practica")
                                        .resolve("laboratorio")
                                        .resolve("BancoEvaluacion.java")
                                        .toString()));
        comando.addAll(Arrays.asList(argumentos));
        Path log = temporal.resolve("banco-" + System.nanoTime() + ".log");
        Process proceso =
                new ProcessBuilder(comando)
                        .redirectErrorStream(true)
                        .redirectOutput(log.toFile())
                        .start();
        if (!proceso.waitFor(3, TimeUnit.MINUTES)) {
            proceso.descendants().forEach(ProcessHandle::destroyForcibly);
            proceso.destroyForcibly();
            throw new AssertionError("El banco no terminó en tres minutos");
        }
        return new Ejecucion(proceso.exitValue(), Files.readString(log, StandardCharsets.UTF_8));
    }

    private static Path raiz() {
        return Path.of(System.getProperty("opsec.demo")).toAbsolutePath().normalize().getParent();
    }

    private static String hash(Path archivo) throws Exception {
        return HexFormat.of()
                .formatHex(
                        MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archivo)));
    }

    private record Ejecucion(int codigo, String salida) {}
}
