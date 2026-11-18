package io.github.physanto.opsecengine.nucleo.sesion;

import io.github.physanto.opsecengine.dominio.caso.ConfigEjecucion;
import io.github.physanto.opsecengine.dominio.caso.Paso;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.dominio.configuracion.Rol;
import io.github.physanto.opsecengine.protocolo.dto.CodigoResultado;
import io.github.physanto.opsecengine.protocolo.dto.ResultadoPasoDto;
import java.io.IOException;
import java.util.Objects;

/**
 * Inicia sesión en la aplicación abierta con los pasos de {@code login} del proyecto y las
 * credenciales de un rol. Lo usan los servicios que necesitan un rol activo sin ejecutar un caso,
 * como la verificación y la exploración.
 *
 * <p>Los pasos de login reemplazan {@code {usuario}} y {@code {contrasena}}. No tiene estado; es
 * seguro entre hilos.
 */
public final class InicioSesion {

    /** Variable de los pasos de login que se reemplaza por el usuario del rol. */
    public static final String VARIABLE_USUARIO = "{usuario}";

    /** Variable de los pasos de login que se reemplaza por la contraseña del rol. */
    public static final String VARIABLE_CONTRASENA = "{contrasena}";

    private InicioSesion() {}

    /**
     * Ejecuta los pasos de login del proyecto con las credenciales del rol.
     *
     * @param proyecto proyecto con los pasos de login; no puede ser null
     * @param rol rol con el que se inicia sesión; no puede ser null
     * @param cliente conexión con el agente de la aplicación abierta; no puede ser null
     * @param config configuración que fija el modo y la espera de los pasos; no puede ser null
     * @return null si todos los pasos terminaron con {@code OK}; si no, el motivo en español, sin
     *     credenciales
     * @throws IOException si falla la comunicación con el agente
     * @throws NullPointerException si algún parámetro es null
     */
    public static String iniciar(
            Proyecto proyecto, Rol rol, ClienteAgente cliente, ConfigEjecucion config)
            throws IOException {
        Objects.requireNonNull(proyecto, "El proyecto no puede ser null");
        Objects.requireNonNull(rol, "El rol no puede ser null");
        Objects.requireNonNull(cliente, "El cliente no puede ser null");
        Objects.requireNonNull(config, "La configuración no puede ser null");
        if (proyecto.pasosLogin().isEmpty()) {
            return "el proyecto no declara pasos de inicio de sesión";
        }
        for (Paso plantilla : proyecto.pasosLogin()) {
            Paso paso =
                    new Paso(
                            plantilla.orden(),
                            plantilla.accion(),
                            plantilla.objetivo(),
                            reemplazar(plantilla.valor(), rol),
                            plantilla.esperaMaxima(),
                            plantilla.extensiones());
            ResultadoPasoDto resultado =
                    cliente.enviarPaso(TraductorProtocolo.aPasoDto(paso, config));
            if (resultado.getCodigo() != CodigoResultado.OK) {
                return "falló el paso "
                        + plantilla.orden()
                        + " del inicio de sesión con el rol "
                        + rol.nombre()
                        + ": "
                        + resultado.getCodigo();
            }
        }
        return null;
    }

    private static String reemplazar(String valor, Rol rol) {
        return valor == null
                ? null
                : valor.replace(VARIABLE_USUARIO, rol.usuario())
                        .replace(VARIABLE_CONTRASENA, rol.contrasena());
    }
}
