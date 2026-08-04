#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
CHECKPOINT_HELPER="$ROOT/relation-detector/scripts/migration/phase0-checkpoint.py"
OUTPUT_ROOT="${PHASE0_OUTPUT_ROOT:-$ROOT/relation-detector/target/phase0-reconstruction}"
WORKTREE_ROOT="${PHASE0_WORKTREE_ROOT:-/private/tmp}"
PLAN_ONLY=false
MANIFEST_ONLY=false
ACTIVE_WORKTREES=()

if [[ "${1:-}" == "--import-results-only" ]]; then
  if [[ "$#" -ne 4 ]]; then
    echo "usage: $0 --import-results-only <source-results> <batch-report> <destination-results>" >&2
    exit 2
  fi
  python3 "$CHECKPOINT_HELPER" import-results "$2" "$3" "$4"
  exit 0
elif [[ "${1:-}" == "--convert-results-only" ]]; then
  if [[ "$#" -ne 4 ]]; then
    echo "usage: $0 --convert-results-only <source-results> <batch-report> <destination-results>" >&2
    exit 2
  fi
  python3 "$CHECKPOINT_HELPER" convert-results "$2" "$3" "$4"
  exit 0
elif [[ "${1:-}" == "--reuse-valid-only" ]]; then
  if [[ "$#" -ne 3 ]]; then
    echo "usage: $0 --reuse-valid-only <checkpoint> <commit>" >&2
    exit 2
  fi
  python3 "$CHECKPOINT_HELPER" verify-reuse "$2" "$3"
  exit 0
elif [[ "${1:-}" == "--manifest-only" ]]; then
  MANIFEST_ONLY=true
  shift
  if [[ "$#" -ne 6 ]]; then
    echo "usage: $0 --manifest-only <destination> <label> <commit> <parser-status> <acceptance-status> <generated-report-gate>" >&2
    exit 2
  fi
  MANIFEST_DESTINATION="$1"
  MANIFEST_LABEL="$2"
  MANIFEST_COMMIT="$3"
  MANIFEST_PARSER_STATUS="$4"
  MANIFEST_ACCEPTANCE_STATUS="$5"
  MANIFEST_GENERATED_REPORT_GATE="$6"
elif [[ "${1:-}" == "--plan-only" ]]; then
  PLAN_ONLY=true
  shift
fi
if [[ "$MANIFEST_ONLY" == "false" ]]; then
  if [[ "$#" -ne 3 ]]; then
    echo "usage: $0 [--plan-only] <pre-migration-commit> <migration-commit> <current-commit>" >&2
    exit 2
  fi
  COMMIT_A="$1"
  COMMIT_B="$2"
  COMMIT_C="$3"
fi

worktree_path() {
  local label="$1"
  local commit="$2"
  local lower_label
  lower_label="$(printf '%s' "$label" | tr '[:upper:]' '[:lower:]')"
  printf '%s/relation-detector-baseline-%s-%s' "$WORKTREE_ROOT" "$lower_label" "${commit:0:8}"
}

cleanup() {
  [[ "$MANIFEST_ONLY" == "false" ]] || return 0
  local worktree
  for worktree in "${ACTIVE_WORKTREES[@]:-}"; do
    if [[ -n "$worktree" && -e "$worktree" ]]; then
      git -C "$ROOT" worktree remove --force "$worktree" >/dev/null 2>&1 || true
    fi
  done
  git -C "$ROOT" worktree prune >/dev/null 2>&1 || true
}
trap cleanup EXIT

if [[ "$MANIFEST_ONLY" == "false" && "$PLAN_ONLY" == "true" ]]; then
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
  python3 "$CHECKPOINT_HELPER" write-manifest \
    "$destination" "$label" "$commit" "$parser_baseline_status" \
    "$acceptance_status" "$generated_report_gate"
}

if [[ "$MANIFEST_ONLY" == "true" ]]; then
  write_checkpoint_manifest "$MANIFEST_LABEL" "$MANIFEST_COMMIT" \
    "$MANIFEST_PARSER_STATUS" "$MANIFEST_ACCEPTANCE_STATUS" \
    "$MANIFEST_DESTINATION" "$MANIFEST_GENERATED_REPORT_GATE"
  exit 0
fi

mkdir -p "$OUTPUT_ROOT"

