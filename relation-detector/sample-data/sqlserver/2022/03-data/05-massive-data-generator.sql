-- ============================================================
-- SQL Server ERP natural sample data.
-- Seed and transaction rows use ordinary INSERT VALUES so natural sample-data
-- does not inflate relationship/lineage counts with synthetic relationship SQL.
-- Baseline is T-SQL 2016-compatible for all SQL Server versions.
-- ============================================================

-- Natural seed rows for [dbo].[departments].
SET IDENTITY_INSERT [dbo].[departments] ON;
INSERT INTO [dbo].[departments] ([id], [parent_id], [name], [code], [manager_id], [budget], [headcount_plan], [status], [created_at], [updated_at]) VALUES (1, 1, 'Departments 1', 'DEPARTMENT-1001', 1, 1000.00, 11, 'active', '2026-02-01', '2026-02-01');
INSERT INTO [dbo].[departments] ([id], [parent_id], [name], [code], [manager_id], [budget], [headcount_plan], [status], [created_at], [updated_at]) VALUES (2, 2, 'Departments 2', 'DEPARTMENT-1002', 2, 1800.00, 12, 'posted', '2026-02-02', '2026-02-02');
SET IDENTITY_INSERT [dbo].[departments] OFF;

-- Natural seed rows for [dbo].[positions].
SET IDENTITY_INSERT [dbo].[positions] ON;
INSERT INTO [dbo].[positions] ([id], [department_id], [name], [code], [level], [min_salary], [max_salary], [headcount], [status], [created_at]) VALUES (1, 1, 'Positions 1', 'POSITIONS-2001', 1, 1.0000, 1.0000, 11, 'active', '2026-02-01');
INSERT INTO [dbo].[positions] ([id], [department_id], [name], [code], [level], [min_salary], [max_salary], [headcount], [status], [created_at]) VALUES (2, 2, 'Positions 2', 'POSITIONS-2002', 2, 1.0000, 1.0000, 12, 'posted', '2026-02-02');
SET IDENTITY_INSERT [dbo].[positions] OFF;

-- Natural seed rows for [dbo].[employees].
SET IDENTITY_INSERT [dbo].[employees] ON;
INSERT INTO [dbo].[employees] ([id], [employee_no], [name], [gender], [id_card], [phone], [email], [birth_date], [hire_date], [department_id], [position_id], [manager_id], [salary], [social_security_base], [housing_fund_base], [bank_name], [bank_account], [status], [resignation_date], [resignation_reason], [address], [emergency_contact], [emergency_phone], [created_at], [updated_at]) VALUES (1, 'EMPLOYEES-3001', 'Employees 1', 'gender-1', 'id_card-1', '13800000001', 'sample1@example.com', '2026-02-01', '2026-02-01', 1, 1, 1, 1.0000, 1.0000, 1.0000, 'bank_name-1', 'bank_account-1', 'active', '2026-02-01', 'resignation_reason-1', 'Employees 1', 'emergency_contact-1', 'emergency_phone-1', '2026-02-01', '2026-02-01');
INSERT INTO [dbo].[employees] ([id], [employee_no], [name], [gender], [id_card], [phone], [email], [birth_date], [hire_date], [department_id], [position_id], [manager_id], [salary], [social_security_base], [housing_fund_base], [bank_name], [bank_account], [status], [resignation_date], [resignation_reason], [address], [emergency_contact], [emergency_phone], [created_at], [updated_at]) VALUES (2, 'EMPLOYEES-3002', 'Employees 2', 'gender-2', 'id_card-2', '13800000002', 'sample2@example.com', '2026-02-02', '2026-02-02', 2, 2, 2, 1.0000, 1.0000, 1.0000, 'bank_name-2', 'bank_account-2', 'posted', '2026-02-02', 'resignation_reason-2', 'Employees 2', 'emergency_contact-2', 'emergency_phone-2', '2026-02-02', '2026-02-02');
SET IDENTITY_INSERT [dbo].[employees] OFF;

-- Natural seed rows for [dbo].[employee_salary_log].
SET IDENTITY_INSERT [dbo].[employee_salary_log] ON;
INSERT INTO [dbo].[employee_salary_log] ([id], [employee_id], [old_salary], [new_salary], [change_reason], [effective_date], [approved_by], [created_at]) VALUES (1, 1, 1.0000, 1.0000, 'change_reason-1', '2026-02-01', 1, '2026-02-01');
INSERT INTO [dbo].[employee_salary_log] ([id], [employee_id], [old_salary], [new_salary], [change_reason], [effective_date], [approved_by], [created_at]) VALUES (2, 2, 1.0000, 1.0000, 'change_reason-2', '2026-02-02', 2, '2026-02-02');
SET IDENTITY_INSERT [dbo].[employee_salary_log] OFF;

-- Natural seed rows for [dbo].[attendance].
SET IDENTITY_INSERT [dbo].[attendance] ON;
INSERT INTO [dbo].[attendance] ([id], [employee_id], [attendance_date], [clock_in], [clock_out], [status], [late_minutes], [early_minutes], [overtime_hours], [remark]) VALUES (1, 1, '2026-03-31', '2026-02-01', '2026-02-01', 'active', 6, 6, 1.0000, 'remark-1');
INSERT INTO [dbo].[attendance] ([id], [employee_id], [attendance_date], [clock_in], [clock_out], [status], [late_minutes], [early_minutes], [overtime_hours], [remark]) VALUES (2, 2, '2026-03-31', '2026-02-02', '2026-02-02', 'posted', 7, 7, 1.0000, 'remark-2');
SET IDENTITY_INSERT [dbo].[attendance] OFF;

