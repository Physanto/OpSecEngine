package io.github.physanto.opsecengine.agente.ganchos;

import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isSubTypeOf;
import static net.bytebuddy.matcher.ElementMatchers.isSynthetic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.github.physanto.opsecengine.agente.api.EmisorEventos;
import io.github.physanto.opsecengine.agente.api.Gancho;
import io.github.physanto.opsecengine.protocolo.dto.ConfigGanchosDto;
import java.lang.instrument.Instrumentation;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.function.Consumer;
import javax.sql.DataSource;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Gancho que observa las sentencias JDBC de la aplicación y emite un evento {@code SQL} por cada
 * ejecución.
 *
 * <p>Instrumenta, con ByteBuddy, las clases que implementan {@link Statement}, {@link
 * PreparedStatement} y {@link Connection}:
 *
 * <ul>
 *   <li>{@code execute}, {@code executeQuery}, {@code executeUpdate} y {@code executeLargeUpdate}
 *       de {@code Statement} con el SQL como argumento: evento con {@code tipoSentencia=STATEMENT}.
 *   <li>{@code Driver.connect}, {@code DataSource.getConnection} y las ejecuciones: además provocan
 *       los fallos inyectados {@code SQL_CONEXION} (las dos primeras) y {@code SQL_EJECUTAR} (ver
 *       {@link InyectorFallos}).
 *   <li>{@code Connection.prepareStatement}: recuerda el SQL; los {@code setXxx(int, ...)} guardan
 *       los parámetros y el {@code execute*()} sin argumentos emite el evento con {@code
 *       tipoSentencia=PREPARED} y {@code parametros}.
 * </ul>
 *
 * <p>El evento lleva {@code sql}, {@code tipoSentencia}, {@code operacion}, {@code filasAfectadas}
 * si el método devolvió un número, {@code conexion} (URL sin credenciales), {@code error} si falló,
 * y {@code sensible=true} si la operación está en la configuración vigente ({@link
 * ConfiguracionGanchos}).
 *
 * <p>Es de fallo seguro: si la instalación falla se registra y la aplicación sigue sin gancho. Las
 * clases que carga un cargador que no ve el jar del agente no se observan.
 */
public final class GanchoJdbc implements Gancho {

    /** Identificador del gancho. */
    public static final String ID = "jdbc";

    private final Consumer<String> registro;

    /**
     * Crea el gancho.
     *
     * @param registro destino de los errores del gancho; no puede ser null
     */
    public GanchoJdbc(Consumer<String> registro) {
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
     * Instala la instrumentación; debe llamarse antes de que la aplicación cargue su driver.
     *
     * @param inst instrumentación de la JVM
     * @param emisor emisor de los eventos {@code SQL}
     * @param config configuración inicial; la vigente se lee de {@link ConfiguracionGanchos}
     */
    @Override
    public void instalar(Instrumentation inst, EmisorEventos emisor, ConfigGanchosDto config) {
        try {
            ConfiguracionGanchos.reemplazar(config);
            GestorJdbc.configurar(emisor, registro);
            InyectorFallos.configurar(emisor, registro);
            String paqueteAgente = paqueteRaizDelAgente();
            new AgentBuilder.Default()
                    .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                    .with(AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                    .with(AgentBuilder.TypeStrategy.Default.REDEFINE)
                    .ignore(
                            nameStartsWith("net.bytebuddy.")
                                    .or(nameStartsWith("java."))
                                    .or(nameStartsWith("javax."))
                                    .or(nameStartsWith("jdk."))
                                    .or(nameStartsWith("sun."))
                                    .or(nameStartsWith(paqueteAgente))
                                    .or(isSynthetic()))
                    .type(
                            not(isInterface())
                                    .and(
                                            isSubTypeOf(Statement.class)
                                                    .or(isSubTypeOf(Connection.class))
                                                    .or(isSubTypeOf(Driver.class))
                                                    .or(isSubTypeOf(DataSource.class))))
                    .transform((builder, tipo, cargador, modulo, dominio) -> {
                        if (tipo.isAssignableTo(Driver.class)) {
                            builder =
                                    builder.visit(
                                            Advice.to(ConsejoFallo.Conexion.class)
                                                    .on(
                                                            named("connect")
                                                                    .and(isPublic())
                                                                    .and(not(isAbstract()))));
                        }
                        if (tipo.isAssignableTo(DataSource.class)) {
                            builder =
                                    builder.visit(
                                            Advice.to(ConsejoFallo.Conexion.class)
                                                    .on(
                                                            named("getConnection")
                                                                    .and(isPublic())
                                                                    .and(not(isAbstract()))));
                        }
                        if (tipo.isAssignableTo(Connection.class)) {
                            builder =
                                    builder.visit(
                                            Advice.to(ConsejoPreparar.class)
                                                    .on(
                                                            named("prepareStatement")
                                                                    .and(isPublic())
                                                                    .and(not(isAbstract()))));
                        }
                        if (tipo.isAssignableTo(Statement.class)) {
                            builder =
                                    builder.visit(
                                                    Advice.to(ConsejoEjecucion.class)
                                                            .on(ejecuciones()))
                                            .visit(
                                                    Advice.to(ConsejoFallo.Sql.class)
                                                            .on(ejecuciones()));
                        }
                        if (tipo.isAssignableTo(PreparedStatement.class)) {
                            builder =
                                    builder.visit(
                                                    Advice.to(ConsejoParametro.class)
                                                            .on(parametros()))
                                            .visit(
                                                    Advice.to(ConsejoLimpiar.class)
                                                            .on(
                                                                    named("clearParameters")
                                                                            .and(isPublic())
                                                                            .and(
                                                                                    not(
                                                                                            isAbstract()))));
                        }
                        return builder;
                    })
                    .installOn(inst);
        } catch (Throwable e) {
            registro.accept("No se pudo instalar el gancho JDBC: " + e);
        }
    }

    /** Métodos que ejecutan una sentencia. */
    private static ElementMatcher.Junction<MethodDescription> ejecuciones() {
        return named("execute")
                .or(named("executeQuery"))
                .or(named("executeUpdate"))
                .or(named("executeLargeUpdate"))
                .and(isPublic())
                .and(not(isAbstract()));
    }

    /** Métodos {@code setXxx(int, valor...)} que asignan un parámetro. */
    private static ElementMatcher.Junction<MethodDescription> parametros() {
        return nameStartsWith("set")
                .and(takesArgument(0, int.class))
                .and(takesArguments(2).or(takesArguments(3)).or(takesArguments(4)))
                .and(isPublic())
                .and(not(isAbstract()));
    }

    /** Paquete raíz del agente (sin el sufijo {@code .ganchos}), también si está reubicado. */
    private static String paqueteRaizDelAgente() {
        String nombre = GanchoJdbc.class.getPackage().getName();
        return nombre.substring(0, nombre.lastIndexOf('.'));
    }
}
