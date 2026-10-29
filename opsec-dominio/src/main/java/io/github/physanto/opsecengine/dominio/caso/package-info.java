/**
 * Modelos que describen qué se prueba y cómo se ejecuta: localizadores, pasos, casos, suites y
 * configuración de ejecución.
 *
 * <p>Todos los modelos son inmutables y seguros entre hilos: los {@code record} hacen copias
 * defensivas de sus colecciones y validan sus datos al construirse. Los campos opcionales se
 * documentan como "puede ser null"; las colecciones nunca son null.
 *
 * <p>Las colecciones de tipo {@code Set} y {@code Map} no garantizan un orden de iteración estable;
 * quien necesite una salida reproducible (por ejemplo, al serializar) debe ordenarlas.
 */
package io.github.physanto.opsecengine.dominio.caso;
