# Laboratorio Docker con Xvfb (issue #64)

La imagen construye la CLI, su agente y `clientes-vulnerable` con JDK 21. Al
ejecutarla, Xvfb proporciona la pantalla virtual que necesitan Swing y AWT y la
CLI controla la app mediante el agente. Tini gestiona los procesos y señales del
contenedor, por lo que no es necesario añadir `--init` a `docker run`.
No se necesita una pantalla del equipo
anfitrión ni configurar `DISPLAY` fuera del contenedor.

Se reutilizan los proyectos y la suite de `demo/`: F6 escribe una contraseña de
prueba en la consola y el oráculo `CONTRASENA-EN-CONSOLA` decide el veredicto.
El informe actual es `resultado.json`, con traza y hallazgos; no se genera HTML.

El [banco de evaluación (#66)](BANCO.md) ejecuta listas de aplicaciones y versiones
desde Java y genera tablas CSV/Markdown de detecciones, falsos positivos y tiempos.

La [preparación de integración v0.3 (#67)](INTEGRACION-V03.md) contiene los veinte
perfiles de F1–F10 y una aceptación optativa que exige todas las dependencias.

## Requisitos y construcción

- Docker con soporte para contenedores Linux (en Windows, Docker Desktop iniciado
  en modo de contenedores Linux).
- Acceso a Internet durante `docker build` para descargar la imagen base, paquetes
  de Ubuntu, Maven y las dependencias. La demo empaquetada no necesita Internet.

Desde la **raíz del repositorio**, en Git Bash, Linux o macOS:

```sh
docker build -f apps-practica/laboratorio/Dockerfile -t opsec-laboratorio:local .
```

El contexto es el repo completo (`.`). `Dockerfile.dockerignore`, junto al
Dockerfile, limita los archivos enviados y excluye los JAR y `target/` locales.
Así la imagen compila sus propios artefactos aunque el equipo anfitrión use otra
versión de Java. La etapa final contiene solo el JDK, las bibliotecas gráficas,
la CLI con su agente, la app y la configuración de la demo.

La base fija una versión publicada de Temurin 21. Para registrar exactamente la
base utilizada, se puede sustituir por su digest con
`--build-arg IMAGEN_JDK=eclipse-temurin@sha256:<digest>` y conservar ese valor
junto al commit y los informes. Los paquetes de Ubuntu todavía se resuelven desde
sus repositorios al construir; no se garantiza una imagen idéntica byte a byte.

## Ejecutar y guardar los informes en un volumen

Los siguientes comandos sirven en Git Bash, Linux y macOS. `MSYS_NO_PATHCONV=1`
evita que Git Bash convierta las rutas del contenedor a rutas de Windows.

```sh
docker volume create opsec-resultados

# Vulnerable: debe detectar F6, escribir el informe y salir con código 3.
MSYS_NO_PATHCONV=1 docker run --rm \
  --mount type=volume,source=opsec-resultados,target=/resultados \
  opsec-laboratorio:local
echo "Código vulnerable: $?"

# Corregida: misma suite, F6 corregido, código esperado 0.
MSYS_NO_PATHCONV=1 docker run --rm \
  --mount type=volume,source=opsec-resultados,target=/resultados \
  opsec-laboratorio:local ejecutar /opt/opsec/demo/proyecto-corregido.yaml \
  --resultados /resultados/corregida
echo "Código corregida: $?"
```

El volumen conserva los archivos aunque los contenedores se eliminen con `--rm`:

```text
/resultados/
├── vulnerable/login/
│   ├── resultado.json
│   ├── app-salida.log
│   └── app-errores.log
└── corregida/login/
    ├── resultado.json
    ├── app-salida.log
    └── app-errores.log
```

Para leer los informes desde el mismo volumen:

```sh
MSYS_NO_PATHCONV=1 docker run --rm --entrypoint cat \
  --mount type=volume,source=opsec-resultados,target=/resultados,readonly \
  opsec-laboratorio:local /resultados/vulnerable/login/resultado.json

MSYS_NO_PATHCONV=1 docker run --rm --entrypoint cat \
  --mount type=volume,source=opsec-resultados,target=/resultados,readonly \
  opsec-laboratorio:local /resultados/corregida/login/resultado.json
```

En PowerShell se omite el prefijo `MSYS_NO_PATHCONV=1`, se escribe cada comando en
una sola línea y se consulta `$LASTEXITCODE` inmediatamente después de cada
`docker run`.

## Verificación de aceptación

| Comprobación | Resultado esperado |
|---|---|
| `docker run --rm opsec-laboratorio:local version` | Motor y Java 21 disponibles. |
| Ejecución vulnerable | `LOGIN-ADMIN`: `NO_CUMPLE`, hallazgo `CONTRASENA-EN-CONSOLA` (CWE-532), código 3. |
| Ejecución corregida | `LOGIN-ADMIN`: `CUMPLE`, código 0. |
| Lectura del volumen después de `--rm` | Ambos `resultado.json` siguen disponibles con sus trazas y veredictos. |

Comprobación realizada el 11 de noviembre de 2026 con Docker Desktop, contenedores
Linux sobre WSL 2 y Java 21.0.13 dentro de la imagen: construcción correcta,
ambas ejecuciones sin `--init` con los códigos y veredictos de la tabla, y lectura
de sus JSON desde contenedores nuevos con el volumen en modo de solo lectura.
Los resultados adicionales de esta comprobación se guardaron bajo
`/resultados/verificacion64/`, conservando los informes previos de la demo.

La imagen incluye Tini porque `xvfb-run` como PID 1 quedó esperando el arranque de
Xvfb en la prueba sin `--init`. Se repitieron ambas ejecuciones con Tini y terminaron
correctamente, sin necesitar esa opción del anfitrión.

El código 3 de la vulnerable es una detección esperada. Los códigos 1 y 2 indican
errores de ejecución o argumentos; el 4 indica un resultado no concluyente. No se
transforman esos códigos en éxitos del contenedor.

La comprobación funcional equivalente fuera de Docker ya existe en `DemoIT`:

```sh
./mvnw -B -pl opsec-cli -am verify
```

Necesita pantalla (en Linux: `xvfb-run --auto-servernum ./mvnw -B -pl opsec-cli -am verify`).
`DemoIT` cubre la demo del motor, pero no sustituye construir y ejecutar la imagen
para verificar JDK 21, Xvfb y la persistencia del volumen.

## Suite de humo (issue #65)

`opsec-cli/src/test/java/.../DemoIT.java` ejecuta cuatro escenarios con la app real:

| Fallo | Suite reutilizada | Vulnerable | Corregido |
|---|---|---|---|
| F6: contraseña en consola | `demo/suites/login.yaml` | `NO_CUMPLE`, código 3, CWE-532 | `CUMPLE`, código 0 |
| F8: excepción no capturada | `clientes-vulnerable/ejemplos/f8/suites/f8.yaml` | `NO_CUMPLE`, código 3 | `CUMPLE`, código 0 |

La prueba comprueba el oráculo de cada hallazgo y que sus referencias de evidencia
existan en la traza. En los controles corregidos exige que no haya hallazgos ni
motivo de ejecución incompleta. Una detección ausente, un veredicto no concluyente,
un hallazgo de otro oráculo o un falso positivo provoca una aserción fallida y un
build rojo.
El ejemplo vulnerable F8 deja únicamente F8 activo; el proyecto corregido usa los
mismos pasos y corrige todos los fallos.

El flujo existente de `.github/workflows/ci.yml` ejecuta `./mvnw -B verify` bajo
Xvfb en Linux para los PR hacia `main`. Failsafe incluye `DemoIT`; no se necesita
otro flujo. En local sin pantalla se omite; en Linux con `CI=true`, no disponer de
pantalla hace fallar la prueba para evitar una omisión silenciosa en CI.
La rama `test/app-prueba-2` no está incluida entre los filtros de push de ese flujo:
la ejecución en GitHub se comprobará al abrir el PR autorizado hacia `main`.

La suite cubre los dos oráculos implementados actualmente. F7 y los niveles 2/3
requieren implementaciones del motor antes de añadir comprobaciones de detección;
no se declaran cubiertos por esta suite de humo. La integración de todos los
fallos de v0.3 corresponde a la issue #67.

Verificación realizada el 11 de noviembre de 2026:

| Comprobación | Resultado observado |
|---|---|
| Windows, JDK 27: `./mvnw -B -pl opsec-cli -am verify` | `BUILD SUCCESS`; F6 y F8 pasan con sus controles corregidos. |
| Linux, JDK 21.0.13, `CI=true`, Xvfb: `./mvnw -B verify` completo | `BUILD SUCCESS`; `DemoIT`: 2 pruebas, 0 fallos, 0 errores y 0 omitidas (4 ejecuciones de la app). |
| Copia del JAR sin registrar el oráculo de F6 | Maven termina con código 1: falla el caso F6 y pasa F8. |
| Copia del JAR sin registrar el oráculo de F8 | Maven termina con código 1: falla el caso F8 y pasa F6. |

Los controles retiraron registros de `ServiceLoader` solo en copias temporales
del JAR; no modificaron el código ni el registro del motor. La evidencia local
está en `target/issue65/evidencia/` (ignorada por Git). El filtro de construcción
conserva los recursos de prueba `src/main/resources/resultados/` y excluye las
carpetas de informes generados.

Esto demuestra localmente que una pérdida de detección rompe la verificación que
ejecuta la CI. Quedan pendientes la ejecución real del flujo en GitHub y el cierre
formal de la Definición de Terminado: CI verde, PR aprobado e integrado en `main`
y tarea en *Hecho*. No se declara completado ese proceso desde la verificación
local.

## Referencias técnicas

- [Etiquetas oficiales de Eclipse Temurin](https://github.com/docker-library/docs/blob/master/eclipse-temurin/README.md).
- [Dockerfile: ENTRYPOINT, CMD y VOLUME](https://docs.docker.com/reference/dockerfile/).
- [Contexto de construcción y archivos de exclusión por Dockerfile](https://docs.docker.com/build/concepts/context/#filename-and-location).
