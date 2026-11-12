package io.github.physanto.opsecengine.informes;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.physanto.opsecengine.dominio.extension.Reportero;
import io.github.physanto.opsecengine.dominio.resultado.Confianza;
import io.github.physanto.opsecengine.dominio.resultado.EstadoVeredicto;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoCaso;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoSuite;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reportero del formato {@value #FORMATO}: escribe {@value #ARCHIVO}, el resultado de la suite en
 * SARIF 2.1.0, el formato estándar de resultados de análisis que entienden GitHub code scanning,
 * Visual Studio Code y otras herramientas.
 *
 * <p>Correspondencia con el resultado de OpSecEngine:
 *
 * <ul>
 *   <li>Cada oráculo que produjo algún hallazgo es una <em>regla</em> ({@code tool.driver.rules})
 *       con su identificador, el título de su primer hallazgo y, si lo tiene, su CWE como etiqueta
 *       {@code external/cwe/cwe-<n>}.
 *   <li>Cada hallazgo es un <em>resultado</em> con su regla, un {@code level} según la confianza
 *       ({@code CONFIRMADO} es {@code error}, {@code PROBABLE} es {@code warning} e {@code INFO} es
 *       {@code note}), la explicación como mensaje y una huella estable para que el sistema
 *       receptor reconozca el mismo hallazgo entre ejecuciones.
 *   <li>La <em>ubicación</em> de un hallazgo de caso es la suite que lo contiene (la ruta {@code
 *       suites/<suite>.yaml}, por convención del proyecto) y, como ubicación lógica, el caso. Un
 *       hallazgo de suite tiene solo la suite.
 *   <li>Un caso {@code NO_CONCLUYENTE} no es un resultado: se informa como notificación de
 *       ejecución de la herramienta, con su motivo.
 * </ul>
 *
 * <p>Una aplicación de escritorio no tiene archivos de código que señalar, así que la ubicación
 * física es la suite de pruebas; no apunta a una línea del código de la aplicación.
 *
 * <p>El texto se escribe en UTF-8. Se registra en {@code
 * META-INF/services/io.github.physanto.opsecengine.dominio.extension.Reportero}. No tiene estado
 * mutable y es seguro entre hilos.
 */
public final class ReporteroSarif implements Reportero {

    /** Formato que produce. */
    public static final String FORMATO = "sarif";

    /** Archivo que escribe en la carpeta de resultados. */
    public static final String ARCHIVO = "resultado.sarif";

    /** Versión de SARIF que se escribe. */
    public static final String VERSION_SARIF = "2.1.0";

    /** Dirección del esquema JSON de SARIF 2.1.0. */
    public static final String ESQUEMA =
            "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/main/sarif-2.1/schema/"
                    + "sarif-schema-2.1.0.json";

    private static final String HUELLA = "opsecengine/v1";

    private final JsonMapper mapper =
            JsonMapper.builder()
                    .enable(SerializationFeature.INDENT_OUTPUT)
                    .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                    .build();

    /** Crea el reportero; lo usa {@link java.util.ServiceLoader}. */
    public ReporteroSarif() {}

    /**
     * Devuelve el formato del reportero.
     *
     * @return {@value #FORMATO}
     */
    @Override
    public String formato() {
        return FORMATO;
    }

    /**
     * Escribe {@value #ARCHIVO} en la carpeta de destino; lo reemplaza si ya existe.
     *
     * @param resultado resultado de la suite; no puede ser null
     * @param destino carpeta de resultados, que ya existe; no puede ser null
     * @throws IOException si no puede escribir el archivo
     */
    @Override
    public void escribir(ResultadoSuite resultado, Path destino) throws IOException {
        Objects.requireNonNull(resultado, "El resultado no puede ser null");
        Objects.requireNonNull(destino, "La carpeta de destino no puede ser null");
        try (Writer escritor =
                Files.newBufferedWriter(destino.resolve(ARCHIVO), StandardCharsets.UTF_8)) {
            mapper.writeValue(escritor, documento(resultado));
        }
    }

    private static Map<String, Object> documento(ResultadoSuite resultado) {
        List<String> reglas = new ArrayList<>();
        List<Map<String, Object>> resultados = new ArrayList<>();
        for (ResultadoCaso caso : resultado.casos()) {
            for (Hallazgo hallazgo : caso.veredicto().hallazgos()) {
                resultados.add(resultado(resultado, caso, hallazgo, reglas));
            }
        }
        for (Hallazgo hallazgo : resultado.hallazgosSuite()) {
            resultados.add(resultado(resultado, null, hallazgo, reglas));
        }

        Map<String, Object> herramienta = new LinkedHashMap<>();
        herramienta.put("name", "OpSecEngine");
        herramienta.put("version", resultado.manifiesto().versionMotor());
        herramienta.put("rules", definiciones(resultado, reglas));

        Map<String, Object> ejecucion = new LinkedHashMap<>();
        ejecucion.put("tool", Map.of("driver", herramienta));
        ejecucion.put("invocations", List.of(invocacion(resultado)));
        ejecucion.put("results", resultados);
        ejecucion.put(
                "properties",
                orden(
                        "suite", resultado.nombreSuite(),
                        "hashJar", resultado.manifiesto().hashJar(),
                        "hashSuite", resultado.manifiesto().hashSuite(),
                        "semilla", resultado.manifiesto().semilla()));

        Map<String, Object> raiz = new LinkedHashMap<>();
        raiz.put("$schema", ESQUEMA);
        raiz.put("version", VERSION_SARIF);
        raiz.put("runs", List.of(ejecucion));
        return raiz;
    }

    /** Arma el resultado de un hallazgo y registra su regla si es la primera vez que aparece. */
    private static Map<String, Object> resultado(
            ResultadoSuite suite, ResultadoCaso caso, Hallazgo hallazgo, List<String> reglas) {
        if (!reglas.contains(hallazgo.oraculoId())) {
            reglas.add(hallazgo.oraculoId());
        }
        String casoId = caso == null ? null : caso.casoId();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ruleId", hallazgo.oraculoId());
        r.put("ruleIndex", reglas.indexOf(hallazgo.oraculoId()));
        r.put("level", nivel(hallazgo.confianza()));
        r.put("message", Map.of("text", hallazgo.titulo() + ". " + hallazgo.explicacion()));
        r.put("locations", List.of(ubicacion(suite.nombreSuite(), casoId)));
        r.put(
                "partialFingerprints",
                Map.of(
                        HUELLA,
                        huella(
                                hallazgo.oraculoId(),
                                suite.nombreSuite(),
                                casoId,
                                hallazgo.titulo())));
        Map<String, Object> propiedades = new LinkedHashMap<>();
        propiedades.put("confianza", hallazgo.confianza().name());
        if (hallazgo.cwe() != null) {
            propiedades.put("cwe", hallazgo.cwe());
        }
        if (casoId != null) {
            propiedades.put("caso", casoId);
            propiedades.put("inestable", caso.inestable());
        }
        if (!hallazgo.evidencia().isEmpty()) {
            propiedades.put("eventosDeEvidencia", hallazgo.evidencia());
        }
        r.put("properties", propiedades);
        return r;
    }

    private static Map<String, Object> ubicacion(String suite, String casoId) {
        Map<String, Object> fisica = new LinkedHashMap<>();
        fisica.put("artifactLocation", Map.of("uri", "suites/" + suite + ".yaml"));
        fisica.put("region", Map.of("startLine", 1));
        Map<String, Object> ubicacion = new LinkedHashMap<>();
        ubicacion.put("physicalLocation", fisica);
        Map<String, Object> logica = new LinkedHashMap<>();
        logica.put("name", casoId == null ? suite : casoId);
        logica.put("fullyQualifiedName", casoId == null ? suite : suite + "/" + casoId);
        logica.put("kind", casoId == null ? "module" : "member");
        ubicacion.put("logicalLocations", List.of(logica));
        return ubicacion;
    }

    /** Define una regla por oráculo, con el título y el CWE de su primer hallazgo. */
    private static List<Map<String, Object>> definiciones(
            ResultadoSuite resultado, List<String> reglas) {
        List<Map<String, Object>> definiciones = new ArrayList<>();
        for (String id : reglas) {
            Hallazgo primero = primerHallazgo(resultado, id);
            Set<String> etiquetas = new LinkedHashSet<>();
            etiquetas.add("security");
            for (Hallazgo h : todosLosHallazgos(resultado)) {
                if (h.oraculoId().equals(id) && h.cwe() != null) {
                    etiquetas.add("external/cwe/" + h.cwe().toLowerCase(Locale.ROOT));
                }
            }
            Map<String, Object> regla = new LinkedHashMap<>();
            regla.put("id", id);
            regla.put("name", id);
            regla.put("shortDescription", Map.of("text", primero.titulo()));
            regla.put("fullDescription", Map.of("text", primero.explicacion()));
            CatalogoCwe.oficial()
                    .buscar(primero.cwe())
                    .ifPresent(d -> {
                        regla.put("helpUri", d.enlace());
                        regla.put("help", ayuda(d));
                    });
            regla.put("properties", Map.of("tags", List.copyOf(etiquetas)));
            definiciones.add(regla);
        }
        return definiciones;
    }

    /**
     * Arma la ayuda de una regla con la explicación del catálogo oficial de CWE, en texto y en
     * Markdown (GitHub code scanning muestra la versión Markdown).
     */
    private static Map<String, Object> ayuda(CatalogoCwe.DescripcionCwe d) {
        Map<String, Object> ayuda = new LinkedHashMap<>();
        ayuda.put(
                "text",
                d.id()
                        + " ("
                        + d.nombre()
                        + "). "
                        + d.explicacion()
                        + " Cómo corregirlo: "
                        + d.recomendacion());
        ayuda.put(
                "markdown",
                "**"
                        + d.id()
                        + ": "
                        + d.nombre()
                        + "**\n\n"
                        + d.explicacion()
                        + "\n\n**Cómo corregirlo:** "
                        + d.recomendacion()
                        + "\n\n["
                        + d.id()
                        + " en MITRE]("
                        + d.enlace()
                        + ")");
        return ayuda;
    }

    private static Map<String, Object> invocacion(ResultadoSuite resultado) {
        List<Map<String, Object>> notificaciones = new ArrayList<>();
        for (ResultadoCaso caso : resultado.casos()) {
            if (caso.veredicto().estado() == EstadoVeredicto.NO_CONCLUYENTE) {
                Map<String, Object> aviso = new LinkedHashMap<>();
                aviso.put("level", "warning");
                aviso.put(
                        "message",
                        Map.of(
                                "text",
                                "El caso "
                                        + caso.casoId()
                                        + " no fue concluyente: "
                                        + caso.veredicto().motivo()));
                notificaciones.add(aviso);
            }
        }
        Map<String, Object> invocacion = new LinkedHashMap<>();
        invocacion.put("executionSuccessful", true);
        invocacion.put("startTimeUtc", resultado.inicio().toString());
        invocacion.put("endTimeUtc", resultado.fin().toString());
        if (!notificaciones.isEmpty()) {
            invocacion.put("toolExecutionNotifications", notificaciones);
        }
        return invocacion;
    }

    private static Hallazgo primerHallazgo(ResultadoSuite resultado, String oraculoId) {
        return todosLosHallazgos(resultado).stream()
                .filter(h -> h.oraculoId().equals(oraculoId))
                .findFirst()
                .orElseThrow();
    }

    private static List<Hallazgo> todosLosHallazgos(ResultadoSuite resultado) {
        List<Hallazgo> todos = new ArrayList<>();
        for (ResultadoCaso caso : resultado.casos()) {
            todos.addAll(caso.veredicto().hallazgos());
        }
        todos.addAll(resultado.hallazgosSuite());
        return todos;
    }

    private static String nivel(Confianza confianza) {
        return switch (confianza) {
            case CONFIRMADO -> "error";
            case PROBABLE -> "warning";
            case INFO -> "note";
        };
    }

    /** Huella SHA-256 estable de un hallazgo: no depende del orden ni de la fecha. */
    private static String huella(String oraculo, String suite, String caso, String titulo) {
        try {
            MessageDigest resumen = MessageDigest.getInstance("SHA-256");
            String texto = String.join("\u001f", oraculo, suite, caso == null ? "" : caso, titulo);
            return HexFormat.of().formatHex(resumen.digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("La JVM no trae SHA-256", e);
        }
    }

    /** Crea un mapa que conserva el orden en que se pasan los pares clave-valor. */
    private static Map<String, Object> orden(Object... paresClaveValor) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        for (int i = 0; i < paresClaveValor.length; i += 2) {
            mapa.put((String) paresClaveValor[i], paresClaveValor[i + 1]);
        }
        return mapa;
    }
}
