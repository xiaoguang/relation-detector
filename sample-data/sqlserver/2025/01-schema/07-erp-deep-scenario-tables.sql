-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

CREATE TABLE [dbo].[production_plans] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [plan_no] NVARCHAR(30) NOT NULL,
    [product_id] BIGINT NOT NULL,
    [warehouse_id] BIGINT NOT NULL,
    [plan_month] NVARCHAR(7) NOT NULL,
    [forecast_qty] INT NOT NULL,
    [confirmed_sales_qty] INT NULL,
    [safety_stock_qty] INT NULL,
    [planned_production_qty] INT NOT NULL,
    [status] NVARCHAR(50) NULL,
    [planner_id] BIGINT NOT NULL,
    [approved_by] BIGINT NULL,
    [approved_at] DATETIME2 NULL,
    [created_at] DATETIME2 NULL,
    CONSTRAINT [pk_production_plans] PRIMARY KEY ([id]),
    CONSTRAINT [fk_prod_plan_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[products] ([id]),
    CONSTRAINT [fk_prod_plan_warehouse] FOREIGN KEY ([warehouse_id]) REFERENCES [dbo].[warehouses] ([id]),
    CONSTRAINT [fk_prod_plan_planner] FOREIGN KEY ([planner_id]) REFERENCES [dbo].[employees] ([id]),
    CONSTRAINT [fk_prod_plan_approved] FOREIGN KEY ([approved_by]) REFERENCES [dbo].[employees] ([id])
);

CREATE TABLE [dbo].[mrp_runs] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [run_no] NVARCHAR(30) NOT NULL,
    [plan_id] BIGINT NOT NULL,
    [run_date] DATE NOT NULL,
    [demand_source] NVARCHAR(50) NOT NULL,
    [status] NVARCHAR(50) NULL,
    [created_by] BIGINT NOT NULL,
    [completed_at] DATETIME2 NULL,
    CONSTRAINT [pk_mrp_runs] PRIMARY KEY ([id]),
    CONSTRAINT [fk_mrp_run_plan] FOREIGN KEY ([plan_id]) REFERENCES [dbo].[production_plans] ([id]),
    CONSTRAINT [fk_mrp_run_created_by] FOREIGN KEY ([created_by]) REFERENCES [dbo].[employees] ([id])
);

CREATE TABLE [dbo].[mrp_run_items] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [run_id] BIGINT NOT NULL,
    [parent_product_id] BIGINT NOT NULL,
    [component_product_id] BIGINT NOT NULL,
    [gross_requirement] DECIMAL(18,4) NOT NULL,
    [on_hand_qty] DECIMAL(18,4) NULL,
    [reserved_qty] DECIMAL(18,4) NULL,
    [planned_receipt_qty] DECIMAL(18,4) NULL,
    [net_requirement] DECIMAL(18,4) NOT NULL,
    [suggested_order_qty] DECIMAL(18,4) NOT NULL,
    [suggested_supplier_id] BIGINT NULL,
    [suggested_due_date] DATE NULL,
    CONSTRAINT [pk_mrp_run_items] PRIMARY KEY ([id]),
    CONSTRAINT [fk_mrp_item_run] FOREIGN KEY ([run_id]) REFERENCES [dbo].[mrp_runs] ([id]),
    CONSTRAINT [fk_mrp_item_parent] FOREIGN KEY ([parent_product_id]) REFERENCES [dbo].[products] ([id]),
    CONSTRAINT [fk_mrp_item_component] FOREIGN KEY ([component_product_id]) REFERENCES [dbo].[products] ([id]),
    CONSTRAINT [fk_mrp_item_supplier] FOREIGN KEY ([suggested_supplier_id]) REFERENCES [dbo].[suppliers] ([id])
);

CREATE TABLE [dbo].[work_order_operations] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [work_order_id] BIGINT NOT NULL,
    [operation_id] BIGINT NOT NULL,
    [operation_seq] INT NOT NULL,
    [planned_start] DATETIME2 NULL,
    [planned_end] DATETIME2 NULL,
    [actual_start] DATETIME2 NULL,
    [actual_end] DATETIME2 NULL,
    [status] NVARCHAR(50) NULL,
    [assigned_employee_id] BIGINT NULL,
    [qualified_qty] INT NULL,
    [scrapped_qty] INT NULL,
    [rework_qty] INT NULL,
    CONSTRAINT [pk_work_order_operations] PRIMARY KEY ([id]),
    CONSTRAINT [uk_work_order_operation] UNIQUE ([work_order_id], [operation_seq]),
    CONSTRAINT [fk_wo_op_work_order] FOREIGN KEY ([work_order_id]) REFERENCES [dbo].[work_orders] ([id]),
    CONSTRAINT [fk_wo_op_operation] FOREIGN KEY ([operation_id]) REFERENCES [dbo].[production_operations] ([id]),
    CONSTRAINT [fk_wo_op_employee] FOREIGN KEY ([assigned_employee_id]) REFERENCES [dbo].[employees] ([id])
);

