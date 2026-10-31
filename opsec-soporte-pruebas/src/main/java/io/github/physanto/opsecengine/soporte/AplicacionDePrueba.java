package io.github.physanto.opsecengine.soporte;

import java.time.Duration;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Aplicación mínima sin interfaz gráfica para probar el lanzamiento de procesos con el agente.
 *
 * <p>Escribe una línea ({@value #LINEA_INICIO}) en la salida de error y espera sin hacer nada, para
 * dar tiempo a que el agente se conecte. Funciona en entornos headless, como la integración
 * continua. La línea se escribe con {@code java.util.logging}, como el resto del motor.
 *
 * <p>Ejemplo: lanzarla en otro proceso con el agente.
 *
 * <pre>{@code
 * String classpath = String.join(File.pathSeparator, entradas);
 * Process proceso = new ProcessBuilder(
 *         javaActual, "-javaagent:" + jarAgente + "=" + motor.argumentosAgente(),
 *         "-cp", classpath, AplicacionDePrueba.class.getName(), "30").start();
 * }</pre>
 *
 * <p>La clase no se instancia.
 */
public final class AplicacionDePrueba {

    /** Línea que escribe la aplicación al arrancar. */
    public static final String LINEA_INICIO = "AplicacionDePrueba iniciada";

    /** Segundos que espera la aplicación si no se indica otro valor. */
    public static final int ESPERA_POR_DEFECTO_SEGUNDOS = 60;

    private AplicacionDePrueba() {}

    /**
     * Escribe {@value #LINEA_INICIO} y espera antes de terminar.
     *
     * @param argumentos opcionalmente, los segundos de espera como primer argumento; por defecto
     *     {@value #ESPERA_POR_DEFECTO_SEGUNDOS}
     * @throws InterruptedException si se interrumpe la espera
     * @throws NumberFormatException si el primer argumento no es un entero
     */
    public static void main(String[] argumentos) throws InterruptedException {
        Duration espera =
                Duration.ofSeconds(
                        argumentos.length > 0
                                ? Integer.parseInt(argumentos[0])
                                : ESPERA_POR_DEFECTO_SEGUNDOS);
        Logger registro = Logger.getLogger(AplicacionDePrueba.class.getName());
        registro.setUseParentHandlers(false);
        Handler consola = new ConsoleHandler();
        consola.setFormatter(new FormatoLinea());
        registro.addHandler(consola);
        registro.info(LINEA_INICIO);
        consola.flush();
        Thread.sleep(espera.toMillis());
    }

    /** Formato de una sola línea: solo el mensaje. */
    private static final class FormatoLinea extends Formatter {
        @Override
        public String format(LogRecord registro) {
            return registro.getMessage() + System.lineSeparator();
        }
    }
}
