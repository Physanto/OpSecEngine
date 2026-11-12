package io.github.physanto.opsecengine.oraculos;

import java.util.Objects;

/**
 * Adaptador local que identifica dos intentos fallidos de login comparables. El llamador garantiza
 * que se usa el mismo formulario y que los datos corresponden a los escenarios declarados. Es
 * inmutable y seguro entre hilos; no deduce la existencia de cuentas desde sus nombres.
 *
 * @param casoUsuarioInexistente id del caso que prueba una cuenta inexistente
 * @param pasoUsuarioInexistente paso PULSAR que envía ese intento
 * @param casoContrasenaIncorrecta id del caso con cuenta existente y contraseña incorrecta
 * @param pasoContrasenaIncorrecta paso PULSAR que envía ese intento
 */
public record ComparacionEnumeracionUsuarios(
        String casoUsuarioInexistente,
        int pasoUsuarioInexistente,
        String casoContrasenaIncorrecta,
        int pasoContrasenaIncorrecta) {
    /**
     * Valida que se identifican dos casos distintos con pasos positivos.
     *
     * @throws NullPointerException si algún id es null
     * @throws IllegalArgumentException si un id está vacío, los casos coinciden o un paso no es
     *     positivo
     */
    public ComparacionEnumeracionUsuarios {
        Objects.requireNonNull(casoUsuarioInexistente, "Falta el caso de usuario inexistente");
        Objects.requireNonNull(casoContrasenaIncorrecta, "Falta el caso de contraseña incorrecta");
        if (casoUsuarioInexistente.isBlank()
                || casoContrasenaIncorrecta.isBlank()
                || casoUsuarioInexistente.equals(casoContrasenaIncorrecta)
                || pasoUsuarioInexistente < 1
                || pasoContrasenaIncorrecta < 1) {
            throw new IllegalArgumentException(
                    "Se requieren dos casos distintos y pasos positivos");
        }
    }
}
