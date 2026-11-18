package io.github.physanto.opsecengine.agente.ganchos;

import io.github.physanto.opsecengine.agente.api.EmisorEventos;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * Lógica de los ganchos JDBC: arma y emite los eventos {@code SQL}.
 *
 * <p>Los consejos de ByteBuddy se copian dentro de las clases del driver y llaman a estos métodos
 * estáticos, por eso la clase es pública. Es de fallo seguro: ninguna excepción sale de aquí. Una
 * marca por hilo evita eventos duplicados cuando un método del driver llama a otro, y evita
 * recursión cuando el gancho consulta la conexión. Es seguro entre hilos.
 */
public final class GestorJdbc {

    /** Sentencia preparada: su SQL y los parámetros asignados, por posición. */
    private static final class Preparada {
        final String sql;
        final Map<Integer, String> parametros = new TreeMap<Integer, String>();

        Preparada(String sql) {
            this.sql = sql;
        }
    }

    private static final ThreadLocal<Boolean> ACTIVO = new ThreadLocal<Boolean>();
    private static final Map<Object, Preparada> PREPARADAS =
            Collections.synchronizedMap(new WeakHashMap<Object, Preparada>());

    private static volatile EmisorEventos emisor;
    private static volatile Consumer<String> registro =
            new Consumer<String>() {
                @Override
                public void accept(String mensaje) {}
            };

    private GestorJdbc() {}

    /**
     * Conecta el gestor con el emisor de eventos.
     *
     * @param nuevoEmisor emisor de los eventos {@code SQL}; no puede ser null
     * @param nuevoRegistro destino de los errores del gancho; no puede ser null
     */
    public static void configurar(EmisorEventos nuevoEmisor, Consumer<String> nuevoRegistro) {
        registro = nuevoRegistro;
        emisor = nuevoEmisor;
    }

    /**
     * Marca el inicio de una ejecución observada.
     *
     * @return true si esta llamada es la primera del hilo y debe emitir el evento al terminar
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
     * Emite el evento {@code SQL} de una ejecución y libera la marca del hilo.
     *
     * @param propietario lo devuelto por {@link #entrar()}
     * @param statement sentencia que ejecutó
     * @param args argumentos del método ejecutado
     * @param retorno valor devuelto, o null si lanzó una excepción
     * @param error excepción lanzada, o null
     */
    public static void salirEjecucion(
            boolean propietario, Object statement, Object[] args, Object retorno, Throwable error) {
        if (!propietario) {
            return;
        }
        try {
            emitir(statement, args, retorno, error);
        } catch (Throwable e) {
            registro.accept("El gancho JDBC falló: " + e);
        } finally {
            ACTIVO.remove();
        }
    }

    /**
     * Recuerda el SQL de una sentencia preparada recién creada.
     *
     * @param preparada objeto devuelto por {@code Connection.prepareStatement}
     * @param args argumentos de {@code prepareStatement}; el primero es el SQL
     */
    public static void preparada(Object preparada, Object[] args) {
        try {
            if (preparada != null && args.length > 0 && args[0] instanceof String) {
                PREPARADAS.put(preparada, new Preparada((String) args[0]));
            }
        } catch (Throwable e) {
            registro.accept("El gancho JDBC falló al preparar: " + e);
        }
    }

    /**
     * Guarda el valor de un parámetro de una sentencia preparada.
     *
     * @param statement sentencia preparada
     * @param metodo nombre del setter, por ejemplo {@code setString}
     * @param args argumentos del setter; el primero es la posición del parámetro
     */
    public static void parametro(Object statement, String metodo, Object[] args) {
        try {
            Preparada preparada = PREPARADAS.get(statement);
            if (preparada == null || args.length < 2 || !(args[0] instanceof Integer)) {
                return;
            }
            Object valor = "setNull".equals(metodo) ? null : args[1];
            synchronized (preparada) {
                preparada.parametros.put((Integer) args[0], textoDe(valor));
            }
        } catch (Throwable e) {
            registro.accept("El gancho JDBC falló al leer un parámetro: " + e);
        }
    }

    /**
     * Borra los parámetros de una sentencia preparada.
     *
     * @param statement sentencia preparada
     */
    public static void limpiarParametros(Object statement) {
        try {
            Preparada preparada = PREPARADAS.get(statement);
            if (preparada != null) {
                synchronized (preparada) {
                    preparada.parametros.clear();
                }
            }
        } catch (Throwable e) {
            registro.accept("El gancho JDBC falló al limpiar parámetros: " + e);
        }
    }

    private static void emitir(Object statement, Object[] args, Object retorno, Throwable error)
            throws Exception {
        String sql;
        String tipo;
        List<String> parametros = null;
        if (args.length > 0 && args[0] instanceof String) {
            sql = (String) args[0];
            tipo = "STATEMENT";
        } else if (statement instanceof PreparedStatement) {
            Preparada preparada = PREPARADAS.get(statement);
            if (preparada == null) {
                return;
            }
            sql = preparada.sql;
            tipo = "PREPARED";
            synchronized (preparada) {
                parametros = new ArrayList<String>(preparada.parametros.values());
            }
        } else {
            return;
        }
        Map<String, Object> datos = new LinkedHashMap<String, Object>();
        datos.put("sql", sql);
        datos.put("tipoSentencia", tipo);
        String operacion = operacion(sql);
        if (operacion != null) {
            datos.put("operacion", operacion);
            if (ConfiguracionGanchos.esSqlSensible(operacion)) {
                datos.put("sensible", Boolean.TRUE);
            }
        }
        if (parametros != null) {
            datos.put("parametros", parametros);
        }
        if (retorno instanceof Integer || retorno instanceof Long) {
            datos.put("filasAfectadas", retorno);
        }
        String conexion = conexion(statement);
        if (conexion != null) {
            datos.put("conexion", conexion);
        }
        if (error != null) {
            datos.put("error", String.valueOf(error.getMessage()));
        }
        emisor.emitir("SQL", datos);
    }

    /** Primera palabra de la sentencia en mayúsculas, o null si no hay. */
    static String operacion(String sql) {
        String limpio = sql.trim();
        int fin = 0;
        while (fin < limpio.length() && Character.isLetter(limpio.charAt(fin))) {
            fin++;
        }
        return fin == 0 ? null : limpio.substring(0, fin).toUpperCase(Locale.ROOT);
    }

    /** URL JDBC de la conexión de la sentencia, sin credenciales; null si no se puede leer. */
    private static String conexion(Object statement) {
        try {
            Connection conexion = ((Statement) statement).getConnection();
            return conexion == null ? null : sinCredenciales(conexion.getMetaData().getURL());
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * Quita usuario y contraseña de una URL JDBC: {@code user=}, {@code password=} y {@code u:p@}.
     */
    static String sinCredenciales(String url) {
        if (url == null) {
            return null;
        }
        String resultado = url.replaceAll("(?i)([?;&,])(user|password|pwd|pass)=[^;&,]*", "$1");
        resultado = resultado.replaceAll("//[^/@]*@", "//");
        return resultado.replaceAll("[?;&,]+$", "").replaceAll("([?;&,])[;&,]+", "$1");
    }

    private static String textoDe(Object valor) {
        if (valor == null) {
            return "NULL";
        }
        if (valor instanceof CharSequence
                || valor instanceof Number
                || valor instanceof Boolean
                || valor instanceof Character
                || valor instanceof java.util.Date
                || valor.getClass().getName().startsWith("java.time.")) {
            return String.valueOf(valor);
        }
        return "<" + valor.getClass().getSimpleName() + ">";
    }
}
