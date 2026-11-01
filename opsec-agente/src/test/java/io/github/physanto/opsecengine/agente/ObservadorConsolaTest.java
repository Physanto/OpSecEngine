package io.github.physanto.opsecengine.agente;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.physanto.opsecengine.agente.api.EmisorEventos;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Pruebas de la envoltura de la consola, sin reemplazar {@code System.out}. */
class ObservadorConsolaTest {

    private final List<Map<String, Object>> eventos = new ArrayList<>();
    private final EmisorEventos emisor =
            (tipo, datos) -> {
                assertThat(tipo).isEqualTo("CONSOLA");
                eventos.add(Map.copyOf(datos));
            };

    @Test
    void emiteUnaLineaPorSaltoYConservaLaSalidaOriginal() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream original = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        PrintStream observada =
                ObservadorConsola.envolver(
                        original, ObservadorConsola.SALIDA, StandardCharsets.UTF_8, emisor);

        observada.print("usuario=Admin, ");
        observada.print("contraseña=Admin#2026\n");
        observada.print("línea con retorno\r\n");
        observada.print("sin salto final");

        assertThat(eventos)
                .containsExactly(
                        Map.of("flujo", "SALIDA", "texto", "usuario=Admin, contraseña=Admin#2026"),
                        Map.of("flujo", "SALIDA", "texto", "línea con retorno"));
        assertThat(bytes.toString(StandardCharsets.UTF_8))
                .isEqualTo(
                        "usuario=Admin, contraseña=Admin#2026\nlínea con retorno\r\nsin salto final");
    }

    @Test
    void usaLaCodificacionDeLaSalidaOriginal() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream original = new PrintStream(bytes, true, StandardCharsets.ISO_8859_1);
        PrintStream observada =
                ObservadorConsola.envolver(
                        original, ObservadorConsola.ERROR, StandardCharsets.ISO_8859_1, emisor);

        observada.print("contraseña\n");

        assertThat(eventos).containsExactly(Map.of("flujo", "ERROR", "texto", "contraseña"));
        assertThat(bytes.toString(StandardCharsets.ISO_8859_1)).isEqualTo("contraseña\n");
    }

    @Test
    void noObservaLoQueSeEscribeMientrasEmite() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream[] observada = new PrintStream[1];
        EmisorEventos reentrante =
                (tipo, datos) -> {
                    eventos.add(Map.copyOf(datos));
                    observada[0].print("escrito al emitir\n");
                };
        observada[0] =
                ObservadorConsola.envolver(
                        new PrintStream(bytes, true, StandardCharsets.UTF_8),
                        ObservadorConsola.SALIDA,
                        StandardCharsets.UTF_8,
                        reentrante);

        observada[0].print("hola\n");

        assertThat(eventos).hasSize(1);
        assertThat(bytes.toString(StandardCharsets.UTF_8)).isEqualTo("hola\nescrito al emitir\n");
    }
}
