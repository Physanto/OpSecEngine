/**
 * Pruebas de arquitectura y de contratos del motor.
 *
 * <p>Verifica con ArchUnit que se cumplan las reglas de dependencia entre módulos descritas en la
 * arquitectura; por ejemplo, que el dominio no dependa de ningún otro módulo y que el agente solo
 * dependa del protocolo. También valida la carpeta {@code contratos/}: los esquemas JSON, los
 * ejemplos contra sus esquemas y los catálogos contra el dominio y el protocolo. El módulo no
 * contiene clases principales.
 */
package io.github.physanto.opsecengine.verificacion;
