# ERP sample object coverage report

Generated from static SQL inspection after the sample-data cleanup.

## Object counts

| Area | MySQL 8.0 | PostgreSQL 16 | PostgreSQL 17 | PostgreSQL 18 | Notes |
| --- | ---: | ---: | ---: | ---: | --- |
| SQL files | 38 | 37 | 37 | 37 | PostgreSQL 16/17 are full source-level translations of PostgreSQL 18. |
| Lines | 21,440 | 21,685 | 21,685 | 21,661 | Approximate static line count. PG16/17 are slightly longer because PG18 temporal syntax is expanded into compatibility constraints. |
| Persistent tables | 135 | 143 | 143 | 143 | Aligned business table model. PostgreSQL versions additionally isolate version-demo tables. |
| Temporary tables | 4 | 0 | 0 | 0 | MySQL uses generator-internal temporary tables: `tmp_cat1`, `tmp_cat2`, `tmp_city_coords`, `tmp_prod_names`. |
| Views | 6 | 6 | 6 | 6 | Aligned. |
| Triggers | 12 | 42 | 42 | 42 | PostgreSQL has extra trigger functions/triggers for `updated_at` and trigger-body translation. |
| Procedures | 109 | 76 | 76 | 76 | PostgreSQL read-style procedures are often translated to `RETURNS TABLE` functions; all PostgreSQL versions include the deep ERP scenario routines. |
| Functions | 20 | 60 | 60 | 60 | PostgreSQL includes trigger functions and procedure-like read functions. |
| Index definitions | 26 | 262 | 262 | 262 | PostgreSQL schema uses separate `CREATE INDEX`; MySQL often defines indexes inline. |
| Insert target tables | 111 | 143 | 143 | 143 | PostgreSQL versions cover every MySQL seed target and include version-demo seed targets. |

## Expected differences

| Difference | Classification | Reason |
| --- | --- | --- |
| PostgreSQL-only `tax_invoices` seed target | Expected dialect/sample enrichment | PostgreSQL keeps explicit VAT invoice samples alongside the common tax-rate model. |
| PostgreSQL-only version demo tables | Expected version feature / compatibility sample | PG18 uses `pg18_*` tables for virtual generated column and temporal constraints. PG16/17 use `pg16_*` / `pg17_*` compatibility tables with stored generated columns, exclusion constraints, and normal FK references. |
| MySQL-only `tmp_cat1`, `tmp_cat2`, `tmp_city_coords`, `tmp_prod_names` insert targets | Expected generator internal tables | These are temporary tables used by MySQL massive-data generator procedures, not persistent ERP entities. |
| PostgreSQL has many more functions | Expected dialect translation | PostgreSQL triggers require trigger functions; read-style MySQL procedures are represented as `RETURNS TABLE` functions. |
| PostgreSQL has more index definitions | Expected dialect syntax | MySQL schema often declares indexes inline; PostgreSQL uses standalone `CREATE INDEX`. |

## ERP module coverage

| Module | Representative tables |
| --- | --- |
| Organization and HR | `departments`, `positions`, `employees`, `attendance`, `leave_records`, `employee_shifts`, `employee_shift_assignments` |
| RBAC and audit | `roles`, `permissions`, `role_permissions`, `employee_roles`, `audit_log` |
| Product and inventory | `product_categories`, `products`, `product_batches`, `warehouses`, `inventory`, `inventory_transactions`, `inventory_reservations` |
| Procurement | `purchase_requisitions`, `purchase_orders`, `purchase_receipts`, `purchase_returns` |
| Sales and customer | `customers`, `customer_addresses`, `sales_orders`, `sales_returns`, `promotions`, `service_tickets` |
| Finance | `accounts`, `vouchers`, `voucher_items`, `cashier_journals`, `settlements`, `payment_receipts`, `accounting_periods` |
| Tax and invoice | `invoices`, `tax_invoices`, `tax_filings`, `tax_rates` |
| Manufacturing | `boms`, `work_orders`, `work_order_materials`, `production_routes`, `production_operations` |
| MRP and shop-floor execution | `production_plans`, `mrp_runs`, `mrp_run_items`, `work_order_operations`, `operation_reports`, `material_issues`, `finished_goods_receipts` |
| Costing and valuation | `standard_costs`, `inventory_cost_layers`, `inventory_valuation_snapshots`, `work_order_costs`, `cogs_entries` |
| AR/AP and budgeting | `account_subjects`, `opening_balances`, `account_balances`, `budget_versions`, `budget_items`, `ar_invoices`, `ap_invoices`, `payment_requests` |
| WMS and repair service | `warehouse_zones`, `warehouse_locations`, `inventory_location_balances`, `putaway_tasks`, `picking_tasks`, `repair_orders`, `repair_order_parts` |
| Master-data governance and security | `numbering_rules`, `master_data_change_requests`, `master_data_change_items`, `data_permission_scopes`, `sensitive_access_logs` |
| Quality and traceability | `inspection_standards`, `inspection_reports`, `serial_numbers`, `serial_number_logs` |
| Governance and planning | `approval_workflows`, `approval_nodes`, `approval_instances`, `contracts`, `projects`, `kpi_indicators` |

## Static cleanup results

- MySQL `GROUPING SETS` / `GROUPING()` usage was replaced with MySQL 8.0-compatible `UNION ALL` rollup logic.
- MySQL deep ERP scenario files were added: `01-schema/07-erp-deep-scenario-tables.sql`, `02-procedures/13-erp-deep-scenario-procedures.sql`, `03-data/07-erp-deep-scenario-data.sql`, and `04-queries/11-erp-deep-scenario-queries.sql`.
- PostgreSQL 16/17/18 deep ERP scenario files are aligned: `01-schema/07-erp-deep-scenario-tables.sql`, `02-procedures/13-erp-deep-scenario-procedures.sql`, `03-data/04-erp-deep-scenario-data.sql`, and `04-queries/12-erp-deep-scenario-queries.sql`.
- PostgreSQL 16/17 compatibility directories were added as complete translations of `postgres/18`. Their version demo SQL downgrades PG18-only syntax to valid PostgreSQL 16/17 equivalents.
- PostgreSQL seed coverage gaps for returns, damage reports, shipments, depreciation, performance reviews, and price changes were filled in `03-data/05-erp-coverage-gap-data.sql`.
- PostgreSQL invalid `CHECK (>= 0)` constraints were rewritten as column-aware checks.
- PostgreSQL generated columns using `CURRENT_DATE` were rewritten to use row-local `snapshot_date`.
- Representative seed rows were added for `leave_records`, `promotion_usages`, `foreign_currency_accounts`, and `consignment_consumptions`.
- `.DS_Store` files were removed from `sample-data`.

## Remaining validation needed

The current report is static. Final acceptance still requires loading the sample databases in real MySQL 8.0 and PostgreSQL 16/17/18 instances and executing schema/data/procedure/query smoke workflows.
