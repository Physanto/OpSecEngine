# opsec-cli

Interfaz de línea de comandos del motor (comando `opsec`). Hoy tiene `version` y `validar`; `explorar`, `generar`, `ejecutar`, `informe` y `verificar` llegan desde la v0.1. Solo habla con los servicios del núcleo.

- **Dueño:** P3.
- **Paquete raíz:** `io.github.physanto.opsecengine.cli`

## Uso

El build deja un jar ejecutable con todas las dependencias en `target/opsec-<versión>.jar`:

```sh
java -jar opsec-cli/target/opsec-0.0.1-SNAPSHOT.jar version
java -jar opsec-cli/target/opsec-0.0.1-SNAPSHOT.jar validar contratos/ejemplos/proyecto.yaml
```

| Comando | Qué hace |
|---|---|
| `opsec version` (o `opsec --version`) | Muestra la versión del motor y la de Java. |
| `opsec validar <proyecto.yaml>` | Valida el proyecto y las suites de su carpeta `suites/`, sin ejecutar nada. Si hay errores los muestra con la ruta del campo. |

Códigos de salida: 0 si todo salió bien, 1 si hay errores en los archivos y 2 si los argumentos no son válidos.

## Dependencias permitidas

- `opsec-nucleo`.
- picocli.
- `slf4j-jdk14`, solo en tiempo de ejecución: envía los mensajes de SLF4J de las librerías a `java.util.logging`.

Ningún otro módulo puede depender de este.

## Pruebas

Desde la raíz del repositorio:

```sh
./mvnw -B -pl opsec-cli -am verify
```

En Windows:

```bat
mvnw.cmd -B -pl opsec-cli -am verify
```
