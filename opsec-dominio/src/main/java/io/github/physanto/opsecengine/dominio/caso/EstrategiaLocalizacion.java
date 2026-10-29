package io.github.physanto.opsecengine.dominio.caso;

/**
 * Forma de encontrar un componente o una ventana dentro de la aplicación probada.
 *
 * <p>Cada {@link Criterio} combina una estrategia con un valor. Las estrategias de componente
 * buscan dentro de las ventanas visibles; las de ventana ({@link #TITULO_VENTANA} y {@link
 * #CLASE_VENTANA}) identifican una ventana completa.
 */
public enum EstrategiaLocalizacion {
    /** Texto de la etiqueta asociada al componente (por ejemplo, el {@code JLabel} de un campo). */
    ETIQUETA,
    /** Texto visible del propio componente, como el de un botón o un elemento de menú. */
    TEXTO,
    /** Nombre accesible del componente ({@code AccessibleContext.getAccessibleName()}). */
    NOMBRE_ACCESIBLE,
    /** Nombre asignado con {@code Component.setName(String)}. */
    NOMBRE_COMPONENTE,
    /** Nombre completo o simple de la clase del componente, por ejemplo {@code JButton}. */
    TIPO,
    /** Ruta del componente en el árbol de la ventana, en el formato que define el agente. */
    RUTA,
    /** Título de una ventana o diálogo. */
    TITULO_VENTANA,
    /** Nombre completo o simple de la clase de una ventana o diálogo. */
    CLASE_VENTANA
}