-- Natural seed rows for [dbo].[leave_records].
SET IDENTITY_INSERT [dbo].[leave_records] ON;
INSERT INTO [dbo].[leave_records] ([id], [employee_id], [leave_type], [start_date], [end_date], [days], [reason], [status], [approved_by], [approved_at], [created_at]) VALUES (1, 1, 'leave_type-1', '2026-02-01', '2026-03-31', 1.0000, 'Leave Records 1', 'active', 1, '2026-02-01', '2026-02-01');
INSERT INTO [dbo].[leave_records] ([id], [employee_id], [leave_type], [start_date], [end_date], [days], [reason], [status], [approved_by], [approved_at], [created_at]) VALUES (2, 2, 'leave_type-2', '2026-02-02', '2026-03-31', 1.0000, 'Leave Records 2', 'posted', 2, '2026-02-02', '2026-02-02');
SET IDENTITY_INSERT [dbo].[leave_records] OFF;

-- Natural seed rows for [dbo].[roles].
SET IDENTITY_INSERT [dbo].[roles] ON;
INSERT INTO [dbo].[roles] ([id], [name], [code], [description], [is_system], [created_at]) VALUES (1, 'Roles 1', 'ROLES-7001', 'Roles 1', 1, '2026-02-01');
INSERT INTO [dbo].[roles] ([id], [name], [code], [description], [is_system], [created_at]) VALUES (2, 'Roles 2', 'ROLES-7002', 'Roles 2', 0, '2026-02-02');
SET IDENTITY_INSERT [dbo].[roles] OFF;

-- Natural seed rows for [dbo].[permissions].
SET IDENTITY_INSERT [dbo].[permissions] ON;
INSERT INTO [dbo].[permissions] ([id], [parent_id], [name], [code], [resource_type], [resource_path], [action], [created_at]) VALUES (1, 1, 'Permissions 1', 'PERMISSION-8001', 'resource_type-1', 'resource_path-1', 'Permissions 1', '2026-02-01');
INSERT INTO [dbo].[permissions] ([id], [parent_id], [name], [code], [resource_type], [resource_path], [action], [created_at]) VALUES (2, 2, 'Permissions 2', 'PERMISSION-8002', 'resource_type-2', 'resource_path-2', 'Permissions 2', '2026-02-02');
SET IDENTITY_INSERT [dbo].[permissions] OFF;

-- Natural seed rows for [dbo].[role_permissions].
SET IDENTITY_INSERT [dbo].[role_permissions] ON;
INSERT INTO [dbo].[role_permissions] ([id], [role_id], [permission_id]) VALUES (1, 1, 1);
INSERT INTO [dbo].[role_permissions] ([id], [role_id], [permission_id]) VALUES (2, 2, 2);
SET IDENTITY_INSERT [dbo].[role_permissions] OFF;

-- Natural seed rows for [dbo].[employee_roles].
SET IDENTITY_INSERT [dbo].[employee_roles] ON;
INSERT INTO [dbo].[employee_roles] ([id], [employee_id], [role_id], [granted_by], [granted_at], [expires_at]) VALUES (1, 1, 1, 10001, '2026-02-01', '2026-02-01');
INSERT INTO [dbo].[employee_roles] ([id], [employee_id], [role_id], [granted_by], [granted_at], [expires_at]) VALUES (2, 2, 2, 10002, '2026-02-02', '2026-02-02');
SET IDENTITY_INSERT [dbo].[employee_roles] OFF;

-- Natural seed rows for [dbo].[audit_log].
SET IDENTITY_INSERT [dbo].[audit_log] ON;
INSERT INTO [dbo].[audit_log] ([id], [employee_id], [action], [target_type], [target_id], [old_value], [new_value], [ip_address], [user_agent], [remark], [created_at]) VALUES (1, 1, 'Audit Log 1', 'standard', 1, 'Audit Log 1', 'Audit Log 1', 'ip_address-1', 'user_agent-1', 'remark-1', '2026-02-01');
INSERT INTO [dbo].[audit_log] ([id], [employee_id], [action], [target_type], [target_id], [old_value], [new_value], [ip_address], [user_agent], [remark], [created_at]) VALUES (2, 2, 'Audit Log 2', 'business', 2, 'Audit Log 2', 'Audit Log 2', 'ip_address-2', 'user_agent-2', 'remark-2', '2026-02-02');
SET IDENTITY_INSERT [dbo].[audit_log] OFF;

-- Natural seed rows for [dbo].[product_categories].
SET IDENTITY_INSERT [dbo].[product_categories] ON;
INSERT INTO [dbo].[product_categories] ([id], [parent_id], [name], [code], [description], [sort_order], [status], [created_at]) VALUES (1, 1, 'Product Categories 1', 'PRODUCT_CA-12001', 'Product Categories 1', 1, 'active', '2026-02-01');
INSERT INTO [dbo].[product_categories] ([id], [parent_id], [name], [code], [description], [sort_order], [status], [created_at]) VALUES (2, 2, 'Product Categories 2', 'PRODUCT_CA-12002', 'Product Categories 2', 2, 'posted', '2026-02-02');
SET IDENTITY_INSERT [dbo].[product_categories] OFF;

