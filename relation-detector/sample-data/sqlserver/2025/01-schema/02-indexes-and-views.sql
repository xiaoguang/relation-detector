-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

CREATE INDEX [ix_departments_parent_id] ON [dbo].[departments] ([parent_id]);

CREATE INDEX [ix_positions_department_id] ON [dbo].[positions] ([department_id]);

CREATE INDEX [ix_employees_department_id] ON [dbo].[employees] ([department_id]);

CREATE INDEX [ix_employees_position_id] ON [dbo].[employees] ([position_id]);

CREATE INDEX [ix_employees_manager_id] ON [dbo].[employees] ([manager_id]);

CREATE INDEX [ix_employee_salary_log_employee_id] ON [dbo].[employee_salary_log] ([employee_id]);

CREATE INDEX [ix_attendance_employee_id] ON [dbo].[attendance] ([employee_id]);

CREATE INDEX [ix_leave_records_employee_id] ON [dbo].[leave_records] ([employee_id]);

CREATE INDEX [ix_permissions_parent_id] ON [dbo].[permissions] ([parent_id]);

CREATE INDEX [ix_role_permissions_role_id] ON [dbo].[role_permissions] ([role_id]);

CREATE INDEX [ix_role_permissions_permission_id] ON [dbo].[role_permissions] ([permission_id]);

CREATE INDEX [ix_employee_roles_employee_id] ON [dbo].[employee_roles] ([employee_id]);

CREATE INDEX [ix_employee_roles_role_id] ON [dbo].[employee_roles] ([role_id]);

CREATE INDEX [ix_product_categories_parent_id] ON [dbo].[product_categories] ([parent_id]);

CREATE INDEX [ix_products_category_id] ON [dbo].[products] ([category_id]);

CREATE INDEX [ix_supplier_products_supplier_id] ON [dbo].[supplier_products] ([supplier_id]);

CREATE INDEX [ix_supplier_products_product_id] ON [dbo].[supplier_products] ([product_id]);

CREATE INDEX [ix_product_batches_product_id] ON [dbo].[product_batches] ([product_id]);

CREATE INDEX [ix_product_batches_supplier_id] ON [dbo].[product_batches] ([supplier_id]);

CREATE INDEX [ix_warehouses_manager_id] ON [dbo].[warehouses] ([manager_id]);

CREATE INDEX [ix_inventory_product_id] ON [dbo].[inventory] ([product_id]);

CREATE INDEX [ix_inventory_batch_id] ON [dbo].[inventory] ([batch_id]);

CREATE INDEX [ix_inventory_warehouse_id] ON [dbo].[inventory] ([warehouse_id]);

CREATE INDEX [ix_inventory_transactions_product_id] ON [dbo].[inventory_transactions] ([product_id]);

CREATE INDEX [ix_inventory_transactions_batch_id] ON [dbo].[inventory_transactions] ([batch_id]);

CREATE INDEX [ix_inventory_transactions_warehouse_id] ON [dbo].[inventory_transactions] ([warehouse_id]);

CREATE INDEX [ix_purchase_requisitions_department_id] ON [dbo].[purchase_requisitions] ([department_id]);

CREATE INDEX [ix_purchase_requisitions_requester_id] ON [dbo].[purchase_requisitions] ([requester_id]);

CREATE INDEX [ix_purchase_requisition_items_requisition_id] ON [dbo].[purchase_requisition_items] ([requisition_id]);

CREATE INDEX [ix_purchase_requisition_items_product_id] ON [dbo].[purchase_requisition_items] ([product_id]);

CREATE INDEX [ix_purchase_orders_supplier_id] ON [dbo].[purchase_orders] ([supplier_id]);

CREATE INDEX [ix_purchase_orders_requisition_id] ON [dbo].[purchase_orders] ([requisition_id]);

CREATE INDEX [ix_purchase_orders_purchaser_id] ON [dbo].[purchase_orders] ([purchaser_id]);

CREATE INDEX [ix_purchase_order_items_order_id] ON [dbo].[purchase_order_items] ([order_id]);

CREATE INDEX [ix_purchase_order_items_product_id] ON [dbo].[purchase_order_items] ([product_id]);

