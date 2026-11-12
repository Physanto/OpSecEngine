# [Contrato] Observaciones y resultados de calidad de GUI (#47)

Estado: propuesta para revisión del líder; no enviada a GitHub ni aprobada.

## Contratos afectados

Catálogo de oráculos, dominio y formato de observaciones de calidad; eventos del agente,
contexto y evaluación del núcleo. No se modifica ninguna de esas áreas en esta rama.

## Cambio propuesto

Reservar estos ids, sin CWE: `RESPUESTA-LENTA` para repeticiones comparables de una acción;
`VENTANA-NO-LIBERADA` para una observación final que aún conserva recursos nativos;
`CONTROL-SIN-ETIQUETA`, `ORDEN-TABULACION` y `ACCION-TECLADO-NO-ALCANZADA` para observaciones
de accesibilidad; `RESULTADO-ROBUSTEZ-INCORRECTO` para una señal explícita tras un escenario.
El primero necesita contexto de repeticiones de suite; los restantes, observaciones de caso.
El líder debe acordar sus momentos y representación compartida antes de registrarlos por SPI.

Las observaciones deben conservar proceso, sesión, caso y referencia de evidencia. Para ventanas
se necesita una observación final de `isDisplayable` después de solicitar liberación, con id
estable. `VENTANA_CERRADA` también significa ocultación y no demuestra liberación ni fuga de
memoria. Para accesibilidad se necesitan etiqueta realmente asociada, nombre accesible,
visibilidad, habilitación, ámbito interactivo y recorridos reales de foco y activación por
teclado, junto con la política esperada y una marca de observación completa.

Las mediciones `TIEMPO.RESPUESTA_ACCION` existentes bastan para calcular mediana y p95 si el
núcleo identifica repeticiones independientes de la misma acción bajo condiciones comparables.
Debe aportar mínimo de muestras y umbral, sin confundir reintentos con mediciones independientes.
`TIEMPO.EDT_BLOQUEADO` y su `umbralMs` explícito ya permiten el oráculo reservado
`INTERFAZ-CONGELADA`; no se necesita ampliar su contrato para esta implementación.

Para robustez, el ejecutor debe acreditar la realización del doble clic, cierre o fallo inyectado
y delimitar los pasos del escenario. La inyección de errores de red o base de datos corresponde
al agente; el evaluador solo observa sus efectos. La política aporta señales distintas de
respuesta controlada y resultado incorrecto, sin fijar mensajes de una aplicación.

## Adaptación local y compatibilidad

`EvaluadorTiemposRespuesta`, `EvaluadorCalidadGui` y `EvaluadorEscenarioRobustez` son APIs Java
locales. No implementan `Oraculo` ni se registran por SPI. Devuelven `Veredicto` completo para
conservar `NO_CONCLUYENTE` ante datos ausentes o ambiguos. Los ids propuestos solo aparecen
en esos resultados locales; no son nuevas reservas del catálogo. Sus observaciones de GUI
referencian un registro local aportado por el llamador, sin inventar eventos del protocolo.

La propuesta es aditiva. El líder debe formalizar la captura y el transporte de observaciones,
el agrupamiento de repeticiones y la conservación de los tres estados al integrar la evaluación,
coordinando dominio, agente, núcleo, contratos y salidas. No hay instrumentación, inyección
de fallos ni conexión nueva a la CLI en esta entrega del módulo de oráculos.
