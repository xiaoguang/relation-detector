-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

CREATE TABLE [dbo].[departments] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [parent_id] BIGINT NULL,
    [name] NVARCHAR(100) NOT NULL,
    [code] NVARCHAR(20) NOT NULL,
    [manager_id] BIGINT NULL,
    [budget] DECIMAL(18,2) NULL,
    [headcount_plan] INT NULL,
    [status] NVARCHAR(50) NULL,
    [created_at] DATETIME2 NULL,
    [updated_at] DATETIME2 NULL,
    CONSTRAINT [pk_departments] PRIMARY KEY ([id]),
    CONSTRAINT [fk_dept_parent] FOREIGN KEY ([parent_id]) REFERENCES [dbo].[departments] ([id])
);

CREATE TABLE [dbo].[positions] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [department_id] BIGINT NOT NULL,
    [name] NVARCHAR(100) NOT NULL,
    [code] NVARCHAR(20) NOT NULL,
    [level] INT NULL,
    [min_salary] DECIMAL(12,2) NULL,
    [max_salary] DECIMAL(12,2) NULL,
    [headcount] INT NULL,
    [status] NVARCHAR(50) NULL,
    [created_at] DATETIME2 NULL,
    CONSTRAINT [pk_positions] PRIMARY KEY ([id]),
    CONSTRAINT [uk_dept_code] UNIQUE ([department_id], [code]),
    CONSTRAINT [fk_pos_dept] FOREIGN KEY ([department_id]) REFERENCES [dbo].[departments] ([id])
);

CREATE TABLE [dbo].[employees] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [employee_no] NVARCHAR(20) NOT NULL,
    [name] NVARCHAR(50) NOT NULL,
    [gender] NVARCHAR(50) NOT NULL,
    [id_card] NVARCHAR(18) NOT NULL,
    [phone] NVARCHAR(20) NOT NULL,
    [email] NVARCHAR(100) NULL,
    [birth_date] DATE NOT NULL,
    [hire_date] DATE NOT NULL,
    [department_id] BIGINT NOT NULL,
    [position_id] BIGINT NOT NULL,
    [manager_id] BIGINT NULL,
    [salary] DECIMAL(12,2) NOT NULL,
    [social_security_base] DECIMAL(12,2) NULL,
    [housing_fund_base] DECIMAL(12,2) NULL,
    [bank_name] NVARCHAR(100) NULL,
    [bank_account] NVARCHAR(50) NULL,
    [status] NVARCHAR(50) NULL,
    [resignation_date] DATE NULL,
    [resignation_reason] NVARCHAR(500) NULL,
    [address] NVARCHAR(200) NULL,
    [emergency_contact] NVARCHAR(50) NULL,
    [emergency_phone] NVARCHAR(20) NULL,
    [created_at] DATETIME2 NULL,
    [updated_at] DATETIME2 NULL,
    CONSTRAINT [pk_employees] PRIMARY KEY ([id]),
    CONSTRAINT [fk_emp_dept] FOREIGN KEY ([department_id]) REFERENCES [dbo].[departments] ([id]),
    CONSTRAINT [fk_emp_pos] FOREIGN KEY ([position_id]) REFERENCES [dbo].[positions] ([id]),
    CONSTRAINT [fk_emp_manager] FOREIGN KEY ([manager_id]) REFERENCES [dbo].[employees] ([id])
);

ALTER TABLE [dbo].[departments]
ADD CONSTRAINT [fk_dept_manager]
FOREIGN KEY ([manager_id]) REFERENCES [dbo].[employees] ([id]) ON DELETE SET NULL;

CREATE TABLE [dbo].[employee_salary_log] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [employee_id] BIGINT NOT NULL,
    [old_salary] DECIMAL(12,2) NOT NULL,
    [new_salary] DECIMAL(12,2) NOT NULL,
    [change_reason] NVARCHAR(200) NOT NULL,
    [effective_date] DATE NOT NULL,
    [approved_by] BIGINT NULL,
    [created_at] DATETIME2 NULL,
    CONSTRAINT [pk_employee_salary_log] PRIMARY KEY ([id]),
    CONSTRAINT [fk_sal_emp] FOREIGN KEY ([employee_id]) REFERENCES [dbo].[employees] ([id])
);

CREATE TABLE [dbo].[attendance] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [employee_id] BIGINT NOT NULL,
    [attendance_date] DATE NOT NULL,
    [clock_in] DATETIME2 NULL,
    [clock_out] DATETIME2 NULL,
    [status] NVARCHAR(50) NULL,
    [late_minutes] INT NULL,
    [early_minutes] INT NULL,
    [overtime_hours] DECIMAL(4,1) NULL,
    [remark] NVARCHAR(200) NULL,
    CONSTRAINT [pk_attendance] PRIMARY KEY ([id]),
    CONSTRAINT [uk_emp_date] UNIQUE ([employee_id], [attendance_date]),
    CONSTRAINT [fk_att_emp] FOREIGN KEY ([employee_id]) REFERENCES [dbo].[employees] ([id])
);

