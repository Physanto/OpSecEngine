package io.github.physanto.opsecengine.dominio.extension;

import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.mapa.MapaApp;
import java.util.List;

/**
 * Proveedor opcional de un modelo de lenguaje que propone casos y clasifica operaciones sensibles.
 *
 * <p><b>Cuándo se llama.</b> Solo si el usuario lo activa: el núcleo pide propuestas a partir del
 * mapa de la aplicación. Lo que devuelve son sugerencias que el usuario revisa antes de usarlas; el
 * motor nunca decide un veredicto con ellas.
 *
 * <p><b>Errores.</b> El modelo puede no estar disponible o responder algo inválido. El proveedor no
 * debe lanzar excepciones por eso: devuelve una lista vacía y registra el motivo.
 *
 * <p><b>Hilos.</b> Las implementaciones deben ser sin estado o seguras entre hilos.
 *
 * <p><b>Registro.</b> Con {@link java.util.ServiceLoader}, declarando la implementación en {@code
 * META-INF/services/io.github.physanto.opsecengine.dominio.extension.ProveedorIA}. Necesita un
 * constructor público sin parámetros.
 */
public interface ProveedorIA {

    /**
     * Propone casos de prueba a partir del mapa y de una descripción en lenguaje natural.
     *
     * @param mapa mapa de la aplicación; nunca es null
     * @param descripcion qué quiere probar el usuario, por ejemplo {@code "eliminar una persona sin
     *     ser administrador"}; nunca es null
     * @return casos propuestos, con ids únicos entre sí; lista vacía, nunca null, si no hay
     *     propuestas
     */
    List<Caso> proponerCasos(MapaApp mapa, String descripcion);

    /**
     * Señala las operaciones del mapa que parecen sensibles.
     *
     * @param mapa mapa de la aplicación; nunca es null
     * @return nombres de operaciones con el mismo formato que las claves de {@link
     *     io.github.physanto.opsecengine.dominio.configuracion.Proyecto#politica()}, por ejemplo
     *     {@code "Eliminar Persona"}; lista vacía, nunca null, si no encuentra ninguna
     */
    List<String> clasificarSensibles(MapaApp mapa);
}
