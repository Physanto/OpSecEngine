# opsec-ataques

Biblioteca de payloads de ataque en YAML y su cargador.

- **Dueño:** P2.
- **Paquete raíz:** `io.github.physanto.opsecengine.ataques`

## Dependencias permitidas

- `opsec-dominio`.
- Jackson (`jackson-databind` y `jackson-dataformat-yaml`).

No puede depender del núcleo, del agente ni del protocolo.

## Pruebas

Desde la raíz del repositorio:

```sh
./mvnw -B -pl opsec-ataques -am verify
```

En Windows:

```bat
mvnw.cmd -B -pl opsec-ataques -am verify
```
