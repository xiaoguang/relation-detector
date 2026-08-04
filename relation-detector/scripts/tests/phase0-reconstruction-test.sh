#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SCRIPT="$ROOT/relation-detector/scripts/migration/reconstruct-grammar-migration-baseline.sh"
CHECKPOINT_HELPER="$ROOT/relation-detector/scripts/migration/phase0-checkpoint.py"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

[[ -x "$SCRIPT" ]]
grep -q 'trap cleanup EXIT' "$SCRIPT"
grep -q 'worktree add --detach' "$SCRIPT"
grep -q 'worktree remove --force' "$SCRIPT"
grep -q 'runGeneratedReportTests=false' "$SCRIPT"
grep -q 'run-all-sample-data-parsers.sh' "$SCRIPT"
grep -q 'PARTIAL_HISTORICAL' "$CHECKPOINT_HELPER"
grep -q 'parserBaselineStatus' "$CHECKPOINT_HELPER"
grep -q 'PHASE0_REUSE_COMPLETED' "$SCRIPT"

bash "$SCRIPT" --plan-only aaaaaaaa bbbbbbbb cccccccc >"$TMP_DIR/plan.json"
jq -e '
  .checkpoints.A.commit == "aaaaaaaa" and
  .checkpoints.B.commit == "bbbbbbbb" and
  .checkpoints.C.commit == "cccccccc" and
  (.checkpoints.A.worktree | endswith("relation-detector-baseline-a-aaaaaaaa")) and
  (.checkpoints.B.worktree | endswith("relation-detector-baseline-b-bbbbbbbb")) and
  (.checkpoints.C.worktree | endswith("relation-detector-baseline-c-cccccccc")) and
  (.outputRoot | endswith("relation-detector/target/phase0-reconstruction"))
' "$TMP_DIR/plan.json" >/dev/null

CHECKPOINT="$TMP_DIR/checkpoint"
FLAT_SOURCE="$TMP_DIR/historical-flat"
FLAT_REPORT="$TMP_DIR/historical-batch-report.json"
CONVERTED="$TMP_DIR/converted-results"
mkdir -p "$FLAT_SOURCE"
python3 - "$FLAT_SOURCE" "$FLAT_REPORT" <<'PY'
import json
import sys
from pathlib import Path

root = Path(sys.argv[1])
report = Path(sys.argv[2])
case_ids = [f"case-{index:02d}" for index in range(1, 20)]
for case_id in case_ids:
    (root / f"{case_id}.json").write_text(
        json.dumps({"case": case_id, "view": "direct"}) + "\n", encoding="utf-8"
    )
    (root / f"{case_id}-historical-full.json").write_text(
        json.dumps({"case": case_id, "view": "full"}) + "\n", encoding="utf-8"
    )
report.write_text(
    json.dumps({"cases": [{"id": case_id} for case_id in case_ids]}) + "\n",
    encoding="utf-8",
)
PY

bash "$SCRIPT" --convert-results-only "$FLAT_SOURCE" "$FLAT_REPORT" "$CONVERTED"
[[ "$(find "$CONVERTED" -mindepth 1 -maxdepth 1 -type d | wc -l | tr -d '[:space:]')" -eq 19 ]]
[[ "$(find "$CONVERTED" -mindepth 2 -maxdepth 2 -type f | wc -l | tr -d '[:space:]')" -eq 38 ]]
jq -e '.case == "case-01" and .view == "direct"' "$CONVERTED/case-01/direct.json" >/dev/null
jq -e '.case == "case-01" and .view == "full"' "$CONVERTED/case-01/result.json" >/dev/null

IMPORTED_BUNDLE="$TMP_DIR/imported-bundle-results"
bash "$SCRIPT" --import-results-only "$CONVERTED" "$FLAT_REPORT" "$IMPORTED_BUNDLE"
cmp "$CONVERTED/case-01/direct.json" "$IMPORTED_BUNDLE/case-01/direct.json"
cmp "$CONVERTED/case-01/result.json" "$IMPORTED_BUNDLE/case-01/result.json"

