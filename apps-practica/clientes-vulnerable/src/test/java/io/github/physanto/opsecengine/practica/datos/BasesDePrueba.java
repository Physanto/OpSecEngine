package io.github.physanto.opsecengine.practica.datos;

import java.sql.SQLException;
import java.util.UUID;

/** Crea bases en memoria con nombre único, para que cada prueba tenga sus propios datos. */
public final class BasesDePrueba {

    private BasesDePrueba() {}

    /**
     * Crea una base nueva con los datos semilla.
     *
     * @return base abierta; la prueba debe cerrarla
     * @throws SQLException si no se puede crear
     */
    public static BaseDatos nueva() throws SQLException {
        return BaseDatos.enMemoria("prueba_" + UUID.randomUUID().toString().replace("-", ""));
    }
}
