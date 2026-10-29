/**
 * Protocolo de comunicación entre el motor y el agente.
 *
 * <p>Los mensajes viajan como JSON por líneas (JSON Lines) sobre un socket TCP en {@code
 * 127.0.0.1}: un {@link io.github.physanto.opsecengine.protocolo.Mensaje} por línea, en UTF-8 y
 * terminado en {@code "\n"} en todas las plataformas. Cada mensaje es un sobre con versión, tipo,
 * id, correlación y un cuerpo cuyo formato depende del tipo (ver {@link
 * io.github.physanto.opsecengine.protocolo.TipoMensaje}). Los cuerpos se describen con las clases
 * del paquete {@code dto}, y hay un ejemplo por tipo en {@code contratos/protocolo/ejemplos/}.
 *
 * <p>Para que el motor y el agente puedan evolucionar por separado, los mensajes llevan número de
 * versión y los lectores ignoran los campos desconocidos.
 *
 * <p>Se compila para Java 8 porque también lo usa el agente, que corre dentro de la aplicación
 * probada. No depende de ningún otro módulo del proyecto.
 */
package io.github.physanto.opsecengine.protocolo;
