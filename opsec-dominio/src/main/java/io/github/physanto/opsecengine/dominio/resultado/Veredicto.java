package io.github.physanto.opsecengine.dominio.resultado;

import java.util.List;
import java.util.Objects;

/**
 * Decisión sobre un caso o una suite, con los hallazgos que la justifican.
 *
 * <p>Reglas: {@link EstadoVeredicto#NO_CUMPLE} exige al menos un hallazgo y {@link
 * EstadoVeredicto#NO_CONCLUYENTE} exige un motivo. Un veredicto {@link EstadoVeredicto#CUMPLE}
 * puede llevar hallazgos informativos.
 *
 * <p>Es inmutable y seguro entre hilos.
 *
 * @param estado estado del veredicto; no puede ser null
 * @param hallazgos hallazgos que justifican el veredicto; si es null queda vacía, y no admite
 *     elementos null
 * @param motivo explicación de por qué no se pudo decidir; obligatoria en {@link
 *     EstadoVeredicto#NO_CONCLUYENTE} y puede ser null en los demás estados
 */
public record Veredicto(EstadoVeredicto estado, List<Hallazgo> hallazgos, String motivo) {

    /**
     * Crea un veredicto y valida sus reglas.
     *
     * @throws NullPointerException si {@code estado} es null o si algún hallazgo es null
     * @throws IllegalArgumentException si el estado es {@code NO_CUMPLE} sin hallazgos, o {@code
     *     NO_CONCLUYENTE} sin motivo o con un motivo en blanco
     */
    public Veredicto {
        Objects.requireNonNull(estado, "El estado del veredicto no puede ser null");
        hallazgos = hallazgos == null ? List.of() : List.copyOf(hallazgos);
        if (estado == EstadoVeredicto.NO_CUMPLE && hallazgos.isEmpty()) {
            throw new IllegalArgumentException(
                    "Un veredicto NO_CUMPLE necesita al menos un hallazgo");
        }
        if (estado == EstadoVeredicto.NO_CONCLUYENTE && (motivo == null || motivo.isBlank())) {
            throw new IllegalArgumentException("Un veredicto NO_CONCLUYENTE necesita un motivo");
        }
    }

    /**
     * Crea un veredicto sin fallos y sin hallazgos.
     *
     * @return veredicto {@link EstadoVeredicto#CUMPLE}
     */
    public static Veredicto cumple() {
        return new Veredicto(EstadoVeredicto.CUMPLE, List.of(), null);
    }

    /**
     * Crea un veredicto con fallos.
     *
     * @param hallazgos hallazgos que justifican el veredicto; al menos uno
     * @return veredicto {@link EstadoVeredicto#NO_CUMPLE}
     * @throws IllegalArgumentException si {@code hallazgos} es null o está vacía
     * @throws NullPointerException si algún hallazgo es null
     */
    public static Veredicto noCumple(List<Hallazgo> hallazgos) {
        return new Veredicto(EstadoVeredicto.NO_CUMPLE, hallazgos, null);
    }

    /**
     * Crea un veredicto para cuando el motor no puede decidir.
     *
     * @param motivo explicación de por qué no se pudo decidir; no puede ser null ni estar en blanco
     * @return veredicto {@link EstadoVeredicto#NO_CONCLUYENTE}
     * @throws IllegalArgumentException si {@code motivo} es null o está en blanco
     */
    public static Veredicto noConcluyente(String motivo) {
        return new Veredicto(EstadoVeredicto.NO_CONCLUYENTE, List.of(), motivo);
    }
}
