#!/usr/bin/env bash
# Compila y ejecuta la aplicación (requiere JDK 17+ y PostgreSQL con BD/usuario "nuam").
set -e
cd "$(dirname "$0")"
mkdir -p out && javac -encoding UTF-8 -cp lib/postgresql.jar -d out $(find src/main/java -name '*.java')
exec java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "out:lib/postgresql.jar" cl.nuam.Main
