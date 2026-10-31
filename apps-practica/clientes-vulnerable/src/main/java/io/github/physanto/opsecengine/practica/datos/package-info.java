/**
 * Persistencia de la app de práctica sobre una base H2 embebida en memoria.
 *
 * <p>La base se crea al iniciar la app con los datos semilla (tablas {@code usuario} y {@code
 * persona}) y desaparece al cerrarla, así que cada ejecución empieza con el mismo estado.
 */
package io.github.physanto.opsecengine.practica.datos;
