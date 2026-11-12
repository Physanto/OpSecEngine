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
| `CONTRASENA-EN-CONSOLA` | `OraculoContrasenaEnConsola` | `EVENTO` | CWE-532 | Una contraseña de un rol, un valor escrito en un campo de contraseña o el canario asociado a ese valor aparece en `CONSOLA`. Reconoce palabras del localizador y `JPasswordField` en `PASO_FIN.componente`. Ignora valores y marcas de menos de 4 caracteres. No copia valores, marcas, localizadores ni líneas de consola al hallazgo. Lo activa el fallo F6 de la app de práctica. |

Para los payloads generados por la #37, el canario se obtiene de
`Caso.extensiones().get("validacion")`, campo `canario`. Solo se rastrea tras observarlo dentro
del valor de un `LLENAR` sensible del mismo caso. Un canario escrito únicamente en usuario o
búsqueda no activa este oráculo. La evidencia del rastreo incluye las secuencias de escritura
y consola; los valores completos de contraseña conservan la secuencia de consola como evidencia.
Los nombres se separan por palabras y camelCase: `txtPassword` cuenta como sensible y `bypass`
no. Un `PASO_FIN` con `OK` y componente `JPasswordField` identifica también campos de nombre opaco.
Las variables deben llegar materializadas desde la ejecución; el oráculo no carga ni expande
la biblioteca de ataques. El rastreo por canario requiere los metadatos indicados.

## Canarios en SQL y Firestore (#42)

`OraculoSqliCanario` (`SQLI-CANARIO`) y `OraculoFirestoreRutaCanario`
(`FIRESTORE-RUTA-CANARIO`) se evalúan al terminar el caso, con CWE-89 y registro SPI.
Leen la marca exacta de `Caso.extensiones().get("validacion")`, campo `canario`, reutilizando
la convención local de las #37 y #39. Exigen al menos cuatro caracteres, una marca no vacía
y sin llaves de plantilla. Primero deben observarla dentro del valor materializado de un
`LLENAR` del propio caso; solo después buscan coincidencias, distinguiendo mayúsculas.

SQL busca únicamente en `SQL.sql` cuando `tipoSentencia=STATEMENT`. Ignora `PREPARED`,
parámetros y mensajes de error como destinos. Firestore busca únicamente en `FIRESTORE.ruta`,
incluidas operaciones de lectura; la aparición en `campos` no activa el oráculo. Un evento
con error sigue demostrando propagación si la marca está en el destino correspondiente.
Cada coincidencia produce un hallazgo `PROBABLE` con las secuencias de entrada y backend,
sin copiar la marca, el payload, la consulta ni la ruta. No confirma explotación ni efectos
sobre datos, aunque haya filas afectadas. El rastreo parte de `PASO_INICIO`: no exige un
`PASO_FIN` exitoso, porque el backend puede ejecutarse durante la escritura del campo.

La traza oficial `c13-sqli-login.jsonl`, con `K7Q2` declarado en su contexto, da `NO_CUMPLE`
por F4; su variante con `PREPARED` no activa el oráculo. Sin metadatos o sin escritura previa
no hay hallazgos; una lista vacía no demuestra ausencia de inyección. El llamador debe aportar
la marca y los eventos JDBC o Firestore. Los oráculos no instrumentan la aplicación ni extraen
canarios de texto arbitrario. La [solicitud de contrato](solicitud-contrato-42.md) propone
formalizar la asociación entre caso y canario; la conexión al núcleo sigue pendiente.

## Enumeración de usuarios (#43)

`OraculoEnumeracionUsuarios` (`ENUMERACION-USUARIOS`, `SUITE`, CWE-204) compara los textos
ordenados de los diálogos de dos intentos de login fallidos. El llamador debe aportar una
instancia Java de `ComparacionEnumeracionUsuarios` en
`Suite.extensiones().get("enumeracionUsuarios")`: caso de usuario inexistente y su paso de
envío, caso de cuenta existente con contraseña incorrecta y su paso de envío. Debe garantizar
que los datos representan esos escenarios y que ambos usan el mismo formulario.
Para `c01-c02-mensajes.jsonl`, la instancia es
`new ComparacionEnumeracionUsuarios("C01", 5, "C02", 5)`; la prueba produce `NO_CUMPLE` por F5.
La traza corregida con el mismo mensaje en ambos casos no activa el oráculo.

