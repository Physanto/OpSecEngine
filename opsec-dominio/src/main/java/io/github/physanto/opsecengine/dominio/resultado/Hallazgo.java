package io.github.physanto.opsecengine.dominio.resultado;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Fallo u observación que un oráculo encontró en una traza.
 *
 * <p>Es inmutable y seguro entre hilos.
 *
 * @param oraculoId id del oráculo que produjo el hallazgo, por ejemplo {@code "SIN-SESION"}; no
 *     puede ser null ni estar en blanco
 * @param cwe identificador CWE con el formato {@code "CWE-<número>"}, por ejemplo {@code
 *     "CWE-209"}; puede ser null si el hallazgo no corresponde a una debilidad CWE
 * @param confianza grado de certeza; no puede ser null
 * @param titulo resumen de una línea; no puede ser null ni estar en blanco
 * @param explicacion explicación en lenguaje natural de qué se encontró y por qué es un problema;
 *     no puede ser null
 * @param evidencia secuencias de los eventos de la traza que respaldan el hallazgo; si es null
 *     queda vacía, y no admite elementos null
 */
public record Hallazgo(
        String oraculoId,
        String cwe,
        Confianza confianza,
        String titulo,
        String explicacion,
        List<Long> evidencia) {

    private static final Pattern FORMATO_CWE = Pattern.compile("CWE-[0-9]+");

    /**
     * Crea un hallazgo y valida sus datos.
     *
     * @throws NullPointerException si {@code oraculoId}, {@code confianza}, {@code titulo} o {@code
     *     explicacion} son null, o si {@code evidencia} contiene elementos null
     * @throws IllegalArgumentException si {@code oraculoId} o {@code titulo} están en blanco, o si
     *     {@code cwe} no es null y no tiene el formato {@code CWE-<número>}
     */
    public Hallazgo {
        Objects.requireNonNull(oraculoId, "El id del oráculo del hallazgo no puede ser null");
        if (oraculoId.isBlank()) {
            throw new IllegalArgumentException(
                    "El id del oráculo del hallazgo no puede estar en blanco");
        }
        if (cwe != null && !FORMATO_CWE.matcher(cwe).matches()) {
            throw new IllegalArgumentException("El CWE debe tener el formato CWE-<número>: " + cwe);
        }
        Objects.requireNonNull(confianza, "La confianza del hallazgo no puede ser null");
        Objects.requireNonNull(titulo, "El título del hallazgo no puede ser null");
        if (titulo.isBlank()) {
            throw new IllegalArgumentException("El título del hallazgo no puede estar en blanco");
        }
        Objects.requireNonNull(explicacion, "La explicación del hallazgo no puede ser null");
        evidencia = evidencia == null ? List.of() : List.copyOf(evidencia);
    }
}
