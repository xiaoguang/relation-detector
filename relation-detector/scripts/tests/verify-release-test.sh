#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
TMP_DIR="$(mktemp -d)"
RELEASE_LOCK_SESSION="release-lock-test-$$"
RELEASE_INTERRUPT_SESSION="$RELEASE_LOCK_SESSION-interrupt"

cleanup() {
  status=$?
  trap - EXIT
  rm -rf "$TMP_DIR" "$ROOT/relation-detector/target/verification/$RELEASE_LOCK_SESSION" \
    "$ROOT/relation-detector/target/verification/$RELEASE_INTERRUPT_SESSION"
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

require_file() {
  local path="$1"
  if [[ ! -f "$path" ]]; then
    echo "expected file: $path" >&2
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

VERIFY_RELEASE="$ROOT/relation-detector/scripts/verify-release.sh"
MANIFEST_BUILDER="$ROOT/relation-detector/scripts/build-verification-manifest.py"

[[ -x "$VERIFY_RELEASE" ]]
[[ -x "$MANIFEST_BUILDER" ]]
grep -q 'NO_CACHE_STATUS' "$VERIFY_RELEASE"
grep -q 'no-cache acceptance failed' "$VERIFY_RELEASE"
grep -q -- '-DcorrectnessFixtureProfile=smoke' "$VERIFY_RELEASE"
grep -q 'heavy-job-lock.sh' "$VERIFY_RELEASE"
grep -q 'heavy_job_lock_acquire' "$VERIFY_RELEASE"
grep -q 'heavy_job_lock_release' "$VERIFY_RELEASE"
grep -q 'VERIFY_RELEASE_MVN' "$VERIFY_RELEASE"
grep -q 'VERIFY_RELEASE_VERIFY_ALL_RUNNER' "$VERIFY_RELEASE"
grep -q 'VERIFY_RELEASE_STALE_SUMMARY' "$VERIFY_RELEASE"

no_cache_line="$(grep -n '^run_no_cache_acceptance$' "$VERIFY_RELEASE" | head -n 1 | cut -d: -f1)"
verify_all_line="$(grep -n '^run_active_child env' "$VERIFY_RELEASE" | head -n 1 | cut -d: -f1)"
lock_line="$(grep -n 'heavy_job_lock_acquire' "$VERIFY_RELEASE" | head -n 1 | cut -d: -f1)"
if [[ -z "$no_cache_line" || -z "$verify_all_line" || "$no_cache_line" -ge "$verify_all_line" ]]; then
  echo "no-cache acceptance must execute before verify-all" >&2
  exit 1
fi
if [[ -z "$lock_line" || "$lock_line" -ge "$no_cache_line" ]]; then
  echo "release lock must be acquired before no-cache acceptance" >&2
  exit 1
fi
stale_summary_cleanup_line="$(grep -n 'rm -f "\$STALE_SUMMARY"' \
  "$VERIFY_RELEASE" | head -n 1 | cut -d: -f1)"
if [[ -z "$stale_summary_cleanup_line" || "$stale_summary_cleanup_line" -ge "$no_cache_line" ]]; then
  echo "stale summary must be removed before no-cache acceptance" >&2
  exit 1
fi

mkdir -p "$TMP_DIR/bin"
cat >"$TMP_DIR/bin/git" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
case "${1:-} ${2:-}" in
  'status --porcelain') exit 0 ;;
  'rev-parse HEAD') echo test-head ;;
  'branch --show-current') echo test-branch ;;
  *) echo "unexpected git invocation: $*" >&2; exit 91 ;;
esac
SH
cat >"$TMP_DIR/mvn-stub" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
[[ -d "$RELATION_DETECTOR_HEAVY_JOB_LOCK_DIR" ]]
[[ "$(cat "$RELATION_DETECTOR_HEAVY_JOB_LOCK_DIR/token")" == "$RELATION_DETECTOR_HEAVY_JOB_LOCK_TOKEN" ]]
touch "$VERIFY_RELEASE_TEST_MAVEN"
SH
cat >"$TMP_DIR/verify-all-stub" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
source "$VERIFY_RELEASE_TEST_LOCK_LIBRARY"
heavy_job_lock_acquire "$RELATION_DETECTOR_HEAVY_JOB_LOCK_DIR" verify-all
[[ "$HEAVY_JOB_LOCK_BORROWED" == true ]]
heavy_job_lock_release
[[ -d "$RELATION_DETECTOR_HEAVY_JOB_LOCK_DIR" ]]
touch "$VERIFY_RELEASE_TEST_CHILD"
exit 42
SH
chmod +x "$TMP_DIR/bin/git" "$TMP_DIR/mvn-stub" "$TMP_DIR/verify-all-stub"

