-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

CREATE TABLE [dbo].[reconciliation_items] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [reconciliation_id] BIGINT NOT NULL,
    [journal_id] BIGINT NULL,
    [transaction_date] DATE NOT NULL,
    [description] NVARCHAR(500) NOT NULL,
    [debit_amount] DECIMAL(18,2) NULL,
    [credit_amount] DECIMAL(18,2) NULL,
    [is_matched] BIT NULL,
    [matched_item_id] BIGINT NULL,
    [difference_reason] NVARCHAR(500) NULL,
    CONSTRAINT [pk_reconciliation_items] PRIMARY KEY ([id]),
    CONSTRAINT [fk_ri_recon] FOREIGN KEY ([reconciliation_id]) REFERENCES [dbo].[reconciliations] ([id])
);

CREATE TABLE [dbo].[settlements] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [settlement_no] NVARCHAR(30) NOT NULL,
    [settlement_type] NVARCHAR(50) NOT NULL,
    [party_id] BIGINT NOT NULL,
    [settlement_date] DATE NOT NULL,
    [period_start] DATE NOT NULL,
    [period_end] DATE NOT NULL,
    [total_amount] DECIMAL(18,2) NOT NULL,
    [settled_amount] DECIMAL(18,2) NULL,
    [unpaid_amount] DECIMAL(18,2) NULL,
    [payment_due_date] DATE NULL,
    [payment_method] NVARCHAR(50) NULL,
    [status] NVARCHAR(50) NULL,
    [voucher_id] BIGINT NULL,
    [prepared_by] BIGINT NOT NULL,
    [approved_by] BIGINT NULL,
    [remark] NVARCHAR(500) NULL,
    [created_at] DATETIME2 NULL,
    [updated_at] DATETIME2 NULL,
    CONSTRAINT [pk_settlements] PRIMARY KEY ([id]),
    CONSTRAINT [fk_settle_voucher] FOREIGN KEY ([voucher_id]) REFERENCES [dbo].[vouchers] ([id]),
    CONSTRAINT [fk_settle_prepared] FOREIGN KEY ([prepared_by]) REFERENCES [dbo].[employees] ([id]),
    CONSTRAINT [fk_settle_approved] FOREIGN KEY ([approved_by]) REFERENCES [dbo].[employees] ([id])
);

CREATE TABLE [dbo].[settlement_items] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [settlement_id] BIGINT NOT NULL,
    [reference_type] NVARCHAR(50) NOT NULL,
    [reference_id] BIGINT NOT NULL,
    [amount] DECIMAL(18,2) NOT NULL,
    [settled_amount] DECIMAL(18,2) NULL,
    [remark] NVARCHAR(200) NULL,
    CONSTRAINT [pk_settlement_items] PRIMARY KEY ([id]),
    CONSTRAINT [fk_si_settlement] FOREIGN KEY ([settlement_id]) REFERENCES [dbo].[settlements] ([id])
);

CREATE TABLE [dbo].[shipments] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [shipment_no] NVARCHAR(30) NOT NULL,
    [order_id] BIGINT NOT NULL,
    [warehouse_id] BIGINT NOT NULL,
    [carrier] NVARCHAR(100) NOT NULL,
    [tracking_no] NVARCHAR(50) NULL,
    [shipping_method] NVARCHAR(50) NULL,
    [shipping_fee] DECIMAL(12,2) NULL,
    [package_count] INT NULL,
    [weight_kg] DECIMAL(8,3) NULL,
    [status] NVARCHAR(50) NULL,
    [picker_id] BIGINT NULL,
    [packer_id] BIGINT NULL,
    [shipped_at] DATETIME2 NULL,
    [delivered_at] DATETIME2 NULL,
    [estimated_delivery_date] DATE NULL,
    [actual_delivery_date] DATE NULL,
    [from_address] NVARCHAR(300) NULL,
    [to_address] NVARCHAR(300) NOT NULL,
    [receiver_name] NVARCHAR(50) NULL,
    [receiver_phone] NVARCHAR(20) NULL,
    [remark] NVARCHAR(500) NULL,
    [created_at] DATETIME2 NULL,
    [updated_at] DATETIME2 NULL,
    CONSTRAINT [pk_shipments] PRIMARY KEY ([id]),
    CONSTRAINT [fk_ship_order] FOREIGN KEY ([order_id]) REFERENCES [dbo].[sales_orders] ([id]),
    CONSTRAINT [fk_ship_wh] FOREIGN KEY ([warehouse_id]) REFERENCES [dbo].[warehouses] ([id])
);

