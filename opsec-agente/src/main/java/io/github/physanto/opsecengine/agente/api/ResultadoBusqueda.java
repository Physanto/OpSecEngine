package io.github.physanto.opsecengine.agente.api;

import io.github.physanto.opsecengine.protocolo.dto.CodigoResultado;
import io.github.physanto.opsecengine.protocolo.dto.NodoArbolDto;
import java.util.Objects;

/**
 * Resultado de buscar un componente con un localizador.
 *
 * <p>Describe el componente encontrado con un {@link NodoArbolDto} en vez de devolver el objeto del
 * toolkit, para que {@link AdaptadorToolkit} no dependa de Swing. El código es {@link
 * CodigoResultado#OK}, {@link CodigoResultado#NO_ENCONTRADO} o {@link
 * CodigoResultado#NO_ALCANZABLE}. Es inmutable y seguro entre hilos.
 */
public final class ResultadoBusqueda {

    private final CodigoResultado codigo;
    private final NodoArbolDto nodo;
    private final int indiceCriterio;
    private final String detalle;

    private ResultadoBusqueda(
            CodigoResultado codigo, NodoArbolDto nodo, int indiceCriterio, String detalle) {
        this.codigo = codigo;
        this.nodo = nodo;
        this.indiceCriterio = indiceCriterio;
        this.detalle = detalle;
    }

    /**
     * Crea el resultado de un componente encontrado y alcanzable.
     *
     * @param nodo descripción del componente; no puede ser null
     * @param indiceCriterio posición, desde 0, del criterio del localizador que lo encontró
     * @return resultado con código {@link CodigoResultado#OK}
     * @throws NullPointerException si {@code nodo} es null
     * @throws IllegalArgumentException si {@code indiceCriterio} es negativo
     */
    public static ResultadoBusqueda encontrado(NodoArbolDto nodo, int indiceCriterio) {
        return new ResultadoBusqueda(
                CodigoResultado.OK, exigirNodo(nodo), exigirIndice(indiceCriterio), null);
    }

    /**
     * Crea el resultado de una búsqueda en la que ningún criterio encontró un componente.
     *
     * @param detalle explicación para el informe; puede ser null
     * @return resultado con código {@link CodigoResultado#NO_ENCONTRADO}, sin nodo y con índice -1
     */
    public static ResultadoBusqueda noEncontrado(String detalle) {
        return new ResultadoBusqueda(CodigoResultado.NO_ENCONTRADO, null, -1, detalle);
    }

    /**
     * Crea el resultado de un componente que existe pero no se puede usar: está oculto,
     * deshabilitado o tapado por un diálogo.
     *
     * @param nodo descripción del componente; no puede ser null
     * @param indiceCriterio posición, desde 0, del criterio del localizador que lo encontró
     * @param detalle explicación de por qué no es alcanzable; puede ser null
     * @return resultado con código {@link CodigoResultado#NO_ALCANZABLE}
     * @throws NullPointerException si {@code nodo} es null
     * @throws IllegalArgumentException si {@code indiceCriterio} es negativo
     */
    public static ResultadoBusqueda noAlcanzable(
            NodoArbolDto nodo, int indiceCriterio, String detalle) {
        return new ResultadoBusqueda(
                CodigoResultado.NO_ALCANZABLE,
                exigirNodo(nodo),
                exigirIndice(indiceCriterio),
                detalle);
    }

    /**
     * Devuelve el código del resultado.
     *
     * @return {@code OK}, {@code NO_ENCONTRADO} o {@code NO_ALCANZABLE}
     */
    public CodigoResultado getCodigo() {
        return codigo;
    }

    /**
     * Indica si se encontró un componente alcanzable.
     *
     * @return {@code true} si el código es {@link CodigoResultado#OK}
     */
    public boolean isEncontrado() {
        return codigo == CodigoResultado.OK;
    }

    /**
     * Devuelve la descripción del componente encontrado.
     *
     * @return nodo del componente, o null si no se encontró
     */
    public NodoArbolDto getNodo() {
        return nodo;
    }

    /**
     * Devuelve la posición del criterio que encontró el componente.
     *
     * @return índice desde 0, o -1 si no se encontró
     */
    public int getIndiceCriterio() {
        return indiceCriterio;
    }

    /**
     * Devuelve la explicación del resultado.
     *
     * @return detalle, o null si no hay
     */
    public String getDetalle() {
        return detalle;
    }

    /**
     * Compara este resultado con otro objeto campo por campo.
     *
     * @param otro objeto a comparar
     * @return {@code true} si es un resultado con los mismos campos
     */
    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (!(otro instanceof ResultadoBusqueda)) {
            return false;
        }
        ResultadoBusqueda resultado = (ResultadoBusqueda) otro;
        return indiceCriterio == resultado.indiceCriterio
                && codigo == resultado.codigo
                && Objects.equals(nodo, resultado.nodo)
                && Objects.equals(detalle, resultado.detalle);
    }

    /**
     * Calcula el hash a partir de todos los campos.
     *
     * @return hash del resultado
     */
    @Override
    public int hashCode() {
        return Objects.hash(codigo, nodo, indiceCriterio, detalle);
    }

    /**
     * Devuelve una representación legible del resultado.
     *
     * @return texto con el código, el nodo, el índice del criterio y el detalle
     */
    @Override
    public String toString() {
        return "ResultadoBusqueda[codigo="
                + codigo
                + ", nodo="
                + nodo
                + ", indiceCriterio="
                + indiceCriterio
                + ", detalle="
                + detalle
                + "]";
    }

    private static NodoArbolDto exigirNodo(NodoArbolDto nodo) {
        return Objects.requireNonNull(nodo, "El nodo del componente no puede ser null");
    }

    private static int exigirIndice(int indiceCriterio) {
        if (indiceCriterio < 0) {
            throw new IllegalArgumentException(
                    "El índice del criterio no puede ser negativo: " + indiceCriterio);
        }
        return indiceCriterio;
    }
}
