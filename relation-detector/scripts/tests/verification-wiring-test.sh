#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
VERIFY="$ROOT/relation-detector/scripts/verify-all.sh"
VERIFY_RELEASE="$ROOT/relation-detector/scripts/verify-release.sh"
RUNNER="$ROOT/relation-detector/test-fixtures/examples/sample-data-parser-cli/run-all-sample-data-parsers.sh"
CORRECTNESS_RUNNER="$ROOT/relation-detector/scripts/run-correctness-isolated.sh"
SAMPLE_DATA_ISOLATED_RUNNER="$ROOT/relation-detector/scripts/run-sample-data-isolated.sh"
LOCK_LIBRARY="$ROOT/relation-detector/scripts/heavy-job-lock.sh"

grep -q '<id>matrix-smoke</id>' "$ROOT/pom.xml"
grep -q '<id>acceptance</id>' "$ROOT/pom.xml"
[[ "$(grep -Ec '^[[:space:]]*"\$MVN_BIN"([[:space:]]|$)' "$VERIFY")" -eq 1 ]]
grep -q -- '-Pacceptance' "$VERIFY"
grep -q -- '-DcorrectnessFixtureProfile=smoke' "$VERIFY"
grep -q 'run-correctness-isolated.sh' "$VERIFY"
[[ -x "$CORRECTNESS_RUNNER" ]]
[[ -x "$SAMPLE_DATA_ISOLATED_RUNNER" ]]
[[ -f "$LOCK_LIBRARY" ]]
grep -q 'heavy-job-lock.sh' "$CORRECTNESS_RUNNER"
grep -q 'heavy-job-lock.sh' "$SAMPLE_DATA_ISOLATED_RUNNER"
grep -q 'heavy-job-lock.sh' "$VERIFY"
grep -q 'heavy-job-lock.sh' "$VERIFY_RELEASE"
! grep -q '^acquire_lock()' "$CORRECTNESS_RUNNER"
! grep -q '^acquire_lock()' "$SAMPLE_DATA_ISOLATED_RUNNER"
grep -q 'heavy_job_lock_acquire' "$VERIFY"
grep -q 'heavy_job_lock_acquire' "$VERIFY_RELEASE"

verify_lock_line="$(grep -n 'heavy_job_lock_acquire' "$VERIFY" | head -n 1 | cut -d: -f1)"
verify_maven_line="$(grep -n '^run_acceptance_smoke$' "$VERIFY" | head -n 1 | cut -d: -f1)"
release_lock_line="$(grep -n 'heavy_job_lock_acquire' "$VERIFY_RELEASE" | head -n 1 | cut -d: -f1)"
release_maven_line="$(grep -n '^run_no_cache_acceptance$' "$VERIFY_RELEASE" | head -n 1 | cut -d: -f1)"
if [[ -z "$verify_lock_line" || -z "$verify_maven_line" || "$verify_lock_line" -ge "$verify_maven_line" ]]; then
  echo "verify-all must acquire the heavy-job lock before acceptance" >&2
  exit 1
fi
if [[ -z "$release_lock_line" || -z "$release_maven_line" || "$release_lock_line" -ge "$release_maven_line" ]]; then
  echo "verify-release must acquire the heavy-job lock before no-cache acceptance" >&2
  exit 1
fi
grep -q 'run-sample-data-isolated.sh' "$RUNNER"
grep -q 'SAMPLE_DATA_PARSER_CLI_HEAP:-6g' "$SAMPLE_DATA_ISOLATED_RUNNER"
grep -q 'SAMPLE_DATA_PARSER_CLI_CASE_PARALLELISM:-1' "$SAMPLE_DATA_ISOLATED_RUNNER"
grep -q 'RELATION_DETECTOR_HEAVY_JOB_LOCK_DIR' "$CORRECTNESS_RUNNER"
grep -q 'RELATION_DETECTOR_HEAVY_JOB_LOCK_DIR' "$SAMPLE_DATA_ISOLATED_RUNNER"
grep -q 'SAMPLE_DATA_PARSER_CLI_CASE_PARALLELISM:-1' "$VERIFY"
grep -q 'SAMPLE_DATA_PARSER_CLI_CASE_PARALLELISM:-1' \
  "$ROOT/relation-detector/scripts/reconstruct-grammar-migration-baseline.sh"
grep -q 'CASE_PARALLELISM="${SAMPLE_DATA_PARSER_CLI_CASE_PARALLELISM:-1}"' "$RUNNER"
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