CREATE TABLE [dbo].[shipping_tracks] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [shipment_id] BIGINT NOT NULL,
    [track_time] DATETIME2 NOT NULL,
    [location] NVARCHAR(200) NOT NULL,
    [status_desc] NVARCHAR(200) NOT NULL,
    [operator] NVARCHAR(50) NULL,
    [remark] NVARCHAR(200) NULL,
    [created_at] DATETIME2 NULL,
    CONSTRAINT [pk_shipping_tracks] PRIMARY KEY ([id]),
    CONSTRAINT [fk_st_shipment] FOREIGN KEY ([shipment_id]) REFERENCES [dbo].[shipments] ([id])
);

CREATE TABLE [dbo].[commission_rules] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [name] NVARCHAR(100) NOT NULL,
    [product_category_id] BIGINT NULL,
    [min_amount] DECIMAL(18,2) NULL,
    [max_amount] DECIMAL(18,2) NULL,
    [commission_rate] DECIMAL(5,4) NOT NULL,
    [bonus] DECIMAL(12,2) NULL,
    [effective_date] DATE NOT NULL,
    [expiry_date] DATE NULL,
    [status] NVARCHAR(50) NULL,
    [created_at] DATETIME2 NULL,
    CONSTRAINT [pk_commission_rules] PRIMARY KEY ([id]),
    CONSTRAINT [fk_cr_category] FOREIGN KEY ([product_category_id]) REFERENCES [dbo].[product_categories] ([id])
);

CREATE TABLE [dbo].[sales_commissions] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [employee_id] BIGINT NOT NULL,
    [order_id] BIGINT NOT NULL,
    [order_item_id] BIGINT NULL,
    [period] NVARCHAR(7) NOT NULL,
    [base_amount] DECIMAL(18,2) NOT NULL,
    [commission_rate] DECIMAL(5,4) NOT NULL,
    [commission_amount] DECIMAL(12,2) NOT NULL,
    [bonus] DECIMAL(12,2) NULL,
    [total_commission] DECIMAL(12,2) NULL,
    [status] NVARCHAR(50) NULL,
    [calculated_at] DATETIME2 NULL,
    [paid_at] DATETIME2 NULL,
    [settlement_id] BIGINT NULL,
    CONSTRAINT [pk_sales_commissions] PRIMARY KEY ([id]),
    CONSTRAINT [uk_order_item] UNIQUE ([order_id], [order_item_id]),
    CONSTRAINT [fk_sc_emp] FOREIGN KEY ([employee_id]) REFERENCES [dbo].[employees] ([id]),
    CONSTRAINT [fk_sc_order] FOREIGN KEY ([order_id]) REFERENCES [dbo].[sales_orders] ([id])
);

CREATE TABLE [dbo].[promotions] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [name] NVARCHAR(200) NOT NULL,
    [code] NVARCHAR(30) NOT NULL,
    [promotion_type] NVARCHAR(50) NOT NULL,
    [discount_value] DECIMAL(12,2) NOT NULL,
    [min_purchase_amount] DECIMAL(18,2) NULL,
    [max_discount_amount] DECIMAL(12,2) NULL,
    [usage_limit] INT NULL,
    [used_count] INT NULL,
    [start_date] DATE NOT NULL,
    [end_date] DATE NOT NULL,
    [status] NVARCHAR(50) NULL,
    [created_at] DATETIME2 NULL,
    [updated_at] DATETIME2 NULL,
    CONSTRAINT [pk_promotions] PRIMARY KEY ([id])
);

CREATE TABLE [dbo].[promotion_products] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [promotion_id] BIGINT NOT NULL,
    [product_id] BIGINT NULL,
    [category_id] BIGINT NULL,
    CONSTRAINT [pk_promotion_products] PRIMARY KEY ([id]),
    CONSTRAINT [uk_promo_product] UNIQUE ([promotion_id], [product_id], [category_id]),
    CONSTRAINT [fk_pp_promo] FOREIGN KEY ([promotion_id]) REFERENCES [dbo].[promotions] ([id]),
    CONSTRAINT [fk_pp_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[products] ([id]),
    CONSTRAINT [fk_pp_category] FOREIGN KEY ([category_id]) REFERENCES [dbo].[product_categories] ([id])
);

