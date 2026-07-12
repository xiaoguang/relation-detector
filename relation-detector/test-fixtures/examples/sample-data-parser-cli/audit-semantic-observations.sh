#!/usr/bin/env bash
set -euo pipefail

RESULT_DIR="${1:?result directory is required}"
REPORT="${2:?report path is required}"
REQUESTED_CASES="${3:-}"
DIFF_DIR="$(dirname "$REPORT")/observation-diffs"
mkdir -p "$DIFF_DIR"

printf 'Pair\tToken\tFull\tTokenOnly\tFullOnly\n' > "$REPORT"
failed=false

requested() {
  local name="$1"
  [[ -z "$REQUESTED_CASES" || ",$REQUESTED_CASES," == *",$name,"* ]]
}

write_fingerprints() {
  local input="$1"
  local output="$2"
  jq -r '
    def endpoint:
      [.catalog, .database, .schema, .table, .column]
      | map(select(. != null and . != ""))
      | join(".");
    def source_identity($e):
      ($e.attributes.sourceFile // "") as $file
      | if $file != "" then $file
        else (($e.attributes.sourceObjectType // "") + ":"
              + ($e.attributes.sourceObjectName // "")) as $object
        | if $object != ":" then $object else ($e.source // "") end
        end;
    (.relationships[] as $fact
      | $fact.rawEvidence[] as $e
      | select(["DDL_FOREIGN_KEY", "SQL_LOG_JOIN", "SQL_LOG_EXISTS",
                "SQL_LOG_SUBQUERY_IN", "VIEW_JOIN"] | index($e.type))
      | ["RELATIONSHIP", ($fact.source | endpoint), ($fact.target | endpoint),
         $fact.relationType, $fact.relationSubType, $e.type, source_identity($e),
         ($e.attributes.sourceStatementId // ""), ($e.attributes.sourceBlockId // ""),
         ($e.attributes.sourceLine // 0), ($e.attributes.joinKind // "")]
      | @tsv),
    (.dataLineages[] as $fact
      | $fact.sources[] as $source
      | $fact.rawEvidence[] as $e
      | select($e.type == "DATA_LINEAGE")
      | ["DATA_LINEAGE", ($source | endpoint), ($fact.target | endpoint),
         $fact.flowKind, $fact.transformType, $e.type, source_identity($e),
         ($e.attributes.sourceStatementId // ""), ($e.attributes.sourceBlockId // ""),
         ($e.attributes.sourceLine // 0), ($e.attributes.mappingKind // "")]
      | @tsv)
  ' "$input" | sort -u > "$output"
}

audit_pair() {
  local label="$1"
  local token_case="$2"
  local full_case="$3"
  local token_json="$RESULT_DIR/$token_case.json"
  local full_json="$RESULT_DIR/$full_case.json"

  if ! requested "$token_case" || ! requested "$full_case"; then
    return
  fi
  if [[ ! -f "$token_json" || ! -f "$full_json" ]]; then
    echo "Missing observation parity input for $label" >&2
    failed=true
    return
  fi

  local token_tsv="$DIFF_DIR/$label-token.tsv"
  local full_tsv="$DIFF_DIR/$label-full.tsv"
  local token_only="$DIFF_DIR/$label-token-only.tsv"
  local full_only="$DIFF_DIR/$label-full-only.tsv"
  write_fingerprints "$token_json" "$token_tsv"
  write_fingerprints "$full_json" "$full_tsv"
  comm -23 "$token_tsv" "$full_tsv" > "$token_only"
  comm -13 "$token_tsv" "$full_tsv" > "$full_only"

  local token_count full_count token_only_count full_only_count
  token_count="$(wc -l < "$token_tsv" | tr -d ' ')"
  full_count="$(wc -l < "$full_tsv" | tr -d ' ')"
  token_only_count="$(wc -l < "$token_only" | tr -d ' ')"
  full_only_count="$(wc -l < "$full_only" | tr -d ' ')"
  printf '%s\t%s\t%s\t%s\t%s\n' \
    "$label" "$token_count" "$full_count" "$token_only_count" "$full_only_count" >> "$REPORT"
  if [[ "$token_only_count" -ne 0 || "$full_only_count" -ne 0 ]]; then
    failed=true
  fi
}

audit_pair mysql mysql-token-event-root mysql-v8_0-full
audit_pair postgres postgres-token-event-root postgres-v18-full
audit_pair oracle oracle-token-event-root oracle-v26ai-full
audit_pair sqlserver sqlserver-token-event-root sqlserver-v2025-full

if [[ "$failed" == true ]]; then
  echo "Semantic observation parity failed; see $REPORT and $DIFF_DIR" >&2
  exit 1
fi

