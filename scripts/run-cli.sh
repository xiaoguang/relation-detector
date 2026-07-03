#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$ROOT"

if [[ "${RELATION_DETECTOR_SKIP_PACKAGE:-false}" != "true" ]]; then
  mvn -q -pl core,adaptor-mysql,adaptor-postgres,adaptor-oracle,adaptor-sqlserver,cli -am -Dmaven.test.skip=true package
  "$ROOT/scripts/check-no-jls-bad-classes.sh" "$ROOT"
fi

required_jars=(
  "$ROOT/contracts/target/relation-detector-contracts-0.1.0-SNAPSHOT.jar"
  "$ROOT/core/target/relation-detector-core-0.1.0-SNAPSHOT.jar"
  "$ROOT/adaptor-mysql/target/relation-detector-adaptor-mysql-0.1.0-SNAPSHOT.jar"
  "$ROOT/adaptor-postgres/target/relation-detector-adaptor-postgres-0.1.0-SNAPSHOT.jar"
  "$ROOT/adaptor-oracle/target/relation-detector-adaptor-oracle-0.1.0-SNAPSHOT.jar"
  "$ROOT/adaptor-sqlserver/target/relation-detector-adaptor-sqlserver-0.1.0-SNAPSHOT.jar"
  "$ROOT/cli/target/relation-detector-cli-0.1.0-SNAPSHOT.jar"
  "$HOME/.m2/repository/org/antlr/antlr4-runtime/4.13.2/antlr4-runtime-4.13.2.jar"
  "$HOME/.m2/repository/com/fasterxml/jackson/core/jackson-annotations/2.17.2/jackson-annotations-2.17.2.jar"
  "$HOME/.m2/repository/com/fasterxml/jackson/core/jackson-core/2.17.2/jackson-core-2.17.2.jar"
  "$HOME/.m2/repository/com/fasterxml/jackson/core/jackson-databind/2.17.2/jackson-databind-2.17.2.jar"
  "$HOME/.m2/repository/com/fasterxml/jackson/dataformat/jackson-dataformat-yaml/2.17.2/jackson-dataformat-yaml-2.17.2.jar"
  "$HOME/.m2/repository/org/yaml/snakeyaml/2.2/snakeyaml-2.2.jar"
  "$HOME/.m2/repository/com/mysql/mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar"
  "$HOME/.m2/repository/org/postgresql/postgresql/42.7.4/postgresql-42.7.4.jar"
)

for jar in "${required_jars[@]}"; do
  if [[ ! -f "$jar" ]]; then
    echo "Missing runtime jar: $jar" >&2
    echo "Run: mvn -pl cli -am -Dmaven.test.skip=true package" >&2
    exit 1
  fi
done

classpath="$(IFS=:; echo "${required_jars[*]}")"
exec java -cp "$classpath" com.relationdetector.cli.Main "$@"
