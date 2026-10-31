## Qué hace

<!-- Resumen breve del cambio y del módulo que toca. -->

Cierra #

## Cómo probarlo

<!-- Comandos o pasos para comprobar el cambio. -->

```sh
./mvnw -B verify
```

## Contratos

- [ ] Toca contratos: **no**
- [ ] Toca contratos: **sí** — solicitud aprobada: <!-- enlace al issue de cambio de contrato -->

## Definición de Terminado

- [ ] El PR apunta a `develop` (o a `main` solo desde `release/*` o `hotfix/*`).
- [ ] Tiene pruebas unitarias; si agrega un oráculo, incluye al menos una traza que lo activa y una que no.
- [ ] `./mvnw -B verify` pasa y la CI está en verde, incluidos el formato y las reglas de dependencia.
- [ ] No modifica archivos protegidos sin una solicitud de cambio de contrato aprobada.
- [ ] El `README.md` del módulo está actualizado si cambió su uso.
- [ ] Javadoc en español en todo lo público o protegido, e identificadores en ASCII.
- [ ] Los commits siguen el formato `tipo: descripción`.
- [ ] La tarea está en *Hecho* en el tablero (después de la fusión).
