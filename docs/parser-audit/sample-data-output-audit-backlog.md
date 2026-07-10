# Sample-Data Output Audit Backlog

This backlog records issues found by reading the generated sample-data CLI JSON outputs together with the SQL assets that produced them. It is intentionally separate from generated summaries: the entries below are repair notes for future parser / SQL asset work, not acceptance criteria for the current generated files.

Audit source:

- Generated JSON: `relation-detector/target/sample-data-parser-cli/results/*.json`
- SQL assets: `relation-detector/sample-data/**`
- Current parser comparison entry point: `docs/parser-audit/parser-comparison-summary.md`

## Current Clean Checks

The latest audit did not find structural corruption in the generated JSON:

- Summary counts match the corresponding output arrays.
- `warning-codes.tsv` is clean: all parser categories report `NONE 0`.
- `rawEvidence.source` no longer contains local absolute workspace paths.
- Relationship `NAMING_MATCH.evidenceRef` values resolve to top-level `namingEvidence.id`.
- No derived non-adjacent cycles, derived self-loops, or direct duplicate derived facts were found.
- Lineage evidence no longer has null evidence type.

These checks only prove output structure is internally consistent. They do not prove every SQL asset is schema-valid or every parser output is semantically correct.

## Future Repair Items

### 1. SQL Server DDL FK Gap

Status: `CONFIRMED_PARSER_GAP`

SQL Server DDL contains an explicit FK:

- `relation-detector/sample-data/sqlserver/2025/01-schema/01-tables.sql`
- `ALTER TABLE [dbo].[departments] ... FOREIGN KEY ([manager_id]) REFERENCES [dbo].[employees] ([id])`

Expected relation / naming evidence:

- `dbo.departments.manager_id -> dbo.employees.id`

Current issue:

- SQL Server token-event and full-grammer outputs miss this FK relation and the corresponding top-level naming evidence.

Future fix:

- Repair SQL Server ALTER TABLE FK DDL visitor / DDL inventory handling.

### 2. SQL Server Sample-Data Schema Inconsistency

Status: `CONFIRMED_SQL_ASSET_GAP`

SQL Server output includes endpoints that are not declared by the SQL Server sample schema. The largest cluster is in:

- `relation-detector/sample-data/sqlserver/2025/02-procedures/13-erp-deep-scenario-procedures.sql`
- `relation-detector/sample-data/sqlserver/2025/03-data/07-erp-deep-scenario-data.sql`

Examples:

- `budget_versions.department_id` is referenced, while the schema does not declare that column.
- `budget_items.actual_amount`, `budget_items.remaining_amount`, and `budget_items.updated_at` are referenced, while the schema uses a different budget item shape.
- `sales_fact.sales_order_id`, `sales_fact.category_code`, `sales_fact.region_code`, `sales_fact.quantity`, and `sales_fact.gross_amount` do not match the SQL Server `sales_fact` schema.
- `payments.sales_order_id`, `payments.account_id`, `payments.method`, and `payments.created_by` do not match the SQL Server `payments` schema.
- `ar_invoices.invoice_no` / `ap_invoices.invoice_no` are used where the schema uses `ar_no` / `ap_no`.
- `customers.city`, `customers.province`, `products.category_code`, `picking_task_items.task_id`, `sales_returns.sales_order_id`, `tax_invoices.filing_id`, `tax_invoices.tax_rate_id`, `vouchers.department_id`, and `vouchers.amount` are referenced without matching declared columns.

Known bad join example:

- `relation-detector/sample-data/sqlserver/2025/03-data/07-erp-deep-scenario-data.sql`
- The SQL joins `budget_versions.ledger_book_id` to `departments.id` and `account_subjects.id`. The parser output follows the SQL, but the SQL itself is not schema/business correct.

Impact:

- This contaminates direct relationship / lineage and derived relationship / lineage counts.
- `Diag=0` only means the SQL parsed syntactically; it does not validate sample SQL against the declared schema.

Future fix:

- Align SQL Server procedure/data SQL with the SQL Server DDL schema before treating counts as quality evidence.
- Add an asset hygiene check that can optionally compare output endpoints with declared sample schema columns.

