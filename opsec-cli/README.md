# opsec-cli

Interfaz de línea de comandos del motor (comando `opsec`). Hoy tiene `version`, `validar` y una primera versión de `ejecutar`; `explorar`, `generar`, `informe` y `verificar` llegan desde la v0.1. Solo habla con los servicios del núcleo.

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
| `opsec ejecutar <proyecto.yaml> [--suite <suite.yaml>]... [--resultados <carpeta>] [--agente <jar>]` | Ejecuta las suites indicadas (o todas las de `suites/`) contra la app, muestra el veredicto y los hallazgos de cada caso, y escribe `resultado.json` y la salida de la app en `<resultados>/<suite>/`. Por defecto `<resultados>` es `resultados/<AAAA-MM-DD_HHmmss>` junto al proyecto. |

Códigos de salida: 0 si todo salió bien (en `ejecutar`, si todos los casos cumplen), 1 si hay errores en los archivos o no se pudo ejecutar, 2 si los argumentos no son válidos, 3 si algún caso no cumple y 4 si ninguno falla pero alguno quedó no concluyente. Los códigos 3 y 4 permiten usar `opsec ejecutar` en una CI.

## Pendiente para el dueño del módulo (P3, salidas)

Ordenado según `PLAN_DE_TRABAJO.md` (sprints 1 a 4):

1. Reportero HTML (línea de tiempo, capturas, veredicto por caso) y agregarlo a los formatos de `ejecutar` (hoy solo `json`), con una opción `--formato`.
2. Comando `verificar` (autoverificación previa) y opciones de `ejecutar` para el modo visible u oculto y el paralelismo cuando el núcleo los soporte.
3. Mostrar el progreso mientras se ejecuta (el núcleo todavía no emite eventos de progreso).
4. Comandos `explorar`, `generar` e `informe` (v0.2).

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

Desde la raíz del repositorio:

```sh
./mvnw -B -pl opsec-cli -am verify
```

En Windows:

```bat
mvnw.cmd -B -pl opsec-cli -am verify
```
