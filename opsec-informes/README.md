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

Pendiente de este reportero: filtros y comparación entre ejecuciones, explicaciones de CWE a partir de `cwe.yaml` y gráficos de rendimiento (ver los issues del módulo).

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
