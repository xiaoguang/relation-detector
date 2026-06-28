# Portable Sample Data Review

## Summary

`sample-data/portable` now mirrors the MySQL 8.0 ERP object inventory with portable SQL forms.

| Object type | MySQL 8.0 | Portable | Status |
|---|---:|---:|---|
| Tables | 100 | 100 | MATCHED |
| Views | 6 | 6 | MATCHED |
| Procedures | 99 | 99 | MATCHED |
| Functions | 20 | 20 | MATCHED |
| Triggers | 12 | 12 | MATCHED |
| Business table seed targets | 100 | 100 | MATCHED |

## Representation Rules

- Tables keep source table names, simplified portable column types, FK constraints, and selected unique/index evidence.
- Views are represented as portable `CREATE VIEW ... AS SELECT ...` examples for catalog equivalence.
- Procedures/functions/triggers keep object names and use SQL/PSM-style declarations.
- Common correctness uses `04-process-bodies-for-golden.sql` object blocks so the typed common grammar analyzes the internal SQL statements rather than a dialect-specific routine wrapper.
- No table-name or column-name filtering is used for expected parser behavior; generated SQL exposes typed JOIN/EXISTS/IN/INSERT/UPDATE structures.
- MySQL contains four local temporary insert targets (`tmp_cat1`, `tmp_cat2`, `tmp_city_coords`, `tmp_prod_names`) inside data-generation routines. Portable sample data intentionally excludes them because temporary rowsets should not enter relation or lineage golden.

## Review

Current status: no `REVIEW_NEEDED` item. The only intentional downgrade is that SQL/PSM declarations are portable parser material, not guaranteed directly executable by both MySQL 8.0 and PostgreSQL 18 without adaptation.

## Object Mapping

### Tables

