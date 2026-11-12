package io.github.physanto.opsecengine.dominio.caso;

import java.util.Objects;

/**
 * Un intento de inicio de sesión dentro de un caso: los pasos que escriben el usuario y la
 * contraseña, el paso que los envía y los valores escritos.
 *
 * <p>Forma parte de {@link PoliticaAutenticacion}. Los números de paso son los de {@code
 * ContextoEvento.numeroPaso()}, empezando en 1. Desde contratos v1.2.
 *
 * <p>Es inmutable y seguro entre hilos.
 *
 * @param pasoUsuario número del paso {@code LLENAR} que escribe el usuario; mayor que 0
 * @param pasoContrasena número del paso {@code LLENAR} que escribe la contraseña; mayor que 0 y
 *     distinto de {@code pasoUsuario}
 * @param pasoEnvio número del paso {@code PULSAR} que envía el formulario; posterior a los dos
 *     anteriores
 * @param usuario usuario escrito; no puede ser null
 * @param contrasena contraseña escrita; no puede ser null
 * @param debil {@code true} si la contraseña se declara débil, por ejemplo porque viene de la
 *     categoría {@code CREDENCIALES_DEBILES} de la biblioteca de ataques
 */
public record IntentoLogin(
        int pasoUsuario,
        int pasoContrasena,
        int pasoEnvio,
        String usuario,
        String contrasena,
        boolean debil) {

    /**
     * Crea el intento y valida sus datos.
     *
     * @throws NullPointerException si {@code usuario} o {@code contrasena} es null
     * @throws IllegalArgumentException si los pasos no son positivos, si el usuario y la contraseña
     *     se escriben en el mismo paso o si el envío no va después de los dos
     */
    public IntentoLogin {
        Objects.requireNonNull(usuario, "El usuario del intento no puede ser null");
        Objects.requireNonNull(contrasena, "La contraseña del intento no puede ser null");
        if (pasoUsuario < 1
                || pasoContrasena < 1
                || pasoUsuario == pasoContrasena
                || pasoEnvio <= Math.max(pasoUsuario, pasoContrasena)) {
            throw new IllegalArgumentException(
                    "El intento necesita dos escrituras distintas anteriores al envío");
        }
    }
}
