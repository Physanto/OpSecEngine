# opsec-protocolo

Mensajes que intercambian el motor y el agente (JSON por líneas sobre un socket TCP local) y su serialización. Se compila para Java 8 (`--release 8`) porque también lo usa el agente.

- **Dueño:** líder (L). Es un contrato: los cambios se piden con una solicitud de cambio de contrato.
- **Paquete raíz:** `io.github.physanto.opsecengine.protocolo`

## Contenido

- `Mensaje` y `TipoMensaje`: el sobre de cada mensaje (versión, tipo, id, correlación y cuerpo). La versión actual es `Mensaje.VERSION_PROTOCOLO` = 1.
- Paquete `dto`: el cuerpo de cada tipo de mensaje, independiente del dominio.
- `CodecMensajes`: JSON Lines en UTF-8 con separador `\n` fijo; ignora los campos desconocidos.
- `CanalMensajes`: envía y recibe mensajes por un socket. Documenta el saludo `HOLA` con token.

Hay un ejemplo JSON por tipo de mensaje en `contratos/protocolo/ejemplos/`, en la raíz del repositorio; las pruebas los leen todos.

## Dependencias permitidas

- Jackson (`jackson-databind`).

No puede depender de ningún otro módulo del proyecto.

## Pruebas

Desde la raíz del repositorio:

```sh
./mvnw -B -pl opsec-protocolo -am verify
```

En Windows:

```bat
mvnw.cmd -B -pl opsec-protocolo -am verify
```
