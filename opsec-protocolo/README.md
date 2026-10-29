# opsec-protocolo

Mensajes que intercambian el motor y el agente (JSON por líneas sobre un socket TCP local) y su serialización. Se compila para Java 8 (`--release 8`) porque también lo usa el agente.

- **Dueño:** líder (L). Es un contrato: los cambios se piden con una solicitud de cambio de contrato.
- **Paquete raíz:** `io.github.physanto.opsecengine.protocolo`

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
