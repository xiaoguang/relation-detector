#!/usr/bin/env bash
set -euo pipefail

RELATION_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERIFICATION_RUNNER="$RELATION_ROOT/scripts/run-release-verification-tool.sh"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

cat >"$TMP_DIR/one.json" <<'JSON'
{"generatedAt":"first","summary":{"count":1},"facts":[{"id":"a"}]}
JSON
cat >"$TMP_DIR/two.json" <<'JSON'
{"facts":[{"id":"a"}],"summary":{"count":1},"generatedAt":"second"}
JSON

"$VERIFICATION_RUNNER" fingerprint \
  --workspace "$TMP_DIR/fingerprint-one" \
  --output "$TMP_DIR/one.tsv" \
  "$TMP_DIR/one.json"
"$VERIFICATION_RUNNER" fingerprint \
  --workspace "$TMP_DIR/fingerprint-two" \
  --output "$TMP_DIR/two.tsv" \
  "$TMP_DIR/two.json"
first_hash="$(cut -f1 "$TMP_DIR/one.tsv")"
second_hash="$(cut -f1 "$TMP_DIR/two.tsv")"
[[ "$first_hash" == "$second_hash" ]]

cat >"$TMP_DIR/token.json" <<'JSON'
{"relationships":[{"source":"orders.customer_id","target":"customers.id","evidence":[{"type":"SQL_LOG_JOIN","attributes":{"tokenEventNative":true,"sourceFile":"query.sql","sourceStatementId":"query.sql:2-2","sourceLine":2}}]}],"dataLineages":[{"source":"orders.amount","target":"sales_fact.amount","flowKind":"VALUE","transformType":"DIRECT"}],"parserProfile":"token-event"}
JSON
cat >"$TMP_DIR/full.json" <<'JSON'
{"relationships":[{"source":"orders.customer_id","target":"customers.id","evidence":[{"type":"SQL_LOG_JOIN","attributes":{"fullGrammarNative":true,"sourceFile":"query.sql","sourceStatementId":"query.sql:2-2","sourceLine":2}}]}],"dataLineages":[{"source":"orders.amount","target":"sales_fact.amount","flowKind":"VALUE","transformType":"DIRECT"}],"parserProfile":"full-grammar"}
JSON
cat >"$TMP_DIR/semantic-change.json" <<'JSON'
{"relationships":[{"source":"orders.customer_id","target":"customers.id","evidence":[{"type":"SQL_LOG_JOIN","attributes":{"fullGrammarNative":true,"sourceFile":"query.sql","sourceStatementId":"query.sql:3-3","sourceLine":3}}]}],"dataLineages":[{"source":"orders.amount","target":"sales_fact.amount","flowKind":"CONTROL","transformType":"DIRECT"}],"parserProfile":"full-grammar"}
JSON

"$VERIFICATION_RUNNER" fingerprint --semantic \
  --workspace "$TMP_DIR/fingerprint-token" \
  --output "$TMP_DIR/token.tsv" \
  "$TMP_DIR/token.json"
"$VERIFICATION_RUNNER" fingerprint --semantic \
  --workspace "$TMP_DIR/fingerprint-full" \
  --output "$TMP_DIR/full.tsv" \
  "$TMP_DIR/full.json"
"$VERIFICATION_RUNNER" fingerprint --semantic \
  --workspace "$TMP_DIR/fingerprint-changed" \
  --output "$TMP_DIR/changed.tsv" \
  "$TMP_DIR/semantic-change.json"
token_semantic_hash="$(cut -f1 "$TMP_DIR/token.tsv")"
full_semantic_hash="$(cut -f1 "$TMP_DIR/full.tsv")"
changed_semantic_hash="$(cut -f1 "$TMP_DIR/changed.tsv")"
[[ "$token_semantic_hash" == "$full_semantic_hash" ]]
[[ "$token_semantic_hash" != "$changed_semantic_hash" ]]

