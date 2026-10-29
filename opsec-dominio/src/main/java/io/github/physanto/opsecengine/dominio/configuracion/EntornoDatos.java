package io.github.physanto.opsecengine.dominio.configuracion;

import java.util.Map;
import java.util.Objects;

/**
 * Entorno de datos de prueba que usa la aplicación probada.
 *
 * <p>El tipo elige el {@code ProveedorDatos} que prepara y restaura los datos antes de cada caso.
 * Los tipos documentados son {@value #NINGUNO}, {@value #FIREBASE_EMULADOR}, {@value #MYSQL} y
 * {@value #H2}; un plugin puede aportar otros.
 *
 * <p>Es inmutable y seguro entre hilos.
 *
 * @param tipo tipo de entorno; no puede ser null ni estar en blanco
 * @param parametros parámetros propios del tipo, por ejemplo el puerto del emulador; si es null
 *     queda vacío, y no admite claves ni valores null
 */
public record EntornoDatos(String tipo, Map<String, String> parametros) {

    /** Tipo para aplicaciones sin backend o cuyos datos no se preparan. */
    public static final String NINGUNO = "ninguno";

    /** Tipo para el emulador local de Firebase (Firestore y autenticación). */
    public static final String FIREBASE_EMULADOR = "firebase-emulador";

    /** Tipo para una base de datos MySQL desechable. */
    public static final String MYSQL = "mysql";

    /** Tipo para una base de datos H2 en memoria o en archivo. */
    public static final String H2 = "h2";

    /**
     * Crea un entorno de datos con una copia inmutable de sus parámetros.
     *
     * @throws NullPointerException si {@code tipo} es null o si {@code parametros} contiene claves
     *     o valores null
     * @throws IllegalArgumentException si {@code tipo} está en blanco
     */
    public EntornoDatos {
        Objects.requireNonNull(tipo, "El tipo del entorno de datos no puede ser null");
        if (tipo.isBlank()) {
            throw new IllegalArgumentException(
                    "El tipo del entorno de datos no puede estar en blanco");
        }
        parametros = parametros == null ? Map.of() : Map.copyOf(parametros);
    }

    /**
     * Devuelve un entorno sin datos de prueba.
     *
     * @return entorno de tipo {@value #NINGUNO} sin parámetros
     */
    public static EntornoDatos ninguno() {
        return new EntornoDatos(NINGUNO, Map.of());
    }
}
