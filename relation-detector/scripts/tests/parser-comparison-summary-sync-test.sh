#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SYNC="$ROOT/relation-detector/scripts/sync-parser-comparison-summary.py"
SUMMARY="$ROOT/relation-detector/target/sample-data-parser-cli/summary-with-derived.tsv"
DOCUMENT="$ROOT/docs/parser-audit/parser-comparison-summary.md"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

cp "$DOCUMENT" "$TMP_DIR/parser-comparison-summary.md"

python3 - "$TMP_DIR/parser-comparison-summary.md" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
current = "| Oracle token-event root sample-data | 38 | 32 / 6 | 366 | 328 | 248 | 0 | 984 | 57 | 724 |"
stale = "| Oracle token-event root sample-data | 38 | 32 / 6 | 366 | 328 | 248 | 0 | 1265 | 57 | 906 |"
if current not in text:
    raise SystemExit("expected current Oracle row is missing")
path.write_text(text.replace(current, stale, 1), encoding="utf-8")
PY

if python3 "$SYNC" --summary "$SUMMARY" --document "$TMP_DIR/parser-comparison-summary.md"; then
  echo "stale parser comparison summary must fail" >&2
  exit 1
fi

python3 "$SYNC" --summary "$SUMMARY" --document "$TMP_DIR/parser-comparison-summary.md" --update
python3 "$SYNC" --summary "$SUMMARY" --document "$TMP_DIR/parser-comparison-summary.md"

echo "parser comparison summary sync test passed"
