# opsec-ataques

Biblioteca de payloads de ataque en YAML y su cargador.

- **Dueño:** P2.
- **Paquete raíz:** `io.github.physanto.opsecengine.ataques`

## Contenido

- `CargadorPayloads`: lee archivos de payloads, los valida contra `contratos/esquemas/payload.schema.json` (copiado como recurso al construir) y los convierte en objetos `Payload`. Los errores llevan la ruta del campo y un mensaje en español.
- `Payload`, `CategoriaPayload` y `TipoCampo`: el modelo de un payload.
- Payloads incluidos en `src/main/resources/io/github/physanto/opsecengine/ataques/payloads/`:

| Archivo | Payloads |
|---|---|
| `vacios.yaml` | `VACIO-CADENA`, `VACIO-ESPACIOS`, `VACIO-TABULACION`, `VACIO-SALTO-LINEA` |

Para agregar un archivo, se copia en esa carpeta y se agrega su nombre a `CargadorPayloads.INCLUIDOS`.

## Dependencias permitidas

- `opsec-dominio`.
- Jackson (`jackson-databind` y `jackson-dataformat-yaml`).
- `json-schema-validator` (networknt).

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
