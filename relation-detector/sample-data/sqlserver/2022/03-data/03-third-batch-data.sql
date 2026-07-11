-- ============================================================
-- SQL Server ERP natural sample data.
-- Deterministic business rows shared by SQL Server 2016-2025.
-- ============================================================

SET IDENTITY_INSERT [dbo].[ar_aging_snapshots] ON;
INSERT INTO [dbo].[ar_aging_snapshots] ([id], [snapshot_date], [customer_id], [order_id], [invoice_amount], [paid_amount], [outstanding_amount], [due_date], [aging_days], [aging_bucket], [bad_debt_provision], [last_collection_date], [collection_notes]) VALUES (1, '2026-02-28', 1, 1, 1000.00, 400.00, 600.00, '2026-03-03', 0, 'current', 0.00, '2026-02-20', N'客户已确认三月初支付余款');
INSERT INTO [dbo].[ar_aging_snapshots] ([id], [snapshot_date], [customer_id], [order_id], [invoice_amount], [paid_amount], [outstanding_amount], [due_date], [aging_days], [aging_bucket], [bad_debt_provision], [last_collection_date], [collection_notes]) VALUES (2, '2026-02-28', 2, 2, 1800.00, 1800.00, 0.00, '2026-02-28', 0, 'paid', 0.00, '2026-02-25', N'已结清');
SET IDENTITY_INSERT [dbo].[ar_aging_snapshots] OFF;

SET IDENTITY_INSERT [dbo].[ap_aging_snapshots] ON;
INSERT INTO [dbo].[ap_aging_snapshots] ([id], [snapshot_date], [supplier_id], [order_id], [invoice_amount], [paid_amount], [outstanding_amount], [due_date], [aging_bucket], [planned_payment_date]) VALUES (1, '2026-02-28', 1, 1, 1000.00, 250.00, 750.00, '2026-03-15', 'current', '2026-03-10');
INSERT INTO [dbo].[ap_aging_snapshots] ([id], [snapshot_date], [supplier_id], [order_id], [invoice_amount], [paid_amount], [outstanding_amount], [due_date], [aging_bucket], [planned_payment_date]) VALUES (2, '2026-02-28', 2, 2, 1800.00, 900.00, 900.00, '2026-03-20', 'current', '2026-03-18');
SET IDENTITY_INSERT [dbo].[ap_aging_snapshots] OFF;

SET IDENTITY_INSERT [dbo].[tax_invoices] ON;
INSERT INTO [dbo].[tax_invoices] ([id], [invoice_no], [invoice_code], [invoice_type], [tax_direction], [party_type], [party_id], [invoice_date], [amount_excluding_tax], [tax_rate], [tax_amount], [amount_including_tax], [verification_status], [verified_at], [verified_by], [tax_period], [deduction_period], [reference_type], [reference_id], [status], [remark], [created_at], [updated_at]) VALUES (1, 'INV-202602-001', '044001', 'vat_special', 'output', 'customer', 1, '2026-02-10', 1000.00, 0.1300, 130.00, 1130.00, 'verified', '2026-02-11', 1, '2026-02', NULL, 'sales_order', 1, 'issued', N'销售增值税专用发票', '2026-02-10', '2026-02-11');
INSERT INTO [dbo].[tax_invoices] ([id], [invoice_no], [invoice_code], [invoice_type], [tax_direction], [party_type], [party_id], [invoice_date], [amount_excluding_tax], [tax_rate], [tax_amount], [amount_including_tax], [verification_status], [verified_at], [verified_by], [tax_period], [deduction_period], [reference_type], [reference_id], [status], [remark], [created_at], [updated_at]) VALUES (2, 'INV-202602-002', '044002', 'vat_general', 'input', 'supplier', 2, '2026-02-12', 1800.00, 0.0600, 108.00, 1908.00, 'verified', '2026-02-13', 2, '2026-02', '2026-02', 'purchase_order', 2, 'deductible', N'采购增值税普通发票', '2026-02-12', '2026-02-13');
SET IDENTITY_INSERT [dbo].[tax_invoices] OFF;

