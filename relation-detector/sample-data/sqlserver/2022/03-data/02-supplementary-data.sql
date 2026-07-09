-- ============================================================
-- SQL Server ERP natural sample data.
-- Seed and transaction rows use ordinary INSERT VALUES so natural sample-data
-- does not inflate relationship/lineage counts with synthetic relationship SQL.
-- Baseline is T-SQL 2016-compatible for all SQL Server versions.
-- ============================================================

-- Natural seed rows for [dbo].[reconciliation_items].
SET IDENTITY_INSERT [dbo].[reconciliation_items] ON;
INSERT INTO [dbo].[reconciliation_items] ([id], [reconciliation_id], [journal_id], [transaction_date], [description], [debit_amount], [credit_amount], [is_matched], [matched_item_id], [difference_reason]) VALUES (1, 1, 1, '2026-02-01', 'Reconciliation Items 1', 1000.00, 1000.00, 1, 1, 'difference_reason-1');
INSERT INTO [dbo].[reconciliation_items] ([id], [reconciliation_id], [journal_id], [transaction_date], [description], [debit_amount], [credit_amount], [is_matched], [matched_item_id], [difference_reason]) VALUES (2, 2, 2, '2026-02-02', 'Reconciliation Items 2', 1800.00, 1800.00, 0, 2, 'difference_reason-2');
SET IDENTITY_INSERT [dbo].[reconciliation_items] OFF;

-- Natural seed rows for [dbo].[settlements].
SET IDENTITY_INSERT [dbo].[settlements] ON;
INSERT INTO [dbo].[settlements] ([id], [settlement_no], [settlement_type], [party_id], [settlement_date], [period_start], [period_end], [total_amount], [settled_amount], [unpaid_amount], [payment_due_date], [payment_method], [status], [voucher_id], [prepared_by], [approved_by], [remark], [created_at], [updated_at]) VALUES (1, 'SETTLEMENT-2001', 'settlement_type-1', 1, '2026-02-01', '2026-02', '2026-03-31', 1000.00, 1000.00, 1000.00, '2026-03-31', 'payment_method-1', 'active', 1, 1, 1, 'remark-1', '2026-02-01', '2026-02-01');
INSERT INTO [dbo].[settlements] ([id], [settlement_no], [settlement_type], [party_id], [settlement_date], [period_start], [period_end], [total_amount], [settled_amount], [unpaid_amount], [payment_due_date], [payment_method], [status], [voucher_id], [prepared_by], [approved_by], [remark], [created_at], [updated_at]) VALUES (2, 'SETTLEMENT-2002', 'settlement_type-2', 2, '2026-02-02', '2026-02', '2026-03-31', 1800.00, 1800.00, 1800.00, '2026-03-31', 'payment_method-2', 'posted', 2, 2, 2, 'remark-2', '2026-02-02', '2026-02-02');
SET IDENTITY_INSERT [dbo].[settlements] OFF;

-- Natural seed rows for [dbo].[settlement_items].
SET IDENTITY_INSERT [dbo].[settlement_items] ON;
INSERT INTO [dbo].[settlement_items] ([id], [settlement_id], [reference_type], [reference_id], [amount], [settled_amount], [remark]) VALUES (1, 1, 'standard', 1, 1000.00, 1000.00, 'remark-1');
INSERT INTO [dbo].[settlement_items] ([id], [settlement_id], [reference_type], [reference_id], [amount], [settled_amount], [remark]) VALUES (2, 2, 'business', 2, 1800.00, 1800.00, 'remark-2');
SET IDENTITY_INSERT [dbo].[settlement_items] OFF;

-- Natural seed rows for [dbo].[shipments].
SET IDENTITY_INSERT [dbo].[shipments] ON;
INSERT INTO [dbo].[shipments] ([id], [shipment_no], [order_id], [warehouse_id], [carrier], [tracking_no], [shipping_method], [shipping_fee], [package_count], [weight_kg], [status], [picker_id], [packer_id], [shipped_at], [delivered_at], [estimated_delivery_date], [actual_delivery_date], [from_address], [to_address], [receiver_name], [receiver_phone], [remark], [created_at], [updated_at]) VALUES (1, 'SHIPMENTS-4001', 1, 1, 'carrier-1', 'SHIPMENTS-4001', 'shipping_method-1', 1.0000, 4001, 1.0000, 'active', 1, 1, '2026-02-01', '2026-02-01', '2026-02-01', '2026-02-01', 'from_address-1', 'to_address-1', 'receiver_name-1', 'receiver_phone-1', 'remark-1', '2026-02-01', '2026-02-01');
INSERT INTO [dbo].[shipments] ([id], [shipment_no], [order_id], [warehouse_id], [carrier], [tracking_no], [shipping_method], [shipping_fee], [package_count], [weight_kg], [status], [picker_id], [packer_id], [shipped_at], [delivered_at], [estimated_delivery_date], [actual_delivery_date], [from_address], [to_address], [receiver_name], [receiver_phone], [remark], [created_at], [updated_at]) VALUES (2, 'SHIPMENTS-4002', 2, 2, 'carrier-2', 'SHIPMENTS-4002', 'shipping_method-2', 1.0000, 4002, 1.0000, 'posted', 2, 2, '2026-02-02', '2026-02-02', '2026-02-02', '2026-02-02', 'from_address-2', 'to_address-2', 'receiver_name-2', 'receiver_phone-2', 'remark-2', '2026-02-02', '2026-02-02');
SET IDENTITY_INSERT [dbo].[shipments] OFF;

