-- ============================================================
-- ERP深业务场景扩展表
-- 覆盖: MRP/生产执行、成本核算、总账预算、AR/AP、WMS、售后维修、主数据治理
-- 数据库: MySQL 8.0
-- ============================================================

USE erp_system;

-- ============================================================
-- 1. 生产计划、MRP和工单执行
-- ============================================================

CREATE TABLE production_plans (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    plan_no VARCHAR(30) NOT NULL UNIQUE,
    product_id BIGINT UNSIGNED NOT NULL,
    warehouse_id BIGINT UNSIGNED NOT NULL,
    plan_month VARCHAR(7) NOT NULL COMMENT 'YYYY-MM',
    forecast_qty INT NOT NULL,
    confirmed_sales_qty INT DEFAULT 0,
    safety_stock_qty INT DEFAULT 0,
    planned_production_qty INT NOT NULL,
    status ENUM('draft','approved','released','closed','cancelled') DEFAULT 'draft',
    planner_id BIGINT UNSIGNED NOT NULL,
    approved_by BIGINT UNSIGNED NULL,
    approved_at DATETIME NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_prod_plan_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_prod_plan_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT fk_prod_plan_planner FOREIGN KEY (planner_id) REFERENCES employees(id),
    CONSTRAINT fk_prod_plan_approved FOREIGN KEY (approved_by) REFERENCES employees(id),
    INDEX idx_prod_plan_month (plan_month),
    INDEX idx_prod_plan_status (status)
) ENGINE=InnoDB;

CREATE TABLE mrp_runs (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    run_no VARCHAR(30) NOT NULL UNIQUE,
    plan_id BIGINT UNSIGNED NOT NULL,
    run_date DATE NOT NULL,
    demand_source ENUM('sales_order','forecast','safety_stock','manual') NOT NULL,
    status ENUM('running','completed','failed') DEFAULT 'running',
    created_by BIGINT UNSIGNED NOT NULL,
    completed_at DATETIME NULL,
    CONSTRAINT fk_mrp_run_plan FOREIGN KEY (plan_id) REFERENCES production_plans(id),
    CONSTRAINT fk_mrp_run_created_by FOREIGN KEY (created_by) REFERENCES employees(id),
    INDEX idx_mrp_run_date (run_date)
) ENGINE=InnoDB;

CREATE TABLE mrp_run_items (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    run_id BIGINT UNSIGNED NOT NULL,
    parent_product_id BIGINT UNSIGNED NOT NULL,
    component_product_id BIGINT UNSIGNED NOT NULL,
    gross_requirement DECIMAL(18,4) NOT NULL,
    on_hand_qty DECIMAL(18,4) DEFAULT 0.0000,
    reserved_qty DECIMAL(18,4) DEFAULT 0.0000,
    planned_receipt_qty DECIMAL(18,4) DEFAULT 0.0000,
    net_requirement DECIMAL(18,4) NOT NULL,
    suggested_order_qty DECIMAL(18,4) NOT NULL,
    suggested_supplier_id BIGINT UNSIGNED NULL,
    suggested_due_date DATE NULL,
    CONSTRAINT fk_mrp_item_run FOREIGN KEY (run_id) REFERENCES mrp_runs(id) ON DELETE CASCADE,
    CONSTRAINT fk_mrp_item_parent FOREIGN KEY (parent_product_id) REFERENCES products(id),
    CONSTRAINT fk_mrp_item_component FOREIGN KEY (component_product_id) REFERENCES products(id),
    CONSTRAINT fk_mrp_item_supplier FOREIGN KEY (suggested_supplier_id) REFERENCES suppliers(id),
    INDEX idx_mrp_item_component (component_product_id)
) ENGINE=InnoDB;

CREATE TABLE work_order_operations (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    work_order_id BIGINT UNSIGNED NOT NULL,
    operation_id BIGINT UNSIGNED NOT NULL,
    operation_seq INT UNSIGNED NOT NULL,
    planned_start DATETIME NULL,
    planned_end DATETIME NULL,
    actual_start DATETIME NULL,
    actual_end DATETIME NULL,
    status ENUM('pending','running','completed','rework','scrapped') DEFAULT 'pending',
    assigned_employee_id BIGINT UNSIGNED NULL,
    qualified_qty INT DEFAULT 0,
    scrapped_qty INT DEFAULT 0,
    rework_qty INT DEFAULT 0,
    CONSTRAINT fk_wo_op_work_order FOREIGN KEY (work_order_id) REFERENCES work_orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_wo_op_operation FOREIGN KEY (operation_id) REFERENCES production_operations(id),
    CONSTRAINT fk_wo_op_employee FOREIGN KEY (assigned_employee_id) REFERENCES employees(id),
    UNIQUE KEY uk_work_order_operation (work_order_id, operation_seq)
) ENGINE=InnoDB;

