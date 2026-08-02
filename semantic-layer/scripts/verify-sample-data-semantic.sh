#!/usr/bin/env bash
set -euo pipefail
set -m

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TIER="${SAMPLE_DATA_SEMANTIC_TIER:-matrix}"
RESULT_DIR="${SAMPLE_DATA_SEMANTIC_RESULT_DIR:-$ROOT/relation-detector/target/sample-data-parser-cli/results}"
OUTPUT_ROOT="${SAMPLE_DATA_SEMANTIC_OUTPUT_ROOT:-$ROOT/semantic-layer/target/sample-data-semantic-verification/$(date -u +%Y%m%dT%H%M%SZ)}"
SEMANTIC_JAVA="${SAMPLE_DATA_SEMANTIC_JAVA:-java}"
SEMANTIC_JAR="${SAMPLE_DATA_SEMANTIC_JAR:-$ROOT/semantic-layer/semantic-cli/target/relation-detector-semantic-cli-0.1.0-SNAPSHOT.jar}"
SEMANTIC_HEAP="${SAMPLE_DATA_SEMANTIC_HEAP:-4g}"
SEMANTIC_MAX_SHARDS="${SAMPLE_DATA_SEMANTIC_MAX_SHARDS:-256}"
CASE_PARALLELISM="${SAMPLE_DATA_SEMANTIC_CASE_PARALLELISM:-1}"
EXPECTED_CATEGORIES="${SAMPLE_DATA_SEMANTIC_EXPECTED_CATEGORIES:-19}"
KG_OUTPUT="${SAMPLE_DATA_SEMANTIC_KG_OUTPUT:-digest-only}"
MODEL="gpt-5.6-sol"
REASONING_EFFORT="xhigh"
SMOKE_CASE="mysql-v8_0-full-derived-fresh"
REQUEST_ROOT="${SAMPLE_DATA_SEMANTIC_REQUEST_ROOT:-$OUTPUT_ROOT/requests}"
RESPONSE_ROOT="${SAMPLE_DATA_SEMANTIC_RESPONSE_ROOT:-$OUTPUT_ROOT/responses}"
SUMMARY="$OUTPUT_ROOT/summary.tsv"
ACTIVE_WORKER_ONE=""
ACTIVE_WORKER_TWO=""

terminate_worker() {
  local pid="$1"
  [[ -n "$pid" ]] || return 0
  kill -TERM -- "-$pid" 2>/dev/null || true
  sleep 1
  kill -KILL -- "-$pid" 2>/dev/null || true
  wait "$pid" 2>/dev/null || true
}

cleanup_workers() {
  local status=$?
  trap - EXIT INT TERM
  terminate_worker "$ACTIVE_WORKER_ONE"
  terminate_worker "$ACTIVE_WORKER_TWO"
  exit "$status"
}

trap cleanup_workers EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

fail() {
  echo "$1" >&2
  exit "${2:-1}"
}

if [[ "$TIER" != "smoke" && "$TIER" != "matrix" && "$TIER" != "enrichment" ]]; then
  fail "SAMPLE_DATA_SEMANTIC_TIER must be smoke, matrix, or enrichment" 2
fi
if [[ ! -f "$SEMANTIC_JAR" ]]; then
  fail "semantic CLI artifact is unavailable" 2
fi
if ! [[ "$SEMANTIC_MAX_SHARDS" =~ ^[1-9][0-9]*$ ]]; then
  fail "SAMPLE_DATA_SEMANTIC_MAX_SHARDS must be a positive integer" 2
fi
if [[ "$CASE_PARALLELISM" != "1" && "$CASE_PARALLELISM" != "2" ]]; then
  fail "SAMPLE_DATA_SEMANTIC_CASE_PARALLELISM must be 1 or 2" 2
fi
if [[ "$KG_OUTPUT" != "full" && "$KG_OUTPUT" != "digest-only" ]]; then
  fail "SAMPLE_DATA_SEMANTIC_KG_OUTPUT must be full or digest-only" 2
fi
if [[ -e "$OUTPUT_ROOT" ]]; then
  fail "sample-data semantic output directory already exists" 2
fi
mkdir -p "$OUTPUT_ROOT"

run_directory() {
  local root="$1"
  local count
  count="$(find "$root" -mindepth 1 -maxdepth 1 -type d -name 'run-*' | wc -l | tr -d '[:space:]')"
  [[ "$count" -eq 1 ]] || fail "semantic request run is incomplete"
  find "$root" -mindepth 1 -maxdepth 1 -type d -name 'run-*' -print
}

