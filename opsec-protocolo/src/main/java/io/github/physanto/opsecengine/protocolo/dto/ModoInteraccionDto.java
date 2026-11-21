package io.github.physanto.opsecengine.protocolo.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Modo de interacción que el motor pide al agente en un mensaje {@code MODO_INTERACCION}.
 *
 * <p>En {@link #INSPECCIONAR} el agente resalta el componente bajo el cursor y, cuando el usuario
 * hace clic, emite un evento {@code COMPONENTE_INSPECCIONADO} sin que el clic llegue a la
 * aplicación. En {@link #GRABAR} deja la aplicación funcionando con normalidad y emite un evento
 * {@code ACCION_GRABADA} por cada acción del usuario. {@link #NINGUNO} desactiva el modo activo.
 *
 * <p>Es inmutable y seguro entre hilos.
 */
public final class ModoInteraccionDto {

    /** Resalta el componente bajo el cursor y devuelve su localizador al hacer clic. */
    public static final String INSPECCIONAR = "INSPECCIONAR";

    /** Registra las acciones del usuario como pasos. */
    public static final String GRABAR = "GRABAR";

    /** Desactiva el modo de inspección o de grabación. */
    public static final String NINGUNO = "NINGUNO";

    private final String modo;

    /**
     * Crea el pedido de un modo.
     *
     * @param modo uno de {@link #INSPECCIONAR}, {@link #GRABAR} o {@link #NINGUNO}; no puede ser
     *     null
     * @throws IllegalArgumentException si el modo no es ninguno de los tres
     */
    @JsonCreator
    public ModoInteraccionDto(@JsonProperty("modo") String modo) {
        Objects.requireNonNull(modo, "El modo de interacción no puede ser null");
        if (!INSPECCIONAR.equals(modo) && !GRABAR.equals(modo) && !NINGUNO.equals(modo)) {
            throw new IllegalArgumentException("Modo de interacción desconocido: " + modo);
        }
        this.modo = modo;
    }

    /**
     * Devuelve el modo pedido.
     *
     * @return {@link #INSPECCIONAR}, {@link #GRABAR} o {@link #NINGUNO}
     */
    public String getModo() {
        return modo;
    }

    /**
     * Compara este pedido con otro objeto.
     *
     * @param otro objeto a comparar
     * @return {@code true} si es un pedido del mismo modo
     */
    @Override
    public boolean equals(Object otro) {
        return this == otro
                || otro instanceof ModoInteraccionDto
                        && modo.equals(((ModoInteraccionDto) otro).modo);
    }

    /**
     * Calcula el hash a partir del modo.
     *
     * @return hash del pedido
     */
    @Override
    public int hashCode() {
        return modo.hashCode();
    }

    /**
     * Devuelve una representación legible del pedido.
     *
     * @return texto con el modo
     */
    @Override
    public String toString() {
        return "ModoInteraccionDto[modo=" + modo + "]";
    }
}
