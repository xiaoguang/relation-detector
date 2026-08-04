#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SCRIPT="$ROOT/relation-detector/scripts/benchmark/verify-sample-data-parser-concurrency.sh"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/sample-data-concurrency-test.XXXXXX")"
trap 'rm -rf "$TMP_ROOT"' EXIT

grep -q 'SAMPLE_DATA_CONCURRENCY_RUNNER' "$SCRIPT"

FAKE_RUNNER="$TMP_ROOT/fake-runner.sh"
cat >"$FAKE_RUNNER" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

output="${SAMPLE_DATA_PARSER_CLI_OUT:?}"
parallelism="${SAMPLE_DATA_PARSER_CLI_CASE_PARALLELISM:?}"
index=1
while [[ "$index" -le 19 ]]; do
  category="case-$index"
  [[ "$index" -ne 1 ]] || category="mysql-v8_0-full"
  mkdir -p "$output/results/$category"
  printf '{"generatedAt":"%s","identity":"%s","view":"direct"}\n' \
    "$parallelism" "$category" >"$output/results/$category/direct.json"
  payload="$category"
  if [[ "${SAMPLE_DATA_CONCURRENCY_TEST_MISMATCH:-false}" == "true" && \
        "$parallelism" -eq 3 && "$index" -eq 1 ]]; then
    payload="parallel-mismatch"
  fi
  printf '{"generatedAt":"%s","identity":"%s","view":"result"}\n' \
    "$parallelism" "$payload" >"$output/results/$category/result.json"
  index=$((index + 1))
done
EOF
chmod +x "$FAKE_RUNNER"

SAMPLE_DATA_CONCURRENCY_RUNNER="$FAKE_RUNNER" \
SAMPLE_DATA_CONCURRENCY_OUT="$TMP_ROOT/pass" \
  bash "$SCRIPT" >"$TMP_ROOT/pass.out"
grep -q 'verified: 38 JSON files' "$TMP_ROOT/pass.out"
[[ "$(find "$TMP_ROOT/pass/serial/results" -mindepth 2 -maxdepth 2 -type f \
  \( -name direct.json -o -name result.json \) | wc -l | tr -d '[:space:]')" -eq 38 ]]

if SAMPLE_DATA_CONCURRENCY_RUNNER="$FAKE_RUNNER" \
  SAMPLE_DATA_CONCURRENCY_TEST_MISMATCH=true \
  SAMPLE_DATA_CONCURRENCY_OUT="$TMP_ROOT/fail" \
    bash "$SCRIPT" >"$TMP_ROOT/fail.out" 2>"$TMP_ROOT/fail.err"; then
  echo "concurrency parity must reject a changed bundle leaf" >&2
  exit 1
fi
grep -q 'Canonical mismatch: mysql-v8_0-full/result.json' "$TMP_ROOT/fail.err"

echo "sample-data concurrency parity test passed"
