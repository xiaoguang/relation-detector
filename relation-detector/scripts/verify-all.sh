#!/usr/bin/env bash
set -euo pipefail
set -m

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# shellcheck source=heavy-job-lock.sh
source "$ROOT/relation-detector/scripts/heavy-job-lock.sh"
cd "$ROOT"

LOCK_DIR="${RELATION_DETECTOR_HEAVY_JOB_LOCK_DIR:-$ROOT/relation-detector/target/.relation-detector-heavy-job.lock}"
LOCK_JOB="verify-all"
MVN_BIN="${VERIFY_ALL_MVN:-mvn}"
CORRECTNESS_RUNNER="${VERIFY_ALL_CORRECTNESS_RUNNER:-$ROOT/relation-detector/scripts/run-correctness-isolated.sh}"
SAMPLE_DATA_RUNNER="${VERIFY_ALL_SAMPLE_DATA_RUNNER:-$ROOT/relation-detector/test-fixtures/examples/sample-data-parser-cli/run-all-sample-data-parsers.sh}"
STALE_SUMMARY="${VERIFY_ALL_STALE_SUMMARY:-$ROOT/relation-detector/target/sample-data-parser-cli/summary-with-derived.tsv}"
ACTIVE_CHILD_PID=""

terminate_active_process_tree() {
  local root_pid="${ACTIVE_CHILD_PID:-}"
  [[ -n "$root_pid" ]] || return 0
  kill -TERM -- "-$root_pid" 2>/dev/null || true
  local attempt
  for attempt in 1 2 3; do
    kill -0 -- "-$root_pid" 2>/dev/null || break
    sleep 1
  done
  kill -KILL -- "-$root_pid" 2>/dev/null || true
  wait "$root_pid" 2>/dev/null || true
  ACTIVE_CHILD_PID=""
}

wait_for_active_child() {
  local status=0
  wait "$ACTIVE_CHILD_PID" || status=$?
  ACTIVE_CHILD_PID=""
  return "$status"
}

run_active_child() {
  "$@" &
  ACTIVE_CHILD_PID=$!
  wait_for_active_child
}

run_acceptance_smoke() {
  (
    set -o pipefail
    "$MVN_BIN" -T 2 -Pacceptance \
      -DcorrectnessFixtureProfile=smoke \
      -DcorrectnessFixtureParallelism=6 \
      verify 2>&1 | tee "$MAVEN_LOG"
  ) &
  ACTIVE_CHILD_PID=$!
  wait_for_active_child
}

