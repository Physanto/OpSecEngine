/**
 * Interfaces de extensión del motor: oráculos, generadores de casos, reporteros, proveedores de
 * datos y proveedores de IA, junto con los modelos que reciben.
 *
 * <p>Las implementaciones se registran con {@link java.util.ServiceLoader}. Cada una necesita un
 * constructor público sin parámetros y se declara en un archivo {@code META-INF/services/} cuyo
 * nombre es el nombre completo de la interfaz y cuyo contenido es una línea por implementación con
 * el nombre completo de la clase. Por ejemplo, el archivo {@code
 * META-INF/services/io.github.physanto.opsecengine.dominio.extension.Oraculo} puede contener:
 *
 * <pre>{@code
 * io.github.physanto.opsecengine.oraculos.OraculoSinSesion
 * io.github.physanto.opsecengine.oraculos.OraculoExcepcion
 * }</pre>
 *
 * <p>La evaluación por evento, caso y suite se abstrae con {@link
 * io.github.physanto.opsecengine.dominio.extension.ProveedorEvaluacion}, {@link
 * io.github.physanto.opsecengine.dominio.extension.Evaluador} y {@link
 * io.github.physanto.opsecengine.dominio.extension.EvaluacionCaso}. Solo el proveedor se registra
 * con ServiceLoader; el evaluador y las sesiones se crean a través de sus contratos.
 *
 * <p>Un plugin es un JAR con sus implementaciones y sus archivos {@code META-INF/services/} que se
 * copia en la carpeta {@code plugins/}.
 */
package io.github.physanto.opsecengine.dominio.extension;
