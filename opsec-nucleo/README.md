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
| `nucleo.sesion` | `InicioSesion` (login de un rol con los pasos del proyecto), `TraductorProtocolo` (dominio ↔ DTO), `ClienteAgente` (lado del motor del protocolo), `LanzadorProceso` (lanza la app con `-javaagent`), `SesionService` (abre y cierra sesiones). |
| `nucleo.ejecucion` | `EjecucionService`: ejecuta una suite, una sesión por caso, y arma las trazas. Si el caso tiene rol, antes de sus pasos ejecuta los pasos de `login` del proyecto con `{usuario}` y `{contrasena}` reemplazados; esos eventos llevan el paso 0 y la contraseña se registra como `****`. Si el login termina bien, registra `SESION` (`activa=true`) y desde ahí los eventos llevan el rol y `sesionActiva=true`; si falla, el caso queda `NO_CONCLUYENTE` sin ejecutar sus pasos. Al abrir cada sesión envía `CONFIG_GANCHOS` al agente (`ClienteAgente.configurarGanchos`, #15) con las operaciones sensibles del proyecto y el canario del caso; si el agente no lo confirma, el caso queda `NO_CONCLUYENTE`. Un caso que escribe `{canario}` en un `LLENAR` sin declarar canario recibe uno determinista (`OPSEC` y 12 dígitos hexadecimales del SHA-256 de semilla y id del caso), que queda en `Caso.canario()` para que `SQLI-CANARIO` lo rastree. Si un caso no termina `CUMPLE`, se repite entero en una sesión nueva hasta `config.reintentos` veces más (#29) y se detiene en el primer intento que cumple; `ResultadoCaso.intentos` los cuenta. Si los intentos no dieron todos el mismo estado, el caso es `inestable` y se informa el intento más grave; un caso que falla siempre igual no lo es. Con `config.paralelismo` mayor que uno (#30) los casos se reparten entre ese número de hilos, cada uno con su propia sesión (`SesionService.derivar`); los casos que comparten `extensiones.grupo` van uno tras otro, en orden, en el mismo hilo; el resultado conserva el orden de la suite, las notificaciones al observador se serializan y el modo `ROBOT` se ejecuta de a un caso. Los pasos `CAPTURAR` piden la imagen al agente, la guardan en `capturas/<caso>-<paso>.png` dentro de la carpeta de resultados del `LanzadorProceso` y registran el evento `CAPTURA`, que el informe HTML incrusta. |
| `nucleo.evaluacion` | `EvaluacionService`: delega en el proveedor descubierto por `ServiceLoader`, abre sesiones por caso y conserva veredictos completos. |
| `nucleo.informe` | `InformeService`: genera los informes con los reporteros registrados. |
| `nucleo.generacion` | `GeneracionService` (#26): arma una `Suite` con los casos que proponen los `GeneradorCasos` registrados para cada enfoque pedido y cada objetivo; renombra ids repetidos con `-2`, `-3` y ordena por enfoque e id. Un enfoque sin generador es un error que lo nombra. Sin objetivos los deriva del mapa (un campo por cada transición `LLENAR`; si no hay, toda la app). `guardar` escribe el YAML con `SuiteYaml` y lo relee con `ProyectoService`, así que lo guardado cumple `suite.schema.json`. `SuiteYaml` es la inversa de la lectura (la suite de ejemplo del contrato sobrevive a escribirla y leerla); un `llenar` o `seleccionar` solo puede localizar el campo con un texto simple. El generador de validación de `opsec-oraculos` solo produce casos cuando se le entrega su adaptador de formularios. El comando `opsec generar` es de `opsec-cli` (#53). |
| `nucleo.exploracion` | `ExploracionService` (#25): explora la app y devuelve un `MapaApp` (ventanas como nodos, acciones como aristas), con un contexto `sin-sesion` y uno por rol. Recorre en anchura; cada control se prueba en una sesión nueva (login, camino y acción), así que el estado es siempre limpio. Nunca pulsa controles cuyo texto coincida con `ConfigExploracion.textosDestructivos` (por defecto «eliminar», «borrar», «salir», «cerrar sesion») ni con una operación de la `politica` del proyecto. Se detiene por profundidad (4), por tiempo (5 min) o cuando no aparecen ventanas nuevas. `MapaJson` guarda y lee `mapa.json`. Todavía no explora puntos de entrada alternativos (`entrada:<clase>`). El comando `opsec explorar` es de `opsec-cli` (#53). |
| `nucleo.verificacion` | `VerificacionService` (#27): autoverificación previa. Comprueba, en orden y deteniéndose en el primer fallo: proyecto y suites válidos, JAR de la aplicación y del agente, conexión (la aplicación saluda), localizadores del primer paso de cada caso (con el login del rol si lo tiene; un localizador ausente es `AVISO`, no fallo) y preparación de los datos de prueba. Devuelve un `ResultadoVerificacion` con una `Comprobacion` (`OK`, `AVISO` o `FALLO`) por cada una y avisa de cada una a medida que termina. `ComandoVerificar` de la CLI todavía hace sus propias comprobaciones; adoptar este servicio es un cambio del módulo `opsec-cli`. |
| `nucleo.reproduccion` | `ReejecucionService` (#28): `registrar` guarda en la carpeta de resultados el `manifiesto.json` (hash del JAR, versiones de motor y de Java, sistema operativo, semilla, hash de la suite y fecha; `ManifiestoJson`) y `suite.yaml`, la suite ejecutada con su semilla. `reejecutar` los carga, calcula de nuevo el hash del JAR y de la suite (`Huellas`) y no ejecuta si cambió la aplicación, la suite o la semilla, salvo que se pida `permitirCambios`; las diferencias de versiones o de sistema solo se devuelven como avisos (`Discrepancia`). `casosConVeredictoDistinto` compara dos resultados. Si la suite tiene pasos que el formato YAML no puede expresar (`SuiteYaml.noExpresables` los lista; por ejemplo, llenar un campo localizado por su nombre de componente), `registrar` lanza `ExcepcionReejecucion` con esos pasos y no escribe nada: esa ejecución no se podría repetir. Los comandos de la CLI que lo usen son de `opsec-cli` (#53). |
| `nucleo.grabacion` | `GrabacionService` (#17): activa en el agente el inspector (`iniciarInspeccion`, componentes elegidos con `tomarInspecciones`, que devuelve `Inspeccion`) o la grabadora (`iniciarGrabacion`, `terminarGrabacion`) y convierte los eventos `ACCION_GRABADA` en una `Suite` de un solo caso con `convertir`; `ResultadoGrabacion` lleva además las advertencias. `LLENAR` y `SELECCIONAR` se localizan por la etiqueta (el formato de suites no admite otra cosa); lo que el formato no puede expresar —un campo sin etiqueta, desmarcar una casilla— se omite con un aviso, y un dato sensible (contraseña) queda en claro con un aviso. La suite se guarda con `SuiteYaml.guardar`. `ClienteAgente.modoInteraccion` envía `MODO_INTERACCION`. |
| `nucleo.cobertura` | Cobertura de código de la app probada con JaCoCo (#19). `ConfigCobertura` (jar del agente de JaCoCo, archivo `jacoco.exec` y patrones de clases a medir) y `LanzadorProceso.conCobertura` cargan el agente de JaCoCo antes que el del motor; las clases del propio agente nunca se miden. Todas las sesiones de una ejecución añaden sus datos al mismo `jacoco.exec` (también con casos en paralelo) y el agente del motor los escribe al recibir `CERRAR`. `CoberturaService.generar` cruza ese archivo con las clases del JAR de la app y escribe un informe HTML (`html/index.html`, con el código fuente si se da la carpeta) y otro XML (`cobertura.xml`); devuelve un `ResumenCobertura` con los `Contador` (instrucciones, ramas y líneas) de toda la app y de cada `ClaseCobertura`. Solo mide las clases que están directamente en el JAR y cumplen los patrones: en un JAR con librerías empaquetadas hay que indicar el paquete de la app. Activarlo desde la CLI es de `opsec-cli`. |

El Javadoc de `EjecucionService` lista lo que falta y la versión en que llega.

## Ejemplo de referencia

`EjecucionServiceTest` prueba la ejecución contra `AgenteFalso` de `opsec-soporte-pruebas`, sin lanzar ninguna aplicación: el arranque de la sesión es una lambda que conecta un agente falso al puerto del motor. `EjecucionServiceIT` hace lo mismo con el agente real y `AplicacionDePrueba` en otra JVM.

## Dependencias permitidas

- `opsec-dominio`.
- `opsec-protocolo`.
- Jackson (`jackson-databind` y `jackson-dataformat-yaml`).
- `json-schema-validator` (networknt).
- `org.jacoco.report` (EPL-2.0), para generar los informes de cobertura.
- Solo para pruebas: `opsec-soporte-pruebas` y el jar del agente (`opsec-agente`, clasificador `agente`) y el agente de JaCoCo (`org.jacoco.agent`, clasificador `runtime`), que no entran en el classpath de las pruebas: las de integración reciben sus rutas en las propiedades `opsec.agente.jar` y `opsec.jacoco.jar`.

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

## Esperas por condición y reintentos

Una espera por condición usa el localizador del componente o ventana esperado y termina
cuando el agente lo encuentra. Por ejemplo, dentro de los pasos de una suite:

```yaml
- esperar: {texto: "Menú administrador"}
  esperaMaximaMs: 2000
```

`esperaMaximaMs` reemplaza el plazo `config.esperaMaximaPasoMs` de la suite para ese
paso. El núcleo envía el plazo al agente; `ClienteAgente` añade su margen de respuesta
para permitir que termine la espera antes de declarar un fallo de comunicación.

Los reintentos se configuran desde la presentación, en el constructor del servicio:

```java
EjecucionService ejecucion =
        new EjecucionService(proyecto, sesiones, evaluacion, observador, 2);
```

También se admite `new EjecucionService(proyecto, sesiones, evaluacion, 2)` sin observador.
La cantidad debe ser no negativa y cuenta repeticiones adicionales: con 2 hay hasta
3 intentos. Los constructores anteriores conservan cero reintentos por defecto.
La configuración se aplica a los pasos de espera del caso y de su login.

Se repite únicamente `ESPERAR` cuando el agente responde `TIEMPO_AGOTADO`. Cada intento
usa el mismo plazo y la misma sesión. No hay una pausa fija entre intentos: cada solicitud
vuelve a esperar la condición hasta su plazo. Si se agotan, el caso queda
`NO_CONCLUYENTE` y no ejecuta sus pasos siguientes, salvo que los oráculos encuentren
un fallo y determinen `NO_CUMPLE`. No se repiten otros códigos de error, desconexiones,
respuestas que no llegan a tiempo ni acciones que cambian la aplicación: una acción
que agota su tiempo podría seguir ejecutándose. Una interrupción impide el siguiente
reintento y conserva la marca del hilo.

Cada intento conserva su `PASO_INICIO`, los eventos del agente y su `PASO_FIN`, con el
mismo número de paso, y emite los avisos de progreso correspondientes. Los totales del
progreso describen pasos previstos y no incluyen intentos adicionales. Todos los eventos
llegan a los oráculos, incluidos los de intentos que agotaron el tiempo; que la espera
se recupere no elimina hallazgos. El número de intentos del caso sigue en 1 y su marca
de inestable sigue en `false`. `ConfigEjecucion.reintentos` corresponde a la reejecución
de casos completos de la v0.3 (#29) y no se usa para esta configuración.

`EsperasEjecucionServiceTest` usa `AgenteFalso` con respuestas demoradas y guiones que
agotan el plazo antes de responder bien. Comprueba los límites, la traza, el progreso,
la conservación de hallazgos y los errores que no se deben reintentar.

## Evaluación delegada (#23)

`RegistroExtensiones.crearEvaluador(carpetaReglas)` descubre `ProveedorEvaluacion` con
el mismo cargador de clases del registro y exige exactamente una implementación válida.
La carga se hace cuando se necesita el evaluador, sin obligar a otras operaciones del
registro a crear uno. Una ausencia, ambigüedad o respuesta nula produce un error de
configuración. `opsec-oraculos` aporta el proveedor en el classpath de ejecución; el
núcleo no depende de sus clases ni agrega una dependencia a ese módulo.

`new EvaluacionService(registro)` conserva la API existente y no carga reglas de archivos.
Para reglas YAML, el consumidor indica la carpeta explícitamente:

```java
EvaluacionService evaluacion = new EvaluacionService(registro, carpetaReglas);
```

El proveedor combina esas reglas con los oráculos registrados antes de ejecutar casos.
Los errores de lectura, reglas inválidas e ids duplicados se informan al crear el servicio.
No se deduce una ruta desde el JAR de la aplicación. La CLI conserva su llamada anterior;
la selección de carpeta desde esa presentación corresponde a su integración posterior.

`EjecucionService` crea una sesión de evaluación por caso y entrega cada evento justo
cuando se incorpora a la traza: pasos, login, capturas, reintentos de espera y los eventos
que entrega el cliente del agente. Al finalizar llama a `terminar(motivoIncompleto)`;
los oráculos EVENTO no vuelven a evaluarse sobre toda la traza al cerrar el caso. Los
hallazgos y errores anteriores se conservan según el contrato. Esto no cambia el hilo
lector de sockets: los eventos del agente se evalúan cuando el servicio recoge su lote.

`evaluarCaso(traza, contexto, motivo)` sigue disponible para trazas recogidas: reproduce
sus eventos en orden, con el mismo ciclo de vida. Una traza ajena al contexto se rechaza.
`evaluarResultadoSuite(contexto)` devuelve el veredicto completo de suite;
`evaluarSuite(contexto)` conserva la API anterior que entrega solo hallazgos.
`ResultadoSuite` no dispone de un campo para el estado o los motivos de suite; para
consultarlos se usa la API de evaluación completa.

Las pruebas de la fachada usan un proveedor local registrado únicamente en los recursos
de pruebas. Las pruebas del proveedor real y sus reglas pertenecen a `opsec-oraculos`;
la CLI y la verificación del proyecto ejercitan su descubrimiento real. No se añade una
dependencia de pruebas del núcleo a oráculos. `EvaluacionDelegadaTest` comprueba la
reproducción de trazas, los veredictos completos y los errores de descubrimiento;
`EjecucionServiceTest` comprueba con `AgenteFalso` que los prefijos se evalúan antes de
enviar el paso siguiente. Las pruebas existentes conservan sus comprobaciones; la traza
del ejemplo de evaluación ahora contiene un evento real para activar el momento EVENTO.

## Ejecución oculta

`EjecucionService` transmite `suite.config().oculto()` al abrir cada caso. `SesionService` y `ArranqueApp` conservan sus firmas anteriores y añaden sobrecargas para esta opción. `LanzadorProceso` agrega `oculto=true` a los argumentos de `-javaagent` solo cuando se solicita; el agente mantiene las ventanas fuera de pantalla y sin foco. Las sesiones sin esta opción conservan el comando anterior.

## Payloads explícitos por caso

Un caso puede declarar `extensiones.payloads` como una lista no vacía de textos y usar
`{payload}` en uno o más valores de `llenar`. Antes de abrir sesiones, el ejecutor genera
una copia independiente por valor, en orden, con ids `<caso>-P1`, `<caso>-P2`, etc.
La sustitución es literal y admite cadenas vacías, comillas, Unicode y saltos de línea;
no vuelve a interpretar el texto insertado como una plantilla. Conserva rol, políticas,
oráculos y configuración, y cada copia tiene sesión, resultado, traza y capturas propios.
El progreso y la evaluación de suite reciben los casos materializados.

Se rechazan antes de ejecutar: listas vacías, valores que no sean textos, ausencia de
`{payload}` en LLENAR, marcadores en valores de otras acciones, colisiones de ids,
mezcla con `ataques` y expansión de un caso usado por `enumeracionUsuarios`.
La lista se elimina de las copias para que no se expandan de nuevo.

Esta opción usa la sección de extensiones existente y no modifica contratos.
No conecta los ids de `caso.ataques` con la biblioteca, no genera canarios y no instala
ganchos JDBC/Firestore. Véase la demostración completa en `demo/payloads/`.

## Fallos inyectados (#16)

La sección `fallos` de un caso (`punto:clase[:mensaje]`, ver `contratos/protocolo/README.md`) pide al agente que lance excepciones en SQL, Firestore o archivos. `EjecucionService` los envía en un segundo `CONFIG_GANCHOS` y terminan con la sesión del caso:

- Con `rol`, después del inicio de sesión: el login no debe fallar por el fallo del caso.
- Sin `rol`, cuando la aplicación ya mostró su primera ventana (consulta `ARBOL_SOLICITUD` cada 100 ms, hasta la espera máxima de un paso; si no aparece ninguna ventana, los activa de todos modos y lo registra). Sin esa espera, el fallo llegaba mientras la app abría su base de datos y la app terminaba antes de ejecutar el primer paso.

`InyeccionFallosIT` lo comprueba con la app de práctica real y `demo/robustez`: el agente informa el fallo, los cinco pasos terminan en `OK` y el diálogo de error muestra la pila (F7) o un mensaje genérico según la app.
