-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

CREATE TABLE [dbo].[consignment_consumptions] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [consignment_id] BIGINT NOT NULL,
    [consumed_qty] INT NOT NULL,
    [consumed_date] DATE NOT NULL,
    [unit_price] DECIMAL(12,2) NOT NULL,
    [amount] DECIMAL(18,2) NULL,
    [confirmed_by_customer] BIT NULL,
    [sales_order_id] BIGINT NULL,
    [remark] NVARCHAR(200) NULL,
    [created_at] DATETIME2 NULL,
    CONSTRAINT [pk_consignment_consumptions] PRIMARY KEY ([id]),
    CONSTRAINT [fk_cc_consignment] FOREIGN KEY ([consignment_id]) REFERENCES [dbo].[consignment_inventory] ([id])
);

CREATE TABLE [dbo].[price_change_logs] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [product_id] BIGINT NOT NULL,
    [price_type] NVARCHAR(50) NOT NULL,
    [old_price] DECIMAL(12,2) NOT NULL,
    [new_price] DECIMAL(12,2) NOT NULL,
    [change_reason] NVARCHAR(200) NOT NULL,
    [effective_date] DATE NOT NULL,
    [changed_by] BIGINT NOT NULL,
    [approved_by] BIGINT NULL,
    [created_at] DATETIME2 NULL,
    CONSTRAINT [pk_price_change_logs] PRIMARY KEY ([id]),
    CONSTRAINT [fk_pcl_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[products] ([id]),
    CONSTRAINT [fk_pcl_changed] FOREIGN KEY ([changed_by]) REFERENCES [dbo].[employees] ([id])
);

CREATE TABLE [dbo].[tenants] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [tenant_code] NVARCHAR(30) NOT NULL,
    [tenant_name] NVARCHAR(100) NOT NULL,
    [legal_entity_name] NVARCHAR(200) NOT NULL,
    [tax_no] NVARCHAR(30) NULL,
    [status] NVARCHAR(50) NULL,
    [created_at] DATETIME2 NULL,
    [updated_at] DATETIME2 NULL,
    CONSTRAINT [pk_tenants] PRIMARY KEY ([id])
);

CREATE TABLE [dbo].[ledger_books] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [tenant_id] BIGINT NOT NULL,
    [book_code] NVARCHAR(30) NOT NULL,
    [book_name] NVARCHAR(100) NOT NULL,
    [base_currency] NVARCHAR(3) NULL,
    [fiscal_year_start_month] INT NULL,
    [is_default] BIT NULL,
    [status] NVARCHAR(50) NULL,
    CONSTRAINT [pk_ledger_books] PRIMARY KEY ([id]),
    CONSTRAINT [uk_ledger_book] UNIQUE ([tenant_id], [book_code]),
    CONSTRAINT [fk_ledger_tenant] FOREIGN KEY ([tenant_id]) REFERENCES [dbo].[tenants] ([id])
);

CREATE TABLE [dbo].[customer_addresses] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [customer_id] BIGINT NOT NULL,
    [address_type] NVARCHAR(50) NOT NULL,
    [receiver_name] NVARCHAR(100) NULL,
    [receiver_phone] NVARCHAR(30) NULL,
    [province] NVARCHAR(50) NOT NULL,
    [city] NVARCHAR(50) NOT NULL,
    [district] NVARCHAR(50) NULL,
    [street] NVARCHAR(300) NOT NULL,
    [postal_code] NVARCHAR(20) NULL,
    [is_default] BIT NULL,
    CONSTRAINT [pk_customer_addresses] PRIMARY KEY ([id]),
    CONSTRAINT [fk_customer_address_customer] FOREIGN KEY ([customer_id]) REFERENCES [dbo].[customers] ([id])
);

