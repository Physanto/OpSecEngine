package io.github.physanto.opsecengine.nucleo.exploracion;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Límites y reglas de seguridad de una exploración.
 *
 * @param profundidadMaxima cantidad máxima de acciones encadenadas desde la ventana inicial; no
 *     puede ser negativa
 * @param tiempoMaximo tiempo total máximo de la exploración; debe ser positivo
 * @param textosDestructivos textos de controles que nunca se pulsan, comparados sin tildes ni
 *     mayúsculas y por inclusión (por ejemplo {@code "eliminar"} omite «Eliminar persona»); no
 *     puede ser null
 * @param oculto si la aplicación se explora con las ventanas fuera de pantalla
 * @param esperaTransicion cuánto se espera, tras una acción, a que cambien las ventanas; debe ser
 *     positivo
 * @param entradas clases con {@code main} (o ventanas que se pueden lanzar solas) que se exploran
 *     como puntos de entrada alternativos, cada una en su contexto {@code entrada:<clase>} y sin
 *     iniciar sesión; no puede ser null (vacía si no hay)
 */
public record ConfigExploracion(
        int profundidadMaxima,
        Duration tiempoMaximo,
        Set<String> textosDestructivos,
        boolean oculto,
        Duration esperaTransicion,
        List<String> entradas) {

    /** Profundidad máxima por defecto. */
    public static final int PROFUNDIDAD_POR_DEFECTO = 4;

    /** Tiempo máximo por defecto. */
    public static final Duration TIEMPO_POR_DEFECTO = Duration.ofMinutes(5);

    /** Espera por defecto a que cambien las ventanas después de una acción. */
    public static final Duration ESPERA_TRANSICION_POR_DEFECTO = Duration.ofMillis(1500);

    /** Textos de controles que no se pulsan por defecto. */
    public static final Set<String> DESTRUCTIVOS_POR_DEFECTO =
            Set.of("eliminar", "borrar", "salir", "cerrar sesion");

    /**
     * Crea la configuración y valida sus datos.
     *
     * @throws NullPointerException si algún objeto es null
     * @throws IllegalArgumentException si la profundidad es negativa o una duración no es positiva
     */
    public ConfigExploracion {
        if (profundidadMaxima < 0) {
            throw new IllegalArgumentException("La profundidad no puede ser negativa");
        }
        positiva(tiempoMaximo, "El tiempo máximo");
        positiva(esperaTransicion, "La espera de transición");
        textosDestructivos = Set.copyOf(textosDestructivos);
        entradas = List.copyOf(entradas);
        for (String entrada : entradas) {
            if (entrada.isBlank()) {
                throw new IllegalArgumentException("Una clase de entrada está en blanco");
            }
        }
    }

    /**
     * Crea la configuración sin puntos de entrada alternativos.
     *
     * @param profundidadMaxima cantidad máxima de acciones encadenadas; no negativa
     * @param tiempoMaximo tiempo total máximo; positivo
     * @param textosDestructivos textos de controles que nunca se pulsan
     * @param oculto si la aplicación se explora con las ventanas fuera de pantalla
     * @param esperaTransicion espera a que cambien las ventanas tras una acción; positiva
     */
    public ConfigExploracion(
            int profundidadMaxima,
            Duration tiempoMaximo,
            Set<String> textosDestructivos,
            boolean oculto,
            Duration esperaTransicion) {
        this(
                profundidadMaxima,
                tiempoMaximo,
                textosDestructivos,
                oculto,
                esperaTransicion,
                List.of());
    }

    /**
     * Devuelve una copia que además explora estas clases como puntos de entrada.
     *
     * @param clases clases con {@code main}, con su nombre completo; no puede ser null
     * @return la configuración con esas entradas, en lugar de las anteriores
     */
    public ConfigExploracion conEntradas(List<String> clases) {
        return new ConfigExploracion(
                profundidadMaxima,
                tiempoMaximo,
                textosDestructivos,
                oculto,
                esperaTransicion,
                clases);
    }

    /**
     * Devuelve la configuración por defecto: profundidad 4, 5 minutos, textos destructivos
     * habituales, ventanas ocultas y 1,5 s de espera por transición.
     *
     * @return la configuración por defecto
     */
    public static ConfigExploracion porDefecto() {
        return new ConfigExploracion(
                PROFUNDIDAD_POR_DEFECTO,
                TIEMPO_POR_DEFECTO,
                DESTRUCTIVOS_POR_DEFECTO,
                true,
                ESPERA_TRANSICION_POR_DEFECTO);
    }

    private static void positiva(Duration duracion, String nombre) {
        Objects.requireNonNull(duracion, nombre + " no puede ser null");
        if (duracion.isZero() || duracion.isNegative()) {
            throw new IllegalArgumentException(nombre + " debe ser positivo");
        }
    }
}
