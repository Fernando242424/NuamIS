FROM eclipse-temurin:17-jdk
RUN apt-get update && apt-get install -y --no-install-recommends postgresql-client && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY . .
RUN mkdir -p out && javac -encoding UTF-8 -cp lib/postgresql.jar -d out $(find src/main/java -name '*.java')
ENV NUAM_TLS=0 NUAM_SECURE_COOKIES=1 NUAM_DEMO=1
CMD ["java","-Dfile.encoding=UTF-8","-Dstdout.encoding=UTF-8","-cp","out:lib/postgresql.jar","cl.nuam.Main"]
