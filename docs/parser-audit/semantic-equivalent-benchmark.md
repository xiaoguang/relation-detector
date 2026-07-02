# Semantic Equivalent Benchmark Audit

This audit records cross-parser scenarios where the SQL text may differ by
dialect, but the intended business semantics and the final physical
relationship / lineage fingerprints must match one canonical expectation.

## Purpose

- Keep `sample-data` parser comparisons semantic rather than count-based.
- Expose parser gaps when a dialect has equivalent SQL but misses confirmed
  physical relationships or lineage.
- Separate semantic-equivalent coverage from dialect/version-only syntax tests.

## Current Scenarios

| Scenario | Coverage | Expected relation | Expected lineage | Status |
|---|---:|---:|---:|---|
| `sales-fact-rebuild` | common, MySQL token-event, MySQL 8.0 full-grammer, PostgreSQL root/v16/v17/v18, Oracle root/v12c/v19c/v21c/v26ai | 2 | 7 | `MATCHED` |
| `batch-expiry-analysis` | common, MySQL token-event, MySQL 8.0 full-grammer, PostgreSQL root/v16/v17/v18, Oracle root/v12c/v19c/v21c/v26ai | 2 | 0 | `MATCHED` |
| `ddl-fk-index` | common, MySQL token-event, MySQL 8.0 full-grammer, PostgreSQL root/v16/v17/v18, Oracle root/v12c/v19c/v21c/v26ai | 1 | 0 | `MATCHED` |
| `inventory-posting` | common, MySQL token-event, MySQL 8.0 full-grammer, PostgreSQL root/v16/v17/v18, Oracle root/v12c/v19c/v21c/v26ai | 2 | 6 | `MATCHED` |
| `mrp-run` | common, MySQL token-event, MySQL 8.0 full-grammer, PostgreSQL root/v16/v17/v18, Oracle root/v12c/v19c/v21c/v26ai | 2 | 5 | `MATCHED` |
| `picking-task` | common, MySQL token-event, MySQL 8.0 full-grammer, PostgreSQL root/v16/v17/v18, Oracle root/v12c/v19c/v21c/v26ai | 3 | 6 | `MATCHED` |
| `return-refund` | common, MySQL token-event, MySQL 8.0 full-grammer, PostgreSQL root/v16/v17/v18, Oracle root/v12c/v19c/v21c/v26ai | 2 | 6 | `MATCHED` |

## Interpretation

- `MATCHED` means every listed parser category has a correctness fixture whose
  expected relationship and lineage fingerprints exactly match the scenario
  canonical files under `test-fixtures/semantic-equivalent`.
- The benchmark intentionally compares final physical endpoints. CTEs, derived
  tables, temporary tables, and intermediate aliases should not become canonical
  endpoints when the parser can resolve them to physical tables.
- The first seven scenarios did not expose confirmed parser gaps. Future
  scenarios should use the same workflow: add equivalent SQL first, run
  correctness, compare against canonical, then fix typed grammar / visitor /
  expression analyzer for confirmed gaps.

## Validation Commands

```bash
mvn -pl cli -am \
  -Dtest=SemanticEquivalentCorrectnessTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -pl cli -am \
  -Dtest=CorrectnessFixtureRunnerTest \
  -DcorrectnessFixtureFilter=semantic-equivalent \
  -Dsurefire.failIfNoSpecifiedTests=false test
```