Cada caso debe pertenecer a la suite y tener una sola traza y un único inicio del paso de envío.
Solo se comparan diálogos del propio caso y paso entre `PASO_INICIO` con `PULSAR` y `PASO_FIN`
con `OK`. Se descartan intentos con sesión activa, respuestas sin texto, envíos incompletos,
acciones intercaladas, reintentos y diálogos del envío que llegan después de su finalización.
Compara contenido, cantidad y orden de los textos, omitiendo solo espacios exteriores;
no compara títulos, opciones, tipos de diálogo ni tiempos.

Las diferencias producen un hallazgo `PROBABLE`, sin copiar mensajes ni credenciales.
La explicación identifica los dos casos; la evidencia lista primero las secuencias de diálogos
del usuario inexistente y luego las de contraseña incorrecta. Sin configuración o evidencia
suficiente no hay hallazgos: el `CUMPLE` del evaluador existente no acredita entonces ausencia
de enumeración. Un mapa de YAML no se transforma en el adaptador automáticamente. La
[solicitud de contrato](solicitud-contrato-43.md) propone formalizar esta configuración;
la carga y conexión al núcleo siguen pendientes. No se infieren escenarios desde ids,
descripciones, nombres de usuario ni frases propias de una aplicación.

## Credenciales débiles y bloqueo de login (#44)

`OraculoCredencialesDebiles` (`CREDENCIALES-DEBILES`, CWE-521) y `OraculoSinBloqueo`
(`SIN-BLOQUEO`, CWE-307) se registran por SPI y se evalúan al terminar el caso.
El llamador aporta una instancia Java de `PoliticaAutenticacion` en
`Caso.extensiones().get("autenticacion")`, con:

- `intentos`: lista ordenada de `Intento(pasoUsuario, pasoContrasena, pasoEnvio, usuario,
  contrasena, debil)`. Los valores ya deben estar materializados; pueden estar vacíos.
- `fallosPermitidos`: N rechazos consecutivos antes de exigir protección.
- `retardoMinimo`: duración positiva considerada protección desde el último rechazo.
- `rechazo` y `bloqueo`: listas no vacías de `PoliticaValidacionTemporal.Senal`, con igualdades
  sobre datos de eventos. El integrador define su significado para la aplicación.

El rastreador exige dos `LLENAR` de campos distintos con los valores declarados y sus finales
`OK`, sin sobrescrituras, sesión previa ni envíos intermedios. El envío debe ser un `PULSAR`
único con final `OK`. Solo atribuye respuestas al mismo caso y paso, antes de la siguiente
acción; descarta respuestas tardías o contradictorias. Un `OK`, un rol o una ventana abierta
no prueban autenticación: debe observarse `SESION` con `activa=true`. Si ese evento aporta
`usuario`, debe coincidir exactamente con el payload.

La aceptación de un payload declarado débil produce un hallazgo `CONFIRMADO`. Las trazas de
F1 prueban `soporte` / `soporte123` aceptado y rechazado; esas credenciales no están fijadas
en el código del oráculo. La declaración `debil` procede de la biblioteca o política del
llamador; no se infiere fortaleza ni se leen los payloads de `opsec-ataques`.

`SIN-BLOQUEO` requiere N rechazos observados seguidos del mismo usuario y una aceptación
posterior antes del retardo mínimo, sin bloqueo observado. Produce un hallazgo `PROBABLE`.
Un cambio de usuario, intento incompleto, bloqueo, sesión o reinicio intermedio, envío no
declarado, intervalo al menos igual al umbral o tiempos no monótonos rompen la serie.
El intervalo se mide desde el rechazo anterior hasta la respuesta siguiente, incluyendo la
escritura y la espera del cliente: puede ocultar una falta de protección, pero no atribuye
una pausa del cliente a un fallo del servidor. La política debe describir el mismo ámbito
de bloqueo sin reinicios que borren su contador. N no se fija en cinco ni se confunde con
`ConfigEjecucion.reintentos`, que repite casos completos.