CREATE TABLE [dbo].[leave_records] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [employee_id] BIGINT NOT NULL,
    [leave_type] NVARCHAR(50) NOT NULL,
    [start_date] DATE NOT NULL,
    [end_date] DATE NOT NULL,
    [days] DECIMAL(4,1) NOT NULL,
    [reason] NVARCHAR(500) NULL,
    [status] NVARCHAR(50) NULL,
    [approved_by] BIGINT NULL,
    [approved_at] DATETIME2 NULL,
    [created_at] DATETIME2 NULL,
    CONSTRAINT [pk_leave_records] PRIMARY KEY ([id]),
    CONSTRAINT [fk_leave_emp] FOREIGN KEY ([employee_id]) REFERENCES [dbo].[employees] ([id])
);

CREATE TABLE [dbo].[roles] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [name] NVARCHAR(50) NOT NULL,
    [code] NVARCHAR(30) NOT NULL,
    [description] NVARCHAR(200) NULL,
    [is_system] BIT NULL,
    [created_at] DATETIME2 NULL,
    CONSTRAINT [pk_roles] PRIMARY KEY ([id])
);

CREATE TABLE [dbo].[permissions] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [parent_id] BIGINT NULL,
    [name] NVARCHAR(100) NOT NULL,
    [code] NVARCHAR(50) NOT NULL,
    [resource_type] NVARCHAR(50) NOT NULL,
    [resource_path] NVARCHAR(200) NULL,
    [action] NVARCHAR(50) NOT NULL,
    [created_at] DATETIME2 NULL,
    CONSTRAINT [pk_permissions] PRIMARY KEY ([id]),
    CONSTRAINT [fk_perm_parent] FOREIGN KEY ([parent_id]) REFERENCES [dbo].[permissions] ([id])
);

CREATE TABLE [dbo].[role_permissions] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [role_id] BIGINT NOT NULL,
    [permission_id] BIGINT NOT NULL,
    CONSTRAINT [pk_role_permissions] PRIMARY KEY ([id]),
    CONSTRAINT [uk_role_perm] UNIQUE ([role_id], [permission_id]),
    CONSTRAINT [fk_rp_role] FOREIGN KEY ([role_id]) REFERENCES [dbo].[roles] ([id]),
    CONSTRAINT [fk_rp_perm] FOREIGN KEY ([permission_id]) REFERENCES [dbo].[permissions] ([id])
);

CREATE TABLE [dbo].[employee_roles] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [employee_id] BIGINT NOT NULL,
    [role_id] BIGINT NOT NULL,
    [granted_by] BIGINT NULL,
    [granted_at] DATETIME2 NULL,
    [expires_at] DATETIME2 NULL,
    CONSTRAINT [pk_employee_roles] PRIMARY KEY ([id]),
    CONSTRAINT [uk_emp_role] UNIQUE ([employee_id], [role_id]),
    CONSTRAINT [fk_er_emp] FOREIGN KEY ([employee_id]) REFERENCES [dbo].[employees] ([id]),
    CONSTRAINT [fk_er_role] FOREIGN KEY ([role_id]) REFERENCES [dbo].[roles] ([id])
);

CREATE TABLE [dbo].[audit_log] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [employee_id] BIGINT NULL,
    [action] NVARCHAR(100) NOT NULL,
    [target_type] NVARCHAR(50) NOT NULL,
    [target_id] BIGINT NULL,
    [old_value] NVARCHAR(MAX) NULL,
    [new_value] NVARCHAR(MAX) NULL,
    [ip_address] NVARCHAR(45) NULL,
    [user_agent] NVARCHAR(500) NULL,
    [remark] NVARCHAR(500) NULL,
    [created_at] DATETIME2 NULL,
    CONSTRAINT [pk_audit_log] PRIMARY KEY ([id])
);

CREATE TABLE [dbo].[product_categories] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [parent_id] BIGINT NULL,
    [name] NVARCHAR(100) NOT NULL,
    [code] NVARCHAR(20) NOT NULL,
    [description] NVARCHAR(500) NULL,
    [sort_order] INT NULL,
    [status] NVARCHAR(50) NULL,
    [created_at] DATETIME2 NULL,
    CONSTRAINT [pk_product_categories] PRIMARY KEY ([id]),
    CONSTRAINT [fk_cat_parent] FOREIGN KEY ([parent_id]) REFERENCES [dbo].[product_categories] ([id])
);

CREATE TABLE [dbo].[suppliers] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [name] NVARCHAR(200) NOT NULL,
    [code] NVARCHAR(20) NOT NULL,
    [contact_person] NVARCHAR(50) NULL,
    [phone] NVARCHAR(20) NULL,
    [email] NVARCHAR(100) NULL,
    [address] NVARCHAR(300) NULL,
    [province] NVARCHAR(50) NULL,
    [city] NVARCHAR(50) NULL,
    [district] NVARCHAR(50) NULL,
    [latitude] DECIMAL(10,7) NULL,
    [longitude] DECIMAL(10,7) NULL,
    [bank_name] NVARCHAR(100) NULL,
    [bank_account] NVARCHAR(50) NULL,
    [tax_id] NVARCHAR(50) NULL,
    [credit_level] NVARCHAR(50) NULL,
    [cooperation_status] NVARCHAR(50) NULL,
    [created_at] DATETIME2 NULL,
    [updated_at] DATETIME2 NULL,
    CONSTRAINT [pk_suppliers] PRIMARY KEY ([id])
);

