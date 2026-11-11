# Guía de contribución

Gracias por contribuir a **OpSecEngine**. Esta guía explica cómo se organiza el trabajo en el repositorio: ramas, commits, pull requests, contratos y convenciones de código.

## Índice

1. [Ramas por módulo](#ramas-por-módulo)
2. [Commits](#commits)
3. [Pull requests](#pull-requests)
4. [Archivos protegidos y cambios de contrato](#archivos-protegidos-y-cambios-de-contrato)
5. [Definición de Terminado](#definición-de-terminado)
6. [Convenciones de código](#convenciones-de-código)
7. [Compilar y probar](#compilar-y-probar)

## Ramas por módulo

`main` es la rama de integración: tiene la base del proyecto y todo lo que el líder ya integró. Cada módulo tiene **una rama propia**, de larga duración, donde su dueño trabaja todas las issues de ese módulo. El líder integra cada rama de módulo en `main`.

| Rama | Para qué | Quién trabaja | Nace de | Se integra en |
|---|---|---|---|---|
| `main` | Base del proyecto e integración. Siempre compila y pasa la CI. | Solo el líder integra | — | — |
| `modulo/agente` | `opsec-agente` | P1 | `main` | `main` |
| `modulo/nucleo` | `opsec-nucleo` | Líder | `main` | `main` |
| `modulo/oraculos` | `opsec-oraculos` | P2 | `main` | `main` |
| `modulo/ataques` | `opsec-ataques` | P2 | `main` | `main` |
| `modulo/informes` | `opsec-informes` | P3 (salidas) | `main` | `main` |
| `modulo/cli` | `opsec-cli` | P3 (salidas) | `main` | `main` |
| `modulo/app-practica` | `apps-practica/` | P3 (laboratorio) | `main` | `main` |
| `feature/*` | Cambios del líder en la base: contratos, `pom.xml`, CI, documentación común | Líder | `main` | `main` |
| `hotfix/*` | Corrección urgente de algo ya integrado | Líder | `main` | `main` |

Reglas:

- **En tu rama de módulo solo se toca tu módulo.** Si necesitas cambiar un contrato, otro módulo o un archivo protegido, abre un issue (ver [Archivos protegidos](#archivos-protegidos-y-cambios-de-contrato)); no lo cambies en tu rama.
- **Una rama cubre varias issues.** Cada issue se resuelve con uno o más commits en la rama del módulo; en el cuerpo del commit escribe `Refs #<número>` para enlazarlo.
- **Trae `main` a tu rama al menos una vez al día** y siempre antes de pedir una integración:

  ```sh
  git fetch origin
  git merge origin/main
  ```

  Nunca uses `rebase` sobre una rama ya subida ni `git push --force`.
- **Integra seguido**, no al final: cuando termines un grupo de issues (como mínimo una vez por sprint), abre un PR de tu rama de módulo hacia `main`. Después de la integración la rama sigue viva y continúas en ella.
- Nadie hace commit directo en `main`: todo entra por PR o por una integración del líder.
- Las versiones se etiquetan en `main` (`v0.1.0`, `v0.2.0`…) al cerrar cada hito.

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

1. La primera vez, cambia a la rama de tu módulo (ya existe en GitHub):

   ```sh
   git fetch origin
   git checkout modulo/<modulo>
   ```

2. Toma una issue del tablero, muévela a *En curso* y trabájala en commits pequeños. Ejecuta `./mvnw -B verify` antes de cada `git push`.
3. Sube tus commits: `git push origin modulo/<modulo>`. La CI corre en cada push.
4. Cuando tengas un grupo de issues terminado, trae `main` a tu rama (`git merge origin/main`), vuelve a ejecutar `verify` y abre el PR **de `modulo/<modulo>` hacia `main`**.
5. Completa la plantilla del PR: qué hace, cómo probarlo, una línea `Cierra #<número>` por cada issue terminada y el checklist de la Definición de Terminado.
6. Requisitos para integrar:
   - CI en verde en Windows, macOS y Linux.
   - Revisión del líder (y del dueño del módulo, ver `.github/CODEOWNERS`).
7. El líder integra con un commit de merge (sin *squash* ni *fast-forward*). La rama de módulo **no se borra**: sigues trabajando en ella.

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

- [ ] El código está integrado en `main` mediante un PR aprobado.
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
