package io.github.physanto.opsecengine.agente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import io.github.physanto.opsecengine.protocolo.CodecMensajes;
import io.github.physanto.opsecengine.protocolo.dto.ArbolDto;
import io.github.physanto.opsecengine.protocolo.dto.CodigoResultado;
import io.github.physanto.opsecengine.protocolo.dto.CriterioDto;
import io.github.physanto.opsecengine.protocolo.dto.LocalizadorDto;
import io.github.physanto.opsecengine.protocolo.dto.NodoArbolDto;
import io.github.physanto.opsecengine.protocolo.dto.PasoDto;
import io.github.physanto.opsecengine.soporte.MotorFalso;
import java.awt.GraphicsEnvironment;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Compara el árbol del inicio de sesión real con el ejemplo publicado del protocolo. */
class ArbolAppPracticaIT {

    @Test
    void elInicioDeSesionProduceLaEstructuraDelContrato() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "necesita pantalla");
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
                        Path.of("target", "registros-it", "arbol-" + System.nanoTime()));
        Path salida = carpeta.resolve("aplicacion.log");
        CodecMensajes codec = new CodecMensajes();
        String json =
                Files.readString(
                        Path.of("..", "contratos", "protocolo", "ejemplos", "arbol.json"),
                        StandardCharsets.UTF_8);
        NodoArbolDto esperado =
                codec.leerCuerpo(codec.deserializar(json), ArbolDto.class).getVentanas().get(0);

        try (MotorFalso motor = new MotorFalso()) {
            Process proceso =
                    new ProcessBuilder(
                                    Path.of(System.getProperty("java.home"), "bin", "java")
                                            .toString(),
                                    "-Dstdout.encoding=UTF-8",
                                    "-Dstderr.encoding=UTF-8",
                                    "-javaagent:" + jarAgente + "=" + motor.argumentosAgente(),
                                    "-jar",
                                    jarApp.toString())
                            .redirectErrorStream(true)
                            .redirectOutput(salida.toFile())
                            .start();
            try {
                motor.esperarHola(Duration.ofSeconds(30));
                ejecutar(motor, "ESPERAR", "TITULO_VENTANA", "Portada");
                ejecutar(motor, "PULSAR", "TEXTO", "Avanzar");
                ejecutar(motor, "PULSAR", "TEXTO", "Nube");
                ejecutar(motor, "ESPERAR", "TITULO_VENTANA", "Iniciar sesión");
                NodoArbolDto real =
                        motor.pedirArbol().getVentanas().stream()
                                .filter(nodo -> esperado.getTipo().equals(nodo.getTipo()))
                                .findFirst()
                                .orElseThrow();
                comparar(esperado, real);
                // Todas las rutas accionables del contrato también sirven para ejecutar un paso.
                verificarRutas(motor, esperado);
                assertThat(motor.cerrar()).isTrue();
                assertThat(proceso.waitFor(10, TimeUnit.SECONDS)).isTrue();
                assertThat(proceso.exitValue()).isZero();
            } finally {
                proceso.destroy();
                if (!proceso.waitFor(10, TimeUnit.SECONDS)) {
                    proceso.destroyForcibly();
                    proceso.waitFor(10, TimeUnit.SECONDS);
                }
            }
        }
    }

    private static void ejecutar(MotorFalso motor, String accion, String estrategia, String valor)
            throws Exception {
        assertThat(
                        motor.enviarPaso(
                                        new PasoDto(
                                                accion,
                                                new LocalizadorDto(
                                                        List.of(
                                                                new CriterioDto(
                                                                        estrategia, valor))),
                                                null,
                                                "SEMANTICO",
                                                10000))
                                .getCodigo())
                .as(accion + " " + valor)
                .isEqualTo(CodigoResultado.OK);
    }

    private static void comparar(NodoArbolDto esperado, NodoArbolDto real) {
        assertThat(real.getTipo()).isEqualTo(esperado.getTipo());
        assertThat(real.getRuta()).isEqualTo(esperado.getRuta());
        assertThat(real.getAcciones()).containsExactlyElementsOf(esperado.getAcciones());
        assertThat(real.isAlcanzable()).isEqualTo(esperado.isAlcanzable());
        if (esperado.getNombre() != null) {
            assertThat(real.getNombre()).isEqualTo(esperado.getNombre());
        }
        if (esperado.getTexto() != null) {
            assertThat(real.getTexto()).isEqualTo(esperado.getTexto());
        }
        for (NodoArbolDto hijo : esperado.getHijos()) {
            NodoArbolDto encontrado =
                    real.getHijos().stream()
                            .filter(nodo -> hijo.getRuta().equals(nodo.getRuta()))
                            .findFirst()
                            .orElseThrow(
                                    () -> new AssertionError("Falta el nodo " + hijo.getRuta()));
            comparar(hijo, encontrado);
        }
    }

    private static void verificarRutas(MotorFalso motor, NodoArbolDto nodo) throws Exception {
        if (!nodo.getAcciones().isEmpty()) {
            ejecutar(motor, "ESPERAR", "RUTA", nodo.getRuta());
        }
        for (NodoArbolDto hijo : nodo.getHijos()) {
            verificarRutas(motor, hijo);
        }
    }
}
