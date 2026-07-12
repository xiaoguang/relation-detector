#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
COMPARE="$ROOT/relation-detector/scripts/compare-semantic-results.py"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

[[ -x "$COMPARE" ]]
mkdir -p "$TMP_DIR/a" "$TMP_DIR/b" "$TMP_DIR/c" "$TMP_DIR/strict-a" "$TMP_DIR/strict-b" \
  "$TMP_DIR/evidence-a" "$TMP_DIR/evidence-b"

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

python3 - "$TMP_DIR/strict-a/postgres.json" "$TMP_DIR/strict-b/postgres.json" <<'PY'
import json
import sys

def observation(object_type, line, evidence="ROUTINE_JOIN"):
    return {
        "type": evidence,
        "source": "ROUTINE:public.sync_orders",
        "sourceType": "DATABASE_OBJECT",
        "attributes": {
            "sourceFile": "relation-detector/sample-data/postgres/16/routines.sql",
            "sourceObjectName": "sync_orders",
            "sourceObjectType": object_type,
            "sourceStatementId": "public.sync_orders",
            "sourceBlockId": "public.sync_orders",
            "sourceLine": line,
            "joinKind": "EQUALITY"
        }
    }

def relationship(column, evidence):
    return {
        "source": {"table": "orders", "column": column},
        "target": {"table": "customers", "column": "id"},
        "relationType": "FK_LIKE",
        "relationSubType": "SQL_INFERRED_FK",
        "evidence": [{"type": "ROUTINE_JOIN"}],
        "rawEvidence": evidence
    }

def document(relationships):
    return {
        "relationships": relationships,
        "dataLineages": [],
        "namingEvidence": [],
        "derivedRelationships": [],
        "derivedDataLineages": [],
        "warnings": []
    }

before = document([
    relationship("customer_id", [observation("ROUTINE", 11)]),
    relationship("sales_rep_id", [observation("ROUTINE", 12)]),
    relationship("approver_id", [observation("ROUTINE", 13), observation("FUNCTION", 13)]),
    relationship("reviewer_id", [observation("ROUTINE", 14)])
])
after = document([
    relationship("customer_id", [observation("PROCEDURE", 11)]),
    relationship("sales_rep_id", [observation("PROCEDURE", 12), observation("FUNCTION", 12)]),
    relationship("approver_id", [observation("PROCEDURE", 13)]),
    relationship("reviewer_id", [observation("PROCEDURE", 15)])
])

for path, data in zip(sys.argv[1:], (before, after)):
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(data, handle)
PY

python3 "$COMPARE" --before "$TMP_DIR/strict-a" --after "$TMP_DIR/strict-b" \
  --transition A_TO_B --output "$TMP_DIR/strict-unclassified.json"

# The old classifier accepted a wide provenance label for every add/remove. A strict
# pairing implementation must reject this mapping because only customer_id is 1:1.
jq '{changes: (.changes | map({key: .id, value: {classification: "PROVENANCE_NORMALIZATION_ONLY", rationale: "wide provenance label", sqlContext: "routines.sql"}}) | from_entries)}' \
  "$TMP_DIR/strict-unclassified.json" >"$TMP_DIR/wide-provenance.json"
if python3 "$COMPARE" --before "$TMP_DIR/strict-a" --after "$TMP_DIR/strict-b" \
  --transition A_TO_B --classifications "$TMP_DIR/wide-provenance.json" \
  --output "$TMP_DIR/wide-provenance-result.json" 2>"$TMP_DIR/wide-provenance.stderr"; then
  echo "wide provenance classification unexpectedly accepted" >&2
  exit 1
fi
rg -q 'requires a strict 1:1 observation pair' "$TMP_DIR/wide-provenance.stderr"

jq -e '
  .summary.provenanceNormalizationPairs == 1 and
  .summary.provenanceNormalizationObservationChanges == 2 and
  ([.changes[] | select(.classification == "PROVENANCE_NORMALIZATION_ONLY")] | length) == 2 and
  ([.changes[] | select(.classification == "REVIEW_NEEDED")] | length) == 8
' "$TMP_DIR/strict-unclassified.json" >/dev/null

