package io.github.physanto.opsecengine.protocolo.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

/**
 * Componente del árbol de una ventana, con sus hijos.
 *
 * <p>Lo usan el explorador para construir el mapa y el inspector para mostrar la interfaz. Es
 * inmutable y seguro entre hilos.
 */
public final class NodoArbolDto {

    private final String tipo;
    private final String texto;
    private final String nombre;
    private final String nombreAccesible;
    private final boolean visible;
    private final boolean habilitado;
    private final boolean editable;
    private final List<NodoArbolDto> hijos;

    /**
     * Crea un nodo del árbol.
     *
     * @param tipo nombre completo de la clase del componente, por ejemplo {@code
     *     javax.swing.JButton}; no puede ser null ni estar vacío
     * @param texto texto visible del componente o título de la ventana; puede ser null
     * @param nombre nombre asignado con {@code Component.setName}; puede ser null
     * @param nombreAccesible nombre accesible del componente; puede ser null
     * @param visible {@code true} si el componente se muestra en pantalla
     * @param habilitado {@code true} si el componente acepta interacción
     * @param editable {@code true} si el componente permite escribir, como un campo de texto
     * @param hijos componentes contenidos, en el orden del contenedor; si es null queda vacía, y no
     *     admite elementos null
     * @throws NullPointerException si {@code tipo} es null o si algún hijo es null
     * @throws IllegalArgumentException si {@code tipo} está vacío
     */
    @JsonCreator
    public NodoArbolDto(
            @JsonProperty("tipo") String tipo,
            @JsonProperty("texto") String texto,
            @JsonProperty("nombre") String nombre,
            @JsonProperty("nombreAccesible") String nombreAccesible,
            @JsonProperty("visible") boolean visible,
            @JsonProperty("habilitado") boolean habilitado,
            @JsonProperty("editable") boolean editable,
            @JsonProperty("hijos") List<NodoArbolDto> hijos) {
        this.tipo = Copias.texto(tipo, "El tipo del nodo");
        this.texto = texto;
        this.nombre = nombre;
        this.nombreAccesible = nombreAccesible;
        this.visible = visible;
        this.habilitado = habilitado;
        this.editable = editable;
        this.hijos = Copias.lista(hijos, "Los hijos del nodo");
    }

    /**
     * Devuelve la clase del componente.
     *
     * @return nombre completo de la clase; nunca es null
     */
    public String getTipo() {
        return tipo;
    }

    /**
     * Devuelve el texto visible del componente.
     *
     * @return texto, o null si no tiene
     */
    public String getTexto() {
        return texto;
    }

    /**
     * Devuelve el nombre asignado al componente.
     *
     * @return nombre, o null si no tiene
     */
    public String getNombre() {
        return nombre;
    }

    /**
     * Devuelve el nombre accesible del componente.
     *
     * @return nombre accesible, o null si no tiene
     */
    public String getNombreAccesible() {
        return nombreAccesible;
    }

    /**
     * Indica si el componente se muestra en pantalla.
     *
     * @return {@code true} si es visible
     */
    public boolean isVisible() {
        return visible;
    }

    /**
     * Indica si el componente acepta interacción.
     *
     * @return {@code true} si está habilitado
     */
    public boolean isHabilitado() {
        return habilitado;
    }

    /**
     * Indica si el componente permite escribir.
     *
     * @return {@code true} si es editable
     */
    public boolean isEditable() {
        return editable;
    }

    /**
     * Devuelve los componentes contenidos.
     *
     * @return lista inmutable; vacía si no tiene hijos
     */
    public List<NodoArbolDto> getHijos() {
        return hijos;
    }

    /**
     * Compara este nodo con otro objeto campo por campo, incluidos sus hijos.
     *
     * @param otro objeto a comparar
     * @return {@code true} si es un nodo con los mismos campos e hijos
     */
    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (!(otro instanceof NodoArbolDto)) {
            return false;
        }
        NodoArbolDto nodo = (NodoArbolDto) otro;
        return visible == nodo.visible
                && habilitado == nodo.habilitado
                && editable == nodo.editable
                && tipo.equals(nodo.tipo)
                && Objects.equals(texto, nodo.texto)
                && Objects.equals(nombre, nodo.nombre)
                && Objects.equals(nombreAccesible, nodo.nombreAccesible)
                && hijos.equals(nodo.hijos);
    }

    /**
     * Calcula el hash a partir de todos los campos.
     *
     * @return hash del nodo
     */
    @Override
    public int hashCode() {
        return Objects.hash(
                tipo, texto, nombre, nombreAccesible, visible, habilitado, editable, hijos);
    }

    /**
     * Devuelve una representación breve del nodo, sin sus hijos.
     *
     * @return texto con el tipo, el texto, el nombre y la cantidad de hijos
     */
    @Override
    public String toString() {
        return "NodoArbolDto[tipo="
                + tipo
                + ", texto="
                + texto
                + ", nombre="
                + nombre
                + ", hijos="
                + hijos.size()
                + "]";
    }
}