CREATE INDEX [ix_purchase_receipts_order_id] ON [dbo].[purchase_receipts] ([order_id]);

CREATE INDEX [ix_purchase_receipts_warehouse_id] ON [dbo].[purchase_receipts] ([warehouse_id]);

CREATE INDEX [ix_purchase_receipts_receiver_id] ON [dbo].[purchase_receipts] ([receiver_id]);

CREATE INDEX [ix_purchase_receipt_items_receipt_id] ON [dbo].[purchase_receipt_items] ([receipt_id]);

CREATE INDEX [ix_purchase_receipt_items_product_id] ON [dbo].[purchase_receipt_items] ([product_id]);

CREATE INDEX [ix_purchase_receipt_items_batch_id] ON [dbo].[purchase_receipt_items] ([batch_id]);

CREATE INDEX [ix_sales_orders_customer_id] ON [dbo].[sales_orders] ([customer_id]);

CREATE INDEX [ix_sales_orders_salesperson_id] ON [dbo].[sales_orders] ([salesperson_id]);

CREATE INDEX [ix_sales_orders_warehouse_id] ON [dbo].[sales_orders] ([warehouse_id]);

CREATE INDEX [ix_sales_order_items_order_id] ON [dbo].[sales_order_items] ([order_id]);

CREATE INDEX [ix_sales_order_items_product_id] ON [dbo].[sales_order_items] ([product_id]);

CREATE INDEX [ix_sales_order_items_batch_id] ON [dbo].[sales_order_items] ([batch_id]);

CREATE INDEX [ix_sales_returns_order_id] ON [dbo].[sales_returns] ([order_id]);

CREATE INDEX [ix_sales_returns_customer_id] ON [dbo].[sales_returns] ([customer_id]);

CREATE INDEX [ix_sales_returns_warehouse_id] ON [dbo].[sales_returns] ([warehouse_id]);

CREATE INDEX [ix_sales_returns_handler_id] ON [dbo].[sales_returns] ([handler_id]);

CREATE INDEX [ix_sales_returns_approved_by] ON [dbo].[sales_returns] ([approved_by]);

CREATE INDEX [ix_sales_returns_refund_voucher_id] ON [dbo].[sales_returns] ([refund_voucher_id]);

CREATE INDEX [ix_sales_return_items_return_id] ON [dbo].[sales_return_items] ([return_id]);

CREATE INDEX [ix_sales_return_items_product_id] ON [dbo].[sales_return_items] ([product_id]);

CREATE INDEX [ix_sales_return_items_batch_id] ON [dbo].[sales_return_items] ([batch_id]);

CREATE INDEX [ix_purchase_returns_purchase_order_id] ON [dbo].[purchase_returns] ([purchase_order_id]);

CREATE INDEX [ix_purchase_returns_purchase_receipt_id] ON [dbo].[purchase_returns] ([purchase_receipt_id]);

CREATE INDEX [ix_purchase_returns_supplier_id] ON [dbo].[purchase_returns] ([supplier_id]);

CREATE INDEX [ix_purchase_returns_warehouse_id] ON [dbo].[purchase_returns] ([warehouse_id]);

CREATE INDEX [ix_purchase_returns_handler_id] ON [dbo].[purchase_returns] ([handler_id]);

CREATE INDEX [ix_purchase_returns_approved_by] ON [dbo].[purchase_returns] ([approved_by]);

CREATE INDEX [ix_purchase_returns_refund_voucher_id] ON [dbo].[purchase_returns] ([refund_voucher_id]);

CREATE INDEX [ix_purchase_return_items_return_id] ON [dbo].[purchase_return_items] ([return_id]);

CREATE INDEX [ix_purchase_return_items_product_id] ON [dbo].[purchase_return_items] ([product_id]);

CREATE INDEX [ix_purchase_return_items_batch_id] ON [dbo].[purchase_return_items] ([batch_id]);

CREATE INDEX [ix_damage_reports_warehouse_id] ON [dbo].[damage_reports] ([warehouse_id]);

CREATE INDEX [ix_damage_reports_reported_by] ON [dbo].[damage_reports] ([reported_by]);