-- Natural seed rows for [dbo].[shipping_tracks].
SET IDENTITY_INSERT [dbo].[shipping_tracks] ON;
INSERT INTO [dbo].[shipping_tracks] ([id], [shipment_id], [track_time], [location], [status_desc], [operator], [remark], [created_at]) VALUES (1, 1, '2026-02-01', 'location-1', 'status_desc-1', 'operator-1', 'remark-1', '2026-02-01');
INSERT INTO [dbo].[shipping_tracks] ([id], [shipment_id], [track_time], [location], [status_desc], [operator], [remark], [created_at]) VALUES (2, 2, '2026-02-02', 'location-2', 'status_desc-2', 'operator-2', 'remark-2', '2026-02-02');
SET IDENTITY_INSERT [dbo].[shipping_tracks] OFF;

-- Natural seed rows for [dbo].[commission_rules].
SET IDENTITY_INSERT [dbo].[commission_rules] ON;
INSERT INTO [dbo].[commission_rules] ([id], [name], [product_category_id], [min_amount], [max_amount], [commission_rate], [bonus], [effective_date], [expiry_date], [status], [created_at]) VALUES (1, 'Commission Rules 1', 1, 1000.00, 1000.00, 0.0500, 1.0000, '2026-02-01', '2026-03-31', 'active', '2026-02-01');
INSERT INTO [dbo].[commission_rules] ([id], [name], [product_category_id], [min_amount], [max_amount], [commission_rate], [bonus], [effective_date], [expiry_date], [status], [created_at]) VALUES (2, 'Commission Rules 2', 2, 1800.00, 1800.00, 0.0750, 1.0000, '2026-02-02', '2026-03-31', 'posted', '2026-02-02');
SET IDENTITY_INSERT [dbo].[commission_rules] OFF;

-- Natural seed rows for [dbo].[sales_commissions].
SET IDENTITY_INSERT [dbo].[sales_commissions] ON;
INSERT INTO [dbo].[sales_commissions] ([id], [employee_id], [order_id], [order_item_id], [period], [base_amount], [commission_rate], [commission_amount], [bonus], [total_commission], [status], [calculated_at], [paid_at], [settlement_id]) VALUES (1, 1, 1, 1, 'period-1', 1000.00, 0.0500, 1000.00, 1.0000, 1.0000, 'active', '2026-02-01', '2026-02-01', 1);
INSERT INTO [dbo].[sales_commissions] ([id], [employee_id], [order_id], [order_item_id], [period], [base_amount], [commission_rate], [commission_amount], [bonus], [total_commission], [status], [calculated_at], [paid_at], [settlement_id]) VALUES (2, 2, 2, 2, 'period-2', 1800.00, 0.0750, 1800.00, 1.0000, 1.0000, 'posted', '2026-02-02', '2026-02-02', 2);
SET IDENTITY_INSERT [dbo].[sales_commissions] OFF;

-- Natural seed rows for [dbo].[promotions].
SET IDENTITY_INSERT [dbo].[promotions] ON;
INSERT INTO [dbo].[promotions] ([id], [name], [code], [promotion_type], [discount_value], [min_purchase_amount], [max_discount_amount], [usage_limit], [used_count], [start_date], [end_date], [status], [created_at], [updated_at]) VALUES (1, 'Promotions 1', 'PROMOTIONS-8001', 'promotion_type-1', 1.0000, 1000.00, 1000.00, 8001, 8001, '2026-02-01', '2026-03-31', 'active', '2026-02-01', '2026-02-01');
INSERT INTO [dbo].[promotions] ([id], [name], [code], [promotion_type], [discount_value], [min_purchase_amount], [max_discount_amount], [usage_limit], [used_count], [start_date], [end_date], [status], [created_at], [updated_at]) VALUES (2, 'Promotions 2', 'PROMOTIONS-8002', 'promotion_type-2', 1.0000, 1800.00, 1800.00, 8002, 8002, '2026-02-02', '2026-03-31', 'posted', '2026-02-02', '2026-02-02');
SET IDENTITY_INSERT [dbo].[promotions] OFF;

