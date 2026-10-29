/**
 * Protocolo de comunicación entre el motor y el agente.
 *
 * <p>Define los mensajes que se intercambian como JSON por líneas sobre un socket TCP local ({@code
 * HOLA}, {@code EJECUTAR_PASO}, {@code RESULTADO_PASO}, {@code EVENTO}, {@code CAPTURAR}, {@code
 * CAPTURA}, {@code ARBOL}, {@code CONFIG_GANCHOS} y {@code CERRAR}) y su serialización. Los
 * mensajes llevan número de versión para que el motor y el agente puedan evolucionar por separado.
 *
 * <p>Se compila para Java 8 porque también lo usa el agente, que corre dentro de la aplicación
 * probada.
 */
package io.github.physanto.opsecengine.protocolo;