SET IDENTITY_INSERT [dbo].[tax_filings] ON;
INSERT INTO [dbo].[tax_filings] ([id], [tax_period], [tax_type], [output_tax], [input_tax], [input_tax_transfer], [tax_payable], [tax_paid], [filing_date], [filing_deadline], [status], [prepared_by], [voucher_id], [remark], [created_at], [updated_at]) VALUES (1, '2026-02', 'vat', 238.00, 130.00, 10.00, 118.00, 118.00, '2026-03-05', '2026-03-15', 'filed', 1, 1, N'二月增值税申报', '2026-03-05', '2026-03-05');
INSERT INTO [dbo].[tax_filings] ([id], [tax_period], [tax_type], [output_tax], [input_tax], [input_tax_transfer], [tax_payable], [tax_paid], [filing_date], [filing_deadline], [status], [prepared_by], [voucher_id], [remark], [created_at], [updated_at]) VALUES (2, '2026-02', 'surcharge', 108.00, 50.00, 0.00, 58.00, 58.00, '2026-03-05', '2026-03-15', 'filed', 2, 2, N'二月附加税申报', '2026-03-05', '2026-03-05');
SET IDENTITY_INSERT [dbo].[tax_filings] OFF;

SET IDENTITY_INSERT [dbo].[inspection_standards] ON;
INSERT INTO [dbo].[inspection_standards] ([id], [product_id], [standard_name], [inspection_items], [sampling_method], [sample_size], [aql_level], [status], [created_at]) VALUES (1, 1, N'电子产品入库检验标准', N'外观、功能、包装', 'GB2828', 20, 1.00, 'active', '2026-02-01');
INSERT INTO [dbo].[inspection_standards] ([id], [product_id], [standard_name], [inspection_items], [sampling_method], [sample_size], [aql_level], [status], [created_at]) VALUES (2, 2, N'食品批次入库检验标准', N'包装、保质期、抽样', 'GB2828', 30, 2.50, 'active', '2026-02-01');
SET IDENTITY_INSERT [dbo].[inspection_standards] OFF;

SET IDENTITY_INSERT [dbo].[inspection_reports] ON;
INSERT INTO [dbo].[inspection_reports] ([id], [report_no], [inspection_type], [reference_type], [reference_id], [product_id], [batch_id], [standard_id], [sample_size], [inspected_qty], [qualified_qty], [defective_qty], [defect_rate], [inspection_result], [inspector_id], [inspection_date], [defect_description], [disposition], [status], [created_at], [updated_at]) VALUES (1, 'QC-202602-001', 'IQC', 'purchase_receipt', 1, 1, 1, 1, 20, 20, 19, 1, 5.00, 'qualified', 1, '2026-02-15', N'一件外包装轻微破损', N'接受并记录', 'completed', '2026-02-15', '2026-02-15');
INSERT INTO [dbo].[inspection_reports] ([id], [report_no], [inspection_type], [reference_type], [reference_id], [product_id], [batch_id], [standard_id], [sample_size], [inspected_qty], [qualified_qty], [defective_qty], [defect_rate], [inspection_result], [inspector_id], [inspection_date], [defect_description], [disposition], [status], [created_at], [updated_at]) VALUES (2, 'QC-202602-002', 'IQC', 'purchase_receipt', 2, 2, 2, 2, 30, 30, 24, 6, 20.00, 'rejected', 2, '2026-02-16', N'六件封口不合格', N'退回供应商', 'completed', '2026-02-16', '2026-02-16');
SET IDENTITY_INSERT [dbo].[inspection_reports] OFF;

SET IDENTITY_INSERT [dbo].[approval_workflows] ON;
INSERT INTO [dbo].[approval_workflows] ([id], [workflow_name], [workflow_code], [target_type], [description], [is_active], [created_at]) VALUES (1, N'采购审批流程', 'PURCHASE_APPROVAL', 'purchase_requisition', N'部门经理与采购经理两级审批', 1, '2026-02-01');
INSERT INTO [dbo].[approval_workflows] ([id], [workflow_name], [workflow_code], [target_type], [description], [is_active], [created_at]) VALUES (2, N'合同审批流程', 'CONTRACT_APPROVAL', 'contract', N'合同负责人单级审批', 1, '2026-02-01');
SET IDENTITY_INSERT [dbo].[approval_workflows] OFF;

