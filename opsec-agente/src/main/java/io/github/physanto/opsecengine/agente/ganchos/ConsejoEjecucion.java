package io.github.physanto.opsecengine.agente.ganchos;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

/**
 * Consejo de ByteBuddy para {@code Statement.execute*}: emite el evento {@code SQL} al terminar,
 * con éxito o con excepción. El código se copia dentro del método del driver; no se debe llamar
 * directamente.
 */
public final class ConsejoEjecucion {

    private ConsejoEjecucion() {}

    /**
     * Marca el inicio de la ejecución.
     *
     * @return true si esta es la llamada más externa del hilo
     */
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static boolean entrar() {
        return GestorJdbc.entrar();
    }

    /**
     * Emite el evento al terminar el método.
     *
     * @param propietario valor devuelto por {@link #entrar()}
     * @param statement sentencia que ejecuta
     * @param args argumentos del método
     * @param retorno valor devuelto, o null
     * @param error excepción lanzada, o null
     */
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void salir(
            @Advice.Enter boolean propietario,
            @Advice.This Object statement,
            @Advice.AllArguments Object[] args,
            @Advice.Return(typing = Assigner.Typing.DYNAMIC) Object retorno,
            @Advice.Thrown Throwable error) {
        GestorJdbc.salirEjecucion(propietario, statement, args, retorno, error);
    }
}
