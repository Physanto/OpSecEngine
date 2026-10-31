package io.github.physanto.opsecengine.nucleo.proyecto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.github.physanto.opsecengine.dominio.caso.Suite;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Carga y valida los archivos de un proyecto de pruebas: {@code proyecto.yaml} y {@code
 * suites/*.yaml}.
 *
 * <p>Cada archivo se lee en UTF-8, se valida contra su esquema de {@code contratos/esquemas/} y se
 * traduce a objetos del dominio. Si algo falla, lanza {@link ExcepcionConfiguracion} con todos los
 * errores, cada uno con la ruta del campo y un mensaje en español. Además del esquema, comprueba lo
 * que cruza datos: que la política y los casos usen roles definidos en el proyecto y que los ids de
 * los casos no se repitan.
 *
 * <p>Traducción:
 *
 * <ul>
 *   <li>Si falta {@code nombre}, el proyecto toma el nombre de su carpeta.
 *   <li>La ruta del JAR se resuelve contra la carpeta de {@code proyecto.yaml}.
 *   <li>Cada paso es un objeto con una sola acción como clave. {@code llenar} y {@code seleccionar}
 *       con varios campos producen un paso por campo, en el orden escrito, y los pasos de un caso
 *       se numeran desde 1 después de expandirlos.
 *   <li>Un localizador de texto simple se busca primero como etiqueta y luego como texto visible;
 *       un localizador objeto da un criterio por clave, en el orden escrito.
 *   <li>{@code esperar} con un número es una espera fija: el paso no tiene objetivo y lleva los
 *       milisegundos en {@link io.github.physanto.opsecengine.dominio.caso.Paso#valor()}.
 *   <li>Los campos que faltan en {@code config} y {@code sensibles} toman sus valores por defecto.
 * </ul>
 *
 * <p>Ejemplo:
 *
 * <pre>{@code
 * ProyectoService servicio = new ProyectoService();
 * Proyecto proyecto = servicio.cargarProyecto(Path.of("pruebas", "proyecto.yaml"));
 * List<Suite> suites = servicio.cargarSuites(Path.of("pruebas", "suites"), proyecto);
 * }</pre>
 *
 * <p>No tiene estado mutable y es seguro entre hilos.
 */
public final class ProyectoService {

    /** Nombre del archivo del proyecto dentro de su carpeta. */
    public static final String ARCHIVO_PROYECTO = "proyecto.yaml";

    /** Carpeta de las suites dentro de la carpeta del proyecto. */
    public static final String CARPETA_SUITES = "suites";

    private static final Pattern CLAVE_REPETIDA = Pattern.compile("Duplicate field '(.*)'");

    private final YAMLMapper mapper =
            YAMLMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    private final ValidadorEsquemas validador = new ValidadorEsquemas();

    /** Crea el servicio. */
    public ProyectoService() {}

    /**
     * Carga y valida un {@code proyecto.yaml}.
     *
     * @param archivo ruta del archivo; no puede ser null
     * @return el proyecto, con la ruta del JAR ya resuelta contra la carpeta del archivo
     * @throws ExcepcionConfiguracion si el archivo no es YAML válido, no cumple el esquema o nombra
     *     roles que no existen
     * @throws IOException si no se puede leer el archivo
     * @throws NullPointerException si {@code archivo} es null
     */
    public Proyecto cargarProyecto(Path archivo) throws IOException, ExcepcionConfiguracion {
        JsonNode raiz = leerValidado(archivo, ValidadorEsquemas.PROYECTO);
        Path carpeta = archivo.toAbsolutePath().normalize().getParent();
        TraductorConfiguracion traductor = new TraductorConfiguracion(mapper);
        Proyecto proyecto = traductor.proyecto(raiz, carpeta);
        exigirSinErrores(archivo, traductor.errores());
        return proyecto;
    }

    /**
     * Carga y valida una suite.
     *
     * @param archivo ruta del archivo de la suite; no puede ser null
     * @param proyecto proyecto al que pertenece, para comprobar los roles; no puede ser null
     * @return la suite
     * @throws ExcepcionConfiguracion si el archivo no es YAML válido, no cumple el esquema, repite
     *     ids de casos o usa roles que no existen en el proyecto
     * @throws IOException si no se puede leer el archivo
     * @throws NullPointerException si algún parámetro es null
     */
    public Suite cargarSuite(Path archivo, Proyecto proyecto)
            throws IOException, ExcepcionConfiguracion {
        Objects.requireNonNull(proyecto, "El proyecto no puede ser null");
        JsonNode raiz = leerValidado(archivo, ValidadorEsquemas.SUITE);
        TraductorConfiguracion traductor = new TraductorConfiguracion(mapper);
        Suite suite = traductor.suite(raiz, proyecto);
        exigirSinErrores(archivo, traductor.errores());
        return suite;
    }

    /**
     * Carga todas las suites ({@code *.yaml} y {@code *.yml}) de una carpeta, ordenadas por nombre
     * de archivo. No entra en subcarpetas.
     *
     * @param carpeta carpeta de las suites; no puede ser null
     * @param proyecto proyecto al que pertenecen; no puede ser null
     * @return las suites; lista vacía si la carpeta no existe o no tiene suites
     * @throws ExcepcionConfiguracion en la primera suite que no sea válida
     * @throws IOException si no se puede leer la carpeta o un archivo
     * @throws NullPointerException si algún parámetro es null
     */
    public List<Suite> cargarSuites(Path carpeta, Proyecto proyecto)
            throws IOException, ExcepcionConfiguracion {
        Objects.requireNonNull(carpeta, "La carpeta de suites no puede ser null");
        Objects.requireNonNull(proyecto, "El proyecto no puede ser null");
        if (!Files.isDirectory(carpeta)) {
            return List.of();
        }
        List<Path> archivos;
        try (Stream<Path> contenido = Files.list(carpeta)) {
            archivos =
                    contenido
                            .filter(Files::isRegularFile)
                            .filter(ProyectoService::esYaml)
                            .sorted()
                            .toList();
        }
        List<Suite> suites = new ArrayList<>();
        for (Path archivo : archivos) {
            suites.add(cargarSuite(archivo, proyecto));
        }
        return suites;
    }

    private JsonNode leerValidado(Path archivo, String esquema)
            throws IOException, ExcepcionConfiguracion {
        Objects.requireNonNull(archivo, "El archivo no puede ser null");
        JsonNode raiz;
        try (Reader lector = Files.newBufferedReader(archivo, StandardCharsets.UTF_8)) {
            raiz = mapper.readTree(lector);
        } catch (StreamReadException e) {
            throw new ExcepcionConfiguracion(archivo, List.of(errorDeLectura(e)));
        } catch (JsonProcessingException e) {
            throw new ExcepcionConfiguracion(
                    archivo, List.of(new ErrorConfiguracion("", e.getOriginalMessage())));
        }
        if (raiz == null || raiz.isMissingNode() || raiz.isNull()) {
            throw new ExcepcionConfiguracion(
                    archivo, List.of(new ErrorConfiguracion("", "el archivo está vacío")));
        }
        exigirSinErrores(archivo, validador.validar(esquema, raiz));
        return raiz;
    }

    private static ErrorConfiguracion errorDeLectura(StreamReadException e) {
        Matcher repetida = CLAVE_REPETIDA.matcher(e.getOriginalMessage());
        String detalle =
                repetida.find()
                        ? "la clave '" + repetida.group(1) + "' está repetida"
                        : "el YAML no es válido (" + e.getOriginalMessage() + ")";
        if (e.getLocation() == null) {
            return new ErrorConfiguracion("", detalle);
        }
        return new ErrorConfiguracion(
                "",
                "línea "
                        + e.getLocation().getLineNr()
                        + ", columna "
                        + e.getLocation().getColumnNr()
                        + ": "
                        + detalle);
    }

    private static void exigirSinErrores(Path archivo, List<ErrorConfiguracion> errores)
            throws ExcepcionConfiguracion {
        if (!errores.isEmpty()) {
            throw new ExcepcionConfiguracion(archivo, errores);
        }
    }

    private static boolean esYaml(Path archivo) {
        String nombre = String.valueOf(archivo.getFileName());
        return nombre.endsWith(".yaml") || nombre.endsWith(".yml");
    }
}