SET IDENTITY_INSERT [dbo].[approval_nodes] ON;
INSERT INTO [dbo].[approval_nodes] ([id], [workflow_id], [node_name], [node_level], [approver_type], [approver_id], [approval_mode], [timeout_hours], [can_delegate], [created_at]) VALUES (1, 1, N'部门经理审批', 1, 'employee', 1, 'single', 24, 1, '2026-02-01');
INSERT INTO [dbo].[approval_nodes] ([id], [workflow_id], [node_name], [node_level], [approver_type], [approver_id], [approval_mode], [timeout_hours], [can_delegate], [created_at]) VALUES (2, 1, N'采购经理审批', 2, 'employee', 2, 'single', 24, 1, '2026-02-01');
INSERT INTO [dbo].[approval_nodes] ([id], [workflow_id], [node_name], [node_level], [approver_type], [approver_id], [approval_mode], [timeout_hours], [can_delegate], [created_at]) VALUES (3, 2, N'合同负责人审批', 1, 'employee', 2, 'single', 48, 0, '2026-02-01');
SET IDENTITY_INSERT [dbo].[approval_nodes] OFF;

SET IDENTITY_INSERT [dbo].[approval_instances] ON;
INSERT INTO [dbo].[approval_instances] ([id], [instance_no], [workflow_id], [target_type], [target_id], [target_summary], [current_node_level], [total_nodes], [submitted_by], [submitted_at], [status], [completed_at], [remark]) VALUES (1, 'AI-202602-001', 1, 'purchase_requisition', 1, N'仓库补货请购', 2, 2, 1, '2026-02-18', 'approved', '2026-02-19', N'两级审批已完成');
INSERT INTO [dbo].[approval_instances] ([id], [instance_no], [workflow_id], [target_type], [target_id], [target_summary], [current_node_level], [total_nodes], [submitted_by], [submitted_at], [status], [completed_at], [remark]) VALUES (2, 'AI-202602-002', 2, 'contract', 2, N'年度采购合同', 1, 1, 2, '2026-02-20', 'approved', '2026-02-20', N'合同负责人已批准');
SET IDENTITY_INSERT [dbo].[approval_instances] OFF;

SET IDENTITY_INSERT [dbo].[approval_records] ON;
INSERT INTO [dbo].[approval_records] ([id], [instance_id], [node_id], [approver_id], [action], [comment], [action_at], [delegated_to]) VALUES (1, 1, 1, 1, 'approve', N'同意补货', '2026-02-18', 2);
INSERT INTO [dbo].[approval_records] ([id], [instance_id], [node_id], [approver_id], [action], [comment], [action_at], [delegated_to]) VALUES (2, 2, 3, 2, 'approve', N'同意签署', '2026-02-20', NULL);
SET IDENTITY_INSERT [dbo].[approval_records] OFF;

SET IDENTITY_INSERT [dbo].[cash_flow_forecasts] ON;
INSERT INTO [dbo].[cash_flow_forecasts] ([id], [forecast_date], [forecast_type], [beginning_balance], [expected_collections], [expected_payments], [expected_salary], [expected_tax], [other_income], [other_expense], [net_cash_flow], [ending_balance], [created_at]) VALUES (1, '2026-03-01', 'weekly', 100000.00, 30000.00, 12000.00, 8000.00, 3000.00, 1000.00, 2000.00, 6000.00, 106000.00, '2026-02-28');
INSERT INTO [dbo].[cash_flow_forecasts] ([id], [forecast_date], [forecast_type], [beginning_balance], [expected_collections], [expected_payments], [expected_salary], [expected_tax], [other_income], [other_expense], [net_cash_flow], [ending_balance], [created_at]) VALUES (2, '2026-03-08', 'weekly', 106000.00, 25000.00, 15000.00, 8000.00, 2500.00, 500.00, 1000.00, -1000.00, 105000.00, '2026-02-28');
SET IDENTITY_INSERT [dbo].[cash_flow_forecasts] OFF;

SET IDENTITY_INSERT [dbo].[projects] ON;
INSERT INTO [dbo].[projects] ([id], [project_no], [name], [project_type], [department_id], [manager_id], [budget], [start_date], [planned_end_date], [actual_end_date], [status], [priority], [description], [created_at], [updated_at]) VALUES (1, 'PRJ-2026-001', N'仓储数字化升级', 'internal', 1, 1, 500000.00, '2026-01-15', '2026-09-30', NULL, 'active', 'high', N'升级库位与批次追踪能力', '2026-01-10', '2026-02-28');
INSERT INTO [dbo].[projects] ([id], [project_no], [name], [project_type], [department_id], [manager_id], [budget], [start_date], [planned_end_date], [actual_end_date], [status], [priority], [description], [created_at], [updated_at]) VALUES (2, 'PRJ-2026-002', N'供应商协同优化', 'internal', 2, 2, 280000.00, '2026-02-01', '2026-08-31', NULL, 'active', 'medium', N'改善供应商交付与质量协同', '2026-01-20', '2026-02-28');
SET IDENTITY_INSERT [dbo].[projects] OFF;

