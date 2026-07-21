#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
CORRECTNESS_RUNNER="$ROOT/relation-detector/scripts/run-correctness-isolated.sh"
SAMPLE_DATA_RUNNER="$ROOT/relation-detector/scripts/run-sample-data-isolated.sh"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/heavy-job-mutual-exclusion.XXXXXX")"
SHARED_LOCK="$TMP_DIR/heavy-job.lock"
CORRECTNESS_PID=""
SAMPLE_DATA_PID=""

cleanup() {
  if [[ -n "$CORRECTNESS_PID" ]]; then
    kill -TERM "$CORRECTNESS_PID" 2>/dev/null || true
    wait "$CORRECTNESS_PID" 2>/dev/null || true
  fi
  if [[ -n "$SAMPLE_DATA_PID" ]]; then
    kill -TERM "$SAMPLE_DATA_PID" 2>/dev/null || true
    wait "$SAMPLE_DATA_PID" 2>/dev/null || true
  fi
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

wait_for_file() {
  local file="$1"
  local attempt
  for attempt in 1 2 3 4 5; do
    [[ -f "$file" ]] && return 0
    sleep 1
  done
  echo "timed out waiting for $file" >&2
  return 1
}

require_file() {
  local file="$1"
  if [[ ! -f "$file" ]]; then
    echo "expected file: $file" >&2
    exit 1
  fi
}

require_absent() {
  local path="$1"
  if [[ -e "$path" ]]; then
    echo "unexpected path: $path" >&2
    exit 1
  fi
}

require_equal() {
  local expected="$1"
  local actual="$2"
  if [[ "$actual" != "$expected" ]]; then
    echo "expected '$expected', got '$actual'" >&2
    exit 1
  fi
}

cat >"$TMP_DIR/mvn-blocking" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
touch "$HEAVY_TEST_CORRECTNESS_STARTED"
sleep 300 &
child=$!
wait "$child"
SH

cat >"$TMP_DIR/mvn-invoked" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
touch "$HEAVY_TEST_CORRECTNESS_INVOKED"
exit 42
SH

cat >"$TMP_DIR/sample-blocking" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
touch "$HEAVY_TEST_SAMPLE_STARTED"
sleep 300 &
child=$!
wait "$child"
SH

cat >"$TMP_DIR/sample-invoked" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
touch "$HEAVY_TEST_SAMPLE_INVOKED"
exit 42
SH

chmod +x "$TMP_DIR/mvn-blocking" "$TMP_DIR/mvn-invoked" \
  "$TMP_DIR/sample-blocking" "$TMP_DIR/sample-invoked"

# A running correctness suite owns the shared lock before any sample-data worker starts.
CORRECTNESS_MVN="$TMP_DIR/mvn-blocking" \
CORRECTNESS_LOCK_DIR="$SHARED_LOCK" \
CORRECTNESS_OUTPUT_DIR="$TMP_DIR/correctness-output" \
CORRECTNESS_RUN_SUMMARY="$TMP_DIR/correctness-summary.json" \
CORRECTNESS_SKIP_PROCESS_GUARD=true \
HEAVY_TEST_CORRECTNESS_STARTED="$TMP_DIR/correctness-started" \
  "$CORRECTNESS_RUNNER" >"$TMP_DIR/correctness.out" 2>"$TMP_DIR/correctness.err" &
CORRECTNESS_PID=$!
wait_for_file "$TMP_DIR/correctness-started"
require_file "$SHARED_LOCK/job"
require_equal correctness "$(cat "$SHARED_LOCK/job")"

set +e
RELATION_DETECTOR_HEAVY_JOB_LOCK_DIR="$SHARED_LOCK" \
SAMPLE_DATA_PARSER_CLI_GROUP_RUNNER="$TMP_DIR/sample-invoked" \
SAMPLE_DATA_PARSER_CLI_SKIP_PACKAGE=true \
SAMPLE_DATA_PARSER_CLI_SKIP_PROCESS_GUARD=true \
SAMPLE_DATA_PARSER_CLI_OUT="$TMP_DIR/rejected-sample-output" \
HEAVY_TEST_SAMPLE_INVOKED="$TMP_DIR/sample-invoked-marker" \
  "$SAMPLE_DATA_RUNNER" common-token-event-sample-data \
  >"$TMP_DIR/rejected-sample.out" 2>"$TMP_DIR/rejected-sample.err"
sample_status=$?
set -e
require_equal 73 "$sample_status"
require_absent "$TMP_DIR/sample-invoked-marker"

kill -TERM "$CORRECTNESS_PID"
wait "$CORRECTNESS_PID" 2>/dev/null || true
CORRECTNESS_PID=""
require_absent "$SHARED_LOCK"

# A running sample-data suite owns the same lock before any correctness Maven process starts.
SAMPLE_DATA_PARSER_CLI_GROUP_RUNNER="$TMP_DIR/sample-blocking" \
SAMPLE_DATA_PARSER_CLI_LOCK_DIR="$SHARED_LOCK" \
SAMPLE_DATA_PARSER_CLI_SKIP_PACKAGE=true \
SAMPLE_DATA_PARSER_CLI_SKIP_PROCESS_GUARD=true \
SAMPLE_DATA_PARSER_CLI_OUT="$TMP_DIR/sample-output" \
HEAVY_TEST_SAMPLE_STARTED="$TMP_DIR/sample-started" \
  "$SAMPLE_DATA_RUNNER" common-token-event-sample-data \
  >"$TMP_DIR/sample.out" 2>"$TMP_DIR/sample.err" &
SAMPLE_DATA_PID=$!
wait_for_file "$TMP_DIR/sample-started"
require_file "$SHARED_LOCK/job"
require_equal sample-data "$(cat "$SHARED_LOCK/job")"

set +e
RELATION_DETECTOR_HEAVY_JOB_LOCK_DIR="$SHARED_LOCK" \
CORRECTNESS_MVN="$TMP_DIR/mvn-invoked" \
CORRECTNESS_OUTPUT_DIR="$TMP_DIR/rejected-correctness-output" \
CORRECTNESS_RUN_SUMMARY="$TMP_DIR/rejected-correctness-summary.json" \
CORRECTNESS_SKIP_PROCESS_GUARD=true \
HEAVY_TEST_CORRECTNESS_INVOKED="$TMP_DIR/correctness-invoked-marker" \
  "$CORRECTNESS_RUNNER" >"$TMP_DIR/rejected-correctness.out" \
  2>"$TMP_DIR/rejected-correctness.err"
correctness_status=$?
set -e
require_equal 1 "$correctness_status"
require_absent "$TMP_DIR/correctness-invoked-marker"

kill -TERM "$SAMPLE_DATA_PID"
wait "$SAMPLE_DATA_PID" 2>/dev/null || true
SAMPLE_DATA_PID=""
require_absent "$SHARED_LOCK"

# A stale owner is reclaimed, and the lock is released again on validation failure.
mkdir "$SHARED_LOCK"
printf '%s\n' 99999999 >"$SHARED_LOCK/pid"
printf '%s\n' stale-job >"$SHARED_LOCK/job"
printf '%s\n' stale-token >"$SHARED_LOCK/token"
set +e
RELATION_DETECTOR_HEAVY_JOB_LOCK_DIR="$SHARED_LOCK" \
SAMPLE_DATA_PARSER_CLI_GROUP_RUNNER="$TMP_DIR/sample-invoked" \
SAMPLE_DATA_PARSER_CLI_SKIP_PACKAGE=true \
SAMPLE_DATA_PARSER_CLI_SKIP_PROCESS_GUARD=true \
SAMPLE_DATA_PARSER_CLI_OUT="$TMP_DIR/stale-output" \
  "$SAMPLE_DATA_RUNNER" definitely-not-a-parser-case \
  >"$TMP_DIR/stale.out" 2>"$TMP_DIR/stale.err"
stale_status=$?
set -e
require_equal 2 "$stale_status"
require_absent "$SHARED_LOCK"

echo "heavy job mutual exclusion tests passed"
