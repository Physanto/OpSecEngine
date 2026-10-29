package io.github.physanto.opsecengine.protocolo.dto;

/** Código con que el agente informa cómo terminó un paso. */
public enum CodigoResultado {
    /** El paso se ejecutó correctamente. */
    OK,
    /** Ningún criterio del localizador encontró un componente. */
    NO_ENCONTRADO,
    /** El componente existe, pero no está visible, no está habilitado o lo tapa un diálogo. */
    NO_ALCANZABLE,
    /** El paso no terminó dentro de su espera máxima. */
    TIEMPO_AGOTADO,
    /** Ocurrió un error inesperado al ejecutar el paso; el detalle lo explica. */
    ERROR
}
