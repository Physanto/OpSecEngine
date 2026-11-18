package io.github.physanto.opsecengine.agente.ganchos;

import net.bytebuddy.asm.Advice;

/**
 * Consejo de ByteBuddy para {@code PreparedStatement.clearParameters}: olvida los parámetros
 * guardados. El código se copia dentro del método del driver; no se debe llamar directamente.
 */
public final class ConsejoLimpiar {

    private ConsejoLimpiar() {}

    /**
     * Olvida los parámetros de la sentencia.
     *
     * @param statement sentencia preparada
     */
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void salir(@Advice.This Object statement) {
        GestorJdbc.limpiarParametros(statement);
    }
}
