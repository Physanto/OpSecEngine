package io.github.physanto.opsecengine.practica.datos;

import java.util.Objects;

/**
 * Fila de la tabla {@code persona}: los clientes que la app busca y elimina.
 *
 * <p>Es inmutable y segura entre hilos.
 */
public final class Persona {

    private final String uid;
    private final String nombre;
    private final String documento;
    private final String correo;

    /**
     * Crea una persona.
     *
     * @param uid identificador único, por ejemplo {@code P001}
     * @param nombre nombre completo
     * @param documento número de documento
     * @param correo correo electrónico
     * @throws NullPointerException si algún argumento es {@code null}
     */
    public Persona(String uid, String nombre, String documento, String correo) {
        this.uid = Objects.requireNonNull(uid, "uid");
        this.nombre = Objects.requireNonNull(nombre, "nombre");
        this.documento = Objects.requireNonNull(documento, "documento");
        this.correo = Objects.requireNonNull(correo, "correo");
    }

    /**
     * Devuelve el identificador único.
     *
     * @return uid
     */
    public String uid() {
        return uid;
    }

    /**
     * Devuelve el nombre completo.
     *
     * @return nombre
     */
    public String nombre() {
        return nombre;
    }

    /**
     * Devuelve el número de documento.
     *
     * @return documento
     */
    public String documento() {
        return documento;
    }

    /**
     * Devuelve el correo electrónico.
     *
     * @return correo
     */
    public String correo() {
        return correo;
    }

    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (!(otro instanceof Persona)) {
            return false;
        }
        Persona persona = (Persona) otro;
        return uid.equals(persona.uid)
                && nombre.equals(persona.nombre)
                && documento.equals(persona.documento)
                && correo.equals(persona.correo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uid, nombre, documento, correo);
    }

    @Override
    public String toString() {
        return "Persona[" + uid + ", " + nombre + "]";
    }
}
