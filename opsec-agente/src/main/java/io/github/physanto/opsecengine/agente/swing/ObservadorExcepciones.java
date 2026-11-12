package io.github.physanto.opsecengine.agente.swing;

import io.github.physanto.opsecengine.agente.api.EmisorEventos;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Observa las excepciones que nadie captura, en el EDT y en los demás hilos de la aplicación, y
 * emite por cada una un evento {@code EXCEPCION} con {@code capturada=false}.
 *
 * <p>Se instala como manejador por defecto de excepciones no capturadas ({@link
 * Thread#setDefaultUncaughtExceptionHandler}). El EDT le entrega las excepciones de los
 * escuchadores y de las tareas de {@code invokeLater}, así que también cubre la interfaz. Después
 * de emitir el evento pasa la excepción al manejador que había antes; si no había ninguno, escribe
 * la pila en {@code System.err} como lo hace la JVM, para que la aplicación muestre lo mismo que
 * sin el agente.
 *
 * <p>No ve las excepciones de un hilo que tenga su propio manejador, ni las que ocurran si la
 * aplicación reemplaza después el manejador por defecto. Las excepciones que lanza una acción del
 * agente las informa el propio adaptador, porque el agente las recibe antes que el manejador.
 *
 * <p>Es seguro entre hilos y nunca lanza excepciones hacia la aplicación.
 */
public final class ObservadorExcepciones implements Thread.UncaughtExceptionHandler {

    /** Líneas de pila que se envían como máximo en un evento {@code EXCEPCION}. */
    static final int MAXIMO_LINEAS_PILA = 30;

    private final EmisorEventos emisor;
    private final Thread.UncaughtExceptionHandler anterior;
    private final Consumer<String> registro;

    /**
     * Crea el observador sin instalarlo.
     *
     * @param emisor emisor de los eventos; no puede ser null
     * @param anterior manejador al que se pasa cada excepción después de emitir el evento; null
     *     para escribir la pila en {@code System.err}
     * @param registro destino de los mensajes propios del agente si falla la emisión; no puede ser
     *     null
     */
    ObservadorExcepciones(
            EmisorEventos emisor,
            Thread.UncaughtExceptionHandler anterior,
            Consumer<String> registro) {
        this.emisor = emisor;
        this.anterior = anterior;
        this.registro = registro;
    }

    /**
     * Instala el observador como manejador por defecto, encadenado con el que hubiera.
     *
     * @param emisor emisor de los eventos {@code EXCEPCION}; no puede ser null
     * @param registro destino de los mensajes propios del agente; no puede ser null
     * @return el observador instalado
     * @throws SecurityException si un administrador de seguridad impide cambiar el manejador
     */
    public static ObservadorExcepciones instalar(EmisorEventos emisor, Consumer<String> registro) {
        ObservadorExcepciones observador =
                new ObservadorExcepciones(
                        emisor, Thread.getDefaultUncaughtExceptionHandler(), registro);
        Thread.setDefaultUncaughtExceptionHandler(observador);
        return observador;
    }

    /**
     * Emite el evento {@code EXCEPCION} y pasa la excepción al manejador anterior.
     *
     * @param hilo hilo donde ocurrió la excepción
     * @param excepcion excepción que nadie capturó
     */
    @Override
    public void uncaughtException(Thread hilo, Throwable excepcion) {
        try {
            emisor.emitir("EXCEPCION", datos(excepcion, hilo.getName(), false));
        } catch (RuntimeException e) {
            registro.accept("No se pudo informar una excepción de la aplicación: " + e);
        }
        if (anterior != null) {
            anterior.uncaughtException(hilo, excepcion);
        } else {
            System.err.print("Exception in thread \"" + hilo.getName() + "\" ");
            excepcion.printStackTrace(System.err);
        }
    }

    /**
     * Arma los datos de un evento {@code EXCEPCION} según el catálogo de eventos.
     *
     * @param excepcion excepción
     * @param hilo nombre del hilo donde ocurrió
     * @param capturada si la aplicación la capturó
     * @return los campos {@code clase}, {@code mensaje}, {@code hilo}, {@code capturada} y {@code
     *     pila} (como máximo {@value #MAXIMO_LINEAS_PILA} líneas)
     */
    static Map<String, Object> datos(Throwable excepcion, String hilo, boolean capturada) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("clase", excepcion.getClass().getName());
        datos.put("mensaje", excepcion.getMessage());
        datos.put("hilo", hilo);
        datos.put("capturada", capturada);
        List<String> pila = new ArrayList<>();
        for (StackTraceElement elemento : excepcion.getStackTrace()) {
            if (pila.size() == MAXIMO_LINEAS_PILA) {
                break;
            }
            pila.add(elemento.toString());
        }
        datos.put("pila", pila);
        return datos;
    }
}
