# Contratos de OpSecEngine

Esta carpeta guarda los contratos que comparten todos los módulos y quienes escriben proyectos, suites, payloads o reglas: los formatos de archivo, los catálogos de identificadores y los ejemplos. Junto con `opsec-dominio`, `opsec-protocolo` y el paquete `api` de `opsec-agente`, forman los contratos, hoy en la versión 1.1.

Todo lo que hay aquí es un contrato: se usa tal como está y los cambios se piden con una solicitud de cambio de contrato (ver más abajo). El módulo `opsec-verificacion` valida en cada build que los ejemplos cumplan sus esquemas y que los catálogos coincidan con el código.

## Contenido

### `esquemas/`

JSON Schema (draft 2020-12) de cada formato. Los archivos se escriben en YAML o JSON; el esquema se aplica igual a los dos.

| Archivo | Qué describe |
|---|---|
| `proyecto.schema.json` | `proyecto.yaml`: la app (JAR, clase principal, Java con que se lanza, argumentos de la JVM), los datos de prueba, los roles con sus credenciales, los pasos de login, las operaciones sensibles, la política de roles. |
| `suite.schema.json` | `suites/*.yaml`: nombre, versión, enfoques, configuración de ejecución y casos con sus pasos, ataques y oráculos. También define el formato de un paso y de un localizador. |
| `payload.schema.json` | `ataques/*.yaml`: una lista de payloads de la biblioteca de ataques. |
| `oraculo-regla.schema.json` | `oraculos/*.yaml`: una lista de reglas de oráculo simples, sin compilar nada. |
| `traza-evento.schema.json` | Una línea de `traza.jsonl`: un evento con su secuencia, instante ISO-8601, tipo, contexto y datos. |

Reglas comunes:

- Todos aceptan una propiedad `extensiones` libre, que el motor conserva sin interpretar. Cualquier otra propiedad desconocida es un error, para detectar errores de escritura.
- Los valores enumerados se escriben en MAYÚSCULAS, igual que en el dominio (`EVENTOS`, `SEGURIDAD`, `CONFIRMADO`). Los ids de oráculos y payloads van en MAYÚSCULAS con guiones (`SQLI-CANARIO`).
- Las rutas se escriben con `/` y son relativas a la carpeta del archivo que las contiene; el motor las convierte a la ruta del sistema operativo.
- Los tiempos van en milisegundos, en campos que terminan en `Ms`.

#### Pasos y localizadores

Un paso es un objeto con **una sola** acción como clave: `pulsar`, `llenar`, `seleccionar`, `marcar`, `esperar`, `ir_a`, `cerrar_ventana` o `capturar`. Puede llevar además `esperaMaximaMs` y `extensiones`.

```yaml
- pulsar: Avanzar                                    # localizador de texto simple
- pulsar: {nombreComponente: btnIniciarSesion, texto: Iniciar Sesion}
- llenar: {Usuario: Admin, Contraseña: "12345"}      # etiqueta → valor
- seleccionar: {Tipo: Cliente}
- esperar: {claseVentana: GUI_Admin.MenuAdministrador}
- cerrar_ventana: {claseVentana: javax.swing.JDialog}
- capturar:
```

- Un localizador de texto simple se busca primero como etiqueta asociada y, si no aparece, como texto visible.
- Un localizador objeto admite los criterios `etiqueta`, `texto`, `nombreAccesible`, `nombreComponente`, `tipo`, `ruta`, `tituloVentana` y `claseVentana`. Se prueban en el orden en que se escriben.
- `llenar` y `seleccionar` reciben un mapa campo → valor; cada entrada se convierte en un paso del dominio, en el mismo orden.
- `esperar` recibe un localizador o, si no hay otra opción, un número de milisegundos.
- `capturar` sin valor captura la ventana activa.

Variables que el motor reemplaza en los valores:

| Variable | Valor |
|---|---|
| `{usuario}`, `{contrasena}` | Credenciales del rol, en los pasos de `login` del proyecto. |
| `{payload}` | Cada payload de la lista `ataques` del caso; el caso se ejecuta una vez por payload. |
| `{canario}` | Marca única que genera el motor para rastrear un payload hasta SQL, Firestore o la consola. |

### `catalogos/`

| Archivo | Qué contiene |
|---|---|
| `eventos.yaml` | Por cada `TipoEvento`, quién lo emite y los campos de su mapa `datos`: nombre, tipo, si es obligatorio, valores posibles y descripción. |
| `oraculos.yaml` | Ids de oráculos reservados por el motor, con su momento (`EVENTO`, `CASO`, `SUITE`), su CWE y una descripción corta. |
| `codigos-resultado.yaml` | Códigos de resultado de un paso (`OK`, `NO_ENCONTRADO`, `NO_ALCANZABLE`, `TIEMPO_AGOTADO`, `ERROR`) y su significado. |
| `cwe.yaml` | Debilidades CWE usadas por los oráculos y los payloads: nombre, explicación en español sencillo y recomendación de corrección. |

Los catálogos tienen un campo `version` y una lista de entradas. Agregar una entrada o un campo opcional es un cambio aditivo.

### `ejemplos/`

Un proyecto de pruebas completo de la app de clientes original, con la misma estructura de carpetas que usa el motor:

| Archivo | Esquema |
|---|---|
| `proyecto.yaml` | `proyecto.schema.json`; roles `cliente` y `admin`. |
| `suites/login.yaml` | `suite.schema.json`; casos C01, C02, C04, C13 y C15 de SEC-001. |
| `ataques/inyeccion-sql.yaml` | `payload.schema.json` |
| `oraculos/mensaje-sql-en-dialogo.yaml` | `oraculo-regla.schema.json` |
| `resultados/2026-10-30_1530/traza.jsonl` | `traza-evento.schema.json`, una línea por evento. |

### `protocolo/`

Un ejemplo JSON por tipo de mensaje del protocolo motor ↔ agente. Ver `protocolo/README.md`.

## Versiones

| Versión | Etiqueta | Cambios |
|---|---|---|
| 1 | `contratos-v1` | Versión inicial. |
| 1.1 | `contratos-v1.1` | Aditivo: campo opcional `app.java` en `proyecto.yaml` (carpeta del JDK o JRE con que se lanza la app) y componente `javaApp` del record `Proyecto`, que conserva el constructor de la v1. |

## Cambiar un contrato

1. Quien necesita el cambio abre un issue con la plantilla **Solicitud de cambio de contrato** e indica:
   - qué cambia,
   - por qué,
   - si es **aditivo** (agrega un campo, un tipo de evento, un mensaje, una entrada de catálogo) o **rompe** algo (renombra, elimina, cambia un tipo),
   - qué módulos afecta.
2. El líder responde:
   - **Cambios aditivos:** en un día hábil como máximo. Se aprueban casi siempre.
   - **Cambios que rompen:** se discuten en la reunión semanal y se aplican al inicio del siguiente sprint, nunca a mitad.
3. El líder, o quien autorice, hace el cambio, sube la versión de los contratos (`contratos-v1.1`, `contratos-v2`) y avisa al equipo.
4. Mientras tanto, quien lo pidió no se bloquea: trabaja con un adaptador local en su módulo y lo elimina cuando llega el cambio.

Todo PR que modifique esta carpeta lleva la etiqueta `contrato` y enlaza su solicitud aprobada.
