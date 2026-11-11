package io.github.physanto.opsecengine.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.physanto.opsecengine.dominio.caso.Suite;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.nucleo.extensiones.RegistroExtensiones;
import io.github.physanto.opsecengine.nucleo.proyecto.ProyectoService;
import java.awt.GraphicsEnvironment;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Preparación de los diez pares de v0.3. La aceptación completa es optativa hasta integrar sus
 * dependencias: activar con {@code -Dopsec.integracion.v03=true}. Una preparación válida no
 * significa que los fallos ya se detecten.
 */
class IntegracionV03IT {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Set<String> TODOS =
            IntStream.rangeClosed(1, 10)
                    .mapToObj(numero -> "F" + numero)
                    .collect(Collectors.toSet());

    @Test
    void losVeinteProyectosYSusSuitesValidanYAislanCadaFallo() throws Exception {
        List<Perfil> perfiles = cargarPerfiles();
        assertThat(perfiles).hasSize(10);
        Set<String> disponibles = oraculosDisponibles();
        Path salida = carpetaResultados();
        Files.createDirectories(salida);
        ObjectNode preparacion = JSON.createObjectNode();
        preparacion.put("fallos", perfiles.size()).put("variantes", perfiles.size() * 2);
        preparacion.put("aceptacionCompletaEjecutada", false);
        preparacion.set(
                "oraculosDisponibles", JSON.valueToTree(disponibles.stream().sorted().toList()));
        preparacion.set("bloqueos", JSON.valueToTree(bloqueos(perfiles, disponibles)));
        Files.writeString(
                salida.resolve("preparacion.json"),
                preparacion.toPrettyString() + "\n",
                StandardCharsets.UTF_8);
        escribirBanco(
                perfiles.stream()
                        .filter(perfil -> Set.of("F6", "F8").contains(perfil.fallo()))
                        .toList(),
                salida.resolve("banco-disponible.json"));
    }

    @Test
    void sinProveedoresSeBloqueaCadaFalloEnVezDeReducirLaCobertura() throws Exception {
        List<Perfil> perfiles = cargarPerfiles();
        List<String> faltantes = bloqueos(perfiles, Set.of());
        assertThat(faltantes).hasSize(10);
        for (String fallo : TODOS) {
            assertThat(faltantes).anyMatch(motivo -> motivo.startsWith(fallo + ":"));
        }
    }

    @Test
    void cadaFalloNoCumpleEnVulnerableYCumpleEnCorregida() throws Exception {
        assumeTrue(
                Boolean.getBoolean("opsec.integracion.v03"),
                "Aceptación v0.3 pendiente; activar explícitamente tras integrar las dependencias");
        List<Perfil> perfiles = cargarPerfiles();
        List<String> faltantes = bloqueos(perfiles, oraculosDisponibles());
        assertThat(faltantes)
                .as(
                        "#67 no puede aprobarse con cobertura parcial. Dependencias pendientes: %s",
                        faltantes)
                .isEmpty();
        assertThat(GraphicsEnvironment.isHeadless())
                .as("La integración necesita pantalla o Xvfb")
                .isFalse();
        Path salida = carpetaResultados().resolve("ejecucion-" + System.nanoTime());
        Files.createDirectories(salida);
        Path banco = salida.resolve("banco-completo.json");
        escribirBanco(perfiles, banco);
        Path mediciones = salida.resolve("mediciones");
        Path log = salida.resolve("banco.log");
        Process proceso =
                new ProcessBuilder(
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
                                        .toString(),
                                "ejecutar",
                                banco.toString(),
                                "--salida",
                                mediciones.toString())
                        .redirectErrorStream(true)
                        .redirectOutput(log.toFile())
                        .start();
        if (!proceso.waitFor(6, TimeUnit.MINUTES)) {
            proceso.descendants().forEach(ProcessHandle::destroyForcibly);
            proceso.destroyForcibly();
            throw new AssertionError("La integración no terminó en seis minutos");
        }
        assertThat(proceso.exitValue()).as(Files.readString(log, StandardCharsets.UTF_8)).isZero();
        JsonNode ejecucion =
                JSON.readTree(
                        Files.readString(
                                mediciones.resolve("ejecucion.json"), StandardCharsets.UTF_8));
        assertThat(ejecucion.path("registros").size()).isEqualTo(20);
        for (JsonNode registro : ejecucion.path("registros")) {
            boolean vulnerable = registro.path("id").asText().endsWith("-vulnerable");
            assertThat(registro.path("codigoCli").asInt())
                    .as(registro.path("id").asText())
                    .isEqualTo(vulnerable ? 3 : 0);
        }
    }

