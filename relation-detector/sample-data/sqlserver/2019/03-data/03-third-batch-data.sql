-- ============================================================
-- SQL Server ERP natural sample data.
-- Seed and transaction rows use ordinary INSERT VALUES so natural sample-data
-- does not inflate relationship/lineage counts with synthetic relationship SQL.
-- Baseline is T-SQL 2016-compatible for all SQL Server versions.
-- ============================================================

-- Natural seed rows for [dbo].[ar_aging_snapshots].
SET IDENTITY_INSERT [dbo].[ar_aging_snapshots] ON;
INSERT INTO [dbo].[ar_aging_snapshots] ([id], [snapshot_date], [customer_id], [order_id], [invoice_amount], [paid_amount], [outstanding_amount], [due_date], [aging_days], [aging_bucket], [bad_debt_provision], [last_collection_date], [collection_notes]) VALUES (1, '2026-02-01', 1, 1, 1000.00, 1000.00, 1000.00, '2026-03-31', 6, 'aging_bucket-1', 1.0000, '2026-02-01', 'collection_notes-1');
INSERT INTO [dbo].[ar_aging_snapshots] ([id], [snapshot_date], [customer_id], [order_id], [invoice_amount], [paid_amount], [outstanding_amount], [due_date], [aging_days], [aging_bucket], [bad_debt_provision], [last_collection_date], [collection_notes]) VALUES (2, '2026-02-02', 2, 2, 1800.00, 1800.00, 1800.00, '2026-03-31', 7, 'aging_bucket-2', 1.0000, '2026-02-02', 'collection_notes-2');
SET IDENTITY_INSERT [dbo].[ar_aging_snapshots] OFF;

-- Natural seed rows for [dbo].[ap_aging_snapshots].
SET IDENTITY_INSERT [dbo].[ap_aging_snapshots] ON;
INSERT INTO [dbo].[ap_aging_snapshots] ([id], [snapshot_date], [supplier_id], [order_id], [invoice_amount], [paid_amount], [outstanding_amount], [due_date], [aging_bucket], [planned_payment_date]) VALUES (1, '2026-02-01', 1, 1, 1000.00, 1000.00, 1000.00, '2026-03-31', 'aging_bucket-1', '2026-02-01');
INSERT INTO [dbo].[ap_aging_snapshots] ([id], [snapshot_date], [supplier_id], [order_id], [invoice_amount], [paid_amount], [outstanding_amount], [due_date], [aging_bucket], [planned_payment_date]) VALUES (2, '2026-02-02', 2, 2, 1800.00, 1800.00, 1800.00, '2026-03-31', 'aging_bucket-2', '2026-02-02');
SET IDENTITY_INSERT [dbo].[ap_aging_snapshots] OFF;

-- Natural seed rows for [dbo].[tax_invoices].
SET IDENTITY_INSERT [dbo].[tax_invoices] ON;
INSERT INTO [dbo].[tax_invoices] ([id], [invoice_no], [invoice_code], [invoice_type], [tax_direction], [party_type], [party_id], [invoice_date], [amount_excluding_tax], [tax_rate], [tax_amount], [amount_including_tax], [verification_status], [verified_at], [verified_by], [tax_period], [deduction_period], [reference_type], [reference_id], [status], [remark], [created_at], [updated_at]) VALUES (1, 'TAX_INVOIC-3001', 'TAX_INVOIC-3001', 'standard', 'tax_direction-1', 'party_type-1', 1, '2026-02-01', 1000.00, 0.0500, 1000.00, 1000.00, 'verification_status-1', '2026-02-01', 1, 'tax_period-1', 'deduction_period-1', 'standard', 1, 'active', 'remark-1', '2026-02-01', '2026-02-01');
INSERT INTO [dbo].[tax_invoices] ([id], [invoice_no], [invoice_code], [invoice_type], [tax_direction], [party_type], [party_id], [invoice_date], [amount_excluding_tax], [tax_rate], [tax_amount], [amount_including_tax], [verification_status], [verified_at], [verified_by], [tax_period], [deduction_period], [reference_type], [reference_id], [status], [remark], [created_at], [updated_at]) VALUES (2, 'TAX_INVOIC-3002', 'TAX_INVOIC-3002', 'business', 'tax_direction-2', 'party_type-2', 2, '2026-02-02', 1800.00, 0.0750, 1800.00, 1800.00, 'verification_status-2', '2026-02-02', 2, 'tax_period-2', 'deduction_period-2', 'business', 2, 'posted', 'remark-2', '2026-02-02', '2026-02-02');
SET IDENTITY_INSERT [dbo].[tax_invoices] OFF;