-- Natural seed rows for [dbo].[promotion_products].
SET IDENTITY_INSERT [dbo].[promotion_products] ON;
INSERT INTO [dbo].[promotion_products] ([id], [promotion_id], [product_id], [category_id]) VALUES (1, 1, 1, 1);
INSERT INTO [dbo].[promotion_products] ([id], [promotion_id], [product_id], [category_id]) VALUES (2, 2, 2, 2);
SET IDENTITY_INSERT [dbo].[promotion_products] OFF;

-- Natural seed rows for [dbo].[promotion_usages].
SET IDENTITY_INSERT [dbo].[promotion_usages] ON;
INSERT INTO [dbo].[promotion_usages] ([id], [promotion_id], [order_id], [customer_id], [discount_applied], [used_at]) VALUES (1, 1, 1, 1, 1.0000, '2026-02-01');
INSERT INTO [dbo].[promotion_usages] ([id], [promotion_id], [order_id], [customer_id], [discount_applied], [used_at]) VALUES (2, 2, 2, 2, 1.0000, '2026-02-02');
SET IDENTITY_INSERT [dbo].[promotion_usages] OFF;

-- Natural seed rows for [dbo].[invoices].
SET IDENTITY_INSERT [dbo].[invoices] ON;
INSERT INTO [dbo].[invoices] ([id], [invoice_no], [invoice_type], [supplier_id], [customer_id], [invoice_date], [due_date], [total_amount], [tax_amount], [tax_rate], [status], [verified_by], [verified_at], [created_at], [updated_at]) VALUES (1, 'INVOICES-11001', 'standard', 1, 1, '2026-02-01', '2026-03-31', 1000.00, 1000.00, 0.0500, 'active', 1, '2026-02-01', '2026-02-01', '2026-02-01');
INSERT INTO [dbo].[invoices] ([id], [invoice_no], [invoice_type], [supplier_id], [customer_id], [invoice_date], [due_date], [total_amount], [tax_amount], [tax_rate], [status], [verified_by], [verified_at], [created_at], [updated_at]) VALUES (2, 'INVOICES-11002', 'business', 2, 2, '2026-02-02', '2026-03-31', 1800.00, 1800.00, 0.0750, 'posted', 2, '2026-02-02', '2026-02-02', '2026-02-02');
SET IDENTITY_INSERT [dbo].[invoices] OFF;

-- Natural seed rows for [dbo].[three_way_matching].
SET IDENTITY_INSERT [dbo].[three_way_matching] ON;
INSERT INTO [dbo].[three_way_matching] ([id], [invoice_id], [purchase_order_id], [purchase_receipt_id], [product_id], [po_quantity], [receipt_quantity], [invoice_quantity], [po_price], [receipt_price], [invoice_price], [quantity_match], [price_match], [match_status], [match_result], [matched_by], [matched_at]) VALUES (1, 1, 1, 1, 1, 11, 11, 11, 1000.00, 1000.00, 1000.00, 1, 1, 'match_status-1', 'match_result-1', 12001, '2026-02-01');
INSERT INTO [dbo].[three_way_matching] ([id], [invoice_id], [purchase_order_id], [purchase_receipt_id], [product_id], [po_quantity], [receipt_quantity], [invoice_quantity], [po_price], [receipt_price], [invoice_price], [quantity_match], [price_match], [match_status], [match_result], [matched_by], [matched_at]) VALUES (2, 2, 2, 2, 2, 12, 12, 12, 1800.00, 1800.00, 1800.00, 0, 0, 'match_status-2', 'match_result-2', 12002, '2026-02-02');
SET IDENTITY_INSERT [dbo].[three_way_matching] OFF;

