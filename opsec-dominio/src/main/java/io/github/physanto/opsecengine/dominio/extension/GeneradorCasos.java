package io.github.physanto.opsecengine.dominio.extension;

import io.github.physanto.opsecengine.dominio.caso.Caso;
import io.github.physanto.opsecengine.dominio.caso.Enfoque;
import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import io.github.physanto.opsecengine.dominio.mapa.MapaApp;
import io.github.physanto.opsecengine.dominio.mapa.Objetivo;
import java.util.List;

/**
 * Produce casos de prueba para un enfoque a partir del mapa de la aplicación.
 *
 * <p><b>Cuándo se llama.</b> Cuando el usuario pide generar casos para un objetivo, el núcleo llama
 * a {@link #generar(MapaApp, Objetivo, Proyecto)} en cada generador cuyo enfoque fue elegido.
 *
 * <p><b>Datos inesperados.</b> El generador no debe lanzar excepciones porque el mapa esté
 * incompleto o el objetivo no exista en él: en ese caso devuelve una lista vacía.
 *
 * <p><b>Reproducibilidad.</b> Con los mismos datos debe producir los mismos casos, en el mismo
 * orden y con los mismos ids.
 *
 * <p><b>Hilos.</b> Las implementaciones deben ser sin estado o seguras entre hilos.
 *
 * <p><b>Registro.</b> Con {@link java.util.ServiceLoader}, declarando la implementación en {@code
 * META-INF/services/io.github.physanto.opsecengine.dominio.extension.GeneradorCasos}. Necesita un
 * constructor público sin parámetros.
 */
public interface GeneradorCasos {

    /**
     * Devuelve el enfoque que cubre el generador.
     *
     * @return enfoque del generador; siempre el mismo para una implementación
     */
    Enfoque enfoque();

    /**
     * Genera casos para un objetivo.
     *
     * @param mapa mapa de la aplicación; nunca es null
     * @param objetivo parte de la aplicación que se quiere probar; nunca es null
     * @param proyecto proyecto de pruebas, con roles y operaciones sensibles; nunca es null
     * @return casos generados, con ids únicos entre sí; lista vacía, nunca null, si no hay nada que
     *     generar
     */
    List<Caso> generar(MapaApp mapa, Objetivo objetivo, Proyecto proyecto);
}
