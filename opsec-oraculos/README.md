# opsec-oraculos

Oráculos incluidos en el motor. Cada uno implementa `Oraculo` y decide hallazgos sobre la traza de un evento, un caso o una suite. El núcleo los descubre con `ServiceLoader`.

- **Dueño:** P2.
- **Paquete raíz:** `io.github.physanto.opsecengine.oraculos`

## Ejemplo de referencia

`OraculoExcepcionNoCapturada` (id `EXCEPCION-NO-CAPTURADA`, momento `EVENTO`) produce un hallazgo por cada evento `EXCEPCION` con `capturada=false`. Se registra en `src/main/resources/META-INF/services/io.github.physanto.opsecengine.dominio.extension.Oraculo`.

Su prueba, `OraculoExcepcionNoCapturadaTest`, carga con `CargadorTrazas` de `opsec-soporte-pruebas` una traza que lo activa (`src/test/resources/.../excepcion-no-capturada.jsonl`) y `caso-limpio.jsonl`, que no. Cada oráculo nuevo sigue el mismo patrón: al menos una traza que lo activa y una que no.

## Oráculos incluidos

| Id | Clase | Momento | CWE | Qué detecta |
|---|---|---|---|---|
| `EXCEPCION-NO-CAPTURADA` | `OraculoExcepcionNoCapturada` | `EVENTO` | — | Un evento `EXCEPCION` con `capturada=false`. |
| `APP-TERMINADA` | `OraculoAppTerminada` | `EVENTO` | — | Un evento `APP_TERMINADA` con el booleano `esperada=false`, incluso con código de salida cero. Ignora cierres esperados, el cierre de ventanas y campos ausentes o mal tipados. El agente o el núcleo deben emitir el evento; el oráculo no observa procesos directamente. |
| `TRAZA-EN-DIALOGO` | `OraculoTrazaEnDialogo` | `EVENTO` | CWE-209 | El campo `texto` de un `DIALOGO` contiene una firma de excepción o una línea de pila Java. Detecta clases calificadas, excepciones sin paquete seguidas de dos puntos y marcos con archivo y línea, `Native Method` o `Unknown Source`. No copia el mensaje al hallazgo ni marca palabras genéricas como «error». |
| `CONTRASENA-EN-CONSOLA` | `OraculoContrasenaEnConsola` | `EVENTO` | CWE-532 | Una línea `CONSOLA` que contiene una contraseña de prueba: la de un rol del proyecto o la escrita con `LLENAR` en un campo cuyo localizador menciona contraseña, password, clave o pass. Ignora contraseñas de menos de 4 caracteres y nunca copia la contraseña en el hallazgo. Lo activa el fallo F6 de la app de práctica. |

## Evaluación por momento

`EvaluadorOraculos` recibe los oráculos registrados. `iniciarCaso(contexto)` crea una sesión
independiente: `registrarEvento(evento)` evalúa los oráculos `EVENTO` con el prefijo observado y
`terminar(motivoIncompleto)` evalúa los de `CASO`. Los veredictos acumulan hallazgos sin repetirlos;
un error de un oráculo queda como motivo no concluyente. `traza()` devuelve una copia inmutable.
Al terminar la suite, `evaluarSuite(Contexto.deSuite(...))` evalúa únicamente los de `SUITE`.
Cada sesión de caso se usa desde un solo hilo y recibe secuencias estrictamente crecientes.

El prefijo conserva la detección de contraseñas obtenidas de `PASO_INICIO`, aunque puede requerir
trabajo cuadrático con trazas largas. El núcleo todavía debe conectar estas llamadas a su ejecución;
esta implementación no modifica `EvaluacionService` ni `EjecucionService`.

## Evaluación temporal de validación (#38)

`EvaluadorValidacionTemporal.evaluar(traza, politica)` evalúa al terminar el caso una
`PoliticaValidacionTemporal`: caso, paso `LLENAR`, paso posterior `PULSAR`, valor declarado
inválido y señales explícitas de aceptación y rechazo. Cada `Senal` combina un tipo de evento
(`DIALOGO`, `SESION` o `ESTADO`) con igualdades sobre sus datos. Las señales deben describir la
semántica de la app; un `PASO_FIN` con `OK` no demuestra aceptación de la entrada.

Exige que la escritura termine correctamente y considera solo respuestas del caso y paso de envío,
posteriores a este y anteriores a la siguiente acción. La aceptación produce `NO_CUMPLE` con un
hallazgo `PROBABLE`; el rechazo explícito con envío terminado produce `CUMPLE`. La falta de
evidencia produce `NO_CONCLUYENTE`. No copia el valor probado ni los mensajes de la app al hallazgo.
Una señal de aceptación tardía atribuida al envío, después de comenzar otra acción, también deja
el resultado no concluyente si todavía no se había observado una aceptación dentro del intervalo.

