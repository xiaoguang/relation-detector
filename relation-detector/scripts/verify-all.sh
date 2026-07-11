#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

SESSION_ID="$(date -u +%Y%m%dT%H%M%SZ)"
SESSION_START="$(date +%s)"
VERIFY_DIR="$ROOT/relation-detector/target/verification/$SESSION_ID"
MAVEN_LOG="$VERIFY_DIR/acceptance.log"
mkdir -p "$VERIFY_DIR"

mvn -T 2 -Pacceptance verify 2>&1 | tee "$MAVEN_LOG"

SAMPLE_DATA_PARSER_CLI_SKIP_PACKAGE=true \
SAMPLE_DATA_PARSER_CLI_CASE_PARALLELISM="${SAMPLE_DATA_PARSER_CLI_CASE_PARALLELISM:-3}" \
SAMPLE_DATA_PARSER_CLI_SCAN_PARALLELISM="${SAMPLE_DATA_PARSER_CLI_SCAN_PARALLELISM:-2}" \
  bash relation-detector/test-fixtures/examples/sample-data-parser-cli/run-all-sample-data-parsers.sh

python3 relation-detector/scripts/validate-sample-data-results.py \
  relation-detector/target/sample-data-parser-cli/results

python3 relation-detector/scripts/canonical-json-fingerprint.py \
  relation-detector/target/sample-data-parser-cli/results >"$VERIFY_DIR/fingerprints.tsv"

python3 relation-detector/scripts/build-performance-report.py \
  --session-start "$SESSION_START" \
  --surefire-root "$ROOT" \
  --cli-log-root "$ROOT/relation-detector/target/sample-data-parser-cli/logs" \
  --cli-report "$ROOT/relation-detector/target/sample-data-parser-cli/batch-report.json" \
  --correctness-summary "$ROOT/relation-detector/target/correctness-run-summary.json" \
  --fingerprints "$VERIFY_DIR/fingerprints.tsv" \
  --maven-log "$MAVEN_LOG" \
  --output "$VERIFY_DIR/performance-report.json"

echo "Verification report: $VERIFY_DIR/performance-report.json"
echo "Canonical fingerprints: $VERIFY_DIR/fingerprints.tsv"

if [[ "${VERIFY_SAMPLE_DATA_CONCURRENCY:-false}" == "true" ]]; then
  bash relation-detector/scripts/verify-sample-data-parser-concurrency.sh
fi
