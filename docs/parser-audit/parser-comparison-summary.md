# Parser Comparison Summary

This document separates three questions that used to be mixed together:

1. What can each parser currently recognize on full `sample-data` assets?
2. Can every parser produce the same result when the business semantics are intentionally equivalent?
3. Which remaining differences are parser capability gaps instead of SQL asset differences?

## 1. sample-data Capability Snapshot

`sample-data` is the broad ERP corpus. The SQL files are dialect-native, so this table is a capability snapshot, not a strict equality benchmark.

| Parser category | Fixtures | SQL / DDL | Relations | Lineage | NAMING_MATCH | Diagnostics |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| common token-event sample-data | 15 | 11 / 4 | 729 | 292 | 419 | 0 |
| MySQL token-event root sample-data | 34 | 28 / 6 | 566 | 261 | 267 | 0 |
| MySQL full-grammer v5_7 sample-data | 37 | 31 / 6 | 562 | 285 | 256 | 0 |
| MySQL full-grammer v8_0 sample-data | 37 | 31 / 6 | 785 | 273 | 418 | 0 |
| PostgreSQL token-event root sample-data | 31 | 25 / 6 | 674 | 218 | 273 | 0 |
| PostgreSQL full-grammer v16 sample-data | 31 | 25 / 6 | 675 | 219 | 340 | 0 |
| PostgreSQL full-grammer v17 sample-data | 31 | 25 / 6 | 675 | 219 | 340 | 0 |
| PostgreSQL full-grammer v18 sample-data | 31 | 25 / 6 | 674 | 218 | 340 | 0 |
| Oracle token-event root sample-data | 34 | 27 / 7 | 629 | 217 | 232 | 0 |
| Oracle full-grammer v12c sample-data | 34 | 27 / 7 | 666 | 217 | 330 | 0 |
| Oracle full-grammer v19c sample-data | 34 | 27 / 7 | 666 | 217 | 330 | 0 |
| Oracle full-grammer v21c sample-data | 34 | 27 / 7 | 666 | 217 | 330 | 0 |
| Oracle full-grammer v26ai sample-data | 34 | 27 / 7 | 666 | 217 | 330 | 0 |
| SQL Server token-event root sample-data | 38 | 32 / 6 | 703 | 360 | 275 | 0 |
| SQL Server full-grammer v2016 sample-data | 38 | 32 / 6 | 709 | 360 | 345 | 0 |
| SQL Server full-grammer v2017 sample-data | 38 | 32 / 6 | 709 | 360 | 345 | 0 |
| SQL Server full-grammer v2019 sample-data | 38 | 32 / 6 | 709 | 360 | 345 | 0 |
| SQL Server full-grammer v2022 sample-data | 38 | 32 / 6 | 709 | 360 | 345 | 0 |
| SQL Server full-grammer v2025 sample-data | 38 | 32 / 6 | 709 | 360 | 345 | 0 |

Interpretation:

- MySQL token-event and MySQL 5.7/8.0 full-grammer differ because root token-event covers fewer MySQL-native fixture files, while versioned full-grammer includes 5.7/8.0 compatible translations and broader DDL naming inventory. The semantic-equivalent benchmark is the equality check; this table is a broad capability snapshot.
- PostgreSQL token-event and PostgreSQL full-grammer are close on sample-data lineage. Full-grammer emits more top-level naming evidence from DDL inventory, while relation/lineage deltas are small.
- Oracle full-grammer now matches Oracle token-event on lineage for the retained sample-data surface and emits additional DDL/name evidence. Broader official Oracle grammar hardening remains backlog, not a `REVIEW_NEEDED` business decision.
- SQL Server sample-data is now fully represented. SQL Server full-grammer is slightly ahead of token-event on relationship and naming coverage while lineage is aligned.
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
| MySQL token-event vs MySQL full-grammer on broad sample-data | root `566 / 261`; v8_0 `785 / 273` | `TOKEN_EVENT_TYPED_VISITOR_COVERAGE` | Continue adding MySQL-native typed token-event visitor support only when a concrete SQL fixture shows a confirmed miss. Do not restore scanner or regex fallback. |
| PostgreSQL token-event vs PostgreSQL full-grammer on broad sample-data | root `674 / 218`; v18 `674 / 218` | `NAMING_EVIDENCE_INVENTORY_DELTA` | Lineage and relationship counts are aligned for v18; full-grammer still emits more top-level naming evidence from DDL inventory. |
| Oracle full-grammer vs Oracle token-event on broad sample-data | root `629 / 217`; full-grammer `666 / 217` | `ORACLE_RELATION_NAMING_COVERAGE` | Continue auditing relation/naming deltas through typed visitor / evidence aggregation, not SQL text heuristics. Oracle full-grammer remains versioned but official full syntax coverage is still backlog. |
| SQL Server full-grammer vs token-event on broad sample-data | root `703 / 360`; full-grammer `709 / 360` | `SQLSERVER_TOKEN_EVENT_RELATION_COVERAGE` | Keep token-event compact. Confirmed T-SQL gaps should be fixed in typed token-event grammar/visitor, not by copying full grammar. |
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