printf '{}\n' >"$FLAT_SOURCE/unexpected.json"
if bash "$SCRIPT" --convert-results-only \
  "$FLAT_SOURCE" "$FLAT_REPORT" "$TMP_DIR/extra-converted" >/dev/null 2>&1; then
  echo "historical conversion accepted an extra flat result" >&2
  exit 1
fi
[[ ! -e "$TMP_DIR/extra-converted" ]]
mv "$FLAT_SOURCE/unexpected.json" "$TMP_DIR/unexpected.json"

mv "$FLAT_SOURCE/case-02-historical-full.json" "$TMP_DIR/case-02-full.json"
if bash "$SCRIPT" --convert-results-only \
  "$FLAT_SOURCE" "$FLAT_REPORT" "$TMP_DIR/missing-converted" >/dev/null 2>&1; then
  echo "historical conversion accepted a missing full result" >&2
  exit 1
fi
[[ ! -e "$TMP_DIR/missing-converted" ]]
mv "$TMP_DIR/case-02-full.json" "$FLAT_SOURCE/case-02-historical-full.json"

mv "$FLAT_SOURCE/case-03.json" "$TMP_DIR/case-03.json"
ln -s "$TMP_DIR/case-03.json" "$FLAT_SOURCE/case-03.json"
if bash "$SCRIPT" --convert-results-only \
  "$FLAT_SOURCE" "$FLAT_REPORT" "$TMP_DIR/symlink-converted" >/dev/null 2>&1; then
  echo "historical conversion accepted a symlink flat result" >&2
  exit 1
fi
[[ ! -e "$TMP_DIR/symlink-converted" ]]
unlink "$FLAT_SOURCE/case-03.json"
mv "$TMP_DIR/case-03.json" "$FLAT_SOURCE/case-03.json"

mkdir -p "$TMP_DIR/stale-converted"
printf 'stale\n' >"$TMP_DIR/stale-converted/stale.txt"
if bash "$SCRIPT" --convert-results-only \
  "$FLAT_SOURCE" "$FLAT_REPORT" "$TMP_DIR/stale-converted" >/dev/null 2>&1; then
  echo "historical conversion merged into a stale destination" >&2
  exit 1
fi
[[ -f "$TMP_DIR/stale-converted/stale.txt" ]]

mkdir -p "$CHECKPOINT"
cp -R "$CONVERTED" "$CHECKPOINT/results"
bash "$SCRIPT" --manifest-only "$CHECKPOINT" A aaaaaaaa 0 0 EXECUTED
jq -e '
  .parserCategories == 19 and
  .jsonFiles == 38 and
  .resultFiles[0].path == "case-01/direct.json" and
  .resultFiles[1].path == "case-01/result.json" and
  all(.resultFiles[]; (.sha256 | length) == 64 and .bytes > 0)
' "$CHECKPOINT/checkpoint-manifest.json" >/dev/null

bash "$SCRIPT" --reuse-valid-only "$CHECKPOINT" aaaaaaaa

printf '\n' >>"$CHECKPOINT/results/case-01/direct.json"
if bash "$SCRIPT" --reuse-valid-only "$CHECKPOINT" aaaaaaaa >/dev/null 2>&1; then
  echo "checkpoint reuse accepted a content mutation" >&2
  exit 1
fi
cp "$CONVERTED/case-01/direct.json" "$CHECKPOINT/results/case-01/direct.json"

mv "$CHECKPOINT/results/case-02/result.json" "$TMP_DIR/missing-result.json"
if bash "$SCRIPT" --reuse-valid-only "$CHECKPOINT" aaaaaaaa >/dev/null 2>&1; then
  echo "checkpoint reuse accepted a missing result leaf" >&2
  exit 1
fi
mv "$TMP_DIR/missing-result.json" "$CHECKPOINT/results/case-02/result.json"

printf 'extra\n' >"$CHECKPOINT/results/case-03/extra.txt"
if bash "$SCRIPT" --reuse-valid-only "$CHECKPOINT" aaaaaaaa >/dev/null 2>&1; then
  echo "checkpoint reuse accepted an extra bundle entry" >&2
  exit 1
