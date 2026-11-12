# opsec-agente

Agente que se carga con `-javaagent` dentro de la JVM de la aplicación probada: ejecuta acciones sobre Swing, observa ventanas, diálogos, excepciones y consola, instala ganchos y toma capturas. Se compila para Java 8 (`--release 8`).

El empaquetado genera, además del jar normal, `target/opsec-agente-<versión>-agente.jar`: el jar del agente con las dependencias reubicadas bajo `io.github.physanto.opsecengine.agente.interno` y el manifiesto con `Premain-Class`, `Agent-Class` y `Can-Retransform-Classes`. Uso:

```sh
java -javaagent:opsec-agente-<versión>-agente.jar=<argumentos> -jar app.jar
```

## Estado actual: agente mínimo de demostración

Es una primera versión funcional, pensada para ejecutar la demo de punta a punta y servir de base al dueño del módulo. Lo que hay:

| Clase | Paquete | Qué hace |
|---|---|---|
| `AgenteMain` | `agente` | `premain`: lee los argumentos, instala los observadores y arranca el servidor en un hilo daemon. |
| `ServidorProtocolo` | `agente` | Saluda con `HOLA`; atiende `EJECUTAR_PASO`, `ARBOL_SOLICITUD`, `CAPTURAR` y `CERRAR`; responde `NO_IMPLEMENTADO` a lo demás. |
| `EmisorProtocolo` | `agente` | Implementa `EmisorEventos`: numera y envía los `EVENTO`; guarda los anteriores a la conexión y los envía después del saludo. |
| `ObservadorConsola` | `agente` | Envuelve `System.out` y `System.err`; emite `CONSOLA` por línea sin cambiar lo que la app muestra. |
| `RegistroAgente` | `agente` | Mensajes propios del agente en la salida de error original, con el prefijo `[opsec-agente]`. |
| `AdaptadorSwing` | `agente.swing` | Implementa `AdaptadorToolkit`: `PULSAR`, `LLENAR`, `SELECCIONAR`, `MARCAR`, `IR_A`, `CERRAR_VENTANA` y `ESPERAR` en los modos `SEMANTICO` y `EVENTOS`, y el árbol de componentes con rutas, alcance y acciones. |
| `AccionesEventos` | `agente.swing` | Envía eventos sintéticos de ratón y teclado al componente elegido, en el EDT. |
| `Seleccion` | `agente.swing` | Busca una opción por su texto en un combo, una lista, una tabla o un árbol y la selecciona. |
| `Navegacion` | `agente.swing` | `IR_A` (elige pestañas y trae la ventana al frente) y `CERRAR_VENTANA` (envía `WINDOW_CLOSING`). |
| `Capturas` | `agente.swing` | Elige la ventana que se captura, la pinta en un `BufferedImage` y la codifica en PNG y Base64. |
| `BuscadorComponentes` | `agente.swing` | Resuelve localizadores: `ETIQUETA` (asociación explícita o cercanía), `RUTA`, `TEXTO`, `NOMBRE_COMPONENTE`, `NOMBRE_ACCESIBLE`, `TIPO`, `TITULO_VENTANA`, `CLASE_VENTANA`; verifica que el componente sea alcanzable. |
| `EjecutorEdt` | `agente.swing` | Ejecuta en el EDT con tiempo máximo; detecta cuando una acción abre un diálogo modal. |
| `ObservadorExcepciones` | `agente.swing` | Manejador por defecto de excepciones no capturadas: emite `EXCEPCION` (`capturada=false`) por las del EDT y las de los demás hilos y las pasa al manejador anterior. |
| `ObservadorVentanas` | `agente.swing` | Emite `VENTANA_ABIERTA`, `VENTANA_CERRADA` (con `liberada`) y `DIALOGO` (con el texto de los `JOptionPane`). |
| `CierreAplicacion` | `agente.swing` | Cierra la app después de `CERRAR`: pide el cierre de sus ventanas y, si no termina sola en 1 s, llama a `System.exit(0)`. |
| `DescriptorComponentes` | `agente.swing` | Describe componentes como `NodoArbolDto`; nunca incluye el texto de un `JPasswordField`. |

Comportamiento que conviene conocer:

- Antes de actuar, el agente espera hasta `esperaMaximaMs` a que el componente aparezca y sea alcanzable (visible, habilitado y sin un diálogo modal encima).
- En modo `EVENTOS`, `PULSAR` envía un clic izquierdo con `dispatchEvent`; `LLENAR` selecciona el contenido anterior, lo borra con Retroceso y escribe con eventos de teclado. Los escuchadores de la aplicación reciben las teclas y pueden consumirlas; no se usa `setText` como alternativa. Los eventos de teclado se entregan mediante `KeyboardFocusManager.redispatchEvent` al campo elegido sin depender del foco nativo. Este modo no reproduce las entradas físicas del sistema operativo.
- En modo `SEMANTICO`, se mantienen `doClick` y `setText`. Los campos no editables se rechazan en ambos modos.
- `SELECCIONAR` busca la opción cuyo texto (`toString()` del valor, sin espacios a los lados) es el valor del paso: en un combo o una lista, el elemento; en una tabla, la primera fila con una celda de ese texto; en un árbol, el primer nodo en profundidad, aunque esté en una rama contraída. Espera a que la opción aparezca; si no aparece, `NO_ENCONTRADO`. En modo `SEMANTICO` usa `setSelectedIndex`, `changeSelection` o `setSelectionPath`; en `EVENTOS`, lleva la opción a la vista y le hace clic (en un combo, marca la opción en la lista de su menú y le envía la liberación del ratón, sin mostrar el menú).
- `MARCAR` deja una casilla, un botón de opción o de alternancia en el estado del valor (`true` o `false`; sin valor, `true`). Si ya está así no lo pulsa. Termina con `ERROR` si sigue en el otro estado después de pulsarlo, por ejemplo al desmarcar un botón de opción de un grupo.
- `IR_A` elige, de afuera hacia adentro, las pestañas que contienen al objetivo, restaura su ventana si está minimizada, la trae al frente y le da el foco al objetivo. Con `TEXTO` también encuentra una pestaña por su título, y un componente oculto solo por estar en otra pestaña cuenta como alcanzable (para las demás acciones es `NO_ALCANZABLE`).
- `CERRAR_VENTANA` envía `WINDOW_CLOSING` a la ventana del objetivo, como el botón de cierre, en los dos modos. La app decide qué hacer: ocultarla, destruirla, pedir confirmación o terminar (en ese caso el motor no recibe el resultado del paso). El paso termina con `OK` aunque la ventana siga abierta.
- Si la acción abre un diálogo modal, el paso termina con `OK` apenas aparece el diálogo.
- Si el código de la app lanza una excepción durante la acción, se emite `EXCEPCION` con `capturada=false` y el paso termina con `OK`: la acción se hizo y el fallo es de la app.
- Cada ventana emite `VENTANA_ABIERTA` al mostrarse, también cuando vuelve a mostrarse después de ocultarla (con el mismo `ventanaId`). Ocultarla con `setVisible(false)` emite `VENTANA_CERRADA` con `liberada=false`; destruirla con `dispose` emite `VENTANA_CERRADA` con `liberada=true`, también si ya estaba oculta. El último `VENTANA_CERRADA` de cada `ventanaId` dice si la ventana quedó liberada; si se oculta y se destruye en la misma tarea del EDT, como un `JOptionPane`, sale uno solo con `liberada=true`.
- Las excepciones que nadie captura, en el EDT (escuchadores, tareas de `invokeLater`) o en otro hilo, llegan como `EXCEPCION` con `capturada=false`, el nombre del hilo y hasta 30 líneas de pila. Después siguen al manejador que hubiera; si no había, la pila se escribe en `System.err` como sin el agente. No se ven las de hilos con manejador propio ni las que ocurran si la app reemplaza después el manejador por defecto.
- `CAPTURAR` (mensaje) responde `CAPTURA` con la ventana pintada en PNG con `printAll`, sin `Robot`: funciona aunque la ventana esté tapada o fuera de la pantalla, pero no incluye la barra de título del sistema operativo. La ventana se elige por título o clase (completa o simple); sin nombre, la ventana activa o, si no hay, el último diálogo modal visible o la última ventana visible. Si ninguna coincide, responde `ERROR` con el código `ERROR_INTERNO`. La acción `CAPTURAR` dentro de un `EJECUTAR_PASO` responde `ERROR`: la imagen no cabe en el resultado del paso y el motor debe pedirla con el mensaje.
- `CERRAR` cierra la app de forma ordenada. El agente emite `APP_TERMINADA` con `esperada=true` y `causa=CERRAR`, cierra la conexión (es la respuesta que espera el motor: así no lo toma como una caída) y le envía `WINDOW_CLOSING` a cada ventana visible, como el botón de cierre. Si la app no termina sola en 1 s (sus ventanas solo se ocultan, pide confirmación, tiene otros hilos vivos o no tiene ventanas), el agente termina la JVM con `System.exit(0)`. El plazo es menor que los 2 s que espera el motor antes de detener el proceso. Si el motor corta la conexión sin `CERRAR`, la app sigue corriendo.

