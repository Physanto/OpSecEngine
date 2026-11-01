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
| `CONTRASENA-EN-CONSOLA` | `OraculoContrasenaEnConsola` | `EVENTO` | CWE-532 | Una línea `CONSOLA` que contiene una contraseña de prueba: la de un rol del proyecto o la escrita con `LLENAR` en un campo cuyo localizador menciona contraseña, password, clave o pass. Ignora contraseñas de menos de 4 caracteres y nunca copia la contraseña en el hallazgo. Lo activa el fallo F6 de la app de práctica. |

### Pendiente para el dueño del módulo (P2)

Ordenado según `PLAN_DE_TRABAJO.md` (sprints 1 a 6):

1. Evaluador por evento, caso y suite (hoy `EvaluacionService` del núcleo entrega la traza completa al terminar el caso) y cargador de reglas YAML de `oraculos/`.
2. Oráculos implícitos que faltan: cierre inesperado de la app y `TRAZA-EN-DIALOGO` (los eventos `DIALOGO` ya llegan del agente).
3. `CONTRASENA-EN-CONSOLA`: buscar también los payloads con `{contrasena}` y los canarios cuando lleguen los ataques (v0.2).
4. Oráculos de seguridad del catálogo (v0.3): `SIN-SESION`, `AUTORIZACION-DIFERENCIAL`, `SQLI-CANARIO`, `ENUMERACION-USUARIOS`, `SIN-BLOQUEO`, `CREDENCIALES-DEBILES`, `CONTRASENA-EN-CAMPO-VISIBLE`, entre otros.

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
