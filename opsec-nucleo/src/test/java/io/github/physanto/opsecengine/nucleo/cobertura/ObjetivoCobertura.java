package io.github.physanto.opsecengine.nucleo.cobertura;

/** Clase mínima que las pruebas miden: un método que se usa y otro que nunca se llama. */
public class ObjetivoCobertura {

    /**
     * Método que las pruebas ejecutan.
     *
     * @param x número cualquiera
     * @return el doble si es positivo; si no, su opuesto
     */
    public int usado(int x) {
        if (x > 0) {
            return x * 2;
        }
        return -x;
    }

    /**
     * Método que nadie llama.
     *
     * @return una constante
     */
    public int noUsado() {
        return 42;
    }
}
