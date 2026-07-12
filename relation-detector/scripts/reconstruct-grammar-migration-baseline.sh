#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUTPUT_ROOT="${PHASE0_OUTPUT_ROOT:-$ROOT/relation-detector/target/phase0-reconstruction}"
WORKTREE_ROOT="${PHASE0_WORKTREE_ROOT:-/private/tmp}"
PLAN_ONLY=false
ACTIVE_WORKTREES=()

if [[ "${1:-}" == "--plan-only" ]]; then
  PLAN_ONLY=true
  shift
fi
if [[ "$#" -ne 3 ]]; then
  echo "usage: $0 [--plan-only] <pre-migration-commit> <migration-commit> <current-commit>" >&2
  exit 2
fi

COMMIT_A="$1"
COMMIT_B="$2"
COMMIT_C="$3"

worktree_path() {
  local label="$1"
  local commit="$2"
  local lower_label
  lower_label="$(printf '%s' "$label" | tr '[:upper:]' '[:lower:]')"
  printf '%s/relation-detector-baseline-%s-%s' "$WORKTREE_ROOT" "$lower_label" "${commit:0:8}"
}

cleanup() {
  local worktree
  for worktree in "${ACTIVE_WORKTREES[@]:-}"; do
    if [[ -n "$worktree" && -e "$worktree" ]]; then
      git -C "$ROOT" worktree remove --force "$worktree" >/dev/null 2>&1 || true
    fi
  done
  git -C "$ROOT" worktree prune >/dev/null 2>&1 || true
}
trap cleanup EXIT

if [[ "$PLAN_ONLY" == "true" ]]; then
  python3 - "$OUTPUT_ROOT" \
    "$COMMIT_A" "$(worktree_path A "$COMMIT_A")" \
    "$COMMIT_B" "$(worktree_path B "$COMMIT_B")" \
    "$COMMIT_C" "$(worktree_path C "$COMMIT_C")" <<'PY'
import json
import sys

output, a, aw, b, bw, c, cw = sys.argv[1:]
print(json.dumps({
    "outputRoot": output,
    "checkpoints": {
        "A": {"commit": a, "worktree": aw},
        "B": {"commit": b, "worktree": bw},
        "C": {"commit": c, "worktree": cw},
    },
}, indent=2, sort_keys=True))
PY
  exit 0
fi

mkdir -p "$OUTPUT_ROOT"

write_inventories() {
  local commit="$1"
  local destination="$2"
  git -C "$ROOT" ls-tree -r --name-only "$commit" | grep -E '\.g4$' \
    >"$destination/tracked-g4.txt" || true
  git -C "$ROOT" ls-tree -r --name-only "$commit" | grep -E '(^|/)README\.md$' | grep '/grammar/' \
    >"$destination/grammar-readmes.txt" || true
  git -C "$ROOT" grep -h '^package ' "$commit" -- '*.java' 2>/dev/null | sort -u \
    >"$destination/java-packages.txt" || true
  git -C "$ROOT" ls-tree -r --name-only "$commit" | grep '/META-INF/services/' \
    >"$destination/serviceloader-files.txt" || true
}

write_checkpoint_manifest() {
  local label="$1"
  local commit="$2"
  local parser_baseline_status="$3"
  local acceptance_status="$4"
  local destination="$5"
  local generated_report_gate="$6"
  python3 - "$label" "$commit" "$parser_baseline_status" "$acceptance_status" \
    "$destination" "$generated_report_gate" <<'PY'
import csv
import hashlib
import json
import sys
from pathlib import Path

label, commit, parser_status, acceptance_status, raw_destination, generated_report_gate = sys.argv[1:]
destination = Path(raw_destination)
results = sorted((destination / "results").glob("*.json"))
direct = [path for path in results if not path.stem.endswith("-derived-fresh")]
correctness_path = destination / "correctness-run-summary.json"
correctness = json.loads(correctness_path.read_text(encoding="utf-8")) if correctness_path.exists() else {}

warning_total = 0
warning_path = destination / "warning-codes.tsv"
if warning_path.exists():
    with warning_path.open(encoding="utf-8", newline="") as handle:
        warning_total = sum(int(row.get("count") or 0) for row in csv.DictReader(handle, delimiter="\t"))

parity_differences = 0
parity_path = destination / "observation-parity.tsv"
if parity_path.exists():
    with parity_path.open(encoding="utf-8", newline="") as handle:
        parity_differences = sum(
            int(row.get("TokenOnly") or 0) + int(row.get("FullOnly") or 0)
            for row in csv.DictReader(handle, delimiter="\t")
        )

artifacts = []
for path in sorted(destination.iterdir()):
    if not path.is_file() or path.name == "checkpoint-manifest.json":
        continue
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    artifacts.append({"path": path.name, "sha256": digest, "bytes": path.stat().st_size})

manifest = {
    "label": label,
    "commit": commit,
    "status": (
        "PASS" if int(parser_status) == 0 and int(acceptance_status) == 0
        else "PARTIAL_HISTORICAL" if int(parser_status) == 0
        else "FAIL"
    ),
    "acceptanceStatus": int(acceptance_status),
    "parserBaselineStatus": int(parser_status),
    "generatedReportFreshness": generated_report_gate,
    "correctness": correctness,
    "parserCategories": len(direct),
    "jsonFiles": len(results),
    "diagnostics": warning_total,
    "observationParityDifferences": parity_differences,
    "artifacts": artifacts,
}
(destination / "checkpoint-manifest.json").write_text(
    json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8"
)
PY
}