### 3. SQL Server Duplicate Data Files

Status: `CONFIRMED_SQL_ASSET_GAP`

Within every SQL Server version, these files are byte-for-byte identical:

- `03-data/01-master-data.sql`
- `03-data/04-return-damage-data.sql`
- `03-data/05-massive-data-generator.sql`

Impact:

- They do not inflate relation/lineage much because seed `INSERT VALUES` usually has no physical lineage.
- They do overstate natural sample-data coverage and make the 38-fixture corpus look broader than it is.

Future fix:

- Replace duplicates with distinct natural data assets or reduce duplicate fixture coverage.

### 4. Common Natural Benchmark Schema Inconsistency

Status: `CONFIRMED_SQL_ASSET_GAP`

`sample-data/common-natural` still references columns that do not exist in the common schema.

Examples:

- `purchase_receipts.purchase_order_id` where the schema uses `order_id`.
- `sales_order_items.sales_order_id` where the schema uses `order_id`.
- `sales_returns.sales_order_id` where the schema uses `order_id`.
- `shipments.sales_order_id` where the schema uses `order_id`.
- `payments.sales_order_id` and `payments.account_id` without matching common schema columns.
- `reconciliation_items.account_id`, `reconciliation_items.system_amount`, and `reconciliation_items.bank_amount` without matching common schema columns.
- `stocktake_items.system_qty` / `counted_qty` where the schema uses `book_quantity` / `counted_quantity`.
- `cashier_journals.sales_order_id` where the schema uses `reference_type` / `reference_id`.

Impact:

- Common direct relationship, naming evidence, and derived relationship counts include schema-invalid paths.

Future fix:

- Normalize common natural SQL to its own declared schema, or move incompatible statements into parser coverage assets with explicit notes.

### 5. MySQL 5.7 Asset / Parser Mixed Issues

Status: `MIXED_SQL_ASSET_AND_PARSER_GAP`

MySQL 5.7 output contains endpoints that are not declared in the 5.7 sample schema.

Examples:

- `commission_rules.employee_id`, `commission_rules.rate`
- `inventory.reserved_quantity`
- `mrp_run_items.mrp_run_id`
- `mrp_runs.production_plan_id`
- `positions.base_salary`
- `sales_commissions.commission_month`, `sales_commissions.sales_amount`
- `sales_fact.category_id`, `sales_fact.region_id`
- `sales_returns.created_by`
- `product_batches.order_id`

Interpretation:

- Some entries are likely SQL rewrite / asset mismatch.
- `product_batches.order_id` is likely a parser alias/scope bug from nested scalar subquery handling.

Future fix:

- Split the MySQL 5.7 vs 8.0 audit into item-level `EXPECTED_SQL_ASSET_DELTA`, `MYSQL57_PARSER_GAP`, and `MYSQL80_FALSE_POSITIVE`.
- Do not force count parity between 5.7 and 8.0.

### 6. Cross-Dialect Invalid SQL Assets

Status: `CONFIRMED_SQL_ASSET_GAP`

The same schema mismatch appears in MySQL / PostgreSQL / Oracle assets:

- `invoices.reference_id` is referenced by `04-queries/08-common-system-queries.sql`, but the schema does not declare `reference_id` on `invoices`.
- `positions.base_salary` is referenced by deep procedure assets, while the schema declares salary range columns such as `min_salary` / `max_salary`.

Impact:

- Parsers that output these endpoints are faithfully reflecting invalid SQL.
- Parsers that miss these endpoints may appear weaker, but the underlying SQL should be corrected first.

Future fix:

- Repair the shared ERP SQL assets against each dialect schema before using these statements for parser parity.

### 7. MySQL Scalar Subquery Scope / Flow Classification

Status: `CONFIRMED_PARSER_GAP`

Known SQL context:

- `relation-detector/sample-data/mysql/8.0/03-data/04-return-damage-data.sql`
- Nested scalar subquery writes `purchase_return_items.batch_id` from `product_batches.id`, with filters through `purchase_order_items` and outer `purchase_returns`.

Expected classification:

