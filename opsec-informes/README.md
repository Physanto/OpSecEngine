# opsec-informes

Reporteros de resultados: HTML con capturas, JSON, JUnit XML y SARIF. Cada uno implementa `Reportero` y el núcleo los descubre con `ServiceLoader`.

- **Dueño:** P3.
- **Paquete raíz:** `io.github.physanto.opsecengine.informes`

## Ejemplo de referencia

`ReporteroJson` (formato `json`) escribe `resultado.json` en la carpeta de resultados, con sangría y en UTF-8, en el mismo formato de `resultados/ejemplo.json` de `opsec-soporte-pruebas`. Se registra en `src/main/resources/META-INF/services/io.github.physanto.opsecengine.dominio.extension.Reportero`.

Su prueba, `ReporteroJsonTest`, escribe el resultado de ejemplo (`DatosEjemplo.resultadoSuite()`) y comprueba que el archivo se vuelve a leer igual.

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
