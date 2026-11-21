package io.github.physanto.opsecengine.nucleo.cobertura;

/**
 * Cuántos elementos se ejercitaron y cuántos no: instrucciones, ramas o líneas.
 *
 * <p>Es inmutable y seguro entre hilos.
 *
 * @param cubiertos elementos que se ejecutaron al menos una vez; no negativo
 * @param perdidos elementos que nunca se ejecutaron; no negativo
 */
public record Contador(int cubiertos, int perdidos) {

    /** Valida que los conteos no sean negativos. */
    public Contador {
        if (cubiertos < 0 || perdidos < 0) {
            throw new IllegalArgumentException(
                    "Los conteos no pueden ser negativos: " + cubiertos + ", " + perdidos);
        }
    }

    /**
     * Devuelve el total de elementos.
     *
     * @return cubiertos más perdidos
     */
    public int total() {
        return cubiertos + perdidos;
    }

    /**
     * Devuelve el porcentaje cubierto.
     *
     * @return de 0 a 100; 0 si no hay elementos
     */
    public double porcentaje() {
        return total() == 0 ? 0.0 : 100.0 * cubiertos / total();
    }
}