CREATE TABLE [dbo].[supplier_addresses] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [supplier_id] BIGINT NOT NULL,
    [address_type] NVARCHAR(50) NOT NULL,
    [contact_name] NVARCHAR(100) NULL,
    [contact_phone] NVARCHAR(30) NULL,
    [province] NVARCHAR(50) NOT NULL,
    [city] NVARCHAR(50) NOT NULL,
    [district] NVARCHAR(50) NULL,
    [street] NVARCHAR(300) NOT NULL,
    [postal_code] NVARCHAR(20) NULL,
    [is_default] BIT NULL,
    CONSTRAINT [pk_supplier_addresses] PRIMARY KEY ([id]),
    CONSTRAINT [fk_supplier_address_supplier] FOREIGN KEY ([supplier_id]) REFERENCES [dbo].[suppliers] ([id])
);

CREATE TABLE [dbo].[tax_rates] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [tax_code] NVARCHAR(30) NOT NULL,
    [tax_name] NVARCHAR(100) NOT NULL,
    [tax_type] NVARCHAR(50) NOT NULL,
    [rate] DECIMAL(8,4) NOT NULL,
    [effective_from] DATE NOT NULL,
    [effective_to] DATE NULL,
    [status] NVARCHAR(50) NULL,
    CONSTRAINT [pk_tax_rates] PRIMARY KEY ([id])
);

CREATE TABLE [dbo].[accounting_periods] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [ledger_book_id] BIGINT NOT NULL,
    [period_code] NVARCHAR(7) NOT NULL,
    [period_start] DATE NOT NULL,
    [period_end] DATE NOT NULL,
    [status] NVARCHAR(50) NULL,
    [closed_by] BIGINT NULL,
    [closed_at] DATETIME2 NULL,
    CONSTRAINT [pk_accounting_periods] PRIMARY KEY ([id]),
    CONSTRAINT [uk_accounting_period] UNIQUE ([ledger_book_id], [period_code]),
    CONSTRAINT [fk_period_book] FOREIGN KEY ([ledger_book_id]) REFERENCES [dbo].[ledger_books] ([id]),
    CONSTRAINT [fk_period_closed_by] FOREIGN KEY ([closed_by]) REFERENCES [dbo].[employees] ([id])
);

CREATE TABLE [dbo].[period_close_jobs] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [period_id] BIGINT NOT NULL,
    [job_code] NVARCHAR(30) NOT NULL,
    [job_name] NVARCHAR(100) NOT NULL,
    [status] NVARCHAR(50) NULL,
    [started_at] DATETIME2 NULL,
    [finished_at] DATETIME2 NULL,
    [message] NVARCHAR(1000) NULL,
    CONSTRAINT [pk_period_close_jobs] PRIMARY KEY ([id]),
    CONSTRAINT [uk_period_job] UNIQUE ([period_id], [job_code]),
    CONSTRAINT [fk_period_close_job_period] FOREIGN KEY ([period_id]) REFERENCES [dbo].[accounting_periods] ([id])
);

CREATE TABLE [dbo].[payment_receipts] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [receipt_no] NVARCHAR(30) NOT NULL,
    [receipt_type] NVARCHAR(50) NOT NULL,
    [party_type] NVARCHAR(50) NOT NULL,
    [party_id] BIGINT NULL,
    [account_id] BIGINT NOT NULL,
    [receipt_date] DATE NOT NULL,
    [amount] DECIMAL(18,2) NOT NULL,
    [currency] NVARCHAR(3) NULL,
    [status] NVARCHAR(50) NULL,
    [handled_by] BIGINT NOT NULL,
    [confirmed_at] DATETIME2 NULL,
    [remark] NVARCHAR(500) NULL,
    CONSTRAINT [pk_payment_receipts] PRIMARY KEY ([id]),
    CONSTRAINT [fk_payment_receipt_account] FOREIGN KEY ([account_id]) REFERENCES [dbo].[accounts] ([id]),
    CONSTRAINT [fk_payment_receipt_handler] FOREIGN KEY ([handled_by]) REFERENCES [dbo].[employees] ([id])
);

