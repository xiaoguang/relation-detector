#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

VERIFY_RELEASE="$ROOT/relation-detector/scripts/verify-release.sh"
MANIFEST_BUILDER="$ROOT/relation-detector/scripts/build-verification-manifest.py"

[[ -x "$VERIFY_RELEASE" ]]
[[ -x "$MANIFEST_BUILDER" ]]
grep -q 'NO_CACHE_STATUS' "$VERIFY_RELEASE"
grep -q 'no-cache acceptance failed' "$VERIFY_RELEASE"

no_cache_line="$(grep -n 'maven.build.cache.enabled=false' "$VERIFY_RELEASE" | head -n 1 | cut -d: -f1)"
verify_all_line="$(grep -n 'verify-all.sh' "$VERIFY_RELEASE" | head -n 1 | cut -d: -f1)"
[[ -n "$no_cache_line" && -n "$verify_all_line" && "$no_cache_line" -lt "$verify_all_line" ]]

mkdir -p "$TMP_DIR/results" "$TMP_DIR/verification"
cat >"$TMP_DIR/result.json" <<'JSON'
{
  "summary": {
    "directRelationshipCount": 0,
    "derivedRelationshipCount": 0,
    "totalRelationshipCount": 0,
    "directDataLineageCount": 0,
    "derivedDataLineageCount": 0,
    "totalDataLineageCount": 0,
    "directNamingEvidenceCount": 0,
    "derivedNamingEvidenceCount": 0,
    "totalNamingEvidenceCount": 0,
    "warningCount": 0
  },
  "relationships": [],
  "dataLineages": [],
  "derivedRelationships": [],
  "derivedDataLineages": [],
  "namingEvidence": [],
  "derivedNamingEvidence": [],
  "warnings": []
}
JSON
cp "$TMP_DIR/result.json" "$TMP_DIR/results/example.json"
cp "$TMP_DIR/result.json" "$TMP_DIR/results/example-derived-fresh.json"

cat >"$TMP_DIR/correctness.json" <<'JSON'
{"profile":"full","discovered":1,"selected":1,"executed":1,"passed":1,"failed":0}
JSON
cat >"$TMP_DIR/parity.tsv" <<'TSV'
Pair	Token	Full	TokenOnly	FullOnly
mysql	1	1	0	0
postgres	1	1	0	0
oracle	1	1	0	0
sqlserver	1	1	0	0
TSV
cat >"$TMP_DIR/warnings.tsv" <<'TSV'
parser	warningCode	count
example	NONE	0
TSV
cat >"$TMP_DIR/fingerprints.tsv" <<'TSV'
aaa	example.json
bbb	example-derived-fresh.json
TSV
cp "$TMP_DIR/fingerprints.tsv" "$TMP_DIR/semantic-fingerprints.tsv"
printf '{"java":"test"}\n' >"$TMP_DIR/verification/environment.json"

python3 "$MANIFEST_BUILDER" \
  --verification-dir "$TMP_DIR/verification" \
  --results-dir "$TMP_DIR/results" \
  --correctness-summary "$TMP_DIR/correctness.json" \
  --observation-parity "$TMP_DIR/parity.tsv" \
  --warning-codes "$TMP_DIR/warnings.tsv" \
  --fingerprints "$TMP_DIR/fingerprints.tsv" \
  --semantic-fingerprints "$TMP_DIR/semantic-fingerprints.tsv" \
  --commit test-commit \
  --branch test-branch \
  --origin-main test-commit \
  --worktree-clean true \
  --maven-status 0 \
  --expected-fixtures 1 \
  --expected-categories 1 \
  --expected-json 2 \
  --artifact "$TMP_DIR/verification/environment.json" \
  --output "$TMP_DIR/verification/verification-manifest.json"

jq -e '
  .status == "PASS" and
  .correctness.executed == 1 and
  .parserMatrix.categories == 1 and
  .parserMatrix.jsonFiles == 2 and
  .diagnostics.total == 0 and
  .observationParity.differenceCount == 0 and
  .integrity.evidenceRefs == "PASS" and
  .integrity.sourcePaths == "PASS" and
  .integrity.sourceLines == "PASS" and
  .integrity.derivedCycles == "PASS" and
  (.artifacts | length) == 6 and
  any(.artifacts[]; .path == "environment.json")
' "$TMP_DIR/verification/verification-manifest.json" >/dev/null

cat >"$TMP_DIR/parity-bad.tsv" <<'TSV'
Pair	Token	Full	TokenOnly	FullOnly
mysql	1	2	0	1
postgres	1	1	0	0
oracle	1	1	0	0
sqlserver	1	1	0	0
TSV

if python3 "$MANIFEST_BUILDER" \
  --verification-dir "$TMP_DIR/verification" \
  --results-dir "$TMP_DIR/results" \
  --correctness-summary "$TMP_DIR/correctness.json" \
  --observation-parity "$TMP_DIR/parity-bad.tsv" \
  --warning-codes "$TMP_DIR/warnings.tsv" \
  --fingerprints "$TMP_DIR/fingerprints.tsv" \
  --semantic-fingerprints "$TMP_DIR/semantic-fingerprints.tsv" \
  --commit test-commit --branch test-branch --origin-main test-commit \
  --worktree-clean true --maven-status 0 \
  --expected-fixtures 1 --expected-categories 1 --expected-json 2 \
  --output "$TMP_DIR/verification/bad-manifest.json" >/dev/null 2>&1; then
  echo "non-zero observation parity must fail manifest generation" >&2
  exit 1
fi

echo "verify release test passed"
