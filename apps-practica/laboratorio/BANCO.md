# Banco de evaluación (issue #66)

`BancoEvaluacion.java` ejecuta una lista declarada de aplicaciones, versiones y
suites a través de la CLI. Es un programa Java 21, sin shell ni comandos del
sistema operativo. Usa el núcleo que ya trae el JAR de la CLI para validar los
proyectos y las suites, y Jackson de ese mismo JAR para leer los informes.

El banco inicial (`banco.json`) contiene la app disponible en el repo y sus cuatro
variantes evaluables: F6 y F8, vulnerables y corregidas. El registro admite otras
apps y versiones mediante más escenarios; no se han incorporado apps externas
ni se atribuyen resultados a oráculos todavía no implementados. La ampliación del
conjunto de apps corresponde a #70 y la detección completa de v0.3 a #67.
La [preparación de #70](AMPLIACION-BANCO.md) añade el campo opcional `inventario`
y las tablas de cobertura GUI, conservando los escenarios y métricas originales.

## Ejecutar en Windows, Linux y macOS

Desde la raíz del repo, con JDK 21 o superior y una pantalla disponible:

```sh
./mvnw -B -pl opsec-cli -am package -DskipTests

java --source 21 --class-path opsec-cli/target/opsec-0.1.0-SNAPSHOT.jar \
  apps-practica/laboratorio/BancoEvaluacion.java ejecutar \
  apps-practica/laboratorio/banco.json --salida demo/resultados/banco-001
```

El bloque sirve en Git Bash, Linux y macOS. En PowerShell se escribe el comando
`java` en una sola línea y se usa `./mvnw.cmd` para Maven. Las rutas se pasan como
argumentos a `ProcessBuilder`; se admiten espacios sin construir comandos de shell.
El script usa la misma JVM que lo ejecuta para iniciar el motor.

Para medir de nuevo, elija otra carpeta (`banco-002`, por ejemplo). El programa
exige que la carpeta de salida esté vacía y conserva cada ejecución por separado.

## Registro de aplicaciones y verdad de referencia

Cada escenario declara:

| Campo | Significado |
|---|---|
| `id` | Identificador único para los archivos: letras ASCII, números, guiones y guiones bajos. |
| `aplicacion`, `version` | Etiquetas de la app y la variante que se está midiendo. |
| `proyecto` | YAML del proyecto con JAR, argumentos JVM y datos de esa versión. |
| `suite` | YAML de la suite que se ejecutará. |
| `esperados` | Mapa de todos los IDs de caso a sus oráculos esperados; `[]` para controles sin fallos. |

Las rutas de proyecto y suite son relativas a la carpeta de `banco.json`, o
absolutas. `tiempoMaximoSegundos` limita cada proceso de la CLI; si se omite o vale
0, el límite es 120 segundos. Se admiten valores de 1 a 3600.

La referencia se escribe a partir de los fallos sembrados, antes de medir. Por
ejemplo, `LOGIN-ADMIN` espera `CONTRASENA-EN-CONSOLA` con F6 activo y `[]` cuando
F6 está corregido. No se deduce la referencia del veredicto que produjo el motor.
El programa exige que se declaren todos los casos de la suite para evitar tratar
casos olvidados como controles negativos. La clave opcional `$suite` declara los
oráculos esperados de nivel suite; si falta, se espera que no produzcan hallazgos.

## Métricas y archivos

- **Detectados:** pares caso-oráculo esperados que aparecen con confianza
  `CONFIRMADO` o `PROBABLE`. Varios eventos del mismo oráculo en un caso cuentan una
  vez. Los hallazgos `INFO` no se cuentan como detecciones.
- **Falsos positivos:** oráculos observados que no estaban en la referencia de ese
  caso, incluidos los controles corregidos.
- **No detectados:** oráculos esperados ausentes en casos concluyentes. Los casos
  no concluyentes o incompletos sin la detección esperada se contabilizan aparte;
  no se convierten en falsos negativos ni en éxitos.
- **Tiempo del proceso:** milisegundos medidos con reloj monótono desde el arranque
  de la CLI hasta su salida; incluye el motor, el agente, la app y los informes.
- **Tiempo de casos:** suma de las duraciones que el motor guardó en el JSON.

Cada ejecución deja:

```text
banco-001/
├── banco.json                 referencia utilizada, sin modificar
├── ejecucion.json             JDK, sistema, códigos, tiempos y hashes SHA-256
├── tabla.csv                  resultados para análisis
├── tabla.md                   misma tabla para revisión
└── <id>/
    ├── cli.log
    ├── cli-errores.log
    └── resultados/<suite>/    resultado.json y logs de la app
```

El registro conserva hashes de la CLI, la app, el proyecto, la suite, la
configuración y cada informe. Guarde también el commit del repo y los artefactos
de las apps si necesita repetir la ejecución física. La carpeta
`demo/resultados/` está ignorada por Git: publicar resultados se decide aparte.

Un error de arranque, un timeout, un informe ausente/alterado o una inconsistencia
entre los códigos y los veredictos produce una fila `ERROR`: sus métricas de
detección quedan vacías. No se registra un cero que pudiera interpretarse como una
medición válida. Los errores de configuración previos a la ejecución detienen el
banco antes de abrir las apps.

| Código del banco | Significado |
|---|---|
| 0 | Todas las mediciones coinciden con la referencia, también las vulnerables detectadas. |
| 1 | Configuración inválida o errores de ejecución/informes. |
| 3 | Hay falsos positivos o detecciones ausentes en casos concluyentes. |
| 4 | Sin errores ni regresiones concluyentes, pero quedan casos no concluyentes. |

El código 3 de la CLI en una variante vulnerable es esperado; no implica un error
del banco si detectó el fallo correcto. Si hay varios problemas, los errores
tienen prioridad sobre las regresiones, y estas sobre los no concluyentes.

## Reproducir la tabla sin volver a ejecutar las apps

```sh
java --source 21 --class-path opsec-cli/target/opsec-0.1.0-SNAPSHOT.jar \
  apps-practica/laboratorio/BancoEvaluacion.java resumir demo/resultados/banco-001
```

`resumir` usa los informes y los tiempos ya guardados. Comprueba sus hashes y
regenera `tabla.csv` y `tabla.md` en UTF-8 con finales LF y orden fijo. Las tablas
se reproducen byte a byte desde la misma evidencia. Una ejecución nueva mide
tiempos nuevos: no se promete igualdad de tiempos entre máquinas ni entre corridas.

## Ejecutar con el Docker del laboratorio y Xvfb

Construya la imagen desde la raíz. Los comandos siguientes sirven en Git Bash,
Linux y macOS; en PowerShell se omite `MSYS_NO_PATHCONV=1` y se usa una sola línea.

```sh
docker build -f apps-practica/laboratorio/Dockerfile -t opsec-laboratorio:local .
docker volume create opsec-resultados

MSYS_NO_PATHCONV=1 docker run --rm --entrypoint /usr/bin/tini \
  --mount type=volume,source=opsec-resultados,target=/resultados \
  opsec-laboratorio:local -- xvfb-run --auto-servernum \
  java --source 21 --class-path /opt/opsec/opsec.jar \
  /opt/opsec/apps-practica/laboratorio/BancoEvaluacion.java ejecutar \
  /opt/opsec/apps-practica/laboratorio/banco.json --salida /resultados/banco-001

MSYS_NO_PATHCONV=1 docker run --rm --entrypoint java \
  --mount type=volume,source=opsec-resultados,target=/resultados \
  opsec-laboratorio:local --source 21 --class-path /opt/opsec/opsec.jar \
  /opt/opsec/apps-practica/laboratorio/BancoEvaluacion.java resumir /resultados/banco-001

MSYS_NO_PATHCONV=1 docker run --rm --entrypoint cat \
  --mount type=volume,source=opsec-resultados,target=/resultados,readonly \
  opsec-laboratorio:local /resultados/banco-001/tabla.md
```

## Pruebas y aceptación

`BancoEvaluacionIT`, en `opsec-cli`, prueba el programa fuente real: cálculos de
detección, duplicados, falsos positivos, no detectados, no concluyentes, errores,
informes alterados, rutas con espacios y tablas idénticas al reconstruirlas. La
prueba con GUI ejecuta las cuatro variantes reales y comprueba que una carpeta con
resultados no se sobrescriba. Se ejecuta con `./mvnw -B -pl opsec-cli -am verify`;
la parte con ventanas se omite sin pantalla. En Linux se usa Xvfb.

La aceptación funcional exige ejecutar los escenarios, conservar la tabla con sus
informes y reproducirla desde esos mismos archivos. La Definición de Terminado
también requiere CI verde, PR aprobado e integrado y tablero en *Hecho*: esas
acciones del equipo se comprueban por separado de las pruebas locales.

Verificación realizada el 11 de noviembre de 2026:

| Comprobación | Resultado observado |
|---|---|
| Banco real en Windows con JDK 27 | Cuatro variantes medidas; 2 detecciones esperadas, 0 falsos positivos y 0 no detectados. |
| `BancoEvaluacionIT` en Windows | 4 pruebas, 0 fallos, 0 errores, 0 omitidas. |
| Banco real en el Docker del laboratorio, JDK 21.0.13 y Xvfb | Cuatro variantes medidas con el mismo resultado de detección. |
| Reconstrucción dentro de Docker | CSV y Markdown conservan sus hashes SHA-256. |
| Reconstrucción de los resultados Linux en Windows | CSV y Markdown idénticos byte a byte. |
| `./mvnw -B verify` completo en Linux, JDK 21 y Xvfb | `BUILD SUCCESS`; las 4 pruebas del banco pasan sin omisiones. |

Los resultados locales conservados están en `demo/resultados/banco-issue66-windows/`
y `demo/resultados/banco-issue66-linux/`. En el volumen `opsec-resultados` también
permanece `/resultados/banco-issue66-linux/`. La evidencia de la verificación Linux
está en `target/issue66/evidencia-ci/`. Estos archivos generados no se agregan a Git.
Durante la comprobación se corrigió la salida de consola UTF-8 en Windows; las
tablas y los procesos Java usan la codificación indicada explícitamente.

Referencia de la ejecución como archivo fuente:
[Java 21, source-file mode](https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html#using-source-file-mode-to-launch-single-file-source-code-programs).
