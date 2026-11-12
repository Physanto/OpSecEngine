# opsec-informes

Reporteros de resultados: HTML con capturas, JSON, JUnit XML y SARIF. Cada uno implementa `Reportero` y el núcleo los descubre con `ServiceLoader`.

- **Dueño:** P3 (salidas).
- **Paquete raíz:** `io.github.physanto.opsecengine.informes`

## Ejemplo de referencia

`ReporteroJson` (formato `json`) escribe `resultado.json` en la carpeta de resultados, con sangría y en UTF-8, en el mismo formato de `resultados/ejemplo.json` de `opsec-soporte-pruebas`. Se registra en `src/main/resources/META-INF/services/io.github.physanto.opsecengine.dominio.extension.Reportero`.

Su prueba, `ReporteroJsonTest`, escribe el resultado de ejemplo (`DatosEjemplo.resultadoSuite()`) y comprueba que el archivo se vuelve a leer igual.

## Reportero HTML

`ReporteroHtml` (formato `html`) escribe `informe.html` en la carpeta de resultados: un solo archivo autocontenido, sin JavaScript ni recursos externos (lleva una política de seguridad de contenido que lo impide), con modo claro y oscuro según el navegador. Todo texto que viene de la traza se escapa.

Contenido:

- Resumen de la suite (casos que cumplen, no cumplen, no concluyentes e inestables) y manifiesto de la ejecución.
- Por cada caso: veredicto, duración, intentos, motivo (si es no concluyente), hallazgos con su oráculo, CWE y confianza, y la línea de tiempo de la traza. Los eventos que respaldan un hallazgo se resaltan y se enlazan desde él.
- Capturas: los eventos `CAPTURA` se incrustan en el HTML si el archivo (PNG, hasta 8 MB) existe dentro de la carpeta de resultados; si no, se muestra un aviso. Nunca se leen archivos fuera de esa carpeta.
- Un informe sin fallos dice «No se encontraron fallos en lo probado», nunca que la aplicación sea segura.

**Filtros.** El informe trae filtros por veredicto, oráculo y CWE, hechos solo con CSS: cada opción es un botón de opción y una regla `body:has(#opción:checked)` oculta los casos que no la cumplen. Así el informe sigue sin JavaScript. Los filtros se combinan (se muestra un caso si cumple todos los activos) y también alcanzan a los hallazgos de suite, salvo el de veredicto. Las opciones de oráculo y CWE se identifican por su posición en una lista ordenada, no por su texto, para que nada de la traza llegue a un selector CSS. Requieren un navegador con soporte de `:has()` (los actuales desde 2023); en uno antiguo los controles no hacen nada y se ve el informe completo.

**Comparación entre ejecuciones.** `ComparadorResultados.comparar(anterior, actual)` empareja los casos por id y los clasifica: *empeoró* o *mejoró* según la gravedad del veredicto (`CUMPLE` < `NO_CONCLUYENTE` < `NO_CUMPLE`), *nuevo*, *eliminado* o *igual*, con los hallazgos resueltos y nuevos de cada caso (un hallazgo se identifica por su oráculo y su título). `ComparacionHtml.escribir(anterior, actual, carpeta)` escribe `comparacion.html` con los casos ordenados por importancia (primero los que empeoraron), los hallazgos de suite y los dos manifiestos, y avisa si cambió la aplicación, la suite o el motor. No es un `Reportero`, porque ese contrato recibe un solo resultado: la presentación (por ejemplo el futuro comando `opsec informe`) debe llamarla con dos resultados ya leídos.

Pendiente de este módulo: explicaciones de CWE a partir de `cwe.yaml` (#57, necesita acceso al catálogo, que está en `contratos/`) y gráficos de rendimiento (#59, necesita los eventos de tiempo del agente).

## Reportero JUnit XML

`ReporteroJunitXml` (formato `junit`) escribe `junit.xml`, el formato que leen las herramientas de integración continua (visor de pruebas de GitHub Actions, Jenkins, GitLab).

| En OpSecEngine | En JUnit XML |
|---|---|
| La suite | `<testsuite>` (y `<testsuites>` como raíz), con el manifiesto como `<properties>` |
| Un caso | `<testcase classname="<suite>" name="<id del caso>">` |
| Caso `NO_CUMPLE` | Un `<failure message="<título>" type="<CWE u oráculo>">` por cada hallazgo, con oráculo, confianza, explicación y eventos de evidencia |
| Caso `NO_CONCLUYENTE` | `<skipped message="<motivo>"/>`: no se pudo decidir, así que no cuenta como pasado ni como fallido |
| Caso `CUMPLE` | Sin elementos; sus hallazgos informativos y su marca de inestable van en `<system-out>` |
| Hallazgo de suite | Un `<testcase name="(suite) <oráculo>">` aparte, con su `<failure>` |

Se escribe en UTF-8 y se descartan los caracteres que XML 1.0 no admite (por ejemplo, el nulo de un payload de ataque). Las pruebas leen el archivo con un analizador XML estándar; no se ha probado contra el visor de GitHub Actions.

## Reportero SARIF

`ReporteroSarif` (formato `sarif`) escribe `resultado.sarif` en SARIF 2.1.0, el formato estándar de resultados de análisis (GitHub code scanning, Visual Studio Code).

- Cada **oráculo** con algún hallazgo es una regla (`tool.driver.rules`), con el título de su primer hallazgo y, si lo tiene, su CWE como etiqueta `external/cwe/cwe-<n>`.
- Cada **hallazgo** es un resultado con su regla, un `level` según la confianza (`CONFIRMADO` es `error`, `PROBABLE` es `warning`, `INFO` es `note`), la explicación como mensaje y una huella estable (`partialFingerprints`) para que el sistema receptor reconozca el mismo hallazgo entre ejecuciones.
- **Ubicación:** una aplicación de escritorio no tiene archivos de código que señalar, así que la ubicación física es `suites/<suite>.yaml` (línea 1, por convención del proyecto) y el caso va como ubicación lógica. No apunta a una línea del código de la aplicación.
- Un caso **no concluyente** no es un resultado: se informa como notificación de la ejecución (`toolExecutionNotifications`) con su motivo.

Las pruebas comprueban las propiedades obligatorias y los valores permitidos de SARIF 2.1.0 para lo que se escribe. **No** se valida contra el esquema JSON oficial, porque ese archivo no está en el repositorio, y no se ha subido a GitHub code scanning.

## Dependencias permitidas

- `opsec-dominio`.
- Jackson (`jackson-databind` y `jackson-datatype-jsr310`).
- Solo para pruebas: `opsec-soporte-pruebas`.

No puede depender del núcleo, del agente ni del protocolo.

## Pruebas

Desde la raíz del repositorio:

```sh
./mvnw -B -pl opsec-informes -am verify
```

En Windows:

```bat
mvnw.cmd -B -pl opsec-informes -am verify
```
