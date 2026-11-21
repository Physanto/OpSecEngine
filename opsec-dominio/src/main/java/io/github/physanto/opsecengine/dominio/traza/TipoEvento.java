package io.github.physanto.opsecengine.dominio.traza;

/**
 * Tipo de un {@link Evento} de la traza.
 *
 * <p>Los campos que lleva {@link Evento#datos()} para cada tipo (nombre, tipo y si son
 * obligatorios) se documentan en el catálogo de eventos {@code contratos/catalogos/eventos.yaml}.
 * Agregar un campo a un tipo es un cambio aditivo del contrato.
 */
public enum TipoEvento {
    /** Empieza la ejecución de un paso. Campos: ver {@code contratos/catalogos/eventos.yaml}. */
    PASO_INICIO,
    /**
     * Termina un paso, con su código de resultado. Campos: ver {@code
     * contratos/catalogos/eventos.yaml}.
     */
    PASO_FIN,
    /** Se abre o se muestra una ventana. Campos: ver {@code contratos/catalogos/eventos.yaml}. */
    VENTANA_ABIERTA,
    /** Se cierra o se oculta una ventana. Campos: ver {@code contratos/catalogos/eventos.yaml}. */
    VENTANA_CERRADA,
    /**
     * Aparece un diálogo, por ejemplo un {@code JOptionPane}. Campos: ver {@code
     * contratos/catalogos/eventos.yaml}.
     */
    DIALOGO,
    /**
     * La aplicación ejecuta una sentencia SQL por JDBC. Campos: ver {@code
     * contratos/catalogos/eventos.yaml}.
     */
    SQL,
    /**
     * La aplicación ejecuta una operación de Firestore. Campos: ver {@code
     * contratos/catalogos/eventos.yaml}.
     */
    FIRESTORE,
    /**
     * La aplicación lee, escribe o borra un archivo. Campos: ver {@code
     * contratos/catalogos/eventos.yaml}.
     */
    ARCHIVO,
    /**
     * La aplicación ejecuta un comando del sistema operativo. Campos: ver {@code
     * contratos/catalogos/eventos.yaml}.
     */
    COMANDO,
    /**
     * Ocurre una excepción no capturada en el EDT o en otro hilo. Campos: ver {@code
     * contratos/catalogos/eventos.yaml}.
     */
    EXCEPCION,
    /**
     * La aplicación escribe en la salida estándar o de error. Campos: ver {@code
     * contratos/catalogos/eventos.yaml}.
     */
    CONSOLA,
    /** Se toma una captura de pantalla. Campos: ver {@code contratos/catalogos/eventos.yaml}. */
    CAPTURA,
    /**
     * Instantánea del estado de los datos de prueba, antes o después de un caso. Campos: ver {@code
     * contratos/catalogos/eventos.yaml}.
     */
    ESTADO,
    /**
     * Cambia el estado de la sesión: inicio o cierre de sesión con un rol. Campos: ver {@code
     * contratos/catalogos/eventos.yaml}.
     */
    SESION,
    /**
     * Medida de tiempo, por ejemplo la respuesta de una acción o un EDT bloqueado. Campos: ver
     * {@code contratos/catalogos/eventos.yaml}.
     */
    TIEMPO,
    /**
     * La aplicación probada termina, de forma normal o inesperada. Campos: ver {@code
     * contratos/catalogos/eventos.yaml}.
     */
    APP_TERMINADA,
    /**
     * El usuario eligió un componente en el modo de inspección. Campos: ver {@code
     * contratos/catalogos/eventos.yaml}.
     */
    COMPONENTE_INSPECCIONADO,
    /**
     * El usuario hizo una acción que se grabó como paso. Campos: ver {@code
     * contratos/catalogos/eventos.yaml}.
     */
    ACCION_GRABADA
}
