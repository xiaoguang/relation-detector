#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
RELATION_ROOT="$ROOT/relation-detector"

if [[ "${SAMPLE_DATA_PARSER_CLI_GROUP_WORKER:-false}" != "true" ]]; then
  exec "$RELATION_ROOT/scripts/run-sample-data-isolated.sh" "$@"
fi

OUT_DIR="${SAMPLE_DATA_PARSER_CLI_OUT:-$RELATION_ROOT/target/sample-data-parser-cli}"
CONFIG_DIR="$OUT_DIR/configs"
RESULT_DIR="$OUT_DIR/results"
INCLUDE_DERIVED="${SAMPLE_DATA_PARSER_CLI_INCLUDE_DERIVED:-true}"
CASE_PARALLELISM="${SAMPLE_DATA_PARSER_CLI_CASE_PARALLELISM:-1}"
SCAN_PARALLELISM="${SAMPLE_DATA_PARSER_CLI_SCAN_PARALLELISM:-2}"
MAX_WORKER_THREADS="${SAMPLE_DATA_PARSER_CLI_MAX_WORKER_THREADS:-8}"
LOG_DIR="$OUT_DIR/logs"
BATCH_MANIFEST="${SAMPLE_DATA_PARSER_CLI_BATCH_MANIFEST:-$OUT_DIR/batch.yml}"
BATCH_REPORT="${SAMPLE_DATA_PARSER_CLI_BATCH_REPORT:-$OUT_DIR/batch-report.json}"
BATCH_LOG="${SAMPLE_DATA_PARSER_CLI_BATCH_LOG:-$LOG_DIR/batch.log}"
RUN_LOCK_DIR="${SAMPLE_DATA_PARSER_CLI_LOCK_DIR:-$RELATION_ROOT/target/.sample-data-parser-cli.lock}"
RUN_LOCK_HELD=false

release_run_lock() {
  if [[ "$RUN_LOCK_HELD" != "true" ]]; then
    return
  fi
  rm -f "$RUN_LOCK_DIR/pid"
  rmdir "$RUN_LOCK_DIR" 2>/dev/null || true
  RUN_LOCK_HELD=false
}

acquire_run_lock() {
  mkdir -p "$(dirname "$RUN_LOCK_DIR")"
  if ! mkdir "$RUN_LOCK_DIR" 2>/dev/null; then
    local owner="unknown"
    if [[ -r "$RUN_LOCK_DIR/pid" ]]; then
      owner="$(cat "$RUN_LOCK_DIR/pid")"
    fi
    echo "sample-data parser CLI is already running or requires cleanup (pid=$owner, lock=$RUN_LOCK_DIR)" >&2
    exit 73
  fi
  printf '%s\n' "$$" >"$RUN_LOCK_DIR/pid"
  RUN_LOCK_HELD=true
}

trap release_run_lock EXIT
acquire_run_lock

if ! [[ "$CASE_PARALLELISM" =~ ^[1-9][0-9]*$ ]]; then
  echo "SAMPLE_DATA_PARSER_CLI_CASE_PARALLELISM must be a positive integer" >&2
  exit 2
fi
if ! [[ "$SCAN_PARALLELISM" =~ ^[1-9][0-9]*$ ]]; then
  echo "SAMPLE_DATA_PARSER_CLI_SCAN_PARALLELISM must be a positive integer" >&2
  exit 2
fi
if ! [[ "$MAX_WORKER_THREADS" =~ ^[1-9][0-9]*$ ]]; then
  echo "SAMPLE_DATA_PARSER_CLI_MAX_WORKER_THREADS must be a positive integer" >&2
  exit 2
fi
if [[ "$SCAN_PARALLELISM" -gt "$MAX_WORKER_THREADS" ]]; then
  echo "scan parallelism cannot exceed the batch worker budget" >&2
  exit 2
fi

cd "$ROOT"

mkdir -p "$CONFIG_DIR" "$RESULT_DIR" "$LOG_DIR"

if [[ "$#" -gt 0 ]]; then
  REQUESTED_CASES=("$@")
  REQUESTED_CASE_COUNT="$#"
