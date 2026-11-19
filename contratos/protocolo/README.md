# Protocolo motor ↔ agente

Ejemplos de los mensajes del protocolo (versión 1), uno por tipo, en `ejemplos/`. El nombre del archivo es el tipo de mensaje en minúsculas y con guiones; por ejemplo, `EJECUTAR_PASO` está en `ejecutar-paso.json`.

Los ejemplos tienen formato para que se lean bien. Por el socket, cada mensaje viaja en una sola línea JSON, en UTF-8 y terminado en `\n` (JSON Lines).

Las clases de cada mensaje y de su cuerpo están en el módulo `opsec-protocolo`: `Mensaje`, `TipoMensaje` y el paquete `dto`. Las pruebas de ese módulo leen todos estos ejemplos, así que un ejemplo que no coincida con el contrato hace fallar el build.

Este directorio es un contrato: los cambios se piden con una solicitud de cambio de contrato.

## Árbol de componentes (contratos-v1.4)

Cada nodo de `ARBOL` conserva los campos existentes y añade:

- `ruta`: localizador `Ventana/Clase[indice]/...`. La ventana se identifica por título,
  nombre o clase simple; cada segmento posterior corresponde a un hijo directo, con índice
  desde cero entre hermanos de la misma clase simple. Incluye los contenedores internos
  de Swing. Se usa un identificador de ventana sin `/`; si el título no sirve, se usa el
  nombre y, en último término, la clase simple.
- `alcanzable`: componente visible y habilitado, con ancestros visibles, ancho y alto
  positivos y área dentro de los límites de su ventana. Un componente completamente fuera
  de ella no es alcanzable. No depende de que la ventana esté dentro de una pantalla física.
- `acciones`: lista de acciones aplicables al tipo de control, independientemente de si está
  alcanzable en ese instante. `PULSAR` para `AbstractButton`; `LLENAR` para campos de texto
  editables; `SELECCIONAR` para combos, listas, tablas y pestañas; `MARCAR` para botones de
  alternancia, casillas y botones de opción; `IR_A` para elementos de menú. Un control puede
  ofrecer varias acciones. Los contenedores internos (`JRootPane`, `JLayeredPane` y glass
  pane) y el contenido del glass pane no ofrecen acciones de exploración.

Los campos nuevos son opcionales al leer: `ruta=null`, `alcanzable=false` y `acciones=[]`.
Los campos desconocidos se ignoran también en los hijos. Se conserva el constructor anterior
para los consumidores existentes; el formato del sobre sigue en versión 1.

Se incluyen todas las ventanas visibles, los diálogos y los menús emergentes abiertos, tanto
ligeros (dentro del árbol de su ventana) como pesados (en una ventana propia). La lectura se
realiza en el EDT. `ejemplos/arbol.json` representa el formulario `InicioSesion` de la app de
práctica; las pruebas comparan controles, nombres y acciones sin depender del aspecto visual
ni de los detalles que pueda añadir cada Look and Feel.

## Configuración de ganchos (contratos-v1.5)

`CONFIG_GANCHOS` (motor → agente) reemplaza por completo la configuración de los ganchos del agente: `sqlSensibles`, `firestoreSensibles`, `canarios` y `fallosInyectados`. El agente la aplica a los eventos siguientes y responde con un `RESULTADO_PASO` de código `OK`, con la misma correlación que el mensaje recibido, para confirmar que ya está vigente. Hasta que llega, la configuración está vacía.

Los eventos `SQL` y `FIRESTORE` llevan el campo opcional `sensible=true` cuando su operación está en la lista de sensibles (la comparación no distingue mayúsculas de minúsculas); si no lo está, el campo se omite.

## Inyección de fallos (contratos-v1.6)

Cada elemento de `fallosInyectados` de `CONFIG_GANCHOS` (y de `caso.fallos` en la suite) tiene el formato `<punto>:<clase-excepcion>[:<mensaje>]`, por ejemplo `SQL_CONEXION:java.sql.SQLException:sin red`. Los puntos son:

| Punto | Dónde se provoca |
|---|---|
| `SQL_CONEXION` | `DriverManager.getConnection` |
| `SQL_EJECUTAR` | `execute`, `executeQuery` y `executeUpdate` de `Statement` y `PreparedStatement` |
| `FIRESTORE_ESCRIBIR` | `set`, `update`, `delete`, `create` y `commit` de Firestore |
| `ARCHIVO_ESCRIBIR` | reservado; todavía no lo atiende ningún gancho |

Antes de ejecutar el método original, el agente lanza la excepción (instanciada con un constructor `(String)`; si la clase no existe o no se puede crear, una `RuntimeException`) y emite un evento `ESTADO` con `fallo=<punto>`. Un punto desconocido se ignora y se registra. Al terminar el caso el motor envía de nuevo `CONFIG_GANCHOS` sin fallos.

