package io.github.physanto.opsecengine.dominio.caso;

import java.util.List;

/**
 * Forma de encontrar un componente o una ventana de la aplicación probada sin depender de su
 * código.
 *
 * <p>Contiene criterios ordenados por prioridad: el agente prueba el primero y, si no encuentra
 * nada, pasa al siguiente. Debe tener al menos un criterio.
 *
 * <p>Es inmutable y seguro entre hilos.
 *
 * @param criterios criterios ordenados de mayor a menor prioridad; no puede ser null ni estar
 *     vacía, y no admite elementos null
 */
public record Localizador(List<Criterio> criterios) {

    /**
     * Crea un localizador con una copia inmutable de los criterios.
     *
     * @throws IllegalArgumentException si {@code criterios} es null o está vacía
     * @throws NullPointerException si algún criterio es null
     */
    public Localizador {
        criterios = criterios == null ? List.of() : List.copyOf(criterios);
        if (criterios.isEmpty()) {
            throw new IllegalArgumentException("El localizador necesita al menos un criterio");
        }
    }

    /**
     * Crea un localizador que busca un componente por su texto visible.
     *
     * @param texto texto visible del componente, por ejemplo {@code "Iniciar Sesion"}
     * @return localizador con un único criterio {@link EstrategiaLocalizacion#TEXTO}
     * @throws NullPointerException si {@code texto} es null
     * @throws IllegalArgumentException si {@code texto} está en blanco
     */
    public static Localizador porTexto(String texto) {
        return de(EstrategiaLocalizacion.TEXTO, texto);
    }

    /**
     * Crea un localizador que busca un componente por el texto de su etiqueta asociada.
     *
     * @param etiqueta texto de la etiqueta, por ejemplo {@code "Usuario"}
     * @return localizador con un único criterio {@link EstrategiaLocalizacion#ETIQUETA}
     * @throws NullPointerException si {@code etiqueta} es null
     * @throws IllegalArgumentException si {@code etiqueta} está en blanco
     */
    public static Localizador porEtiqueta(String etiqueta) {
        return de(EstrategiaLocalizacion.ETIQUETA, etiqueta);
    }

    /**
     * Crea un localizador que identifica una ventana por el nombre de su clase.
     *
     * @param claseVentana nombre completo o simple de la clase de la ventana
     * @return localizador con un único criterio {@link EstrategiaLocalizacion#CLASE_VENTANA}
     * @throws NullPointerException si {@code claseVentana} es null
     * @throws IllegalArgumentException si {@code claseVentana} está en blanco
     */
    public static Localizador porClaseVentana(String claseVentana) {
        return de(EstrategiaLocalizacion.CLASE_VENTANA, claseVentana);
    }

    private static Localizador de(EstrategiaLocalizacion estrategia, String valor) {
        return new Localizador(List.of(new Criterio(estrategia, valor)));
    }
}
