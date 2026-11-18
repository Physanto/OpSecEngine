/**
 * Ganchos que observan, dentro de la JVM de la aplicación probada, lo que la aplicación hace fuera
 * de la interfaz gráfica, como las sentencias JDBC.
 *
 * <p>Se instalan con ByteBuddy sobre las clases que carga la aplicación. Son de fallo seguro: si un
 * gancho falla, el error se registra y la aplicación sigue funcionando como si no estuviera. Se
 * compila para Java 8, como el resto del agente.
 */
package io.github.physanto.opsecengine.agente.ganchos;
