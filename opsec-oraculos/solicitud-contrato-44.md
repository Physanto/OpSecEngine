# [Contrato] Payloads y política de autenticación (#44)

Estado: propuesta para revisión del líder; no enviada a GitHub ni aprobada.

## Cambio propuesto

Formalizar una política por caso que identifique los payloads de credenciales materializados,
sus pasos de escritura de usuario y contraseña y el paso de envío. Cada payload debe indicar
si se declara débil. La política debe aportar N fallos consecutivos, un retardo mínimo positivo
y señales explícitas y distintas de rechazo de credenciales y bloqueo. El integrador garantiza
que los intentos comparten formulario y ámbito de bloqueo, sin reinicios que borren el contador.

Los catálogos reservan `SIN-BLOQUEO` y `CREDENCIALES-DEBILES`, pero no definen la transmisión de
estos datos desde la biblioteca de ataques ni los umbrales de protección de la aplicación.
La propuesta es aditiva y afectaría a dominio, esquema de suite, núcleo, oráculos y generación
de payloads. Oráculos no debe depender del módulo de ataques. Los eventos existentes bastan;
la aceptación se prueba mediante `SESION` con `activa=true`, no con un paso terminado en `OK`.

## Adaptación local

`PoliticaAutenticacion` y su record `Intento` viven únicamente en `opsec-oraculos`. El llamador
aporta una instancia Java en `Caso.extensiones().get("autenticacion")`; un mapa YAML no se
convierte automáticamente en ella. Se reutiliza `PoliticaValidacionTemporal.Senal` para las
igualdades sobre eventos, sin modificar la implementación de la #38.

El rastreador exige escrituras correctas de los valores declarados, sin sobrescrituras, y un
envío único terminado en `OK`. Una sesión iniciada con un payload declarado débil produce
un hallazgo confirmado de CWE-521. N rechazos seguidos del mismo usuario, seguidos por una
sesión iniciada antes del retardo mínimo y sin protección observada, producen un hallazgo
probable de CWE-307. No demuestra que falte protección en todos los escenarios posibles.

Sin configuración o evidencia suficiente no hay hallazgos; el `CUMPLE` del evaluador existente
no acredita entonces una política de autenticación segura. El líder debe definir el formato
compartido, conectar la biblioteca y el núcleo, y emitir señales fiables de sesión. Esta
implementación no cambia contratos ni instrumenta la aplicación.
