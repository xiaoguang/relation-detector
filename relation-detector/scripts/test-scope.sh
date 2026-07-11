#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCOPE="${1:-}"

if [[ -z "$SCOPE" ]]; then
  echo "Usage: $0 <core|mysql|postgres|oracle|sqlserver|assets>[,...]" >&2
  exit 2
fi

MODULES=(relation-detector/cli)
TESTS=(CorrectnessFixtureRunnerTest)
PROFILES=()

append_unique() {
  local value="$1"
  shift
  local existing
  for existing in "$@"; do
    [[ "$existing" == "$value" ]] && return 0
  done
  return 1
}

add_module() { append_unique "$1" "${MODULES[@]}" || MODULES+=("$1"); }
add_test() { append_unique "$1" "${TESTS[@]}" || TESTS+=("$1"); }
add_profile() { append_unique "$1" "${PROFILES[@]-}" || PROFILES+=("$1"); }

IFS=',' read -r -a SCOPES <<<"$SCOPE"
for requested in "${SCOPES[@]}"; do
  case "$requested" in
    core)
      add_module relation-detector/core
      add_test '*Scan*'
      add_test '*StatementExecution*'
      add_test '*Lineage*'
      add_test '*NamingEvidence*'
      add_test '*DerivedPath*'
      add_test SemanticEquivalentCorrectnessTest
      add_profile common
      ;;
    mysql)
      add_module relation-detector/adaptor-mysql
      add_test '*MySql*'
      add_profile mysql-root
      add_profile mysql/v5_7
      add_profile mysql/v8_0
      ;;
    postgres)
      add_module relation-detector/adaptor-postgres
      add_test '*Postgres*'
      add_test '*Routine*'
      add_profile postgres-root
      add_profile postgres/v16
      add_profile postgres/v17
      add_profile postgres/v18
      ;;
    oracle)
      add_module relation-detector/adaptor-oracle
      add_test '*Oracle*'
      add_profile oracle-root
      add_profile oracle/v12c
      add_profile oracle/v19c
      add_profile oracle/v21c
      add_profile oracle/v26ai
      ;;
    sqlserver)
      add_module relation-detector/adaptor-sqlserver
      add_test '*SqlServer*'
      add_profile sqlserver-root
      add_profile sqlserver/v2016
      add_profile sqlserver/v2017
      add_profile sqlserver/v2019
      add_profile sqlserver/v2022
      add_profile sqlserver/v2025
      ;;
    assets)
      add_test '*SqlAssetHygieneTest'
      add_test CommonNaturalSchemaAssetTest
      add_test CommonNaturalTypedParserAcceptanceTest
      add_test SampleDataGoldenEndpointInventoryTest
      ;;
    *)
      echo "Unknown test scope: $requested" >&2
      exit 2
      ;;
  esac
done

if [[ "${#PROFILES[@]}" -eq 0 ]]; then
  PROFILES=(smoke)
fi

MODULE_CSV="$(IFS=,; echo "${MODULES[*]}")"
TEST_CSV="$(IFS=,; echo "${TESTS[*]}")"
PROFILE_CSV="$(IFS=,; echo "${PROFILES[*]}")"

cd "$ROOT"
mvn -T 2 -pl "$MODULE_CSV" -am \
  -Dtest="$TEST_CSV" \
  -DcorrectnessFixtureProfile="$PROFILE_CSV" \
  -DcorrectnessFixtureParallelism=8 \
  -Dsurefire.failIfNoSpecifiedTests=false test