-- Natural seed rows for [dbo].[suppliers].
SET IDENTITY_INSERT [dbo].[suppliers] ON;
INSERT INTO [dbo].[suppliers] ([id], [name], [code], [contact_person], [phone], [email], [address], [province], [city], [district], [latitude], [longitude], [bank_name], [bank_account], [tax_id], [credit_level], [cooperation_status], [created_at], [updated_at]) VALUES (1, 'Suppliers 1', 'SUPPLIERS-13001', 'Suppliers 1', '13800000001', 'sample1@example.com', 'Suppliers 1', 'province-1', 'city-1', 'district-1', 1.0000, 1.0000, 'bank_name-1', 'bank_account-1', 1, 'credit_level-1', 'cooperation_status-1', '2026-02-01', '2026-02-01');
INSERT INTO [dbo].[suppliers] ([id], [name], [code], [contact_person], [phone], [email], [address], [province], [city], [district], [latitude], [longitude], [bank_name], [bank_account], [tax_id], [credit_level], [cooperation_status], [created_at], [updated_at]) VALUES (2, 'Suppliers 2', 'SUPPLIERS-13002', 'Suppliers 2', '13800000002', 'sample2@example.com', 'Suppliers 2', 'province-2', 'city-2', 'district-2', 1.0000, 1.0000, 'bank_name-2', 'bank_account-2', 2, 'credit_level-2', 'cooperation_status-2', '2026-02-02', '2026-02-02');
SET IDENTITY_INSERT [dbo].[suppliers] OFF;

-- Natural seed rows for [dbo].[products].
SET IDENTITY_INSERT [dbo].[products] ON;
INSERT INTO [dbo].[products] ([id], [sku], [name], [category_id], [unit], [spec], [brand], [barcode], [purchase_price], [wholesale_price], [retail_price], [min_stock], [max_stock], [batch_managed], [shelf_life_days], [weight_kg], [volume_m3], [status], [created_at], [updated_at]) VALUES (1, 'PRODUCTS-14001', 'Products 1', 1, 'Products 1', 'Products 1', 'Products 1', 'barcode-1', 1000.00, 1000.00, 1000.00, 14001, 14001, 1, 6, 1.0000, 1.0000, 'active', '2026-02-01', '2026-02-01');
INSERT INTO [dbo].[products] ([id], [sku], [name], [category_id], [unit], [spec], [brand], [barcode], [purchase_price], [wholesale_price], [retail_price], [min_stock], [max_stock], [batch_managed], [shelf_life_days], [weight_kg], [volume_m3], [status], [created_at], [updated_at]) VALUES (2, 'PRODUCTS-14002', 'Products 2', 2, 'Products 2', 'Products 2', 'Products 2', 'barcode-2', 1800.00, 1800.00, 1800.00, 14002, 14002, 0, 7, 1.0000, 1.0000, 'posted', '2026-02-02', '2026-02-02');
SET IDENTITY_INSERT [dbo].[products] OFF;

-- Natural seed rows for [dbo].[supplier_products].
SET IDENTITY_INSERT [dbo].[supplier_products] ON;
INSERT INTO [dbo].[supplier_products] ([id], [supplier_id], [product_id], [supplier_price], [lead_time_days], [min_order_qty], [shipping_cost_per_km], [return_rate], [quality_score], [is_preferred], [last_order_date], [total_order_count], [total_order_qty]) VALUES (1, 1, 1, 1000.00, 6, 11, 1000.00, 0.0500, 1.0000, 1, '2026-02-01', 15001, 11);
INSERT INTO [dbo].[supplier_products] ([id], [supplier_id], [product_id], [supplier_price], [lead_time_days], [min_order_qty], [shipping_cost_per_km], [return_rate], [quality_score], [is_preferred], [last_order_date], [total_order_count], [total_order_qty]) VALUES (2, 2, 2, 1800.00, 7, 12, 1800.00, 0.0750, 1.0000, 0, '2026-02-02', 15002, 12);
SET IDENTITY_INSERT [dbo].[supplier_products] OFF;

-- Natural seed rows for [dbo].[product_batches].
SET IDENTITY_INSERT [dbo].[product_batches] ON;
INSERT INTO [dbo].[product_batches] ([id], [product_id], [batch_no], [production_date], [expiry_date], [supplier_id], [purchase_price], [initial_qty], [current_qty], [status], [created_at]) VALUES (1, 1, 'PRODUCT_BA-16001', '2026-02-01', '2026-03-31', 1, 1000.00, 11, 11, 'active', '2026-02-01');
INSERT INTO [dbo].[product_batches] ([id], [product_id], [batch_no], [production_date], [expiry_date], [supplier_id], [purchase_price], [initial_qty], [current_qty], [status], [created_at]) VALUES (2, 2, 'PRODUCT_BA-16002', '2026-02-02', '2026-03-31', 2, 1800.00, 12, 12, 'posted', '2026-02-02');
SET IDENTITY_INSERT [dbo].[product_batches] OFF;