verify_request_case() {
  local case_name="$1"
  local input="$2"
  local row_file="$3"
  local case_root="$REQUEST_ROOT/$case_name"
  local reconstructed="$OUTPUT_ROOT/reconstructed/$case_name.json"
  local request_run
  local digest_report
  local digest_values
  local kg_bytes kg_sha evidence_bytes evidence_sha build_bytes build_sha

  "$SEMANTIC_JAVA" "-Xmx$SEMANTIC_HEAP" -jar "$SEMANTIC_JAR" \
    extract \
    --provider codex-session \
    --model "$MODEL" \
    --reasoning-effort "$REASONING_EFFORT" \
    --kg-output "$KG_OUTPUT" \
    --max-shards "$SEMANTIC_MAX_SHARDS" \
    --input "$input" \
    --output "$case_root"

  request_run="$(run_directory "$case_root")"
  if [[ "$(find "$request_run/shards" -type f -name 'semantic-extraction-codex-session.md' | wc -l | tr -d '[:space:]')" -lt 1 ]]; then
    fail "semantic Codex-session shard requests are incomplete"
  fi
  mkdir -p "$(dirname "$reconstructed")"
  "$SEMANTIC_JAVA" -Xmx512m -cp "$SEMANTIC_JAR" \
    com.relationdetector.semantic.cli.SemanticRequestBundleReconstructorMain \
    "$request_run" "$reconstructed" >/dev/null
  [[ -s "$reconstructed" ]] || fail "semantic request bundle reconstruction is incomplete"

  digest_report="$request_run/deterministic-kg/semantic-kg-digests.json"
  [[ -s "$digest_report" ]] || fail "semantic KG digest report is incomplete"
  digest_values="$("$SEMANTIC_JAVA" -Xmx64m -cp "$SEMANTIC_JAR" \
    com.relationdetector.semantic.cli.SemanticKgDigestReportMain "$digest_report")"
  IFS=$'\t' read -r \
    kg_bytes kg_sha evidence_bytes evidence_sha build_bytes build_sha <<<"$digest_values"
  printf '%s\t%s\tPASS\tPASS\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$case_name" "$input" \
    "$kg_bytes" "$kg_sha" "$evidence_bytes" "$evidence_sha" \
    "$build_bytes" "$build_sha" "$request_run" >>"$row_file"
}

run_case_list() {
  local list_file="$1"
  local row_file="$2"
  while IFS= read -r input; do
    [[ -n "$input" ]] || continue
    local case_name="$(basename "$input" .json)"
    verify_request_case "$case_name" "$input" "$row_file" \
      >"$OUTPUT_ROOT/logs/$case_name.log" 2>&1
  done <"$list_file"
}

wait_worker_pair() {
  local first_pid="$1"
  local second_pid="$2"
  local first_done=0
  local second_done=0
  local code
  while [[ "$first_done" -eq 0 || "$second_done" -eq 0 ]]; do
    if [[ "$first_done" -eq 0 ]] && ! kill -0 "$first_pid" 2>/dev/null; then
      set +e
      wait "$first_pid"
      code=$?
      set -e
      first_done=1
      if [[ "$code" -ne 0 ]]; then
        terminate_worker "$second_pid"
        return "$code"
      fi
    fi
    if [[ "$second_done" -eq 0 ]] && ! kill -0 "$second_pid" 2>/dev/null; then
      set +e
      wait "$second_pid"
      code=$?
      set -e
      second_done=1
      if [[ "$code" -ne 0 ]]; then
        terminate_worker "$first_pid"
        return "$code"
      fi
    fi
    if [[ "$first_done" -eq 0 || "$second_done" -eq 0 ]]; then
      sleep 1
    fi
  done
}

run_case_matrix() {
  local all_cases="$1"
  mkdir -p "$OUTPUT_ROOT/logs"
  if [[ "$CASE_PARALLELISM" -eq 1 ]]; then
    run_case_list "$all_cases" "$OUTPUT_ROOT/rows-1.tsv"
    LC_ALL=C sort -t $'\t' -k1,1 "$OUTPUT_ROOT/rows-1.tsv" >>"$SUMMARY"
    return
  fi
  local first="$OUTPUT_ROOT/cases-1.txt"
  local second="$OUTPUT_ROOT/cases-2.txt"
  : >"$first"
  : >"$second"
  local category_ordinal=-1
  local previous_category=""
  while IFS= read -r input; do
    local case_name category
    case_name="$(basename "$input" .json)"
    category="${case_name%-derived-fresh}"
    if [[ "$category" != "$previous_category" ]]; then
      category_ordinal=$((category_ordinal + 1))
      previous_category="$category"
    fi
    if [[ $((category_ordinal % 2)) -eq 0 ]]; then
      printf '%s\n' "$input" >>"$first"
    else
      printf '%s\n' "$input" >>"$second"
    fi
  done <"$all_cases"
  run_case_list "$first" "$OUTPUT_ROOT/rows-1.tsv" &
  local first_pid=$!
  ACTIVE_WORKER_ONE="$first_pid"
  run_case_list "$second" "$OUTPUT_ROOT/rows-2.tsv" &
  local second_pid=$!
  ACTIVE_WORKER_TWO="$second_pid"
  wait_worker_pair "$first_pid" "$second_pid"
  ACTIVE_WORKER_ONE=""
  ACTIVE_WORKER_TWO=""
  LC_ALL=C sort -t $'\t' -k1,1 "$OUTPUT_ROOT/rows-1.tsv" "$OUTPUT_ROOT/rows-2.tsv" >>"$SUMMARY"
}

