package io.github.physanto.opsecengine.nucleo.proyecto;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.path.PathType;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Valida documentos contra los esquemas JSON de {@code contratos/esquemas/}.
 *
 * <p>Los esquemas se copian como recursos del módulo al construirlo, así que la validación no usa
 * la red: los {@code $id} remotos se resuelven contra esos recursos. Los mensajes salen en español
 * y las rutas en formato JSON Pointer.
 *
 * <p>Es seguro entre hilos.
 */
final class ValidadorEsquemas {

    /** Esquema de {@code proyecto.yaml}. */
    static final String PROYECTO = "proyecto.schema.json";

    /** Esquema de las suites. */
    static final String SUITE = "suite.schema.json";

    /** Prefijo común de los {@code $id} de los esquemas. */
    static final String BASE_ID =
            "https://raw.githubusercontent.com/Physanto/OpSecEngine/main/contratos/esquemas/";

    private static final String RECURSOS = "/io/github/physanto/opsecengine/nucleo/esquemas/";
    private static final String UNA_ACCION_POR_PASO = "#/$defs/paso/oneOf";

    private final SchemaRegistry registro =
            SchemaRegistry.withDefaultDialect(
                    SpecificationVersion.DRAFT_2020_12,
                    constructor ->
                            constructor
                                    .schemaRegistryConfig(
                                            SchemaRegistryConfig.builder()
                                                    .formatAssertionsEnabled(true)
                                                    .locale(Locale.forLanguageTag("es"))
                                                    .pathType(PathType.JSON_POINTER)
                                                    .build())
                                    .schemas(ValidadorEsquemas::leerEsquema));

    /**
     * Valida un documento.
     *
     * @param esquema nombre del archivo del esquema, por ejemplo {@link #SUITE}
     * @param documento documento leído del YAML
     * @return errores encontrados, sin repetidos; vacía si el documento es válido
     */
    List<ErrorConfiguracion> validar(String esquema, JsonNode documento) {
        Schema schema = registro.getSchema(SchemaLocation.of(BASE_ID + esquema));
        Set<ErrorConfiguracion> errores = new LinkedHashSet<>();
        for (Error error : schema.validate(documento)) {
            String ubicacionEsquema = error.getSchemaLocation().toString();
            if (ubicacionEsquema.contains(UNA_ACCION_POR_PASO + "/")) {
                // Cada alternativa del oneOf de un paso pide su propia acción: es ruido.
                continue;
            }
            String mensaje =
                    ubicacionEsquema.endsWith(UNA_ACCION_POR_PASO)
                            ? "un paso lleva exactamente una acción: pulsar, llenar, seleccionar,"
                                    + " marcar, esperar, ir_a, cerrar_ventana o capturar"
                            : error.getMessage();
            errores.add(new ErrorConfiguracion(error.getInstanceLocation().toString(), mensaje));
        }
        return new ArrayList<>(errores);
    }

    private static String leerEsquema(String id) {
        if (!id.startsWith(BASE_ID)) {
            return null;
        }
        String recurso = RECURSOS + id.substring(BASE_ID.length());
        try (InputStream flujo = ValidadorEsquemas.class.getResourceAsStream(recurso)) {
            if (flujo == null) {
                throw new IllegalStateException(
                        "Falta el esquema " + recurso + "; reconstruya el módulo opsec-nucleo");
            }
            return new String(flujo.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el esquema " + recurso, e);
        }
    }
}
