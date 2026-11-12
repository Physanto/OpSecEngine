package io.github.physanto.opsecengine.dominio.caso;

import io.github.physanto.opsecengine.dominio.traza.Evento;
import io.github.physanto.opsecengine.dominio.traza.TipoEvento;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Señal observable en la traza: un evento de un tipo dado cuyos datos tienen ciertos valores.
 *
 * <p>Las políticas de validación y de autenticación la usan para describir, sin depender de los
 * textos de una aplicación concreta, qué evento significa aceptación, rechazo o bloqueo. Por
 * ejemplo, un {@link TipoEvento#DIALOGO} con {@code tipoMensaje = ERROR}, o un {@link
 * TipoEvento#SESION} con {@code activa = true}. Desde contratos v1.2.
 *
 * <p>Es inmutable y segura entre hilos.
 *
 * @param tipo tipo del evento; no puede ser null
 * @param campos valores que deben tener los datos del evento, todos a la vez; no puede ser null ni
 *     estar vacío, y sus valores solo pueden ser textos, booleanos o números
 */
public record Senal(TipoEvento tipo, Map<String, Object> campos) {

    /**
     * Crea una señal y valida sus datos.
     *
     * @throws NullPointerException si {@code tipo} o {@code campos} es null, o si {@code campos}
     *     contiene claves o valores null
     * @throws IllegalArgumentException si {@code campos} está vacío, tiene una clave en blanco o un
     *     valor que no es texto, booleano o número
     */
    public Senal {
        Objects.requireNonNull(tipo, "El tipo de la señal no puede ser null");
        Objects.requireNonNull(campos, "Los campos de la señal no pueden ser null");
        if (campos.isEmpty()) {
            throw new IllegalArgumentException("La señal necesita al menos un campo");
        }
        Map<String, Object> copia = new LinkedHashMap<>();
        campos.forEach((campo, valor) -> {
            Objects.requireNonNull(campo, "El campo de la señal no puede ser null");
            Objects.requireNonNull(valor, "El valor del campo " + campo + " es null");
            if (campo.isBlank()) {
                throw new IllegalArgumentException("El campo de la señal no puede estar en blanco");
            }
            if (!(valor instanceof String || valor instanceof Boolean || valor instanceof Number)) {
                throw new IllegalArgumentException(
                        "El campo " + campo + " de la señal solo admite texto, booleano o número");
            }
            copia.put(campo, valor);
        });
        campos = Map.copyOf(copia);
    }

    /**
     * Indica si un evento cumple la señal: es del mismo tipo y sus datos tienen todos los valores
     * de {@link #campos()}. Los números se comparan por su valor ({@code 1} es igual a {@code
     * 1.0}); los textos no se convierten en números ni en booleanos.
     *
     * @param evento evento de la traza; puede ser null, en cuyo caso devuelve {@code false}
     * @return {@code true} si el evento cumple la señal
     */
    public boolean coincide(Evento evento) {
        if (evento == null || evento.tipo() != tipo) {
            return false;
        }
        for (Map.Entry<String, Object> campo : campos.entrySet()) {
            if (!iguales(campo.getValue(), evento.datos().get(campo.getKey()))) {
                return false;
            }
        }
        return true;
    }

    private static boolean iguales(Object esperado, Object actual) {
        if (esperado instanceof Number a && actual instanceof Number b) {
            try {
                return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString())) == 0;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return esperado.equals(actual);
    }
}
