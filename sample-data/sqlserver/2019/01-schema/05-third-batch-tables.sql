-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

CREATE TABLE [dbo].[ar_aging_snapshots] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [snapshot_date] DATE NOT NULL,
    [customer_id] BIGINT NOT NULL,
    [order_id] BIGINT NOT NULL,
    [invoice_amount] DECIMAL(18,2) NOT NULL,
    [paid_amount] DECIMAL(18,2) NULL,
    [outstanding_amount] DECIMAL(18,2) NULL,
    [due_date] DATE NOT NULL,
    [aging_days] INT NULL,
    [aging_bucket] NVARCHAR(20) NULL,
    [bad_debt_provision] DECIMAL(18,2) NULL,
    [last_collection_date] DATE NULL,
    [collection_notes] NVARCHAR(500) NULL,
    CONSTRAINT [pk_ar_aging_snapshots] PRIMARY KEY ([id]),
    CONSTRAINT [fk_ar_customer] FOREIGN KEY ([customer_id]) REFERENCES [dbo].[customers] ([id]),
    CONSTRAINT [fk_ar_order] FOREIGN KEY ([order_id]) REFERENCES [dbo].[sales_orders] ([id])
);

CREATE TABLE [dbo].[ap_aging_snapshots] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [snapshot_date] DATE NOT NULL,
    [supplier_id] BIGINT NOT NULL,
    [order_id] BIGINT NOT NULL,
    [invoice_amount] DECIMAL(18,2) NOT NULL,
    [paid_amount] DECIMAL(18,2) NULL,
    [outstanding_amount] DECIMAL(18,2) NULL,
    [due_date] DATE NOT NULL,
    [aging_bucket] NVARCHAR(20) NULL,
    [planned_payment_date] DATE NULL,
    CONSTRAINT [pk_ap_aging_snapshots] PRIMARY KEY ([id]),
    CONSTRAINT [fk_ap_supplier] FOREIGN KEY ([supplier_id]) REFERENCES [dbo].[suppliers] ([id]),
    CONSTRAINT [fk_ap_order] FOREIGN KEY ([order_id]) REFERENCES [dbo].[purchase_orders] ([id])
);

CREATE TABLE [dbo].[tax_invoices] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [invoice_no] NVARCHAR(50) NOT NULL,
    [invoice_code] NVARCHAR(50) NULL,
    [invoice_type] NVARCHAR(50) NOT NULL,
    [tax_direction] NVARCHAR(50) NOT NULL,
    [party_type] NVARCHAR(50) NOT NULL,
    [party_id] BIGINT NOT NULL,
    [invoice_date] DATE NOT NULL,
    [amount_excluding_tax] DECIMAL(18,2) NOT NULL,
    [tax_rate] DECIMAL(5,4) NOT NULL,
    [tax_amount] DECIMAL(18,2) NULL,
    [amount_including_tax] DECIMAL(18,2) NULL,
    [verification_status] NVARCHAR(50) NULL,
    [verified_at] DATETIME2 NULL,
    [verified_by] BIGINT NULL,
    [tax_period] NVARCHAR(7) NOT NULL,
    [deduction_period] NVARCHAR(7) NULL,
    [reference_type] NVARCHAR(50) NULL,
    [reference_id] BIGINT NULL,
    [status] NVARCHAR(50) NULL,
    [remark] NVARCHAR(500) NULL,
    [created_at] DATETIME2 NULL,
    [updated_at] DATETIME2 NULL,
    CONSTRAINT [pk_tax_invoices] PRIMARY KEY ([id]),
    CONSTRAINT [fk_ti_verified] FOREIGN KEY ([verified_by]) REFERENCES [dbo].[employees] ([id])
);

