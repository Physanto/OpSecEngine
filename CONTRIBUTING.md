# Guía de contribución

Gracias por contribuir a **OpSecEngine**. Esta guía explica cómo se organiza el trabajo en el repositorio: ramas, commits, pull requests, contratos y convenciones de código.

## Índice

1. [Ramas (git flow)](#ramas-git-flow)
2. [Commits](#commits)
3. [Pull requests](#pull-requests)
4. [Archivos protegidos y cambios de contrato](#archivos-protegidos-y-cambios-de-contrato)
5. [Definición de Terminado](#definición-de-terminado)
6. [Convenciones de código](#convenciones-de-código)
7. [Compilar y probar](#compilar-y-probar)

## Ramas (git flow)

| Rama | Para qué | Nace de | Se fusiona en |
|---|---|---|---|
| `main` | Versiones publicadas. Siempre estable. | — | — |
| `develop` | Integración del trabajo del equipo. | `main` | — |
| `feature/*` | Una tarea: funcionalidad, corrección, pruebas o documentación. | `develop` | `develop` |
| `release/*` | Preparar una versión: ajustes finales, número de versión, notas. | `develop` | `main` y `develop` |
| `hotfix/*` | Corregir algo urgente de una versión publicada. | `main` | `main` y `develop` |

Nombres de rama en minúscula, con guiones y sin tildes ni ñ:

```text
feature/agente-eventos-sinteticos
feature/oraculos-sin-sesion-falso-positivo
feature/informes-readme
release/0.1.0
hotfix/0.1.1-cierre-de-sesion
```

Reglas:

- **Una rama, un tema, un módulo.** Si una tarea toca módulos de dueños distintos, se divide en varias ramas y PR.
- Las ramas `feature/*` viven pocos días (3 o 4 como máximo). Mejor PR pequeños y frecuentes.
- Nunca se hace commit directo en `main` ni en `develop`, y nunca se usa `git push --force` sobre ramas compartidas.
- Actualiza tu rama con `develop` al menos una vez al día:

  ```sh
  git fetch origin
  git merge origin/develop
  ```

  Si la rama todavía no se ha subido, también sirve `git rebase origin/develop`; una vez subida, usa `merge` para no tener que forzar el push.

- Al cerrar una versión, la rama `release/*` se fusiona en `main`, se etiqueta (`v0.1.0`) y se fusiona de vuelta en `develop`. Lo mismo con `hotfix/*`.

## Commits

Formato de la primera línea:

```text
tipo: descripción
```

- Sin alcance entre paréntesis (`feat: ...`, no `feat(agente): ...`).
- En español, en minúscula y en imperativo: "agrega", "corrige", "elimina".
- Máximo 72 caracteres.
- Commits pequeños y coherentes: cada uno compila y deja las pruebas en verde.

| Tipo | Uso |
|---|---|
| `feat` | Funcionalidad nueva |
| `fix` | Corrección de un error |
| `docs` | Solo documentación |
| `test` | Pruebas nuevas o corregidas |
| `build` | Construcción, dependencias, Maven |
| `ci` | Integración continua |
| `chore` | Mantenimiento que no cambia el código del motor (plantillas, configuración) |
| `refactor` | Cambio de código que no altera el comportamiento |
| `style` | Formato, sin cambios de lógica |
| `perf` | Mejora de rendimiento |

Ejemplos:

```text
feat: agrega observador de dialogos
fix: evita falso positivo en sin-sesion
test: agrega traza de eliminacion sin login
```

## Pull requests

1. Crea la rama desde `develop` actualizado:

   ```sh
   git checkout develop
   git pull
   git checkout -b feature/<modulo>-<tema>
   ```

2. Trabaja en commits pequeños y ejecuta `./mvnw -B verify` antes de subir.
3. Sube la rama: `git push -u origin feature/<modulo>-<tema>`.
4. Abre el PR en GitHub con **base `develop`** (la rama por defecto del repositorio es `main`: revisa la base antes de crearlo). Solo las ramas `release/*` y `hotfix/*` abren PR hacia `main`.
5. Completa la plantilla del PR: qué hace, cómo probarlo, el issue relacionado y el checklist de la Definición de Terminado.
6. Requisitos para fusionar:
   - CI en verde en Windows, macOS y Linux.
   - Aprobación del dueño del módulo (ver `.github/CODEOWNERS`); si toca contratos, también la del líder.
7. Se fusiona con un commit de merge (sin *fast-forward*) para conservar el historial de la rama, y luego se borra la rama.

## Archivos protegidos y cambios de contrato

Los contratos son los acuerdos entre módulos. Se usan tal como están; si alguien necesita algo distinto, lo pide, no lo modifica.

| Ruta | Dueño | Por qué |
|---|---|---|
| `pom.xml` (raíz) | Líder | Versiones y módulos comunes |
| `opsec-dominio/` | Líder | Modelos e interfaces de extensión |
| `opsec-protocolo/` | Líder | Contrato entre motor y agente |
| `opsec-agente/src/main/java/**/api/` | Líder | Interfaces del agente (`AdaptadorToolkit`, `Gancho`, `EmisorEventos`) |
| `opsec-soporte-pruebas/` | Líder | Dobles de prueba y datos de ejemplo compartidos |
| `opsec-verificacion/` | Líder | Pruebas de arquitectura y de contratos |
| `contratos/` | Líder | Esquemas, catálogos y ejemplos |
| `build-tools/` | Líder | Reglas de calidad (Checkstyle) |
| `.github/`, `CONTRIBUTING.md` | Líder | Proceso |

Cómo se hace cumplir: `CODEOWNERS` y la protección de ramas impiden fusionar sin la aprobación del líder, y `opsec-verificacion` hace fallar el build si se rompen las reglas de dependencia o los contratos.

Proceso de cambio de contrato:

1. Abre un issue con la plantilla **Solicitud de cambio de contrato**: contrato afectado, cambio propuesto, motivo, si es aditivo o rompe compatibilidad, y módulos afectados.
2. El líder responde:
   - **Cambios aditivos** (agregar un campo, un tipo de evento, un mensaje, una entrada de catálogo): en un día hábil como máximo.
   - **Cambios que rompen** (renombrar, eliminar, cambiar un tipo): se discuten en la reunión semanal y se aplican al inicio del siguiente sprint.
3. El líder, o quien autorice, hace el cambio, sube la versión de los contratos (`contratos-v1.1`, `contratos-v2`) y avisa al equipo.
4. Mientras tanto, quien lo pidió trabaja con un adaptador local en su módulo y lo elimina cuando llega el cambio.

Todo PR que modifique contratos lleva la etiqueta `contrato` y enlaza su solicitud aprobada. Más detalles en [`contratos/README.md`](contratos/README.md).

## Definición de Terminado

Una tarea está terminada cuando:

- [ ] El código está en `develop` mediante un PR aprobado.
- [ ] Tiene pruebas unitarias; los oráculos además tienen al menos una traza que los activa y una que no.
- [ ] La CI está en verde, incluidos el formato y las reglas de dependencia.
- [ ] No modifica archivos protegidos sin una solicitud de cambio de contrato aprobada.
- [ ] El `README.md` del módulo está actualizado si cambió su uso.
- [ ] La tarea está en *Hecho* en el tablero del proyecto.

## Convenciones de código

**Idioma y documentación**

- Nombres de clases y métodos en español, con el estilo del proyecto (`EjecucionService`, `evaluar`).
- Javadoc en español en todas las clases, interfaces, records, enums, métodos y constructores públicos o protegidos: propósito y contrato (qué recibe, qué devuelve, qué excepciones lanza, si es seguro entre hilos).
- Un `package-info.java` documentado por paquete.
- README, textos y mensajes al usuario en español.

**Identificadores en ASCII**

- Clases, métodos, variables, paquetes y archivos sin tildes ni ñ: `contrasena`, `accion`, `version`.
- Las tildes sí se usan en Javadoc, comentarios, textos y documentación.

**Multiplataforma (Windows, macOS y Linux)**

- Rutas con `java.nio.file.Path` y `Files`; nunca separadores fijos (`/` o `\`).
- `File.pathSeparator` en los classpaths.
- UTF-8 explícito en toda lectura y escritura de texto.
- Sin depender de comandos del sistema operativo.
- Las pruebas con interfaz gráfica se omiten con `Assumptions` cuando el entorno no tiene pantalla (headless).

**Versiones de Java**

- El motor compila con Java 21 (`--release 21`).
- `opsec-protocolo` y `opsec-agente` compilan con Java 8 (`--release 8`), porque el agente corre dentro de la aplicación probada.
- La app de práctica `apps-practica/clientes-vulnerable` compila con Java 11 (lo exige H2).

**Otras reglas**

- Modelos de dominio inmutables (`record`); sin estado estático en el núcleo.
- Nada de `System.out` en el motor: se usa `java.util.logging`. Solo la CLI, el agente y la app de práctica escriben en consola.
- Todas las versiones de dependencias y plugins se fijan en el `pom.xml` raíz; los `pom.xml` de módulo no declaran versiones.
- Las reglas de calidad (Spotless, Checkstyle, Enforcer, ArchUnit) no se desactivan para que algo pase. Si una regla estorba, se plantea en un issue.

## Compilar y probar

Solo se necesita un **JDK 21**. El Maven Wrapper descarga la versión de Maven correcta.

**Linux y macOS**

```sh
./mvnw -B verify                 # compila, aplica todas las verificaciones y ejecuta las pruebas
./mvnw spotless:apply            # aplica el formato automático
```

En Linux sin pantalla, las pruebas con Swing se omiten. Para ejecutarlas, usa una pantalla virtual:

```sh
xvfb-run --auto-servernum ./mvnw -B verify
```

Con pantalla real (en cualquier sistema), las pruebas con Swing abren ventanas durante unos segundos y la app de práctica puede tomar el foco. Mientras corre `verify`, no escribas sobre esas ventanas: una tecla puede cambiar un campo o cerrar un diálogo y hacer fallar una prueba. En Linux, `xvfb-run` evita que aparezcan.

**Windows** (CMD o PowerShell)

```bat
mvnw.cmd -B verify
mvnw.cmd spotless:apply
```

En Git Bash también funciona `./mvnw -B verify`.

**Documentación Javadoc**

```sh
./mvnw -B -DskipTests package javadoc:aggregate
```

El resultado queda en `target/reports/apidocs/index.html`.

**Un solo módulo**

```sh
./mvnw -B verify -pl opsec-oraculos -am
```
