# Comprobación de integración v0.3 (issue #67)

Estado al 11 de noviembre de 2026: **pendiente por dependencias del motor**.
El criterio exige que todos los fallos sembrados produzcan `NO_CUMPLE` en su
variante vulnerable y `CUMPLE` al corregirse. Actualmente se ha comprobado ese par
para F6 y F8. El resto no se declara aprobado.

## Evidencia y alcance

- En la rama local y en `main` de GitHub hay dos implementaciones de oráculo:
  `OraculoContrasenaEnConsola` y `OraculoExcepcionNoCapturada`. El registro de
  `ServiceLoader` local contiene esas mismas dos clases.
- Las cuatro mediciones del [banco de evaluación](BANCO.md) se verificaron leyendo
  sus informes originales: F6 y F8 vulnerables dan `NO_CUMPLE`/código 3; sus
  controles corregidos dan `CUMPLE`/código 0.
- Se ejecutó adicionalmente el ejemplo F1 con el banco. La CLI devolvió código 4 y
  el JSON de `F1-CASO` contiene `NO_CONCLUYENTE` con el motivo:
  `El caso pide el oráculo CREDENCIALES-DEBILES, que no está registrado`.
- El banco registró ese diagnóstico como no concluyente. No se interpreta como
  ausencia del fallo ni como un resultado de aceptación.

Los resultados locales están en `demo/resultados/banco-issue66-linux/` y
`demo/resultados/integracion-v03-diagnostico-f1/`; no se agregan a Git.
El diagnóstico adicional usó `target/issue67/banco-diagnostico.json`, con el
proyecto y la suite existentes de F1 y un límite de 30 segundos. No se modificó
el registro del motor ni se sustituyeron sus oráculos.

## Matriz de aceptación

«No ejecutada» significa que no se le atribuye un veredicto observado a esa
variante. Los bloqueos se comprobaron mediante el código y las issues abiertas;
no se infieren resultados de ejecución para cubrir las celdas vacías.

