package io.github.physanto.opsecengine.dominio.caso;

/** Enfoque de prueba: el tipo de calidad que una suite o un generador de casos busca evaluar. */
public enum Enfoque {
    /** Comprueba que las funcionalidades hacen lo que se espera. */
    FUNCIONAL,
    /** Comprueba que los datos de entrada inválidos se rechazan correctamente. */
    VALIDACION,
    /** Busca fallos de seguridad como inyección, falta de autorización o fuga de datos. */
    SEGURIDAD,
    /** Mide tiempos de respuesta y detecta interfaces congeladas. */
    RENDIMIENTO,
    /** Comprueba el comportamiento ante fallos, cierres y condiciones inesperadas. */
    ROBUSTEZ,
    /** Comprueba nombres accesibles, foco y uso con teclado. */
    ACCESIBILIDAD,
    /** Repite casos conocidos para detectar cambios de comportamiento entre versiones. */
    REGRESION
}