mkdir -p "$TMP_DIR/reports" "$TMP_DIR/logs"
cat >"$TMP_DIR/reports/TEST-fast.xml" <<'XML'
<testsuite name="FastTest" tests="2" failures="0" errors="0" skipped="0" time="1.25"/>
XML
cat >"$TMP_DIR/reports/TEST-slow.xml" <<'XML'
<testsuite name="SlowTest" tests="3" failures="0" errors="0" skipped="1" time="8.50"/>
XML
cat >"$TMP_DIR/logs/mysql.log" <<'LOG'
case=mysql-v8_0-full elapsedSeconds=12 status=0
LOG
cat >"$TMP_DIR/batch-report.json" <<'JSON'
{
  "summary": {"caseCount": 2, "successCount": 2, "failedCount": 0, "skippedCount": 0},
  "cases": [
    {"id": "oracle-v26ai-full", "status": "SUCCESS", "elapsedMillis": 14500},
    {"id": "sqlserver-v2025-full", "status": "SUCCESS", "elapsedMillis": 9200}
  ]
}
JSON
cat >"$TMP_DIR/maven.log" <<'LOG'
slow correctness fixture /repo/test-fixtures/correctness/mysql/example/manifest.yml 4321 ms
[INFO] relation-detector-core ............................. SUCCESS [  8.691 s]
LOG
cat >"$TMP_DIR/correctness-summary.json" <<'JSON'
{"profile":"full","discovered":1198,"selected":1198,"executed":1198,"passed":1198,"failed":0}
JSON
cat >"$TMP_DIR/fingerprints.tsv" <<'TSV'
abc123	/repo/results/mysql-v8_0-full.json
def456	/repo/results/mysql-v8_0-full-derived-fresh.json
TSV
cat >"$TMP_DIR/semantic-fingerprints.tsv" <<'TSV'
semantic123	/repo/results/mysql-v8_0-full.json
semantic456	/repo/results/mysql-v8_0-full-derived-fresh.json
TSV

"$VERIFICATION_RUNNER" performance \
  --session-start 0 \
  --surefire-root "$TMP_DIR/reports" \
  --cli-log-root "$TMP_DIR/logs" \
  --cli-report "$TMP_DIR/batch-report.json" \
  --correctness-summary "$TMP_DIR/correctness-summary.json" \
  --fingerprints "$TMP_DIR/fingerprints.tsv" \
  --semantic-fingerprints "$TMP_DIR/semantic-fingerprints.tsv" \
  --maven-log "$TMP_DIR/maven.log" \
  --output "$TMP_DIR/report.json"

jq -e '.tests.total == 5 and .tests.skipped == 1' "$TMP_DIR/report.json" >/dev/null
jq -e '.tests.slowest[0].name == "SlowTest"' "$TMP_DIR/report.json" >/dev/null
jq -e '.cliCases[0].name == "mysql-v8_0-full" and .cliCases[0].elapsedSeconds == 12' \
  "$TMP_DIR/report.json" >/dev/null
jq -e '.cliBatch.summary.caseCount == 2 and .cliBatch.cases[0].name == "oracle-v26ai-full" and .cliBatch.cases[0].elapsedMillis == 14500' \
  "$TMP_DIR/report.json" >/dev/null
jq -e '.fixtures.slowest[0].elapsedMillis == 4321' "$TMP_DIR/report.json" >/dev/null
jq -e '.maven.modules[0].name == "relation-detector-core"' "$TMP_DIR/report.json" >/dev/null
jq -e '.correctness.executed == 1198 and .correctness.failed == 0' "$TMP_DIR/report.json" >/dev/null
jq -e '.canonicalFingerprints.count == 2 and .canonicalFingerprints.items[0].name == "mysql-v8_0-full-derived-fresh.json"' \
  "$TMP_DIR/report.json" >/dev/null
jq -e '.semanticFingerprints.count == 2 and .semanticFingerprints.items[0].sha256 == "semantic456"' \
  "$TMP_DIR/report.json" >/dev/null

mkdir -p "$TMP_DIR/results"
cat >"$TMP_DIR/results/example.json" <<'JSON'
{"summary":{"directRelationshipCount":0,"derivedRelationshipCount":0,"totalRelationshipCount":0,"directDataLineageCount":0,"derivedDataLineageCount":0,"totalDataLineageCount":0,"directNamingEvidenceCount":0,"derivedNamingEvidenceCount":0,"totalNamingEvidenceCount":0,"warningCount":0},"metadataInventory":{"status":"COMPLETE","basis":"DDL_DECLARATIONS","scope":{"catalog":"test","schema":"","includeTables":[],"excludeTables":[]},"counts":{"tables":1,"columns":1,"constraints":0,"indexes":0},"tables":[{"tableName":"orders"}],"columns":[{"tableName":"orders","columnName":"id","ordinalPosition":1}],"constraints":[],"indexes":[]},"relationships":[],"derivedRelationships":[],"dataLineages":[],"derivedDataLineages":[],"namingEvidence":[],"derivedNamingEvidence":[],"warnings":[]}
JSON
cp "$TMP_DIR/results/example.json" "$TMP_DIR/results/example-derived-fresh.json"
"$VERIFICATION_RUNNER" validate-results \
  --result-dir "$TMP_DIR/results" \
  --expected-categories 1 \
  --output "$TMP_DIR/result-validation.json" >/dev/null