CREATE TABLE operation_reports (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    work_order_operation_id BIGINT UNSIGNED NOT NULL,
    report_no VARCHAR(30) NOT NULL UNIQUE,
    employee_id BIGINT UNSIGNED NOT NULL,
    report_time DATETIME NOT NULL,
    input_qty INT NOT NULL,
    qualified_qty INT NOT NULL,
    scrapped_qty INT DEFAULT 0,
    rework_qty INT DEFAULT 0,
    labor_minutes DECIMAL(10,2) DEFAULT 0.00,
    machine_minutes DECIMAL(10,2) DEFAULT 0.00,
    CONSTRAINT fk_operation_report_op FOREIGN KEY (work_order_operation_id) REFERENCES work_order_operations(id),
    CONSTRAINT fk_operation_report_employee FOREIGN KEY (employee_id) REFERENCES employees(id),
    INDEX idx_operation_report_time (report_time)
) ENGINE=InnoDB;

CREATE TABLE material_issues (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    issue_no VARCHAR(30) NOT NULL UNIQUE,
    work_order_id BIGINT UNSIGNED NOT NULL,
    warehouse_id BIGINT UNSIGNED NOT NULL,
    issue_date DATE NOT NULL,
    issued_by BIGINT UNSIGNED NOT NULL,
    status ENUM('draft','posted','cancelled') DEFAULT 'draft',
    CONSTRAINT fk_material_issue_work_order FOREIGN KEY (work_order_id) REFERENCES work_orders(id),
    CONSTRAINT fk_material_issue_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT fk_material_issue_employee FOREIGN KEY (issued_by) REFERENCES employees(id)
) ENGINE=InnoDB;

CREATE TABLE material_issue_items (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    issue_id BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    batch_id BIGINT UNSIGNED NULL,
    required_qty DECIMAL(18,4) NOT NULL,
    issued_qty DECIMAL(18,4) NOT NULL,
    unit_cost DECIMAL(18,4) NOT NULL,
    CONSTRAINT fk_material_issue_item_issue FOREIGN KEY (issue_id) REFERENCES material_issues(id) ON DELETE CASCADE,
    CONSTRAINT fk_material_issue_item_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_material_issue_item_batch FOREIGN KEY (batch_id) REFERENCES product_batches(id)
) ENGINE=InnoDB;

CREATE TABLE finished_goods_receipts (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    receipt_no VARCHAR(30) NOT NULL UNIQUE,
    work_order_id BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    batch_id BIGINT UNSIGNED NULL,
    warehouse_id BIGINT UNSIGNED NOT NULL,
    receipt_date DATE NOT NULL,
    received_qty INT NOT NULL,
    unit_cost DECIMAL(18,4) DEFAULT 0.0000,
    received_by BIGINT UNSIGNED NOT NULL,
    status ENUM('draft','posted','cancelled') DEFAULT 'draft',
    CONSTRAINT fk_fg_receipt_work_order FOREIGN KEY (work_order_id) REFERENCES work_orders(id),
    CONSTRAINT fk_fg_receipt_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_fg_receipt_batch FOREIGN KEY (batch_id) REFERENCES product_batches(id),
    CONSTRAINT fk_fg_receipt_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT fk_fg_receipt_employee FOREIGN KEY (received_by) REFERENCES employees(id)
) ENGINE=InnoDB;

-- ============================================================
-- 2. 成本核算与库存估值
-- ============================================================

