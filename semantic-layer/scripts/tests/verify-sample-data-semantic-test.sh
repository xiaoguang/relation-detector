#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SCRIPT="$ROOT/semantic-layer/scripts/verify-sample-data-semantic.sh"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/semantic-sample-data-test.XXXXXX")"
trap 'rm -rf "$TMP_ROOT"' EXIT

RESULT_DIR="$TMP_ROOT/results"
FAKE_JAVA="$TMP_ROOT/fake-java"
FAKE_JAR="$TMP_ROOT/semantic-cli.jar"
CALLS="$TMP_ROOT/calls.tsv"
mkdir -p "$RESULT_DIR"
touch "$FAKE_JAR"

index=1
while [[ "$index" -le 19 ]]; do
  printf '{}\n' >"$RESULT_DIR/case-$index.json"
  printf '{}\n' >"$RESULT_DIR/case-$index-derived-fresh.json"
  index=$((index + 1))
done
cp "$RESULT_DIR/case-1-derived-fresh.json" \
  "$RESULT_DIR/mysql-v8_0-full-derived-fresh.json"
rm "$RESULT_DIR/case-1-derived-fresh.json"
cp "$RESULT_DIR/case-1.json" "$RESULT_DIR/mysql-v8_0-full.json"
rm "$RESULT_DIR/case-1.json"

cat >"$FAKE_JAVA" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >>"$SAMPLE_DATA_SEMANTIC_TEST_CALLS"
if [[ "$*" == *"SemanticKgDigestReportMain"* ]]; then
  printf '3\t%s\t3\t%s\t3\t%s\n' \
    '0000000000000000000000000000000000000000000000000000000000000001' \
    '0000000000000000000000000000000000000000000000000000000000000002' \
    '0000000000000000000000000000000000000000000000000000000000000003'
  exit 0
fi
if [[ "$*" == *"SemanticRequestBundleReconstructorMain"* ]]; then
  previous=""
  target=""
  for argument in "$@"; do
    target="$argument"
  done
  mkdir -p "$(dirname "$target")"
  printf '{}\n' >"$target"
  printf '%064d\n' 0
  exit 0
fi
if [[ "$*" == *"SemanticCodexSessionCompletionMain"* ]]; then
  output=""
  previous=""
  for argument in "$@"; do
    if [[ "$previous" == "--output" ]]; then
      output="$argument"
    fi
    previous="$argument"
  done
  mkdir -p "$output/run-test"
  printf '%s\n' "$output/run-test"
  exit 0
fi
command_name=""
output=""
previous=""
for argument in "$@"; do
  if [[ "$previous" == "--output" ]]; then
    output="$argument"
  fi
  if [[ "$argument" == "extract" ]]; then
    command_name="$argument"
  fi
  previous="$argument"
done
if [[ "$command_name" != "extract" ]]; then
  exit 9
fi
mkdir -p "$output/run-test/shards/shard-0001" "$output/run-test/deterministic-kg"
printf 'Codex request\n' >"$output/run-test/shards/shard-0001/semantic-extraction-codex-session.md"
printf '{"mode":"DIGEST_ONLY","validation":{"referenceClosure":"PASS"},"artifacts":[]}\n' \
  >"$output/run-test/deterministic-kg/semantic-kg-digests.json"
EOF
chmod +x "$FAKE_JAVA"

COMMON_ENV=(
  "SAMPLE_DATA_SEMANTIC_RESULT_DIR=$RESULT_DIR"
  "SAMPLE_DATA_SEMANTIC_JAVA=$FAKE_JAVA"
  "SAMPLE_DATA_SEMANTIC_JAR=$FAKE_JAR"
  "SAMPLE_DATA_SEMANTIC_TEST_CALLS=$CALLS"
)

env "${COMMON_ENV[@]}" \
  SAMPLE_DATA_SEMANTIC_TIER=smoke \
  SAMPLE_DATA_SEMANTIC_OUTPUT_ROOT="$TMP_ROOT/smoke" \
  bash "$SCRIPT" >/dev/null
[[ "$(wc -l <"$TMP_ROOT/smoke/summary.tsv" | tr -d '[:space:]')" -eq 2 ]]
[[ "$(wc -l <"$CALLS" | tr -d '[:space:]')" -eq 3 ]]

env "${COMMON_ENV[@]}" \
  SAMPLE_DATA_SEMANTIC_TIER=matrix \
  SAMPLE_DATA_SEMANTIC_OUTPUT_ROOT="$TMP_ROOT/matrix" \
  bash "$SCRIPT" >/dev/null
