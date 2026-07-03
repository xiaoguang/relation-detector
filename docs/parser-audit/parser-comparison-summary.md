# Parser Comparison Summary

This document separates three questions that used to be mixed together:

1. What can each parser currently recognize on full `sample-data` assets?
2. Can every parser produce the same result when the business semantics are intentionally equivalent?
3. Which remaining differences are parser capability gaps instead of SQL asset differences?

## 1. sample-data Capability Snapshot

`sample-data` is the broad ERP corpus. The SQL files are dialect-native, so this table is a capability snapshot, not a strict equality benchmark.

| Parser category | Fixtures | SQL / DDL | Relations | Lineage |
| --- | ---: | ---: | ---: | ---: |
| common token-event sample-data | 15 | 11 / 4 | 729 | 292 |
| MySQL token-event root sample-data | 34 | 28 / 6 | 562 | 208 |
| MySQL full-grammer v8_0 sample-data | 37 | 31 / 6 | 784 | 273 |
| PostgreSQL token-event root sample-data | 31 | 25 / 6 | 673 | 218 |
| PostgreSQL full-grammer v16 sample-data | 31 | 25 / 6 | 675 | 219 |
| PostgreSQL full-grammer v17 sample-data | 31 | 25 / 6 | 675 | 219 |
| PostgreSQL full-grammer v18 sample-data | 31 | 25 / 6 | 674 | 218 |
| Oracle token-event root sample-data | 34 | 27 / 7 | 629 | 217 |
| Oracle full-grammer v12c sample-data | 34 | 27 / 7 | 666 | 217 |
| Oracle full-grammer v19c sample-data | 34 | 27 / 7 | 666 | 217 |
| Oracle full-grammer v21c sample-data | 34 | 27 / 7 | 666 | 217 |
| Oracle full-grammer v26ai sample-data | 34 | 27 / 7 | 666 | 217 |

Interpretation:

- MySQL full-grammer remains stronger than MySQL token-event on broad MySQL-native sample-data.
- PostgreSQL token-event and PostgreSQL full-grammer are now close on sample-data; the remaining relation delta is small.
- Oracle full-grammer now matches Oracle token-event on sample-data lineage and emits more sample-data relationships. The remaining Oracle work is token-event relation evidence coverage and broader official grammar hardening, not confirmed procedure lineage.
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

The 12 parser categories are:

- common token-event
- MySQL token-event root
- MySQL full-grammer v8_0
- PostgreSQL token-event root
- PostgreSQL full-grammer v16 / v17 / v18
- Oracle token-event root
- Oracle full-grammer v12c / v19c / v21c / v26ai

Current conclusion: no confirmed parser gap is exposed by the seven semantic-equivalent scenarios. If a future scenario has matching business semantics but different fingerprints, that is a parser gap candidate unless the SQL translation itself is not semantically equivalent.

## 3. Parser Gap List

| Area | Current evidence | Classification | Next action |
| --- | --- | --- | --- |
| MySQL token-event vs MySQL full-grammer on broad sample-data | `562 / 208` vs `784 / 273` relation / lineage | `TOKEN_EVENT_TYPED_VISITOR_COVERAGE` | Continue adding MySQL-native typed token-event visitor support only when a concrete SQL fixture shows a confirmed miss. Do not restore scanner or regex fallback. |
| PostgreSQL token-event vs PostgreSQL full-grammer on broad sample-data | root `673 / 218`; v18 `674 / 218` | `MINOR_EVIDENCE_DELTA` | Track the remaining relation-only delta as typed visitor / evidence aggregation hardening. It is not a SQL asset mismatch. |
| Oracle full-grammer vs Oracle token-event on broad sample-data | root `629 / 217`; full-grammer `666 / 217` | `ORACLE_TOKEN_EVENT_RELATION_EVIDENCE_COVERAGE` | Procedure lineage equivalence is reached for the current sample-data slice. Continue auditing token-event relation-only misses through typed visitor / evidence aggregation, not SQL text heuristics. Oracle full-grammer remains `INCOMPLETE_VERSIONED`. |
| Cross-dialect semantic-equivalent scenarios | all seven scenarios are `MATCHED` | `NO_CONFIRMED_GAP` | Use this benchmark as the primary proof that equivalent SQL can converge across parser categories. |
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
