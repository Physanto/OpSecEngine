# OpSecEngine

OpSecEngine (Opcode Security Engine): motor libre de pruebas GUI y de seguridad para aplicaciones Java Swing.

> Proyecto en construcción.

## Calidad del código

El build verifica el formato (Spotless con google-java-format, estilo AOSP), el estilo y el Javadoc (Checkstyle, configurado en `build-tools/checkstyle.xml`), las versiones de Java y Maven y la convergencia de dependencias (Maven Enforcer), y las reglas de arquitectura entre módulos (ArchUnit, en `opsec-verificacion`). Se necesita Java 17 o superior.

Aplicar el formato automático:

```sh
./mvnw spotless:apply
```

Compilar y ejecutar todas las verificaciones:

```sh
./mvnw -B verify
```

En Windows:

```bat
mvnw.cmd spotless:apply
mvnw.cmd -B verify
```
