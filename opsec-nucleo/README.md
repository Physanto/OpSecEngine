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
| `nucleo.ejecucion` | `EjecucionService`: ejecuta una suite, una sesión por caso, y arma las trazas. Si el caso tiene rol, antes de sus pasos ejecuta los pasos de `login` del proyecto con `{usuario}` y `{contrasena}` reemplazados; esos eventos llevan el paso 0 y la contraseña se registra como `****`. Si el login termina bien, registra `SESION` (`activa=true`) y desde ahí los eventos llevan el rol y `sesionActiva=true`; si falla, el caso queda `NO_CONCLUYENTE` sin ejecutar sus pasos. Los pasos `CAPTURAR` piden la imagen al agente, la guardan en `capturas/<caso>-<paso>.png` dentro de la carpeta de resultados del `LanzadorProceso` y registran el evento `CAPTURA`, que el informe HTML incrusta. |
| `nucleo.evaluacion` | `EvaluacionService`: aplica los oráculos y calcula los veredictos. |
| `nucleo.informe` | `InformeService`: genera los informes con los reporteros registrados. |

El Javadoc de `EjecucionService` lista lo que falta y la versión en que llega.

## Ejemplo de referencia

`EjecucionServiceTest` prueba la ejecución contra `AgenteFalso` de `opsec-soporte-pruebas`, sin lanzar ninguna aplicación: el arranque de la sesión es una lambda que conecta un agente falso al puerto del motor. `EjecucionServiceIT` hace lo mismo con el agente real y `AplicacionDePrueba` en otra JVM.

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

## Progreso de la ejecución

La presentación puede suscribirse sin conocer la implementación del servicio:

```java
ObservadorProgreso observador = progreso -> mostrarAvance(progreso);
EjecucionService ejecucion =
        new EjecucionService(proyecto, sesiones, evaluacion, observador);
ResultadoSuite resultado = ejecucion.ejecutar(suite);
```

`ObservadorProgreso` y `ProgresoEjecucion` están en `nucleo.ejecucion`.
El constructor de tres argumentos sigue disponible y usa `ObservadorProgreso.VACIO`.
Se notifica el inicio y fin de suite, caso y paso, con nombre de suite, total de casos,
identificador del caso, orden y total de pasos y, al terminar un paso, su código de resultado.
Los pasos de login llevan `login=true` y el orden de su plantilla; los valores y las
credenciales no se incluyen. Los totales son los previstos: tras un paso fallido no
se notifican los pasos que no llegaron a ejecutarse. Un caso no ejecutable tiene
inicio y fin, sin pasos; una suite vacía tiene inicio y fin de suite.

Las llamadas son síncronas y siguen el orden de ejecución. El observador debe regresar
pronto; una interfaz gráfica debe trasladar sus cambios al hilo de presentación.
Si el observador lanza una excepción de ejecución, se registra y la suite continúa.
El fin de caso se notifica después de evaluarlo y el de suite cuando su resultado está
listo; una excepción que interrumpa la ejecución no garantiza una notificación de fin.

`EjecucionServiceTest` comprueba las notificaciones con `AgenteFalso`, su orden,
los fallos de pasos y del observador, el login sin credenciales en los avisos,
los casos no ejecutables y las suites vacías.
