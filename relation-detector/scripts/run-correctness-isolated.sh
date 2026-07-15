#!/usr/bin/env bash
set -euo pipefail
set -m

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MVN_BIN="${CORRECTNESS_MVN:-mvn}"
PARALLELISM="${CORRECTNESS_PARALLELISM:-6}"
HEAP="${CORRECTNESS_HEAP:-6g}"
SESSION_ID="${CORRECTNESS_SESSION_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
OUTPUT_DIR="${CORRECTNESS_OUTPUT_DIR:-$ROOT/relation-detector/target/correctness-isolated/$SESSION_ID}"
RUN_SUMMARY="${CORRECTNESS_RUN_SUMMARY:-$ROOT/relation-detector/target/correctness-run-summary.json}"
LOCK_DIR="${CORRECTNESS_LOCK_DIR:-$ROOT/relation-detector/target/correctness-isolated.lock}"
AGGREGATOR="$ROOT/relation-detector/scripts/aggregate-correctness-summaries.py"

GROUP_IDS=(common mysql postgres oracle-root oracle-v12c oracle-v19c oracle-v21c oracle-v26ai sqlserver)
GROUP_PROFILES=(common mysql postgres oracle-root oracle/v12c oracle/v19c oracle/v21c oracle/v26ai sqlserver)
EXPECTED_CATEGORIES=(
  common/root
  mysql/root mysql/5_7 mysql/8_0
  postgres/root postgres/16 postgres/17 postgres/18
  oracle/root oracle/12c oracle/19c oracle/21c oracle/26ai
  sqlserver/root sqlserver/2016 sqlserver/2017 sqlserver/2019 sqlserver/2022 sqlserver/2025
)

ACTIVE_MAVEN_PID=""
LOCK_OWNED=false

if ! [[ "$PARALLELISM" =~ ^[0-9]+$ ]] || (( PARALLELISM < 4 || PARALLELISM > 8 )); then
  echo "CORRECTNESS_PARALLELISM must be between 4 and 8: $PARALLELISM" >&2
  exit 2
fi
if ! [[ "$HEAP" =~ ^[1-9][0-9]*[gG]$ ]]; then
  echo "CORRECTNESS_HEAP must be a positive gigabyte value such as 6g: $HEAP" >&2
  exit 2
fi

terminate_active_process_tree() {
  local root_pid="${ACTIVE_MAVEN_PID:-}"
  [[ -n "$root_pid" ]] || return 0
  kill -TERM -- "-$root_pid" 2>/dev/null || true

  local attempt
  for attempt in 1 2 3; do
    kill -0 -- "-$root_pid" 2>/dev/null || break
    sleep 1
  done
  kill -KILL -- "-$root_pid" 2>/dev/null || true
  wait "$root_pid" 2>/dev/null || true
  ACTIVE_MAVEN_PID=""
}

release_lock() {
  [[ "$LOCK_OWNED" == true ]] || return 0
  rm -f "$LOCK_DIR/pid"
  rmdir "$LOCK_DIR" 2>/dev/null || true
  LOCK_OWNED=false
}

cleanup() {
  local status=$?
  trap - EXIT INT TERM
  terminate_active_process_tree
  release_lock
  exit "$status"
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

acquire_lock() {
  if mkdir "$LOCK_DIR" 2>/dev/null; then
    LOCK_OWNED=true
    printf '%s\n' "$$" >"$LOCK_DIR/pid"
    return 0
  fi

  local owner=""
  [[ -f "$LOCK_DIR/pid" ]] && owner="$(cat "$LOCK_DIR/pid")"
  if [[ -n "$owner" ]] && kill -0 "$owner" 2>/dev/null; then
    echo "isolated correctness is already running: pid=$owner" >&2
    exit 1
  fi
  rm -f "$LOCK_DIR/pid"
  if ! rmdir "$LOCK_DIR" 2>/dev/null || ! mkdir "$LOCK_DIR" 2>/dev/null; then
    echo "stale correctness lock requires manual cleanup: $LOCK_DIR" >&2
    exit 1
  fi
  LOCK_OWNED=true
  printf '%s\n' "$$" >"$LOCK_DIR/pid"
}

guard_against_existing_surefire() {
  [[ "${CORRECTNESS_SKIP_PROCESS_GUARD:-false}" == true ]] && return 0
  local pattern="$ROOT/relation-detector/cli/target/surefire/surefirebooter"
  local existing
  existing="$(pgrep -f "$pattern" 2>/dev/null || true)"
  if [[ -n "$existing" ]]; then
    echo "refusing to overlap an existing relation-detector Surefire JVM: $existing" >&2
    exit 1
  fi
}

run_group() {
  local group_id="$1"
  local profile="$2"
  local log_file="$OUTPUT_DIR/$group_id.log"
  local summary_file="$OUTPUT_DIR/$group_id-summary.json"

  printf 'correctness group=%s profile=%s heap=%s parallelism=%s\n' \
    "$group_id" "$profile" "$HEAP" "$PARALLELISM"
  "$MVN_BIN" -pl relation-detector/cli -am \
    -Dmaven.build.cache.enabled=false \
    '-Dtest=CorrectnessFixtureRunnerTest#allCorrectnessFixturesPassGoldenExpectations' \
    -DcorrectnessFixtureProfile="$profile" \
    -DcorrectnessFixtureParallelism="$PARALLELISM" \
    -DupdateCorrectnessGold=false \
    -DargLine="-Xms1g -Xmx$HEAP -XX:+UseG1GC" \
    -Dsurefire.failIfNoSpecifiedTests=false \
    test >"$log_file" 2>&1 &
  ACTIVE_MAVEN_PID=$!

  local status=0
  wait "$ACTIVE_MAVEN_PID" || status=$?
  ACTIVE_MAVEN_PID=""
  if (( status != 0 )); then
    echo "correctness group failed: $group_id (status=$status, log=$log_file)" >&2
    tail -n 120 "$log_file" >&2 || true
    exit "$status"
  fi
  if [[ ! -f "$RUN_SUMMARY" ]]; then
    echo "correctness group did not write summary: $group_id" >&2
    exit 1
  fi
  cp "$RUN_SUMMARY" "$summary_file"
}

acquire_lock
guard_against_existing_surefire
mkdir -p "$OUTPUT_DIR"

SUMMARY_FILES=()
for index in "${!GROUP_IDS[@]}"; do
  run_group "${GROUP_IDS[$index]}" "${GROUP_PROFILES[$index]}"
  SUMMARY_FILES+=("$OUTPUT_DIR/${GROUP_IDS[$index]}-summary.json")
done

AGGREGATE_ARGS=()
for category in "${EXPECTED_CATEGORIES[@]}"; do
  AGGREGATE_ARGS+=(--expected-category "$category")
done
python3 "$AGGREGATOR" \
  --output "$OUTPUT_DIR/correctness-run-summary.json" \
  "${AGGREGATE_ARGS[@]}" \
  "${SUMMARY_FILES[@]}"
cp "$OUTPUT_DIR/correctness-run-summary.json" "$RUN_SUMMARY"

printf 'isolated correctness summary: %s\n' "$OUTPUT_DIR/correctness-run-summary.json"
