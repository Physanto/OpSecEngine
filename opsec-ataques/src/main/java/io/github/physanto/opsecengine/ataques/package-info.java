/**
 * Biblioteca de payloads de ataque y su cargador.
 *
 * <p>Los payloads se describen en archivos YAML agrupados por categoría, con el formato de {@code
 * contratos/esquemas/payload.schema.json}, y {@link
 * io.github.physanto.opsecengine.ataques.CargadorPayloads} los valida y los convierte en objetos
 * {@link io.github.physanto.opsecengine.ataques.Payload} para que los generadores de casos los
 * usen.
 *
 * <p>Este módulo solo depende del dominio, de Jackson y del validador de esquemas JSON.
 */
package io.github.physanto.opsecengine.ataques;
