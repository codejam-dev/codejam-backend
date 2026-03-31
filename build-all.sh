#!/bin/bash
set -e

ROOT_DIR=$(cd "$(dirname "$0")" && pwd)
cd "$ROOT_DIR"

if [[ "${JAVA_HOME:-}" == "" ]] && [[ -d "/opt/homebrew/opt/openjdk@21" ]]; then
  export JAVA_HOME="/opt/homebrew/opt/openjdk@21"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

echo "Building CodeJam monolith (codejam-commons + codejam-app)..."
echo "Java: $(java -version 2>&1 | head -n 1)"
echo ""

mvn -q clean install -DskipTests

echo ""
echo "Done. Fat JAR: codejam-app/target/codejam-app-*.jar"
