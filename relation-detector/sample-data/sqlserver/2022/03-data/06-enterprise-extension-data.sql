-- ============================================================
-- SQL Server ERP natural sample data.
-- Seed and transaction rows use ordinary INSERT VALUES so natural sample-data
-- does not inflate relationship/lineage counts with synthetic relationship SQL.
-- Baseline is T-SQL 2016-compatible for all SQL Server versions.
-- ============================================================

-- Natural seed rows for [dbo].[consignment_consumptions].
SET IDENTITY_INSERT [dbo].[consignment_consumptions] ON;
INSERT INTO [dbo].[consignment_consumptions] ([id], [consignment_id], [consumed_qty], [consumed_date], [unit_price], [amount], [confirmed_by_customer], [sales_order_id], [remark], [created_at]) VALUES (1, 1, 11, '2026-02-01', 1000.00, 1000.00, 1, 1, 'remark-1', '2026-02-01');
INSERT INTO [dbo].[consignment_consumptions] ([id], [consignment_id], [consumed_qty], [consumed_date], [unit_price], [amount], [confirmed_by_customer], [sales_order_id], [remark], [created_at]) VALUES (2, 2, 12, '2026-02-02', 1800.00, 1800.00, 0, 2, 'remark-2', '2026-02-02');
SET IDENTITY_INSERT [dbo].[consignment_consumptions] OFF;

-- Natural seed rows for [dbo].[price_change_logs].
SET IDENTITY_INSERT [dbo].[price_change_logs] ON;
INSERT INTO [dbo].[price_change_logs] ([id], [product_id], [price_type], [old_price], [new_price], [change_reason], [effective_date], [changed_by], [approved_by], [created_at]) VALUES (1, 1, 'price_type-1', 1000.00, 1000.00, 'change_reason-1', '2026-02-01', 2001, 1, '2026-02-01');
INSERT INTO [dbo].[price_change_logs] ([id], [product_id], [price_type], [old_price], [new_price], [change_reason], [effective_date], [changed_by], [approved_by], [created_at]) VALUES (2, 2, 'price_type-2', 1800.00, 1800.00, 'change_reason-2', '2026-02-02', 2002, 2, '2026-02-02');
SET IDENTITY_INSERT [dbo].[price_change_logs] OFF;

-- Natural seed rows for [dbo].[tenants].
SET IDENTITY_INSERT [dbo].[tenants] ON;
INSERT INTO [dbo].[tenants] ([id], [tenant_code], [tenant_name], [legal_entity_name], [tax_no], [status], [created_at], [updated_at]) VALUES (1, 'TENANTS-3001', 'tenant_name-1', 'legal_entity_name-1', 'TENANTS-3001', 'active', '2026-02-01', '2026-02-01');
INSERT INTO [dbo].[tenants] ([id], [tenant_code], [tenant_name], [legal_entity_name], [tax_no], [status], [created_at], [updated_at]) VALUES (2, 'TENANTS-3002', 'tenant_name-2', 'legal_entity_name-2', 'TENANTS-3002', 'posted', '2026-02-02', '2026-02-02');
SET IDENTITY_INSERT [dbo].[tenants] OFF;

-- Natural seed rows for [dbo].[ledger_books].
SET IDENTITY_INSERT [dbo].[ledger_books] ON;
INSERT INTO [dbo].[ledger_books] ([id], [tenant_id], [book_code], [book_name], [base_currency], [fiscal_year_start_month], [is_default], [status]) VALUES (1, 1, 'LEDGER_BOO-4001', 'book_name-1', 'base_currency-1', 4001, 1, 'active');
INSERT INTO [dbo].[ledger_books] ([id], [tenant_id], [book_code], [book_name], [base_currency], [fiscal_year_start_month], [is_default], [status]) VALUES (2, 2, 'LEDGER_BOO-4002', 'book_name-2', 'base_currency-2', 4002, 0, 'posted');
SET IDENTITY_INSERT [dbo].[ledger_books] OFF;

