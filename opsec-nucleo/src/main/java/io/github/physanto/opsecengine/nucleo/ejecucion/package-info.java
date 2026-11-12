/**
 * Ejecución de suites: envío de los pasos al agente y armado de las trazas.
 *
 * <p>{@link io.github.physanto.opsecengine.nucleo.ejecucion.EjecucionService} recorre los casos,
 * abre una sesión por caso, registra los eventos del motor y del agente, y entrega cada traza al
 * servicio de evaluación. El observador de progreso permite a las presentaciones recibir el inicio
 * y fin de suite, caso y paso sin acceder a los detalles de la ejecución.
 */
package io.github.physanto.opsecengine.nucleo.ejecucion;