-- Natural seed rows for [dbo].[tax_filings].
SET IDENTITY_INSERT [dbo].[tax_filings] ON;
INSERT INTO [dbo].[tax_filings] ([id], [tax_period], [tax_type], [output_tax], [input_tax], [input_tax_transfer], [tax_payable], [tax_paid], [filing_date], [filing_deadline], [status], [prepared_by], [voucher_id], [remark], [created_at], [updated_at]) VALUES (1, 'tax_period-1', 'tax_type-1', 1.0000, 1.0000, 1.0000, 1.0000, 1.0000, '2026-02-01', '2026-02-01', 'active', 1, 1, 'remark-1', '2026-02-01', '2026-02-01');
INSERT INTO [dbo].[tax_filings] ([id], [tax_period], [tax_type], [output_tax], [input_tax], [input_tax_transfer], [tax_payable], [tax_paid], [filing_date], [filing_deadline], [status], [prepared_by], [voucher_id], [remark], [created_at], [updated_at]) VALUES (2, 'tax_period-2', 'tax_type-2', 1.0000, 1.0000, 1.0000, 1.0000, 1.0000, '2026-02-02', '2026-02-02', 'posted', 2, 2, 'remark-2', '2026-02-02', '2026-02-02');
SET IDENTITY_INSERT [dbo].[tax_filings] OFF;

-- Natural seed rows for [dbo].[inspection_standards].
SET IDENTITY_INSERT [dbo].[inspection_standards] ON;
INSERT INTO [dbo].[inspection_standards] ([id], [product_id], [standard_name], [inspection_items], [sampling_method], [sample_size], [aql_level], [status], [created_at]) VALUES (1, 1, 'Inspection Standards 1', 'inspection_items-1', 'sampling_method-1', 5001, 1.0000, 'active', '2026-02-01');
INSERT INTO [dbo].[inspection_standards] ([id], [product_id], [standard_name], [inspection_items], [sampling_method], [sample_size], [aql_level], [status], [created_at]) VALUES (2, 2, 'Inspection Standards 2', 'inspection_items-2', 'sampling_method-2', 5002, 1.0000, 'posted', '2026-02-02');
SET IDENTITY_INSERT [dbo].[inspection_standards] OFF;

-- Natural seed rows for [dbo].[inspection_reports].
SET IDENTITY_INSERT [dbo].[inspection_reports] ON;
INSERT INTO [dbo].[inspection_reports] ([id], [report_no], [inspection_type], [reference_type], [reference_id], [product_id], [batch_id], [standard_id], [sample_size], [inspected_qty], [qualified_qty], [defective_qty], [defect_rate], [inspection_result], [inspector_id], [inspection_date], [defect_description], [disposition], [status], [created_at], [updated_at]) VALUES (1, 'INSPECTION-6001', 'inspection_type-1', 'standard', 1, 1, 1, 1, 6001, 11, 11, 11, 0.0500, 'inspection_result-1', 1, '2026-02-01', 'defect_description-1', 'disposition-1', 'active', '2026-02-01', '2026-02-01');
INSERT INTO [dbo].[inspection_reports] ([id], [report_no], [inspection_type], [reference_type], [reference_id], [product_id], [batch_id], [standard_id], [sample_size], [inspected_qty], [qualified_qty], [defective_qty], [defect_rate], [inspection_result], [inspector_id], [inspection_date], [defect_description], [disposition], [status], [created_at], [updated_at]) VALUES (2, 'INSPECTION-6002', 'inspection_type-2', 'business', 2, 2, 2, 2, 6002, 12, 12, 12, 0.0750, 'inspection_result-2', 2, '2026-02-02', 'defect_description-2', 'disposition-2', 'posted', '2026-02-02', '2026-02-02');
SET IDENTITY_INSERT [dbo].[inspection_reports] OFF;