mkdir -p "$TMP_DIR/results-duplicate"
cat >"$TMP_DIR/results-duplicate/example.json" <<'JSON'
{"summary":{"directRelationshipCount":1,"derivedRelationshipCount":0,"totalRelationshipCount":1,"directDataLineageCount":0,"derivedDataLineageCount":0,"totalDataLineageCount":0,"directNamingEvidenceCount":0,"derivedNamingEvidenceCount":0,"totalNamingEvidenceCount":0,"warningCount":0},"metadataInventory":{"status":"COMPLETE","basis":"DDL_DECLARATIONS","scope":{"catalog":"test","schema":"","includeTables":[],"excludeTables":[]},"counts":{"tables":1,"columns":1,"constraints":0,"indexes":0},"tables":[{"tableName":"orders"}],"columns":[{"tableName":"orders","columnName":"id","ordinalPosition":1}],"constraints":[],"indexes":[]},"relationships":[{"rawEvidence":[{"type":"SQL_LOG_JOIN","source":"query.sql","attributes":{"sourceFile":"query.sql","sourceStatementId":"query.sql:1-1","sourceLine":1}},{"type":"SQL_LOG_JOIN","source":"query.sql","attributes":{"sourceFile":"query.sql","sourceStatementId":"query.sql:1-1","sourceLine":1}}]}],"derivedRelationships":[],"dataLineages":[],"derivedDataLineages":[],"namingEvidence":[],"derivedNamingEvidence":[],"warnings":[]}
JSON
cp "$TMP_DIR/results-duplicate/example.json" "$TMP_DIR/results-duplicate/example-derived-fresh.json"
printf 'SELECT 1;\n' >"$TMP_DIR/query.sql"
if RELATION_DETECTOR_VERIFICATION_WORKING_DIRECTORY="$TMP_DIR" \
  "$VERIFICATION_RUNNER" validate-results \
  --result-dir "$TMP_DIR/results-duplicate" \
  --expected-categories 1 \
  --output "$TMP_DIR/duplicate-validation.json" >/dev/null 2>&1; then
  echo "duplicate raw observations must fail sample-data validation" >&2
  exit 1
fi

mkdir -p "$TMP_DIR/results-line"
cat >"$TMP_DIR/results-line/example.json" <<'JSON'
{"summary":{"directRelationshipCount":1,"derivedRelationshipCount":0,"totalRelationshipCount":1,"directDataLineageCount":0,"derivedDataLineageCount":0,"totalDataLineageCount":0,"directNamingEvidenceCount":0,"derivedNamingEvidenceCount":0,"totalNamingEvidenceCount":0,"warningCount":0},"metadataInventory":{"status":"COMPLETE","basis":"DDL_DECLARATIONS","scope":{"catalog":"test","schema":"","includeTables":[],"excludeTables":[]},"counts":{"tables":1,"columns":1,"constraints":0,"indexes":0},"tables":[{"tableName":"orders"}],"columns":[{"tableName":"orders","columnName":"id","ordinalPosition":1}],"constraints":[],"indexes":[]},"relationships":[{"rawEvidence":[{"type":"SQL_LOG_JOIN","source":"query.sql","attributes":{"sourceFile":"query.sql","sourceStatementId":"query.sql:1-1","sourceLine":2}}]}],"derivedRelationships":[],"dataLineages":[],"derivedDataLineages":[],"namingEvidence":[],"derivedNamingEvidence":[],"warnings":[]}
JSON
cp "$TMP_DIR/results-line/example.json" "$TMP_DIR/results-line/example-derived-fresh.json"
if RELATION_DETECTOR_VERIFICATION_WORKING_DIRECTORY="$TMP_DIR" \
  "$VERIFICATION_RUNNER" validate-results \
  --result-dir "$TMP_DIR/results-line" \
  --expected-categories 1 \
  --output "$TMP_DIR/line-validation.json" >/dev/null 2>&1; then
  echo "sourceLine outside statement/file must fail sample-data validation" >&2
  exit 1
fi

echo "benchmark tools test passed"