CREATE TABLE [dbo].[promotion_usages] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [promotion_id] BIGINT NOT NULL,
    [order_id] BIGINT NOT NULL,
    [customer_id] BIGINT NOT NULL,
    [discount_applied] DECIMAL(12,2) NOT NULL,
    [used_at] DATETIME2 NULL,
    CONSTRAINT [pk_promotion_usages] PRIMARY KEY ([id]),
    CONSTRAINT [uk_promo_order] UNIQUE ([promotion_id], [order_id]),
    CONSTRAINT [fk_pu_promo] FOREIGN KEY ([promotion_id]) REFERENCES [dbo].[promotions] ([id]),
    CONSTRAINT [fk_pu_order] FOREIGN KEY ([order_id]) REFERENCES [dbo].[sales_orders] ([id]),
    CONSTRAINT [fk_pu_customer] FOREIGN KEY ([customer_id]) REFERENCES [dbo].[customers] ([id])
);

CREATE TABLE [dbo].[invoices] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [invoice_no] NVARCHAR(50) NOT NULL,
    [invoice_type] NVARCHAR(50) NOT NULL,
    [supplier_id] BIGINT NULL,
    [customer_id] BIGINT NULL,
    [invoice_date] DATE NOT NULL,
    [due_date] DATE NULL,
    [total_amount] DECIMAL(18,2) NOT NULL,
    [tax_amount] DECIMAL(18,2) NULL,
    [tax_rate] DECIMAL(5,4) NULL,
    [status] NVARCHAR(50) NULL,
    [verified_by] BIGINT NULL,
    [verified_at] DATETIME2 NULL,
    [created_at] DATETIME2 NULL,
    [updated_at] DATETIME2 NULL,
    CONSTRAINT [pk_invoices] PRIMARY KEY ([id]),
    CONSTRAINT [fk_inv_supplier] FOREIGN KEY ([supplier_id]) REFERENCES [dbo].[suppliers] ([id]),
    CONSTRAINT [fk_inv_customer] FOREIGN KEY ([customer_id]) REFERENCES [dbo].[customers] ([id])
);

CREATE TABLE [dbo].[three_way_matching] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [invoice_id] BIGINT NOT NULL,
    [purchase_order_id] BIGINT NOT NULL,
    [purchase_receipt_id] BIGINT NOT NULL,
    [product_id] BIGINT NOT NULL,
    [po_quantity] INT NOT NULL,
    [receipt_quantity] INT NOT NULL,
    [invoice_quantity] INT NOT NULL,
    [po_price] DECIMAL(12,2) NOT NULL,
    [receipt_price] DECIMAL(12,2) NOT NULL,
    [invoice_price] DECIMAL(12,2) NOT NULL,
    [quantity_match] BIT NULL,
    [price_match] BIT NULL,
    [match_status] NVARCHAR(50) NULL,
    [match_result] NVARCHAR(500) NULL,
    [matched_by] BIGINT NULL,
    [matched_at] DATETIME2 NULL,
    CONSTRAINT [pk_three_way_matching] PRIMARY KEY ([id]),
    CONSTRAINT [fk_twm_invoice] FOREIGN KEY ([invoice_id]) REFERENCES [dbo].[invoices] ([id]),
    CONSTRAINT [fk_twm_po] FOREIGN KEY ([purchase_order_id]) REFERENCES [dbo].[purchase_orders] ([id]),
    CONSTRAINT [fk_twm_receipt] FOREIGN KEY ([purchase_receipt_id]) REFERENCES [dbo].[purchase_receipts] ([id]),
    CONSTRAINT [fk_twm_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[products] ([id])
);

