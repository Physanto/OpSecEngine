# opsec-cli

Interfaz de línea de comandos del motor (comando `opsec`). Hoy tiene `version`, `validar`, `verificar` y una primera versión de `ejecutar`; `explorar`, `generar` e `informe` llegan en la v0.2. Solo habla con los servicios del núcleo.

- **Dueño:** P3 (salidas).
- **Paquete raíz:** `io.github.physanto.opsecengine.cli`

## Uso

El build deja un jar ejecutable con todas las dependencias en `target/opsec-<versión>.jar`:

```sh
java -jar opsec-cli/target/opsec-<versión>.jar version
java -jar opsec-cli/target/opsec-<versión>.jar validar contratos/ejemplos/proyecto.yaml
java -jar opsec-cli/target/opsec-<versión>.jar ejecutar mis-pruebas/proyecto.yaml
```

El jar trae dentro el jar del agente (recurso `opsec-agente.jar`, que se copia a un archivo temporal al ejecutar) y, como dependencias de tiempo de ejecución, los oráculos y los reporteros, que el núcleo descubre con `ServiceLoader`.

| Comando | Qué hace |
|---|---|
| `opsec version` (o `opsec --version`) | Muestra la versión del motor y la de Java. |
| `opsec validar <proyecto.yaml>` | Valida el proyecto y las suites de su carpeta `suites/`, sin ejecutar nada. Si hay errores los muestra con la ruta del campo. |
| `opsec verificar <proyecto.yaml> [--agente <jar>]` | Comprueba, sin ejecutar casos, que todo está listo (ver abajo). Abre la aplicación unos segundos. |
| `opsec ejecutar <proyecto.yaml> [--suite <suite.yaml>]... [--formato <formato>[,<formato>...]] [--resultados <carpeta>] [--agente <jar>] [--sin-progreso]` | Ejecuta las suites indicadas (o todas las de `suites/`) contra la app, muestra el veredicto y los hallazgos de cada caso, y escribe los informes de los formatos pedidos y la salida de la app en `<resultados>/<suite>/`. Por defecto `<resultados>` es `resultados/<AAAA-MM-DD_HHmmss>` junto al proyecto. |

### Verificación previa (`verificar`)

Hace estas comprobaciones en orden y se detiene en la primera que falla. Cada una escribe una línea `[OK]`, `[AVISO]` o `[FALLO]`:

1. El proyecto y las suites de `suites/` son válidos (sin suites es solo un aviso).
2. Existe el JAR de la aplicación.
3. Se encuentra el jar del agente (el que trae la CLI o el de `--agente`).
4. Conexión: se arranca la aplicación con el agente, se espera su saludo (hasta 30 s) y se cierra. Si falla, muestra las últimas líneas de la salida de error de la aplicación y la carpeta temporal con sus registros completos.

Códigos de salida: 0 si todo está listo; 1 si el proyecto, una suite o un jar no son válidos o no existen; 5 si la aplicación no arranca o el agente no saluda a tiempo; 2 si los argumentos no son válidos.

Cuando exista `VerificacionService` en el núcleo, el comando también comprobará que los localizadores de las suites existen en la aplicación (hoy no lo hace).

### Progreso en consola

Mientras `ejecutar` corre, muestra el avance en la **salida de error**, para que la salida estándar solo tenga los veredictos y el resumen (y una CI o un script la puedan leer sin ruido). Usa los eventos de progreso del núcleo (`ObservadorProgreso`) y escribe una línea por transición, sin colores ni reescritura de líneas:

```text
Ejecutando la suite login (2 casos)
  [1/2] Caso LOGIN-ADMIN (4 pasos)
        Inicio de sesión, paso 1/3: OK
        Paso 1/4: OK
        Paso 2/4: NO_ENCONTRADO
  [2/2] Caso LOGIN-CLIENTE (4 pasos)
        ...
Suite login terminada
```

Solo se muestra el fin de cada paso, con su código de resultado; un reintento de `ESPERAR` aparece como otra línea del mismo paso. No incluye valores ni credenciales (el núcleo no los envía). `--sin-progreso` lo desactiva.

### Formatos de informe (`--formato`, `-f`)

Lista separada por comas (`--formato html,json`) o repetida (`-f html -f json`). Sin la opción se genera `json`. Los nombres no distinguen mayúsculas y se ignoran los repetidos. Cada formato debe tener un reportero registrado (el núcleo los descubre con `ServiceLoader`): hoy `json` (`resultado.json`); `html` (`informe.html`) llegará con el reportero de `opsec-informes`. Se validan antes de lanzar la app: si alguno no existe, el comando termina con código 2 y muestra los formatos disponibles.

Códigos de salida: 0 si todo salió bien (en `ejecutar`, si todos los casos cumplen), 1 si hay errores en los archivos o no se pudo ejecutar, 2 si los argumentos no son válidos (por ejemplo, un formato que no existe), 3 si algún caso no cumple y 4 si ninguno falla pero alguno quedó no concluyente. Los códigos 3 y 4 permiten usar `opsec ejecutar` en una CI.

## Pendiente para el dueño del módulo (P3, salidas)

Ordenado según `PLAN_DE_TRABAJO.md` (sprints 1 a 4):

1. Opciones de `ejecutar` para el modo visible u oculto y el paralelismo cuando el núcleo los soporte; y que `verificar` use `VerificacionService` cuando exista.
2. Comandos `explorar`, `generar` e `informe` (v0.2, necesitan `ExploracionService` y `GeneracionService`).

## Dependencias permitidas

- `opsec-nucleo`.
- picocli.
- `slf4j-jdk14`, solo en tiempo de ejecución: envía los mensajes de SLF4J de las librerías a `java.util.logging`.
- `opsec-oraculos` y `opsec-informes`, solo en tiempo de ejecución, para empaquetarlos: el código de la CLI no los usa.
- El jar del agente (`opsec-agente`, clasificador `agente`), solo para copiarlo como recurso; no entra en ningún classpath.

Ningún otro módulo puede depender de este.

## Pruebas

`DemoIT` es la suite de humo de integración (#65): ejecuta la app real con F6 y F8
activos y corregidos. Comprueba códigos de salida, veredictos, oráculos, CWE y
evidencias del JSON contra los eventos de la traza. Una detección ausente, un
informe incorrecto o un falso positivo en la corregida hace fallar `verify`.
Se omite sin pantalla en local; en la CI Linux la ausencia de Xvfb hace fallar la
prueba. Véase el [alcance y la verificación del laboratorio](../apps-practica/laboratorio/README.md#suite-de-humo-issue-65).

`BancoEvaluacionIT` verifica el [banco de métricas del laboratorio](../apps-practica/laboratorio/BANCO.md):
ejecuciones reales, conteos de detecciones y falsos positivos, datos incompletos o
alterados y reproducción idéntica de sus tablas desde los informes guardados.
También comprueba el [banco con inventario de GUI](../apps-practica/laboratorio/AMPLIACION-BANCO.md):
pasos fallidos, eventos duplicados, datos ausentes, referencia de permisos e
integridad y reproducción de las métricas de cobertura.

`IntegracionV03IT` valida la [matriz completa de integración](../apps-practica/laboratorio/INTEGRACION-V03.md).
Su aceptación de los veinte perfiles solo se activa con `-Dopsec.integracion.v03=true`;
la ejecución normal comprueba la preparación y registra los bloqueos. Una omisión
de la aceptación completa no significa que #67 esté terminada.

Desde la raíz del repositorio:

```sh
./mvnw -B -pl opsec-cli -am verify
```

En Windows:

```bat
mvnw.cmd -B -pl opsec-cli -am verify
```
