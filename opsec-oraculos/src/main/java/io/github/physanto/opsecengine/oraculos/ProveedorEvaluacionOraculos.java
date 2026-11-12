package io.github.physanto.opsecengine.oraculos;

import io.github.physanto.opsecengine.dominio.extension.Evaluador;
import io.github.physanto.opsecengine.dominio.extension.Oraculo;
import io.github.physanto.opsecengine.dominio.extension.ProveedorEvaluacion;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Proveedor de evaluación descubierto con ServiceLoader mediante el contrato de dominio.
 *
 * <p>No tiene estado y es seguro entre hilos. Cada llamada valida los oráculos registrados, carga
 * las reglas opcionales y crea un evaluador independiente, sin devolver resultados parciales.
 */
public final class ProveedorEvaluacionOraculos implements ProveedorEvaluacion {

    /** Crea el proveedor para ServiceLoader; no carga archivos ni descubre otros servicios. */
    public ProveedorEvaluacionOraculos() {}

    /**
     * Crea el evaluador y agrega las reglas YAML después de los oráculos recibidos.
     *
     * @param oraculos oráculos registrados, sin null ni ids duplicados
     * @param carpetaReglas carpeta de reglas; null para no cargar archivos
     * @return evaluador independiente, seguro entre hilos si los oráculos lo son
     * @throws NullPointerException si la colección, algún oráculo, id o momento es null
     * @throws IllegalArgumentException si hay ids duplicados o reglas inválidas
     * @throws IOException si la carpeta indicada o sus archivos no pueden leerse
     */
    @Override
    public Evaluador crear(Collection<? extends Oraculo> oraculos, Path carpetaReglas)
            throws IOException {
        List<Oraculo> todos = new ArrayList<>(List.copyOf(oraculos));
        if (carpetaReglas != null) {
            todos.addAll(new CargadorReglas().cargarCarpeta(carpetaReglas));
        }
        return new EvaluadorOraculos(todos);
    }
}
