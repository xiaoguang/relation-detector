-- ============================================================
-- Portable ERP deep scenario tables
-- Covers MRP, shop-floor execution, costing, AR/AP, WMS,
-- repair service, master-data governance, and sensitive access audit.
-- Uses a common SQL subset for relation-detector common token-event golden.
-- ============================================================

CREATE TABLE production_plans (
    id BIGINT PRIMARY KEY,
    plan_no VARCHAR(30) NOT NULL UNIQUE,
    product_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    plan_month VARCHAR(7) NOT NULL,
    forecast_qty INTEGER NOT NULL,
    confirmed_sales_qty INTEGER DEFAULT 0,
    safety_stock_qty INTEGER DEFAULT 0,
    planned_production_qty INTEGER NOT NULL,
    status VARCHAR(20) DEFAULT 'draft',
    planner_id BIGINT NOT NULL,
    approved_by BIGINT NULL,
    approved_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_prod_plan_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_prod_plan_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT fk_prod_plan_planner FOREIGN KEY (planner_id) REFERENCES employees(id),
    CONSTRAINT fk_prod_plan_approved FOREIGN KEY (approved_by) REFERENCES employees(id)
);

CREATE INDEX idx_prod_plan_month ON production_plans (plan_month);
CREATE INDEX idx_prod_plan_status ON production_plans (status);

CREATE TABLE mrp_runs (
    id BIGINT PRIMARY KEY,
    run_no VARCHAR(30) NOT NULL UNIQUE,
    plan_id BIGINT NOT NULL,
    run_date DATE NOT NULL,
    demand_source VARCHAR(30) NOT NULL,
    status VARCHAR(20) DEFAULT 'running',
    created_by BIGINT NOT NULL,
    completed_at TIMESTAMP NULL,
    CONSTRAINT fk_mrp_run_plan FOREIGN KEY (plan_id) REFERENCES production_plans(id),
    CONSTRAINT fk_mrp_run_created_by FOREIGN KEY (created_by) REFERENCES employees(id)
);

CREATE INDEX idx_mrp_run_date ON mrp_runs (run_date);

CREATE TABLE mrp_run_items (
    id BIGINT PRIMARY KEY,
    run_id BIGINT NOT NULL,
    parent_product_id BIGINT NOT NULL,
    component_product_id BIGINT NOT NULL,
    gross_requirement DECIMAL(18,4) NOT NULL,
    on_hand_qty DECIMAL(18,4) DEFAULT 0.0000,
    reserved_qty DECIMAL(18,4) DEFAULT 0.0000,
    planned_receipt_qty DECIMAL(18,4) DEFAULT 0.0000,
    net_requirement DECIMAL(18,4) NOT NULL,
    suggested_order_qty DECIMAL(18,4) NOT NULL,
    suggested_supplier_id BIGINT NULL,
    suggested_due_date DATE NULL,
    CONSTRAINT fk_mrp_item_run FOREIGN KEY (run_id) REFERENCES mrp_runs(id) ON DELETE CASCADE,
    CONSTRAINT fk_mrp_item_parent FOREIGN KEY (parent_product_id) REFERENCES products(id),
    CONSTRAINT fk_mrp_item_component FOREIGN KEY (component_product_id) REFERENCES products(id),
    CONSTRAINT fk_mrp_item_supplier FOREIGN KEY (suggested_supplier_id) REFERENCES suppliers(id)
);

CREATE INDEX idx_mrp_item_component ON mrp_run_items (component_product_id);

