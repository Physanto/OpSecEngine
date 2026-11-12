package io.github.physanto.opsecengine.informes;

/**
 * Utilidades para escribir HTML seguro.
 *
 * <p>Las trazas contienen texto controlado por la aplicación probada y por los payloads de ataque
 * (por ejemplo {@code <script>} o {@code "' OR 1=1"}), así que todo texto que llega al informe pasa
 * por {@link #escapar(String)}.
 *
 * <p>No tiene estado y no se instancia; es seguro entre hilos.
 */
final class Html {

    private Html() {}

    /**
     * Escapa un texto para usarlo como contenido o como valor de un atributo entre comillas.
     *
     * @param texto texto original; si es null se devuelve vacío
     * @return el texto con {@code & < > " '} reemplazados por sus entidades, y sin los caracteres
     *     de control que HTML no admite (salvo salto de línea y tabulación)
     */
    static String escapar(String texto) {
        if (texto == null || texto.isEmpty()) {
            return "";
        }
        StringBuilder salida = new StringBuilder(texto.length() + 16);
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            switch (c) {
                case '&' -> salida.append("&amp;");
                case '<' -> salida.append("&lt;");
                case '>' -> salida.append("&gt;");
                case '"' -> salida.append("&quot;");
                case '\'' -> salida.append("&#39;");
                default -> {
                    if (c >= 0x20 || c == '\n' || c == '\t') {
                        salida.append(c);
                    }
                }
            }
        }
        return salida.toString();
    }

    /**
     * Recorta un texto largo para mostrarlo en una celda.
     *
     * @param texto texto original; si es null se devuelve vacío
     * @param maximo número máximo de caracteres, mayor que cero
     * @return el texto completo si cabe; si no, sus primeros {@code maximo} caracteres seguidos de
     *     «…»
     */
    static String recortar(String texto, int maximo) {
        if (texto == null) {
            return "";
        }
        return texto.length() <= maximo ? texto : texto.substring(0, maximo) + "…";
    }
}