CREATE TABLE [dbo].[tax_filings] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [tax_period] NVARCHAR(7) NOT NULL,
    [tax_type] NVARCHAR(50) NOT NULL,
    [output_tax] DECIMAL(18,2) NULL,
    [input_tax] DECIMAL(18,2) NULL,
    [input_tax_transfer] DECIMAL(18,2) NULL,
    [tax_payable] DECIMAL(18,2) NULL,
    [tax_paid] DECIMAL(18,2) NULL,
    [filing_date] DATE NULL,
    [filing_deadline] DATE NOT NULL,
    [status] NVARCHAR(50) NULL,
    [prepared_by] BIGINT NULL,
    [voucher_id] BIGINT NULL,
    [remark] NVARCHAR(500) NULL,
    [created_at] DATETIME2 NULL,
    [updated_at] DATETIME2 NULL,
    CONSTRAINT [pk_tax_filings] PRIMARY KEY ([id]),
    CONSTRAINT [uk_period_type] UNIQUE ([tax_period], [tax_type]),
    CONSTRAINT [fk_tf_prepared] FOREIGN KEY ([prepared_by]) REFERENCES [dbo].[employees] ([id])
);

CREATE TABLE [dbo].[inspection_standards] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [product_id] BIGINT NOT NULL,
    [standard_name] NVARCHAR(200) NOT NULL,
    [inspection_items] NVARCHAR(MAX) NULL,
    [sampling_method] NVARCHAR(50) NULL,
    [sample_size] INT NULL,
    [aql_level] DECIMAL(5,2) NULL,
    [status] NVARCHAR(50) NULL,
    [created_at] DATETIME2 NULL,
    CONSTRAINT [pk_inspection_standards] PRIMARY KEY ([id]),
    CONSTRAINT [uk_product_standard] UNIQUE ([product_id], [standard_name]),
    CONSTRAINT [fk_is_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[products] ([id])
);

CREATE TABLE [dbo].[inspection_reports] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [report_no] NVARCHAR(30) NOT NULL,
    [inspection_type] NVARCHAR(50) NOT NULL,
    [reference_type] NVARCHAR(50) NOT NULL,
    [reference_id] BIGINT NOT NULL,
    [product_id] BIGINT NOT NULL,
    [batch_id] BIGINT NULL,
    [standard_id] BIGINT NULL,
    [sample_size] INT NOT NULL,
    [inspected_qty] INT NOT NULL,
    [qualified_qty] INT NULL,
    [defective_qty] INT NULL,
    [defect_rate] DECIMAL(5,2) NULL,
    [inspection_result] NVARCHAR(50) NOT NULL,
    [inspector_id] BIGINT NOT NULL,
    [inspection_date] DATE NOT NULL,
    [defect_description] NVARCHAR(MAX) NULL,
    [disposition] NVARCHAR(200) NULL,
    [status] NVARCHAR(50) NULL,
    [created_at] DATETIME2 NULL,
    [updated_at] DATETIME2 NULL,
    CONSTRAINT [pk_inspection_reports] PRIMARY KEY ([id]),
    CONSTRAINT [fk_ir_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[products] ([id]),
    CONSTRAINT [fk_ir_batch] FOREIGN KEY ([batch_id]) REFERENCES [dbo].[product_batches] ([id]),
    CONSTRAINT [fk_ir_standard] FOREIGN KEY ([standard_id]) REFERENCES [dbo].[inspection_standards] ([id]),
    CONSTRAINT [fk_ir_inspector] FOREIGN KEY ([inspector_id]) REFERENCES [dbo].[employees] ([id])
);

CREATE TABLE [dbo].[approval_workflows] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [workflow_name] NVARCHAR(100) NOT NULL,
    [workflow_code] NVARCHAR(30) NOT NULL,
    [target_type] NVARCHAR(50) NOT NULL,
    [description] NVARCHAR(500) NULL,
    [is_active] BIT NULL,
    [created_at] DATETIME2 NULL,
    CONSTRAINT [pk_approval_workflows] PRIMARY KEY ([id])
);

CREATE TABLE [dbo].[approval_nodes] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [workflow_id] BIGINT NOT NULL,
    [node_name] NVARCHAR(100) NOT NULL,
    [node_level] INT NOT NULL,
    [approver_type] NVARCHAR(50) NOT NULL,
    [approver_id] BIGINT NULL,
    [approval_mode] NVARCHAR(50) NULL,
    [timeout_hours] INT NULL,
    [can_delegate] BIT NULL,
    [created_at] DATETIME2 NULL,
    CONSTRAINT [pk_approval_nodes] PRIMARY KEY ([id]),
    CONSTRAINT [uk_workflow_level] UNIQUE ([workflow_id], [node_level]),
    CONSTRAINT [fk_an_workflow] FOREIGN KEY ([workflow_id]) REFERENCES [dbo].[approval_workflows] ([id])
);