set +e
PATH="$TMP_DIR/bin:$PATH" \
VERIFY_SESSION_ID="$RELEASE_LOCK_SESSION" \
RELATION_DETECTOR_HEAVY_JOB_LOCK_DIR="$TMP_DIR/release.lock" \
VERIFY_RELEASE_MVN="$TMP_DIR/mvn-stub" \
VERIFY_RELEASE_VERIFY_ALL_RUNNER="$TMP_DIR/verify-all-stub" \
VERIFY_RELEASE_STALE_SUMMARY="$TMP_DIR/stale-summary.tsv" \
VERIFY_RELEASE_TEST_LOCK_LIBRARY="$ROOT/relation-detector/scripts/heavy-job-lock.sh" \
VERIFY_RELEASE_TEST_MAVEN="$TMP_DIR/release-maven" \
VERIFY_RELEASE_TEST_CHILD="$TMP_DIR/release-child" \
  "$VERIFY_RELEASE" >"$TMP_DIR/release.out" 2>"$TMP_DIR/release.err"
release_status=$?
set -e
if [[ "$release_status" -ne 42 ]]; then
  cat "$TMP_DIR/release.err" >&2
  require_equal 42 "$release_status"
fi
require_file "$TMP_DIR/release-maven"
require_file "$TMP_DIR/release-child"
require_absent "$TMP_DIR/release.lock"

cat >"$TMP_DIR/mvn-blocking" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
sleep 300 &
child=$!
printf '%s\n' "$child" >"$VERIFY_RELEASE_TEST_BLOCKING_CHILD"
wait "$child"
SH
cat >"$TMP_DIR/mvn-invoked" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
touch "$VERIFY_RELEASE_TEST_CORRECTNESS_INVOKED"
SH
cat >"$TMP_DIR/sample-invoked" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
touch "$VERIFY_RELEASE_TEST_SAMPLE_INVOKED"
SH
chmod +x "$TMP_DIR/mvn-blocking" "$TMP_DIR/mvn-invoked" "$TMP_DIR/sample-invoked"

PATH="$TMP_DIR/bin:$PATH" \
VERIFY_SESSION_ID="$RELEASE_INTERRUPT_SESSION" \
RELATION_DETECTOR_HEAVY_JOB_LOCK_DIR="$TMP_DIR/release-interrupt.lock" \
VERIFY_RELEASE_MVN="$TMP_DIR/mvn-blocking" \
VERIFY_RELEASE_VERIFY_ALL_RUNNER="$TMP_DIR/verify-all-stub" \
VERIFY_RELEASE_STALE_SUMMARY="$TMP_DIR/interrupt-stale-summary.tsv" \
VERIFY_RELEASE_TEST_BLOCKING_CHILD="$TMP_DIR/release-blocking-child.pid" \
  "$VERIFY_RELEASE" >"$TMP_DIR/release-interrupt.out" 2>"$TMP_DIR/release-interrupt.err" &
release_pid=$!

for _ in 1 2 3 4 5; do
  [[ -f "$TMP_DIR/release-blocking-child.pid" ]] && break
  sleep 1
done
require_file "$TMP_DIR/release-blocking-child.pid"
release_child_pid="$(cat "$TMP_DIR/release-blocking-child.pid")"

set +e
RELATION_DETECTOR_HEAVY_JOB_LOCK_DIR="$TMP_DIR/release-interrupt.lock" \
CORRECTNESS_MVN="$TMP_DIR/mvn-invoked" \
CORRECTNESS_OUTPUT_DIR="$TMP_DIR/rejected-correctness-output" \
CORRECTNESS_RUN_SUMMARY="$TMP_DIR/rejected-correctness-summary.json" \
CORRECTNESS_SKIP_PROCESS_GUARD=true \
VERIFY_RELEASE_TEST_CORRECTNESS_INVOKED="$TMP_DIR/correctness-invoked" \
  "$ROOT/relation-detector/scripts/run-correctness-isolated.sh" \
  >"$TMP_DIR/rejected-correctness.out" 2>"$TMP_DIR/rejected-correctness.err"
correctness_status=$?

RELATION_DETECTOR_HEAVY_JOB_LOCK_DIR="$TMP_DIR/release-interrupt.lock" \
SAMPLE_DATA_PARSER_CLI_GROUP_RUNNER="$TMP_DIR/sample-invoked" \
SAMPLE_DATA_PARSER_CLI_SKIP_PACKAGE=true \
SAMPLE_DATA_PARSER_CLI_SKIP_PROCESS_GUARD=true \
SAMPLE_DATA_PARSER_CLI_OUT="$TMP_DIR/rejected-sample-output" \
VERIFY_RELEASE_TEST_SAMPLE_INVOKED="$TMP_DIR/sample-invoked-marker" \
  "$ROOT/relation-detector/scripts/run-sample-data-isolated.sh" common-token-event-sample-data \
  >"$TMP_DIR/rejected-sample.out" 2>"$TMP_DIR/rejected-sample.err"
sample_status=$?
set -e
require_equal 1 "$correctness_status"
require_equal 73 "$sample_status"
require_absent "$TMP_DIR/correctness-invoked"
require_absent "$TMP_DIR/sample-invoked-marker"

kill -TERM "$release_pid"
for _ in 1 2 3 4 5; do
  kill -0 "$release_pid" 2>/dev/null || break
  sleep 1
