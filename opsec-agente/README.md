# opsec-agente

Agente que se carga con `-javaagent` dentro de la JVM de la aplicación probada: ejecuta acciones sobre Swing, observa ventanas, diálogos, excepciones y consola, instala ganchos y toma capturas. Se compila para Java 8 (`--release 8`).

El empaquetado genera, además del jar normal, `target/opsec-agente-<versión>-agente.jar`: el jar del agente con las dependencias reubicadas bajo `io.github.physanto.opsecengine.agente.interno` y el manifiesto con `Premain-Class`, `Agent-Class` y `Can-Retransform-Classes`. Uso:

```sh
java -javaagent:opsec-agente-<versión>-agente.jar=<argumentos> -jar app.jar
```

- **Dueño:** P1. El paquete `api` (`AdaptadorToolkit`, `Gancho`, `EmisorEventos`) es un contrato del líder (L).
- **Paquete raíz:** `io.github.physanto.opsecengine.agente`

## Dependencias permitidas

- `opsec-protocolo`.
- ByteBuddy.

Nunca puede depender del núcleo ni del dominio.

## Pruebas

Desde la raíz del repositorio:

```sh
./mvnw -B -pl opsec-agente -am verify
```

En Windows:

```bat
mvnw.cmd -B -pl opsec-agente -am verify
```
