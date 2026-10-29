# opsec-verificacion

Pruebas de arquitectura con ArchUnit que hacen fallar el build si se rompen las reglas de dependencia entre módulos. No contiene clases principales: todo su código está en `src/test/java`.

- **Dueño:** líder (L).
- **Paquete raíz:** `io.github.physanto.opsecengine.verificacion`

## Dependencias permitidas

- Todos los módulos del motor, solo con `<scope>test</scope>`.
- ArchUnit (`archunit-junit5`), solo para pruebas.

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
