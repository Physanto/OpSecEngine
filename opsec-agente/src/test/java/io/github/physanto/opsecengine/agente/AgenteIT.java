package io.github.physanto.opsecengine.agente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

import io.github.physanto.opsecengine.protocolo.dto.CodigoResultado;
import io.github.physanto.opsecengine.protocolo.dto.CriterioDto;
import io.github.physanto.opsecengine.protocolo.dto.EventoDto;
import io.github.physanto.opsecengine.protocolo.dto.HolaDto;
import io.github.physanto.opsecengine.protocolo.dto.LocalizadorDto;
import io.github.physanto.opsecengine.protocolo.dto.NodoArbolDto;
import io.github.physanto.opsecengine.protocolo.dto.PasoDto;
import io.github.physanto.opsecengine.protocolo.dto.ResultadoPasoDto;
import io.github.physanto.opsecengine.soporte.AplicacionDePrueba;
import io.github.physanto.opsecengine.soporte.ExcepcionErrorRemoto;
import io.github.physanto.opsecengine.soporte.MotorFalso;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Pruebas de integración: lanzan una aplicación en otra JVM con {@code -javaagent} apuntando al jar
 * empaquetado del agente y conversan con él desde {@link MotorFalso}.
 *
 * <p>Las ejecuta failsafe en la fase {@code integration-test}, después de empaquetar el agente. La
 * ruta del jar llega en la propiedad del sistema {@code opsec.agente.jar}. La prueba con ventanas
 * se omite si el entorno no tiene pantalla.
 */
class AgenteIT {

    private static final Duration ESPERA = Duration.ofSeconds(30);

    private Path carpeta;

    @BeforeEach
    void crearCarpeta(TestInfo prueba) throws IOException {
        carpeta = carpetaRegistros(prueba);
    }