done
if kill -0 "$release_pid" 2>/dev/null; then
  kill -KILL "$release_pid" 2>/dev/null || true
  kill -KILL "$release_child_pid" 2>/dev/null || true
  echo "verify-release did not exit after interruption" >&2
  exit 1
fi
wait "$release_pid" 2>/dev/null || true
if kill -0 "$release_child_pid" 2>/dev/null; then
  kill -KILL "$release_child_pid" 2>/dev/null || true
  echo "verify-release left a Maven descendant after interruption" >&2
  exit 1
fi
require_absent "$TMP_DIR/release-interrupt.lock"

mkdir -p "$TMP_DIR/results" "$TMP_DIR/verification"
cat >"$TMP_DIR/result.json" <<'JSON'
{
  "summary": {
    "directRelationshipCount": 0,
    "derivedRelationshipCount": 0,
    "totalRelationshipCount": 0,
    "directDataLineageCount": 0,
    "derivedDataLineageCount": 0,
    "totalDataLineageCount": 0,
    "directNamingEvidenceCount": 0,
    "derivedNamingEvidenceCount": 0,
    "totalNamingEvidenceCount": 0,
    "warningCount": 0
  },
  "relationships": [],
  "dataLineages": [],
  "derivedRelationships": [],
  "derivedDataLineages": [],
  "namingEvidence": [],
  "derivedNamingEvidence": [],
  "warnings": []
}
JSON
cp "$TMP_DIR/result.json" "$TMP_DIR/results/example.json"
cp "$TMP_DIR/result.json" "$TMP_DIR/results/example-derived-fresh.json"

cat >"$TMP_DIR/correctness.json" <<'JSON'
{"profile":"full","discovered":1,"selected":1,"executed":1,"passed":1,"failed":0}
JSON
cat >"$TMP_DIR/parity.tsv" <<'TSV'
Pair	Token	Full	TokenOnly	FullOnly
mysql	1	1	0	0
postgres	1	1	0	0
oracle	1	1	0	0
sqlserver	1	1	0	0
TSV
cat >"$TMP_DIR/warnings.tsv" <<'TSV'
parser	warningCode	count
example	NONE	0
TSV
cat >"$TMP_DIR/fingerprints.tsv" <<'TSV'
aaa	example.json
bbb	example-derived-fresh.json
TSV
cp "$TMP_DIR/fingerprints.tsv" "$TMP_DIR/semantic-fingerprints.tsv"
printf '{"java":"test"}\n' >"$TMP_DIR/verification/environment.json"

python3 "$MANIFEST_BUILDER" \
  --verification-dir "$TMP_DIR/verification" \
  --results-dir "$TMP_DIR/results" \
  --correctness-summary "$TMP_DIR/correctness.json" \
  --observation-parity "$TMP_DIR/parity.tsv" \
  --warning-codes "$TMP_DIR/warnings.tsv" \
  --fingerprints "$TMP_DIR/fingerprints.tsv" \
  --semantic-fingerprints "$TMP_DIR/semantic-fingerprints.tsv" \
  --commit test-commit \
  --branch test-branch \
  --origin-main test-commit \
  --worktree-clean true \
  --maven-status 0 \
  --expected-fixtures 1 \
  --expected-categories 1 \
  --expected-json 2 \
  --artifact "$TMP_DIR/verification/environment.json" \
  --output "$TMP_DIR/verification/verification-manifest.json"

jq -e '
  .status == "PASS" and
  .correctness.executed == 1 and
  .parserMatrix.categories == 1 and
  .parserMatrix.jsonFiles == 2 and
  .diagnostics.total == 0 and
  .observationParity.differenceCount == 0 and
  .integrity.evidenceRefs == "PASS" and
  .integrity.sourcePaths == "PASS" and
  .integrity.sourceLines == "PASS" and
  .integrity.derivedCycles == "PASS" and
  (.artifacts | length) == 6 and
  any(.artifacts[]; .path == "environment.json")
' "$TMP_DIR/verification/verification-manifest.json" >/dev/null

cat >"$TMP_DIR/parity-bad.tsv" <<'TSV'
Pair	Token	Full	TokenOnly	FullOnly
mysql	1	2	0	1
postgres	1	1	0	0
oracle	1	1	0	0
sqlserver	1	1	0	0
TSV

if python3 "$MANIFEST_BUILDER" \
  --verification-dir "$TMP_DIR/verification" \
  --results-dir "$TMP_DIR/results" \
  --correctness-summary "$TMP_DIR/correctness.json" \
  --observation-parity "$TMP_DIR/parity-bad.tsv" \
  --warning-codes "$TMP_DIR/warnings.tsv" \
  --fingerprints "$TMP_DIR/fingerprints.tsv" \
  --semantic-fingerprints "$TMP_DIR/semantic-fingerprints.tsv" \
  --commit test-commit --branch test-branch --origin-main test-commit \
  --worktree-clean true --maven-status 0 \
  --expected-fixtures 1 --expected-categories 1 --expected-json 2 \
  --output "$TMP_DIR/verification/bad-manifest.json" >/dev/null 2>&1; then
  echo "non-zero observation parity must fail manifest generation" >&2
  exit 1
fi

echo "verify release test passed"