elif [[ -n "${SAMPLE_DATA_PARSER_CLI_CASES:-}" ]]; then
  IFS=',' read -r -a REQUESTED_CASES <<<"$SAMPLE_DATA_PARSER_CLI_CASES"
  REQUESTED_CASE_COUNT="${#REQUESTED_CASES[@]}"
else
  REQUESTED_CASES=()
  REQUESTED_CASE_COUNT=0
fi

KNOWN_CASES=(
  common-token-event-sample-data
  mysql-token-event-root mysql-v5_7-full mysql-v8_0-full
  postgres-token-event-root postgres-v16-full postgres-v17-full postgres-v18-full
  oracle-token-event-root oracle-v12c-full oracle-v19c-full oracle-v21c-full oracle-v26ai-full
  sqlserver-token-event-root sqlserver-v2016-full sqlserver-v2017-full sqlserver-v2019-full
  sqlserver-v2022-full sqlserver-v2025-full
)

is_known_case() {
  local requested="$1"
  local known
  for known in "${KNOWN_CASES[@]}"; do
    [[ "$known" == "$requested" ]] && return 0
  done
  return 1
}

requested_index=0
while [[ "$requested_index" -lt "$REQUESTED_CASE_COUNT" ]]; do
  requested="${REQUESTED_CASES[$requested_index]}"
  if ! is_known_case "$requested"; then
    echo "Unknown sample-data parser case: $requested" >&2
    exit 2
  fi
  requested_index=$((requested_index + 1))
done

should_run_case() {
  local name="$1"
  if [[ "$REQUESTED_CASE_COUNT" -eq 0 ]]; then
    return 0
  fi
  local requested index=0
  while [[ "$index" -lt "$REQUESTED_CASE_COUNT" ]]; do
    requested="${REQUESTED_CASES[$index]}"
    if [[ "$requested" == "$name" ]]; then
      return 0
    fi
    index=$((index + 1))
  done
  return 1
}

