package io.github.physanto.opsecengine.protocolo;

/**
 * Tipo de un {@link Mensaje}, que indica su dirección y el formato de su cuerpo.
 *
 * <p>Los mensajes que responden a otro llevan en {@link Mensaje#getCorrelacion()} el id del mensaje
 * al que responden.
 */
public enum TipoMensaje {
    /**
     * Agente → motor. Primer mensaje de la conexión, con el token de sesión. Cuerpo: {@code
     * HolaDto}.
     */
    HOLA,
    /** Motor → agente. Pide ejecutar un paso. Cuerpo: {@code PasoDto}. */
    EJECUTAR_PASO,
    /**
     * Agente → motor. Responde a {@link #EJECUTAR_PASO} con el resultado. Cuerpo: {@code
     * ResultadoPasoDto}.
     */
    RESULTADO_PASO,
    /**
     * Agente → motor. Informa algo observado en la aplicación, sin que se haya pedido. Cuerpo:
     * {@code EventoDto}.
     */
    EVENTO,
    /** Motor → agente. Pide una captura de una ventana. Cuerpo: {@code SolicitudCapturaDto}. */
    CAPTURAR,
    /** Agente → motor. Responde a {@link #CAPTURAR} con la imagen. Cuerpo: {@code CapturaDto}. */
    CAPTURA,
    /**
     * Motor → agente. Pide el árbol de componentes de las ventanas visibles. Cuerpo: objeto vacío.
     */
    ARBOL_SOLICITUD,
    /**
     * Agente → motor. Responde a {@link #ARBOL_SOLICITUD} con el árbol. Cuerpo: {@code ArbolDto}.
     */
    ARBOL,
    /**
     * Motor → agente. Configura los ganchos: operaciones sensibles, canarios y fallos a inyectar.
     * Cuerpo: {@code ConfigGanchosDto}.
     */
    CONFIG_GANCHOS,
    /**
     * Motor → agente. Activa o desactiva el modo de inspección o de grabación. El agente responde
     * {@link #RESULTADO_PASO} con {@code OK} cuando el modo ya está vigente, o con {@code ERROR} si
     * no puede activarlo (por ejemplo, sin entorno gráfico). Cuerpo: {@code ModoInteraccionDto}.
     */
    MODO_INTERACCION,
    /** Motor → agente. Pide cerrar la aplicación de forma ordenada. Cuerpo: objeto vacío. */
    CERRAR,
    /**
     * Cualquier dirección. Informa que un mensaje no se pudo atender, por ejemplo por un token
     * inválido o un cuerpo mal formado. Cuerpo: {@code ErrorDto}.
     */
    ERROR
}
