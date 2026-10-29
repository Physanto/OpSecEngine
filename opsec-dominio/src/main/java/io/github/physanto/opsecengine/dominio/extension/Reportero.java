package io.github.physanto.opsecengine.dominio.extension;

import io.github.physanto.opsecengine.dominio.resultado.ResultadoSuite;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Escribe el resultado de una suite en un formato de informe.
 *
 * <p><b>Cuándo se llama.</b> Al terminar una suite, el núcleo llama a {@link
 * #escribir(ResultadoSuite, Path)} en cada reportero cuyo formato fue pedido.
 *
 * <p><b>Datos inesperados.</b> El reportero no debe lanzar excepciones porque falten datos
 * opcionales en el resultado, como capturas o hallazgos; solo lanza {@link IOException} si no puede
 * escribir.
 *
 * <p><b>Hilos.</b> Las implementaciones deben ser sin estado o seguras entre hilos.
 *
 * <p><b>Registro.</b> Con {@link java.util.ServiceLoader}, declarando la implementación en {@code
 * META-INF/services/io.github.physanto.opsecengine.dominio.extension.Reportero}. Necesita un
 * constructor público sin parámetros.
 */
public interface Reportero {

    /**
     * Devuelve el formato que produce el reportero.
     *
     * @return nombre único del formato en minúsculas, por ejemplo {@code "html"}, {@code "json"},
     *     {@code "junit"} o {@code "sarif"}
     */
    String formato();

    /**
     * Escribe el informe.
     *
     * <p>Crea sus archivos dentro de la carpeta de destino, con nombres propios del formato (por
     * ejemplo {@code informe.html}), y los reemplaza si ya existen. Escribe el texto en UTF-8 y no
     * modifica archivos de otros reporteros.
     *
     * @param resultado resultado de la suite; nunca es null
     * @param destino carpeta de resultados de la ejecución; ya existe y nunca es null
     * @throws IOException si no puede escribir en la carpeta de destino
     */
    void escribir(ResultadoSuite resultado, Path destino) throws IOException;
}
