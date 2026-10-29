package io.github.physanto.opsecengine.dominio.extension;

import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.Suite;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.dominio.traza.Traza;
import java.util.List;
import java.util.Objects;

/**
 * Información que recibe un {@link Oraculo} junto con la traza: el proyecto, la suite y, según el
 * momento, el caso o las trazas de toda la suite.
 *
 * <p>En los momentos {@link Momento#EVENTO} y {@link Momento#CASO} hay caso y {@code trazasSuite}
 * está vacía. En el momento {@link Momento#SUITE} el caso es null y {@code trazasSuite} tiene las
 * trazas de los casos ejecutados. Es inmutable y seguro entre hilos.
 *
 * @param proyecto proyecto de pruebas; no puede ser null
 * @param suite suite en ejecución; no puede ser null
 * @param caso caso evaluado; es null en el momento {@link Momento#SUITE}
 * @param trazasSuite trazas de los casos de la suite, en orden de ejecución; vacía fuera del
 *     momento {@link Momento#SUITE}; si es null queda vacía, y no admite elementos null
 */
public record Contexto(Proyecto proyecto, Suite suite, Caso caso, List<Traza> trazasSuite) {

    /**
     * Crea un contexto y valida sus datos.
     *
     * @throws NullPointerException si {@code proyecto} o {@code suite} son null, o si alguna traza
     *     es null
     * @throws IllegalArgumentException si hay caso y {@code trazasSuite} no está vacía
     */
    public Contexto {
        Objects.requireNonNull(proyecto, "El proyecto del contexto no puede ser null");
        Objects.requireNonNull(suite, "La suite del contexto no puede ser null");
        trazasSuite = trazasSuite == null ? List.of() : List.copyOf(trazasSuite);
        if (caso != null && !trazasSuite.isEmpty()) {
            throw new IllegalArgumentException(
                    "Un contexto de caso no lleva trazas de suite; use null como caso para el"
                            + " momento SUITE");
        }
    }

    /**
     * Crea el contexto de los momentos {@link Momento#EVENTO} y {@link Momento#CASO}.
     *
     * @param proyecto proyecto de pruebas; no puede ser null
     * @param suite suite en ejecución; no puede ser null
     * @param caso caso evaluado; no puede ser null
     * @return contexto con el caso y sin trazas de suite
     * @throws NullPointerException si algún parámetro es null
     */
    public static Contexto deCaso(Proyecto proyecto, Suite suite, Caso caso) {
        Objects.requireNonNull(caso, "El caso del contexto no puede ser null");
        return new Contexto(proyecto, suite, caso, List.of());
    }

    /**
     * Crea el contexto del momento {@link Momento#SUITE}.
     *
     * @param proyecto proyecto de pruebas; no puede ser null
     * @param suite suite ejecutada; no puede ser null
     * @param trazasSuite trazas de los casos ejecutados; si es null queda vacía
     * @return contexto sin caso y con las trazas de la suite
     * @throws NullPointerException si {@code proyecto} o {@code suite} son null, o si alguna traza
     *     es null
     */
    public static Contexto deSuite(Proyecto proyecto, Suite suite, List<Traza> trazasSuite) {
        return new Contexto(proyecto, suite, null, trazasSuite);
    }
}