CREATE TABLE [dbo].[products] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [sku] NVARCHAR(50) NOT NULL,
    [name] NVARCHAR(200) NOT NULL,
    [category_id] BIGINT NOT NULL,
    [unit] NVARCHAR(20) NOT NULL,
    [spec] NVARCHAR(100) NULL,
    [brand] NVARCHAR(100) NULL,
    [barcode] NVARCHAR(50) NULL,
    [purchase_price] DECIMAL(12,2) NULL,
    [wholesale_price] DECIMAL(12,2) NULL,
    [retail_price] DECIMAL(12,2) NULL,
    [min_stock] INT NULL,
    [max_stock] INT NULL,
    [batch_managed] BIT NULL,
    [shelf_life_days] INT NULL,
    [weight_kg] DECIMAL(8,3) NULL,
    [volume_m3] DECIMAL(8,6) NULL,
    [status] NVARCHAR(50) NULL,
    [created_at] DATETIME2 NULL,
    [updated_at] DATETIME2 NULL,
    CONSTRAINT [pk_products] PRIMARY KEY ([id]),
    CONSTRAINT [fk_prod_cat] FOREIGN KEY ([category_id]) REFERENCES [dbo].[product_categories] ([id])
);

CREATE TABLE [dbo].[supplier_products] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [supplier_id] BIGINT NOT NULL,
    [product_id] BIGINT NOT NULL,
    [supplier_price] DECIMAL(12,2) NULL,
    [lead_time_days] INT NULL,
    [min_order_qty] INT NULL,
    [shipping_cost_per_km] DECIMAL(8,4) NULL,
    [return_rate] DECIMAL(5,4) NULL,
    [quality_score] DECIMAL(5,2) NULL,
    [is_preferred] BIT NULL,
    [last_order_date] DATE NULL,
    [total_order_count] INT NULL,
    [total_order_qty] INT NULL,
    CONSTRAINT [pk_supplier_products] PRIMARY KEY ([id]),
    CONSTRAINT [uk_supplier_product] UNIQUE ([supplier_id], [product_id]),
    CONSTRAINT [fk_sp_supplier] FOREIGN KEY ([supplier_id]) REFERENCES [dbo].[suppliers] ([id]),
    CONSTRAINT [fk_sp_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[products] ([id])
);

CREATE TABLE [dbo].[product_batches] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [product_id] BIGINT NOT NULL,
    [batch_no] NVARCHAR(50) NOT NULL,
    [production_date] DATE NULL,
    [expiry_date] DATE NULL,
    [supplier_id] BIGINT NULL,
    [purchase_price] DECIMAL(12,2) NULL,
    [initial_qty] INT NOT NULL,
    [current_qty] INT NOT NULL,
    [status] NVARCHAR(50) NULL,
    [created_at] DATETIME2 NULL,
    CONSTRAINT [pk_product_batches] PRIMARY KEY ([id]),
    CONSTRAINT [uk_product_batch] UNIQUE ([product_id], [batch_no]),
    CONSTRAINT [fk_batch_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[products] ([id]),
    CONSTRAINT [fk_batch_supplier] FOREIGN KEY ([supplier_id]) REFERENCES [dbo].[suppliers] ([id])
);

CREATE TABLE [dbo].[warehouses] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [name] NVARCHAR(100) NOT NULL,
    [code] NVARCHAR(20) NOT NULL,
    [address] NVARCHAR(200) NULL,
    [province] NVARCHAR(50) NULL,
    [city] NVARCHAR(50) NULL,
    [district] NVARCHAR(50) NULL,
    [latitude] DECIMAL(10,7) NULL,
    [longitude] DECIMAL(10,7) NULL,
    [manager_id] BIGINT NULL,
    [type] NVARCHAR(50) NULL,
    [capacity_m3] DECIMAL(10,3) NULL,
    [status] NVARCHAR(50) NULL,
    [created_at] DATETIME2 NULL,
    CONSTRAINT [pk_warehouses] PRIMARY KEY ([id]),
    CONSTRAINT [fk_wh_manager] FOREIGN KEY ([manager_id]) REFERENCES [dbo].[employees] ([id])
);

CREATE TABLE [dbo].[inventory] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [product_id] BIGINT NOT NULL,
    [batch_id] BIGINT NULL,
    [warehouse_id] BIGINT NOT NULL,
    [shelf_location] NVARCHAR(50) NULL,
    [quantity] INT NOT NULL,
    [locked_quantity] INT NULL,
    [available_quantity] INT NULL,
    [last_stocktake_date] DATE NULL,
    [updated_at] DATETIME2 NULL,
    CONSTRAINT [pk_inventory] PRIMARY KEY ([id]),
    CONSTRAINT [uk_product_batch_wh] UNIQUE ([product_id], [batch_id], [warehouse_id]),
    CONSTRAINT [fk_inv_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[products] ([id]),
    CONSTRAINT [fk_inv_batch] FOREIGN KEY ([batch_id]) REFERENCES [dbo].[product_batches] ([id]),
    CONSTRAINT [fk_inv_wh] FOREIGN KEY ([warehouse_id]) REFERENCES [dbo].[warehouses] ([id])
);