CREATE TABLE [dbo].[approval_instances] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [instance_no] NVARCHAR(30) NOT NULL,
    [workflow_id] BIGINT NOT NULL,
    [target_type] NVARCHAR(50) NOT NULL,
    [target_id] BIGINT NOT NULL,
    [target_summary] NVARCHAR(500) NULL,
    [current_node_level] INT NULL,
    [total_nodes] INT NULL,
    [submitted_by] BIGINT NOT NULL,
    [submitted_at] DATETIME2 NULL,
    [status] NVARCHAR(50) NULL,
    [completed_at] DATETIME2 NULL,
    [remark] NVARCHAR(500) NULL,
    CONSTRAINT [pk_approval_instances] PRIMARY KEY ([id]),
    CONSTRAINT [fk_ai_workflow] FOREIGN KEY ([workflow_id]) REFERENCES [dbo].[approval_workflows] ([id]),
    CONSTRAINT [fk_ai_submitted] FOREIGN KEY ([submitted_by]) REFERENCES [dbo].[employees] ([id])
);

CREATE TABLE [dbo].[approval_records] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [instance_id] BIGINT NOT NULL,
    [node_id] BIGINT NOT NULL,
    [approver_id] BIGINT NOT NULL,
    [action] NVARCHAR(50) NOT NULL,
    [comment] NVARCHAR(500) NULL,
    [action_at] DATETIME2 NULL,
    [delegated_to] BIGINT NULL,
    CONSTRAINT [pk_approval_records] PRIMARY KEY ([id]),
    CONSTRAINT [fk_ar_instance] FOREIGN KEY ([instance_id]) REFERENCES [dbo].[approval_instances] ([id]),
    CONSTRAINT [fk_ar_node] FOREIGN KEY ([node_id]) REFERENCES [dbo].[approval_nodes] ([id]),
    CONSTRAINT [fk_ar_approver] FOREIGN KEY ([approver_id]) REFERENCES [dbo].[employees] ([id])
);

CREATE TABLE [dbo].[cash_flow_forecasts] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [forecast_date] DATE NOT NULL,
    [forecast_type] NVARCHAR(50) NOT NULL,
    [beginning_balance] DECIMAL(18,2) NULL,
    [expected_collections] DECIMAL(18,2) NULL,
    [expected_payments] DECIMAL(18,2) NULL,
    [expected_salary] DECIMAL(18,2) NULL,
    [expected_tax] DECIMAL(18,2) NULL,
    [other_income] DECIMAL(18,2) NULL,
    [other_expense] DECIMAL(18,2) NULL,
    [net_cash_flow] DECIMAL(18,2) NULL,
    [ending_balance] DECIMAL(18,2) NULL,
    [created_at] DATETIME2 NULL,
    CONSTRAINT [pk_cash_flow_forecasts] PRIMARY KEY ([id]),
    CONSTRAINT [uk_forecast_date_type] UNIQUE ([forecast_date], [forecast_type])
);

CREATE TABLE [dbo].[projects] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [project_no] NVARCHAR(30) NOT NULL,
    [name] NVARCHAR(200) NOT NULL,
    [project_type] NVARCHAR(50) NOT NULL,
    [department_id] BIGINT NOT NULL,
    [manager_id] BIGINT NOT NULL,
    [budget] DECIMAL(18,2) NOT NULL,
    [start_date] DATE NOT NULL,
    [planned_end_date] DATE NOT NULL,
    [actual_end_date] DATE NULL,
    [status] NVARCHAR(50) NULL,
    [priority] NVARCHAR(50) NULL,
    [description] NVARCHAR(MAX) NULL,
    [created_at] DATETIME2 NULL,
    [updated_at] DATETIME2 NULL,
    CONSTRAINT [pk_projects] PRIMARY KEY ([id]),
    CONSTRAINT [fk_proj_dept] FOREIGN KEY ([department_id]) REFERENCES [dbo].[departments] ([id]),
    CONSTRAINT [fk_proj_manager] FOREIGN KEY ([manager_id]) REFERENCES [dbo].[employees] ([id])
);