cleanup() {
  status=$?
  trap - EXIT INT TERM
  terminate_active_process_tree
  heavy_job_lock_release || true
  exit "$status"
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

if ! heavy_job_lock_acquire "$LOCK_DIR" "$LOCK_JOB"; then
  exit 1
fi

SESSION_ID="${VERIFY_SESSION_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
SESSION_START="$(date +%s)"
VERIFY_DIR="$ROOT/relation-detector/target/verification/$SESSION_ID"
MAVEN_LOG="$VERIFY_DIR/acceptance.log"
mkdir -p "$VERIFY_DIR"

COMMIT="$(git rev-parse HEAD)"
BRANCH="$(git branch --show-current)"
BRANCH="${BRANCH:-DETACHED}"
ORIGIN_MAIN="$(git rev-parse --verify origin/main 2>/dev/null || printf 'UNAVAILABLE')"
if [[ -z "$(git status --porcelain)" ]]; then
  WORKTREE_CLEAN=true
else
  WORKTREE_CLEAN=false
fi

python3 - "$VERIFY_DIR/environment.json" "$COMMIT" "$BRANCH" "$ORIGIN_MAIN" "$MVN_BIN" <<'PY'
import json
import platform
import subprocess
import sys

output, commit, branch, origin_main, maven_bin = sys.argv[1:]

def command(*args):
    return subprocess.run(args, check=True, text=True, stdout=subprocess.PIPE).stdout.strip()

data = {
    "commit": commit,
    "branch": branch,
    "originMain": origin_main,
    "maven": command(maven_bin, "-version"),
    "platform": platform.platform(),
    "python": platform.python_version(),
}
java = subprocess.run(["java", "-version"], text=True, stdout=subprocess.PIPE,
                      stderr=subprocess.STDOUT, check=True)
data["java"] = java.stdout.strip()
with open(output, "w", encoding="utf-8") as handle:
    json.dump(data, handle, indent=2, sort_keys=True)
    handle.write("\n")
PY

# The comparison test must not consume a summary left by an older or subset CLI run.
# The complete sample-data phase below recreates this file before final validation.
rm -f "$STALE_SUMMARY"

run_acceptance_smoke

run_active_child bash "$CORRECTNESS_RUNNER"

run_active_child env \
  SAMPLE_DATA_PARSER_CLI_SKIP_PACKAGE=true \
  SAMPLE_DATA_PARSER_CLI_CASE_PARALLELISM="${SAMPLE_DATA_PARSER_CLI_CASE_PARALLELISM:-1}" \
  SAMPLE_DATA_PARSER_CLI_SCAN_PARALLELISM="${SAMPLE_DATA_PARSER_CLI_SCAN_PARALLELISM:-2}" \
  bash "$SAMPLE_DATA_RUNNER"

python3 relation-detector/scripts/sync-parser-comparison-summary.py

python3 relation-detector/scripts/validate-sample-data-results.py \
  relation-detector/target/sample-data-parser-cli/results

python3 relation-detector/scripts/canonical-json-fingerprint.py \
  relation-detector/target/sample-data-parser-cli/results >"$VERIFY_DIR/fingerprints.tsv"

python3 relation-detector/scripts/canonical-json-fingerprint.py --semantic \
  relation-detector/target/sample-data-parser-cli/results >"$VERIFY_DIR/semantic-fingerprints.tsv"

cp relation-detector/target/sample-data-parser-cli/summary.tsv "$VERIFY_DIR/summary.tsv"
cp relation-detector/target/sample-data-parser-cli/summary-with-derived.tsv \
  "$VERIFY_DIR/summary-with-derived.tsv"
cp relation-detector/target/sample-data-parser-cli/warning-codes.tsv "$VERIFY_DIR/warning-codes.tsv"
cp relation-detector/target/sample-data-parser-cli/observation-parity.tsv \
  "$VERIFY_DIR/observation-parity.tsv"
cp relation-detector/target/sample-data-parser-cli/batch-report.json "$VERIFY_DIR/batch-report.json"
cp relation-detector/target/correctness-run-summary.json "$VERIFY_DIR/correctness-run-summary.json"
mkdir -p "$VERIFY_DIR/reports"
cp relation-detector/target/generated-reports/correctness-test-summary.md \
  "$VERIFY_DIR/reports/correctness-test-summary.md"
cp relation-detector/target/generated-reports/data-lineage-full-audit.md \
  "$VERIFY_DIR/reports/data-lineage-full-audit.md"

MANIFEST_OPTIONAL_ARGS=(--artifact "$VERIFY_DIR/environment.json")
if [[ -n "${VERIFY_NO_CACHE_STATUS:-}" ]]; then
  MANIFEST_OPTIONAL_ARGS+=(--no-cache-status "$VERIFY_NO_CACHE_STATUS")
fi
if [[ -n "${VERIFY_NO_CACHE_LOG:-}" && -f "$VERIFY_NO_CACHE_LOG" ]]; then
  cp "$VERIFY_NO_CACHE_LOG" "$VERIFY_DIR/no-cache-acceptance.log"
  MANIFEST_OPTIONAL_ARGS+=(--artifact "$VERIFY_DIR/no-cache-acceptance.log")
fi

python3 relation-detector/scripts/build-performance-report.py \
  --session-start "$SESSION_START" \
  --surefire-root "$ROOT" \
  --cli-log-root "$ROOT/relation-detector/target/sample-data-parser-cli/logs" \
  --cli-report "$ROOT/relation-detector/target/sample-data-parser-cli/batch-report.json" \
  --correctness-summary "$ROOT/relation-detector/target/correctness-run-summary.json" \
  --fingerprints "$VERIFY_DIR/fingerprints.tsv" \
  --semantic-fingerprints "$VERIFY_DIR/semantic-fingerprints.tsv" \
  --maven-log "$MAVEN_LOG" \
  --output "$VERIFY_DIR/performance-report.json"

python3 relation-detector/scripts/build-verification-manifest.py \
  --verification-dir "$VERIFY_DIR" \
  --results-dir relation-detector/target/sample-data-parser-cli/results \
  --correctness-summary "$VERIFY_DIR/correctness-run-summary.json" \
  --observation-parity "$VERIFY_DIR/observation-parity.tsv" \
  --warning-codes "$VERIFY_DIR/warning-codes.tsv" \
  --fingerprints "$VERIFY_DIR/fingerprints.tsv" \
  --semantic-fingerprints "$VERIFY_DIR/semantic-fingerprints.tsv" \
  --commit "$COMMIT" \
  --branch "$BRANCH" \
  --origin-main "$ORIGIN_MAIN" \
  --worktree-clean "$WORKTREE_CLEAN" \
  --maven-status 0 \
  "${MANIFEST_OPTIONAL_ARGS[@]}" \
  --artifact "$VERIFY_DIR/acceptance.log" \
  --artifact "$VERIFY_DIR/summary.tsv" \
  --artifact "$VERIFY_DIR/summary-with-derived.tsv" \
  --artifact "$VERIFY_DIR/batch-report.json" \
  --artifact "$VERIFY_DIR/reports/correctness-test-summary.md" \
  --artifact "$VERIFY_DIR/reports/data-lineage-full-audit.md" \
  --artifact "$VERIFY_DIR/performance-report.json" \
  --output "$VERIFY_DIR/verification-manifest.json"

echo "Verification report: $VERIFY_DIR/performance-report.json"
echo "Canonical fingerprints: $VERIFY_DIR/fingerprints.tsv"
echo "Semantic fingerprints: $VERIFY_DIR/semantic-fingerprints.tsv"
echo "Verification manifest: $VERIFY_DIR/verification-manifest.json"

if [[ "${VERIFY_SAMPLE_DATA_CONCURRENCY:-false}" == "true" ]]; then
  bash relation-detector/scripts/verify-sample-data-parser-concurrency.sh
fi