CREATE TABLE [dbo].[operation_reports] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [work_order_operation_id] BIGINT NOT NULL,
    [report_no] NVARCHAR(30) NOT NULL,
    [employee_id] BIGINT NOT NULL,
    [report_time] DATETIME2 NOT NULL,
    [input_qty] INT NOT NULL,
    [qualified_qty] INT NOT NULL,
    [scrapped_qty] INT NULL,
    [rework_qty] INT NULL,
    [labor_minutes] DECIMAL(10,2) NULL,
    [machine_minutes] DECIMAL(10,2) NULL,
    CONSTRAINT [pk_operation_reports] PRIMARY KEY ([id]),
    CONSTRAINT [fk_operation_report_op] FOREIGN KEY ([work_order_operation_id]) REFERENCES [dbo].[work_order_operations] ([id]),
    CONSTRAINT [fk_operation_report_employee] FOREIGN KEY ([employee_id]) REFERENCES [dbo].[employees] ([id])
);

CREATE TABLE [dbo].[material_issues] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [issue_no] NVARCHAR(30) NOT NULL,
    [work_order_id] BIGINT NOT NULL,
    [warehouse_id] BIGINT NOT NULL,
    [issue_date] DATE NOT NULL,
    [issued_by] BIGINT NOT NULL,
    [status] NVARCHAR(50) NULL,
    CONSTRAINT [pk_material_issues] PRIMARY KEY ([id]),
    CONSTRAINT [fk_material_issue_work_order] FOREIGN KEY ([work_order_id]) REFERENCES [dbo].[work_orders] ([id]),
    CONSTRAINT [fk_material_issue_warehouse] FOREIGN KEY ([warehouse_id]) REFERENCES [dbo].[warehouses] ([id]),
    CONSTRAINT [fk_material_issue_employee] FOREIGN KEY ([issued_by]) REFERENCES [dbo].[employees] ([id])
);

CREATE TABLE [dbo].[material_issue_items] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [issue_id] BIGINT NOT NULL,
    [product_id] BIGINT NOT NULL,
    [batch_id] BIGINT NULL,
    [required_qty] DECIMAL(18,4) NOT NULL,
    [issued_qty] DECIMAL(18,4) NOT NULL,
    [unit_cost] DECIMAL(18,4) NOT NULL,
    CONSTRAINT [pk_material_issue_items] PRIMARY KEY ([id]),
    CONSTRAINT [fk_material_issue_item_issue] FOREIGN KEY ([issue_id]) REFERENCES [dbo].[material_issues] ([id]),
    CONSTRAINT [fk_material_issue_item_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[products] ([id]),
    CONSTRAINT [fk_material_issue_item_batch] FOREIGN KEY ([batch_id]) REFERENCES [dbo].[product_batches] ([id])
);

CREATE TABLE [dbo].[finished_goods_receipts] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [receipt_no] NVARCHAR(30) NOT NULL,
    [work_order_id] BIGINT NOT NULL,
    [product_id] BIGINT NOT NULL,
    [batch_id] BIGINT NULL,
    [warehouse_id] BIGINT NOT NULL,
    [receipt_date] DATE NOT NULL,
    [received_qty] INT NOT NULL,
    [unit_cost] DECIMAL(18,4) NULL,
    [received_by] BIGINT NOT NULL,
    [status] NVARCHAR(50) NULL,
    CONSTRAINT [pk_finished_goods_receipts] PRIMARY KEY ([id]),
    CONSTRAINT [fk_fg_receipt_work_order] FOREIGN KEY ([work_order_id]) REFERENCES [dbo].[work_orders] ([id]),
    CONSTRAINT [fk_fg_receipt_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[products] ([id]),
    CONSTRAINT [fk_fg_receipt_batch] FOREIGN KEY ([batch_id]) REFERENCES [dbo].[product_batches] ([id]),
    CONSTRAINT [fk_fg_receipt_warehouse] FOREIGN KEY ([warehouse_id]) REFERENCES [dbo].[warehouses] ([id]),
    CONSTRAINT [fk_fg_receipt_employee] FOREIGN KEY ([received_by]) REFERENCES [dbo].[employees] ([id])
);

CREATE TABLE [dbo].[standard_costs] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [product_id] BIGINT NOT NULL,
    [cost_version] NVARCHAR(20) NOT NULL,
    [material_cost] DECIMAL(18,4) NULL,
    [labor_cost] DECIMAL(18,4) NULL,
    [overhead_cost] DECIMAL(18,4) NULL,
    [effective_from] DATE NOT NULL,
    [effective_to] DATE NULL,
    [status] NVARCHAR(50) NULL,
    [approved_by] BIGINT NULL,
    CONSTRAINT [pk_standard_costs] PRIMARY KEY ([id]),
    CONSTRAINT [uk_product_cost_version] UNIQUE ([product_id], [cost_version]),
    CONSTRAINT [fk_std_cost_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[products] ([id]),
    CONSTRAINT [fk_std_cost_approved] FOREIGN KEY ([approved_by]) REFERENCES [dbo].[employees] ([id])
);

