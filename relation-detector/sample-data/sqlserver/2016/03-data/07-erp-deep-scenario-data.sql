-- ============================================================
-- SQL Server ERP natural sample data.
-- Seed and transaction rows use ordinary INSERT VALUES so natural sample-data
-- does not inflate relationship/lineage counts with synthetic relationship SQL.
-- Baseline is T-SQL 2016-compatible for all SQL Server versions.
-- ============================================================

-- Natural seed rows for [dbo].[production_plans].
SET IDENTITY_INSERT [dbo].[production_plans] ON;
INSERT INTO [dbo].[production_plans] ([id], [plan_no], [product_id], [warehouse_id], [plan_month], [forecast_qty], [confirmed_sales_qty], [safety_stock_qty], [planned_production_qty], [status], [planner_id], [approved_by], [approved_at], [created_at]) VALUES (1, 'PRODUCTION-1001', 1, 1, 'plan_month-1', 11, 11, 11, 11, 'active', 1, 1, '2026-02-01', '2026-02-01');
INSERT INTO [dbo].[production_plans] ([id], [plan_no], [product_id], [warehouse_id], [plan_month], [forecast_qty], [confirmed_sales_qty], [safety_stock_qty], [planned_production_qty], [status], [planner_id], [approved_by], [approved_at], [created_at]) VALUES (2, 'PRODUCTION-1002', 2, 2, 'plan_month-2', 12, 12, 12, 12, 'posted', 2, 2, '2026-02-02', '2026-02-02');
SET IDENTITY_INSERT [dbo].[production_plans] OFF;

-- Natural seed rows for [dbo].[mrp_runs].
SET IDENTITY_INSERT [dbo].[mrp_runs] ON;
INSERT INTO [dbo].[mrp_runs] ([id], [run_no], [plan_id], [run_date], [demand_source], [status], [created_by], [completed_at]) VALUES (1, 'MRP_RUNS-2001', 1, '2026-02-01', 'demand_source-1', 'active', 1, '2026-02-01');
INSERT INTO [dbo].[mrp_runs] ([id], [run_no], [plan_id], [run_date], [demand_source], [status], [created_by], [completed_at]) VALUES (2, 'MRP_RUNS-2002', 2, '2026-02-02', 'demand_source-2', 'posted', 2, '2026-02-02');
SET IDENTITY_INSERT [dbo].[mrp_runs] OFF;

-- Natural seed rows for [dbo].[mrp_run_items].
SET IDENTITY_INSERT [dbo].[mrp_run_items] ON;
INSERT INTO [dbo].[mrp_run_items] ([id], [run_id], [parent_product_id], [component_product_id], [gross_requirement], [on_hand_qty], [reserved_qty], [planned_receipt_qty], [net_requirement], [suggested_order_qty], [suggested_supplier_id], [suggested_due_date]) VALUES (1, 1, 1, 1, 1.0000, 10.0000, 10.0000, 10.0000, 1.0000, 10.0000, 1, '2026-03-31');
INSERT INTO [dbo].[mrp_run_items] ([id], [run_id], [parent_product_id], [component_product_id], [gross_requirement], [on_hand_qty], [reserved_qty], [planned_receipt_qty], [net_requirement], [suggested_order_qty], [suggested_supplier_id], [suggested_due_date]) VALUES (2, 2, 2, 2, 1.0000, 18.0000, 18.0000, 18.0000, 1.0000, 18.0000, 2, '2026-03-31');
SET IDENTITY_INSERT [dbo].[mrp_run_items] OFF;

-- Natural seed rows for [dbo].[work_order_operations].
SET IDENTITY_INSERT [dbo].[work_order_operations] ON;
INSERT INTO [dbo].[work_order_operations] ([id], [work_order_id], [operation_id], [operation_seq], [planned_start], [planned_end], [actual_start], [actual_end], [status], [assigned_employee_id], [qualified_qty], [scrapped_qty], [rework_qty]) VALUES (1, 1, 1, 1, '2026-02-01', '2026-03-31', '2026-02-01', '2026-03-31', 'active', 1, 11, 11, 11);
INSERT INTO [dbo].[work_order_operations] ([id], [work_order_id], [operation_id], [operation_seq], [planned_start], [planned_end], [actual_start], [actual_end], [status], [assigned_employee_id], [qualified_qty], [scrapped_qty], [rework_qty]) VALUES (2, 2, 2, 2, '2026-02-02', '2026-03-31', '2026-02-02', '2026-03-31', 'posted', 2, 12, 12, 12);
SET IDENTITY_INSERT [dbo].[work_order_operations] OFF;

-- Natural seed rows for [dbo].[operation_reports].
SET IDENTITY_INSERT [dbo].[operation_reports] ON;
INSERT INTO [dbo].[operation_reports] ([id], [work_order_operation_id], [report_no], [employee_id], [report_time], [input_qty], [qualified_qty], [scrapped_qty], [rework_qty], [labor_minutes], [machine_minutes]) VALUES (1, 1, 'OPERATION_-5001', 1, '2026-02-01', 11, 11, 11, 11, 1.0000, 1.0000);
INSERT INTO [dbo].[operation_reports] ([id], [work_order_operation_id], [report_no], [employee_id], [report_time], [input_qty], [qualified_qty], [scrapped_qty], [rework_qty], [labor_minutes], [machine_minutes]) VALUES (2, 2, 'OPERATION_-5002', 2, '2026-02-02', 12, 12, 12, 12, 1.0000, 1.0000);
SET IDENTITY_INSERT [dbo].[operation_reports] OFF;

-- Natural seed rows for [dbo].[material_issues].
SET IDENTITY_INSERT [dbo].[material_issues] ON;
INSERT INTO [dbo].[material_issues] ([id], [issue_no], [work_order_id], [warehouse_id], [issue_date], [issued_by], [status]) VALUES (1, 'MATERIAL_I-6001', 1, 1, '2026-02-01', 6001, 'active');
INSERT INTO [dbo].[material_issues] ([id], [issue_no], [work_order_id], [warehouse_id], [issue_date], [issued_by], [status]) VALUES (2, 'MATERIAL_I-6002', 2, 2, '2026-02-02', 6002, 'posted');
SET IDENTITY_INSERT [dbo].[material_issues] OFF;

