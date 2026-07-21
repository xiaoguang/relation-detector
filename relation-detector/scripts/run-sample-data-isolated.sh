#!/usr/bin/env bash
set -euo pipefail
set -m

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RELATION_ROOT="$ROOT/relation-detector"
# shellcheck source=heavy-job-lock.sh
source "$RELATION_ROOT/scripts/heavy-job-lock.sh"
GROUP_RUNNER="${SAMPLE_DATA_PARSER_CLI_GROUP_RUNNER:-$RELATION_ROOT/test-fixtures/examples/sample-data-parser-cli/run-all-sample-data-parsers.sh}"
AGGREGATOR="$RELATION_ROOT/scripts/aggregate-sample-data-batch-reports.py"
OUT_DIR="${SAMPLE_DATA_PARSER_CLI_OUT:-$RELATION_ROOT/target/sample-data-parser-cli}"
LOCK_DIR="${SAMPLE_DATA_PARSER_CLI_LOCK_DIR:-${RELATION_DETECTOR_HEAVY_JOB_LOCK_DIR:-$RELATION_ROOT/target/.relation-detector-heavy-job.lock}}"
LOCK_JOB="sample-data"
HEAP="${SAMPLE_DATA_PARSER_CLI_HEAP:-6g}"
CASE_PARALLELISM="${SAMPLE_DATA_PARSER_CLI_CASE_PARALLELISM:-1}"
SCAN_PARALLELISM="${SAMPLE_DATA_PARSER_CLI_SCAN_PARALLELISM:-2}"
MAX_WORKER_THREADS="${SAMPLE_DATA_PARSER_CLI_MAX_WORKER_THREADS:-8}"
SESSION_ID="${SAMPLE_DATA_PARSER_CLI_SESSION_ID:-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
GROUP_ROOT="$OUT_DIR/isolated/$SESSION_ID"

GROUP_IDS=(common mysql postgres oracle-root oracle-v12c oracle-v19c oracle-v21c oracle-v26ai sqlserver)
GROUP_CASES=(
  "common-token-event-sample-data"
  "mysql-token-event-root mysql-v5_7-full mysql-v8_0-full"
  "postgres-token-event-root postgres-v16-full postgres-v17-full postgres-v18-full"
  "oracle-token-event-root"
  "oracle-v12c-full"
  "oracle-v19c-full"
  "oracle-v21c-full"
  "oracle-v26ai-full"
  "sqlserver-token-event-root sqlserver-v2016-full sqlserver-v2017-full sqlserver-v2019-full sqlserver-v2022-full sqlserver-v2025-full"
)
KNOWN_CASES=(
  common-token-event-sample-data
  mysql-token-event-root mysql-v5_7-full mysql-v8_0-full
  postgres-token-event-root postgres-v16-full postgres-v17-full postgres-v18-full
  oracle-token-event-root oracle-v12c-full oracle-v19c-full oracle-v21c-full oracle-v26ai-full
  sqlserver-token-event-root sqlserver-v2016-full sqlserver-v2017-full sqlserver-v2019-full
  sqlserver-v2022-full sqlserver-v2025-full
)

ACTIVE_GROUP_PID=""

if ! [[ "$HEAP" =~ ^[1-9][0-9]*[gG]$ ]]; then
  echo "SAMPLE_DATA_PARSER_CLI_HEAP must be a positive gigabyte value such as 6g: $HEAP" >&2
  exit 2
fi
if ! [[ "$CASE_PARALLELISM" =~ ^[1-4]$ ]]; then
  echo "SAMPLE_DATA_PARSER_CLI_CASE_PARALLELISM must be between 1 and 4: $CASE_PARALLELISM" >&2
  exit 2
fi
if ! [[ "$SCAN_PARALLELISM" =~ ^[1-9][0-9]*$ ]]; then
  echo "SAMPLE_DATA_PARSER_CLI_SCAN_PARALLELISM must be a positive integer" >&2
  exit 2
fi
if ! [[ "$MAX_WORKER_THREADS" =~ ^[1-9][0-9]*$ ]]; then
  echo "SAMPLE_DATA_PARSER_CLI_MAX_WORKER_THREADS must be a positive integer" >&2
  exit 2
