package com.google.cloud.firestore;

import java.util.Map;

/** Doble de prueba de Firestore: solo existe en las pruebas, con el mismo nombre que la real. */
public class WriteBatch {

    /**
     * Agrega una escritura al lote.
     *
     * @param documento documento destino
     * @param campos campos escritos
     * @return este lote
     */
    public WriteBatch set(DocumentReference documento, Map<String, Object> campos) {
        return this;
    }

    /**
     * Confirma el lote.
     *
     * @return nada útil
     */
    public String commit() {
        return "ok";
    }
}
