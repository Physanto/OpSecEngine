package io.github.physanto.opsecengine.protocolo.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Copias defensivas inmutables compatibles con Java 8. */
final class Copias {

    private Copias() {}

    /**
     * Copia una lista en una lista inmutable.
     *
     * @param lista lista original; puede ser null
     * @param descripcion nombre de la lista para el mensaje de error
     * @return copia inmutable; vacía si {@code lista} es null
     * @throws NullPointerException si la lista contiene elementos null
     */
    static <T> List<T> lista(List<T> lista, String descripcion) {
        if (lista == null) {
            return Collections.emptyList();
        }
        List<T> copia = new ArrayList<>(lista.size());
        for (T elemento : lista) {
            copia.add(Objects.requireNonNull(elemento, descripcion + " no admite elementos null"));
        }
        return Collections.unmodifiableList(copia);
    }

    /**
     * Copia un mapa en un mapa inmutable que conserva el orden y descarta los valores null.
     *
     * @param mapa mapa original; puede ser null
     * @param descripcion nombre del mapa para el mensaje de error
     * @return copia inmutable; vacía si {@code mapa} es null
     * @throws NullPointerException si el mapa contiene claves null
     */
    static Map<String, Object> mapaSinNulos(Map<String, Object> mapa, String descripcion) {
        if (mapa == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> copia = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entrada : mapa.entrySet()) {
            Objects.requireNonNull(entrada.getKey(), descripcion + " no admite claves null");
            if (entrada.getValue() != null) {
                copia.put(entrada.getKey(), entrada.getValue());
            }
        }
        return Collections.unmodifiableMap(copia);
    }

    /**
     * Exige un texto no null y no vacío.
     *
     * @param valor texto a validar
     * @param descripcion nombre del campo para el mensaje de error
     * @return el mismo texto
     * @throws NullPointerException si {@code valor} es null
     * @throws IllegalArgumentException si {@code valor} está vacío o solo tiene espacios
     */
    static String texto(String valor, String descripcion) {
        Objects.requireNonNull(valor, descripcion + " no puede ser null");
        if (valor.trim().isEmpty()) {
            throw new IllegalArgumentException(descripcion + " no puede estar vacío");
        }
        return valor;
    }
}