write_config() {
  local config="$1"
  local database_type="$2"
  local parser_mode="$3"
  local grammar_profile="$4"
  local database_version="$5"
  local sample_dir="$6"
  local derived_enabled="${7:-false}"
  local schema_dir="$sample_dir/01-schema"
  local procedure_dir="$sample_dir/02-procedures"
  if [[ ! -d "$procedure_dir" && -d "$sample_dir/02-processes" ]]; then
    procedure_dir="$sample_dir/02-processes"
  fi
  local use_procedure_file_list=false
  if [[ "$database_type" == "COMMON" && "$(basename "$procedure_dir")" == "02-processes" ]]; then
    use_procedure_file_list=true
  fi
  local trigger_file="$schema_dir/03-triggers.sql"
  local object_pattern='CREATE[[:space:]]+(OR[[:space:]]+(REPLACE|ALTER)[[:space:]]+)?(PROCEDURE|FUNCTION|TRIGGER|PACKAGE)'

  {
    printf 'database:\n'
    printf '  type: %s\n' "$database_type"
    printf '  schema: sample_data\n'
    printf '\n'
    printf 'parser:\n'
    printf '  mode: %s\n' "$parser_mode"
    if [[ -n "$grammar_profile" ]]; then
      printf '  grammarProfile: %s\n' "$grammar_profile"
    fi
    if [[ -n "$database_version" ]]; then
      printf '  databaseVersion: "%s"\n' "$database_version"
    fi
    printf '\n'
    printf 'execution:\n'
    printf '  parallelism: %s\n' "$SCAN_PARALLELISM"
    printf '\n'
    printf 'sources:\n'
    printf '  metadata:\n'
    printf '    enabled: false\n'
    printf '  ddl:\n'
    printf '    enabled: true\n'
    printf '    fromDatabase: false\n'
    printf '    files:\n'
    local schema_file
    for schema_file in "$schema_dir"/*.sql; do
      if [[ "$(basename "$schema_file")" == "03-triggers.sql" ]]; then
        continue
      fi
      printf '      - %s\n' "$schema_file"
    done
    printf '  objects:\n'
    printf '    enabled: true\n'
    printf '    fromDatabase: false\n'
    local object_files_printed=false
    if [[ "$use_procedure_file_list" == "true" ]]; then
      printf '    files:\n'
      object_files_printed=true
      local procedure_file
      for procedure_file in "$procedure_dir"/*.sql; do
        if [[ ! -f "$procedure_file" ]]; then
          continue
        fi
        if [[ "$(basename "$procedure_file")" == *-for-golden.sql ]]; then
          continue
        fi
        printf '      - %s\n' "$procedure_file"
      done
    else
      printf '    paths:\n'
      printf '      - %s\n' "$procedure_dir"
      printf '    include:\n'
      printf '      - "**/*.sql"\n'
    fi
    if [[ -f "$trigger_file" ]]; then
      if [[ "$object_files_printed" == "false" ]]; then
        printf '    files:\n'
        object_files_printed=true
      fi
      printf '      - %s\n' "$trigger_file"
    fi
    local maybe_object_file
    for maybe_object_file in "$sample_dir"/03-data/*.sql "$sample_dir"/04-queries/*.sql; do
      if [[ ! -f "$maybe_object_file" ]]; then
        continue
      fi
      if grep -Eiq "$object_pattern|^[[:space:]]*DELIMITER[[:space:]]+" "$maybe_object_file"; then
        if [[ "$object_files_printed" == "false" ]]; then
          printf '    files:\n'
          object_files_printed=true
        fi
        printf '      - %s\n' "$maybe_object_file"
      fi
    done
    printf '  logs:\n'
    printf '    enabled: true\n'
    printf '    format: PLAIN_SQL\n'
    printf '    filterSystemQueries: true\n'
    printf '    files:\n'
    local log_file
    for log_file in "$sample_dir"/03-data/*.sql "$sample_dir"/04-queries/*.sql; do
      if [[ ! -f "$log_file" ]]; then
        continue
      fi
      if grep -Eiq "$object_pattern|^[[:space:]]*DELIMITER[[:space:]]+" "$log_file"; then
        continue
      fi
      printf '      - %s\n' "$log_file"
    done
    printf '  dataProfile:\n'
    printf '    enabled: false\n'
    printf '\n'
    printf 'output:\n'
    printf '  format: json\n'
    printf '  minConfidence: 0.0\n'
    printf '  includeEvidence: true\n'
    printf '  includeWarnings: true\n'
    printf '  includeObservationCounts: true\n'
    if [[ "$derived_enabled" == "true" ]]; then
      printf '\n'
      printf 'derivedPaths:\n'
      printf '  enabled: true\n'
      printf '  relationships: true\n'
      printf '  dataLineage: true\n'
      printf '  namingEvidence: true\n'
      printf '  includeNamingEdgesInRelationshipPaths: true\n'
      printf '  maxPathLength: 5\n'
      printf '  maxPathsPerPair: 0\n'
      printf '  maxFacts: 0\n'
      printf '  confidenceDecay: 0.75\n'
      printf '  minConfidence: 0.10\n'
    fi
  } > "$config"
}

BATCH_CASES=()

prepare_case() {
  local name="$1"
  local database_type="$2"
  local parser_mode="$3"
  local grammar_profile="$4"
  local database_version="$5"
  local sample_dir="$6"

  if ! should_run_case "$name"; then
    return 0
  fi

  local config="$CONFIG_DIR/$name.yml"
  local output="$RESULT_DIR/$name.json"
  if [[ "$INCLUDE_DERIVED" == "true" ]]; then
    local derived_name="$name-derived-fresh"
    local derived_config="$CONFIG_DIR/$derived_name.yml"
    local derived_output="$RESULT_DIR/$derived_name.json"
    write_config "$config" "$database_type" "$parser_mode" "$grammar_profile" "$database_version" "$sample_dir" false
    write_config "$derived_config" "$database_type" "$parser_mode" "$grammar_profile" "$database_version" "$sample_dir" true
    BATCH_CASES+=("$name|$derived_config|$derived_output|$output")
  else
    write_config "$config" "$database_type" "$parser_mode" "$grammar_profile" "$database_version" "$sample_dir" false
    BATCH_CASES+=("$name|$config|$output|")
  fi
}

queue_case() {
  local name="$1"
  shift
  if ! should_run_case "$name"; then
    return 0
  fi
  prepare_case "$name" "$@"
}

write_batch_manifest() {
  {
    printf 'version: 1\n'
    printf 'execution:\n'
    printf '  caseParallelism: %s\n' "$CASE_PARALLELISM"
    printf '  maxWorkerThreads: %s\n' "$MAX_WORKER_THREADS"
    printf '  failurePolicy: continue\n'
    printf 'report: %s\n' "$BATCH_REPORT"
    printf 'cases:\n'
    local item name config output direct_output
    for item in "${BATCH_CASES[@]}"; do
      IFS='|' read -r name config output direct_output <<<"$item"
      printf '  - id: %s\n' "$name"
      printf '    config: %s\n' "$config"
      printf '    output: %s\n' "$output"
      if [[ -n "$direct_output" ]]; then
        printf '    directOutput: %s\n' "$direct_output"
      fi
    done
  } >"$BATCH_MANIFEST"
}

if [[ "${SAMPLE_DATA_PARSER_CLI_SKIP_PACKAGE:-false}" != "true" ]]; then
  mvn -q -pl relation-detector/core,relation-detector/adaptor-mysql,relation-detector/adaptor-postgres,relation-detector/adaptor-oracle,relation-detector/adaptor-sqlserver,relation-detector/cli -am -Dmaven.test.skip=true package
fi
if [[ "${SAMPLE_DATA_PARSER_CLI_SKIP_JLS_CHECK:-false}" != "true" ]]; then
  "$RELATION_ROOT/scripts/check-no-jls-bad-classes.sh" "$ROOT"
fi

queue_case common-token-event-sample-data COMMON token-event "" "" "$RELATION_ROOT/sample-data/common-natural"

queue_case mysql-token-event-root MYSQL token-event "" "" "$RELATION_ROOT/sample-data/mysql/8.0"
queue_case mysql-v5_7-full MYSQL full-grammar mysql/5.7 5.7 "$RELATION_ROOT/sample-data/mysql/5.7"
queue_case mysql-v8_0-full MYSQL full-grammar mysql/8.0 8.0 "$RELATION_ROOT/sample-data/mysql/8.0"

queue_case postgres-token-event-root POSTGRESQL token-event "" "" "$RELATION_ROOT/sample-data/postgres/18"
queue_case postgres-v16-full POSTGRESQL full-grammar postgresql/16 16 "$RELATION_ROOT/sample-data/postgres/16"
queue_case postgres-v17-full POSTGRESQL full-grammar postgresql/17 17 "$RELATION_ROOT/sample-data/postgres/17"
queue_case postgres-v18-full POSTGRESQL full-grammar postgresql/18 18 "$RELATION_ROOT/sample-data/postgres/18"

queue_case oracle-token-event-root ORACLE token-event "" "" "$RELATION_ROOT/sample-data/oracle/26ai"
queue_case oracle-v12c-full ORACLE full-grammar oracle/12c 12c "$RELATION_ROOT/sample-data/oracle/12c"
queue_case oracle-v19c-full ORACLE full-grammar oracle/19c 19c "$RELATION_ROOT/sample-data/oracle/19c"
queue_case oracle-v21c-full ORACLE full-grammar oracle/21c 21c "$RELATION_ROOT/sample-data/oracle/21c"
queue_case oracle-v26ai-full ORACLE full-grammar oracle/26ai 26ai "$RELATION_ROOT/sample-data/oracle/26ai"

queue_case sqlserver-token-event-root SQLSERVER token-event "" "" "$RELATION_ROOT/sample-data/sqlserver/2025"
queue_case sqlserver-v2016-full SQLSERVER full-grammar sqlserver/2016 2016 "$RELATION_ROOT/sample-data/sqlserver/2016"
queue_case sqlserver-v2017-full SQLSERVER full-grammar sqlserver/2017 2017 "$RELATION_ROOT/sample-data/sqlserver/2017"
queue_case sqlserver-v2019-full SQLSERVER full-grammar sqlserver/2019 2019 "$RELATION_ROOT/sample-data/sqlserver/2019"
queue_case sqlserver-v2022-full SQLSERVER full-grammar sqlserver/2022 2022 "$RELATION_ROOT/sample-data/sqlserver/2022"
queue_case sqlserver-v2025-full SQLSERVER full-grammar sqlserver/2025 2025 "$RELATION_ROOT/sample-data/sqlserver/2025"

write_batch_manifest
if [[ "${SAMPLE_DATA_PARSER_CLI_SKIP_BATCH:-false}" != "true" ]]; then
  if ! RELATION_DETECTOR_SKIP_PACKAGE=true "$RELATION_ROOT/scripts/run-cli.sh" batch \
    --manifest "$BATCH_MANIFEST" >"$BATCH_LOG" 2>&1; then
    cat "$BATCH_LOG" >&2
    exit 1
  fi
fi

REQUESTED_CASES_CSV=""
if [[ "$REQUESTED_CASE_COUNT" -gt 0 ]]; then
  REQUESTED_CASES_CSV="$(IFS=,; echo "${REQUESTED_CASES[*]}")"
fi
python3 - "$RESULT_DIR" "$CONFIG_DIR" "$OUT_DIR/summary.tsv" "$OUT_DIR/summary-with-derived.tsv" "$OUT_DIR/warning-codes.tsv" "$REQUESTED_CASES_CSV" <<'PY'
import json
import sys
from collections import Counter
from pathlib import Path

result_dir = Path(sys.argv[1])
config_dir = Path(sys.argv[2])
summary_path = Path(sys.argv[3])
derived_summary_path = Path(sys.argv[4])
warnings_path = Path(sys.argv[5])
requested_cases = [item for item in sys.argv[6].split(",") if item]

def list_items(lines, section_name):
    values = []
    in_section = False
    section_indent = None
    for line in lines:
        stripped = line.strip()
        if stripped == f"{section_name}:":
            in_section = True
            section_indent = len(line) - len(line.lstrip())
            continue
        if not in_section:
            continue
        indent = len(line) - len(line.lstrip())
        if stripped.endswith(":") and indent <= section_indent:
            break
        if stripped.startswith("- "):
            values.append(stripped[2:].strip().strip('"'))
    return values

def file_counts(config_path):
    lines = config_path.read_text(encoding="utf-8").splitlines()
    ddl_files = list_items(lines, "files")
    # The generated file has three files sections in order: ddl, optional objects, logs.
    sections = []
    current = []
    in_files = False
    files_indent = None
    for line in lines:
        stripped = line.strip()
        indent = len(line) - len(line.lstrip())
        if stripped == "files:":
            if in_files:
                sections.append(current)
            current = []
            in_files = True
            files_indent = indent
            continue
        if in_files and stripped.endswith(":") and indent <= files_indent:
            sections.append(current)
            current = []
            in_files = False
        if in_files and stripped.startswith("- "):
            current.append(stripped[2:].strip().strip('"'))
    if in_files:
        sections.append(current)
    ddl = len(sections[0]) if sections else 0
    object_explicit = len(sections[1]) if len(sections) > 1 else 0
    logs = len(sections[2]) if len(sections) > 2 else 0
    object_paths = []
    lines_text = "\n".join(lines)
    marker = "  objects:\n"
    if marker in lines_text:
        object_block = lines_text.split(marker, 1)[1].split("\n  logs:\n", 1)[0]
        block_lines = object_block.splitlines()
        for i, line in enumerate(block_lines):
            if line.strip() == "paths:":
                for item in block_lines[i + 1:]:
                    if item.strip().startswith("- "):
                        object_paths.append(item.strip()[2:])
                    elif item.startswith("    ") or not item.strip():
                        continue
                    else:
                        break
    object_path_files = 0
    for root in object_paths:
        object_path_files += len(list(Path(root).glob("**/*.sql")))
    sql = object_explicit + object_path_files + logs
    return ddl + sql, sql, ddl

summary_rows = []
derived_rows = []
warning_rows = []
for path in sorted(result_dir.glob("*.json")):
    if path.stem.endswith("-derived") or path.stem.endswith("-derived-fresh") or path.stem.endswith("-refresh"):
        continue
    if requested_cases and path.stem not in requested_cases:
        continue
    config_path = config_dir / f"{path.stem}.yml"
    if not config_path.exists():
        continue
    data = json.loads(path.read_text(encoding="utf-8"))
    summary = data.get("summary", {})
    warnings = data.get("warnings") or []
    codes = Counter(w.get("code", "UNKNOWN") for w in warnings)
    sources = summary.get("sources") or data.get("sources") or []
    source_text = ",".join(sources) if isinstance(sources, list) else str(sources)
    fixtures, sql_files, ddl_files = file_counts(config_path)
    summary_rows.append([
        path.stem,
        str(fixtures),
        f"{sql_files} / {ddl_files}",
        str(summary.get("directRelationshipCount",
                        summary.get("relationshipCount", len(data.get("relationships") or [])))),
        str(summary.get("directDataLineageCount",
                        summary.get("dataLineageCount", len(data.get("dataLineages") or [])))),
            str(summary.get("directNamingEvidenceCount",
                        summary.get("namingEvidenceCount", len(data.get("namingEvidence") or [])))),
        str(summary.get("warningCount", len(warnings))),
        source_text,
        str(path),
    ])
    if not codes:
        warning_rows.append([path.stem, "NONE", "0"])
    else:
        for code, count in sorted(codes.items()):
            warning_rows.append([path.stem, code, str(count)])
    derived_path = result_dir / f"{path.stem}-derived-fresh.json"
    if derived_path.exists():
        derived_data = json.loads(derived_path.read_text(encoding="utf-8"))
        derived_summary = derived_data.get("summary", {})
        derived_name_count = derived_summary.get("derivedNamingEvidenceCount")
        if derived_name_count is None:
            derived_name_count = sum(
                1 for item in derived_data.get("namingEvidence") or []
                if item.get("rule") == "TRANSITIVE_NAMING_PATH"
            )
        derived_rows.append([
            path.stem,
            str(fixtures),
            f"{sql_files} / {ddl_files}",
            str(derived_summary.get("directRelationshipCount",
                                    derived_summary.get("relationshipCount",
                                                        len(derived_data.get("relationships") or [])))),
            str(derived_summary.get("directDataLineageCount",
                                    derived_summary.get("dataLineageCount",
                                                        len(derived_data.get("dataLineages") or [])))),
            str(derived_summary.get("directNamingEvidenceCount",
                                    derived_summary.get("namingEvidenceCount",
                                                        len(derived_data.get("namingEvidence") or [])))),
            str(derived_summary.get("warningCount", len(derived_data.get("warnings") or []))),
            str(derived_summary.get("derivedRelationshipCount", len(derived_data.get("derivedRelationships") or []))),
            str(derived_summary.get("derivedDataLineageCount", len(derived_data.get("derivedDataLineages") or []))),
            str(derived_name_count),
        ])

summary_path.write_text(
    "parser\tfixtures\tSQL / DDL\trelations\tlineage\tnamingEvidence\twarnings\tsources\tjson\n"
    + "\n".join("\t".join(row) for row in summary_rows)
    + "\n",
    encoding="utf-8",
)
warnings_path.write_text(
    "parser\twarningCode\tcount\n"
    + "\n".join("\t".join(row) for row in warning_rows)
    + "\n",
    encoding="utf-8",
)
derived_summary_path.write_text(
    "Parser\tFix\tSQL/DDL\tRel\tLin\tName\tDiag\tDerRel\tDerLin\tDerName\n"
    + "\n".join("\t".join(row) for row in derived_rows)
    + "\n",
    encoding="utf-8",
)
PY

bash "$RELATION_ROOT/test-fixtures/examples/sample-data-parser-cli/audit-semantic-observations.sh" \
  "$RESULT_DIR" "$OUT_DIR/observation-parity.tsv" "$REQUESTED_CASES_CSV"

echo
echo "Summary: $OUT_DIR/summary.tsv"
echo "Summary with derived: $OUT_DIR/summary-with-derived.tsv"
echo "Warnings: $OUT_DIR/warning-codes.tsv"
echo "Observation parity: $OUT_DIR/observation-parity.tsv"
echo "Results: $RESULT_DIR"
