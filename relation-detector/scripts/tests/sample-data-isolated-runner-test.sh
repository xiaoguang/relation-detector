#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
RUNNER="$ROOT/relation-detector/scripts/run-sample-data-isolated.sh"
CASE_RUNNER="$ROOT/relation-detector/test-fixtures/examples/sample-data-parser-cli/run-all-sample-data-parsers.sh"
TMP_BASE="$(cd "${TMPDIR:-/tmp}" && pwd -P)"
TMP_DIR="$(mktemp -d "$TMP_BASE/sample-data-isolated.XXXXXX")"

cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

cat >"$TMP_DIR/group-worker" <<'SH'
#!/usr/bin/env bash
set -euo pipefail

printf '%s|%s|%s|%s\n' \
  "${SAMPLE_DATA_PARSER_CLI_GROUP_ID:-finalize}" \
  "${JAVA_TOOL_OPTIONS:-}" \
  "${SAMPLE_DATA_PARSER_CLI_CASE_PARALLELISM:-}" \
  "$*" >>"$SAMPLE_DATA_TEST_INVOCATIONS"

if [[ "${SAMPLE_DATA_PARSER_CLI_SKIP_BATCH:-false}" == "true" ]]; then
  mkdir -p "$SAMPLE_DATA_PARSER_CLI_OUT"
  printf 'parser\tfixtures\n' >"$SAMPLE_DATA_PARSER_CLI_OUT/summary.tsv"
  printf 'Parser\tFix\n' >"$SAMPLE_DATA_PARSER_CLI_OUT/summary-with-derived.tsv"
  printf 'parser\twarningCode\tcount\n' >"$SAMPLE_DATA_PARSER_CLI_OUT/warning-codes.tsv"
  printf 'Pair\tToken\tFull\tTokenOnly\tFullOnly\n' >"$SAMPLE_DATA_PARSER_CLI_OUT/observation-parity.tsv"
  exit 0
fi

mkdir -p "$(dirname "$SAMPLE_DATA_PARSER_CLI_BATCH_REPORT")" \
  "$SAMPLE_DATA_PARSER_CLI_OUT/results"

python3 - "$SAMPLE_DATA_PARSER_CLI_BATCH_REPORT" "$SAMPLE_DATA_PARSER_CLI_OUT" "$@" <<'PY'
import json
import sys
from pathlib import Path

report = Path(sys.argv[1])
output_root = Path(sys.argv[2])
case_ids = sys.argv[3:]
cases = []
for case_id in case_ids:
    bundle = output_root / "results" / case_id
    bundle.mkdir(parents=True, exist_ok=True)
    direct = bundle / "direct.json"
    result = bundle / "result.json"
    direct.write_text("{}\n", encoding="utf-8")
    result.write_text("{}\n", encoding="utf-8")
    cases.append({
        "id": case_id,
        "status": "SUCCESS",
        "elapsedMillis": 1,
        "outputBundle": str(bundle),
    })
payload = {
    "artifactSchemaVersion": 2,
    "summary": {
        "caseCount": len(cases),
        "successCount": len(cases),
        "failedCount": 0,
        "skippedCount": 0,
    },
    "cases": cases,
}
report.write_text(json.dumps(payload), encoding="utf-8")
PY
SH
chmod +x "$TMP_DIR/group-worker"

mkdir -p "$TMP_DIR/output/results/common-token-event-sample-data"
printf 'old result\n' >"$TMP_DIR/output/results/common-token-event-sample-data/result.json"
printf 'old direct\n' >"$TMP_DIR/output/results/common-token-event-sample-data/direct.json"

SAMPLE_DATA_PARSER_CLI_GROUP_RUNNER="$TMP_DIR/group-worker" \
SAMPLE_DATA_PARSER_CLI_SKIP_PACKAGE=true \
SAMPLE_DATA_PARSER_CLI_OUT="$TMP_DIR/output" \
SAMPLE_DATA_PARSER_CLI_LOCK_DIR="$TMP_DIR/lock" \
SAMPLE_DATA_PARSER_CLI_SKIP_PROCESS_GUARD=true \
SAMPLE_DATA_TEST_INVOCATIONS="$TMP_DIR/invocations.txt" \
  "$RUNNER" >/dev/null

cut -d'|' -f1 "$TMP_DIR/invocations.txt" >"$TMP_DIR/group-order.txt"
cat >"$TMP_DIR/expected-groups.txt" <<'EOF'
common
mysql
postgres
oracle-root
oracle-v12c
oracle-v19c
oracle-v21c
oracle-v26ai
sqlserver
finalize
EOF
cmp "$TMP_DIR/expected-groups.txt" "$TMP_DIR/group-order.txt"