CREATE TABLE standard_costs (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT UNSIGNED NOT NULL,
    cost_version VARCHAR(20) NOT NULL,
    material_cost DECIMAL(18,4) DEFAULT 0.0000,
    labor_cost DECIMAL(18,4) DEFAULT 0.0000,
    overhead_cost DECIMAL(18,4) DEFAULT 0.0000,
    effective_from DATE NOT NULL,
    effective_to DATE NULL,
    status ENUM('draft','active','expired') DEFAULT 'draft',
    approved_by BIGINT UNSIGNED NULL,
    CONSTRAINT fk_std_cost_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_std_cost_approved FOREIGN KEY (approved_by) REFERENCES employees(id),
    UNIQUE KEY uk_product_cost_version (product_id, cost_version)
) ENGINE=InnoDB;

CREATE TABLE inventory_cost_layers (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT UNSIGNED NOT NULL,
    batch_id BIGINT UNSIGNED NULL,
    warehouse_id BIGINT UNSIGNED NOT NULL,
    source_type ENUM('purchase_receipt','work_order_receipt','adjustment','return_in') NOT NULL,
    source_id BIGINT UNSIGNED NOT NULL,
    receipt_date DATE NOT NULL,
    original_qty DECIMAL(18,4) NOT NULL,
    remaining_qty DECIMAL(18,4) NOT NULL,
    unit_cost DECIMAL(18,4) NOT NULL,
    currency VARCHAR(3) DEFAULT 'CNY',
    CONSTRAINT fk_cost_layer_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_cost_layer_batch FOREIGN KEY (batch_id) REFERENCES product_batches(id),
    CONSTRAINT fk_cost_layer_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    INDEX idx_cost_layer_source (source_type, source_id),
    INDEX idx_cost_layer_product_date (product_id, receipt_date)
) ENGINE=InnoDB;

CREATE TABLE inventory_valuation_snapshots (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    snapshot_date DATE NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    warehouse_id BIGINT UNSIGNED NOT NULL,
    quantity DECIMAL(18,4) NOT NULL,
    unit_cost DECIMAL(18,4) NOT NULL,
    inventory_value DECIMAL(18,2) NOT NULL,
    valuation_method ENUM('moving_average','fifo','standard') NOT NULL,
    CONSTRAINT fk_inv_value_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_inv_value_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    UNIQUE KEY uk_inv_value_snapshot (snapshot_date, product_id, warehouse_id, valuation_method)
) ENGINE=InnoDB;

CREATE TABLE work_order_costs (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    work_order_id BIGINT UNSIGNED NOT NULL,
    material_cost DECIMAL(18,2) DEFAULT 0.00,
    labor_cost DECIMAL(18,2) DEFAULT 0.00,
    overhead_cost DECIMAL(18,2) DEFAULT 0.00,
    finished_qty INT DEFAULT 0,
    unit_cost DECIMAL(18,4) DEFAULT 0.0000,
    variance_amount DECIMAL(18,2) DEFAULT 0.00,
    calculated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_work_order_cost_work_order FOREIGN KEY (work_order_id) REFERENCES work_orders(id),
    UNIQUE KEY uk_work_order_cost (work_order_id)
) ENGINE=InnoDB;

CREATE TABLE cogs_entries (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    sales_order_id BIGINT UNSIGNED NOT NULL,
    sales_order_item_id BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    batch_id BIGINT UNSIGNED NULL,
    quantity DECIMAL(18,4) NOT NULL,
    unit_cost DECIMAL(18,4) NOT NULL,
    cogs_amount DECIMAL(18,2) NOT NULL,
    voucher_id BIGINT UNSIGNED NULL,
    posted_at DATETIME NULL,
    CONSTRAINT fk_cogs_order FOREIGN KEY (sales_order_id) REFERENCES sales_orders(id),
    CONSTRAINT fk_cogs_order_item FOREIGN KEY (sales_order_item_id) REFERENCES sales_order_items(id),
    CONSTRAINT fk_cogs_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_cogs_batch FOREIGN KEY (batch_id) REFERENCES product_batches(id),
    CONSTRAINT fk_cogs_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers(id)
) ENGINE=InnoDB;

-- ============================================================
-- 3. 总账、预算、AR/AP业务单据
-- ============================================================

CREATE TABLE account_subjects (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    parent_id BIGINT UNSIGNED NULL,
    subject_code VARCHAR(30) NOT NULL UNIQUE,
    subject_name VARCHAR(100) NOT NULL,
    subject_type ENUM('asset','liability','equity','revenue','expense','cost') NOT NULL,
    balance_direction ENUM('debit','credit') NOT NULL,
    is_leaf BOOLEAN DEFAULT TRUE,
    status ENUM('active','inactive') DEFAULT 'active',
    CONSTRAINT fk_subject_parent FOREIGN KEY (parent_id) REFERENCES account_subjects(id)
) ENGINE=InnoDB;