CREATE TABLE [dbo].[inventory_cost_layers] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [product_id] BIGINT NOT NULL,
    [batch_id] BIGINT NULL,
    [warehouse_id] BIGINT NOT NULL,
    [source_type] NVARCHAR(50) NOT NULL,
    [source_id] BIGINT NOT NULL,
    [receipt_date] DATE NOT NULL,
    [original_qty] DECIMAL(18,4) NOT NULL,
    [remaining_qty] DECIMAL(18,4) NOT NULL,
    [unit_cost] DECIMAL(18,4) NOT NULL,
    [currency] NVARCHAR(3) NULL,
    CONSTRAINT [pk_inventory_cost_layers] PRIMARY KEY ([id]),
    CONSTRAINT [fk_cost_layer_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[products] ([id]),
    CONSTRAINT [fk_cost_layer_batch] FOREIGN KEY ([batch_id]) REFERENCES [dbo].[product_batches] ([id]),
    CONSTRAINT [fk_cost_layer_warehouse] FOREIGN KEY ([warehouse_id]) REFERENCES [dbo].[warehouses] ([id])
);

CREATE TABLE [dbo].[inventory_valuation_snapshots] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [snapshot_date] DATE NOT NULL,
    [product_id] BIGINT NOT NULL,
    [warehouse_id] BIGINT NOT NULL,
    [quantity] DECIMAL(18,4) NOT NULL,
    [unit_cost] DECIMAL(18,4) NOT NULL,
    [inventory_value] DECIMAL(18,2) NOT NULL,
    [valuation_method] NVARCHAR(50) NOT NULL,
    CONSTRAINT [pk_inventory_valuation_snapshots] PRIMARY KEY ([id]),
    CONSTRAINT [uk_inv_value_snapshot] UNIQUE ([snapshot_date], [product_id], [warehouse_id], [valuation_method]),
    CONSTRAINT [fk_inv_value_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[products] ([id]),
    CONSTRAINT [fk_inv_value_warehouse] FOREIGN KEY ([warehouse_id]) REFERENCES [dbo].[warehouses] ([id])
);

CREATE TABLE [dbo].[work_order_costs] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [work_order_id] BIGINT NOT NULL,
    [material_cost] DECIMAL(18,2) NULL,
    [labor_cost] DECIMAL(18,2) NULL,
    [overhead_cost] DECIMAL(18,2) NULL,
    [finished_qty] INT NULL,
    [unit_cost] DECIMAL(18,4) NULL,
    [variance_amount] DECIMAL(18,2) NULL,
    [calculated_at] DATETIME2 NULL,
    CONSTRAINT [pk_work_order_costs] PRIMARY KEY ([id]),
    CONSTRAINT [uk_work_order_cost] UNIQUE ([work_order_id]),
    CONSTRAINT [fk_work_order_cost_work_order] FOREIGN KEY ([work_order_id]) REFERENCES [dbo].[work_orders] ([id])
);

CREATE TABLE [dbo].[cogs_entries] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [sales_order_id] BIGINT NOT NULL,
    [sales_order_item_id] BIGINT NOT NULL,
    [product_id] BIGINT NOT NULL,
    [batch_id] BIGINT NULL,
    [quantity] DECIMAL(18,4) NOT NULL,
    [unit_cost] DECIMAL(18,4) NOT NULL,
    [cogs_amount] DECIMAL(18,2) NOT NULL,
    [voucher_id] BIGINT NULL,
    [posted_at] DATETIME2 NULL,
    CONSTRAINT [pk_cogs_entries] PRIMARY KEY ([id]),
    CONSTRAINT [fk_cogs_order] FOREIGN KEY ([sales_order_id]) REFERENCES [dbo].[sales_orders] ([id]),
    CONSTRAINT [fk_cogs_order_item] FOREIGN KEY ([sales_order_item_id]) REFERENCES [dbo].[sales_order_items] ([id]),
    CONSTRAINT [fk_cogs_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[products] ([id]),
    CONSTRAINT [fk_cogs_batch] FOREIGN KEY ([batch_id]) REFERENCES [dbo].[product_batches] ([id]),
    CONSTRAINT [fk_cogs_voucher] FOREIGN KEY ([voucher_id]) REFERENCES [dbo].[vouchers] ([id])
);

CREATE TABLE [dbo].[account_subjects] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [parent_id] BIGINT NULL,
    [subject_code] NVARCHAR(30) NOT NULL,
    [subject_name] NVARCHAR(100) NOT NULL,
    [subject_type] NVARCHAR(50) NOT NULL,
    [balance_direction] NVARCHAR(50) NOT NULL,
    [is_leaf] BIT NULL,
    [status] NVARCHAR(50) NULL,
    CONSTRAINT [pk_account_subjects] PRIMARY KEY ([id]),
    CONSTRAINT [fk_subject_parent] FOREIGN KEY ([parent_id]) REFERENCES [dbo].[account_subjects] ([id])
);