| Fallo | Vulnerable observada | Corregida observada | Estado y dependencia |
|---|---|---|---|
| F1: credenciales embebidas | `NO_CONCLUYENTE`, código 4 | No ejecutada | Falta `CREDENCIALES-DEBILES` (#44). El candidato dinámico demuestra debilidad de credenciales (CWE-521), no por sí solo que estén embebidas (CWE-798); véase `FALLOS.md`. |
| F2: Local sin sesión | No ejecutada | No ejecutada | Faltan `SIN-SESION` (#40), ganchos JDBC (#13) y localización del campo Uid (#9). |
| F3: eliminación directa sin sesión | No ejecutada | No ejecutada | Mismas dependencias de F2, con `EliminarPersona` como punto de entrada. |
| F4: SQL concatenado | No ejecutada | No ejecutada | Faltan `SQLI-CANARIO` (#42), ganchos JDBC (#13) y configuración/rastreo de canarios (#15). |
| F5: enumeración de usuarios | No ejecutada | No ejecutada | Falta `ENUMERACION-USUARIOS` (#43) y comprobar su evaluación a nivel de suite. |
| F6: contraseña en consola | `NO_CUMPLE`, código 3 | `CUMPLE`, código 0 | Par comprobado con el banco y la app real. |
| F7: traza en diálogo | No ejecutada | No ejecutada | Falta `TRAZA-EN-DIALOGO` (#34). |
| F8: excepción no capturada | `NO_CUMPLE`, código 3 | `CUMPLE`, código 0 | Par comprobado con el banco y la app real. |
| F9: validación del registro | No ejecutada | No ejecutada | Suite preparada con registro inválido y control válido; falta el oráculo de #38 y su identificador/contrato acordado. |
| F10: modificación sin autorización | No ejecutada | No ejecutada | Suite y política preparadas; faltan autorización diferencial (#41), el contexto por rol y los eventos de operaciones sensibles para comprobarlas de extremo a extremo. |

La ausencia de un proveedor registrado impide que el evaluador produzca el
hallazgo de ese oráculo. Que su ID esté reservado en el catálogo no significa que
su implementación esté disponible. Las pruebas unitarias de los interruptores y
servicios de la app tampoco sustituyen esta integración con el motor.

## Comprobación necesaria para los oráculos de suite

La lectura del código actual de `ComandoEjecutar` muestra que el resumen y el código
de salida se calculan con los veredictos de los casos. Los `hallazgosSuite` se
imprimen por separado. Todavía no se ha probado de extremo a extremo esa ruta con
un oráculo real de suite, porque no hay uno registrado.

Al integrar enumeración y autorización diferencial se debe comprobar que sus
hallazgos se propaguen al veredicto y al código de salida requerido. Un caso o una
CLI en `CUMPLE` con un hallazgo de suite confirmado no sirve para declarar
satisfecho el criterio de #67. Esta comprobación corresponde coordinarla con el
evaluador de #32 y el dueño de la CLI.

## Preparación disponible para la siguiente integración

`integracion-v03/matriz.json` registra los diez fallos con sus proyectos y suites
vulnerables/corregidos: **20 variantes**. Los perfiles aíslan cada fallo, salvo F7,
que conserva F4 en ambas variantes para provocar el mismo error SQL y comprobar
si se expone su traza. F3 mantiene el `main` de `EliminarPersona` en ambas versiones.
Al añadir F11–F13 en #69, los perfiles v0.3 los corrigen explícitamente para
mantener aislados los fallos originales; la matriz continúa teniendo veinte variantes.

F4 conserva el marcador literal `OPSEC-F4`; todavía no es un canario registrado
por el motor. Al integrar #15/#42 se debe ajustar ese caso al mecanismo real de
configuración y rastreo de canarios, sin atribuir detección al marcador por sí solo.

Las variantes corregidas de F1, F2, F3, F4, F5 y F7 tienen pasos propios para
observar el rechazo o el mensaje seguro. F2/F3 corregidos no intentan borrar tras
una denegación: comprueban el aviso y la vuelta al login o la portada. Los flujos
con diálogos usan `EVENTOS` y los que cierran un diálogo usan `cerrar_ventana`;
necesitan las acciones del agente de #3/#4. Esas suites están preparadas según el
contrato, pero validar su YAML no demuestra que el agente actual las reproduzca.

F9 incluye registro inválido y un control válido. Su oráculo permanece `null` en
la matriz, con referencia a #38; no se ha inventado un ID. Al acordarlo se debe
completar ese campo y añadirlo a `oraculos` de **ambos** casos de la suite. F10
incluye controles de administrador y cliente, login por rol y política de UPDATE.
F9/F10 esperan el inicio de sesión por rol de #20; la política de F10 también debe
contrastarse con la interpretación que implemente el oráculo de #41.

`IntegracionV03IT` valida normalmente los veinte proyectos/suites, los IDs de
caso, los interruptores y los proveedores requeridos. Genera en
`opsec-cli/target/integracion-v03/`:

- `preparacion.json`: oráculos encontrados y bloqueos, sin atribuir veredictos a
  variantes no ejecutadas.
- `banco-disponible.json`: pares F6/F8 preparados para el motor actual.

Para comprobar esta preparación, desde la raíz en Git Bash:

```sh
./mvnw -B -pl opsec-cli -am -Dit.test=IntegracionV03IT \
  -Dfailsafe.failIfNoSpecifiedTests=false verify

java --source 21 --class-path opsec-cli/target/opsec-0.1.0-SNAPSHOT.jar \
  apps-practica/laboratorio/BancoEvaluacion.java ejecutar \
  opsec-cli/target/integracion-v03/banco-disponible.json \
  --salida demo/resultados/integracion-v03-disponible
```

La prueba de aceptación completa se omite intencionalmente en el modo normal,
porque todavía faltan dependencias. Para activarla explícitamente con pantalla
(en Linux anteponer `xvfb-run --auto-servernum`):

```sh
./mvnw -B -pl opsec-cli -am -Dit.test=IntegracionV03IT \
  -Dfailsafe.failIfNoSpecifiedTests=false -Dopsec.integracion.v03=true verify
```

Actualmente debe fallar **antes de abrir las apps**, enumerando los oráculos
pendientes y el ID de F9 sin acordar. No reduce la matriz a los fallos disponibles
para conseguir un resultado verde. Cuando estén todas las dependencias, generará
el banco completo, ejecutará las veinte variantes y exigirá códigos CLI 3/0,
además de los hallazgos y controles que comprueba el banco. Si un flujo o un
veredicto todavía no cumple el contrato, la aceptación fallará y conservará sus
logs en `opsec-cli/target/integracion-v03/` para corregirlo con el motor integrado.
Antes de cerrar la issue, la aceptación completa debe quedar activada en la CI
acordada con el líder, sin omitir esa prueba. Un build normal que solo valida la
preparación no acredita el cierre de #67.

## Verificación de esta preparación

- `verify` completo en Windows con JDK 27: `BUILD SUCCESS`, sin fallos ni errores.
  `IntegracionV03IT` ejecutó sus dos pruebas de preparación; su única omisión es
  la aceptación completa, todavía pendiente de dependencias.
- Los veinte proyectos y sus suites validaron con el cargador real del motor,
  incluidos el aislamiento de interruptores y los IDs de casos comparables.
- El banco generado para F6/F8 ejecutó cuatro variantes reales: ambas vulnerables
  dieron `NO_CUMPLE`/código 3 y ambas corregidas `CUMPLE`/código 0, sin falsos
  positivos ni falsos negativos. Evidencia local:
  `demo/resultados/integracion-v03-disponible/`.
- Se activó expresamente la aceptación completa para comprobar su bloqueo:
  Maven terminó con código 1 y una sola aserción fallida que enumera F1, F2, F3,
  F4, F5, F7, F9 y F10. Es el rechazo esperado de cobertura incompleta, no una
  ejecución de esos ocho pares ni un resultado de aceptación.
- La imagen `opsec-laboratorio:local` se construyó y se comprobó dentro del
  contenedor que incorpora la matriz de diez fallos. El intento adicional de
  preparar la verificación completa en Linux/Xvfb se interrumpió por resolución
  DNS de `auth.docker.io`; no se declara ejecutada esa verificación en Linux.

Los XML de preparación y del rechazo controlado se conservaron en
`target/issue67/evidencia/`. Los informes del `verify` completo de Windows están
en las carpetas `target/surefire-reports` y `target/failsafe-reports` de cada
módulo. Estos datos generados permanecen fuera de Git.

## Condiciones para cerrar #67

1. Integrar los oráculos y las capacidades del agente/núcleo anteriores, con sus
   contratos acordados. Las issues de oráculos consultadas siguen abiertas y
   pertenecen a otro módulo; podrían tener trabajo en ramas aún no integrado.
2. Ajustar las suites y proyectos preparados a las implementaciones integradas,
   incluida la definición de validación de F9 y la política/comparación de roles
   de F10.
3. Activar la aceptación completa para generar y ejecutar el banco de veinte
   escenarios, con referencia independiente por caso y, cuando corresponda, por
   suite (`$suite`). Conservar los informes, los hashes y la tabla.
4. Exigir el hallazgo esperado y `NO_CUMPLE` en cada vulnerable; `CUMPLE` sin ese
   hallazgo en cada corregida. Un `NO_CONCLUYENTE`, un timeout o un hallazgo de otro
   oráculo no cumple la aceptación.
5. Completar la Definición de Terminado: pruebas, CI, revisión e integración por PR
   aprobado y estado del tablero. Este informe no marca la issue como terminada.

Dependencias consultadas:
[#9](https://github.com/Physanto/OpSecEngine/issues/9),
[#13](https://github.com/Physanto/OpSecEngine/issues/13),
[#15](https://github.com/Physanto/OpSecEngine/issues/15),
[#32](https://github.com/Physanto/OpSecEngine/issues/32),
[#34](https://github.com/Physanto/OpSecEngine/issues/34),
[#38](https://github.com/Physanto/OpSecEngine/issues/38),
[#40](https://github.com/Physanto/OpSecEngine/issues/40),
[#41](https://github.com/Physanto/OpSecEngine/issues/41),
[#42](https://github.com/Physanto/OpSecEngine/issues/42),
[#43](https://github.com/Physanto/OpSecEngine/issues/43),
[#44](https://github.com/Physanto/OpSecEngine/issues/44).