-- Natural seed rows for [dbo].[warehouses].
SET IDENTITY_INSERT [dbo].[warehouses] ON;
INSERT INTO [dbo].[warehouses] ([id], [name], [code], [address], [province], [city], [district], [latitude], [longitude], [manager_id], [type], [capacity_m3], [status], [created_at]) VALUES (1, 'Warehouses 1', 'WAREHOUSES-17001', 'Warehouses 1', 'province-1', 'city-1', 'district-1', 1.0000, 1.0000, 1, 'standard', 1.0000, 'active', '2026-02-01');
INSERT INTO [dbo].[warehouses] ([id], [name], [code], [address], [province], [city], [district], [latitude], [longitude], [manager_id], [type], [capacity_m3], [status], [created_at]) VALUES (2, 'Warehouses 2', 'WAREHOUSES-17002', 'Warehouses 2', 'province-2', 'city-2', 'district-2', 1.0000, 1.0000, 2, 'business', 1.0000, 'posted', '2026-02-02');
SET IDENTITY_INSERT [dbo].[warehouses] OFF;

-- Natural seed rows for [dbo].[inventory].
SET IDENTITY_INSERT [dbo].[inventory] ON;
INSERT INTO [dbo].[inventory] ([id], [product_id], [batch_id], [warehouse_id], [shelf_location], [quantity], [locked_quantity], [available_quantity], [last_stocktake_date], [updated_at]) VALUES (1, 1, 1, 1, 'shelf_location-1', 11, 11, 11, '2026-02-01', '2026-02-01');
INSERT INTO [dbo].[inventory] ([id], [product_id], [batch_id], [warehouse_id], [shelf_location], [quantity], [locked_quantity], [available_quantity], [last_stocktake_date], [updated_at]) VALUES (2, 2, 2, 2, 'shelf_location-2', 12, 12, 12, '2026-02-02', '2026-02-02');
SET IDENTITY_INSERT [dbo].[inventory] OFF;

-- Natural seed rows for [dbo].[inventory_transactions].
SET IDENTITY_INSERT [dbo].[inventory_transactions] ON;
INSERT INTO [dbo].[inventory_transactions] ([id], [product_id], [batch_id], [warehouse_id], [transaction_type], [quantity_change], [before_qty], [after_qty], [reference_type], [reference_id], [operator_id], [remark], [created_at]) VALUES (1, 1, 1, 1, 'standard', 11, 11, 11, 'standard', 1, 1, 'remark-1', '2026-02-01');
INSERT INTO [dbo].[inventory_transactions] ([id], [product_id], [batch_id], [warehouse_id], [transaction_type], [quantity_change], [before_qty], [after_qty], [reference_type], [reference_id], [operator_id], [remark], [created_at]) VALUES (2, 2, 2, 2, 'business', 12, 12, 12, 'business', 2, 2, 'remark-2', '2026-02-02');
SET IDENTITY_INSERT [dbo].[inventory_transactions] OFF;

-- Natural seed rows for [dbo].[purchase_requisitions].
SET IDENTITY_INSERT [dbo].[purchase_requisitions] ON;
INSERT INTO [dbo].[purchase_requisitions] ([id], [requisition_no], [department_id], [requester_id], [requisition_date], [required_date], [urgency], [total_amount], [status], [remark], [created_at], [updated_at]) VALUES (1, 'PURCHASE_R-20001', 1, 1, '2026-02-01', '2026-02-01', 'urgency-1', 1000.00, 'active', 'remark-1', '2026-02-01', '2026-02-01');
INSERT INTO [dbo].[purchase_requisitions] ([id], [requisition_no], [department_id], [requester_id], [requisition_date], [required_date], [urgency], [total_amount], [status], [remark], [created_at], [updated_at]) VALUES (2, 'PURCHASE_R-20002', 2, 2, '2026-02-02', '2026-02-02', 'urgency-2', 1800.00, 'posted', 'remark-2', '2026-02-02', '2026-02-02');
SET IDENTITY_INSERT [dbo].[purchase_requisitions] OFF;

-- Natural seed rows for [dbo].[purchase_requisition_items].
SET IDENTITY_INSERT [dbo].[purchase_requisition_items] ON;
INSERT INTO [dbo].[purchase_requisition_items] ([id], [requisition_id], [product_id], [quantity], [estimated_price], [amount], [remark]) VALUES (1, 1, 1, 11, 1000.00, 1000.00, 'remark-1');
INSERT INTO [dbo].[purchase_requisition_items] ([id], [requisition_id], [product_id], [quantity], [estimated_price], [amount], [remark]) VALUES (2, 2, 2, 12, 1800.00, 1800.00, 'remark-2');
SET IDENTITY_INSERT [dbo].[purchase_requisition_items] OFF;