CREATE TABLE work_order_operations (
    id BIGINT PRIMARY KEY,
    work_order_id BIGINT NOT NULL,
    operation_id BIGINT NOT NULL,
    operation_seq INTEGER NOT NULL,
    planned_start TIMESTAMP NULL,
    planned_end TIMESTAMP NULL,
    actual_start TIMESTAMP NULL,
    actual_end TIMESTAMP NULL,
    status VARCHAR(20) DEFAULT 'pending',
    assigned_employee_id BIGINT NULL,
    qualified_qty INTEGER DEFAULT 0,
    scrapped_qty INTEGER DEFAULT 0,
    rework_qty INTEGER DEFAULT 0,
    CONSTRAINT fk_wo_op_work_order FOREIGN KEY (work_order_id) REFERENCES work_orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_wo_op_operation FOREIGN KEY (operation_id) REFERENCES production_operations(id),
    CONSTRAINT fk_wo_op_employee FOREIGN KEY (assigned_employee_id) REFERENCES employees(id),
    CONSTRAINT uk_work_order_operation UNIQUE (work_order_id, operation_seq)
);

CREATE TABLE operation_reports (
    id BIGINT PRIMARY KEY,
    work_order_operation_id BIGINT NOT NULL,
    report_no VARCHAR(30) NOT NULL UNIQUE,
    employee_id BIGINT NOT NULL,
    report_time TIMESTAMP NOT NULL,
    input_qty INTEGER NOT NULL,
    qualified_qty INTEGER NOT NULL,
    scrapped_qty INTEGER DEFAULT 0,
    rework_qty INTEGER DEFAULT 0,
    labor_minutes DECIMAL(10,2) DEFAULT 0.00,
    machine_minutes DECIMAL(10,2) DEFAULT 0.00,
    CONSTRAINT fk_operation_report_op FOREIGN KEY (work_order_operation_id) REFERENCES work_order_operations(id),
    CONSTRAINT fk_operation_report_employee FOREIGN KEY (employee_id) REFERENCES employees(id)
);

CREATE INDEX idx_operation_report_time ON operation_reports (report_time);

CREATE TABLE material_issues (
    id BIGINT PRIMARY KEY,
    issue_no VARCHAR(30) NOT NULL UNIQUE,
    work_order_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    issue_date DATE NOT NULL,
    issued_by BIGINT NOT NULL,
    status VARCHAR(20) DEFAULT 'draft',
    CONSTRAINT fk_material_issue_work_order FOREIGN KEY (work_order_id) REFERENCES work_orders(id),
    CONSTRAINT fk_material_issue_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT fk_material_issue_employee FOREIGN KEY (issued_by) REFERENCES employees(id)
);

CREATE TABLE material_issue_items (
    id BIGINT PRIMARY KEY,
    issue_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    batch_id BIGINT NULL,
    required_qty DECIMAL(18,4) NOT NULL,
    issued_qty DECIMAL(18,4) NOT NULL,
    unit_cost DECIMAL(18,4) NOT NULL,
    CONSTRAINT fk_material_issue_item_issue FOREIGN KEY (issue_id) REFERENCES material_issues(id) ON DELETE CASCADE,
    CONSTRAINT fk_material_issue_item_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_material_issue_item_batch FOREIGN KEY (batch_id) REFERENCES product_batches(id)
);

CREATE TABLE finished_goods_receipts (
    id BIGINT PRIMARY KEY,
    receipt_no VARCHAR(30) NOT NULL UNIQUE,
    work_order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    batch_id BIGINT NULL,
    warehouse_id BIGINT NOT NULL,
    receipt_date DATE NOT NULL,
    received_qty INTEGER NOT NULL,
    unit_cost DECIMAL(18,4) DEFAULT 0.0000,
    received_by BIGINT NOT NULL,
    status VARCHAR(20) DEFAULT 'draft',
    CONSTRAINT fk_fg_receipt_work_order FOREIGN KEY (work_order_id) REFERENCES work_orders(id),
    CONSTRAINT fk_fg_receipt_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_fg_receipt_batch FOREIGN KEY (batch_id) REFERENCES product_batches(id),
    CONSTRAINT fk_fg_receipt_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT fk_fg_receipt_employee FOREIGN KEY (received_by) REFERENCES employees(id)
);

