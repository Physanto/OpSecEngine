# clientes-vulnerable

Aplicación Swing de práctica con fallos sembrados, que sirve de objetivo para evaluar el motor y para docencia. Su artifactId es `opsec-clientes-vulnerable`.

- **Dueño:** P3.
- **Paquete raíz:** `io.github.physanto.opsecengine.practica`

## Dependencias permitidas

Ninguna. La app no depende de ningún módulo del motor: el motor la prueba desde fuera.

## Pruebas

Desde la raíz del repositorio:

```sh
./mvnw -B -pl apps-practica/clientes-vulnerable -am verify
```

En Windows:

```bat
mvnw.cmd -B -pl apps-practica/clientes-vulnerable -am verify
```
