#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
LOCK_LIBRARY="$ROOT/relation-detector/scripts/heavy-job-lock.sh"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/heavy-job-lock.XXXXXX")"

cleanup() {
  status=$?
  trap - EXIT
  rm -rf "$TMP_DIR"
  exit "$status"
}
trap cleanup EXIT

require_equal() {
  local expected="$1"
  local actual="$2"
  if [[ "$expected" != "$actual" ]]; then
    echo "expected '$expected', got '$actual'" >&2
    exit 1
  fi
}

require_absent() {
  local path="$1"
  if [[ -e "$path" ]]; then
    echo "expected path to be absent: $path" >&2
    exit 1
  fi
}

# Loading the library is the public shell contract exercised below.
# shellcheck source=../heavy-job-lock.sh
if [[ ! -f "$LOCK_LIBRARY" ]]; then
  echo "missing heavy-job lock library: $LOCK_LIBRARY" >&2
  exit 1
fi
source "$LOCK_LIBRARY"

# Simultaneous first acquisition must produce exactly one owner.
cat >"$TMP_DIR/contender.sh" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
source "$HEAVY_TEST_LOCK_LIBRARY"
while [[ ! -e "$HEAVY_TEST_START" ]]; do
  sleep 0.02
done
if heavy_job_lock_acquire "$HEAVY_TEST_LOCK_DIR" contender; then
  printf '%s\n' "$$" >>"$HEAVY_TEST_WINNERS"
  sleep 2
  heavy_job_lock_release
else
  printf '%s\n' "$$" >>"$HEAVY_TEST_LOSERS"
fi
SH
chmod +x "$TMP_DIR/contender.sh"

CONCURRENT_LOCK="$TMP_DIR/concurrent.lock"
: >"$TMP_DIR/winners"
: >"$TMP_DIR/losers"
contender_pids=""
for _ in 1 2 3 4 5 6 7 8; do
  HEAVY_TEST_LOCK_LIBRARY="$LOCK_LIBRARY" \
  HEAVY_TEST_LOCK_DIR="$CONCURRENT_LOCK" \
  HEAVY_TEST_START="$TMP_DIR/start" \
  HEAVY_TEST_WINNERS="$TMP_DIR/winners" \
  HEAVY_TEST_LOSERS="$TMP_DIR/losers" \
    "$TMP_DIR/contender.sh" >"$TMP_DIR/contender-$_.out" \
    2>"$TMP_DIR/contender-$_.err" &
  contender_pids="$contender_pids $!"
done
touch "$TMP_DIR/start"
for contender_pid in $contender_pids; do
  wait "$contender_pid"
done
require_equal 1 "$(wc -l <"$TMP_DIR/winners" | tr -d ' ')"
require_equal 7 "$(wc -l <"$TMP_DIR/losers" | tr -d ' ')"
require_absent "$CONCURRENT_LOCK"

# A directory whose owner metadata is still being published must fail closed.
INCOMPLETE_LOCK="$TMP_DIR/incomplete.lock"
mkdir "$INCOMPLETE_LOCK"
printf '%s\n' initializing >"$INCOMPLETE_LOCK/job"
if (heavy_job_lock_acquire "$INCOMPLETE_LOCK" rejected) \
    >"$TMP_DIR/incomplete.out" 2>"$TMP_DIR/incomplete.err"; then
  echo "incomplete lock was unexpectedly reclaimed" >&2
  exit 1
fi
[[ -d "$INCOMPLETE_LOCK" ]]
[[ "$(cat "$INCOMPLETE_LOCK/job")" == initializing ]]
grep -q 'ownership metadata is incomplete' "$TMP_DIR/incomplete.err"
rm -f "$INCOMPLETE_LOCK/job"
rmdir "$INCOMPLETE_LOCK"

# A live owner blocks contenders without disclosing its token.
LIVE_LOCK="$TMP_DIR/live.lock"
mkdir "$LIVE_LOCK"
printf '%s\n' "$$" >"$LIVE_LOCK/pid"
printf '%s\n' live-owner >"$LIVE_LOCK/job"
printf '%s\n' top-secret-token >"$LIVE_LOCK/token"
if (heavy_job_lock_acquire "$LIVE_LOCK" rejected) \
    >"$TMP_DIR/live.out" 2>"$TMP_DIR/live.err"; then
  echo "live owner was unexpectedly replaced" >&2
  exit 1
