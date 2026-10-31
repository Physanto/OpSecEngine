package io.github.physanto.opsecengine.ataques;

/**
 * Categoría de la biblioteca de ataques; coincide con el campo {@code categoria} de {@code
 * payload.schema.json}.
 */
public enum CategoriaPayload {
    /** Entradas vacías o con solo espacios en blanco. */
    VACIOS,
    /** Entradas muy largas o en el límite de longitud. */
    LONGITUD,
    /** Caracteres Unicode poco comunes, combinados o de control. */
    UNICODE,
    /** Números en el límite, negativos, decimales o con formato raro. */
    NUMERICOS,
    /** Inyección SQL. */
    INYECCION_SQL,
    /** Rutas y valores especiales de Firestore. */
    FIRESTORE,
    /** HTML que Swing interpreta en etiquetas y botones. */
    HTML_SWING,
    /** Cadenas de formato como {@code %s} o {@code %n}. */
    CADENAS_FORMATO,
    /** Rutas de archivo y recorridos de carpetas. */
    RUTAS_ARCHIVO,
    /** Fórmulas que una hoja de cálculo ejecuta al abrir un CSV. */
    FORMULAS_CSV,
    /** Comandos del sistema operativo. */
    COMANDOS
}
