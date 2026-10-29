package io.github.physanto.opsecengine.protocolo.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

/**
 * Configuración de los ganchos que el motor envía en un mensaje {@code CONFIG_GANCHOS}.
 *
 * <p>Es inmutable y seguro entre hilos.
 */
public final class ConfigGanchosDto {

    private final List<String> sqlSensibles;
    private final List<String> firestoreSensibles;
    private final List<String> canarios;
    private final List<String> fallosInyectados;

    /**
     * Crea una configuración de ganchos. Todas las listas quedan vacías si son null y no admiten
     * elementos null.
     *
     * @param sqlSensibles palabras clave de las sentencias SQL sensibles, en mayúsculas, por
     *     ejemplo {@code "DELETE"}
     * @param firestoreSensibles métodos de Firestore sensibles, por ejemplo {@code "delete"}
     * @param canarios textos marcados que, si aparecen en una sentencia SQL u operación de
     *     Firestore, delatan que un dato escrito en la interfaz llegó sin sanear
     * @param fallosInyectados identificadores de los fallos que los ganchos deben provocar, según
     *     el catálogo de fallos
     * @throws NullPointerException si alguna lista contiene elementos null
     */
    @JsonCreator
    public ConfigGanchosDto(
            @JsonProperty("sqlSensibles") List<String> sqlSensibles,
            @JsonProperty("firestoreSensibles") List<String> firestoreSensibles,
            @JsonProperty("canarios") List<String> canarios,
            @JsonProperty("fallosInyectados") List<String> fallosInyectados) {
        this.sqlSensibles = Copias.lista(sqlSensibles, "Las sentencias SQL sensibles");
        this.firestoreSensibles =
                Copias.lista(firestoreSensibles, "Las operaciones de Firestore sensibles");
        this.canarios = Copias.lista(canarios, "Los canarios");
        this.fallosInyectados = Copias.lista(fallosInyectados, "Los fallos inyectados");
    }

    /**
     * Devuelve las palabras clave de las sentencias SQL sensibles.
     *
     * @return lista inmutable; nunca es null
     */
    public List<String> getSqlSensibles() {
        return sqlSensibles;
    }

    /**
     * Devuelve los métodos de Firestore sensibles.
     *
     * @return lista inmutable; nunca es null
     */
    public List<String> getFirestoreSensibles() {
        return firestoreSensibles;
    }

    /**
     * Devuelve los canarios activos.
     *
     * @return lista inmutable; nunca es null
     */
    public List<String> getCanarios() {
        return canarios;
    }

    /**
     * Devuelve los fallos que se deben inyectar.
     *
     * @return lista inmutable; nunca es null
     */
    public List<String> getFallosInyectados() {
        return fallosInyectados;
    }

    /**
     * Compara esta configuración con otro objeto campo por campo.
     *
     * @param otro objeto a comparar
     * @return {@code true} si es una configuración con las mismas listas
     */
    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (!(otro instanceof ConfigGanchosDto)) {
            return false;
        }
        ConfigGanchosDto config = (ConfigGanchosDto) otro;
        return sqlSensibles.equals(config.sqlSensibles)
                && firestoreSensibles.equals(config.firestoreSensibles)
                && canarios.equals(config.canarios)
                && fallosInyectados.equals(config.fallosInyectados);
    }

    /**
     * Calcula el hash a partir de todos los campos.
     *
     * @return hash de la configuración
     */
    @Override
    public int hashCode() {
        return Objects.hash(sqlSensibles, firestoreSensibles, canarios, fallosInyectados);
    }

    /**
     * Devuelve una representación legible de la configuración.
     *
     * @return texto con todas las listas
     */
    @Override
    public String toString() {
        return "ConfigGanchosDto[sqlSensibles="
                + sqlSensibles
                + ", firestoreSensibles="
                + firestoreSensibles
                + ", canarios="
                + canarios
                + ", fallosInyectados="
                + fallosInyectados
                + "]";
    }
}