SET IDENTITY_INSERT [dbo].[project_costs] ON;
INSERT INTO [dbo].[project_costs] ([id], [project_id], [cost_type], [cost_date], [amount], [description], [reference_type], [reference_id], [recorded_by], [created_at]) VALUES (1, 1, 'software', '2026-02-10', 45000.00, N'仓储软件一期款', 'contract', 1, 1, '2026-02-10');
INSERT INTO [dbo].[project_costs] ([id], [project_id], [cost_type], [cost_date], [amount], [description], [reference_type], [reference_id], [recorded_by], [created_at]) VALUES (2, 2, 'consulting', '2026-02-15', 18000.00, N'供应商协同流程咨询', 'contract', 2, 2, '2026-02-15');
SET IDENTITY_INSERT [dbo].[project_costs] OFF;

SET IDENTITY_INSERT [dbo].[exchange_rates] ON;
INSERT INTO [dbo].[exchange_rates] ([id], [from_currency], [to_currency], [rate_date], [rate], [rate_source], [created_at]) VALUES (1, 'USD', 'CNY', '2026-02-28', 7.200000, 'PBOC', '2026-02-28');
INSERT INTO [dbo].[exchange_rates] ([id], [from_currency], [to_currency], [rate_date], [rate], [rate_source], [created_at]) VALUES (2, 'EUR', 'CNY', '2026-02-28', 7.850000, 'PBOC', '2026-02-28');
SET IDENTITY_INSERT [dbo].[exchange_rates] OFF;

SET IDENTITY_INSERT [dbo].[foreign_currency_accounts] ON;
INSERT INTO [dbo].[foreign_currency_accounts] ([id], [account_id], [currency], [original_balance], [cny_equivalent], [last_revaluation_date], [created_at]) VALUES (1, 1, 'USD', 10000.00, 72000.00, '2026-02-28', '2026-02-28');
INSERT INTO [dbo].[foreign_currency_accounts] ([id], [account_id], [currency], [original_balance], [cny_equivalent], [last_revaluation_date], [created_at]) VALUES (2, 2, 'EUR', 8000.00, 62800.00, '2026-02-28', '2026-02-28');
SET IDENTITY_INSERT [dbo].[foreign_currency_accounts] OFF;

SET IDENTITY_INSERT [dbo].[performance_reviews] ON;
INSERT INTO [dbo].[performance_reviews] ([id], [review_no], [employee_id], [reviewer_id], [review_period], [review_type], [performance_score], [competency_score], [attitude_score], [attendance_score], [total_score], [grade], [self_assessment], [reviewer_comment], [improvement_plan], [salary_adjustment], [promotion_recommendation], [status], [created_at], [updated_at]) VALUES (1, 'PR-2025-001', 1, 2, '2025-H2', 'semiannual', 90.00, 88.00, 92.00, 95.00, 91.25, 'A', N'按期完成仓储改造', N'交付稳定', N'加强跨部门协作', 1200.00, 1, 'confirmed', '2026-01-10', '2026-01-15');
INSERT INTO [dbo].[performance_reviews] ([id], [review_no], [employee_id], [reviewer_id], [review_period], [review_type], [performance_score], [competency_score], [attitude_score], [attendance_score], [total_score], [grade], [self_assessment], [reviewer_comment], [improvement_plan], [salary_adjustment], [promotion_recommendation], [status], [created_at], [updated_at]) VALUES (2, 'PR-2025-002', 2, 1, '2025-H2', 'semiannual', 78.00, 82.00, 80.00, 85.00, 81.25, 'B', N'完成供应商梳理', N'质量跟进需加强', N'提升异常闭环效率', 500.00, 0, 'confirmed', '2026-01-10', '2026-01-15');
SET IDENTITY_INSERT [dbo].[performance_reviews] OFF;