CREATE INDEX [ix_damage_reports_approved_by] ON [dbo].[damage_reports] ([approved_by]);

CREATE INDEX [ix_damage_reports_executed_by] ON [dbo].[damage_reports] ([executed_by]);

CREATE INDEX [ix_damage_reports_voucher_id] ON [dbo].[damage_reports] ([voucher_id]);

CREATE INDEX [ix_damage_report_items_report_id] ON [dbo].[damage_report_items] ([report_id]);

CREATE INDEX [ix_damage_report_items_product_id] ON [dbo].[damage_report_items] ([product_id]);

CREATE INDEX [ix_damage_report_items_batch_id] ON [dbo].[damage_report_items] ([batch_id]);

CREATE INDEX [ix_accounts_parent_id] ON [dbo].[accounts] ([parent_id]);

CREATE INDEX [ix_vouchers_prepared_by] ON [dbo].[vouchers] ([prepared_by]);

CREATE INDEX [ix_vouchers_reviewed_by] ON [dbo].[vouchers] ([reviewed_by]);

CREATE INDEX [ix_vouchers_posted_by] ON [dbo].[vouchers] ([posted_by]);

CREATE INDEX [ix_voucher_items_voucher_id] ON [dbo].[voucher_items] ([voucher_id]);

CREATE INDEX [ix_voucher_items_account_id] ON [dbo].[voucher_items] ([account_id]);

CREATE INDEX [ix_cashier_journals_account_id] ON [dbo].[cashier_journals] ([account_id]);

CREATE INDEX [ix_cashier_journals_cashier_id] ON [dbo].[cashier_journals] ([cashier_id]);

CREATE INDEX [ix_cashier_journals_voucher_id] ON [dbo].[cashier_journals] ([voucher_id]);

CREATE INDEX [ix_salary_payments_employee_id] ON [dbo].[salary_payments] ([employee_id]);

CREATE INDEX [ix_salary_payments_voucher_id] ON [dbo].[salary_payments] ([voucher_id]);

CREATE INDEX [ix_reconciliations_account_id] ON [dbo].[reconciliations] ([account_id]);

CREATE INDEX [ix_reconciliations_prepared_by] ON [dbo].[reconciliations] ([prepared_by]);

CREATE INDEX [ix_reconciliations_reviewed_by] ON [dbo].[reconciliations] ([reviewed_by]);

CREATE INDEX [ix_reconciliation_items_reconciliation_id] ON [dbo].[reconciliation_items] ([reconciliation_id]);

CREATE INDEX [ix_settlements_voucher_id] ON [dbo].[settlements] ([voucher_id]);

CREATE INDEX [ix_settlements_prepared_by] ON [dbo].[settlements] ([prepared_by]);

CREATE INDEX [ix_settlements_approved_by] ON [dbo].[settlements] ([approved_by]);

CREATE INDEX [ix_settlement_items_settlement_id] ON [dbo].[settlement_items] ([settlement_id]);

CREATE INDEX [ix_shipments_order_id] ON [dbo].[shipments] ([order_id]);

CREATE INDEX [ix_shipments_warehouse_id] ON [dbo].[shipments] ([warehouse_id]);

CREATE INDEX [ix_shipping_tracks_shipment_id] ON [dbo].[shipping_tracks] ([shipment_id]);

CREATE INDEX [ix_commission_rules_product_category_id] ON [dbo].[commission_rules] ([product_category_id]);

CREATE INDEX [ix_sales_commissions_employee_id] ON [dbo].[sales_commissions] ([employee_id]);

CREATE INDEX [ix_sales_commissions_order_id] ON [dbo].[sales_commissions] ([order_id]);

CREATE INDEX [ix_promotion_products_promotion_id] ON [dbo].[promotion_products] ([promotion_id]);

CREATE INDEX [ix_promotion_products_product_id] ON [dbo].[promotion_products] ([product_id]);

CREATE INDEX [ix_promotion_products_category_id] ON [dbo].[promotion_products] ([category_id]);

CREATE INDEX [ix_promotion_usages_promotion_id] ON [dbo].[promotion_usages] ([promotion_id]);

