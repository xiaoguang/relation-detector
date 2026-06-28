# ERP sample object coverage report

Generated from static SQL inspection after the sample-data cleanup.

## Object counts

| Area | MySQL 8.0 | PostgreSQL 18 | Notes |
| --- | ---: | ---: | --- |
| SQL files | 34 | 32 | PostgreSQL keeps PG18-only syntax in a separate query file. |
| Lines | 19,550 | 19,047 | Approximate static line count. |
| Persistent tables | 100 | 103 | PostgreSQL has the same 100 business tables plus 3 PG18-specific demo tables. |
| Temporary tables | 4 | 0 | MySQL uses generator-internal temporary tables: `tmp_cat1`, `tmp_cat2`, `tmp_city_coords`, `tmp_prod_names`. |
| Views | 6 | 6 | Aligned. |
| Triggers | 12 | 42 | PostgreSQL has extra trigger functions/triggers for `updated_at` and trigger-body translation. |
| Procedures | 99 | 62 | PostgreSQL read-style procedures are often translated to `RETURNS TABLE` functions. |
| Functions | 20 | 60 | PostgreSQL includes trigger functions and procedure-like read functions. |
| Index definitions | 26 | 250 | PostgreSQL schema uses separate `CREATE INDEX`; MySQL often defines indexes inline. |
| Insert target tables | 104 | 103 | Every persistent table has representative seed/generator coverage; MySQL has 4 additional temporary generator targets. |

## Expected differences

| Difference | Classification | Reason |
| --- | --- | --- |
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
| Quality and traceability | `inspection_standards`, `inspection_reports`, `serial_numbers`, `serial_number_logs` |
| Governance and planning | `approval_workflows`, `approval_nodes`, `approval_instances`, `contracts`, `projects`, `kpi_indicators` |

## Static cleanup results

- MySQL `GROUPING SETS` / `GROUPING()` usage was replaced with MySQL 8.0-compatible `UNION ALL` rollup logic.
- PostgreSQL invalid `CHECK (>= 0)` constraints were rewritten as column-aware checks.
- PostgreSQL generated columns using `CURRENT_DATE` were rewritten to use row-local `snapshot_date`.
- Representative seed rows were added for `leave_records`, `promotion_usages`, `foreign_currency_accounts`, and `consignment_consumptions`.
- `.DS_Store` files were removed from `sample-data`.

## Remaining validation needed

The current report is static. Final acceptance still requires loading both databases in real MySQL 8.0 and PostgreSQL 18 instances and executing schema/data/procedure/query smoke workflows.