CREATE TABLE standard_costs (
    id BIGINT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    cost_version VARCHAR(20) NOT NULL,
    material_cost DECIMAL(18,4) DEFAULT 0.0000,
    labor_cost DECIMAL(18,4) DEFAULT 0.0000,
    overhead_cost DECIMAL(18,4) DEFAULT 0.0000,
    effective_from DATE NOT NULL,
    effective_to DATE NULL,
    status VARCHAR(20) DEFAULT 'draft',
    approved_by BIGINT NULL,
    CONSTRAINT fk_std_cost_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_std_cost_approved FOREIGN KEY (approved_by) REFERENCES employees(id),
    CONSTRAINT uk_product_cost_version UNIQUE (product_id, cost_version)
);

CREATE TABLE inventory_cost_layers (
    id BIGINT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    batch_id BIGINT NULL,
    warehouse_id BIGINT NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    source_id BIGINT NOT NULL,
    receipt_date DATE NOT NULL,
    original_qty DECIMAL(18,4) NOT NULL,
    remaining_qty DECIMAL(18,4) NOT NULL,
    unit_cost DECIMAL(18,4) NOT NULL,
    currency VARCHAR(3) DEFAULT 'CNY',
    CONSTRAINT fk_cost_layer_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_cost_layer_batch FOREIGN KEY (batch_id) REFERENCES product_batches(id),
    CONSTRAINT fk_cost_layer_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id)
);

CREATE INDEX idx_cost_layer_source ON inventory_cost_layers (source_type, source_id);
CREATE INDEX idx_cost_layer_product_date ON inventory_cost_layers (product_id, receipt_date);

CREATE TABLE inventory_valuation_snapshots (
    id BIGINT PRIMARY KEY,
    snapshot_date DATE NOT NULL,
    product_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    quantity DECIMAL(18,4) NOT NULL,
    unit_cost DECIMAL(18,4) NOT NULL,
    inventory_value DECIMAL(18,2) NOT NULL,
    valuation_method VARCHAR(30) NOT NULL,
    CONSTRAINT fk_inv_value_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_inv_value_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT uk_inv_value_snapshot UNIQUE (snapshot_date, product_id, warehouse_id, valuation_method)
);

CREATE TABLE work_order_costs (
    id BIGINT PRIMARY KEY,
    work_order_id BIGINT NOT NULL,
    material_cost DECIMAL(18,2) DEFAULT 0.00,
    labor_cost DECIMAL(18,2) DEFAULT 0.00,
    overhead_cost DECIMAL(18,2) DEFAULT 0.00,
    finished_qty INTEGER DEFAULT 0,
    unit_cost DECIMAL(18,4) DEFAULT 0.0000,
    variance_amount DECIMAL(18,2) DEFAULT 0.00,
    calculated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_work_order_cost_work_order FOREIGN KEY (work_order_id) REFERENCES work_orders(id),
    CONSTRAINT uk_work_order_cost UNIQUE (work_order_id)
);

CREATE TABLE cogs_entries (
    id BIGINT PRIMARY KEY,
    sales_order_id BIGINT NOT NULL,
    sales_order_item_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    batch_id BIGINT NULL,
    quantity DECIMAL(18,4) NOT NULL,
    unit_cost DECIMAL(18,4) NOT NULL,
    cogs_amount DECIMAL(18,2) NOT NULL,
    voucher_id BIGINT NULL,
    posted_at TIMESTAMP NULL,
    CONSTRAINT fk_cogs_order FOREIGN KEY (sales_order_id) REFERENCES sales_orders(id),
    CONSTRAINT fk_cogs_order_item FOREIGN KEY (sales_order_item_id) REFERENCES sales_order_items(id),
    CONSTRAINT fk_cogs_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_cogs_batch FOREIGN KEY (batch_id) REFERENCES product_batches(id),
    CONSTRAINT fk_cogs_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers(id)
);

