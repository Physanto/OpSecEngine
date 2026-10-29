/**
 * Modelos de la configuración de un proyecto de pruebas: la aplicación probada, sus roles, sus
 * datos de prueba, las operaciones sensibles y la política de roles.
 *
 * <p>Corresponden al archivo {@code proyecto.yaml}. Todos los modelos son inmutables y seguros
 * entre hilos: los {@code record} hacen copias defensivas de sus colecciones y validan sus datos al
 * construirse. Los campos opcionales se documentan como "puede ser null"; las colecciones nunca son
 * null.
 *
 * <p>Las colecciones de tipo {@code Set} y {@code Map} no garantizan un orden de iteración estable;
 * quien necesite una salida reproducible (por ejemplo, al serializar) debe ordenarlas.
 */
package io.github.physanto.opsecengine.dominio.configuracion;
