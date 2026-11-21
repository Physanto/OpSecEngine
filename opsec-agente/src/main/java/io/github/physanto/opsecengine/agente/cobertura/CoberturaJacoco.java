package io.github.physanto.opsecengine.agente.cobertura;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Pide al agente de JaCoCo que escriba en su archivo los datos de cobertura reunidos hasta ahora.
 *
 * <p>El motor carga el agente de JaCoCo ({@code -javaagent:jacocoagent.jar=destfile=...}) antes que
 * el suyo. JaCoCo escribe el archivo al terminar la JVM, pero si la aplicación se detiene a la
 * fuerza esa escritura no ocurre. Por eso el agente del motor llama a {@link #volcar()} al recibir
 * {@code CERRAR}, cuando la aplicación todavía está viva: así los datos de la sesión quedan en el
 * archivo aunque después el proceso se detenga. Como JaCoCo reinicia sus contadores después de
 * escribir y añade al archivo, la escritura final al salir no duplica nada.
 *
 * <p>Todo se hace por reflexión sobre {@code org.jacoco.agent.rt.RT}, que existe solo si JaCoCo
 * está cargado. Sin JaCoCo no hace nada. Nunca lanza excepciones a la aplicación. Es seguro entre
 * hilos.
 */
public final class CoberturaJacoco {

    /** Clase de JaCoCo que da acceso a su agente en ejecución. */
    static final String CLASE_RT = "org.jacoco.agent.rt.RT";

    private final String claseRt;
    private final Consumer<String> errores;

    /**
     * Crea el volcador para el agente de JaCoCo real.
     *
     * @param errores recibe los problemas al escribir los datos; no puede ser null
     */
    public CoberturaJacoco(Consumer<String> errores) {
        this(CLASE_RT, errores);
    }

    /**
     * Crea el volcador para una clase de acceso distinta; sirve para las pruebas.
     *
     * @param claseRt nombre de la clase con el método estático {@code getAgent()}, que devuelve un
     *     objeto con {@code dump(boolean)}
     * @param errores recibe los problemas al escribir los datos; no puede ser null
     */
    public CoberturaJacoco(String claseRt, Consumer<String> errores) {
        this.claseRt = claseRt;
        this.errores = errores;
    }

    /**
     * Escribe los datos de cobertura reunidos hasta ahora y reinicia los contadores.
     *
     * @return {@code true} si JaCoCo está cargado y escribió los datos; {@code false} si no está
     *     cargado o falló (un fallo se informa a {@code errores})
     */
    public boolean volcar() {
        Class<?> rt;
        try {
            rt = Class.forName(claseRt, true, ClassLoader.getSystemClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
        try {
            Method obtener = rt.getMethod("getAgent");
            Object agente = obtener.invoke(null);
            // El método se toma de la interfaz pública, no de la clase interna del agente.
            Method volcar = obtener.getReturnType().getMethod("dump", boolean.class);
            volcar.invoke(agente, Boolean.TRUE);
            return true;
        } catch (InvocationTargetException e) {
            errores.accept("JaCoCo no pudo escribir la cobertura: " + e.getCause());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            errores.accept("No se pudo volcar la cobertura de JaCoCo: " + e);
        }
        return false;
    }
}