CREATE TABLE account_subjects (
    id BIGINT PRIMARY KEY,
    parent_id BIGINT NULL,
    subject_code VARCHAR(30) NOT NULL UNIQUE,
    subject_name VARCHAR(100) NOT NULL,
    subject_type VARCHAR(20) NOT NULL,
    balance_direction VARCHAR(10) NOT NULL,
    is_leaf BOOLEAN DEFAULT TRUE,
    status VARCHAR(20) DEFAULT 'active',
    CONSTRAINT fk_subject_parent FOREIGN KEY (parent_id) REFERENCES account_subjects(id)
);

CREATE TABLE opening_balances (
    id BIGINT PRIMARY KEY,
    ledger_book_id BIGINT NOT NULL,
    subject_id BIGINT NOT NULL,
    period_code VARCHAR(7) NOT NULL,
    debit_amount DECIMAL(18,2) DEFAULT 0.00,
    credit_amount DECIMAL(18,2) DEFAULT 0.00,
    CONSTRAINT fk_opening_balance_book FOREIGN KEY (ledger_book_id) REFERENCES ledger_books(id),
    CONSTRAINT fk_opening_balance_subject FOREIGN KEY (subject_id) REFERENCES account_subjects(id),
    CONSTRAINT uk_opening_balance UNIQUE (ledger_book_id, subject_id, period_code)
);

CREATE TABLE account_balances (
    id BIGINT PRIMARY KEY,
    ledger_book_id BIGINT NOT NULL,
    subject_id BIGINT NOT NULL,
    period_code VARCHAR(7) NOT NULL,
    begin_debit DECIMAL(18,2) DEFAULT 0.00,
    begin_credit DECIMAL(18,2) DEFAULT 0.00,
    current_debit DECIMAL(18,2) DEFAULT 0.00,
    current_credit DECIMAL(18,2) DEFAULT 0.00,
    ending_debit DECIMAL(18,2) DEFAULT 0.00,
    ending_credit DECIMAL(18,2) DEFAULT 0.00,
    CONSTRAINT fk_account_balance_book FOREIGN KEY (ledger_book_id) REFERENCES ledger_books(id),
    CONSTRAINT fk_account_balance_subject FOREIGN KEY (subject_id) REFERENCES account_subjects(id),
    CONSTRAINT uk_account_balance UNIQUE (ledger_book_id, subject_id, period_code)
);

CREATE TABLE budget_versions (
    id BIGINT PRIMARY KEY,
    ledger_book_id BIGINT NOT NULL,
    version_code VARCHAR(30) NOT NULL,
    version_name VARCHAR(100) NOT NULL,
    fiscal_year INTEGER NOT NULL,
    status VARCHAR(20) DEFAULT 'draft',
    approved_by BIGINT NULL,
    CONSTRAINT fk_budget_version_book FOREIGN KEY (ledger_book_id) REFERENCES ledger_books(id),
    CONSTRAINT fk_budget_version_approved FOREIGN KEY (approved_by) REFERENCES employees(id),
    CONSTRAINT uk_budget_version UNIQUE (ledger_book_id, version_code)
);

CREATE TABLE budget_items (
    id BIGINT PRIMARY KEY,
    version_id BIGINT NOT NULL,
    department_id BIGINT NOT NULL,
    subject_id BIGINT NOT NULL,
    period_code VARCHAR(7) NOT NULL,
    budget_amount DECIMAL(18,2) NOT NULL,
    used_amount DECIMAL(18,2) DEFAULT 0.00,
    CONSTRAINT fk_budget_item_version FOREIGN KEY (version_id) REFERENCES budget_versions(id) ON DELETE CASCADE,
    CONSTRAINT fk_budget_item_department FOREIGN KEY (department_id) REFERENCES departments(id),
    CONSTRAINT fk_budget_item_subject FOREIGN KEY (subject_id) REFERENCES account_subjects(id),
    CONSTRAINT uk_budget_item UNIQUE (version_id, department_id, subject_id, period_code)
);

