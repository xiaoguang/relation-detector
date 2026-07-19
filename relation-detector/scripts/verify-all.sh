#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

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

python3 - "$VERIFY_DIR/environment.json" "$COMMIT" "$BRANCH" "$ORIGIN_MAIN" <<'PY'
import json
import platform
import subprocess
import sys

output, commit, branch, origin_main = sys.argv[1:]

def command(*args):
    return subprocess.run(args, check=True, text=True, stdout=subprocess.PIPE).stdout.strip()

data = {
    "commit": commit,
    "branch": branch,
    "originMain": origin_main,
    "maven": command("mvn", "-version"),
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
rm -f relation-detector/target/sample-data-parser-cli/summary-with-derived.tsv

mvn -T 2 -Pacceptance \
  -DcorrectnessFixtureProfile=smoke \
  -DcorrectnessFixtureParallelism=6 \
  verify 2>&1 | tee "$MAVEN_LOG"

bash relation-detector/scripts/run-correctness-isolated.sh

SAMPLE_DATA_PARSER_CLI_SKIP_PACKAGE=true \
SAMPLE_DATA_PARSER_CLI_CASE_PARALLELISM="${SAMPLE_DATA_PARSER_CLI_CASE_PARALLELISM:-3}" \
SAMPLE_DATA_PARSER_CLI_SCAN_PARALLELISM="${SAMPLE_DATA_PARSER_CLI_SCAN_PARALLELISM:-2}" \
  bash relation-detector/test-fixtures/examples/sample-data-parser-cli/run-all-sample-data-parsers.sh

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
