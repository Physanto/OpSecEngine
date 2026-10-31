package io.github.physanto.opsecengine.protocolo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Lee los ejemplos de {@code contratos/protocolo/ejemplos} con el codec real. */
class EjemplosProtocoloTest {

    private static final Path EJEMPLOS = Path.of("..", "contratos", "protocolo", "ejemplos");

    private final CodecMensajes codec = new CodecMensajes();

    private static String nombreArchivo(TipoMensaje tipo) {
        return tipo.name().toLowerCase(Locale.ROOT).replace('_', '-') + ".json";
    }

    @Test
    void hayUnEjemploPorTipoYNingunoDeMas() throws IOException {
        List<String> esperados =
                Stream.of(TipoMensaje.values())
                        .map(EjemplosProtocoloTest::nombreArchivo)
                        .collect(Collectors.toList());

        try (Stream<Path> archivos = Files.list(EJEMPLOS)) {
            assertThat(archivos.map(archivo -> archivo.getFileName().toString()))
                    .containsExactlyInAnyOrderElementsOf(esperados);
        }
    }

    @ParameterizedTest
    @EnumSource(TipoMensaje.class)
    void cadaEjemploSeLeeConSuTipoYSuCuerpo(TipoMensaje tipo) throws Exception {
        String json =
                Files.readString(EJEMPLOS.resolve(nombreArchivo(tipo)), StandardCharsets.UTF_8);

        Mensaje mensaje = codec.deserializar(json);

        assertThat(mensaje.getTipo()).isEqualTo(tipo);
        assertThat(mensaje.getVersion()).isEqualTo(Mensaje.VERSION_PROTOCOLO);
        Class<?> claseCuerpo = Muestras.CLASES.get(tipo);
        if (claseCuerpo == null) {
            assertThat(mensaje.getCuerpo().isEmpty()).isTrue();
        } else {
            assertThat(codec.leerCuerpo(mensaje, claseCuerpo)).isInstanceOf(claseCuerpo);
        }
        assertThat(codec.deserializar(codec.serializar(mensaje))).isEqualTo(mensaje);
    }
}
