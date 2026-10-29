package io.github.physanto.opsecengine.dominio.resultado;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Resultado de ejecutar una suite completa: resultados por caso, hallazgos de suite y manifiesto.
 *
 * <p>Es inmutable y seguro entre hilos.
 *
 * @param nombreSuite nombre de la suite; no puede ser null ni estar en blanco
 * @param manifiesto datos para repetir la ejecución; no puede ser null
 * @param casos resultados de los casos en el orden en que se ejecutaron, con ids únicos; si es null
 *     queda vacía, y no admite elementos null
 * @param hallazgosSuite hallazgos de los oráculos de suite, que comparan varios casos; si es null
 *     queda vacía, y no admite elementos null
 * @param inicio momento en que empezó la ejecución; no puede ser null
 * @param fin momento en que terminó la ejecución; no puede ser null ni anterior a {@code inicio}
 */
public record ResultadoSuite(
        String nombreSuite,
        Manifiesto manifiesto,
        List<ResultadoCaso> casos,
        List<Hallazgo> hallazgosSuite,
        Instant inicio,
        Instant fin) {

    /**
     * Crea el resultado de una suite y valida sus datos.
     *
     * @throws NullPointerException si {@code nombreSuite}, {@code manifiesto}, {@code inicio} o
     *     {@code fin} son null, o si alguna lista contiene elementos null
     * @throws IllegalArgumentException si {@code nombreSuite} está en blanco, si {@code fin} es
     *     anterior a {@code inicio} o si hay dos resultados con el mismo id de caso
     */
    public ResultadoSuite {
        Objects.requireNonNull(nombreSuite, "El nombre de la suite no puede ser null");
        if (nombreSuite.isBlank()) {
            throw new IllegalArgumentException("El nombre de la suite no puede estar en blanco");
        }
        Objects.requireNonNull(manifiesto, "El manifiesto de la suite no puede ser null");
        casos = casos == null ? List.of() : List.copyOf(casos);
        hallazgosSuite = hallazgosSuite == null ? List.of() : List.copyOf(hallazgosSuite);
        Objects.requireNonNull(inicio, "El inicio de la ejecución no puede ser null");
        Objects.requireNonNull(fin, "El fin de la ejecución no puede ser null");
        if (fin.isBefore(inicio)) {
            throw new IllegalArgumentException(
                    "El fin de la ejecución (" + fin + ") es anterior al inicio (" + inicio + ")");
        }
        Set<String> ids = new HashSet<>();
        for (ResultadoCaso caso : casos) {
            if (!ids.add(caso.casoId())) {
                throw new IllegalArgumentException(
                        "La suite "
                                + nombreSuite
                                + " tiene resultados repetidos del caso "
                                + caso.casoId());
            }
        }
    }

    /**
     * Cuenta los casos según el estado de su veredicto.
     *
     * @return mapa inmutable con todos los estados en el orden de {@link EstadoVeredicto},
     *     incluidos los que tienen cero casos
     */
    public Map<EstadoVeredicto, Integer> resumen() {
        Map<EstadoVeredicto, Integer> conteo = new EnumMap<>(EstadoVeredicto.class);
        for (EstadoVeredicto estado : EstadoVeredicto.values()) {
            conteo.put(estado, 0);
        }
        for (ResultadoCaso caso : casos) {
            conteo.merge(caso.veredicto().estado(), 1, Integer::sum);
        }
        return Collections.unmodifiableMap(conteo);
    }
}
