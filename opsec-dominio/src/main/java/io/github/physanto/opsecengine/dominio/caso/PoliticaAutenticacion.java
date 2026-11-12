package io.github.physanto.opsecengine.dominio.caso;

import java.util.List;
import java.util.Objects;

/**
 * Política de autenticación de un caso: los intentos de inicio de sesión que hace, cuántos fallos
 * seguidos tolera la aplicación antes de proteger la cuenta y qué señales significan rechazo y
 * bloqueo.
 *
 * <p>La usan los oráculos {@code CREDENCIALES-DEBILES} (CWE-521) y {@code SIN-BLOQUEO} (CWE-307).
 * Un inicio de sesión aceptado se reconoce por un evento {@code SESION} con {@code activa = true},
 * no por un paso terminado en {@code OK}. Desde contratos v1.2.
 *
 * <p>Es inmutable y segura entre hilos.
 *
 * @param intentos intentos en el orden en que se hacen, sin pasos que se solapen; no puede ser null
 *     ni estar vacía
 * @param fallosPermitidos fallos seguidos con el mismo usuario a partir de los cuales la aplicación
 *     debería bloquear o retrasar el siguiente intento; mayor que 0
 * @param retardoMinimoMs espera mínima, en milisegundos, que se considera protección suficiente
 *     tras {@code fallosPermitidos} fallos; mayor que 0
 * @param rechazo señales que indican que la aplicación rechazó las credenciales; no puede ser null
 *     ni estar vacía
 * @param bloqueo señales que indican que la aplicación bloqueó la cuenta; no puede ser null ni
 *     estar vacía, y no comparte señales con {@code rechazo}
 */
public record PoliticaAutenticacion(
        List<IntentoLogin> intentos,
        int fallosPermitidos,
        long retardoMinimoMs,
        List<Senal> rechazo,
        List<Senal> bloqueo) {

    /**
     * Crea la política con copias inmutables de sus listas y valida sus datos.
     *
     * @throws NullPointerException si alguna lista es null o contiene elementos null
     * @throws IllegalArgumentException si no hay intentos, si los intentos se solapan, si los
     *     umbrales no son positivos, si alguna lista de señales está vacía o si una señal aparece
     *     en las dos
     */
    public PoliticaAutenticacion {
        intentos = List.copyOf(Objects.requireNonNull(intentos, "Faltan los intentos"));
        rechazo = List.copyOf(Objects.requireNonNull(rechazo, "Faltan las señales"));
        bloqueo = List.copyOf(Objects.requireNonNull(bloqueo, "Faltan las señales"));
        if (intentos.isEmpty()) {
            throw new IllegalArgumentException("La política necesita al menos un intento");
        }
        if (fallosPermitidos < 1 || retardoMinimoMs < 1) {
            throw new IllegalArgumentException(
                    "Los fallos permitidos y el retardo mínimo deben ser positivos");
        }
        if (rechazo.isEmpty() || bloqueo.isEmpty()) {
            throw new IllegalArgumentException(
                    "La política necesita señales de rechazo y de bloqueo");
        }
        for (Senal senal : rechazo) {
            if (bloqueo.contains(senal)) {
                throw new IllegalArgumentException(
                        "Una misma señal no puede indicar rechazo y bloqueo");
            }
        }
        int anterior = 0;
        for (IntentoLogin intento : intentos) {
            if (Math.min(intento.pasoUsuario(), intento.pasoContrasena()) <= anterior) {
                throw new IllegalArgumentException(
                        "Los intentos deben ir en orden y sin pasos que se solapen");
            }
            anterior = intento.pasoEnvio();
        }
    }
}