CREATE TABLE [dbo].[opening_balances] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [ledger_book_id] BIGINT NOT NULL,
    [subject_id] BIGINT NOT NULL,
    [period_code] NVARCHAR(7) NOT NULL,
    [debit_amount] DECIMAL(18,2) NULL,
    [credit_amount] DECIMAL(18,2) NULL,
    CONSTRAINT [pk_opening_balances] PRIMARY KEY ([id]),
    CONSTRAINT [uk_opening_balance] UNIQUE ([ledger_book_id], [subject_id], [period_code]),
    CONSTRAINT [fk_opening_balance_book] FOREIGN KEY ([ledger_book_id]) REFERENCES [dbo].[ledger_books] ([id]),
    CONSTRAINT [fk_opening_balance_subject] FOREIGN KEY ([subject_id]) REFERENCES [dbo].[account_subjects] ([id])
);

CREATE TABLE [dbo].[account_balances] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [ledger_book_id] BIGINT NOT NULL,
    [subject_id] BIGINT NOT NULL,
    [period_code] NVARCHAR(7) NOT NULL,
    [begin_debit] DECIMAL(18,2) NULL,
    [begin_credit] DECIMAL(18,2) NULL,
    [current_debit] DECIMAL(18,2) NULL,
    [current_credit] DECIMAL(18,2) NULL,
    [ending_debit] DECIMAL(18,2) NULL,
    [ending_credit] DECIMAL(18,2) NULL,
    CONSTRAINT [pk_account_balances] PRIMARY KEY ([id]),
    CONSTRAINT [uk_account_balance] UNIQUE ([ledger_book_id], [subject_id], [period_code]),
    CONSTRAINT [fk_account_balance_book] FOREIGN KEY ([ledger_book_id]) REFERENCES [dbo].[ledger_books] ([id]),
    CONSTRAINT [fk_account_balance_subject] FOREIGN KEY ([subject_id]) REFERENCES [dbo].[account_subjects] ([id])
);

CREATE TABLE [dbo].[budget_versions] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [ledger_book_id] BIGINT NOT NULL,
    [version_code] NVARCHAR(30) NOT NULL,
    [version_name] NVARCHAR(100) NOT NULL,
    [fiscal_year] INT NOT NULL,
    [status] NVARCHAR(50) NULL,
    [approved_by] BIGINT NULL,
    CONSTRAINT [pk_budget_versions] PRIMARY KEY ([id]),
    CONSTRAINT [uk_budget_version] UNIQUE ([ledger_book_id], [version_code]),
    CONSTRAINT [fk_budget_version_book] FOREIGN KEY ([ledger_book_id]) REFERENCES [dbo].[ledger_books] ([id]),
    CONSTRAINT [fk_budget_version_approved] FOREIGN KEY ([approved_by]) REFERENCES [dbo].[employees] ([id])
);

CREATE TABLE [dbo].[budget_items] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [version_id] BIGINT NOT NULL,
    [department_id] BIGINT NOT NULL,
    [subject_id] BIGINT NOT NULL,
    [period_code] NVARCHAR(7) NOT NULL,
    [budget_amount] DECIMAL(18,2) NOT NULL,
    [used_amount] DECIMAL(18,2) NULL,
    CONSTRAINT [pk_budget_items] PRIMARY KEY ([id]),
    CONSTRAINT [uk_budget_item] UNIQUE ([version_id], [department_id], [subject_id], [period_code]),
    CONSTRAINT [fk_budget_item_version] FOREIGN KEY ([version_id]) REFERENCES [dbo].[budget_versions] ([id]),
    CONSTRAINT [fk_budget_item_department] FOREIGN KEY ([department_id]) REFERENCES [dbo].[departments] ([id]),
    CONSTRAINT [fk_budget_item_subject] FOREIGN KEY ([subject_id]) REFERENCES [dbo].[account_subjects] ([id])
);

CREATE TABLE [dbo].[ar_invoices] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [ar_no] NVARCHAR(30) NOT NULL,
    [sales_order_id] BIGINT NOT NULL,
    [customer_id] BIGINT NOT NULL,
    [invoice_date] DATE NOT NULL,
    [due_date] DATE NOT NULL,
    [invoice_amount] DECIMAL(18,2) NOT NULL,
    [paid_amount] DECIMAL(18,2) NULL,
    [writeoff_amount] DECIMAL(18,2) NULL,
    [status] NVARCHAR(50) NULL,
    CONSTRAINT [pk_ar_invoices] PRIMARY KEY ([id]),
    CONSTRAINT [fk_ar_invoice_order] FOREIGN KEY ([sales_order_id]) REFERENCES [dbo].[sales_orders] ([id]),
    CONSTRAINT [fk_ar_invoice_customer] FOREIGN KEY ([customer_id]) REFERENCES [dbo].[customers] ([id])
);