CREATE TABLE ar_invoices (
    id BIGINT PRIMARY KEY,
    ar_no VARCHAR(30) NOT NULL UNIQUE,
    sales_order_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    invoice_date DATE NOT NULL,
    due_date DATE NOT NULL,
    invoice_amount DECIMAL(18,2) NOT NULL,
    paid_amount DECIMAL(18,2) DEFAULT 0.00,
    writeoff_amount DECIMAL(18,2) DEFAULT 0.00,
    status VARCHAR(20) DEFAULT 'open',
    CONSTRAINT fk_ar_invoice_order FOREIGN KEY (sales_order_id) REFERENCES sales_orders(id),
    CONSTRAINT fk_ar_invoice_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
);

CREATE INDEX idx_ar_invoice_customer_due ON ar_invoices (customer_id, due_date);

CREATE TABLE ap_invoices (
    id BIGINT PRIMARY KEY,
    ap_no VARCHAR(30) NOT NULL UNIQUE,
    purchase_order_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    invoice_date DATE NOT NULL,
    due_date DATE NOT NULL,
    invoice_amount DECIMAL(18,2) NOT NULL,
    paid_amount DECIMAL(18,2) DEFAULT 0.00,
    status VARCHAR(20) DEFAULT 'open',
    CONSTRAINT fk_ap_invoice_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id),
    CONSTRAINT fk_ap_invoice_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id)
);

CREATE INDEX idx_ap_invoice_supplier_due ON ap_invoices (supplier_id, due_date);

CREATE TABLE payment_requests (
    id BIGINT PRIMARY KEY,
    request_no VARCHAR(30) NOT NULL UNIQUE,
    supplier_id BIGINT NOT NULL,
    requested_by BIGINT NOT NULL,
    request_date DATE NOT NULL,
    planned_pay_date DATE NOT NULL,
    total_amount DECIMAL(18,2) DEFAULT 0.00,
    status VARCHAR(20) DEFAULT 'draft',
    CONSTRAINT fk_payment_request_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id),
    CONSTRAINT fk_payment_request_employee FOREIGN KEY (requested_by) REFERENCES employees(id)
);

CREATE TABLE payment_request_items (
    id BIGINT PRIMARY KEY,
    request_id BIGINT NOT NULL,
    ap_invoice_id BIGINT NOT NULL,
    requested_amount DECIMAL(18,2) NOT NULL,
    CONSTRAINT fk_payment_request_item_request FOREIGN KEY (request_id) REFERENCES payment_requests(id) ON DELETE CASCADE,
    CONSTRAINT fk_payment_request_item_ap FOREIGN KEY (ap_invoice_id) REFERENCES ap_invoices(id)
);

CREATE TABLE warehouse_zones (
    id BIGINT PRIMARY KEY,
    warehouse_id BIGINT NOT NULL,
    zone_code VARCHAR(30) NOT NULL,
    zone_name VARCHAR(100) NOT NULL,
    zone_type VARCHAR(20) NOT NULL,
    CONSTRAINT fk_zone_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT uk_zone UNIQUE (warehouse_id, zone_code)
);

CREATE TABLE warehouse_locations (
    id BIGINT PRIMARY KEY,
    zone_id BIGINT NOT NULL,
    location_code VARCHAR(50) NOT NULL,
    location_type VARCHAR(20) DEFAULT 'bin',
    max_weight_kg DECIMAL(12,3) DEFAULT 0.000,
    max_volume_m3 DECIMAL(12,6) DEFAULT 0.000000,
    status VARCHAR(20) DEFAULT 'active',
    CONSTRAINT fk_location_zone FOREIGN KEY (zone_id) REFERENCES warehouse_zones(id),
    CONSTRAINT uk_location UNIQUE (zone_id, location_code)
);

