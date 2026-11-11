# clientes-vulnerable

Aplicación Swing de práctica con fallos sembrados, que sirve de objetivo para evaluar el motor y para docencia. Imita un proyecto típico de estudiantes (portada, selección de modo, inicio de sesión, menús por rol y gestión de personas) con una base H2 embebida en memoria. Su artifactId es `opsec-clientes-vulnerable`.

- **Dueño:** P3 (laboratorio).
- **Paquete raíz:** `io.github.physanto.opsecengine.practica`
- **Versión de Java:** compila con `--release 11`, porque H2 2.x se publica para Java 11. Se ejecuta con Java 11 o superior.

Los fallos sembrados, cómo reproducirlos y qué oráculo debe detectarlos están en [FALLOS.md](FALLOS.md).

El nivel 1 incluye F6 (contraseña en consola), F7 (traza en diálogo) y F8 (excepción sin capturar).
La selección, los interruptores y las pruebas están descritos en [FALLOS.md](FALLOS.md).
El oráculo de F7 está pendiente en el motor.

Los niveles 2 y 3 añaden los formularios Registrar Persona (F9) y Modificar Persona
(F10). Sus correcciones y pruebas están descritas en [FALLOS.md](FALLOS.md).

Para ejecutar la demo del motor sobre esta app sin pantalla del anfitrión, consulte
el [laboratorio Docker con JDK 21 y Xvfb](../laboratorio/README.md).

