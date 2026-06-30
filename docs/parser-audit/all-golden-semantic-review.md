# All Golden SQL Semantic Review

This review covers every correctness fixture manifest under `test-fixtures/correctness`. It records the current semantic golden state, parser-family differences, and review status. It is intentionally evidence-grounded: parser counts are not treated as truth unless the SQL context supports the relationship or lineage.

## Scope And Status

- Fixture manifests reviewed: `707`.
- SQL/DDL inputs are the fixture `input.sql` files referenced by each manifest.
- Current review result: no `REVIEW_NEEDED` items are open.
- Confirmed recent fix: `NAMING_MATCH` is now generated as a direction hint on existing SQL predicate candidates. It supports `TABLE_ID`, `ID_SUFFIX_TO_ID`, and `SELF_ROLE_ID`; it cannot create a relationship by itself.
- Confirmed recent lineage fix: token-event CASE analysis now includes columns from `WHEN` predicates as `CONTROL:CASE_WHEN` lineage sources when they are physical rowset columns.
- Confirmed recent MySQL token-event grammar fix: `IS NULL` / `IS NOT NULL` predicates are now typed grammar nodes. This lets MySQL token-event parse the rest of JOIN predicates instead of dropping later joined rowsets, which restored semantically valid sample-data procedure/view/trigger relations and derived aggregate lineage.
- Confirmed recent fixture fix: new ERP deep-scenario procedure files now carry object-block markers, so routine bodies are actually included in correctness parsing.
- Remaining cross-parser differences are categorized as typed visitor coverage gaps or expected version deltas, not as approved scanner-style guessing.

## Parser Category Totals

| Category | Fixtures | Relations | Lineage | Naming relation fingerprints | Sample fixtures | Sample relations | Sample lineage |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| common token-event | 36 | 705 | 189 | 195 | 19 | 689 | 183 |
| MySQL token-event root | 97 | 597 | 240 | 242 | 40 | 522 | 183 |
| MySQL full-grammer v8_0 | 97 | 873 | 327 | 434 | 40 | 749 | 232 |
| PostgreSQL token-event root | 118 | 1090 | 176 | 241 | 39 | 409 | 101 |
| PostgreSQL full-grammer v16 | 118 | 1422 | 192 | 395 | 39 | 641 | 101 |
| PostgreSQL full-grammer v17 | 120 | 1425 | 214 | 396 | 39 | 641 | 101 |
| PostgreSQL full-grammer v18 | 121 | 1427 | 213 | 395 | 39 | 641 | 101 |

## Golden Semantic Shape

### NAMING_MATCH Direction Evidence

`NAMING_MATCH` is a direction evidence enhancer, not a relationship source. The current golden contains it only when all of these are true:

- a typed parser already produced a column-level SQL predicate candidate;
- the endpoints are physical table columns, not parameters, local variables, temporary tables, literals, `NEW/OLD`, `EXCLUDED`, or pseudo rowsets;
- exactly one naming rule gives a direction:
  - `TABLE_ID`: `orders.customer_id -> customers.id`;
  - `ID_SUFFIX_TO_ID`: one endpoint is `*_id`, the other is `id`;
  - `SELF_ROLE_ID`: same physical table, different SQL aliases, role column such as `manager_id` points to the role target `id`;
- ambiguous name matches do not produce `NAMING_MATCH`.

This means the naming heuristic can promote an existing SQL predicate `CO_OCCURRENCE` to `FK_LIKE`, but it never creates a relation from table or column names alone.

### Relationship Fingerprint Types

| Type | Count |
| --- | ---: |
| `FK_LIKE` | 6514 |
| `CO_OCCURRENCE` | 1025 |

### Lineage Fingerprint Types

| Flow/Transform | Count |
| --- | ---: |
| `VALUE:DIRECT` | 909 |
| `VALUE:ARITHMETIC` | 198 |
| `VALUE:AGGREGATE` | 141 |
| `VALUE:COALESCE` | 94 |
| `VALUE:CONCAT_FORMAT` | 82 |
| `CONTROL:CASE_WHEN` | 79 |
| `VALUE:FUNCTION_CALL` | 30 |
| `CONTROL:AGGREGATE` | 8 |
| `VALUE:CUMULATIVE` | 10 |

## Cross-Parser Difference Classification

| Classification | Groups | Right-side extra relations | Right-side extra lineage | Meaning |
| --- | ---: | ---: | ---: | --- |
| `EXPECTED_VERSION_DELTA` | 3 | 2 | 31 | The difference follows PostgreSQL major-version syntax/capability boundaries. |
| `PARSER_GAP_ROUTINE_OR_COMPLEX_QUERY` | 55 | 606 | 192 | The gap is concentrated in routines, generated data SQL, triggers, or complex business queries. |
| `PARSER_GAP_TYPED_VISITOR_COVERAGE` | 154 | 1150 | 98 | A stronger parser has semantically valid facts that root token-event does not yet produce from typed contexts. |

## High-Impact Follow-Up Backlog

| Fixture | Pair | Classification | Missing relations | Missing lineage | Suggested handling |
| --- | --- | --- | ---: | ---: | --- |
| `sample-data-full-01-schema-01-tables-ddl` | PostgreSQL token-event root -> PostgreSQL full-grammer v18 | `PARSER_GAP_TYPED_VISITOR_COVERAGE` | 87 | 0 | Extend typed visitor/grammar; do not restore scanner. |
| `sample-data-full-01-schema-01-tables-ddl` | PostgreSQL token-event root -> PostgreSQL full-grammer v17 | `PARSER_GAP_TYPED_VISITOR_COVERAGE` | 87 | 0 | Extend typed visitor/grammar; do not restore scanner. |
| `sample-data-full-01-schema-01-tables-ddl` | PostgreSQL token-event root -> PostgreSQL full-grammer v16 | `PARSER_GAP_TYPED_VISITOR_COVERAGE` | 87 | 0 | Extend typed visitor/grammar; do not restore scanner. |
| `basic-correctness-case-01-ddl` | PostgreSQL token-event root -> PostgreSQL full-grammer v18 | `PARSER_GAP_TYPED_VISITOR_COVERAGE` | 56 | 0 | Extend typed visitor/grammar; do not restore scanner. |
| `basic-correctness-case-01-ddl` | PostgreSQL token-event root -> PostgreSQL full-grammer v17 | `PARSER_GAP_TYPED_VISITOR_COVERAGE` | 56 | 0 | Extend typed visitor/grammar; do not restore scanner. |
| `basic-correctness-case-01-ddl` | PostgreSQL token-event root -> PostgreSQL full-grammer v16 | `PARSER_GAP_TYPED_VISITOR_COVERAGE` | 56 | 0 | Extend typed visitor/grammar; do not restore scanner. |
| `sample-data-real-world-scenarios-sql` | PostgreSQL token-event root -> PostgreSQL full-grammer v18 | `PARSER_GAP_TYPED_VISITOR_COVERAGE` | 53 | 0 | Extend typed visitor/grammar; do not restore scanner. |
| `sample-data-real-world-scenarios-sql` | PostgreSQL token-event root -> PostgreSQL full-grammer v17 | `PARSER_GAP_TYPED_VISITOR_COVERAGE` | 53 | 0 | Extend typed visitor/grammar; do not restore scanner. |
| `sample-data-real-world-scenarios-sql` | PostgreSQL token-event root -> PostgreSQL full-grammer v16 | `PARSER_GAP_TYPED_VISITOR_COVERAGE` | 53 | 0 | Extend typed visitor/grammar; do not restore scanner. |
| `sample-data-real-world-scenarios-sql` | MySQL token-event root -> MySQL full-grammer v8_0 | `PARSER_GAP_TYPED_VISITOR_COVERAGE` | 53 | 0 | Extend typed visitor/grammar; do not restore scanner. |
| `sample-data-full-03-data-02-supplementary-data-sql` | MySQL token-event root -> MySQL full-grammer v8_0 | `PARSER_GAP_ROUTINE_OR_COMPLEX_QUERY` | 4 | 49 | Extend typed visitor/grammar; do not restore scanner. |
| `sample-data-full-01-schema-01-tables-ddl` | MySQL token-event root -> MySQL full-grammer v8_0 | `PARSER_GAP_TYPED_VISITOR_COVERAGE` | 47 | 0 | Extend typed visitor/grammar; do not restore scanner. |
| `sample-data-full-03-data-03-third-batch-data-sql` | MySQL token-event root -> MySQL full-grammer v8_0 | `PARSER_GAP_ROUTINE_OR_COMPLEX_QUERY` | 2 | 33 | Extend typed visitor/grammar; do not restore scanner. |
| `basic-correctness-case-01-procedure-sp-fill-biz-bill-item-fact-new-with-progress-sql` | MySQL token-event root -> MySQL full-grammer v8_0 | `PARSER_GAP_ROUTINE_OR_COMPLEX_QUERY` | 7 | 22 | Extend typed visitor/grammar; do not restore scanner. |
| `sample-data-full-04-queries-01-complex-queries-sql` | PostgreSQL token-event root -> PostgreSQL full-grammer v18 | `PARSER_GAP_ROUTINE_OR_COMPLEX_QUERY` | 28 | 0 | Extend typed visitor/grammar; do not restore scanner. |
| `sample-data-full-04-queries-01-complex-queries-sql` | PostgreSQL token-event root -> PostgreSQL full-grammer v17 | `PARSER_GAP_ROUTINE_OR_COMPLEX_QUERY` | 28 | 0 | Extend typed visitor/grammar; do not restore scanner. |
| `sample-data-full-04-queries-01-complex-queries-sql` | PostgreSQL token-event root -> PostgreSQL full-grammer v16 | `PARSER_GAP_ROUTINE_OR_COMPLEX_QUERY` | 28 | 0 | Extend typed visitor/grammar; do not restore scanner. |
| `sample-data-full-04-queries-01-complex-queries-sql` | MySQL token-event root -> MySQL full-grammer v8_0 | `PARSER_GAP_ROUTINE_OR_COMPLEX_QUERY` | 28 | 0 | Extend typed visitor/grammar; do not restore scanner. |
| `sample-data-full-03-data-05-massive-data-generator-sql` | MySQL token-event root -> MySQL full-grammer v8_0 | `PARSER_GAP_ROUTINE_OR_COMPLEX_QUERY` | 1 | 24 | Extend typed visitor/grammar; do not restore scanner. |
| `sample-data-full-04-queries-08-common-system-queries-sql` | PostgreSQL token-event root -> PostgreSQL full-grammer v18 | `PARSER_GAP_ROUTINE_OR_COMPLEX_QUERY` | 24 | 0 | Extend typed visitor/grammar; do not restore scanner. |
| `sample-data-full-04-queries-08-common-system-queries-sql` | PostgreSQL token-event root -> PostgreSQL full-grammer v17 | `PARSER_GAP_ROUTINE_OR_COMPLEX_QUERY` | 24 | 0 | Extend typed visitor/grammar; do not restore scanner. |
| `sample-data-full-04-queries-08-common-system-queries-sql` | PostgreSQL token-event root -> PostgreSQL full-grammer v16 | `PARSER_GAP_ROUTINE_OR_COMPLEX_QUERY` | 24 | 0 | Extend typed visitor/grammar; do not restore scanner. |
| `sample-data-full-04-queries-08-common-system-queries-sql` | MySQL token-event root -> MySQL full-grammer v8_0 | `PARSER_GAP_ROUTINE_OR_COMPLEX_QUERY` | 24 | 0 | Extend typed visitor/grammar; do not restore scanner. |
| `sample-data-full-03-data-04-return-damage-data-sql` | MySQL token-event root -> MySQL full-grammer v8_0 | `PARSER_GAP_ROUTINE_OR_COMPLEX_QUERY` | 2 | 20 | Extend typed visitor/grammar; do not restore scanner. |
| `sample-data-full-04-queries-02-complex-queries-batch2-sql` | MySQL token-event root -> MySQL full-grammer v8_0 | `PARSER_GAP_ROUTINE_OR_COMPLEX_QUERY` | 20 | 0 | Extend typed visitor/grammar; do not restore scanner. |
| `sample-data-full-04-queries-03-complex-queries-batch3-sql` | PostgreSQL token-event root -> PostgreSQL full-grammer v18 | `PARSER_GAP_ROUTINE_OR_COMPLEX_QUERY` | 19 | 0 | Extend typed visitor/grammar; do not restore scanner. |
| `sample-data-full-04-queries-03-complex-queries-batch3-sql` | PostgreSQL token-event root -> PostgreSQL full-grammer v17 | `PARSER_GAP_ROUTINE_OR_COMPLEX_QUERY` | 19 | 0 | Extend typed visitor/grammar; do not restore scanner. |
| `sample-data-full-04-queries-03-complex-queries-batch3-sql` | PostgreSQL token-event root -> PostgreSQL full-grammer v16 | `PARSER_GAP_ROUTINE_OR_COMPLEX_QUERY` | 19 | 0 | Extend typed visitor/grammar; do not restore scanner. |
| `sample-data-full-04-queries-03-complex-queries-batch3-sql` | MySQL token-event root -> MySQL full-grammer v8_0 | `PARSER_GAP_ROUTINE_OR_COMPLEX_QUERY` | 19 | 0 | Extend typed visitor/grammar; do not restore scanner. |
| `sample-data-full-04-queries-02-complex-queries-batch2-sql` | PostgreSQL token-event root -> PostgreSQL full-grammer v18 | `PARSER_GAP_ROUTINE_OR_COMPLEX_QUERY` | 18 | 0 | Extend typed visitor/grammar; do not restore scanner. |
| `sample-data-full-04-queries-02-complex-queries-batch2-sql` | PostgreSQL token-event root -> PostgreSQL full-grammer v17 | `PARSER_GAP_ROUTINE_OR_COMPLEX_QUERY` | 18 | 0 | Extend typed visitor/grammar; do not restore scanner. |
| `sample-data-full-04-queries-02-complex-queries-batch2-sql` | PostgreSQL token-event root -> PostgreSQL full-grammer v16 | `PARSER_GAP_ROUTINE_OR_COMPLEX_QUERY` | 18 | 0 | Extend typed visitor/grammar; do not restore scanner. |
| `pg15-sql` | PostgreSQL token-event root -> PostgreSQL full-grammer v18 | `PARSER_GAP_TYPED_VISITOR_COVERAGE` | 2 | 16 | Extend typed visitor/grammar; do not restore scanner. |
| `pg15-sql` | PostgreSQL token-event root -> PostgreSQL full-grammer v17 | `PARSER_GAP_TYPED_VISITOR_COVERAGE` | 2 | 16 | Extend typed visitor/grammar; do not restore scanner. |
| `sample-data-full-02-procedures-09-return-refund-procedures-sql` | MySQL token-event root -> MySQL full-grammer v8_0 | `PARSER_GAP_ROUTINE_OR_COMPLEX_QUERY` | 15 | 0 | Extend typed visitor/grammar; do not restore scanner. |
| `sample-data-full-04-queries-06-return-damage-queries-sql` | PostgreSQL token-event root -> PostgreSQL full-grammer v18 | `PARSER_GAP_ROUTINE_OR_COMPLEX_QUERY` | 14 | 0 | Extend typed visitor/grammar; do not restore scanner. |
| `sample-data-full-04-queries-06-return-damage-queries-sql` | PostgreSQL token-event root -> PostgreSQL full-grammer v17 | `PARSER_GAP_ROUTINE_OR_COMPLEX_QUERY` | 14 | 0 | Extend typed visitor/grammar; do not restore scanner. |
| `sample-data-full-04-queries-06-return-damage-queries-sql` | PostgreSQL token-event root -> PostgreSQL full-grammer v16 | `PARSER_GAP_ROUTINE_OR_COMPLEX_QUERY` | 14 | 0 | Extend typed visitor/grammar; do not restore scanner. |
| `sample-data-full-04-queries-06-return-damage-queries-sql` | MySQL token-event root -> MySQL full-grammer v8_0 | `PARSER_GAP_ROUTINE_OR_COMPLEX_QUERY` | 14 | 0 | Extend typed visitor/grammar; do not restore scanner. |
| `sample-data-full-02-procedures-11-common-system-procedures-sql` | MySQL token-event root -> MySQL full-grammer v8_0 | `PARSER_GAP_ROUTINE_OR_COMPLEX_QUERY` | 14 | 0 | Extend typed visitor/grammar; do not restore scanner. |
| `generated-comprehensive-query-sql` | PostgreSQL token-event root -> PostgreSQL full-grammer v18 | `PARSER_GAP_TYPED_VISITOR_COVERAGE` | 14 | 0 | Extend typed visitor/grammar; do not restore scanner. |
| `generated-comprehensive-query-sql` | PostgreSQL token-event root -> PostgreSQL full-grammer v17 | `PARSER_GAP_TYPED_VISITOR_COVERAGE` | 14 | 0 | Extend typed visitor/grammar; do not restore scanner. |
| `generated-comprehensive-query-sql` | PostgreSQL token-event root -> PostgreSQL full-grammer v16 | `PARSER_GAP_TYPED_VISITOR_COVERAGE` | 14 | 0 | Extend typed visitor/grammar; do not restore scanner. |
| `basic-correctness-case-01-procedure-proc-generate-purchase-inbound-from-order-sql` | MySQL token-event root -> MySQL full-grammer v8_0 | `PARSER_GAP_ROUTINE_OR_COMPLEX_QUERY` | 3 | 10 | Extend typed visitor/grammar; do not restore scanner. |
| `sample-data-full-02-procedures-10-supplier-geo-procedures-sql` | MySQL token-event root -> MySQL full-grammer v8_0 | `PARSER_GAP_ROUTINE_OR_COMPLEX_QUERY` | 7 | 5 | Extend typed visitor/grammar; do not restore scanner. |
| `sample-data-full-01-schema-03-triggers-sql` | MySQL token-event root -> MySQL full-grammer v8_0 | `PARSER_GAP_ROUTINE_OR_COMPLEX_QUERY` | 6 | 6 | Extend typed visitor/grammar; do not restore scanner. |
| `pg17-sql` | PostgreSQL token-event root -> PostgreSQL full-grammer v18 | `EXPECTED_VERSION_DELTA` | 1 | 11 | Keep as version-specific golden. |
| `pg17-sql` | PostgreSQL token-event root -> PostgreSQL full-grammer v17 | `EXPECTED_VERSION_DELTA` | 1 | 11 | Keep as version-specific golden. |
| `sample-data-full-02-procedures-02-procedures-supplement-sql` | MySQL token-event root -> MySQL full-grammer v8_0 | `PARSER_GAP_ROUTINE_OR_COMPLEX_QUERY` | 11 | 0 | Extend typed visitor/grammar; do not restore scanner. |
| `sample-data-full-04-queries-05-batch-expiry-queries-sql` | PostgreSQL token-event root -> PostgreSQL full-grammer v18 | `PARSER_GAP_ROUTINE_OR_COMPLEX_QUERY` | 10 | 0 | Extend typed visitor/grammar; do not restore scanner. |