    private static List<Perfil> cargarPerfiles() throws Exception {
        Path carpeta =
                raiz().resolve("apps-practica").resolve("laboratorio").resolve("integracion-v03");
        JsonNode matriz =
                JSON.readTree(
                        Files.readString(carpeta.resolve("matriz.json"), StandardCharsets.UTF_8));
        assertThat(matriz.path("version").asInt()).isEqualTo(1);
        List<Perfil> perfiles = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        ProyectoService servicio = new ProyectoService();
        for (JsonNode entrada : matriz.path("fallos")) {
            String fallo = entrada.path("fallo").asText();
            assertThat(vistos.add(fallo)).as("Fallo repetido: %s", fallo).isTrue();
            List<Path> proyectos =
                    List.of(
                            carpeta.resolve(entrada.path("proyectoVulnerable").asText()),
                            carpeta.resolve(entrada.path("proyectoCorregido").asText()));
            List<Path> archivosSuite =
                    List.of(
                            carpeta.resolve(entrada.path("suiteVulnerable").asText()),
                            carpeta.resolve(entrada.path("suiteCorregida").asText()));
            List<Suite> suites = new ArrayList<>();
            for (int variante = 0; variante < 2; variante++) {
                Proyecto proyecto = servicio.cargarProyecto(proyectos.get(variante));
                assertThat(proyecto.jar()).isRegularFile();
                suites.add(servicio.cargarSuite(archivosSuite.get(variante), proyecto));
                Set<String> activos = activos(proyecto);
                Set<String> esperados = variante == 0 ? Set.of(fallo) : Set.of();
                if (fallo.equals("F7"))
                    esperados = variante == 0 ? Set.of("F4", "F7") : Set.of("F4");
                assertThat(activos)
                        .as("Interruptores de %s", proyectos.get(variante))
                        .isEqualTo(esperados);
                assertThat(proyecto.clasePrincipal())
                        .isEqualTo(
                                fallo.equals("F3")
                                        ? "io.github.physanto.opsecengine.practica.gui.EliminarPersona"
                                        : null);
            }
            Set<String> casos =
                    suites.get(0).casos().stream()
                            .map(caso -> caso.id())
                            .collect(Collectors.toSet());
            assertThat(
                            suites.get(1).casos().stream()
                                    .map(caso -> caso.id())
                                    .collect(Collectors.toSet()))
                    .as("Casos comparables de %s", fallo)
                    .isEqualTo(casos);
            String oraculo =
                    entrada.path("oraculo").isNull() ? null : entrada.path("oraculo").asText();
            String ambito = entrada.path("ambito").asText();
            assertThat(ambito).isIn("CASO", "SUITE");
            Set<String> casosConFallo = new HashSet<>();
            entrada.path("casosConFallo").forEach(id -> casosConFallo.add(id.asText()));
            assertThat(casos).containsAll(casosConFallo);
            if (oraculo != null) {
                assertThat(oraculo).isNotBlank();
                for (Suite suite : suites) {
                    for (var caso : suite.casos()) {
                        assertThat(caso.oraculos())
                                .as("Oráculo explícito de %s/%s", fallo, caso.id())
                                .contains(oraculo);
                    }
                }
            } else {
                assertThat(fallo).as("Solo F9 espera todavía un ID acordado").isEqualTo("F9");
            }
            perfiles.add(
                    new Perfil(
                            fallo,
                            oraculo,
                            ambito,
                            casosConFallo,
                            proyectos,
                            archivosSuite,
                            suites));
        }
        assertThat(vistos).isEqualTo(TODOS);
        return perfiles;
    }

