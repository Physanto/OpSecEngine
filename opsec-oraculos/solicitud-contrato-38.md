# [Contrato] Evaluación explícita de entradas inválidas (#38)

Estado: propuesta para revisión del líder; no enviada a GitHub ni aprobada.

## Contratos afectados

- `contratos/catalogos/oraculos.yaml` y `contratos/catalogos/cwe.yaml`.
- `opsec-dominio`: interfaz `Oraculo` y definición compartida de la política de validación.
- Esquema y ejemplos de configuración de esa política, si el líder decide exponerla en YAML.

## Cambio propuesto

1. Reservar `ENTRADA-INVALIDA-ACEPTADA`, momento `CASO`, asociado a `CWE-20`. Su regla detecta
   una señal explícita de aceptación después de escribir y enviar un valor declarado inválido
   por la política del caso. No considera que un paso `OK` pruebe aceptación de la entrada.
2. Incorporar `CWE-20`, «Validación incorrecta de entradas», al catálogo de debilidades.
3. Definir una política por caso con estos datos: `casoId`, `pasoEntrada`, `pasoEnvio`,
   `valorInvalido`, `aceptacion` y `rechazo`. Cada señal contiene `tipo` y `camposIguales`.
   Las señales usan eventos existentes `DIALOGO`, `SESION` o `ESTADO`; dentro de una señal se
   exigen todas las igualdades, y las señales de cada lista son alternativas. Los valores se
   comparan sin convertir cadenas en números o booleanos. El paso de entrada debe ser `LLENAR`
   y el envío posterior, `PULSAR`. No se necesitan tipos de evento nuevos.
4. Añadir a `Oraculo` un método predeterminado `evaluarResultado(Traza, Contexto)` que devuelva
   `Veredicto`. Su implementación predeterminada conserva la evaluación actual de hallazgos;
   el nuevo oráculo lo especializa para devolver `NO_CONCLUYENTE` si falta evidencia. Mantener
   `evaluar(Traza, Contexto)` para los consumidores existentes y adaptar el núcleo para conservar
   el resultado completo. El nuevo oráculo no debe integrarse únicamente mediante el método
   antiguo, porque una lista vacía perdería la distinción entre rechazo observado e incertidumbre.

## Motivo y semántica

La biblioteca genera entradas de distintas clases; no todas son inválidas para cualquier app.
La política debe declarar la restricción y las señales observables. La ausencia de una excepción,
de un diálogo o de hallazgos no demuestra un rechazo correcto.

- Aceptación observada: `NO_CUMPLE` con un hallazgo `PROBABLE` y secuencias como evidencia.
  Confirmar la causalidad y el efecto real requiere comprobaciones adicionales del integrador.
- Rechazo explícito y envío terminado `OK`, sin aceptación observada: `CUMPLE` para esta propiedad.
- Política, escritura, envío o respuesta insuficientes o ambiguos: `NO_CONCLUYENTE`.
- Si la única aceptación atribuida al envío llega después de comenzar otra acción, el resultado
  es `NO_CONCLUYENTE`, incluso cuando antes hubo una señal de rechazo.
- Una aceptación no se descarta porque también haya un mensaje de rechazo o el envío quede incompleto.

## Compatibilidad y módulos afectados

Propuesta aditiva: nuevas entradas de catálogo y método predeterminado, sin eliminar la API actual.
Requiere revisar `opsec-dominio`, `opsec-nucleo`, `opsec-oraculos`, `contratos/`,
`opsec-soporte-pruebas` y `opsec-verificacion`. Hasta su aprobación no se cambia la versión del contrato.

## Adaptador local mientras tanto

`PoliticaValidacionTemporal` y `EvaluadorValidacionTemporal` viven solo en `opsec-oraculos`.
El evaluador se llama directamente al terminar el caso y devuelve `Veredicto`; no implementa
`Oraculo`, no se registra con `ServiceLoader` y no cambia el núcleo ni la CLI. Los ids y el CWE
de sus hallazgos son propuestas locales. La integración oficial debe conservar los tres estados
y reemplazar estos tipos temporales por el contrato aprobado.

Las pruebas incluyen una traza de aceptación y otra de rechazo, además de evidencia incompleta,
señales fuera de contexto, escritura sobrescrita y finalización fallida del envío.
