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
| `longitud.yaml` | Cadenas ASCII de 255, 256, 10 000 y 1 048 576 caracteres (1 MiB). |
| `unicode.yaml` | Emoji, carácter nulo, controles de dirección y acento combinante. |
| `numericos.yaml` | Negativos, desbordamiento, decimal con coma, `NaN` y ceros iniciales. |
| `inyeccion-sql.yaml` | Tautología, comilla, condición falsa, comentario y consulta SELECT apilada. |
| `firestore.yaml` | Separadores, `__name__` y segmentos de puntos en rutas. |
| `html-swing.yaml` | HTML de negrita y color, sin recursos externos. |
| `cadenas-formato.yaml` | `%s`, `%n` y `%x`. |
| `rutas-archivo.yaml` | Recorrido de carpetas con separadores Unix y Windows. |
| `formulas-csv.yaml` | Fórmulas aritméticas iniciadas con `=` y `+`. |
| `comandos.yaml` | Separadores de shell seguidos de `echo` con canario. |
| `credenciales-debiles.yaml` | Contraseñas numéricas y de un carácter, en las categorías existentes `NUMERICOS` y `LONGITUD`. |

La biblioteca incluye 39 payloads. Los rastreables llevan `{canario}` y `usa_canario: true`.
Los vacíos, numéricos, límites de longitud y contraseñas débiles conservan su valor exacto sin
canario. Los controles Unicode se escriben escapados en YAML y se conservan al cargarlo.
El archivo de longitud contiene las cadenas completas; no depende de una expansión adicional.
La sustitución del canario y la ejecución de los payloads corresponden al núcleo.

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
