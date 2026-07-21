#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
VERIFY_ALL="$ROOT/relation-detector/scripts/verify-all.sh"
LOCK_LIBRARY="$ROOT/relation-detector/scripts/heavy-job-lock.sh"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/verify-all-heavy-lock.XXXXXX")"
LOCK_DIR="$TMP_DIR/heavy-job.lock"
SESSION_ID="lock-test-$$"

cleanup() {
  status=$?
  trap - EXIT
  rm -rf "$TMP_DIR" "$ROOT/relation-detector/target/verification/$SESSION_ID" \
    "$ROOT/relation-detector/target/verification/$SESSION_ID-interrupt"
  exit "$status"
}
trap cleanup EXIT

# Test-only command hooks keep this contract test from starting repository Maven or parser jobs.
grep -q 'VERIFY_ALL_MVN' "$VERIFY_ALL"
grep -q 'VERIFY_ALL_CORRECTNESS_RUNNER' "$VERIFY_ALL"
grep -q 'VERIFY_ALL_SAMPLE_DATA_RUNNER' "$VERIFY_ALL"
grep -q 'VERIFY_ALL_STALE_SUMMARY' "$VERIFY_ALL"

cat >"$TMP_DIR/mvn-stub" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
if [[ "${1:-}" == -version ]]; then
  echo "test Maven"
  exit 0
fi
[[ -d "$RELATION_DETECTOR_HEAVY_JOB_LOCK_DIR" ]]
[[ "$(cat "$RELATION_DETECTOR_HEAVY_JOB_LOCK_DIR/token")" == "$RELATION_DETECTOR_HEAVY_JOB_LOCK_TOKEN" ]]
touch "$VERIFY_ALL_TEST_SMOKE"
SH

cat >"$TMP_DIR/correctness-stub" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
source "$VERIFY_ALL_TEST_LOCK_LIBRARY"
heavy_job_lock_acquire "$RELATION_DETECTOR_HEAVY_JOB_LOCK_DIR" correctness
[[ "$HEAVY_JOB_LOCK_BORROWED" == true ]]
heavy_job_lock_release
[[ -d "$RELATION_DETECTOR_HEAVY_JOB_LOCK_DIR" ]]
touch "$VERIFY_ALL_TEST_CORRECTNESS"
SH

cat >"$TMP_DIR/sample-stub" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
source "$VERIFY_ALL_TEST_LOCK_LIBRARY"
heavy_job_lock_acquire "$RELATION_DETECTOR_HEAVY_JOB_LOCK_DIR" sample-data
[[ "$HEAVY_JOB_LOCK_BORROWED" == true ]]
heavy_job_lock_release
[[ -d "$RELATION_DETECTOR_HEAVY_JOB_LOCK_DIR" ]]
touch "$VERIFY_ALL_TEST_SAMPLE"
exit 42
SH
chmod +x "$TMP_DIR/mvn-stub" "$TMP_DIR/correctness-stub" "$TMP_DIR/sample-stub"

set +e
RELATION_DETECTOR_HEAVY_JOB_LOCK_DIR="$LOCK_DIR" \
VERIFY_SESSION_ID="$SESSION_ID" \
VERIFY_ALL_MVN="$TMP_DIR/mvn-stub" \
VERIFY_ALL_CORRECTNESS_RUNNER="$TMP_DIR/correctness-stub" \
VERIFY_ALL_SAMPLE_DATA_RUNNER="$TMP_DIR/sample-stub" \
VERIFY_ALL_STALE_SUMMARY="$TMP_DIR/stale-summary.tsv" \
VERIFY_ALL_TEST_LOCK_LIBRARY="$LOCK_LIBRARY" \
VERIFY_ALL_TEST_SMOKE="$TMP_DIR/smoke" \
VERIFY_ALL_TEST_CORRECTNESS="$TMP_DIR/correctness" \
VERIFY_ALL_TEST_SAMPLE="$TMP_DIR/sample" \
  "$VERIFY_ALL" >"$TMP_DIR/verify.out" 2>"$TMP_DIR/verify.err"
status=$?
set -e

[[ "$status" -eq 42 ]]
[[ -f "$TMP_DIR/smoke" ]]
[[ -f "$TMP_DIR/correctness" ]]
[[ -f "$TMP_DIR/sample" ]]
[[ ! -e "$LOCK_DIR" ]]

# Interrupting the outer owner must terminate its active Maven process tree before releasing the lock.
cat >"$TMP_DIR/mvn-blocking" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
if [[ "${1:-}" == -version ]]; then
  echo "test Maven"
  exit 0
fi
sleep 300 &
child=$!
printf '%s\n' "$child" >"$VERIFY_ALL_TEST_BLOCKING_CHILD"
wait "$child"
SH
chmod +x "$TMP_DIR/mvn-blocking"

INTERRUPT_LOCK="$TMP_DIR/interrupt.lock"
INTERRUPT_SESSION="$SESSION_ID-interrupt"
RELATION_DETECTOR_HEAVY_JOB_LOCK_DIR="$INTERRUPT_LOCK" \
VERIFY_SESSION_ID="$INTERRUPT_SESSION" \
VERIFY_ALL_MVN="$TMP_DIR/mvn-blocking" \
VERIFY_ALL_CORRECTNESS_RUNNER="$TMP_DIR/correctness-stub" \
VERIFY_ALL_SAMPLE_DATA_RUNNER="$TMP_DIR/sample-stub" \
VERIFY_ALL_STALE_SUMMARY="$TMP_DIR/interrupt-stale-summary.tsv" \
VERIFY_ALL_TEST_BLOCKING_CHILD="$TMP_DIR/blocking-child.pid" \
  "$VERIFY_ALL" >"$TMP_DIR/interrupt.out" 2>"$TMP_DIR/interrupt.err" &
verify_pid=$!

for _ in 1 2 3 4 5; do
  [[ -f "$TMP_DIR/blocking-child.pid" ]] && break
  sleep 1
done
[[ -f "$TMP_DIR/blocking-child.pid" ]]
blocking_child_pid="$(cat "$TMP_DIR/blocking-child.pid")"
kill -TERM "$verify_pid"

for _ in 1 2 3 4 5; do
  kill -0 "$verify_pid" 2>/dev/null || break
  sleep 1
done
if kill -0 "$verify_pid" 2>/dev/null; then
  kill -KILL "$verify_pid" 2>/dev/null || true
  kill -KILL "$blocking_child_pid" 2>/dev/null || true
  echo "verify-all did not exit after interruption" >&2
  exit 1
fi
wait "$verify_pid" 2>/dev/null || true
if kill -0 "$blocking_child_pid" 2>/dev/null; then
  kill -KILL "$blocking_child_pid" 2>/dev/null || true
  echo "verify-all left a Maven descendant after interruption" >&2
  exit 1
fi
[[ ! -e "$INTERRUPT_LOCK" ]]
rm -rf "$ROOT/relation-detector/target/verification/$INTERRUPT_SESSION"

echo "verify-all heavy job lock test passed"
