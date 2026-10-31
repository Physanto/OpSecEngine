package io.github.physanto.opsecengine.informes;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.physanto.opsecengine.dominio.extension.Reportero;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoSuite;
import io.github.physanto.opsecengine.soporte.DatosEjemplo;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Ejemplo de referencia para probar un reportero: escribe el resultado de ejemplo de {@code
 * opsec-soporte-pruebas} y revisa el archivo producido.
 */
class ReporteroJsonTest {

    private static final JsonMapper LECTOR =
            JsonMapper.builder().addModule(new JavaTimeModule()).build();

    private final ReporteroJson reportero = new ReporteroJson();

    @TempDir Path destino;

    @Test
    void elArchivoSePuedeLeerOtraVezComoResultado() throws Exception {
        ResultadoSuite original = DatosEjemplo.resultadoSuite();

        reportero.escribir(original, destino);

        assertThat(
                        LECTOR.readValue(
                                destino.resolve(ReporteroJson.ARCHIVO).toFile(),
                                ResultadoSuite.class))
                .isEqualTo(original);
    }

    @Test
    void escribeJsonLegibleEnUtf8YReemplazaElAnterior() throws Exception {
        Path archivo = destino.resolve(ReporteroJson.ARCHIVO);
        Files.writeString(archivo, "anterior", StandardCharsets.UTF_8);

        reportero.escribir(DatosEjemplo.resultadoSuite(), destino);

        String texto = Files.readString(archivo, StandardCharsets.UTF_8);
        assertThat(texto).startsWith("{").contains("\n  \"nombreSuite\" : \"ejemplo\"");
        assertThat(texto).contains("sesión").doesNotContain("anterior");
        assertThat(texto.indexOf("\"accion\"")).isLessThan(texto.indexOf("\"modo\""));
    }

    @Test
    void estaRegistradoParaServiceLoaderConElFormatoJson() {
        assertThat(ServiceLoader.load(Reportero.class))
                .filteredOn(ReporteroJson.class::isInstance)
                .singleElement()
                .extracting(Reportero::formato)
                .isEqualTo("json");
    }
}
