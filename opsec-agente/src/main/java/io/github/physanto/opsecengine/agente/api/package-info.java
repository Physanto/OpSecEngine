/**
 * API interna del agente: las interfaces que separan el servidor del protocolo de las piezas que
 * actúan sobre la aplicación probada.
 *
 * <p><b>Este paquete es un contrato administrado por el líder.</b> Sus interfaces las implementa el
 * dueño del agente, pero solo el líder las modifica, mediante una solicitud de cambio de contrato.
 *
 * <p>Contiene {@link io.github.physanto.opsecengine.agente.api.AdaptadorToolkit} (recorre la
 * interfaz y ejecuta acciones; hoy Swing, mañana JavaFX), {@link
 * io.github.physanto.opsecengine.agente.api.Gancho} (instrumenta clases de la aplicación) y {@link
 * io.github.physanto.opsecengine.agente.api.EmisorEventos} (envía al motor lo observado). Usa los
 * DTO de {@code opsec-protocolo} y nunca depende del núcleo ni del dominio.
 *
 * <p>Se compila para Java 8.
 */
package io.github.physanto.opsecengine.agente.api;