CREATE TABLE inventory_location_balances (
    id BIGINT PRIMARY KEY,
    location_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    batch_id BIGINT NULL,
    quantity DECIMAL(18,4) NOT NULL,
    locked_quantity DECIMAL(18,4) DEFAULT 0.0000,
    CONSTRAINT fk_loc_balance_location FOREIGN KEY (location_id) REFERENCES warehouse_locations(id),
    CONSTRAINT fk_loc_balance_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_loc_balance_batch FOREIGN KEY (batch_id) REFERENCES product_batches(id),
    CONSTRAINT uk_location_product_batch UNIQUE (location_id, product_id, batch_id)
);

CREATE TABLE putaway_tasks (
    id BIGINT PRIMARY KEY,
    task_no VARCHAR(30) NOT NULL UNIQUE,
    receipt_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    batch_id BIGINT NULL,
    from_location_id BIGINT NULL,
    to_location_id BIGINT NOT NULL,
    quantity DECIMAL(18,4) NOT NULL,
    assigned_to BIGINT NULL,
    status VARCHAR(20) DEFAULT 'pending',
    CONSTRAINT fk_putaway_receipt FOREIGN KEY (receipt_id) REFERENCES purchase_receipts(id),
    CONSTRAINT fk_putaway_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_putaway_batch FOREIGN KEY (batch_id) REFERENCES product_batches(id),
    CONSTRAINT fk_putaway_to_location FOREIGN KEY (to_location_id) REFERENCES warehouse_locations(id),
    CONSTRAINT fk_putaway_employee FOREIGN KEY (assigned_to) REFERENCES employees(id)
);

CREATE TABLE picking_tasks (
    id BIGINT PRIMARY KEY,
    task_no VARCHAR(30) NOT NULL UNIQUE,
    sales_order_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    wave_no VARCHAR(30) NULL,
    assigned_to BIGINT NULL,
    status VARCHAR(20) DEFAULT 'pending',
    CONSTRAINT fk_picking_order FOREIGN KEY (sales_order_id) REFERENCES sales_orders(id),
    CONSTRAINT fk_picking_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT fk_picking_employee FOREIGN KEY (assigned_to) REFERENCES employees(id)
);

CREATE TABLE picking_task_items (
    id BIGINT PRIMARY KEY,
    picking_task_id BIGINT NOT NULL,
    sales_order_item_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    batch_id BIGINT NULL,
    location_id BIGINT NOT NULL,
    required_qty DECIMAL(18,4) NOT NULL,
    picked_qty DECIMAL(18,4) DEFAULT 0.0000,
    CONSTRAINT fk_picking_item_task FOREIGN KEY (picking_task_id) REFERENCES picking_tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_picking_item_order_item FOREIGN KEY (sales_order_item_id) REFERENCES sales_order_items(id),
    CONSTRAINT fk_picking_item_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_picking_item_batch FOREIGN KEY (batch_id) REFERENCES product_batches(id),
    CONSTRAINT fk_picking_item_location FOREIGN KEY (location_id) REFERENCES warehouse_locations(id)
);

CREATE TABLE repair_orders (
    id BIGINT PRIMARY KEY,
    repair_no VARCHAR(30) NOT NULL UNIQUE,
    service_ticket_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    serial_number_id BIGINT NULL,
    received_date DATE NOT NULL,
    fault_desc VARCHAR(500) NOT NULL,
    status VARCHAR(20) DEFAULT 'received',
    technician_id BIGINT NULL,
    estimated_cost DECIMAL(18,2) DEFAULT 0.00,
    actual_cost DECIMAL(18,2) DEFAULT 0.00,
    CONSTRAINT fk_repair_ticket FOREIGN KEY (service_ticket_id) REFERENCES service_tickets(id),
    CONSTRAINT fk_repair_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_repair_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_repair_serial FOREIGN KEY (serial_number_id) REFERENCES serial_numbers(id),
    CONSTRAINT fk_repair_technician FOREIGN KEY (technician_id) REFERENCES employees(id)
);