-- Natural seed rows for [dbo].[approval_workflows].
SET IDENTITY_INSERT [dbo].[approval_workflows] ON;
INSERT INTO [dbo].[approval_workflows] ([id], [workflow_name], [workflow_code], [target_type], [description], [is_active], [created_at]) VALUES (1, 'Approval Workflows 1', 'APPROVAL_W-7001', 'standard', 'Approval Workflows 1', 1, '2026-02-01');
INSERT INTO [dbo].[approval_workflows] ([id], [workflow_name], [workflow_code], [target_type], [description], [is_active], [created_at]) VALUES (2, 'Approval Workflows 2', 'APPROVAL_W-7002', 'business', 'Approval Workflows 2', 0, '2026-02-02');
SET IDENTITY_INSERT [dbo].[approval_workflows] OFF;

-- Natural seed rows for [dbo].[approval_nodes].
SET IDENTITY_INSERT [dbo].[approval_nodes] ON;
INSERT INTO [dbo].[approval_nodes] ([id], [workflow_id], [node_name], [node_level], [approver_type], [approver_id], [approval_mode], [timeout_hours], [can_delegate], [created_at]) VALUES (1, 1, 'Approval Nodes 1', 1, 'approver_type-1', 1, 'approval_mode-1', 6, 1, '2026-02-01');
INSERT INTO [dbo].[approval_nodes] ([id], [workflow_id], [node_name], [node_level], [approver_type], [approver_id], [approval_mode], [timeout_hours], [can_delegate], [created_at]) VALUES (2, 2, 'Approval Nodes 2', 2, 'approver_type-2', 2, 'approval_mode-2', 7, 0, '2026-02-02');
SET IDENTITY_INSERT [dbo].[approval_nodes] OFF;

-- Natural seed rows for [dbo].[approval_instances].
SET IDENTITY_INSERT [dbo].[approval_instances] ON;
INSERT INTO [dbo].[approval_instances] ([id], [instance_no], [workflow_id], [target_type], [target_id], [target_summary], [current_node_level], [total_nodes], [submitted_by], [submitted_at], [status], [completed_at], [remark]) VALUES (1, 'APPROVAL_I-9001', 1, 'standard', 1, 'target_summary-1', 1, 9001, 9001, '2026-02-01', 'active', '2026-02-01', 'remark-1');
INSERT INTO [dbo].[approval_instances] ([id], [instance_no], [workflow_id], [target_type], [target_id], [target_summary], [current_node_level], [total_nodes], [submitted_by], [submitted_at], [status], [completed_at], [remark]) VALUES (2, 'APPROVAL_I-9002', 2, 'business', 2, 'target_summary-2', 2, 9002, 9002, '2026-02-02', 'posted', '2026-02-02', 'remark-2');
SET IDENTITY_INSERT [dbo].[approval_instances] OFF;

