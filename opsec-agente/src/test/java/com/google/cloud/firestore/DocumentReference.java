package com.google.cloud.firestore;

import java.util.Map;

/** Doble de prueba de Firestore: solo existe en las pruebas, con el mismo nombre que la real. */
public class DocumentReference {

    private final String ruta;

    /**
     * Crea el doble.
     *
     * @param ruta ruta del documento
     */
    public DocumentReference(String ruta) {
        this.ruta = ruta;
    }

    /**
     * Devuelve la ruta.
     *
     * @return la ruta
     */
    public String getPath() {
        return ruta;
    }

    /**
     * Escribe campos.
     *
     * @param campos campos escritos
     * @return nada útil
     */
    public String set(Map<String, Object> campos) {
        return "ok";
    }

    /**
     * Borra el documento.
     *
     * @return nada útil
     */
    public String delete() {
        if (ruta.contains("fallo")) {
            throw new IllegalStateException("sin permiso");
        }
        return "ok";
    }
}
