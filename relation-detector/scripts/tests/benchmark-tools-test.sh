#!/usr/bin/env bash
set -euo pipefail

RELATION_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

cat >"$TMP_DIR/one.json" <<'JSON'
{"generatedAt":"first","summary":{"count":1},"facts":[{"id":"a"}]}
JSON
cat >"$TMP_DIR/two.json" <<'JSON'
{"facts":[{"id":"a"}],"summary":{"count":1},"generatedAt":"second"}
JSON

first_hash="$(python3 "$RELATION_ROOT/scripts/canonical-json-fingerprint.py" "$TMP_DIR/one.json" | cut -f1)"
second_hash="$(python3 "$RELATION_ROOT/scripts/canonical-json-fingerprint.py" "$TMP_DIR/two.json" | cut -f1)"
[[ "$first_hash" == "$second_hash" ]]

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

python3 "$RELATION_ROOT/scripts/build-performance-report.py" \
  --session-start 0 \
  --surefire-root "$TMP_DIR/reports" \
  --cli-log-root "$TMP_DIR/logs" \
  --cli-report "$TMP_DIR/batch-report.json" \
  --correctness-summary "$TMP_DIR/correctness-summary.json" \
  --fingerprints "$TMP_DIR/fingerprints.tsv" \
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

mkdir -p "$TMP_DIR/results"
cat >"$TMP_DIR/results/example.json" <<'JSON'
{"summary":{"directRelationshipCount":0,"derivedRelationshipCount":0,"totalRelationshipCount":0,"directDataLineageCount":0,"derivedDataLineageCount":0,"totalDataLineageCount":0,"directNamingEvidenceCount":0,"derivedNamingEvidenceCount":0,"totalNamingEvidenceCount":0,"warningCount":0},"relationships":[],"derivedRelationships":[],"dataLineages":[],"derivedDataLineages":[],"namingEvidence":[],"derivedNamingEvidence":[],"warnings":[]}
JSON
cp "$TMP_DIR/results/example.json" "$TMP_DIR/results/example-derived-fresh.json"
python3 "$RELATION_ROOT/scripts/validate-sample-data-results.py" \
  "$TMP_DIR/results" --expected-categories 1 >/dev/null

echo "benchmark tools test passed"