-- Natural seed rows for [dbo].[fixed_assets].
SET IDENTITY_INSERT [dbo].[fixed_assets] ON;
INSERT INTO [dbo].[fixed_assets] ([id], [asset_no], [name], [category], [purchase_date], [purchase_amount], [salvage_value], [useful_life_months], [monthly_depreciation], [accumulated_depreciation], [net_book_value], [department_id], [custodian_id], [location], [status], [last_depreciation_date], [created_at], [updated_at]) VALUES (1, 'FIXED_ASSE-13001', 'Fixed Assets 1', 'category-1', '2026-02-01', 1000.00, 1.0000, 13001, 1.0000, 1.0000, 1.0000, 1, 1, 'location-1', 'active', '2026-02-01', '2026-02-01', '2026-02-01');
INSERT INTO [dbo].[fixed_assets] ([id], [asset_no], [name], [category], [purchase_date], [purchase_amount], [salvage_value], [useful_life_months], [monthly_depreciation], [accumulated_depreciation], [net_book_value], [department_id], [custodian_id], [location], [status], [last_depreciation_date], [created_at], [updated_at]) VALUES (2, 'FIXED_ASSE-13002', 'Fixed Assets 2', 'category-2', '2026-02-02', 1800.00, 1.0000, 13002, 1.0000, 1.0000, 1.0000, 2, 2, 'location-2', 'posted', '2026-02-02', '2026-02-02', '2026-02-02');
SET IDENTITY_INSERT [dbo].[fixed_assets] OFF;

-- Natural seed rows for [dbo].[depreciation_log].
SET IDENTITY_INSERT [dbo].[depreciation_log] ON;
INSERT INTO [dbo].[depreciation_log] ([id], [asset_id], [depreciation_date], [depreciation_amount], [before_accumulated], [after_accumulated], [before_net_value], [after_net_value], [voucher_id], [created_at]) VALUES (1, 1, '2026-02-01', 1000.00, 1.0000, 1.0000, 1.0000, 1.0000, 1, '2026-02-01');
INSERT INTO [dbo].[depreciation_log] ([id], [asset_id], [depreciation_date], [depreciation_amount], [before_accumulated], [after_accumulated], [before_net_value], [after_net_value], [voucher_id], [created_at]) VALUES (2, 2, '2026-02-02', 1800.00, 1.0000, 1.0000, 1.0000, 1.0000, 2, '2026-02-02');
SET IDENTITY_INSERT [dbo].[depreciation_log] OFF;

-- Natural seed rows for [dbo].[boms].
SET IDENTITY_INSERT [dbo].[boms] ON;
INSERT INTO [dbo].[boms] ([id], [parent_product_id], [child_product_id], [quantity], [unit], [scrap_rate], [sort_order], [effective_date], [expiry_date], [status], [created_at]) VALUES (1, 1, 1, 10.0000, 'Boms 1', 0.0500, 1, '2026-02-01', '2026-03-31', 'active', '2026-02-01');
INSERT INTO [dbo].[boms] ([id], [parent_product_id], [child_product_id], [quantity], [unit], [scrap_rate], [sort_order], [effective_date], [expiry_date], [status], [created_at]) VALUES (2, 2, 2, 18.0000, 'Boms 2', 0.0750, 2, '2026-02-02', '2026-03-31', 'posted', '2026-02-02');
SET IDENTITY_INSERT [dbo].[boms] OFF;

-- Natural seed rows for [dbo].[work_orders].
SET IDENTITY_INSERT [dbo].[work_orders] ON;
INSERT INTO [dbo].[work_orders] ([id], [order_no], [product_id], [bom_id], [planned_quantity], [completed_quantity], [rejected_quantity], [warehouse_id], [start_date], [due_date], [completed_date], [status], [priority], [released_by], [remark], [created_at], [updated_at]) VALUES (1, 'WORK_ORDER-16001', 1, 1, 11, 11, 11, 1, '2026-02-01', '2026-03-31', '2026-02-01', 'active', 'priority-1', 16001, 'remark-1', '2026-02-01', '2026-02-01');
INSERT INTO [dbo].[work_orders] ([id], [order_no], [product_id], [bom_id], [planned_quantity], [completed_quantity], [rejected_quantity], [warehouse_id], [start_date], [due_date], [completed_date], [status], [priority], [released_by], [remark], [created_at], [updated_at]) VALUES (2, 'WORK_ORDER-16002', 2, 2, 12, 12, 12, 2, '2026-02-02', '2026-03-31', '2026-02-02', 'posted', 'priority-2', 16002, 'remark-2', '2026-02-02', '2026-02-02');
SET IDENTITY_INSERT [dbo].[work_orders] OFF;

