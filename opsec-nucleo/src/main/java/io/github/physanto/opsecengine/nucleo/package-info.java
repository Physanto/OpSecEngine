/**
 * Servicios de aplicación del motor.
 *
 * <p>Orquesta los casos de uso: cargar proyectos y suites, lanzar la aplicación con el agente,
 * explorar, generar casos, ejecutarlos, evaluar oráculos y producir informes. Es la única
 * puerta de entrada de las presentaciones (CLI, escritorio, JUnit, MCP) al motor.
 *
 * <p>También contiene la infraestructura propia del motor: lectura y escritura YAML/JSON,
 * lanzador de procesos y cliente del agente. Descubre oráculos, generadores y reporteros con
 * {@link java.util.ServiceLoader}. No conoce Swing ni la interfaz gráfica del motor.
 */
package io.github.physanto.opsecengine.nucleo;