-- Natural seed rows for [dbo].[approval_records].
SET IDENTITY_INSERT [dbo].[approval_records] ON;
INSERT INTO [dbo].[approval_records] ([id], [instance_id], [node_id], [approver_id], [action], [comment], [action_at], [delegated_to]) VALUES (1, 1, 1, 1, 'Approval Records 1', 'comment-1', '2026-02-01', 10001);
INSERT INTO [dbo].[approval_records] ([id], [instance_id], [node_id], [approver_id], [action], [comment], [action_at], [delegated_to]) VALUES (2, 2, 2, 2, 'Approval Records 2', 'comment-2', '2026-02-02', 10002);
SET IDENTITY_INSERT [dbo].[approval_records] OFF;

-- Natural seed rows for [dbo].[cash_flow_forecasts].
SET IDENTITY_INSERT [dbo].[cash_flow_forecasts] ON;
INSERT INTO [dbo].[cash_flow_forecasts] ([id], [forecast_date], [forecast_type], [beginning_balance], [expected_collections], [expected_payments], [expected_salary], [expected_tax], [other_income], [other_expense], [net_cash_flow], [ending_balance], [created_at]) VALUES (1, '2026-02-01', 'forecast_type-1', 1000.00, 1.0000, 1.0000, 1.0000, 1.0000, 1.0000, 1.0000, 1.0000, 1000.00, '2026-02-01');
INSERT INTO [dbo].[cash_flow_forecasts] ([id], [forecast_date], [forecast_type], [beginning_balance], [expected_collections], [expected_payments], [expected_salary], [expected_tax], [other_income], [other_expense], [net_cash_flow], [ending_balance], [created_at]) VALUES (2, '2026-02-02', 'forecast_type-2', 1800.00, 1.0000, 1.0000, 1.0000, 1.0000, 1.0000, 1.0000, 1.0000, 1800.00, '2026-02-02');
SET IDENTITY_INSERT [dbo].[cash_flow_forecasts] OFF;

-- Natural seed rows for [dbo].[projects].
SET IDENTITY_INSERT [dbo].[projects] ON;
INSERT INTO [dbo].[projects] ([id], [project_no], [name], [project_type], [department_id], [manager_id], [budget], [start_date], [planned_end_date], [actual_end_date], [status], [priority], [description], [created_at], [updated_at]) VALUES (1, 'PROJECTS-12001', 'Projects 1', 'standard', 1, 1, 1000.00, '2026-02-01', '2026-03-31', '2026-03-31', 'active', 'priority-1', 'Projects 1', '2026-02-01', '2026-02-01');
INSERT INTO [dbo].[projects] ([id], [project_no], [name], [project_type], [department_id], [manager_id], [budget], [start_date], [planned_end_date], [actual_end_date], [status], [priority], [description], [created_at], [updated_at]) VALUES (2, 'PROJECTS-12002', 'Projects 2', 'business', 2, 2, 1800.00, '2026-02-02', '2026-03-31', '2026-03-31', 'posted', 'priority-2', 'Projects 2', '2026-02-02', '2026-02-02');
SET IDENTITY_INSERT [dbo].[projects] OFF;

-- Natural seed rows for [dbo].[project_costs].
SET IDENTITY_INSERT [dbo].[project_costs] ON;
INSERT INTO [dbo].[project_costs] ([id], [project_id], [cost_type], [cost_date], [amount], [description], [reference_type], [reference_id], [recorded_by], [created_at]) VALUES (1, 1, 'cost_type-1', '2026-02-01', 1000.00, 'Project Costs 1', 'standard', 1, 13001, '2026-02-01');
INSERT INTO [dbo].[project_costs] ([id], [project_id], [cost_type], [cost_date], [amount], [description], [reference_type], [reference_id], [recorded_by], [created_at]) VALUES (2, 2, 'cost_type-2', '2026-02-02', 1800.00, 'Project Costs 2', 'business', 2, 13002, '2026-02-02');
SET IDENTITY_INSERT [dbo].[project_costs] OFF;

