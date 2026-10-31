package io.github.physanto.opsecengine.nucleo.informe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.physanto.opsecengine.dominio.extension.Reportero;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoSuite;
import io.github.physanto.opsecengine.nucleo.extensiones.RegistroExtensiones;
import io.github.physanto.opsecengine.soporte.DatosEjemplo;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InformeServiceTest {

    private final InformeService informes =
            new InformeService(
                    new RegistroExtensiones(
                            List.of(), List.of(), List.of(texto("txt"), texto("md")), List.of()));

    @TempDir Path carpeta;

    @Test
    void escribeCadaFormatoPedidoEnLaCarpetaDeResultados() throws Exception {
        Path destino = carpeta.resolve("resultados").resolve("2026-10-30_1530");

        informes.generar(DatosEjemplo.resultadoSuite(), destino, List.of("md", "txt", "md"));

        assertThat(destino.resolve("informe.txt")).hasContent("ejemplo");
        assertThat(destino.resolve("informe.md")).hasContent("ejemplo");
    }

    @Test
    void noEscribeNadaSiFaltaUnFormato() throws Exception {
        ResultadoSuite resultado = DatosEjemplo.resultadoSuite();

        assertThatThrownBy(() -> informes.generar(resultado, carpeta, List.of("txt", "sarif")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No hay un reportero para el formato sarif")
                .hasMessageContaining("[txt, md]");
        assertThat(carpeta).isEmptyDirectory();
    }

    @Test
    void listaLosFormatosDisponibles() {
        assertThat(informes.formatosDisponibles()).containsExactly("txt", "md");
    }

    /** Reportero que escribe el nombre de la suite en informe.&lt;formato&gt;. */
    private static Reportero texto(String formato) {
        return new Reportero() {
            @Override
            public String formato() {
                return formato;
            }

            @Override
            public void escribir(ResultadoSuite resultado, Path destino) throws IOException {
                Files.writeString(
                        destino.resolve("informe." + formato),
                        resultado.nombreSuite(),
                        StandardCharsets.UTF_8);
            }
        };
    }
}