python3 - "$TMP_DIR/evidence-a/postgres.json" "$TMP_DIR/evidence-b/postgres.json" <<'PY'
import json
import sys

observation = {
    "type": "SQL_LOG_JOIN",
    "source": "query.sql",
    "sourceType": "PLAIN_SQL",
    "attributes": {
        "sourceFile": "relation-detector/sample-data/postgres/16/query.sql",
        "sourceStatementId": "query.sql:1-2",
        "sourceLine": 2,
        "joinKind": "JOIN_ON"
    }
}

def document(evidence_types):
    return {
        "relationships": [{
            "source": {"table": "orders", "column": "customer_id"},
            "target": {"table": "customers", "column": "id"},
            "relationType": "FK_LIKE",
            "relationSubType": "DDL_DECLARED_FK",
            "evidence": [{"type": evidence_type} for evidence_type in evidence_types],
            "rawEvidence": [observation]
        }],
        "dataLineages": [],
        "namingEvidence": [],
        "derivedRelationships": [],
        "derivedDataLineages": [],
        "warnings": []
    }

for path, data in zip(sys.argv[1:], (
        document(["SQL_LOG_JOIN"]),
        document(["SQL_LOG_JOIN", "TARGET_UNIQUE"]))):
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(data, handle)
PY
cp "$TMP_DIR/evidence-a/postgres.json" "$TMP_DIR/evidence-a/postgres-derived-fresh.json"
cp "$TMP_DIR/evidence-b/postgres.json" "$TMP_DIR/evidence-b/postgres-derived-fresh.json"

python3 "$COMPARE" --before "$TMP_DIR/evidence-a" --after "$TMP_DIR/evidence-b" \
  --transition A_TO_B --output "$TMP_DIR/evidence-change.json"
jq -e '
  .summary.factChanges == 4 and
  .summary.observationChanges == 0 and
  .summary.reviewNeeded == 4 and
  ([.changes[].id] | unique | length) == (.changes | length)
' "$TMP_DIR/evidence-change.json" >/dev/null

python3 "$COMPARE" --before "$TMP_DIR/strict-a" --after "$TMP_DIR/strict-b" \
  --transition C_TO_D --output "$TMP_DIR/c-to-d.json"
jq --argjson classifications '[
  "ROUTINE_RELATIONSHIP_OBSERVATION_RECOVERY",
  "ROUTINE_NAMING_OBSERVATION_RECOVERY",
  "NAMING_OBSERVATION_SELECTION_REGRESSION",
  "CONDITIONAL_RELATIONSHIP_RECOVERY",
  "CONDITIONAL_DERIVED_REGRESSION",
  "UNION_LINEAGE_RECOVERY",
  "SQL_ASSET_CORRECTION"
]' '
  {changes: (
    [.changes[] | select(.classification == "REVIEW_NEEDED")]
    | to_entries
    | map({
        key: .value.id,
        value: {
          classification: $classifications[.key % ($classifications | length)],
          rationale: "synthetic C-to-D audit classification",
          sqlContext: "routines.sql"
        }
      })
    | from_entries
  )}
' "$TMP_DIR/c-to-d.json" >"$TMP_DIR/c-to-d-classifications.json"
python3 "$COMPARE" --before "$TMP_DIR/strict-a" --after "$TMP_DIR/strict-b" \
  --transition C_TO_D --classifications "$TMP_DIR/c-to-d-classifications.json" \
  --require-no-review --output "$TMP_DIR/c-to-d-classified.json"
jq -e '
  .transition == "C_TO_D" and
  .summary.reviewNeeded == 0 and
  ([.changes[].classification] | unique | sort) == [
    "CONDITIONAL_DERIVED_REGRESSION",
    "CONDITIONAL_RELATIONSHIP_RECOVERY",
    "NAMING_OBSERVATION_SELECTION_REGRESSION",
    "PROVENANCE_NORMALIZATION_ONLY",
    "ROUTINE_NAMING_OBSERVATION_RECOVERY",
    "ROUTINE_RELATIONSHIP_OBSERVATION_RECOVERY",
    "SQL_ASSET_CORRECTION",
    "UNION_LINEAGE_RECOVERY"
  ]
' "$TMP_DIR/c-to-d-classified.json" >/dev/null

echo "semantic results compare test passed"
