# Preparación del banco ampliado (issue #70)

**Estado: parcial.** Se añaden métricas reproducibles de cobertura GUI y el
registro de procedencia/permisos. Sigue habiendo **una sola aplicación**, la de
práctica del repositorio. No se han recibido ni incorporado proyectos externos
de estudiantes ni se supone autorización por disponer de su código.

## Registro y permisos

`banco-ampliado.json` ejecuta los cuatro controles actuales de F6/F8 y referencia
`inventario.json`. Por cada aplicación el inventario exige:

- `id`: coincide con `aplicacion` de los escenarios del banco.
- `origen`: repositorio o procedencia de los artefactos.
- `permiso`: referencia al consentimiento o a la licencia que autoriza su uso.
  El programa comprueba que se declare; no verifica por sí mismo su validez.
- `alcance`: qué GUI se incluye o excluye de los denominadores.
- `ventanas`: títulos únicos inventariados.
- `acciones`: claves únicas `ACCION|objetivo`, con el localizador canónico que
  figura en `PASO_INICIO`; no incluye el valor escrito ni contraseñas.

Antes de añadir un proyecto de estudiantes se necesitan sus artefactos y el
permiso de sus autores, acordar si se pueden redistribuir y preparar su proyecto,
sus suites y una referencia de fallos independiente del resultado del motor.
Se registra después su procedencia y permiso, y se añaden escenarios por versión.
No se copia el consentimiento con datos personales a un repositorio público.

Para incluir artefactos autorizados en Docker habrá que agregar rutas explícitas
al Dockerfile y a su filtro; actualmente la imagen contiene solo la app de práctica.

## Métricas

Se conservan todas las métricas del [banco original](BANCO.md): detecciones,
falsos positivos, no detectados, no concluyentes y tiempos de proceso/casos.
El inventario añade, **por aplicación y versión**, sin sumar repeticiones:

| Métrica | Definición |
|---|---|
| Ventanas ejercitadas | Títulos inventariados observados en `VENTANA_ABIERTA` |
| Cobertura de ventanas | Ventanas ejercitadas / ventanas inventariadas × 100 |
| Acciones ejercitadas | Pares acción/localizador inventariados con `PASO_INICIO` y su `PASO_FIN` en `OK` dentro del mismo caso/paso |
| Cobertura de acciones | Acciones ejercitadas / acciones inventariadas × 100 |

Esperas y capturas no son acciones de interacción para esta métrica. Las acciones
fallidas no cuentan. Los elementos observados fuera del inventario se conservan
en listas separadas y no aumentan el numerador. Sin eventos suficientes, el
porcentaje queda `null` en JSON y vacío en CSV, con estado
`SIN_EVENTOS_SUFICIENTES`. No se convierte ausencia de instrumentación en 0 %.

El inventario actual tiene 14 títulos y 33 claves de acciones de navegación,
formularios y diagnósticos. Es un inventario explícito: excluye mensajes variables
de validación, no distingue roles y no descubre automáticamente toda la GUI.
Títulos o localizadores repetidos en pantallas distintas se cuentan una vez;
para una app que necesite distinguirlos se debe acordar un inventario más preciso.
Los localizadores se comparan literalmente; una estrategia nueva puede necesitar
un inventario actualizado y una ejecución nueva. El JSON guarda los observados
fuera de inventario para detectar estas diferencias.

No es cobertura de código ni cobertura de todos los fallos. `coberturaCodigo`
queda `null` hasta integrar JaCoCo. Una cobertura GUI alta tampoco demuestra la
detección de vulnerabilidades ni el cierre de #67 o #69.

## Ejecutar y reproducir

Desde la raíz, con JDK 21 o superior y pantalla disponible:

```sh
java --source 21 --class-path opsec-cli/target/opsec-0.1.0-SNAPSHOT.jar \
  apps-practica/laboratorio/BancoEvaluacion.java ejecutar \
  apps-practica/laboratorio/banco-ampliado.json \
  --salida demo/resultados/banco-ampliado-001

java --source 21 --class-path opsec-cli/target/opsec-0.1.0-SNAPSHOT.jar \
  apps-practica/laboratorio/BancoEvaluacion.java resumir \
  demo/resultados/banco-ampliado-001
```

