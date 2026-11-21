package io.github.physanto.opsecengine.nucleo.reproduccion;

import io.github.physanto.opsecengine.dominio.caso.Suite;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.dominio.resultado.EstadoVeredicto;
import io.github.physanto.opsecengine.dominio.resultado.Manifiesto;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoCaso;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoSuite;
import io.github.physanto.opsecengine.nucleo.VersionMotor;
import io.github.physanto.opsecengine.nucleo.ejecucion.EjecucionService;
import io.github.physanto.opsecengine.nucleo.ejecucion.Huellas;
import io.github.physanto.opsecengine.nucleo.generacion.SuiteYaml;
import io.github.physanto.opsecengine.nucleo.proyecto.ExcepcionConfiguracion;
import io.github.physanto.opsecengine.nucleo.proyecto.ProyectoService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Guarda lo necesario para repetir una ejecución y la repite exactamente.
 *
 * <p>{@link #registrar} deja en la carpeta de resultados el {@code manifiesto.json} (hash del JAR,
 * versiones, sistema operativo, semilla, hash de la suite y fecha) y la copia {@code suite.yaml} de
 * la suite ejecutada, con su semilla. {@link #reejecutar} carga ambos, comprueba que la aplicación
 * y la suite sigan siendo las mismas y ejecuta de nuevo con la misma semilla; las diferencias que
 * no impiden la repetición (versiones de Java, del motor o del sistema) se devuelven como avisos.
 *
 * <p>Una re-ejecución es exacta si coinciden el hash del JAR, el de la suite y la semilla. La
 * semilla fija los valores generados por el motor, como los canarios.
 *
 * <p>Ejemplo:
 *
 * <pre>{@code
 * ReejecucionService reejecucion = new ReejecucionService();
 * reejecucion.registrar(resultado, suite, carpetaResultados); // ExcepcionReejecucion si no cabe en YAML
 * ResultadoReejecucion otra = reejecucion.reejecutar(carpetaResultados, proyecto, ejecucion, false);
 * List<String> distintos = ReejecucionService.casosConVeredictoDistinto(resultado, otra.resultado());
 * }</pre>
 *
 * <p>No tiene estado; es seguro entre hilos si lo es el {@link EjecucionService} que se le pase.
 */
public final class ReejecucionService {

    /** Nombre del manifiesto dentro de la carpeta de resultados. */
    public static final String ARCHIVO_MANIFIESTO = "manifiesto.json";

    /** Nombre de la copia de la suite dentro de la carpeta de resultados. */
    public static final String ARCHIVO_SUITE = "suite.yaml";

    private final ProyectoService proyectos;

    /** Crea el servicio con su propio {@link ProyectoService}. */
    public ReejecucionService() {
        this(new ProyectoService());
    }

    /**
     * Crea el servicio.
     *
     * @param proyectos servicio con el que se carga la suite guardada; no puede ser null
     * @throws NullPointerException si {@code proyectos} es null
     */
    public ReejecucionService(ProyectoService proyectos) {
        this.proyectos = Objects.requireNonNull(proyectos, "El servicio de proyectos es null");
    }

    /**
     * Guarda el manifiesto del resultado y la suite ejecutada en una carpeta.
     *
     * @param resultado resultado de la ejecución; no puede ser null
     * @param suite suite que se ejecutó; no puede ser null
     * @param carpeta carpeta de resultados; se crea si no existe; no puede ser null
     * @throws IOException si no se puede escribir
     * @throws ExcepcionReejecucion si la suite tiene pasos que el formato YAML no puede expresar
     *     (por ejemplo, llenar un campo localizado por su nombre de componente): la ejecución no se
     *     podría repetir, así que no se escribe nada y el mensaje enumera esos pasos
     * @throws NullPointerException si algún parámetro es null
     */
    public void registrar(ResultadoSuite resultado, Suite suite, Path carpeta)
            throws IOException, ExcepcionReejecucion {
        Objects.requireNonNull(resultado, "El resultado no puede ser null");
        Objects.requireNonNull(suite, "La suite no puede ser null");
        Objects.requireNonNull(carpeta, "La carpeta no puede ser null");
        List<String> problemas = SuiteYaml.noExpresables(suite);
        if (!problemas.isEmpty()) {
            throw new ExcepcionReejecucion(
                    "No se puede guardar la suite para repetir la ejecución; no se escribió nada. "
                            + String.join("; ", problemas));
        }
        Files.createDirectories(carpeta);
        SuiteYaml.guardar(suite, carpeta.resolve(ARCHIVO_SUITE));
        ManifiestoJson.escribir(resultado.manifiesto(), carpeta.resolve(ARCHIVO_MANIFIESTO));
    }

    /**
     * Carga el manifiesto y la suite guardados en una carpeta de resultados.
     *
     * @param carpeta carpeta de resultados; no puede ser null
     * @param proyecto proyecto con el que se valida la suite (sus roles); no puede ser null
     * @return el manifiesto y la suite
     * @throws ExcepcionReejecucion si falta alguno de los dos archivos o no son válidos
     * @throws NullPointerException si algún parámetro es null
     */
    public Reproduccion cargar(Path carpeta, Proyecto proyecto) throws ExcepcionReejecucion {
        Objects.requireNonNull(carpeta, "La carpeta no puede ser null");
        Objects.requireNonNull(proyecto, "El proyecto no puede ser null");
        Path archivoManifiesto = carpeta.resolve(ARCHIVO_MANIFIESTO);
        Path archivoSuite = carpeta.resolve(ARCHIVO_SUITE);
        for (Path archivo : List.of(archivoManifiesto, archivoSuite)) {
            if (!Files.isRegularFile(archivo)) {
                throw new ExcepcionReejecucion(
                        "No se puede repetir la ejecución: falta " + archivo);
            }
        }
        try {
            return new Reproduccion(
                    ManifiestoJson.leer(archivoManifiesto),
                    proyectos.cargarSuite(archivoSuite, proyecto));
        } catch (IOException | ExcepcionConfiguracion e) {
            throw new ExcepcionReejecucion(
                    "No se puede repetir la ejecución de " + carpeta + ": " + e.getMessage(), e);
        }
    }

    /**
     * Compara un manifiesto guardado con el de las condiciones actuales.
     *
     * @param guardado manifiesto de la ejecución original; no puede ser null
     * @param actual manifiesto de las condiciones actuales; no puede ser null
     * @return las diferencias, vacía si todo coincide; el hash del JAR, el de la suite y la semilla
     *     son bloqueantes
     * @throws NullPointerException si algún parámetro es null
     */
    public static List<Discrepancia> comparar(Manifiesto guardado, Manifiesto actual) {
        Objects.requireNonNull(guardado, "El manifiesto guardado no puede ser null");
        Objects.requireNonNull(actual, "El manifiesto actual no puede ser null");
        List<Discrepancia> lista = new ArrayList<>();
        agregar(lista, "hashJar", guardado.hashJar(), actual.hashJar(), true);
        agregar(lista, "hashSuite", guardado.hashSuite(), actual.hashSuite(), true);
        agregar(
                lista,
                "semilla",
                String.valueOf(guardado.semilla()),
                String.valueOf(actual.semilla()),
                true);
        agregar(lista, "versionMotor", guardado.versionMotor(), actual.versionMotor(), false);
        agregar(lista, "versionJava", guardado.versionJava(), actual.versionJava(), false);
        agregar(
                lista,
                "sistemaOperativo",
                guardado.sistemaOperativo(),
                actual.sistemaOperativo(),
                false);
        return lista;
    }

    /**
     * Repite la ejecución guardada en una carpeta de resultados.
     *
     * <p>Antes de ejecutar compara el manifiesto guardado con el JAR y la suite de ahora. Si cambió
     * la aplicación, la suite o la semilla, no ejecuta, salvo que {@code permitirCambios} sea
     * {@code true}; en ese caso lo avisa en las discrepancias del resultado.
     *
     * @param carpeta carpeta de resultados de la ejecución original; no puede ser null
     * @param proyecto proyecto con el JAR actual; debe ser el que usa {@code ejecucion}; no puede
     *     ser null
     * @param ejecucion servicio con el que se ejecuta; no puede ser null
     * @param permitirCambios si se ejecuta aunque la aplicación, la suite o la semilla hayan
     *     cambiado
     * @return el nuevo resultado y las diferencias encontradas
     * @throws ExcepcionReejecucion si faltan los archivos guardados o hay diferencias bloqueantes y
     *     {@code permitirCambios} es {@code false}
     * @throws NullPointerException si algún parámetro es null
     */
    public ResultadoReejecucion reejecutar(
            Path carpeta, Proyecto proyecto, EjecucionService ejecucion, boolean permitirCambios)
            throws ExcepcionReejecucion {
        Objects.requireNonNull(ejecucion, "El servicio de ejecución no puede ser null");
        Reproduccion guardada = cargar(carpeta, proyecto);
        Manifiesto actual = condicionesActuales(guardada, proyecto);
        List<Discrepancia> discrepancias = comparar(guardada.manifiesto(), actual);
        List<String> bloqueantes =
                discrepancias.stream()
                        .filter(Discrepancia::bloqueante)
                        .map(Discrepancia::campo)
                        .toList();
        if (!bloqueantes.isEmpty() && !permitirCambios) {
            throw new ExcepcionReejecucion(
                    "No se puede repetir exactamente la ejecución: cambió "
                            + String.join(", ", bloqueantes));
        }
        return new ResultadoReejecucion(ejecucion.ejecutar(guardada.suite()), discrepancias);
    }

    /**
     * Ids de los casos cuyo veredicto difiere entre dos resultados, o que están en uno solo.
     *
     * @param original resultado de referencia; no puede ser null
     * @param repetido resultado de la re-ejecución; no puede ser null
     * @return los ids, en el orden de {@code original} y luego los que solo estén en {@code
     *     repetido}; vacía si ambos dan el mismo veredicto en todos los casos
     * @throws NullPointerException si algún parámetro es null
     */
    public static List<String> casosConVeredictoDistinto(
            ResultadoSuite original, ResultadoSuite repetido) {
        Objects.requireNonNull(original, "El resultado original no puede ser null");
        Objects.requireNonNull(repetido, "El resultado repetido no puede ser null");
        Map<String, EstadoVeredicto> nuevos = new HashMap<>();
        for (ResultadoCaso caso : repetido.casos()) {
            nuevos.put(caso.casoId(), caso.veredicto().estado());
        }
        List<String> distintos = new ArrayList<>();
        for (ResultadoCaso caso : original.casos()) {
            if (nuevos.remove(caso.casoId()) != caso.veredicto().estado()) {
                distintos.add(caso.casoId());
            }
        }
        for (ResultadoCaso caso : repetido.casos()) {
            if (nuevos.containsKey(caso.casoId())) {
                distintos.add(caso.casoId());
            }
        }
        return distintos;
    }

    private static Manifiesto condicionesActuales(Reproduccion guardada, Proyecto proyecto) {
        return new Manifiesto(
                Huellas.jar(proyecto.jar()),
                VersionMotor.motor(),
                VersionMotor.java(),
                VersionMotor.sistemaOperativo(),
                guardada.suite().config().semilla(),
                Huellas.suite(guardada.suite()),
                guardada.manifiesto().fecha());
    }

    private static void agregar(
            List<Discrepancia> lista,
            String campo,
            String guardado,
            String actual,
            boolean bloqueante) {
        if (!guardado.equals(actual)) {
            lista.add(new Discrepancia(campo, guardado, actual, bloqueante));
        }
    }
}