CREATE TABLE [dbo].[inventory_transactions] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [product_id] BIGINT NOT NULL,
    [batch_id] BIGINT NULL,
    [warehouse_id] BIGINT NOT NULL,
    [transaction_type] NVARCHAR(50) NOT NULL,
    [quantity_change] INT NOT NULL,
    [before_qty] INT NOT NULL,
    [after_qty] INT NOT NULL,
    [reference_type] NVARCHAR(50) NULL,
    [reference_id] BIGINT NULL,
    [operator_id] BIGINT NULL,
    [remark] NVARCHAR(500) NULL,
    [created_at] DATETIME2 NULL,
    CONSTRAINT [pk_inventory_transactions] PRIMARY KEY ([id]),
    CONSTRAINT [fk_it_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[products] ([id]),
    CONSTRAINT [fk_it_batch] FOREIGN KEY ([batch_id]) REFERENCES [dbo].[product_batches] ([id]),
    CONSTRAINT [fk_it_wh] FOREIGN KEY ([warehouse_id]) REFERENCES [dbo].[warehouses] ([id])
);

CREATE TABLE [dbo].[purchase_requisitions] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [requisition_no] NVARCHAR(30) NOT NULL,
    [department_id] BIGINT NOT NULL,
    [requester_id] BIGINT NOT NULL,
    [requisition_date] DATE NOT NULL,
    [required_date] DATE NULL,
    [urgency] NVARCHAR(50) NULL,
    [total_amount] DECIMAL(18,2) NULL,
    [status] NVARCHAR(50) NULL,
    [remark] NVARCHAR(500) NULL,
    [created_at] DATETIME2 NULL,
    [updated_at] DATETIME2 NULL,
    CONSTRAINT [pk_purchase_requisitions] PRIMARY KEY ([id]),
    CONSTRAINT [fk_pr_dept] FOREIGN KEY ([department_id]) REFERENCES [dbo].[departments] ([id]),
    CONSTRAINT [fk_pr_requester] FOREIGN KEY ([requester_id]) REFERENCES [dbo].[employees] ([id])
);

CREATE TABLE [dbo].[purchase_requisition_items] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [requisition_id] BIGINT NOT NULL,
    [product_id] BIGINT NOT NULL,
    [quantity] INT NOT NULL,
    [estimated_price] DECIMAL(12,2) NULL,
    [amount] DECIMAL(18,2) NULL,
    [remark] NVARCHAR(200) NULL,
    CONSTRAINT [pk_purchase_requisition_items] PRIMARY KEY ([id]),
    CONSTRAINT [fk_pri_req] FOREIGN KEY ([requisition_id]) REFERENCES [dbo].[purchase_requisitions] ([id]),
    CONSTRAINT [fk_pri_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[products] ([id])
);

CREATE TABLE [dbo].[purchase_orders] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [order_no] NVARCHAR(30) NOT NULL,
    [supplier_id] BIGINT NOT NULL,
    [requisition_id] BIGINT NULL,
    [department_id] BIGINT NOT NULL,
    [purchaser_id] BIGINT NOT NULL,
    [order_date] DATE NOT NULL,
    [expected_delivery_date] DATE NULL,
    [actual_delivery_date] DATE NULL,
    [total_amount] DECIMAL(18,2) NULL,
    [paid_amount] DECIMAL(18,2) NULL,
    [payment_terms] NVARCHAR(100) NULL,
    [status] NVARCHAR(50) NULL,
    [remark] NVARCHAR(500) NULL,
    [created_at] DATETIME2 NULL,
    [updated_at] DATETIME2 NULL,
    CONSTRAINT [pk_purchase_orders] PRIMARY KEY ([id]),
    CONSTRAINT [fk_po_supplier] FOREIGN KEY ([supplier_id]) REFERENCES [dbo].[suppliers] ([id]),
    CONSTRAINT [fk_po_req] FOREIGN KEY ([requisition_id]) REFERENCES [dbo].[purchase_requisitions] ([id]),
    CONSTRAINT [fk_po_purchaser] FOREIGN KEY ([purchaser_id]) REFERENCES [dbo].[employees] ([id])
);

CREATE TABLE [dbo].[purchase_order_items] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [order_id] BIGINT NOT NULL,
    [product_id] BIGINT NOT NULL,
    [quantity] INT NOT NULL,
    [unit_price] DECIMAL(12,2) NOT NULL,
    [amount] DECIMAL(18,2) NULL,
    [received_qty] INT NULL,
    [returned_qty] INT NULL,
    [remark] NVARCHAR(200) NULL,
    CONSTRAINT [pk_purchase_order_items] PRIMARY KEY ([id]),
    CONSTRAINT [fk_poi_order] FOREIGN KEY ([order_id]) REFERENCES [dbo].[purchase_orders] ([id]),
    CONSTRAINT [fk_poi_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[products] ([id])
);

