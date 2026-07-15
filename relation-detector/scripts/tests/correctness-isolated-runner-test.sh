#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
RUNNER="$ROOT/relation-detector/scripts/run-correctness-isolated.sh"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/correctness-isolated.XXXXXX")"

cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

cat >"$TMP_DIR/mvn-success" <<'SH'
#!/usr/bin/env bash
set -euo pipefail

profile=""
for argument in "$@"; do
  case "$argument" in
    -DcorrectnessFixtureProfile=*) profile="${argument#*=}" ;;
  esac
done

[[ -n "$profile" ]]
[[ " $* " == *" -DcorrectnessFixtureParallelism=6 "* ]]
[[ " $* " == *" -Dmaven.build.cache.enabled=false "* ]]
[[ " $* " == *" -DupdateCorrectnessGold=false "* ]]
[[ " $* " == *" -DargLine=-Xms1g -Xmx6g -XX:+UseG1GC "* ]]
printf '%s\n' "$profile" >>"$CORRECTNESS_TEST_INVOCATIONS"

case "$profile" in
  common) categories='["common/root"]' ;;
  mysql) categories='["mysql/root","mysql/5_7","mysql/8_0"]' ;;
  postgres) categories='["postgres/root","postgres/16","postgres/17","postgres/18"]' ;;
  oracle-root) categories='["oracle/root"]' ;;
  oracle/v12c) categories='["oracle/12c"]' ;;
  oracle/v19c) categories='["oracle/19c"]' ;;
  oracle/v21c) categories='["oracle/21c"]' ;;
  oracle/v26ai) categories='["oracle/26ai"]' ;;
  sqlserver) categories='["sqlserver/root","sqlserver/2016","sqlserver/2017","sqlserver/2019","sqlserver/2022","sqlserver/2025"]' ;;
  *) exit 31 ;;
esac

python3 - "$CORRECTNESS_RUN_SUMMARY" "$profile" "$categories" <<'PY'
import json
import sys

output, profile, categories_json = sys.argv[1:]
categories = json.loads(categories_json)
groups = [
    {"id": category, "executed": 1, "passed": 1, "failed": 0, "elapsedMillis": 10}
    for category in categories
]
summary = {
    "profile": profile,
    "discovered": 19,
    "selected": len(groups),
    "executed": len(groups),
    "passed": len(groups),
    "failed": 0,
    "elapsedMillis": len(groups) * 10,
    "dialectVersions": groups,
}
with open(output, "w", encoding="utf-8") as handle:
    json.dump(summary, handle)
PY
SH
chmod +x "$TMP_DIR/mvn-success"

CORRECTNESS_MVN="$TMP_DIR/mvn-success" \
CORRECTNESS_OUTPUT_DIR="$TMP_DIR/output" \
CORRECTNESS_RUN_SUMMARY="$TMP_DIR/current-summary.json" \
CORRECTNESS_LOCK_DIR="$TMP_DIR/lock" \
CORRECTNESS_TEST_INVOCATIONS="$TMP_DIR/invocations.txt" \
CORRECTNESS_SKIP_PROCESS_GUARD=true \
  "$RUNNER" >/dev/null

cat >"$TMP_DIR/expected-invocations.txt" <<'EOF'
common
mysql
postgres
oracle-root
oracle/v12c
oracle/v19c
oracle/v21c
oracle/v26ai
sqlserver
EOF
cmp "$TMP_DIR/expected-invocations.txt" "$TMP_DIR/invocations.txt"

jq -e '.profile == "full-isolated" and .discovered == 19 and
       .selected == 19 and .executed == 19 and .passed == 19 and .failed == 0 and
       (.dialectVersions | length) == 19' \
  "$TMP_DIR/output/correctness-run-summary.json" >/dev/null

if CORRECTNESS_MVN="$TMP_DIR/mvn-success" \
    CORRECTNESS_OUTPUT_DIR="$TMP_DIR/rejected-output" \
    CORRECTNESS_RUN_SUMMARY="$TMP_DIR/rejected-summary.json" \
    CORRECTNESS_LOCK_DIR="$TMP_DIR/rejected-lock" \
    CORRECTNESS_TEST_INVOCATIONS="$TMP_DIR/rejected-invocations.txt" \
    CORRECTNESS_SKIP_PROCESS_GUARD=true \
    CORRECTNESS_PARALLELISM=12 \
      "$RUNNER" >/dev/null 2>&1; then
  echo "parallelism outside 4-8 unexpectedly succeeded" >&2
  exit 1
fi

cat >"$TMP_DIR/mvn-blocking" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
sleep 300 &
child=$!
printf '%s\n' "$child" >"$CORRECTNESS_BLOCKING_CHILD_PID"
wait "$child"
SH
chmod +x "$TMP_DIR/mvn-blocking"

CORRECTNESS_MVN="$TMP_DIR/mvn-blocking" \
CORRECTNESS_OUTPUT_DIR="$TMP_DIR/blocking-output" \
CORRECTNESS_RUN_SUMMARY="$TMP_DIR/blocking-summary.json" \
CORRECTNESS_LOCK_DIR="$TMP_DIR/blocking-lock" \
CORRECTNESS_BLOCKING_CHILD_PID="$TMP_DIR/blocking-child.pid" \
CORRECTNESS_SKIP_PROCESS_GUARD=true \
  "$RUNNER" >/dev/null 2>&1 &
runner_pid=$!

for _ in 1 2 3 4 5; do
  [[ -f "$TMP_DIR/blocking-child.pid" ]] && break
  sleep 1
done
[[ -f "$TMP_DIR/blocking-child.pid" ]]
blocking_child_pid="$(cat "$TMP_DIR/blocking-child.pid")"

kill -TERM "$runner_pid"
wait "$runner_pid" 2>/dev/null || true
for _ in 1 2 3 4 5; do
  kill -0 "$blocking_child_pid" 2>/dev/null || break
  sleep 1
done
if kill -0 "$blocking_child_pid" 2>/dev/null; then
  echo "runner left a descendant process after interruption: $blocking_child_pid" >&2
  kill -KILL "$blocking_child_pid" 2>/dev/null || true
  exit 1
fi

echo "isolated correctness runner tests passed"