CREATE INDEX [ix_promotion_usages_order_id] ON [dbo].[promotion_usages] ([order_id]);

CREATE INDEX [ix_promotion_usages_customer_id] ON [dbo].[promotion_usages] ([customer_id]);

CREATE INDEX [ix_invoices_supplier_id] ON [dbo].[invoices] ([supplier_id]);

CREATE INDEX [ix_invoices_customer_id] ON [dbo].[invoices] ([customer_id]);

CREATE INDEX [ix_three_way_matching_invoice_id] ON [dbo].[three_way_matching] ([invoice_id]);

CREATE INDEX [ix_three_way_matching_purchase_order_id] ON [dbo].[three_way_matching] ([purchase_order_id]);

CREATE INDEX [ix_three_way_matching_purchase_receipt_id] ON [dbo].[three_way_matching] ([purchase_receipt_id]);

CREATE INDEX [ix_three_way_matching_product_id] ON [dbo].[three_way_matching] ([product_id]);

CREATE INDEX [ix_fixed_assets_department_id] ON [dbo].[fixed_assets] ([department_id]);

CREATE INDEX [ix_fixed_assets_custodian_id] ON [dbo].[fixed_assets] ([custodian_id]);

CREATE INDEX [ix_depreciation_log_asset_id] ON [dbo].[depreciation_log] ([asset_id]);

CREATE INDEX [ix_boms_parent_product_id] ON [dbo].[boms] ([parent_product_id]);

CREATE INDEX [ix_boms_child_product_id] ON [dbo].[boms] ([child_product_id]);

CREATE INDEX [ix_work_orders_product_id] ON [dbo].[work_orders] ([product_id]);

CREATE INDEX [ix_work_orders_bom_id] ON [dbo].[work_orders] ([bom_id]);

CREATE INDEX [ix_work_orders_warehouse_id] ON [dbo].[work_orders] ([warehouse_id]);

CREATE INDEX [ix_work_order_materials_work_order_id] ON [dbo].[work_order_materials] ([work_order_id]);

CREATE INDEX [ix_work_order_materials_product_id] ON [dbo].[work_order_materials] ([product_id]);

CREATE INDEX [ix_service_tickets_customer_id] ON [dbo].[service_tickets] ([customer_id]);

CREATE INDEX [ix_service_tickets_order_id] ON [dbo].[service_tickets] ([order_id]);

CREATE INDEX [ix_service_tickets_product_id] ON [dbo].[service_tickets] ([product_id]);

CREATE INDEX [ix_service_tickets_assigned_to] ON [dbo].[service_tickets] ([assigned_to]);

CREATE INDEX [ix_contracts_prepared_by] ON [dbo].[contracts] ([prepared_by]);

CREATE INDEX [ix_contracts_approved_by] ON [dbo].[contracts] ([approved_by]);

CREATE INDEX [ix_contract_milestones_contract_id] ON [dbo].[contract_milestones] ([contract_id]);

CREATE INDEX [ix_ar_aging_snapshots_customer_id] ON [dbo].[ar_aging_snapshots] ([customer_id]);

CREATE INDEX [ix_ar_aging_snapshots_order_id] ON [dbo].[ar_aging_snapshots] ([order_id]);

CREATE INDEX [ix_ap_aging_snapshots_supplier_id] ON [dbo].[ap_aging_snapshots] ([supplier_id]);

CREATE INDEX [ix_ap_aging_snapshots_order_id] ON [dbo].[ap_aging_snapshots] ([order_id]);

CREATE INDEX [ix_tax_invoices_verified_by] ON [dbo].[tax_invoices] ([verified_by]);

CREATE INDEX [ix_tax_filings_prepared_by] ON [dbo].[tax_filings] ([prepared_by]);

CREATE INDEX [ix_inspection_standards_product_id] ON [dbo].[inspection_standards] ([product_id]);

CREATE INDEX [ix_inspection_reports_product_id] ON [dbo].[inspection_reports] ([product_id]);

CREATE INDEX [ix_inspection_reports_batch_id] ON [dbo].[inspection_reports] ([batch_id]);

