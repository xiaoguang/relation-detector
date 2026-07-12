#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
COMPARE="$ROOT/relation-detector/scripts/compare-semantic-results.py"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

[[ -x "$COMPARE" ]]
mkdir -p "$TMP_DIR/a" "$TMP_DIR/b" "$TMP_DIR/c"

cat >"$TMP_DIR/a/mysql.json" <<'JSON'
{
  "relationships": [{
    "source": {"table":"orders","column":"customer_id"},
    "target": {"table":"customers","column":"id"},
    "relationType":"FK_LIKE",
    "relationSubType":"SQL_INFERRED_FK",
    "evidence":[{"type":"SQL_LOG_JOIN"}],
    "rawEvidence":[{"type":"SQL_LOG_JOIN","sourceType":"SQL_LOG","source":"/tmp/checkout/relation-detector/sample-data/query.sql","attributes":{"sourceFile":"/tmp/checkout/relation-detector/sample-data/query.sql","sourceStatementId":"query.sql:2-2","sourceLine":2,"tokenEventNative":true}}]
  }],
  "dataLineages": [{
    "sources":[{"table":"orders","column":"amount"}],
    "target":{"table":"sales_fact","column":"amount"},
    "flowKind":"VALUE",
    "transformType":"DIRECT",
    "rawEvidence":[{"type":"DATA_LINEAGE","source":"query.sql","attributes":{"sourceFile":"relation-detector/sample-data/query.sql","sourceStatementId":"query.sql:3-3","sourceLine":3,"tokenEventNative":true}}]
  }],
  "namingEvidence": [],
  "derivedRelationships": [],
  "derivedDataLineages": [],
  "warnings": []
}
JSON

cat >"$TMP_DIR/b/mysql.json" <<'JSON'
{
  "relationships": [{
    "source": {"table":"orders","column":"customer_id"},
    "target": {"table":"customers","column":"id"},
    "relationType":"FK_LIKE",
    "relationSubType":"SQL_INFERRED_FK",
    "evidence":[{"type":"SQL_LOG_JOIN"}],
    "rawEvidence":[{"type":"SQL_LOG_JOIN","sourceType":"SQL_LOG","source":"relation-detector/sample-data/query.sql","attributes":{"sourceFile":"relation-detector/sample-data/query.sql","sourceStatementId":"query.sql:2-2","sourceLine":2,"fullGrammarNative":true}}]
  }],
  "dataLineages": [{
    "sources":[{"table":"orders","column":"amount"}],
    "target":{"table":"sales_fact","column":"amount"},
    "flowKind":"VALUE",
    "transformType":"DIRECT",
    "rawEvidence":[{"type":"DATA_LINEAGE","source":"query.sql","attributes":{"sourceFile":"relation-detector/sample-data/query.sql","sourceStatementId":"query.sql:3-3","sourceLine":3,"fullGrammarNative":true}}]
  }],
  "namingEvidence": [],
  "derivedRelationships": [],
  "derivedDataLineages": [],
  "warnings": []
}
JSON

python3 - "$TMP_DIR/a/mysql.json" "$TMP_DIR/b/mysql.json" <<'PY'
import json, sys
legacy = "GRAM" + "MER"
for path, marker in ((sys.argv[1], "FULL_" + legacy), (sys.argv[2], "FULL_GRAMMAR")):
    data = json.load(open(path, encoding="utf-8"))
    data["relationships"][0]["rawEvidence"][0]["attributes"]["semanticMarker"] = marker
    context_key = "full" + ("Gram" + "mer" if path == sys.argv[1] else "Grammar") + "ContextSource"
    data["relationships"][0]["rawEvidence"][0]["attributes"][context_key] = "typed-context"
    json.dump(data, open(path, "w", encoding="utf-8"))
PY

python3 "$COMPARE" --before "$TMP_DIR/a" --after "$TMP_DIR/b" \
  --transition A_TO_B --output "$TMP_DIR/equal.json"
jq -e '.summary.factChanges == 0 and .summary.observationChanges == 0 and .summary.reviewNeeded == 0' \
  "$TMP_DIR/equal.json" >/dev/null

python3 "$COMPARE" --inventory-root "$TMP_DIR/a" --output "$TMP_DIR/inventory.json"
jq -e '
  .summary.factCount == 2 and
  .summary.observationCount == 2 and
  (.factFingerprint | length) == 64 and
  (.observationFingerprint | length) == 64
' "$TMP_DIR/inventory.json" >/dev/null

python3 - "$TMP_DIR/b/mysql.json" "$TMP_DIR/c/mysql.json" <<'PY'
import json, sys
data = json.load(open(sys.argv[1], encoding="utf-8"))
data["dataLineages"][0]["flowKind"] = "CONTROL"
data["relationships"][0]["rawEvidence"][0]["attributes"]["sourceLine"] = 4
json.dump(data, open(sys.argv[2], "w", encoding="utf-8"))
PY

python3 "$COMPARE" --before "$TMP_DIR/b" --after "$TMP_DIR/c" \
  --transition B_TO_C --output "$TMP_DIR/changed.json"
jq -e '.summary.factChanges == 2 and .summary.observationChanges == 4 and .summary.reviewNeeded == 6' \
  "$TMP_DIR/changed.json" >/dev/null
jq -e 'all(.changes[]; .classification == "REVIEW_NEEDED" and (.id | length) == 64)' \
  "$TMP_DIR/changed.json" >/dev/null
jq -e 'all(.changes[] | select(.scope == "FACT"); (.value.sqlContexts | length) > 0)' \
  "$TMP_DIR/changed.json" >/dev/null

jq '{changes: (.changes | map({key: .id, value: {classification: (if .scope == "FACT" then "SQL_ASSET_CORRECTION" else "PROVENANCE_CORRECTION" end), rationale: "audited test change", sqlContext: "query.sql"}}) | from_entries)}' \
  "$TMP_DIR/changed.json" >"$TMP_DIR/classifications.json"

python3 "$COMPARE" --before "$TMP_DIR/b" --after "$TMP_DIR/c" \
  --transition B_TO_C --classifications "$TMP_DIR/classifications.json" \
  --require-no-review --output "$TMP_DIR/classified.json"
jq -e '.summary.reviewNeeded == 0 and .summary.classifiedChanges == 6' \
  "$TMP_DIR/classified.json" >/dev/null

echo "semantic results compare test passed"
