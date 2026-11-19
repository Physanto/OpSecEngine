package io.github.physanto.opsecengine.agente.ganchos;

import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import io.github.physanto.opsecengine.agente.api.EmisorEventos;
import io.github.physanto.opsecengine.agente.api.Gancho;
import io.github.physanto.opsecengine.protocolo.dto.ConfigGanchosDto;
import java.lang.instrument.Instrumentation;
import java.util.function.Consumer;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

/**
 * Gancho que observa las operaciones de Firestore y emite un evento {@code FIRESTORE} por cada una.
 *
 * <p>Instrumenta por nombre, sin depender de Firestore al compilar: {@code
 * com.google.cloud.firestore.DocumentReference} ({@code set}, {@code update}, {@code delete},
 * {@code create}, {@code get}) y {@code com.google.cloud.firestore.WriteBatch} ({@code set}, {@code
 * update}, {@code delete}, {@code create}, {@code commit}). El evento lleva {@code operacion},
 * {@code ruta}, {@code campos} (solo los nombres, nunca los valores), {@code lote}, {@code error} y
 * {@code sensible}. Si las clases no existen en la aplicación no hace nada.
 */
public final class GanchoFirestore implements Gancho {

    /** Identificador del gancho. */
    public static final String ID = "firestore";

    private static final String DOCUMENTO = "com.google.cloud.firestore.DocumentReference";
    private static final String LOTE = "com.google.cloud.firestore.WriteBatch";

    private final Consumer<String> registro;

    /**
     * Crea el gancho.
     *
     * @param registro destino de los errores del gancho; no puede ser null
     */
    public GanchoFirestore(Consumer<String> registro) {
        this.registro = registro;
    }

    /**
     * Devuelve el identificador del gancho.
     *
     * @return {@value #ID}
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Instala la instrumentación.
     *
     * @param inst instrumentación de la JVM
     * @param emisor emisor de los eventos {@code FIRESTORE}
     * @param config configuración inicial; la vigente se lee de {@link ConfiguracionGanchos}
     */
    @Override
    public void instalar(Instrumentation inst, EmisorEventos emisor, ConfigGanchosDto config) {
        try {
            GestorFirestore.configurar(emisor, registro);
            new AgentBuilder.Default()
                    .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                    .with(AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                    .with(AgentBuilder.TypeStrategy.Default.REDEFINE)
                    .ignore(nameStartsWith("net.bytebuddy.").or(nameStartsWith("java.")))
                    .type(named(DOCUMENTO).or(named(LOTE)))
                    .transform((builder, tipo, cargador, modulo, dominio) ->
                            builder.visit(
                                    Advice.to(ConsejoFirestore.class)
                                            .on(
                                                    named("set")
                                                            .or(named("update"))
                                                            .or(named("delete"))
                                                            .or(named("create"))
                                                            .or(named("get"))
                                                            .or(named("commit"))
                                                            .and(isPublic())
                                                            .and(not(isAbstract())))))
                    .installOn(inst);
        } catch (Throwable e) {
            registro.accept("No se pudo instalar el gancho de Firestore: " + e);
        }
    }
}
