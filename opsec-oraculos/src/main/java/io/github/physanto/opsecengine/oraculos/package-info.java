/**
 * Oráculos incluidos en el motor.
 *
 * <p>Cada oráculo implementa la interfaz de extensión del dominio y decide hallazgos a partir de la
 * traza de un evento, un caso o una suite. El núcleo los descubre con {@link
 * java.util.ServiceLoader}.
 *
 * <p>Este módulo solo depende del dominio.
 */
package io.github.physanto.opsecengine.oraculos;