-- Natural seed rows for [dbo].[purchase_orders].
SET IDENTITY_INSERT [dbo].[purchase_orders] ON;
INSERT INTO [dbo].[purchase_orders] ([id], [order_no], [supplier_id], [requisition_id], [department_id], [purchaser_id], [order_date], [expected_delivery_date], [actual_delivery_date], [total_amount], [paid_amount], [payment_terms], [status], [remark], [created_at], [updated_at]) VALUES (1, 'PURCHASE_O-22001', 1, 1, 1, 1, '2026-02-01', '2026-02-01', '2026-02-01', 1000.00, 1000.00, 'payment_terms-1', 'active', 'remark-1', '2026-02-01', '2026-02-01');
INSERT INTO [dbo].[purchase_orders] ([id], [order_no], [supplier_id], [requisition_id], [department_id], [purchaser_id], [order_date], [expected_delivery_date], [actual_delivery_date], [total_amount], [paid_amount], [payment_terms], [status], [remark], [created_at], [updated_at]) VALUES (2, 'PURCHASE_O-22002', 2, 2, 2, 2, '2026-02-02', '2026-02-02', '2026-02-02', 1800.00, 1800.00, 'payment_terms-2', 'posted', 'remark-2', '2026-02-02', '2026-02-02');
SET IDENTITY_INSERT [dbo].[purchase_orders] OFF;

-- Natural seed rows for [dbo].[purchase_order_items].
SET IDENTITY_INSERT [dbo].[purchase_order_items] ON;
INSERT INTO [dbo].[purchase_order_items] ([id], [order_id], [product_id], [quantity], [unit_price], [amount], [received_qty], [returned_qty], [remark]) VALUES (1, 1, 1, 11, 1000.00, 1000.00, 11, 11, 'remark-1');
INSERT INTO [dbo].[purchase_order_items] ([id], [order_id], [product_id], [quantity], [unit_price], [amount], [received_qty], [returned_qty], [remark]) VALUES (2, 2, 2, 12, 1800.00, 1800.00, 12, 12, 'remark-2');
SET IDENTITY_INSERT [dbo].[purchase_order_items] OFF;

-- Natural seed rows for [dbo].[purchase_receipts].
SET IDENTITY_INSERT [dbo].[purchase_receipts] ON;
INSERT INTO [dbo].[purchase_receipts] ([id], [receipt_no], [order_id], [warehouse_id], [receiver_id], [receipt_date], [batch_no], [total_qty], [total_amount], [status], [inspection_result], [remark], [created_at]) VALUES (1, 'PURCHASE_R-24001', 1, 1, 1, '2026-02-01', 'PURCHASE_R-24001', 11, 1000.00, 'active', 'inspection_result-1', 'remark-1', '2026-02-01');
INSERT INTO [dbo].[purchase_receipts] ([id], [receipt_no], [order_id], [warehouse_id], [receiver_id], [receipt_date], [batch_no], [total_qty], [total_amount], [status], [inspection_result], [remark], [created_at]) VALUES (2, 'PURCHASE_R-24002', 2, 2, 2, '2026-02-02', 'PURCHASE_R-24002', 12, 1800.00, 'posted', 'inspection_result-2', 'remark-2', '2026-02-02');
SET IDENTITY_INSERT [dbo].[purchase_receipts] OFF;

-- Natural seed rows for [dbo].[purchase_receipt_items].
SET IDENTITY_INSERT [dbo].[purchase_receipt_items] ON;
INSERT INTO [dbo].[purchase_receipt_items] ([id], [receipt_id], [order_item_id], [product_id], [batch_id], [received_qty], [accepted_qty], [rejected_qty], [unit_price], [production_date], [expiry_date], [remark]) VALUES (1, 1, 1, 1, 1, 11, 11, 11, 1000.00, '2026-02-01', '2026-03-31', 'remark-1');
INSERT INTO [dbo].[purchase_receipt_items] ([id], [receipt_id], [order_item_id], [product_id], [batch_id], [received_qty], [accepted_qty], [rejected_qty], [unit_price], [production_date], [expiry_date], [remark]) VALUES (2, 2, 2, 2, 2, 12, 12, 12, 1800.00, '2026-02-02', '2026-03-31', 'remark-2');
SET IDENTITY_INSERT [dbo].[purchase_receipt_items] OFF;

-- Natural seed rows for [dbo].[customers].
SET IDENTITY_INSERT [dbo].[customers] ON;
INSERT INTO [dbo].[customers] ([id], [code], [name], [type], [contact_person], [phone], [email], [address], [credit_limit], [credit_days], [balance], [membership_level], [status], [created_at], [updated_at]) VALUES (1, 'CUSTOMERS-26001', 'Customers 1', 'standard', 'Customers 1', '13800000001', 'sample1@example.com', 'Customers 1', 1.0000, 6, 1000.00, 'membership_level-1', 'active', '2026-02-01', '2026-02-01');
INSERT INTO [dbo].[customers] ([id], [code], [name], [type], [contact_person], [phone], [email], [address], [credit_limit], [credit_days], [balance], [membership_level], [status], [created_at], [updated_at]) VALUES (2, 'CUSTOMERS-26002', 'Customers 2', 'business', 'Customers 2', '13800000002', 'sample2@example.com', 'Customers 2', 1.0000, 7, 1800.00, 'membership_level-2', 'posted', '2026-02-02', '2026-02-02');
SET IDENTITY_INSERT [dbo].[customers] OFF;