CREATE INDEX [ix_inspection_reports_standard_id] ON [dbo].[inspection_reports] ([standard_id]);

CREATE INDEX [ix_inspection_reports_inspector_id] ON [dbo].[inspection_reports] ([inspector_id]);

CREATE INDEX [ix_approval_nodes_workflow_id] ON [dbo].[approval_nodes] ([workflow_id]);

CREATE INDEX [ix_approval_instances_workflow_id] ON [dbo].[approval_instances] ([workflow_id]);

CREATE INDEX [ix_approval_instances_submitted_by] ON [dbo].[approval_instances] ([submitted_by]);

CREATE INDEX [ix_approval_records_instance_id] ON [dbo].[approval_records] ([instance_id]);

CREATE INDEX [ix_approval_records_node_id] ON [dbo].[approval_records] ([node_id]);

CREATE INDEX [ix_approval_records_approver_id] ON [dbo].[approval_records] ([approver_id]);

CREATE INDEX [ix_projects_department_id] ON [dbo].[projects] ([department_id]);

CREATE INDEX [ix_projects_manager_id] ON [dbo].[projects] ([manager_id]);

CREATE INDEX [ix_project_costs_project_id] ON [dbo].[project_costs] ([project_id]);

CREATE INDEX [ix_foreign_currency_accounts_account_id] ON [dbo].[foreign_currency_accounts] ([account_id]);

CREATE INDEX [ix_performance_reviews_employee_id] ON [dbo].[performance_reviews] ([employee_id]);

CREATE INDEX [ix_performance_reviews_reviewer_id] ON [dbo].[performance_reviews] ([reviewer_id]);

CREATE INDEX [ix_serial_numbers_product_id] ON [dbo].[serial_numbers] ([product_id]);

CREATE INDEX [ix_serial_numbers_batch_id] ON [dbo].[serial_numbers] ([batch_id]);

CREATE INDEX [ix_serial_numbers_warehouse_id] ON [dbo].[serial_numbers] ([warehouse_id]);

CREATE INDEX [ix_serial_number_logs_serial_number_id] ON [dbo].[serial_number_logs] ([serial_number_id]);

CREATE INDEX [ix_consignment_inventory_product_id] ON [dbo].[consignment_inventory] ([product_id]);

CREATE INDEX [ix_consignment_inventory_batch_id] ON [dbo].[consignment_inventory] ([batch_id]);

CREATE INDEX [ix_consignment_inventory_customer_id] ON [dbo].[consignment_inventory] ([customer_id]);

CREATE INDEX [ix_consignment_consumptions_consignment_id] ON [dbo].[consignment_consumptions] ([consignment_id]);

CREATE INDEX [ix_price_change_logs_product_id] ON [dbo].[price_change_logs] ([product_id]);

CREATE INDEX [ix_price_change_logs_changed_by] ON [dbo].[price_change_logs] ([changed_by]);

CREATE INDEX [ix_ledger_books_tenant_id] ON [dbo].[ledger_books] ([tenant_id]);

CREATE INDEX [ix_customer_addresses_customer_id] ON [dbo].[customer_addresses] ([customer_id]);

CREATE INDEX [ix_supplier_addresses_supplier_id] ON [dbo].[supplier_addresses] ([supplier_id]);

CREATE INDEX [ix_accounting_periods_ledger_book_id] ON [dbo].[accounting_periods] ([ledger_book_id]);

CREATE INDEX [ix_accounting_periods_closed_by] ON [dbo].[accounting_periods] ([closed_by]);

CREATE INDEX [ix_period_close_jobs_period_id] ON [dbo].[period_close_jobs] ([period_id]);

CREATE INDEX [ix_payment_receipts_account_id] ON [dbo].[payment_receipts] ([account_id]);

CREATE INDEX [ix_payment_receipts_handled_by] ON [dbo].[payment_receipts] ([handled_by]);

CREATE INDEX [ix_payment_receipt_allocations_receipt_id] ON [dbo].[payment_receipt_allocations] ([receipt_id]);

CREATE INDEX [ix_stocktakes_warehouse_id] ON [dbo].[stocktakes] ([warehouse_id]);

