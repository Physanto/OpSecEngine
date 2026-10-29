package io.github.physanto.opsecengine.dominio.traza;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Evento observado durante una ejecución.
 *
 * <p>Los datos propios de cada tipo viajan en {@link #datos()}, con los campos que documenta el
 * catálogo {@code contratos/catalogos/eventos.yaml}. Sus valores son los tipos que produce un
 * lector JSON: {@code String}, {@code Number}, {@code Boolean}, {@code List} y {@code Map}. Las
 * lecturas tipadas ({@link #texto(String)}, {@link #numero(String)}, {@link #booleano(String)})
 * nunca lanzan excepciones por datos ausentes o de otro tipo: devuelven un valor vacío.
 *
 * <p>Es inmutable y seguro entre hilos. Los valores de {@code datos} se copian de forma
 * superficial: quien los crea debe usar valores inmutables.
 *
 * @param secuencia número de orden del evento dentro de su traza, cero o más; es único en la traza
 *     y sirve como evidencia en los hallazgos
 * @param instante momento en que ocurrió el evento; no puede ser null
 * @param tipo tipo de evento; no puede ser null
 * @param contexto contexto en que ocurrió; no puede ser null (use {@link
 *     ContextoEvento#fueraDeCaso()} si no hay caso)
 * @param datos campos propios del tipo de evento; si es null queda vacío, y no admite claves ni
 *     valores null
 */
public record Evento(
        long secuencia,
        Instant instante,
        TipoEvento tipo,
        ContextoEvento contexto,
        Map<String, Object> datos) {

    /**
     * Crea un evento con una copia inmutable de sus datos y valida sus campos.
     *
     * @throws IllegalArgumentException si {@code secuencia} es negativa
     * @throws NullPointerException si {@code instante}, {@code tipo} o {@code contexto} son null, o
     *     si {@code datos} contiene claves o valores null
     */
    public Evento {
        if (secuencia < 0) {
            throw new IllegalArgumentException(
                    "La secuencia del evento no puede ser negativa: " + secuencia);
        }
        Objects.requireNonNull(instante, "El instante del evento no puede ser null");
        Objects.requireNonNull(tipo, "El tipo del evento no puede ser null");
        Objects.requireNonNull(contexto, "El contexto del evento no puede ser null");
        datos = datos == null ? Map.of() : Map.copyOf(datos);
    }

    /**
     * Lee un campo de texto de los datos.
     *
     * @param clave nombre del campo; no puede ser null
     * @return el valor si existe y es un {@code String}; vacío en otro caso
     * @throws NullPointerException si {@code clave} es null
     */
    public Optional<String> texto(String clave) {
        return leer(clave, String.class);
    }

    /**
     * Lee un campo numérico de los datos.
     *
     * @param clave nombre del campo; no puede ser null
     * @return el valor si existe y es un {@code Number} (por ejemplo {@code Integer}, {@code Long}
     *     o {@code Double}); vacío en otro caso
     * @throws NullPointerException si {@code clave} es null
     */
    public Optional<Number> numero(String clave) {
        return leer(clave, Number.class);
    }

    /**
     * Lee un campo booleano de los datos.
     *
     * @param clave nombre del campo; no puede ser null
     * @return el valor si existe y es un {@code Boolean}; vacío en otro caso
     * @throws NullPointerException si {@code clave} es null
     */
    public Optional<Boolean> booleano(String clave) {
        return leer(clave, Boolean.class);
    }

    private <T> Optional<T> leer(String clave, Class<T> tipoValor) {
        Objects.requireNonNull(clave, "La clave del dato no puede ser null");
        Object valor = datos.get(clave);
        return tipoValor.isInstance(valor) ? Optional.of(tipoValor.cast(valor)) : Optional.empty();
    }
}
