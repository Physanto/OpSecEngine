package io.github.physanto.opsecengine.agente.ganchos;

import io.github.physanto.opsecengine.agente.api.EmisorEventos;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Lógica del gancho de Firestore: arma y emite los eventos {@code FIRESTORE}.
 *
 * <p>Los consejos de ByteBuddy llaman a estos métodos estáticos. No depende de las clases de
 * Firestore: lee la ruta ({@code getPath()}) por reflexión. Solo envía los nombres de los campos
 * escritos, nunca sus valores. Es de fallo seguro y seguro entre hilos.
 */
public final class GestorFirestore {

    private static final ThreadLocal<Boolean> ACTIVO = new ThreadLocal<Boolean>();

    private static volatile EmisorEventos emisor;
    private static volatile Consumer<String> registro =
            new Consumer<String>() {
                @Override
                public void accept(String mensaje) {}
            };

    private GestorFirestore() {}

    /**
     * Conecta el gestor con el emisor de eventos.
     *
     * @param nuevoEmisor emisor de los eventos {@code FIRESTORE}
     * @param nuevoRegistro destino de los errores del gancho
     */
    public static void configurar(EmisorEventos nuevoEmisor, Consumer<String> nuevoRegistro) {
        registro = nuevoRegistro;
        emisor = nuevoEmisor;
    }

    /**
     * Marca el inicio de una operación observada.
     *
     * @return true si es la llamada más externa del hilo
     */
    public static boolean entrar() {
        try {
            if (emisor == null || ACTIVO.get() != null) {
                return false;
            }
            ACTIVO.set(Boolean.TRUE);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Emite el evento de una operación y libera la marca del hilo.
     *
     * @param propietario lo devuelto por {@link #entrar()}
     * @param objeto documento o lote sobre el que se opera
     * @param metodo nombre del método, por ejemplo {@code set}
     * @param args argumentos del método
     * @param error excepción lanzada, o null
     */
    public static void salir(
            boolean propietario, Object objeto, String metodo, Object[] args, Throwable error) {
        if (!propietario) {
            return;
        }
        try {
            emitir(objeto, metodo, args, error);
        } catch (Throwable e) {
            registro.accept("El gancho de Firestore falló: " + e);
        } finally {
            ACTIVO.remove();
        }
    }

    private static void emitir(Object objeto, String metodo, Object[] args, Throwable error) {
        boolean lote = objeto.getClass().getName().endsWith("WriteBatch");
        String operacion = metodo.toLowerCase(Locale.ROOT);
        String ruta = lote ? rutaDeArgumentos(args) : ruta(objeto);
        Map<String, Object> datos = new LinkedHashMap<String, Object>();
        datos.put("operacion", operacion);
        datos.put("ruta", ruta == null ? "" : ruta);
        List<String> campos = campos(args);
        if (campos != null) {
            Map<String, Object> mapa = new LinkedHashMap<String, Object>();
            for (String campo : campos) {
                mapa.put(campo, Boolean.TRUE);
            }
            datos.put("campos", mapa);
        }
        if (lote) {
            datos.put("lote", Boolean.TRUE);
        }
        if (ConfiguracionGanchos.esFirestoreSensible(operacion)) {
            datos.put("sensible", Boolean.TRUE);
        }
        if (error != null) {
            datos.put("error", String.valueOf(error.getMessage()));
        }
        emisor.emitir("FIRESTORE", datos);
    }

    /** Ruta del primer argumento que sea un documento (en un lote), o null. */
    private static String rutaDeArgumentos(Object[] args) {
        for (Object arg : args) {
            String ruta = ruta(arg);
            if (ruta != null) {
                return ruta;
            }
        }
        return null;
    }

    /** {@code getPath()} por reflexión; null si el objeto no lo tiene. */
    static String ruta(Object documento) {
        if (documento == null) {
            return null;
        }
        try {
            Method metodo = documento.getClass().getMethod("getPath");
            Object ruta = metodo.invoke(documento);
            return ruta instanceof String ? (String) ruta : null;
        } catch (Throwable e) {
            return null;
        }
    }

    /** Nombres de los campos del primer {@code Map} argumento, ordenados; null si no hay. */
    static List<String> campos(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof Map) {
                TreeSet<String> claves = new TreeSet<String>();
                for (Object clave : ((Map<?, ?>) arg).keySet()) {
                    claves.add(String.valueOf(clave));
                }
                return Collections.unmodifiableList(new ArrayList<String>(claves));
            }
        }
        return null;
    }
}