-- Natural seed rows for [dbo].[sales_orders].
SET IDENTITY_INSERT [dbo].[sales_orders] ON;
INSERT INTO [dbo].[sales_orders] ([id], [order_no], [customer_id], [salesperson_id], [warehouse_id], [order_date], [delivery_date], [total_amount], [discount_amount], [paid_amount], [tax_amount], [payment_method], [status], [invoice_no], [remark], [created_at], [updated_at]) VALUES (1, 'SALES_ORDE-27001', 1, 1, 1, '2026-02-01', '2026-02-01', 1000.00, 1000.00, 1000.00, 1000.00, 'payment_method-1', 'active', 'SALES_ORDE-27001', 'remark-1', '2026-02-01', '2026-02-01');
INSERT INTO [dbo].[sales_orders] ([id], [order_no], [customer_id], [salesperson_id], [warehouse_id], [order_date], [delivery_date], [total_amount], [discount_amount], [paid_amount], [tax_amount], [payment_method], [status], [invoice_no], [remark], [created_at], [updated_at]) VALUES (2, 'SALES_ORDE-27002', 2, 2, 2, '2026-02-02', '2026-02-02', 1800.00, 1800.00, 1800.00, 1800.00, 'payment_method-2', 'posted', 'SALES_ORDE-27002', 'remark-2', '2026-02-02', '2026-02-02');
SET IDENTITY_INSERT [dbo].[sales_orders] OFF;

-- Natural seed rows for [dbo].[sales_order_items].
SET IDENTITY_INSERT [dbo].[sales_order_items] ON;
INSERT INTO [dbo].[sales_order_items] ([id], [order_id], [product_id], [batch_id], [quantity], [unit_price], [discount], [amount], [returned_qty]) VALUES (1, 1, 1, 1, 11, 1000.00, 1.0000, 1000.00, 11);
INSERT INTO [dbo].[sales_order_items] ([id], [order_id], [product_id], [batch_id], [quantity], [unit_price], [discount], [amount], [returned_qty]) VALUES (2, 2, 2, 2, 12, 1800.00, 1.0000, 1800.00, 12);
SET IDENTITY_INSERT [dbo].[sales_order_items] OFF;

-- Natural seed rows for [dbo].[sales_returns].
SET IDENTITY_INSERT [dbo].[sales_returns] ON;
INSERT INTO [dbo].[sales_returns] ([id], [return_no], [order_id], [customer_id], [warehouse_id], [handler_id], [return_date], [return_reason], [return_type], [total_amount], [refund_amount], [restock_fee], [status], [approved_by], [approved_at], [refund_voucher_id], [return_shipping_fee], [remark], [created_at], [updated_at]) VALUES (1, 'SALES_RETU-29001', 1, 1, 1, 1, '2026-02-01', 'return_reason-1', 'return_type-1', 1000.00, 1000.00, 1.0000, 'active', 1, '2026-02-01', 1, 1.0000, 'remark-1', '2026-02-01', '2026-02-01');
INSERT INTO [dbo].[sales_returns] ([id], [return_no], [order_id], [customer_id], [warehouse_id], [handler_id], [return_date], [return_reason], [return_type], [total_amount], [refund_amount], [restock_fee], [status], [approved_by], [approved_at], [refund_voucher_id], [return_shipping_fee], [remark], [created_at], [updated_at]) VALUES (2, 'SALES_RETU-29002', 2, 2, 2, 2, '2026-02-02', 'return_reason-2', 'return_type-2', 1800.00, 1800.00, 1.0000, 'posted', 2, '2026-02-02', 2, 1.0000, 'remark-2', '2026-02-02', '2026-02-02');
SET IDENTITY_INSERT [dbo].[sales_returns] OFF;

-- Natural seed rows for [dbo].[sales_return_items].
SET IDENTITY_INSERT [dbo].[sales_return_items] ON;
INSERT INTO [dbo].[sales_return_items] ([id], [return_id], [order_item_id], [product_id], [batch_id], [return_qty], [unit_price], [amount], [status]) VALUES (1, 1, 1, 1, 1, 11, 1000.00, 1000.00, 'active');
INSERT INTO [dbo].[sales_return_items] ([id], [return_id], [order_item_id], [product_id], [batch_id], [return_qty], [unit_price], [amount], [status]) VALUES (2, 2, 2, 2, 2, 12, 1800.00, 1800.00, 'posted');
SET IDENTITY_INSERT [dbo].[sales_return_items] OFF;

-- Natural seed rows for [dbo].[purchase_returns].
SET IDENTITY_INSERT [dbo].[purchase_returns] ON;
INSERT INTO [dbo].[purchase_returns] ([id], [return_no], [purchase_order_id], [purchase_receipt_id], [supplier_id], [warehouse_id], [handler_id], [return_date], [return_reason], [return_type], [total_amount], [refund_received], [shipping_fee], [status], [approved_by], [approved_at], [refund_voucher_id], [remark], [created_at], [updated_at]) VALUES (1, 'PURCHASE_R-31001', 1, 1, 1, 1, 1, '2026-02-01', 'return_reason-1', 'return_type-1', 1000.00, 1.0000, 1.0000, 'active', 1, '2026-02-01', 1, 'remark-1', '2026-02-01', '2026-02-01');
INSERT INTO [dbo].[purchase_returns] ([id], [return_no], [purchase_order_id], [purchase_receipt_id], [supplier_id], [warehouse_id], [handler_id], [return_date], [return_reason], [return_type], [total_amount], [refund_received], [shipping_fee], [status], [approved_by], [approved_at], [refund_voucher_id], [remark], [created_at], [updated_at]) VALUES (2, 'PURCHASE_R-31002', 2, 2, 2, 2, 2, '2026-02-02', 'return_reason-2', 'return_type-2', 1800.00, 1.0000, 1.0000, 'posted', 2, '2026-02-02', 2, 'remark-2', '2026-02-02', '2026-02-02');
SET IDENTITY_INSERT [dbo].[purchase_returns] OFF;

