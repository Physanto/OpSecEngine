# opsec-soporte-pruebas

Dobles de prueba compartidos (`AgenteFalso`, `MotorFalso`) y datos de ejemplo (trazas, mapas y resultados) para que cada módulo se pruebe sin depender del trabajo de los demás. Los otros módulos lo usan solo con `<scope>test</scope>`.

- **Dueño:** líder (L). Es un contrato: los cambios se piden con una solicitud de cambio de contrato.
- **Paquete raíz:** `io.github.physanto.opsecengine.soporte`

## Dependencias permitidas

- `opsec-dominio`.
- `opsec-protocolo`.
- Jackson (`jackson-databind`).

## Pruebas

Desde la raíz del repositorio:

```sh
./mvnw -B -pl opsec-soporte-pruebas -am verify
```

En Windows:

```bat
mvnw.cmd -B -pl opsec-soporte-pruebas -am verify
```
