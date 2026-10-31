package io.github.physanto.opsecengine.verificacion;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.physanto.opsecengine.soporte.TrazaEjemplo;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Las trazas de ejemplo de {@code opsec-soporte-pruebas} cumplen el esquema de línea de traza y el
 * catálogo de eventos.
 */
class TrazasSoporteTest {

    private static final JsonMapper JSON = new JsonMapper();

    @ParameterizedTest
    @EnumSource(TrazaEjemplo.class)
    void cadaTrazaDeEjemploCumpleElEsquemaYElCatalogo(TrazaEjemplo ejemplo) throws IOException {
        List<JsonNode> eventos = leer(ejemplo);

        assertThat(eventos).isNotEmpty();
        for (JsonNode evento : eventos) {
            assertThat(Contratos.errores(Contratos.TRAZA_EVENTO, evento))
                    .as("errores del evento %s de %s", evento.path("secuencia"), ejemplo.archivo())
                    .isEmpty();
        }
        Catalogos.verificarTraza(eventos, ejemplo.archivo());
    }

    private static List<JsonNode> leer(TrazaEjemplo ejemplo) throws IOException {
        List<JsonNode> eventos = new ArrayList<>();
        try (InputStream flujo = ejemplo.abrir();
                BufferedReader lector =
                        new BufferedReader(new InputStreamReader(flujo, StandardCharsets.UTF_8))) {
            for (String linea = lector.readLine(); linea != null; linea = lector.readLine()) {
                if (!linea.isBlank()) {
                    eventos.add(JSON.readTree(linea));
                }
            }
        }
        return eventos;
    }
}
