# opsec-soporte-pruebas

Dobles de prueba compartidos (`AgenteFalso`, `MotorFalso`) y datos de ejemplo (trazas, mapas y resultados) para que cada módulo se pruebe sin depender del trabajo de los demás. Los otros módulos lo usan solo con `<scope>test</scope>`.

- **Dueño:** líder (L). Es un contrato: los cambios se piden con una solicitud de cambio de contrato.
- **Paquete raíz:** `io.github.physanto.opsecengine.soporte`

## Contenido

| Clase | Para qué sirve |
|---|---|
| `AgenteFalso` | Hace de agente con el protocolo real: envía `HOLA` y responde según un guion (`cuandoPaso(...).emitir(...).responder(...)`), un árbol fijo y una captura PNG generada en memoria. Registra los mensajes recibidos. |
| `MotorFalso` | Hace de motor: abre un puerto libre en `127.0.0.1`, valida el `HOLA` y ofrece `enviarPaso`, `pedirArbol`, `pedirCaptura` y `cerrar`, además de los eventos recibidos. Sirve para probar el agente sin el núcleo. |
| `ExcepcionErrorRemoto` | La lanza `MotorFalso` cuando el agente responde `ERROR`. |
| `CargadorTrazas` y `TrazaEjemplo` | Leen trazas JSONL (`contratos/esquemas/traza-evento.schema.json`) como objetos `Traza`; incluyen las cinco trazas de ejemplo. |
| `DatosEjemplo` | Carga el mapa de la app de clientes y un resultado de suite con un caso de cada estado. |
| `AplicacionDePrueba` | Aplicación sin interfaz gráfica que escribe una línea y espera; sirve para lanzar procesos con el agente en entornos headless. |

Los datos están en `src/main/resources/io/github/physanto/opsecengine/soporte/`; su `README.md` explica qué debe concluir un oráculo correcto con cada traza.

Cada clase tiene en su Javadoc un ejemplo de uso. Para usarlas en otro módulo:

```xml
<dependency>
    <groupId>${project.groupId}</groupId>
    <artifactId>opsec-soporte-pruebas</artifactId>
    <scope>test</scope>
</dependency>
```

## Dependencias permitidas

- `opsec-dominio`.
- `opsec-protocolo`.
- Jackson (`jackson-databind` y `jackson-datatype-jsr310`).

## Pruebas

Desde la raíz del repositorio:

```sh
./mvnw -B -pl opsec-soporte-pruebas -am verify
```

En Windows:

```bat
mvnw.cmd -B -pl opsec-soporte-pruebas -am verify
```