-- Natural seed rows for [dbo].[material_issue_items].
SET IDENTITY_INSERT [dbo].[material_issue_items] ON;
INSERT INTO [dbo].[material_issue_items] ([id], [issue_id], [product_id], [batch_id], [required_qty], [issued_qty], [unit_cost]) VALUES (1, 1, 1, 1, 10.0000, 10.0000, 1000.00);
INSERT INTO [dbo].[material_issue_items] ([id], [issue_id], [product_id], [batch_id], [required_qty], [issued_qty], [unit_cost]) VALUES (2, 2, 2, 2, 18.0000, 18.0000, 1800.00);
SET IDENTITY_INSERT [dbo].[material_issue_items] OFF;

-- Natural seed rows for [dbo].[finished_goods_receipts].
SET IDENTITY_INSERT [dbo].[finished_goods_receipts] ON;
INSERT INTO [dbo].[finished_goods_receipts] ([id], [receipt_no], [work_order_id], [product_id], [batch_id], [warehouse_id], [receipt_date], [received_qty], [unit_cost], [received_by], [status]) VALUES (1, 'FINISHED_G-8001', 1, 1, 1, 1, '2026-02-01', 11, 1000.00, 8001, 'active');
INSERT INTO [dbo].[finished_goods_receipts] ([id], [receipt_no], [work_order_id], [product_id], [batch_id], [warehouse_id], [receipt_date], [received_qty], [unit_cost], [received_by], [status]) VALUES (2, 'FINISHED_G-8002', 2, 2, 2, 2, '2026-02-02', 12, 1800.00, 8002, 'posted');
SET IDENTITY_INSERT [dbo].[finished_goods_receipts] OFF;

-- Natural seed rows for [dbo].[standard_costs].
SET IDENTITY_INSERT [dbo].[standard_costs] ON;
INSERT INTO [dbo].[standard_costs] ([id], [product_id], [cost_version], [material_cost], [labor_cost], [overhead_cost], [effective_from], [effective_to], [status], [approved_by]) VALUES (1, 1, 'cost_version-1', 1000.00, 1000.00, 1000.00, '2026-02-01', '2026-02-01', 'active', 1);
INSERT INTO [dbo].[standard_costs] ([id], [product_id], [cost_version], [material_cost], [labor_cost], [overhead_cost], [effective_from], [effective_to], [status], [approved_by]) VALUES (2, 2, 'cost_version-2', 1800.00, 1800.00, 1800.00, '2026-02-02', '2026-02-02', 'posted', 2);
SET IDENTITY_INSERT [dbo].[standard_costs] OFF;

-- Natural seed rows for [dbo].[inventory_cost_layers].
SET IDENTITY_INSERT [dbo].[inventory_cost_layers] ON;
INSERT INTO [dbo].[inventory_cost_layers] ([id], [product_id], [batch_id], [warehouse_id], [source_type], [source_id], [receipt_date], [original_qty], [remaining_qty], [unit_cost], [currency]) VALUES (1, 1, 1, 1, 'source_type-1', 1, '2026-02-01', 10.0000, 10.0000, 1000.00, 'CNY');
INSERT INTO [dbo].[inventory_cost_layers] ([id], [product_id], [batch_id], [warehouse_id], [source_type], [source_id], [receipt_date], [original_qty], [remaining_qty], [unit_cost], [currency]) VALUES (2, 2, 2, 2, 'source_type-2', 2, '2026-02-02', 18.0000, 18.0000, 1800.00, 'CNY');
SET IDENTITY_INSERT [dbo].[inventory_cost_layers] OFF;

-- Natural seed rows for [dbo].[inventory_valuation_snapshots].
SET IDENTITY_INSERT [dbo].[inventory_valuation_snapshots] ON;
INSERT INTO [dbo].[inventory_valuation_snapshots] ([id], [snapshot_date], [product_id], [warehouse_id], [quantity], [unit_cost], [inventory_value], [valuation_method]) VALUES (1, '2026-02-01', 1, 1, 10.0000, 1000.00, 1.0000, 'valuation_method-1');
INSERT INTO [dbo].[inventory_valuation_snapshots] ([id], [snapshot_date], [product_id], [warehouse_id], [quantity], [unit_cost], [inventory_value], [valuation_method]) VALUES (2, '2026-02-02', 2, 2, 18.0000, 1800.00, 1.0000, 'valuation_method-2');
SET IDENTITY_INSERT [dbo].[inventory_valuation_snapshots] OFF;

-- Natural seed rows for [dbo].[work_order_costs].
SET IDENTITY_INSERT [dbo].[work_order_costs] ON;
INSERT INTO [dbo].[work_order_costs] ([id], [work_order_id], [material_cost], [labor_cost], [overhead_cost], [finished_qty], [unit_cost], [variance_amount], [calculated_at]) VALUES (1, 1, 1000.00, 1000.00, 1000.00, 11, 1000.00, 1000.00, '2026-02-01');
INSERT INTO [dbo].[work_order_costs] ([id], [work_order_id], [material_cost], [labor_cost], [overhead_cost], [finished_qty], [unit_cost], [variance_amount], [calculated_at]) VALUES (2, 2, 1800.00, 1800.00, 1800.00, 12, 1800.00, 1800.00, '2026-02-02');
SET IDENTITY_INSERT [dbo].[work_order_costs] OFF;

-- Natural seed rows for [dbo].[cogs_entries].
SET IDENTITY_INSERT [dbo].[cogs_entries] ON;
INSERT INTO [dbo].[cogs_entries] ([id], [sales_order_id], [sales_order_item_id], [product_id], [batch_id], [quantity], [unit_cost], [cogs_amount], [voucher_id], [posted_at]) VALUES (1, 1, 1, 1, 1, 10.0000, 1000.00, 1000.00, 1, '2026-02-01');
INSERT INTO [dbo].[cogs_entries] ([id], [sales_order_id], [sales_order_item_id], [product_id], [batch_id], [quantity], [unit_cost], [cogs_amount], [voucher_id], [posted_at]) VALUES (2, 2, 2, 2, 2, 18.0000, 1800.00, 1800.00, 2, '2026-02-02');
SET IDENTITY_INSERT [dbo].[cogs_entries] OFF;

