#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
VERIFY="$ROOT/relation-detector/scripts/verify-all.sh"
RUNNER="$ROOT/relation-detector/test-fixtures/examples/sample-data-parser-cli/run-all-sample-data-parsers.sh"
CORRECTNESS_RUNNER="$ROOT/relation-detector/scripts/run-correctness-isolated.sh"
SAMPLE_DATA_ISOLATED_RUNNER="$ROOT/relation-detector/scripts/run-sample-data-isolated.sh"

grep -q '<id>matrix-smoke</id>' "$ROOT/pom.xml"
grep -q '<id>acceptance</id>' "$ROOT/pom.xml"
[[ "$(grep -Ec '^[[:space:]]*mvn([[:space:]]|$)' "$VERIFY")" -eq 1 ]]
grep -q -- '-Pacceptance' "$VERIFY"
grep -q -- '-DcorrectnessFixtureProfile=smoke' "$VERIFY"
grep -q 'run-correctness-isolated.sh' "$VERIFY"
[[ -x "$CORRECTNESS_RUNNER" ]]
[[ -x "$SAMPLE_DATA_ISOLATED_RUNNER" ]]
grep -q 'run-sample-data-isolated.sh' "$RUNNER"
grep -q 'SAMPLE_DATA_PARSER_CLI_HEAP:-6g' "$SAMPLE_DATA_ISOLATED_RUNNER"
grep -q 'oracle-v12c oracle-v19c oracle-v21c oracle-v26ai' "$SAMPLE_DATA_ISOLATED_RUNNER"
grep -q 'validate-sample-data-results.py' "$VERIFY"
grep -q 'sync-parser-comparison-summary.py' "$VERIFY"
grep -q 'build-performance-report.py' "$VERIFY"
grep -q 'canonical-json-fingerprint.py' "$VERIFY"
grep -q 'build-verification-manifest.py' "$VERIFY"
grep -q 'verification-manifest.json' "$VERIFY"
grep -q 'VERIFY_SESSION_ID' "$VERIFY"
grep -q 'MANIFEST_OPTIONAL_ARGS=(--artifact' "$VERIFY"
! grep -q 'NO_CACHE_STATUS_ARGS\[@\]' "$VERIFY"
grep -q 'summary-with-derived.tsv' "$VERIFY"
grep -q 'warning-codes.tsv' "$VERIFY"
grep -q 'observation-parity.tsv' "$VERIFY"
grep -q 'target/generated-reports/correctness-test-summary.md' "$VERIFY"
grep -q 'target/generated-reports/data-lineage-full-audit.md' "$VERIFY"
grep -q -- '--artifact "$VERIFY_DIR/reports/correctness-test-summary.md"' "$VERIFY"
grep -q -- '--artifact "$VERIFY_DIR/reports/data-lineage-full-audit.md"' "$VERIFY"
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