### Localizadores robustos

`ETIQUETA` prefiere la asociación `JLabel.setLabelFor`. Si ninguna etiqueta coincidente
la tiene, busca controles editables en el mismo contenedor padre: primero a la derecha,
con el centro vertical dentro de la altura de la etiqueta; en su defecto, debajo, con el
centro horizontal dentro del ancho de la etiqueta. Elige la distancia euclídea mínima entre
bordes, calculada en coordenadas de la ventana. Admite campos de texto editables, combos,
spinners y botones seleccionables. Los empates producen `NO_ENCONTRADO` con motivo
`ambiguo`, al igual que varias etiquetas que apuntan a controles distintos.

`RUTA` usa `Ventana/Clase[indice]/Clase[indice]/...`. El primer segmento coincide exactamente
con el título, el nombre (`setName`) o la clase simple de la ventana. Cada segmento posterior
corresponde a un hijo directo, incluidos los contenedores internos de Swing. El índice es
opcional y empieza en cero entre hermanos de la misma clase simple. Por ejemplo:

```text
InicioSesion/JRootPane[0]/JLayeredPane[0]/JPanel[0]/JTextField[0]
```

Sin índice se consideran todos los hermanos de esa clase; si la ruta completa deja varios
resultados, se rechaza como ambigua. Una ruta inexistente o mal formada devuelve
`NO_ENCONTRADO`.

`TEXTO`, `ETIQUETA`, `NOMBRE_ACCESIBLE` y `TITULO_VENTANA` intentan primero la coincidencia
exacta, sin espacios a los lados. Solo si no encuentran resultados, normalizan a minúsculas
con `Locale.ROOT`, eliminan diacríticos y colapsan espacios. Así, `contrasena` encuentra
`Contraseña` y `  iniciar   sesion ` encuentra `Iniciar Sesión`. Varias coincidencias flexibles
producen `NO_ENCONTRADO` con motivo `ambiguo`; no se elige arbitrariamente.

### Modo oculto

El argumento opcional `oculto=true` del agente activa `SupresorFoco`. El núcleo lo transmite
cuando la suite declara `config.oculto: true`. Por defecto las ventanas permanecen visibles.
El supresor desactiva `autoRequestFocus` y `focusableWindowState` y mueve cada ventana a
100 píxeles a la derecha del extremo de todas las pantallas, incluso en configuraciones
con coordenadas negativas. Atiende también diálogos, nuevas presentaciones, activaciones
y movimientos posteriores de la aplicación. Una revisión en el EDT cada 100 ms corrige
cambios nativos aunque su notificación no permita recolocar la ventana. Se instala antes de su `main`.

`SEMANTICO` y `EVENTOS` funcionan con ventanas fuera de pantalla y no enfocables; las capturas
siguen pintando la ventana sin necesitar una pantalla física. La aplicación sigue necesitando
un entorno gráfico: no se transforma Swing en headless. No se interceptan `toFront()` ni
`requestFocus()` mediante bytecode. La supresión inicial se aplica al recibir los eventos
AWT; puede producirse una presentación nativa breve antes de recibirlos.

### Árbol de la interfaz

`ARBOL` describe todas las ventanas visibles en el EDT, incluidos diálogos y menús emergentes
abiertos. Los menús ligeros forman parte de la ventana que los contiene; los pesados aparecen
en su propia ventana. Cada nodo incluye `ruta`, `alcanzable` y `acciones` (contratos-v1.4).

Las rutas conservan los contenedores internos y los índices reales del árbol Swing. Se prefiere
el título de la ventana; si está vacío o contiene `/`, se usa su nombre y, si tampoco sirve,
su clase simple. `alcanzable` exige visibilidad, estado habilitado, ancestros visibles,
tamaño positivo e intersección con el área de su ventana. Funciona aunque esta esté fuera de
las pantallas físicas. No comprueba si otra ventana del escritorio tapa el control.

