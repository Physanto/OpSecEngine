package io.github.physanto.opsecengine.dominio.resultado;

import io.github.physanto.opsecengine.dominio.traza.Traza;
import java.time.Duration;
import java.util.Objects;

/**
 * Resultado de ejecutar y evaluar un caso.
 *
 * <p>Es inmutable y seguro entre hilos.
 *
 * @param casoId id del caso; no puede ser null ni estar en blanco
 * @param veredicto veredicto del caso; no puede ser null
 * @param traza traza del último intento, con el mismo id de caso; no puede ser null (si el caso no
 *     llegó a ejecutarse, la traza está vacía)
 * @param duracion tiempo total de ejecución, incluidos los reintentos; no puede ser null ni
 *     negativa
 * @param intentos veces que se ejecutó el caso; uno o más
 * @param inestable {@code true} si el resultado cambió entre intentos; solo puede valer {@code
 *     true} con más de un intento
 */
public record ResultadoCaso(
        String casoId,
        Veredicto veredicto,
        Traza traza,
        Duration duracion,
        int intentos,
        boolean inestable) {

    /**
     * Crea el resultado de un caso y valida sus datos.
     *
     * @throws NullPointerException si {@code casoId}, {@code veredicto}, {@code traza} o {@code
     *     duracion} son null
     * @throws IllegalArgumentException si {@code casoId} está en blanco o no coincide con el de la
     *     traza, si {@code duracion} es negativa, si {@code intentos} es menor que 1 o si se marca
     *     como inestable con un solo intento
     */
    public ResultadoCaso {
        Objects.requireNonNull(casoId, "El id del caso del resultado no puede ser null");
        if (casoId.isBlank()) {
            throw new IllegalArgumentException(
                    "El id del caso del resultado no puede estar en blanco");
        }
        Objects.requireNonNull(veredicto, "El veredicto del caso " + casoId + " no puede ser null");
        Objects.requireNonNull(traza, "La traza del caso " + casoId + " no puede ser null");
        if (!traza.casoId().equals(casoId)) {
            throw new IllegalArgumentException(
                    "La traza pertenece al caso " + traza.casoId() + " y no al caso " + casoId);
        }
        Objects.requireNonNull(duracion, "La duración del caso " + casoId + " no puede ser null");
        if (duracion.isNegative()) {
            throw new IllegalArgumentException(
                    "La duración del caso " + casoId + " no puede ser negativa: " + duracion);
        }
        if (intentos < 1) {
            throw new IllegalArgumentException(
                    "Los intentos del caso " + casoId + " deben ser 1 o más: " + intentos);
        }
        if (inestable && intentos == 1) {
            throw new IllegalArgumentException(
                    "El caso " + casoId + " no puede ser inestable con un solo intento");
        }
    }
}