SET IDENTITY_INSERT [dbo].[kpi_indicators] ON;
INSERT INTO [dbo].[kpi_indicators] ([id], [name], [indicator_type], [unit], [target_direction], [target_value], [target_min], [target_max], [weight], [applicable_role_id], [department_id], [status], [created_at]) VALUES (1, N'库存盘点准确率', 'operational', '%', 'higher_better', 99.00, 95.00, 100.00, 0.3000, 1, 1, 'active', '2026-02-01');
INSERT INTO [dbo].[kpi_indicators] ([id], [name], [indicator_type], [unit], [target_direction], [target_value], [target_min], [target_max], [weight], [applicable_role_id], [department_id], [status], [created_at]) VALUES (2, N'供应商准时交付率', 'supplier', '%', 'higher_better', 95.00, 90.00, 100.00, 0.2000, 2, 2, 'active', '2026-02-01');
SET IDENTITY_INSERT [dbo].[kpi_indicators] OFF;

SET IDENTITY_INSERT [dbo].[serial_numbers] ON;
INSERT INTO [dbo].[serial_numbers] ([id], [product_id], [batch_id], [serial_no], [status], [warehouse_id], [purchase_receipt_id], [sales_order_id], [return_id], [current_owner_id], [warranty_start], [warranty_end], [last_scan_date], [last_scan_location], [created_at], [updated_at]) VALUES (1, 1, 1, 'SN-202602-0001', 'in_stock', 1, 1, NULL, NULL, NULL, '2026-02-15', '2027-02-14', '2026-02-15', N'一号仓收货区', '2026-02-15', '2026-02-15');
INSERT INTO [dbo].[serial_numbers] ([id], [product_id], [batch_id], [serial_no], [status], [warehouse_id], [purchase_receipt_id], [sales_order_id], [return_id], [current_owner_id], [warranty_start], [warranty_end], [last_scan_date], [last_scan_location], [created_at], [updated_at]) VALUES (2, 2, 2, 'SN-202602-0002', 'sold', 2, 2, 2, NULL, 2, '2026-02-16', '2027-02-15', '2026-02-20', N'销售出库口', '2026-02-16', '2026-02-20');
SET IDENTITY_INSERT [dbo].[serial_numbers] OFF;

SET IDENTITY_INSERT [dbo].[serial_number_logs] ON;
INSERT INTO [dbo].[serial_number_logs] ([id], [serial_number_id], [event_type], [from_status], [to_status], [from_location], [to_location], [reference_type], [reference_id], [operator_id], [event_time], [remark]) VALUES (1, 1, 'receipt', NULL, 'in_stock', NULL, N'一号仓收货区', 'purchase_receipt', 1, 1, '2026-02-15', N'入库扫描');
INSERT INTO [dbo].[serial_number_logs] ([id], [serial_number_id], [event_type], [from_status], [to_status], [from_location], [to_location], [reference_type], [reference_id], [operator_id], [event_time], [remark]) VALUES (2, 2, 'shipment', 'in_stock', 'sold', N'二号仓拣货区', N'销售出库口', 'sales_order', 2, 2, '2026-02-20', N'销售出库扫描');
SET IDENTITY_INSERT [dbo].[serial_number_logs] OFF;

SET IDENTITY_INSERT [dbo].[consignment_inventory] ON;
INSERT INTO [dbo].[consignment_inventory] ([id], [product_id], [batch_id], [customer_id], [consigned_qty], [consumed_qty], [available_qty], [unit_price], [consigned_date], [last_consumed_date], [settlement_period], [status], [created_at], [updated_at]) VALUES (1, 1, 1, 1, 100, 30, 70, 120.00, '2026-02-01', '2026-02-25', 'monthly', 'active', '2026-02-01', '2026-02-25');
INSERT INTO [dbo].[consignment_inventory] ([id], [product_id], [batch_id], [customer_id], [consigned_qty], [consumed_qty], [available_qty], [unit_price], [consigned_date], [last_consumed_date], [settlement_period], [status], [created_at], [updated_at]) VALUES (2, 2, 2, 2, 80, 20, 60, 88.00, '2026-02-05', '2026-02-26', 'monthly', 'active', '2026-02-05', '2026-02-26');
SET IDENTITY_INSERT [dbo].[consignment_inventory] OFF;
