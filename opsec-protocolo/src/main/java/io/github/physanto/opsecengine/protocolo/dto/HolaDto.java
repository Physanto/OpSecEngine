package io.github.physanto.opsecengine.protocolo.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Saludo con que el agente abre la conexión en un mensaje {@code HOLA}.
 *
 * <p>Es inmutable y seguro entre hilos. {@link #toString()} oculta el token.
 */
public final class HolaDto {

    private final String token;
    private final String versionAgente;
    private final String versionJava;
    private final String sistemaOperativo;

    /**
     * Crea un saludo.
     *
     * @param token token de sesión que el motor pasó al agente al lanzarlo; no puede ser null ni
     *     estar vacío
     * @param versionAgente versión del agente; no puede ser null ni estar vacía
     * @param versionJava versión de Java de la aplicación probada ({@code java.version}); no puede
     *     ser null ni estar vacía
     * @param sistemaOperativo nombre y versión del sistema operativo; no puede ser null ni estar
     *     vacío
     * @throws NullPointerException si algún parámetro es null
     * @throws IllegalArgumentException si algún parámetro está vacío
     */
    @JsonCreator
    public HolaDto(
            @JsonProperty("token") String token,
            @JsonProperty("versionAgente") String versionAgente,
            @JsonProperty("versionJava") String versionJava,
            @JsonProperty("sistemaOperativo") String sistemaOperativo) {
        this.token = Copias.texto(token, "El token del saludo");
        this.versionAgente = Copias.texto(versionAgente, "La versión del agente");
        this.versionJava = Copias.texto(versionJava, "La versión de Java");
        this.sistemaOperativo = Copias.texto(sistemaOperativo, "El sistema operativo");
    }

    /**
     * Devuelve el token de sesión.
     *
     * @return token; nunca es null
     */
    public String getToken() {
        return token;
    }

    /**
     * Devuelve la versión del agente.
     *
     * @return versión; nunca es null
     */
    public String getVersionAgente() {
        return versionAgente;
    }

    /**
     * Devuelve la versión de Java de la aplicación probada.
     *
     * @return versión; nunca es null
     */
    public String getVersionJava() {
        return versionJava;
    }

    /**
     * Devuelve el sistema operativo.
     *
     * @return nombre y versión; nunca es null
     */
    public String getSistemaOperativo() {
        return sistemaOperativo;
    }

    /**
     * Compara este saludo con otro objeto campo por campo.
     *
     * @param otro objeto a comparar
     * @return {@code true} si es un saludo con los mismos campos
     */
    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (!(otro instanceof HolaDto)) {
            return false;
        }
        HolaDto hola = (HolaDto) otro;
        return token.equals(hola.token)
                && versionAgente.equals(hola.versionAgente)
                && versionJava.equals(hola.versionJava)
                && sistemaOperativo.equals(hola.sistemaOperativo);
    }

    /**
     * Calcula el hash a partir de todos los campos.
     *
     * @return hash del saludo
     */
    @Override
    public int hashCode() {
        return Objects.hash(token, versionAgente, versionJava, sistemaOperativo);
    }

    /**
     * Devuelve una representación legible del saludo con el token oculto.
     *
     * @return texto con las versiones y el sistema operativo, sin el token
     */
    @Override
    public String toString() {
        return "HolaDto[token=****, versionAgente="
                + versionAgente
                + ", versionJava="
                + versionJava
                + ", sistemaOperativo="
                + sistemaOperativo
                + "]";
    }
}
