#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
OUT_DIR="${SAMPLE_DATA_PARSER_CLI_OUT:-$ROOT/target/sample-data-parser-cli}"
CONFIG_DIR="$OUT_DIR/configs"
RESULT_DIR="$OUT_DIR/results"

cd "$ROOT"

mkdir -p "$CONFIG_DIR" "$RESULT_DIR"

if [[ "$#" -gt 0 ]]; then
  REQUESTED_CASES=("$@")
else
  REQUESTED_CASES=()
fi

should_run_case() {
  local name="$1"
  if [[ "${#REQUESTED_CASES[@]}" -eq 0 ]]; then
    return 0
  fi
  local requested
  for requested in "${REQUESTED_CASES[@]}"; do
    if [[ "$requested" == "$name" ]]; then
      return 0
    fi
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
  local schema_dir="$sample_dir/01-schema"
  local procedure_dir="$sample_dir/02-procedures"
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
    printf '    paths:\n'
    printf '      - %s\n' "$procedure_dir"
    printf '    include:\n'
    printf '      - "**/*.sql"\n'
    if [[ -f "$trigger_file" ]]; then
      printf '    files:\n'
      printf '      - %s\n' "$trigger_file"
    fi
    local maybe_object_file
    local object_files_printed=false
    for maybe_object_file in "$sample_dir"/03-data/*.sql "$sample_dir"/04-queries/*.sql; do
      if [[ ! -f "$maybe_object_file" ]]; then
        continue
      fi
      if grep -Eiq "$object_pattern|^[[:space:]]*DELIMITER[[:space:]]+" "$maybe_object_file"; then
        if [[ "$object_files_printed" == "false" && ! -f "$trigger_file" ]]; then
          printf '    files:\n'
        fi
        object_files_printed=true
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
  } > "$config"
}

run_case() {
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
  write_config "$config" "$database_type" "$parser_mode" "$grammar_profile" "$database_version" "$sample_dir"
  echo "==> $name"
  RELATION_DETECTOR_SKIP_PACKAGE=true "$ROOT/scripts/run-cli.sh" scan \
    --config "$config" \
    --format json \
    --output "$output"
}

mvn -q -pl core,adaptor-mysql,adaptor-postgres,adaptor-oracle,adaptor-sqlserver,cli -am -Dmaven.test.skip=true package
"$ROOT/scripts/check-no-jls-bad-classes.sh" "$ROOT"

run_case mysql-token-event-root MYSQL token-event "" "" sample-data/mysql/8.0
run_case mysql-v5_7-full MYSQL full-grammer mysql/5.7 5.7 sample-data/mysql/5.7
run_case mysql-v8_0-full MYSQL full-grammer mysql/8.0 8.0 sample-data/mysql/8.0

run_case postgres-token-event-root POSTGRESQL token-event "" "" sample-data/postgres/18
run_case postgres-v16-full POSTGRESQL full-grammer postgresql/16 16 sample-data/postgres/16
run_case postgres-v17-full POSTGRESQL full-grammer postgresql/17 17 sample-data/postgres/17
run_case postgres-v18-full POSTGRESQL full-grammer postgresql/18 18 sample-data/postgres/18

run_case oracle-token-event-root ORACLE token-event "" "" sample-data/oracle/26ai
run_case oracle-v12c-full ORACLE full-grammer oracle/12c 12c sample-data/oracle/12c
run_case oracle-v19c-full ORACLE full-grammer oracle/19c 19c sample-data/oracle/19c
run_case oracle-v21c-full ORACLE full-grammer oracle/21c 21c sample-data/oracle/21c
run_case oracle-v26ai-full ORACLE full-grammer oracle/26ai 26ai sample-data/oracle/26ai

run_case sqlserver-token-event-root SQLSERVER token-event "" "" sample-data/sqlserver/2025
run_case sqlserver-v2016-full SQLSERVER full-grammer sqlserver/2016 2016 sample-data/sqlserver/2016
run_case sqlserver-v2017-full SQLSERVER full-grammer sqlserver/2017 2017 sample-data/sqlserver/2017
run_case sqlserver-v2019-full SQLSERVER full-grammer sqlserver/2019 2019 sample-data/sqlserver/2019
run_case sqlserver-v2022-full SQLSERVER full-grammer sqlserver/2022 2022 sample-data/sqlserver/2022
run_case sqlserver-v2025-full SQLSERVER full-grammer sqlserver/2025 2025 sample-data/sqlserver/2025

REQUESTED_CASES_CSV="$(IFS=,; echo "${REQUESTED_CASES[*]-}")"
python3 - "$RESULT_DIR" "$CONFIG_DIR" "$OUT_DIR/summary.tsv" "$OUT_DIR/warning-codes.tsv" "$REQUESTED_CASES_CSV" <<'PY'
import json
import sys
from collections import Counter
from pathlib import Path

result_dir = Path(sys.argv[1])
config_dir = Path(sys.argv[2])
summary_path = Path(sys.argv[3])
warnings_path = Path(sys.argv[4])
requested_cases = [item for item in sys.argv[5].split(",") if item]
root = Path.cwd()

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
warning_rows = []
for path in sorted(result_dir.glob("*.json")):
    if path.stem == "common-token-event-sample-data":
        continue
    if requested_cases and path.stem not in requested_cases:
        continue
    data = json.loads(path.read_text(encoding="utf-8"))
    summary = data.get("summary", {})
    warnings = data.get("warnings") or []
    codes = Counter(w.get("code", "UNKNOWN") for w in warnings)
    sources = summary.get("sources") or data.get("sources") or []
    source_text = ",".join(sources) if isinstance(sources, list) else str(sources)
    fixtures, sql_files, ddl_files = file_counts(config_dir / f"{path.stem}.yml")
    summary_rows.append([
        path.stem,
        str(fixtures),
        f"{sql_files} / {ddl_files}",
        str(summary.get("relationshipCount", len(data.get("relationships") or []))),
        str(summary.get("dataLineageCount", len(data.get("dataLineages") or []))),
        str(summary.get("namingEvidenceCount", len(data.get("namingEvidence") or []))),
        str(summary.get("warningCount", len(warnings))),
        source_text,
        str(path),
    ])
    if not codes:
        warning_rows.append([path.stem, "NONE", "0"])
    else:
        for code, count in sorted(codes.items()):
            warning_rows.append([path.stem, code, str(count)])

def should_include_common_row():
    return not requested_cases or "common-token-event-sample-data" in requested_cases

def json_count(path):
    if not path.exists():
        return 0
    data = json.loads(path.read_text(encoding="utf-8"))
    return len(data.get("fingerprints") or [])

def fingerprint_items(path, key, fixture):
    if not path.exists():
        return []
    data = json.loads(path.read_text(encoding="utf-8"))
    items = []
    for fingerprint in data.get("fingerprints") or []:
        item = {
            "fingerprint": fingerprint,
            "fixture": fixture.name,
            "source": str(fixture),
        }
        if key == "namingEvidence" and fingerprint.endswith(":NAMING_MATCH"):
            item["id"] = fingerprint.rsplit(":", 1)[0]
        items.append(item)
    return items

def manifest_target(path):
    text = (path / "manifest.yml").read_text(encoding="utf-8")
    return "DDL" if "parserTarget: DDL" in text else "SQL"

if should_include_common_row():
    common_root = root / "test-fixtures" / "correctness" / "common"
    common_fixtures = sorted(
        item for item in common_root.iterdir()
        if item.is_dir() and "sample-data" in item.name
    )
    sql_count = sum(1 for fixture in common_fixtures if manifest_target(fixture) == "SQL")
    ddl_count = sum(1 for fixture in common_fixtures if manifest_target(fixture) == "DDL")
    relation_count = sum(json_count(fixture / "expected-relations.json") for fixture in common_fixtures)
    lineage_count = sum(json_count(fixture / "expected-lineage.json") for fixture in common_fixtures)
    naming_count = sum(json_count(fixture / "expected-naming-evidence.json") for fixture in common_fixtures)
    diagnostic_count = sum(json_count(fixture / "expected-diagnostics.json") for fixture in common_fixtures)
    common_relationships = []
    common_lineages = []
    common_naming = []
    common_warnings = []
    for fixture in common_fixtures:
        common_relationships.extend(fingerprint_items(fixture / "expected-relations.json", "relationships", fixture))
        common_lineages.extend(fingerprint_items(fixture / "expected-lineage.json", "dataLineages", fixture))
        common_naming.extend(fingerprint_items(fixture / "expected-naming-evidence.json", "namingEvidence", fixture))
        common_warnings.extend(fingerprint_items(fixture / "expected-diagnostics.json", "warnings", fixture))
    common_output = result_dir / "common-token-event-sample-data.json"
    common_output.write_text(json.dumps({
        "database": {
            "type": "COMMON",
            "parserMode": "token-event",
            "structuredParser": "common-token-event",
        },
        "generatedBy": "sample-data-parser-cli common benchmark aggregator",
        "summary": {
            "relationshipCount": relation_count,
            "dataLineageCount": lineage_count,
            "namingEvidenceCount": naming_count,
            "warningCount": diagnostic_count,
            "sources": ["correctness-common-portable-benchmark"],
        },
        "relationships": common_relationships,
        "dataLineages": common_lineages,
        "namingEvidence": common_naming,
        "warnings": common_warnings,
    }, ensure_ascii=False, indent=2, sort_keys=False) + "\n", encoding="utf-8")
    summary_rows.insert(0, [
        "common-token-event-sample-data",
        str(len(common_fixtures)),
        f"{sql_count} / {ddl_count}",
        str(relation_count),
        str(lineage_count),
        str(naming_count),
        str(diagnostic_count),
        "correctness-common-portable-benchmark",
        str(common_output),
    ])
    warning_rows.insert(0, ["common-token-event-sample-data", "NONE" if diagnostic_count == 0 else "EXPECTED_DIAGNOSTICS",
                            str(diagnostic_count)])

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
PY

echo
echo "Summary: $OUT_DIR/summary.tsv"
echo "Warnings: $OUT_DIR/warning-codes.tsv"
echo "Results: $RESULT_DIR"
