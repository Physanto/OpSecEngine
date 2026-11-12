package io.github.physanto.opsecengine.dominio.caso;

import java.util.Objects;

/**
 * Par de intentos de inicio de sesión que se comparan para detectar enumeración de usuarios: uno
 * con un usuario que no existe y otro con un usuario que existe y una contraseña incorrecta.
 *
 * <p>La usa el oráculo {@code ENUMERACION-USUARIOS} (CWE-204): si las respuestas de los dos
 * intentos son distintas, un atacante puede saber qué usuarios existen. Quien escribe la suite
 * garantiza que los dos casos usan el mismo formulario. Los números de paso son los de {@code
 * ContextoEvento.numeroPaso()}, empezando en 1. Desde contratos v1.2.
 *
 * <p>Es inmutable y segura entre hilos.
 *
 * @param casoUsuarioInexistente id del caso que intenta entrar con un usuario que no existe; no
 *     puede ser null ni estar en blanco
 * @param pasoUsuarioInexistente número del paso {@code PULSAR} que envía ese intento; mayor que 0
 * @param casoContrasenaIncorrecta id del caso que intenta entrar con un usuario existente y una
 *     contraseña incorrecta; no puede ser null, estar en blanco ni ser igual al anterior
 * @param pasoContrasenaIncorrecta número del paso {@code PULSAR} que envía ese intento; mayor que 0
 */
public record ComparacionLogin(
        String casoUsuarioInexistente,
        int pasoUsuarioInexistente,
        String casoContrasenaIncorrecta,
        int pasoContrasenaIncorrecta) {

    /**
     * Crea la comparación y valida sus datos.
     *
     * @throws NullPointerException si alguno de los ids de caso es null
     * @throws IllegalArgumentException si un id está en blanco, si los dos ids son iguales o si un
     *     paso no es positivo
     */
    public ComparacionLogin {
        Objects.requireNonNull(casoUsuarioInexistente, "Falta el caso de usuario inexistente");
        Objects.requireNonNull(casoContrasenaIncorrecta, "Falta el caso de contraseña incorrecta");
        if (casoUsuarioInexistente.isBlank()
                || casoContrasenaIncorrecta.isBlank()
                || casoUsuarioInexistente.equals(casoContrasenaIncorrecta)) {
            throw new IllegalArgumentException("La comparación necesita dos casos distintos");
        }
        if (pasoUsuarioInexistente < 1 || pasoContrasenaIncorrecta < 1) {
            throw new IllegalArgumentException("Los pasos de la comparación deben ser positivos");
        }
    }
}
