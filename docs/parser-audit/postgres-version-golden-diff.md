# PostgreSQL Versioned Golden Diff

This report compares strict PostgreSQL full-grammer correctness directories:

- `test-fixtures/correctness/postgres/v16`
- `test-fixtures/correctness/postgres/v17`
- `test-fixtures/correctness/postgres/v18`

The root `test-fixtures/correctness/postgres` directory remains the historical
baseline and is not treated as a strict version grammar proof.

## Summary

| Category | Result | Classification |
| --- | --- | --- |
| Common fixture count | 55 fixtures in v16/v17/v18 | MATCHED |
| Relation differences on common fixtures | 0 | MATCHED |
| Data Lineage differences on common fixtures | 0 | MATCHED |
| Diagnostic differences on common fixtures | 0 | MATCHED |
| v17-only fixtures absent from v16 | 2 | EXPECTED_VERSION_GAP |
| v18-only fixtures absent from v16/v17 | 3 | EXPECTED_VERSION_GAP |

## v17-only Fixtures

These are expected to be absent from PostgreSQL 16 strict full-grammer golden.

| Fixture | Covered syntax | Expected lower-version classification |
| --- | --- | --- |
| `postgres17-json-table-sql` | SQL/JSON `JSON_TABLE()` rowset and `JSON_EXISTS()` | EXPECTED_VERSION_GAP |
| `postgres17-merge-returning-sql` | `MERGE ... WHEN NOT MATCHED BY SOURCE/TARGET` and `RETURNING merge_action()` | EXPECTED_VERSION_GAP |

## v18-only Fixtures

These are expected to be absent from PostgreSQL 16 and PostgreSQL 17 strict
full-grammer golden.

| Fixture | Covered syntax | Expected lower-version classification |
| --- | --- | --- |
| `postgres18-returning-old-new-sql` | DML `RETURNING old/new` pseudo row references | EXPECTED_VERSION_GAP |
| `postgres18-temporal-constraints-ddl` | `WITHOUT OVERLAPS` and `PERIOD` temporal FK columns | EXPECTED_VERSION_GAP |
| `postgres18-virtual-generated-ddl` | Virtual generated columns | EXPECTED_VERSION_GAP |

## Review Queue

No `GRAMMAR_GAP`, `SEMANTIC_GAP`, or `REVIEW_NEEDED` items are present in the
current comparison.

If a future comparison shows that a lower PostgreSQL profile misses a relation,
evidence, or lineage item from the latest version on a syntax that should exist
in that lower version, classify it as `GRAMMAR_GAP` or `SEMANTIC_GAP` instead
of accepting the missing item as a version gap.
