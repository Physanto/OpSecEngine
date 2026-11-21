package io.github.physanto.opsecengine.agente.cobertura;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Doble de {@code org.jacoco.agent.rt.RT}: registra cada volcado y puede fallar a pedido. */
public final class RtFalso {

    /** Valores de {@code reset} con que se pidió cada volcado, en orden. */
    public static final List<Boolean> VOLCADOS = new CopyOnWriteArrayList<>();

    /** Si es true, {@code dump} lanza una excepción. */
    public static volatile boolean fallar;

    private RtFalso() {}

    /** Doble de {@code IAgent}. */
    public interface Agente {

        /**
         * Escribe los datos.
         *
         * @param reset si se reinician los contadores
         */
        void dump(boolean reset);
    }

    /**
     * Devuelve el agente falso.
     *
     * @return el agente
     */
    public static Agente getAgent() {
        return reset -> {
            if (fallar) {
                throw new IllegalStateException("sin archivo de destino");
            }
            VOLCADOS.add(reset);
        };
    }

    /** Deja el doble como estaba. */
    public static void reiniciar() {
        VOLCADOS.clear();
        fallar = false;
    }
}