El banco copia el inventario en la salida y guarda su SHA-256 en
`ejecucion.json`. `resumir` verifica el inventario, la configuración y los informes;
regenera `tabla.csv/md` y `cobertura.json/csv/md` desde los datos guardados, sin
reabrir la app y conservando los tiempos originales. El banco sin `inventario`
continúa funcionando como antes. Sus códigos de error/regresión/no concluyente
siguen siendo 1/3/4: la cobertura no los convierte en un éxito.

En Git Bash con Docker Desktop en modo Linux:

```sh
docker build -f apps-practica/laboratorio/Dockerfile -t opsec-laboratorio:local .
docker volume create opsec-resultados

MSYS_NO_PATHCONV=1 docker run --rm \
  --mount type=volume,source=opsec-resultados,target=/resultados \
  --entrypoint /usr/bin/tini opsec-laboratorio:local -- \
  xvfb-run --auto-servernum java --source 21 --class-path /opt/opsec/opsec.jar \
  /opt/opsec/apps-practica/laboratorio/BancoEvaluacion.java ejecutar \
  /opt/opsec/apps-practica/laboratorio/banco-ampliado.json \
  --salida /resultados/banco-ampliado-001

MSYS_NO_PATHCONV=1 docker run --rm \
  --mount type=volume,source=opsec-resultados,target=/resultados \
  --entrypoint java opsec-laboratorio:local --source 21 \
  --class-path /opt/opsec/opsec.jar \
  /opt/opsec/apps-practica/laboratorio/BancoEvaluacion.java resumir \
  /resultados/banco-ampliado-001
```

Use otra carpeta para una medición nueva. Reproducir la tabla es una operación
determinista sobre informes guardados; repetir físicamente la app vuelve a medir
los tiempos y no garantiza tiempos idénticos. Los resultados quedan fuera de Git.

## Aceptación pendiente

Verificación observada el 11 de noviembre de 2026:

| Comprobación | Resultado |
|---|---|
| `verify` completo en Windows, JDK 27 | `BUILD SUCCESS`; las 8 pruebas del banco pasan; la aceptación completa de #67 conserva su omisión explícita por dependencias |
| Dos bancos nuevos en Docker, JDK 21/Xvfb | 8 ejecuciones de la app; en cada banco, 2 detecciones esperadas, 0 falsos positivos, 0 no detectados y 0 no concluyentes |
| Cobertura de F6 vulnerable/corregido | 4/14 ventanas (28,57 %) y 5/33 acciones (15,15 %) en ambas corridas |
| Cobertura de F8 vulnerable/corregido | 3/14 ventanas (21,43 %) y 3/33 acciones (9,09 %) en ambas corridas |
| Reproducción de informes guardados | `tabla.csv/md` y `cobertura.json/csv/md` idénticos por SHA-256 antes/después de `resumir` |

Los tiempos de proceso observados por variante van de 4779 a 5865 ms entre
las dos corridas; se guardan las medidas individuales y no se exige igualdad de
tiempos. Dos corridas coincidentes no bastan para estimar una tasa estadística
de inestabilidad ni el esfuerzo humano de un estudio.
La evidencia está en el volumen `opsec-resultados`, carpetas `issue70-a` y
`issue70-b`, y su copia local en `target/issue70/evidencia-linux/`, fuera de Git.
La comprobación Docker anterior usa **solo la app de práctica**; falta repetirla
con las aplicaciones adicionales que exige la issue.

- Incorporar aplicaciones adicionales con permiso y sus escenarios; no se da por
  realizado por añadir únicamente infraestructura de métricas.
- Comprobar los resultados y su reproducción con esas apps en Docker.
- Completar CI, revisión e integración por PR aprobado y el tablero, conforme a
  `CONTRIBUTING.md`. Este cambio no cierra #70.
