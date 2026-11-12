# Contrato de evaluación por evento, caso y suite

Cambio aditivo para la versión 1.3 de contratos, vinculado a #23 y aprobado por el líder.
Agrega interfaces a `opsec-dominio`; conserva los modelos, los constructores y las interfaces
anteriores. El protocolo, los esquemas YAML y los catálogos siguen con sus formatos actuales.

## Problema y estado de la entrega

`EvaluadorOraculos` y `CargadorReglas` viven en `opsec-oraculos`. El núcleo necesita delegar
en ellos sin importar sus clases ni añadir una dependencia a ese módulo. Hoy solo se
descubren oráculos individuales, generadores, reporteros y proveedores de datos.

Esta entrega define el punto de extensión y el trabajo necesario de P2. Todavía no registra
un proveedor ni cambia la ejecución del núcleo. La #23 sigue pendiente hasta que exista el
proveedor y el núcleo lo conecte a los eventos de la ejecución.

## Interfaces

Todas viven en `io.github.physanto.opsecengine.dominio.extension`:

| Interfaz | Método | Propósito |
|---|---|---|
| `ProveedorEvaluacion` | `crear(Collection<? extends Oraculo>, Path)` | Crear un evaluador con los oráculos registrados y una carpeta opcional de reglas. Es el servicio que se descubre. |
| `Evaluador` | `iniciarCaso(Contexto)` | Crear una sesión independiente para un caso. |
| `Evaluador` | `evaluarSuite(Contexto)` | Evaluar los oráculos SUITE y devolver un `Veredicto` completo. |
| `EvaluacionCaso` | `registrarEvento(Evento)` | Evaluar los oráculos EVENTO con el prefijo observado y devolver el veredicto parcial. |
| `EvaluacionCaso` | `terminar(String)` | Evaluar los oráculos CASO y combinar sus resultados con los acumulados. |
| `EvaluacionCaso` | `traza()` | Obtener una copia inmutable de los eventos recibidos. |

El Javadoc de las interfaces fija las validaciones, excepciones y reglas de concurrencia.
El proveedor y el evaluador son seguros entre hilos si los oráculos lo son. Cada sesión de
caso pertenece a un solo hilo y solo admite un cierre.

## Implementación solicitada a P2

Trabajo en `modulo/oraculos`, después de traer este contrato desde `main`:

1. Crear un proveedor público sin argumentos que implemente `ProveedorEvaluacion`.
   Puede adaptar `EvaluadorOraculos` y `CargadorReglas`, conservando sus APIs actuales.
2. Registrar su nombre completo en
   `src/main/resources/META-INF/services/io.github.physanto.opsecengine.dominio.extension.ProveedorEvaluacion`.
   Se registra únicamente el proveedor, no una instancia de evaluador ni de sesión.
3. Implementar `crear`: copiar los oráculos recibidos, cargar las reglas si la ruta no es null
   y crear un evaluador nuevo con ambos conjuntos. La carpeta se recibe explícitamente;
   no se deduce del JAR ni del directorio de trabajo. Una carpeta indicada pero ausente o
   ilegible produce `IOException`; null no carga archivos. Una carpeta vacía es válida.
   Los archivos inmediatos `*.yaml` se cargan en UTF-8, ordenados por nombre, reutilizando
   la validación existente. Se rechazan ids duplicados entre todos los oráculos y reglas.
   No se devuelve un evaluador parcial ante errores de configuración.
4. Hacer que el evaluador y las sesiones implementen las interfaces nuevas, directamente
   o mediante adaptadores locales. Mantener la separación EVENTO, CASO y SUITE y la
   validación de secuencias y pertenencia al caso.
5. Invocar `Oraculo.evaluarResultado`, conservando estados y motivos. La implementación
   actual invoca `evaluar`, por lo que pierde los NO_CONCLUYENTE de oráculos como
   `ENTRADA-INVALIDA-ACEPTADA`. Los errores de un oráculo no detienen a los demás y
   quedan como motivos persistentes. Los ids solicitados pero ausentes también persisten.
