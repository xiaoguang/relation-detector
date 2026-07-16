#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

if [[ -n "$(git status --porcelain)" ]]; then
  echo "release verification requires a clean worktree" >&2
  exit 1
fi

SESSION_ID="${VERIFY_SESSION_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
VERIFY_DIR="$ROOT/relation-detector/target/verification/$SESSION_ID"
NO_CACHE_LOG="$(mktemp "${TMPDIR:-/tmp}/relation-detector-no-cache.XXXXXX.log")"
trap 'rm -f "$NO_CACHE_LOG"' EXIT

# A previous sample-data run must not affect the no-cache freshness tests.
# verify-all recreates this summary from the current commit after acceptance.
rm -f relation-detector/target/sample-data-parser-cli/summary-with-derived.tsv

write_failure_manifest() {
  local phase="$1"
  local status="$2"
  local message="$3"
  mkdir -p "$VERIFY_DIR"
  cp "$NO_CACHE_LOG" "$VERIFY_DIR/no-cache-acceptance.log"
  python3 - "$VERIFY_DIR/verification-manifest.json" "$phase" "$status" "$message" <<'PY'
import hashlib
import json
import subprocess
import sys
from pathlib import Path

output, phase, status, message = sys.argv[1:]
log = Path(output).parent / "no-cache-acceptance.log"
manifest = {
    "status": "FAIL",
    "commit": subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip(),
    "branch": subprocess.check_output(["git", "branch", "--show-current"], text=True).strip() or "DETACHED",
    "failedPhase": phase,
    "errors": [message],
    "maven": {phase + "Status": int(status)},
    "artifacts": [{
        "path": log.name,
        "sha256": hashlib.sha256(log.read_bytes()).hexdigest(),
        "bytes": log.stat().st_size,
    }],
}
Path(output).write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY
}

set +e
mvn -T 2 -Pacceptance \
  -Dmaven.build.cache.enabled=false \
  -DcorrectnessFixtureProfile=smoke \
  -DcorrectnessFixtureParallelism=6 \
  clean verify 2>&1 | tee "$NO_CACHE_LOG"
NO_CACHE_STATUS="${PIPESTATUS[0]}"
set -e
if [[ "$NO_CACHE_STATUS" -ne 0 ]]; then
  write_failure_manifest noCache "$NO_CACHE_STATUS" "no-cache acceptance failed"
  echo "no-cache acceptance failed; manifest: $VERIFY_DIR/verification-manifest.json" >&2
  exit "$NO_CACHE_STATUS"
fi

set +e
VERIFY_SESSION_ID="$SESSION_ID" \
VERIFY_NO_CACHE_STATUS="$NO_CACHE_STATUS" \
VERIFY_NO_CACHE_LOG="$NO_CACHE_LOG" \
  bash relation-detector/scripts/verify-all.sh
VERIFY_ALL_STATUS=$?
set -e
if [[ "$VERIFY_ALL_STATUS" -ne 0 ]]; then
  if [[ ! -f "$VERIFY_DIR/verification-manifest.json" ]]; then
    write_failure_manifest verifyAll "$VERIFY_ALL_STATUS" "verify-all acceptance failed"
  fi
  exit "$VERIFY_ALL_STATUS"
fi

echo "Release verification completed: relation-detector/target/verification/$SESSION_ID"
