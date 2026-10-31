/**
 * Sesiones con la aplicación probada: lanzamiento del proceso con el agente, cliente del protocolo
 * y conversión entre el dominio y los DTO del protocolo.
 *
 * <p>{@link io.github.physanto.opsecengine.nucleo.sesion.SesionService} combina {@link
 * io.github.physanto.opsecengine.nucleo.sesion.LanzadorProceso} y {@link
 * io.github.physanto.opsecengine.nucleo.sesion.ClienteAgente}: arranca la aplicación con {@code
 * -javaagent}, espera el saludo del agente en {@code 127.0.0.1} y la detiene al cerrar.
 */
package io.github.physanto.opsecengine.nucleo.sesion;