CREATE TABLE repair_order_parts (
    id BIGINT PRIMARY KEY,
    repair_order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    batch_id BIGINT NULL,
    quantity DECIMAL(18,4) NOT NULL,
    unit_cost DECIMAL(18,4) NOT NULL,
    issued_from_warehouse_id BIGINT NOT NULL,
    CONSTRAINT fk_repair_part_order FOREIGN KEY (repair_order_id) REFERENCES repair_orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_repair_part_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_repair_part_batch FOREIGN KEY (batch_id) REFERENCES product_batches(id),
    CONSTRAINT fk_repair_part_warehouse FOREIGN KEY (issued_from_warehouse_id) REFERENCES warehouses(id)
);

CREATE TABLE numbering_rules (
    id BIGINT PRIMARY KEY,
    document_type VARCHAR(50) NOT NULL UNIQUE,
    prefix VARCHAR(20) NOT NULL,
    date_pattern VARCHAR(20) DEFAULT 'YYYYMM',
    sequence_length INTEGER DEFAULT 4,
    current_sequence INTEGER DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE master_data_change_requests (
    id BIGINT PRIMARY KEY,
    request_no VARCHAR(30) NOT NULL UNIQUE,
    master_type VARCHAR(20) NOT NULL,
    master_id BIGINT NOT NULL,
    change_reason VARCHAR(500) NOT NULL,
    requested_by BIGINT NOT NULL,
    requested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    approved_by BIGINT NULL,
    approved_at TIMESTAMP NULL,
    status VARCHAR(20) DEFAULT 'submitted',
    CONSTRAINT fk_mdc_requested_by FOREIGN KEY (requested_by) REFERENCES employees(id),
    CONSTRAINT fk_mdc_approved_by FOREIGN KEY (approved_by) REFERENCES employees(id)
);

CREATE INDEX idx_mdc_master ON master_data_change_requests (master_type, master_id);

CREATE TABLE master_data_change_items (
    id BIGINT PRIMARY KEY,
    request_id BIGINT NOT NULL,
    field_name VARCHAR(100) NOT NULL,
    old_value VARCHAR(1000) NULL,
    new_value VARCHAR(1000) NULL,
    CONSTRAINT fk_mdc_item_request FOREIGN KEY (request_id) REFERENCES master_data_change_requests(id) ON DELETE CASCADE
);

CREATE TABLE data_permission_scopes (
    id BIGINT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    scope_type VARCHAR(20) NOT NULL,
    scope_id BIGINT NOT NULL,
    can_read BOOLEAN DEFAULT TRUE,
    can_write BOOLEAN DEFAULT FALSE,
    CONSTRAINT fk_data_scope_role FOREIGN KEY (role_id) REFERENCES roles(id),
    CONSTRAINT uk_data_scope UNIQUE (role_id, scope_type, scope_id)
);

CREATE TABLE sensitive_access_logs (
    id BIGINT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    object_type VARCHAR(50) NOT NULL,
    object_id BIGINT NOT NULL,
    field_name VARCHAR(100) NOT NULL,
    access_reason VARCHAR(500) NULL,
    accessed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sensitive_access_employee FOREIGN KEY (employee_id) REFERENCES employees(id)
);

CREATE INDEX idx_sensitive_access_object ON sensitive_access_logs (object_type, object_id);

CREATE TABLE region_dim (
    id BIGINT PRIMARY KEY,
    region_code VARCHAR(30) NOT NULL UNIQUE,
    region_name VARCHAR(100) NOT NULL,
    province VARCHAR(50) NOT NULL,
    city VARCHAR(50),
    district VARCHAR(50),
    sales_region VARCHAR(100) NOT NULL,
    region_level VARCHAR(20),
    is_active BOOLEAN,
    CONSTRAINT uk_region_location UNIQUE (province, city, district)
);

CREATE INDEX idx_region_sales_region ON region_dim (sales_region);

CREATE TABLE fiscal_calendar (
    calendar_date DATE PRIMARY KEY,
    fiscal_year INTEGER NOT NULL,
    fiscal_quarter INTEGER NOT NULL,
    fiscal_month INTEGER NOT NULL,
    fiscal_month_name VARCHAR(20) NOT NULL,
    period_code VARCHAR(7) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    is_current_fiscal_year BOOLEAN,
    accounting_period_id BIGINT,
    CONSTRAINT fk_fiscal_calendar_period FOREIGN KEY (accounting_period_id) REFERENCES accounting_periods(id)
);

CREATE INDEX idx_fiscal_calendar_year_month ON fiscal_calendar (fiscal_year, fiscal_month);

CREATE TABLE category_dim (
    id BIGINT PRIMARY KEY,
    source_category_id BIGINT NOT NULL,
    category_code VARCHAR(50) NOT NULL,
    level1_name VARCHAR(100) NOT NULL,
    level2_name VARCHAR(100),
    leaf_name VARCHAR(100) NOT NULL,
    is_womenwear BOOLEAN,
    effective_from DATE NOT NULL,
    effective_to DATE,
    status VARCHAR(20),
    CONSTRAINT fk_category_dim_source FOREIGN KEY (source_category_id) REFERENCES product_categories(id),
    CONSTRAINT uk_category_dim_source UNIQUE (source_category_id)
);

CREATE INDEX idx_category_dim_level1 ON category_dim (level1_name);

CREATE TABLE payments (
    id BIGINT PRIMARY KEY,
    payment_no VARCHAR(40) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL,
    order_id BIGINT,
    receipt_id BIGINT,
    journal_id BIGINT,
    payment_date DATE NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    currency VARCHAR(3),
    payment_method VARCHAR(30) NOT NULL,
    payment_status VARCHAR(20),
    failure_reason VARCHAR(300),
    created_at TIMESTAMP,
    CONSTRAINT fk_payment_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES sales_orders(id),
    CONSTRAINT fk_payment_receipt FOREIGN KEY (receipt_id) REFERENCES payment_receipts(id),
    CONSTRAINT fk_payment_journal FOREIGN KEY (journal_id) REFERENCES cashier_journals(id)
);

CREATE INDEX idx_payment_customer_date ON payments (customer_id, payment_date);
CREATE INDEX idx_payment_status_date ON payments (payment_status, payment_date);

CREATE TABLE sales_fact (
    id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    order_item_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    category_dim_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    region_dim_id BIGINT NOT NULL,
    fiscal_date DATE NOT NULL,
    payment_id BIGINT,
    quantity_sold DECIMAL(18,4) NOT NULL,
    sales_amount DECIMAL(18,2) NOT NULL,
    paid_amount DECIMAL(18,2),
    refund_amount DECIMAL(18,2),
    net_sales_amount DECIMAL(18,2) NOT NULL,
    gross_margin_amount DECIMAL(18,2),
    order_status VARCHAR(30) NOT NULL,
    sales_channel VARCHAR(30),
    created_at TIMESTAMP,
    CONSTRAINT fk_sales_fact_order FOREIGN KEY (order_id) REFERENCES sales_orders(id),
    CONSTRAINT fk_sales_fact_item FOREIGN KEY (order_item_id) REFERENCES sales_order_items(id),
    CONSTRAINT fk_sales_fact_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_sales_fact_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_sales_fact_category FOREIGN KEY (category_dim_id) REFERENCES category_dim(id),
    CONSTRAINT fk_sales_fact_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT fk_sales_fact_region FOREIGN KEY (region_dim_id) REFERENCES region_dim(id),
    CONSTRAINT fk_sales_fact_fiscal FOREIGN KEY (fiscal_date) REFERENCES fiscal_calendar(calendar_date),
    CONSTRAINT fk_sales_fact_payment FOREIGN KEY (payment_id) REFERENCES payments(id),
    CONSTRAINT uk_sales_fact_item UNIQUE (order_item_id)
);

CREATE INDEX idx_sales_fact_customer_date ON sales_fact (customer_id, fiscal_date);
CREATE INDEX idx_sales_fact_region_category ON sales_fact (region_dim_id, category_dim_id, fiscal_date);
