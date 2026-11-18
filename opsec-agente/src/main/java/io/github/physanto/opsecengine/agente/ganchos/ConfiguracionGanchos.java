package io.github.physanto.opsecengine.agente.ganchos;

import io.github.physanto.opsecengine.protocolo.dto.ConfigGanchosDto;
import java.util.Collections;
import java.util.Locale;

/**
 * Configuración vigente de los ganchos: operaciones sensibles, canarios y fallos a inyectar.
 *
 * <p>El motor la envía con {@code CONFIG_GANCHOS}; cada mensaje reemplaza la anterior por completo.
 * Los ganchos la consultan cada vez que emiten un evento, así que un cambio vale para los eventos
 * siguientes. Hasta que llega la primera, está vacía. Es seguro entre hilos: la referencia es
 * volátil y el objeto es inmutable.
 */
public final class ConfiguracionGanchos {

    private static final ConfigGanchosDto VACIA =
            new ConfigGanchosDto(
                    Collections.<String>emptyList(),
                    Collections.<String>emptyList(),
                    Collections.<String>emptyList(),
                    Collections.<String>emptyList());

    private static volatile ConfigGanchosDto actual = VACIA;

    private ConfiguracionGanchos() {}

    /**
     * Reemplaza la configuración vigente.
     *
     * @param nueva configuración recibida; null la deja vacía
     */
    public static void reemplazar(ConfigGanchosDto nueva) {
        actual = nueva == null ? VACIA : nueva;
    }

    /**
     * Devuelve la configuración vigente.
     *
     * @return la configuración; nunca es null
     */
    public static ConfigGanchosDto actual() {
        return actual;
    }

    /**
     * Indica si una operación SQL está en la lista de sensibles.
     *
     * @param operacion primera palabra de la sentencia, por ejemplo {@code DELETE}; puede ser null
     * @return true si está en la lista, sin distinguir mayúsculas de minúsculas
     */
    public static boolean esSqlSensible(String operacion) {
        return contiene(actual.getSqlSensibles(), operacion);
    }

    /**
     * Indica si una operación de Firestore está en la lista de sensibles.
     *
     * @param operacion nombre de la operación, por ejemplo {@code delete}; puede ser null
     * @return true si está en la lista, sin distinguir mayúsculas de minúsculas
     */
    public static boolean esFirestoreSensible(String operacion) {
        return contiene(actual.getFirestoreSensibles(), operacion);
    }

    private static boolean contiene(Iterable<String> lista, String operacion) {
        if (operacion == null) {
            return false;
        }
        String buscada = operacion.toUpperCase(Locale.ROOT);
        for (String elemento : lista) {
            if (elemento.toUpperCase(Locale.ROOT).equals(buscada)) {
                return true;
            }
        }
        return false;
    }
}
