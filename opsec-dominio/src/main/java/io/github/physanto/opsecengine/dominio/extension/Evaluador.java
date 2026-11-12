package io.github.physanto.opsecengine.dominio.extension;

import io.github.physanto.opsecengine.dominio.resultado.Veredicto;

/**
 * Evalúa oráculos por evento, caso y suite mediante sesiones independientes de cada caso.
 *
 * <p>Contrato aditivo de la versión 1.3. Se obtiene de {@link ProveedorEvaluacion}; no se registra
 * directamente con {@link java.util.ServiceLoader}. Debe ser seguro entre hilos si los oráculos lo
 * son, como exige su contrato. El estado mutable pertenece a cada {@link EvaluacionCaso}.
 *
 * <p>La evaluación invoca {@link Oraculo#evaluarResultado}, conserva hallazgos informativos y
 * motivos no concluyentes y respeta la selección: un oráculo aplica si el caso lo pide por id o si
 * {@link Oraculo#aplica} devuelve true. Cada momento ejecuta únicamente sus propios oráculos. Los
 * errores de ejecución de un oráculo se convierten en motivos no concluyentes sin detener los
 * demás. Los errores de configuración corresponden a {@link ProveedorEvaluacion#crear}.
 */
public interface Evaluador {

    /**
     * Inicia una sesión de evaluación sin ejecutar todavía ningún oráculo.
     *
     * <p>Los ids pedidos por el caso y no registrados se conservan como motivos no concluyentes
     * para el veredicto final. Cada llamada devuelve una sesión nueva, incluso para el mismo caso.
     *
     * @param contexto contexto del caso, con {@link Contexto#caso()} no null
     * @return sesión independiente, sin eventos ni hallazgos; nunca null
     * @throws NullPointerException si el contexto o su caso son null
     */
    EvaluacionCaso iniciarCaso(Contexto contexto);

    /**
     * Evalúa exclusivamente los oráculos de momento {@link Momento#SUITE}.
     *
     * <p>Usa las trazas de {@link Contexto#trazasSuite()} y llama a los oráculos con traza null.
     * Devuelve el veredicto completo, incluidos errores de evaluación y falta de evidencia, sin
     * reducirlo a una lista de hallazgos. Si no hay oráculos aplicables devuelve CUMPLE.
     *
     * @param contexto contexto de suite, sin caso; no puede ser null
     * @return veredicto acumulado de los oráculos de suite; nunca null
     * @throws NullPointerException si el contexto es null
     * @throws IllegalArgumentException si el contexto contiene un caso
     */
    Veredicto evaluarSuite(Contexto contexto);
}