CREATE INDEX [ix_stocktakes_created_by] ON [dbo].[stocktakes] ([created_by]);

CREATE INDEX [ix_stocktakes_reviewed_by] ON [dbo].[stocktakes] ([reviewed_by]);

CREATE INDEX [ix_stocktake_items_stocktake_id] ON [dbo].[stocktake_items] ([stocktake_id]);

CREATE INDEX [ix_stocktake_items_product_id] ON [dbo].[stocktake_items] ([product_id]);

CREATE INDEX [ix_stocktake_items_batch_id] ON [dbo].[stocktake_items] ([batch_id]);

CREATE INDEX [ix_stock_transfers_from_warehouse_id] ON [dbo].[stock_transfers] ([from_warehouse_id]);

CREATE INDEX [ix_stock_transfers_to_warehouse_id] ON [dbo].[stock_transfers] ([to_warehouse_id]);

CREATE INDEX [ix_stock_transfers_requested_by] ON [dbo].[stock_transfers] ([requested_by]);

CREATE INDEX [ix_stock_transfers_approved_by] ON [dbo].[stock_transfers] ([approved_by]);

CREATE VIEW [dbo].[vw_relation_1] AS
SELECT c1.[parent_id] AS [source_id], p1.[id] AS [target_id]
FROM [dbo].[departments] AS c1
INNER JOIN [dbo].[departments] AS p1 ON c1.[parent_id] = p1.[id];

CREATE VIEW [dbo].[vw_relation_2] AS
SELECT c2.[department_id] AS [source_id], p2.[id] AS [target_id]
FROM [dbo].[positions] AS c2
INNER JOIN [dbo].[departments] AS p2 ON c2.[department_id] = p2.[id];

CREATE VIEW [dbo].[vw_relation_3] AS
SELECT c3.[department_id] AS [source_id], p3.[id] AS [target_id]
FROM [dbo].[employees] AS c3
INNER JOIN [dbo].[departments] AS p3 ON c3.[department_id] = p3.[id];

CREATE VIEW [dbo].[vw_relation_4] AS
SELECT c4.[position_id] AS [source_id], p4.[id] AS [target_id]
FROM [dbo].[employees] AS c4
INNER JOIN [dbo].[positions] AS p4 ON c4.[position_id] = p4.[id];

CREATE VIEW [dbo].[vw_relation_5] AS
SELECT c5.[manager_id] AS [source_id], p5.[id] AS [target_id]
FROM [dbo].[employees] AS c5
INNER JOIN [dbo].[employees] AS p5 ON c5.[manager_id] = p5.[id];

CREATE VIEW [dbo].[vw_relation_6] AS
SELECT c6.[employee_id] AS [source_id], p6.[id] AS [target_id]
FROM [dbo].[employee_salary_log] AS c6
INNER JOIN [dbo].[employees] AS p6 ON c6.[employee_id] = p6.[id];

CREATE VIEW [dbo].[vw_relation_7] AS
SELECT c7.[employee_id] AS [source_id], p7.[id] AS [target_id]
FROM [dbo].[attendance] AS c7
INNER JOIN [dbo].[employees] AS p7 ON c7.[employee_id] = p7.[id];

CREATE VIEW [dbo].[vw_relation_8] AS
SELECT c8.[employee_id] AS [source_id], p8.[id] AS [target_id]
FROM [dbo].[leave_records] AS c8
INNER JOIN [dbo].[employees] AS p8 ON c8.[employee_id] = p8.[id];

CREATE VIEW [dbo].[vw_relation_9] AS
SELECT c9.[parent_id] AS [source_id], p9.[id] AS [target_id]
FROM [dbo].[permissions] AS c9
INNER JOIN [dbo].[permissions] AS p9 ON c9.[parent_id] = p9.[id];

CREATE VIEW [dbo].[vw_relation_10] AS
SELECT c10.[role_id] AS [source_id], p10.[id] AS [target_id]
FROM [dbo].[role_permissions] AS c10
INNER JOIN [dbo].[roles] AS p10 ON c10.[role_id] = p10.[id];

