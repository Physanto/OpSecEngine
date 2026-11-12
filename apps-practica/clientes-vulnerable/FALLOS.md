# Fallos sembrados en clientes-vulnerable

La app trae trece fallos sembrados. Por defecto **todos están activos**; cada uno tiene un identificador y se corrige con la propiedad del sistema `practica.corregir`:

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
| F7 | Un error SQL del login expone su pila de llamadas en un diálogo | CWE-209 | `TRAZA-EN-DIALOGO` (pendiente en el motor) |
| F8 | El botón de diagnóstico lanza una excepción sin capturar | CWE-248 | `EXCEPCION-NO-CAPTURADA` (oráculo implícito, sin CWE en el catálogo) |
| F9 | El registro permite campos vacíos o de formato inválido | CWE-20 | Validación del registro (nivel 2) |
| F10 | Un cliente puede modificar los datos de una persona | CWE-862 | Autorización por rol (nivel 3; el oráculo diferencial está pendiente) |
| F11 | Trabajo de tres segundos en el EDT | CWE-400 | `INTERFAZ-CONGELADA` (#47, pendiente) |
| F12 | Ventanas ocultas sin `dispose` | CWE-404 | Fugas de ventanas (#47, ID pendiente de contrato) |
| F13 | Operación con tres segundos de demora innecesaria fuera del EDT | CWE-400 | Tiempo de respuesta (#47, ID y umbral pendientes de contrato) |

Los ids de oráculo son los del catálogo `contratos/catalogos/oraculos.yaml`.

## Nivel 1 (v0.1, issue #61)

Se seleccionan F6, F7 y F8 por corresponder a los tres oráculos de la v0.1.
F1–F5 se mantienen como base original; F6–F8 son nivel 1, F9 cubre validación
de nivel 2 y F10 cubre seguridad de nivel 3.

| Fallo | Oráculo esperado | Corrección | Prueba unitaria |
|---|---|---|---|
| F6 | `CONTRASENA-EN-CONSOLA` | `-Dpractica.corregir=F6` | `LoginServicioTest`: contraseña presente/ausente en consola |
| F7 | `TRAZA-EN-DIALOGO` | `-Dpractica.corregir=F7` | `DiagnosticoServicioTest`: pila expuesta/mensaje genérico |
| F8 | `EXCEPCION-NO-CAPTURADA` | `-Dpractica.corregir=F8` | `DiagnosticoServicioTest`: excepción/acción normal; `VentanasTest`: conexión del botón |

Las pruebas de lógica no necesitan pantalla; la prueba del botón se omite sin
pantalla. La implementación de `TRAZA-EN-DIALOGO` corresponde al módulo de oráculos:
su ausencia no impide preparar F7, pero sí comprobar su detección con el motor actual.

## Niveles 2 y 3 (issue #63)

| Nivel | Ventana | Fallo | CWE | Corrección | Pruebas |
|---|---|---|---|---|---|
| 2 | Registrar persona | F9: acepta nombre vacío, documento no numérico y correo inválido | CWE-20 | `-Dpractica.corregir=F9` valida Uid, nombre, documento y correo antes del INSERT | `EdicionPersonaServicioTest` prueba el registro inválido vulnerable y corregido; `VentanasTest` identifica los controles |
| 3 | Modificar persona | F10: permite que el rol Cliente actualice cualquier persona | CWE-862 | `-Dpractica.corregir=F10` exige rol Administrador antes del UPDATE | `EdicionPersonaServicioTest` prueba escritura como Cliente y su rechazo corregido; `VentanasTest` identifica los controles |

Los nuevos accesos aparecen en los menús de administrador (registro y modificación)
y cliente (modificación). La versión corregida comparte el formulario y pasos; los
interruptores solo cambian la decisión del servicio.

El repositorio no implementa todavía `ExploracionService`. Las pruebas de ventanas
construyen sin mostrar las pantallas y comprueban componentes identificables y las
opciones del menú, pero no pueden satisfacer el criterio «el explorador encuentra
todas las ventanas». Ese criterio queda pendiente del módulo del motor; no se afirma
que el explorador actual lo haya verificado.

## Correspondencia de fallos y suites (issue #62)

Para F11–F13 (v0.5, #69), los interruptores, la reproducción, las correcciones y
las dependencias de detección se describen en [README.md](README.md#rendimiento-y-robustez-69).
Sus proyectos vulnerables y corregidos y sus suites están en
`ejemplos/rendimiento/f11`, `f12` y `f13`. Las pruebas de `RendimientoMuestraTest`
comprueban ambos estados de cada fallo; no sustituyen la detección pendiente de
los oráculos de #47 y de los eventos de #18.

El nivel 0 identifica la base original (F1–F5); el nivel 1 es la selección de la
v0.1 (F6–F8). Cada fallo tiene una suite de ejemplo y un proyecto que selecciona
sus interruptores. Las rutas de esta tabla son relativas a esta carpeta.

| Nivel | Fallo | CWE del fallo | Oráculo esperado | Suite de ejemplo |
|---|---|---|---|---|
| 0 (base) | F1 | CWE-798 | `CREDENCIALES-DEBILES` (CWE-521; véase la limitación debajo) | [f1.yaml](ejemplos/f1/suites/f1.yaml) |
| 0 (base) | F2 | CWE-306 | `SIN-SESION` | [f2.yaml](ejemplos/f2/suites/f2.yaml) |
| 0 (base) | F3 | CWE-306 | `SIN-SESION` | [f3.yaml](ejemplos/f3/suites/f3.yaml) |
| 0 (base) | F4 | CWE-89 | `SQLI-CANARIO` | [f4.yaml](ejemplos/f4/suites/f4.yaml) |
| 0 (base) | F5 | CWE-204 | `ENUMERACION-USUARIOS` | [f5.yaml](ejemplos/f5/suites/f5.yaml) |
| 1 | F6 | CWE-532 | `CONTRASENA-EN-CONSOLA` | [f6.yaml](ejemplos/f6/suites/f6.yaml) |
| 1 | F7 | CWE-209 | `TRAZA-EN-DIALOGO` | [f7.yaml](ejemplos/f7/suites/f7.yaml) |
| 1 | F8 | CWE-248 | `EXCEPCION-NO-CAPTURADA` (CWE nulo en el catálogo) | [f8.yaml](ejemplos/f8/suites/f8.yaml) |

**Alcance de los ejemplos:** no modifican la app ni implementan oráculos nuevos.
Cada carpeta `ejemplos/fN/` contiene `proyecto.yaml` y `suites/fN.yaml`. Los proyectos
corrigen los otros fallos para aislar el objetivo, salvo F7, que necesita F4 activo
para provocar el error de consulta. F3 especifica como clase principal
`io.github.physanto.opsecengine.practica.gui.EliminarPersona`; las demás suites
arrancan desde la portada. No se usa la acción `IR_A`, aún no implementada.

### Validar las ocho suites

Desde la raíz del repositorio, con JDK 21, construir la CLI y la app:

```bash
./mvnw -B verify
```

`opsec validar` recibe un **proyecto**, no un archivo de suite: valida también
los YAML de `suites/` junto a él. Para un fallo:

```bash
java -jar opsec-cli/target/opsec-0.1.0-SNAPSHOT.jar validar apps-practica/clientes-vulnerable/ejemplos/f6/proyecto.yaml
```

Para validar todos en Git Bash, Linux o macOS (se detiene si alguno falla):

```bash
for fallo in 1 2 3 4 5 6 7 8; do
  java -jar opsec-cli/target/opsec-0.1.0-SNAPSHOT.jar validar "apps-practica/clientes-vulnerable/ejemplos/f${fallo}/proyecto.yaml" || break
done
```

En PowerShell:

```powershell
foreach ($fallo in 1..8) {
    java -jar opsec-cli/target/opsec-0.1.0-SNAPSHOT.jar validar "apps-practica/clientes-vulnerable/ejemplos/f$fallo/proyecto.yaml"
    if ($LASTEXITCODE -ne 0) { throw "Falló la validación de F$fallo" }
}
```

Se esperan ocho proyectos y ocho suites válidos (nueve casos: F5 contiene C01 y
C02 para comparar los mensajes). El resultado válido comprueba carga y formato;
no demuestra la ejecución del flujo ni la detección del fallo.

### Dependencias de ejecución para P2 y el líder

- **F1:** `CREDENCIALES-DEBILES` es el oráculo dinámico previsto para la pareja
  `soporte` / `soporte123`; su CWE es 521. No demuestra por sí mismo que la
  contraseña esté embebida en el código (CWE-798); eso corresponde al análisis
  estático descrito en F1. El oráculo dinámico todavía falta.
- **F2 y F3:** faltan `SIN-SESION`, los ganchos JDBC y la confirmación del borrado.
  Además, `Uid` en EliminarPersona no tiene etiqueta asociada ni nombre: el agente
  actual no resuelve ese campo con el localizador de texto de `llenar`. Las suites
  documentan el flujo esperado y validan, pero necesitan la mejora del agente para
  completarlo. No se altera ese componente en esta issue.
- **F4:** la suite contiene una entrada SQL concreta con el marcador literal
  `OPSEC-F4`. No es un canario generado por el motor. Faltan el rastreo/generación
  de canarios, los ganchos JDBC y `SQLI-CANARIO` para confirmar el hallazgo.
- **F5:** C01 usa un usuario inexistente y C02 una contraseña incorrecta del Admin.
  Falta `ENUMERACION-USUARIOS`, que debe comparar los diálogos a nivel de suite.
- **F7:** falta `TRAZA-EN-DIALOGO`; el proyecto conserva F4 activo intencionalmente.
- **F6 y F8:** usan los dos oráculos disponibles. Sus suites no requieren login
  automático por `rol:` ni expansión de ataques; se usan pasos explícitos y modo
  `SEMANTICO`, el modo disponible actualmente.

Los oráculos se solicitan explícitamente en cada caso. El motor actual señala
los no registrados como `NO_CONCLUYENTE`; no debe interpretarse ese resultado
como ausencia del fallo. Esta entrega valida ejemplos; la integración completa
del banco y las versiones corregidas pertenece a otras issues.

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
4. Una comilla suelta (`'`) en el usuario rompe la sentencia. Con F7 activo aparece la pila; con F7 corregido aparece "No se pudo consultar la base de datos."

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

### F7 – Pila de llamadas en diálogo (CWE-209)

**Dónde:** manejo de `SQLException` en `gui/InicioSesion.java`; el mensaje se construye
en `servicio/DiagnosticoServicio.java`.

**Cómo reproducirlo a mano:**
1. Pulsar **Avanzar** y **Nube**.
2. Escribir una comilla (`'`) en **Usuario** y una contraseña no vacía.
3. Pulsar **Iniciar Sesion**. El diálogo muestra la clase de excepción, la consulta
   SQL y la pila de llamadas. F4 debe estar activo para provocar el error SQL.

**Corregido:** `-Dpractica.corregir=F7` conserva el error de consulta y muestra solo
"No se pudo consultar la base de datos.". Para comparar solo este fallo, ejecutar
la vulnerable con `-Dpractica.corregir=F1,F2,F3,F5,F6,F8` y la corregida con
`-Dpractica.corregir=F1,F2,F3,F5,F6,F7,F8`. Ambas mantienen F4 activo para que la
misma entrada produzca el mismo error. Corregir F4 impediría activar este escenario.

**Prueba:** `DiagnosticoServicioTest.f7ExponeLaPilaYLaCorreccionOcultaLosDetalles`
comprueba que una misma excepción expone información con F7 activo y solo un
mensaje genérico con F7 corregido.

**Detección esperada:** `TRAZA-EN-DIALOGO` sobre el evento `DIALOGO`. El agente
observa diálogos, pero este oráculo todavía no está implementado. No se declara
verificada su detección de punta a punta.

### F8 – Excepción no capturada en una acción (CWE-248)

**Dónde:** botón **Probar excepción** (`botonProbarExcepcion`) del login y
`servicio/DiagnosticoServicio.probarExcepcion`.

**Cómo reproducirlo a mano:** pulsar **Avanzar**, **Nube** y **Probar excepción**.
El listener deja escapar una `IllegalStateException` identificada con F8; al
ejecutar manualmente se observa en la salida de error del EDT.

**Corregido:** `-Dpractica.corregir=F8` permite que la misma acción termine normalmente.
El botón permanece disponible en ambas variantes. Para aislarlo, usar
`-Dpractica.corregir=F1,F2,F3,F4,F5,F6,F7` y comparar con
`-Dpractica.corregir=todos`.

**Pruebas:** `DiagnosticoServicioTest` comprueba ambas variantes y la independencia
de F7 y F8. `VentanasTest.elBotonDeDiagnosticoDejaEscaparF8SoloCuandoEstaActivo`
ejercita el botón real en el EDT, sin mostrar ventanas, si hay pantalla disponible.

**Detección esperada:** `EXCEPCION-NO-CAPTURADA`, sobre `EXCEPCION` con
`capturada=false`. El agente semántico observa la excepción durante la acción.
CWE-248 describe el fallo sembrado; el oráculo implícito conserva su CWE nulo
en el catálogo del motor, que no se modifica.

## Comportamientos que no son fallos sembrados

Para que cada oráculo se active solo con su fallo, la app evita a propósito otros problemas conocidos:

- La contraseña se escribe en un `JPasswordField` (no activa `CONTRASENA-EN-CAMPO-VISIBLE`).
- Los errores de base de datos muestran un mensaje sin pila de llamadas cuando F7 está corregido.
- Las contraseñas de los usuarios semilla no son triviales (no activan `CREDENCIALES-DEBILES`, salvo la cuenta de F1).
- La base es H2 en memoria, sin conexión de red (no activa `JDBC-SIN-CIFRADO`).

Hay debilidades que la app no corrige porque quedan fuera de esta versión: no bloquea tras varios intentos fallidos (`SIN-BLOQUEO`, CWE-307) y guarda las contraseñas en texto plano.
