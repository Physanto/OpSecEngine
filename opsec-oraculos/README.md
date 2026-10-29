# opsec-oraculos

Oráculos incluidos en el motor. Cada uno implementa `Oraculo` y decide hallazgos sobre la traza de un evento, un caso o una suite. El núcleo los descubre con `ServiceLoader`.

- **Dueño:** P2.
- **Paquete raíz:** `io.github.physanto.opsecengine.oraculos`

## Dependencias permitidas

- `opsec-dominio`.

No puede depender del núcleo, del agente ni del protocolo.

## Pruebas

Desde la raíz del repositorio:

```sh
./mvnw -B -pl opsec-oraculos -am verify
```

En Windows:

```bat
mvnw.cmd -B -pl opsec-oraculos -am verify
```