-- Natural seed rows for [dbo].[exchange_rates].
SET IDENTITY_INSERT [dbo].[exchange_rates] ON;
INSERT INTO [dbo].[exchange_rates] ([id], [from_currency], [to_currency], [rate_date], [rate], [rate_source], [created_at]) VALUES (1, 'CNY', 'USD', '2026-02-01', 0.0500, 'rate_source-1', '2026-02-01');
INSERT INTO [dbo].[exchange_rates] ([id], [from_currency], [to_currency], [rate_date], [rate], [rate_source], [created_at]) VALUES (2, 'CNY', 'USD', '2026-02-02', 0.0750, 'rate_source-2', '2026-02-02');
SET IDENTITY_INSERT [dbo].[exchange_rates] OFF;

-- Natural seed rows for [dbo].[foreign_currency_accounts].
SET IDENTITY_INSERT [dbo].[foreign_currency_accounts] ON;
INSERT INTO [dbo].[foreign_currency_accounts] ([id], [account_id], [currency], [original_balance], [cny_equivalent], [last_revaluation_date], [created_at]) VALUES (1, 1, 'CNY', 1000.00, 1.0000, '2026-02-01', '2026-02-01');
INSERT INTO [dbo].[foreign_currency_accounts] ([id], [account_id], [currency], [original_balance], [cny_equivalent], [last_revaluation_date], [created_at]) VALUES (2, 2, 'CNY', 1800.00, 1.0000, '2026-02-02', '2026-02-02');
SET IDENTITY_INSERT [dbo].[foreign_currency_accounts] OFF;

-- Natural seed rows for [dbo].[performance_reviews].
SET IDENTITY_INSERT [dbo].[performance_reviews] ON;
INSERT INTO [dbo].[performance_reviews] ([id], [review_no], [employee_id], [reviewer_id], [review_period], [review_type], [performance_score], [competency_score], [attitude_score], [attendance_score], [total_score], [grade], [self_assessment], [reviewer_comment], [improvement_plan], [salary_adjustment], [promotion_recommendation], [status], [created_at], [updated_at]) VALUES (1, 'PERFORMANC-16001', 1, 1, 'review_period-1', 'review_type-1', 1.0000, 1.0000, 1.0000, 1.0000, 1.0000, 'grade-1', 'self_assessment-1', 'reviewer_comment-1', 'improvement_plan-1', 1.0000, 1, 'active', '2026-02-01', '2026-02-01');
INSERT INTO [dbo].[performance_reviews] ([id], [review_no], [employee_id], [reviewer_id], [review_period], [review_type], [performance_score], [competency_score], [attitude_score], [attendance_score], [total_score], [grade], [self_assessment], [reviewer_comment], [improvement_plan], [salary_adjustment], [promotion_recommendation], [status], [created_at], [updated_at]) VALUES (2, 'PERFORMANC-16002', 2, 2, 'review_period-2', 'review_type-2', 1.0000, 1.0000, 1.0000, 1.0000, 1.0000, 'grade-2', 'self_assessment-2', 'reviewer_comment-2', 'improvement_plan-2', 1.0000, 0, 'posted', '2026-02-02', '2026-02-02');
SET IDENTITY_INSERT [dbo].[performance_reviews] OFF;

-- Natural seed rows for [dbo].[kpi_indicators].
SET IDENTITY_INSERT [dbo].[kpi_indicators] ON;
INSERT INTO [dbo].[kpi_indicators] ([id], [name], [indicator_type], [unit], [target_direction], [target_value], [target_min], [target_max], [weight], [applicable_role_id], [department_id], [status], [created_at]) VALUES (1, 'Kpi Indicators 1', 'Kpi Indicators 1', 'Kpi Indicators 1', 'target_direction-1', 1.0000, 1.0000, 1.0000, 1.0000, 1, 1, 'active', '2026-02-01');
INSERT INTO [dbo].[kpi_indicators] ([id], [name], [indicator_type], [unit], [target_direction], [target_value], [target_min], [target_max], [weight], [applicable_role_id], [department_id], [status], [created_at]) VALUES (2, 'Kpi Indicators 2', 'Kpi Indicators 2', 'Kpi Indicators 2', 'target_direction-2', 1.0000, 1.0000, 1.0000, 1.0000, 2, 2, 'posted', '2026-02-02');
SET IDENTITY_INSERT [dbo].[kpi_indicators] OFF;

