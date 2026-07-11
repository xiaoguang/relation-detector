# Parser Comparison Summary

This document separates three questions that used to be mixed together:

1. What can each parser currently recognize on full `sample-data` assets?
2. Can every parser produce the same result when the business semantics are intentionally equivalent?
3. Which remaining differences are parser capability gaps instead of SQL asset differences?

## 1. sample-data Capability Snapshot

`sample-data` is the broad ERP corpus. The SQL files are dialect-native, so this table is a capability snapshot, not a strict equality benchmark.

| Parser category | Fixtures | SQL / DDL | Relations | Lineage | NAMING_MATCH | Diagnostics |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| common token-event sample-data | 9 | 6 / 3 | 316 | 109 | 250 | 0 |
| MySQL token-event root sample-data | 38 | 32 / 6 | 361 | 288 | 248 | 0 |
| MySQL full-grammer v5_7 sample-data | 38 | 32 / 6 | 330 | 290 | 244 | 0 |
| MySQL full-grammer v8_0 sample-data | 38 | 32 / 6 | 361 | 288 | 248 | 0 |
| PostgreSQL token-event root sample-data | 38 | 32 / 6 | 356 | 250 | 247 | 0 |
| PostgreSQL full-grammer v16 sample-data | 38 | 32 / 6 | 356 | 251 | 247 | 0 |
| PostgreSQL full-grammer v17 sample-data | 38 | 32 / 6 | 356 | 251 | 247 | 0 |
| PostgreSQL full-grammer v18 sample-data | 38 | 32 / 6 | 356 | 250 | 247 | 0 |
| Oracle token-event root sample-data | 38 | 32 / 6 | 365 | 247 | 248 | 0 |
| Oracle full-grammer v12c sample-data | 38 | 32 / 6 | 365 | 247 | 248 | 0 |
| Oracle full-grammer v19c sample-data | 38 | 32 / 6 | 365 | 247 | 248 | 0 |
| Oracle full-grammer v21c sample-data | 38 | 32 / 6 | 365 | 247 | 248 | 0 |
| Oracle full-grammer v26ai sample-data | 38 | 32 / 6 | 365 | 247 | 248 | 0 |
| SQL Server token-event root sample-data | 38 | 32 / 6 | 337 | 310 | 245 | 0 |
| SQL Server full-grammer v2016 sample-data | 38 | 32 / 6 | 337 | 310 | 245 | 0 |
| SQL Server full-grammer v2017 sample-data | 38 | 32 / 6 | 337 | 310 | 245 | 0 |
| SQL Server full-grammer v2019 sample-data | 38 | 32 / 6 | 337 | 310 | 245 | 0 |
| SQL Server full-grammer v2022 sample-data | 38 | 32 / 6 | 337 | 310 | 245 | 0 |
| SQL Server full-grammer v2025 sample-data | 38 | 32 / 6 | 337 | 310 | 245 | 0 |

Interpretation:

- MySQL token-event and MySQL 5.7/8.0 full-grammer now cover the same sample-data surface. Their remaining differences come from natural 5.7/8.0 SQL rewrites, versioned DDL/routine coverage, and parser capability differences. The semantic-equivalent benchmark is the equality check; this table is a broad capability snapshot.
- PostgreSQL token-event and PostgreSQL full-grammer are close on sample-data lineage. v16/v17 have one extra non-trivial self-update lineage from the version-specific `pg16_generated_margin_demo.sales_amount = target.sales_amount * 1.05` SQL; that is an expected asset/version delta, not a parser false positive.
- Oracle token-event and the four full-grammer profiles now match on direct `Rel/Lin/Name` counts and on the audited direct relationship set. Token-event supports `OPEN cursor FOR SELECT` and `LISTAGG ... WITHIN GROUP`; full-grammer traverses SELECT-list scalar subqueries and rejects function-to-function expressions as direct column equality.
- SQL Server natural assets now conform to their DDL contract. Token-event and all five full-grammer profiles produce the same audited direct relationship, lineage fingerprint, and direct naming-id sets on the natural corpus.
- `sample-data` counts should not be used as a semantic equality score because each dialect has native syntax and version-specific assets.

### Derived-enabled Snapshot

