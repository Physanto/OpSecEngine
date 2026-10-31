package io.github.physanto.opsecengine.ataques;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Un payload de la biblioteca de ataques: el texto que se escribe en un campo para buscar un fallo.
 *
 * <p>Es inmutable y seguro entre hilos. Los valores de {@code extensiones} se copian de forma
 * superficial.
 *
 * @param id identificador único, en mayúsculas con guiones; no puede ser null
 * @param categoria categoría de la biblioteca; no puede ser null
 * @param aplicaA tipos de campo en los que se prueba; si es null o vacío aplica a cualquiera
 * @param texto texto que se escribe en el campo (el campo {@code payload} del YAML); puede estar
 *     vacío pero no ser null
 * @param usaCanario si es true, el texto contiene {@code {canario}} y el motor lo reemplaza por una
 *     marca única
 * @param oraculos ids de los oráculos que evalúan su efecto; si es null queda vacía
 * @param cwe debilidad que intenta revelar, con el formato {@code CWE-<número>}; puede ser null
 * @param descripcion explicación para las personas; puede ser null
 * @param extensiones campos adicionales; si es null queda vacío
 */
public record Payload(
        String id,
        CategoriaPayload categoria,
        Set<TipoCampo> aplicaA,
        String texto,
        boolean usaCanario,
        List<String> oraculos,
        String cwe,
        String descripcion,
        Map<String, Object> extensiones) {

    /**
     * Crea el payload y valida sus datos.
     *
     * @throws NullPointerException si {@code id}, {@code categoria} o {@code texto} son null
     */
    public Payload {
        Objects.requireNonNull(id, "El id del payload no puede ser null");
        Objects.requireNonNull(categoria, "La categoría del payload " + id + " no puede ser null");
        Objects.requireNonNull(texto, "El texto del payload " + id + " no puede ser null");
        aplicaA =
                aplicaA == null || aplicaA.isEmpty()
                        ? Set.of(TipoCampo.CUALQUIERA)
                        : Set.copyOf(EnumSet.copyOf(aplicaA));
        oraculos = oraculos == null ? List.of() : List.copyOf(oraculos);
        extensiones = extensiones == null ? Map.of() : Map.copyOf(extensiones);
    }

    /**
     * Indica si el payload se prueba en un tipo de campo.
     *
     * @param tipo tipo del campo; no puede ser null
     * @return true si el payload aplica a cualquier campo o a ese tipo
     */
    public boolean sirvePara(TipoCampo tipo) {
        Objects.requireNonNull(tipo, "El tipo de campo no puede ser null");
        return aplicaA.contains(TipoCampo.CUALQUIERA) || aplicaA.contains(tipo);
    }
}
