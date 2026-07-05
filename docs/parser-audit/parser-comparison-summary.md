# Parser Comparison Summary

This document separates three questions that used to be mixed together:

1. What can each parser currently recognize on full `sample-data` assets?
2. Can every parser produce the same result when the business semantics are intentionally equivalent?
3. Which remaining differences are parser capability gaps instead of SQL asset differences?

## 1. sample-data Capability Snapshot

`sample-data` is the broad ERP corpus. The SQL files are dialect-native, so this table is a capability snapshot, not a strict equality benchmark.

| Parser category | Fixtures | SQL / DDL | Relations | Lineage | NAMING_MATCH | Diagnostics |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| common token-event sample-data | 13 | 10 / 3 | 317 | 152 | 248 | 0 |
| MySQL token-event root sample-data | 38 | 32 / 6 | 349 | 240 | 238 | 0 |
| MySQL full-grammer v5_7 sample-data | 38 | 32 / 6 | 346 | 265 | 243 | 0 |
| MySQL full-grammer v8_0 sample-data | 38 | 32 / 6 | 397 | 254 | 245 | 0 |
| PostgreSQL token-event root sample-data | 38 | 32 / 6 | 369 | 205 | 241 | 0 |
| PostgreSQL full-grammer v16 sample-data | 38 | 32 / 6 | 371 | 206 | 241 | 0 |
| PostgreSQL full-grammer v17 sample-data | 38 | 32 / 6 | 371 | 206 | 241 | 0 |
| PostgreSQL full-grammer v18 sample-data | 38 | 32 / 6 | 370 | 205 | 241 | 0 |
| Oracle token-event root sample-data | 38 | 32 / 6 | 373 | 212 | 238 | 0 |
| Oracle full-grammer v12c sample-data | 38 | 32 / 6 | 375 | 217 | 239 | 0 |
| Oracle full-grammer v19c sample-data | 38 | 32 / 6 | 375 | 217 | 239 | 0 |
| Oracle full-grammer v21c sample-data | 38 | 32 / 6 | 375 | 217 | 239 | 0 |
| Oracle full-grammer v26ai sample-data | 38 | 32 / 6 | 375 | 217 | 239 | 0 |
| SQL Server token-event root sample-data | 38 | 32 / 6 | 335 | 287 | 246 | 0 |
| SQL Server full-grammer v2016 sample-data | 38 | 32 / 6 | 347 | 324 | 246 | 0 |
| SQL Server full-grammer v2017 sample-data | 38 | 32 / 6 | 347 | 324 | 246 | 0 |
| SQL Server full-grammer v2019 sample-data | 38 | 32 / 6 | 347 | 324 | 246 | 0 |
| SQL Server full-grammer v2022 sample-data | 38 | 32 / 6 | 347 | 324 | 246 | 0 |
| SQL Server full-grammer v2025 sample-data | 38 | 32 / 6 | 347 | 324 | 246 | 0 |

Interpretation:

- MySQL token-event and MySQL 5.7/8.0 full-grammer now cover the same sample-data surface. Their remaining differences come from natural 5.7/8.0 SQL rewrites, versioned DDL/routine coverage, and parser capability differences. The semantic-equivalent benchmark is the equality check; this table is a broad capability snapshot.
- PostgreSQL token-event and PostgreSQL full-grammer are close on sample-data lineage. Full-grammer emits more top-level naming evidence from DDL inventory, while relation/lineage deltas are small.
- Oracle full-grammer now matches Oracle token-event on lineage for the retained sample-data surface and emits additional DDL/name evidence. Broader official Oracle grammar hardening remains backlog, not a `REVIEW_NEEDED` business decision.
- SQL Server sample-data is now fully represented. SQL Server full-grammer is ahead of token-event on relationship and lineage coverage; top-level naming evidence is aligned after schema/bare endpoint canonicalization.
- `sample-data` counts should not be used as a semantic equality score because each dialect has native syntax and version-specific assets.

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
| MySQL token-event vs MySQL full-grammer on broad sample-data | root `349 / 240`; v8_0 `397 / 254` | `TOKEN_EVENT_TYPED_VISITOR_COVERAGE` | Continue adding MySQL-native typed token-event visitor support only when a concrete SQL fixture shows a confirmed miss. Do not restore scanner or regex fallback. |
| PostgreSQL token-event vs PostgreSQL full-grammer on broad sample-data | root `369 / 205`; v18 `370 / 205` | `NAMING_EVIDENCE_INVENTORY_DELTA` | Lineage is aligned for v18; full-grammer has a small relationship edge from versioned DDL/predicate coverage while naming evidence is aligned on the current CLI sample-data surface. |
| Oracle full-grammer vs Oracle token-event on broad sample-data | root `373 / 212`; full-grammer `375 / 217` | `ORACLE_RELATION_NAMING_COVERAGE` | Continue auditing relation/naming/lineage deltas through typed visitor / evidence aggregation, not SQL text heuristics. Oracle full-grammer remains versioned but official full syntax coverage is still backlog. |
| SQL Server full-grammer vs token-event on broad sample-data | root `335 / 287`; full-grammer `347 / 324` | `SQLSERVER_TOKEN_EVENT_TYPED_VISITOR_COVERAGE` | Keep token-event compact. Confirmed T-SQL relation/lineage gaps should be fixed in typed token-event grammar/visitor, not by copying full grammar. |
| Cross-dialect semantic-equivalent scenarios | all scenarios are `MATCHED` | `NO_CONFIRMED_GAP` | Use this benchmark as the primary proof that equivalent SQL can converge across parser categories. |
| Dynamic SQL, parameters, local variables, temporary tables, pseudo rowsets | excluded by design in lineage audit | `EXPECTED_FILTERED_SCOPE` | Do not add these to physical relation / lineage golden unless a future design changes the semantic boundary. |

## Validation

Use these commands to validate the split:

```bash
mvn -pl cli -am \
  -Dtest=SemanticEquivalentCorrectnessTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -pl cli -am \
  -Dtest=CorrectnessFixtureRunnerTest \
  -DcorrectnessFixtureFilter=semantic-equivalent \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -pl cli -am \
  -Dtest=CorrectnessFixtureRunnerTest \
  -DcorrectnessFixtureProfile=full \
  -Dsurefire.failIfNoSpecifiedTests=false test
```
