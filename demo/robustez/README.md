# ROBUSTEZ-001: inyección de fallos de base de datos (#16)

Un caso de robustez sobre la app de práctica `apps-practica/clientes-vulnerable`: el motor inicia sesión con los datos del caso y, mientras la app muestra su pantalla de login, el agente hace que la conexión a la base de datos falle. El oráculo `TRAZA-EN-DIALOGO` revisa el diálogo de error que ve el usuario.

| Proyecto | App | Resultado esperado | Código de salida |
|---|---|---|---|
| `proyecto.yaml` | F7 activo (F6 corregido para aislarlo) | `NO_CUMPLE`: `TRAZA-EN-DIALOGO` (CWE-209), el diálogo muestra la pila de la excepción inyectada | 3 |
| `proyecto-corregido.yaml` | Todos corregidos (`-Dpractica.corregir=todos`) | `CUMPLE`: el diálogo dice "No se pudo consultar la base de datos." | 0 |

## Caso

| Id | Qué prueba | Fallo inyectado |
|---|---|---|
| R01 | Una conexión que falla al iniciar sesión no debe revelar detalles internos | `SQL_CONEXION:java.sql.SQLException:conexión inyectada` |

El fallo se declara en la sección `fallos` del caso (formato `punto:clase[:mensaje]`, ver `contratos/protocolo/README.md`). Como el caso no inicia sesión con un rol del proyecto, el motor espera a que la app muestre su primera ventana antes de activar el fallo: la app abre su base de datos al arrancar y, con el fallo ya activo, terminaría antes de mostrar nada.

## Ejecutar

Desde la raíz del repositorio (en Windows, `mvnw.cmd` y `\` en las rutas):

```sh
./mvnw -B package -DskipTests
java -jar opsec-cli/target/opsec-<versión>.jar ejecutar demo/robustez/proyecto.yaml -f html,json
java -jar opsec-cli/target/opsec-<versión>.jar ejecutar demo/robustez/proyecto-corregido.yaml -f html,json
```

Necesita una pantalla. La prueba `InyeccionFallosIT` (módulo `opsec-nucleo`) ejecuta los dos proyectos en cada `./mvnw verify` y comprueba la traza: los cinco pasos terminan en `OK`, el agente informa el fallo inyectado y el diálogo muestra la pila (vulnerable) o el mensaje genérico (corregida).