- `departments` -> `sample-data/portable` table `departments`
- `positions` -> `sample-data/portable` table `positions`
- `employees` -> `sample-data/portable` table `employees`
- `employee_salary_log` -> `sample-data/portable` table `employee_salary_log`
- `attendance` -> `sample-data/portable` table `attendance`
- `leave_records` -> `sample-data/portable` table `leave_records`
- `roles` -> `sample-data/portable` table `roles`
- `permissions` -> `sample-data/portable` table `permissions`
- `role_permissions` -> `sample-data/portable` table `role_permissions`
- `employee_roles` -> `sample-data/portable` table `employee_roles`
- `audit_log` -> `sample-data/portable` table `audit_log`
- `product_categories` -> `sample-data/portable` table `product_categories`
- `suppliers` -> `sample-data/portable` table `suppliers`
- `products` -> `sample-data/portable` table `products`
- `supplier_products` -> `sample-data/portable` table `supplier_products`
- `product_batches` -> `sample-data/portable` table `product_batches`
- `warehouses` -> `sample-data/portable` table `warehouses`
- `inventory` -> `sample-data/portable` table `inventory`
- `inventory_transactions` -> `sample-data/portable` table `inventory_transactions`
- `purchase_requisitions` -> `sample-data/portable` table `purchase_requisitions`
- `purchase_requisition_items` -> `sample-data/portable` table `purchase_requisition_items`
- `purchase_orders` -> `sample-data/portable` table `purchase_orders`
- `purchase_order_items` -> `sample-data/portable` table `purchase_order_items`
- `purchase_receipts` -> `sample-data/portable` table `purchase_receipts`
- `purchase_receipt_items` -> `sample-data/portable` table `purchase_receipt_items`
- `customers` -> `sample-data/portable` table `customers`
- `sales_orders` -> `sample-data/portable` table `sales_orders`
- `sales_order_items` -> `sample-data/portable` table `sales_order_items`
- `sales_returns` -> `sample-data/portable` table `sales_returns`
- `sales_return_items` -> `sample-data/portable` table `sales_return_items`
- `purchase_returns` -> `sample-data/portable` table `purchase_returns`
- `purchase_return_items` -> `sample-data/portable` table `purchase_return_items`
- `damage_reports` -> `sample-data/portable` table `damage_reports`
- `damage_report_items` -> `sample-data/portable` table `damage_report_items`
- `accounts` -> `sample-data/portable` table `accounts`
- `vouchers` -> `sample-data/portable` table `vouchers`
- `voucher_items` -> `sample-data/portable` table `voucher_items`
- `cashier_journals` -> `sample-data/portable` table `cashier_journals`
- `salary_payments` -> `sample-data/portable` table `salary_payments`
- `reconciliations` -> `sample-data/portable` table `reconciliations`
- `reconciliation_items` -> `sample-data/portable` table `reconciliation_items`
- `settlements` -> `sample-data/portable` table `settlements`
- `settlement_items` -> `sample-data/portable` table `settlement_items`
- `shipments` -> `sample-data/portable` table `shipments`
- `shipping_tracks` -> `sample-data/portable` table `shipping_tracks`
- `commission_rules` -> `sample-data/portable` table `commission_rules`
- `sales_commissions` -> `sample-data/portable` table `sales_commissions`
- `promotions` -> `sample-data/portable` table `promotions`
- `promotion_products` -> `sample-data/portable` table `promotion_products`
- `promotion_usages` -> `sample-data/portable` table `promotion_usages`
- `invoices` -> `sample-data/portable` table `invoices`
- `three_way_matching` -> `sample-data/portable` table `three_way_matching`
- `fixed_assets` -> `sample-data/portable` table `fixed_assets`
- `depreciation_log` -> `sample-data/portable` table `depreciation_log`
- `boms` -> `sample-data/portable` table `boms`
- `work_orders` -> `sample-data/portable` table `work_orders`
- `work_order_materials` -> `sample-data/portable` table `work_order_materials`
- `service_tickets` -> `sample-data/portable` table `service_tickets`
- `contracts` -> `sample-data/portable` table `contracts`
- `contract_milestones` -> `sample-data/portable` table `contract_milestones`
- `ar_aging_snapshots` -> `sample-data/portable` table `ar_aging_snapshots`
- `ap_aging_snapshots` -> `sample-data/portable` table `ap_aging_snapshots`
- `tax_invoices` -> `sample-data/portable` table `tax_invoices`
- `tax_filings` -> `sample-data/portable` table `tax_filings`
- `inspection_standards` -> `sample-data/portable` table `inspection_standards`
- `inspection_reports` -> `sample-data/portable` table `inspection_reports`
- `approval_workflows` -> `sample-data/portable` table `approval_workflows`
- `approval_nodes` -> `sample-data/portable` table `approval_nodes`
- `approval_instances` -> `sample-data/portable` table `approval_instances`
- `approval_records` -> `sample-data/portable` table `approval_records`
- `cash_flow_forecasts` -> `sample-data/portable` table `cash_flow_forecasts`
- `projects` -> `sample-data/portable` table `projects`
- `project_costs` -> `sample-data/portable` table `project_costs`
- `exchange_rates` -> `sample-data/portable` table `exchange_rates`
- `foreign_currency_accounts` -> `sample-data/portable` table `foreign_currency_accounts`
- `performance_reviews` -> `sample-data/portable` table `performance_reviews`
- `kpi_indicators` -> `sample-data/portable` table `kpi_indicators`
- `serial_numbers` -> `sample-data/portable` table `serial_numbers`
- `serial_number_logs` -> `sample-data/portable` table `serial_number_logs`
- `consignment_inventory` -> `sample-data/portable` table `consignment_inventory`
- `consignment_consumptions` -> `sample-data/portable` table `consignment_consumptions`
- `price_change_logs` -> `sample-data/portable` table `price_change_logs`
- `tenants` -> `sample-data/portable` table `tenants`
- `ledger_books` -> `sample-data/portable` table `ledger_books`
- `customer_addresses` -> `sample-data/portable` table `customer_addresses`
- `supplier_addresses` -> `sample-data/portable` table `supplier_addresses`
- `tax_rates` -> `sample-data/portable` table `tax_rates`
- `accounting_periods` -> `sample-data/portable` table `accounting_periods`
- `period_close_jobs` -> `sample-data/portable` table `period_close_jobs`
- `payment_receipts` -> `sample-data/portable` table `payment_receipts`
- `payment_receipt_allocations` -> `sample-data/portable` table `payment_receipt_allocations`
- `stocktakes` -> `sample-data/portable` table `stocktakes`
- `stocktake_items` -> `sample-data/portable` table `stocktake_items`
- `stock_transfers` -> `sample-data/portable` table `stock_transfers`
- `stock_transfer_items` -> `sample-data/portable` table `stock_transfer_items`
- `inventory_reservations` -> `sample-data/portable` table `inventory_reservations`
- `production_routes` -> `sample-data/portable` table `production_routes`
- `production_operations` -> `sample-data/portable` table `production_operations`
- `employee_shifts` -> `sample-data/portable` table `employee_shifts`
- `employee_shift_assignments` -> `sample-data/portable` table `employee_shift_assignments`