collect_checkpoint() {
  local label="$1"
  local commit="$2"
  local source_root="$3"
  local parser_baseline_status="$4"
  local acceptance_status="$5"
  local generated_report_gate="$6"
  local destination="$OUTPUT_ROOT/$label-$commit"
  local source_target="$source_root/relation-detector/target"

  mkdir -p "$destination"
  if [[ -d "$source_target/sample-data-parser-cli/results" ]]; then
    cp -R "$source_target/sample-data-parser-cli/results" "$destination/results"
  fi
  for name in summary.tsv summary-with-derived.tsv warning-codes.tsv observation-parity.tsv batch-report.json; do
    if [[ -f "$source_target/sample-data-parser-cli/$name" ]]; then
      cp "$source_target/sample-data-parser-cli/$name" "$destination/$name"
    fi
  done
  if [[ -f "$source_target/correctness-run-summary.json" ]]; then
    cp "$source_target/correctness-run-summary.json" "$destination/correctness-run-summary.json"
  fi
  if [[ -f "$OUTPUT_ROOT/$label-$commit-run.log" ]]; then
    cp "$OUTPUT_ROOT/$label-$commit-run.log" "$destination/acceptance.log"
  else
    local latest_acceptance
    latest_acceptance="$(find "$source_target/verification" -name acceptance.log -type f -print 2>/dev/null \
      | sort | tail -n 1)"
    if [[ -n "$latest_acceptance" ]]; then
      cp "$latest_acceptance" "$destination/acceptance.log"
    fi
  fi
  write_inventories "$commit" "$destination"
  if [[ -d "$destination/results" ]]; then
    python3 "$ROOT/relation-detector/scripts/canonical-json-fingerprint.py" \
      "$destination/results" >"$destination/fingerprints.tsv"
    python3 "$ROOT/relation-detector/scripts/canonical-json-fingerprint.py" --semantic \
      "$destination/results" >"$destination/semantic-fingerprints.tsv"
    python3 "$ROOT/relation-detector/scripts/compare-semantic-results.py" \
      --inventory-root "$destination/results" \
      --output "$destination/semantic-inventory.json"
  fi
  write_checkpoint_manifest "$label" "$commit" "$parser_baseline_status" \
    "$acceptance_status" "$destination" "$generated_report_gate"
}