CREATE TABLE [dbo].[ap_invoices] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [ap_no] NVARCHAR(30) NOT NULL,
    [purchase_order_id] BIGINT NOT NULL,
    [supplier_id] BIGINT NOT NULL,
    [invoice_date] DATE NOT NULL,
    [due_date] DATE NOT NULL,
    [invoice_amount] DECIMAL(18,2) NOT NULL,
    [paid_amount] DECIMAL(18,2) NULL,
    [status] NVARCHAR(50) NULL,
    CONSTRAINT [pk_ap_invoices] PRIMARY KEY ([id]),
    CONSTRAINT [fk_ap_invoice_order] FOREIGN KEY ([purchase_order_id]) REFERENCES [dbo].[purchase_orders] ([id]),
    CONSTRAINT [fk_ap_invoice_supplier] FOREIGN KEY ([supplier_id]) REFERENCES [dbo].[suppliers] ([id])
);

CREATE TABLE [dbo].[payment_requests] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [request_no] NVARCHAR(30) NOT NULL,
    [supplier_id] BIGINT NOT NULL,
    [requested_by] BIGINT NOT NULL,
    [request_date] DATE NOT NULL,
    [planned_pay_date] DATE NOT NULL,
    [total_amount] DECIMAL(18,2) NULL,
    [status] NVARCHAR(50) NULL,
    CONSTRAINT [pk_payment_requests] PRIMARY KEY ([id]),
    CONSTRAINT [fk_payment_request_supplier] FOREIGN KEY ([supplier_id]) REFERENCES [dbo].[suppliers] ([id]),
    CONSTRAINT [fk_payment_request_employee] FOREIGN KEY ([requested_by]) REFERENCES [dbo].[employees] ([id])
);

CREATE TABLE [dbo].[payment_request_items] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [request_id] BIGINT NOT NULL,
    [ap_invoice_id] BIGINT NOT NULL,
    [requested_amount] DECIMAL(18,2) NOT NULL,
    CONSTRAINT [pk_payment_request_items] PRIMARY KEY ([id]),
    CONSTRAINT [fk_payment_request_item_request] FOREIGN KEY ([request_id]) REFERENCES [dbo].[payment_requests] ([id]),
    CONSTRAINT [fk_payment_request_item_ap] FOREIGN KEY ([ap_invoice_id]) REFERENCES [dbo].[ap_invoices] ([id])
);

CREATE TABLE [dbo].[warehouse_zones] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [warehouse_id] BIGINT NOT NULL,
    [zone_code] NVARCHAR(30) NOT NULL,
    [zone_name] NVARCHAR(100) NOT NULL,
    [zone_type] NVARCHAR(50) NOT NULL,
    CONSTRAINT [pk_warehouse_zones] PRIMARY KEY ([id]),
    CONSTRAINT [uk_zone] UNIQUE ([warehouse_id], [zone_code]),
    CONSTRAINT [fk_zone_warehouse] FOREIGN KEY ([warehouse_id]) REFERENCES [dbo].[warehouses] ([id])
);

CREATE TABLE [dbo].[warehouse_locations] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [zone_id] BIGINT NOT NULL,
    [location_code] NVARCHAR(50) NOT NULL,
    [location_type] NVARCHAR(50) NULL,
    [max_weight_kg] DECIMAL(12,3) NULL,
    [max_volume_m3] DECIMAL(12,6) NULL,
    [status] NVARCHAR(50) NULL,
    CONSTRAINT [pk_warehouse_locations] PRIMARY KEY ([id]),
    CONSTRAINT [uk_location] UNIQUE ([zone_id], [location_code]),
    CONSTRAINT [fk_location_zone] FOREIGN KEY ([zone_id]) REFERENCES [dbo].[warehouse_zones] ([id])
);

CREATE TABLE [dbo].[inventory_location_balances] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [location_id] BIGINT NOT NULL,
    [product_id] BIGINT NOT NULL,
    [batch_id] BIGINT NULL,
    [quantity] DECIMAL(18,4) NOT NULL,
    [locked_quantity] DECIMAL(18,4) NULL,
    CONSTRAINT [pk_inventory_location_balances] PRIMARY KEY ([id]),
    CONSTRAINT [uk_location_product_batch] UNIQUE ([location_id], [product_id], [batch_id]),
    CONSTRAINT [fk_loc_balance_location] FOREIGN KEY ([location_id]) REFERENCES [dbo].[warehouse_locations] ([id]),
    CONSTRAINT [fk_loc_balance_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[products] ([id]),
    CONSTRAINT [fk_loc_balance_batch] FOREIGN KEY ([batch_id]) REFERENCES [dbo].[product_batches] ([id])
);

