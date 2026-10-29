/**
 * Agente que se carga con {@code -javaagent} dentro de la JVM de la aplicación probada.
 *
 * <p>Ejecuta las acciones que pide el motor sobre la interfaz Swing, observa ventanas, diálogos,
 * excepciones y consola, instala ganchos de instrumentación y toma capturas, y reporta resultados y
 * eventos por el protocolo.
 *
 * <p>Se compila para Java 8 y reubica sus dependencias para no chocar con las librerías de la
 * aplicación probada. Solo depende de {@code opsec-protocolo}; nunca del núcleo ni del dominio.
 */
package io.github.physanto.opsecengine.agente;