-- Natural seed rows for [dbo].[customer_addresses].
SET IDENTITY_INSERT [dbo].[customer_addresses] ON;
INSERT INTO [dbo].[customer_addresses] ([id], [customer_id], [address_type], [receiver_name], [receiver_phone], [province], [city], [district], [street], [postal_code], [is_default]) VALUES (1, 1, 'address_type-1', 'receiver_name-1', 'receiver_phone-1', 'province-1', 'city-1', 'district-1', 'street-1', 'CUSTOMER_A-5001', 1);
INSERT INTO [dbo].[customer_addresses] ([id], [customer_id], [address_type], [receiver_name], [receiver_phone], [province], [city], [district], [street], [postal_code], [is_default]) VALUES (2, 2, 'address_type-2', 'receiver_name-2', 'receiver_phone-2', 'province-2', 'city-2', 'district-2', 'street-2', 'CUSTOMER_A-5002', 0);
SET IDENTITY_INSERT [dbo].[customer_addresses] OFF;

-- Natural seed rows for [dbo].[supplier_addresses].
SET IDENTITY_INSERT [dbo].[supplier_addresses] ON;
INSERT INTO [dbo].[supplier_addresses] ([id], [supplier_id], [address_type], [contact_name], [contact_phone], [province], [city], [district], [street], [postal_code], [is_default]) VALUES (1, 1, 'address_type-1', 'contact_name-1', 'contact_phone-1', 'province-1', 'city-1', 'district-1', 'street-1', 'SUPPLIER_A-6001', 1);
INSERT INTO [dbo].[supplier_addresses] ([id], [supplier_id], [address_type], [contact_name], [contact_phone], [province], [city], [district], [street], [postal_code], [is_default]) VALUES (2, 2, 'address_type-2', 'contact_name-2', 'contact_phone-2', 'province-2', 'city-2', 'district-2', 'street-2', 'SUPPLIER_A-6002', 0);
SET IDENTITY_INSERT [dbo].[supplier_addresses] OFF;

-- Natural seed rows for [dbo].[tax_rates].
SET IDENTITY_INSERT [dbo].[tax_rates] ON;
INSERT INTO [dbo].[tax_rates] ([id], [tax_code], [tax_name], [tax_type], [rate], [effective_from], [effective_to], [status]) VALUES (1, 'TAX_RATES-7001', 'tax_name-1', 'tax_type-1', 0.0500, '2026-02-01', '2026-02-01', 'active');
INSERT INTO [dbo].[tax_rates] ([id], [tax_code], [tax_name], [tax_type], [rate], [effective_from], [effective_to], [status]) VALUES (2, 'TAX_RATES-7002', 'tax_name-2', 'tax_type-2', 0.0750, '2026-02-02', '2026-02-02', 'posted');
SET IDENTITY_INSERT [dbo].[tax_rates] OFF;

-- Natural seed rows for [dbo].[accounting_periods].
SET IDENTITY_INSERT [dbo].[accounting_periods] ON;
INSERT INTO [dbo].[accounting_periods] ([id], [ledger_book_id], [period_code], [period_start], [period_end], [status], [closed_by], [closed_at]) VALUES (1, 1, 'ACCOUNTING-8001', '2026-02', '2026-03-31', 'active', 1, '2026-02-01');
INSERT INTO [dbo].[accounting_periods] ([id], [ledger_book_id], [period_code], [period_start], [period_end], [status], [closed_by], [closed_at]) VALUES (2, 2, 'ACCOUNTING-8002', '2026-02', '2026-03-31', 'posted', 2, '2026-02-02');
SET IDENTITY_INSERT [dbo].[accounting_periods] OFF;

-- Natural seed rows for [dbo].[period_close_jobs].
SET IDENTITY_INSERT [dbo].[period_close_jobs] ON;
INSERT INTO [dbo].[period_close_jobs] ([id], [period_id], [job_code], [job_name], [status], [started_at], [finished_at], [message]) VALUES (1, 1, 'PERIOD_CLO-9001', 'job_name-1', 'active', '2026-02-01', '2026-02-01', 'message-1');
INSERT INTO [dbo].[period_close_jobs] ([id], [period_id], [job_code], [job_name], [status], [started_at], [finished_at], [message]) VALUES (2, 2, 'PERIOD_CLO-9002', 'job_name-2', 'posted', '2026-02-02', '2026-02-02', 'message-2');
SET IDENTITY_INSERT [dbo].[period_close_jobs] OFF;