## Confirmed Fixes In Current Review

| Fixture | Change | Why it is semantically correct |
| --- | --- | --- |
| `mysqlsample-data-full-02-procedures-01-procedures-sql` | Added `cashier_journals.journal_type` to CASE control lineage for reconciliation debit/credit amounts. | `journal_type IN (...)` controls whether `cj.amount` flows into debit or credit. |
| `postgres-business-asset-balances-update-outer-join-sql` | Added CASE control lineage into `asset_balances.discrepancy_flag`. | `ledger_system_a.balance != ledger_system_b.balance` controls the target flag. |
| `postgres-business-update-warehouse-comma-subquery-sql` | Added CASE control lineage into `warehouse_inventory.last_audit_status`. | `stock_available - quantity < 10` controls the target status. |
| `postgres-business-update-warehouse-complex-sql` | Added CASE control lineage into `warehouse_inventory.last_audit_status`. | Same expression as the comma-subquery equivalent fixture. |
| `mysqlsample-data-full-01-schema-02-indexes-and-views-views-sql` | Added purchase-order view JOIN relations previously skipped after `IS NULL` style predicates in the same grammar path. | The view has explicit column-column joins such as `purchase_orders.supplier_id = suppliers.id`, `purchase_orders.purchaser_id = employees.id`, and `purchase_order_items.order_id = purchase_orders.id`. |
| `mysqlsample-data-full-01-schema-03-triggers-sql` | Added inventory-to-sales-order-item trigger JOIN co-occurrences. | Trigger body joins `inventory` to `sales_order_items` on physical `product_id` and `batch_id` columns. Direction is not uniquely proven, so they remain co-occurrence. |
| `mysqlsample-data-full-02-procedures-02-procedures-supplement-sql` | Added `purchase_orders.supplier_id -> suppliers.id`. | The procedure contains an explicit supplier join predicate. |
| `mysqlsample-data-full-02-procedures-04-procedures-supplement-sql` | Added `commission_rules.product_category_id` / `products.category_id` co-occurrence. | The procedure has a real category join, but neither side is `id`, so direction remains unknown. |
| `mysqlsample-data-full-02-procedures-09-return-refund-procedures-sql` | Added return/refund procedure joins for sales returns, customers, orders, vouchers, warehouses, and employee approval. | These are explicit column-column SQL JOIN predicates. `employees.id = sales_returns.approved_by` remains co-occurrence because naming evidence does not unambiguously prove direction. |
| `mysqlsample-data-full-02-procedures-13-erp-deep-scenario-procedures-sql` | Added derived aggregate and function lineage from MRP, costing, repair, and finance procedures. | Procedure body object markers now include the full routine body, and typed `IS NULL` support preserves derived JOINs, so aggregate aliases such as `inv.on_hand_qty` resolve to physical inventory fields. |

## Fixture Index