-- Natural seed rows for [dbo].[account_subjects].
SET IDENTITY_INSERT [dbo].[account_subjects] ON;
INSERT INTO [dbo].[account_subjects] ([id], [parent_id], [subject_code], [subject_name], [subject_type], [balance_direction], [is_leaf], [status]) VALUES (1, 1, 'ACCOUNT_SU-14001', 'subject_name-1', 'subject_type-1', 'balance_direction-1', 1, 'active');
INSERT INTO [dbo].[account_subjects] ([id], [parent_id], [subject_code], [subject_name], [subject_type], [balance_direction], [is_leaf], [status]) VALUES (2, 2, 'ACCOUNT_SU-14002', 'subject_name-2', 'subject_type-2', 'balance_direction-2', 0, 'posted');
SET IDENTITY_INSERT [dbo].[account_subjects] OFF;

-- Natural seed rows for [dbo].[opening_balances].
SET IDENTITY_INSERT [dbo].[opening_balances] ON;
INSERT INTO [dbo].[opening_balances] ([id], [ledger_book_id], [subject_id], [period_code], [debit_amount], [credit_amount]) VALUES (1, 1, 1, 'OPENING_BA-15001', 1000.00, 1000.00);
INSERT INTO [dbo].[opening_balances] ([id], [ledger_book_id], [subject_id], [period_code], [debit_amount], [credit_amount]) VALUES (2, 2, 2, 'OPENING_BA-15002', 1800.00, 1800.00);
SET IDENTITY_INSERT [dbo].[opening_balances] OFF;

-- Natural seed rows for [dbo].[account_balances].
SET IDENTITY_INSERT [dbo].[account_balances] ON;
INSERT INTO [dbo].[account_balances] ([id], [ledger_book_id], [subject_id], [period_code], [begin_debit], [begin_credit], [current_debit], [current_credit], [ending_debit], [ending_credit]) VALUES (1, 1, 1, 'ACCOUNT_BA-16001', 1.0000, 1.0000, 1.0000, 1.0000, 1.0000, 1.0000);
INSERT INTO [dbo].[account_balances] ([id], [ledger_book_id], [subject_id], [period_code], [begin_debit], [begin_credit], [current_debit], [current_credit], [ending_debit], [ending_credit]) VALUES (2, 2, 2, 'ACCOUNT_BA-16002', 1.0000, 1.0000, 1.0000, 1.0000, 1.0000, 1.0000);
SET IDENTITY_INSERT [dbo].[account_balances] OFF;

-- Natural seed rows for [dbo].[budget_versions].
SET IDENTITY_INSERT [dbo].[budget_versions] ON;
INSERT INTO [dbo].[budget_versions] ([id], [ledger_book_id], [version_code], [version_name], [fiscal_year], [status], [approved_by]) VALUES (1, 1, 'BUDGET_VER-17001', 'version_name-1', 17001, 'active', 1);
INSERT INTO [dbo].[budget_versions] ([id], [ledger_book_id], [version_code], [version_name], [fiscal_year], [status], [approved_by]) VALUES (2, 2, 'BUDGET_VER-17002', 'version_name-2', 17002, 'posted', 2);
SET IDENTITY_INSERT [dbo].[budget_versions] OFF;

-- Natural seed rows for [dbo].[budget_items].
SET IDENTITY_INSERT [dbo].[budget_items] ON;
INSERT INTO [dbo].[budget_items] ([id], [version_id], [department_id], [subject_id], [period_code], [budget_amount], [used_amount]) VALUES (1, 1, 1, 1, 'BUDGET_ITE-18001', 1000.00, 1000.00);
INSERT INTO [dbo].[budget_items] ([id], [version_id], [department_id], [subject_id], [period_code], [budget_amount], [used_amount]) VALUES (2, 2, 2, 2, 'BUDGET_ITE-18002', 1800.00, 1800.00);
SET IDENTITY_INSERT [dbo].[budget_items] OFF;

-- Natural seed rows for [dbo].[ar_invoices].
SET IDENTITY_INSERT [dbo].[ar_invoices] ON;
INSERT INTO [dbo].[ar_invoices] ([id], [ar_no], [sales_order_id], [customer_id], [invoice_date], [due_date], [invoice_amount], [paid_amount], [writeoff_amount], [status]) VALUES (1, 'AR_INVOICE-19001', 1, 1, '2026-02-01', '2026-03-31', 1000.00, 1000.00, 1000.00, 'active');
INSERT INTO [dbo].[ar_invoices] ([id], [ar_no], [sales_order_id], [customer_id], [invoice_date], [due_date], [invoice_amount], [paid_amount], [writeoff_amount], [status]) VALUES (2, 'AR_INVOICE-19002', 2, 2, '2026-02-02', '2026-03-31', 1800.00, 1800.00, 1800.00, 'posted');
SET IDENTITY_INSERT [dbo].[ar_invoices] OFF;

-- Natural seed rows for [dbo].[ap_invoices].
SET IDENTITY_INSERT [dbo].[ap_invoices] ON;
INSERT INTO [dbo].[ap_invoices] ([id], [ap_no], [purchase_order_id], [supplier_id], [invoice_date], [due_date], [invoice_amount], [paid_amount], [status]) VALUES (1, 'AP_INVOICE-20001', 1, 1, '2026-02-01', '2026-03-31', 1000.00, 1000.00, 'active');
INSERT INTO [dbo].[ap_invoices] ([id], [ap_no], [purchase_order_id], [supplier_id], [invoice_date], [due_date], [invoice_amount], [paid_amount], [status]) VALUES (2, 'AP_INVOICE-20002', 2, 2, '2026-02-02', '2026-03-31', 1800.00, 1800.00, 'posted');
SET IDENTITY_INSERT [dbo].[ap_invoices] OFF;

