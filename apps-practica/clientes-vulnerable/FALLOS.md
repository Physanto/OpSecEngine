# Fallos sembrados en clientes-vulnerable

La app trae seis fallos sembrados. Por defecto **todos están activos**; cada uno tiene un identificador y se corrige con la propiedad del sistema `practica.corregir`:

| Valor | Efecto |
|---|---|
| sin la propiedad, o vacía | todos los fallos activos (versión vulnerable) |
| `-Dpractica.corregir=F1,F4` | corrige solo F1 y F4; los demás siguen activos |
| `-Dpractica.corregir=todos` | corrige todos (versión corregida) |

Los ids no distinguen mayúsculas y admiten espacios alrededor de las comas. Un id desconocido detiene la app con código de salida 2. Al arrancar, la app escribe en la consola qué fallos quedaron activos y cuáles corregidos.

## Resumen

| Id | Descripción | CWE | Oráculo que debería detectarlo |
|---|---|---|---|
| F1 | Cuenta de soporte con usuario y contraseña escritos en el código | CWE-798 | Análisis estático (FindSecBugs `HARD_CODE_PASSWORD`); en ejecución, `CREDENCIALES-DEBILES` si los ataques prueban la pareja `soporte` / `soporte123` |
| F2 | El modo "Local" abre el menú del administrador sin iniciar sesión | CWE-306 | `SIN-SESION`; también `AUTORIZACION-DIFERENCIAL` |
| F3 | La ventana `EliminarPersona` tiene un `main` público que la abre sin iniciar sesión | CWE-306 | `SIN-SESION` (con `EliminarPersona` como punto de entrada) |
| F4 | El inicio de sesión concatena el texto de los campos en un `Statement` | CWE-89 | `SQLI-CANARIO` |
| F5 | Mensajes distintos para usuario inexistente y contraseña incorrecta | CWE-204 | `ENUMERACION-USUARIOS` |
| F6 | La contraseña se imprime en la consola al iniciar sesión | CWE-532 | `CONTRASENA-EN-CONSOLA` |

Los ids de oráculo son los del catálogo `contratos/catalogos/oraculos.yaml`.

## Datos semilla

Usuarios de la tabla `usuario`:

| Usuario | Contraseña | Rol |
|---|---|---|
| `Admin` | `Admin#2026` | ADMINISTRADOR |
| `Cliente` | `Cliente#2026` | CLIENTE |

Personas de la tabla `persona`: uids `P001` a `P005`. La base vive en memoria (`jdbc:h2:mem:clientes`) y se vuelve a crear en cada ejecución.

## Detalle de cada fallo

### F1 – Credenciales embebidas en el código (CWE-798)

**Dónde:** `servicio/LoginServicio.java`, constantes `USUARIO_SOPORTE` y `CONTRASENA_SOPORTE`.

**Cómo reproducirlo a mano:**
1. Pulsar **Avanzar** y luego **Nube**.
2. Escribir el usuario `soporte` y la contraseña `soporte123` y pulsar **Iniciar Sesion**.
3. Se abre el menú del administrador, aunque `soporte` no existe en la tabla `usuario`.

**Corregido:** las credenciales se leen solo de la base; `soporte` recibe el mensaje de credenciales inválidas.

**Detección:** las constantes siguen en el bytecode aunque F1 esté corregido, así que el análisis estático señala ambas versiones. En ejecución, el catálogo v1 no tiene un oráculo propio para este fallo: lo descubre `CREDENCIALES-DEBILES` si la biblioteca de ataques incluye la pareja `soporte` / `soporte123`.

### F2 – Modo "Local" sin inicio de sesión (CWE-306)

**Dónde:** `gui/EscogerModo.java` (botón **Local**) y `servicio/ControlAcceso.java`.

**Cómo reproducirlo a mano:**
1. Pulsar **Avanzar** y luego **Local**.
2. Se abre el **Menú administrador** sin pedir credenciales.
3. Pulsar **Eliminar Persona**, escribir `P001` y pulsar **Eliminar**: el área de resultado dice "Se eliminó la persona P001."

**Corregido:** **Local** avisa que el modo local también requiere iniciar sesión y abre el inicio de sesión. Las ventanas administrativas solo se abren con una sesión de administrador.