CREATE TABLE [dbo].[payment_receipt_allocations] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [receipt_id] BIGINT NOT NULL,
    [reference_type] NVARCHAR(50) NOT NULL,
    [reference_id] BIGINT NOT NULL,
    [allocated_amount] DECIMAL(18,2) NOT NULL,
    CONSTRAINT [pk_payment_receipt_allocations] PRIMARY KEY ([id]),
    CONSTRAINT [fk_payment_allocation_receipt] FOREIGN KEY ([receipt_id]) REFERENCES [dbo].[payment_receipts] ([id])
);

CREATE TABLE [dbo].[stocktakes] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [stocktake_no] NVARCHAR(30) NOT NULL,
    [warehouse_id] BIGINT NOT NULL,
    [stocktake_date] DATE NOT NULL,
    [stocktake_type] NVARCHAR(50) NULL,
    [status] NVARCHAR(50) NULL,
    [created_by] BIGINT NOT NULL,
    [reviewed_by] BIGINT NULL,
    [posted_at] DATETIME2 NULL,
    CONSTRAINT [pk_stocktakes] PRIMARY KEY ([id]),
    CONSTRAINT [fk_stocktake_warehouse] FOREIGN KEY ([warehouse_id]) REFERENCES [dbo].[warehouses] ([id]),
    CONSTRAINT [fk_stocktake_created_by] FOREIGN KEY ([created_by]) REFERENCES [dbo].[employees] ([id]),
    CONSTRAINT [fk_stocktake_reviewed_by] FOREIGN KEY ([reviewed_by]) REFERENCES [dbo].[employees] ([id])
);

CREATE TABLE [dbo].[stocktake_items] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [stocktake_id] BIGINT NOT NULL,
    [product_id] BIGINT NOT NULL,
    [batch_id] BIGINT NULL,
    [book_quantity] INT NOT NULL,
    [counted_quantity] INT NOT NULL,
    [variance_quantity] INT NULL,
    [variance_reason] NVARCHAR(300) NULL,
    CONSTRAINT [pk_stocktake_items] PRIMARY KEY ([id]),
    CONSTRAINT [fk_stocktake_item_stocktake] FOREIGN KEY ([stocktake_id]) REFERENCES [dbo].[stocktakes] ([id]),
    CONSTRAINT [fk_stocktake_item_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[products] ([id]),
    CONSTRAINT [fk_stocktake_item_batch] FOREIGN KEY ([batch_id]) REFERENCES [dbo].[product_batches] ([id])
);

CREATE TABLE [dbo].[stock_transfers] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [transfer_no] NVARCHAR(30) NOT NULL,
    [from_warehouse_id] BIGINT NOT NULL,
    [to_warehouse_id] BIGINT NOT NULL,
    [requested_by] BIGINT NOT NULL,
    [approved_by] BIGINT NULL,
    [transfer_date] DATE NOT NULL,
    [status] NVARCHAR(50) NULL,
    CONSTRAINT [pk_stock_transfers] PRIMARY KEY ([id]),
    CONSTRAINT [fk_transfer_from_wh] FOREIGN KEY ([from_warehouse_id]) REFERENCES [dbo].[warehouses] ([id]),
    CONSTRAINT [fk_transfer_to_wh] FOREIGN KEY ([to_warehouse_id]) REFERENCES [dbo].[warehouses] ([id]),
    CONSTRAINT [fk_transfer_requested_by] FOREIGN KEY ([requested_by]) REFERENCES [dbo].[employees] ([id]),
    CONSTRAINT [fk_transfer_approved_by] FOREIGN KEY ([approved_by]) REFERENCES [dbo].[employees] ([id])
);

CREATE TABLE [dbo].[stock_transfer_items] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [transfer_id] BIGINT NOT NULL,
    [product_id] BIGINT NOT NULL,
    [batch_id] BIGINT NULL,
    [quantity] INT NOT NULL,
    [received_quantity] INT NULL,
    CONSTRAINT [pk_stock_transfer_items] PRIMARY KEY ([id]),
    CONSTRAINT [fk_transfer_item_transfer] FOREIGN KEY ([transfer_id]) REFERENCES [dbo].[stock_transfers] ([id]),
    CONSTRAINT [fk_transfer_item_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[products] ([id]),
    CONSTRAINT [fk_transfer_item_batch] FOREIGN KEY ([batch_id]) REFERENCES [dbo].[product_batches] ([id])
);

