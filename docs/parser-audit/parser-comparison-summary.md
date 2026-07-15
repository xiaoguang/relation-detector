# Parser Comparison Summary

This document separates three questions that used to be mixed together:

1. What can each parser currently recognize on full `sample-data` assets?
2. Can every parser produce the same result when the business semantics are intentionally equivalent?
3. Which remaining differences are parser capability gaps instead of SQL asset differences?

## 1. sample-data Capability Snapshot

`sample-data` is the broad ERP corpus. The SQL files are dialect-native, so this table is a capability snapshot, not a strict equality benchmark.

| Parser category | Fixtures | SQL / DDL | Relations | Lineage | NAMING_MATCH | Diagnostics |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| common token-event sample-data | 9 | 6 / 3 | 321 | 110 | 250 | 0 |
| MySQL token-event root sample-data | 38 | 32 / 6 | 361 | 448 | 248 | 0 |
| MySQL full-grammar v5_7 sample-data | 38 | 32 / 6 | 331 | 428 | 244 | 0 |
| MySQL full-grammar v8_0 sample-data | 38 | 32 / 6 | 361 | 448 | 248 | 0 |
| PostgreSQL token-event root sample-data | 38 | 32 / 6 | 366 | 384 | 248 | 0 |
| PostgreSQL full-grammar v16 sample-data | 38 | 32 / 6 | 366 | 384 | 248 | 0 |
| PostgreSQL full-grammar v17 sample-data | 38 | 32 / 6 | 366 | 384 | 248 | 0 |
| PostgreSQL full-grammar v18 sample-data | 38 | 32 / 6 | 366 | 384 | 248 | 0 |
| Oracle token-event root sample-data | 38 | 32 / 6 | 366 | 328 | 248 | 0 |
| Oracle full-grammar v12c sample-data | 38 | 32 / 6 | 366 | 330 | 248 | 0 |
| Oracle full-grammar v19c sample-data | 38 | 32 / 6 | 366 | 328 | 248 | 0 |
| Oracle full-grammar v21c sample-data | 38 | 32 / 6 | 366 | 328 | 248 | 0 |
| Oracle full-grammar v26ai sample-data | 38 | 32 / 6 | 366 | 328 | 248 | 0 |
| SQL Server token-event root sample-data | 38 | 32 / 6 | 342 | 756 | 246 | 0 |
| SQL Server full-grammar v2016 sample-data | 38 | 32 / 6 | 342 | 756 | 246 | 0 |
| SQL Server full-grammar v2017 sample-data | 38 | 32 / 6 | 342 | 756 | 246 | 0 |
| SQL Server full-grammar v2019 sample-data | 38 | 32 / 6 | 342 | 756 | 246 | 0 |
| SQL Server full-grammar v2022 sample-data | 38 | 32 / 6 | 342 | 756 | 246 | 0 |
| SQL Server full-grammar v2025 sample-data | 38 | 32 / 6 | 342 | 756 | 246 | 0 |

Interpretation:

- MySQL token-event and MySQL 5.7/8.0 full-grammar now cover the same sample-data surface. Their remaining differences come from natural 5.7/8.0 SQL rewrites, versioned DDL/routine coverage, and parser capability differences. The semantic-equivalent benchmark is the equality check; this table is a broad capability snapshot.
- PostgreSQL token-event and v16/v17/v18 full-grammar now produce the same direct fact counts and exact semantic observations on the natural corpus. The non-trivial `UPDATE ... RETURNING` self-update is retained in every applicable profile.
- Oracle token-event and v26ai full-grammar match exactly on semantic observations. v12c retains a small lineage count difference caused by its version-specific natural SQL assets; relationships and direct naming counts remain aligned.
- SQL Server natural assets now conform to their DDL contract. Token-event and all five full-grammar profiles produce the same audited direct relationship, lineage fingerprint, and direct naming-id sets on the natural corpus.
- `sample-data` counts should not be used as a semantic equality score because each dialect has native syntax and version-specific assets.

### Derived-enabled Snapshot

This table uses the same sample-data CLI inputs with `derivedPaths.enabled=true`. `DerRel` is derived relationship count, `DerLin` is derived value-lineage count, and `DerName` is top-level `TRANSITIVE_NAMING_PATH` count. `Rel`, `Lin`, and `DirName` are the direct counts from the same scan; `Name` is intentionally not a total so direct and derived values remain separately readable.