CREATE TABLE [dbo].[purchase_receipts] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [receipt_no] NVARCHAR(30) NOT NULL,
    [order_id] BIGINT NOT NULL,
    [warehouse_id] BIGINT NOT NULL,
    [receiver_id] BIGINT NOT NULL,
    [receipt_date] DATE NOT NULL,
    [batch_no] NVARCHAR(50) NULL,
    [total_qty] INT NULL,
    [total_amount] DECIMAL(18,2) NULL,
    [status] NVARCHAR(50) NULL,
    [inspection_result] NVARCHAR(500) NULL,
    [remark] NVARCHAR(500) NULL,
    [created_at] DATETIME2 NULL,
    CONSTRAINT [pk_purchase_receipts] PRIMARY KEY ([id]),
    CONSTRAINT [fk_prec_order] FOREIGN KEY ([order_id]) REFERENCES [dbo].[purchase_orders] ([id]),
    CONSTRAINT [fk_prec_wh] FOREIGN KEY ([warehouse_id]) REFERENCES [dbo].[warehouses] ([id]),
    CONSTRAINT [fk_prec_receiver] FOREIGN KEY ([receiver_id]) REFERENCES [dbo].[employees] ([id])
);

CREATE TABLE [dbo].[purchase_receipt_items] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [receipt_id] BIGINT NOT NULL,
    [order_item_id] BIGINT NOT NULL,
    [product_id] BIGINT NOT NULL,
    [batch_id] BIGINT NULL,
    [received_qty] INT NOT NULL,
    [accepted_qty] INT NULL,
    [rejected_qty] INT NULL,
    [unit_price] DECIMAL(12,2) NOT NULL,
    [production_date] DATE NULL,
    [expiry_date] DATE NULL,
    [remark] NVARCHAR(200) NULL,
    CONSTRAINT [pk_purchase_receipt_items] PRIMARY KEY ([id]),
    CONSTRAINT [fk_preci_receipt] FOREIGN KEY ([receipt_id]) REFERENCES [dbo].[purchase_receipts] ([id]),
    CONSTRAINT [fk_preci_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[products] ([id]),
    CONSTRAINT [fk_preci_batch] FOREIGN KEY ([batch_id]) REFERENCES [dbo].[product_batches] ([id])
);

CREATE TABLE [dbo].[customers] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [code] NVARCHAR(20) NOT NULL,
    [name] NVARCHAR(200) NOT NULL,
    [type] NVARCHAR(50) NULL,
    [contact_person] NVARCHAR(50) NULL,
    [phone] NVARCHAR(20) NULL,
    [email] NVARCHAR(100) NULL,
    [address] NVARCHAR(300) NULL,
    [credit_limit] DECIMAL(18,2) NULL,
    [credit_days] INT NULL,
    [balance] DECIMAL(18,2) NULL,
    [membership_level] NVARCHAR(50) NULL,
    [status] NVARCHAR(50) NULL,
    [created_at] DATETIME2 NULL,
    [updated_at] DATETIME2 NULL,
    CONSTRAINT [pk_customers] PRIMARY KEY ([id])
);

CREATE TABLE [dbo].[sales_orders] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [order_no] NVARCHAR(30) NOT NULL,
    [customer_id] BIGINT NOT NULL,
    [salesperson_id] BIGINT NOT NULL,
    [warehouse_id] BIGINT NOT NULL,
    [order_date] DATE NOT NULL,
    [delivery_date] DATE NULL,
    [total_amount] DECIMAL(18,2) NULL,
    [discount_amount] DECIMAL(18,2) NULL,
    [paid_amount] DECIMAL(18,2) NULL,
    [tax_amount] DECIMAL(18,2) NULL,
    [payment_method] NVARCHAR(50) NULL,
    [status] NVARCHAR(50) NULL,
    [invoice_no] NVARCHAR(50) NULL,
    [remark] NVARCHAR(500) NULL,
    [created_at] DATETIME2 NULL,
    [updated_at] DATETIME2 NULL,
    CONSTRAINT [pk_sales_orders] PRIMARY KEY ([id]),
    CONSTRAINT [fk_so_customer] FOREIGN KEY ([customer_id]) REFERENCES [dbo].[customers] ([id]),
    CONSTRAINT [fk_so_salesperson] FOREIGN KEY ([salesperson_id]) REFERENCES [dbo].[employees] ([id]),
    CONSTRAINT [fk_so_wh] FOREIGN KEY ([warehouse_id]) REFERENCES [dbo].[warehouses] ([id])
);

CREATE TABLE [dbo].[sales_order_items] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [order_id] BIGINT NOT NULL,
    [product_id] BIGINT NOT NULL,
    [batch_id] BIGINT NULL,
    [quantity] INT NOT NULL,
    [unit_price] DECIMAL(12,2) NOT NULL,
    [discount] DECIMAL(12,2) NULL,
    [amount] DECIMAL(18,2) NOT NULL,
    [returned_qty] INT NULL,
    CONSTRAINT [pk_sales_order_items] PRIMARY KEY ([id]),
    CONSTRAINT [fk_soi_order] FOREIGN KEY ([order_id]) REFERENCES [dbo].[sales_orders] ([id]),
    CONSTRAINT [fk_soi_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[products] ([id]),
    CONSTRAINT [fk_soi_batch] FOREIGN KEY ([batch_id]) REFERENCES [dbo].[product_batches] ([id])
);