-- Natural seed rows for [dbo].[payment_requests].
SET IDENTITY_INSERT [dbo].[payment_requests] ON;
INSERT INTO [dbo].[payment_requests] ([id], [request_no], [supplier_id], [requested_by], [request_date], [planned_pay_date], [total_amount], [status]) VALUES (1, 'PAYMENT_RE-21001', 1, 21001, '2026-02-01', '2026-02-01', 1000.00, 'active');
INSERT INTO [dbo].[payment_requests] ([id], [request_no], [supplier_id], [requested_by], [request_date], [planned_pay_date], [total_amount], [status]) VALUES (2, 'PAYMENT_RE-21002', 2, 21002, '2026-02-02', '2026-02-02', 1800.00, 'posted');
SET IDENTITY_INSERT [dbo].[payment_requests] OFF;

-- Natural seed rows for [dbo].[payment_request_items].
SET IDENTITY_INSERT [dbo].[payment_request_items] ON;
INSERT INTO [dbo].[payment_request_items] ([id], [request_id], [ap_invoice_id], [requested_amount]) VALUES (1, 1, 1, 1000.00);
INSERT INTO [dbo].[payment_request_items] ([id], [request_id], [ap_invoice_id], [requested_amount]) VALUES (2, 2, 2, 1800.00);
SET IDENTITY_INSERT [dbo].[payment_request_items] OFF;

-- Natural seed rows for [dbo].[warehouse_zones].
SET IDENTITY_INSERT [dbo].[warehouse_zones] ON;
INSERT INTO [dbo].[warehouse_zones] ([id], [warehouse_id], [zone_code], [zone_name], [zone_type]) VALUES (1, 1, 'WAREHOUSE_-23001', 'zone_name-1', 'zone_type-1');
INSERT INTO [dbo].[warehouse_zones] ([id], [warehouse_id], [zone_code], [zone_name], [zone_type]) VALUES (2, 2, 'WAREHOUSE_-23002', 'zone_name-2', 'zone_type-2');
SET IDENTITY_INSERT [dbo].[warehouse_zones] OFF;

-- Natural seed rows for [dbo].[warehouse_locations].
SET IDENTITY_INSERT [dbo].[warehouse_locations] ON;
INSERT INTO [dbo].[warehouse_locations] ([id], [zone_id], [location_code], [location_type], [max_weight_kg], [max_volume_m3], [status]) VALUES (1, 1, 'WAREHOUSE_-24001', 'location_type-1', 1.0000, 1.0000, 'active');
INSERT INTO [dbo].[warehouse_locations] ([id], [zone_id], [location_code], [location_type], [max_weight_kg], [max_volume_m3], [status]) VALUES (2, 2, 'WAREHOUSE_-24002', 'location_type-2', 1.0000, 1.0000, 'posted');
SET IDENTITY_INSERT [dbo].[warehouse_locations] OFF;

-- Natural seed rows for [dbo].[inventory_location_balances].
SET IDENTITY_INSERT [dbo].[inventory_location_balances] ON;
INSERT INTO [dbo].[inventory_location_balances] ([id], [location_id], [product_id], [batch_id], [quantity], [locked_quantity]) VALUES (1, 1, 1, 1, 10.0000, 10.0000);
INSERT INTO [dbo].[inventory_location_balances] ([id], [location_id], [product_id], [batch_id], [quantity], [locked_quantity]) VALUES (2, 2, 2, 2, 18.0000, 18.0000);
SET IDENTITY_INSERT [dbo].[inventory_location_balances] OFF;

-- Natural seed rows for [dbo].[putaway_tasks].
SET IDENTITY_INSERT [dbo].[putaway_tasks] ON;
INSERT INTO [dbo].[putaway_tasks] ([id], [task_no], [receipt_id], [product_id], [batch_id], [from_location_id], [to_location_id], [quantity], [assigned_to], [status]) VALUES (1, 'PUTAWAY_TA-26001', 1, 1, 1, 1, 1, 10.0000, 26001, 'active');
INSERT INTO [dbo].[putaway_tasks] ([id], [task_no], [receipt_id], [product_id], [batch_id], [from_location_id], [to_location_id], [quantity], [assigned_to], [status]) VALUES (2, 'PUTAWAY_TA-26002', 2, 2, 2, 2, 2, 18.0000, 26002, 'posted');
SET IDENTITY_INSERT [dbo].[putaway_tasks] OFF;

-- Natural seed rows for [dbo].[picking_tasks].
SET IDENTITY_INSERT [dbo].[picking_tasks] ON;
INSERT INTO [dbo].[picking_tasks] ([id], [task_no], [sales_order_id], [warehouse_id], [wave_no], [assigned_to], [status]) VALUES (1, 'PICKING_TA-27001', 1, 1, 'PICKING_TA-27001', 27001, 'active');
INSERT INTO [dbo].[picking_tasks] ([id], [task_no], [sales_order_id], [warehouse_id], [wave_no], [assigned_to], [status]) VALUES (2, 'PICKING_TA-27002', 2, 2, 'PICKING_TA-27002', 27002, 'posted');
SET IDENTITY_INSERT [dbo].[picking_tasks] OFF;

-- Natural seed rows for [dbo].[picking_task_items].
SET IDENTITY_INSERT [dbo].[picking_task_items] ON;
INSERT INTO [dbo].[picking_task_items] ([id], [picking_task_id], [sales_order_item_id], [product_id], [batch_id], [location_id], [required_qty], [picked_qty]) VALUES (1, 1, 1, 1, 1, 1, 10.0000, 10.0000);
INSERT INTO [dbo].[picking_task_items] ([id], [picking_task_id], [sales_order_item_id], [product_id], [batch_id], [location_id], [required_qty], [picked_qty]) VALUES (2, 2, 2, 2, 2, 2, 18.0000, 18.0000);
SET IDENTITY_INSERT [dbo].[picking_task_items] OFF;

