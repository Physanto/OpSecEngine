/**
 * Modelos de lo que se observa durante una ejecución: eventos, su contexto y la traza ordenada de
 * un caso.
 *
 * <p>La traza separa observar de juzgar: el agente y el núcleo producen eventos, y los oráculos los
 * leen para decidir el veredicto. Los campos de {@code datos} de cada tipo de evento se documentan
 * en el catálogo {@code contratos/catalogos/eventos.yaml}.
 *
 * <p>Todos los modelos son inmutables y seguros entre hilos.
 */
package io.github.physanto.opsecengine.dominio.traza;
