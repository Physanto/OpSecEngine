package io.github.physanto.opsecengine.oraculos;

import io.github.physanto.opsecengine.oraculos.PoliticaValidacionTemporal.Senal;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Adaptador local de payloads y controles de autenticación. El llamador garantiza que los intentos
 * pertenecen al mismo formulario y ámbito de bloqueo, sin reinicios que borren su contador. Es
 * inmutable y seguro entre hilos. No clasifica contraseñas mediante heurísticas.
 *
 * @param intentos payloads materializados en orden de envío, sin pasos de envío repetidos
 * @param fallosPermitidos número positivo de fallos consecutivos antes de exigir protección
 * @param retardoMinimo tiempo positivo que se considera protección desde el último rechazo
 * @param rechazo señales explícitas de rechazo de credenciales, al menos una
 * @param bloqueo señales explícitas de bloqueo, al menos una
 */
public record PoliticaAutenticacion(
        List<Intento> intentos,
        int fallosPermitidos,
        Duration retardoMinimo,
        List<Senal> rechazo,
        List<Senal> bloqueo) {
    /**
     * Copia las colecciones y valida umbrales, señales y orden de los intentos.
     *
     * @throws NullPointerException si falta una colección, duración o algún elemento
     * @throws IllegalArgumentException si los umbrales, señales o pasos son incoherentes
     */
    public PoliticaAutenticacion {
        intentos = List.copyOf(intentos);
        rechazo = List.copyOf(rechazo);
        bloqueo = List.copyOf(bloqueo);
        Objects.requireNonNull(retardoMinimo, "Falta el umbral de retardo");
        if (fallosPermitidos < 1
                || retardoMinimo.isZero()
                || retardoMinimo.isNegative()
                || rechazo.isEmpty()
                || bloqueo.isEmpty()
                || rechazo.stream().anyMatch(bloqueo::contains)) {
            throw new IllegalArgumentException(
                    "Se requieren umbrales positivos y señales distintas");
        }
        int anterior = 0;
        for (Intento intento : intentos) {
            if (intento.pasoUsuario() <= anterior || intento.pasoContrasena() <= anterior) {
                throw new IllegalArgumentException(
                        "Los intentos deben tener pasos ordenados y separados");
            }
            anterior = intento.pasoEnvio();
        }
    }

    /**
     * Payload de credenciales cuyo envío se observará en la traza.
     *
     * @param pasoUsuario paso LLENAR del usuario
     * @param pasoContrasena paso LLENAR de la contraseña, distinto del anterior
     * @param pasoEnvio paso PULSAR posterior a ambas escrituras
     * @param usuario valor materializado, incluido el vacío
     * @param contrasena valor materializado, incluido el vacío
     * @param debil true si la biblioteca o política declara débiles estas credenciales
     */
    public record Intento(
            int pasoUsuario,
            int pasoContrasena,
            int pasoEnvio,
            String usuario,
            String contrasena,
            boolean debil) {
        /**
         * Valida los pasos y los valores del payload, sin inferir su fortaleza.
         *
         * @throws NullPointerException si falta un valor
         * @throws IllegalArgumentException si los pasos no identifican dos escrituras y un envío
         *     posterior
         */
        public Intento {
            Objects.requireNonNull(usuario, "Falta el valor de usuario");
            Objects.requireNonNull(contrasena, "Falta el valor de contraseña");
            if (pasoUsuario < 1
                    || pasoContrasena < 1
                    || pasoUsuario == pasoContrasena
                    || pasoEnvio <= Math.max(pasoUsuario, pasoContrasena)) {
                throw new IllegalArgumentException(
                        "Se requieren dos escrituras distintas anteriores al envío");
            }
        }
    }
}