CREATE TABLE [dbo].[inventory_reservations] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [reservation_no] NVARCHAR(30) NOT NULL,
    [product_id] BIGINT NOT NULL,
    [batch_id] BIGINT NULL,
    [warehouse_id] BIGINT NOT NULL,
    [source_type] NVARCHAR(50) NOT NULL,
    [source_id] BIGINT NOT NULL,
    [reserved_quantity] INT NOT NULL,
    [released_quantity] INT NULL,
    [status] NVARCHAR(50) NULL,
    [expires_at] DATETIME2 NULL,
    CONSTRAINT [pk_inventory_reservations] PRIMARY KEY ([id]),
    CONSTRAINT [fk_inv_res_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[products] ([id]),
    CONSTRAINT [fk_inv_res_batch] FOREIGN KEY ([batch_id]) REFERENCES [dbo].[product_batches] ([id]),
    CONSTRAINT [fk_inv_res_warehouse] FOREIGN KEY ([warehouse_id]) REFERENCES [dbo].[warehouses] ([id])
);

CREATE TABLE [dbo].[production_routes] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [product_id] BIGINT NOT NULL,
    [route_code] NVARCHAR(30) NOT NULL,
    [route_name] NVARCHAR(100) NOT NULL,
    [version_no] NVARCHAR(20) NOT NULL,
    [status] NVARCHAR(50) NULL,
    CONSTRAINT [pk_production_routes] PRIMARY KEY ([id]),
    CONSTRAINT [uk_product_route] UNIQUE ([product_id], [route_code], [version_no]),
    CONSTRAINT [fk_route_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[products] ([id])
);

CREATE TABLE [dbo].[production_operations] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [route_id] BIGINT NOT NULL,
    [operation_no] INT NOT NULL,
    [operation_name] NVARCHAR(100) NOT NULL,
    [work_center] NVARCHAR(100) NOT NULL,
    [standard_minutes] DECIMAL(10,2) NOT NULL,
    [predecessor_operation_id] BIGINT NULL,
    CONSTRAINT [pk_production_operations] PRIMARY KEY ([id]),
    CONSTRAINT [uk_route_operation] UNIQUE ([route_id], [operation_no]),
    CONSTRAINT [fk_operation_route] FOREIGN KEY ([route_id]) REFERENCES [dbo].[production_routes] ([id]),
    CONSTRAINT [fk_operation_predecessor] FOREIGN KEY ([predecessor_operation_id]) REFERENCES [dbo].[production_operations] ([id])
);

CREATE TABLE [dbo].[employee_shifts] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [shift_code] NVARCHAR(20) NOT NULL,
    [shift_name] NVARCHAR(100) NOT NULL,
    [start_time] TIME NOT NULL,
    [end_time] TIME NOT NULL,
    [planned_hours] DECIMAL(4,2) NOT NULL,
    [is_night_shift] BIT NULL,
    CONSTRAINT [pk_employee_shifts] PRIMARY KEY ([id])
);

CREATE TABLE [dbo].[employee_shift_assignments] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [employee_id] BIGINT NOT NULL,
    [shift_id] BIGINT NOT NULL,
    [work_date] DATE NOT NULL,
    [warehouse_id] BIGINT NULL,
    [status] NVARCHAR(50) NULL,
    CONSTRAINT [pk_employee_shift_assignments] PRIMARY KEY ([id]),
    CONSTRAINT [uk_employee_shift_date] UNIQUE ([employee_id], [work_date]),
    CONSTRAINT [fk_shift_assignment_employee] FOREIGN KEY ([employee_id]) REFERENCES [dbo].[employees] ([id]),
    CONSTRAINT [fk_shift_assignment_shift] FOREIGN KEY ([shift_id]) REFERENCES [dbo].[employee_shifts] ([id]),
    CONSTRAINT [fk_shift_assignment_warehouse] FOREIGN KEY ([warehouse_id]) REFERENCES [dbo].[warehouses] ([id])
);
