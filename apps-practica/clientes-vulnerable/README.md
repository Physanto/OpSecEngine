# clientes-vulnerable

Aplicación Swing de práctica con fallos sembrados, que sirve de objetivo para evaluar el motor y para docencia. Imita un proyecto típico de estudiantes (portada, selección de modo, inicio de sesión, menús por rol y gestión de personas) con una base H2 embebida en memoria. Su artifactId es `opsec-clientes-vulnerable`.

- **Dueño:** P3.
- **Paquete raíz:** `io.github.physanto.opsecengine.practica`
- **Versión de Java:** compila con `--release 11`, porque H2 2.x se publica para Java 11. Se ejecuta con Java 11 o superior.

Los fallos sembrados, cómo reproducirlos y qué oráculo debe detectarlos están en [FALLOS.md](FALLOS.md).

## Ventanas

| Ventana | Clase | Componentes |
|---|---|---|
| Portada | `gui.Portada` | botón **Avanzar** |
| Escoger modo | `gui.EscogerModo` | botones **Nube** (`botonNube`) y **Local** |
| Iniciar sesión | `gui.InicioSesion` | campos **Usuario** (`campoUsuario`, con etiqueta) y **Contraseña** (solo etiqueta), botones **Iniciar Sesion** (`botonIniciarSesion`) y **Volver** |
| Menú cliente | `gui.MenuCliente` | botones **Buscar Persona** y **Cerrar Sesion** |
| Menú administrador | `gui.MenuAdministrador` | botones **Buscar Persona**, **Eliminar Persona** (`botonEliminarPersona`) y **Cerrar Sesion** |
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
