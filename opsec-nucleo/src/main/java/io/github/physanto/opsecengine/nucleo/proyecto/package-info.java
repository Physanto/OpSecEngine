/**
 * Carga y validación de los archivos de un proyecto de pruebas: {@code proyecto.yaml} y las suites.
 *
 * <p>{@link io.github.physanto.opsecengine.nucleo.proyecto.ProyectoService} valida cada archivo
 * contra su esquema de {@code contratos/esquemas/} y lo traduce a objetos del dominio. Los errores
 * llegan en {@link io.github.physanto.opsecengine.nucleo.proyecto.ExcepcionConfiguracion}, con la
 * ruta del campo y un mensaje en español.
 */
package io.github.physanto.opsecengine.nucleo.proyecto;
