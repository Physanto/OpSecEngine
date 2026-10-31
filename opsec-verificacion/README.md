# opsec-verificacion

Pruebas que hacen fallar el build si se rompe un contrato. No contiene clases principales: todo su código está en `src/test/java`.

- `ReglasDependenciaTest`: reglas de dependencia entre módulos, con ArchUnit.
- `EsquemasTest`: los esquemas de `contratos/esquemas/` son JSON Schema 2020-12 válidos, sus valores enumerados coinciden con el dominio y rechazan los errores típicos.
- `EjemplosTest`: cada archivo de `contratos/ejemplos/` cumple su esquema y sus referencias (roles, oráculos, ataques, CWE, campos de eventos) existen.
- `TrazasSoporteTest`: las trazas de ejemplo de `opsec-soporte-pruebas` cumplen el esquema de línea de traza y el catálogo de eventos.
- `CatalogosTest`: los catálogos de `contratos/catalogos/` se leen sin errores y coinciden con el dominio (`TipoEvento`, `Accion`, `ModoAccion`, `Momento`) y con el protocolo (`CodigoResultado`).

- **Dueño:** líder (L).
- **Paquete raíz:** `io.github.physanto.opsecengine.verificacion`

## Dependencias permitidas

- Todos los módulos del motor, solo con `<scope>test</scope>`.
- ArchUnit (`archunit-junit5`), solo para pruebas.
- `json-schema-validator` y Jackson (`jackson-databind`, `jackson-dataformat-yaml`), solo para pruebas.

No depende de la app de práctica.

## Pruebas

Desde la raíz del repositorio:

```sh
./mvnw -B -pl opsec-verificacion -am verify
```

En Windows:

```bat
mvnw.cmd -B -pl opsec-verificacion -am verify
```