if head -9 "$TMP_DIR/invocations.txt" | grep -v '|-Xms1g -Xmx6g -XX:+UseG1GC|1|' | grep -q .; then
  echo "group runner did not receive the 6g heap and safe default case parallelism" >&2
  exit 1
fi

jq -e '.summary.caseCount == 19 and .summary.successCount == 19 and
       .summary.failedCount == 0 and .summary.skippedCount == 0 and
       (.cases | length) == 19' \
  "$TMP_DIR/output/batch-report.json" >/dev/null

if [[ "$(find "$TMP_DIR/output/results" -mindepth 2 -maxdepth 2 -name '*.json' | wc -l | tr -d ' ')" != "38" ]]; then
  echo "isolated runner did not preserve all 38 direct/derived outputs" >&2
  exit 1
fi
[[ "$(find "$TMP_DIR/output/results" -mindepth 1 -maxdepth 1 -type d | wc -l | tr -d ' ')" == "19" ]]
: >"$TMP_DIR/subset-invocations.txt"
SAMPLE_DATA_PARSER_CLI_GROUP_RUNNER="$TMP_DIR/group-worker" \
SAMPLE_DATA_PARSER_CLI_SKIP_PACKAGE=true \
SAMPLE_DATA_PARSER_CLI_OUT="$TMP_DIR/subset-output" \
SAMPLE_DATA_PARSER_CLI_LOCK_DIR="$TMP_DIR/subset-lock" \
SAMPLE_DATA_PARSER_CLI_SKIP_PROCESS_GUARD=true \
SAMPLE_DATA_TEST_INVOCATIONS="$TMP_DIR/subset-invocations.txt" \
  "$RUNNER" mysql-v8_0-full >/dev/null

cut -d'|' -f1 "$TMP_DIR/subset-invocations.txt" >"$TMP_DIR/subset-groups.txt"
printf 'mysql\nfinalize\n' >"$TMP_DIR/expected-subset-groups.txt"
cmp "$TMP_DIR/expected-subset-groups.txt" "$TMP_DIR/subset-groups.txt"
jq -e '.summary.caseCount == 1 and .summary.successCount == 1 and
       .cases[0].id == "mysql-v8_0-full"' \
  "$TMP_DIR/subset-output/batch-report.json" >/dev/null
[[ "$(find "$TMP_DIR/subset-output/results" -mindepth 2 -maxdepth 2 -name '*.json' | wc -l | tr -d ' ')" == "2" ]]
[[ -f "$TMP_DIR/subset-output/results/mysql-v8_0-full/result.json" ]]
[[ -f "$TMP_DIR/subset-output/results/mysql-v8_0-full/direct.json" ]]

mkdir -p "$TMP_DIR/extra-output/results/mysql-v8_0-full"
printf '{}\n' >"$TMP_DIR/extra-output/results/mysql-v8_0-full/result.json"
printf '{}\n' >"$TMP_DIR/extra-output/results/mysql-v8_0-full/direct.json"
printf 'keep\n' >"$TMP_DIR/extra-output/results/mysql-v8_0-full/extra.txt"
if SAMPLE_DATA_PARSER_CLI_GROUP_RUNNER="$TMP_DIR/group-worker" \
  SAMPLE_DATA_PARSER_CLI_SKIP_PACKAGE=true \
  SAMPLE_DATA_PARSER_CLI_OUT="$TMP_DIR/extra-output" \
  SAMPLE_DATA_PARSER_CLI_LOCK_DIR="$TMP_DIR/extra-lock" \
  SAMPLE_DATA_PARSER_CLI_SKIP_PROCESS_GUARD=true \
  SAMPLE_DATA_TEST_INVOCATIONS="$TMP_DIR/extra-invocations.txt" \
    "$RUNNER" mysql-v8_0-full >/dev/null 2>&1; then
  echo "sample-data runner accepted an output bundle with extra content" >&2
  exit 1
fi
[[ -f "$TMP_DIR/extra-output/results/mysql-v8_0-full/extra.txt" ]]
[[ -f "$TMP_DIR/extra-output/results/mysql-v8_0-full/result.json" ]]
[[ -f "$TMP_DIR/extra-output/results/mysql-v8_0-full/direct.json" ]]