**Detección:** `SIN-SESION` ve un `DELETE` sin sesión iniciada. `AUTORIZACION-DIFERENCIAL` ve que el contexto sin sesión alcanza "Eliminar Persona", que la política reserva al rol admin.

### F3 – Ventana administrativa con `main` público (CWE-306)

**Dónde:** `gui/EliminarPersona.java`, método `main`.

**Cómo reproducirlo a mano:**
1. Abrir la ventana directamente:
   ```sh
   java -cp apps-practica/clientes-vulnerable/target/clientes-vulnerable.jar io.github.physanto.opsecengine.practica.gui.EliminarPersona
   ```
2. Escribir `P002` y pulsar **Eliminar**: la persona se elimina sin haber iniciado sesión.

**Corregido:** el `main` verifica la sesión, muestra "Acceso denegado: esta opción requiere una sesión de administrador." y abre la portada.

**Detección:** `SIN-SESION`, con un proyecto cuyo `app.principal` es `io.github.physanto.opsecengine.practica.gui.EliminarPersona` (o con ese punto de entrada encontrado por el explorador).

### F4 – SQL concatenado en el inicio de sesión (CWE-89)

**Dónde:** `datos/UsuarioRepositorio.java`, método `consultarConcatenado`.

**Cómo reproducirlo a mano:**
1. Pulsar **Avanzar** y luego **Nube**.
2. Escribir el usuario `Admin' --` y cualquier contraseña, y pulsar **Iniciar Sesion**: se entra como Admin.
3. Otra variante: usuario `Admin` y contraseña `' OR '1'='1`.
4. Una comilla suelta (`'`) en el usuario rompe la sentencia y la app responde "No se pudo consultar la base de datos."

**Corregido:** las consultas usan `PreparedStatement`; esos textos se tratan como datos y el intento falla.

**Detección:** `SQLI-CANARIO`: el canario escrito en el campo aparece dentro del texto de una sentencia de tipo `STATEMENT`. Necesita los ganchos JDBC del agente.

### F5 – Enumeración de usuarios (CWE-204)

**Dónde:** `servicio/LoginServicio.java`, al final de `iniciarSesion`.

**Cómo reproducirlo a mano:**
1. Pulsar **Avanzar** y luego **Nube**.
2. Usuario `NoExiste`, contraseña `x`: el mensaje dice "El usuario no existe."
3. Usuario `Admin`, contraseña `x`: el mensaje dice "La contraseña es incorrecta."

**Corregido:** los dos casos muestran "Usuario o contraseña incorrectos."

**Detección:** `ENUMERACION-USUARIOS`, que compara los diálogos de los casos C01 (usuario inexistente) y C02 (contraseña incorrecta) de la suite de inicio de sesión.

### F6 – Contraseña en la consola (CWE-532)

**Dónde:** `servicio/LoginServicio.java`, al comienzo de `iniciarSesion`.

**Cómo reproducirlo a mano:**
1. Ejecutar la app desde una terminal.
2. Iniciar sesión con cualquier usuario y contraseña.
3. La terminal muestra `Intento de inicio de sesión: usuario=Admin, contraseña=Admin#2026`.

**Corregido:** la línea solo muestra el usuario: `Intento de inicio de sesión del usuario Admin`.

**Detección:** `CONTRASENA-EN-CONSOLA`, que busca la contraseña de prueba del rol en los eventos de consola.

## Comportamientos que no son fallos sembrados

Para que cada oráculo se active solo con su fallo, la app evita a propósito otros problemas conocidos:

- La contraseña se escribe en un `JPasswordField` (no activa `CONTRASENA-EN-CAMPO-VISIBLE`).
- Los errores de base de datos muestran un mensaje sin pila de llamadas (no activa `TRAZA-EN-DIALOGO`).
- Las contraseñas de los usuarios semilla no son triviales (no activan `CREDENCIALES-DEBILES`, salvo la cuenta de F1).
- La base es H2 en memoria, sin conexión de red (no activa `JDBC-SIN-CIFRADO`).

Hay debilidades que la app no corrige porque quedan fuera de esta versión: no bloquea tras varios intentos fallidos (`SIN-BLOQUEO`, CWE-307) y guarda las contraseñas en texto plano.