6. Separar la falta provisional de evidencia de los errores persistentes: para cada oráculo
   EVENTO, su último veredicto reemplaza el motivo anterior de falta de evidencia. Si al
   llegar otro evento ya tiene evidencia suficiente, ese motivo deja de impedir CUMPLE.
   Los hallazgos anteriores se conservan y se deduplican por igualdad, en orden de aparición.
   El motivo de ejecución incompleta se conserva al terminar, incluso con NO_CUMPLE.

No añadir dependencias al núcleo, al protocolo ni al agente. El proveedor pertenece a
`opsec-oraculos` y usa las interfaces de dominio.

### Pruebas de aceptación para P2

- Descubrimiento del proveedor real por `ServiceLoader` usando la interfaz de dominio.
- Evaluación Java sin carpeta, carpeta vacía y carpeta con reglas válidas de cada momento.
- Errores por carpeta ausente, reglas inválidas, ids reservados y duplicados entre reglas
  o entre una regla y un oráculo registrado. No generar resultados parciales.
- EVENTO recibe el prefijo completo; CASO se ejecuta una vez al cerrar y SUITE solo con
  el contexto y las trazas de suite. Ningún momento ejecuta oráculos de otro momento.
- Un oráculo que sobrescribe `evaluarResultado` conserva su NO_CONCLUYENTE y el motivo.
- Un oráculo EVENTO pasa de falta de evidencia a CUMPLE tras recibir más eventos; el
  motivo provisional se resuelve. Un error anterior de ejecución de un oráculo persiste.
- Los hallazgos INFO, los ids ausentes y el motivo incompleto se conservan; un hallazgo
  no informativo determina NO_CUMPLE aunque la ejecución esté incompleta.
- Sesiones independientes, traza inmutable, orden estricto, rechazo de eventos ajenos,
  cierre único y rechazo de eventos después del cierre. Una entrada rechazada no cambia
  la sesión; `traza()` sigue disponible después del cierre.
- Compatibilidad de los consumidores actuales de las clases concretas del módulo.
- `./mvnw -B verify` completo, incluidas las reglas de dependencia.

## Integración posterior del núcleo

Con el proveedor implementado e integrado, el líder continúa #23 en `modulo/nucleo`:

1. Descubrir `ProveedorEvaluacion` mediante `ServiceLoader`, usando el mismo cargador que
   las otras extensiones. Exigir exactamente un proveedor; cero o varios son errores de
   configuración. No elegir por orden del classpath ni usar reflexión sobre clases concretas.
2. Crear el evaluador con los oráculos registrados y la ruta de reglas suministrada de
   forma explícita por el consumidor. Conservar las APIs actuales de `EvaluacionService`.
3. Iniciar una sesión por caso y registrar cada evento del motor y del agente cuando se
   incorpora a la traza. Incluir login, capturas, reintentos de espera y eventos de cierre.
   Evaluar en ese punto no obliga a cambiar la recepción de sockets: los eventos del agente
   se procesan cuando el cliente los entrega al servicio.
4. Terminar cada caso con su motivo incompleto y evaluar la suite con todas sus trazas.
   Conservar el veredicto completo de suite en la API de evaluación; la API anterior que
   devuelve hallazgos puede mantenerse como fachada compatible, sin afirmar que transmite
   el estado de la suite. El modelo actual `ResultadoSuite` solo transporta hallazgos de suite;
   representar allí un estado no concluyente requeriría otro cambio de contrato aprobado.
5. Probar el flujo con `AgenteFalso` y con el proveedor real en una prueba de integración
   del proyecto. Evitar añadir dependencias de compilación entre núcleo y oráculos.

Se coordina la integración del proveedor antes de cambiar el descubrimiento obligatorio
para que los consumidores existentes sigan funcionando durante la transición.
