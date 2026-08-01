#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RESULT_DIR="${SAMPLE_DATA_SEMANTIC_RESULT_DIR:-$ROOT/relation-detector/target/sample-data-parser-cli/results}"
OUTPUT_ROOT="${SAMPLE_DATA_SEMANTIC_OUTPUT_ROOT:-$ROOT/semantic-layer/target/sample-data-semantic-verification/$(date -u +%Y%m%dT%H%M%SZ)}"
SEMANTIC_JAVA="${SAMPLE_DATA_SEMANTIC_JAVA:-java}"
SEMANTIC_JAR="${SAMPLE_DATA_SEMANTIC_JAR:-$ROOT/semantic-layer/semantic-cli/target/relation-detector-semantic-cli-0.1.0-SNAPSHOT.jar}"
SEMANTIC_HEAP="${SAMPLE_DATA_SEMANTIC_HEAP:-4g}"
SEMANTIC_MAX_SHARDS="${SAMPLE_DATA_SEMANTIC_MAX_SHARDS:-256}"
EXPECTED_CATEGORIES="${SAMPLE_DATA_SEMANTIC_EXPECTED_CATEGORIES:-19}"
EXPECTED_RESULTS=$((EXPECTED_CATEGORIES * 2))
MODEL="gpt-5.6-sol"
REASONING_EFFORT="xhigh"
KG_ROOT="$OUTPUT_ROOT/kg"
REQUEST_ROOT="$OUTPUT_ROOT/request-only"
SUMMARY="$OUTPUT_ROOT/summary.tsv"

if [[ ! -d "$RESULT_DIR" ]]; then
  echo "sample-data semantic input directory is unavailable" >&2
  exit 2
fi
if [[ ! -f "$SEMANTIC_JAR" ]]; then
  echo "semantic CLI artifact is unavailable" >&2
  exit 2
fi
if ! [[ "$SEMANTIC_MAX_SHARDS" =~ ^[1-9][0-9]*$ ]]; then
  echo "SAMPLE_DATA_SEMANTIC_MAX_SHARDS must be a positive integer" >&2
  exit 2
fi
if [[ -e "$OUTPUT_ROOT" ]]; then
  echo "sample-data semantic output directory already exists" >&2
  exit 2
fi

mkdir -p "$KG_ROOT" "$REQUEST_ROOT"
printf 'case\tinput\tkg\trequestOnly\tkgBytes\tkgSha256\tevidenceGraphBytes\tevidenceGraphSha256\tbuildRunBytes\tbuildRunSha256\trequestRoot\n' >"$SUMMARY"

result_count="$(find "$RESULT_DIR" -maxdepth 1 -type f -name '*.json' | wc -l | tr -d '[:space:]')"
derived_count="$(find "$RESULT_DIR" -maxdepth 1 -type f -name '*-derived-fresh.json' | wc -l | tr -d '[:space:]')"
if [[ "$result_count" -ne "$EXPECTED_RESULTS" || "$derived_count" -ne "$EXPECTED_CATEGORIES" ]]; then
  echo "sample-data semantic matrix is incomplete" >&2
  exit 1
fi

find "$RESULT_DIR" -maxdepth 1 -type f -name '*.json' | LC_ALL=C sort |
while IFS= read -r input; do
  case_name="$(basename "$input" .json)"
  request_status="NOT_APPLICABLE"
  request_output=""
  if [[ "$case_name" == *-derived-fresh ]]; then
    request_output="$REQUEST_ROOT/$case_name"
    "$SEMANTIC_JAVA" "-Xmx$SEMANTIC_HEAP" -jar "$SEMANTIC_JAR" \
      extract \
      --provider openai-api \
      --request-only \
      --model "$MODEL" \
      --reasoning-effort "$REASONING_EFFORT" \
      --kg-output digest-only \
      --max-shards "$SEMANTIC_MAX_SHARDS" \
      --input "$input" \
      --output "$request_output"

    request_count="$(find "$request_output" -type f -name 'semantic-extraction-request.json' | wc -l | tr -d '[:space:]')"
    run_count="$(find "$request_output" -mindepth 1 -maxdepth 1 -type d -name 'run-*' | wc -l | tr -d '[:space:]')"
    if [[ "$request_count" -lt 1 || "$run_count" -ne 1 ]]; then
      echo "semantic request-only artifacts are incomplete" >&2
      exit 1
    fi
    request_run="$(find "$request_output" -mindepth 1 -maxdepth 1 -type d -name 'run-*' -print)"
    kg_output="$request_run/deterministic-kg"
    request_status="PASS"
  else
    kg_output="$KG_ROOT/$case_name"
    "$SEMANTIC_JAVA" "-Xmx$SEMANTIC_HEAP" -jar "$SEMANTIC_JAR" \
      build \
      --input "$input" \
      --output "$kg_output" \
      --kg-output digest-only
  fi

  digest_report="$kg_output/semantic-kg-digests.json"
  if [[ ! -s "$digest_report" ]]; then
    echo "semantic KG digest report is incomplete" >&2
    exit 1
  fi
  digest_values="$("$SEMANTIC_JAVA" -Xmx64m -cp "$SEMANTIC_JAR" \
    com.relationdetector.semantic.cli.SemanticKgDigestReportMain "$digest_report")"
  IFS=$'\t' read -r \
    kg_bytes kg_sha256 \
    evidence_graph_bytes evidence_graph_sha256 \
    build_run_bytes build_run_sha256 <<<"$digest_values"

  printf '%s\t%s\tPASS\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$case_name" \
    "$input" \
    "$request_status" \
    "$kg_bytes" \
    "$kg_sha256" \
    "$evidence_graph_bytes" \
    "$evidence_graph_sha256" \
    "$build_run_bytes" \
    "$build_run_sha256" \
    "$request_output" >>"$SUMMARY"
done

summary_rows="$(tail -n +2 "$SUMMARY" | wc -l | tr -d '[:space:]')"
kg_passes="$(awk -F '\t' 'NR > 1 && $3 == "PASS" {count++} END {print count + 0}' "$SUMMARY")"
request_passes="$(awk -F '\t' 'NR > 1 && $4 == "PASS" {count++} END {print count + 0}' "$SUMMARY")"
if [[ "$summary_rows" -ne "$EXPECTED_RESULTS" ||
      "$kg_passes" -ne "$EXPECTED_RESULTS" ||
      "$request_passes" -ne "$EXPECTED_CATEGORIES" ]]; then
  echo "sample-data semantic verification summary is incomplete" >&2
  exit 1
fi

echo "Semantic sample-data verification: $SUMMARY"