run_deterministic_tier() {
  [[ -d "$RESULT_DIR" ]] || fail "sample-data semantic input directory is unavailable" 2
  mkdir -p "$REQUEST_ROOT"
  printf 'case\tinput\tkg\treconstruction\tkgBytes\tkgSha256\tevidenceGraphBytes\tevidenceGraphSha256\tbuildRunBytes\tbuildRunSha256\trequestRun\n' >"$SUMMARY"
  mkdir -p "$OUTPUT_ROOT/logs"
  if [[ "$TIER" == "smoke" ]]; then
    local input="$RESULT_DIR/$SMOKE_CASE.json"
    [[ -f "$input" ]] || fail "semantic smoke input is unavailable"
    verify_request_case "$SMOKE_CASE" "$input" "$SUMMARY" \
      >"$OUTPUT_ROOT/logs/$SMOKE_CASE.log" 2>&1
  else
    local expected=$((EXPECTED_CATEGORIES * 2))
    local count
    count="$(find "$RESULT_DIR" -maxdepth 1 -type f -name '*.json' | wc -l | tr -d '[:space:]')"
    [[ "$count" -eq "$expected" ]] || fail "sample-data semantic matrix is incomplete"
    local cases="$OUTPUT_ROOT/cases.txt"
    find "$RESULT_DIR" -maxdepth 1 -type f -name '*.json' | LC_ALL=C sort >"$cases"
    run_case_matrix "$cases"
  fi
  local expected_rows=1
  [[ "$TIER" == "matrix" ]] && expected_rows=$((EXPECTED_CATEGORIES * 2))
  local rows
  rows="$(tail -n +2 "$SUMMARY" | wc -l | tr -d '[:space:]')"
  [[ "$rows" -eq "$expected_rows" ]] || fail "semantic deterministic tier summary is incomplete"
}

run_enrichment_tier() {
  [[ -d "$REQUEST_ROOT" ]] || fail "semantic request matrix is unavailable" 2
  [[ -d "$RESPONSE_ROOT" ]] || fail "semantic response matrix is unavailable" 2
  printf 'case\trequestRun\tresponseRoot\tstatus\tresult\n' >"$SUMMARY"
  local pending=0
  local count=0
  while IFS= read -r case_root; do
    local case_name request_run response_root result code status
    case_name="$(basename "$case_root")"
    request_run="$(run_directory "$case_root")"
    response_root="$RESPONSE_ROOT/$case_name"
    set +e
    result="$("$SEMANTIC_JAVA" "-Xmx$SEMANTIC_HEAP" -cp "$SEMANTIC_JAR" \
      com.relationdetector.semantic.cli.SemanticCodexSessionCompletionMain \
      --request-run "$request_run" \
      --responses "$response_root" \
      --output "$OUTPUT_ROOT/completed/$case_name")"
    code=$?
    set -e
    if [[ "$code" -eq 0 ]]; then
      status="COMPLETE"
    elif [[ "$code" -eq 2 ]]; then
      status="PENDING"
      pending=$((pending + 1))
    else
      fail "semantic Codex-session completion failed"
    fi
    printf '%s\t%s\t%s\t%s\t%s\n' \
      "$case_name" "$request_run" "$response_root" "$status" "$result" >>"$SUMMARY"
    count=$((count + 1))
  done < <(find "$REQUEST_ROOT" -mindepth 1 -maxdepth 1 -type d | LC_ALL=C sort)
  [[ "$count" -gt 0 ]] || fail "semantic request matrix contains no cases"
  write_enrichment_manifest "$count" "$pending"
  [[ "$pending" -eq 0 ]] || fail "semantic enrichment responses are incomplete" 3
}

write_enrichment_manifest() {
  local case_count="$1"
  local pending_count="$2"
  local complete_count=$((case_count - pending_count))
  local status="COMPLETE"
  [[ "$pending_count" -eq 0 ]] || status="PENDING"
  local target="$OUTPUT_ROOT/semantic-e2e-manifest.json"
  local temporary="$target.tmp.$$"
  {
    printf '{\n'
    printf '  "artifactSchemaVersion": 1,\n'
    printf '  "tier": "enrichment",\n'
    printf '  "status": "%s",\n' "$status"
    printf '  "model": "%s",\n' "$MODEL"
    printf '  "reasoningEffort": "%s",\n' "$REASONING_EFFORT"
    printf '  "caseCount": %s,\n' "$case_count"
    printf '  "completeCount": %s,\n' "$complete_count"
    printf '  "pendingCount": %s,\n' "$pending_count"
    printf '  "summary": "summary.tsv"\n'
    printf '}\n'
  } >"$temporary"
  mv "$temporary" "$target"
}

if [[ "$TIER" == "enrichment" ]]; then
  run_enrichment_tier
else
  run_deterministic_tier
fi

echo "Semantic sample-data $TIER verification: $SUMMARY"
