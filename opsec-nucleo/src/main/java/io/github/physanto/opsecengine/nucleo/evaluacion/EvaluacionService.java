package io.github.physanto.opsecengine.nucleo.evaluacion;

import io.github.physanto.opsecengine.dominio.extension.Contexto;
import io.github.physanto.opsecengine.dominio.extension.EvaluacionCaso;
import io.github.physanto.opsecengine.dominio.extension.Evaluador;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.resultado.Veredicto;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import io.github.physanto.opsecengine.nucleo.extensiones.RegistroExtensiones;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Fachada del proveedor de evaluación descubierto mediante ServiceLoader.
 *
 * <p>Delega la evaluación por evento, caso y suite en el contrato {@link Evaluador}, sin importar
 * clases de implementación. {@link #iniciarCaso} permite evaluar mientras se ejecutan los pasos;
 * {@link #evaluarCaso} conserva la API para trazas ya recogidas y las reproduce en orden. La
 * selección de oráculos, los hallazgos y los motivos corresponden al proveedor.
 *
 * <p>La carpeta opcional de reglas se recibe explícitamente, sin deducirla del JAR o del directorio
 * de trabajo. Los errores de configuración se informan antes de ejecutar casos. Es seguro entre
 * hilos si el evaluador lo es; cada sesión de caso pertenece a un solo hilo.
 *
 * <p>{@link #evaluarResultadoSuite} conserva el veredicto completo. {@link #evaluarSuite} mantiene
 * la API anterior que devuelve solo hallazgos; no transmite los motivos de suite. El modelo actual
 * de resultado de suite tampoco dispone de un campo para esos motivos.
 */
public final class EvaluacionService {
    private final Evaluador evaluador;

    /**
     * Descubre el proveedor de evaluación sin cargar reglas de archivos.
     *
     * @param registro registro de extensiones; no puede ser null
     * @throws NullPointerException si el registro es null
     * @throws IllegalStateException si no hay exactamente un proveedor válido
     * @throws UncheckedIOException si el proveedor falla con un error de lectura
     */
    public EvaluacionService(RegistroExtensiones registro) {
        Objects.requireNonNull(registro, "El registro no puede ser null");
        try {
            this.evaluador = registro.crearEvaluador(null);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo crear el evaluador", e);
        }
    }

    /**
     * Descubre el proveedor y carga las reglas de una carpeta explícita.
     *
     * @param registro registro de extensiones; no puede ser null
     * @param carpetaReglas carpeta de reglas YAML; null para no cargar archivos
     * @throws NullPointerException si el registro es null
     * @throws IllegalStateException si no hay exactamente un proveedor válido
     * @throws IllegalArgumentException si hay reglas inválidas o ids duplicados
     * @throws IOException si la carpeta indicada o sus archivos no pueden leerse
     */
    public EvaluacionService(RegistroExtensiones registro, Path carpetaReglas) throws IOException {
        this.evaluador =
                Objects.requireNonNull(registro, "El registro no puede ser null")
                        .crearEvaluador(carpetaReglas);
    }

    /**
     * Crea la fachada con un evaluador proporcionado, por ejemplo un doble de prueba.
     *
     * @param evaluador implementación del contrato de dominio; no puede ser null
     * @throws NullPointerException si el evaluador es null
     */
    public EvaluacionService(Evaluador evaluador) {
        this.evaluador = Objects.requireNonNull(evaluador, "El evaluador no puede ser null");
    }

    /**
     * Inicia una sesión independiente de evaluación para un caso.
     *
     * @param contexto contexto con caso; no puede ser null ni tener caso null
     * @return sesión sin eventos; nunca null
     * @throws NullPointerException si el contexto o su caso son null
     */
    public EvaluacionCaso iniciarCaso(Contexto contexto) {
        Objects.requireNonNull(contexto, "El contexto no puede ser null");
        Objects.requireNonNull(contexto.caso(), "El contexto no tiene caso");
        return Objects.requireNonNull(evaluador.iniciarCaso(contexto), "La sesión es null");
    }

    /**
     * Evalúa una traza recogida reproduciendo sus eventos y cerrando la sesión del caso.
     *
     * @param traza traza del caso, completa o no; no puede ser null ni ser de otro caso
     * @param contexto contexto del caso; no puede ser null ni tener caso null
     * @param motivoIncompleto por qué no terminó la ejecución; null si terminó
     * @return veredicto completo del proveedor
     * @throws NullPointerException si la traza, el contexto o su caso son null
     * @throws IllegalArgumentException si el id de la traza no coincide o el motivo está en blanco
     */
    public Veredicto evaluarCaso(Traza traza, Contexto contexto, String motivoIncompleto) {
        Objects.requireNonNull(traza, "La traza no puede ser null");
        Objects.requireNonNull(contexto, "El contexto no puede ser null");
        Objects.requireNonNull(contexto.caso(), "El contexto no tiene caso");
        if (!traza.casoId().equals(contexto.caso().id())) {
            throw new IllegalArgumentException("La traza pertenece a otro caso");
        }
        EvaluacionCaso sesion = iniciarCaso(contexto);
        traza.eventos().forEach(sesion::registrarEvento);
        return sesion.terminar(motivoIncompleto);
    }

    /**
     * Evalúa los oráculos de suite y conserva su veredicto completo.
     *
     * @param contexto contexto sin caso y con las trazas de la suite; no puede ser null
     * @return veredicto completo, incluidos motivos no concluyentes
     * @throws NullPointerException si el contexto es null
     * @throws IllegalArgumentException si el contexto tiene un caso
     */
    public Veredicto evaluarResultadoSuite(Contexto contexto) {
        Objects.requireNonNull(contexto, "El contexto no puede ser null");
        if (contexto.caso() != null) {
            throw new IllegalArgumentException(
                    "La evaluación de suite requiere un contexto sin caso");
        }
        return evaluador.evaluarSuite(contexto);
    }

    /**
     * Devuelve los hallazgos de suite mediante la API anterior.
     *
     * @param contexto contexto sin caso y con las trazas de la suite; no puede ser null
     * @return hallazgos de suite, sin transmitir sus motivos; para el veredicto completo use {@link
     *     #evaluarResultadoSuite}
     * @throws NullPointerException si el contexto es null
     * @throws IllegalArgumentException si el contexto tiene un caso
     */
    public List<Hallazgo> evaluarSuite(Contexto contexto) {
        return evaluarResultadoSuite(contexto).hallazgos();
    }
}