    private static Set<String> activos(Proyecto proyecto) {
        String valor =
                proyecto.argumentosJvm().stream()
                        .filter(arg -> arg.startsWith("-Dpractica.corregir="))
                        .map(arg -> arg.substring("-Dpractica.corregir=".length()))
                        .findFirst()
                        .orElseThrow();
        // La matriz v0.3 sigue cubriendo F1–F10; los perfiles corrigen también F11–F13 de v0.5.
        Set<String> resultado =
                IntStream.rangeClosed(1, 13)
                        .mapToObj(numero -> "F" + numero)
                        .collect(Collectors.toSet());
        if (valor.equals("todos")) resultado.clear();
        else resultado.removeAll(List.of(valor.split(",")));
        return resultado;
    }

    private static List<String> bloqueos(List<Perfil> perfiles, Set<String> disponibles) {
        List<String> resultado = new ArrayList<>();
        for (Perfil perfil : perfiles) {
            if (perfil.oraculo() == null)
                resultado.add(perfil.fallo() + ": sin ID de oráculo acordado (#38)");
            else if (!disponibles.contains(perfil.oraculo()))
                resultado.add(perfil.fallo() + ": " + perfil.oraculo());
        }
        return resultado;
    }

    private static Set<String> oraculosDisponibles() {
        return RegistroExtensiones.descubrir().oraculos().stream()
                .map(oraculo -> oraculo.id())
                .collect(Collectors.toSet());
    }

    private static void escribirBanco(List<Perfil> perfiles, Path archivo) throws Exception {
        ObjectNode banco =
                JSON.createObjectNode().put("version", 1).put("tiempoMaximoSegundos", 30);
        var escenarios = banco.putArray("escenarios");
        for (Perfil perfil : perfiles) {
            assertThat(perfil.oraculo())
                    .as("No se genera una referencia sin oráculo acordado")
                    .isNotBlank();
            for (int variante = 0; variante < 2; variante++) {
                boolean vulnerable = variante == 0;
                String id =
                        perfil.fallo().toLowerCase(java.util.Locale.ROOT)
                                + (vulnerable ? "-vulnerable" : "-corregido");
                ObjectNode escenario =
                        escenarios
                                .addObject()
                                .put("id", id)
                                .put("aplicacion", "clientes-vulnerable")
                                .put("version", id)
                                .put(
                                        "proyecto",
                                        perfil.proyectos()
                                                .get(variante)
                                                .toAbsolutePath()
                                                .toString())
                                .put(
                                        "suite",
                                        perfil.archivosSuite()
                                                .get(variante)
                                                .toAbsolutePath()
                                                .toString());
                ObjectNode esperados = escenario.putObject("esperados");
                for (var caso : perfil.suites().get(variante).casos()) {
                    var ids = esperados.putArray(caso.id());
                    if (vulnerable
                            && perfil.ambito().equals("CASO")
                            && perfil.casosConFallo().contains(caso.id())) {
                        ids.add(perfil.oraculo());
                    }
                }
                if (perfil.ambito().equals("SUITE")) {
                    var ids = esperados.putArray("$suite");
                    if (vulnerable) ids.add(perfil.oraculo());
                }
            }
        }
        Files.writeString(archivo, banco.toPrettyString() + "\n", StandardCharsets.UTF_8);
    }

    private static Path raiz() {
        return Path.of(System.getProperty("opsec.demo")).toAbsolutePath().normalize().getParent();
    }

    private static Path carpetaResultados() {
        return raiz().resolve("opsec-cli").resolve("target").resolve("integracion-v03");
    }

    private record Perfil(
            String fallo,
            String oraculo,
            String ambito,
            Set<String> casosConFallo,
            List<Path> proyectos,
            List<Path> archivosSuite,
            List<Suite> suites) {}
}
