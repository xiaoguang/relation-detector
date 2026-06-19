# PostgreSQL ANTLR Extra Relations Audit

This report lists PostgreSQL relations that ANTLR identifies beyond the legacy Simple parser baseline.

The entries below have been manually reviewed and accepted as ANTLR gold. Simple remains useful for diagnostics, but it no longer vetoes these relationships.

## SQL Fixtures

| Fixture | Input Location | ANTLR-only relation fingerprint | Evidence | Source type | Judgment |
|---|---|---|---|---|---|
| `postgres-official-multiway-join-sql` | `test-fixtures/correctness/postgres/postgres-official-multiway-join-sql/input.sql` | `FK_LIKE:orders.user_id->users.id:SQL_LOG_JOIN` | `SQL_LOG_JOIN` | `PLAIN_SQL` | `ACCEPTED_ANTLR_GOLD` |
| `postgres-official-multiway-join-sql` | `test-fixtures/correctness/postgres/postgres-official-multiway-join-sql/input.sql` | `FK_LIKE:orders.tenant_id->tenants.id:SQL_LOG_JOIN` | `SQL_LOG_JOIN` | `PLAIN_SQL` | `ACCEPTED_ANTLR_GOLD` |

Review note: `orders.tenant_id -> tenants.id` comes from the mixed comma/explicit join predicate `o.tenant_id = t.id` in the multiway join fixture. The shadow comparison also reports `orders.user_id -> users.id` as ANTLR-only for one statement-level comparison even though the aggregate fixture baseline already contains that same fingerprint from another statement.

## DDL Fixtures

No PostgreSQL ANTLR-only DDL relations are currently recorded in the official fixture batch.
