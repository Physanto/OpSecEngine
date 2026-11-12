package io.github.physanto.opsecengine.oraculos;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PlantillasSuiteTest {
    private static final String ESQUEMA_ID =
            "https://raw.githubusercontent.com/Physanto/OpSecEngine/main/contratos/esquemas/suite.schema.json";
    private final YAMLMapper mapper =
            YAMLMapper.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .build();

    @ParameterizedTest
    @CsvSource({"funcional,FUNCIONAL", "validacion,VALIDACION", "seguridad,SEGURIDAD"})
    void lasPlantillasEmpaquetadasCumplenElEsquemaYLosCatalogos(String nombre, String enfoque)
            throws Exception {
        String recurso = "/io/github/physanto/opsecengine/oraculos/plantillas/" + nombre + ".yaml";
        JsonNode plantilla;
        try (var flujo = getClass().getResourceAsStream(recurso)) {
            assertThat(flujo).as("recurso de plantilla %s", recurso).isNotNull();
            try (var lector = new InputStreamReader(flujo, StandardCharsets.UTF_8)) {
                plantilla = mapper.readTree(lector);
            }
        }
        assertThat(esquema().validate(plantilla)).as("validación oficial de %s", recurso).isEmpty();
        assertThat(plantilla.path("enfoques").size()).isEqualTo(1);
        assertThat(plantilla.path("enfoques").get(0).asText()).isEqualTo(enfoque);

        Set<String> catalogo = new HashSet<>();
        try (var lector =
                Files.newBufferedReader(
                        Path.of("..", "contratos", "catalogos", "oraculos.yaml"),
                        StandardCharsets.UTF_8)) {
            mapper.readTree(lector)
                    .path("oraculos")
                    .forEach(o -> catalogo.add(o.path("id").asText()));
        }
        Set<String> ids = new HashSet<>();
        for (JsonNode caso : plantilla.path("casos")) {
            assertThat(ids.add(caso.path("id").asText())).as("id de caso único").isTrue();
            for (JsonNode oraculo : caso.path("oraculos")) {
                assertThat(catalogo)
                        .as("oráculo disponible en %s", recurso)
                        .contains(oraculo.asText());
            }
            assertThat(caso.path("ataques").size())
                    .as("plantilla sin biblioteca externa obligatoria")
                    .isZero();
            assertThat(caso.has("rol")).as("plantilla sin rol externo obligatorio").isFalse();
            for (JsonNode paso : caso.path("pasos")) {
                for (JsonNode valor : paso.path("llenar")) {
                    assertThat(valor.asText())
                            .doesNotContain("{payload}", "{canario}", "{usuario}", "{contrasena}");
                }
            }
        }
    }

    private static Schema esquema() throws Exception {
        String contenido =
                Files.readString(
                        Path.of("..", "contratos", "esquemas", "suite.schema.json"),
                        StandardCharsets.UTF_8);
        return SchemaRegistry.withDefaultDialect(
                        SpecificationVersion.DRAFT_2020_12,
                        constructor ->
                                constructor.schemas(id -> {
                                    if (!ESQUEMA_ID.equals(id)) {
                                        throw new IllegalArgumentException(
                                                "Referencia de esquema no local: " + id);
                                    }
                                    return contenido;
                                }))
                .getSchema(SchemaLocation.of(ESQUEMA_ID));
    }
}