La tabla nivel → fallo → CWE → oráculo → suite y los comandos de validación están
en [FALLOS.md](FALLOS.md#correspondencia-de-fallos-y-suites-issue-62).
Cada carpeta de [ejemplos/](ejemplos/) contiene un proyecto y una suite por fallo.

## Ventanas

| Ventana | Clase | Componentes |
|---|---|---|
| Portada | `gui.Portada` | botón **Avanzar** |
| Componentes de práctica (#68) | `gui.ComponentesMuestra` | tabla, lista, pestañas y dos diálogos modales anidados; se abre con **Componentes** en la portada |
| Escoger modo | `gui.EscogerModo` | botones **Nube** (`botonNube`) y **Local** |
| Iniciar sesión | `gui.InicioSesion` | campos **Usuario** (`campoUsuario`, con etiqueta) y **Contraseña** (solo etiqueta), botones **Iniciar Sesion** (`botonIniciarSesion`), **Probar excepción** (`botonProbarExcepcion`) y **Volver** |
| Menú cliente | `gui.MenuCliente` | botones **Buscar Persona**, **Modificar Persona** (`botonModificarPersona`) y **Cerrar Sesion** |
| Menú administrador | `gui.MenuAdministrador` | botones **Buscar Persona**, **Registrar Persona** (`botonRegistrarPersona`), **Modificar Persona** (`botonModificarPersona`), **Eliminar Persona** (`botonEliminarPersona`) y **Cerrar Sesion** |
| Registrar persona | `gui.RegistrarPersona` | Uid, nombre, documento, correo; **Guardar** (`botonGuardarRegistrarPersona`) |
| Modificar persona | `gui.ModificarPersona` | Uid, nombre, documento, correo; **Guardar** (`botonGuardarModificarPersona`) |
| Buscar persona | `gui.BuscarPersona` | campo **Uid** (`campoUid`, con etiqueta), botón **Buscar**, área de resultado sin nombre |
| Eliminar persona | `gui.EliminarPersona` | campo sin nombre ni etiqueta asociada junto al texto "Uid", botón **Eliminar** (`botonEliminar`), área `areaResultado` |

Entre paréntesis va el nombre de `setName`. La mezcla de componentes con nombre, con etiqueta asociada (`setLabelFor`) y sin ninguno de los dos es intencional: ejercita las distintas estrategias de localización del motor.

Usuarios semilla: `Admin` / `Admin#2026` (administrador) y `Cliente` / `Cliente#2026` (cliente).

## Construir

Desde la raíz del repositorio:

```sh
./mvnw -B -pl apps-practica/clientes-vulnerable -am package
```

En Windows:

```bat
mvnw.cmd -B -pl apps-practica/clientes-vulnerable -am package
```

El resultado es `apps-practica/clientes-vulnerable/target/clientes-vulnerable.jar`: un jar ejecutable que incluye la base H2 y cuya clase principal es la portada.

## Ejecutar

Los comandos se escriben desde la raíz del repositorio.

**Linux y macOS:**

```sh
# Versión vulnerable (todos los fallos activos)
java -jar apps-practica/clientes-vulnerable/target/clientes-vulnerable.jar

# Corrige solo F1 y F4
java -Dpractica.corregir=F1,F4 -jar apps-practica/clientes-vulnerable/target/clientes-vulnerable.jar

# Versión corregida
java -Dpractica.corregir=todos -jar apps-practica/clientes-vulnerable/target/clientes-vulnerable.jar

# Abrir directamente la ventana de eliminación (fallo F3)
java -cp apps-practica/clientes-vulnerable/target/clientes-vulnerable.jar io.github.physanto.opsecengine.practica.gui.EliminarPersona
```

**Windows (símbolo del sistema, `cmd`):**

```bat
java -jar apps-practica\clientes-vulnerable\target\clientes-vulnerable.jar
java -Dpractica.corregir=F1,F4 -jar apps-practica\clientes-vulnerable\target\clientes-vulnerable.jar
java -Dpractica.corregir=todos -jar apps-practica\clientes-vulnerable\target\clientes-vulnerable.jar
java -cp apps-practica\clientes-vulnerable\target\clientes-vulnerable.jar io.github.physanto.opsecengine.practica.gui.EliminarPersona
```

**Windows (PowerShell):** igual que en `cmd`, pero con la opción `-D` entre comillas, porque PowerShell puede partir el argumento en el punto o en la coma:

```powershell
java "-Dpractica.corregir=F1,F4" -jar apps-practica\clientes-vulnerable\target\clientes-vulnerable.jar
```

Al arrancar, la app escribe en la consola qué fallos quedaron activos. Códigos de salida: 1 si no se pudo crear la base y 2 si `practica.corregir` contiene un fallo desconocido.

## Dependencias permitidas

Solo H2. La app no depende de ningún módulo del motor: el motor la prueba desde fuera.

## Pruebas

```sh
./mvnw -B -pl apps-practica/clientes-vulnerable -am verify
```

En Windows:

```bat
mvnw.cmd -B -pl apps-practica/clientes-vulnerable -am verify
```

La lógica (repositorios, inicio de sesión, control de acceso e interruptores) se prueba sin interfaz. Las pruebas de ventanas se omiten automáticamente cuando el entorno no tiene pantalla (`GraphicsEnvironment.isHeadless()`); construyen las ventanas sin mostrarlas.

## Componentes para grabadora e inspector (#68)

Desde la portada, **Componentes** abre una pantalla con `tablaPersonas` (tres filas
de muestra, sin edición), `listaCategorias` y `pestanasPractica` (Tabla/Lista).
La selección cambia `resultadoComponentes`. **Abrir detalle** abre `dialogoDetalle`;
desde él, **Abrir confirmación** abre `dialogoConfirmacion`, cuyo propietario es
el primer diálogo. Cerrar la ventana libera también sus diálogos. Estos controles
no modifican los datos de personas.

Flujo manual para grabar: seleccionar una fila, pasar a Lista, elegir Soporte,
abrir Detalle y Confirmación, aceptar y cerrar Detalle. Los componentes tienen
nombres estables y nombres accesibles o etiquetas asociadas.
La suite de `ejemplos/componentes/suites/componentes.yaml` es una **referencia
manual**, con los flujos de tabla, lista y diálogos separados. Validar con:

```sh
java -jar opsec-cli/target/opsec-0.1.0-SNAPSHOT.jar validar \
  apps-practica/clientes-vulnerable/ejemplos/componentes/proyecto.yaml
```

**Aceptación pendiente:** la grabadora y el inspector aún no están implementados
en esta rama (la CLI ofrece `version`, `validar` y `ejecutar`). Las pruebas de Swing
comprueban selecciones, pestañas, propietarios y liberación de diálogos; la
validación de la referencia manual no demuestra que la grabadora produzca una
suite. Cuando se integre, habrá que grabar el flujo anterior, validar su YAML con
`opsec validar` y reproducirlo, sin reemplazar esa evidencia por esta referencia.
