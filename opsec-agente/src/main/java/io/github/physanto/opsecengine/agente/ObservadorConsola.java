package io.github.physanto.opsecengine.agente;

import io.github.physanto.opsecengine.agente.api.EmisorEventos;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Observa la consola de la aplicación probada y emite un evento {@code CONSOLA} por cada línea que
 * escribe en {@code System.out} o {@code System.err}.
 *
 * <p>Reemplaza las dos salidas por envolturas que primero escriben en la salida original, sin
 * cambiar lo que la aplicación muestra, y luego emiten la línea. Debe instalarse en {@code
 * premain}, antes de que la aplicación guarde una referencia a {@code System.out}.
 *
 * <p>Las líneas se decodifican con la misma codificación que usa la salida original. Una línea más
 * larga que {@value #LONGITUD_MAXIMA_LINEA} bytes se recorta. Las envolturas son seguras entre
 * hilos y nunca lanzan excepciones por culpa del agente.
 */
final class ObservadorConsola {

    /** Bytes máximos que se conservan de una línea. */
    static final int LONGITUD_MAXIMA_LINEA = 64 * 1024;

    /** Valor del campo {@code flujo} para {@code System.out}. */
    static final String SALIDA = "SALIDA";

    /** Valor del campo {@code flujo} para {@code System.err}. */
    static final String ERROR = "ERROR";

    private ObservadorConsola() {}

    /**
     * Reemplaza {@code System.out} y {@code System.err} por envolturas observadas.
     *
     * @param emisor emisor de los eventos {@code CONSOLA}
     */
    static void instalar(EmisorEventos emisor) {
        System.setOut(
                envolver(
                        System.out,
                        SALIDA,
                        codificacion("stdout.encoding", "sun.stdout.encoding"),
                        emisor));
        System.setErr(
                envolver(
                        System.err,
                        ERROR,
                        codificacion("stderr.encoding", "sun.stderr.encoding"),
                        emisor));
    }

    /**
     * Crea una envoltura de una salida.
     *
     * @param original salida que sigue recibiendo todo lo que se escribe
     * @param flujo valor del campo {@code flujo}: {@link #SALIDA} o {@link #ERROR}
     * @param codificacion codificación de la salida original
     * @param emisor emisor de los eventos {@code CONSOLA}
     * @return la envoltura, con vaciado automático
     */
    static PrintStream envolver(
            PrintStream original, String flujo, Charset codificacion, EmisorEventos emisor) {
        try {
            return new PrintStream(
                    new SalidaObservada(original, flujo, codificacion, emisor),
                    true,
                    codificacion.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("Codificación no soportada: " + codificacion, e);
        }
    }

    /**
     * Codificación de una salida estándar: la de la primera propiedad definida o la del sistema.
     */
    private static Charset codificacion(String... propiedades) {
        for (String propiedad : propiedades) {
            String nombre = System.getProperty(propiedad);
            if (nombre != null && Charset.isSupported(nombre)) {
                return Charset.forName(nombre);
            }
        }
        return Charset.defaultCharset();
    }

    /** Flujo que copia los bytes a la salida original y emite cada línea completa. */
    static final class SalidaObservada extends OutputStream {

        /** Marca el hilo que está emitiendo, para no observar lo que escribe el propio envío. */
        private static final ThreadLocal<Boolean> EMITIENDO = new ThreadLocal<>();

        private final PrintStream original;
        private final String flujo;
        private final Charset codificacion;
        private final EmisorEventos emisor;
        private final ByteArrayOutputStream linea = new ByteArrayOutputStream();

        SalidaObservada(
                PrintStream original, String flujo, Charset codificacion, EmisorEventos emisor) {
            this.original = original;
            this.flujo = flujo;
            this.codificacion = codificacion;
            this.emisor = emisor;
        }

        @Override
        public synchronized void write(int b) {
            original.write(b);
            acumular(b);
        }

        @Override
        public synchronized void write(byte[] bytes, int desde, int cantidad) {
            original.write(bytes, desde, cantidad);
            for (int i = desde; i < desde + cantidad; i++) {
                acumular(bytes[i]);
            }
        }

        @Override
        public void flush() {
            original.flush();
        }

        private void acumular(int b) {
            if (b == '\n') {
                emitirLinea();
            } else if (linea.size() < LONGITUD_MAXIMA_LINEA) {
                linea.write(b);
            }
        }

        private void emitirLinea() {
            String texto = new String(linea.toByteArray(), codificacion);
            linea.reset();
            if (texto.endsWith("\r")) {
                texto = texto.substring(0, texto.length() - 1);
            }
            if (Boolean.TRUE.equals(EMITIENDO.get())) {
                return;
            }
            EMITIENDO.set(Boolean.TRUE);
            try {
                Map<String, Object> datos = new LinkedHashMap<>();
                datos.put("flujo", flujo);
                datos.put("texto", texto);
                emisor.emitir("CONSOLA", datos);
            } catch (RuntimeException e) {
                RegistroAgente.error("No se pudo observar una línea de la consola: " + e);
            } finally {
                EMITIENDO.remove();
            }
        }
    }
}