Las acciones describen las capacidades del tipo de control incluso cuando está oculto o
deshabilitado: `PULSAR`, `LLENAR`, `SELECCIONAR`, `MARCAR` e `IR_A`. Los nodos internos
`JRootPane`, `JLayeredPane`, el glass pane y sus descendientes no ofrecen acciones para la
exploración. Se mantienen en la estructura y en las rutas. Las contraseñas se describen sin
exponer su contenido.

### Pendiente para el dueño del módulo (P1)

Ordenado según `PLAN_DE_TRABAJO.md` (sprints 1 a 4):

1. Modo `ROBOT`.
2. Ganchos con ByteBuddy (v0.3): `CONFIG_GANCHOS`, JDBC y Firestore.

- **Dueño:** P1. El paquete `api` (`AdaptadorToolkit`, `Gancho`, `EmisorEventos`) es un contrato del líder (L).
- **Paquete raíz:** `io.github.physanto.opsecengine.agente`

## Dependencias permitidas

- `opsec-protocolo`.
- ByteBuddy.
- `opsec-soporte-pruebas`, solo para pruebas.

Nunca puede depender del núcleo ni del dominio.

## Pruebas

Las pruebas unitarias usan `MotorFalso` de `opsec-soporte-pruebas` para probar el servidor sin el núcleo (`ServidorProtocoloTest`), incluido que `CERRAR` emita `APP_TERMINADA` y cierre la app solo cuando el motor lo pide; prueban el plazo del cierre (`CierreAplicacionTest`) y la consola, el adaptador, los eventos sintéticos, la selección de opciones, la navegación entre pestañas, las capturas y el observador de excepciones sin ventanas (`ObservadorConsolaTest`, `AdaptadorSwingTest`, `AccionesEventosTest`, `SeleccionTest`, `NavegacionTest`, `CapturasTest`, `ObservadorExcepcionesTest`). Se verifica la entrega de eventos de ratón y teclado, el reemplazo y borrado del texto, las contraseñas, el texto multilínea y el respeto de las teclas consumidas por la aplicación.

`BuscadorComponentesTest` comprueba en ventanas reales la cercanía horizontal y vertical, los empates, la prioridad de `setLabelFor`, las rutas y la comparación flexible. Se omite sin pantalla.

La prueba de integración `AgenteIT` (failsafe, fase `integration-test`) lanza una JVM con `-javaagent` apuntando al jar empaquetado: con `AplicacionDePrueba` verifica el `HOLA` y los eventos de consola; con `VentanaDePrueba` (una ventana Swing de las pruebas) llena un campo mediante una etiqueta sin `setLabelFor`, pulsa botones, recibe el diálogo modal y la excepción, y pide el árbol; además selecciona en un combo, marca una casilla, va a una pestaña, cierra un diálogo y pide capturas de la ventana y del diálogo modal; otra prueba lanza excepciones sin capturar en el EDT y en otro hilo y comprueba sus eventos. También se comprueba que un diálogo reutilizado emita `liberada=false` al ocultarse y `true` al destruirse, y que después de `CERRAR` la app termine con código 0: la ventana recibe el pedido de cierre y, sin ventanas, el agente fuerza la salida. Repite las pruebas con ventanas en los modos `SEMANTICO` y `EVENTOS`; se omite si el entorno no tiene pantalla.

`DescriptorComponentesTest` comprueba las acciones por tipo (también sin pantalla), la
protección de contraseñas, las rutas, el alcance, el glass pane y los menús ligeros y pesados.
`ArbolAppPracticaIT` arranca el jar real, navega hasta `InicioSesion` y compara la estructura,
los nombres y las acciones con `contratos/protocolo/ejemplos/arbol.json`. Verifica además las
rutas de sus controles accionables. La dependencia de prueba en la app solo ordena el reactor;
se excluye del classpath de las pruebas. Las pruebas que necesitan ventanas se omiten sin pantalla.

Desde la raíz del repositorio:

```sh
./mvnw -B -pl opsec-agente -am verify
```

En Windows:

```bat
mvnw.cmd -B -pl opsec-agente -am verify
```

`SupresorFocoTest` verifica la posición en varias pantallas y la reacción a apertura, activación y recentrado. `AgenteIT` repite el llenado, pulsación, diálogo y capturas en modo oculto con SEMANTICO y EVENTOS, comprobando desde la aplicación que tanto la ventana como su diálogo quedan fuera de todas las pantallas y sin foco.