| Parser category | Fixtures | SQL / DDL | Rel | Lin | DirName | Diag | DerRel | DerLin | DerName |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| common token-event sample-data | 9 | 6 / 3 | 321 | 110 | 250 | 0 | 1190 | 7 | 861 |
| MySQL token-event root sample-data | 38 | 32 / 6 | 361 | 448 | 248 | 0 | 1266 | 89 | 907 |
| MySQL full-grammar v5_7 sample-data | 38 | 32 / 6 | 331 | 428 | 244 | 0 | 1166 | 100 | 848 |
| MySQL full-grammar v8_0 sample-data | 38 | 32 / 6 | 361 | 448 | 248 | 0 | 1266 | 89 | 907 |
| PostgreSQL token-event root sample-data | 38 | 32 / 6 | 366 | 384 | 248 | 0 | 1264 | 62 | 905 |
| PostgreSQL full-grammar v16 sample-data | 38 | 32 / 6 | 366 | 384 | 248 | 0 | 1264 | 62 | 905 |
| PostgreSQL full-grammar v17 sample-data | 38 | 32 / 6 | 366 | 384 | 248 | 0 | 1264 | 62 | 905 |
| PostgreSQL full-grammar v18 sample-data | 38 | 32 / 6 | 366 | 384 | 248 | 0 | 1264 | 62 | 905 |
| Oracle token-event root sample-data | 38 | 32 / 6 | 366 | 328 | 248 | 0 | 1265 | 57 | 906 |
| Oracle full-grammar v12c sample-data | 38 | 32 / 6 | 366 | 330 | 248 | 0 | 1265 | 57 | 906 |
| Oracle full-grammar v19c sample-data | 38 | 32 / 6 | 366 | 328 | 248 | 0 | 1265 | 57 | 906 |
| Oracle full-grammar v21c sample-data | 38 | 32 / 6 | 366 | 328 | 248 | 0 | 1265 | 57 | 906 |
| Oracle full-grammar v26ai sample-data | 38 | 32 / 6 | 366 | 328 | 248 | 0 | 1265 | 57 | 906 |
| SQL Server token-event root sample-data | 38 | 32 / 6 | 342 | 756 | 246 | 0 | 1127 | 195 | 812 |
| SQL Server full-grammar v2016 sample-data | 38 | 32 / 6 | 342 | 756 | 246 | 0 | 1127 | 195 | 812 |
| SQL Server full-grammar v2017 sample-data | 38 | 32 / 6 | 342 | 756 | 246 | 0 | 1127 | 195 | 812 |
| SQL Server full-grammar v2019 sample-data | 38 | 32 / 6 | 342 | 756 | 246 | 0 | 1127 | 195 | 812 |
| SQL Server full-grammar v2022 sample-data | 38 | 32 / 6 | 342 | 756 | 246 | 0 | 1127 | 195 | 812 |
| SQL Server full-grammar v2025 sample-data | 38 | 32 / 6 | 342 | 756 | 246 | 0 | 1127 | 195 | 812 |

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
| `relation-probe` | 9 | 0 | SQL Server full-grammar v2016/v2017/v2019/v2022/v2025 | `MATCHED` |

The 12 shared ERP parser categories are:

- common token-event
- MySQL token-event root
- MySQL full-grammar v8_0
- PostgreSQL token-event root
- PostgreSQL full-grammar v16 / v17 / v18
- Oracle token-event root
- Oracle full-grammar v12c / v19c / v21c / v26ai

SQL Server currently has an additional `relation-probe` semantic-equivalent scenario for high-density T-SQL relationship detection across `v2016` through `v2025`. Current conclusion: no confirmed parser gap is exposed by the semantic-equivalent scenarios. If a future scenario has matching business semantics but different fingerprints, that is a parser gap candidate unless the SQL translation itself is not semantically equivalent.

## 3. Parser Gap List

| Area | Current evidence | Classification | Next action |
| --- | --- | --- | --- |
| MySQL token-event vs MySQL full-grammar on broad sample-data | root/v8.0 `361 / 448 / 248` | `AUDITED_SET_MATCH` | Direct relationship fingerprints, exact lineage observations, and direct naming ids match between root token-event and v8_0 full. |
| PostgreSQL token-event vs PostgreSQL full-grammar on broad sample-data | all profiles `366 / 384 / 248` | `AUDITED_SET_MATCH` | Exact semantic observations match between root token-event and v18 full; v16/v17/v18 direct counts are also aligned. |
| Oracle full-grammar vs Oracle token-event on broad sample-data | root/v26ai `366 / 328 / 248` | `AUDITED_SET_MATCH` | Exact observations match for the same v26ai asset; the v12c lineage count difference is a version-specific SQL asset delta. |
| SQL Server full-grammar vs token-event on broad sample-data | all profiles `342 / 756 / 246` | `AUDITED_SET_MATCH` | Direct relationship fingerprints, exact lineage observations, and direct naming ids match across root and all five full profiles. |
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
