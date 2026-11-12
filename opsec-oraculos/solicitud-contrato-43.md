# [Contrato] Comparación de intentos de login (#43)

Estado: propuesta para revisión del líder; no enviada a GitHub ni aprobada.

## Cambio propuesto

Añadir a la configuración de suite una comparación explícita de dos escenarios: usuario
inexistente y cuenta existente con contraseña incorrecta. Cada referencia debe indicar el id
del caso y el número del paso que envía el login. El integrador garantiza que los intentos usan
el mismo formulario y que los datos de prueba corresponden a esos escenarios.

El catálogo reserva `ENUMERACION-USUARIOS`, pero los contratos no identifican qué intentos son
comparables. Los ids, las descripciones y los mensajes de una app no son una clasificación
general fiable. La propuesta es aditiva y afectaría a dominio, esquema de suite, núcleo,
oráculos y ejemplos de soporte. No requiere nuevos tipos de evento.

## Adaptador local

`ComparacionEnumeracionUsuarios` vive únicamente en `opsec-oraculos`. El llamador aporta una
instancia Java en `Suite.extensiones().get("enumeracionUsuarios")`. Para la traza oficial:
`new ComparacionEnumeracionUsuarios("C01", 5, "C02", 5)`.
Un mapa cargado de YAML no se convierte automáticamente en ese record. No se modifican
contratos ni el núcleo; el líder debe definir el formato compartido e integrar su carga.

El oráculo compara los textos ordenados de `DIALOGO` entre el `PASO_INICIO` del envío `PULSAR`
y su `PASO_FIN` con `OK`, sin sesión activa. Diferencias producen un hallazgo `PROBABLE`;
respuestas iguales no producen hallazgos. Si faltan respuestas completas, se abstiene.
El contrato actual solo devuelve hallazgos: esa abstención no constituye una comprobación
de ausencia de enumeración, aunque el evaluador existente devuelva `CUMPLE` sin hallazgos.
