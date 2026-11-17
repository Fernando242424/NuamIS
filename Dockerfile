# nuam · imagen para Render (compila con JDK y ejecuta con JRE + cliente de PostgreSQL para los respaldos)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY lib lib
COPY src src
RUN mkdir out && javac -encoding UTF-8 -d out -cp lib/postgresql.jar src/main/java/cl/nuam/*.java

FROM eclipse-temurin:21-jre
# Versión del cliente: igual (o mayor) que la de PostgreSQL en Neon (se ve en el panel del proyecto).
ARG PGV=17
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates lsb-release \
 && install -d /usr/share/postgresql-common/pgdg \
 && curl -fsSo /usr/share/postgresql-common/pgdg/apt.postgresql.org.asc https://www.postgresql.org/media/keys/ACCC4CF8.asc \
 && echo "deb [signed-by=/usr/share/postgresql-common/pgdg/apt.postgresql.org.asc] https://apt.postgresql.org/pub/repos/apt $(lsb_release -cs)-pgdg main" > /etc/apt/sources.list.d/pgdg.list \
 && apt-get update && apt-get install -y --no-install-recommends postgresql-client-${PGV} \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/out out
COPY lib lib
COPY sql sql
COPY web web
# Render termina el HTTPS por fuera: la app habla HTTP en el puerto que Render le da (variable PORT).
ENV NUAM_TLS=0 NUAM_SECURE_COOKIES=1
EXPOSE 10000
CMD ["java", "-Xmx320m", "-cp", "out:lib/postgresql.jar", "cl.nuam.Main"]
