package io.github.physanto.opsecengine.dominio.mapa;

import io.github.physanto.opsecengine.dominio.caso.Localizador;

/**
 * Qué parte de la aplicación se quiere probar: toda la aplicación, una ventana o un control
 * concreto de una ventana.
 *
 * <p>Si {@code ventanaId} es null, el objetivo es toda la aplicación. Si además hay {@code
 * control}, el objetivo es ese control dentro de la ventana, por ejemplo el botón "Eliminar
 * Persona". Es inmutable y seguro entre hilos.
 *
 * @param descripcion descripción legible del objetivo; puede ser null
 * @param ventanaId id de la ventana del {@link MapaApp}; puede ser null para toda la aplicación
 * @param control localizador del control dentro de la ventana; puede ser null, y solo se admite si
 *     hay {@code ventanaId}
 */
public record Objetivo(String descripcion, String ventanaId, Localizador control) {

    /**
     * Crea un objetivo y valida sus datos.
     *
     * @throws IllegalArgumentException si {@code ventanaId} no es null y está en blanco, o si hay
     *     {@code control} sin {@code ventanaId}
     */
    public Objetivo {
        if (ventanaId != null && ventanaId.isBlank()) {
            throw new IllegalArgumentException(
                    "El id de la ventana del objetivo no puede estar en blanco; use null para"
                            + " toda la aplicación");
        }
        if (control != null && ventanaId == null) {
            throw new IllegalArgumentException(
                    "Un objetivo con control necesita la ventana que lo contiene");
        }
    }

    /**
     * Crea un objetivo que abarca toda la aplicación.
     *
     * @return objetivo sin ventana ni control
     */
    public static Objetivo todaLaApp() {
        return new Objetivo("Toda la aplicación", null, null);
    }

    /**
     * Indica si el objetivo abarca toda la aplicación.
     *
     * @return {@code true} si no hay {@code ventanaId}
     */
    public boolean esTodaLaApp() {
        return ventanaId == null;
    }
}