CREATE TABLE opening_balances (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    ledger_book_id BIGINT UNSIGNED NOT NULL,
    subject_id BIGINT UNSIGNED NOT NULL,
    period_code VARCHAR(7) NOT NULL,
    debit_amount DECIMAL(18,2) DEFAULT 0.00,
    credit_amount DECIMAL(18,2) DEFAULT 0.00,
    CONSTRAINT fk_opening_balance_book FOREIGN KEY (ledger_book_id) REFERENCES ledger_books(id),
    CONSTRAINT fk_opening_balance_subject FOREIGN KEY (subject_id) REFERENCES account_subjects(id),
    UNIQUE KEY uk_opening_balance (ledger_book_id, subject_id, period_code)
) ENGINE=InnoDB;

CREATE TABLE account_balances (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    ledger_book_id BIGINT UNSIGNED NOT NULL,
    subject_id BIGINT UNSIGNED NOT NULL,
    period_code VARCHAR(7) NOT NULL,
    begin_debit DECIMAL(18,2) DEFAULT 0.00,
    begin_credit DECIMAL(18,2) DEFAULT 0.00,
    current_debit DECIMAL(18,2) DEFAULT 0.00,
    current_credit DECIMAL(18,2) DEFAULT 0.00,
    ending_debit DECIMAL(18,2) DEFAULT 0.00,
    ending_credit DECIMAL(18,2) DEFAULT 0.00,
    CONSTRAINT fk_account_balance_book FOREIGN KEY (ledger_book_id) REFERENCES ledger_books(id),
    CONSTRAINT fk_account_balance_subject FOREIGN KEY (subject_id) REFERENCES account_subjects(id),
    UNIQUE KEY uk_account_balance (ledger_book_id, subject_id, period_code)
) ENGINE=InnoDB;

CREATE TABLE budget_versions (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    ledger_book_id BIGINT UNSIGNED NOT NULL,
    version_code VARCHAR(30) NOT NULL,
    version_name VARCHAR(100) NOT NULL,
    fiscal_year INT NOT NULL,
    status ENUM('draft','approved','locked','archived') DEFAULT 'draft',
    approved_by BIGINT UNSIGNED NULL,
    CONSTRAINT fk_budget_version_book FOREIGN KEY (ledger_book_id) REFERENCES ledger_books(id),
    CONSTRAINT fk_budget_version_approved FOREIGN KEY (approved_by) REFERENCES employees(id),
    UNIQUE KEY uk_budget_version (ledger_book_id, version_code)
) ENGINE=InnoDB;

CREATE TABLE budget_items (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    version_id BIGINT UNSIGNED NOT NULL,
    department_id BIGINT UNSIGNED NOT NULL,
    subject_id BIGINT UNSIGNED NOT NULL,
    period_code VARCHAR(7) NOT NULL,
    budget_amount DECIMAL(18,2) NOT NULL,
    used_amount DECIMAL(18,2) DEFAULT 0.00,
    CONSTRAINT fk_budget_item_version FOREIGN KEY (version_id) REFERENCES budget_versions(id) ON DELETE CASCADE,
    CONSTRAINT fk_budget_item_department FOREIGN KEY (department_id) REFERENCES departments(id),
    CONSTRAINT fk_budget_item_subject FOREIGN KEY (subject_id) REFERENCES account_subjects(id),
    UNIQUE KEY uk_budget_item (version_id, department_id, subject_id, period_code)
) ENGINE=InnoDB;

CREATE TABLE ar_invoices (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    ar_no VARCHAR(30) NOT NULL UNIQUE,
    sales_order_id BIGINT UNSIGNED NOT NULL,
    customer_id BIGINT UNSIGNED NOT NULL,
    invoice_date DATE NOT NULL,
    due_date DATE NOT NULL,
    invoice_amount DECIMAL(18,2) NOT NULL,
    paid_amount DECIMAL(18,2) DEFAULT 0.00,
    writeoff_amount DECIMAL(18,2) DEFAULT 0.00,
    status ENUM('open','partially_paid','paid','written_off','cancelled') DEFAULT 'open',
    CONSTRAINT fk_ar_invoice_order FOREIGN KEY (sales_order_id) REFERENCES sales_orders(id),
    CONSTRAINT fk_ar_invoice_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    INDEX idx_ar_invoice_customer_due (customer_id, due_date)
) ENGINE=InnoDB;