-- Natural seed rows for [dbo].[repair_orders].
SET IDENTITY_INSERT [dbo].[repair_orders] ON;
INSERT INTO [dbo].[repair_orders] ([id], [repair_no], [service_ticket_id], [customer_id], [product_id], [serial_number_id], [received_date], [fault_desc], [status], [technician_id], [estimated_cost], [actual_cost]) VALUES (1, 'REPAIR_ORD-29001', 1, 1, 1, 1, '2026-02-01', 'fault_desc-1', 'active', 1, 1000.00, 1000.00);
INSERT INTO [dbo].[repair_orders] ([id], [repair_no], [service_ticket_id], [customer_id], [product_id], [serial_number_id], [received_date], [fault_desc], [status], [technician_id], [estimated_cost], [actual_cost]) VALUES (2, 'REPAIR_ORD-29002', 2, 2, 2, 2, '2026-02-02', 'fault_desc-2', 'posted', 2, 1800.00, 1800.00);
SET IDENTITY_INSERT [dbo].[repair_orders] OFF;

-- Natural seed rows for [dbo].[repair_order_parts].
SET IDENTITY_INSERT [dbo].[repair_order_parts] ON;
INSERT INTO [dbo].[repair_order_parts] ([id], [repair_order_id], [product_id], [batch_id], [quantity], [unit_cost], [issued_from_warehouse_id]) VALUES (1, 1, 1, 1, 10.0000, 1000.00, 1);
INSERT INTO [dbo].[repair_order_parts] ([id], [repair_order_id], [product_id], [batch_id], [quantity], [unit_cost], [issued_from_warehouse_id]) VALUES (2, 2, 2, 2, 18.0000, 1800.00, 2);
SET IDENTITY_INSERT [dbo].[repair_order_parts] OFF;

-- Natural seed rows for [dbo].[numbering_rules].
SET IDENTITY_INSERT [dbo].[numbering_rules] ON;
INSERT INTO [dbo].[numbering_rules] ([id], [document_type], [prefix], [date_pattern], [sequence_length], [current_sequence], [updated_at]) VALUES (1, 'document_type-1', 'prefix-1', 'date_pattern-1', 1, 1, '2026-02-01');
INSERT INTO [dbo].[numbering_rules] ([id], [document_type], [prefix], [date_pattern], [sequence_length], [current_sequence], [updated_at]) VALUES (2, 'document_type-2', 'prefix-2', 'date_pattern-2', 2, 2, '2026-02-02');
SET IDENTITY_INSERT [dbo].[numbering_rules] OFF;

-- Natural seed rows for [dbo].[master_data_change_requests].
SET IDENTITY_INSERT [dbo].[master_data_change_requests] ON;
INSERT INTO [dbo].[master_data_change_requests] ([id], [request_no], [master_type], [master_id], [change_reason], [requested_by], [requested_at], [approved_by], [approved_at], [status]) VALUES (1, 'MASTER_DAT-32001', 'master_type-1', 1, 'change_reason-1', 32001, '2026-02-01', 1, '2026-02-01', 'active');
INSERT INTO [dbo].[master_data_change_requests] ([id], [request_no], [master_type], [master_id], [change_reason], [requested_by], [requested_at], [approved_by], [approved_at], [status]) VALUES (2, 'MASTER_DAT-32002', 'master_type-2', 2, 'change_reason-2', 32002, '2026-02-02', 2, '2026-02-02', 'posted');
SET IDENTITY_INSERT [dbo].[master_data_change_requests] OFF;

-- Natural seed rows for [dbo].[master_data_change_items].
SET IDENTITY_INSERT [dbo].[master_data_change_items] ON;
INSERT INTO [dbo].[master_data_change_items] ([id], [request_id], [field_name], [old_value], [new_value]) VALUES (1, 1, 'field_name-1', 'Master Data Change Items 1', 'Master Data Change Items 1');
INSERT INTO [dbo].[master_data_change_items] ([id], [request_id], [field_name], [old_value], [new_value]) VALUES (2, 2, 'field_name-2', 'Master Data Change Items 2', 'Master Data Change Items 2');
SET IDENTITY_INSERT [dbo].[master_data_change_items] OFF;

-- Natural seed rows for [dbo].[data_permission_scopes].
SET IDENTITY_INSERT [dbo].[data_permission_scopes] ON;
INSERT INTO [dbo].[data_permission_scopes] ([id], [role_id], [scope_type], [scope_id], [can_read], [can_write]) VALUES (1, 1, 'scope_type-1', 1, 1, 1);
INSERT INTO [dbo].[data_permission_scopes] ([id], [role_id], [scope_type], [scope_id], [can_read], [can_write]) VALUES (2, 2, 'scope_type-2', 2, 0, 0);
SET IDENTITY_INSERT [dbo].[data_permission_scopes] OFF;

-- Natural seed rows for [dbo].[sensitive_access_logs].
SET IDENTITY_INSERT [dbo].[sensitive_access_logs] ON;
INSERT INTO [dbo].[sensitive_access_logs] ([id], [employee_id], [object_type], [object_id], [field_name], [access_reason], [accessed_at]) VALUES (1, 1, 'object_type-1', 1, 'field_name-1', 'access_reason-1', '2026-02-01');
INSERT INTO [dbo].[sensitive_access_logs] ([id], [employee_id], [object_type], [object_id], [field_name], [access_reason], [accessed_at]) VALUES (2, 2, 'object_type-2', 2, 'field_name-2', 'access_reason-2', '2026-02-02');
SET IDENTITY_INSERT [dbo].[sensitive_access_logs] OFF;

-- Natural seed rows for [dbo].[region_dim].
SET IDENTITY_INSERT [dbo].[region_dim] ON;
INSERT INTO [dbo].[region_dim] ([id], [region_code], [region_name], [province], [city], [district], [sales_region], [region_level], [is_active]) VALUES (1, 'REGION_DIM-36001', 'region_name-1', 'province-1', 'city-1', 'district-1', 'sales_region-1', 'region_level-1', 1);
INSERT INTO [dbo].[region_dim] ([id], [region_code], [region_name], [province], [city], [district], [sales_region], [region_level], [is_active]) VALUES (2, 'REGION_DIM-36002', 'region_name-2', 'province-2', 'city-2', 'district-2', 'sales_region-2', 'region_level-2', 0);
SET IDENTITY_INSERT [dbo].[region_dim] OFF;

