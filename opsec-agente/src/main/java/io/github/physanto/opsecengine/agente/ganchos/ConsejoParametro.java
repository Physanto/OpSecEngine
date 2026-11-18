package io.github.physanto.opsecengine.agente.ganchos;

import net.bytebuddy.asm.Advice;

/**
 * Consejo de ByteBuddy para los {@code setXxx(int, ...)} de {@code PreparedStatement}: guarda el
 * valor de cada parámetro. El código se copia dentro del método del driver; no se debe llamar
 * directamente.
 */
public final class ConsejoParametro {

    private ConsejoParametro() {}

    /**
     * Guarda el parámetro asignado.
     *
     * @param statement sentencia preparada
     * @param metodo nombre del método, por ejemplo {@code setString}
     * @param args argumentos del método
     */
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void salir(
            @Advice.This Object statement,
            @Advice.Origin("#m") String metodo,
            @Advice.AllArguments Object[] args) {
        GestorJdbc.parametro(statement, metodo, args);
    }
}