-- Natural seed rows for [dbo].[payment_receipts].
SET IDENTITY_INSERT [dbo].[payment_receipts] ON;
INSERT INTO [dbo].[payment_receipts] ([id], [receipt_no], [receipt_type], [party_type], [party_id], [account_id], [receipt_date], [amount], [currency], [status], [handled_by], [confirmed_at], [remark]) VALUES (1, 'PAYMENT_RE-10001', 'receipt_type-1', 'party_type-1', 1, 1, '2026-02-01', 1000.00, 'CNY', 'active', 10001, '2026-02-01', 'remark-1');
INSERT INTO [dbo].[payment_receipts] ([id], [receipt_no], [receipt_type], [party_type], [party_id], [account_id], [receipt_date], [amount], [currency], [status], [handled_by], [confirmed_at], [remark]) VALUES (2, 'PAYMENT_RE-10002', 'receipt_type-2', 'party_type-2', 2, 2, '2026-02-02', 1800.00, 'CNY', 'posted', 10002, '2026-02-02', 'remark-2');
SET IDENTITY_INSERT [dbo].[payment_receipts] OFF;

-- Natural seed rows for [dbo].[payment_receipt_allocations].
SET IDENTITY_INSERT [dbo].[payment_receipt_allocations] ON;
INSERT INTO [dbo].[payment_receipt_allocations] ([id], [receipt_id], [reference_type], [reference_id], [allocated_amount]) VALUES (1, 1, 'standard', 1, 1000.00);
INSERT INTO [dbo].[payment_receipt_allocations] ([id], [receipt_id], [reference_type], [reference_id], [allocated_amount]) VALUES (2, 2, 'business', 2, 1800.00);
SET IDENTITY_INSERT [dbo].[payment_receipt_allocations] OFF;

-- Natural seed rows for [dbo].[stocktakes].
SET IDENTITY_INSERT [dbo].[stocktakes] ON;
INSERT INTO [dbo].[stocktakes] ([id], [stocktake_no], [warehouse_id], [stocktake_date], [stocktake_type], [status], [created_by], [reviewed_by], [posted_at]) VALUES (1, 'STOCKTAKES-12001', 1, '2026-02-01', 'stocktake_type-1', 'active', 1, 12001, '2026-02-01');
INSERT INTO [dbo].[stocktakes] ([id], [stocktake_no], [warehouse_id], [stocktake_date], [stocktake_type], [status], [created_by], [reviewed_by], [posted_at]) VALUES (2, 'STOCKTAKES-12002', 2, '2026-02-02', 'stocktake_type-2', 'posted', 2, 12002, '2026-02-02');
SET IDENTITY_INSERT [dbo].[stocktakes] OFF;

-- Natural seed rows for [dbo].[stocktake_items].
SET IDENTITY_INSERT [dbo].[stocktake_items] ON;
INSERT INTO [dbo].[stocktake_items] ([id], [stocktake_id], [product_id], [batch_id], [book_quantity], [counted_quantity], [variance_quantity], [variance_reason]) VALUES (1, 1, 1, 1, 11, 11, 11, 'variance_reason-1');
INSERT INTO [dbo].[stocktake_items] ([id], [stocktake_id], [product_id], [batch_id], [book_quantity], [counted_quantity], [variance_quantity], [variance_reason]) VALUES (2, 2, 2, 2, 12, 12, 12, 'variance_reason-2');
SET IDENTITY_INSERT [dbo].[stocktake_items] OFF;

-- Natural seed rows for [dbo].[stock_transfers].
SET IDENTITY_INSERT [dbo].[stock_transfers] ON;
INSERT INTO [dbo].[stock_transfers] ([id], [transfer_no], [from_warehouse_id], [to_warehouse_id], [requested_by], [approved_by], [transfer_date], [status]) VALUES (1, 'STOCK_TRAN-14001', 1, 1, 14001, 1, '2026-02-01', 'active');
INSERT INTO [dbo].[stock_transfers] ([id], [transfer_no], [from_warehouse_id], [to_warehouse_id], [requested_by], [approved_by], [transfer_date], [status]) VALUES (2, 'STOCK_TRAN-14002', 2, 2, 14002, 2, '2026-02-02', 'posted');
SET IDENTITY_INSERT [dbo].[stock_transfers] OFF;

