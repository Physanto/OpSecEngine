# [Contrato] Declaración del canario de un caso (#42)

Estado: propuesta para revisión del líder; no enviada a GitHub ni aprobada.

## Contratos afectados y cambio propuesto

Formalizar en `Caso` y en `contratos/esquemas/suite.schema.json` la asociación entre un caso y
el canario materializado de su payload. El cambio sería aditivo: un campo opcional con la marca
exacta, no una plantilla. Los eventos existentes bastan para rastrearla desde `LLENAR` hasta SQL
o Firestore; no se proponen campos ni tipos de evento nuevos.

## Motivo y módulos afectados

El catálogo define `SQLI-CANARIO` y `FIRESTORE-RUTA-CANARIO`, pero no dónde recibe el oráculo
la identidad del canario. Inferirla de cualquier valor escrito confundiría datos normales con
marcas de prueba. El generador o ejecutor debe conservar la marca al materializar el payload.
La definición compartida afectaría al dominio, contratos, núcleo y oráculos. Ataques seguiría
aportando plantillas, sin convertirse en una dependencia de producción de oráculos.

## Adaptación local

Hasta su aprobación, los oráculos leen `Caso.extensiones.validacion.canario`, la convención
local usada por las #37 y #39. Exigen una cadena de al menos cuatro caracteres, no vacía ni con
llaves de plantilla, y haberla observado antes en el valor de un `LLENAR` del mismo caso.
La prueba de C13 declara explícitamente `K7Q2` en ese contexto. No se infieren marcas desde la
traza ni se expande la biblioteca de ataques. El líder debe definir la ubicación definitiva y
conectar su propagación en el núcleo; esta implementación no modifica áreas protegidas.
