# opsec-agente

Agente que se carga con `-javaagent` dentro de la JVM de la aplicación probada: ejecuta acciones sobre Swing, observa ventanas, diálogos, excepciones y consola, instala ganchos y toma capturas. Se compila para Java 8 (`--release 8`).

El empaquetado genera, además del jar normal, `target/opsec-agente-<versión>-agente.jar`: el jar del agente con las dependencias reubicadas bajo `io.github.physanto.opsecengine.agente.interno` y el manifiesto con `Premain-Class`, `Agent-Class` y `Can-Retransform-Classes`. Uso:

```sh
java -javaagent:opsec-agente-<versión>-agente.jar=<argumentos> -jar app.jar
```

**Estado actual: agente eco.** `AgenteMain` solo hace el saludo: lee `puerto` y `token`, se conecta al motor en un hilo daemon, envía `HOLA`, responde `ERROR` con el código `NO_IMPLEMENTADO` a cualquier otro mensaje y termina la conversación con `CERRAR` (sin cerrar la aplicación). Es un esqueleto que el dueño del módulo reemplazará por el agente real.

- **Dueño:** P1. El paquete `api` (`AdaptadorToolkit`, `Gancho`, `EmisorEventos`) es un contrato del líder (L).
- **Paquete raíz:** `io.github.physanto.opsecengine.agente`

## Dependencias permitidas

- `opsec-protocolo`.
- ByteBuddy.
- `opsec-soporte-pruebas`, solo para pruebas.

Nunca puede depender del núcleo ni del dominio.

## Pruebas

Las pruebas unitarias usan `MotorFalso` de `opsec-soporte-pruebas` para probar el agente sin el núcleo. La prueba de integración `AgenteEcoIT` (failsafe, fase `integration-test`) lanza `AplicacionDePrueba` en otra JVM con `-javaagent` apuntando al jar empaquetado y verifica el `HOLA`.

Desde la raíz del repositorio:

```sh
./mvnw -B -pl opsec-agente -am verify
```

En Windows:

```bat
mvnw.cmd -B -pl opsec-agente -am verify
```