-- Natural seed rows for [dbo].[purchase_return_items].
SET IDENTITY_INSERT [dbo].[purchase_return_items] ON;
INSERT INTO [dbo].[purchase_return_items] ([id], [return_id], [product_id], [batch_id], [return_qty], [unit_price], [amount], [reason]) VALUES (1, 1, 1, 1, 11, 1000.00, 1000.00, 'Purchase Return Items 1');
INSERT INTO [dbo].[purchase_return_items] ([id], [return_id], [product_id], [batch_id], [return_qty], [unit_price], [amount], [reason]) VALUES (2, 2, 2, 2, 12, 1800.00, 1800.00, 'Purchase Return Items 2');
SET IDENTITY_INSERT [dbo].[purchase_return_items] OFF;

-- Natural seed rows for [dbo].[damage_reports].
SET IDENTITY_INSERT [dbo].[damage_reports] ON;
INSERT INTO [dbo].[damage_reports] ([id], [report_no], [warehouse_id], [report_type], [report_date], [reported_by], [total_quantity], [total_loss_amount], [status], [approved_by], [approved_at], [executed_by], [executed_at], [voucher_id], [description], [remark], [created_at], [updated_at]) VALUES (1, 'DAMAGE_REP-33001', 1, 'report_type-1', '2026-02-01', 33001, 11, 1000.00, 'active', 1, '2026-02-01', 33001, '2026-02-01', 1, 'Damage Reports 1', 'remark-1', '2026-02-01', '2026-02-01');
INSERT INTO [dbo].[damage_reports] ([id], [report_no], [warehouse_id], [report_type], [report_date], [reported_by], [total_quantity], [total_loss_amount], [status], [approved_by], [approved_at], [executed_by], [executed_at], [voucher_id], [description], [remark], [created_at], [updated_at]) VALUES (2, 'DAMAGE_REP-33002', 2, 'report_type-2', '2026-02-02', 33002, 12, 1800.00, 'posted', 2, '2026-02-02', 33002, '2026-02-02', 2, 'Damage Reports 2', 'remark-2', '2026-02-02', '2026-02-02');
SET IDENTITY_INSERT [dbo].[damage_reports] OFF;

-- Natural seed rows for [dbo].[damage_report_items].
SET IDENTITY_INSERT [dbo].[damage_report_items] ON;
INSERT INTO [dbo].[damage_report_items] ([id], [report_id], [product_id], [batch_id], [quantity], [unit_cost], [loss_amount], [reason]) VALUES (1, 1, 1, 1, 11, 1000.00, 1000.00, 'Damage Report Items 1');
INSERT INTO [dbo].[damage_report_items] ([id], [report_id], [product_id], [batch_id], [quantity], [unit_cost], [loss_amount], [reason]) VALUES (2, 2, 2, 2, 12, 1800.00, 1800.00, 'Damage Report Items 2');
SET IDENTITY_INSERT [dbo].[damage_report_items] OFF;

-- Natural seed rows for [dbo].[accounts].
SET IDENTITY_INSERT [dbo].[accounts] ON;
INSERT INTO [dbo].[accounts] ([id], [code], [parent_id], [name], [account_type], [balance_direction], [is_cash], [is_bank], [bank_name], [bank_account], [current_balance], [status], [created_at]) VALUES (1, 'ACCOUNTS-35001', 1, 'Accounts 1', 'account_type-1', 'balance_direction-1', 1, 1, 'bank_name-1', 'bank_account-1', 1000.00, 'active', '2026-02-01');
INSERT INTO [dbo].[accounts] ([id], [code], [parent_id], [name], [account_type], [balance_direction], [is_cash], [is_bank], [bank_name], [bank_account], [current_balance], [status], [created_at]) VALUES (2, 'ACCOUNTS-35002', 2, 'Accounts 2', 'account_type-2', 'balance_direction-2', 0, 0, 'bank_name-2', 'bank_account-2', 1800.00, 'posted', '2026-02-02');
SET IDENTITY_INSERT [dbo].[accounts] OFF;

-- Natural seed rows for [dbo].[vouchers].
SET IDENTITY_INSERT [dbo].[vouchers] ON;
INSERT INTO [dbo].[vouchers] ([id], [voucher_no], [voucher_date], [voucher_type], [reference_type], [reference_id], [total_debit], [total_credit], [prepared_by], [reviewed_by], [posted_by], [status], [summary], [created_at], [updated_at]) VALUES (1, 'VOUCHERS-36001', '2026-02-01', 'voucher_type-1', 'standard', 1, 1.0000, 1.0000, 1, 36001, 36001, 'active', 'summary-1', '2026-02-01', '2026-02-01');
INSERT INTO [dbo].[vouchers] ([id], [voucher_no], [voucher_date], [voucher_type], [reference_type], [reference_id], [total_debit], [total_credit], [prepared_by], [reviewed_by], [posted_by], [status], [summary], [created_at], [updated_at]) VALUES (2, 'VOUCHERS-36002', '2026-02-02', 'voucher_type-2', 'business', 2, 1.0000, 1.0000, 2, 36002, 36002, 'posted', 'summary-2', '2026-02-02', '2026-02-02');
SET IDENTITY_INSERT [dbo].[vouchers] OFF;

