# NOT_APPLICABLE Data Lineage Review

This review records the manual audit for `NOT_APPLICABLE` entries in
`data-lineage-full-audit.md`.

## Current Counts

After the fixes in this review:

| Classification | Count |
| --- | ---: |
| TOTAL | 763 |
| EXISTING_GOLD | 211 |
| SUGGESTED_GOLD | 0 |
| PENDING_REVIEW | 0 |
| NOT_APPLICABLE | 552 |

## Confirmed False NOT_APPLICABLE Fixed

These fixtures were previously classified as `NOT_APPLICABLE` only because the
typed parser path did not enter a statement shape that is valid for the target
dialect. They now have lineage golden.

| Fixture | Root cause | Fix | Added lineage |
| --- | --- | --- | ---: |
| `common-sample-data-full-02-processes-01-procedures-sql` | Common token-event did not enter portable `CREATE PROCEDURE ... BEGIN ATOMIC ... END` wrappers. | Added typed routine/block wrapper rules to `CommonRelationSql.g4`. | 67 |
| `mysql-official-cte-dml-sql` | MySQL token-event did not parse `WITH ... UPDATE ... JOIN ... SET`. | Added `withClause?` to MySQL typed update rule and visited CTEs before update body. | 1 |
| `postgres-business-risk-ledger-update-cte-comma-sql` | PostgreSQL token-event did not parse top-level `WITH ... UPDATE ... FROM`; window sources were not analyzed. | Added typed `WITH UPDATE`, `INTERVAL`, `::cast`, and window-source analysis. | 1 |
| `postgres-business-risk-ledger-update-cte-explicit-join-sql` | Same as above. | Same as above. | 1 |
| `postgres-business-account-balances-financial-cte-sql` | PostgreSQL token-event did not parse top-level `WITH ... UPDATE ... FROM`, `::cast`, `EXTRACT`, array predicate, and window sources. | Added typed PostgreSQL expression rules and window-source analysis. | 3 |
| `postgres-business-account-balances-financial-explicit-join-sql` | Same as above. | Same as above. | 3 |
| `postgres-official-cte-dml-sql` | PostgreSQL token-event did not parse `WITH ... MERGE`. | Added `withClause?` to PostgreSQL typed merge rule and visited CTEs before merge body. | 1 |

## Remaining Confirmed Coverage Gaps

These are not true "no physical source" cases. They remain `NOT_APPLICABLE` in
the generated report because the current parser cannot yet emit stable
candidates for the full statement shape. They should not be treated as business
semantic rejections.

| Fixture | Why it is not truly NOT_APPLICABLE | Recommended follow-up |
| --- | --- | --- |
| `mysql-business-financial-asset-wash-procedure-sql` | The equivalent comma-rowset fixture and MySQL `v8_0` full-grammar fixture already have physical lineage from `transaction_ledgers`, `users`, and `account_balances`. The original `UPDATE ... INNER JOIN (WITH ...) AS ... , gcp SET ...` form still produces no candidates. | Fix MySQL token-event for this combined UPDATE JOIN + derived CTE + comma rowset shape, then align golden with `mysql-business-financial-asset-wash-procedure-comma-sql`. |
| `postgres-pg15-sql` | PostgreSQL v16/v17/v18 full-grammar already extracts MERGE lineage from the same PG15 SQL. Root token-event does not cover the full high-version MERGE/JSON/derived CTE syntax. | Extend PostgreSQL token-event only if root fallback is expected to cover this high-version syntax; otherwise document as token-event coverage backlog. |
| `postgres-pg17-sql` | PostgreSQL v17/v18 full-grammar already extracts MERGE lineage from the same PG17 SQL. Root token-event does not cover JSON_TABLE/MERGE RETURNING shape. | Same as above; this is high-version PostgreSQL token-event coverage, not a semantic "not applicable" case. |

## Confirmed True NOT_APPLICABLE Categories

The remaining bulk of `NOT_APPLICABLE` entries is expected under Data Lineage v1:

| Category | Current count | Reason |
| --- | ---: | --- |
| SELECT-only / query-only SQL | 273 | No target column write exists. |
| DDL | 147 | DDL contributes relationship/index evidence, not value lineage. |
| DELETE-only SQL | 43 | DELETE does not write target column values. |
| Local temporary table source only | 16 | Procedure-local temporary rowsets are intentionally excluded from physical lineage. |
| Version-boundary negative fixture | 1 | Unsupported SQL exists only to test version rejection. |

The remaining `write statement has no physical table.column source` entries are
mostly seed `INSERT VALUES`, parameter/local-variable procedure writes, and
dialect/version demo statements. They can contain write keywords, but their
source side is literal, parameter, local variable, dynamic SQL, or unsupported
high-version syntax rather than a stable physical `table.column` source.