### Procedures

- `sp_approve_requisition` -> portable procedure `sp_approve_requisition` + object block `PROCEDURE:portable.sp_approve_requisition`
- `sp_approve_sales_return` -> portable procedure `sp_approve_sales_return` + object block `PROCEDURE:portable.sp_approve_sales_return`
- `sp_assign_department_manager` -> portable procedure `sp_assign_department_manager` + object block `PROCEDURE:portable.sp_assign_department_manager`
- `sp_assign_service_ticket` -> portable procedure `sp_assign_service_ticket` + object block `PROCEDURE:portable.sp_assign_service_ticket`
- `sp_auto_replenishment_suggestion` -> portable procedure `sp_auto_replenishment_suggestion` + object block `PROCEDURE:portable.sp_auto_replenishment_suggestion`
- `sp_batch_expiry_tracking` -> portable procedure `sp_batch_expiry_tracking` + object block `PROCEDURE:portable.sp_batch_expiry_tracking`
- `sp_calculate_cash_flow` -> portable procedure `sp_calculate_cash_flow` + object block `PROCEDURE:portable.sp_calculate_cash_flow`
- `sp_calculate_commission` -> portable procedure `sp_calculate_commission` + object block `PROCEDURE:portable.sp_calculate_commission`
- `sp_calculate_monthly_pl` -> portable procedure `sp_calculate_monthly_pl` + object block `PROCEDURE:portable.sp_calculate_monthly_pl`
- `sp_category_sales_vs_expiry` -> portable procedure `sp_category_sales_vs_expiry` + object block `PROCEDURE:portable.sp_category_sales_vs_expiry`
- `sp_change_product_price` -> portable procedure `sp_change_product_price` + object block `PROCEDURE:portable.sp_change_product_price`
- `sp_check_customer_credit` -> portable procedure `sp_check_customer_credit` + object block `PROCEDURE:portable.sp_check_customer_credit`
- `sp_check_department_budget` -> portable procedure `sp_check_department_budget` + object block `PROCEDURE:portable.sp_check_department_budget`
- `sp_close_accounting_period` -> portable procedure `sp_close_accounting_period` + object block `PROCEDURE:portable.sp_close_accounting_period`
- `sp_compare_suppliers_for_product` -> portable procedure `sp_compare_suppliers_for_product` + object block `PROCEDURE:portable.sp_compare_suppliers_for_product`
- `sp_contract_expiry_alert` -> portable procedure `sp_contract_expiry_alert` + object block `PROCEDURE:portable.sp_contract_expiry_alert`
- `sp_create_batch` -> portable procedure `sp_create_batch` + object block `PROCEDURE:portable.sp_create_batch`
- `sp_create_cashier_journal` -> portable procedure `sp_create_cashier_journal` + object block `PROCEDURE:portable.sp_create_cashier_journal`
- `sp_create_damage_report` -> portable procedure `sp_create_damage_report` + object block `PROCEDURE:portable.sp_create_damage_report`
- `sp_create_department` -> portable procedure `sp_create_department` + object block `PROCEDURE:portable.sp_create_department`
- `sp_create_performance_review` -> portable procedure `sp_create_performance_review` + object block `PROCEDURE:portable.sp_create_performance_review`
- `sp_create_product` -> portable procedure `sp_create_product` + object block `PROCEDURE:portable.sp_create_product`
- `sp_create_purchase_order` -> portable procedure `sp_create_purchase_order` + object block `PROCEDURE:portable.sp_create_purchase_order`
- `sp_create_purchase_requisition` -> portable procedure `sp_create_purchase_requisition` + object block `PROCEDURE:portable.sp_create_purchase_requisition`
- `sp_create_purchase_return` -> portable procedure `sp_create_purchase_return` + object block `PROCEDURE:portable.sp_create_purchase_return`
- `sp_create_reconciliation` -> portable procedure `sp_create_reconciliation` + object block `PROCEDURE:portable.sp_create_reconciliation`
- `sp_create_sales_order` -> portable procedure `sp_create_sales_order` + object block `PROCEDURE:portable.sp_create_sales_order`
- `sp_create_sales_return` -> portable procedure `sp_create_sales_return` + object block `PROCEDURE:portable.sp_create_sales_return`
- `sp_create_settlement` -> portable procedure `sp_create_settlement` + object block `PROCEDURE:portable.sp_create_settlement`
- `sp_create_shipment` -> portable procedure `sp_create_shipment` + object block `PROCEDURE:portable.sp_create_shipment`
- `sp_create_stock_transfer` -> portable procedure `sp_create_stock_transfer` + object block `PROCEDURE:portable.sp_create_stock_transfer`
- `sp_create_voucher` -> portable procedure `sp_create_voucher` + object block `PROCEDURE:portable.sp_create_voucher`
- `sp_create_warehouse` -> portable procedure `sp_create_warehouse` + object block `PROCEDURE:portable.sp_create_warehouse`
- `sp_customer_category_trend_by_store` -> portable procedure `sp_customer_category_trend_by_store` + object block `PROCEDURE:portable.sp_customer_category_trend_by_store`
- `sp_customer_recent_orders` -> portable procedure `sp_customer_recent_orders` + object block `PROCEDURE:portable.sp_customer_recent_orders`
- `sp_customer_store_preference` -> portable procedure `sp_customer_store_preference` + object block `PROCEDURE:portable.sp_customer_store_preference`
- `sp_customer_store_purchase_history` -> portable procedure `sp_customer_store_purchase_history` + object block `PROCEDURE:portable.sp_customer_store_purchase_history`
- `sp_daily_expiry_alert` -> portable procedure `sp_daily_expiry_alert` + object block `PROCEDURE:portable.sp_daily_expiry_alert`
- `sp_depreciate_assets` -> portable procedure `sp_depreciate_assets` + object block `PROCEDURE:portable.sp_depreciate_assets`
- `sp_employee_salary_history` -> portable procedure `sp_employee_salary_history` + object block `PROCEDURE:portable.sp_employee_salary_history`
- `sp_evaluate_supplier` -> portable procedure `sp_evaluate_supplier` + object block `PROCEDURE:portable.sp_evaluate_supplier`
- `sp_execute_damage_report` -> portable procedure `sp_execute_damage_report` + object block `PROCEDURE:portable.sp_execute_damage_report`
- `sp_expiry_heatmap` -> portable procedure `sp_expiry_heatmap` + object block `PROCEDURE:portable.sp_expiry_heatmap`
- `sp_file_tax_return` -> portable procedure `sp_file_tax_return` + object block `PROCEDURE:portable.sp_file_tax_return`
- `sp_find_best_supplier` -> portable procedure `sp_find_best_supplier` + object block `PROCEDURE:portable.sp_find_best_supplier`
- `sp_generate_all_business_data` -> portable procedure `sp_generate_all_business_data` + object block `PROCEDURE:portable.sp_generate_all_business_data`
- `sp_generate_ar_aging` -> portable procedure `sp_generate_ar_aging` + object block `PROCEDURE:portable.sp_generate_ar_aging`
- `sp_generate_attendance` -> portable procedure `sp_generate_attendance` + object block `PROCEDURE:portable.sp_generate_attendance`
- `sp_generate_purchase_data` -> portable procedure `sp_generate_purchase_data` + object block `PROCEDURE:portable.sp_generate_purchase_data`
- `sp_generate_sales_data` -> portable procedure `sp_generate_sales_data` + object block `PROCEDURE:portable.sp_generate_sales_data`
- `sp_grant_role_to_employee` -> portable procedure `sp_grant_role_to_employee` + object block `PROCEDURE:portable.sp_grant_role_to_employee`
- `sp_hire_employee` -> portable procedure `sp_hire_employee` + object block `PROCEDURE:portable.sp_hire_employee`
- `sp_inventory_turnover` -> portable procedure `sp_inventory_turnover` + object block `PROCEDURE:portable.sp_inventory_turnover`
- `sp_issue_work_order_materials` -> portable procedure `sp_issue_work_order_materials` + object block `PROCEDURE:portable.sp_issue_work_order_materials`
- `sp_monthly_store_ranking` -> portable procedure `sp_monthly_store_ranking` + object block `PROCEDURE:portable.sp_monthly_store_ranking`
- `sp_pick_and_pack` -> portable procedure `sp_pick_and_pack` + object block `PROCEDURE:portable.sp_pick_and_pack`
- `sp_poor_attendance_report` -> portable procedure `sp_poor_attendance_report` + object block `PROCEDURE:portable.sp_poor_attendance_report`
- `sp_post_stocktake` -> portable procedure `sp_post_stocktake` + object block `PROCEDURE:portable.sp_post_stocktake`
- `sp_post_voucher` -> portable procedure `sp_post_voucher` + object block `PROCEDURE:portable.sp_post_voucher`
- `sp_process_approval` -> portable procedure `sp_process_approval` + object block `PROCEDURE:portable.sp_process_approval`
- `sp_process_expired_batches` -> portable procedure `sp_process_expired_batches` + object block `PROCEDURE:portable.sp_process_expired_batches`
- `sp_process_salary` -> portable procedure `sp_process_salary` + object block `PROCEDURE:portable.sp_process_salary`
- `sp_process_sales_return_refund` -> portable procedure `sp_process_sales_return_refund` + object block `PROCEDURE:portable.sp_process_sales_return_refund`
- `sp_product_batch_detail` -> portable procedure `sp_product_batch_detail` + object block `PROCEDURE:portable.sp_product_batch_detail`
- `sp_promote_to_manager` -> portable procedure `sp_promote_to_manager` + object block `PROCEDURE:portable.sp_promote_to_manager`
- `sp_receive_purchase` -> portable procedure `sp_receive_purchase` + object block `PROCEDURE:portable.sp_receive_purchase`
- `sp_resign_employee` -> portable procedure `sp_resign_employee` + object block `PROCEDURE:portable.sp_resign_employee`
- `sp_return_financial_impact` -> portable procedure `sp_return_financial_impact` + object block `PROCEDURE:portable.sp_return_financial_impact`
- `sp_return_full_trace` -> portable procedure `sp_return_full_trace` + object block `PROCEDURE:portable.sp_return_full_trace`
- `sp_return_rate_analysis` -> portable procedure `sp_return_rate_analysis` + object block `PROCEDURE:portable.sp_return_rate_analysis`
- `sp_sales_performance_ranking` -> portable procedure `sp_sales_performance_ranking` + object block `PROCEDURE:portable.sp_sales_performance_ranking`
- `sp_scan_serial_number` -> portable procedure `sp_scan_serial_number` + object block `PROCEDURE:portable.sp_scan_serial_number`
- `sp_settle_consignment` -> portable procedure `sp_settle_consignment` + object block `PROCEDURE:portable.sp_settle_consignment`
- `sp_stocktake` -> portable procedure `sp_stocktake` + object block `PROCEDURE:portable.sp_stocktake`
- `sp_store_audit_pl` -> portable procedure `sp_store_audit_pl` + object block `PROCEDURE:portable.sp_store_audit_pl`
- `sp_store_bestsellers` -> portable procedure `sp_store_bestsellers` + object block `PROCEDURE:portable.sp_store_bestsellers`
- `sp_store_dashboard` -> portable procedure `sp_store_dashboard` + object block `PROCEDURE:portable.sp_store_dashboard`
- `sp_store_expiry_dashboard` -> portable procedure `sp_store_expiry_dashboard` + object block `PROCEDURE:portable.sp_store_expiry_dashboard`
- `sp_store_performance_compare` -> portable procedure `sp_store_performance_compare` + object block `PROCEDURE:portable.sp_store_performance_compare`
- `sp_store_product_affinity` -> portable procedure `sp_store_product_affinity` + object block `PROCEDURE:portable.sp_store_product_affinity`
- `sp_store_sales_forecast` -> portable procedure `sp_store_sales_forecast` + object block `PROCEDURE:portable.sp_store_sales_forecast`
- `sp_submit_approval` -> portable procedure `sp_submit_approval` + object block `PROCEDURE:portable.sp_submit_approval`
- `sp_supplier_geographic_analysis` -> portable procedure `sp_supplier_geographic_analysis` + object block `PROCEDURE:portable.sp_supplier_geographic_analysis`
- `sp_three_way_matching` -> portable procedure `sp_three_way_matching` + object block `PROCEDURE:portable.sp_three_way_matching`
- `sp_transfer_inventory` -> portable procedure `sp_transfer_inventory` + object block `PROCEDURE:portable.sp_transfer_inventory`
- `sp_update_supplier_metrics` -> portable procedure `sp_update_supplier_metrics` + object block `PROCEDURE:portable.sp_update_supplier_metrics`
- `sp_validate_promotion` -> portable procedure `sp_validate_promotion` + object block `PROCEDURE:portable.sp_validate_promotion`

