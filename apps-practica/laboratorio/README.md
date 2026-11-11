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

## Referencias técnicas

- [Etiquetas oficiales de Eclipse Temurin](https://github.com/docker-library/docs/blob/master/eclipse-temurin/README.md).
- [Dockerfile: ENTRYPOINT, CMD y VOLUME](https://docs.docker.com/reference/dockerfile/).
- [Contexto de construcción y archivos de exclusión por Dockerfile](https://docs.docker.com/build/concepts/context/#filename-and-location).
