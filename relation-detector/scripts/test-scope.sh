#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCOPE="${1:-}"

if [[ -z "$SCOPE" ]]; then
  echo "Usage: $0 <core|mysql|postgres|oracle|sqlserver|assets>[,...]" >&2
  exit 2
fi

run_correctness() {
  local profile="$1"
  mvn -pl relation-detector/cli -am \
    -Dtest=CorrectnessFixtureRunnerTest \
    -DcorrectnessFixtureProfile="$profile" \
    -DcorrectnessFixtureParallelism=8 \
    -Dsurefire.failIfNoSpecifiedTests=false test
}

cd "$ROOT"
IFS=',' read -r -a SCOPES <<< "$SCOPE"
for requested in "${SCOPES[@]}"; do
  case "$requested" in
    core)
      mvn -pl relation-detector/core,relation-detector/cli -am \
        -Dtest='*Scan*,*StatementExecution*,*Lineage*,*NamingEvidence*,*DerivedPath*,SemanticEquivalentCorrectnessTest' \
        -Dsurefire.failIfNoSpecifiedTests=false test
      run_correctness "common"
      ;;
    mysql)
      mvn -pl relation-detector/adaptor-mysql,relation-detector/cli -am \
        -Dtest='*MySql*,*Parser*,*Lineage*,*Ddl*' \
        -Dsurefire.failIfNoSpecifiedTests=false test
      run_correctness "mysql-root,mysql/v5_7,mysql/v8_0"
      ;;
    postgres)
      mvn -pl relation-detector/adaptor-postgres,relation-detector/cli -am \
        -Dtest='*Postgres*,*Routine*,*Parser*,*Lineage*,*Ddl*' \
        -Dsurefire.failIfNoSpecifiedTests=false test
      run_correctness "postgres-root,postgres/v16,postgres/v17,postgres/v18"
      ;;
    oracle)
      mvn -pl relation-detector/adaptor-oracle,relation-detector/cli -am \
        -Dtest='*Oracle*,*Parser*,*Lineage*,*Ddl*,OracleSqlAssetHygieneTest' \
        -Dsurefire.failIfNoSpecifiedTests=false test
      run_correctness "oracle-root,oracle/v12c,oracle/v19c,oracle/v21c,oracle/v26ai"
      ;;
    sqlserver)
      mvn -pl relation-detector/adaptor-sqlserver,relation-detector/cli -am \
        -Dtest='*SqlServer*,*Parser*,*Lineage*,*Ddl*' \
        -Dsurefire.failIfNoSpecifiedTests=false test
      run_correctness "sqlserver-root,sqlserver/v2016,sqlserver/v2017,sqlserver/v2019,sqlserver/v2022,sqlserver/v2025"
      ;;
    assets)
      mvn -pl relation-detector/cli -am \
        -Dtest='*SqlAssetHygieneTest,CommonNaturalSchemaAssetTest,CommonNaturalTypedParserAcceptanceTest,SampleDataGoldenEndpointInventoryTest' \
        -Dsurefire.failIfNoSpecifiedTests=false test
      ;;
    *)
      echo "Unknown test scope: $requested" >&2
      exit 2
      ;;
  esac
done
