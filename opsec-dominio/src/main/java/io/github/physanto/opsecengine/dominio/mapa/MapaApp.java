package io.github.physanto.opsecengine.dominio.mapa;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Mapa de la aplicación probada: un grafo con ventanas como nodos y transiciones como aristas.
 *
 * <p>Cada transición pertenece a un contexto. Los valores documentados son:
 *
 * <ul>
 *   <li>{@value #SIN_SESION}: sin iniciar sesión;
 *   <li>{@code "rol:<nombre>"}: con la sesión iniciada con un rol del proyecto, por ejemplo {@code
 *       "rol:admin"} (ver {@link #contextoRol(String)});
 *   <li>{@code "entrada:<clase>"}: abriendo directamente una clase con {@code main} o un {@code
 *       JFrame}, por ejemplo {@code "entrada:app.MenuAdmin"} (ver {@link
 *       #contextoEntrada(String)}).
 * </ul>
 *
 * <p>El constructor exige ids de ventana únicos y que cada transición una ventanas del mapa. Es
 * inmutable y seguro entre hilos.
 *
 * @param ventanas ventanas del mapa, con ids únicos; si es null queda vacío, y no admite elementos
 *     null
 * @param transiciones transiciones del mapa, en el orden en que se descubrieron; si es null queda
 *     vacía, y no admite elementos null
 */
public record MapaApp(Set<Ventana> ventanas, List<Transicion> transiciones) {

    /** Contexto de lo que se alcanza sin iniciar sesión. */
    public static final String SIN_SESION = "sin-sesion";

    /** Prefijo de los contextos de rol. */
    public static final String PREFIJO_ROL = "rol:";

    /** Prefijo de los contextos de punto de entrada. */
    public static final String PREFIJO_ENTRADA = "entrada:";

    /**
     * Crea un mapa con copias inmutables de sus colecciones y valida su estructura.
     *
     * @throws NullPointerException si alguna colección contiene elementos null
     * @throws IllegalArgumentException si dos ventanas tienen el mismo id o si una transición
     *     nombra una ventana que no está en el mapa
     */
    public MapaApp {
        ventanas = ventanas == null ? Set.of() : Set.copyOf(ventanas);
        transiciones = transiciones == null ? List.of() : List.copyOf(transiciones);
        Map<String, Ventana> porId = indexar(ventanas);
        for (Transicion transicion : transiciones) {
            if (!porId.containsKey(transicion.origen())
                    || !porId.containsKey(transicion.destino())) {
                throw new IllegalArgumentException(
                        "La transición de "
                                + transicion.origen()
                                + " a "
                                + transicion.destino()
                                + " nombra una ventana que no está en el mapa");
            }
        }
    }

    /**
     * Devuelve las ventanas que se alcanzan desde una ventana siguiendo las transiciones de un
     * contexto.
     *
     * @param ventanaId id de la ventana de partida; no puede ser null
     * @param contexto contexto cuyas transiciones se siguen; no puede ser null
     * @return conjunto inmutable con la ventana de partida y todas las alcanzables por uno o más
     *     pasos, en orden de recorrido en anchura; vacío si la ventana no está en el mapa
     * @throws NullPointerException si {@code ventanaId} o {@code contexto} son null
     */
    public Set<Ventana> alcanzablesDesde(String ventanaId, String contexto) {
        Objects.requireNonNull(ventanaId, "El id de la ventana de partida no puede ser null");
        Objects.requireNonNull(contexto, "El contexto no puede ser null");
        Map<String, Ventana> porId = indexar(ventanas);
        Ventana inicio = porId.get(ventanaId);
        if (inicio == null) {
            return Set.of();
        }
        Set<Ventana> visitadas = new LinkedHashSet<>();
        Deque<Ventana> pendientes = new ArrayDeque<>();
        visitadas.add(inicio);
        pendientes.add(inicio);
        while (!pendientes.isEmpty()) {
            String actual = pendientes.poll().id();
            for (Transicion transicion : transiciones) {
                if (transicion.origen().equals(actual) && transicion.contexto().equals(contexto)) {
                    Ventana destino = porId.get(transicion.destino());
                    if (visitadas.add(destino)) {
                        pendientes.add(destino);
                    }
                }
            }
        }
        return Collections.unmodifiableSet(visitadas);
    }

    /**
     * Devuelve los contextos que aparecen en las transiciones del mapa.
     *
     * @return conjunto inmutable de contextos en el orden en que aparecen por primera vez
     */
    public Set<String> contextos() {
        Set<String> contextos = new LinkedHashSet<>();
        for (Transicion transicion : transiciones) {
            contextos.add(transicion.contexto());
        }
        return Collections.unmodifiableSet(contextos);
    }

    /**
     * Construye el contexto de un rol.
     *
     * @param nombreRol nombre del rol en el proyecto; no puede ser null
     * @return el texto {@code "rol:<nombreRol>"}
     * @throws NullPointerException si {@code nombreRol} es null
     */
    public static String contextoRol(String nombreRol) {
        return PREFIJO_ROL
                + Objects.requireNonNull(nombreRol, "El nombre del rol no puede ser null");
    }

    /**
     * Construye el contexto de un punto de entrada.
     *
     * @param clase nombre completo de la clase de entrada; no puede ser null
     * @return el texto {@code "entrada:<clase>"}
     * @throws NullPointerException si {@code clase} es null
     */
    public static String contextoEntrada(String clase) {
        return PREFIJO_ENTRADA
                + Objects.requireNonNull(clase, "La clase de entrada no puede ser null");
    }

    private static Map<String, Ventana> indexar(Set<Ventana> ventanas) {
        Map<String, Ventana> porId = new HashMap<>();
        for (Ventana ventana : ventanas) {
            if (porId.put(ventana.id(), ventana) != null) {
                throw new IllegalArgumentException(
                        "El mapa tiene ventanas repetidas con el id " + ventana.id());
            }
        }
        return porId;
    }
}
