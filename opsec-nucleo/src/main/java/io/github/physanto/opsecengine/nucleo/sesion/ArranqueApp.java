package io.github.physanto.opsecengine.nucleo.sesion;

import io.github.physanto.opsecengine.dominio.configuracion.Proyecto;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Arranca la aplicación probada de modo que su agente se conecte al motor.
 *
 * <p>{@link SesionService} lo usa para abrir cada sesión. La implementación normal es {@link
 * LanzadorProceso}, que lanza otra JVM con {@code -javaagent}; en las pruebas se puede usar una
 * expresión lambda que conecte un agente falso al puerto:
 *
 * <pre>{@code
 * ArranqueApp arranque = (proyecto, puerto, token) -> {
 *     AgenteFalso agente = new AgenteFalso();
 *     agente.conectar(puerto, token);
 *     return agente::close;
 * };
 * }</pre>
 */
@FunctionalInterface
public interface ArranqueApp {

    /**
     * Arranca la aplicación con el agente apuntando a un puerto y un token.
     *
     * <p>No espera el saludo del agente: de eso se encarga quien llama.
     *
     * @param proyecto proyecto con la aplicación que se arranca; nunca es null
     * @param puerto puerto de {@code 127.0.0.1} donde el motor espera al agente
     * @param token token de la sesión que el agente debe enviar en el {@code HOLA}; nunca es null
     * @return acción que detiene la aplicación; se llama al cerrar la sesión y puede llamarse
     *     aunque la aplicación ya haya terminado
     * @throws IOException si no se puede arrancar la aplicación
     */
    Closeable arrancar(Proyecto proyecto, int puerto, String token) throws IOException;

    /**
     * Devuelve la carpeta de resultados de la ejecución, donde el motor guarda también las
     * capturas.
     *
     * @return la carpeta, o vacío si este arranque no tiene una (el valor por defecto)
     */
    default Optional<Path> carpetaResultados() {
        return Optional.empty();
    }
}