-- Natural seed rows for [dbo].[voucher_items].
SET IDENTITY_INSERT [dbo].[voucher_items] ON;
INSERT INTO [dbo].[voucher_items] ([id], [voucher_id], [account_id], [line_no], [direction], [amount], [summary]) VALUES (1, 1, 1, 37001, 'direction-1', 1000.00, 'summary-1');
INSERT INTO [dbo].[voucher_items] ([id], [voucher_id], [account_id], [line_no], [direction], [amount], [summary]) VALUES (2, 2, 2, 37002, 'direction-2', 1800.00, 'summary-2');
SET IDENTITY_INSERT [dbo].[voucher_items] OFF;

-- Natural seed rows for [dbo].[cashier_journals].
SET IDENTITY_INSERT [dbo].[cashier_journals] ON;
INSERT INTO [dbo].[cashier_journals] ([id], [journal_no], [journal_date], [account_id], [cashier_id], [journal_type], [amount], [counterparty], [reference_type], [reference_id], [voucher_id], [bank_account], [check_no], [status], [remark], [created_at]) VALUES (1, 'CASHIER_JO-38001', '2026-02-01', 1, 1, 'journal_type-1', 1000.00, 'counterparty-1', 'standard', 1, 1, 'bank_account-1', 'CASHIER_JO-38001', 'active', 'remark-1', '2026-02-01');
INSERT INTO [dbo].[cashier_journals] ([id], [journal_no], [journal_date], [account_id], [cashier_id], [journal_type], [amount], [counterparty], [reference_type], [reference_id], [voucher_id], [bank_account], [check_no], [status], [remark], [created_at]) VALUES (2, 'CASHIER_JO-38002', '2026-02-02', 2, 2, 'journal_type-2', 1800.00, 'counterparty-2', 'business', 2, 2, 'bank_account-2', 'CASHIER_JO-38002', 'posted', 'remark-2', '2026-02-02');
SET IDENTITY_INSERT [dbo].[cashier_journals] OFF;

-- Natural seed rows for [dbo].[salary_payments].
SET IDENTITY_INSERT [dbo].[salary_payments] ON;
INSERT INTO [dbo].[salary_payments] ([id], [payment_no], [employee_id], [payment_date], [salary_month], [base_salary], [overtime_pay], [bonus], [deduction], [social_security_personal], [housing_fund_personal], [income_tax], [net_pay], [social_security_company], [housing_fund_company], [payment_method], [status], [paid_at], [voucher_id], [remark], [created_at]) VALUES (1, 'SALARY_PAY-39001', 1, '2026-02-01', 'salary_month-1', 1.0000, 1.0000, 1.0000, 1.0000, 1.0000, 1.0000, 1.0000, 1.0000, 1.0000, 1.0000, 'payment_method-1', 'active', '2026-02-01', 1, 'remark-1', '2026-02-01');
INSERT INTO [dbo].[salary_payments] ([id], [payment_no], [employee_id], [payment_date], [salary_month], [base_salary], [overtime_pay], [bonus], [deduction], [social_security_personal], [housing_fund_personal], [income_tax], [net_pay], [social_security_company], [housing_fund_company], [payment_method], [status], [paid_at], [voucher_id], [remark], [created_at]) VALUES (2, 'SALARY_PAY-39002', 2, '2026-02-02', 'salary_month-2', 1.0000, 1.0000, 1.0000, 1.0000, 1.0000, 1.0000, 1.0000, 1.0000, 1.0000, 1.0000, 'payment_method-2', 'posted', '2026-02-02', 2, 'remark-2', '2026-02-02');
SET IDENTITY_INSERT [dbo].[salary_payments] OFF;

-- Natural seed rows for [dbo].[reconciliations].
SET IDENTITY_INSERT [dbo].[reconciliations] ON;
INSERT INTO [dbo].[reconciliations] ([id], [recon_no], [account_id], [recon_date], [period_start], [period_end], [book_balance], [bank_balance], [difference], [unreconciled_income], [unreconciled_expense], [adjusted_balance], [prepared_by], [reviewed_by], [status], [remark], [created_at], [updated_at]) VALUES (1, 'RECONCILIA-40001', 1, '2026-02-01', '2026-02', '2026-03-31', 1000.00, 1000.00, 1.0000, 1.0000, 1.0000, 1000.00, 1, 40001, 'active', 'remark-1', '2026-02-01', '2026-02-01');
INSERT INTO [dbo].[reconciliations] ([id], [recon_no], [account_id], [recon_date], [period_start], [period_end], [book_balance], [bank_balance], [difference], [unreconciled_income], [unreconciled_expense], [adjusted_balance], [prepared_by], [reviewed_by], [status], [remark], [created_at], [updated_at]) VALUES (2, 'RECONCILIA-40002', 2, '2026-02-02', '2026-02', '2026-03-31', 1800.00, 1800.00, 1.0000, 1.0000, 1.0000, 1800.00, 2, 40002, 'posted', 'remark-2', '2026-02-02', '2026-02-02');
SET IDENTITY_INSERT [dbo].[reconciliations] OFF;