### Functions

- `fn_calculate_income_tax` -> portable function `fn_calculate_income_tax` + object block `FUNCTION:portable.fn_calculate_income_tax`
- `fn_convert_currency` -> portable function `fn_convert_currency` + object block `FUNCTION:portable.fn_convert_currency`
- `fn_employee_full_name` -> portable function `fn_employee_full_name` + object block `FUNCTION:portable.fn_employee_full_name`
- `fn_estimate_shipping_cost` -> portable function `fn_estimate_shipping_cost` + object block `FUNCTION:portable.fn_estimate_shipping_cost`
- `fn_get_attendance_rate` -> portable function `fn_get_attendance_rate` + object block `FUNCTION:portable.fn_get_attendance_rate`
- `fn_get_customer_clv` -> portable function `fn_get_customer_clv` + object block `FUNCTION:portable.fn_get_customer_clv`
- `fn_get_customer_credit_available` -> portable function `fn_get_customer_credit_available` + object block `FUNCTION:portable.fn_get_customer_credit_available`
- `fn_get_customer_credit_score` -> portable function `fn_get_customer_credit_score` + object block `FUNCTION:portable.fn_get_customer_credit_score`
- `fn_get_customer_repurchase_rate` -> portable function `fn_get_customer_repurchase_rate` + object block `FUNCTION:portable.fn_get_customer_repurchase_rate`
- `fn_get_customer_status` -> portable function `fn_get_customer_status` + object block `FUNCTION:portable.fn_get_customer_status`
- `fn_get_days_sales_outstanding` -> portable function `fn_get_days_sales_outstanding` + object block `FUNCTION:portable.fn_get_days_sales_outstanding`
- `fn_get_employee_tenure` -> portable function `fn_get_employee_tenure` + object block `FUNCTION:portable.fn_get_employee_tenure`
- `fn_get_gross_margin` -> portable function `fn_get_gross_margin` + object block `FUNCTION:portable.fn_get_gross_margin`
- `fn_get_inspection_pass_rate` -> portable function `fn_get_inspection_pass_rate` + object block `FUNCTION:portable.fn_get_inspection_pass_rate`
- `fn_get_inventory_turnover_days` -> portable function `fn_get_inventory_turnover_days` + object block `FUNCTION:portable.fn_get_inventory_turnover_days`
- `fn_get_monthly_sales` -> portable function `fn_get_monthly_sales` + object block `FUNCTION:portable.fn_get_monthly_sales`
- `fn_get_product_stock` -> portable function `fn_get_product_stock` + object block `FUNCTION:portable.fn_get_product_stock`
- `fn_get_project_completion_pct` -> portable function `fn_get_project_completion_pct` + object block `FUNCTION:portable.fn_get_project_completion_pct`
- `fn_haversine_distance` -> portable function `fn_haversine_distance` + object block `FUNCTION:portable.fn_haversine_distance`
- `fn_supplier_score` -> portable function `fn_supplier_score` + object block `FUNCTION:portable.fn_supplier_score`