run_checkpoint() {
  local label="$1"
  local commit="$2"
  local reuse_root="${3:-}"
  local completed_manifest="$OUTPUT_ROOT/$label-$commit/checkpoint-manifest.json"
  local worktree
  local acceptance_status=0
  local parser_baseline_status=0
  local correctness_status=0
  local package_status=0
  local cli_status=0
  local validation_status=0

  if [[ "${PHASE0_REUSE_COMPLETED:-true}" == "true" && -f "$completed_manifest" ]]; then
    if python3 - "$completed_manifest" "$commit" <<'PY'
import json
import sys

manifest = json.load(open(sys.argv[1], encoding="utf-8"))
valid = (
    manifest.get("commit") == sys.argv[2]
    and manifest.get("parserBaselineStatus") == 0
    and manifest.get("parserCategories") == 19
    and manifest.get("jsonFiles") == 38
)
raise SystemExit(0 if valid else 1)
PY
    then
      echo "Reusing completed checkpoint $label ($commit)"
      return
    fi
  fi

  git -C "$ROOT" cat-file -e "$commit^{commit}"
  if [[ -n "$reuse_root" ]]; then
    local actual
    actual="$(git -C "$reuse_root" rev-parse HEAD)"
    if [[ "$actual" != "$(git -C "$ROOT" rev-parse "$commit")" ]]; then
      echo "Cannot reuse $reuse_root for checkpoint $label: expected $commit, found $actual" >&2
      return 1
    fi
    collect_checkpoint "$label" "$commit" "$reuse_root" 0 0 "EXECUTED"
    return
  fi

  worktree="$(worktree_path "$label" "$commit")"
  if [[ -e "$worktree" ]]; then
    echo "Refusing to overwrite existing worktree path: $worktree" >&2
    return 1
  fi
  git -C "$ROOT" worktree add --detach "$worktree" "$commit"
  ACTIVE_WORKTREES+=("$worktree")
  local log="$OUTPUT_ROOT/$label-$commit-run.log"
  printf '%s\n' "Historical acceptance excludes generated report freshness tests; parser fixtures remain full." \
    >"$log"

  set +e
  (cd "$worktree" && mvn -T 2 -Pacceptance -DrunGeneratedReportTests=false verify) \
    >>"$log" 2>&1
  acceptance_status=$?
  set -e

  set +e
  python3 - "$worktree/relation-detector/target/correctness-run-summary.json" <<'PY'
import json
import sys
from pathlib import Path

path = Path(sys.argv[1])
if not path.exists():
    raise SystemExit(1)
data = json.loads(path.read_text(encoding="utf-8"))
raise SystemExit(0 if data.get("executed") == 1198 and data.get("passed") == 1198 and data.get("failed") == 0 else 1)
PY
  correctness_status=$?
  set -e
  if [[ "$correctness_status" -ne 0 ]]; then
    set +e
    (cd "$worktree" && mvn -pl relation-detector/cli -am \
      -Dtest=CorrectnessFixtureRunnerTest \
      -DcorrectnessFixtureProfile=full \
      -DcorrectnessFixtureParallelism=12 \
      -Dsurefire.failIfNoSpecifiedTests=false test) >>"$log" 2>&1
    correctness_status=$?
    set -e
  fi

  set +e
  (cd "$worktree" && mvn -pl relation-detector/cli -am -Dmaven.test.skip=true package) \
    >>"$log" 2>&1
  package_status=$?
  set -e
  if [[ "$package_status" -eq 0 ]]; then
    set +e
    (
      cd "$worktree"
      SAMPLE_DATA_PARSER_CLI_SKIP_PACKAGE=true \
      SAMPLE_DATA_PARSER_CLI_CASE_PARALLELISM="${SAMPLE_DATA_PARSER_CLI_CASE_PARALLELISM:-3}" \
      SAMPLE_DATA_PARSER_CLI_SCAN_PARALLELISM="${SAMPLE_DATA_PARSER_CLI_SCAN_PARALLELISM:-2}" \
        bash relation-detector/test-fixtures/examples/sample-data-parser-cli/run-all-sample-data-parsers.sh
    ) >>"$log" 2>&1
    cli_status=$?
    set -e
  else
    cli_status=1
  fi
  if [[ "$cli_status" -eq 0 ]]; then
    set +e
    (cd "$worktree" && python3 "$ROOT/relation-detector/scripts/validate-sample-data-results.py" \
      relation-detector/target/sample-data-parser-cli/results) >>"$log" 2>&1
    validation_status=$?
    set -e
  else
    validation_status=1
  fi
  if [[ "$correctness_status" -ne 0 || "$package_status" -ne 0 || \
        "$cli_status" -ne 0 || "$validation_status" -ne 0 ]]; then
    parser_baseline_status=1
  fi

  collect_checkpoint "$label" "$commit" "$worktree" "$parser_baseline_status" \
    "$acceptance_status" "SKIPPED_HISTORICAL_STALE"
  if [[ "$parser_baseline_status" -ne 0 ]]; then
    echo "Checkpoint $label ($commit) failed; see $OUTPUT_ROOT/$label-$commit-run.log" >&2
    return "$parser_baseline_status"
  fi
}

run_checkpoint A "$COMMIT_A" "${PHASE0_REUSE_A_ROOT:-}"
run_checkpoint B "$COMMIT_B" "${PHASE0_REUSE_B_ROOT:-}"
run_checkpoint C "$COMMIT_C" "${PHASE0_REUSE_C_ROOT:-}"

python3 "$ROOT/relation-detector/scripts/compare-semantic-results.py" \
  --before "$OUTPUT_ROOT/A-$COMMIT_A/results" \
  --after "$OUTPUT_ROOT/B-$COMMIT_B/results" \
  --transition A_TO_B \
  --output "$OUTPUT_ROOT/a-to-b-semantic-diff.json"
python3 "$ROOT/relation-detector/scripts/compare-semantic-results.py" \
  --before "$OUTPUT_ROOT/B-$COMMIT_B/results" \
  --after "$OUTPUT_ROOT/C-$COMMIT_C/results" \
  --transition B_TO_C \
  --output "$OUTPUT_ROOT/b-to-c-semantic-diff.json"

echo "Phase 0 reconstruction artifacts: $OUTPUT_ROOT"
