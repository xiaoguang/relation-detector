#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
AUDITOR="$ROOT/relation-detector/test-fixtures/examples/sample-data-parser-cli/audit-semantic-observations.sh"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/observation-audit-bundle.XXXXXX")"
trap 'rm -rf "$TMP_ROOT"' EXIT

RESULTS="$TMP_ROOT/results"
REPORT="$TMP_ROOT/observation-parity.tsv"
for case_id in \
  mysql-token-event-root mysql-v8_0-full \
  postgres-token-event-root postgres-v18-full \
  oracle-token-event-root oracle-v26ai-full \
  sqlserver-token-event-root sqlserver-v2025-full; do
  mkdir -p "$RESULTS/$case_id"
  printf '{"relationships":[],"dataLineages":[]}\n' >"$RESULTS/$case_id/direct.json"
done

bash "$AUDITOR" "$RESULTS" "$REPORT"
[[ "$(wc -l <"$REPORT" | tr -d '[:space:]')" -eq 5 ]]
[[ "$(awk -F '\t' 'NR > 1 && ($4 != 0 || $5 != 0) {count++} END {print count + 0}' "$REPORT")" -eq 0 ]]

LEGACY="$TMP_ROOT/legacy"
mkdir -p "$LEGACY"
for case_id in mysql-token-event-root mysql-v8_0-full; do
  printf '{"relationships":[],"dataLineages":[]}\n' >"$LEGACY/$case_id.json"
done
if bash "$AUDITOR" "$LEGACY" "$TMP_ROOT/legacy-report.tsv" \
    'mysql-token-event-root,mysql-v8_0-full' >/dev/null 2>&1; then
  echo "observation auditor accepted removed flat case JSON layout" >&2
  exit 1
fi

echo "observation audit bundle test passed"