CREATE TABLE ap_invoices (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    ap_no VARCHAR(30) NOT NULL UNIQUE,
    purchase_order_id BIGINT UNSIGNED NOT NULL,
    supplier_id BIGINT UNSIGNED NOT NULL,
    invoice_date DATE NOT NULL,
    due_date DATE NOT NULL,
    invoice_amount DECIMAL(18,2) NOT NULL,
    paid_amount DECIMAL(18,2) DEFAULT 0.00,
    status ENUM('open','partially_paid','paid','cancelled') DEFAULT 'open',
    CONSTRAINT fk_ap_invoice_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id),
    CONSTRAINT fk_ap_invoice_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id),
    INDEX idx_ap_invoice_supplier_due (supplier_id, due_date)
) ENGINE=InnoDB;

CREATE TABLE payment_requests (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    request_no VARCHAR(30) NOT NULL UNIQUE,
    supplier_id BIGINT UNSIGNED NOT NULL,
    requested_by BIGINT UNSIGNED NOT NULL,
    request_date DATE NOT NULL,
    planned_pay_date DATE NOT NULL,
    total_amount DECIMAL(18,2) DEFAULT 0.00,
    status ENUM('draft','submitted','approved','paid','rejected') DEFAULT 'draft',
    CONSTRAINT fk_payment_request_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id),
    CONSTRAINT fk_payment_request_employee FOREIGN KEY (requested_by) REFERENCES employees(id)
) ENGINE=InnoDB;

CREATE TABLE payment_request_items (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    request_id BIGINT UNSIGNED NOT NULL,
    ap_invoice_id BIGINT UNSIGNED NOT NULL,
    requested_amount DECIMAL(18,2) NOT NULL,
    CONSTRAINT fk_payment_request_item_request FOREIGN KEY (request_id) REFERENCES payment_requests(id) ON DELETE CASCADE,
    CONSTRAINT fk_payment_request_item_ap FOREIGN KEY (ap_invoice_id) REFERENCES ap_invoices(id)
) ENGINE=InnoDB;

-- ============================================================
-- 4. WMS库位、拣货、打包和售后维修
-- ============================================================

CREATE TABLE warehouse_zones (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    warehouse_id BIGINT UNSIGNED NOT NULL,
    zone_code VARCHAR(30) NOT NULL,
    zone_name VARCHAR(100) NOT NULL,
    zone_type ENUM('storage','picking','staging','returns','qc') NOT NULL,
    CONSTRAINT fk_zone_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    UNIQUE KEY uk_zone (warehouse_id, zone_code)
) ENGINE=InnoDB;

CREATE TABLE warehouse_locations (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    zone_id BIGINT UNSIGNED NOT NULL,
    location_code VARCHAR(50) NOT NULL,
    location_type ENUM('bin','pallet','shelf','dock') DEFAULT 'bin',
    max_weight_kg DECIMAL(12,3) DEFAULT 0.000,
    max_volume_m3 DECIMAL(12,6) DEFAULT 0.000000,
    status ENUM('active','locked','maintenance') DEFAULT 'active',
    CONSTRAINT fk_location_zone FOREIGN KEY (zone_id) REFERENCES warehouse_zones(id),
    UNIQUE KEY uk_location (zone_id, location_code)
) ENGINE=InnoDB;

CREATE TABLE inventory_location_balances (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    location_id BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    batch_id BIGINT UNSIGNED NULL,
    quantity DECIMAL(18,4) NOT NULL,
    locked_quantity DECIMAL(18,4) DEFAULT 0.0000,
    CONSTRAINT fk_loc_balance_location FOREIGN KEY (location_id) REFERENCES warehouse_locations(id),
    CONSTRAINT fk_loc_balance_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_loc_balance_batch FOREIGN KEY (batch_id) REFERENCES product_batches(id),
    UNIQUE KEY uk_location_product_batch (location_id, product_id, batch_id)
) ENGINE=InnoDB;

