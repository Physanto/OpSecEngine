# Demo de entradas de prueba desde YAML

Una plantilla de recorrido se ejecuta con cada texto de `caso.extensiones.payloads`.
El motor sustituye `{payload}` literalmente en los valores de los pasos `llenar`,
conserva los demás pasos y abre una aplicación nueva por entrada. Cada ejecución
aparece en el informe como `LOGIN-P1`, `LOGIN-P2` o `LOGIN-P3`, con su propia traza,
veredicto y captura. Las ventanas se ven durante la presentación.

Desde la raíz del repositorio, con el jar de consola construido:

```sh
java -jar opsec-cli/target/opsec-0.1.0-SNAPSHOT.jar ejecutar demo/payloads/proyecto.yaml -f html,json --resultados demo/payloads/resultados/vulnerable
java -jar opsec-cli/target/opsec-0.1.0-SNAPSHOT.jar ejecutar demo/payloads/proyecto-corregido.yaml -f html,json --resultados demo/payloads/resultados/corregido
```

Los informes quedan en `resultados/vulnerable/entradas/informe.html` y
`resultados/corregido/entradas/informe.html`, relativos a esta carpeta.
La vulnerable desactiva F6 para aislar la exposición de detalles técnicos (CWE-209).
Las dos entradas con comilla deben producir el hallazgo `TRAZA-EN-DIALOGO` y código 3;
la entrada normal no debe revelar una traza. La corregida debe terminar con código 0.
Estos resultados requieren comprobarse con pantalla antes de la presentación.

Esto ejecuta valores explícitos elegidos por el usuario; no selecciona automáticamente
los 39 payloads de la biblioteca. `ataques: [ID]` sigue sin estar conectado al catálogo.
No genera canarios ni confirma inyección SQL mediante eventos JDBC. El veredicto
CUMPLE solo significa que los oráculos aplicados no detectaron fallos en lo observado.

Para otra aplicación Swing, cambie el JAR en proyecto.yaml y adapte los pasos y etiquetas.
No necesita su código fuente. Puede usar cadenas vacías, Unicode y otros textos en la lista.
No mezcle esta lista con `ataques` ni expanda los casos referenciados por enumeracionUsuarios.
