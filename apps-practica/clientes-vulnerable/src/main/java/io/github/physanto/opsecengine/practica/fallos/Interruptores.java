package io.github.physanto.opsecengine.practica.fallos;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Indica qué fallos sembrados están activos y cuáles corregidos.
 *
 * <p>Por defecto todos están activos. Se leen de la propiedad del sistema {@value #PROPIEDAD}: una
 * lista de ids separados por comas ({@code F1,F4}) corrige solo esos fallos y {@value #TODOS} los
 * corrige todos. Los ids no distinguen mayúsculas y admiten espacios alrededor. Es inmutable y
 * seguro entre hilos.
 */
public final class Interruptores {

    /** Propiedad del sistema con los fallos que se deben corregir. */
    public static final String PROPIEDAD = "practica.corregir";

    /** Valor de la propiedad que corrige todos los fallos. */
    public static final String TODOS = "todos";

    private final Set<Fallo> corregidos;

    private Interruptores(Set<Fallo> corregidos) {
        this.corregidos = Collections.unmodifiableSet(corregidos);
    }

    /**
     * Devuelve los interruptores con todos los fallos activos (la app vulnerable).
     *
     * @return interruptores sin fallos corregidos
     */
    public static Interruptores todosActivos() {
        return new Interruptores(EnumSet.noneOf(Fallo.class));
    }

    /**
     * Devuelve los interruptores con todos los fallos corregidos.
     *
     * @return interruptores sin fallos activos
     */
    public static Interruptores todosCorregidos() {
        return new Interruptores(EnumSet.allOf(Fallo.class));
    }

    /**
     * Devuelve los interruptores que corrigen solo los fallos dados.
     *
     * @param fallos fallos que se corrigen; los demás quedan activos
     * @return interruptores
     */
    public static Interruptores corrigiendo(Fallo... fallos) {
        EnumSet<Fallo> conjunto = EnumSet.noneOf(Fallo.class);
        Collections.addAll(conjunto, fallos);
        return new Interruptores(conjunto);
    }

    /**
     * Interpreta el valor de la propiedad {@value #PROPIEDAD}.
     *
     * @param valor {@code null} o vacío para no corregir nada, {@value #TODOS} o una lista de ids
     *     separados por comas
     * @return interruptores
     * @throws IllegalArgumentException si la lista contiene un id que no existe
     */
    public static Interruptores desdeTexto(String valor) {
        EnumSet<Fallo> conjunto = EnumSet.noneOf(Fallo.class);
        if (valor == null) {
            return new Interruptores(conjunto);
        }
        for (String parte : valor.split(",", -1)) {
            String id = parte.trim().toUpperCase(Locale.ROOT);
            if (id.isEmpty()) {
                continue;
            }
            if (id.equals(TODOS.toUpperCase(Locale.ROOT))) {
                conjunto = EnumSet.allOf(Fallo.class);
                continue;
            }
            try {
                conjunto.add(Fallo.valueOf(id));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Fallo desconocido en "
                                + PROPIEDAD
                                + ": '"
                                + parte.trim()
                                + "'. Valores válidos: F1 a F6 o "
                                + TODOS
                                + ".",
                        e);
            }
        }
        return new Interruptores(conjunto);
    }

    /**
     * Lee los interruptores de la propiedad del sistema {@value #PROPIEDAD}.
     *
     * @return interruptores
     * @throws IllegalArgumentException si la propiedad contiene un id que no existe
     */
    public static Interruptores desdeSistema() {
        return desdeTexto(System.getProperty(PROPIEDAD));
    }

    /**
     * Indica si un fallo está activo, es decir, si la app se comporta de forma vulnerable.
     *
     * @param fallo fallo que se consulta
     * @return {@code true} si el fallo no fue corregido
     */
    public boolean estaActivo(Fallo fallo) {
        return !corregidos.contains(fallo);
    }

    /**
     * Devuelve los fallos activos.
     *
     * @return conjunto inmutable, en el orden de {@link Fallo}
     */
    public Set<Fallo> activos() {
        return Collections.unmodifiableSet(EnumSet.complementOf(copiaCorregidos()));
    }

    /**
     * Devuelve los fallos corregidos.
     *
     * @return conjunto inmutable, en el orden de {@link Fallo}
     */
    public Set<Fallo> corregidos() {
        return corregidos;
    }

    private EnumSet<Fallo> copiaCorregidos() {
        return corregidos.isEmpty() ? EnumSet.noneOf(Fallo.class) : EnumSet.copyOf(corregidos);
    }

    /** Describe los fallos activos y corregidos, por ejemplo para escribirlo al arrancar. */
    @Override
    public String toString() {
        return "Fallos activos: " + lista(activos()) + "; corregidos: " + lista(corregidos);
    }

    private static String lista(Set<Fallo> fallos) {
        return fallos.isEmpty()
                ? "ninguno"
                : fallos.stream().map(Fallo::name).collect(Collectors.joining(", "));
    }
}
