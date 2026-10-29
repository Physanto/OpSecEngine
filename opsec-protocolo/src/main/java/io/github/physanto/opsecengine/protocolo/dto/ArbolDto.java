package io.github.physanto.opsecengine.protocolo.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Árbol de componentes que el agente envía en un mensaje {@code ARBOL}.
 *
 * <p>Es inmutable y seguro entre hilos.
 */
public final class ArbolDto {

    private final List<NodoArbolDto> ventanas;

    /**
     * Crea un árbol.
     *
     * @param ventanas nodos raíz, uno por ventana o diálogo visible; si es null queda vacía, y no
     *     admite elementos null
     * @throws NullPointerException si alguna ventana es null
     */
    @JsonCreator
    public ArbolDto(@JsonProperty("ventanas") List<NodoArbolDto> ventanas) {
        this.ventanas = Copias.lista(ventanas, "Las ventanas del árbol");
    }

    /**
     * Devuelve los nodos raíz del árbol.
     *
     * @return lista inmutable con una raíz por ventana visible
     */
    public List<NodoArbolDto> getVentanas() {
        return ventanas;
    }

    /**
     * Compara este árbol con otro objeto.
     *
     * @param otro objeto a comparar
     * @return {@code true} si es un árbol con las mismas ventanas en el mismo orden
     */
    @Override
    public boolean equals(Object otro) {
        return this == otro
                || otro instanceof ArbolDto && ventanas.equals(((ArbolDto) otro).ventanas);
    }

    /**
     * Calcula el hash a partir de las ventanas.
     *
     * @return hash del árbol
     */
    @Override
    public int hashCode() {
        return ventanas.hashCode();
    }

    /**
     * Devuelve una representación breve del árbol.
     *
     * @return texto con las ventanas raíz
     */
    @Override
    public String toString() {
        return "ArbolDto" + ventanas;
    }
}
