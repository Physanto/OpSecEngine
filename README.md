# OpSecEngine

[![CI](https://github.com/Physanto/OpSecEngine/actions/workflows/ci.yml/badge.svg?branch=develop)](https://github.com/Physanto/OpSecEngine/actions/workflows/ci.yml)

OpSecEngine (Opcode Security Engine): motor libre de pruebas GUI y de seguridad para aplicaciones Java Swing.

## Descripción

OpSecEngine prueba aplicaciones de escritorio Java Swing **sin modificarlas**. Lanza la aplicación en su propia JVM con un agente (`-javaagent`) que ejecuta acciones sobre la interfaz (pulsar, llenar, seleccionar, esperar), observa ventanas, diálogos, excepciones y consola, y reporta todo al motor por un socket local. Con esa traza, los **oráculos** deciden si cada caso cumple o no, y los **reporteros** generan informes en HTML, JSON, JUnit XML y SARIF.

Ideas principales:

- **Cualquier app, cualquier funcionalidad.** El motor no conoce ventanas ni botones de una app concreta: lo específico vive en `proyecto.yaml` y en las suites YAML. Los componentes se encuentran con localizadores genéricos (etiqueta, texto, nombre accesible, tipo).
- **Dos procesos.** Si la app se cae o llama a `System.exit`, el motor y el informe sobreviven.
- **Pruebas funcionales y de seguridad.** Operaciones sin sesión, autorización por rol, inyección SQL con canarios, mensajes que revelan información, entre otros, con su CWE asociado.
- **Extensible.** Oráculos, generadores de casos, reporteros y proveedores de datos se agregan con `ServiceLoader`, sin tocar el núcleo.

## Estado del proyecto

En construcción: **Fase 0 (base del proyecto)**, versión `0.0.1-SNAPSHOT`. Todavía no hay versiones publicadas.

Ya está disponible:

- Contratos versión 1: modelos del dominio, interfaces de extensión, protocolo motor-agente, esquemas JSON de los YAML y catálogos (eventos, oráculos, códigos de resultado, CWE).
- Dobles de prueba (`AgenteFalso`, `MotorFalso`) y trazas, mapas y resultados de ejemplo.
- Núcleo mínimo: carga y validación de proyectos y suites, sesiones, ejecución, evaluación e informes.
- Un ejemplo de referencia por módulo: un oráculo, un archivo de payloads, el reportero JSON y la CLI con `version` y `validar`.
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

Solo se necesita un **JDK 17 o superior** (probado con 17 y 21). No hace falta instalar Maven: el proyecto incluye el Maven Wrapper (`mvnw` y `mvnw.cmd`), que descarga la versión correcta la primera vez.

## Compilar y probar

**Linux y macOS**

```sh
./mvnw -B verify
```

En Linux sin pantalla, las pruebas con Swing se omiten automáticamente. Para ejecutarlas con una pantalla virtual:

```sh
xvfb-run --auto-servernum ./mvnw -B verify
```

**Windows** (CMD o PowerShell)

```bat
mvnw.cmd -B verify
```

En Git Bash también funciona `./mvnw -B verify`.

`verify` compila, comprueba el formato (Spotless con google-java-format, estilo AOSP), el estilo y el Javadoc (Checkstyle, con `build-tools/checkstyle.xml`), las versiones de Java y Maven y la convergencia de dependencias (Maven Enforcer), ejecuta las pruebas unitarias y de integración, y verifica las reglas de arquitectura y los contratos (`opsec-verificacion`).

Otros comandos útiles:

```sh
./mvnw spotless:apply                       # aplica el formato automático
./mvnw -B -DskipTests javadoc:aggregate     # documentación en target/reports/apidocs/
./mvnw -B verify -pl opsec-oraculos -am     # un solo módulo y sus dependencias
```

En Windows, reemplaza `./mvnw` por `mvnw.cmd`.

### Línea de comandos

Después de `verify`, la CLI `opsec` queda empaquetada con todas sus dependencias:

```sh
java -jar opsec-cli/target/opsec-0.0.1-SNAPSHOT.jar version
java -jar opsec-cli/target/opsec-0.0.1-SNAPSHOT.jar validar contratos/ejemplos/proyecto.yaml
```

## Módulos

| Módulo | Responsabilidad | Java | Rol dueño |
|---|---|---|---|
| `opsec-dominio` | Modelos inmutables e interfaces de extensión (`Oraculo`, `GeneradorCasos`, `Reportero`, `ProveedorDatos`, `ProveedorIA`). Sin dependencias. | 17 | Líder |
| `opsec-protocolo` | Mensajes motor-agente (JSON por líneas sobre socket local) y su serialización. | 8 | Líder |
| `opsec-agente` | Agente que corre dentro de la app probada: driver Swing, observadores, ganchos y capturas. Dependencias reubicadas. | 8 | P1 (el paquete `api`, el líder) |
| `opsec-nucleo` | Servicios de aplicación: proyectos, sesiones, ejecución, evaluación, informes; cliente del agente y lanzador de procesos. | 17 | Líder |
| `opsec-oraculos` | Oráculos incluidos, descubiertos con `ServiceLoader`. | 17 | P2 |
| `opsec-ataques` | Biblioteca de payloads en YAML y su cargador. | 17 | P2 |
| `opsec-informes` | Reporteros: HTML, JSON, JUnit XML y SARIF. | 17 | P3 |
| `opsec-cli` | Línea de comandos `opsec`. | 17 | P3 |
| `opsec-soporte-pruebas` | Dobles de prueba compartidos y datos de ejemplo. Solo se usa en pruebas. | 17 | Líder |
| `opsec-verificacion` | Pruebas de arquitectura (ArchUnit) y de contratos. Sin código principal. | 17 | Líder |
| `apps-practica/clientes-vulnerable` | App Swing con fallos sembrados, para evaluar el motor y para docencia. | 11 | P3 |

Reglas de dependencia: `opsec-dominio` no depende de nada; `opsec-agente` solo depende de `opsec-protocolo`; oráculos, ataques e informes dependen solo del dominio; las presentaciones (como la CLI) solo hablan con el núcleo, y ningún módulo depende de una presentación. Las reglas se comprueban en cada build.

Cada módulo tiene su propio `README.md` con más detalle.

## Estructura de carpetas

```text
OpSecEngine/
├── pom.xml                       pom padre: módulos y versiones fijas
├── mvnw, mvnw.cmd, .mvn/         Maven Wrapper
├── build-tools/                  configuración de calidad (Checkstyle)
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

Lee la [guía de contribución](CONTRIBUTING.md): flujo de ramas (git flow), formato de commits, pull requests hacia `develop`, archivos protegidos, proceso de cambio de contrato, Definición de Terminado y convenciones de código.

## Licencia

Distribuido bajo la [Licencia Apache 2.0](LICENSE). Copyright 2026 Contribuyentes de OpSecEngine.