-- Natural seed rows for [dbo].[serial_numbers].
SET IDENTITY_INSERT [dbo].[serial_numbers] ON;
INSERT INTO [dbo].[serial_numbers] ([id], [product_id], [batch_id], [serial_no], [status], [warehouse_id], [purchase_receipt_id], [sales_order_id], [return_id], [current_owner_id], [warranty_start], [warranty_end], [last_scan_date], [last_scan_location], [created_at], [updated_at]) VALUES (1, 1, 1, 'SERIAL_NUM-18001', 'active', 1, 1, 1, 1, 1, '2026-02-01', '2026-03-31', '2026-02-01', 'last_scan_location-1', '2026-02-01', '2026-02-01');
INSERT INTO [dbo].[serial_numbers] ([id], [product_id], [batch_id], [serial_no], [status], [warehouse_id], [purchase_receipt_id], [sales_order_id], [return_id], [current_owner_id], [warranty_start], [warranty_end], [last_scan_date], [last_scan_location], [created_at], [updated_at]) VALUES (2, 2, 2, 'SERIAL_NUM-18002', 'posted', 2, 2, 2, 2, 2, '2026-02-02', '2026-03-31', '2026-02-02', 'last_scan_location-2', '2026-02-02', '2026-02-02');
SET IDENTITY_INSERT [dbo].[serial_numbers] OFF;

-- Natural seed rows for [dbo].[serial_number_logs].
SET IDENTITY_INSERT [dbo].[serial_number_logs] ON;
INSERT INTO [dbo].[serial_number_logs] ([id], [serial_number_id], [event_type], [from_status], [to_status], [from_location], [to_location], [reference_type], [reference_id], [operator_id], [event_time], [remark]) VALUES (1, 1, 'event_type-1', 'from_status-1', 'to_status-1', 'from_location-1', 'to_location-1', 'standard', 1, 1, '2026-02-01', 'remark-1');
INSERT INTO [dbo].[serial_number_logs] ([id], [serial_number_id], [event_type], [from_status], [to_status], [from_location], [to_location], [reference_type], [reference_id], [operator_id], [event_time], [remark]) VALUES (2, 2, 'event_type-2', 'from_status-2', 'to_status-2', 'from_location-2', 'to_location-2', 'business', 2, 2, '2026-02-02', 'remark-2');
SET IDENTITY_INSERT [dbo].[serial_number_logs] OFF;

-- Natural seed rows for [dbo].[consignment_inventory].
SET IDENTITY_INSERT [dbo].[consignment_inventory] ON;
INSERT INTO [dbo].[consignment_inventory] ([id], [product_id], [batch_id], [customer_id], [consigned_qty], [consumed_qty], [available_qty], [unit_price], [consigned_date], [last_consumed_date], [settlement_period], [status], [created_at], [updated_at]) VALUES (1, 1, 1, 1, 11, 11, 11, 1000.00, '2026-02-01', '2026-02-01', 'settlement_period-1', 'active', '2026-02-01', '2026-02-01');
INSERT INTO [dbo].[consignment_inventory] ([id], [product_id], [batch_id], [customer_id], [consigned_qty], [consumed_qty], [available_qty], [unit_price], [consigned_date], [last_consumed_date], [settlement_period], [status], [created_at], [updated_at]) VALUES (2, 2, 2, 2, 12, 12, 12, 1800.00, '2026-02-02', '2026-02-02', 'settlement_period-2', 'posted', '2026-02-02', '2026-02-02');
SET IDENTITY_INSERT [dbo].[consignment_inventory] OFF;