CREATE TABLE [dbo].[project_costs] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [project_id] BIGINT NOT NULL,
    [cost_type] NVARCHAR(50) NOT NULL,
    [cost_date] DATE NOT NULL,
    [amount] DECIMAL(18,2) NOT NULL,
    [description] NVARCHAR(500) NULL,
    [reference_type] NVARCHAR(50) NULL,
    [reference_id] BIGINT NULL,
    [recorded_by] BIGINT NULL,
    [created_at] DATETIME2 NULL,
    CONSTRAINT [pk_project_costs] PRIMARY KEY ([id]),
    CONSTRAINT [fk_pc_project] FOREIGN KEY ([project_id]) REFERENCES [dbo].[projects] ([id])
);

CREATE TABLE [dbo].[exchange_rates] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [from_currency] NVARCHAR(3) NOT NULL,
    [to_currency] NVARCHAR(3) NOT NULL,
    [rate_date] DATE NOT NULL,
    [rate] DECIMAL(12,6) NOT NULL,
    [rate_source] NVARCHAR(50) NULL,
    [created_at] DATETIME2 NULL,
    CONSTRAINT [pk_exchange_rates] PRIMARY KEY ([id]),
    CONSTRAINT [uk_currency_date] UNIQUE ([from_currency], [to_currency], [rate_date])
);

CREATE TABLE [dbo].[foreign_currency_accounts] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [account_id] BIGINT NOT NULL,
    [currency] NVARCHAR(3) NOT NULL,
    [original_balance] DECIMAL(18,2) NULL,
    [cny_equivalent] DECIMAL(18,2) NULL,
    [last_revaluation_date] DATE NULL,
    [created_at] DATETIME2 NULL,
    CONSTRAINT [pk_foreign_currency_accounts] PRIMARY KEY ([id]),
    CONSTRAINT [uk_account_currency] UNIQUE ([account_id], [currency]),
    CONSTRAINT [fk_fca_account] FOREIGN KEY ([account_id]) REFERENCES [dbo].[accounts] ([id])
);

CREATE TABLE [dbo].[performance_reviews] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [review_no] NVARCHAR(30) NOT NULL,
    [employee_id] BIGINT NOT NULL,
    [reviewer_id] BIGINT NOT NULL,
    [review_period] NVARCHAR(7) NOT NULL,
    [review_type] NVARCHAR(50) NOT NULL,
    [performance_score] DECIMAL(5,2) NULL,
    [competency_score] DECIMAL(5,2) NULL,
    [attitude_score] DECIMAL(5,2) NULL,
    [attendance_score] DECIMAL(5,2) NULL,
    [total_score] DECIMAL(5,2) NULL,
    [grade] NVARCHAR(1) NULL,
    [self_assessment] NVARCHAR(MAX) NULL,
    [reviewer_comment] NVARCHAR(MAX) NULL,
    [improvement_plan] NVARCHAR(MAX) NULL,
    [salary_adjustment] DECIMAL(12,2) NULL,
    [promotion_recommendation] BIT NULL,
    [status] NVARCHAR(50) NULL,
    [created_at] DATETIME2 NULL,
    [updated_at] DATETIME2 NULL,
    CONSTRAINT [pk_performance_reviews] PRIMARY KEY ([id]),
    CONSTRAINT [uk_emp_period_type] UNIQUE ([employee_id], [review_period], [review_type]),
    CONSTRAINT [fk_pr_employee] FOREIGN KEY ([employee_id]) REFERENCES [dbo].[employees] ([id]),
    CONSTRAINT [fk_pr_reviewer] FOREIGN KEY ([reviewer_id]) REFERENCES [dbo].[employees] ([id])
);