Los hallazgos solo referencian secuencias; no copian credenciales ni mensajes de la aplicación.
Sin política o evidencia suficiente no hay hallazgos y el `CUMPLE` del evaluador no demuestra
seguridad. Un mapa YAML no se convierte en el adaptador automáticamente. La
[solicitud de contrato](solicitud-contrato-44.md) propone formalizar esos datos; la carga de
payloads y la conexión al núcleo siguen pendientes.

## Contraseñas visibles y cifrado JDBC (#45)

`OraculoContrasenaEnCampoVisible` (`CONTRASENA-EN-CAMPO-VISIBLE`, `CASO`, CWE-549) correlaciona
un `LLENAR` no vacío con su `PASO_FIN` correcto, antes de otra acción, y exige una clase de
componente conocida: `JTextField`, `JTextArea`, `JTextPane` o `JEditorPane`, con nombre corto
o paquete `javax.swing`. Identifica la contraseña por las mismas palabras y reglas de camelCase
de `CONTRASENA-EN-CONSOLA`, o por el paso y valor de contraseña de `PoliticaAutenticacion` (#44).
Un campo de usuario o búsqueda no se clasifica por contener un valor parecido a una contraseña.
El hallazgo es `PROBABLE` y referencia escritura y final sin copiar valores ni localizadores.
Ignora clases desconocidas, escrituras fallidas o vacías y `JPasswordField`. No conoce el carácter
de eco: un `JPasswordField` configurado para mostrar texto requiere más datos del emisor.

`OraculoJdbcSinCifrado` (`JDBC-SIN-CIFRADO`, `EVENTO`, CWE-319) examina `SQL.conexion` de eventos
del propio caso sin error de backend. Produce un hallazgo `PROBABLE` por evento cuando la URL
conocida desactiva TLS o permite texto claro, sin copiar URL, consultas ni credenciales:

- MySQL (`jdbc:mysql://`): `sslMode=DISABLED` o `PREFERRED`, `useSSL=false`, o ausencia de una
  exigencia de TLS. Un `sslMode` explícito prevalece sobre las opciones heredadas. `REQUIRED`,
  `VERIFY_CA` y `VERIFY_IDENTITY` no activan. Sin `sslMode`, `requireSSL=true` o
  `verifyServerCertificate=true` descartan el hallazgo salvo `useSSL=false`.
- PostgreSQL (`jdbc:postgresql://`): `sslmode=disable`, `allow` o `prefer`, o ausencia de
  exigencia de TLS. Un `sslmode` explícito prevalece sobre `ssl`. `require`, `verify-ca`,
  `verify-full` y `ssl=true` o `ssl` sin valor no activan.
- SQL Server (`jdbc:sqlserver://`): solo `encrypt=false` explícito. No infiere un valor
  predeterminado sin conocer la versión del driver.
- H2: `jdbc:h2:tcp://` activa; `jdbc:h2:ssl://` y las URLs locales o en memoria no activan.
  `CIPHER=AES` cifra archivos y no convierte TCP en TLS.

El análisis se limita a esas formas de URL. Se abstiene ante drivers desconocidos, opciones
TLS no reconocidas, parámetros repetidos, escapes inválidos, llaves y formas MySQL de host
con paréntesis. Decodifica los parámetros de consulta MySQL/PostgreSQL y conserva sus nombres
exactos; las claves SQL Server no distinguen mayúsculas. No determina el TLS negociado,
propiedades suministradas aparte ni validación de certificados. Un resultado sin hallazgos
no certifica el canal. La app de práctica usa `JPasswordField` y H2 en memoria y no activa
estos oráculos; las pruebas incluyen trazas positivas y negativas independientes.

La interpretación sigue las referencias oficiales de
[MySQL Connector/J](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-security.html),
[pgJDBC](https://jdbc.postgresql.org/documentation/ssl/),
[SQL Server JDBC](https://learn.microsoft.com/en-us/sql/connect/jdbc/setting-the-connection-properties)
y [H2](https://h2database.github.io/html/features.html).
Ambos oráculos se registran por SPI; el emisor debe aportar la clase de componente y la URL
de conexión. No instrumentan la aplicación ni cambian la integración pendiente del núcleo.

## Evaluación por momento

`OraculoSinSesion` (id `SIN-SESION`, momento `CASO`, CWE-306) detecta operaciones SQL o
Firestore incluidas en `Proyecto.sensibles()` cuyo contexto declara `sesionActiva=false`.
Respeta las listas vacías y compara operaciones sin distinguir mayúsculas. Si SQL no aporta
`operacion`, usa su primera palabra, omitiendo espacios y comentarios iniciales; no analiza
sentencias compuestas ni resuelve escrituras dentro de un `WITH`. En esos casos el emisor debe
aportar `operacion`. No interpreta roles, ventanas ni un inicio de sesión posterior como prueba
de autenticación de una escritura anterior. Ignora eventos de otros casos y errores de backend.

El hallazgo es confirmado cuando SQL informa una cantidad entera positiva de `filasAfectadas`;
sin esa prueba es probable, incluso con cero filas. Firestore produce hallazgos probables porque
su evento no confirma el efecto. Los eventos `ESTADO` no se asocian automáticamente a escrituras.
La evidencia referencia la secuencia de la operación sin copiar consultas, rutas ni valores.
Las pruebas comprueban `n3-local-sin-login.jsonl` (NO_CUMPLE), `a1-admin-elimina.jsonl` (CUMPLE)
y la apertura directa de F3, incluida su denegación corregida. El emisor debe aportar eventos de
backend y un contexto de sesión correcto; el oráculo no instrumenta la aplicación.

`OraculoAutorizacionDiferencial` (id `AUTORIZACION-DIFERENCIAL`, momento `SUITE`, CWE-862)
compara las operaciones observadas en `Contexto.trazasSuite()` con `Proyecto.politica()`.
Una operación se identifica por el título de una `VENTANA_ABIERTA` o por el objetivo `texto=...`
de un `PULSAR` que produce SQL o Firestore sensible en ese mismo paso, antes de `PASO_FIN`
o de la siguiente acción. Compara nombres completos sin distinguir mayúsculas y omitiendo espacios
exteriores; los nombres de rol conservan su escritura exacta. Usa el rol del evento solo si
`sesionActiva=true`; el rol previsto del caso no acredita una sesión.

Una operación reservada a otro rol produce un hallazgo probable; SQL con filas afectadas enteras
positivas en el mismo paso lo confirma. Ignora errores de backend y no vincula escrituras de pasos
posteriores con una ventana anterior. F2 se detecta como probable en N3 al abrir «Eliminar persona»;
A1 admin no lo activa. Un clic terminado en `OK` o un diálogo de denegación no acreditan acceso.
Con política parcial, omite operaciones no declaradas. Sin política, informa candidatos `INFO`
cuando la misma operación aparece en contextos distintos, sin inferir jerarquías ni declarar fallos.
Los candidatos pueden incluir ventanas públicas: requieren revisión y una política explícita.
El evaluador existente devuelve `CUMPLE` si solo hay hallazgos `INFO`; ese resultado no acredita
la autorización de las operaciones cuando falta la política.
La evidencia contiene secuencias del caso identificado en la explicación; no copia consultas ni
valores. Solo considera trazas de casos de la suite y eventos cuyo caso coincide con su traza.
El núcleo debe invocar la evaluación de suite y aportar contextos correctos; el oráculo no
instrumenta la aplicación ni resuelve equivalencias entre títulos y clases de ventanas.

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

1. Oráculos de rendimiento, robustez y accesibilidad de la #47.

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
