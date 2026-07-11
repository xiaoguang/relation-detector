#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RELATION_ROOT="$ROOT/relation-detector"

cd "$ROOT"

if [[ "${RELATION_DETECTOR_SKIP_PACKAGE:-false}" != "true" ]]; then
  mvn -q -pl relation-detector/core,relation-detector/adaptor-mysql,relation-detector/adaptor-postgres,relation-detector/adaptor-oracle,relation-detector/adaptor-sqlserver,relation-detector/cli -am -Dmaven.test.skip=true package
  "$RELATION_ROOT/scripts/check-no-jls-bad-classes.sh" "$ROOT"
fi

required_jars=(
  "$RELATION_ROOT/contracts/target/relation-detector-contracts-0.1.0-SNAPSHOT.jar"
  "$RELATION_ROOT/grammar/mysql-v5_7/target/relation-detector-grammar-mysql-v5_7-0.1.0-SNAPSHOT.jar"
  "$RELATION_ROOT/grammar/mysql-v8_0/target/relation-detector-grammar-mysql-v8_0-0.1.0-SNAPSHOT.jar"
  "$RELATION_ROOT/grammar/postgres-v16/target/relation-detector-grammar-postgres-v16-0.1.0-SNAPSHOT.jar"
  "$RELATION_ROOT/grammar/postgres-v17/target/relation-detector-grammar-postgres-v17-0.1.0-SNAPSHOT.jar"
  "$RELATION_ROOT/grammar/postgres-v18/target/relation-detector-grammar-postgres-v18-0.1.0-SNAPSHOT.jar"
  "$RELATION_ROOT/grammar/postgres-routine/target/relation-detector-grammar-postgres-routine-0.1.0-SNAPSHOT.jar"
  "$RELATION_ROOT/grammar/oracle-v12c/target/relation-detector-grammar-oracle-v12c-0.1.0-SNAPSHOT.jar"
  "$RELATION_ROOT/grammar/oracle-v19c/target/relation-detector-grammar-oracle-v19c-0.1.0-SNAPSHOT.jar"
  "$RELATION_ROOT/grammar/oracle-v21c/target/relation-detector-grammar-oracle-v21c-0.1.0-SNAPSHOT.jar"
  "$RELATION_ROOT/grammar/oracle-v26ai/target/relation-detector-grammar-oracle-v26ai-0.1.0-SNAPSHOT.jar"
  "$RELATION_ROOT/grammar/sqlserver-v2016/target/relation-detector-grammar-sqlserver-v2016-0.1.0-SNAPSHOT.jar"
  "$RELATION_ROOT/grammar/sqlserver-v2017/target/relation-detector-grammar-sqlserver-v2017-0.1.0-SNAPSHOT.jar"
  "$RELATION_ROOT/grammar/sqlserver-v2019/target/relation-detector-grammar-sqlserver-v2019-0.1.0-SNAPSHOT.jar"
  "$RELATION_ROOT/grammar/sqlserver-v2022/target/relation-detector-grammar-sqlserver-v2022-0.1.0-SNAPSHOT.jar"
  "$RELATION_ROOT/grammar/sqlserver-v2025/target/relation-detector-grammar-sqlserver-v2025-0.1.0-SNAPSHOT.jar"
  "$RELATION_ROOT/core/target/relation-detector-core-0.1.0-SNAPSHOT.jar"
  "$RELATION_ROOT/adaptor-mysql/target/relation-detector-adaptor-mysql-0.1.0-SNAPSHOT.jar"
  "$RELATION_ROOT/adaptor-postgres/target/relation-detector-adaptor-postgres-0.1.0-SNAPSHOT.jar"
  "$RELATION_ROOT/adaptor-oracle/target/relation-detector-adaptor-oracle-0.1.0-SNAPSHOT.jar"
  "$RELATION_ROOT/adaptor-sqlserver/target/relation-detector-adaptor-sqlserver-0.1.0-SNAPSHOT.jar"
  "$RELATION_ROOT/cli/target/relation-detector-cli-0.1.0-SNAPSHOT.jar"
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
    echo "Run: mvn -pl relation-detector/cli -am -Dmaven.test.skip=true package" >&2
    exit 1
  fi
done

classpath="$(IFS=:; echo "${required_jars[*]}")"
exec java -cp "$classpath" com.relationdetector.cli.Main "$@"
