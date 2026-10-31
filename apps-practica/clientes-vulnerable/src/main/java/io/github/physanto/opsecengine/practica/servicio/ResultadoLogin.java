package io.github.physanto.opsecengine.practica.servicio;

import java.util.Objects;
import java.util.Optional;

/**
 * Resultado de un intento de inicio de sesión: la sesión si tuvo éxito o el mensaje para el usuario
 * si no.
 *
 * <p>Es inmutable y seguro entre hilos.
 */
public final class ResultadoLogin {

    private final Sesion sesion;
    private final String mensaje;

    private ResultadoLogin(Sesion sesion, String mensaje) {
        this.sesion = sesion;
        this.mensaje = mensaje;
    }

    /**
     * Crea un resultado exitoso.
     *
     * @param sesion sesión iniciada
     * @return resultado con la sesión y sin mensaje
     * @throws NullPointerException si la sesión es {@code null}
     */
    public static ResultadoLogin exito(Sesion sesion) {
        return new ResultadoLogin(Objects.requireNonNull(sesion, "sesion"), null);
    }

    /**
     * Crea un resultado fallido.
     *
     * @param mensaje texto que la ventana muestra al usuario
     * @return resultado sin sesión y con el mensaje
     * @throws NullPointerException si el mensaje es {@code null}
     */
    public static ResultadoLogin fallo(String mensaje) {
        return new ResultadoLogin(null, Objects.requireNonNull(mensaje, "mensaje"));
    }

    /**
     * Indica si el inicio de sesión tuvo éxito.
     *
     * @return {@code true} si hay sesión
     */
    public boolean esExitoso() {
        return sesion != null;
    }

    /**
     * Devuelve la sesión iniciada.
     *
     * @return la sesión, o vacío si el intento falló
     */
    public Optional<Sesion> sesion() {
        return Optional.ofNullable(sesion);
    }

    /**
     * Devuelve el mensaje para el usuario.
     *
     * @return el mensaje, o vacío si el intento tuvo éxito
     */
    public Optional<String> mensaje() {
        return Optional.ofNullable(mensaje);
    }
}