CREATE TABLE [dbo].[sales_returns] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [return_no] NVARCHAR(30) NOT NULL,
    [order_id] BIGINT NOT NULL,
    [customer_id] BIGINT NOT NULL,
    [warehouse_id] BIGINT NOT NULL,
    [handler_id] BIGINT NOT NULL,
    [return_date] DATE NOT NULL,
    [return_reason] NVARCHAR(500) NOT NULL,
    [return_type] NVARCHAR(50) NULL,
    [total_amount] DECIMAL(18,2) NULL,
    [refund_amount] DECIMAL(18,2) NULL,
    [restock_fee] DECIMAL(12,2) NULL,
    [status] NVARCHAR(50) NULL,
    [approved_by] BIGINT NULL,
    [approved_at] DATETIME2 NULL,
    [refund_voucher_id] BIGINT NULL,
    [return_shipping_fee] DECIMAL(12,2) NULL,
    [remark] NVARCHAR(500) NULL,
    [created_at] DATETIME2 NULL,
    [updated_at] DATETIME2 NULL,
    CONSTRAINT [pk_sales_returns] PRIMARY KEY ([id]),
    CONSTRAINT [fk_sr_order] FOREIGN KEY ([order_id]) REFERENCES [dbo].[sales_orders] ([id]),
    CONSTRAINT [fk_sr_customer] FOREIGN KEY ([customer_id]) REFERENCES [dbo].[customers] ([id]),
    CONSTRAINT [fk_sr_wh] FOREIGN KEY ([warehouse_id]) REFERENCES [dbo].[warehouses] ([id]),
    CONSTRAINT [fk_sr_handler] FOREIGN KEY ([handler_id]) REFERENCES [dbo].[employees] ([id]),
    CONSTRAINT [fk_sr_approved] FOREIGN KEY ([approved_by]) REFERENCES [dbo].[employees] ([id]),
    CONSTRAINT [fk_sr_refund_voucher] FOREIGN KEY ([refund_voucher_id]) REFERENCES [dbo].[vouchers] ([id])
);

CREATE TABLE [dbo].[sales_return_items] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [return_id] BIGINT NOT NULL,
    [order_item_id] BIGINT NOT NULL,
    [product_id] BIGINT NOT NULL,
    [batch_id] BIGINT NULL,
    [return_qty] INT NOT NULL,
    [unit_price] DECIMAL(12,2) NOT NULL,
    [amount] DECIMAL(18,2) NOT NULL,
    [status] NVARCHAR(50) NULL,
    CONSTRAINT [pk_sales_return_items] PRIMARY KEY ([id]),
    CONSTRAINT [fk_sri_return] FOREIGN KEY ([return_id]) REFERENCES [dbo].[sales_returns] ([id]),
    CONSTRAINT [fk_sri_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[products] ([id]),
    CONSTRAINT [fk_sri_batch] FOREIGN KEY ([batch_id]) REFERENCES [dbo].[product_batches] ([id])
);

CREATE TABLE [dbo].[purchase_returns] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [return_no] NVARCHAR(30) NOT NULL,
    [purchase_order_id] BIGINT NOT NULL,
    [purchase_receipt_id] BIGINT NULL,
    [supplier_id] BIGINT NOT NULL,
    [warehouse_id] BIGINT NOT NULL,
    [handler_id] BIGINT NOT NULL,
    [return_date] DATE NOT NULL,
    [return_reason] NVARCHAR(500) NOT NULL,
    [return_type] NVARCHAR(50) NULL,
    [total_amount] DECIMAL(18,2) NULL,
    [refund_received] DECIMAL(18,2) NULL,
    [shipping_fee] DECIMAL(12,2) NULL,
    [status] NVARCHAR(50) NULL,
    [approved_by] BIGINT NULL,
    [approved_at] DATETIME2 NULL,
    [refund_voucher_id] BIGINT NULL,
    [remark] NVARCHAR(500) NULL,
    [created_at] DATETIME2 NULL,
    [updated_at] DATETIME2 NULL,
    CONSTRAINT [pk_purchase_returns] PRIMARY KEY ([id]),
    CONSTRAINT [fk_prt_po] FOREIGN KEY ([purchase_order_id]) REFERENCES [dbo].[purchase_orders] ([id]),
    CONSTRAINT [fk_prt_receipt] FOREIGN KEY ([purchase_receipt_id]) REFERENCES [dbo].[purchase_receipts] ([id]),
    CONSTRAINT [fk_prt_supplier] FOREIGN KEY ([supplier_id]) REFERENCES [dbo].[suppliers] ([id]),
    CONSTRAINT [fk_prt_wh] FOREIGN KEY ([warehouse_id]) REFERENCES [dbo].[warehouses] ([id]),
    CONSTRAINT [fk_prt_handler] FOREIGN KEY ([handler_id]) REFERENCES [dbo].[employees] ([id]),
    CONSTRAINT [fk_prt_approved] FOREIGN KEY ([approved_by]) REFERENCES [dbo].[employees] ([id]),
    CONSTRAINT [fk_prt_voucher] FOREIGN KEY ([refund_voucher_id]) REFERENCES [dbo].[vouchers] ([id])
);

