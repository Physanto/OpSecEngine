<p align="center">
  <img src="docs/imagenes/logo.png" alt="OpSecEngine: Opcode Security Engine" width="100%">
</p>

# OpSecEngine

[![CI](https://github.com/Physanto/OpSecEngine/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Physanto/OpSecEngine/actions/workflows/ci.yml)

OpSecEngine (Opcode Security Engine): motor libre de pruebas GUI y de seguridad para aplicaciones Java Swing.

## Descripción

OpSecEngine prueba aplicaciones de escritorio Java Swing **sin modificarlas**. Lanza la aplicación en su propia JVM con un agente (`-javaagent`) que ejecuta acciones sobre la interfaz (pulsar, llenar, seleccionar, esperar), observa ventanas, diálogos, excepciones y consola, y reporta todo al motor por un socket local. Con esa traza, los **oráculos** deciden si cada caso cumple o no, y los **reporteros** generan informes en HTML, JSON, JUnit XML y SARIF.

Ideas principales:

- **Cualquier app, cualquier funcionalidad.** El motor no conoce ventanas ni botones de una app concreta: lo específico vive en `proyecto.yaml` y en las suites YAML. Los componentes se encuentran con localizadores genéricos (etiqueta, texto, nombre accesible, tipo).
- **Dos procesos.** Si la app se cae o llama a `System.exit`, el motor y el informe sobreviven.
- **Pruebas funcionales y de seguridad.** Operaciones sin sesión, autorización por rol, inyección SQL con canarios, mensajes que revelan información, entre otros, con su CWE asociado.
- **Extensible.** Oráculos, generadores de casos, reporteros y proveedores de datos se agregan con `ServiceLoader`, sin tocar el núcleo.

## Estado del proyecto

En construcción: base del proyecto terminada (versión `0.0.6`) y desarrollo de la v0.1 en curso. Ya se puede ver una primera demo de punta a punta: [`demo/`](demo/README.md) detecta una contraseña escrita en la consola (CWE-532) en la app de práctica.

Ya está disponible:

- Contratos versión 1.1: modelos del dominio, interfaces de extensión, protocolo motor-agente, esquemas JSON de los YAML y catálogos (eventos, oráculos, códigos de resultado, CWE).
- Dobles de prueba (`AgenteFalso`, `MotorFalso`) y trazas, mapas y resultados de ejemplo.
- Núcleo mínimo: carga y validación de proyectos y suites, sesiones, ejecución, evaluación e informes.
- Agente mínimo: pulsa, llena y espera en modo semántico, y observa consola, ventanas, diálogos y excepciones.
- Oráculos `EXCEPCION-NO-CAPTURADA` y `CONTRASENA-EN-CONSOLA` (CWE-532), un archivo de payloads y el reportero JSON.
- CLI con `version`, `validar` y `ejecutar`, que trae dentro el agente, los oráculos y los reporteros.
- App de práctica `clientes-vulnerable` con fallos sembrados.
- Verificaciones automáticas de formato, estilo, Javadoc, dependencias entre módulos y contratos, e integración continua en Windows, macOS y Linux.

Hoja de ruta:

| Versión | Contenido |
|---|---|
| v0.1 | Agente (driver, observadores, capturas), sesión y ejecución, oráculos implícitos, informes HTML y JSON, CLI |
| v0.2 | Exploración y mapa de la app, generación de casos, biblioteca de ataques |
| v0.3 | Ganchos JDBC y Firestore, oráculos de seguridad, manifiesto, SARIF |
| v0.4 | Aplicación de escritorio (asistente, grabadora, inspector) |
| v0.5 | Rendimiento, robustez y accesibilidad |
| v0.6 | Extensión de JUnit 5, servidor MCP, proveedor de IA opcional y cobertura |

## Requisitos

Solo se necesita un **JDK 21**, la versión con la que trabaja el equipo y prueba la CI. Las aplicaciones que se prueban pueden ser de Java 8 en adelante: corren en su propia JVM y el agente se compila para Java 8. No hace falta instalar Maven: el proyecto incluye el Maven Wrapper (`mvnw` y `mvnw.cmd`), que descarga la versión correcta la primera vez.

## Compilar y probar

**Linux y macOS**

```sh
./mvnw -B verify
```

En Linux sin pantalla, las pruebas con Swing se omiten automáticamente. Para ejecutarlas con una pantalla virtual:

```sh
xvfb-run --auto-servernum ./mvnw -B verify
```

Con pantalla real (en cualquier sistema), las pruebas con Swing abren ventanas durante unos segundos y la app de práctica puede tomar el foco. Mientras corre `verify`, no escribas sobre esas ventanas: una tecla puede cambiar un campo o cerrar un diálogo y hacer fallar una prueba. En Linux, `xvfb-run` evita que aparezcan.

**Windows** (CMD o PowerShell)

```bat
mvnw.cmd -B verify
```

En Git Bash también funciona `./mvnw -B verify`.

`verify` compila, comprueba el formato (Spotless con google-java-format, estilo AOSP), el estilo y el Javadoc (Checkstyle, con `build-tools/checkstyle.xml`), las versiones de Java y Maven y la convergencia de dependencias (Maven Enforcer), ejecuta las pruebas unitarias y de integración, y verifica las reglas de arquitectura y los contratos (`opsec-verificacion`).

Otros comandos útiles:

```sh
./mvnw spotless:apply                       # aplica el formato automático
./mvnw -B -DskipTests package javadoc:aggregate   # documentación en target/reports/apidocs/
./mvnw -B verify -pl opsec-oraculos -am     # un solo módulo y sus dependencias
```

En Windows, reemplaza `./mvnw` por `mvnw.cmd`.

### Línea de comandos

Después de `verify`, la CLI `opsec` queda empaquetada con todas sus dependencias:

```sh
java -jar opsec-cli/target/opsec-<versión>.jar version
java -jar opsec-cli/target/opsec-<versión>.jar validar contratos/ejemplos/proyecto.yaml
java -jar opsec-cli/target/opsec-<versión>.jar ejecutar demo/proyecto.yaml
```

Ver [`demo/README.md`](demo/README.md) para la demo completa.

## Módulos

| Módulo | Responsabilidad | Java | Rol dueño |
|---|---|---|---|
| `opsec-dominio` | Modelos inmutables e interfaces de extensión (`Oraculo`, `GeneradorCasos`, `Reportero`, `ProveedorDatos`, `ProveedorIA`). Sin dependencias. | 21 | Líder |
| `opsec-protocolo` | Mensajes motor-agente (JSON por líneas sobre socket local) y su serialización. | 8 | Líder |
| `opsec-agente` | Agente que corre dentro de la app probada: driver Swing, observadores, ganchos y capturas. Dependencias reubicadas. | 8 | P1 (el paquete `api`, el líder) |
| `opsec-nucleo` | Servicios de aplicación: proyectos, sesiones, ejecución, evaluación, informes; cliente del agente y lanzador de procesos. | 21 | Líder |
| `opsec-oraculos` | Oráculos incluidos, descubiertos con `ServiceLoader`. | 21 | P2 |
| `opsec-ataques` | Biblioteca de payloads en YAML y su cargador. | 21 | P2 |
| `opsec-informes` | Reporteros: HTML, JSON, JUnit XML y SARIF. | 21 | P3 (salidas) |
| `opsec-cli` | Línea de comandos `opsec`. | 21 | P3 (salidas) |
| `opsec-soporte-pruebas` | Dobles de prueba compartidos y datos de ejemplo. Solo se usa en pruebas. | 21 | Líder |
| `opsec-verificacion` | Pruebas de arquitectura (ArchUnit) y de contratos. Sin código principal. | 21 | Líder |
| `apps-practica/clientes-vulnerable` | App Swing con fallos sembrados, para evaluar el motor y para docencia. | 11 | P3 (laboratorio) |

Reglas de dependencia: `opsec-dominio` no depende de nada; `opsec-agente` solo depende de `opsec-protocolo`; oráculos, ataques e informes dependen solo del dominio; las presentaciones (como la CLI) solo hablan con el núcleo, y ningún módulo depende de una presentación. Las reglas se comprueban en cada build.

Cada módulo tiene su propio `README.md` con más detalle.

## Estructura de carpetas

```text
OpSecEngine/
├── pom.xml                       pom padre: módulos y versiones fijas
├── mvnw, mvnw.cmd, .mvn/         Maven Wrapper
├── build-tools/                  configuración de calidad (Checkstyle)
├── docs/imagenes/                imágenes del README
├── contratos/
│   ├── esquemas/                 JSON Schema de proyecto, suite, payload, regla de oráculo y traza
│   ├── catalogos/                eventos, oráculos, códigos de resultado y CWE
│   ├── ejemplos/                 proyecto, suites, ataques, oráculos y resultados de ejemplo
│   └── protocolo/ejemplos/       un mensaje JSON de ejemplo por tipo
├── opsec-dominio/
├── opsec-protocolo/
├── opsec-agente/
├── opsec-nucleo/
├── opsec-oraculos/
├── opsec-ataques/
├── opsec-informes/
├── opsec-cli/
├── opsec-soporte-pruebas/
├── opsec-verificacion/
├── apps-practica/
│   └── clientes-vulnerable/
└── .github/                      CI, CODEOWNERS y plantillas de issues y PR
```

## Contribuir

Lee la [guía de contribución](CONTRIBUTING.md): ramas por módulo, formato de commits, integración hacia `main`, archivos protegidos, proceso de cambio de contrato, Definición de Terminado y convenciones de código.

## Licencia

Distribuido bajo la [Licencia Apache 2.0](LICENSE). Copyright 2026 Contribuyentes de OpSecEngine.
