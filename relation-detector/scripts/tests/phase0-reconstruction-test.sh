#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SCRIPT="$ROOT/relation-detector/scripts/reconstruct-grammar-migration-baseline.sh"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

[[ -x "$SCRIPT" ]]
grep -q 'trap cleanup EXIT' "$SCRIPT"
grep -q 'worktree add --detach' "$SCRIPT"
grep -q 'worktree remove --force' "$SCRIPT"
grep -q 'runGeneratedReportTests=false' "$SCRIPT"
grep -q 'run-all-sample-data-parsers.sh' "$SCRIPT"
grep -q 'PARTIAL_HISTORICAL' "$SCRIPT"
grep -q 'parserBaselineStatus' "$SCRIPT"
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

echo "phase0 reconstruction test passed"
