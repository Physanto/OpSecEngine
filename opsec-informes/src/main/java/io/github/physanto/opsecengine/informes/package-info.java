/**
 * Reporteros de resultados.
 *
 * <p>Cada reportero implementa la interfaz de extensión del dominio y escribe un formato de
 * informe: HTML con capturas, JSON, JUnit XML y SARIF. El núcleo los descubre con {@link
 * java.util.ServiceLoader}.
 *
 * <p>Este módulo solo depende del dominio y de Jackson.
 */
package io.github.physanto.opsecengine.informes;
