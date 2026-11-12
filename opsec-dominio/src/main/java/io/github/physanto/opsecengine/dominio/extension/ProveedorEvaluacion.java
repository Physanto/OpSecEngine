package io.github.physanto.opsecengine.dominio.extension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;

/**
 * Crea evaluadores de oráculos sin exponer las clases de su implementación al núcleo.
 *
 * <p>Contrato aditivo de la versión 1.3. La implementación se descubre con {@link
 * java.util.ServiceLoader} mediante el archivo {@code
 * META-INF/services/io.github.physanto.opsecengine.dominio.extension.ProveedorEvaluacion}; requiere
 * un constructor público sin parámetros. El consumidor debe exigir un único proveedor: la ausencia
 * o ambigüedad se informa como error de configuración, sin elegir por orden del classpath.
 *
 * <p>Las implementaciones deben ser sin estado o seguras entre hilos. Cada llamada crea un
 * evaluador independiente y no modifica los oráculos ni la colección recibida.
 */
public interface ProveedorEvaluacion {

    /**
     * Crea un evaluador con los oráculos registrados y las reglas opcionales del proyecto.
     *
     * <p>Con carpeta, carga los archivos inmediatos {@code *.yaml} en orden de nombre, en UTF-8,
     * valida las reglas y agrega sus oráculos después de los registrados. Una carpeta vacía no
     * agrega reglas; una carpeta indicada pero ausente o ilegible produce un error. No se infiere
     * la ruta a partir del JAR de la aplicación ni del directorio de trabajo.
     *
     * <p>La colección se copia al crear el evaluador. Se rechazan ids duplicados en el conjunto
     * completo, incluso entre archivos YAML y oráculos registrados. Los fallos de configuración se
     * informan antes de ejecutar casos, sin devolver un evaluador parcial.
     *
     * @param oraculos oráculos registrados, en orden; no puede ser null ni contener null; sus ids y
     *     momentos tampoco pueden ser null
     * @param carpetaReglas carpeta del proyecto con reglas YAML; null para no cargar reglas
     * @return evaluador independiente que conserva el orden de los oráculos; nunca null
     * @throws NullPointerException si la colección, algún oráculo, id o momento es null
     * @throws IllegalArgumentException si hay ids duplicados o alguna regla no cumple los contratos
     * @throws IOException si la carpeta indicada no puede listarse o algún archivo no puede leerse
     */
    Evaluador crear(Collection<? extends Oraculo> oraculos, Path carpetaReglas) throws IOException;
}
