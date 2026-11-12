package io.github.physanto.opsecengine.informes;

/**
 * Utilidades para escribir XML 1.0 válido.
 *
 * <p>Los textos de la traza pueden traer caracteres de control que XML 1.0 no admite ni siquiera
 * escapados (por ejemplo el carácter nulo de un payload de ataque), así que {@link
 * #escapar(String)} los descarta además de escapar los caracteres especiales.
 *
 * <p>No tiene estado y no se instancia; es seguro entre hilos.
 */
final class Xml {

    private Xml() {}

    /**
     * Escapa un texto para usarlo como contenido o como valor de un atributo entre comillas.
     *
     * @param texto texto original; si es null se devuelve vacío
     * @return el texto con {@code & < > " '} reemplazados por sus entidades y sin los caracteres
     *     que XML 1.0 no admite
     */
    static String escapar(String texto) {
        if (texto == null || texto.isEmpty()) {
            return "";
        }
        StringBuilder salida = new StringBuilder(texto.length() + 16);
        for (int i = 0; i < texto.length(); ) {
            int punto = texto.codePointAt(i);
            i += Character.charCount(punto);
            switch (punto) {
                case '&' -> salida.append("&amp;");
                case '<' -> salida.append("&lt;");
                case '>' -> salida.append("&gt;");
                case '"' -> salida.append("&quot;");
                case '\'' -> salida.append("&apos;");
                case '\n', '\r', '\t' -> salida.appendCodePoint(punto);
                default -> {
                    if (esValido(punto)) {
                        salida.appendCodePoint(punto);
                    }
                }
            }
        }
        return salida.toString();
    }

    /** Indica si un punto de código está permitido en XML 1.0 (producción {@code Char}). */
    private static boolean esValido(int punto) {
        return (punto >= 0x20 && punto <= 0xD7FF)
                || (punto >= 0xE000 && punto <= 0xFFFD)
                || (punto >= 0x10000 && punto <= 0x10FFFF);
    }
}