CREATE TABLE putaway_tasks (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    task_no VARCHAR(30) NOT NULL UNIQUE,
    receipt_id BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    batch_id BIGINT UNSIGNED NULL,
    from_location_id BIGINT UNSIGNED NULL,
    to_location_id BIGINT UNSIGNED NOT NULL,
    quantity DECIMAL(18,4) NOT NULL,
    assigned_to BIGINT UNSIGNED NULL,
    status ENUM('pending','running','completed','cancelled') DEFAULT 'pending',
    CONSTRAINT fk_putaway_receipt FOREIGN KEY (receipt_id) REFERENCES purchase_receipts(id),
    CONSTRAINT fk_putaway_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_putaway_batch FOREIGN KEY (batch_id) REFERENCES product_batches(id),
    CONSTRAINT fk_putaway_to_location FOREIGN KEY (to_location_id) REFERENCES warehouse_locations(id),
    CONSTRAINT fk_putaway_employee FOREIGN KEY (assigned_to) REFERENCES employees(id)
) ENGINE=InnoDB;

CREATE TABLE picking_tasks (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    task_no VARCHAR(30) NOT NULL UNIQUE,
    sales_order_id BIGINT UNSIGNED NOT NULL,
    warehouse_id BIGINT UNSIGNED NOT NULL,
    wave_no VARCHAR(30) NULL,
    assigned_to BIGINT UNSIGNED NULL,
    status ENUM('pending','allocated','picked','packed','cancelled') DEFAULT 'pending',
    CONSTRAINT fk_picking_order FOREIGN KEY (sales_order_id) REFERENCES sales_orders(id),
    CONSTRAINT fk_picking_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT fk_picking_employee FOREIGN KEY (assigned_to) REFERENCES employees(id)
) ENGINE=InnoDB;

CREATE TABLE picking_task_items (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    picking_task_id BIGINT UNSIGNED NOT NULL,
    sales_order_item_id BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    batch_id BIGINT UNSIGNED NULL,
    location_id BIGINT UNSIGNED NOT NULL,
    required_qty DECIMAL(18,4) NOT NULL,
    picked_qty DECIMAL(18,4) DEFAULT 0.0000,
    CONSTRAINT fk_picking_item_task FOREIGN KEY (picking_task_id) REFERENCES picking_tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_picking_item_order_item FOREIGN KEY (sales_order_item_id) REFERENCES sales_order_items(id),
    CONSTRAINT fk_picking_item_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_picking_item_batch FOREIGN KEY (batch_id) REFERENCES product_batches(id),
    CONSTRAINT fk_picking_item_location FOREIGN KEY (location_id) REFERENCES warehouse_locations(id)
) ENGINE=InnoDB;

CREATE TABLE repair_orders (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    repair_no VARCHAR(30) NOT NULL UNIQUE,
    service_ticket_id BIGINT UNSIGNED NOT NULL,
    customer_id BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    serial_number_id BIGINT UNSIGNED NULL,
    received_date DATE NOT NULL,
    fault_desc VARCHAR(500) NOT NULL,
    status ENUM('received','diagnosing','repairing','waiting_parts','completed','returned') DEFAULT 'received',
    technician_id BIGINT UNSIGNED NULL,
    estimated_cost DECIMAL(18,2) DEFAULT 0.00,
    actual_cost DECIMAL(18,2) DEFAULT 0.00,
    CONSTRAINT fk_repair_ticket FOREIGN KEY (service_ticket_id) REFERENCES service_tickets(id),
    CONSTRAINT fk_repair_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_repair_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_repair_serial FOREIGN KEY (serial_number_id) REFERENCES serial_numbers(id),
    CONSTRAINT fk_repair_technician FOREIGN KEY (technician_id) REFERENCES employees(id)
) ENGINE=InnoDB;

CREATE TABLE repair_order_parts (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    repair_order_id BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    batch_id BIGINT UNSIGNED NULL,
    quantity DECIMAL(18,4) NOT NULL,
    unit_cost DECIMAL(18,4) NOT NULL,
    issued_from_warehouse_id BIGINT UNSIGNED NOT NULL,
    CONSTRAINT fk_repair_part_order FOREIGN KEY (repair_order_id) REFERENCES repair_orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_repair_part_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_repair_part_batch FOREIGN KEY (batch_id) REFERENCES product_batches(id),
    CONSTRAINT fk_repair_part_warehouse FOREIGN KEY (issued_from_warehouse_id) REFERENCES warehouses(id)
) ENGINE=InnoDB;