mkdir -p "$TMP_DIR/symlink-output/results/mysql-v8_0-full"
printf '{}\n' >"$TMP_DIR/symlink-output/results/mysql-v8_0-full/result.json"
printf '{}\n' >"$TMP_DIR/symlink-output/results/mysql-v8_0-full/direct.json"
printf 'outside\n' >"$TMP_DIR/outside.txt"
ln -s "$TMP_DIR/outside.txt" "$TMP_DIR/symlink-output/results/mysql-v8_0-full/link.json"
if SAMPLE_DATA_PARSER_CLI_GROUP_RUNNER="$TMP_DIR/group-worker" \
  SAMPLE_DATA_PARSER_CLI_SKIP_PACKAGE=true \
  SAMPLE_DATA_PARSER_CLI_OUT="$TMP_DIR/symlink-output" \
  SAMPLE_DATA_PARSER_CLI_LOCK_DIR="$TMP_DIR/symlink-lock" \
  SAMPLE_DATA_PARSER_CLI_SKIP_PROCESS_GUARD=true \
  SAMPLE_DATA_TEST_INVOCATIONS="$TMP_DIR/symlink-invocations.txt" \
    "$RUNNER" mysql-v8_0-full >/dev/null 2>&1; then
  echo "sample-data runner accepted an output bundle with a symlink" >&2
  exit 1
fi
[[ -L "$TMP_DIR/symlink-output/results/mysql-v8_0-full/link.json" ]]
[[ -f "$TMP_DIR/symlink-output/results/mysql-v8_0-full/result.json" ]]
[[ -f "$TMP_DIR/symlink-output/results/mysql-v8_0-full/direct.json" ]]

mkdir -p "$TMP_DIR/legacy-output/results/mysql-v8_0-full"
printf '{}\n' >"$TMP_DIR/legacy-output/results/mysql-v8_0-full/result.json"
printf '{}\n' >"$TMP_DIR/legacy-output/results/mysql-v8_0-full/direct.json"
printf 'legacy\n' >"$TMP_DIR/legacy-output/results/mysql-v8_0-full-derived-fresh.json"
if SAMPLE_DATA_PARSER_CLI_GROUP_RUNNER="$TMP_DIR/group-worker" \
  SAMPLE_DATA_PARSER_CLI_SKIP_PACKAGE=true \
  SAMPLE_DATA_PARSER_CLI_OUT="$TMP_DIR/legacy-output" \
  SAMPLE_DATA_PARSER_CLI_LOCK_DIR="$TMP_DIR/legacy-lock" \
  SAMPLE_DATA_PARSER_CLI_SKIP_PROCESS_GUARD=true \
  SAMPLE_DATA_TEST_INVOCATIONS="$TMP_DIR/legacy-invocations.txt" \
    "$RUNNER" mysql-v8_0-full >/dev/null 2>&1; then
  echo "sample-data runner accepted a legacy flat result" >&2
  exit 1
fi
[[ -f "$TMP_DIR/legacy-output/results/mysql-v8_0-full-derived-fresh.json" ]]
[[ -f "$TMP_DIR/legacy-output/results/mysql-v8_0-full/result.json" ]]
[[ -f "$TMP_DIR/legacy-output/results/mysql-v8_0-full/direct.json" ]]

mkdir -p "$TMP_DIR/ancestor-real/results/mysql-v8_0-full"
printf '{}\n' >"$TMP_DIR/ancestor-real/results/mysql-v8_0-full/result.json"
printf '{}\n' >"$TMP_DIR/ancestor-real/results/mysql-v8_0-full/direct.json"
ln -s "$TMP_DIR/ancestor-real" "$TMP_DIR/ancestor-link"
if SAMPLE_DATA_PARSER_CLI_GROUP_RUNNER="$TMP_DIR/group-worker" \
  SAMPLE_DATA_PARSER_CLI_SKIP_PACKAGE=true \
  SAMPLE_DATA_PARSER_CLI_OUT="$TMP_DIR/ancestor-link" \
  SAMPLE_DATA_PARSER_CLI_LOCK_DIR="$TMP_DIR/ancestor-lock" \
  SAMPLE_DATA_PARSER_CLI_SKIP_PROCESS_GUARD=true \
  SAMPLE_DATA_TEST_INVOCATIONS="$TMP_DIR/ancestor-invocations.txt" \
    "$RUNNER" mysql-v8_0-full >/dev/null 2>&1; then
  echo "sample-data runner followed a result-directory ancestor symlink" >&2
  exit 1
