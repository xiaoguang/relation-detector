# PostgreSQL Translation Review for MySQL `proc_` Procedures

This audit covers the 12 MySQL `proc_` object blocks from `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` and their PostgreSQL translations in `test-fixtures/postgres/basic-correctness/case-01/sql/mysql-proc-translated-procedures.sql`.

## Scope

- PostgreSQL root fixtures use `parserMode: token-event`.
- PostgreSQL `v16`, `v17`, and `v18` fixtures use `parserMode: full-grammar` with the matching `postgresql/<major>` profile.
- Support DDL is translated from the complete MySQL `show-create-tables.sql` into `mysql-proc-support-tables.sql`.
- Parameters, local variables, local temporary tables, dynamic SQL, and pseudo rowsets are not physical relationship or lineage endpoints.

## Procedure Fingerprint Comparison

| Procedure | MySQL v8_0 relation | MySQL v8_0 lineage | PostgreSQL root | PostgreSQL v16 | PostgreSQL v17 | PostgreSQL v18 | Result |
| --- | ---: | ---: | --- | --- | --- | --- | --- |
| `proc_batch_call_generate_po` | 0 | 0 | match | match | match | match | MATCHED |
| `proc_batch_generate_purchase_inbound` | 0 | 0 | match | match | match | match | MATCHED |
| `proc_batch_insert_purchase_requisition` | 0 | 0 | match | match | match | match | MATCHED |
| `proc_batch_mock_retail_orders` | 0 | 0 | match | match | match | match | MATCHED |
| `proc_create_order_mock_retail` | 4 | 1 | match | match | match | match | MATCHED |
| `proc_generate_purchase_inbound_from_order` | 5 | 25 | match | match | match | match | MATCHED |
| `proc_generate_purchase_order_from_requisition` | 2 | 21 | match | match | match | match | MATCHED |
| `proc_init_yearly_weights` | 0 | 0 | match | match | match | match | MATCHED |
| `proc_insert_purchase_requisition` | 2 | 0 | match | match | match | match | MATCHED |
| `proc_refresh_org_pdf` | 2 | 5 | match | match | match | match | MATCHED |
| `proc_simulate_yearly_sales` | 0 | 0 | match | match | match | match | MATCHED |
| `proc_worker_daily_distribution` | 1 | 2 | relation match; lineage count match | relation match; lineage count match | relation match; lineage count match | relation match; lineage count match | EXPLAINED_DELTA |

## Explained Delta

### `proc_worker_daily_distribution`

PostgreSQL translation SQL context:

```sql
INSERT INTO jsh_temp_mock_plan (user_id, mock_timestamp_str)
SELECT
    our.user_id,
    hp.hour_val + SUM(hp.weight) OVER (ORDER BY hp.hour_val)
FROM jsh_orga_user_rel our
JOIN jsh_temp_org_pdf pdf ON our.orga_id = pdf.org_id
JOIN jsh_temp_hour_pdf hp ON hp.hour_val >= 0
WHERE our.delete_flag = '0';
```

MySQL `v8_0` lineage for `user_id` is:

```text
VALUE:DIRECT:jsh_orga_user_rel.user_id,jsh_orga_user_rel.orga_id,jsh_temp_org_pdf.org_id,jsh_orga_user_rel.delete_flag->jsh_temp_mock_plan.user_id
```

PostgreSQL lineage is:

```text
VALUE:DIRECT:jsh_orga_user_rel.user_id->jsh_temp_mock_plan.user_id
```

Judgement: PostgreSQL output is the narrower physical assignment lineage. `orga_id`, `org_id`, and `delete_flag` participate in join/filter predicates, not in the value assigned to `jsh_temp_mock_plan.user_id`. They remain represented through relationship evidence where applicable. The translation therefore keeps the typed visitor output instead of adding a special rule to reproduce the broader MySQL source set.

The cumulative timestamp lineage is matched after adding typed `windowClause` support to `PostgresRelationSql.g4`:

```text
VALUE:CUMULATIVE:jsh_temp_hour_pdf.hour_val,jsh_temp_hour_pdf.weight->jsh_temp_mock_plan.mock_timestamp_str
```

## Parser Changes Made

- Added a typed PostgreSQL token-event `windowClause` rule for `functionCall OVER (...)`.
- Updated `PostgresTokenEventParseTreeVisitor` to classify `SUM(...) OVER (...)` as `CUMULATIVE`.
- No regex, token-span scanner, variable-name filter, table-name whitelist, or keyword blacklist was introduced.

## Review Status

No `REVIEW_NEEDED` item remains. The only fingerprint delta is documented above as `EXPLAINED_DELTA` and is intentionally not forced into equivalence because doing so would widen physical assignment lineage beyond the PostgreSQL expression structure.
