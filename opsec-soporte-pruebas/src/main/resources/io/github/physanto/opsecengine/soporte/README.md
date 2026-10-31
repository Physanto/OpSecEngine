# Datos de ejemplo de `opsec-soporte-pruebas`

Datos escritos a mano, realistas, para probar oráculos, informes y el núcleo sin ejecutar la app. Imitan la app de clientes original: ventanas en los paquetes `GUI_Local`, `GUI_Nube`, `GUI_Cliente` y `GUI_Admin`, y una base H2 en memoria (`jdbc:h2:mem:clientes`).

Se cargan desde el código con `CargadorTrazas` (enum `TrazaEjemplo`) y `DatosEjemplo`; no hace falta leer estos archivos a mano.

## Trazas (`trazas/`)

Formato: `contratos/esquemas/traza-evento.schema.json`, un evento por línea, con los campos de `contratos/catalogos/eventos.yaml`. Las secuencias crecen en todo el archivo.

Las contraseñas de los roles son fuertes a propósito, para que `CREDENCIALES-DEBILES` no intervenga, y los campos de contraseña son `JPasswordField`, para que no intervenga `CONTRASENA-EN-CAMPO-VISIBLE`. Como la base es H2 en memoria, tampoco aplica `JDBC-SIN-CIFRADO`.

| Archivo | Caso | Qué pasa | Qué debe concluir un oráculo correcto |
|---|---|---|---|
| `n3-local-sin-login.jsonl` | N3 | Portada → EscogerBD → Local → MenuLocal → Eliminar Persona. Un `DELETE` con `Statement`, el diálogo "Persona eliminada" y `ESTADO` con `existe=false`. Nunca hay sesión. | **NO CUMPLE** por `SIN-SESION` (CWE-306), con confianza CONFIRMADO: el `ESTADO` después del caso muestra que el registro desapareció. Ningún otro oráculo da hallazgos. |
| `a1-admin-elimina.jsonl` | A1 | Login válido de Admin (evento `SESION` con rol `admin`) y luego el mismo borrado desde `MenuAdministrador`. | **CUMPLE**. `SIN-SESION` no aplica porque hay sesión, y la política del proyecto de ejemplo permite a `admin` eliminar personas. |
| `c13-sqli-login.jsonl` | C13 | En el usuario se escribe `' OR '1'='1' -- K7Q2`. El login ejecuta un `SELECT` de tipo `STATEMENT` cuyo texto contiene `K7Q2`, y se abre `MenuAdministrador`. | **NO CUMPLE** por `SQLI-CANARIO` (CWE-89): el canario `K7Q2` del valor escrito llegó al SQL concatenado. |
| `c01-c02-mensajes.jsonl` | C01 y C02 | Dos casos en el mismo archivo. C01: usuario inexistente, diálogo "El usuario no existe". C02: Admin con contraseña incorrecta, diálogo "Contraseña incorrecta". | Cada caso por separado: **CUMPLE**. A nivel de suite: `ENUMERACION-USUARIOS` (CWE-204) da un hallazgo, porque los mensajes de C01 y C02 son distintos. |
| `caso-limpio.jsonl` | LIMPIO | Cliente inicia sesión, abre Buscar Persona y busca el id 3 con `PreparedStatement`. | **CUMPLE**. Ningún oráculo debe dar hallazgos; sirve para detectar falsos positivos. |

## Mapa (`mapas/clientes.json`)

Un `MapaApp` de la app de clientes con cuatro contextos:

- `sin-sesion`: Portada → EscogerBD → MenuLocal → EliminarPersona, y EscogerBD → InicioSesion.
- `rol:cliente`: login hasta MenuCliente → BuscarPersona.
- `rol:admin`: login hasta MenuAdministrador → BuscarPersona y EliminarPersonaAdministrador.
- `entrada:GUI_Admin.EliminarPersonaAdministrador`: la ventana abierta directamente por su `main`, que elimina sin login.

Un oráculo de autorización diferencial debería señalar que "Eliminar" se alcanza en `sin-sesion` y en la entrada directa, no solo en `rol:admin`.

## Resultado (`resultados/ejemplo.json`)

Un `ResultadoSuite` con un caso de cada estado, para diseñar informes:

- N3: **NO_CUMPLE**, con un hallazgo `SIN-SESION` CONFIRMADO y su evidencia (secuencias de la traza).
- A1: **CUMPLE**, inestable: falló el primer intento y pasó el segundo.
- C15: **NO_CONCLUYENTE**, porque un paso terminó con `TIEMPO_AGOTADO`.

Las trazas incluidas en el resultado son cortas: solo los eventos que hacen falta para el informe.