fi
mv "$CHECKPOINT/results/case-03/extra.txt" "$TMP_DIR/extra.txt"

mv "$CHECKPOINT/results/case-04/direct.json" "$TMP_DIR/case-04-direct.json"
ln -s "$TMP_DIR/case-04-direct.json" "$CHECKPOINT/results/case-04/direct.json"
if bash "$SCRIPT" --reuse-valid-only "$CHECKPOINT" aaaaaaaa >/dev/null 2>&1; then
  echo "checkpoint reuse accepted a symlink result leaf" >&2
  exit 1
fi
unlink "$CHECKPOINT/results/case-04/direct.json"
mv "$TMP_DIR/case-04-direct.json" "$CHECKPOINT/results/case-04/direct.json"

EXTRA_CHECKPOINT="$TMP_DIR/extra-checkpoint"
mkdir -p "$EXTRA_CHECKPOINT"
cp -R "$CONVERTED" "$EXTRA_CHECKPOINT/results"
printf 'extra\n' >"$EXTRA_CHECKPOINT/results/case-05/extra.txt"
if bash "$SCRIPT" --manifest-only "$EXTRA_CHECKPOINT" A aaaaaaaa 0 0 EXECUTED \
    >/dev/null 2>&1; then
  echo "checkpoint manifest accepted an extra bundle entry" >&2
  exit 1
fi
[[ ! -e "$EXTRA_CHECKPOINT/checkpoint-manifest.json" ]]

SYMLINK_CHECKPOINT="$TMP_DIR/symlink-checkpoint"
mkdir -p "$SYMLINK_CHECKPOINT/results"
cp -R "$CONVERTED/case-01" "$SYMLINK_CHECKPOINT/external-case"
ln -s "$SYMLINK_CHECKPOINT/external-case" "$SYMLINK_CHECKPOINT/results/case-01"
for index in $(seq -w 2 19); do
  cp -R "$CONVERTED/case-$index" "$SYMLINK_CHECKPOINT/results/case-$index"
done
if bash "$SCRIPT" --manifest-only "$SYMLINK_CHECKPOINT" A aaaaaaaa 0 0 EXECUTED \
    >/dev/null 2>&1; then
  echo "checkpoint manifest accepted a category symlink" >&2
  exit 1
fi
[[ ! -e "$SYMLINK_CHECKPOINT/checkpoint-manifest.json" ]]

LARGE_CHECKPOINT="$TMP_DIR/large-checkpoint"
mkdir -p "$LARGE_CHECKPOINT"
cp -R "$CONVERTED" "$LARGE_CHECKPOINT/results"
dd if=/dev/zero of="$LARGE_CHECKPOINT/results/case-19/result.json" \
  bs=1048576 count=128 2>/dev/null
python3 - "$SCRIPT" "$LARGE_CHECKPOINT" <<'PY'
import resource
import subprocess
import sys

subprocess.run([
    "bash", sys.argv[1], "--manifest-only", sys.argv[2],
    "A", "aaaaaaaa", "0", "0", "EXECUTED"
], check=True)
maximum_rss = resource.getrusage(resource.RUSAGE_CHILDREN).ru_maxrss
if sys.platform != "darwin":
    maximum_rss *= 1024
if maximum_rss >= 64 * 1024 * 1024:
    raise SystemExit(f"checkpoint hashing exceeded bounded memory: {maximum_rss}")
PY
[[ "$(jq -r '.resultFiles[] | select(.path == "case-19/result.json") | .bytes' \
  "$LARGE_CHECKPOINT/checkpoint-manifest.json")" -eq 134217728 ]]

if rg -n 'read_bytes' \
  "$SCRIPT" "$ROOT/relation-detector/scripts/migration/phase0-checkpoint.py"; then
  echo "checkpoint hashing must stream files in bounded chunks" >&2
  exit 1
fi

echo "phase0 reconstruction test passed"
