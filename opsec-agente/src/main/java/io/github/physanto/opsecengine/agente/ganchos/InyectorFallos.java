package io.github.physanto.opsecengine.agente.ganchos;

import io.github.physanto.opsecengine.agente.api.EmisorEventos;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Decide si un punto de la aplicación debe fallar y crea la excepción que se lanza.
 *
 * <p>Los fallos vienen de {@link ConfiguracionGanchos} con el formato {@code
 * <punto>:<clase-excepcion>[:<mensaje>]}. Los consejos de ByteBuddy llaman a {@link #pendiente} al
 * entrar a un método instrumentado y, si devuelve una excepción, la lanzan. Es de fallo seguro: si
 * algo sale mal aquí no se inyecta nada. Seguro entre hilos.
 */
public final class InyectorFallos {

    /** Punto de conexión SQL. */
    public static final String SQL_CONEXION = "SQL_CONEXION";

    /** Punto de ejecución de sentencias SQL. */
    public static final String SQL_EJECUTAR = "SQL_EJECUTAR";

    /** Punto de escritura en Firestore. */
    public static final String FIRESTORE_ESCRIBIR = "FIRESTORE_ESCRIBIR";

    private static volatile EmisorEventos emisor;
    private static volatile Consumer<String> registro =
            new Consumer<String>() {
                @Override
                public void accept(String mensaje) {}
            };

    private InyectorFallos() {}

    /**
     * Conecta el inyector con el emisor de eventos.
     *
     * @param nuevoEmisor emisor del evento {@code ESTADO}
     * @param nuevoRegistro destino de los errores
     */
    public static void configurar(EmisorEventos nuevoEmisor, Consumer<String> nuevoRegistro) {
        registro = nuevoRegistro;
        emisor = nuevoEmisor;
    }

    /**
     * Devuelve la excepción que hay que lanzar en un punto, si hay un fallo configurado para él, y
     * emite el evento {@code ESTADO} con {@code fallo=<punto>}.
     *
     * @param punto punto de inyección, por ejemplo {@link #SQL_CONEXION}
     * @return la excepción, o null si el punto no está activo
     */
    public static Throwable pendiente(String punto) {
        try {
            for (String fallo : ConfiguracionGanchos.actual().getFallosInyectados()) {
                String[] partes = fallo.split(":", 3);
                if (partes.length >= 2 && partes[0].trim().equals(punto)) {
                    String mensaje = partes.length == 3 ? partes[2] : "Fallo inyectado en " + punto;
                    Throwable excepcion = crear(partes[1].trim(), mensaje);
                    avisar(punto, excepcion);
                    return excepcion;
                }
            }
        } catch (Throwable e) {
            registro.accept("El inyector de fallos falló: " + e);
        }
        return null;
    }

    /**
     * Crea la excepción con un constructor {@code (String)}; si no se puede, una RuntimeException.
     */
    static Throwable crear(String clase, String mensaje) {
        try {
            Class<?> tipo = Class.forName(clase, true, ClassLoader.getSystemClassLoader());
            if (Throwable.class.isAssignableFrom(tipo)) {
                return (Throwable) tipo.getConstructor(String.class).newInstance(mensaje);
            }
        } catch (Throwable e) {
            // se usa la excepción genérica
        }
        return new RuntimeException(mensaje);
    }

    private static void avisar(String punto, Throwable excepcion) {
        EmisorEventos actual = emisor;
        if (actual == null) {
            return;
        }
        Map<String, Object> datos = new LinkedHashMap<String, Object>();
        datos.put("fallo", punto);
        datos.put("excepcion", excepcion.getClass().getName());
        actual.emitir("ESTADO", datos);
    }
}
