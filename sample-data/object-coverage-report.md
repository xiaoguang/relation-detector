# ERP sample object coverage report

Generated from static SQL inspection after the sample-data cleanup.

## Object counts

| Area | MySQL 8.0 | PostgreSQL 18 | Notes |
| --- | ---: | ---: | --- |
| SQL files | 38 | 37 | Both sides include the deep ERP scenario extension; PostgreSQL keeps PG18-only syntax in a separate query file. |
| Lines | 21,440 | 21,000 | Approximate static line count. |
| Persistent tables | 135 | 135 | Aligned business table model. PostgreSQL additionally isolates PG18 demo tables in the same 135-table count. |
| Temporary tables | 4 | 0 | MySQL uses generator-internal temporary tables: `tmp_cat1`, `tmp_cat2`, `tmp_city_coords`, `tmp_prod_names`. |
| Views | 6 | 6 | Aligned. |
| Triggers | 12 | 42 | PostgreSQL has extra trigger functions/triggers for `updated_at` and trigger-body translation. |
| Procedures | 109 | 72 | PostgreSQL read-style procedures are often translated to `RETURNS TABLE` functions; both sides now include the deep ERP scenario routines. |
| Functions | 20 | 60 | PostgreSQL includes trigger functions and procedure-like read functions. |
| Index definitions | 26 | 262 | PostgreSQL schema uses separate `CREATE INDEX`; MySQL often defines indexes inline. |
| Insert target tables | 111 | 115 | PostgreSQL now covers every MySQL seed target and adds `tax_invoices` plus three PG18-specific demo tables. |

## Expected differences

| Difference | Classification | Reason |
| --- | --- | --- |
| PostgreSQL-only `tax_invoices` seed target | Expected dialect/sample enrichment | PostgreSQL keeps explicit VAT invoice samples alongside the common tax-rate model. |
| PostgreSQL-only `pg18_generated_margin_demo`, `pg18_price_validity_demo`, `pg18_price_promotion_demo` | Expected version feature | These tables isolate PostgreSQL 18 virtual generated column and temporal constraint samples. |
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
- PostgreSQL deep ERP scenario files were added: `01-schema/07-erp-deep-scenario-tables.sql`, `02-procedures/13-erp-deep-scenario-procedures.sql`, `03-data/04-erp-deep-scenario-data.sql`, and `04-queries/12-erp-deep-scenario-queries.sql`.
- PostgreSQL seed coverage gaps for returns, damage reports, shipments, depreciation, performance reviews, and price changes were filled in `03-data/05-erp-coverage-gap-data.sql`.
- PostgreSQL invalid `CHECK (>= 0)` constraints were rewritten as column-aware checks.
- PostgreSQL generated columns using `CURRENT_DATE` were rewritten to use row-local `snapshot_date`.
- Representative seed rows were added for `leave_records`, `promotion_usages`, `foreign_currency_accounts`, and `consignment_consumptions`.
- `.DS_Store` files were removed from `sample-data`.

## Remaining validation needed

The current report is static. Final acceptance still requires loading both databases in real MySQL 8.0 and PostgreSQL 18 instances and executing schema/data/procedure/query smoke workflows.
