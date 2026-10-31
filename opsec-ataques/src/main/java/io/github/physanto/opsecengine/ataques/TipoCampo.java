package io.github.physanto.opsecengine.ataques;

/**
 * Tipo de campo en el que tiene sentido probar un payload; coincide con el campo {@code aplica_a}
 * de {@code payload.schema.json}.
 */
public enum TipoCampo {
    /** Cualquier campo. */
    CUALQUIERA,
    /** Texto libre. */
    TEXTO,
    /** Contraseña. */
    CONTRASENA,
    /** Número. */
    NUMERO,
    /** Fecha. */
    FECHA,
    /** Correo electrónico. */
    CORREO,
    /** Búsqueda. */
    BUSQUEDA,
    /** Identificador, como una cédula o un código. */
    IDENTIFICADOR
}