collect_checkpoint() {
  local label="$1"
  local commit="$2"
  local source_root="$3"
  local parser_baseline_status="$4"
  local acceptance_status="$5"
  local generated_report_gate="$6"
  local destination="$OUTPUT_ROOT/$label-$commit"
  local staging="$OUTPUT_ROOT/.$label-$commit.staging.$$"
  local source_target="$source_root/relation-detector/target"
  local source_sample="$source_target/sample-data-parser-cli"
  local validation_log="$OUTPUT_ROOT/$label-$commit-validation.log"
  local validation_status=0

  if [[ -e "$destination" || -L "$destination" ]]; then
    echo "Refusing to merge checkpoint into an existing destination: $destination" >&2
    return 1
  fi
  if [[ -e "$staging" || -L "$staging" ]]; then
    echo "Refusing to reuse an existing checkpoint staging path: $staging" >&2
    return 1
  fi
  mkdir "$staging"
  python3 "$CHECKPOINT_HELPER" import-results \
    "$source_sample/results" "$source_sample/batch-report.json" "$staging/results"
  set +e
  RELATION_DETECTOR_VERIFICATION_WORKING_DIRECTORY="$source_root" \
    "$ROOT/relation-detector/scripts/run-release-verification-tool.sh" validate-results \
    --result-dir "$staging/results" \
    --expected-categories 19 \
    --output "$staging/result-validation.json" >>"$validation_log" 2>&1
  validation_status=$?
  set -e
  if [[ "$validation_status" -ne 0 ]]; then
    parser_baseline_status=1
  fi
  for name in summary.tsv summary-with-derived.tsv warning-codes.tsv observation-parity.tsv batch-report.json; do
    if [[ -f "$source_sample/$name" && ! -L "$source_sample/$name" ]]; then
      cp "$source_sample/$name" "$staging/$name"
    fi
  done
  if [[ -f "$source_target/correctness-run-summary.json" \
      && ! -L "$source_target/correctness-run-summary.json" ]]; then
    cp "$source_target/correctness-run-summary.json" "$staging/correctness-run-summary.json"
  fi
  if [[ -f "$OUTPUT_ROOT/$label-$commit-run.log" ]]; then
    cp "$OUTPUT_ROOT/$label-$commit-run.log" "$staging/acceptance.log"
  else
    local latest_acceptance
    latest_acceptance="$(find "$source_target/verification" -name acceptance.log -type f -print 2>/dev/null \
      | sort | tail -n 1)"
    if [[ -n "$latest_acceptance" ]]; then
      cp "$latest_acceptance" "$staging/acceptance.log"
    fi
  fi
  write_inventories "$commit" "$staging"
  if [[ -d "$staging/results" ]]; then
    "$ROOT/relation-detector/scripts/run-release-verification-tool.sh" fingerprint \
      --workspace "$staging/fingerprint-work/canonical" \
      --output "$staging/fingerprints.tsv" \
      "$staging/results"
    "$ROOT/relation-detector/scripts/run-release-verification-tool.sh" fingerprint --semantic \
      --workspace "$staging/fingerprint-work/semantic" \
      --output "$staging/semantic-fingerprints.tsv" \
      "$staging/results"
    python3 "$ROOT/relation-detector/scripts/audit/compare-semantic-results.py" \
      --inventory-root "$staging/results" \
      --output "$staging/semantic-inventory.json"
  fi
  write_checkpoint_manifest "$label" "$commit" "$parser_baseline_status" \
    "$acceptance_status" "$staging" "$generated_report_gate"
  if [[ -e "$destination" || -L "$destination" ]]; then
    echo "Checkpoint destination appeared before atomic publication: $destination" >&2
    return 1
  fi
  mv "$staging" "$destination"
  [[ "$parser_baseline_status" -eq 0 ]]
}

run_checkpoint() {
  local label="$1"
  local commit="$2"
  local reuse_root="${3:-}"
  local destination="$OUTPUT_ROOT/$label-$commit"
  local worktree
  local acceptance_status=0
  local parser_baseline_status=0
  local correctness_status=0
  local package_status=0
  local cli_status=0

  if [[ -e "$destination" || -L "$destination" ]]; then
    if [[ "${PHASE0_REUSE_COMPLETED:-true}" == "true" ]] \
        && python3 "$CHECKPOINT_HELPER" verify-reuse "$destination" "$commit"; then
      echo "Reusing completed checkpoint $label ($commit)"
      return
    fi
    echo "Refusing to overwrite an existing checkpoint that failed audited reuse: $destination" >&2
    return 1
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
      SAMPLE_DATA_PARSER_CLI_CASE_PARALLELISM="${SAMPLE_DATA_PARSER_CLI_CASE_PARALLELISM:-1}" \
      SAMPLE_DATA_PARSER_CLI_SCAN_PARALLELISM="${SAMPLE_DATA_PARSER_CLI_SCAN_PARALLELISM:-2}" \
        bash relation-detector/test-fixtures/examples/sample-data-parser-cli/run-all-sample-data-parsers.sh
    ) >>"$log" 2>&1
    cli_status=$?
    set -e
  else
    cli_status=1
  fi
  if [[ "$correctness_status" -ne 0 || "$package_status" -ne 0 || \
        "$cli_status" -ne 0 ]]; then
    parser_baseline_status=1
  fi

  if ! collect_checkpoint "$label" "$commit" "$worktree" "$parser_baseline_status" \
      "$acceptance_status" "SKIPPED_HISTORICAL_STALE"; then
    parser_baseline_status=1
  fi
  if [[ "$parser_baseline_status" -ne 0 ]]; then
    echo "Checkpoint $label ($commit) failed; see $OUTPUT_ROOT/$label-$commit-run.log" >&2
    return "$parser_baseline_status"
  fi
}

run_checkpoint A "$COMMIT_A" "${PHASE0_REUSE_A_ROOT:-}"
run_checkpoint B "$COMMIT_B" "${PHASE0_REUSE_B_ROOT:-}"
run_checkpoint C "$COMMIT_C" "${PHASE0_REUSE_C_ROOT:-}"

python3 "$ROOT/relation-detector/scripts/audit/compare-semantic-results.py" \
  --before "$OUTPUT_ROOT/A-$COMMIT_A/results" \
  --after "$OUTPUT_ROOT/B-$COMMIT_B/results" \
  --transition A_TO_B \
  --output "$OUTPUT_ROOT/a-to-b-semantic-diff.json"
python3 "$ROOT/relation-detector/scripts/audit/compare-semantic-results.py" \
  --before "$OUTPUT_ROOT/B-$COMMIT_B/results" \
  --after "$OUTPUT_ROOT/C-$COMMIT_C/results" \
  --transition B_TO_C \
  --output "$OUTPUT_ROOT/b-to-c-semantic-diff.json"

echo "Phase 0 reconstruction artifacts: $OUTPUT_ROOT"
