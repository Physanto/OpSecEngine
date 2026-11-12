package io.github.physanto.opsecengine.informes;

import io.github.physanto.opsecengine.dominio.resultado.EstadoVeredicto;
import io.github.physanto.opsecengine.dominio.resultado.Hallazgo;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoCaso;
import io.github.physanto.opsecengine.dominio.resultado.ResultadoSuite;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Compara dos ejecuciones de una suite para ver qué se arregló, qué empeoró y qué cambió.
 *
 * <p>Los casos se emparejan por su id. Un caso <em>mejoró</em> si su veredicto es menos grave que
 * antes y <em>empeoró</em> si es más grave, con este orden de gravedad: {@code CUMPLE}, {@code
 * NO_CONCLUYENTE}, {@code NO_CUMPLE}. Un caso con el mismo veredicto es <em>igual</em>, aunque
 * puede tener hallazgos nuevos o resueltos. Un hallazgo se identifica por su oráculo y su título,
 * no por su explicación ni por los eventos de evidencia, que cambian entre ejecuciones.
 *
 * <p>La comparación no dice si las dos ejecuciones son comparables (misma aplicación, misma suite);
 * eso lo muestra {@link ComparacionHtml} con el manifiesto de cada una.
 *
 * <p>No tiene estado y no se instancia; sus métodos son seguros entre hilos.
 */
public final class ComparadorResultados {

    private ComparadorResultados() {}

    /** Qué le pasó a un caso entre las dos ejecuciones. */
    public enum Cambio {
        /** El veredicto es más grave que antes. */
        EMPEORO,
        /** El caso no estaba en la ejecución anterior. */
        NUEVO,
        /** El veredicto es menos grave que antes. */
        MEJORO,
        /** El caso ya no está en la ejecución actual. */
        ELIMINADO,
        /** El veredicto es el mismo. */
        IGUAL
    }

    /**
     * Cambios de un caso.
     *
     * @param casoId id del caso; no es null
     * @param cambio qué le pasó; no es null
     * @param anterior veredicto de la ejecución anterior; null si el caso es {@code NUEVO}
     * @param actual veredicto de la ejecución actual; null si el caso fue {@code ELIMINADO}
     * @param resueltos hallazgos que estaban antes y ya no están; no es null
     * @param nuevos hallazgos que antes no estaban; no es null
     */
    public record CambioCaso(
            String casoId,
            Cambio cambio,
            EstadoVeredicto anterior,
            EstadoVeredicto actual,
            List<Hallazgo> resueltos,
            List<Hallazgo> nuevos) {

        /** Crea los cambios de un caso con copias inmutables de las listas. */
        public CambioCaso {
            Objects.requireNonNull(casoId, "El id del caso no puede ser null");
            Objects.requireNonNull(cambio, "El cambio no puede ser null");
            resueltos = List.copyOf(resueltos);
            nuevos = List.copyOf(nuevos);
        }
    }

    /**
     * Resultado de comparar dos ejecuciones.
     *
     * @param anterior ejecución anterior; no es null
     * @param actual ejecución actual; no es null
     * @param casos cambios de cada caso, primero los de la ejecución actual en su orden y después
     *     los eliminados; no es null
     * @param resueltosSuite hallazgos de suite que estaban antes y ya no están; no es null
     * @param nuevosSuite hallazgos de suite que antes no estaban; no es null
     */
    public record Comparacion(
            ResultadoSuite anterior,
            ResultadoSuite actual,
            List<CambioCaso> casos,
            List<Hallazgo> resueltosSuite,
            List<Hallazgo> nuevosSuite) {

        /** Crea la comparación con copias inmutables de las listas. */
        public Comparacion {
            Objects.requireNonNull(anterior, "La ejecución anterior no puede ser null");
            Objects.requireNonNull(actual, "La ejecución actual no puede ser null");
            casos = List.copyOf(casos);
            resueltosSuite = List.copyOf(resueltosSuite);
            nuevosSuite = List.copyOf(nuevosSuite);
        }

        /**
         * Cuenta los casos con un cambio.
         *
         * @param cambio cambio buscado; no puede ser null
         * @return número de casos con ese cambio
         */
        public long cuenta(Cambio cambio) {
            return casos.stream().filter(c -> c.cambio() == cambio).count();
        }

        /**
         * Indica si algo empeoró.
         *
         * @return {@code true} si algún caso empeoró, apareció un caso que no cumple o hay un
         *     hallazgo de suite nuevo
         */
        public boolean empeoro() {
            return cuenta(Cambio.EMPEORO) > 0
                    || casos.stream()
                            .anyMatch(c ->
                                    c.cambio() == Cambio.NUEVO
                                            && c.actual() == EstadoVeredicto.NO_CUMPLE)
                    || !nuevosSuite.isEmpty();
        }
    }