-- ============================================================
-- 5. 主数据治理和敏感访问审计
-- ============================================================

CREATE TABLE numbering_rules (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    document_type VARCHAR(50) NOT NULL UNIQUE,
    prefix VARCHAR(20) NOT NULL,
    date_pattern VARCHAR(20) DEFAULT '%Y%m',
    sequence_length INT DEFAULT 4,
    current_sequence INT DEFAULT 0,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE master_data_change_requests (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    request_no VARCHAR(30) NOT NULL UNIQUE,
    master_type ENUM('customer','supplier','product','employee','account') NOT NULL,
    master_id BIGINT UNSIGNED NOT NULL,
    change_reason VARCHAR(500) NOT NULL,
    requested_by BIGINT UNSIGNED NOT NULL,
    requested_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    approved_by BIGINT UNSIGNED NULL,
    approved_at DATETIME NULL,
    status ENUM('submitted','approved','rejected','applied','cancelled') DEFAULT 'submitted',
    CONSTRAINT fk_mdc_requested_by FOREIGN KEY (requested_by) REFERENCES employees(id),
    CONSTRAINT fk_mdc_approved_by FOREIGN KEY (approved_by) REFERENCES employees(id),
    INDEX idx_mdc_master (master_type, master_id)
) ENGINE=InnoDB;

CREATE TABLE master_data_change_items (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    request_id BIGINT UNSIGNED NOT NULL,
    field_name VARCHAR(100) NOT NULL,
    old_value VARCHAR(1000) NULL,
    new_value VARCHAR(1000) NULL,
    CONSTRAINT fk_mdc_item_request FOREIGN KEY (request_id) REFERENCES master_data_change_requests(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE data_permission_scopes (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    role_id BIGINT UNSIGNED NOT NULL,
    scope_type ENUM('department','warehouse','customer','supplier','region') NOT NULL,
    scope_id BIGINT UNSIGNED NOT NULL,
    can_read BOOLEAN DEFAULT TRUE,
    can_write BOOLEAN DEFAULT FALSE,
    CONSTRAINT fk_data_scope_role FOREIGN KEY (role_id) REFERENCES roles(id),
    UNIQUE KEY uk_data_scope (role_id, scope_type, scope_id)
) ENGINE=InnoDB;

CREATE TABLE sensitive_access_logs (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT UNSIGNED NOT NULL,
    object_type VARCHAR(50) NOT NULL,
    object_id BIGINT UNSIGNED NOT NULL,
    field_name VARCHAR(100) NOT NULL,
    access_reason VARCHAR(500) NULL,
    accessed_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sensitive_access_employee FOREIGN KEY (employee_id) REFERENCES employees(id),
    INDEX idx_sensitive_access_object (object_type, object_id)
) ENGINE=InnoDB;

-- ============================================================
-- 6. 语义层示例支撑: 区域、财年、分类、支付和销售事实
-- ============================================================

CREATE TABLE region_dim (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    region_code VARCHAR(30) NOT NULL UNIQUE,
    region_name VARCHAR(100) NOT NULL,
    province VARCHAR(50) NOT NULL,
    city VARCHAR(50) NULL,
    district VARCHAR(50) NULL,
    sales_region VARCHAR(100) NOT NULL,
    region_level ENUM('country','region','province','city','district') DEFAULT 'city',
    is_active BOOLEAN DEFAULT TRUE,
    UNIQUE KEY uk_region_location (province, city, district),
    INDEX idx_region_sales_region (sales_region)
) ENGINE=InnoDB;

CREATE TABLE fiscal_calendar (
    calendar_date DATE PRIMARY KEY,
    fiscal_year INT NOT NULL,
    fiscal_quarter TINYINT UNSIGNED NOT NULL,
    fiscal_month TINYINT UNSIGNED NOT NULL,
    fiscal_month_name VARCHAR(20) NOT NULL,
    period_code VARCHAR(7) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    is_current_fiscal_year BOOLEAN DEFAULT FALSE,
    accounting_period_id BIGINT UNSIGNED NULL,
    CONSTRAINT fk_fiscal_calendar_period FOREIGN KEY (accounting_period_id) REFERENCES accounting_periods(id),
    INDEX idx_fiscal_calendar_year_month (fiscal_year, fiscal_month),
    INDEX idx_fiscal_calendar_current (is_current_fiscal_year)
) ENGINE=InnoDB;

CREATE TABLE category_dim (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    source_category_id BIGINT UNSIGNED NOT NULL,
    category_code VARCHAR(50) NOT NULL,
    level1_name VARCHAR(100) NOT NULL,
    level2_name VARCHAR(100) NULL,
    leaf_name VARCHAR(100) NOT NULL,
    is_womenwear BOOLEAN DEFAULT FALSE,
    effective_from DATE NOT NULL DEFAULT '2026-01-01',
    effective_to DATE NULL,
    status ENUM('active','inactive') DEFAULT 'active',
    CONSTRAINT fk_category_dim_source FOREIGN KEY (source_category_id) REFERENCES product_categories(id),
    UNIQUE KEY uk_category_dim_source (source_category_id),
    INDEX idx_category_dim_level1 (level1_name),
    INDEX idx_category_dim_womenwear (is_womenwear)
) ENGINE=InnoDB;

CREATE TABLE payments (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    payment_no VARCHAR(40) NOT NULL UNIQUE,
    customer_id BIGINT UNSIGNED NOT NULL,
    order_id BIGINT UNSIGNED NULL,
    receipt_id BIGINT UNSIGNED NULL,
    journal_id BIGINT UNSIGNED NULL,
    payment_date DATE NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'CNY',
    payment_method VARCHAR(30) NOT NULL,
    payment_status ENUM('pending','paid','failed','refunded','cancelled') DEFAULT 'pending',
    failure_reason VARCHAR(300) NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payment_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES sales_orders(id),
    CONSTRAINT fk_payment_receipt FOREIGN KEY (receipt_id) REFERENCES payment_receipts(id),
    CONSTRAINT fk_payment_journal FOREIGN KEY (journal_id) REFERENCES cashier_journals(id),
    INDEX idx_payment_customer_date (customer_id, payment_date),
    INDEX idx_payment_status_date (payment_status, payment_date)
) ENGINE=InnoDB;

CREATE TABLE sales_fact (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT UNSIGNED NOT NULL,
    order_item_id BIGINT UNSIGNED NOT NULL,
    customer_id BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    category_dim_id BIGINT UNSIGNED NOT NULL,
    warehouse_id BIGINT UNSIGNED NOT NULL,
    region_dim_id BIGINT UNSIGNED NOT NULL,
    fiscal_date DATE NOT NULL,
    payment_id BIGINT UNSIGNED NULL,
    quantity_sold DECIMAL(18,4) NOT NULL,
    sales_amount DECIMAL(18,2) NOT NULL,
    paid_amount DECIMAL(18,2) DEFAULT 0.00,
    refund_amount DECIMAL(18,2) DEFAULT 0.00,
    net_sales_amount DECIMAL(18,2) NOT NULL,
    gross_margin_amount DECIMAL(18,2) DEFAULT 0.00,
    order_status VARCHAR(30) NOT NULL,
    sales_channel VARCHAR(30) DEFAULT 'direct',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sales_fact_order FOREIGN KEY (order_id) REFERENCES sales_orders(id),
    CONSTRAINT fk_sales_fact_item FOREIGN KEY (order_item_id) REFERENCES sales_order_items(id),
    CONSTRAINT fk_sales_fact_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_sales_fact_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_sales_fact_category FOREIGN KEY (category_dim_id) REFERENCES category_dim(id),
    CONSTRAINT fk_sales_fact_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT fk_sales_fact_region FOREIGN KEY (region_dim_id) REFERENCES region_dim(id),
    CONSTRAINT fk_sales_fact_fiscal FOREIGN KEY (fiscal_date) REFERENCES fiscal_calendar(calendar_date),
    CONSTRAINT fk_sales_fact_payment FOREIGN KEY (payment_id) REFERENCES payments(id),
    UNIQUE KEY uk_sales_fact_item (order_item_id),
    INDEX idx_sales_fact_customer_date (customer_id, fiscal_date),
    INDEX idx_sales_fact_region_category (region_dim_id, category_dim_id, fiscal_date)
) ENGINE=InnoDB;
