#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RELATION_ROOT="$ROOT/relation-detector"
MODE="${1:-report}"
SESSION_ID="$(date -u +%Y%m%dT%H%M%SZ)"
OUT_DIR="${BENCHMARK_OUT_DIR:-$RELATION_ROOT/target/build-benchmarks/$SESSION_ID}"
SESSION_START="$(date +%s)"
MAVEN_LOGS=()

mkdir -p "$OUT_DIR"
cd "$ROOT"

run_step() {
  local name="$1"
  shift
  local log="$OUT_DIR/$name.log"
  local started ended status
  started="$(date +%s)"
  if "$@" >"$log" 2>&1; then
    status=0
  else
    status=$?
  fi
  ended="$(date +%s)"
  printf '%s\t%s\t%s\n' "$name" "$((ended - started))" "$status" >>"$OUT_DIR/steps.tsv"
  MAVEN_LOGS+=("$log")
  if [[ "$status" -ne 0 ]]; then
    tail -80 "$log" >&2
    return "$status"
  fi
}

case "$MODE" in
  clean)
    run_step clean-reference mvn -T 2 -Dmaven.build.cache.enabled=false clean test
    ;;
  warm)
    run_step warm-all mvn -T 2 test
    ;;
  focused)
    run_step focused-oracle mvn -T 2 -pl relation-detector/adaptor-oracle -am \
      -Dtest=OracleAdaptorParserTest -Dsurefire.failIfNoSpecifiedTests=false test
    ;;
  full)
    run_step full-correctness mvn -T 2 -pl relation-detector/cli -am \
      -Dtest=CorrectnessFixtureRunnerTest \
      -DcorrectnessFixtureProfile=full \
      -DcorrectnessFixtureParallelism=12 \
      -DcorrectnessFixtureTiming=true \
      -Dsurefire.failIfNoSpecifiedTests=false test
    ;;
  cli)
    run_step sample-cli env RELATION_DETECTOR_SKIP_PACKAGE=true \
      SAMPLE_DATA_PARSER_CLI_SKIP_PACKAGE=true \
      bash relation-detector/test-fixtures/examples/sample-data-parser-cli/run-all-sample-data-parsers.sh
    ;;
  report)
    ;;
  all)
    run_step clean-reference mvn -T 2 -Dmaven.build.cache.enabled=false clean test
    run_step warm-all mvn -T 2 test
    run_step focused-oracle mvn -T 2 -pl relation-detector/adaptor-oracle -am \
      -Dtest=OracleAdaptorParserTest -Dsurefire.failIfNoSpecifiedTests=false test
    run_step full-correctness mvn -T 2 -pl relation-detector/cli -am \
      -Dtest=CorrectnessFixtureRunnerTest \
      -DcorrectnessFixtureProfile=full \
      -DcorrectnessFixtureParallelism=12 \
      -DcorrectnessFixtureTiming=true \
      -Dsurefire.failIfNoSpecifiedTests=false test
    run_step sample-cli env RELATION_DETECTOR_SKIP_PACKAGE=true \
      SAMPLE_DATA_PARSER_CLI_SKIP_PACKAGE=true \
      bash relation-detector/test-fixtures/examples/sample-data-parser-cli/run-all-sample-data-parsers.sh
    ;;
  *)
    echo "Usage: $0 [report|clean|warm|focused|full|cli|all]" >&2
    exit 2
    ;;
esac

: >"$OUT_DIR/no-maven.log"
REPORT_ARGS=(--maven-log "$OUT_DIR/no-maven.log")
for log in "${MAVEN_LOGS[@]-}"; do
  [[ -n "$log" ]] && REPORT_ARGS+=(--maven-log "$log")
done

RESULT_DIR="$RELATION_ROOT/target/sample-data-parser-cli/results"
: >"$OUT_DIR/fingerprints.tsv"
if [[ -d "$RESULT_DIR" ]]; then
  python3 "$RELATION_ROOT/scripts/canonical-json-fingerprint.py" "$RESULT_DIR" >"$OUT_DIR/fingerprints.tsv"
fi

python3 "$RELATION_ROOT/scripts/build-performance-report.py" \
  --session-start "$SESSION_START" \
  --surefire-root "$ROOT" \
  --cli-log-root "$RELATION_ROOT/target/sample-data-parser-cli/logs" \
  --cli-report "$RELATION_ROOT/target/sample-data-parser-cli/batch-report.json" \
  --correctness-summary "$RELATION_ROOT/target/correctness-run-summary.json" \
  --fingerprints "$OUT_DIR/fingerprints.tsv" \
  "${REPORT_ARGS[@]}" \
  --output "$OUT_DIR/report.json"

echo "Benchmark report: $OUT_DIR/report.json"