fi
grep -q "job=live-owner pid=$$" "$TMP_DIR/live.err"
if grep -q 'top-secret-token' "$TMP_DIR/live.err"; then
  echo "owner token leaked to stderr" >&2
  exit 1
fi
rm -f "$LIVE_LOCK/pid" "$LIVE_LOCK/job" "$LIVE_LOCK/token"
rmdir "$LIVE_LOCK"

# A dead owner is quarantined atomically and replaced once.
STALE_LOCK="$TMP_DIR/stale.lock"
mkdir "$STALE_LOCK"
printf '%s\n' 99999999 >"$STALE_LOCK/pid"
printf '%s\n' stale-owner >"$STALE_LOCK/job"
printf '%s\n' stale-token >"$STALE_LOCK/token"
heavy_job_lock_acquire "$STALE_LOCK" replacement
[[ "$HEAVY_JOB_LOCK_OWNED" == true ]]
[[ "$(cat "$STALE_LOCK/pid")" == "$$" ]]
[[ "$(cat "$STALE_LOCK/job")" == replacement ]]
heavy_job_lock_release
require_absent "$STALE_LOCK"

# A child can borrow the exact inherited lock, but cannot release its owner.
BORROWED_LOCK="$TMP_DIR/borrowed.lock"
heavy_job_lock_acquire "$BORROWED_LOCK" release
OWNER_TOKEN="$RELATION_DETECTOR_HEAVY_JOB_LOCK_TOKEN"
OWNER_PID="$(cat "$BORROWED_LOCK/pid")"
RELATION_DETECTOR_HEAVY_JOB_LOCK_DIR="$BORROWED_LOCK" \
RELATION_DETECTOR_HEAVY_JOB_LOCK_TOKEN="$OWNER_TOKEN" \
HEAVY_TEST_LOCK_LIBRARY="$LOCK_LIBRARY" \
  bash -c '
    set -euo pipefail
    source "$HEAVY_TEST_LOCK_LIBRARY"
    heavy_job_lock_acquire "$RELATION_DETECTOR_HEAVY_JOB_LOCK_DIR" child
    [[ "$HEAVY_JOB_LOCK_BORROWED" == true ]]
    heavy_job_lock_release
  '
[[ -d "$BORROWED_LOCK" ]]
[[ "$(cat "$BORROWED_LOCK/pid")" == "$OWNER_PID" ]]

# Wrong tokens and inherited tokens pointed at another path are rejected.
if RELATION_DETECTOR_HEAVY_JOB_LOCK_DIR="$BORROWED_LOCK" \
   RELATION_DETECTOR_HEAVY_JOB_LOCK_TOKEN=wrong-token \
   HEAVY_TEST_LOCK_LIBRARY="$LOCK_LIBRARY" \
   bash -c 'source "$HEAVY_TEST_LOCK_LIBRARY"; heavy_job_lock_acquire "$RELATION_DETECTOR_HEAVY_JOB_LOCK_DIR" child' \
   >"$TMP_DIR/wrong-token.out" 2>"$TMP_DIR/wrong-token.err"; then
  echo "wrong inherited token unexpectedly succeeded" >&2
  exit 1
fi
if grep -q "$OWNER_TOKEN" "$TMP_DIR/wrong-token.err"; then
  echo "owner token leaked while rejecting a borrower" >&2
  exit 1
fi

if RELATION_DETECTOR_HEAVY_JOB_LOCK_DIR="$TMP_DIR/other.lock" \
   RELATION_DETECTOR_HEAVY_JOB_LOCK_TOKEN="$OWNER_TOKEN" \
   HEAVY_TEST_LOCK_LIBRARY="$LOCK_LIBRARY" \
   bash -c 'source "$HEAVY_TEST_LOCK_LIBRARY"; heavy_job_lock_acquire "$RELATION_DETECTOR_HEAVY_JOB_LOCK_DIR" child' \
   >"$TMP_DIR/wrong-path.out" 2>"$TMP_DIR/wrong-path.err"; then
  echo "inherited token unexpectedly changed lock path" >&2
  exit 1
fi

heavy_job_lock_release
require_absent "$BORROWED_LOCK"

echo "heavy job lock tests passed"
