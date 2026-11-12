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
| `AdaptadorSwing` | `agente.swing` | Implementa `AdaptadorToolkit`: `PULSAR`, `LLENAR`, `SELECCIONAR`, `MARCAR`, `IR_A`, `CERRAR_VENTANA` y `ESPERAR` en los modos `SEMANTICO` y `EVENTOS`, y el árbol de componentes. |
| `AccionesEventos` | `agente.swing` | Envía eventos sintéticos de ratón y teclado al componente elegido, en el EDT. |
| `Seleccion` | `agente.swing` | Busca una opción por su texto en un combo, una lista, una tabla o un árbol y la selecciona. |
| `Navegacion` | `agente.swing` | `IR_A` (elige pestañas y trae la ventana al frente) y `CERRAR_VENTANA` (envía `WINDOW_CLOSING`). |
| `Capturas` | `agente.swing` | Elige la ventana que se captura, la pinta en un `BufferedImage` y la codifica en PNG y Base64. |
| `BuscadorComponentes` | `agente.swing` | Resuelve localizadores: `ETIQUETA` (`setLabelFor`), `TEXTO`, `NOMBRE_COMPONENTE`, `NOMBRE_ACCESIBLE`, `TIPO`, `TITULO_VENTANA`, `CLASE_VENTANA`; verifica que el componente sea alcanzable. |
| `EjecutorEdt` | `agente.swing` | Ejecuta en el EDT con tiempo máximo; detecta cuando una acción abre un diálogo modal. |
| `ObservadorExcepciones` | `agente.swing` | Manejador por defecto de excepciones no capturadas: emite `EXCEPCION` (`capturada=false`) por las del EDT y las de los demás hilos y las pasa al manejador anterior. |
| `ObservadorVentanas` | `agente.swing` | Emite `VENTANA_ABIERTA`, `VENTANA_CERRADA` y `DIALOGO` (con el texto de los `JOptionPane`). |
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
- Las excepciones que nadie captura, en el EDT (escuchadores, tareas de `invokeLater`) o en otro hilo, llegan como `EXCEPCION` con `capturada=false`, el nombre del hilo y hasta 30 líneas de pila. Después siguen al manejador que hubiera; si no había, la pila se escribe en `System.err` como sin el agente. No se ven las de hilos con manejador propio ni las que ocurran si la app reemplaza después el manejador por defecto.
- `CAPTURAR` (mensaje) responde `CAPTURA` con la ventana pintada en PNG con `printAll`, sin `Robot`: funciona aunque la ventana esté tapada o fuera de la pantalla, pero no incluye la barra de título del sistema operativo. La ventana se elige por título o clase (completa o simple); sin nombre, la ventana activa o, si no hay, el último diálogo modal visible o la última ventana visible. Si ninguna coincide, responde `ERROR` con el código `ERROR_INTERNO`. La acción `CAPTURAR` dentro de un `EJECUTAR_PASO` responde `ERROR`: la imagen no cabe en el resultado del paso y el motor debe pedirla con el mensaje.
- `CERRAR` termina la conversación pero no cierra la app; la detiene el motor.

### Pendiente para el dueño del módulo (P1)

Ordenado según `PLAN_DE_TRABAJO.md` (sprints 1 a 4):

1. `VENTANA_CERRADA` también al ocultar con `setVisible(false)`.
2. Localizadores robustos: etiqueta sin `setLabelFor` (por cercanía), estrategia `RUTA`, comparación flexible (mayúsculas, dos puntos finales).
3. `CERRAR` que termine la app de forma ordenada; modo oculto y supresión de foco; modo `ROBOT`.
4. Ganchos con ByteBuddy (v0.3): `CONFIG_GANCHOS`, JDBC y Firestore.

- **Dueño:** P1. El paquete `api` (`AdaptadorToolkit`, `Gancho`, `EmisorEventos`) es un contrato del líder (L).
- **Paquete raíz:** `io.github.physanto.opsecengine.agente`

## Dependencias permitidas

- `opsec-protocolo`.
- ByteBuddy.
- `opsec-soporte-pruebas`, solo para pruebas.

Nunca puede depender del núcleo ni del dominio.

## Pruebas

Las pruebas unitarias usan `MotorFalso` de `opsec-soporte-pruebas` para probar el servidor sin el núcleo (`ServidorProtocoloTest`), y prueban la consola, el adaptador, los eventos sintéticos, la selección de opciones, la navegación entre pestañas, las capturas y el observador de excepciones sin ventanas (`ObservadorConsolaTest`, `AdaptadorSwingTest`, `AccionesEventosTest`, `SeleccionTest`, `NavegacionTest`, `CapturasTest`, `ObservadorExcepcionesTest`). Se verifica la entrega de eventos de ratón y teclado, el reemplazo y borrado del texto, las contraseñas, el texto multilínea y el respeto de las teclas consumidas por la aplicación.

La prueba de integración `AgenteIT` (failsafe, fase `integration-test`) lanza una JVM con `-javaagent` apuntando al jar empaquetado: con `AplicacionDePrueba` verifica el `HOLA` y los eventos de consola; con `VentanaDePrueba` (una ventana Swing de las pruebas) llena un campo, pulsa botones, recibe el diálogo modal y la excepción, y pide el árbol; además selecciona en un combo, marca una casilla, va a una pestaña, cierra un diálogo y pide capturas de la ventana y del diálogo modal; otra prueba lanza excepciones sin capturar en el EDT y en otro hilo y comprueba sus eventos. Repite las pruebas con ventanas en los modos `SEMANTICO` y `EVENTOS`; se omite si el entorno no tiene pantalla.

Desde la raíz del repositorio:

```sh
./mvnw -B -pl opsec-agente -am verify
```

En Windows:

```bat
mvnw.cmd -B -pl opsec-agente -am verify
```