### Triggers

- `trg_audit_employee_insert` -> portable trigger `trg_audit_employee_insert` + object block `TRIGGER:portable.trg_audit_employee_insert`
- `trg_audit_employee_update` -> portable trigger `trg_audit_employee_update` + object block `TRIGGER:portable.trg_audit_employee_update`
- `trg_batch_exhausted` -> portable trigger `trg_batch_exhausted` + object block `TRIGGER:portable.trg_batch_exhausted`
- `trg_customer_credit_check` -> portable trigger `trg_customer_credit_check` + object block `TRIGGER:portable.trg_customer_credit_check`
- `trg_inventory_transaction_after_insert` -> portable trigger `trg_inventory_transaction_after_insert` + object block `TRIGGER:portable.trg_inventory_transaction_after_insert`
- `trg_inventory_update_batch` -> portable trigger `trg_inventory_update_batch` + object block `TRIGGER:portable.trg_inventory_update_batch`
- `trg_purchase_order_received` -> portable trigger `trg_purchase_order_received` + object block `TRIGGER:portable.trg_purchase_order_received`
- `trg_requisition_status_change` -> portable trigger `trg_requisition_status_change` + object block `TRIGGER:portable.trg_requisition_status_change`
- `trg_salary_payment_after_insert` -> portable trigger `trg_salary_payment_after_insert` + object block `TRIGGER:portable.trg_salary_payment_after_insert`
- `trg_sales_order_delivered` -> portable trigger `trg_sales_order_delivered` + object block `TRIGGER:portable.trg_sales_order_delivered`
- `trg_sales_return_approved` -> portable trigger `trg_sales_return_approved` + object block `TRIGGER:portable.trg_sales_return_approved`
- `trg_voucher_before_post` -> portable trigger `trg_voucher_before_post` + object block `TRIGGER:portable.trg_voucher_before_post`