CREATE TABLE [dbo].[putaway_tasks] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [task_no] NVARCHAR(30) NOT NULL,
    [receipt_id] BIGINT NOT NULL,
    [product_id] BIGINT NOT NULL,
    [batch_id] BIGINT NULL,
    [from_location_id] BIGINT NULL,
    [to_location_id] BIGINT NOT NULL,
    [quantity] DECIMAL(18,4) NOT NULL,
    [assigned_to] BIGINT NULL,
    [status] NVARCHAR(50) NULL,
    CONSTRAINT [pk_putaway_tasks] PRIMARY KEY ([id]),
    CONSTRAINT [fk_putaway_receipt] FOREIGN KEY ([receipt_id]) REFERENCES [dbo].[purchase_receipts] ([id]),
    CONSTRAINT [fk_putaway_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[products] ([id]),
    CONSTRAINT [fk_putaway_batch] FOREIGN KEY ([batch_id]) REFERENCES [dbo].[product_batches] ([id]),
    CONSTRAINT [fk_putaway_to_location] FOREIGN KEY ([to_location_id]) REFERENCES [dbo].[warehouse_locations] ([id]),
    CONSTRAINT [fk_putaway_employee] FOREIGN KEY ([assigned_to]) REFERENCES [dbo].[employees] ([id])
);

CREATE TABLE [dbo].[picking_tasks] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [task_no] NVARCHAR(30) NOT NULL,
    [sales_order_id] BIGINT NOT NULL,
    [warehouse_id] BIGINT NOT NULL,
    [wave_no] NVARCHAR(30) NULL,
    [assigned_to] BIGINT NULL,
    [status] NVARCHAR(50) NULL,
    CONSTRAINT [pk_picking_tasks] PRIMARY KEY ([id]),
    CONSTRAINT [fk_picking_order] FOREIGN KEY ([sales_order_id]) REFERENCES [dbo].[sales_orders] ([id]),
    CONSTRAINT [fk_picking_warehouse] FOREIGN KEY ([warehouse_id]) REFERENCES [dbo].[warehouses] ([id]),
    CONSTRAINT [fk_picking_employee] FOREIGN KEY ([assigned_to]) REFERENCES [dbo].[employees] ([id])
);

CREATE TABLE [dbo].[picking_task_items] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [picking_task_id] BIGINT NOT NULL,
    [sales_order_item_id] BIGINT NOT NULL,
    [product_id] BIGINT NOT NULL,
    [batch_id] BIGINT NULL,
    [location_id] BIGINT NOT NULL,
    [required_qty] DECIMAL(18,4) NOT NULL,
    [picked_qty] DECIMAL(18,4) NULL,
    CONSTRAINT [pk_picking_task_items] PRIMARY KEY ([id]),
    CONSTRAINT [fk_picking_item_task] FOREIGN KEY ([picking_task_id]) REFERENCES [dbo].[picking_tasks] ([id]),
    CONSTRAINT [fk_picking_item_order_item] FOREIGN KEY ([sales_order_item_id]) REFERENCES [dbo].[sales_order_items] ([id]),
    CONSTRAINT [fk_picking_item_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[products] ([id]),
    CONSTRAINT [fk_picking_item_batch] FOREIGN KEY ([batch_id]) REFERENCES [dbo].[product_batches] ([id]),
    CONSTRAINT [fk_picking_item_location] FOREIGN KEY ([location_id]) REFERENCES [dbo].[warehouse_locations] ([id])
);

CREATE TABLE [dbo].[repair_orders] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [repair_no] NVARCHAR(30) NOT NULL,
    [service_ticket_id] BIGINT NOT NULL,
    [customer_id] BIGINT NOT NULL,
    [product_id] BIGINT NOT NULL,
    [serial_number_id] BIGINT NULL,
    [received_date] DATE NOT NULL,
    [fault_desc] NVARCHAR(500) NOT NULL,
    [status] NVARCHAR(50) NULL,
    [technician_id] BIGINT NULL,
    [estimated_cost] DECIMAL(18,2) NULL,
    [actual_cost] DECIMAL(18,2) NULL,
    CONSTRAINT [pk_repair_orders] PRIMARY KEY ([id]),
    CONSTRAINT [fk_repair_ticket] FOREIGN KEY ([service_ticket_id]) REFERENCES [dbo].[service_tickets] ([id]),
    CONSTRAINT [fk_repair_customer] FOREIGN KEY ([customer_id]) REFERENCES [dbo].[customers] ([id]),
    CONSTRAINT [fk_repair_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[products] ([id]),
    CONSTRAINT [fk_repair_serial] FOREIGN KEY ([serial_number_id]) REFERENCES [dbo].[serial_numbers] ([id]),
    CONSTRAINT [fk_repair_technician] FOREIGN KEY ([technician_id]) REFERENCES [dbo].[employees] ([id])
);

CREATE TABLE [dbo].[repair_order_parts] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [repair_order_id] BIGINT NOT NULL,
    [product_id] BIGINT NOT NULL,
    [batch_id] BIGINT NULL,
    [quantity] DECIMAL(18,4) NOT NULL,
    [unit_cost] DECIMAL(18,4) NOT NULL,
    [issued_from_warehouse_id] BIGINT NOT NULL,
    CONSTRAINT [pk_repair_order_parts] PRIMARY KEY ([id]),
    CONSTRAINT [fk_repair_part_order] FOREIGN KEY ([repair_order_id]) REFERENCES [dbo].[repair_orders] ([id]),
    CONSTRAINT [fk_repair_part_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[products] ([id]),
    CONSTRAINT [fk_repair_part_batch] FOREIGN KEY ([batch_id]) REFERENCES [dbo].[product_batches] ([id]),
    CONSTRAINT [fk_repair_part_warehouse] FOREIGN KEY ([issued_from_warehouse_id]) REFERENCES [dbo].[warehouses] ([id])
);

