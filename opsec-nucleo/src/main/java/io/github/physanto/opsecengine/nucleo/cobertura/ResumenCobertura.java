package io.github.physanto.opsecengine.nucleo.cobertura;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Resumen de la cobertura de la aplicación y dónde quedaron los informes.
 *
 * <p>Es inmutable y seguro entre hilos.
 *
 * @param instrucciones instrucciones de toda la aplicación; no puede ser null
 * @param ramas ramas de toda la aplicación; no puede ser null
 * @param lineas líneas de toda la aplicación; no puede ser null
 * @param clases cobertura de cada clase medida, ordenada por nombre; si es null queda vacía
 * @param indiceHtml página principal del informe HTML; no puede ser null
 * @param xml informe en XML con el formato de JaCoCo; no puede ser null
 */
public record ResumenCobertura(
        Contador instrucciones,
        Contador ramas,
        Contador lineas,
        List<ClaseCobertura> clases,
        Path indiceHtml,
        Path xml) {

    /** Valida los campos y hace inmutable la lista de clases. */
    public ResumenCobertura {
        Objects.requireNonNull(instrucciones, "Las instrucciones no pueden ser null");
        Objects.requireNonNull(ramas, "Las ramas no pueden ser null");
        Objects.requireNonNull(lineas, "Las líneas no pueden ser null");
        Objects.requireNonNull(indiceHtml, "El índice HTML no puede ser null");
        Objects.requireNonNull(xml, "El XML no puede ser null");
        clases = clases == null ? List.of() : List.copyOf(clases);
    }
}
