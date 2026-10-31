/**
 * Modelos del mapa de la aplicación probada: ventanas (nodos), transiciones (aristas) y objetivos
 * de prueba.
 *
 * <p>El explorador construye el mapa por contexto (sin sesión, cada rol y cada punto de entrada) y
 * lo guarda en {@code mapa.json}. Los generadores de casos y los oráculos diferenciales lo usan
 * para saber qué ventanas alcanza cada contexto.
 *
 * <p>Todos los modelos son inmutables y seguros entre hilos. Las colecciones de tipo {@code Set} no
 * garantizan un orden de iteración estable, salvo que su método diga lo contrario.
 */
package io.github.physanto.opsecengine.dominio.mapa;
