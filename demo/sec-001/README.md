# SEC-001: inicio de sesión de punta a punta (integración v0.1)

Ocho casos de inicio de sesión sobre la app de práctica `apps-practica/clientes-vulnerable`, ejecutados con el agente real en modo `EVENTOS`. Los oráculos deciden el veredicto y el motor escribe el informe HTML y el JSON.

| Proyecto | App | Resultado esperado | Código de salida |
|---|---|---|---|
| `proyecto.yaml` | Todos los fallos activos | `NO_CUMPLE`: F6 (CWE-532), F7 en C04 (CWE-209), F5 en C01/C02 (CWE-204) y F4 en C13 (CWE-89) | 3 |
| `proyecto-corregido.yaml` | Todos corregidos (`-Dpractica.corregir=todos`) | Los 8 casos `CUMPLE` | 0 |

## Casos

| Id | Qué prueba | Oráculo principal |
|---|---|---|
| C01, C02 | Usuario inexistente y contraseña incorrecta | `ENUMERACION-USUARIOS` |
| C03 | Campos vacíos | implícitos |
| C04 | Comilla en el usuario (error SQL) | `TRAZA-EN-DIALOGO` |
| C05, C06 | Espacios alrededor y mayúsculas | implícitos |
| C07 | Inicio de sesión con `rol: admin` | `CONTRASENA-EN-CONSOLA` |
| C13 | Un texto con comilla y una marca (`{canario}`) en el usuario no debe llegar concatenado a una sentencia SQL | `SQLI-CANARIO` |

Los oráculos implícitos (por ejemplo `CONTRASENA-EN-CONSOLA`) se aplican a todos los casos; por eso, con F6 activo, casi todos los casos no cumplen.

## Ejecutar

Desde la raíz del repositorio (en Windows, `mvnw.cmd` y `\` en las rutas):

```sh
./mvnw -B package -DskipTests
java -jar opsec-cli/target/opsec-<versión>.jar ejecutar demo/sec-001/proyecto.yaml -f html,json
java -jar opsec-cli/target/opsec-<versión>.jar ejecutar demo/sec-001/proyecto-corregido.yaml -f html,json
```

El informe queda en `demo/sec-001/resultados/<fecha>/sec-001/informe.html`. La prueba `opsec-cli/src/test/java/.../Sec001IT.java` ejecuta lo mismo en cada `./mvnw verify` y en la CI (se omite sin pantalla).

## Pendiente para versiones siguientes

- Bloqueo tras varios intentos (`SIN-BLOQUEO`) y el resto de los 15 casos originales se agregan junto con la biblioteca de ataques.
