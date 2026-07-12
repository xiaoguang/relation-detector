#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
GRAMMAR_ROOT="$ROOT/relation-detector/grammar"

expected=(
  common-token-event common-script
  mysql-token-event mysql-script mysql-v5_7 mysql-v8_0
  postgres-token-event postgres-script postgres-v16 postgres-v17 postgres-v18
  postgres-plpgsql-token-event plpgsql-v16 plpgsql-v17 plpgsql-v18
  oracle-token-event oracle-script
  oracle-v12c oracle-v19c oracle-v21c oracle-v26ai
  sqlserver-token-event sqlserver-script
  sqlserver-v2016 sqlserver-v2017 sqlserver-v2019 sqlserver-v2022 sqlserver-v2025
)

for module in "${expected[@]}"; do
  test -f "$GRAMMAR_ROOT/$module/pom.xml"
  test -n "$(find "$GRAMMAR_ROOT/$module/src/main/antlr4" -name '*.g4' -print -quit)"
done

if find "$ROOT/relation-detector/core" "$ROOT/relation-detector"/adaptor-* -name '*.g4' -print -quit | grep -q .; then
  echo "All grammars must live in relation-detector/grammar" >&2
  exit 1
fi

actual_modules="$(find "$GRAMMAR_ROOT" -mindepth 2 -maxdepth 2 -name pom.xml | wc -l | tr -d ' ')"
test "$actual_modules" = "${#expected[@]}"

echo "grammar module architecture test passed"
