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
| `ServidorProtocolo` | `agente` | Saluda con `HOLA`; atiende `EJECUTAR_PASO`, `ARBOL_SOLICITUD` y `CERRAR`; responde `NO_IMPLEMENTADO` a lo demás. |
| `EmisorProtocolo` | `agente` | Implementa `EmisorEventos`: numera y envía los `EVENTO`; guarda los anteriores a la conexión y los envía después del saludo. |
| `ObservadorConsola` | `agente` | Envuelve `System.out` y `System.err`; emite `CONSOLA` por línea sin cambiar lo que la app muestra. |
| `RegistroAgente` | `agente` | Mensajes propios del agente en la salida de error original, con el prefijo `[opsec-agente]`. |
| `AdaptadorSwing` | `agente.swing` | Implementa `AdaptadorToolkit`: `PULSAR`, `LLENAR` y `ESPERAR` en modo `SEMANTICO`, y el árbol de componentes. |
| `BuscadorComponentes` | `agente.swing` | Resuelve localizadores: `ETIQUETA` (`setLabelFor`), `TEXTO`, `NOMBRE_COMPONENTE`, `NOMBRE_ACCESIBLE`, `TIPO`, `TITULO_VENTANA`, `CLASE_VENTANA`; verifica que el componente sea alcanzable. |
| `EjecutorEdt` | `agente.swing` | Ejecuta en el EDT con tiempo máximo; detecta cuando una acción abre un diálogo modal. |
| `ObservadorVentanas` | `agente.swing` | Emite `VENTANA_ABIERTA`, `VENTANA_CERRADA` y `DIALOGO` (con el texto de los `JOptionPane`). |
| `DescriptorComponentes` | `agente.swing` | Describe componentes como `NodoArbolDto`; nunca incluye el texto de un `JPasswordField`. |

Comportamiento que conviene conocer:

- Antes de actuar, el agente espera hasta `esperaMaximaMs` a que el componente aparezca y sea alcanzable (visible, habilitado y sin un diálogo modal encima).
- Si la acción abre un diálogo modal, el paso termina con `OK` apenas aparece el diálogo.
- Si el código de la app lanza una excepción durante la acción, se emite `EXCEPCION` con `capturada=false` y el paso termina con `OK`: la acción se hizo y el fallo es de la app.
- `CERRAR` termina la conversación pero no cierra la app; la detiene el motor.

### Pendiente para el dueño del módulo (P1)

Ordenado según `PLAN_DE_TRABAJO.md` (sprints 1 a 4):

1. Modo `EVENTOS` (eventos sintéticos con `dispatchEvent`), que es el modo por defecto de las suites; hoy responde `ERROR`.
2. Acciones `SELECCIONAR`, `MARCAR`, `IR_A`, `CERRAR_VENTANA` y `CAPTURAR`.
3. Capturas pintando componentes en `BufferedImage` (`AdaptadorSwing.capturar` y el mensaje `CAPTURAR`).
4. `ObservadorExcepciones`: manejador global del EDT y de los otros hilos (hoy solo se ven las excepciones de las acciones del agente).
5. `VENTANA_CERRADA` también al ocultar con `setVisible(false)`.
6. Localizadores robustos: etiqueta sin `setLabelFor` (por cercanía), estrategia `RUTA`, comparación flexible (mayúsculas, dos puntos finales).
7. `CERRAR` que termine la app de forma ordenada; modo oculto y supresión de foco; modo `ROBOT`.
8. Ganchos con ByteBuddy (v0.3): `CONFIG_GANCHOS`, JDBC y Firestore.

- **Dueño:** P1. El paquete `api` (`AdaptadorToolkit`, `Gancho`, `EmisorEventos`) es un contrato del líder (L).
- **Paquete raíz:** `io.github.physanto.opsecengine.agente`

## Dependencias permitidas

- `opsec-protocolo`.
- ByteBuddy.
- `opsec-soporte-pruebas`, solo para pruebas.

Nunca puede depender del núcleo ni del dominio.

## Pruebas

Las pruebas unitarias usan `MotorFalso` de `opsec-soporte-pruebas` para probar el servidor sin el núcleo (`ServidorProtocoloTest`), y prueban la consola y el adaptador sin ventanas (`ObservadorConsolaTest`, `AdaptadorSwingTest`).

La prueba de integración `AgenteIT` (failsafe, fase `integration-test`) lanza una JVM con `-javaagent` apuntando al jar empaquetado: con `AplicacionDePrueba` verifica el `HOLA` y los eventos de consola; con `VentanaDePrueba` (una ventana Swing de las pruebas) llena un campo, pulsa botones, recibe el diálogo modal y la excepción, y pide el árbol. La prueba con ventanas se omite si el entorno no tiene pantalla.

Desde la raíz del repositorio:

```sh
./mvnw -B -pl opsec-agente -am verify
```

En Windows:

```bat
mvnw.cmd -B -pl opsec-agente -am verify
```
