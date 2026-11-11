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