-- Natural seed rows for [dbo].[stock_transfer_items].
SET IDENTITY_INSERT [dbo].[stock_transfer_items] ON;
INSERT INTO [dbo].[stock_transfer_items] ([id], [transfer_id], [product_id], [batch_id], [quantity], [received_quantity]) VALUES (1, 1, 1, 1, 11, 11);
INSERT INTO [dbo].[stock_transfer_items] ([id], [transfer_id], [product_id], [batch_id], [quantity], [received_quantity]) VALUES (2, 2, 2, 2, 12, 12);
SET IDENTITY_INSERT [dbo].[stock_transfer_items] OFF;

-- Natural seed rows for [dbo].[inventory_reservations].
SET IDENTITY_INSERT [dbo].[inventory_reservations] ON;
INSERT INTO [dbo].[inventory_reservations] ([id], [reservation_no], [product_id], [batch_id], [warehouse_id], [source_type], [source_id], [reserved_quantity], [released_quantity], [status], [expires_at]) VALUES (1, 'INVENTORY_-16001', 1, 1, 1, 'source_type-1', 1, 11, 11, 'active', '2026-02-01');
INSERT INTO [dbo].[inventory_reservations] ([id], [reservation_no], [product_id], [batch_id], [warehouse_id], [source_type], [source_id], [reserved_quantity], [released_quantity], [status], [expires_at]) VALUES (2, 'INVENTORY_-16002', 2, 2, 2, 'source_type-2', 2, 12, 12, 'posted', '2026-02-02');
SET IDENTITY_INSERT [dbo].[inventory_reservations] OFF;

-- Natural seed rows for [dbo].[production_routes].
SET IDENTITY_INSERT [dbo].[production_routes] ON;
INSERT INTO [dbo].[production_routes] ([id], [product_id], [route_code], [route_name], [version_no], [status]) VALUES (1, 1, 'PRODUCTION-17001', 'route_name-1', 'PRODUCTION-17001', 'active');
INSERT INTO [dbo].[production_routes] ([id], [product_id], [route_code], [route_name], [version_no], [status]) VALUES (2, 2, 'PRODUCTION-17002', 'route_name-2', 'PRODUCTION-17002', 'posted');
SET IDENTITY_INSERT [dbo].[production_routes] OFF;

-- Natural seed rows for [dbo].[production_operations].
SET IDENTITY_INSERT [dbo].[production_operations] ON;
INSERT INTO [dbo].[production_operations] ([id], [route_id], [operation_no], [operation_name], [work_center], [standard_minutes], [predecessor_operation_id]) VALUES (1, 1, 18001, 'operation_name-1', 'work_center-1', 1.0000, 1);
INSERT INTO [dbo].[production_operations] ([id], [route_id], [operation_no], [operation_name], [work_center], [standard_minutes], [predecessor_operation_id]) VALUES (2, 2, 18002, 'operation_name-2', 'work_center-2', 1.0000, 2);
SET IDENTITY_INSERT [dbo].[production_operations] OFF;

-- Natural seed rows for [dbo].[employee_shifts].
SET IDENTITY_INSERT [dbo].[employee_shifts] ON;
INSERT INTO [dbo].[employee_shifts] ([id], [shift_code], [shift_name], [start_time], [end_time], [planned_hours], [is_night_shift]) VALUES (1, 'EMPLOYEE_S-19001', 'shift_name-1', '2026-02-01', '2026-03-31', 1.0000, 1);
INSERT INTO [dbo].[employee_shifts] ([id], [shift_code], [shift_name], [start_time], [end_time], [planned_hours], [is_night_shift]) VALUES (2, 'EMPLOYEE_S-19002', 'shift_name-2', '2026-02-02', '2026-03-31', 1.0000, 0);
SET IDENTITY_INSERT [dbo].[employee_shifts] OFF;

-- Natural seed rows for [dbo].[employee_shift_assignments].
SET IDENTITY_INSERT [dbo].[employee_shift_assignments] ON;
INSERT INTO [dbo].[employee_shift_assignments] ([id], [employee_id], [shift_id], [work_date], [warehouse_id], [status]) VALUES (1, 1, 1, '2026-02-01', 1, 'active');
INSERT INTO [dbo].[employee_shift_assignments] ([id], [employee_id], [shift_id], [work_date], [warehouse_id], [status]) VALUES (2, 2, 2, '2026-02-02', 2, 'posted');
SET IDENTITY_INSERT [dbo].[employee_shift_assignments] OFF;