- `VALUE`: `product_batches.id -> purchase_return_items.batch_id`
- `CONTROL`: `product_batches.product_id`, `purchase_order_items.product_id`, `purchase_order_items.order_id`, and `purchase_returns.purchase_order_id` contribute to locating the value.

Current issue:

- MySQL token-event misses part of the predicate / outer source set.
- MySQL full-grammer emits an invalid `product_batches.order_id` source in this path, indicating alias/scope mis-resolution.

Future fix:

- Make scalar subquery analysis separate selected projection sources from predicate / correlated control sources.
- Ensure nested subquery alias scopes do not leak selected table aliases into unrelated rowsets.

### 8. MySQL CASE / IF / Function Flow Semantics

Status: `CONFIRMED_PARSER_SEMANTIC_GAP`

Examples:

- `IF(po.status = 'received', literal, literal)` should treat `po.status` as `CONTROL`, not `VALUE`.
- `YEAR(order_date)` / `MONTH(order_date)` / `DATE_FORMAT(...)` should preserve function-transform semantics.
- `IF(so.status = 'delivered', DATE_ADD(so.order_date, ...), NULL)` should split `so.status` as `CONTROL` and `so.order_date` as `VALUE`.
- Boolean flags such as category membership derived from `pc.name = ...` should not be plain value-copy lineage.

Current issue:

- MySQL full-grammer sometimes marks these sources as `VALUE` / `DIRECT`.
- MySQL token-event sometimes misses the true value operand in negative arithmetic, for example `-rop.quantity`.

Future fix:

- Tighten expression analyzer source roles for CASE/IF predicates, function arguments, and arithmetic expressions.

### 9. Oracle Token / Full Relation Coverage Deltas

Status: `CONFIRMED_PARSER_GAP_AND_SQL_ASSET_GAP`

Token-event-only relation examples:

- `cashier_journals.reference_id -> sales_orders.id`
- `purchase_orders.department_id -> departments.id`

Full-grammer-only valid examples:

- `cashier_journals.reference_id -> purchase_orders.id`
- `purchase_receipt_items.order_item_id -> purchase_order_items.id`

Known SQL asset problem:

- `invoices.reference_id -> purchase_orders.id` comes from the cross-dialect invalid `invoices.reference_id` asset and should not be treated as a parser win.

Future fix:

- Repair invalid invoice SQL first.
- Then add the missing typed relation cases to Oracle token-event and full-grammer visitors independently.

### 10. Transform Type Parity Gaps

Status: `CONFIRMED_PARSER_SEMANTIC_GAP`

Examples:

- SQL Server token-event and full-grammer have the same direct source/target pairs, but transform types differ for `DATEADD`, `YEAR`, `MONTH`, `DATEPART`, `CONVERT`, and `CASE WHEN`.
- Oracle full-grammer sometimes emits `DIRECT` where token-event emits `AGGREGATE` or `FUNCTION_CALL`, especially supplier metric expressions.
- MySQL full-grammer sometimes emits `DIRECT` for date functions.
- PostgreSQL currently has the cleanest transform parity among token-event and full-grammer.

Future fix:

- Move transform classification toward a shared expression role model across token-event and full-grammer.

### 11. Naming Evidence Provenance

Status: `CONFIRMED_OBSERVABILITY_GAP`

Many direct naming evidence observations still use generic source text such as `naming heuristic` and do not carry precise `sourceFile` / `sourceStatementId` provenance.

Impact:

- Counts and evidence refs are structurally valid.
- Auditing a naming evidence item back to the SQL / DDL observation is still harder than intended.

Future fix:

- Carry source observation provenance from DDL column inventory, metadata column facts, and relationship candidates into the `NamingEvidencePool`.
- Keep `namingEvidence` as the single source for `NAMING_MATCH`; do not reintroduce relationship-local rule computation.

## Repair Order Recommendation

1. Fix schema-invalid SQL assets first: SQL Server, common natural, MySQL 5.7, and shared invoice/position assets.
2. Add an optional sample-data schema validation audit that compares output endpoints to declared DDL columns.
3. Fix confirmed parser gaps that remain after SQL assets are valid.
4. Re-run full correctness and sample-data CLI with derived output.
5. Update this backlog by moving resolved items into a "resolved" section with commit / test references.

