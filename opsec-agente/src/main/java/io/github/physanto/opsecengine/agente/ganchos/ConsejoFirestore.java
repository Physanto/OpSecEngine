package io.github.physanto.opsecengine.agente.ganchos;

import net.bytebuddy.asm.Advice;

/**
 * Consejo de ByteBuddy para las operaciones de {@code DocumentReference} y {@code WriteBatch}:
 * emite el evento {@code FIRESTORE} al terminar, con éxito o con excepción. El código se copia
 * dentro del método de Firestore; no se debe llamar directamente.
 */
public final class ConsejoFirestore {

    private ConsejoFirestore() {}

    /**
     * Marca el inicio de la operación.
     *
     * @return true si es la llamada más externa del hilo
     */
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static boolean entrar() {
        return GestorFirestore.entrar();
    }

    /**
     * Emite el evento al terminar.
     *
     * @param propietario valor devuelto por {@link #entrar()}
     * @param objeto documento o lote
     * @param metodo nombre del método
     * @param args argumentos del método
     * @param error excepción lanzada, o null
     */
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void salir(
            @Advice.Enter boolean propietario,
            @Advice.This Object objeto,
            @Advice.Origin("#m") String metodo,
            @Advice.AllArguments Object[] args,
            @Advice.Thrown Throwable error) {
        GestorFirestore.salir(propietario, objeto, metodo, args, error);
    }
}