fi
[[ -L "$TMP_DIR/ancestor-link" ]]
[[ -f "$TMP_DIR/ancestor-real/results/mysql-v8_0-full/result.json" ]]
[[ -f "$TMP_DIR/ancestor-real/results/mysql-v8_0-full/direct.json" ]]

cat >"$TMP_DIR/fake-verification" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
shift
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --summary|--derived-summary|--warnings)
      target="$2"
      mkdir -p "$(dirname "$target")"
      : >"$target"
      shift 2
      ;;
    *)
      shift
      ;;
  esac
done
SH
chmod +x "$TMP_DIR/fake-verification"

SAMPLE_DATA_PARSER_CLI_GROUP_WORKER=true \
SAMPLE_DATA_PARSER_CLI_SKIP_PACKAGE=true \
SAMPLE_DATA_PARSER_CLI_SKIP_JLS_CHECK=true \
SAMPLE_DATA_PARSER_CLI_SKIP_BATCH=true \
SAMPLE_DATA_PARSER_CLI_INCLUDE_DERIVED=false \
SAMPLE_DATA_PARSER_CLI_OUT="$TMP_DIR/case-output" \
SAMPLE_DATA_PARSER_CLI_LOCK_DIR="$TMP_DIR/case-lock" \
RELATION_DETECTOR_VERIFICATION_RUNNER="$TMP_DIR/fake-verification" \
  "$CASE_RUNNER" mysql-v8_0-full >/dev/null

[[ "$(find "$TMP_DIR/case-output/configs" -type f -name '*.yml' | wc -l | tr -d ' ')" == "1" ]]
[[ -f "$TMP_DIR/case-output/configs/mysql-v8_0-full.yml" ]]
! grep -q 'derivedPaths:' "$TMP_DIR/case-output/configs/mysql-v8_0-full.yml"
grep -q '^version: 2$' "$TMP_DIR/case-output/batch.yml"
grep -q 'outputBundle: .*/results/mysql-v8_0-full$' "$TMP_DIR/case-output/batch.yml"
! grep -q 'directOutput:' "$TMP_DIR/case-output/batch.yml"
! grep -q '^    output:' "$TMP_DIR/case-output/batch.yml"

SAMPLE_DATA_PARSER_CLI_GROUP_WORKER=true \
SAMPLE_DATA_PARSER_CLI_SKIP_PACKAGE=true \
SAMPLE_DATA_PARSER_CLI_SKIP_JLS_CHECK=true \
SAMPLE_DATA_PARSER_CLI_SKIP_BATCH=true \
SAMPLE_DATA_PARSER_CLI_OUT="$TMP_DIR/case-derived-output" \
SAMPLE_DATA_PARSER_CLI_LOCK_DIR="$TMP_DIR/case-derived-lock" \
RELATION_DETECTOR_VERIFICATION_RUNNER="$TMP_DIR/fake-verification" \
  "$CASE_RUNNER" mysql-v8_0-full >/dev/null

[[ "$(find "$TMP_DIR/case-derived-output/configs" -type f -name '*.yml' | wc -l | tr -d ' ')" == "1" ]]
grep -q 'derivedPaths:' "$TMP_DIR/case-derived-output/configs/mysql-v8_0-full.yml"
grep -q 'outputBundle: .*/results/mysql-v8_0-full$' "$TMP_DIR/case-derived-output/batch.yml"

cat >"$TMP_DIR/blocking-worker" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
sleep 300 &
child=$!
printf '%s\n' "$child" >"$SAMPLE_DATA_BLOCKING_CHILD_PID"
wait "$child"
SH
chmod +x "$TMP_DIR/blocking-worker"

SAMPLE_DATA_PARSER_CLI_GROUP_RUNNER="$TMP_DIR/blocking-worker" \
SAMPLE_DATA_PARSER_CLI_SKIP_PACKAGE=true \
SAMPLE_DATA_PARSER_CLI_OUT="$TMP_DIR/blocking-output" \
SAMPLE_DATA_PARSER_CLI_LOCK_DIR="$TMP_DIR/blocking-lock" \
SAMPLE_DATA_PARSER_CLI_SKIP_PROCESS_GUARD=true \
SAMPLE_DATA_BLOCKING_CHILD_PID="$TMP_DIR/blocking-child.pid" \
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
  echo "sample-data runner left a descendant process after interruption" >&2
  kill -KILL "$blocking_child_pid" 2>/dev/null || true
  exit 1
fi

echo "sample-data isolated runner tests passed"