    @Test
    void elAgenteSaludaYEnviaLaConsolaDeLaAplicacion() throws Exception {
        Path salida = carpeta.resolve("aplicacion.log");
        try (MotorFalso motor = new MotorFalso()) {
            Process proceso = lanzar(motor, AplicacionDePrueba.class.getName(), salida, "60");
            try {
                HolaDto hola = motor.esperarHola(ESPERA);

                assertThat(hola.getToken()).isEqualTo(motor.token());
                assertThat(hola.getVersionAgente()).isNotEqualTo("desarrollo");
                assertThat(motor.pedirArbol().getVentanas()).isEmpty();
                assertThatThrownBy(() -> motor.pedirCaptura(null))
                        .isInstanceOf(ExcepcionErrorRemoto.class);
                esperarEvento(motor, "CONSOLA", AplicacionDePrueba.LINEA_INICIO);
                assertThat(motor.cerrar()).isTrue();
            } finally {
                detener(proceso);
            }
        }
        assertThat(Files.readString(salida, StandardCharsets.UTF_8))
                .contains(AplicacionDePrueba.LINEA_INICIO)
                .doesNotContain(RegistroAgente.PREFIJO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SEMANTICO", "EVENTOS"})
    void elAgenteManejaUnaVentanaSwing(String modo) throws Exception {
        assumeThat(GraphicsEnvironment.isHeadless()).as("necesita pantalla").isFalse();
        Path salida = carpeta.resolve("ventana.log");
        try (MotorFalso motor = new MotorFalso()) {
            Process proceso = lanzar(motor, VentanaDePrueba.class.getName(), salida);
            try {
                motor.esperarHola(ESPERA);

                assertThat(
                                paso(
                                        motor,
                                        modo,
                                        "ESPERAR",
                                        criterio("TITULO_VENTANA", VentanaDePrueba.TITULO),
                                        null))
                        .isEqualTo(CodigoResultado.OK);
                assertThat(paso(motor, modo, "LLENAR", criterio("ETIQUETA", "Nombre"), "Ana"))
                        .isEqualTo(CodigoResultado.OK);
                assertThat(paso(motor, modo, "PULSAR", criterio("TEXTO", "Saludar"), null))
                        .isEqualTo(CodigoResultado.OK);
                assertThat(eventos(motor, "CONSOLA"))
                        .anySatisfy(
                                e -> assertThat(e.getDatos()).containsEntry("texto", "Hola, Ana"));
                if ("EVENTOS".equals(modo)) {
                    assertThat(eventos(motor, "CONSOLA"))
                            .extracting(e -> e.getDatos().get("texto"))
                            .contains("Tecla en Nombre", "Ratón en Saludar");
                } else {
                    assertThat(eventos(motor, "CONSOLA"))
                            .extracting(e -> e.getDatos().get("texto"))
                            .doesNotContain("Tecla en Nombre", "Ratón en Saludar");
                }
                assertThat(eventos(motor, "DIALOGO"))
                        .singleElement()
                        .satisfies(e ->
                                assertThat(e.getDatos())
                                        .containsEntry("texto", "Hola, Ana")
                                        .containsEntry("titulo", "Saludo")
                                        .containsEntry("tipoMensaje", "INFORMACION")
                                        .containsEntry("opciones", List.of("Cerrar")));

                assertThat(paso(motor, modo, "PULSAR", criterio("TEXTO", "Fallar"), null))
                        .as("un diálogo modal tapa la ventana")
                        .isEqualTo(CodigoResultado.NO_ALCANZABLE);
                assertThat(paso(motor, modo, "PULSAR", criterio("TEXTO", "Cerrar"), null))
                        .isEqualTo(CodigoResultado.OK);
                assertThat(paso(motor, modo, "PULSAR", criterio("TEXTO", "Bloqueado"), null))
                        .isEqualTo(CodigoResultado.NO_ALCANZABLE);
                assertThat(paso(motor, modo, "PULSAR", criterio("TEXTO", "Fallar"), null))
                        .isEqualTo(CodigoResultado.OK);
                assertThat(eventos(motor, "EXCEPCION"))
                        .singleElement()
                        .satisfies(e ->
                                assertThat(e.getDatos())
                                        .containsEntry("clase", "java.lang.IllegalStateException")
                                        .containsEntry("mensaje", "Fallo sembrado")
                                        .containsEntry("capturada", false));

                List<NodoArbolDto> ventanas = motor.pedirArbol().getVentanas();
                assertThat(ventanas)
                        .extracting(NodoArbolDto::getTexto)
                        .contains(VentanaDePrueba.TITULO);
                assertThat(eventos(motor, "VENTANA_ABIERTA")).isNotEmpty();
                assertThat(motor.cerrar()).isTrue();
            } finally {
                detener(proceso);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"SEMANTICO", "EVENTOS"})
    void elAgenteSeleccionaMarcaNavegaYCierraVentanas(String modo) throws Exception {
        assumeThat(GraphicsEnvironment.isHeadless()).as("necesita pantalla").isFalse();
        Path salida = carpeta.resolve("navegacion.log");
        try (MotorFalso motor = new MotorFalso()) {
            Process proceso = lanzar(motor, VentanaDePrueba.class.getName(), salida);
            try {
                motor.esperarHola(ESPERA);
                assertThat(
                                paso(
                                        motor,
                                        modo,
                                        "ESPERAR",
                                        criterio("TITULO_VENTANA", VentanaDePrueba.TITULO),
                                        null))
                        .isEqualTo(CodigoResultado.OK);

                assertThat(paso(motor, modo, "SELECCIONAR", criterio("ETIQUETA", "Rol"), "Admin"))
                        .isEqualTo(CodigoResultado.OK);
                assertThat(
                                paso(
                                        motor,
                                        modo,
                                        "SELECCIONAR",
                                        criterio("ETIQUETA", "Rol"),
                                        "Gerente",
                                        300))
                        .isEqualTo(CodigoResultado.NO_ENCONTRADO);
                assertThat(paso(motor, modo, "MARCAR", criterio("TEXTO", "Recordar"), "true"))
                        .isEqualTo(CodigoResultado.OK);
                assertThat(paso(motor, modo, "MARCAR", criterio("TEXTO", "Recordar"), "true"))
                        .as("ya estaba marcada; no se vuelve a pulsar")
                        .isEqualTo(CodigoResultado.OK);
                assertThat(paso(motor, modo, "MARCAR", criterio("TEXTO", "Recordar"), "false"))
                        .isEqualTo(CodigoResultado.OK);

                assertThat(paso(motor, modo, "PULSAR", criterio("TEXTO", "Detalle"), null, 300))
                        .as("está en una pestaña que no se ve")
                        .isEqualTo(CodigoResultado.NO_ALCANZABLE);
                assertThat(paso(motor, modo, "IR_A", criterio("TEXTO", "Detalle"), null))
                        .isEqualTo(CodigoResultado.OK);
                assertThat(paso(motor, modo, "PULSAR", criterio("TEXTO", "Detalle"), null))
                        .isEqualTo(CodigoResultado.OK);
                assertThat(paso(motor, modo, "IR_A", criterio("TEXTO", "General"), null))
                        .isEqualTo(CodigoResultado.OK);

                assertThat(paso(motor, modo, "PULSAR", criterio("TEXTO", "Abrir"), null))
                        .isEqualTo(CodigoResultado.OK);
                assertThat(
                                paso(
                                        motor,
                                        modo,
                                        "CERRAR_VENTANA",
                                        criterio("TITULO_VENTANA", "Auxiliar"),
                                        null))
                        .isEqualTo(CodigoResultado.OK);
                esperarEvento(motor, "VENTANA_CERRADA", "titulo", "Auxiliar");

                esperarEvento(motor, "CONSOLA", "Detalle pulsado");
                assertThat(eventos(motor, "CONSOLA"))
                        .extracting(e -> e.getDatos().get("texto"))
                        .containsSubsequence(
                                "Rol: Admin",
                                "Recordar: true",
                                "Recordar: false",
                                "Pestaña: Avanzado",
                                "Detalle pulsado",
                                "Pestaña: General")
                        .containsOnlyOnce("Recordar: true");
                assertThat(motor.cerrar()).isTrue();
            } finally {
                detener(proceso);
            }
        }
    }

    private static CodigoResultado paso(
            MotorFalso motor, String modo, String accion, LocalizadorDto localizador, String valor)
            throws IOException {
        return paso(motor, modo, accion, localizador, valor, 3000);
    }

    private static CodigoResultado paso(
            MotorFalso motor,
            String modo,
            String accion,
            LocalizadorDto localizador,
            String valor,
            long esperaMaximaMs)
            throws IOException {
        ResultadoPasoDto resultado =
                motor.enviarPaso(new PasoDto(accion, localizador, valor, modo, esperaMaximaMs));
        return resultado.getCodigo();
    }

    private static LocalizadorDto criterio(String estrategia, String valor) {
        return new LocalizadorDto(List.of(new CriterioDto(estrategia, valor)));
    }

    private static List<EventoDto> eventos(MotorFalso motor, String tipo) {
        return motor.eventos().stream().filter(e -> e.getTipo().equals(tipo)).toList();
    }

    /** Los eventos llegan en paralelo a las respuestas; espera hasta que aparezca el buscado. */
    private static void esperarEvento(MotorFalso motor, String tipo, String texto)
            throws InterruptedException {
        esperarEvento(motor, tipo, "texto", texto);
    }

    /** Espera un evento del tipo cuyo campo de datos contenga el texto. */
    private static void esperarEvento(MotorFalso motor, String tipo, String campo, String texto)
            throws InterruptedException {
        long limite = System.nanoTime() + ESPERA.toNanos();
        while (System.nanoTime() < limite) {
            if (eventos(motor, tipo).stream()
                    .anyMatch(e -> String.valueOf(e.getDatos().get(campo)).contains(texto))) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError(
                "No llegó un evento " + tipo + " con " + campo + " que contenga " + texto);
    }

    private static Process lanzar(MotorFalso motor, String clase, Path salida, String... extra)
            throws IOException, URISyntaxException {
        Path jarAgente = Path.of(System.getProperty("opsec.agente.jar"));
        assertThat(jarAgente).isRegularFile();
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        List<String> comando =
                new ArrayList<>(
                        List.of(
                                java.toString(),
                                "-javaagent:" + jarAgente + "=" + motor.argumentosAgente(),
                                "-cp",
                                String.join(
                                        File.pathSeparator,
                                        ubicacion(AplicacionDePrueba.class),
                                        ubicacion(VentanaDePrueba.class)),
                                clase));
        comando.addAll(List.of(extra));
        return new ProcessBuilder(comando)
                .redirectErrorStream(true)
                .redirectOutput(salida.toFile())
                .start();
    }

    private static String ubicacion(Class<?> clase) throws URISyntaxException {
        return Path.of(clase.getProtectionDomain().getCodeSource().getLocation().toURI())
                .toString();
    }

    private static void detener(Process proceso) throws InterruptedException {
        proceso.destroy();
        if (!proceso.waitFor(10, TimeUnit.SECONDS)) {
            proceso.destroyForcibly();
        }
    }

    /**
     * Carpeta para la salida de la aplicación lanzada, dentro de {@code target/registros-it}.
     *
     * <p>No se usa {@code @TempDir}: en Windows el archivo de salida puede seguir bloqueado unos
     * instantes después de detener el proceso, y JUnit falla al borrar la carpeta temporal.
     */
    private static Path carpetaRegistros(TestInfo prueba) throws IOException {
        String nombre = prueba.getTestMethod().map(metodo -> metodo.getName()).orElse("prueba");
        return Files.createDirectories(
                Path.of("target", "registros-it", nombre + "-" + System.nanoTime()));
    }
}