-- Natural seed rows for [dbo].[fiscal_calendar].
INSERT INTO [dbo].[fiscal_calendar] ([calendar_date], [fiscal_year], [fiscal_quarter], [fiscal_month], [fiscal_month_name], [period_code], [period_start], [period_end], [is_current_fiscal_year], [accounting_period_id]) VALUES ('2026-03-31', 37001, 37001, 37001, 'fiscal_month_name-1', 'FISCAL_CAL-37001', '2026-02', '2026-03-31', 1, 1);
INSERT INTO [dbo].[fiscal_calendar] ([calendar_date], [fiscal_year], [fiscal_quarter], [fiscal_month], [fiscal_month_name], [period_code], [period_start], [period_end], [is_current_fiscal_year], [accounting_period_id]) VALUES ('2026-03-31', 37002, 37002, 37002, 'fiscal_month_name-2', 'FISCAL_CAL-37002', '2026-02', '2026-03-31', 0, 2);

-- Natural seed rows for [dbo].[category_dim].
SET IDENTITY_INSERT [dbo].[category_dim] ON;
INSERT INTO [dbo].[category_dim] ([id], [source_category_id], [category_code], [level1_name], [level2_name], [leaf_name], [is_womenwear], [effective_from], [effective_to], [status]) VALUES (1, 1, 'CATEGORY_D-38001', 'level1_name-1', 'level2_name-1', 'leaf_name-1', 1, '2026-02-01', '2026-02-01', 'active');
INSERT INTO [dbo].[category_dim] ([id], [source_category_id], [category_code], [level1_name], [level2_name], [leaf_name], [is_womenwear], [effective_from], [effective_to], [status]) VALUES (2, 2, 'CATEGORY_D-38002', 'level1_name-2', 'level2_name-2', 'leaf_name-2', 0, '2026-02-02', '2026-02-02', 'posted');
SET IDENTITY_INSERT [dbo].[category_dim] OFF;

-- Natural seed rows for [dbo].[payments].
SET IDENTITY_INSERT [dbo].[payments] ON;
INSERT INTO [dbo].[payments] ([id], [payment_no], [customer_id], [order_id], [receipt_id], [journal_id], [payment_date], [amount], [currency], [payment_method], [payment_status], [failure_reason], [created_at]) VALUES (1, 'PAYMENTS-39001', 1, 1, 1, 1, '2026-02-01', 1000.00, 'CNY', 'payment_method-1', 'payment_status-1', 'failure_reason-1', '2026-02-01');
INSERT INTO [dbo].[payments] ([id], [payment_no], [customer_id], [order_id], [receipt_id], [journal_id], [payment_date], [amount], [currency], [payment_method], [payment_status], [failure_reason], [created_at]) VALUES (2, 'PAYMENTS-39002', 2, 2, 2, 2, '2026-02-02', 1800.00, 'CNY', 'payment_method-2', 'payment_status-2', 'failure_reason-2', '2026-02-02');
SET IDENTITY_INSERT [dbo].[payments] OFF;

-- Natural seed rows for [dbo].[sales_fact].
SET IDENTITY_INSERT [dbo].[sales_fact] ON;
INSERT INTO [dbo].[sales_fact] ([id], [order_id], [order_item_id], [customer_id], [product_id], [category_dim_id], [warehouse_id], [region_dim_id], [fiscal_date], [payment_id], [quantity_sold], [sales_amount], [paid_amount], [refund_amount], [net_sales_amount], [gross_margin_amount], [order_status], [sales_channel], [created_at]) VALUES (1, 1, 1, 1, 1, 1, 1, 1, '2026-02-01', 1, 10.0000, 1000.00, 1000.00, 1000.00, 1000.00, 1000.00, 'order_status-1', 'sales_channel-1', '2026-02-01');
INSERT INTO [dbo].[sales_fact] ([id], [order_id], [order_item_id], [customer_id], [product_id], [category_dim_id], [warehouse_id], [region_dim_id], [fiscal_date], [payment_id], [quantity_sold], [sales_amount], [paid_amount], [refund_amount], [net_sales_amount], [gross_margin_amount], [order_status], [sales_channel], [created_at]) VALUES (2, 2, 2, 2, 2, 2, 2, 2, '2026-02-02', 2, 18.0000, 1800.00, 1800.00, 1800.00, 1800.00, 1800.00, 'order_status-2', 'sales_channel-2', '2026-02-02');
SET IDENTITY_INSERT [dbo].[sales_fact] OFF;

-- ============================================================
-- Natural derived business rows.
-- These INSERT SELECT statements model routine warehouse/finance/semantic
-- derivations and are intentionally not synthetic relationship SQL.
-- ============================================================

INSERT INTO [dbo].[region_dim] ([region_code], [region_name], [province], [city], [district], [sales_region], [region_level], [is_active])
SELECT
    'REG-' + w.[code] AS [region_code],
    w.[city] + N'销售区' AS [region_name],
    w.[province],
    w.[city],
    w.[district],
    w.[province] AS [sales_region],
    'warehouse-city' AS [region_level],
    1 AS [is_active]
FROM [dbo].[warehouses] AS w
WHERE w.[id] > 2;

INSERT INTO [dbo].[fiscal_calendar] ([calendar_date], [fiscal_year], [fiscal_quarter], [fiscal_month], [fiscal_month_name], [period_code], [period_start], [period_end], [is_current_fiscal_year], [accounting_period_id])
SELECT
    so.[order_date] AS [calendar_date],
    YEAR(so.[order_date]) AS [fiscal_year],
    DATEPART(QUARTER, so.[order_date]) AS [fiscal_quarter],
    MONTH(so.[order_date]) AS [fiscal_month],
    CONVERT(NVARCHAR(7), so.[order_date], 120) AS [fiscal_month_name],
    CONVERT(NVARCHAR(7), so.[order_date], 120) AS [period_code],
    DATEADD(DAY, 1 - DAY(so.[order_date]), so.[order_date]) AS [period_start],
    DATEADD(DAY, -DAY(DATEADD(MONTH, 1, so.[order_date])), DATEADD(MONTH, 1, so.[order_date])) AS [period_end],
    CASE WHEN YEAR(so.[order_date]) = 2026 THEN 1 ELSE 0 END AS [is_current_fiscal_year],
    ap.[id] AS [accounting_period_id]
