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
  mkdir -p "$RESULT_DIR/case-$index"
  printf '{}\n' >"$RESULT_DIR/case-$index/direct.json"
  printf '{}\n' >"$RESULT_DIR/case-$index/result.json"
  index=$((index + 1))
done
mv "$RESULT_DIR/case-1" "$RESULT_DIR/mysql-v8_0-full"

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
if [[ "$*" == *"SemanticCompletedRunVerifierMain"* ]]; then
  if [[ "${SAMPLE_DATA_SEMANTIC_TEST_FAIL_VERIFIER:-false}" == "true" ]]; then
    exit 9
  fi
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
[[ "$(awk -F '\t' 'NR == 2 { print $1 }' "$TMP_ROOT/smoke/summary.tsv")" == \
   "mysql-v8_0-full/result.json" ]]

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
[[ "$(grep -c -- '/result.json$' "$TMP_ROOT/matrix-parallel/cases-1.txt")" -eq 10 ]]
[[ "$(grep -c -- '/result.json$' "$TMP_ROOT/matrix-parallel/cases-2.txt")" -eq 9 ]]
[[ "$(grep -c -- '/direct.json$' "$TMP_ROOT/matrix-parallel/cases-1.txt")" -eq 10 ]]
[[ "$(grep -c -- '/direct.json$' "$TMP_ROOT/matrix-parallel/cases-2.txt")" -eq 9 ]]

mkdir -p "$TMP_ROOT/responses"
while IFS= read -r run_root; do
  case_root="$(dirname "$run_root")"
  logical_identity="${case_root#"$TMP_ROOT/matrix/requests"/}"
  [[ "$logical_identity" != "$case_root" ]]
  mkdir -p "$TMP_ROOT/responses/$logical_identity"
done < <(find "$TMP_ROOT/matrix/requests" -type d -name 'run-*' -print | sort)

env "${COMMON_ENV[@]}" \
  SAMPLE_DATA_SEMANTIC_TIER=enrichment \
  SAMPLE_DATA_SEMANTIC_CASE_PARALLELISM=4 \
  SAMPLE_DATA_SEMANTIC_OUTPUT_ROOT="$TMP_ROOT/enrichment" \
  SAMPLE_DATA_SEMANTIC_REQUEST_ROOT="$TMP_ROOT/matrix/requests" \
  SAMPLE_DATA_SEMANTIC_RESPONSE_ROOT="$TMP_ROOT/responses" \
  bash "$SCRIPT" >/dev/null

cp -R "$TMP_ROOT/matrix/requests" "$TMP_ROOT/invalid-requests"
cp -R "$TMP_ROOT/responses" "$TMP_ROOT/invalid-responses"
mv "$TMP_ROOT/invalid-requests/mysql-v8_0-full/direct.json" \
  "$TMP_ROOT/invalid-requests/mysql-v8_0-full/not-a-view.json"
mv "$TMP_ROOT/invalid-responses/mysql-v8_0-full/direct.json" \
  "$TMP_ROOT/invalid-responses/mysql-v8_0-full/not-a-view.json"
if env "${COMMON_ENV[@]}" \
    SAMPLE_DATA_SEMANTIC_TIER=enrichment \
    SAMPLE_DATA_SEMANTIC_OUTPUT_ROOT="$TMP_ROOT/invalid-enrichment" \
    SAMPLE_DATA_SEMANTIC_REQUEST_ROOT="$TMP_ROOT/invalid-requests" \
    SAMPLE_DATA_SEMANTIC_RESPONSE_ROOT="$TMP_ROOT/invalid-responses" \
    bash "$SCRIPT" >/dev/null 2>&1; then
  echo "semantic enrichment accepted a non-canonical request view inventory" >&2
  exit 1
fi
[[ "$(wc -l <"$TMP_ROOT/enrichment/summary.tsv" | tr -d '[:space:]')" -eq 39 ]]
[[ "$(awk -F '\t' 'NR > 1 && $4 == "COMPLETE" {count++} END {print count + 0}' \
  "$TMP_ROOT/enrichment/summary.tsv")" -eq 38 ]]
[[ "$(tail -n +2 "$TMP_ROOT/enrichment/summary.tsv" | cut -f1)" == \
   "$(tail -n +2 "$TMP_ROOT/enrichment/summary.tsv" | cut -f1 | LC_ALL=C sort)" ]]
if [[ ! -f "$TMP_ROOT/enrichment/enrichment-runs-1.txt"
    || ! -f "$TMP_ROOT/enrichment/enrichment-runs-2.txt"
    || ! -f "$TMP_ROOT/enrichment/enrichment-runs-3.txt"
    || ! -f "$TMP_ROOT/enrichment/enrichment-runs-4.txt" ]]; then
  echo "semantic enrichment did not use the requested parallel worker split" >&2
  exit 1
fi
[[ "$(wc -l <"$TMP_ROOT/enrichment/enrichment-runs-1.txt" | tr -d '[:space:]')" -eq 10 ]]
[[ "$(wc -l <"$TMP_ROOT/enrichment/enrichment-runs-2.txt" | tr -d '[:space:]')" -eq 10 ]]
[[ "$(wc -l <"$TMP_ROOT/enrichment/enrichment-runs-3.txt" | tr -d '[:space:]')" -eq 10 ]]
[[ "$(wc -l <"$TMP_ROOT/enrichment/enrichment-runs-4.txt" | tr -d '[:space:]')" -eq 8 ]]
jq -e '.status == "COMPLETE"
  and .model == "gpt-5.6-sol"
  and .reasoningEffort == "xhigh"
  and .caseCount == 38
  and .completeCount == 38
  and .pendingCount == 0' \
  "$TMP_ROOT/enrichment/semantic-e2e-manifest.json" >/dev/null
[[ "$(grep -c 'SemanticCompletedRunVerifierMain' "$CALLS")" -eq 38 ]]

if env "${COMMON_ENV[@]}" \
    SAMPLE_DATA_SEMANTIC_TEST_FAIL_VERIFIER=true \
    SAMPLE_DATA_SEMANTIC_TIER=enrichment \
    SAMPLE_DATA_SEMANTIC_CASE_PARALLELISM=4 \
    SAMPLE_DATA_SEMANTIC_OUTPUT_ROOT="$TMP_ROOT/invalid-completed-run" \
    SAMPLE_DATA_SEMANTIC_REQUEST_ROOT="$TMP_ROOT/matrix/requests" \
    SAMPLE_DATA_SEMANTIC_RESPONSE_ROOT="$TMP_ROOT/responses" \
    bash "$SCRIPT" >/dev/null 2>&1; then
  echo "semantic enrichment accepted an invalid completed run" >&2
  exit 1
fi
[[ ! -e "$TMP_ROOT/invalid-completed-run/semantic-e2e-manifest.json" ]]

if env "${COMMON_ENV[@]}" \
  SAMPLE_DATA_SEMANTIC_TIER=unknown \
  SAMPLE_DATA_SEMANTIC_OUTPUT_ROOT="$TMP_ROOT/invalid" \
  bash "$SCRIPT" >/dev/null 2>&1; then
  echo "unknown semantic verification tier must fail" >&2
  exit 1
fi

echo "sample-data semantic verification test passed"
