#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SCRIPT="$ROOT/semantic-layer/scripts/verify-sample-data-semantic.sh"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/semantic-sample-data-test.XXXXXX")"
trap 'rm -rf "$TMP_ROOT"' EXIT

RESULT_DIR="$TMP_ROOT/results"
OUTPUT_ROOT="$TMP_ROOT/output"
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
command_name=""
output=""
previous=""
for argument in "$@"; do
  if [[ "$previous" == "--output" ]]; then
    output="$argument"
  fi
  if [[ "$argument" == "build" || "$argument" == "extract" ]]; then
    command_name="$argument"
  fi
  previous="$argument"
done
if [[ "$command_name" == "build" ]]; then
  mkdir -p "$output"
  printf '{"mode":"DIGEST_ONLY","validation":{"referenceClosure":"PASS"},"artifacts":[]}\n' \
    >"$output/semantic-kg-digests.json"
elif [[ "$command_name" == "extract" ]]; then
  mkdir -p "$output/run-test/shards/shard-0001" "$output/run-test/deterministic-kg"
  printf '{"model":"gpt-5.6-sol","reasoning":{"effort":"xhigh"}}\n' \
    >"$output/run-test/shards/shard-0001/semantic-extraction-request.json"
  printf '{"mode":"DIGEST_ONLY","validation":{"referenceClosure":"PASS"},"artifacts":[]}\n' \
    >"$output/run-test/deterministic-kg/semantic-kg-digests.json"
else
  exit 9
fi
EOF
chmod +x "$FAKE_JAVA"

SAMPLE_DATA_SEMANTIC_RESULT_DIR="$RESULT_DIR" \
SAMPLE_DATA_SEMANTIC_OUTPUT_ROOT="$OUTPUT_ROOT" \
SAMPLE_DATA_SEMANTIC_JAVA="$FAKE_JAVA" \
SAMPLE_DATA_SEMANTIC_JAR="$FAKE_JAR" \
SAMPLE_DATA_SEMANTIC_TEST_CALLS="$CALLS" \
  bash "$SCRIPT" >/dev/null

[[ "$(wc -l <"$CALLS" | tr -d '[:space:]')" -eq 76 ]]
[[ "$(grep -c ' build ' "$CALLS")" -eq 19 ]]
[[ "$(grep -c ' extract ' "$CALLS")" -eq 19 ]]
[[ "$(grep -c -- '--provider openai-api' "$CALLS")" -eq 19 ]]
[[ "$(grep -c -- '--request-only' "$CALLS")" -eq 19 ]]
[[ "$(grep -c -- '--model gpt-5.6-sol' "$CALLS")" -eq 19 ]]
[[ "$(grep -c -- '--reasoning-effort xhigh' "$CALLS")" -eq 19 ]]
[[ "$(grep -c -- '--kg-output digest-only' "$CALLS")" -eq 38 ]]
[[ "$(grep -c 'SemanticKgDigestReportMain' "$CALLS")" -eq 38 ]]
if [[ "$(grep -c -- '--max-shards 256' "$CALLS")" -ne 19 ]]; then
  echo "semantic sample-data verification must use the bounded 256-shard release limit" >&2
  exit 1
fi
[[ "$(wc -l <"$OUTPUT_ROOT/summary.tsv" | tr -d '[:space:]')" -eq 39 ]]
[[ "$(awk -F '\t' 'NR > 1 && $3 == "PASS" {count++} END {print count + 0}' "$OUTPUT_ROOT/summary.tsv")" -eq 38 ]]
[[ "$(awk -F '\t' 'NR > 1 && $4 == "PASS" {count++} END {print count + 0}' "$OUTPUT_ROOT/summary.tsv")" -eq 19 ]]
[[ "$(head -n 1 "$OUTPUT_ROOT/summary.tsv")" == $'case\tinput\tkg\trequestOnly\tkgBytes\tkgSha256\tevidenceGraphBytes\tevidenceGraphSha256\tbuildRunBytes\tbuildRunSha256\trequestRoot' ]]
[[ "$(awk -F '\t' 'NR > 1 && ($5 != 3 || length($6) != 64 || $7 != 3 || length($8) != 64 || $9 != 3 || length($10) != 64) {count++} END {print count + 0}' "$OUTPUT_ROOT/summary.tsv")" -eq 0 ]]
if find "$OUTPUT_ROOT" -type f \( -name semantic-kg.json -o -name semantic-evidence-graph.json -o -name semantic-build-run.json \) -print -quit | grep -q .; then
  echo "digest-only verification must not create large KG artifacts" >&2
  exit 1
fi
if [[ "$(find "$OUTPUT_ROOT" -type f -name semantic-kg-digests.json | wc -l | tr -d '[:space:]')" -ne 38 ]]; then
  echo "each semantic case must retain one small KG digest report" >&2
  exit 1
fi

rm -f "$RESULT_DIR/case-19-derived-fresh.json"
if SAMPLE_DATA_SEMANTIC_RESULT_DIR="$RESULT_DIR" \
   SAMPLE_DATA_SEMANTIC_OUTPUT_ROOT="$TMP_ROOT/incomplete-output" \
   SAMPLE_DATA_SEMANTIC_JAVA="$FAKE_JAVA" \
   SAMPLE_DATA_SEMANTIC_JAR="$FAKE_JAR" \
   SAMPLE_DATA_SEMANTIC_TEST_CALLS="$CALLS" \
     bash "$SCRIPT" >/dev/null 2>&1; then
  echo "incomplete semantic sample-data matrix must fail" >&2
  exit 1
fi

echo "sample-data semantic verification test passed"