FROM [dbo].[sales_orders] AS so
LEFT JOIN [dbo].[accounting_periods] AS ap
    ON ap.[period_code] = CONVERT(NVARCHAR(7), so.[order_date], 120)
WHERE so.[id] > 2;

INSERT INTO [dbo].[category_dim] ([source_category_id], [category_code], [level1_name], [level2_name], [leaf_name], [is_womenwear], [effective_from], [effective_to], [status])
SELECT
    pc.[id] AS [source_category_id],
    pc.[code] AS [category_code],
    COALESCE(parent.[name], pc.[name]) AS [level1_name],
    CASE WHEN parent.[id] IS NULL THEN NULL ELSE pc.[name] END AS [level2_name],
    pc.[name] AS [leaf_name],
    CASE WHEN pc.[name] LIKE N'%女%' THEN 1 ELSE 0 END AS [is_womenwear],
    CAST(pc.[created_at] AS DATE) AS [effective_from],
    NULL AS [effective_to],
    pc.[status]
FROM [dbo].[product_categories] AS pc
LEFT JOIN [dbo].[product_categories] AS parent
    ON parent.[id] = pc.[parent_id]
WHERE pc.[id] > 2;

INSERT INTO [dbo].[payment_receipts] ([receipt_no], [receipt_type], [party_type], [party_id], [account_id], [receipt_date], [amount], [currency], [status], [handled_by], [confirmed_at], [remark])
SELECT
    'RCPT-' + so.[order_no] AS [receipt_no],
    'customer_receipt' AS [receipt_type],
    'customer' AS [party_type],
    so.[customer_id] AS [party_id],
    1 AS [account_id],
    so.[order_date] AS [receipt_date],
    so.[paid_amount] AS [amount],
    'CNY' AS [currency],
    CASE WHEN so.[paid_amount] > 0 THEN 'confirmed' ELSE 'pending' END AS [status],
    so.[salesperson_id] AS [handled_by],
    so.[updated_at] AS [confirmed_at],
    'derived from sales order payment' AS [remark]
FROM [dbo].[sales_orders] AS so
WHERE so.[id] > 2;

INSERT INTO [dbo].[payment_receipt_allocations] ([receipt_id], [reference_type], [reference_id], [allocated_amount])
SELECT
    pr.[id] AS [receipt_id],
    'sales_order' AS [reference_type],
    so.[id] AS [reference_id],
    so.[paid_amount] AS [allocated_amount]
FROM [dbo].[payment_receipts] AS pr
INNER JOIN [dbo].[sales_orders] AS so
    ON pr.[party_id] = so.[customer_id]
   AND pr.[receipt_date] = so.[order_date]
WHERE so.[id] > 2;

INSERT INTO [dbo].[payments] ([payment_no], [customer_id], [order_id], [receipt_id], [journal_id], [payment_date], [amount], [currency], [payment_method], [payment_status], [failure_reason], [created_at])
SELECT
    'PAY-' + pr.[receipt_no] AS [payment_no],
    so.[customer_id],
    so.[id] AS [order_id],
    pr.[id] AS [receipt_id],
    cj.[id] AS [journal_id],
    pr.[receipt_date] AS [payment_date],
    pr.[amount],
    pr.[currency],
    so.[payment_method],
    CASE WHEN pr.[amount] >= so.[paid_amount] THEN 'paid' ELSE 'partial' END AS [payment_status],
    CASE WHEN pr.[amount] = 0 THEN 'no receipt amount' ELSE NULL END AS [failure_reason],
    pr.[confirmed_at] AS [created_at]
FROM [dbo].[payment_receipts] AS pr
INNER JOIN [dbo].[sales_orders] AS so
    ON so.[customer_id] = pr.[party_id]
LEFT JOIN [dbo].[cashier_journals] AS cj
    ON cj.[reference_id] = so.[id]
   AND cj.[reference_type] = 'sales_order'
WHERE so.[id] > 2;

INSERT INTO [dbo].[reconciliation_items] ([reconciliation_id], [journal_id], [transaction_date], [description], [debit_amount], [credit_amount], [is_matched], [matched_item_id], [difference_reason])
SELECT
    r.[id] AS [reconciliation_id],
    cj.[id] AS [journal_id],
    cj.[journal_date] AS [transaction_date],
    cj.[counterparty] + N' - ' + cj.[reference_type] AS [description],
    CASE WHEN cj.[journal_type] = 'receipt' THEN cj.[amount] ELSE 0 END AS [debit_amount],
    CASE WHEN cj.[journal_type] = 'payment' THEN cj.[amount] ELSE 0 END AS [credit_amount],
    CASE WHEN cj.[status] = 'confirmed' THEN 1 ELSE 0 END AS [is_matched],
    NULL AS [matched_item_id],
    CASE WHEN cj.[status] <> 'confirmed' THEN cj.[remark] ELSE NULL END AS [difference_reason]
FROM [dbo].[reconciliations] AS r
INNER JOIN [dbo].[cashier_journals] AS cj
    ON cj.[account_id] = r.[account_id]
   AND cj.[journal_date] BETWEEN r.[period_start] AND r.[period_end];

INSERT INTO [dbo].[inventory_valuation_snapshots] ([snapshot_date], [product_id], [warehouse_id], [quantity], [unit_cost], [inventory_value], [valuation_method])
SELECT
    CAST('2026-03-31' AS DATE) AS [snapshot_date],
    i.[product_id],
    i.[warehouse_id],
    SUM(i.[quantity]) AS [quantity],
    AVG(p.[purchase_price]) AS [unit_cost],
    SUM(i.[quantity] * p.[purchase_price]) AS [inventory_value],
    'weighted-average' AS [valuation_method]
FROM [dbo].[inventory] AS i
INNER JOIN [dbo].[products] AS p
    ON p.[id] = i.[product_id]
GROUP BY i.[product_id], i.[warehouse_id];