CREATE TABLE [dbo].[purchase_return_items] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [return_id] BIGINT NOT NULL,
    [product_id] BIGINT NOT NULL,
    [batch_id] BIGINT NULL,
    [return_qty] INT NOT NULL,
    [unit_price] DECIMAL(12,2) NOT NULL,
    [amount] DECIMAL(18,2) NULL,
    [reason] NVARCHAR(200) NULL,
    CONSTRAINT [pk_purchase_return_items] PRIMARY KEY ([id]),
    CONSTRAINT [fk_pri_return] FOREIGN KEY ([return_id]) REFERENCES [dbo].[purchase_returns] ([id]),
    CONSTRAINT [fk_pri_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[products] ([id]),
    CONSTRAINT [fk_pri_batch] FOREIGN KEY ([batch_id]) REFERENCES [dbo].[product_batches] ([id])
);

CREATE TABLE [dbo].[damage_reports] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [report_no] NVARCHAR(30) NOT NULL,
    [warehouse_id] BIGINT NOT NULL,
    [report_type] NVARCHAR(50) NOT NULL,
    [report_date] DATE NOT NULL,
    [reported_by] BIGINT NOT NULL,
    [total_quantity] INT NULL,
    [total_loss_amount] DECIMAL(18,2) NULL,
    [status] NVARCHAR(50) NULL,
    [approved_by] BIGINT NULL,
    [approved_at] DATETIME2 NULL,
    [executed_by] BIGINT NULL,
    [executed_at] DATETIME2 NULL,
    [voucher_id] BIGINT NULL,
    [description] NVARCHAR(MAX) NULL,
    [remark] NVARCHAR(500) NULL,
    [created_at] DATETIME2 NULL,
    [updated_at] DATETIME2 NULL,
    CONSTRAINT [pk_damage_reports] PRIMARY KEY ([id]),
    CONSTRAINT [fk_dr_wh] FOREIGN KEY ([warehouse_id]) REFERENCES [dbo].[warehouses] ([id]),
    CONSTRAINT [fk_dr_reported] FOREIGN KEY ([reported_by]) REFERENCES [dbo].[employees] ([id]),
    CONSTRAINT [fk_dr_approved] FOREIGN KEY ([approved_by]) REFERENCES [dbo].[employees] ([id]),
    CONSTRAINT [fk_dr_executed] FOREIGN KEY ([executed_by]) REFERENCES [dbo].[employees] ([id]),
    CONSTRAINT [fk_dr_voucher] FOREIGN KEY ([voucher_id]) REFERENCES [dbo].[vouchers] ([id])
);

CREATE TABLE [dbo].[damage_report_items] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [report_id] BIGINT NOT NULL,
    [product_id] BIGINT NOT NULL,
    [batch_id] BIGINT NULL,
    [quantity] INT NOT NULL,
    [unit_cost] DECIMAL(12,2) NOT NULL,
    [loss_amount] DECIMAL(18,2) NULL,
    [reason] NVARCHAR(200) NULL,
    CONSTRAINT [pk_damage_report_items] PRIMARY KEY ([id]),
    CONSTRAINT [fk_dri_report] FOREIGN KEY ([report_id]) REFERENCES [dbo].[damage_reports] ([id]),
    CONSTRAINT [fk_dri_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[products] ([id]),
    CONSTRAINT [fk_dri_batch] FOREIGN KEY ([batch_id]) REFERENCES [dbo].[product_batches] ([id])
);

CREATE TABLE [dbo].[accounts] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [code] NVARCHAR(20) NOT NULL,
    [parent_id] BIGINT NULL,
    [name] NVARCHAR(100) NOT NULL,
    [account_type] NVARCHAR(50) NOT NULL,
    [balance_direction] NVARCHAR(50) NOT NULL,
    [is_cash] BIT NULL,
    [is_bank] BIT NULL,
    [bank_name] NVARCHAR(100) NULL,
    [bank_account] NVARCHAR(50) NULL,
    [current_balance] DECIMAL(18,2) NULL,
    [status] NVARCHAR(50) NULL,
    [created_at] DATETIME2 NULL,
    CONSTRAINT [pk_accounts] PRIMARY KEY ([id]),
    CONSTRAINT [fk_acct_parent] FOREIGN KEY ([parent_id]) REFERENCES [dbo].[accounts] ([id])
);

CREATE TABLE [dbo].[vouchers] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [voucher_no] NVARCHAR(30) NOT NULL,
    [voucher_date] DATE NOT NULL,
    [voucher_type] NVARCHAR(50) NOT NULL,
    [reference_type] NVARCHAR(50) NULL,
    [reference_id] BIGINT NULL,
    [total_debit] DECIMAL(18,2) NULL,
    [total_credit] DECIMAL(18,2) NULL,
    [prepared_by] BIGINT NOT NULL,
    [reviewed_by] BIGINT NULL,
    [posted_by] BIGINT NULL,
    [status] NVARCHAR(50) NULL,
    [summary] NVARCHAR(500) NULL,
    [created_at] DATETIME2 NULL,
    [updated_at] DATETIME2 NULL,
    CONSTRAINT [pk_vouchers] PRIMARY KEY ([id]),
    CONSTRAINT [fk_v_prepared] FOREIGN KEY ([prepared_by]) REFERENCES [dbo].[employees] ([id]),
    CONSTRAINT [fk_v_reviewed] FOREIGN KEY ([reviewed_by]) REFERENCES [dbo].[employees] ([id]),
    CONSTRAINT [fk_v_posted] FOREIGN KEY ([posted_by]) REFERENCES [dbo].[employees] ([id])
);