    /**
     * Compara dos ejecuciones.
     *
     * @param anterior ejecución anterior; no puede ser null
     * @param actual ejecución actual; no puede ser null
     * @return los cambios de cada caso y de los hallazgos de suite
     * @throws NullPointerException si algún parámetro es null
     */
    public static Comparacion comparar(ResultadoSuite anterior, ResultadoSuite actual) {
        Objects.requireNonNull(anterior, "La ejecución anterior no puede ser null");
        Objects.requireNonNull(actual, "La ejecución actual no puede ser null");
        Map<String, ResultadoCaso> antes = porId(anterior);
        Map<String, ResultadoCaso> ahora = porId(actual);
        List<CambioCaso> casos = new ArrayList<>();
        for (ResultadoCaso caso : actual.casos()) {
            ResultadoCaso previo = antes.get(caso.casoId());
            casos.add(
                    previo == null
                            ? new CambioCaso(
                                    caso.casoId(),
                                    Cambio.NUEVO,
                                    null,
                                    caso.veredicto().estado(),
                                    List.of(),
                                    caso.veredicto().hallazgos())
                            : cambiar(previo, caso));
        }
        for (ResultadoCaso caso : anterior.casos()) {
            if (!ahora.containsKey(caso.casoId())) {
                casos.add(
                        new CambioCaso(
                                caso.casoId(),
                                Cambio.ELIMINADO,
                                caso.veredicto().estado(),
                                null,
                                caso.veredicto().hallazgos(),
                                List.of()));
            }
        }
        return new Comparacion(
                anterior,
                actual,
                casos,
                restar(anterior.hallazgosSuite(), actual.hallazgosSuite()),
                restar(actual.hallazgosSuite(), anterior.hallazgosSuite()));
    }

    private static CambioCaso cambiar(ResultadoCaso previo, ResultadoCaso caso) {
        EstadoVeredicto antes = previo.veredicto().estado();
        EstadoVeredicto ahora = caso.veredicto().estado();
        int diferencia = Integer.compare(gravedad(ahora), gravedad(antes));
        Cambio cambio =
                diferencia > 0 ? Cambio.EMPEORO : diferencia < 0 ? Cambio.MEJORO : Cambio.IGUAL;
        return new CambioCaso(
                caso.casoId(),
                cambio,
                antes,
                ahora,
                restar(previo.veredicto().hallazgos(), caso.veredicto().hallazgos()),
                restar(caso.veredicto().hallazgos(), previo.veredicto().hallazgos()));
    }

    /** Gravedad de un veredicto: cuanto mayor, peor. */
    private static int gravedad(EstadoVeredicto estado) {
        return switch (estado) {
            case CUMPLE -> 0;
            case NO_CONCLUYENTE -> 1;
            case NO_CUMPLE -> 2;
        };
    }

    /** Hallazgos de {@code base} que no están en {@code otros}, por oráculo y título. */
    private static List<Hallazgo> restar(List<Hallazgo> base, List<Hallazgo> otros) {
        List<String> claves = otros.stream().map(ComparadorResultados::clave).toList();
        return base.stream().filter(h -> !claves.contains(clave(h))).toList();
    }

    private static String clave(Hallazgo hallazgo) {
        return hallazgo.oraculoId() + "\u001f" + hallazgo.titulo();
    }

    private static Map<String, ResultadoCaso> porId(ResultadoSuite suite) {
        Map<String, ResultadoCaso> mapa = new LinkedHashMap<>();
        suite.casos().forEach(caso -> mapa.put(caso.casoId(), caso));
        return mapa;
    }
}