INSERT INTO [dbo].[cogs_entries] ([sales_order_id], [sales_order_item_id], [product_id], [batch_id], [quantity], [unit_cost], [cogs_amount], [voucher_id], [posted_at])
SELECT
    so.[id] AS [sales_order_id],
    soi.[id] AS [sales_order_item_id],
    soi.[product_id],
    soi.[batch_id],
    soi.[quantity],
    COALESCE(icl.[unit_cost], p.[purchase_price]) AS [unit_cost],
    soi.[quantity] * COALESCE(icl.[unit_cost], p.[purchase_price]) AS [cogs_amount],
    v.[id] AS [voucher_id],
    so.[updated_at] AS [posted_at]
FROM [dbo].[sales_order_items] AS soi
INNER JOIN [dbo].[sales_orders] AS so
    ON so.[id] = soi.[order_id]
INNER JOIN [dbo].[products] AS p
    ON p.[id] = soi.[product_id]
LEFT JOIN [dbo].[inventory_cost_layers] AS icl
    ON icl.[product_id] = soi.[product_id]
   AND icl.[batch_id] = soi.[batch_id]
LEFT JOIN [dbo].[vouchers] AS v
    ON v.[reference_id] = so.[id]
   AND v.[reference_type] = 'sales_order'
WHERE soi.[id] > 2;

INSERT INTO [dbo].[ar_invoices] ([ar_no], [sales_order_id], [customer_id], [invoice_date], [due_date], [invoice_amount], [paid_amount], [writeoff_amount], [status])
SELECT
    'AR-' + so.[order_no] AS [ar_no],
    so.[id] AS [sales_order_id],
    so.[customer_id],
    so.[order_date] AS [invoice_date],
    DATEADD(DAY, c.[credit_days], so.[order_date]) AS [due_date],
    so.[total_amount] AS [invoice_amount],
    so.[paid_amount],
    so.[discount_amount] AS [writeoff_amount],
    CASE WHEN so.[paid_amount] >= so.[total_amount] THEN 'paid' ELSE 'open' END AS [status]
FROM [dbo].[sales_orders] AS so
INNER JOIN [dbo].[customers] AS c
    ON c.[id] = so.[customer_id]
WHERE so.[id] > 2;

INSERT INTO [dbo].[ap_invoices] ([ap_no], [purchase_order_id], [supplier_id], [invoice_date], [due_date], [invoice_amount], [paid_amount], [status])
SELECT
    'AP-' + po.[order_no] AS [ap_no],
    po.[id] AS [purchase_order_id],
    po.[supplier_id],
    po.[order_date] AS [invoice_date],
    po.[expected_delivery_date] AS [due_date],
    po.[total_amount] AS [invoice_amount],
    po.[paid_amount],
    CASE WHEN po.[paid_amount] >= po.[total_amount] THEN 'paid' ELSE 'open' END AS [status]
FROM [dbo].[purchase_orders] AS po
WHERE po.[id] > 2;

UPDATE bi
SET [used_amount] = COALESCE(usage_by_subject.[used_amount], 0)
FROM [dbo].[budget_items] AS bi
INNER JOIN [dbo].[account_subjects] AS subject
    ON subject.[id] = bi.[subject_id]
LEFT JOIN (
    SELECT
        a.[code] AS [subject_code],
        CONVERT(NVARCHAR(7), v.[voucher_date], 120) AS [period_code],
        SUM(CASE WHEN vi.[direction] = 'debit' THEN vi.[amount] ELSE 0 END) AS [used_amount]
    FROM [dbo].[vouchers] AS v
    INNER JOIN [dbo].[voucher_items] AS vi ON vi.[voucher_id] = v.[id]
    INNER JOIN [dbo].[accounts] AS a ON a.[id] = vi.[account_id]
    WHERE v.[status] = 'posted'
    GROUP BY a.[code], CONVERT(NVARCHAR(7), v.[voucher_date], 120)
) AS usage_by_subject
    ON usage_by_subject.[subject_code] = subject.[subject_code]
   AND usage_by_subject.[period_code] = bi.[period_code];

INSERT INTO [dbo].[sales_fact] ([order_id], [order_item_id], [customer_id], [product_id], [category_dim_id], [warehouse_id], [region_dim_id], [fiscal_date], [payment_id], [quantity_sold], [sales_amount], [paid_amount], [refund_amount], [net_sales_amount], [gross_margin_amount], [order_status], [sales_channel], [created_at])
SELECT
    so.[id] AS [order_id],
    soi.[id] AS [order_item_id],
    so.[customer_id],
    soi.[product_id],
    cd.[id] AS [category_dim_id],
    so.[warehouse_id],
    rd.[id] AS [region_dim_id],
    so.[order_date] AS [fiscal_date],
    pmt.[id] AS [payment_id],
    soi.[quantity] AS [quantity_sold],
    soi.[amount] AS [sales_amount],
    COALESCE(pmt.[amount], so.[paid_amount]) AS [paid_amount],
    COALESCE(sr.[refund_amount], 0) AS [refund_amount],
    soi.[amount] - COALESCE(sr.[refund_amount], 0) AS [net_sales_amount],
    soi.[amount] - COALESCE(ce.[cogs_amount], 0) AS [gross_margin_amount],
    so.[status] AS [order_status],
    so.[payment_method] AS [sales_channel],
    so.[created_at]
FROM [dbo].[sales_order_items] AS soi
INNER JOIN [dbo].[sales_orders] AS so
    ON so.[id] = soi.[order_id]
INNER JOIN [dbo].[products] AS prd
    ON prd.[id] = soi.[product_id]
LEFT JOIN [dbo].[category_dim] AS cd
    ON cd.[source_category_id] = prd.[category_id]
LEFT JOIN [dbo].[warehouses] AS wh
    ON wh.[id] = so.[warehouse_id]
LEFT JOIN [dbo].[region_dim] AS rd
    ON rd.[province] = wh.[province]
   AND rd.[city] = wh.[city]
LEFT JOIN [dbo].[payments] AS pmt
    ON pmt.[order_id] = so.[id]
LEFT JOIN [dbo].[sales_returns] AS sr
    ON sr.[order_id] = so.[id]
LEFT JOIN [dbo].[cogs_entries] AS ce
    ON ce.[sales_order_item_id] = soi.[id]
WHERE soi.[id] > 2;
