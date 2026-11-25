#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build
javac --release 17 -d build src/main/java/ru/balandin/taskgraph/*.java src/test/java/ru/balandin/taskgraph/*.java
if [[ "${1:-test}" == "server" ]]; then
  java -cp build ru.balandin.taskgraph.TaskGraphServer
else
  java -cp build ru.balandin.taskgraph.TaskGraphTest
fi
