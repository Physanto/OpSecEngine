package io.github.physanto.opsecengine.oraculos;

import java.math.BigDecimal;

/** Lectura exacta de milisegundos enteros no negativos, sin redondear ni convertir texto. */
final class MedidasTiempo {
    private MedidasTiempo() {}

    static Long entero(Object valor) {
        if (!(valor instanceof Number numero)) {
            return null;
        }
        try {
            long resultado = new BigDecimal(numero.toString()).longValueExact();
            return resultado >= 0 ? resultado : null;
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }
}
