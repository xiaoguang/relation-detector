#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
VERIFY="$ROOT/relation-detector/scripts/verify-all.sh"
RUNNER="$ROOT/relation-detector/test-fixtures/examples/sample-data-parser-cli/run-all-sample-data-parsers.sh"

grep -q '<id>matrix-smoke</id>' "$ROOT/pom.xml"
grep -q '<id>acceptance</id>' "$ROOT/pom.xml"
[[ "$(grep -Ec '^[[:space:]]*mvn([[:space:]]|$)' "$VERIFY")" -eq 1 ]]
grep -q -- '-Pacceptance' "$VERIFY"
grep -q 'validate-sample-data-results.py' "$VERIFY"
grep -q 'build-performance-report.py' "$VERIFY"
grep -q 'canonical-json-fingerprint.py' "$VERIFY"
grep -q 'build-verification-manifest.py' "$VERIFY"
grep -q 'verification-manifest.json' "$VERIFY"
grep -q 'VERIFY_SESSION_ID' "$VERIFY"
grep -q 'summary-with-derived.tsv' "$VERIFY"
grep -q 'warning-codes.tsv' "$VERIFY"
grep -q 'observation-parity.tsv' "$VERIFY"
grep -q -- '--artifact "$VERIFY_DIR/no-cache-acceptance.log"' "$VERIFY"
grep -q -- '--cli-report' "$VERIFY"
grep -q -- '--correctness-summary' "$VERIFY"
grep -q -- '--fingerprints' "$VERIFY"
grep -q 'SAMPLE_DATA_PARSER_CLI_SKIP_PACKAGE' "$RUNNER"
grep -q 'audit-semantic-observations.sh' "$RUNNER"

if SAMPLE_DATA_PARSER_CLI_SKIP_PACKAGE=true bash "$RUNNER" does-not-exist >/dev/null 2>&1; then
  echo "unknown sample-data parser case must fail" >&2
  exit 1
fi

echo "verification wiring test passed"
