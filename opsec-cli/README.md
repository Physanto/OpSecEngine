# opsec-cli

Interfaz de línea de comandos del motor (comando `opsec`), con los comandos `explorar`, `generar`, `ejecutar`, `informe` y `verificar`. Solo habla con los servicios del núcleo.

- **Dueño:** P3.
- **Paquete raíz:** `io.github.physanto.opsecengine.cli`

## Dependencias permitidas

- `opsec-nucleo`.
- picocli.

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