-- Natural seed rows for [dbo].[work_order_materials].
SET IDENTITY_INSERT [dbo].[work_order_materials] ON;
INSERT INTO [dbo].[work_order_materials] ([id], [work_order_id], [product_id], [batch_id], [required_qty], [issued_qty], [returned_qty], [actual_consumed], [unit], [status]) VALUES (1, 1, 1, 1, 10.0000, 10.0000, 10.0000, 1.0000, 'Work Order Materials 1', 'active');
INSERT INTO [dbo].[work_order_materials] ([id], [work_order_id], [product_id], [batch_id], [required_qty], [issued_qty], [returned_qty], [actual_consumed], [unit], [status]) VALUES (2, 2, 2, 2, 18.0000, 18.0000, 18.0000, 1.0000, 'Work Order Materials 2', 'posted');
SET IDENTITY_INSERT [dbo].[work_order_materials] OFF;

-- Natural seed rows for [dbo].[service_tickets].
SET IDENTITY_INSERT [dbo].[service_tickets] ON;
INSERT INTO [dbo].[service_tickets] ([id], [ticket_no], [customer_id], [order_id], [product_id], [ticket_type], [priority], [subject], [description], [status], [assigned_to], [resolution], [resolved_at], [satisfaction_score], [created_at], [updated_at]) VALUES (1, 'SERVICE_TI-18001', 1, 1, 1, 'ticket_type-1', 'priority-1', 'Service Tickets 1', 'Service Tickets 1', 'active', 18001, 'resolution-1', '2026-02-01', 18001, '2026-02-01', '2026-02-01');
INSERT INTO [dbo].[service_tickets] ([id], [ticket_no], [customer_id], [order_id], [product_id], [ticket_type], [priority], [subject], [description], [status], [assigned_to], [resolution], [resolved_at], [satisfaction_score], [created_at], [updated_at]) VALUES (2, 'SERVICE_TI-18002', 2, 2, 2, 'ticket_type-2', 'priority-2', 'Service Tickets 2', 'Service Tickets 2', 'posted', 18002, 'resolution-2', '2026-02-02', 18002, '2026-02-02', '2026-02-02');
SET IDENTITY_INSERT [dbo].[service_tickets] OFF;

-- Natural seed rows for [dbo].[contracts].
SET IDENTITY_INSERT [dbo].[contracts] ON;
INSERT INTO [dbo].[contracts] ([id], [contract_no], [contract_type], [party_type], [party_id], [subject], [total_amount], [currency], [signed_date], [start_date], [end_date], [payment_terms], [delivery_terms], [status], [prepared_by], [approved_by], [signed_by], [attachment_path], [remark], [created_at], [updated_at]) VALUES (1, 'CONTRACTS-19001', 'contract_type-1', 'party_type-1', 1, 'Contracts 1', 1000.00, 'CNY', '2026-02-01', '2026-02-01', '2026-03-31', 'payment_terms-1', 'delivery_terms-1', 'active', 1, 1, 'signed_by-1', 'attachment_path-1', 'remark-1', '2026-02-01', '2026-02-01');
INSERT INTO [dbo].[contracts] ([id], [contract_no], [contract_type], [party_type], [party_id], [subject], [total_amount], [currency], [signed_date], [start_date], [end_date], [payment_terms], [delivery_terms], [status], [prepared_by], [approved_by], [signed_by], [attachment_path], [remark], [created_at], [updated_at]) VALUES (2, 'CONTRACTS-19002', 'contract_type-2', 'party_type-2', 2, 'Contracts 2', 1800.00, 'CNY', '2026-02-02', '2026-02-02', '2026-03-31', 'payment_terms-2', 'delivery_terms-2', 'posted', 2, 2, 'signed_by-2', 'attachment_path-2', 'remark-2', '2026-02-02', '2026-02-02');
SET IDENTITY_INSERT [dbo].[contracts] OFF;

-- Natural seed rows for [dbo].[contract_milestones].
SET IDENTITY_INSERT [dbo].[contract_milestones] ON;
INSERT INTO [dbo].[contract_milestones] ([id], [contract_id], [milestone_name], [milestone_type], [planned_date], [actual_date], [amount], [completion_pct], [status], [responsible_person], [remark], [created_at]) VALUES (1, 1, 'milestone_name-1', 'milestone_type-1', '2026-02-01', '2026-02-01', 1000.00, 1.0000, 'active', 20001, 'remark-1', '2026-02-01');
INSERT INTO [dbo].[contract_milestones] ([id], [contract_id], [milestone_name], [milestone_type], [planned_date], [actual_date], [amount], [completion_pct], [status], [responsible_person], [remark], [created_at]) VALUES (2, 2, 'milestone_name-2', 'milestone_type-2', '2026-02-02', '2026-02-02', 1800.00, 1.0000, 'posted', 20002, 'remark-2', '2026-02-02');
SET IDENTITY_INSERT [dbo].[contract_milestones] OFF;
