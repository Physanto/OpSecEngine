/**
 * Objetos de transferencia (DTO) que forman el cuerpo de los mensajes del protocolo.
 *
 * <p>Son independientes del dominio del motor: el núcleo traduce entre estos DTO y los modelos del
 * dominio, y el agente los usa sin conocer el dominio. Los nombres de acciones, estrategias, modos
 * y tipos de evento viajan como texto con los mismos valores que los enums del dominio, para que un
 * valor nuevo no rompa a un lector antiguo.
 *
 * <p>Todas las clases son inmutables y seguras entre hilos. Las listas nunca son null: si llegan
 * null quedan vacías.
 */
package io.github.physanto.opsecengine.protocolo.dto;
