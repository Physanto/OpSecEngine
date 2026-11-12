package io.github.physanto.opsecengine.agente;

/**
 * Argumentos del agente, leídos del texto que sigue a {@code -javaagent:<jar>=}.
 *
 * <p>El formato es una lista de pares {@code clave=valor} separados por comas, por ejemplo {@code
 * puerto=51234,token=abc}. Son obligatorios {@code puerto} (entre 1 y 65535) y {@code token} (no
 * vacío). Las claves desconocidas se ignoran para que versiones nuevas del motor puedan agregar
 * opciones. El argumento opcional {@code oculto=true} impide el foco y desplaza las ventanas fuera
 * de las pantallas; por defecto es false. Es inmutable.
 */
final class ArgumentosAgente {

    private final int puerto;
    private final String token;
    private final boolean oculto;

    private ArgumentosAgente(int puerto, String token, boolean oculto) {
        this.puerto = puerto;
        this.token = token;
        this.oculto = oculto;
    }

    /**
     * Lee los argumentos.
     *
     * @param texto argumentos del agente; puede ser null
     * @return los argumentos leídos
     * @throws IllegalArgumentException si falta {@code puerto} o {@code token}, o si el puerto no
     *     es válido
     */
    static ArgumentosAgente leer(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Faltan los argumentos del agente; se esperaba puerto=<puerto>,token=<token>");
        }
        String puerto = null;
        String token = null;
        boolean oculto = false;
        for (String par : texto.split(",")) {
            int igual = par.indexOf('=');
            if (igual < 0) {
                throw new IllegalArgumentException("Argumento del agente sin '=': " + par.trim());
            }
            String clave = par.substring(0, igual).trim();
            String valor = par.substring(igual + 1).trim();
            if (clave.equals("puerto")) {
                puerto = valor;
            } else if (clave.equals("token")) {
                token = valor;
            } else if (clave.equals("oculto")) {
                if (!"true".equals(valor) && !"false".equals(valor)) {
                    throw new IllegalArgumentException("El argumento oculto debe ser true o false");
                }
                oculto = Boolean.parseBoolean(valor);
            }
        }
        if (puerto == null || token == null || token.isEmpty()) {
            throw new IllegalArgumentException(
                    "El agente necesita los argumentos puerto y token: " + sinToken(texto));
        }
        return new ArgumentosAgente(leerPuerto(puerto), token, oculto);
    }

    /**
     * Devuelve el puerto del motor.
     *
     * @return puerto entre 1 y 65535
     */
    int puerto() {
        return puerto;
    }

    /**
     * Devuelve el token de la sesión.
     *
     * @return token no vacío
     */
    String token() {
        return token;
    }

    /** Indica si las ventanas deben ejecutarse fuera de pantalla y sin foco. */
    boolean oculto() {
        return oculto;
    }

    private static int leerPuerto(String texto) {
        int puerto;
        try {
            puerto = Integer.parseInt(texto);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("El puerto del agente no es un número: " + texto, e);
        }
        if (puerto < 1 || puerto > 65535) {
            throw new IllegalArgumentException(
                    "El puerto del agente está fuera de rango: " + puerto);
        }
        return puerto;
    }

    /** Oculta el valor del token para no escribirlo en mensajes de error. */
    private static String sinToken(String texto) {
        return texto.replaceAll("token=[^,]*", "token=****");
    }
}