CREATE VIEW [dbo].[vw_relation_11] AS
SELECT c11.[permission_id] AS [source_id], p11.[id] AS [target_id]
FROM [dbo].[role_permissions] AS c11
INNER JOIN [dbo].[permissions] AS p11 ON c11.[permission_id] = p11.[id];

CREATE VIEW [dbo].[vw_relation_12] AS
SELECT c12.[employee_id] AS [source_id], p12.[id] AS [target_id]
FROM [dbo].[employee_roles] AS c12
INNER JOIN [dbo].[employees] AS p12 ON c12.[employee_id] = p12.[id];

CREATE VIEW [dbo].[vw_relation_13] AS
SELECT c13.[role_id] AS [source_id], p13.[id] AS [target_id]
FROM [dbo].[employee_roles] AS c13
INNER JOIN [dbo].[roles] AS p13 ON c13.[role_id] = p13.[id];

CREATE VIEW [dbo].[vw_relation_14] AS
SELECT c14.[parent_id] AS [source_id], p14.[id] AS [target_id]
FROM [dbo].[product_categories] AS c14
INNER JOIN [dbo].[product_categories] AS p14 ON c14.[parent_id] = p14.[id];

CREATE VIEW [dbo].[vw_relation_15] AS
SELECT c15.[category_id] AS [source_id], p15.[id] AS [target_id]
FROM [dbo].[products] AS c15
INNER JOIN [dbo].[product_categories] AS p15 ON c15.[category_id] = p15.[id];

CREATE VIEW [dbo].[vw_relation_16] AS
SELECT c16.[supplier_id] AS [source_id], p16.[id] AS [target_id]
FROM [dbo].[supplier_products] AS c16
INNER JOIN [dbo].[suppliers] AS p16 ON c16.[supplier_id] = p16.[id];

CREATE VIEW [dbo].[vw_relation_17] AS
SELECT c17.[product_id] AS [source_id], p17.[id] AS [target_id]
FROM [dbo].[supplier_products] AS c17
INNER JOIN [dbo].[products] AS p17 ON c17.[product_id] = p17.[id];

CREATE VIEW [dbo].[vw_relation_18] AS
SELECT c18.[product_id] AS [source_id], p18.[id] AS [target_id]
FROM [dbo].[product_batches] AS c18
INNER JOIN [dbo].[products] AS p18 ON c18.[product_id] = p18.[id];

CREATE VIEW [dbo].[vw_relation_19] AS
SELECT c19.[supplier_id] AS [source_id], p19.[id] AS [target_id]
FROM [dbo].[product_batches] AS c19
INNER JOIN [dbo].[suppliers] AS p19 ON c19.[supplier_id] = p19.[id];

CREATE VIEW [dbo].[vw_relation_20] AS
SELECT c20.[manager_id] AS [source_id], p20.[id] AS [target_id]
FROM [dbo].[warehouses] AS c20
INNER JOIN [dbo].[employees] AS p20 ON c20.[manager_id] = p20.[id];

CREATE VIEW [dbo].[vw_relation_21] AS
SELECT c21.[product_id] AS [source_id], p21.[id] AS [target_id]
FROM [dbo].[inventory] AS c21
INNER JOIN [dbo].[products] AS p21 ON c21.[product_id] = p21.[id];

CREATE VIEW [dbo].[vw_relation_22] AS
SELECT c22.[batch_id] AS [source_id], p22.[id] AS [target_id]
FROM [dbo].[inventory] AS c22
INNER JOIN [dbo].[product_batches] AS p22 ON c22.[batch_id] = p22.[id];

CREATE VIEW [dbo].[vw_relation_23] AS
SELECT c23.[warehouse_id] AS [source_id], p23.[id] AS [target_id]
FROM [dbo].[inventory] AS c23
INNER JOIN [dbo].[warehouses] AS p23 ON c23.[warehouse_id] = p23.[id];

CREATE VIEW [dbo].[vw_relation_24] AS
SELECT c24.[product_id] AS [source_id], p24.[id] AS [target_id]
FROM [dbo].[inventory_transactions] AS c24
INNER JOIN [dbo].[products] AS p24 ON c24.[product_id] = p24.[id];