This table uses the same sample-data CLI inputs with `derivedPaths.enabled=true`. `DerRel` is derived relationship count, `DerLin` is derived value-lineage count, and `DerName` is top-level `TRANSITIVE_NAMING_PATH` count. `Rel`, `Lin`, and `DirName` are the direct counts from the same scan; `Name` is intentionally not a total so direct and derived values remain separately readable.

| Parser category | Fixtures | SQL / DDL | Rel | Lin | DirName | Diag | DerRel | DerLin | DerName |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| common token-event sample-data | 9 | 6 / 3 | 316 | 109 | 250 | 0 | 1008 | 7 | 728 |
| MySQL token-event root sample-data | 38 | 32 / 6 | 361 | 288 | 248 | 0 | 1077 | 63 | 771 |
| MySQL full-grammer v5_7 sample-data | 38 | 32 / 6 | 330 | 290 | 244 | 0 | 999 | 74 | 727 |
| MySQL full-grammer v8_0 sample-data | 38 | 32 / 6 | 361 | 288 | 248 | 0 | 1077 | 63 | 771 |
| PostgreSQL token-event root sample-data | 38 | 32 / 6 | 356 | 250 | 247 | 0 | 1070 | 61 | 764 |
| PostgreSQL full-grammer v16 sample-data | 38 | 32 / 6 | 356 | 251 | 247 | 0 | 1070 | 61 | 764 |
| PostgreSQL full-grammer v17 sample-data | 38 | 32 / 6 | 356 | 251 | 247 | 0 | 1070 | 61 | 764 |
| PostgreSQL full-grammer v18 sample-data | 38 | 32 / 6 | 356 | 250 | 247 | 0 | 1070 | 61 | 764 |
| Oracle token-event root sample-data | 38 | 32 / 6 | 365 | 247 | 248 | 0 | 1077 | 53 | 771 |
| Oracle full-grammer v12c sample-data | 38 | 32 / 6 | 365 | 247 | 248 | 0 | 1077 | 53 | 771 |
| Oracle full-grammer v19c sample-data | 38 | 32 / 6 | 365 | 247 | 248 | 0 | 1077 | 53 | 771 |
| Oracle full-grammer v21c sample-data | 38 | 32 / 6 | 365 | 247 | 248 | 0 | 1077 | 53 | 771 |
| Oracle full-grammer v26ai sample-data | 38 | 32 / 6 | 365 | 247 | 248 | 0 | 1077 | 53 | 771 |
| SQL Server token-event root sample-data | 38 | 32 / 6 | 337 | 310 | 245 | 0 | 993 | 130 | 722 |
| SQL Server full-grammer v2016 sample-data | 38 | 32 / 6 | 337 | 310 | 245 | 0 | 993 | 130 | 722 |
| SQL Server full-grammer v2017 sample-data | 38 | 32 / 6 | 337 | 310 | 245 | 0 | 993 | 130 | 722 |
| SQL Server full-grammer v2019 sample-data | 38 | 32 / 6 | 337 | 310 | 245 | 0 | 993 | 130 | 722 |
| SQL Server full-grammer v2022 sample-data | 38 | 32 / 6 | 337 | 310 | 245 | 0 | 993 | 130 | 722 |
| SQL Server full-grammer v2025 sample-data | 38 | 32 / 6 | 337 | 310 | 245 | 0 | 993 | 130 | 722 |

Derived relationship now uses reverse referenced-by traversal internally and still emits FK-like forward output. The large SQL Server derived relationship inflation caused by earlier forward FK + identity bridge traversal is removed; derived naming evidence is now visible as `TRANSITIVE_NAMING_PATH`.

## 2. semantic-equivalent Benchmark

`test-fixtures/semantic-equivalent` is the equality benchmark. Each scenario has dialect/version fixtures for common, MySQL, PostgreSQL, and Oracle. The SQL text may differ, but the intended business semantics and final physical fingerprints must match.

| Scenario | Relations | Lineage | Fixture coverage | Status |
| --- | ---: | ---: | ---: | --- |
| `sales-fact-rebuild` | 2 | 7 | 12 parser categories | `MATCHED` |
| `batch-expiry-analysis` | 2 | 0 | 12 parser categories | `MATCHED` |
| `ddl-fk-index` | 1 | 0 | 12 parser categories | `MATCHED` |
| `inventory-posting` | 2 | 6 | 12 parser categories | `MATCHED` |
| `mrp-run` | 2 | 5 | 12 parser categories | `MATCHED` |
| `picking-task` | 3 | 6 | 12 parser categories | `MATCHED` |
| `return-refund` | 2 | 6 | 12 parser categories | `MATCHED` |
| `relation-probe` | 9 | 0 | SQL Server full-grammer v2016/v2017/v2019/v2022/v2025 | `MATCHED` |