CREATE TABLE [dbo].[kpi_indicators] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [name] NVARCHAR(200) NOT NULL,
    [indicator_type] NVARCHAR(50) NOT NULL,
    [unit] NVARCHAR(20) NOT NULL,
    [target_direction] NVARCHAR(50) NOT NULL,
    [target_value] DECIMAL(12,2) NOT NULL,
    [target_min] DECIMAL(12,2) NULL,
    [target_max] DECIMAL(12,2) NULL,
    [weight] DECIMAL(5,4) NULL,
    [applicable_role_id] BIGINT NULL,
    [department_id] BIGINT NULL,
    [status] NVARCHAR(50) NULL,
    [created_at] DATETIME2 NULL,
    CONSTRAINT [pk_kpi_indicators] PRIMARY KEY ([id])
);

CREATE TABLE [dbo].[serial_numbers] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [product_id] BIGINT NOT NULL,
    [batch_id] BIGINT NULL,
    [serial_no] NVARCHAR(100) NOT NULL,
    [status] NVARCHAR(50) NULL,
    [warehouse_id] BIGINT NULL,
    [purchase_receipt_id] BIGINT NULL,
    [sales_order_id] BIGINT NULL,
    [return_id] BIGINT NULL,
    [current_owner_id] BIGINT NULL,
    [warranty_start] DATE NULL,
    [warranty_end] DATE NULL,
    [last_scan_date] DATETIME2 NULL,
    [last_scan_location] NVARCHAR(200) NULL,
    [created_at] DATETIME2 NULL,
    [updated_at] DATETIME2 NULL,
    CONSTRAINT [pk_serial_numbers] PRIMARY KEY ([id]),
    CONSTRAINT [uk_serial] UNIQUE ([product_id], [serial_no]),
    CONSTRAINT [fk_sn_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[products] ([id]),
    CONSTRAINT [fk_sn_batch] FOREIGN KEY ([batch_id]) REFERENCES [dbo].[product_batches] ([id]),
    CONSTRAINT [fk_sn_wh] FOREIGN KEY ([warehouse_id]) REFERENCES [dbo].[warehouses] ([id])
);

CREATE TABLE [dbo].[serial_number_logs] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [serial_number_id] BIGINT NOT NULL,
    [event_type] NVARCHAR(50) NOT NULL,
    [from_status] NVARCHAR(20) NULL,
    [to_status] NVARCHAR(20) NOT NULL,
    [from_location] NVARCHAR(200) NULL,
    [to_location] NVARCHAR(200) NULL,
    [reference_type] NVARCHAR(50) NULL,
    [reference_id] BIGINT NULL,
    [operator_id] BIGINT NULL,
    [event_time] DATETIME2 NULL,
    [remark] NVARCHAR(500) NULL,
    CONSTRAINT [pk_serial_number_logs] PRIMARY KEY ([id]),
    CONSTRAINT [fk_snl_sn] FOREIGN KEY ([serial_number_id]) REFERENCES [dbo].[serial_numbers] ([id])
);

CREATE TABLE [dbo].[consignment_inventory] (
    [id] BIGINT IDENTITY(1,1) NOT NULL,
    [product_id] BIGINT NOT NULL,
    [batch_id] BIGINT NULL,
    [customer_id] BIGINT NOT NULL,
    [consigned_qty] INT NOT NULL,
    [consumed_qty] INT NULL,
    [available_qty] INT NULL,
    [unit_price] DECIMAL(12,2) NOT NULL,
    [consigned_date] DATE NOT NULL,
    [last_consumed_date] DATE NULL,
    [settlement_period] NVARCHAR(7) NULL,
    [status] NVARCHAR(50) NULL,
    [created_at] DATETIME2 NULL,
    [updated_at] DATETIME2 NULL,
    CONSTRAINT [pk_consignment_inventory] PRIMARY KEY ([id]),
    CONSTRAINT [uk_product_batch_customer] UNIQUE ([product_id], [batch_id], [customer_id]),
    CONSTRAINT [fk_ci_product] FOREIGN KEY ([product_id]) REFERENCES [dbo].[products] ([id]),
    CONSTRAINT [fk_ci_batch] FOREIGN KEY ([batch_id]) REFERENCES [dbo].[product_batches] ([id]),
    CONSTRAINT [fk_ci_customer] FOREIGN KEY ([customer_id]) REFERENCES [dbo].[customers] ([id])
);
