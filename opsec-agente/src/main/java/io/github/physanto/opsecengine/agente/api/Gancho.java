package io.github.physanto.opsecengine.agente.api;

import io.github.physanto.opsecengine.protocolo.dto.ConfigGanchosDto;
import java.lang.instrument.Instrumentation;

/**
 * Gancho de instrumentación: modifica clases de la aplicación probada para observar operaciones,
 * por ejemplo sentencias JDBC, operaciones de Firestore, archivos o {@code Runtime.exec}, y para
 * inyectar fallos.
 *
 * <p><b>Cuándo se llama.</b> El agente instala cada gancho una sola vez, al arrancar y antes de que
 * la aplicación ejecute su {@code main}, después de recibir la primera configuración de ganchos.
 *
 * <p><b>Fallo seguro.</b> Un gancho corre dentro de una aplicación ajena. Si no puede instalarse o
 * falla mientras observa, debe registrar el problema y dejar que la aplicación siga funcionando
 * igual que sin el gancho; nunca debe lanzar excepciones hacia la aplicación.
 *
 * <p><b>Hilos.</b> El código que el gancho agrega a la aplicación se ejecuta desde cualquier hilo,
 * así que debe ser seguro entre hilos.
 *
 * <p><b>Registro.</b> El agente descubre los ganchos con {@link java.util.ServiceLoader}, en el
 * archivo {@code META-INF/services/io.github.physanto.opsecengine.agente.api.Gancho}. Cada
 * implementación necesita un constructor público sin parámetros.
 */
public interface Gancho {

    /**
     * Devuelve el identificador del gancho.
     *
     * @return id único y estable en minúsculas, por ejemplo {@code "jdbc"} o {@code "firestore"}
     */
    String id();

    /**
     * Instala el gancho sobre las clases de la aplicación.
     *
     * @param inst servicio de instrumentación de la JVM; nunca es null
     * @param emisor emisor por el que el gancho envía sus eventos; nunca es null
     * @param config operaciones sensibles, canarios y fallos a inyectar; nunca es null
     */
    void instalar(Instrumentation inst, EmisorEventos emisor, ConfigGanchosDto config);
}