The 12 shared ERP parser categories are:

- common token-event
- MySQL token-event root
- MySQL full-grammer v8_0
- PostgreSQL token-event root
- PostgreSQL full-grammer v16 / v17 / v18
- Oracle token-event root
- Oracle full-grammer v12c / v19c / v21c / v26ai

SQL Server currently has an additional `relation-probe` semantic-equivalent scenario for high-density T-SQL relationship detection across `v2016` through `v2025`. Current conclusion: no confirmed parser gap is exposed by the semantic-equivalent scenarios. If a future scenario has matching business semantics but different fingerprints, that is a parser gap candidate unless the SQL translation itself is not semantically equivalent.

## 3. Parser Gap List

| Area | Current evidence | Classification | Next action |
| --- | --- | --- | --- |
| MySQL token-event vs MySQL full-grammer on broad sample-data | both `361 / 288 / 248` | `AUDITED_SET_MATCH` | Direct relationship fingerprints, exact lineage fingerprints, and direct naming ids match between root token-event and v8_0 full. |
| PostgreSQL token-event vs PostgreSQL full-grammer on broad sample-data | root/v18 `356 / 250`; v16/v17 `356 / 251` | `EXPECTED_VERSION_DELTA` | v16/v17 carry one additional non-trivial self-update lineage in version-specific SQL. |
| Oracle full-grammer vs Oracle token-event on broad sample-data | all profiles `365 / 247 / 248` | `AUDITED_SET_MATCH` | Relationship, lineage flow/transform, and naming id sets match across root and all four full profiles. |
| SQL Server full-grammer vs token-event on broad sample-data | all profiles `337 / 310 / 245` | `AUDITED_SET_MATCH` | Direct relationship fingerprints, exact lineage fingerprints, and direct naming ids match across root and all five full profiles. |
| Cross-dialect semantic-equivalent scenarios | all scenarios are `MATCHED` | `NO_CONFIRMED_GAP` | Use this benchmark as the primary proof that equivalent SQL can converge across parser categories. |
| Dynamic SQL, parameters, local variables, temporary tables, pseudo rowsets | excluded by design in lineage audit | `EXPECTED_FILTERED_SCOPE` | Do not add these to physical relation / lineage golden unless a future design changes the semantic boundary. |

## 4. Output Audit Notes

The latest sample-data output audit checked every generated JSON in `relation-detector/target/sample-data-parser-cli/results`:

- Summary counts match the corresponding output arrays.
- Direct/derived observation counts equal the sum of raw evidence occurrences.
- `warning-codes.tsv` is clean: every parser reports `NONE 0`.
- `rawEvidence.source` no longer contains the local absolute workspace path.
- Every SQL/DB_OBJECT lineage observation has a repo-relative file, statement/block id, and in-range source line.
- Merged lineage top-level provenance contains only attributes shared by every raw observation.
- Every relationship `NAMING_MATCH.evidenceRef` resolves to top-level `namingEvidence`.

The detailed repair record from the latest JSON + SQL audit is recorded in
[`sample-data-output-audit-backlog.md`](sample-data-output-audit-backlog.md). The key
point is that clean JSON structure and zero diagnostics do not prove the SQL assets
are schema-valid. `SampleDataSchemaConsistencyTest` is now the explicit schema gate; count parity alone remains insufficient evidence of parser quality.

## Validation

Use these commands to validate the split:

```bash
mvn -pl relation-detector/cli -am \
  -Dtest=SemanticEquivalentCorrectnessTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -pl relation-detector/cli -am \
  -Dtest=CorrectnessFixtureRunnerTest \
  -DcorrectnessFixtureFilter=semantic-equivalent \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -pl relation-detector/cli -am \
  -Dtest=CorrectnessFixtureRunnerTest \
  -DcorrectnessFixtureProfile=full \
  -Dsurefire.failIfNoSpecifiedTests=false test
```