[[ "$(wc -l <"$TMP_ROOT/matrix/summary.tsv" | tr -d '[:space:]')" -eq 39 ]]
[[ "$(grep -c -- '--provider codex-session' "$CALLS")" -eq 39 ]]
[[ "$(grep -c -- '--model gpt-5.6-sol' "$CALLS")" -eq 39 ]]
[[ "$(grep -c -- '--reasoning-effort xhigh' "$CALLS")" -eq 39 ]]
[[ "$(grep -c 'SemanticRequestBundleReconstructorMain' "$CALLS")" -eq 39 ]]
[[ "$(grep -c 'SemanticKgDigestReportMain' "$CALLS")" -eq 39 ]]

env "${COMMON_ENV[@]}" \
  SAMPLE_DATA_SEMANTIC_TIER=matrix \
  SAMPLE_DATA_SEMANTIC_CASE_PARALLELISM=2 \
  SAMPLE_DATA_SEMANTIC_OUTPUT_ROOT="$TMP_ROOT/matrix-parallel" \
  bash "$SCRIPT" >/dev/null
[[ "$(wc -l <"$TMP_ROOT/matrix-parallel/summary.tsv" | tr -d '[:space:]')" -eq 39 ]]
[[ "$(tail -n +2 "$TMP_ROOT/matrix-parallel/summary.tsv" | cut -f1)" == \
   "$(tail -n +2 "$TMP_ROOT/matrix/summary.tsv" | cut -f1)" ]]
[[ "$(grep -c -- '-derived-fresh.json$' "$TMP_ROOT/matrix-parallel/cases-1.txt")" -eq 10 ]]
[[ "$(grep -c -- '-derived-fresh.json$' "$TMP_ROOT/matrix-parallel/cases-2.txt")" -eq 9 ]]
[[ "$(( $(wc -l <"$TMP_ROOT/matrix-parallel/cases-1.txt") -
          $(grep -c -- '-derived-fresh.json$' "$TMP_ROOT/matrix-parallel/cases-1.txt") ))" -eq 10 ]]
[[ "$(( $(wc -l <"$TMP_ROOT/matrix-parallel/cases-2.txt") -
          $(grep -c -- '-derived-fresh.json$' "$TMP_ROOT/matrix-parallel/cases-2.txt") ))" -eq 9 ]]

mkdir -p "$TMP_ROOT/responses"
case_index=1
while [[ "$case_index" -le 38 ]]; do
  mkdir -p "$TMP_ROOT/responses/case-$case_index"
  case_index=$((case_index + 1))
done
while IFS= read -r case_root; do
  mkdir -p "$TMP_ROOT/responses/$(basename "$case_root")"
done < <(find "$TMP_ROOT/matrix/requests" -mindepth 1 -maxdepth 1 -type d)

env "${COMMON_ENV[@]}" \
  SAMPLE_DATA_SEMANTIC_TIER=enrichment \
  SAMPLE_DATA_SEMANTIC_OUTPUT_ROOT="$TMP_ROOT/enrichment" \
  SAMPLE_DATA_SEMANTIC_REQUEST_ROOT="$TMP_ROOT/matrix/requests" \
  SAMPLE_DATA_SEMANTIC_RESPONSE_ROOT="$TMP_ROOT/responses" \
  bash "$SCRIPT" >/dev/null
[[ "$(wc -l <"$TMP_ROOT/enrichment/summary.tsv" | tr -d '[:space:]')" -eq 39 ]]
[[ "$(awk -F '\t' 'NR > 1 && $4 == "COMPLETE" {count++} END {print count + 0}' \
  "$TMP_ROOT/enrichment/summary.tsv")" -eq 38 ]]
jq -e '.status == "COMPLETE"
  and .model == "gpt-5.6-sol"
  and .reasoningEffort == "xhigh"
  and .caseCount == 38
  and .completeCount == 38
  and .pendingCount == 0' \
  "$TMP_ROOT/enrichment/semantic-e2e-manifest.json" >/dev/null

if env "${COMMON_ENV[@]}" \
  SAMPLE_DATA_SEMANTIC_TIER=unknown \
  SAMPLE_DATA_SEMANTIC_OUTPUT_ROOT="$TMP_ROOT/invalid" \
  bash "$SCRIPT" >/dev/null 2>&1; then
  echo "unknown semantic verification tier must fail" >&2
  exit 1
fi

echo "sample-data semantic verification test passed"