CREATE TABLE [dbo].[voucher_items] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [voucher_id] BIGINT NOT NULL,
    [account_id] BIGINT NOT NULL,
    [line_no] INT NOT NULL,
    [direction] NVARCHAR(50) NOT NULL,
    [amount] DECIMAL(18,2) NOT NULL,
    [summary] NVARCHAR(500) NULL,
    CONSTRAINT [pk_voucher_items] PRIMARY KEY ([id]),
    CONSTRAINT [fk_vi_voucher] FOREIGN KEY ([voucher_id]) REFERENCES [dbo].[vouchers] ([id]),
    CONSTRAINT [fk_vi_account] FOREIGN KEY ([account_id]) REFERENCES [dbo].[accounts] ([id])
);

CREATE TABLE [dbo].[cashier_journals] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [journal_no] NVARCHAR(30) NOT NULL,
    [journal_date] DATE NOT NULL,
    [account_id] BIGINT NOT NULL,
    [cashier_id] BIGINT NOT NULL,
    [journal_type] NVARCHAR(50) NOT NULL,
    [amount] DECIMAL(18,2) NOT NULL,
    [counterparty] NVARCHAR(200) NULL,
    [reference_type] NVARCHAR(50) NULL,
    [reference_id] BIGINT NULL,
    [voucher_id] BIGINT NULL,
    [bank_account] NVARCHAR(50) NULL,
    [check_no] NVARCHAR(50) NULL,
    [status] NVARCHAR(50) NULL,
    [remark] NVARCHAR(500) NULL,
    [created_at] DATETIME2 NULL,
    CONSTRAINT [pk_cashier_journals] PRIMARY KEY ([id]),
    CONSTRAINT [fk_cj_account] FOREIGN KEY ([account_id]) REFERENCES [dbo].[accounts] ([id]),
    CONSTRAINT [fk_cj_cashier] FOREIGN KEY ([cashier_id]) REFERENCES [dbo].[employees] ([id]),
    CONSTRAINT [fk_cj_voucher] FOREIGN KEY ([voucher_id]) REFERENCES [dbo].[vouchers] ([id])
);

CREATE TABLE [dbo].[salary_payments] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [payment_no] NVARCHAR(30) NOT NULL,
    [employee_id] BIGINT NOT NULL,
    [payment_date] DATE NOT NULL,
    [salary_month] NVARCHAR(7) NOT NULL,
    [base_salary] DECIMAL(12,2) NOT NULL,
    [overtime_pay] DECIMAL(12,2) NULL,
    [bonus] DECIMAL(12,2) NULL,
    [deduction] DECIMAL(12,2) NULL,
    [social_security_personal] DECIMAL(12,2) NULL,
    [housing_fund_personal] DECIMAL(12,2) NULL,
    [income_tax] DECIMAL(12,2) NULL,
    [net_pay] DECIMAL(12,2) NOT NULL,
    [social_security_company] DECIMAL(12,2) NULL,
    [housing_fund_company] DECIMAL(12,2) NULL,
    [payment_method] NVARCHAR(50) NULL,
    [status] NVARCHAR(50) NULL,
    [paid_at] DATETIME2 NULL,
    [voucher_id] BIGINT NULL,
    [remark] NVARCHAR(200) NULL,
    [created_at] DATETIME2 NULL,
    CONSTRAINT [pk_salary_payments] PRIMARY KEY ([id]),
    CONSTRAINT [uk_emp_month] UNIQUE ([employee_id], [salary_month]),
    CONSTRAINT [fk_sp_emp] FOREIGN KEY ([employee_id]) REFERENCES [dbo].[employees] ([id]),
    CONSTRAINT [fk_sp_voucher] FOREIGN KEY ([voucher_id]) REFERENCES [dbo].[vouchers] ([id])
);

CREATE TABLE [dbo].[reconciliations] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [recon_no] NVARCHAR(30) NOT NULL,
    [account_id] BIGINT NOT NULL,
    [recon_date] DATE NOT NULL,
    [period_start] DATE NOT NULL,
    [period_end] DATE NOT NULL,
    [book_balance] DECIMAL(18,2) NOT NULL,
    [bank_balance] DECIMAL(18,2) NULL,
    [difference] DECIMAL(18,2) NULL,
    [unreconciled_income] DECIMAL(18,2) NULL,
    [unreconciled_expense] DECIMAL(18,2) NULL,
    [adjusted_balance] DECIMAL(18,2) NULL,
    [prepared_by] BIGINT NOT NULL,
    [reviewed_by] BIGINT NULL,
    [status] NVARCHAR(50) NULL,
    [remark] NVARCHAR(500) NULL,
    [created_at] DATETIME2 NULL,
    [updated_at] DATETIME2 NULL,
    CONSTRAINT [pk_reconciliations] PRIMARY KEY ([id]),
    CONSTRAINT [fk_recon_account] FOREIGN KEY ([account_id]) REFERENCES [dbo].[accounts] ([id]),
    CONSTRAINT [fk_recon_prepared] FOREIGN KEY ([prepared_by]) REFERENCES [dbo].[employees] ([id]),
    CONSTRAINT [fk_recon_reviewed] FOREIGN KEY ([reviewed_by]) REFERENCES [dbo].[employees] ([id])
);
