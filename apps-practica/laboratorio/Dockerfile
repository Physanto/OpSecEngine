# syntax=docker/dockerfile:1
# Misma versión de Java para construir y ejecutar el laboratorio.
ARG IMAGEN_JDK=eclipse-temurin:21.0.13_9-jdk-jammy

FROM ${IMAGEN_JDK} AS compilacion

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl unzip \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /fuentes
COPY . .
# El reactor incluye el agente y la app de práctica, dependencias de la demo.
# Las pruebas con pantalla se ejecutan aparte; esta etapa solo empaqueta.
RUN chmod +x mvnw \
    && ./mvnw -B -ntp -pl opsec-cli -am package -DskipTests

FROM ${IMAGEN_JDK} AS laboratorio

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        tini xvfb xauth libxrender1 libxtst6 libxi6 libfontconfig1 libfreetype6 fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --user-group --create-home --uid 10001 opsec \
    && mkdir /resultados \
    && chown opsec:opsec /resultados

ENV LANG=C.UTF-8 LC_ALL=C.UTF-8 TZ=Etc/UTC
WORKDIR /opt/opsec

COPY --from=compilacion /fuentes/opsec-cli/target/opsec-[0-9]*.jar ./opsec.jar
COPY --from=compilacion /fuentes/apps-practica/clientes-vulnerable/target/clientes-vulnerable.jar ./apps-practica/clientes-vulnerable/target/clientes-vulnerable.jar
COPY --from=compilacion /fuentes/demo/ ./demo/

USER opsec
VOLUME ["/resultados"]

# Tini gestiona señales y procesos hijos: xvfb-run no se ejecuta como PID 1.
# Xvfb aporta una pantalla real para AWT: no se usa java.awt.headless=true.
# Los argumentos de docker run se pasan a la CLI y se conserva su código de salida.
ENTRYPOINT ["/usr/bin/tini", "--", "xvfb-run", "--auto-servernum", "--server-args=-screen 0 1280x800x24 -nolisten tcp", "java", "-jar", "/opt/opsec/opsec.jar"]
CMD ["ejecutar", "/opt/opsec/demo/proyecto.yaml", "--resultados", "/resultados/vulnerable"]
