# Demo: contraseña en la consola (CWE-532)

Esta demo muestra OpSecEngine de punta a punta sobre la app de práctica `apps-practica/clientes-vulnerable`:

1. El motor lanza la app en su propia JVM con el agente.
2. El agente pulsa **Avanzar** y **Nube**, llena **Usuario** y **Contraseña** y pulsa **Iniciar Sesion**, sin tocar el mouse.
3. El agente envía al motor lo que pasa: líneas de consola, ventanas que se abren y se cierran.
4. El oráculo `CONTRASENA-EN-CONSOLA` revisa la traza y decide el veredicto. Nadie escribe el resultado esperado.

La app tiene el fallo sembrado F6: al iniciar sesión escribe la contraseña en la consola. Con la misma suite:

| Proyecto | App | Veredicto | Código de salida |
|---|---|---|---|
| `proyecto.yaml` | Todos los fallos activos | `NO_CUMPLE` (CWE-532) | 3 |
| `proyecto-corregido.yaml` | F6 corregido (`-Dpractica.corregir=F6`) | `CUMPLE` | 0 |

## Requisitos

- JDK 21 o superior.
- Una pantalla: la app abre sus ventanas mientras el agente la maneja.

## Ejecutar

Desde la raíz del repositorio, primero construya todo (incluye la CLI y la app de práctica):

**Linux y macOS**

```sh
./mvnw -B package -DskipTests
java -jar opsec-cli/target/opsec-<versión>.jar ejecutar demo/proyecto.yaml
java -jar opsec-cli/target/opsec-<versión>.jar ejecutar demo/proyecto-corregido.yaml
```

**Windows**

```bat
mvnw.cmd -B package -DskipTests
java -jar opsec-cli\target\opsec-<versión>.jar ejecutar demo\proyecto.yaml
java -jar opsec-cli\target\opsec-<versión>.jar ejecutar demo\proyecto-corregido.yaml
```

Salida esperada con la app vulnerable:

```
Suite login (1 caso); resultados en .../demo/resultados/<fecha>/login
  LOGIN-ADMIN      NO_CUMPLE       3.0 s
      - CONTRASENA-EN-CONSOLA (CWE-532): Contraseña de prueba en la consola
        La aplicación escribió la contraseña del rol admin en la salida estándar. ...

Resumen: 0 cumplen, 1 no cumplen, 0 no concluyentes
```

Los resultados quedan en `demo/resultados/<fecha>/login/` (ignorada por Git): `resultado.json` con la traza completa y los hallazgos, y `app-salida.log` y `app-errores.log` con la consola de la app.

## Archivos

| Archivo | Qué es |
|---|---|
| `proyecto.yaml` | La app de práctica con todos los fallos activos y sus roles de prueba. |
| `proyecto-corregido.yaml` | La misma app con F6 corregido. |
| `suites/login.yaml` | Un caso: el administrador inicia sesión. Usa el modo `SEMANTICO`, porque el agente todavía no implementa `EVENTOS`. |

La prueba de integración `opsec-cli/src/test/java/.../DemoIT.java` ejecuta esta misma demo en cada `./mvnw verify` y en la CI (se omite sin pantalla).

## Qué todavía no hace

- El informe es solo JSON; el informe HTML llega con el reportero de `opsec-informes`.
- Las ventanas se ven en pantalla; el modo oculto llega con el agente completo.
- El caso escribe las credenciales a mano; el inicio de sesión por rol (`rol: admin` en el caso) llega con el núcleo de la v0.1.
