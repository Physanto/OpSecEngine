package io.github.physanto.opsecengine.dominio.caso;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Caso de prueba: una secuencia de pasos que se ejecuta con un rol, junto con los ataques y
 * oráculos que se le aplican.
 *
 * <p>Es inmutable y seguro entre hilos. Los valores de {@code extensiones} se copian de forma
 * superficial: quien los crea debe usar valores inmutables.
 *
 * @param id identificador del caso, único dentro de su suite, por ejemplo {@code "SEC-001"}; no
 *     puede ser null ni estar en blanco
 * @param descripcion explicación legible del caso; puede ser null
 * @param rol nombre del rol del proyecto con el que se inicia sesión antes de los pasos; puede ser
 *     null, lo que significa que el caso se ejecuta sin sesión
 * @param pasos pasos en el orden en que se ejecutan; si es null queda vacía, y no admite elementos
 *     null
 * @param ataques identificadores de los ataques del catálogo que se aplican al caso; si es null
 *     queda vacía, y no admite elementos null
 * @param oraculos identificadores de los oráculos que se evalúan además de los implícitos; si es
 *     null queda vacía, y no admite elementos null
 * @param extensiones campos adicionales para cambios aditivos del contrato; si es null queda vacío,
 *     y no admite claves ni valores null
 */
public record Caso(
        String id,
        String descripcion,
        String rol,
        List<Paso> pasos,
        List<String> ataques,
        List<String> oraculos,
        Map<String, Object> extensiones) {

    /**
     * Crea un caso con copias inmutables de sus colecciones y valida sus datos.
     *
     * @throws NullPointerException si {@code id} es null o si alguna colección contiene elementos,
     *     claves o valores null
     * @throws IllegalArgumentException si {@code id} está en blanco o si {@code rol} no es null y
     *     está en blanco
     */
    public Caso {
        Objects.requireNonNull(id, "El id del caso no puede ser null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("El id del caso no puede estar en blanco");
        }
        if (rol != null && rol.isBlank()) {
            throw new IllegalArgumentException(
                    "El rol del caso "
                            + id
                            + " no puede estar en blanco; use null para sin sesión");
        }
        pasos = pasos == null ? List.of() : List.copyOf(pasos);
        ataques = ataques == null ? List.of() : List.copyOf(ataques);
        oraculos = oraculos == null ? List.of() : List.copyOf(oraculos);
        extensiones = extensiones == null ? Map.of() : Map.copyOf(extensiones);
    }

    /**
     * Indica si el caso se ejecuta sin iniciar sesión.
     *
     * @return {@code true} si {@link #rol()} es null
     */
    public boolean sinSesion() {
        return rol == null;
    }
}