CREATE TABLE [dbo].[numbering_rules] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [document_type] NVARCHAR(50) NOT NULL,
    [prefix] NVARCHAR(20) NOT NULL,
    [date_pattern] NVARCHAR(20) NULL,
    [sequence_length] INT NULL,
    [current_sequence] INT NULL,
    [updated_at] DATETIME2 NULL,
    CONSTRAINT [pk_numbering_rules] PRIMARY KEY ([id])
);

CREATE TABLE [dbo].[master_data_change_requests] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [request_no] NVARCHAR(30) NOT NULL,
    [master_type] NVARCHAR(50) NOT NULL,
    [master_id] BIGINT NOT NULL,
    [change_reason] NVARCHAR(500) NOT NULL,
    [requested_by] BIGINT NOT NULL,
    [requested_at] DATETIME2 NULL,
    [approved_by] BIGINT NULL,
    [approved_at] DATETIME2 NULL,
    [status] NVARCHAR(50) NULL,
    CONSTRAINT [pk_master_data_change_requests] PRIMARY KEY ([id]),
    CONSTRAINT [fk_mdc_requested_by] FOREIGN KEY ([requested_by]) REFERENCES [dbo].[employees] ([id]),
    CONSTRAINT [fk_mdc_approved_by] FOREIGN KEY ([approved_by]) REFERENCES [dbo].[employees] ([id])
);

CREATE TABLE [dbo].[master_data_change_items] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [request_id] BIGINT NOT NULL,
    [field_name] NVARCHAR(100) NOT NULL,
    [old_value] NVARCHAR(1000) NULL,
    [new_value] NVARCHAR(1000) NULL,
    CONSTRAINT [pk_master_data_change_items] PRIMARY KEY ([id]),
    CONSTRAINT [fk_mdc_item_request] FOREIGN KEY ([request_id]) REFERENCES [dbo].[master_data_change_requests] ([id])
);

CREATE TABLE [dbo].[data_permission_scopes] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [role_id] BIGINT NOT NULL,
    [scope_type] NVARCHAR(50) NOT NULL,
    [scope_id] BIGINT NOT NULL,
    [can_read] BIT NULL,
    [can_write] BIT NULL,
    CONSTRAINT [pk_data_permission_scopes] PRIMARY KEY ([id]),
    CONSTRAINT [uk_data_scope] UNIQUE ([role_id], [scope_type], [scope_id]),
    CONSTRAINT [fk_data_scope_role] FOREIGN KEY ([role_id]) REFERENCES [dbo].[roles] ([id])
);

CREATE TABLE [dbo].[sensitive_access_logs] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [employee_id] BIGINT NOT NULL,
    [object_type] NVARCHAR(50) NOT NULL,
    [object_id] BIGINT NOT NULL,
    [field_name] NVARCHAR(100) NOT NULL,
    [access_reason] NVARCHAR(500) NULL,
    [accessed_at] DATETIME2 NULL,
    CONSTRAINT [pk_sensitive_access_logs] PRIMARY KEY ([id]),
    CONSTRAINT [fk_sensitive_access_employee] FOREIGN KEY ([employee_id]) REFERENCES [dbo].[employees] ([id])
);

CREATE TABLE [dbo].[region_dim] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [region_code] NVARCHAR(30) NOT NULL,
    [region_name] NVARCHAR(100) NOT NULL,
    [province] NVARCHAR(50) NOT NULL,
    [city] NVARCHAR(50) NULL,
    [district] NVARCHAR(50) NULL,
    [sales_region] NVARCHAR(100) NOT NULL,
    [region_level] NVARCHAR(50) NULL,
    [is_active] BIT NULL,
    CONSTRAINT [pk_region_dim] PRIMARY KEY ([id]),
    CONSTRAINT [uk_region_location] UNIQUE ([province], [city], [district])
);

CREATE TABLE [dbo].[fiscal_calendar] (
    [calendar_date] DATE NOT NULL,
    [fiscal_year] INT NOT NULL,
    [fiscal_quarter] INT NOT NULL,
    [fiscal_month] INT NOT NULL,
    [fiscal_month_name] NVARCHAR(20) NOT NULL,
    [period_code] NVARCHAR(7) NOT NULL,
    [period_start] DATE NOT NULL,
    [period_end] DATE NOT NULL,
    [is_current_fiscal_year] BIT NULL,
    [accounting_period_id] BIGINT NULL,
    CONSTRAINT [pk_fiscal_calendar] PRIMARY KEY ([calendar_date]),
    CONSTRAINT [fk_fiscal_calendar_period] FOREIGN KEY ([accounting_period_id]) REFERENCES [dbo].[accounting_periods] ([id])
);

