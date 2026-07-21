#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RELATION_ROOT="$ROOT/relation-detector"
OUT_ROOT="${SAMPLE_DATA_CONCURRENCY_OUT:-$(mktemp -d "$RELATION_ROOT/target/sample-data-concurrency.XXXXXX")}"
SERIAL_OUT="$OUT_ROOT/serial"
PARALLEL_OUT="$OUT_ROOT/parallel"
RUNNER="$RELATION_ROOT/test-fixtures/examples/sample-data-parser-cli/run-all-sample-data-parsers.sh"

# This check proves canonical output parity only. It does not measure wall time,
# retained heap, or GC pressure and therefore cannot approve a higher release default.

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required to compare canonical JSON outputs" >&2
  exit 2
fi

SAMPLE_DATA_PARSER_CLI_OUT="$SERIAL_OUT" \
SAMPLE_DATA_PARSER_CLI_CASE_PARALLELISM=1 \
SAMPLE_DATA_PARSER_CLI_SCAN_PARALLELISM=1 \
  bash "$RUNNER"

SAMPLE_DATA_PARSER_CLI_OUT="$PARALLEL_OUT" \
SAMPLE_DATA_PARSER_CLI_CASE_PARALLELISM=3 \
SAMPLE_DATA_PARSER_CLI_SCAN_PARALLELISM=2 \
  bash "$RUNNER"

checked=0
for serial in "$SERIAL_OUT"/results/*.json; do
  name="$(basename "$serial")"
  parallel="$PARALLEL_OUT/results/$name"
  if [[ ! -f "$parallel" ]]; then
    echo "Missing parallel output: $name" >&2
    exit 1
  fi
  serial_hash="$(jq -S 'del(.generatedAt)' "$serial" | shasum | awk '{print $1}')"
  parallel_hash="$(jq -S 'del(.generatedAt)' "$parallel" | shasum | awk '{print $1}')"
  if [[ "$serial_hash" != "$parallel_hash" ]]; then
    echo "Canonical mismatch: $name" >&2
    exit 1
  fi
  checked=$((checked + 1))
done

if [[ "$checked" -ne 38 ]]; then
  echo "Expected 38 direct/derived JSON files, found $checked" >&2
  exit 1
fi

echo "sample-data parser concurrency output parity verified: $checked JSON files"
echo "this result is not a performance or memory-safety benchmark"
echo "serial outputs: $SERIAL_OUT"
echo "parallel outputs: $PARALLEL_OUT"
