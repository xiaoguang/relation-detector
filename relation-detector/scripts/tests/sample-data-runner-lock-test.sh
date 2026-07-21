#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
RUNNER="$ROOT/relation-detector/test-fixtures/examples/sample-data-parser-cli/run-all-sample-data-parsers.sh"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/sample-data-runner-lock.XXXXXX")"
LOCK_DIR="$TMP_DIR/run.lock"

cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

mkdir "$LOCK_DIR"
printf '%s\n' "$$" >"$LOCK_DIR/pid"
printf '%s\n' test-owner >"$LOCK_DIR/job"
printf '%s\n' test-token >"$LOCK_DIR/token"

if SAMPLE_DATA_PARSER_CLI_LOCK_DIR="$LOCK_DIR" \
    SAMPLE_DATA_PARSER_CLI_SKIP_PACKAGE=true \
    "$RUNNER" common-token-event-sample-data \
    >"$TMP_DIR/duplicate.out" 2>"$TMP_DIR/duplicate.err"; then
  echo "duplicate sample-data runner unexpectedly succeeded" >&2
  exit 1
fi

grep -q 'relation-detector heavy job is already running' "$TMP_DIR/duplicate.err"
grep -q 'job=test-owner' "$TMP_DIR/duplicate.err"
grep -q "pid=$$" "$TMP_DIR/duplicate.err"

rm -f "$LOCK_DIR/pid" "$LOCK_DIR/job" "$LOCK_DIR/token"
rmdir "$LOCK_DIR"

if SAMPLE_DATA_PARSER_CLI_LOCK_DIR="$LOCK_DIR" \
    SAMPLE_DATA_PARSER_CLI_SKIP_PACKAGE=true \
    "$RUNNER" definitely-not-a-parser-case \
    >"$TMP_DIR/release.out" 2>"$TMP_DIR/release.err"; then
  echo "invalid sample-data parser case unexpectedly succeeded" >&2
  exit 1
fi

grep -q 'Unknown sample-data parser case' "$TMP_DIR/release.err"
if [[ -e "$LOCK_DIR" ]]; then
  echo "sample-data runner did not release its lock after exiting" >&2
  exit 1
fi

echo "sample-data runner lock tests passed"
