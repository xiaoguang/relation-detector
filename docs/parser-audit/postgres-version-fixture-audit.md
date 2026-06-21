# PostgreSQL Version Fixture Audit

This audit records the version ownership of the existing PostgreSQL correctness fixtures after adding `postgres/v17` and `postgres/v18` versioned fixture sets.

## Summary

- Existing root PostgreSQL fixtures scanned: 55
- Root baseline changes: none; no fixture was moved out of `test-fixtures/correctness/postgres`.
- Versioned copies added: 55 under `postgres/v17`, 55 under `postgres/v18`.
- Version-exclusive additions: 2 PostgreSQL 17 fixtures and 3 PostgreSQL 18 fixtures.
- Review-needed root fixtures: none found in this pass.

## Classification Rules

- `VERSION_NEUTRAL`: SQL/DDL is valid baseline syntax and can be copied into versioned directories.
- `PG16_BASELINE`: fixture intentionally represents the current root PostgreSQL baseline profile.
- `PG17_ONLY`: fixture uses PostgreSQL 17-only syntax and should live under `postgres/v17`.
- `PG18_ONLY`: fixture uses PostgreSQL 18-only syntax and should live under `postgres/v18`.
- `REVIEW_NEEDED`: syntax ownership is unclear and should not be moved automatically.

## Existing Root Fixtures

| Fixture | Target | Source type | Classification | Notes |
| --- | --- | --- | --- | --- |
| `postgres-ddl-alter-table-fk` | `DDL` | `DDL_FILE` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-ddl-partial-index-boundary` | `DDL` | `DDL_FILE` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-ddl-unique-include-index` | `DDL` | `DDL_FILE` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-generated-comprehensive-query-sql` | `SQL` | `PLAIN_SQL` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-generated-industrial-complex-sql` | `SQL` | `PLAIN_SQL` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-generated-provided-complex-sql` | `SQL` | `PLAIN_SQL` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-basic-correctness-case-01-ddl` | `DDL` | `DDL_FILE` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-basic-correctness-case-01-objects-sql` | `SQL` | `PLAIN_SQL` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-basic-correctness-case-01-statements-sql` | `SQL` | `NATIVE_LOG` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-business-account-balances-financial-cte-sql` | `SQL` | `PLAIN_SQL` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-business-account-balances-financial-explicit-join-sql` | `SQL` | `PLAIN_SQL` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-business-asset-balances-update-outer-join-sql` | `SQL` | `PLAIN_SQL` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-business-cross-border-reconciliation-function-sql` | `SQL` | `FUNCTION` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-business-delete-cascade-cte-sql` | `SQL` | `PLAIN_SQL` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-business-delete-orphan-left-join-sql` | `SQL` | `PLAIN_SQL` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-business-delete-orphan-not-exists-sql` | `SQL` | `PLAIN_SQL` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-business-inventory-purge-deep-subquery-sql` | `SQL` | `PLAIN_SQL` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-business-inventory-purge-exists-equivalent-sql` | `SQL` | `PLAIN_SQL` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-business-risk-ledger-update-cte-comma-sql` | `SQL` | `PLAIN_SQL` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-business-risk-ledger-update-cte-explicit-join-sql` | `SQL` | `PLAIN_SQL` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-business-risk-settlement-function-comma-sql` | `SQL` | `FUNCTION` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-business-risk-settlement-function-sql` | `SQL` | `FUNCTION` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-business-update-inventory-comma-join-sql` | `SQL` | `PLAIN_SQL` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-business-update-inventory-from-join-sql` | `SQL` | `PLAIN_SQL` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-business-update-products-comma-join-sql` | `SQL` | `PLAIN_SQL` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-business-update-products-from-join-sql` | `SQL` | `PLAIN_SQL` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-business-update-users-aggregate-sql` | `SQL` | `PLAIN_SQL` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-business-update-users-scalar-subquery-sql` | `SQL` | `PLAIN_SQL` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-business-update-warehouse-comma-subquery-sql` | `SQL` | `PLAIN_SQL` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-business-update-warehouse-complex-sql` | `SQL` | `PLAIN_SQL` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-business-user-coupons-delete-derived-join-sql` | `SQL` | `PLAIN_SQL` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-business-user-coupons-delete-exists-sql` | `SQL` | `PLAIN_SQL` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-official-alter-index-boundary-ddl` | `DDL` | `DDL_FILE` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-official-cte-dml-sql` | `SQL` | `PLAIN_SQL` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-official-cte-nested-sql` | `SQL` | `PLAIN_SQL` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-official-expression-access-method-index-ddl` | `DDL` | `DDL_FILE` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-official-index-include-partial-ddl` | `DDL` | `DDL_FILE` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-official-index-opclass-expression-ddl` | `DDL` | `DDL_FILE` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-official-index-options-ddl` | `DDL` | `DDL_FILE` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-official-index-partition-boundary-ddl` | `DDL` | `DDL_FILE` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-official-index-storage-ddl` | `DDL` | `DDL_FILE` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-official-join-edge-sql` | `SQL` | `PLAIN_SQL` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-official-lateral-function-sql` | `SQL` | `PLAIN_SQL` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-official-lateral-nested-join-sql` | `SQL` | `PLAIN_SQL` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-official-multiway-join-sql` | `SQL` | `PLAIN_SQL` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-official-subquery-deep-sql` | `SQL` | `PLAIN_SQL` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-official-subquery-edge-sql` | `SQL` | `PLAIN_SQL` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-sql-delete-using-no-alias` | `SQL` | `PLAIN_SQL` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-sql-lateral-derived` | `SQL` | `PLAIN_SQL` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-sql-merge-using` | `SQL` | `PLAIN_SQL` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-sql-multi-layer-cte` | `SQL` | `PLAIN_SQL` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-sql-quoted-mixed-alias` | `SQL` | `PLAIN_SQL` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-sql-recursive-cte` | `SQL` | `PLAIN_SQL` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-sql-unnest-ordinality` | `SQL` | `PLAIN_SQL` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |
| `postgres-sql-update-from-aliases` | `SQL` | `PLAIN_SQL` | `VERSION_NEUTRAL` | `Compatible baseline fixture; retained in root postgres correctness set.` |

## PostgreSQL 17 Version-Only Fixtures

| Fixture | Purpose |
| --- | --- |
| `postgres17-json-table-sql` | SQL/JSON `JSON_TABLE()` rowset; verifies JSON_TABLE alias is not a physical business table. |
| `postgres17-merge-returning-sql` | `MERGE ... WHEN NOT MATCHED BY SOURCE` and `RETURNING merge_action()`; verifies source/target relationships and UPDATE lineage. |

## PostgreSQL 18 Version-Only Fixtures

| Fixture | Purpose |
| --- | --- |
| `postgres18-returning-old-new-sql` | `RETURNING old/new`; verifies pseudo rows are not physical tables and UPDATE lineage remains physical-column only. |
| `postgres18-temporal-constraints-ddl` | Temporal `WITHOUT OVERLAPS` and `PERIOD` FK; verifies only ordinary FK columns become relationships. |
| `postgres18-virtual-generated-ddl` | Virtual generated column DDL parse coverage. |