Esta evaluación no se registra con `ServiceLoader` ni se conecta automáticamente a la CLI, al
generador de la #37 o al núcleo. El llamador debe conservar el `Veredicto` completo y combinarlo
con los oráculos implícitos existentes. La solicitud [solicitud-contrato-38.md](solicitud-contrato-38.md)
propone el id, el CWE y la ampliación necesaria para preservar los tres estados en el contrato.

## Generación de validación con adaptador temporal

`GeneradorValidacion` implementa `GeneradorCasos` para el enfoque `VALIDACION`. El constructor
`GeneradorValidacion(adaptador)` recibe un `AdaptadorValidacion` con formularios y payloads ya cargados:

- `Formulario(ventanaId, base, pasoEnvio, campos)`: caso base con la navegación conocida, valores
  válidos y envío. No admite ataques pendientes de expansión. `pasoEnvio` identifica un `PULSAR`.
- `Campo(ordenPaso, tipo)`: identifica un paso `LLENAR` anterior al envío y su tipo de campo.
- `Payload(id, texto, tiposCampo, usaCanario, oraculos)`: proyección local de una entrada de la
  biblioteca. Los tipos son los nombres de `TipoCampo`, sin importar clases de `opsec-ataques`.

El generador selecciona las ventanas presentes en el mapa y produce una variante por campo y
payload compatible. Conserva los demás valores, pasos y rol del caso base. Sustituye `{canario}`
cuando corresponde, deja `ataques` vacío y combina los ids de oráculos sin repetirlos. Ordena por
id de caso base, orden del campo e id de payload; genera ids y canarios deterministas. El origen
queda en `Caso.extensiones().get("validacion")`, junto al resto de extensiones del caso base.
Un objetivo de campo limita la variación a ese campo; un objetivo de botón de envío incluye todos
los campos del formulario. Omite ventanas ausentes y roles no declarados en el proyecto.

Para la instancia descubierta con `ServiceLoader`, el integrador debe proporcionar una **instancia
Java** de `AdaptadorValidacion` en `Proyecto.extensiones().get("validacion")`. Sin ella devuelve una
lista vacía. Un mapa procedente de YAML no se convierte automáticamente en ese adaptador. No hay
descubrimiento de campos, lectura de la biblioteca ni conexión nueva a la CLI: el líder debe
aportar esos datos y sustituir este adaptador cuando exista el contrato definitivo. La generación
no ejecuta ni comprueba en la GUI los casos base aportados.

## Reglas YAML

`new CargadorReglas().cargarCarpeta(ruta)` carga los archivos `*.yaml` de una carpeta en orden de
nombre y devuelve una lista de `Oraculo`; `leer(archivo)` carga un solo archivo. Las reglas se
validan con el esquema oficial empaquetado, sin acceso a la red, y rechazan ids reservados,
duplicados y CWE ajenos al catálogo. La lista puede combinarse con los oráculos Java al crear
`EvaluadorOraculos`. No se registra cada regla con `ServiceLoader` ni se cambia la CLI.

`texto_contiene` busca cualquiera de sus fragmentos, distinguiendo mayúsculas, en el campo indicado
o en todos los campos de texto si no hay `campo`. Se combina con `tipo` e `igual_a` mediante AND.
Se produce un hallazgo por evento en `EVENTO`, por caso en `CASO` y por suite en `SUITE` si hay
coincidencias. Un campo ausente no equivale a un valor null. Los archivos inválidos lanzan
`IllegalArgumentException` con el origen y los errores; los fallos de lectura lanzan `IOException`.

### Pendiente para el dueño del módulo (P2)

Ordenado según `PLAN_DE_TRABAJO.md` (sprints 1 a 6):

1. `CONTRASENA-EN-CONSOLA`: buscar también los payloads con `{contrasena}` y los canarios cuando lleguen los ataques (v0.2).
2. Oráculos de seguridad del catálogo (v0.3): `SIN-SESION`, `AUTORIZACION-DIFERENCIAL`, `SQLI-CANARIO`, `ENUMERACION-USUARIOS`, `SIN-BLOQUEO`, `CREDENCIALES-DEBILES`, `CONTRASENA-EN-CAMPO-VISIBLE`, entre otros.

## Dependencias permitidas

- `opsec-dominio`.
- Solo para pruebas: `opsec-soporte-pruebas`.

No puede depender del núcleo, del agente ni del protocolo.

## Pruebas

Desde la raíz del repositorio:

```sh
./mvnw -B -pl opsec-oraculos -am verify
```

En Windows:

```bat
mvnw.cmd -B -pl opsec-oraculos -am verify
```
