package io.github.physanto.opsecengine.agente.ganchos;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

/**
 * Consejo de ByteBuddy para {@code Connection.prepareStatement}: recuerda el SQL de la sentencia
 * preparada. El código se copia dentro del método del driver; no se debe llamar directamente.
 */
public final class ConsejoPreparar {

    private ConsejoPreparar() {}

    /**
     * Registra la sentencia preparada.
     *
     * @param preparada objeto devuelto por el método
     * @param args argumentos del método; el primero es el SQL
     */
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void salir(
            @Advice.Return(typing = Assigner.Typing.DYNAMIC) Object preparada,
            @Advice.AllArguments Object[] args) {
        GestorJdbc.preparada(preparada, args);
    }
}
