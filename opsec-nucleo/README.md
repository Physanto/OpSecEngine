# opsec-nucleo

Servicios de aplicación que orquestan los casos de uso (proyecto, sesión, exploración, generación, ejecución, evaluación e informes) e infraestructura propia del motor: YAML/JSON, lanzador de procesos y cliente del agente. Es la única puerta de las presentaciones al motor.

- **Dueño:** líder (L).
- **Paquete raíz:** `io.github.physanto.opsecengine.nucleo`

## Contenido

| Paquete | Clases principales |
|---|---|
| `nucleo` | `VersionMotor`: versión del motor, de Java y del sistema. |
| `nucleo.extensiones` | `RegistroExtensiones`: descubre oráculos, generadores, reporteros y proveedores de datos con `ServiceLoader` y rechaza claves repetidas. |
| `nucleo.proyecto` | `ProyectoService`: carga `proyecto.yaml` y `suites/*.yaml`, los valida contra los esquemas de `contratos/esquemas/` (copiados como recursos al construir) y los traduce al dominio. Los errores llegan en `ExcepcionConfiguracion`, con la ruta del campo. |
| `nucleo.sesion` | `TraductorProtocolo` (dominio ↔ DTO), `ClienteAgente` (lado del motor del protocolo), `LanzadorProceso` (lanza la app con `-javaagent`), `SesionService` (abre y cierra sesiones). |
| `nucleo.ejecucion` | `EjecucionService`: ejecuta una suite, una sesión por caso, y arma las trazas. |
| `nucleo.evaluacion` | `EvaluacionService`: aplica los oráculos y calcula los veredictos. |
| `nucleo.informe` | `InformeService`: genera los informes con los reporteros registrados. |

El Javadoc de `EjecucionService` lista lo que falta y la versión en que llega.

## Ejemplo de referencia

`EjecucionServiceTest` prueba la ejecución contra `AgenteFalso` de `opsec-soporte-pruebas`, sin lanzar ninguna aplicación: el arranque de la sesión es una lambda que conecta un agente falso al puerto del motor. `EjecucionServiceIT` hace lo mismo con el agente eco real y `AplicacionDePrueba` en otra JVM.

## Dependencias permitidas

- `opsec-dominio`.
- `opsec-protocolo`.
- Jackson (`jackson-databind` y `jackson-dataformat-yaml`).
- `json-schema-validator` (networknt).
- Solo para pruebas: `opsec-soporte-pruebas` y el jar del agente (`opsec-agente`, clasificador `agente`), que no entra en el classpath de las pruebas: las de integración reciben su ruta en la propiedad `opsec.agente.jar`.

Oráculos, ataques e informes se descubren con `ServiceLoader`, sin depender de ellos en compilación.

## Pruebas

Desde la raíz del repositorio:

```sh
./mvnw -B -pl opsec-nucleo -am verify
```

En Windows:

```bat
mvnw.cmd -B -pl opsec-nucleo -am verify
```
