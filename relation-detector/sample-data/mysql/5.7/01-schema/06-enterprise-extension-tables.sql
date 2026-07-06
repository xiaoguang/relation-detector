-- ============================================================
-- ERP系统企业级扩展表
-- 覆盖: 多租户/账套、地址、税率、会计期间、收付款、
--       库存盘点/调拨/预留、工艺路线/工序、班次排班
-- 数据库: MySQL 8.0
-- ============================================================

USE erp_system;

-- 多租户与账套
CREATE TABLE tenants (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    tenant_code VARCHAR(30) NOT NULL UNIQUE,
    tenant_name VARCHAR(100) NOT NULL,
    legal_entity_name VARCHAR(200) NOT NULL,
    tax_no VARCHAR(30) NULL,
    status ENUM('active','suspended','closed') DEFAULT 'active',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE ledger_books (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT UNSIGNED NOT NULL,
    book_code VARCHAR(30) NOT NULL,
    book_name VARCHAR(100) NOT NULL,
    base_currency VARCHAR(3) DEFAULT 'CNY',
    fiscal_year_start_month TINYINT UNSIGNED DEFAULT 1,
    is_default BOOLEAN DEFAULT FALSE,
    status ENUM('active','closed') DEFAULT 'active',
    UNIQUE KEY uk_ledger_book (tenant_id, book_code),
    CONSTRAINT fk_ledger_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB;

-- 客户/供应商多地址
CREATE TABLE customer_addresses (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT UNSIGNED NOT NULL,
    address_type ENUM('registered','billing','shipping','contact') NOT NULL,
    receiver_name VARCHAR(100) NULL,
    receiver_phone VARCHAR(30) NULL,
    province VARCHAR(50) NOT NULL,
    city VARCHAR(50) NOT NULL,
    district VARCHAR(50) NULL,
    street VARCHAR(300) NOT NULL,
    postal_code VARCHAR(20) NULL,
    is_default BOOLEAN DEFAULT FALSE,
    CONSTRAINT fk_customer_address_customer FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE,
    INDEX idx_customer_address_city (province, city),
    INDEX idx_customer_address_default (customer_id, is_default)
) ENGINE=InnoDB;

CREATE TABLE supplier_addresses (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    supplier_id BIGINT UNSIGNED NOT NULL,
    address_type ENUM('registered','billing','warehouse','contact') NOT NULL,
    contact_name VARCHAR(100) NULL,
    contact_phone VARCHAR(30) NULL,
    province VARCHAR(50) NOT NULL,
    city VARCHAR(50) NOT NULL,
    district VARCHAR(50) NULL,
    street VARCHAR(300) NOT NULL,
    postal_code VARCHAR(20) NULL,
    is_default BOOLEAN DEFAULT FALSE,
    CONSTRAINT fk_supplier_address_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id) ON DELETE CASCADE,
    INDEX idx_supplier_address_city (province, city),
    INDEX idx_supplier_address_default (supplier_id, is_default)
) ENGINE=InnoDB;

-- 税率和会计期间
CREATE TABLE tax_rates (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    tax_code VARCHAR(30) NOT NULL UNIQUE,
    tax_name VARCHAR(100) NOT NULL,
    tax_type ENUM('vat','income_tax','surtax','stamp','other') NOT NULL,
    rate DECIMAL(8,4) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE NULL,
    status ENUM('active','expired') DEFAULT 'active',
    INDEX idx_tax_rate_effective (tax_type, effective_from, effective_to)
) ENGINE=InnoDB;

CREATE TABLE accounting_periods (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    ledger_book_id BIGINT UNSIGNED NOT NULL,
    period_code VARCHAR(7) NOT NULL COMMENT 'YYYY-MM',
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    status ENUM('open','closing','closed','locked') DEFAULT 'open',
    closed_by BIGINT UNSIGNED NULL,
    closed_at DATETIME NULL,
    UNIQUE KEY uk_accounting_period (ledger_book_id, period_code),
    CONSTRAINT fk_period_book FOREIGN KEY (ledger_book_id) REFERENCES ledger_books(id),
    CONSTRAINT fk_period_closed_by FOREIGN KEY (closed_by) REFERENCES employees(id)
) ENGINE=InnoDB;

CREATE TABLE period_close_jobs (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    period_id BIGINT UNSIGNED NOT NULL,
    job_code VARCHAR(30) NOT NULL,
    job_name VARCHAR(100) NOT NULL,
    status ENUM('pending','running','success','failed','skipped') DEFAULT 'pending',
    started_at DATETIME NULL,
    finished_at DATETIME NULL,
    message VARCHAR(1000) NULL,
    UNIQUE KEY uk_period_job (period_id, job_code),
    CONSTRAINT fk_period_close_job_period FOREIGN KEY (period_id) REFERENCES accounting_periods(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 收付款流水与分配
CREATE TABLE payment_receipts (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    receipt_no VARCHAR(30) NOT NULL UNIQUE,
    receipt_type ENUM('customer_receipt','supplier_payment','refund_payment','internal_transfer') NOT NULL,
    party_type ENUM('customer','supplier','employee','internal') NOT NULL,
    party_id BIGINT UNSIGNED NULL,
    account_id BIGINT UNSIGNED NOT NULL,
    receipt_date DATE NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'CNY',
    status ENUM('draft','confirmed','reconciled','cancelled') DEFAULT 'draft',
    handled_by BIGINT UNSIGNED NOT NULL,
    confirmed_at DATETIME NULL,
    remark VARCHAR(500) NULL,
    INDEX idx_payment_receipt_party (party_type, party_id),
    INDEX idx_payment_receipt_date (receipt_date),
    CONSTRAINT fk_payment_receipt_account FOREIGN KEY (account_id) REFERENCES accounts(id),
    CONSTRAINT fk_payment_receipt_handler FOREIGN KEY (handled_by) REFERENCES employees(id)
) ENGINE=InnoDB;

CREATE TABLE payment_receipt_allocations (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    receipt_id BIGINT UNSIGNED NOT NULL,
    reference_type ENUM('sales_order','purchase_order','sales_return','purchase_return','invoice','settlement') NOT NULL,
    reference_id BIGINT UNSIGNED NOT NULL,
    allocated_amount DECIMAL(18,2) NOT NULL,
    CONSTRAINT fk_payment_allocation_receipt FOREIGN KEY (receipt_id) REFERENCES payment_receipts(id) ON DELETE CASCADE,
    INDEX idx_payment_allocation_reference (reference_type, reference_id)
) ENGINE=InnoDB;

-- 库存盘点、调拨和预留
CREATE TABLE stocktakes (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    stocktake_no VARCHAR(30) NOT NULL UNIQUE,
    warehouse_id BIGINT UNSIGNED NOT NULL,
    stocktake_date DATE NOT NULL,
    stocktake_type ENUM('full','cycle','spot') DEFAULT 'cycle',
    status ENUM('draft','counting','reviewed','posted','cancelled') DEFAULT 'draft',
    created_by BIGINT UNSIGNED NOT NULL,
    reviewed_by BIGINT UNSIGNED NULL,
    posted_at DATETIME NULL,
    CONSTRAINT fk_stocktake_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT fk_stocktake_created_by FOREIGN KEY (created_by) REFERENCES employees(id),
    CONSTRAINT fk_stocktake_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES employees(id)
) ENGINE=InnoDB;

CREATE TABLE stocktake_items (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    stocktake_id BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    batch_id BIGINT UNSIGNED NULL,
    book_quantity INT NOT NULL,
    counted_quantity INT NOT NULL,
    variance_quantity INT GENERATED ALWAYS AS (counted_quantity - book_quantity) STORED,
    variance_reason VARCHAR(300) NULL,
    CONSTRAINT fk_stocktake_item_stocktake FOREIGN KEY (stocktake_id) REFERENCES stocktakes(id) ON DELETE CASCADE,
    CONSTRAINT fk_stocktake_item_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_stocktake_item_batch FOREIGN KEY (batch_id) REFERENCES product_batches(id),
    INDEX idx_stocktake_item_product (product_id, batch_id)
) ENGINE=InnoDB;

CREATE TABLE stock_transfers (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    transfer_no VARCHAR(30) NOT NULL UNIQUE,
    from_warehouse_id BIGINT UNSIGNED NOT NULL,
    to_warehouse_id BIGINT UNSIGNED NOT NULL,
    requested_by BIGINT UNSIGNED NOT NULL,
    approved_by BIGINT UNSIGNED NULL,
    transfer_date DATE NOT NULL,
    status ENUM('draft','approved','in_transit','received','cancelled') DEFAULT 'draft',
    CONSTRAINT fk_transfer_from_wh FOREIGN KEY (from_warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT fk_transfer_to_wh FOREIGN KEY (to_warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT fk_transfer_requested_by FOREIGN KEY (requested_by) REFERENCES employees(id),
    CONSTRAINT fk_transfer_approved_by FOREIGN KEY (approved_by) REFERENCES employees(id)
) ENGINE=InnoDB;

CREATE TABLE stock_transfer_items (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    transfer_id BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    batch_id BIGINT UNSIGNED NULL,
    quantity INT NOT NULL,
    received_quantity INT DEFAULT 0,
    CONSTRAINT fk_transfer_item_transfer FOREIGN KEY (transfer_id) REFERENCES stock_transfers(id) ON DELETE CASCADE,
    CONSTRAINT fk_transfer_item_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_transfer_item_batch FOREIGN KEY (batch_id) REFERENCES product_batches(id)
) ENGINE=InnoDB;

CREATE TABLE inventory_reservations (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    reservation_no VARCHAR(30) NOT NULL UNIQUE,
    product_id BIGINT UNSIGNED NOT NULL,
    batch_id BIGINT UNSIGNED NULL,
    warehouse_id BIGINT UNSIGNED NOT NULL,
    source_type ENUM('sales_order','work_order','transfer','service_ticket') NOT NULL,
    source_id BIGINT UNSIGNED NOT NULL,
    reserved_quantity INT NOT NULL,
    released_quantity INT DEFAULT 0,
    status ENUM('reserved','partially_released','released','cancelled') DEFAULT 'reserved',
    expires_at DATETIME NULL,
    CONSTRAINT fk_inv_res_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_inv_res_batch FOREIGN KEY (batch_id) REFERENCES product_batches(id),
    CONSTRAINT fk_inv_res_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    INDEX idx_inv_res_source (source_type, source_id)
) ENGINE=InnoDB;

-- 工艺路线、工序和班次
CREATE TABLE production_routes (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT UNSIGNED NOT NULL,
    route_code VARCHAR(30) NOT NULL,
    route_name VARCHAR(100) NOT NULL,
    version_no VARCHAR(20) NOT NULL,
    status ENUM('draft','active','retired') DEFAULT 'draft',
    UNIQUE KEY uk_product_route (product_id, route_code, version_no),
    CONSTRAINT fk_route_product FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB;

CREATE TABLE production_operations (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    route_id BIGINT UNSIGNED NOT NULL,
    operation_no INT UNSIGNED NOT NULL,
    operation_name VARCHAR(100) NOT NULL,
    work_center VARCHAR(100) NOT NULL,
    standard_minutes DECIMAL(10,2) NOT NULL,
    predecessor_operation_id BIGINT UNSIGNED NULL,
    UNIQUE KEY uk_route_operation (route_id, operation_no),
    CONSTRAINT fk_operation_route FOREIGN KEY (route_id) REFERENCES production_routes(id) ON DELETE CASCADE,
    CONSTRAINT fk_operation_predecessor FOREIGN KEY (predecessor_operation_id) REFERENCES production_operations(id)
) ENGINE=InnoDB;

CREATE TABLE employee_shifts (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    shift_code VARCHAR(20) NOT NULL UNIQUE,
    shift_name VARCHAR(100) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    planned_hours DECIMAL(4,2) NOT NULL,
    is_night_shift BOOLEAN DEFAULT FALSE
) ENGINE=InnoDB;

CREATE TABLE employee_shift_assignments (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT UNSIGNED NOT NULL,
    shift_id BIGINT UNSIGNED NOT NULL,
    work_date DATE NOT NULL,
    warehouse_id BIGINT UNSIGNED NULL,
    status ENUM('planned','confirmed','cancelled') DEFAULT 'planned',
    UNIQUE KEY uk_employee_shift_date (employee_id, work_date),
    CONSTRAINT fk_shift_assignment_employee FOREIGN KEY (employee_id) REFERENCES employees(id),
    CONSTRAINT fk_shift_assignment_shift FOREIGN KEY (shift_id) REFERENCES employee_shifts(id),
    CONSTRAINT fk_shift_assignment_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id)
) ENGINE=InnoDB;