CREATE TABLE [dbo].[fixed_assets] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [asset_no] NVARCHAR(30) NOT NULL,
    [name] NVARCHAR(200) NOT NULL,
    [category] NVARCHAR(50) NOT NULL,
    [purchase_date] DATE NOT NULL,
    [purchase_amount] DECIMAL(18,2) NOT NULL,
    [salvage_value] DECIMAL(18,2) NULL,
    [useful_life_months] INT NOT NULL,
    [monthly_depreciation] DECIMAL(12,2) NULL,
    [accumulated_depreciation] DECIMAL(18,2) NULL,
    [net_book_value] DECIMAL(18,2) NULL,
    [department_id] BIGINT NOT NULL,
    [custodian_id] BIGINT NULL,
    [location] NVARCHAR(200) NULL,
    [status] NVARCHAR(50) NULL,
    [last_depreciation_date] DATE NULL,
    [created_at] DATETIME2 NULL,
    [updated_at] DATETIME2 NULL,
    CONSTRAINT [pk_fixed_assets] PRIMARY KEY ([id]),
    CONSTRAINT [fk_fa_dept] FOREIGN KEY ([department_id]) REFERENCES [dbo].[departments] ([id]),
    CONSTRAINT [fk_fa_custodian] FOREIGN KEY ([custodian_id]) REFERENCES [dbo].[employees] ([id])
);

CREATE TABLE [dbo].[depreciation_log] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [asset_id] BIGINT NOT NULL,
    [depreciation_date] DATE NOT NULL,
    [depreciation_amount] DECIMAL(12,2) NOT NULL,
    [before_accumulated] DECIMAL(18,2) NOT NULL,
    [after_accumulated] DECIMAL(18,2) NOT NULL,
    [before_net_value] DECIMAL(18,2) NOT NULL,
    [after_net_value] DECIMAL(18,2) NOT NULL,
    [voucher_id] BIGINT NULL,
    [created_at] DATETIME2 NULL,
    CONSTRAINT [pk_depreciation_log] PRIMARY KEY ([id]),
    CONSTRAINT [uk_asset_date] UNIQUE ([asset_id], [depreciation_date]),
    CONSTRAINT [fk_dl_asset] FOREIGN KEY ([asset_id]) REFERENCES [dbo].[fixed_assets] ([id])
);

CREATE TABLE [dbo].[boms] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [parent_product_id] BIGINT NOT NULL,
    [child_product_id] BIGINT NOT NULL,
    [quantity] DECIMAL(10,3) NOT NULL,
    [unit] NVARCHAR(20) NOT NULL,
    [scrap_rate] DECIMAL(5,4) NULL,
    [sort_order] INT NULL,
    [effective_date] DATE NOT NULL,
    [expiry_date] DATE NULL,
    [status] NVARCHAR(50) NULL,
    [created_at] DATETIME2 NULL,
    CONSTRAINT [pk_boms] PRIMARY KEY ([id]),
    CONSTRAINT [uk_parent_child] UNIQUE ([parent_product_id], [child_product_id], [effective_date]),
    CONSTRAINT [fk_bom_parent] FOREIGN KEY ([parent_product_id]) REFERENCES [dbo].[products] ([id]),
    CONSTRAINT [fk_bom_child] FOREIGN KEY ([child_product_id]) REFERENCES [dbo].[products] ([id])
);

CREATE TABLE [dbo].[work_orders] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [order_no] NVARCHAR(30) NOT NULL,
    [product_id] BIGINT NOT NULL,
    [bom_id] BIGINT NULL,
    [planned_quantity] INT NOT NULL,
    [completed_quantity] INT NULL,
    [rejected_quantity] INT NULL,
    [warehouse_id] BIGINT NOT NULL,
    [start_date] DATE NULL,
    [due_date] DATE NULL,
    [completed_date] DATE NULL,
    [status] NVARCHAR(50) NULL,
    [priority] NVARCHAR(50) NULL,
    [released_by] BIGINT NULL,
    [remark] NVARCHAR(500) NULL,
    [created_at] DATETIME2 NULL,
    [updated_at] DATETIME2 NULL,
    CONSTRAINT [pk_work_orders] PRIMARY KEY ([id]),
    CONSTRAINT [fk_wo_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[products] ([id]),
    CONSTRAINT [fk_wo_bom] FOREIGN KEY ([bom_id]) REFERENCES [dbo].[boms] ([id]),
    CONSTRAINT [fk_wo_wh] FOREIGN KEY ([warehouse_id]) REFERENCES [dbo].[warehouses] ([id])
);

