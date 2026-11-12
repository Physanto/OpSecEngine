package io.github.physanto.opsecengine.agente.swing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

/** Pruebas del observador de excepciones no capturadas, sin pantalla. */
class ObservadorExcepcionesTest {

    private final List<Map<String, Object>> eventos = new CopyOnWriteArrayList<>();
    private final List<String> registro = new ArrayList<>();

    @Test
    void emiteElEventoYPasaLaExcepcionAlManejadorAnterior() {
        List<Throwable> recibidas = new ArrayList<>();
        ObservadorExcepciones observador =
                new ObservadorExcepciones(
                        (tipo, datos) -> eventos.add(datos),
                        (hilo, excepcion) -> recibidas.add(excepcion),
                        registro::add);
        IllegalStateException excepcion = new IllegalStateException("Fallo sembrado");
        Thread hilo = new Thread(() -> {}, "trabajador");

        observador.uncaughtException(hilo, excepcion);

        assertThat(eventos)
                .singleElement()
                .satisfies(datos -> {
                    assertThat(datos)
                            .containsEntry("clase", "java.lang.IllegalStateException")
                            .containsEntry("mensaje", "Fallo sembrado")
                            .containsEntry("hilo", "trabajador")
                            .containsEntry("capturada", false);
                    assertThat((List<?>) datos.get("pila")).isNotEmpty();
                });
        assertThat(recibidas).containsExactly(excepcion);
        assertThat(registro).isEmpty();
    }

    @Test
    void sinManejadorAnteriorEscribeLaPilaComoLaJvm() {
        ObservadorExcepciones observador =
                new ObservadorExcepciones((tipo, datos) -> eventos.add(datos), null, registro::add);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        PrintStream original = System.err;
        System.setErr(new PrintStream(salida, true));
        try {
            observador.uncaughtException(
                    new Thread(() -> {}, "trabajador"), new IllegalArgumentException("malo"));
        } finally {
            System.setErr(original);
        }

        assertThat(new String(salida.toByteArray(), StandardCharsets.UTF_8))
                .startsWith(
                        "Exception in thread \"trabajador\" java.lang.IllegalArgumentException:"
                                + " malo");
        assertThat(eventos).hasSize(1);
    }

    @Test
    void siFallaLaEmisionLoRegistraYSigueConElManejadorAnterior() {
        List<Throwable> recibidas = new ArrayList<>();
        ObservadorExcepciones observador =
                new ObservadorExcepciones(
                        (tipo, datos) -> {
                            throw new IllegalStateException("sin conexión");
                        },
                        (hilo, excepcion) -> recibidas.add(excepcion),
                        registro::add);

        observador.uncaughtException(Thread.currentThread(), new RuntimeException("x"));

        assertThat(registro).singleElement().asString().contains("sin conexión");
        assertThat(recibidas).hasSize(1);
    }

    @Test
    void recortaLaPilaAlMaximo() {
        Throwable profunda = new RuntimeException("profunda");
        StackTraceElement[] pila = new StackTraceElement[100];
        for (int i = 0; i < pila.length; i++) {
            pila[i] = new StackTraceElement("Clase", "metodo" + i, "Clase.java", i);
        }
        profunda.setStackTrace(pila);

        Map<String, Object> datos = ObservadorExcepciones.datos(profunda, "main", true);

        assertThat((List<?>) datos.get("pila")).hasSize(ObservadorExcepciones.MAXIMO_LINEAS_PILA);
        assertThat(datos).containsEntry("capturada", true);
    }

    @Test
    void instalarloEncadenaElManejadorPorDefectoQueHabia() throws Exception {
        Thread.UncaughtExceptionHandler original = Thread.getDefaultUncaughtExceptionHandler();
        List<Throwable> recibidas = new CopyOnWriteArrayList<>();
        Thread.setDefaultUncaughtExceptionHandler((hilo, excepcion) -> recibidas.add(excepcion));
        try {
            ObservadorExcepciones.instalar((tipo, datos) -> eventos.add(datos), registro::add);
            Thread hilo =
                    new Thread(
                            () -> {
                                throw new IllegalStateException("en otro hilo");
                            },
                            "trabajador");
            hilo.start();
            hilo.join(5_000);
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(original);
        }

        assertThat(eventos)
                .singleElement()
                .satisfies(datos -> assertThat(datos).containsEntry("hilo", "trabajador"));
        assertThat(recibidas).singleElement().asString().contains("en otro hilo");
    }
}
