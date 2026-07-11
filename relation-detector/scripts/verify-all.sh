#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

mvn -pl relation-detector/cli -am \
  -Dtest=CorrectnessFixtureRunnerTest \
  -DcorrectnessFixtureProfile=full \
  -DcorrectnessFixtureParallelism=8 \
  -DcorrectnessFixtureTiming=true \
  -Dsurefire.failIfNoSpecifiedTests=false test

SAMPLE_DATA_PARSER_CLI_CASE_PARALLELISM="${SAMPLE_DATA_PARSER_CLI_CASE_PARALLELISM:-3}" \
SAMPLE_DATA_PARSER_CLI_SCAN_PARALLELISM="${SAMPLE_DATA_PARSER_CLI_SCAN_PARALLELISM:-2}" \
  bash relation-detector/test-fixtures/examples/sample-data-parser-cli/run-all-sample-data-parsers.sh

if [[ "${VERIFY_SAMPLE_DATA_CONCURRENCY:-false}" == "true" ]]; then
  bash relation-detector/scripts/verify-sample-data-parser-concurrency.sh
fi

mvn -pl relation-detector/cli -am \
  -Dtest='SemanticEquivalentCorrectnessTest,CorrectnessSummaryGeneratorTest,DataLineageAuditGeneratorTest' \
  -DrunGeneratedReportTests=true \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -T 2 test
