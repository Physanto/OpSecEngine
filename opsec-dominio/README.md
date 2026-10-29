# opsec-dominio

Modelos inmutables del dominio (`record` y enums) e interfaces de extensión del motor: `Oraculo`, `GeneradorCasos`, `Reportero`, `ProveedorDatos` y `ProveedorIA`. No contiene lógica de infraestructura.

- **Dueño:** líder (L). Es un contrato: los cambios se piden con una solicitud de cambio de contrato.
- **Paquete raíz:** `io.github.physanto.opsecengine.dominio`

## Paquetes

- `dominio.caso`: localizadores, pasos, casos, suites y configuración de ejecución.
- `dominio.configuracion`: proyecto, roles, entorno de datos y operaciones sensibles (`proyecto.yaml`).

Todos los modelos son inmutables y validan sus datos al construirse. Las colecciones nulas se convierten en vacías y el campo `extensiones` permite agregar datos sin romper el contrato.

## Dependencias permitidas

Ninguna. No depende de otros módulos ni de librerías externas.

## Pruebas

Desde la raíz del repositorio:

```sh
./mvnw -B -pl opsec-dominio -am verify
```

En Windows:

```bat
mvnw.cmd -B -pl opsec-dominio -am verify
```
