package io.github.physanto.opsecengine.dominio.caso;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Suite de pruebas: conjunto de casos que se ejecutan juntos con una misma configuración.
 *
 * <p>Es inmutable y seguro entre hilos. Los valores de {@code extensiones} se copian de forma
 * superficial: quien los crea debe usar valores inmutables.
 *
 * @param nombre nombre de la suite; no puede ser null ni estar en blanco
 * @param version versión de la suite definida por quien la escribe, por ejemplo {@code "1.0"};
 *     puede ser null
 * @param enfoques enfoques que cubre la suite; si es null queda vacío, y no admite elementos null
 * @param config configuración de ejecución; si es null se usa {@link ConfigEjecucion#porDefecto()}
 * @param casos casos en el orden en que se ejecutan, con ids únicos; si es null queda vacía, y no
 *     admite elementos null
 * @param extensiones campos adicionales para cambios aditivos del contrato; si es null queda vacío,
 *     y no admite claves ni valores null
 */
public record Suite(
        String nombre,
        String version,
        Set<Enfoque> enfoques,
        ConfigEjecucion config,
        List<Caso> casos,
        Map<String, Object> extensiones) {

    /**
     * Crea una suite con copias inmutables de sus colecciones y valida sus datos.
     *
     * @throws NullPointerException si {@code nombre} es null o si alguna colección contiene
     *     elementos, claves o valores null
     * @throws IllegalArgumentException si {@code nombre} está en blanco o si hay dos casos con el
     *     mismo id
     */
    public Suite {
        Objects.requireNonNull(nombre, "El nombre de la suite no puede ser null");
        if (nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre de la suite no puede estar en blanco");
        }
        enfoques = enfoques == null ? Set.of() : Set.copyOf(enfoques);
        config = config == null ? ConfigEjecucion.porDefecto() : config;
        casos = casos == null ? List.of() : List.copyOf(casos);
        extensiones = extensiones == null ? Map.of() : Map.copyOf(extensiones);

        Set<String> ids = new HashSet<>();
        for (Caso caso : casos) {
            if (!ids.add(caso.id())) {
                throw new IllegalArgumentException(
                        "La suite " + nombre + " tiene casos repetidos con el id " + caso.id());
            }
        }
    }
}
