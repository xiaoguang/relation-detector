# Data Lineage Pending Review

This file is maintained with Data Lineage v1. It lists field-flow candidates that are plausible, but not yet stable enough to become `expected-lineage.json` golden fingerprints.

v1 rule: only reviewed physical `table.column -> table.column` lineage enters golden. Parameters, JSON paths, literals, local variables, and dynamic SQL remain outside Data Lineage v1.

## Pending Candidates

### `mysql-supply-chain-update-explicit-join-sql`

Input: `test-fixtures/correctness/mysql/mysql-supply-chain-update-explicit-join-sql/input.sql`

Pending fingerprints:

- `VALUE:ARITHMETIC:order_items.quantity->warehouse_inventory.stock_reserved`
- `CONTROL:CASE_WHEN:customer_profiles.risk_score,warehouse_inventory.stock_available,order_items.quantity->warehouse_inventory.last_audit_status`
- `VALUE:AGGREGATE:supplier_manifests.supply_price,warehouse_inventory.default_unit_cost,order_items.quantity->order_items.estimated_cost`

Reason: `estimated_cost` mixes aggregate, fallback, and arithmetic in one expression. The current v1 model supports multi-source lineage, but the desired split between `AGGREGATE`, `COALESCE`, and `ARITHMETIC` needs product-level review.

### `mysql-supply-chain-update-comma-and-subquery-sql`

Input: `test-fixtures/correctness/mysql/mysql-supply-chain-update-comma-and-subquery-sql/input.sql`

Pending fingerprints:

- `VALUE:ARITHMETIC:order_items.quantity->warehouse_inventory.stock_reserved`
- `CONTROL:CASE_WHEN:customer_profiles.risk_score,warehouse_inventory.stock_available,order_items.quantity->warehouse_inventory.last_audit_status`
- `VALUE:AGGREGATE:supplier_manifests.supply_price,warehouse_inventory.default_unit_cost,order_items.quantity->order_items.estimated_cost`

Reason: should align with the explicit JOIN version, but the aggregate source is inside a correlated scalar subquery. Keep out of golden until scalar-subquery lineage is reviewed against the explicit derived-table version.

### `mysql-business-*-procedure-*-sql`

Input group: `test-fixtures/correctness/mysql/mysql-business-*-procedure-*-sql/input.sql`

Pending examples:

- Parameter JSON / `JSON_TABLE` inputs into temporary rowsets.
- Multi-step routine-local rowset transformations.
- Cross-statement updates inside procedure body.

Reason: v1 deliberately excludes Parameter Binding and routine-local variable/temporary rowset lineage. These examples should be revisited after Parameter Binding or procedure-scope data-flow design.

### `postgres-business-account-balances-financial-*-sql`

Input group: `test-fixtures/correctness/postgres/postgres-business-account-balances-financial-*-sql/input.sql`

Pending fingerprints:

- `VALUE:CONCAT_FORMAT:users.country_code,transaction_ledgers.merchant_category->account_balances.compliance_notes`
- `VALUE:ARITHMETIC:account_balances.max_credit_limit->account_balances.adjusted_limit`

Reason: financial CTEs include nested aggregate CTE projection, formatting, casts, and array updates. The business lineage is useful, but CTE output mapping for `STRING_AGG`, `MAX`, and `NTILE` needs review before becoming golden.

### `postgres-business-asset-balances-update-outer-join-sql`

Input: `test-fixtures/correctness/postgres/postgres-business-asset-balances-update-outer-join-sql/input.sql`

Pending fingerprints:

- `VALUE:ARITHMETIC:ledger_system_a.balance,ledger_system_b.balance->asset_balances.computed_balance`
- `CONTROL:CASE_WHEN:ledger_system_a.balance,ledger_system_b.balance->asset_balances.discrepancy_flag`
- `VALUE:DIRECT:staff_assignments.operator_name->asset_balances.last_checked_by`

Reason: FULL OUTER JOIN plus `COALESCE` gives reasonable multi-source lineage, but v1 needs an explicit review decision on whether both sides should be kept as VALUE sources for every COALESCE branch.
