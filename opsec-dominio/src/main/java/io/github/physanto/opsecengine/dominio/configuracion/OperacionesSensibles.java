package io.github.physanto.opsecengine.dominio.configuracion;

import java.util.Set;

/**
 * Operaciones que los ganchos del agente consideran sensibles, es decir, que modifican datos.
 *
 * <p>Los oráculos de seguridad las usan, por ejemplo, para detectar una eliminación ejecutada sin
 * sesión. Es inmutable y seguro entre hilos.
 *
 * @param sql palabras clave de las sentencias SQL sensibles, en mayúsculas, por ejemplo {@code
 *     "DELETE"}; si es null queda vacío, y no admite elementos null
 * @param firestore nombres de los métodos de Firestore sensibles, por ejemplo {@code "delete"}; si
 *     es null queda vacío, y no admite elementos null
 */
public record OperacionesSensibles(Set<String> sql, Set<String> firestore) {

    /**
     * Crea el conjunto de operaciones sensibles con copias inmutables de sus colecciones.
     *
     * @throws NullPointerException si alguna colección contiene elementos null
     */
    public OperacionesSensibles {
        sql = sql == null ? Set.of() : Set.copyOf(sql);
        firestore = firestore == null ? Set.of() : Set.copyOf(firestore);
    }

    /**
     * Devuelve las operaciones sensibles por defecto: las sentencias SQL {@code DELETE}, {@code
     * UPDATE} e {@code INSERT}, y los métodos de Firestore {@code delete}, {@code set} y {@code
     * update}.
     *
     * @return operaciones sensibles por defecto
     */
    public static OperacionesSensibles porDefecto() {
        return new OperacionesSensibles(
                Set.of("DELETE", "UPDATE", "INSERT"), Set.of("delete", "set", "update"));
    }
}