CREATE TABLE [dbo].[category_dim] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [source_category_id] BIGINT NOT NULL,
    [category_code] NVARCHAR(50) NOT NULL,
    [level1_name] NVARCHAR(100) NOT NULL,
    [level2_name] NVARCHAR(100) NULL,
    [leaf_name] NVARCHAR(100) NOT NULL,
    [is_womenwear] BIT NULL,
    [effective_from] DATE NOT NULL,
    [effective_to] DATE NULL,
    [status] NVARCHAR(50) NULL,
    CONSTRAINT [pk_category_dim] PRIMARY KEY ([id]),
    CONSTRAINT [uk_category_dim_source] UNIQUE ([source_category_id]),
    CONSTRAINT [fk_category_dim_source] FOREIGN KEY ([source_category_id]) REFERENCES [dbo].[product_categories] ([id])
);

CREATE TABLE [dbo].[payments] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [payment_no] NVARCHAR(40) NOT NULL,
    [customer_id] BIGINT NOT NULL,
    [order_id] BIGINT NULL,
    [receipt_id] BIGINT NULL,
    [journal_id] BIGINT NULL,
    [payment_date] DATE NOT NULL,
    [amount] DECIMAL(18,2) NOT NULL,
    [currency] NVARCHAR(3) NULL,
    [payment_method] NVARCHAR(30) NOT NULL,
    [payment_status] NVARCHAR(50) NULL,
    [failure_reason] NVARCHAR(300) NULL,
    [created_at] DATETIME2 NULL,
    CONSTRAINT [pk_payments] PRIMARY KEY ([id]),
    CONSTRAINT [fk_payment_customer] FOREIGN KEY ([customer_id]) REFERENCES [dbo].[customers] ([id]),
    CONSTRAINT [fk_payment_order] FOREIGN KEY ([order_id]) REFERENCES [dbo].[sales_orders] ([id]),
    CONSTRAINT [fk_payment_receipt] FOREIGN KEY ([receipt_id]) REFERENCES [dbo].[payment_receipts] ([id]),
    CONSTRAINT [fk_payment_journal] FOREIGN KEY ([journal_id]) REFERENCES [dbo].[cashier_journals] ([id])
);

CREATE TABLE [dbo].[sales_fact] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [order_id] BIGINT NOT NULL,
    [order_item_id] BIGINT NOT NULL,
    [customer_id] BIGINT NOT NULL,
    [product_id] BIGINT NOT NULL,
    [category_dim_id] BIGINT NOT NULL,
    [warehouse_id] BIGINT NOT NULL,
    [region_dim_id] BIGINT NOT NULL,
    [fiscal_date] DATE NOT NULL,
    [payment_id] BIGINT NULL,
    [quantity_sold] DECIMAL(18,4) NOT NULL,
    [sales_amount] DECIMAL(18,2) NOT NULL,
    [paid_amount] DECIMAL(18,2) NULL,
    [refund_amount] DECIMAL(18,2) NULL,
    [net_sales_amount] DECIMAL(18,2) NOT NULL,
    [gross_margin_amount] DECIMAL(18,2) NULL,
    [order_status] NVARCHAR(30) NOT NULL,
    [sales_channel] NVARCHAR(30) NULL,
    [created_at] DATETIME2 NULL,
    CONSTRAINT [pk_sales_fact] PRIMARY KEY ([id]),
    CONSTRAINT [uk_sales_fact_item] UNIQUE ([order_item_id]),
    CONSTRAINT [fk_sales_fact_order] FOREIGN KEY ([order_id]) REFERENCES [dbo].[sales_orders] ([id]),
    CONSTRAINT [fk_sales_fact_item] FOREIGN KEY ([order_item_id]) REFERENCES [dbo].[sales_order_items] ([id]),
    CONSTRAINT [fk_sales_fact_customer] FOREIGN KEY ([customer_id]) REFERENCES [dbo].[customers] ([id]),
    CONSTRAINT [fk_sales_fact_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[products] ([id]),
    CONSTRAINT [fk_sales_fact_category] FOREIGN KEY ([category_dim_id]) REFERENCES [dbo].[category_dim] ([id]),
    CONSTRAINT [fk_sales_fact_warehouse] FOREIGN KEY ([warehouse_id]) REFERENCES [dbo].[warehouses] ([id]),
    CONSTRAINT [fk_sales_fact_region] FOREIGN KEY ([region_dim_id]) REFERENCES [dbo].[region_dim] ([id]),
    CONSTRAINT [fk_sales_fact_fiscal] FOREIGN KEY ([fiscal_date]) REFERENCES [dbo].[fiscal_calendar] ([calendar_date]),
    CONSTRAINT [fk_sales_fact_payment] FOREIGN KEY ([payment_id]) REFERENCES [dbo].[payments] ([id])
);