fi
if (( SCAN_PARALLELISM > MAX_WORKER_THREADS )); then
  echo "scan parallelism cannot exceed the batch worker budget" >&2
  exit 2
fi

terminate_active_group() {
  local root_pid="${ACTIVE_GROUP_PID:-}"
  [[ -n "$root_pid" ]] || return 0
  kill -TERM -- "-$root_pid" 2>/dev/null || true
  local attempt
  for attempt in 1 2 3; do
    kill -0 -- "-$root_pid" 2>/dev/null || break
    sleep 1
  done
  kill -KILL -- "-$root_pid" 2>/dev/null || true
  wait "$root_pid" 2>/dev/null || true
  ACTIVE_GROUP_PID=""
}

cleanup() {
  local status=$?
  trap - EXIT INT TERM
  terminate_active_group
  heavy_job_lock_release || true
  exit "$status"
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

guard_against_existing_batch() {
  [[ "${SAMPLE_DATA_PARSER_CLI_SKIP_PROCESS_GUARD:-false}" == true ]] && return 0
  local existing
  existing="$(pgrep -f 'com\.relationdetector\.cli\.Main batch' 2>/dev/null || true)"
  if [[ -n "$existing" ]]; then
    echo "refusing to overlap an existing relation-detector batch JVM: $existing" >&2
    exit 73
  fi
}

is_known_case() {
  local requested="$1"
  local known
  for known in "${KNOWN_CASES[@]}"; do
    [[ "$known" == "$requested" ]] && return 0
  done
  return 1
}

was_requested() {
  local candidate="$1"
  [[ "$#" -gt 1 ]] || return 0
  shift
  local requested
  for requested in "$@"; do
    [[ "$candidate" == "$requested" ]] && return 0
  done
  return 1
}

run_group() {
  local group_id="$1"
  shift
  local group_dir="$GROUP_ROOT/$group_id"
  mkdir -p "$group_dir"
  printf 'sample-data group=%s heap=%s caseParallelism=%s scanParallelism=%s cases=%s\n' \
    "$group_id" "$HEAP" "$CASE_PARALLELISM" "$SCAN_PARALLELISM" "$*"

  SAMPLE_DATA_PARSER_CLI_GROUP_WORKER=true \
  SAMPLE_DATA_PARSER_CLI_GROUP_ID="$group_id" \
  SAMPLE_DATA_PARSER_CLI_SKIP_PACKAGE=true \
  SAMPLE_DATA_PARSER_CLI_SKIP_JLS_CHECK=true \
  SAMPLE_DATA_PARSER_CLI_OUT="$OUT_DIR" \
  SAMPLE_DATA_PARSER_CLI_BATCH_MANIFEST="$group_dir/batch.yml" \
  SAMPLE_DATA_PARSER_CLI_BATCH_REPORT="$group_dir/batch-report.json" \
  SAMPLE_DATA_PARSER_CLI_BATCH_LOG="$group_dir/batch.log" \
  SAMPLE_DATA_PARSER_CLI_LOCK_DIR="$group_dir/worker.lock" \
  SAMPLE_DATA_PARSER_CLI_CASE_PARALLELISM="$CASE_PARALLELISM" \
  SAMPLE_DATA_PARSER_CLI_SCAN_PARALLELISM="$SCAN_PARALLELISM" \
  SAMPLE_DATA_PARSER_CLI_MAX_WORKER_THREADS="$MAX_WORKER_THREADS" \
  JAVA_TOOL_OPTIONS="-Xms1g -Xmx$HEAP -XX:+UseG1GC" \
    "$GROUP_RUNNER" "$@" >"$group_dir/runner.log" 2>&1 &
  ACTIVE_GROUP_PID=$!

  local status=0
  wait "$ACTIVE_GROUP_PID" || status=$?
  ACTIVE_GROUP_PID=""
  if (( status != 0 )); then
    echo "sample-data group failed: $group_id (status=$status, log=$group_dir/runner.log)" >&2
    tail -n 120 "$group_dir/runner.log" >&2 || true
    exit "$status"
  fi
  if [[ ! -f "$group_dir/batch-report.json" ]]; then
    echo "sample-data group did not write a batch report: $group_id" >&2
    exit 1
  fi
}

if ! heavy_job_lock_acquire "$LOCK_DIR" "$LOCK_JOB"; then
  exit 73
fi
guard_against_existing_batch

REQUESTED_INPUT=()
REQUESTED_COUNT="$#"
if (( REQUESTED_COUNT > 0 )); then
  REQUESTED_INPUT=("$@")
fi
requested_index=0
while (( requested_index < REQUESTED_COUNT )); do
  requested="${REQUESTED_INPUT[$requested_index]}"
  if ! is_known_case "$requested"; then
    echo "Unknown sample-data parser case: $requested" >&2
    exit 2
  fi
  requested_index=$((requested_index + 1))
done

SELECTED_CASES=()
for known in "${KNOWN_CASES[@]}"; do
  if (( REQUESTED_COUNT == 0 )) || was_requested "$known" "${REQUESTED_INPUT[@]}"; then
    SELECTED_CASES+=("$known")
  fi
done

cd "$ROOT"
mkdir -p "$OUT_DIR" "$GROUP_ROOT"
if [[ "${SAMPLE_DATA_PARSER_CLI_SKIP_PACKAGE:-false}" != true ]]; then
  mvn -q -pl relation-detector/core,relation-detector/adaptor-mysql,relation-detector/adaptor-postgres,relation-detector/adaptor-oracle,relation-detector/adaptor-sqlserver,relation-detector/cli -am -Dmaven.test.skip=true package
fi
"$RELATION_ROOT/scripts/check-no-jls-bad-classes.sh" "$ROOT"

REPORTS=()
for index in "${!GROUP_IDS[@]}"; do
  read -r -a candidates <<<"${GROUP_CASES[$index]}"
  selected=()
  for candidate in "${candidates[@]}"; do
    if (( REQUESTED_COUNT == 0 )) || was_requested "$candidate" "${REQUESTED_INPUT[@]}"; then
      selected+=("$candidate")
    fi
  done
  if [[ "${#selected[@]}" -eq 0 ]]; then
    continue
  fi
  run_group "${GROUP_IDS[$index]}" "${selected[@]}"
  REPORTS+=("$GROUP_ROOT/${GROUP_IDS[$index]}/batch-report.json")
done

SAMPLE_DATA_PARSER_CLI_GROUP_WORKER=true \
SAMPLE_DATA_PARSER_CLI_SKIP_PACKAGE=true \
SAMPLE_DATA_PARSER_CLI_SKIP_JLS_CHECK=true \
SAMPLE_DATA_PARSER_CLI_SKIP_BATCH=true \
SAMPLE_DATA_PARSER_CLI_OUT="$OUT_DIR" \
SAMPLE_DATA_PARSER_CLI_BATCH_MANIFEST="$OUT_DIR/batch.yml" \
SAMPLE_DATA_PARSER_CLI_BATCH_REPORT="$OUT_DIR/batch-report.json" \
SAMPLE_DATA_PARSER_CLI_BATCH_LOG="$GROUP_ROOT/finalize.log" \
SAMPLE_DATA_PARSER_CLI_LOCK_DIR="$GROUP_ROOT/finalize.lock" \
SAMPLE_DATA_PARSER_CLI_CASE_PARALLELISM="$CASE_PARALLELISM" \
SAMPLE_DATA_PARSER_CLI_SCAN_PARALLELISM="$SCAN_PARALLELISM" \
SAMPLE_DATA_PARSER_CLI_MAX_WORKER_THREADS="$MAX_WORKER_THREADS" \
  "$GROUP_RUNNER" "${SELECTED_CASES[@]}" >"$GROUP_ROOT/finalize.log" 2>&1

AGGREGATE_ARGS=()
for selected_case in "${SELECTED_CASES[@]}"; do
  AGGREGATE_ARGS+=(--expected-case "$selected_case")
done
python3 "$AGGREGATOR" \
  --output "$OUT_DIR/batch-report.json" \
  "${AGGREGATE_ARGS[@]}" \
  "${REPORTS[@]}"

printf 'isolated sample-data session: %s\n' "$GROUP_ROOT"
printf 'sample-data batch report: %s\n' "$OUT_DIR/batch-report.json"