CREATE TABLE [dbo].[work_order_materials] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [work_order_id] BIGINT NOT NULL,
    [product_id] BIGINT NOT NULL,
    [batch_id] BIGINT NULL,
    [required_qty] DECIMAL(10,3) NOT NULL,
    [issued_qty] DECIMAL(10,3) NULL,
    [returned_qty] DECIMAL(10,3) NULL,
    [actual_consumed] DECIMAL(10,3) NULL,
    [unit] NVARCHAR(20) NOT NULL,
    [status] NVARCHAR(50) NULL,
    CONSTRAINT [pk_work_order_materials] PRIMARY KEY ([id]),
    CONSTRAINT [fk_wom_wo] FOREIGN KEY ([work_order_id]) REFERENCES [dbo].[work_orders] ([id]),
    CONSTRAINT [fk_wom_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[products] ([id])
);

CREATE TABLE [dbo].[service_tickets] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [ticket_no] NVARCHAR(30) NOT NULL,
    [customer_id] BIGINT NOT NULL,
    [order_id] BIGINT NULL,
    [product_id] BIGINT NULL,
    [ticket_type] NVARCHAR(50) NOT NULL,
    [priority] NVARCHAR(50) NULL,
    [subject] NVARCHAR(200) NOT NULL,
    [description] NVARCHAR(MAX) NULL,
    [status] NVARCHAR(50) NULL,
    [assigned_to] BIGINT NULL,
    [resolution] NVARCHAR(500) NULL,
    [resolved_at] DATETIME2 NULL,
    [satisfaction_score] INT NULL,
    [created_at] DATETIME2 NULL,
    [updated_at] DATETIME2 NULL,
    CONSTRAINT [pk_service_tickets] PRIMARY KEY ([id]),
    CONSTRAINT [fk_st_customer] FOREIGN KEY ([customer_id]) REFERENCES [dbo].[customers] ([id]),
    CONSTRAINT [fk_st_order] FOREIGN KEY ([order_id]) REFERENCES [dbo].[sales_orders] ([id]),
    CONSTRAINT [fk_st_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[products] ([id]),
    CONSTRAINT [fk_st_assigned] FOREIGN KEY ([assigned_to]) REFERENCES [dbo].[employees] ([id])
);

CREATE TABLE [dbo].[contracts] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [contract_no] NVARCHAR(30) NOT NULL,
    [contract_type] NVARCHAR(50) NOT NULL,
    [party_type] NVARCHAR(50) NOT NULL,
    [party_id] BIGINT NOT NULL,
    [subject] NVARCHAR(300) NOT NULL,
    [total_amount] DECIMAL(18,2) NOT NULL,
    [currency] NVARCHAR(3) NULL,
    [signed_date] DATE NULL,
    [start_date] DATE NOT NULL,
    [end_date] DATE NOT NULL,
    [payment_terms] NVARCHAR(MAX) NULL,
    [delivery_terms] NVARCHAR(MAX) NULL,
    [status] NVARCHAR(50) NULL,
    [prepared_by] BIGINT NOT NULL,
    [approved_by] BIGINT NULL,
    [signed_by] NVARCHAR(100) NULL,
    [attachment_path] NVARCHAR(500) NULL,
    [remark] NVARCHAR(MAX) NULL,
    [created_at] DATETIME2 NULL,
    [updated_at] DATETIME2 NULL,
    CONSTRAINT [pk_contracts] PRIMARY KEY ([id]),
    CONSTRAINT [fk_ct_prepared] FOREIGN KEY ([prepared_by]) REFERENCES [dbo].[employees] ([id]),
    CONSTRAINT [fk_ct_approved] FOREIGN KEY ([approved_by]) REFERENCES [dbo].[employees] ([id])
);

CREATE TABLE [dbo].[contract_milestones] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [contract_id] BIGINT NOT NULL,
    [milestone_name] NVARCHAR(200) NOT NULL,
    [milestone_type] NVARCHAR(50) NOT NULL,
    [planned_date] DATE NOT NULL,
    [actual_date] DATE NULL,
    [amount] DECIMAL(18,2) NULL,
    [completion_pct] DECIMAL(5,2) NULL,
    [status] NVARCHAR(50) NULL,
    [responsible_person] BIGINT NULL,
    [remark] NVARCHAR(500) NULL,
    [created_at] DATETIME2 NULL,
    CONSTRAINT [pk_contract_milestones] PRIMARY KEY ([id]),
    CONSTRAINT [fk_cm_contract] FOREIGN KEY ([contract_id]) REFERENCES [dbo].[contracts] ([id])
);