| Fixture id | Category | Target | Mode | Profile | Relations | Lineage | Input |
| --- | --- | --- | --- | --- | ---: | ---: | --- |
| `commonsample-data-full-01-schema-01-tables-ddl` | common token-event | DDL | token-event | - | 193 | 0 | `sample-data/portable/01-schema/01-tables.sql` |
| `commonsample-data-full-01-schema-02-views-ddl` | common token-event | DDL | token-event | - | 0 | 0 | `sample-data/portable/01-schema/02-views.sql` |
| `commonsample-data-full-01-schema-02-views-views-sql` | common token-event | SQL | token-event | - | 6 | 0 | `test-fixtures/correctness/common/common-sample-data-full-01-schema-02-views-views-sql/input.sql` |
| `commonsample-data-full-02-processes-01-procedures-sql` | common token-event | SQL | token-event | - | 30 | 0 | `test-fixtures/correctness/common/common-sample-data-full-02-processes-01-procedures-sql/input.sql` |
| `commonsample-data-full-02-processes-02-functions-sql` | common token-event | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/common/common-sample-data-full-02-processes-02-functions-sql/input.sql` |
| `commonsample-data-full-02-processes-03-triggers-sql` | common token-event | SQL | token-event | - | 0 | 1 | `test-fixtures/correctness/common/common-sample-data-full-02-processes-03-triggers-sql/input.sql` |
| `commonsample-data-full-02-processes-04-process-bodies-for-golden-sql` | common token-event | SQL | token-event | - | 33 | 67 | `test-fixtures/correctness/common/common-sample-data-full-02-processes-04-process-bodies-for-golden-sql/input.sql` |
| `commonsample-data-full-03-data-01-master-data-sql` | common token-event | SQL | token-event | - | 0 | 0 | `sample-data/portable/03-data/01-master-data.sql` |
| `commonsample-data-full-04-queries-01-business-queries-sql` | common token-event | SQL | token-event | - | 16 | 3 | `sample-data/portable/04-queries/01-business-queries.sql` |
| `common-sample-data-portable-data-sql` | common token-event | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/common/sample-data-portable-data-sql/input.sql` |
| `common-sample-data-portable-ddl` | common token-event | DDL | token-event | - | 193 | 0 | `test-fixtures/correctness/common/sample-data-portable-ddl/input.ddl.sql` |
| `common-sample-data-portable-lineage-sql` | common token-event | SQL | token-event | - | 16 | 3 | `test-fixtures/correctness/common/sample-data-portable-lineage-sql/input.sql` |
| `common-sample-data-portable-process-sql` | common token-event | SQL | token-event | - | 33 | 67 | `test-fixtures/correctness/common/sample-data-portable-process-sql/input.sql` |
| `common-sample-data-portable-relations-sql` | common token-event | SQL | token-event | - | 16 | 3 | `test-fixtures/correctness/common/sample-data-portable-relations-sql/input.sql` |
| `common-sql-basic-join` | common token-event | SQL | token-event | - | 1 | 0 | `test-fixtures/correctness/common/sql-basic-join/input.sql` |
| `common-sql-common-aggregate-in-negative` | common token-event | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/common/sql-common-aggregate-in-negative/input.sql` |
| `common-sql-common-case-update-lineage` | common token-event | SQL | token-event | - | 0 | 1 | `test-fixtures/correctness/common/sql-common-case-update-lineage/input.sql` |
| `common-sql-common-comma-join` | common token-event | SQL | token-event | - | 1 | 0 | `test-fixtures/correctness/common/sql-common-comma-join/input.sql` |
| `common-sql-common-cte-exists-in` | common token-event | SQL | token-event | - | 3 | 0 | `test-fixtures/correctness/common/sql-common-cte-exists-in/input.sql` |
| `common-sql-common-cte-insert-lineage` | common token-event | SQL | token-event | - | 0 | 2 | `test-fixtures/correctness/common/sql-common-cte-insert-lineage/input.sql` |
| `common-sql-common-delete-exists` | common token-event | SQL | token-event | - | 1 | 0 | `test-fixtures/correctness/common/sql-common-delete-exists/input.sql` |
| `common-sql-common-derived-table` | common token-event | SQL | token-event | - | 1 | 0 | `test-fixtures/correctness/common/sql-common-derived-table/input.sql` |
| `common-sql-common-function-equality-negative` | common token-event | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/common/sql-common-function-equality-negative/input.sql` |
| `common-sql-common-insert-select-lineage` | common token-event | SQL | token-event | - | 0 | 2 | `test-fixtures/correctness/common/sql-common-insert-select-lineage/input.sql` |
| `common-sql-common-literal-in-like-negative` | common token-event | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/common/sql-common-literal-in-like-negative/input.sql` |
| `common-sql-common-multi-join` | common token-event | SQL | token-event | - | 3 | 0 | `test-fixtures/correctness/common/sql-common-multi-join/input.sql` |
| `common-sql-common-scalar-in` | common token-event | SQL | token-event | - | 1 | 0 | `test-fixtures/correctness/common/sql-common-scalar-in/input.sql` |
| `common-sql-common-self-join` | common token-event | SQL | token-event | - | 1 | 0 | `test-fixtures/correctness/common/sql-common-self-join/input.sql` |
| `common-sql-common-tuple-in` | common token-event | SQL | token-event | - | 2 | 0 | `test-fixtures/correctness/common/sql-common-tuple-in/input.sql` |
| `common-sql-common-update-set-lineage` | common token-event | SQL | token-event | - | 1 | 1 | `test-fixtures/correctness/common/sql-common-update-set-lineage/input.sql` |
| `common-sql-join-using` | common token-event | SQL | token-event | - | 1 | 0 | `test-fixtures/correctness/common/sql-join-using/input.sql` |
| `mysql-basic-correctness-case-01-ddl` | MySQL token-event root | DDL | token-event | - | 0 | 0 | `test-fixtures/mysql/basic-correctness/case-01/ddl/show-create-tables.sql` |
| `basic-correctness-case-01-functions-sql` | MySQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-functions.sql` |
| `basic-correctness-case-01-procedure-internal-flush-buffer-sql` | MySQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| `basic-correctness-case-01-procedure-proc-batch-call-generate-po-sql` | MySQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| `basic-correctness-case-01-procedure-proc-batch-generate-purchase-inbound-sql` | MySQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| `basic-correctness-case-01-procedure-proc-batch-insert-purchase-requisition-sql` | MySQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| `basic-correctness-case-01-procedure-proc-batch-mock-retail-orders-sql` | MySQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| `basic-correctness-case-01-procedure-proc-create-order-mock-retail-sql` | MySQL token-event root | SQL | token-event | - | 2 | 1 | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| `basic-correctness-case-01-procedure-proc-generate-purchase-inbound-from-order-sql` | MySQL token-event root | SQL | token-event | - | 2 | 17 | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| `basic-correctness-case-01-procedure-proc-generate-purchase-order-from-requisition-sql` | MySQL token-event root | SQL | token-event | - | 1 | 19 | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| `basic-correctness-case-01-procedure-proc-init-yearly-weights-sql` | MySQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| `basic-correctness-case-01-procedure-proc-insert-purchase-requisition-sql` | MySQL token-event root | SQL | token-event | - | 1 | 0 | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| `basic-correctness-case-01-procedure-proc-refresh-org-pdf-sql` | MySQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| `basic-correctness-case-01-procedure-proc-simulate-yearly-sales-sql` | MySQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| `basic-correctness-case-01-procedure-proc-worker-daily-distribution-sql` | MySQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| `basic-correctness-case-01-procedure-sp-fill-biz-bill-item-fact-new-with-progress-sql` | MySQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| `basic-correctness-case-01-procedure-sp-sync-retail-out-fact-batch-sql` | MySQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| `mysql-basic-correctness-case-01-sql` | MySQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/mysql/basic-correctness/case-01/sql/performance-schema-statements.sql` |
| `mysql-basic-correctness-case-02-ddl` | MySQL token-event root | DDL | token-event | - | 2 | 0 | `test-fixtures/mysql/basic-correctness/case-02/ddl/show-create-tables.sql` |
| `mysql-basic-correctness-case-02-sql` | MySQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/mysql/basic-correctness/case-02/sql/performance-schema-statements.sql` |
| `mysql-basic-correctness-case-03-ddl` | MySQL token-event root | DDL | token-event | - | 0 | 0 | `test-fixtures/mysql/basic-correctness/case-03/ddl/show-create-tables.sql` |
| `mysql-basic-correctness-case-03-sql` | MySQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/mysql/basic-correctness/case-03/sql/performance-schema-statements.sql` |
| `mysql-basic-correctness-case-04-ddl` | MySQL token-event root | DDL | token-event | - | 9 | 0 | `test-fixtures/mysql/basic-correctness/case-04/ddl/show-create-tables.sql` |
| `mysql-basic-correctness-case-04-sql` | MySQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/mysql/basic-correctness/case-04/sql/performance-schema-statements.sql` |
| `mysql-ddl-create-table-fk-index` | MySQL token-event root | DDL | token-event | - | 1 | 0 | `test-fixtures/correctness/mysql/ddl-create-table-fk-index/input.ddl.sql` |
| `mysql-business-cross-border-reconciliation-procedure-comma-subquery-sql` | MySQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/mysql/mysql-business-cross-border-reconciliation-procedure-comma-subquery-sql/input.sql` |
| `mysql-business-cross-border-reconciliation-procedure-sql` | MySQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/mysql/mysql-business-cross-border-reconciliation-procedure-sql/input.sql` |
| `mysql-business-financial-asset-wash-procedure-comma-sql` | MySQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/mysql/mysql-business-financial-asset-wash-procedure-comma-sql/input.sql` |
| `mysql-business-financial-asset-wash-procedure-sql` | MySQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/mysql/mysql-business-financial-asset-wash-procedure-sql/input.sql` |
| `mysql-commerce-promotion-update-comma-join-sql` | MySQL token-event root | SQL | token-event | - | 3 | 1 | `test-fixtures/correctness/mysql/mysql-commerce-promotion-update-comma-join-sql/input.sql` |
| `mysql-commerce-promotion-update-explicit-join-sql` | MySQL token-event root | SQL | token-event | - | 3 | 1 | `test-fixtures/correctness/mysql/mysql-commerce-promotion-update-explicit-join-sql/input.sql` |
| `mysql-invalid-orders-delete-comma-join-sql` | MySQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/mysql/mysql-invalid-orders-delete-comma-join-sql/input.sql` |
| `mysql-invalid-orders-delete-explicit-join-sql` | MySQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/mysql/mysql-invalid-orders-delete-explicit-join-sql/input.sql` |
| `mysql-official-alter-index-ddl` | MySQL token-event root | DDL | token-event | - | 0 | 0 | `test-fixtures/correctness/mysql/mysql-official-alter-index-ddl/input.ddl.sql` |
| `mysql-official-complex-index-ddl` | MySQL token-event root | DDL | token-event | - | 0 | 0 | `test-fixtures/correctness/mysql/mysql-official-complex-index-ddl/input.ddl.sql` |
| `mysql-official-cte-dml-sql` | MySQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/mysql/mysql-official-cte-dml-sql/input.sql` |
| `mysql-official-cte-nested-sql` | MySQL token-event root | SQL | token-event | - | 4 | 0 | `test-fixtures/correctness/mysql/mysql-official-cte-nested-sql/input.sql` |
| `mysql-official-derived-subquery-sql` | MySQL token-event root | SQL | token-event | - | 6 | 0 | `test-fixtures/correctness/mysql/mysql-official-derived-subquery-sql/input.sql` |
| `mysql-official-functional-index-ddl` | MySQL token-event root | DDL | token-event | - | 0 | 0 | `test-fixtures/correctness/mysql/mysql-official-functional-index-ddl/input.ddl.sql` |
| `mysql-official-index-options-ddl` | MySQL token-event root | DDL | token-event | - | 0 | 0 | `test-fixtures/correctness/mysql/mysql-official-index-options-ddl/input.ddl.sql` |
| `mysql-official-invisible-index-ddl` | MySQL token-event root | DDL | token-event | - | 0 | 0 | `test-fixtures/correctness/mysql/mysql-official-invisible-index-ddl/input.ddl.sql` |
| `mysql-official-join-edge-sql` | MySQL token-event root | SQL | token-event | - | 2 | 0 | `test-fixtures/correctness/mysql/mysql-official-join-edge-sql/input.sql` |
| `mysql-official-join-matrix-sql` | MySQL token-event root | SQL | token-event | - | 7 | 0 | `test-fixtures/correctness/mysql/mysql-official-join-matrix-sql/input.sql` |
| `mysql-official-lateral-derived-edge-sql` | MySQL token-event root | SQL | token-event | - | 2 | 0 | `test-fixtures/correctness/mysql/mysql-official-lateral-derived-edge-sql/input.sql` |
| `mysql-official-recursive-cte-sql` | MySQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/mysql/mysql-official-recursive-cte-sql/input.sql` |
| `mysql-official-special-index-ddl` | MySQL token-event root | DDL | token-event | - | 0 | 0 | `test-fixtures/correctness/mysql/mysql-official-special-index-ddl/input.ddl.sql` |
| `mysql-official-subquery-edge-sql` | MySQL token-event root | SQL | token-event | - | 3 | 0 | `test-fixtures/correctness/mysql/mysql-official-subquery-edge-sql/input.sql` |
| `mysql-orphan-reviews-delete-left-join-sql` | MySQL token-event root | SQL | token-event | - | 1 | 0 | `test-fixtures/correctness/mysql/mysql-orphan-reviews-delete-left-join-sql/input.sql` |
| `mysql-orphan-reviews-delete-not-exists-sql` | MySQL token-event root | SQL | token-event | - | 1 | 0 | `test-fixtures/correctness/mysql/mysql-orphan-reviews-delete-not-exists-sql/input.sql` |
| `mysqlsample-data-full-01-schema-01-tables-ddl` | MySQL token-event root | DDL | token-event | - | 88 | 0 | `sample-data/mysql/8.0/01-schema/01-tables.sql` |
| `mysqlsample-data-full-01-schema-02-indexes-and-views-ddl` | MySQL token-event root | DDL | token-event | - | 0 | 0 | `sample-data/mysql/8.0/01-schema/02-indexes-and-views.sql` |
| `mysqlsample-data-full-01-schema-02-indexes-and-views-views-sql` | MySQL token-event root | SQL | token-event | - | 11 | 0 | `test-fixtures/correctness/mysql/mysql-sample-data-full-01-schema-02-indexes-and-views-views-sql/input.sql` |
| `mysqlsample-data-full-01-schema-03-triggers-sql` | MySQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/mysql/mysql-sample-data-full-01-schema-03-triggers-sql/input.sql` |
| `mysqlsample-data-full-01-schema-04-supplementary-tables-ddl` | MySQL token-event root | DDL | token-event | - | 28 | 0 | `sample-data/mysql/8.0/01-schema/04-supplementary-tables.sql` |
| `mysqlsample-data-full-01-schema-05-third-batch-tables-ddl` | MySQL token-event root | DDL | token-event | - | 30 | 0 | `sample-data/mysql/8.0/01-schema/05-third-batch-tables.sql` |
| `mysqlsample-data-full-02-procedures-01-procedures-sql` | MySQL token-event root | SQL | token-event | - | 0 | 5 | `test-fixtures/correctness/mysql/mysql-sample-data-full-02-procedures-01-procedures-sql/input.sql` |
| `mysqlsample-data-full-02-procedures-02-procedures-supplement-sql` | MySQL token-event root | SQL | token-event | - | 2 | 0 | `test-fixtures/correctness/mysql/mysql-sample-data-full-02-procedures-02-procedures-supplement-sql/input.sql` |
| `mysqlsample-data-full-02-procedures-03-functions-sql` | MySQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/mysql/mysql-sample-data-full-02-procedures-03-functions-sql/input.sql` |
| `mysqlsample-data-full-02-procedures-04-procedures-supplement-sql` | MySQL token-event root | SQL | token-event | - | 2 | 7 | `test-fixtures/correctness/mysql/mysql-sample-data-full-02-procedures-04-procedures-supplement-sql/input.sql` |
| `mysqlsample-data-full-02-procedures-05-third-batch-procedures-sql` | MySQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/mysql/mysql-sample-data-full-02-procedures-05-third-batch-procedures-sql/input.sql` |
| `mysqlsample-data-full-02-procedures-06-third-batch-functions-sql` | MySQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/mysql/mysql-sample-data-full-02-procedures-06-third-batch-functions-sql/input.sql` |
| `mysqlsample-data-full-02-procedures-07-store-customer-procedures-sql` | MySQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/mysql/mysql-sample-data-full-02-procedures-07-store-customer-procedures-sql/input.sql` |
| `mysqlsample-data-full-02-procedures-08-batch-expiry-procedures-sql` | MySQL token-event root | SQL | token-event | - | 3 | 0 | `test-fixtures/correctness/mysql/mysql-sample-data-full-02-procedures-08-batch-expiry-procedures-sql/input.sql` |
| `mysqlsample-data-full-02-procedures-09-return-refund-procedures-sql` | MySQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/mysql/mysql-sample-data-full-02-procedures-09-return-refund-procedures-sql/input.sql` |
| `mysqlsample-data-full-02-procedures-10-supplier-geo-procedures-sql` | MySQL token-event root | SQL | token-event | - | 4 | 0 | `test-fixtures/correctness/mysql/mysql-sample-data-full-02-procedures-10-supplier-geo-procedures-sql/input.sql` |
| `mysqlsample-data-full-02-procedures-11-common-system-procedures-sql` | MySQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/mysql/mysql-sample-data-full-02-procedures-11-common-system-procedures-sql/input.sql` |
| `mysqlsample-data-full-02-procedures-12-enterprise-extension-procedures-sql` | MySQL token-event root | SQL | token-event | - | 3 | 8 | `test-fixtures/correctness/mysql/mysql-sample-data-full-02-procedures-12-enterprise-extension-procedures-sql/input.sql` |
| `mysqlsample-data-full-03-data-01-master-data-sql` | MySQL token-event root | SQL | token-event | - | 0 | 0 | `sample-data/mysql/8.0/03-data/01-master-data.sql` |
| `mysqlsample-data-full-03-data-02-supplementary-data-sql` | MySQL token-event root | SQL | token-event | - | 0 | 6 | `sample-data/mysql/8.0/03-data/02-supplementary-data.sql` |
| `mysqlsample-data-full-03-data-03-third-batch-data-sql` | MySQL token-event root | SQL | token-event | - | 0 | 3 | `sample-data/mysql/8.0/03-data/03-third-batch-data.sql` |
| `mysqlsample-data-full-03-data-04-return-damage-data-sql` | MySQL token-event root | SQL | token-event | - | 0 | 2 | `sample-data/mysql/8.0/03-data/04-return-damage-data.sql` |
| `mysqlsample-data-full-03-data-05-massive-data-generator-sql` | MySQL token-event root | SQL | token-event | - | 0 | 4 | `test-fixtures/correctness/mysql/mysql-sample-data-full-03-data-05-massive-data-generator-sql/input.sql` |
| `mysqlsample-data-full-03-data-06-enterprise-extension-data-sql` | MySQL token-event root | SQL | token-event | - | 0 | 0 | `sample-data/mysql/8.0/03-data/06-enterprise-extension-data.sql` |
| `mysqlsample-data-full-04-queries-01-complex-queries-sql` | MySQL token-event root | SQL | token-event | - | 7 | 0 | `sample-data/mysql/8.0/04-queries/01-complex-queries.sql` |
| `mysqlsample-data-full-04-queries-02-complex-queries-batch2-sql` | MySQL token-event root | SQL | token-event | - | 3 | 0 | `sample-data/mysql/8.0/04-queries/02-complex-queries-batch2.sql` |
| `mysqlsample-data-full-04-queries-03-complex-queries-batch3-sql` | MySQL token-event root | SQL | token-event | - | 1 | 0 | `sample-data/mysql/8.0/04-queries/03-complex-queries-batch3.sql` |
| `mysqlsample-data-full-04-queries-04-store-customer-queries-sql` | MySQL token-event root | SQL | token-event | - | 0 | 0 | `sample-data/mysql/8.0/04-queries/04-store-customer-queries.sql` |
| `mysqlsample-data-full-04-queries-05-batch-expiry-queries-sql` | MySQL token-event root | SQL | token-event | - | 4 | 0 | `sample-data/mysql/8.0/04-queries/05-batch-expiry-queries.sql` |
| `mysqlsample-data-full-04-queries-06-return-damage-queries-sql` | MySQL token-event root | SQL | token-event | - | 2 | 0 | `sample-data/mysql/8.0/04-queries/06-return-damage-queries.sql` |
| `mysqlsample-data-full-04-queries-07-supplier-analysis-queries-sql` | MySQL token-event root | SQL | token-event | - | 3 | 0 | `sample-data/mysql/8.0/04-queries/07-supplier-analysis-queries.sql` |
| `mysqlsample-data-full-04-queries-08-common-system-queries-sql` | MySQL token-event root | SQL | token-event | - | 10 | 0 | `sample-data/mysql/8.0/04-queries/08-common-system-queries.sql` |
| `mysql-supply-chain-update-comma-and-subquery-sql` | MySQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/mysql/mysql-supply-chain-update-comma-and-subquery-sql/input.sql` |
| `mysql-supply-chain-update-explicit-join-sql` | MySQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/mysql/mysql-supply-chain-update-explicit-join-sql/input.sql` |
| `mysql-user-spending-comma-join-update-sql` | MySQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/mysql/mysql-user-spending-comma-join-update-sql/input.sql` |
| `mysql-user-spending-left-join-update-sql` | MySQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/mysql/mysql-user-spending-left-join-update-sql/input.sql` |
| `mysql-sample-data-enterprise-extension-ddl` | MySQL token-event root | DDL | token-event | - | 31 | 0 | `sample-data/mysql/8.0/01-schema/06-enterprise-extension-tables.sql` |
| `mysql-sample-data-enterprise-extension-queries-sql` | MySQL token-event root | SQL | token-event | - | 13 | 0 | `sample-data/mysql/8.0/04-queries/10-enterprise-extension-queries.sql` |
| `mysql-sample-data-enterprise-procedures-sql` | MySQL token-event root | SQL | token-event | - | 3 | 8 | `test-fixtures/correctness/mysql/sample-data-enterprise-procedures-sql/input.sql` |
| `mysql-sample-data-real-world-scenarios-sql` | MySQL token-event root | SQL | token-event | - | 0 | 0 | `sample-data/mysql/8.0/04-queries/09-real-world-scenarios.sql` |
| `mysql-sql-cte-lateral` | MySQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/mysql/sql-cte-lateral/input.sql` |
| `mysql-sql-delete-left-join` | MySQL token-event root | SQL | token-event | - | 1 | 0 | `test-fixtures/correctness/mysql/sql-delete-left-join/input.sql` |
| `mysql-sql-multi-table-update` | MySQL token-event root | SQL | token-event | - | 2 | 0 | `test-fixtures/correctness/mysql/sql-multi-table-update/input.sql` |
| `mysql-sql-system-log-noise` | MySQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/mysql/sql-system-log-noise/input.sql` |
| `mysql80-mysql-basic-correctness-case-01-ddl` | MySQL full-grammer v8_0 | DDL | full-grammer | mysql/8.0 | 2 | 0 | `test-fixtures/mysql/basic-correctness/case-01/ddl/show-create-tables.sql` |
| `mysql80-basic-correctness-case-01-functions-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 0 | 0 | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-functions.sql` |
| `mysql80-basic-correctness-case-01-procedure-internal-flush-buffer-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 0 | 0 | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| `mysql80-basic-correctness-case-01-procedure-proc-batch-call-generate-po-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 0 | 0 | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| `mysql80-basic-correctness-case-01-procedure-proc-batch-generate-purchase-inbound-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 0 | 0 | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| `mysql80-basic-correctness-case-01-procedure-proc-batch-insert-purchase-requisition-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 0 | 0 | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| `mysql80-basic-correctness-case-01-procedure-proc-batch-mock-retail-orders-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 0 | 0 | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| `mysql80-basic-correctness-case-01-procedure-proc-create-order-mock-retail-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 4 | 1 | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| `mysql80-basic-correctness-case-01-procedure-proc-generate-purchase-inbound-from-order-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 5 | 25 | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| `mysql80-basic-correctness-case-01-procedure-proc-generate-purchase-order-from-requisition-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 2 | 21 | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| `mysql80-basic-correctness-case-01-procedure-proc-init-yearly-weights-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 0 | 0 | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| `mysql80-basic-correctness-case-01-procedure-proc-insert-purchase-requisition-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 2 | 0 | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| `mysql80-basic-correctness-case-01-procedure-proc-refresh-org-pdf-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 0 | 5 | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| `mysql80-basic-correctness-case-01-procedure-proc-simulate-yearly-sales-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 0 | 0 | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| `mysql80-basic-correctness-case-01-procedure-proc-worker-daily-distribution-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 1 | 2 | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| `mysql80-basic-correctness-case-01-procedure-sp-fill-biz-bill-item-fact-new-with-progress-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 7 | 22 | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| `mysql80-basic-correctness-case-01-procedure-sp-sync-retail-out-fact-batch-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 1 | 0 | `test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql` |
| `mysql80-mysql-basic-correctness-case-01-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 0 | 0 | `test-fixtures/mysql/basic-correctness/case-01/sql/performance-schema-statements.sql` |
| `mysql80-mysql-basic-correctness-case-02-ddl` | MySQL full-grammer v8_0 | DDL | full-grammer | mysql/8.0 | 2 | 0 | `test-fixtures/mysql/basic-correctness/case-02/ddl/show-create-tables.sql` |
| `mysql80-mysql-basic-correctness-case-02-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 0 | 0 | `test-fixtures/mysql/basic-correctness/case-02/sql/performance-schema-statements.sql` |
| `mysql80-mysql-basic-correctness-case-03-ddl` | MySQL full-grammer v8_0 | DDL | full-grammer | mysql/8.0 | 2 | 0 | `test-fixtures/mysql/basic-correctness/case-03/ddl/show-create-tables.sql` |
| `mysql80-mysql-basic-correctness-case-03-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 0 | 0 | `test-fixtures/mysql/basic-correctness/case-03/sql/performance-schema-statements.sql` |
| `mysql80-mysql-basic-correctness-case-04-ddl` | MySQL full-grammer v8_0 | DDL | full-grammer | mysql/8.0 | 9 | 0 | `test-fixtures/mysql/basic-correctness/case-04/ddl/show-create-tables.sql` |
| `mysql80-mysql-basic-correctness-case-04-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 0 | 0 | `test-fixtures/mysql/basic-correctness/case-04/sql/performance-schema-statements.sql` |
| `mysql80-mysql-ddl-create-table-fk-index` | MySQL full-grammer v8_0 | DDL | full-grammer | mysql/8.0 | 1 | 0 | `test-fixtures/correctness/mysql/v8_0/ddl-create-table-fk-index/input.ddl.sql` |
| `mysql80-mysql-business-cross-border-reconciliation-procedure-comma-subquery-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 3 | 0 | `test-fixtures/correctness/mysql/v8_0/mysql-business-cross-border-reconciliation-procedure-comma-subquery-sql/input.sql` |
| `mysql80-mysql-business-cross-border-reconciliation-procedure-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 3 | 0 | `test-fixtures/correctness/mysql/v8_0/mysql-business-cross-border-reconciliation-procedure-sql/input.sql` |
| `mysql80-mysql-business-financial-asset-wash-procedure-comma-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 3 | 3 | `test-fixtures/correctness/mysql/v8_0/mysql-business-financial-asset-wash-procedure-comma-sql/input.sql` |
| `mysql80-mysql-business-financial-asset-wash-procedure-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 3 | 3 | `test-fixtures/correctness/mysql/v8_0/mysql-business-financial-asset-wash-procedure-sql/input.sql` |
| `mysql80-mysql-commerce-promotion-update-comma-join-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 3 | 1 | `test-fixtures/correctness/mysql/v8_0/mysql-commerce-promotion-update-comma-join-sql/input.sql` |
| `mysql80-mysql-commerce-promotion-update-explicit-join-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 3 | 1 | `test-fixtures/correctness/mysql/v8_0/mysql-commerce-promotion-update-explicit-join-sql/input.sql` |
| `mysql80-mysql-invalid-orders-delete-comma-join-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 2 | 0 | `test-fixtures/correctness/mysql/v8_0/mysql-invalid-orders-delete-comma-join-sql/input.sql` |
| `mysql80-mysql-invalid-orders-delete-explicit-join-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 2 | 0 | `test-fixtures/correctness/mysql/v8_0/mysql-invalid-orders-delete-explicit-join-sql/input.sql` |
| `mysql80-mysql-official-alter-index-ddl` | MySQL full-grammer v8_0 | DDL | full-grammer | mysql/8.0 | 0 | 0 | `test-fixtures/correctness/mysql/v8_0/mysql-official-alter-index-ddl/input.ddl.sql` |
| `mysql80-mysql-official-complex-index-ddl` | MySQL full-grammer v8_0 | DDL | full-grammer | mysql/8.0 | 0 | 0 | `test-fixtures/correctness/mysql/v8_0/mysql-official-complex-index-ddl/input.ddl.sql` |
| `mysql80-mysql-official-cte-dml-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 4 | 1 | `test-fixtures/correctness/mysql/v8_0/mysql-official-cte-dml-sql/input.sql` |
| `mysql80-mysql-official-cte-nested-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 5 | 0 | `test-fixtures/correctness/mysql/v8_0/mysql-official-cte-nested-sql/input.sql` |
| `mysql80-mysql-official-derived-subquery-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 6 | 0 | `test-fixtures/correctness/mysql/v8_0/mysql-official-derived-subquery-sql/input.sql` |
| `mysql80-mysql-official-functional-index-ddl` | MySQL full-grammer v8_0 | DDL | full-grammer | mysql/8.0 | 0 | 0 | `test-fixtures/correctness/mysql/v8_0/mysql-official-functional-index-ddl/input.ddl.sql` |
| `mysql80-mysql-official-index-options-ddl` | MySQL full-grammer v8_0 | DDL | full-grammer | mysql/8.0 | 0 | 0 | `test-fixtures/correctness/mysql/v8_0/mysql-official-index-options-ddl/input.ddl.sql` |
| `mysql80-mysql-official-invisible-index-ddl` | MySQL full-grammer v8_0 | DDL | full-grammer | mysql/8.0 | 0 | 0 | `test-fixtures/correctness/mysql/v8_0/mysql-official-invisible-index-ddl/input.ddl.sql` |
| `mysql80-mysql-official-join-edge-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 4 | 0 | `test-fixtures/correctness/mysql/v8_0/mysql-official-join-edge-sql/input.sql` |
| `mysql80-mysql-official-join-matrix-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 8 | 0 | `test-fixtures/correctness/mysql/v8_0/mysql-official-join-matrix-sql/input.sql` |
| `mysql80-mysql-official-lateral-derived-edge-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 4 | 0 | `test-fixtures/correctness/mysql/v8_0/mysql-official-lateral-derived-edge-sql/input.sql` |
| `mysql80-mysql-official-recursive-cte-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 2 | 0 | `test-fixtures/correctness/mysql/v8_0/mysql-official-recursive-cte-sql/input.sql` |
| `mysql80-mysql-official-special-index-ddl` | MySQL full-grammer v8_0 | DDL | full-grammer | mysql/8.0 | 0 | 0 | `test-fixtures/correctness/mysql/v8_0/mysql-official-special-index-ddl/input.ddl.sql` |
| `mysql80-mysql-official-subquery-edge-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 6 | 0 | `test-fixtures/correctness/mysql/v8_0/mysql-official-subquery-edge-sql/input.sql` |
| `mysql80-mysql-orphan-reviews-delete-left-join-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 1 | 0 | `test-fixtures/correctness/mysql/v8_0/mysql-orphan-reviews-delete-left-join-sql/input.sql` |
| `mysql80-mysql-orphan-reviews-delete-not-exists-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 1 | 0 | `test-fixtures/correctness/mysql/v8_0/mysql-orphan-reviews-delete-not-exists-sql/input.sql` |
| `mysql80-mysql-supply-chain-update-comma-and-subquery-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 6 | 3 | `test-fixtures/correctness/mysql/v8_0/mysql-supply-chain-update-comma-and-subquery-sql/input.sql` |
| `mysql80-mysql-supply-chain-update-explicit-join-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 6 | 3 | `test-fixtures/correctness/mysql/v8_0/mysql-supply-chain-update-explicit-join-sql/input.sql` |
| `mysql80-mysql-user-spending-comma-join-update-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 1 | 2 | `test-fixtures/correctness/mysql/v8_0/mysql-user-spending-comma-join-update-sql/input.sql` |
| `mysql80-mysql-user-spending-left-join-update-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 1 | 2 | `test-fixtures/correctness/mysql/v8_0/mysql-user-spending-left-join-update-sql/input.sql` |
| `mysql80sample-data-full-01-schema-01-tables-ddl` | MySQL full-grammer v8_0 | DDL | full-grammer | mysql/8.0 | 94 | 0 | `sample-data/mysql/8.0/01-schema/01-tables.sql` |
| `mysql80sample-data-full-01-schema-02-indexes-and-views-ddl` | MySQL full-grammer v8_0 | DDL | full-grammer | mysql/8.0 | 0 | 0 | `sample-data/mysql/8.0/01-schema/02-indexes-and-views.sql` |
| `mysql80sample-data-full-01-schema-02-indexes-and-views-views-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 17 | 0 | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-01-schema-02-indexes-and-views-views-sql/input.sql` |
| `mysql80sample-data-full-01-schema-03-triggers-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 6 | 6 | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-01-schema-03-triggers-sql/input.sql` |
| `mysql80sample-data-full-01-schema-04-supplementary-tables-ddl` | MySQL full-grammer v8_0 | DDL | full-grammer | mysql/8.0 | 32 | 0 | `sample-data/mysql/8.0/01-schema/04-supplementary-tables.sql` |
| `mysql80sample-data-full-01-schema-05-third-batch-tables-ddl` | MySQL full-grammer v8_0 | DDL | full-grammer | mysql/8.0 | 36 | 0 | `sample-data/mysql/8.0/01-schema/05-third-batch-tables.sql` |
| `mysql80sample-data-full-02-procedures-01-procedures-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 0 | 5 | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-02-procedures-01-procedures-sql/input.sql` |
| `mysql80sample-data-full-02-procedures-02-procedures-supplement-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 13 | 0 | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-02-procedures-02-procedures-supplement-sql/input.sql` |
| `mysql80sample-data-full-02-procedures-03-functions-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 2 | 0 | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-02-procedures-03-functions-sql/input.sql` |
| `mysql80sample-data-full-02-procedures-04-procedures-supplement-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 3 | 9 | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-02-procedures-04-procedures-supplement-sql/input.sql` |
| `mysql80sample-data-full-02-procedures-05-third-batch-procedures-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 5 | 5 | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-02-procedures-05-third-batch-procedures-sql/input.sql` |
| `mysql80sample-data-full-02-procedures-06-third-batch-functions-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 2 | 0 | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-02-procedures-06-third-batch-functions-sql/input.sql` |
| `mysql80sample-data-full-02-procedures-07-store-customer-procedures-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 10 | 0 | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-02-procedures-07-store-customer-procedures-sql/input.sql` |
| `mysql80sample-data-full-02-procedures-08-batch-expiry-procedures-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 13 | 0 | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-02-procedures-08-batch-expiry-procedures-sql/input.sql` |
| `mysql80sample-data-full-02-procedures-09-return-refund-procedures-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 15 | 0 | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-02-procedures-09-return-refund-procedures-sql/input.sql` |
| `mysql80sample-data-full-02-procedures-10-supplier-geo-procedures-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 11 | 5 | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-02-procedures-10-supplier-geo-procedures-sql/input.sql` |
| `mysql80sample-data-full-02-procedures-11-common-system-procedures-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 14 | 0 | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-02-procedures-11-common-system-procedures-sql/input.sql` |
| `mysql80sample-data-full-02-procedures-12-enterprise-extension-procedures-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 3 | 8 | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-02-procedures-12-enterprise-extension-procedures-sql/input.sql` |
| `mysql80sample-data-full-03-data-01-master-data-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 0 | 0 | `sample-data/mysql/8.0/03-data/01-master-data.sql` |
| `mysql80sample-data-full-03-data-02-supplementary-data-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 4 | 51 | `sample-data/mysql/8.0/03-data/02-supplementary-data.sql` |
| `mysql80sample-data-full-03-data-03-third-batch-data-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 2 | 36 | `sample-data/mysql/8.0/03-data/03-third-batch-data.sql` |
| `mysql80sample-data-full-03-data-04-return-damage-data-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 2 | 22 | `sample-data/mysql/8.0/03-data/04-return-damage-data.sql` |
| `mysql80sample-data-full-03-data-05-massive-data-generator-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 1 | 28 | `test-fixtures/correctness/mysql/v8_0/mysql80-sample-data-full-03-data-05-massive-data-generator-sql/input.sql` |
| `mysql80sample-data-full-03-data-06-enterprise-extension-data-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 0 | 0 | `sample-data/mysql/8.0/03-data/06-enterprise-extension-data.sql` |
| `mysql80sample-data-full-04-queries-01-complex-queries-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 35 | 0 | `sample-data/mysql/8.0/04-queries/01-complex-queries.sql` |
| `mysql80sample-data-full-04-queries-02-complex-queries-batch2-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 23 | 0 | `sample-data/mysql/8.0/04-queries/02-complex-queries-batch2.sql` |
| `mysql80sample-data-full-04-queries-03-complex-queries-batch3-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 20 | 0 | `sample-data/mysql/8.0/04-queries/03-complex-queries-batch3.sql` |
| `mysql80sample-data-full-04-queries-04-store-customer-queries-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 8 | 0 | `sample-data/mysql/8.0/04-queries/04-store-customer-queries.sql` |
| `mysql80sample-data-full-04-queries-05-batch-expiry-queries-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 14 | 0 | `sample-data/mysql/8.0/04-queries/05-batch-expiry-queries.sql` |
| `mysql80sample-data-full-04-queries-06-return-damage-queries-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 16 | 0 | `sample-data/mysql/8.0/04-queries/06-return-damage-queries.sql` |
| `mysql80sample-data-full-04-queries-07-supplier-analysis-queries-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 3 | 0 | `sample-data/mysql/8.0/04-queries/07-supplier-analysis-queries.sql` |
| `mysql80sample-data-full-04-queries-08-common-system-queries-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 34 | 0 | `sample-data/mysql/8.0/04-queries/08-common-system-queries.sql` |
| `mysql80-sample-data-enterprise-extension-ddl` | MySQL full-grammer v8_0 | DDL | full-grammer | mysql/8.0 | 31 | 0 | `sample-data/mysql/8.0/01-schema/06-enterprise-extension-tables.sql` |
| `mysql80-sample-data-enterprise-extension-queries-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 17 | 0 | `sample-data/mysql/8.0/04-queries/10-enterprise-extension-queries.sql` |
| `mysql80-sample-data-enterprise-procedures-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 3 | 8 | `test-fixtures/correctness/mysql/sample-data-enterprise-procedures-sql/input.sql` |
| `mysql80-sample-data-real-world-scenarios-sql` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 53 | 0 | `sample-data/mysql/8.0/04-queries/09-real-world-scenarios.sql` |
| `mysql80-mysql-sql-cte-lateral` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 1 | 0 | `test-fixtures/correctness/mysql/v8_0/sql-cte-lateral/input.sql` |
| `mysql80-mysql-sql-delete-left-join` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 1 | 0 | `test-fixtures/correctness/mysql/v8_0/sql-delete-left-join/input.sql` |
| `mysql80-mysql-sql-multi-table-update` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 2 | 0 | `test-fixtures/correctness/mysql/v8_0/sql-multi-table-update/input.sql` |
| `mysql80-mysql-sql-system-log-noise` | MySQL full-grammer v8_0 | SQL | full-grammer | mysql/8.0 | 0 | 0 | `test-fixtures/correctness/mysql/v8_0/sql-system-log-noise/input.sql` |
| `postgres-ddl-alter-table-fk` | PostgreSQL token-event root | DDL | token-event | - | 1 | 0 | `test-fixtures/correctness/postgres/ddl-alter-table-fk/input.ddl.sql` |
| `postgres-ddl-partial-index-boundary` | PostgreSQL token-event root | DDL | token-event | - | 1 | 0 | `test-fixtures/correctness/postgres/ddl-partial-index-boundary/input.ddl.sql` |
| `postgres-ddl-unique-include-index` | PostgreSQL token-event root | DDL | token-event | - | 2 | 0 | `test-fixtures/correctness/postgres/ddl-unique-include-index/input.ddl.sql` |
| `postgres-generated-comprehensive-query-sql` | PostgreSQL token-event root | SQL | token-event | - | 39 | 0 | `test-fixtures/examples/comprehensive-query-sql-001-030.sql` |
| `postgres-generated-industrial-complex-sql` | PostgreSQL token-event root | SQL | token-event | - | 5 | 0 | `test-fixtures/examples/industrial-complex-sql-001-050.sql` |
| `postgres-generated-provided-complex-sql` | PostgreSQL token-event root | SQL | token-event | - | 5 | 0 | `test-fixtures/correctness/postgres/generated-provided-complex-sql/input.sql` |
| `postgres-basic-correctness-case-01-ddl` | PostgreSQL token-event root | DDL | token-event | - | 460 | 0 | `test-fixtures/correctness/postgres/postgres-basic-correctness-case-01-ddl/input.ddl.sql` |
| `postgres-basic-correctness-case-01-objects-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/postgres/postgres-basic-correctness-case-01-objects-sql/input.sql` |
| `postgres-basic-correctness-case-01-statements-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/postgres/postgres-basic-correctness-case-01-statements-sql/input.sql` |
| `postgres-business-account-balances-financial-cte-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/postgres/postgres-business-account-balances-financial-cte-sql/input.sql` |
| `postgres-business-account-balances-financial-explicit-join-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/postgres/postgres-business-account-balances-financial-explicit-join-sql/input.sql` |
| `postgres-business-asset-balances-update-outer-join-sql` | PostgreSQL token-event root | SQL | token-event | - | 4 | 3 | `test-fixtures/correctness/postgres/postgres-business-asset-balances-update-outer-join-sql/input.sql` |
| `postgres-business-cross-border-reconciliation-function-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/postgres/postgres-business-cross-border-reconciliation-function-sql/input.sql` |
| `postgres-business-delete-cascade-cte-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/postgres/postgres-business-delete-cascade-cte-sql/input.sql` |
| `postgres-business-delete-orphan-left-join-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/postgres/postgres-business-delete-orphan-left-join-sql/input.sql` |
| `postgres-business-delete-orphan-not-exists-sql` | PostgreSQL token-event root | SQL | token-event | - | 1 | 0 | `test-fixtures/correctness/postgres/postgres-business-delete-orphan-not-exists-sql/input.sql` |
| `postgres-business-inventory-purge-deep-subquery-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/postgres/postgres-business-inventory-purge-deep-subquery-sql/input.sql` |
| `postgres-business-inventory-purge-exists-equivalent-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/postgres/postgres-business-inventory-purge-exists-equivalent-sql/input.sql` |
| `postgres-business-risk-ledger-update-cte-comma-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/postgres/postgres-business-risk-ledger-update-cte-comma-sql/input.sql` |
| `postgres-business-risk-ledger-update-cte-explicit-join-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/postgres/postgres-business-risk-ledger-update-cte-explicit-join-sql/input.sql` |
| `postgres-business-risk-settlement-function-comma-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/postgres/postgres-business-risk-settlement-function-comma-sql/input.sql` |
| `postgres-business-risk-settlement-function-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/postgres/postgres-business-risk-settlement-function-sql/input.sql` |
| `postgres-business-update-inventory-comma-join-sql` | PostgreSQL token-event root | SQL | token-event | - | 2 | 2 | `test-fixtures/correctness/postgres/postgres-business-update-inventory-comma-join-sql/input.sql` |
| `postgres-business-update-inventory-from-join-sql` | PostgreSQL token-event root | SQL | token-event | - | 2 | 2 | `test-fixtures/correctness/postgres/postgres-business-update-inventory-from-join-sql/input.sql` |
| `postgres-business-update-products-comma-join-sql` | PostgreSQL token-event root | SQL | token-event | - | 2 | 1 | `test-fixtures/correctness/postgres/postgres-business-update-products-comma-join-sql/input.sql` |
| `postgres-business-update-products-from-join-sql` | PostgreSQL token-event root | SQL | token-event | - | 2 | 1 | `test-fixtures/correctness/postgres/postgres-business-update-products-from-join-sql/input.sql` |
| `postgres-business-update-users-aggregate-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/postgres/postgres-business-update-users-aggregate-sql/input.sql` |
| `postgres-business-update-users-scalar-subquery-sql` | PostgreSQL token-event root | SQL | token-event | - | 1 | 0 | `test-fixtures/correctness/postgres/postgres-business-update-users-scalar-subquery-sql/input.sql` |
| `postgres-business-update-warehouse-comma-subquery-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 2 | `test-fixtures/correctness/postgres/postgres-business-update-warehouse-comma-subquery-sql/input.sql` |
| `postgres-business-update-warehouse-complex-sql` | PostgreSQL token-event root | SQL | token-event | - | 1 | 2 | `test-fixtures/correctness/postgres/postgres-business-update-warehouse-complex-sql/input.sql` |
| `postgres-business-user-coupons-delete-derived-join-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/postgres/postgres-business-user-coupons-delete-derived-join-sql/input.sql` |
| `postgres-business-user-coupons-delete-exists-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/postgres/postgres-business-user-coupons-delete-exists-sql/input.sql` |
| `postgres-edge-cases-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/postgres/postgres-edge-cases-sql/input.sql` |
| `postgres-extreme-nesting-withrelation-sql` | PostgreSQL token-event root | SQL | token-event | - | 4 | 0 | `test-fixtures/correctness/postgres/postgres-extreme-nesting-withrelation-sql/input.sql` |
| `postgres-extreme-nesting-withrelation-withlineage-sql` | PostgreSQL token-event root | SQL | token-event | - | 3 | 3 | `test-fixtures/correctness/postgres/postgres-extreme-nesting-withrelation-withlineage-sql/input.sql` |
| `postgres-official-alter-index-boundary-ddl` | PostgreSQL token-event root | DDL | token-event | - | 1 | 0 | `test-fixtures/correctness/postgres/postgres-official-alter-index-boundary-ddl/input.ddl.sql` |
| `postgres-official-cte-dml-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/postgres/postgres-official-cte-dml-sql/input.sql` |
| `postgres-official-cte-nested-sql` | PostgreSQL token-event root | SQL | token-event | - | 4 | 0 | `test-fixtures/correctness/postgres/postgres-official-cte-nested-sql/input.sql` |
| `postgres-official-expression-access-method-index-ddl` | PostgreSQL token-event root | DDL | token-event | - | 1 | 0 | `test-fixtures/correctness/postgres/postgres-official-expression-access-method-index-ddl/input.ddl.sql` |
| `postgres-official-index-include-partial-ddl` | PostgreSQL token-event root | DDL | token-event | - | 1 | 0 | `test-fixtures/correctness/postgres/postgres-official-index-include-partial-ddl/input.ddl.sql` |
| `postgres-official-index-opclass-expression-ddl` | PostgreSQL token-event root | DDL | token-event | - | 1 | 0 | `test-fixtures/correctness/postgres/postgres-official-index-opclass-expression-ddl/input.ddl.sql` |
| `postgres-official-index-options-ddl` | PostgreSQL token-event root | DDL | token-event | - | 1 | 0 | `test-fixtures/correctness/postgres/postgres-official-index-options-ddl/input.ddl.sql` |
| `postgres-official-index-partition-boundary-ddl` | PostgreSQL token-event root | DDL | token-event | - | 1 | 0 | `test-fixtures/correctness/postgres/postgres-official-index-partition-boundary-ddl/input.ddl.sql` |
| `postgres-official-index-storage-ddl` | PostgreSQL token-event root | DDL | token-event | - | 1 | 0 | `test-fixtures/correctness/postgres/postgres-official-index-storage-ddl/input.ddl.sql` |
| `postgres-official-join-edge-sql` | PostgreSQL token-event root | SQL | token-event | - | 5 | 0 | `test-fixtures/correctness/postgres/postgres-official-join-edge-sql/input.sql` |
| `postgres-official-lateral-function-sql` | PostgreSQL token-event root | SQL | token-event | - | 2 | 0 | `test-fixtures/correctness/postgres/postgres-official-lateral-function-sql/input.sql` |
| `postgres-official-lateral-nested-join-sql` | PostgreSQL token-event root | SQL | token-event | - | 1 | 0 | `test-fixtures/correctness/postgres/postgres-official-lateral-nested-join-sql/input.sql` |
| `postgres-official-multiway-join-sql` | PostgreSQL token-event root | SQL | token-event | - | 9 | 0 | `test-fixtures/correctness/postgres/postgres-official-multiway-join-sql/input.sql` |
| `postgres-official-subquery-deep-sql` | PostgreSQL token-event root | SQL | token-event | - | 5 | 0 | `test-fixtures/correctness/postgres/postgres-official-subquery-deep-sql/input.sql` |
| `postgres-official-subquery-edge-sql` | PostgreSQL token-event root | SQL | token-event | - | 5 | 0 | `test-fixtures/correctness/postgres/postgres-official-subquery-edge-sql/input.sql` |
| `postgres-pg10-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/postgres/postgres-pg10-sql/input.sql` |
| `postgres-pg11-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/postgres/postgres-pg11-sql/input.sql` |
| `postgres-pg12-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/postgres/postgres-pg12-sql/input.sql` |
| `postgres-pg13-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/postgres/postgres-pg13-sql/input.sql` |
| `postgres-pg14-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/postgres/postgres-pg14-sql/input.sql` |
| `postgres-pg15-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/postgres/postgres-pg15-sql/input.sql` |
| `postgres-pg16-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/postgres/postgres-pg16-sql/input.sql` |
| `postgres-pg17-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/postgres/postgres-pg17-sql/input.sql` |
| `postgressample-data-full-01-schema-01-tables-ddl` | PostgreSQL token-event root | DDL | token-event | - | 26 | 0 | `sample-data/postgres/18/01-schema/01-tables.sql` |
| `postgressample-data-full-01-schema-02-indexes-and-views-ddl` | PostgreSQL token-event root | DDL | token-event | - | 0 | 0 | `sample-data/postgres/18/01-schema/02-indexes-and-views.sql` |
| `postgressample-data-full-01-schema-02-indexes-and-views-views-sql` | PostgreSQL token-event root | SQL | token-event | - | 9 | 0 | `test-fixtures/correctness/postgres/postgres-sample-data-full-01-schema-02-indexes-and-views-views-sql/input.sql` |
| `postgressample-data-full-01-schema-03-triggers-sql` | PostgreSQL token-event root | SQL | token-event | - | 1 | 4 | `test-fixtures/correctness/postgres/postgres-sample-data-full-01-schema-03-triggers-sql/input.sql` |
| `postgressample-data-full-01-schema-04-supplementary-tables-ddl` | PostgreSQL token-event root | DDL | token-event | - | 28 | 0 | `sample-data/postgres/18/01-schema/04-supplementary-tables.sql` |
| `postgressample-data-full-01-schema-05-third-batch-tables-ddl` | PostgreSQL token-event root | DDL | token-event | - | 30 | 0 | `sample-data/postgres/18/01-schema/05-third-batch-tables.sql` |
| `postgressample-data-full-02-procedures-01-procedures-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/postgres/postgres-sample-data-full-02-procedures-01-procedures-sql/input.sql` |
| `postgressample-data-full-02-procedures-02-procedures-supplement-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/postgres/postgres-sample-data-full-02-procedures-02-procedures-supplement-sql/input.sql` |
| `postgressample-data-full-02-procedures-03-functions-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/postgres/postgres-sample-data-full-02-procedures-03-functions-sql/input.sql` |
| `postgressample-data-full-02-procedures-04-procedures-supplement-sql` | PostgreSQL token-event root | SQL | token-event | - | 2 | 7 | `test-fixtures/correctness/postgres/postgres-sample-data-full-02-procedures-04-procedures-supplement-sql/input.sql` |
| `postgressample-data-full-02-procedures-05-third-batch-procedures-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/postgres/postgres-sample-data-full-02-procedures-05-third-batch-procedures-sql/input.sql` |
| `postgressample-data-full-02-procedures-06-third-batch-functions-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/postgres/postgres-sample-data-full-02-procedures-06-third-batch-functions-sql/input.sql` |
| `postgressample-data-full-02-procedures-07-store-customer-procedures-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/postgres/postgres-sample-data-full-02-procedures-07-store-customer-procedures-sql/input.sql` |
| `postgressample-data-full-02-procedures-08-batch-expiry-procedures-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/postgres/postgres-sample-data-full-02-procedures-08-batch-expiry-procedures-sql/input.sql` |
| `postgressample-data-full-02-procedures-09-return-refund-procedures-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/postgres/postgres-sample-data-full-02-procedures-09-return-refund-procedures-sql/input.sql` |
| `postgressample-data-full-02-procedures-10-supplier-geo-procedures-sql` | PostgreSQL token-event root | SQL | token-event | - | 3 | 0 | `test-fixtures/correctness/postgres/postgres-sample-data-full-02-procedures-10-supplier-geo-procedures-sql/input.sql` |
| `postgressample-data-full-02-procedures-11-common-system-procedures-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/postgres/postgres-sample-data-full-02-procedures-11-common-system-procedures-sql/input.sql` |
| `postgressample-data-full-02-procedures-12-enterprise-extension-procedures-sql` | PostgreSQL token-event root | SQL | token-event | - | 3 | 8 | `test-fixtures/correctness/postgres/postgres-sample-data-full-02-procedures-12-enterprise-extension-procedures-sql/input.sql` |
| `postgressample-data-full-03-data-01-master-data-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `sample-data/postgres/18/03-data/01-master-data.sql` |
| `postgressample-data-full-03-data-02-supplementary-data-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `sample-data/postgres/18/03-data/02-supplementary-data.sql` |
| `postgressample-data-full-03-data-03-enterprise-extension-data-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `sample-data/postgres/18/03-data/03-enterprise-extension-data.sql` |
| `postgressample-data-full-04-queries-01-complex-queries-sql` | PostgreSQL token-event root | SQL | token-event | - | 7 | 0 | `sample-data/postgres/18/04-queries/01-complex-queries.sql` |
| `postgressample-data-full-04-queries-02-complex-queries-batch2-sql` | PostgreSQL token-event root | SQL | token-event | - | 2 | 0 | `sample-data/postgres/18/04-queries/02-complex-queries-batch2.sql` |
| `postgressample-data-full-04-queries-03-complex-queries-batch3-sql` | PostgreSQL token-event root | SQL | token-event | - | 1 | 0 | `sample-data/postgres/18/04-queries/03-complex-queries-batch3.sql` |
| `postgressample-data-full-04-queries-04-store-customer-queries-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `sample-data/postgres/18/04-queries/04-store-customer-queries.sql` |
| `postgressample-data-full-04-queries-05-batch-expiry-queries-sql` | PostgreSQL token-event root | SQL | token-event | - | 4 | 0 | `sample-data/postgres/18/04-queries/05-batch-expiry-queries.sql` |
| `postgressample-data-full-04-queries-06-return-damage-queries-sql` | PostgreSQL token-event root | SQL | token-event | - | 2 | 0 | `sample-data/postgres/18/04-queries/06-return-damage-queries.sql` |
| `postgressample-data-full-04-queries-07-supplier-analysis-queries-sql` | PostgreSQL token-event root | SQL | token-event | - | 3 | 0 | `sample-data/postgres/18/04-queries/07-supplier-analysis-queries.sql` |
| `postgressample-data-full-04-queries-08-common-system-queries-sql` | PostgreSQL token-event root | SQL | token-event | - | 10 | 0 | `sample-data/postgres/18/04-queries/08-common-system-queries.sql` |
| `postgres-sample-data-enterprise-extension-ddl` | PostgreSQL token-event root | DDL | token-event | - | 30 | 0 | `sample-data/postgres/18/01-schema/06-enterprise-extension-tables.sql` |
| `postgres-sample-data-enterprise-extension-queries-sql` | PostgreSQL token-event root | SQL | token-event | - | 13 | 0 | `sample-data/postgres/18/04-queries/10-enterprise-extension-queries.sql` |
| `postgres-sample-data-enterprise-procedures-sql` | PostgreSQL token-event root | SQL | token-event | - | 3 | 8 | `test-fixtures/correctness/postgres/sample-data-enterprise-procedures-sql/input.sql` |
| `postgres-sample-data-pg18-specific-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `sample-data/postgres/18/04-queries/11-pg18-specific.sql` |
| `postgres-sample-data-real-world-scenarios-sql` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `sample-data/postgres/18/04-queries/09-real-world-scenarios.sql` |
| `postgres-sql-delete-using-no-alias` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/postgres/sql-delete-using-no-alias/input.sql` |
| `postgres-sql-lateral-derived` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/postgres/sql-lateral-derived/input.sql` |
| `postgres-sql-merge-using` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/postgres/sql-merge-using/input.sql` |
| `postgres-sql-multi-layer-cte` | PostgreSQL token-event root | SQL | token-event | - | 3 | 0 | `test-fixtures/correctness/postgres/sql-multi-layer-cte/input.sql` |
| `postgres-sql-quoted-mixed-alias` | PostgreSQL token-event root | SQL | token-event | - | 2 | 0 | `test-fixtures/correctness/postgres/sql-quoted-mixed-alias/input.sql` |
| `postgres-sql-recursive-cte` | PostgreSQL token-event root | SQL | token-event | - | 0 | 0 | `test-fixtures/correctness/postgres/sql-recursive-cte/input.sql` |
| `postgres-sql-unnest-ordinality` | PostgreSQL token-event root | SQL | token-event | - | 1 | 0 | `test-fixtures/correctness/postgres/sql-unnest-ordinality/input.sql` |
| `postgres-sql-update-from-aliases` | PostgreSQL token-event root | SQL | token-event | - | 1 | 0 | `test-fixtures/correctness/postgres/sql-update-from-aliases/input.sql` |
| `postgres16-postgres-ddl-alter-table-fk` | PostgreSQL full-grammer v16 | DDL | full-grammer | postgresql/16 | 1 | 0 | `test-fixtures/correctness/postgres/v16/ddl-alter-table-fk/input.ddl.sql` |
| `postgres16-postgres-ddl-partial-index-boundary` | PostgreSQL full-grammer v16 | DDL | full-grammer | postgresql/16 | 1 | 0 | `test-fixtures/correctness/postgres/v16/ddl-partial-index-boundary/input.ddl.sql` |
| `postgres16-postgres-ddl-unique-include-index` | PostgreSQL full-grammer v16 | DDL | full-grammer | postgresql/16 | 2 | 0 | `test-fixtures/correctness/postgres/v16/ddl-unique-include-index/input.ddl.sql` |
| `postgres16-postgres-generated-comprehensive-query-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 53 | 0 | `test-fixtures/examples/comprehensive-query-sql-001-030.sql` |
| `postgres16-postgres-generated-industrial-complex-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 9 | 0 | `test-fixtures/examples/industrial-complex-sql-001-050.sql` |
| `postgres16-postgres-generated-provided-complex-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 10 | 0 | `test-fixtures/correctness/postgres/v16/generated-provided-complex-sql/input.sql` |
| `postgres16-postgres-basic-correctness-case-01-ddl` | PostgreSQL full-grammer v16 | DDL | full-grammer | postgresql/16 | 516 | 0 | `test-fixtures/correctness/postgres/v16/postgres-basic-correctness-case-01-ddl/input.ddl.sql` |
| `postgres16-postgres-basic-correctness-case-01-objects-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 0 | 0 | `test-fixtures/correctness/postgres/v16/postgres-basic-correctness-case-01-objects-sql/input.sql` |
| `postgres16-postgres-basic-correctness-case-01-statements-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 0 | 0 | `test-fixtures/correctness/postgres/v16/postgres-basic-correctness-case-01-statements-sql/input.sql` |
| `postgres16-postgres-business-account-balances-financial-cte-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 3 | 3 | `test-fixtures/correctness/postgres/v16/postgres-business-account-balances-financial-cte-sql/input.sql` |
| `postgres16-postgres-business-account-balances-financial-explicit-join-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 3 | 3 | `test-fixtures/correctness/postgres/v16/postgres-business-account-balances-financial-explicit-join-sql/input.sql` |
| `postgres16-postgres-business-asset-balances-update-outer-join-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 4 | 3 | `test-fixtures/correctness/postgres/v16/postgres-business-asset-balances-update-outer-join-sql/input.sql` |
| `postgres16-postgres-business-cross-border-reconciliation-function-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 0 | 0 | `test-fixtures/correctness/postgres/v16/postgres-business-cross-border-reconciliation-function-sql/input.sql` |
| `postgres16-postgres-business-delete-cascade-cte-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 1 | 0 | `test-fixtures/correctness/postgres/v16/postgres-business-delete-cascade-cte-sql/input.sql` |
| `postgres16-postgres-business-delete-orphan-left-join-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 1 | 0 | `test-fixtures/correctness/postgres/v16/postgres-business-delete-orphan-left-join-sql/input.sql` |
| `postgres16-postgres-business-delete-orphan-not-exists-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 1 | 0 | `test-fixtures/correctness/postgres/v16/postgres-business-delete-orphan-not-exists-sql/input.sql` |
| `postgres16-postgres-business-inventory-purge-deep-subquery-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 4 | 0 | `test-fixtures/correctness/postgres/v16/postgres-business-inventory-purge-deep-subquery-sql/input.sql` |
| `postgres16-postgres-business-inventory-purge-exists-equivalent-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 4 | 0 | `test-fixtures/correctness/postgres/v16/postgres-business-inventory-purge-exists-equivalent-sql/input.sql` |
| `postgres16-postgres-business-risk-ledger-update-cte-comma-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 2 | 1 | `test-fixtures/correctness/postgres/v16/postgres-business-risk-ledger-update-cte-comma-sql/input.sql` |
| `postgres16-postgres-business-risk-ledger-update-cte-explicit-join-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 2 | 1 | `test-fixtures/correctness/postgres/v16/postgres-business-risk-ledger-update-cte-explicit-join-sql/input.sql` |
| `postgres16-postgres-business-risk-settlement-function-comma-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 0 | 0 | `test-fixtures/correctness/postgres/v16/postgres-business-risk-settlement-function-comma-sql/input.sql` |
| `postgres16-postgres-business-risk-settlement-function-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 0 | 0 | `test-fixtures/correctness/postgres/v16/postgres-business-risk-settlement-function-sql/input.sql` |
| `postgres16-postgres-business-update-inventory-comma-join-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 2 | 2 | `test-fixtures/correctness/postgres/v16/postgres-business-update-inventory-comma-join-sql/input.sql` |
| `postgres16-postgres-business-update-inventory-from-join-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 2 | 2 | `test-fixtures/correctness/postgres/v16/postgres-business-update-inventory-from-join-sql/input.sql` |
| `postgres16-postgres-business-update-products-comma-join-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 2 | 1 | `test-fixtures/correctness/postgres/v16/postgres-business-update-products-comma-join-sql/input.sql` |
| `postgres16-postgres-business-update-products-from-join-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 2 | 1 | `test-fixtures/correctness/postgres/v16/postgres-business-update-products-from-join-sql/input.sql` |
| `postgres16-postgres-business-update-users-aggregate-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 1 | 2 | `test-fixtures/correctness/postgres/v16/postgres-business-update-users-aggregate-sql/input.sql` |
| `postgres16-postgres-business-update-users-scalar-subquery-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 1 | 2 | `test-fixtures/correctness/postgres/v16/postgres-business-update-users-scalar-subquery-sql/input.sql` |
| `postgres16-postgres-business-update-warehouse-comma-subquery-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 7 | 2 | `test-fixtures/correctness/postgres/v16/postgres-business-update-warehouse-comma-subquery-sql/input.sql` |
| `postgres16-postgres-business-update-warehouse-complex-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 7 | 2 | `test-fixtures/correctness/postgres/v16/postgres-business-update-warehouse-complex-sql/input.sql` |
| `postgres16-postgres-business-user-coupons-delete-derived-join-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 3 | 0 | `test-fixtures/correctness/postgres/v16/postgres-business-user-coupons-delete-derived-join-sql/input.sql` |
| `postgres16-postgres-business-user-coupons-delete-exists-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 3 | 0 | `test-fixtures/correctness/postgres/v16/postgres-business-user-coupons-delete-exists-sql/input.sql` |
| `postgres16-postgres-official-alter-index-boundary-ddl` | PostgreSQL full-grammer v16 | DDL | full-grammer | postgresql/16 | 1 | 0 | `test-fixtures/correctness/postgres/v16/postgres-official-alter-index-boundary-ddl/input.ddl.sql` |
| `postgres16-postgres-official-cte-dml-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 5 | 1 | `test-fixtures/correctness/postgres/v16/postgres-official-cte-dml-sql/input.sql` |
| `postgres16-postgres-official-cte-nested-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 5 | 0 | `test-fixtures/correctness/postgres/v16/postgres-official-cte-nested-sql/input.sql` |
| `postgres16-postgres-official-expression-access-method-index-ddl` | PostgreSQL full-grammer v16 | DDL | full-grammer | postgresql/16 | 1 | 0 | `test-fixtures/correctness/postgres/v16/postgres-official-expression-access-method-index-ddl/input.ddl.sql` |
| `postgres16-postgres-official-index-include-partial-ddl` | PostgreSQL full-grammer v16 | DDL | full-grammer | postgresql/16 | 1 | 0 | `test-fixtures/correctness/postgres/v16/postgres-official-index-include-partial-ddl/input.ddl.sql` |
| `postgres16-postgres-official-index-opclass-expression-ddl` | PostgreSQL full-grammer v16 | DDL | full-grammer | postgresql/16 | 1 | 0 | `test-fixtures/correctness/postgres/v16/postgres-official-index-opclass-expression-ddl/input.ddl.sql` |
| `postgres16-postgres-official-index-options-ddl` | PostgreSQL full-grammer v16 | DDL | full-grammer | postgresql/16 | 1 | 0 | `test-fixtures/correctness/postgres/v16/postgres-official-index-options-ddl/input.ddl.sql` |
| `postgres16-postgres-official-index-partition-boundary-ddl` | PostgreSQL full-grammer v16 | DDL | full-grammer | postgresql/16 | 1 | 0 | `test-fixtures/correctness/postgres/v16/postgres-official-index-partition-boundary-ddl/input.ddl.sql` |
| `postgres16-postgres-official-index-storage-ddl` | PostgreSQL full-grammer v16 | DDL | full-grammer | postgresql/16 | 1 | 0 | `test-fixtures/correctness/postgres/v16/postgres-official-index-storage-ddl/input.ddl.sql` |
| `postgres16-postgres-official-join-edge-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 6 | 0 | `test-fixtures/correctness/postgres/v16/postgres-official-join-edge-sql/input.sql` |
| `postgres16-postgres-official-lateral-function-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 2 | 0 | `test-fixtures/correctness/postgres/v16/postgres-official-lateral-function-sql/input.sql` |
| `postgres16-postgres-official-lateral-nested-join-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 3 | 0 | `test-fixtures/correctness/postgres/v16/postgres-official-lateral-nested-join-sql/input.sql` |
| `postgres16-postgres-official-multiway-join-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 11 | 0 | `test-fixtures/correctness/postgres/v16/postgres-official-multiway-join-sql/input.sql` |
| `postgres16-postgres-official-subquery-deep-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 11 | 0 | `test-fixtures/correctness/postgres/v16/postgres-official-subquery-deep-sql/input.sql` |
| `postgres16-postgres-official-subquery-edge-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 9 | 0 | `test-fixtures/correctness/postgres/v16/postgres-official-subquery-edge-sql/input.sql` |
| `postgres16-edge-cases-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 4 | 0 | `test-fixtures/correctness/postgres/v16/postgres16-edge-cases-sql/input.sql` |
| `postgres16-extreme-nesting-withrelation-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 8 | 0 | `test-fixtures/correctness/postgres/v16/postgres16-extreme-nesting-withrelation-sql/input.sql` |
| `postgres16-extreme-nesting-withrelation-withlineage-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 7 | 3 | `test-fixtures/correctness/postgres/v16/postgres16-extreme-nesting-withrelation-withlineage-sql/input.sql` |
| `postgres16-pg10-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 3 | 0 | `test-fixtures/correctness/postgres/v16/postgres16-pg10-sql/input.sql` |
| `postgres16-pg11-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 0 | 0 | `test-fixtures/correctness/postgres/v16/postgres16-pg11-sql/input.sql` |
| `postgres16-pg12-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 3 | 0 | `test-fixtures/correctness/postgres/v16/postgres16-pg12-sql/input.sql` |
| `postgres16-pg13-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 1 | 0 | `test-fixtures/correctness/postgres/v16/postgres16-pg13-sql/input.sql` |
| `postgres16-pg14-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 0 | 0 | `test-fixtures/correctness/postgres/v16/postgres16-pg14-sql/input.sql` |
| `postgres16-pg15-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 2 | 7 | `test-fixtures/correctness/postgres/v16/postgres16-pg15-sql/input.sql` |
| `postgres16-pg16-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 0 | 0 | `test-fixtures/correctness/postgres/v16/postgres16-pg16-sql/input.sql` |
| `postgres16-pg17-version-boundary-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 0 | 0 | `test-fixtures/correctness/postgres/v16/postgres16-pg17-version-boundary-sql/input.sql` |
| `postgres16-sample-data-enterprise-extension-ddl` | PostgreSQL full-grammer v16 | DDL | full-grammer | postgresql/16 | 31 | 0 | `sample-data/postgres/18/01-schema/06-enterprise-extension-tables.sql` |
| `postgres16-sample-data-enterprise-extension-queries-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 17 | 0 | `sample-data/postgres/18/04-queries/10-enterprise-extension-queries.sql` |
| `postgres16-sample-data-enterprise-procedures-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 3 | 8 | `test-fixtures/correctness/postgres/sample-data-enterprise-procedures-sql/input.sql` |
| `postgres16sample-data-full-01-schema-01-tables-ddl` | PostgreSQL full-grammer v16 | DDL | full-grammer | postgresql/16 | 94 | 0 | `sample-data/postgres/18/01-schema/01-tables.sql` |
| `postgres16sample-data-full-01-schema-02-indexes-and-views-ddl` | PostgreSQL full-grammer v16 | DDL | full-grammer | postgresql/16 | 0 | 0 | `sample-data/postgres/18/01-schema/02-indexes-and-views.sql` |
| `postgres16sample-data-full-01-schema-02-indexes-and-views-views-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 17 | 0 | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-01-schema-02-indexes-and-views-views-sql/input.sql` |
| `postgres16sample-data-full-01-schema-03-triggers-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 1 | 4 | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-01-schema-03-triggers-sql/input.sql` |
| `postgres16sample-data-full-01-schema-04-supplementary-tables-ddl` | PostgreSQL full-grammer v16 | DDL | full-grammer | postgresql/16 | 32 | 0 | `sample-data/postgres/18/01-schema/04-supplementary-tables.sql` |
| `postgres16sample-data-full-01-schema-05-third-batch-tables-ddl` | PostgreSQL full-grammer v16 | DDL | full-grammer | postgresql/16 | 36 | 0 | `sample-data/postgres/18/01-schema/05-third-batch-tables.sql` |
| `postgres16sample-data-full-02-procedures-01-procedures-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 0 | 0 | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-02-procedures-01-procedures-sql/input.sql` |
| `postgres16sample-data-full-02-procedures-02-procedures-supplement-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 0 | 0 | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-02-procedures-02-procedures-supplement-sql/input.sql` |
| `postgres16sample-data-full-02-procedures-03-functions-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 0 | 0 | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-02-procedures-03-functions-sql/input.sql` |
| `postgres16sample-data-full-02-procedures-04-procedures-supplement-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 2 | 7 | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-02-procedures-04-procedures-supplement-sql/input.sql` |
| `postgres16sample-data-full-02-procedures-05-third-batch-procedures-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 0 | 0 | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-02-procedures-05-third-batch-procedures-sql/input.sql` |
| `postgres16sample-data-full-02-procedures-06-third-batch-functions-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 0 | 0 | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-02-procedures-06-third-batch-functions-sql/input.sql` |
| `postgres16sample-data-full-02-procedures-07-store-customer-procedures-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 0 | 0 | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-02-procedures-07-store-customer-procedures-sql/input.sql` |
| `postgres16sample-data-full-02-procedures-08-batch-expiry-procedures-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 0 | 0 | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-02-procedures-08-batch-expiry-procedures-sql/input.sql` |
| `postgres16sample-data-full-02-procedures-09-return-refund-procedures-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 0 | 0 | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-02-procedures-09-return-refund-procedures-sql/input.sql` |
| `postgres16sample-data-full-02-procedures-10-supplier-geo-procedures-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 3 | 0 | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-02-procedures-10-supplier-geo-procedures-sql/input.sql` |
| `postgres16sample-data-full-02-procedures-11-common-system-procedures-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 0 | 0 | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-02-procedures-11-common-system-procedures-sql/input.sql` |
| `postgres16sample-data-full-02-procedures-12-enterprise-extension-procedures-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 3 | 8 | `test-fixtures/correctness/postgres/v16/postgres16-sample-data-full-02-procedures-12-enterprise-extension-procedures-sql/input.sql` |
| `postgres16sample-data-full-03-data-01-master-data-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 0 | 0 | `sample-data/postgres/18/03-data/01-master-data.sql` |
| `postgres16sample-data-full-03-data-02-supplementary-data-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 0 | 0 | `sample-data/postgres/18/03-data/02-supplementary-data.sql` |
| `postgres16sample-data-full-03-data-03-enterprise-extension-data-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 0 | 0 | `sample-data/postgres/18/03-data/03-enterprise-extension-data.sql` |
| `postgres16sample-data-full-04-queries-01-complex-queries-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 35 | 0 | `sample-data/postgres/18/04-queries/01-complex-queries.sql` |
| `postgres16sample-data-full-04-queries-02-complex-queries-batch2-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 20 | 0 | `sample-data/postgres/18/04-queries/02-complex-queries-batch2.sql` |
| `postgres16sample-data-full-04-queries-03-complex-queries-batch3-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 20 | 0 | `sample-data/postgres/18/04-queries/03-complex-queries-batch3.sql` |
| `postgres16sample-data-full-04-queries-04-store-customer-queries-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 8 | 0 | `sample-data/postgres/18/04-queries/04-store-customer-queries.sql` |
| `postgres16sample-data-full-04-queries-05-batch-expiry-queries-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 14 | 0 | `sample-data/postgres/18/04-queries/05-batch-expiry-queries.sql` |
| `postgres16sample-data-full-04-queries-06-return-damage-queries-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 16 | 0 | `sample-data/postgres/18/04-queries/06-return-damage-queries.sql` |
| `postgres16sample-data-full-04-queries-07-supplier-analysis-queries-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 3 | 0 | `sample-data/postgres/18/04-queries/07-supplier-analysis-queries.sql` |
| `postgres16sample-data-full-04-queries-08-common-system-queries-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 34 | 0 | `sample-data/postgres/18/04-queries/08-common-system-queries.sql` |
| `postgres16-sample-data-pg18-specific-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 0 | 0 | `sample-data/postgres/18/04-queries/11-pg18-specific.sql` |
| `postgres16-sample-data-real-world-scenarios-sql` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 53 | 0 | `sample-data/postgres/18/04-queries/09-real-world-scenarios.sql` |
| `postgres16-postgres-sql-delete-using-no-alias` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 1 | 0 | `test-fixtures/correctness/postgres/v16/sql-delete-using-no-alias/input.sql` |
| `postgres16-postgres-sql-lateral-derived` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 1 | 0 | `test-fixtures/correctness/postgres/v16/sql-lateral-derived/input.sql` |
| `postgres16-postgres-sql-merge-using` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 1 | 1 | `test-fixtures/correctness/postgres/v16/sql-merge-using/input.sql` |
| `postgres16-postgres-sql-multi-layer-cte` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 3 | 0 | `test-fixtures/correctness/postgres/v16/sql-multi-layer-cte/input.sql` |
| `postgres16-postgres-sql-quoted-mixed-alias` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 2 | 0 | `test-fixtures/correctness/postgres/v16/sql-quoted-mixed-alias/input.sql` |
| `postgres16-postgres-sql-recursive-cte` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 1 | 0 | `test-fixtures/correctness/postgres/v16/sql-recursive-cte/input.sql` |
| `postgres16-postgres-sql-unnest-ordinality` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 1 | 0 | `test-fixtures/correctness/postgres/v16/sql-unnest-ordinality/input.sql` |
| `postgres16-postgres-sql-update-from-aliases` | PostgreSQL full-grammer v16 | SQL | full-grammer | postgresql/16 | 1 | 0 | `test-fixtures/correctness/postgres/v16/sql-update-from-aliases/input.sql` |
| `postgres17-ddl-alter-table-fk` | PostgreSQL full-grammer v17 | DDL | full-grammer | postgresql/17 | 1 | 0 | `test-fixtures/correctness/postgres/v17/ddl-alter-table-fk/input.ddl.sql` |
| `postgres17-ddl-partial-index-boundary` | PostgreSQL full-grammer v17 | DDL | full-grammer | postgresql/17 | 1 | 0 | `test-fixtures/correctness/postgres/v17/ddl-partial-index-boundary/input.ddl.sql` |
| `postgres17-ddl-unique-include-index` | PostgreSQL full-grammer v17 | DDL | full-grammer | postgresql/17 | 2 | 0 | `test-fixtures/correctness/postgres/v17/ddl-unique-include-index/input.ddl.sql` |
| `postgres17-generated-comprehensive-query-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 53 | 0 | `test-fixtures/examples/comprehensive-query-sql-001-030.sql` |
| `postgres17-generated-industrial-complex-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 9 | 0 | `test-fixtures/examples/industrial-complex-sql-001-050.sql` |
| `postgres17-generated-provided-complex-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 10 | 0 | `test-fixtures/correctness/postgres/v17/generated-provided-complex-sql/input.sql` |
| `postgres17-basic-correctness-case-01-ddl` | PostgreSQL full-grammer v17 | DDL | full-grammer | postgresql/17 | 516 | 0 | `test-fixtures/correctness/postgres/v17/postgres-basic-correctness-case-01-ddl/input.ddl.sql` |
| `postgres17-basic-correctness-case-01-objects-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 0 | 0 | `test-fixtures/correctness/postgres/v17/postgres-basic-correctness-case-01-objects-sql/input.sql` |
| `postgres17-basic-correctness-case-01-statements-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 0 | 0 | `test-fixtures/correctness/postgres/v17/postgres-basic-correctness-case-01-statements-sql/input.sql` |
| `postgres17-business-account-balances-financial-cte-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 3 | 3 | `test-fixtures/correctness/postgres/v17/postgres-business-account-balances-financial-cte-sql/input.sql` |
| `postgres17-business-account-balances-financial-explicit-join-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 3 | 3 | `test-fixtures/correctness/postgres/v17/postgres-business-account-balances-financial-explicit-join-sql/input.sql` |
| `postgres17-business-asset-balances-update-outer-join-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 4 | 3 | `test-fixtures/correctness/postgres/v17/postgres-business-asset-balances-update-outer-join-sql/input.sql` |
| `postgres17-business-cross-border-reconciliation-function-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 0 | 0 | `test-fixtures/correctness/postgres/v17/postgres-business-cross-border-reconciliation-function-sql/input.sql` |
| `postgres17-business-delete-cascade-cte-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 1 | 0 | `test-fixtures/correctness/postgres/v17/postgres-business-delete-cascade-cte-sql/input.sql` |
| `postgres17-business-delete-orphan-left-join-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 1 | 0 | `test-fixtures/correctness/postgres/v17/postgres-business-delete-orphan-left-join-sql/input.sql` |
| `postgres17-business-delete-orphan-not-exists-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 1 | 0 | `test-fixtures/correctness/postgres/v17/postgres-business-delete-orphan-not-exists-sql/input.sql` |
| `postgres17-business-inventory-purge-deep-subquery-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 4 | 0 | `test-fixtures/correctness/postgres/v17/postgres-business-inventory-purge-deep-subquery-sql/input.sql` |
| `postgres17-business-inventory-purge-exists-equivalent-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 4 | 0 | `test-fixtures/correctness/postgres/v17/postgres-business-inventory-purge-exists-equivalent-sql/input.sql` |
| `postgres17-business-risk-ledger-update-cte-comma-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 2 | 1 | `test-fixtures/correctness/postgres/v17/postgres-business-risk-ledger-update-cte-comma-sql/input.sql` |
| `postgres17-business-risk-ledger-update-cte-explicit-join-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 2 | 1 | `test-fixtures/correctness/postgres/v17/postgres-business-risk-ledger-update-cte-explicit-join-sql/input.sql` |
| `postgres17-business-risk-settlement-function-comma-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 0 | 0 | `test-fixtures/correctness/postgres/v17/postgres-business-risk-settlement-function-comma-sql/input.sql` |
| `postgres17-business-risk-settlement-function-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 0 | 0 | `test-fixtures/correctness/postgres/v17/postgres-business-risk-settlement-function-sql/input.sql` |
| `postgres17-business-update-inventory-comma-join-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 2 | 2 | `test-fixtures/correctness/postgres/v17/postgres-business-update-inventory-comma-join-sql/input.sql` |
| `postgres17-business-update-inventory-from-join-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 2 | 2 | `test-fixtures/correctness/postgres/v17/postgres-business-update-inventory-from-join-sql/input.sql` |
| `postgres17-business-update-products-comma-join-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 2 | 1 | `test-fixtures/correctness/postgres/v17/postgres-business-update-products-comma-join-sql/input.sql` |
| `postgres17-business-update-products-from-join-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 2 | 1 | `test-fixtures/correctness/postgres/v17/postgres-business-update-products-from-join-sql/input.sql` |
| `postgres17-business-update-users-aggregate-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 1 | 2 | `test-fixtures/correctness/postgres/v17/postgres-business-update-users-aggregate-sql/input.sql` |
| `postgres17-business-update-users-scalar-subquery-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 1 | 2 | `test-fixtures/correctness/postgres/v17/postgres-business-update-users-scalar-subquery-sql/input.sql` |
| `postgres17-business-update-warehouse-comma-subquery-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 7 | 2 | `test-fixtures/correctness/postgres/v17/postgres-business-update-warehouse-comma-subquery-sql/input.sql` |
| `postgres17-business-update-warehouse-complex-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 7 | 2 | `test-fixtures/correctness/postgres/v17/postgres-business-update-warehouse-complex-sql/input.sql` |
| `postgres17-business-user-coupons-delete-derived-join-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 3 | 0 | `test-fixtures/correctness/postgres/v17/postgres-business-user-coupons-delete-derived-join-sql/input.sql` |
| `postgres17-business-user-coupons-delete-exists-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 3 | 0 | `test-fixtures/correctness/postgres/v17/postgres-business-user-coupons-delete-exists-sql/input.sql` |
| `postgres17-official-alter-index-boundary-ddl` | PostgreSQL full-grammer v17 | DDL | full-grammer | postgresql/17 | 1 | 0 | `test-fixtures/correctness/postgres/v17/postgres-official-alter-index-boundary-ddl/input.ddl.sql` |
| `postgres17-official-cte-dml-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 5 | 1 | `test-fixtures/correctness/postgres/v17/postgres-official-cte-dml-sql/input.sql` |
| `postgres17-official-cte-nested-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 5 | 0 | `test-fixtures/correctness/postgres/v17/postgres-official-cte-nested-sql/input.sql` |
| `postgres17-official-expression-access-method-index-ddl` | PostgreSQL full-grammer v17 | DDL | full-grammer | postgresql/17 | 1 | 0 | `test-fixtures/correctness/postgres/v17/postgres-official-expression-access-method-index-ddl/input.ddl.sql` |
| `postgres17-official-index-include-partial-ddl` | PostgreSQL full-grammer v17 | DDL | full-grammer | postgresql/17 | 1 | 0 | `test-fixtures/correctness/postgres/v17/postgres-official-index-include-partial-ddl/input.ddl.sql` |
| `postgres17-official-index-opclass-expression-ddl` | PostgreSQL full-grammer v17 | DDL | full-grammer | postgresql/17 | 1 | 0 | `test-fixtures/correctness/postgres/v17/postgres-official-index-opclass-expression-ddl/input.ddl.sql` |
| `postgres17-official-index-options-ddl` | PostgreSQL full-grammer v17 | DDL | full-grammer | postgresql/17 | 1 | 0 | `test-fixtures/correctness/postgres/v17/postgres-official-index-options-ddl/input.ddl.sql` |
| `postgres17-official-index-partition-boundary-ddl` | PostgreSQL full-grammer v17 | DDL | full-grammer | postgresql/17 | 1 | 0 | `test-fixtures/correctness/postgres/v17/postgres-official-index-partition-boundary-ddl/input.ddl.sql` |
| `postgres17-official-index-storage-ddl` | PostgreSQL full-grammer v17 | DDL | full-grammer | postgresql/17 | 1 | 0 | `test-fixtures/correctness/postgres/v17/postgres-official-index-storage-ddl/input.ddl.sql` |
| `postgres17-official-join-edge-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 6 | 0 | `test-fixtures/correctness/postgres/v17/postgres-official-join-edge-sql/input.sql` |
| `postgres17-official-lateral-function-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 2 | 0 | `test-fixtures/correctness/postgres/v17/postgres-official-lateral-function-sql/input.sql` |
| `postgres17-official-lateral-nested-join-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 3 | 0 | `test-fixtures/correctness/postgres/v17/postgres-official-lateral-nested-join-sql/input.sql` |
| `postgres17-official-multiway-join-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 11 | 0 | `test-fixtures/correctness/postgres/v17/postgres-official-multiway-join-sql/input.sql` |
| `postgres17-official-subquery-deep-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 11 | 0 | `test-fixtures/correctness/postgres/v17/postgres-official-subquery-deep-sql/input.sql` |
| `postgres17-official-subquery-edge-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 9 | 0 | `test-fixtures/correctness/postgres/v17/postgres-official-subquery-edge-sql/input.sql` |
| `postgres17-edge-cases-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 4 | 0 | `test-fixtures/correctness/postgres/v17/postgres17-edge-cases-sql/input.sql` |
| `postgres17-extreme-nesting-withrelation-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 8 | 0 | `test-fixtures/correctness/postgres/v17/postgres17-extreme-nesting-withrelation-sql/input.sql` |
| `postgres17-extreme-nesting-withrelation-withlineage-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 7 | 3 | `test-fixtures/correctness/postgres/v17/postgres17-extreme-nesting-withrelation-withlineage-sql/input.sql` |
| `postgres17-json-table-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 1 | 0 | `test-fixtures/correctness/postgres/v17/postgres17-json-table-sql/input.sql` |
| `postgres17-merge-returning-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 1 | 2 | `test-fixtures/correctness/postgres/v17/postgres17-merge-returning-sql/input.sql` |
| `postgres17-pg10-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 3 | 0 | `test-fixtures/correctness/postgres/v17/postgres17-pg10-sql/input.sql` |
| `postgres17-pg11-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 0 | 0 | `test-fixtures/correctness/postgres/v17/postgres17-pg11-sql/input.sql` |
| `postgres17-pg12-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 3 | 0 | `test-fixtures/correctness/postgres/v17/postgres17-pg12-sql/input.sql` |
| `postgres17-pg13-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 1 | 0 | `test-fixtures/correctness/postgres/v17/postgres17-pg13-sql/input.sql` |
| `postgres17-pg14-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 0 | 0 | `test-fixtures/correctness/postgres/v17/postgres17-pg14-sql/input.sql` |
| `postgres17-pg15-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 2 | 16 | `test-fixtures/correctness/postgres/v17/postgres17-pg15-sql/input.sql` |
| `postgres17-pg16-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 0 | 0 | `test-fixtures/correctness/postgres/v17/postgres17-pg16-sql/input.sql` |
| `postgres17-pg17-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 1 | 11 | `test-fixtures/correctness/postgres/v17/postgres17-pg17-sql/input.sql` |
| `postgres17-sample-data-enterprise-extension-ddl` | PostgreSQL full-grammer v17 | DDL | full-grammer | postgresql/17 | 31 | 0 | `sample-data/postgres/18/01-schema/06-enterprise-extension-tables.sql` |
| `postgres17-sample-data-enterprise-extension-queries-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 17 | 0 | `sample-data/postgres/18/04-queries/10-enterprise-extension-queries.sql` |
| `postgres17-sample-data-enterprise-procedures-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 3 | 8 | `test-fixtures/correctness/postgres/sample-data-enterprise-procedures-sql/input.sql` |
| `postgres17sample-data-full-01-schema-01-tables-ddl` | PostgreSQL full-grammer v17 | DDL | full-grammer | postgresql/17 | 94 | 0 | `sample-data/postgres/18/01-schema/01-tables.sql` |
| `postgres17sample-data-full-01-schema-02-indexes-and-views-ddl` | PostgreSQL full-grammer v17 | DDL | full-grammer | postgresql/17 | 0 | 0 | `sample-data/postgres/18/01-schema/02-indexes-and-views.sql` |
| `postgres17sample-data-full-01-schema-02-indexes-and-views-views-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 17 | 0 | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-01-schema-02-indexes-and-views-views-sql/input.sql` |
| `postgres17sample-data-full-01-schema-03-triggers-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 1 | 4 | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-01-schema-03-triggers-sql/input.sql` |
| `postgres17sample-data-full-01-schema-04-supplementary-tables-ddl` | PostgreSQL full-grammer v17 | DDL | full-grammer | postgresql/17 | 32 | 0 | `sample-data/postgres/18/01-schema/04-supplementary-tables.sql` |
| `postgres17sample-data-full-01-schema-05-third-batch-tables-ddl` | PostgreSQL full-grammer v17 | DDL | full-grammer | postgresql/17 | 36 | 0 | `sample-data/postgres/18/01-schema/05-third-batch-tables.sql` |
| `postgres17sample-data-full-02-procedures-01-procedures-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 0 | 0 | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-02-procedures-01-procedures-sql/input.sql` |
| `postgres17sample-data-full-02-procedures-02-procedures-supplement-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 0 | 0 | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-02-procedures-02-procedures-supplement-sql/input.sql` |
| `postgres17sample-data-full-02-procedures-03-functions-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 0 | 0 | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-02-procedures-03-functions-sql/input.sql` |
| `postgres17sample-data-full-02-procedures-04-procedures-supplement-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 2 | 7 | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-02-procedures-04-procedures-supplement-sql/input.sql` |
| `postgres17sample-data-full-02-procedures-05-third-batch-procedures-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 0 | 0 | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-02-procedures-05-third-batch-procedures-sql/input.sql` |
| `postgres17sample-data-full-02-procedures-06-third-batch-functions-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 0 | 0 | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-02-procedures-06-third-batch-functions-sql/input.sql` |
| `postgres17sample-data-full-02-procedures-07-store-customer-procedures-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 0 | 0 | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-02-procedures-07-store-customer-procedures-sql/input.sql` |
| `postgres17sample-data-full-02-procedures-08-batch-expiry-procedures-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 0 | 0 | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-02-procedures-08-batch-expiry-procedures-sql/input.sql` |
| `postgres17sample-data-full-02-procedures-09-return-refund-procedures-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 0 | 0 | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-02-procedures-09-return-refund-procedures-sql/input.sql` |
| `postgres17sample-data-full-02-procedures-10-supplier-geo-procedures-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 3 | 0 | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-02-procedures-10-supplier-geo-procedures-sql/input.sql` |
| `postgres17sample-data-full-02-procedures-11-common-system-procedures-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 0 | 0 | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-02-procedures-11-common-system-procedures-sql/input.sql` |
| `postgres17sample-data-full-02-procedures-12-enterprise-extension-procedures-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 3 | 8 | `test-fixtures/correctness/postgres/v17/postgres17-sample-data-full-02-procedures-12-enterprise-extension-procedures-sql/input.sql` |
| `postgres17sample-data-full-03-data-01-master-data-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 0 | 0 | `sample-data/postgres/18/03-data/01-master-data.sql` |
| `postgres17sample-data-full-03-data-02-supplementary-data-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 0 | 0 | `sample-data/postgres/18/03-data/02-supplementary-data.sql` |
| `postgres17sample-data-full-03-data-03-enterprise-extension-data-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 0 | 0 | `sample-data/postgres/18/03-data/03-enterprise-extension-data.sql` |
| `postgres17sample-data-full-04-queries-01-complex-queries-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 35 | 0 | `sample-data/postgres/18/04-queries/01-complex-queries.sql` |
| `postgres17sample-data-full-04-queries-02-complex-queries-batch2-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 20 | 0 | `sample-data/postgres/18/04-queries/02-complex-queries-batch2.sql` |
| `postgres17sample-data-full-04-queries-03-complex-queries-batch3-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 20 | 0 | `sample-data/postgres/18/04-queries/03-complex-queries-batch3.sql` |
| `postgres17sample-data-full-04-queries-04-store-customer-queries-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 8 | 0 | `sample-data/postgres/18/04-queries/04-store-customer-queries.sql` |
| `postgres17sample-data-full-04-queries-05-batch-expiry-queries-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 14 | 0 | `sample-data/postgres/18/04-queries/05-batch-expiry-queries.sql` |
| `postgres17sample-data-full-04-queries-06-return-damage-queries-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 16 | 0 | `sample-data/postgres/18/04-queries/06-return-damage-queries.sql` |
| `postgres17sample-data-full-04-queries-07-supplier-analysis-queries-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 3 | 0 | `sample-data/postgres/18/04-queries/07-supplier-analysis-queries.sql` |
| `postgres17sample-data-full-04-queries-08-common-system-queries-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 34 | 0 | `sample-data/postgres/18/04-queries/08-common-system-queries.sql` |
| `postgres17-sample-data-pg18-specific-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 0 | 0 | `sample-data/postgres/18/04-queries/11-pg18-specific.sql` |
| `postgres17-sample-data-real-world-scenarios-sql` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 53 | 0 | `sample-data/postgres/18/04-queries/09-real-world-scenarios.sql` |
| `postgres17-sql-delete-using-no-alias` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 1 | 0 | `test-fixtures/correctness/postgres/v17/sql-delete-using-no-alias/input.sql` |
| `postgres17-sql-lateral-derived` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 1 | 0 | `test-fixtures/correctness/postgres/v17/sql-lateral-derived/input.sql` |
| `postgres17-sql-merge-using` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 1 | 1 | `test-fixtures/correctness/postgres/v17/sql-merge-using/input.sql` |
| `postgres17-sql-multi-layer-cte` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 3 | 0 | `test-fixtures/correctness/postgres/v17/sql-multi-layer-cte/input.sql` |
| `postgres17-sql-quoted-mixed-alias` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 2 | 0 | `test-fixtures/correctness/postgres/v17/sql-quoted-mixed-alias/input.sql` |
| `postgres17-sql-recursive-cte` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 1 | 0 | `test-fixtures/correctness/postgres/v17/sql-recursive-cte/input.sql` |
| `postgres17-sql-unnest-ordinality` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 1 | 0 | `test-fixtures/correctness/postgres/v17/sql-unnest-ordinality/input.sql` |
| `postgres17-sql-update-from-aliases` | PostgreSQL full-grammer v17 | SQL | full-grammer | postgresql/17 | 1 | 0 | `test-fixtures/correctness/postgres/v17/sql-update-from-aliases/input.sql` |
| `postgres18-ddl-alter-table-fk` | PostgreSQL full-grammer v18 | DDL | full-grammer | postgresql/18 | 1 | 0 | `test-fixtures/correctness/postgres/v18/ddl-alter-table-fk/input.ddl.sql` |
| `postgres18-ddl-partial-index-boundary` | PostgreSQL full-grammer v18 | DDL | full-grammer | postgresql/18 | 1 | 0 | `test-fixtures/correctness/postgres/v18/ddl-partial-index-boundary/input.ddl.sql` |
| `postgres18-ddl-unique-include-index` | PostgreSQL full-grammer v18 | DDL | full-grammer | postgresql/18 | 2 | 0 | `test-fixtures/correctness/postgres/v18/ddl-unique-include-index/input.ddl.sql` |
| `postgres18-generated-comprehensive-query-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 53 | 0 | `test-fixtures/examples/comprehensive-query-sql-001-030.sql` |
| `postgres18-generated-industrial-complex-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 9 | 0 | `test-fixtures/examples/industrial-complex-sql-001-050.sql` |
| `postgres18-generated-provided-complex-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 10 | 0 | `test-fixtures/correctness/postgres/v18/generated-provided-complex-sql/input.sql` |
| `postgres18-basic-correctness-case-01-ddl` | PostgreSQL full-grammer v18 | DDL | full-grammer | postgresql/18 | 516 | 0 | `test-fixtures/correctness/postgres/v18/postgres-basic-correctness-case-01-ddl/input.ddl.sql` |
| `postgres18-basic-correctness-case-01-objects-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 0 | 0 | `test-fixtures/correctness/postgres/v18/postgres-basic-correctness-case-01-objects-sql/input.sql` |
| `postgres18-basic-correctness-case-01-statements-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 0 | 0 | `test-fixtures/correctness/postgres/v18/postgres-basic-correctness-case-01-statements-sql/input.sql` |
| `postgres18-business-account-balances-financial-cte-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 3 | 3 | `test-fixtures/correctness/postgres/v18/postgres-business-account-balances-financial-cte-sql/input.sql` |
| `postgres18-business-account-balances-financial-explicit-join-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 3 | 3 | `test-fixtures/correctness/postgres/v18/postgres-business-account-balances-financial-explicit-join-sql/input.sql` |
| `postgres18-business-asset-balances-update-outer-join-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 4 | 3 | `test-fixtures/correctness/postgres/v18/postgres-business-asset-balances-update-outer-join-sql/input.sql` |
| `postgres18-business-cross-border-reconciliation-function-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 0 | 0 | `test-fixtures/correctness/postgres/v18/postgres-business-cross-border-reconciliation-function-sql/input.sql` |
| `postgres18-business-delete-cascade-cte-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 1 | 0 | `test-fixtures/correctness/postgres/v18/postgres-business-delete-cascade-cte-sql/input.sql` |
| `postgres18-business-delete-orphan-left-join-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 1 | 0 | `test-fixtures/correctness/postgres/v18/postgres-business-delete-orphan-left-join-sql/input.sql` |
| `postgres18-business-delete-orphan-not-exists-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 1 | 0 | `test-fixtures/correctness/postgres/v18/postgres-business-delete-orphan-not-exists-sql/input.sql` |
| `postgres18-business-inventory-purge-deep-subquery-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 4 | 0 | `test-fixtures/correctness/postgres/v18/postgres-business-inventory-purge-deep-subquery-sql/input.sql` |
| `postgres18-business-inventory-purge-exists-equivalent-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 4 | 0 | `test-fixtures/correctness/postgres/v18/postgres-business-inventory-purge-exists-equivalent-sql/input.sql` |
| `postgres18-business-risk-ledger-update-cte-comma-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 2 | 1 | `test-fixtures/correctness/postgres/v18/postgres-business-risk-ledger-update-cte-comma-sql/input.sql` |
| `postgres18-business-risk-ledger-update-cte-explicit-join-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 2 | 1 | `test-fixtures/correctness/postgres/v18/postgres-business-risk-ledger-update-cte-explicit-join-sql/input.sql` |
| `postgres18-business-risk-settlement-function-comma-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 0 | 0 | `test-fixtures/correctness/postgres/v18/postgres-business-risk-settlement-function-comma-sql/input.sql` |
| `postgres18-business-risk-settlement-function-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 0 | 0 | `test-fixtures/correctness/postgres/v18/postgres-business-risk-settlement-function-sql/input.sql` |
| `postgres18-business-update-inventory-comma-join-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 2 | 2 | `test-fixtures/correctness/postgres/v18/postgres-business-update-inventory-comma-join-sql/input.sql` |
| `postgres18-business-update-inventory-from-join-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 2 | 2 | `test-fixtures/correctness/postgres/v18/postgres-business-update-inventory-from-join-sql/input.sql` |
| `postgres18-business-update-products-comma-join-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 2 | 1 | `test-fixtures/correctness/postgres/v18/postgres-business-update-products-comma-join-sql/input.sql` |
| `postgres18-business-update-products-from-join-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 2 | 1 | `test-fixtures/correctness/postgres/v18/postgres-business-update-products-from-join-sql/input.sql` |
| `postgres18-business-update-users-aggregate-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 1 | 2 | `test-fixtures/correctness/postgres/v18/postgres-business-update-users-aggregate-sql/input.sql` |
| `postgres18-business-update-users-scalar-subquery-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 1 | 2 | `test-fixtures/correctness/postgres/v18/postgres-business-update-users-scalar-subquery-sql/input.sql` |
| `postgres18-business-update-warehouse-comma-subquery-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 7 | 2 | `test-fixtures/correctness/postgres/v18/postgres-business-update-warehouse-comma-subquery-sql/input.sql` |
| `postgres18-business-update-warehouse-complex-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 7 | 2 | `test-fixtures/correctness/postgres/v18/postgres-business-update-warehouse-complex-sql/input.sql` |
| `postgres18-business-user-coupons-delete-derived-join-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 3 | 0 | `test-fixtures/correctness/postgres/v18/postgres-business-user-coupons-delete-derived-join-sql/input.sql` |
| `postgres18-business-user-coupons-delete-exists-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 3 | 0 | `test-fixtures/correctness/postgres/v18/postgres-business-user-coupons-delete-exists-sql/input.sql` |
| `postgres18-official-alter-index-boundary-ddl` | PostgreSQL full-grammer v18 | DDL | full-grammer | postgresql/18 | 1 | 0 | `test-fixtures/correctness/postgres/v18/postgres-official-alter-index-boundary-ddl/input.ddl.sql` |
| `postgres18-official-cte-dml-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 5 | 1 | `test-fixtures/correctness/postgres/v18/postgres-official-cte-dml-sql/input.sql` |
| `postgres18-official-cte-nested-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 5 | 0 | `test-fixtures/correctness/postgres/v18/postgres-official-cte-nested-sql/input.sql` |
| `postgres18-official-expression-access-method-index-ddl` | PostgreSQL full-grammer v18 | DDL | full-grammer | postgresql/18 | 1 | 0 | `test-fixtures/correctness/postgres/v18/postgres-official-expression-access-method-index-ddl/input.ddl.sql` |
| `postgres18-official-index-include-partial-ddl` | PostgreSQL full-grammer v18 | DDL | full-grammer | postgresql/18 | 1 | 0 | `test-fixtures/correctness/postgres/v18/postgres-official-index-include-partial-ddl/input.ddl.sql` |
| `postgres18-official-index-opclass-expression-ddl` | PostgreSQL full-grammer v18 | DDL | full-grammer | postgresql/18 | 1 | 0 | `test-fixtures/correctness/postgres/v18/postgres-official-index-opclass-expression-ddl/input.ddl.sql` |
| `postgres18-official-index-options-ddl` | PostgreSQL full-grammer v18 | DDL | full-grammer | postgresql/18 | 1 | 0 | `test-fixtures/correctness/postgres/v18/postgres-official-index-options-ddl/input.ddl.sql` |
| `postgres18-official-index-partition-boundary-ddl` | PostgreSQL full-grammer v18 | DDL | full-grammer | postgresql/18 | 1 | 0 | `test-fixtures/correctness/postgres/v18/postgres-official-index-partition-boundary-ddl/input.ddl.sql` |
| `postgres18-official-index-storage-ddl` | PostgreSQL full-grammer v18 | DDL | full-grammer | postgresql/18 | 1 | 0 | `test-fixtures/correctness/postgres/v18/postgres-official-index-storage-ddl/input.ddl.sql` |
| `postgres18-official-join-edge-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 6 | 0 | `test-fixtures/correctness/postgres/v18/postgres-official-join-edge-sql/input.sql` |
| `postgres18-official-lateral-function-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 2 | 0 | `test-fixtures/correctness/postgres/v18/postgres-official-lateral-function-sql/input.sql` |
| `postgres18-official-lateral-nested-join-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 3 | 0 | `test-fixtures/correctness/postgres/v18/postgres-official-lateral-nested-join-sql/input.sql` |
| `postgres18-official-multiway-join-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 11 | 0 | `test-fixtures/correctness/postgres/v18/postgres-official-multiway-join-sql/input.sql` |
| `postgres18-official-subquery-deep-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 11 | 0 | `test-fixtures/correctness/postgres/v18/postgres-official-subquery-deep-sql/input.sql` |
| `postgres18-official-subquery-edge-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 9 | 0 | `test-fixtures/correctness/postgres/v18/postgres-official-subquery-edge-sql/input.sql` |
| `postgres18-edge-cases-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 4 | 0 | `test-fixtures/correctness/postgres/v18/postgres18-edge-cases-sql/input.sql` |
| `postgres18-extreme-nesting-withrelation-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 8 | 0 | `test-fixtures/correctness/postgres/v18/postgres18-extreme-nesting-withrelation-sql/input.sql` |
| `postgres18-extreme-nesting-withrelation-withlineage-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 7 | 3 | `test-fixtures/correctness/postgres/v18/postgres18-extreme-nesting-withrelation-withlineage-sql/input.sql` |
| `postgres18-pg10-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 3 | 0 | `test-fixtures/correctness/postgres/v18/postgres18-pg10-sql/input.sql` |
| `postgres18-pg11-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 0 | 0 | `test-fixtures/correctness/postgres/v18/postgres18-pg11-sql/input.sql` |
| `postgres18-pg12-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 3 | 0 | `test-fixtures/correctness/postgres/v18/postgres18-pg12-sql/input.sql` |
| `postgres18-pg13-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 1 | 0 | `test-fixtures/correctness/postgres/v18/postgres18-pg13-sql/input.sql` |
| `postgres18-pg14-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 0 | 0 | `test-fixtures/correctness/postgres/v18/postgres18-pg14-sql/input.sql` |
| `postgres18-pg15-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 2 | 16 | `test-fixtures/correctness/postgres/v18/postgres18-pg15-sql/input.sql` |
| `postgres18-pg16-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 0 | 0 | `test-fixtures/correctness/postgres/v18/postgres18-pg16-sql/input.sql` |
| `postgres18-pg17-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 1 | 11 | `test-fixtures/correctness/postgres/v18/postgres18-pg17-sql/input.sql` |
| `postgres18-returning-old-new-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 1 | 1 | `test-fixtures/correctness/postgres/v18/postgres18-returning-old-new-sql/input.sql` |
| `postgres18-sample-data-enterprise-extension-ddl` | PostgreSQL full-grammer v18 | DDL | full-grammer | postgresql/18 | 31 | 0 | `sample-data/postgres/18/01-schema/06-enterprise-extension-tables.sql` |
| `postgres18-sample-data-enterprise-extension-queries-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 17 | 0 | `sample-data/postgres/18/04-queries/10-enterprise-extension-queries.sql` |
| `postgres18-sample-data-enterprise-procedures-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 3 | 8 | `test-fixtures/correctness/postgres/sample-data-enterprise-procedures-sql/input.sql` |
| `postgres18sample-data-full-01-schema-01-tables-ddl` | PostgreSQL full-grammer v18 | DDL | full-grammer | postgresql/18 | 94 | 0 | `sample-data/postgres/18/01-schema/01-tables.sql` |
| `postgres18sample-data-full-01-schema-02-indexes-and-views-ddl` | PostgreSQL full-grammer v18 | DDL | full-grammer | postgresql/18 | 0 | 0 | `sample-data/postgres/18/01-schema/02-indexes-and-views.sql` |
| `postgres18sample-data-full-01-schema-02-indexes-and-views-views-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 17 | 0 | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-01-schema-02-indexes-and-views-views-sql/input.sql` |
| `postgres18sample-data-full-01-schema-03-triggers-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 1 | 4 | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-01-schema-03-triggers-sql/input.sql` |
| `postgres18sample-data-full-01-schema-04-supplementary-tables-ddl` | PostgreSQL full-grammer v18 | DDL | full-grammer | postgresql/18 | 32 | 0 | `sample-data/postgres/18/01-schema/04-supplementary-tables.sql` |
| `postgres18sample-data-full-01-schema-05-third-batch-tables-ddl` | PostgreSQL full-grammer v18 | DDL | full-grammer | postgresql/18 | 36 | 0 | `sample-data/postgres/18/01-schema/05-third-batch-tables.sql` |
| `postgres18sample-data-full-02-procedures-01-procedures-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 0 | 0 | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-02-procedures-01-procedures-sql/input.sql` |
| `postgres18sample-data-full-02-procedures-02-procedures-supplement-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 0 | 0 | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-02-procedures-02-procedures-supplement-sql/input.sql` |
| `postgres18sample-data-full-02-procedures-03-functions-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 0 | 0 | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-02-procedures-03-functions-sql/input.sql` |
| `postgres18sample-data-full-02-procedures-04-procedures-supplement-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 2 | 7 | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-02-procedures-04-procedures-supplement-sql/input.sql` |
| `postgres18sample-data-full-02-procedures-05-third-batch-procedures-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 0 | 0 | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-02-procedures-05-third-batch-procedures-sql/input.sql` |
| `postgres18sample-data-full-02-procedures-06-third-batch-functions-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 0 | 0 | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-02-procedures-06-third-batch-functions-sql/input.sql` |
| `postgres18sample-data-full-02-procedures-07-store-customer-procedures-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 0 | 0 | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-02-procedures-07-store-customer-procedures-sql/input.sql` |
| `postgres18sample-data-full-02-procedures-08-batch-expiry-procedures-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 0 | 0 | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-02-procedures-08-batch-expiry-procedures-sql/input.sql` |
| `postgres18sample-data-full-02-procedures-09-return-refund-procedures-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 0 | 0 | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-02-procedures-09-return-refund-procedures-sql/input.sql` |
| `postgres18sample-data-full-02-procedures-10-supplier-geo-procedures-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 3 | 0 | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-02-procedures-10-supplier-geo-procedures-sql/input.sql` |
| `postgres18sample-data-full-02-procedures-11-common-system-procedures-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 0 | 0 | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-02-procedures-11-common-system-procedures-sql/input.sql` |
| `postgres18sample-data-full-02-procedures-12-enterprise-extension-procedures-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 3 | 8 | `test-fixtures/correctness/postgres/v18/postgres18-sample-data-full-02-procedures-12-enterprise-extension-procedures-sql/input.sql` |
| `postgres18sample-data-full-03-data-01-master-data-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 0 | 0 | `sample-data/postgres/18/03-data/01-master-data.sql` |
| `postgres18sample-data-full-03-data-02-supplementary-data-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 0 | 0 | `sample-data/postgres/18/03-data/02-supplementary-data.sql` |
| `postgres18sample-data-full-03-data-03-enterprise-extension-data-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 0 | 0 | `sample-data/postgres/18/03-data/03-enterprise-extension-data.sql` |
| `postgres18sample-data-full-04-queries-01-complex-queries-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 35 | 0 | `sample-data/postgres/18/04-queries/01-complex-queries.sql` |
| `postgres18sample-data-full-04-queries-02-complex-queries-batch2-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 20 | 0 | `sample-data/postgres/18/04-queries/02-complex-queries-batch2.sql` |
| `postgres18sample-data-full-04-queries-03-complex-queries-batch3-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 20 | 0 | `sample-data/postgres/18/04-queries/03-complex-queries-batch3.sql` |
| `postgres18sample-data-full-04-queries-04-store-customer-queries-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 8 | 0 | `sample-data/postgres/18/04-queries/04-store-customer-queries.sql` |
| `postgres18sample-data-full-04-queries-05-batch-expiry-queries-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 14 | 0 | `sample-data/postgres/18/04-queries/05-batch-expiry-queries.sql` |
| `postgres18sample-data-full-04-queries-06-return-damage-queries-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 16 | 0 | `sample-data/postgres/18/04-queries/06-return-damage-queries.sql` |
| `postgres18sample-data-full-04-queries-07-supplier-analysis-queries-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 3 | 0 | `sample-data/postgres/18/04-queries/07-supplier-analysis-queries.sql` |
| `postgres18sample-data-full-04-queries-08-common-system-queries-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 34 | 0 | `sample-data/postgres/18/04-queries/08-common-system-queries.sql` |
| `postgres18-sample-data-pg18-specific-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 0 | 0 | `sample-data/postgres/18/04-queries/11-pg18-specific.sql` |
| `postgres18-sample-data-real-world-scenarios-sql` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 53 | 0 | `sample-data/postgres/18/04-queries/09-real-world-scenarios.sql` |
| `postgres18-temporal-constraints-ddl` | PostgreSQL full-grammer v18 | DDL | full-grammer | postgresql/18 | 1 | 0 | `test-fixtures/correctness/postgres/v18/postgres18-temporal-constraints-ddl/input.sql` |
| `postgres18-virtual-generated-ddl` | PostgreSQL full-grammer v18 | DDL | full-grammer | postgresql/18 | 0 | 0 | `test-fixtures/correctness/postgres/v18/postgres18-virtual-generated-ddl/input.sql` |
| `postgres18-sql-delete-using-no-alias` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 1 | 0 | `test-fixtures/correctness/postgres/v18/sql-delete-using-no-alias/input.sql` |
| `postgres18-sql-lateral-derived` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 1 | 0 | `test-fixtures/correctness/postgres/v18/sql-lateral-derived/input.sql` |
| `postgres18-sql-merge-using` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 1 | 1 | `test-fixtures/correctness/postgres/v18/sql-merge-using/input.sql` |
| `postgres18-sql-multi-layer-cte` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 3 | 0 | `test-fixtures/correctness/postgres/v18/sql-multi-layer-cte/input.sql` |
| `postgres18-sql-quoted-mixed-alias` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 2 | 0 | `test-fixtures/correctness/postgres/v18/sql-quoted-mixed-alias/input.sql` |
| `postgres18-sql-recursive-cte` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 1 | 0 | `test-fixtures/correctness/postgres/v18/sql-recursive-cte/input.sql` |
| `postgres18-sql-unnest-ordinality` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 1 | 0 | `test-fixtures/correctness/postgres/v18/sql-unnest-ordinality/input.sql` |
| `postgres18-sql-update-from-aliases` | PostgreSQL full-grammer v18 | SQL | full-grammer | postgresql/18 | 1 | 0 | `test-fixtures/correctness/postgres/v18/sql-update-from-aliases/input.sql` |

## Review Needed

No current item requires user review. If a future diff cannot be explained by typed visitor coverage, version boundary, dynamic SQL filtering, or an already documented semantic rule, add it here with SQL context before changing golden.
