-- ============================================================
-- ERP系统完整数据库设计
-- 模块: HR, 权限, 货品, 批号, 库存, 采购, 销售, 财务
-- 数据库: MySQL 8.0
-- ============================================================

CREATE DATABASE IF NOT EXISTS erp_system
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE erp_system;

-- ============================================================
-- 1. HR模块 - 组织架构
-- ============================================================

-- 部门表
CREATE TABLE departments (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    parent_id BIGINT UNSIGNED NULL,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(20) NOT NULL UNIQUE,
    manager_id BIGINT UNSIGNED NULL COMMENT '部门负责人',
    budget DECIMAL(18,2) DEFAULT 0.00 COMMENT '部门预算',
    headcount_plan INT UNSIGNED DEFAULT 0 COMMENT '计划编制人数',
    status ENUM('active','frozen','dissolved') DEFAULT 'active',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_parent (parent_id),
    INDEX idx_status (status),
    CONSTRAINT fk_dept_parent FOREIGN KEY (parent_id) REFERENCES departments(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- 职位表
CREATE TABLE positions (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    department_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(20) NOT NULL,
    level TINYINT UNSIGNED DEFAULT 1 COMMENT '职级 1-15',
    min_salary DECIMAL(12,2) DEFAULT 0.00,
    max_salary DECIMAL(12,2) DEFAULT 0.00,
    headcount INT UNSIGNED DEFAULT 1 COMMENT '编制人数',
    status ENUM('active','frozen','closed') DEFAULT 'active',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_dept_code (department_id, code),
    INDEX idx_level (level),
    CONSTRAINT fk_pos_dept FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 员工表
CREATE TABLE employees (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    employee_no VARCHAR(20) NOT NULL UNIQUE COMMENT '工号',
    name VARCHAR(50) NOT NULL,
    gender ENUM('M','F') NOT NULL,
    id_card VARCHAR(18) NOT NULL UNIQUE COMMENT '身份证号',
    phone VARCHAR(20) NOT NULL,
    email VARCHAR(100) NULL,
    birth_date DATE NOT NULL,
    hire_date DATE NOT NULL,
    department_id BIGINT UNSIGNED NOT NULL,
    position_id BIGINT UNSIGNED NOT NULL,
    manager_id BIGINT UNSIGNED NULL,
    salary DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    social_security_base DECIMAL(12,2) DEFAULT 0.00 COMMENT '社保基数',
    housing_fund_base DECIMAL(12,2) DEFAULT 0.00 COMMENT '公积金基数',
    bank_name VARCHAR(100) NULL COMMENT '开户银行',
    bank_account VARCHAR(50) NULL COMMENT '银行账号',
    status ENUM('active','probation','leave','resigned','terminated') DEFAULT 'probation',
    resignation_date DATE NULL,
    resignation_reason VARCHAR(500) NULL,
    address VARCHAR(200) NULL,
    emergency_contact VARCHAR(50) NULL,
    emergency_phone VARCHAR(20) NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_dept (department_id),
    INDEX idx_position (position_id),
    INDEX idx_manager (manager_id),
    INDEX idx_status (status),
    INDEX idx_hire_date (hire_date),
    INDEX idx_resignation (resignation_date),
    CONSTRAINT fk_emp_dept FOREIGN KEY (department_id) REFERENCES departments(id),
    CONSTRAINT fk_emp_pos FOREIGN KEY (position_id) REFERENCES positions(id),
    CONSTRAINT fk_emp_manager FOREIGN KEY (manager_id) REFERENCES employees(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- 更新部门负责人外键
ALTER TABLE departments ADD CONSTRAINT fk_dept_manager FOREIGN KEY (manager_id) REFERENCES employees(id) ON DELETE SET NULL;

-- 薪资变动记录
CREATE TABLE employee_salary_log (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT UNSIGNED NOT NULL,
    old_salary DECIMAL(12,2) NOT NULL,
    new_salary DECIMAL(12,2) NOT NULL,
    change_reason VARCHAR(200) NOT NULL,
    effective_date DATE NOT NULL,
    approved_by BIGINT UNSIGNED NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_employee (employee_id),
    INDEX idx_effective_date (effective_date),
    CONSTRAINT fk_sal_emp FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 考勤记录
CREATE TABLE attendance (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT UNSIGNED NOT NULL,
    attendance_date DATE NOT NULL,
    clock_in DATETIME NULL,
    clock_out DATETIME NULL,
    status ENUM('normal','late','early','absent','overtime','leave') DEFAULT 'normal',
    late_minutes INT DEFAULT 0,
    early_minutes INT DEFAULT 0,
    overtime_hours DECIMAL(4,1) DEFAULT 0.0,
    remark VARCHAR(200) NULL,
    UNIQUE KEY uk_emp_date (employee_id, attendance_date),
    INDEX idx_date (attendance_date),
    CONSTRAINT fk_att_emp FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 请假记录
CREATE TABLE leave_records (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT UNSIGNED NOT NULL,
    leave_type ENUM('annual','sick','personal','maternity','bereavement','marriage','other') NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    days DECIMAL(4,1) NOT NULL,
    reason VARCHAR(500) NULL,
    status ENUM('pending','approved','rejected','cancelled') DEFAULT 'pending',
    approved_by BIGINT UNSIGNED NULL,
    approved_at DATETIME NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_emp (employee_id),
    INDEX idx_date_range (start_date, end_date),
    INDEX idx_status (status),
    CONSTRAINT fk_leave_emp FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================================
-- 2. 权限模块
-- ============================================================

CREATE TABLE roles (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    code VARCHAR(30) NOT NULL UNIQUE,
    description VARCHAR(200) NULL,
    is_system BOOLEAN DEFAULT FALSE COMMENT '系统内置角色',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE permissions (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    parent_id BIGINT UNSIGNED NULL,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE COMMENT '权限标识符',
    resource_type VARCHAR(50) NOT NULL COMMENT '资源类型: menu, api, button, data',
    resource_path VARCHAR(200) NULL COMMENT '资源路径',
    action VARCHAR(50) NOT NULL COMMENT '操作: create,read,update,delete,approve,export,import',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_parent (parent_id),
    INDEX idx_resource (resource_type),
    CONSTRAINT fk_perm_parent FOREIGN KEY (parent_id) REFERENCES permissions(id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE role_permissions (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    role_id BIGINT UNSIGNED NOT NULL,
    permission_id BIGINT UNSIGNED NOT NULL,
    UNIQUE KEY uk_role_perm (role_id, permission_id),
    CONSTRAINT fk_rp_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    CONSTRAINT fk_rp_perm FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE employee_roles (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT UNSIGNED NOT NULL,
    role_id BIGINT UNSIGNED NOT NULL,
    granted_by BIGINT UNSIGNED NULL,
    granted_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    expires_at DATETIME NULL,
    UNIQUE KEY uk_emp_role (employee_id, role_id),
    CONSTRAINT fk_er_emp FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
    CONSTRAINT fk_er_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 操作审计日志
CREATE TABLE audit_log (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT UNSIGNED NULL,
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(50) NOT NULL COMMENT '操作对象类型',
    target_id BIGINT NULL COMMENT '操作对象ID',
    old_value JSON NULL,
    new_value JSON NULL,
    ip_address VARCHAR(45) NULL,
    user_agent VARCHAR(500) NULL,
    remark VARCHAR(500) NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_employee (employee_id),
    INDEX idx_target (target_type, target_id),
    INDEX idx_created_at (created_at),
    INDEX idx_action (action)
) ENGINE=InnoDB;

-- ============================================================
-- 3. 货品与批号模块
-- ============================================================

CREATE TABLE product_categories (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    parent_id BIGINT UNSIGNED NULL,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(20) NOT NULL UNIQUE,
    description VARCHAR(500) NULL,
    sort_order INT DEFAULT 0,
    status ENUM('active','inactive') DEFAULT 'active',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_parent (parent_id),
    CONSTRAINT fk_cat_parent FOREIGN KEY (parent_id) REFERENCES product_categories(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- 供应商表
-- 地理信息: 用于计算到门店的物流距离和费用
-- 评分体系: 价格(30%) + 距离(25%) + 退货率(20%) + 质量(15%) + 交期(10%)
CREATE TABLE suppliers (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    code VARCHAR(20) NOT NULL UNIQUE,
    contact_person VARCHAR(50) NULL,
    phone VARCHAR(20) NULL,
    email VARCHAR(100) NULL,
    address VARCHAR(300) NULL,
    -- 地理信息(用于距离计算和物流费用估算)
    province VARCHAR(50) NULL COMMENT '省份',
    city VARCHAR(50) NULL COMMENT '城市',
    district VARCHAR(50) NULL COMMENT '区县',
    latitude DECIMAL(10,7) NULL COMMENT '纬度',
    longitude DECIMAL(10,7) NULL COMMENT '经度',
    -- 供应商综合指标
    bank_name VARCHAR(100) NULL,
    bank_account VARCHAR(50) NULL,
    tax_id VARCHAR(50) NULL COMMENT '税号',
    credit_level ENUM('A','B','C','D') DEFAULT 'B',
    cooperation_status ENUM('active','suspended','blacklist') DEFAULT 'active',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (cooperation_status),
    INDEX idx_city (city),
    INDEX idx_province (province)
) ENGINE=InnoDB;

CREATE TABLE products (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    sku VARCHAR(50) NOT NULL UNIQUE COMMENT 'SKU编码',
    name VARCHAR(200) NOT NULL,
    category_id BIGINT UNSIGNED NOT NULL,
    unit VARCHAR(20) NOT NULL DEFAULT '件' COMMENT '计量单位',
    spec VARCHAR(100) NULL COMMENT '规格型号',
    brand VARCHAR(100) NULL,
    barcode VARCHAR(50) NULL,
    purchase_price DECIMAL(12,2) DEFAULT 0.00 COMMENT '进货价',
    wholesale_price DECIMAL(12,2) DEFAULT 0.00 COMMENT '批发价',
    retail_price DECIMAL(12,2) DEFAULT 0.00 COMMENT '零售价',
    min_stock INT DEFAULT 0 COMMENT '最低库存警戒',
    max_stock INT DEFAULT 99999 COMMENT '最高库存',
    batch_managed BOOLEAN DEFAULT TRUE COMMENT '是否批号管理',
    shelf_life_days INT DEFAULT 0 COMMENT '保质期天数',
    weight_kg DECIMAL(8,3) DEFAULT 0.000,
    volume_m3 DECIMAL(8,6) DEFAULT 0.000000,
    status ENUM('active','discontinued','seasonal') DEFAULT 'active',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_category (category_id),
    INDEX idx_status (status),
    INDEX idx_barcode (barcode),
    CONSTRAINT fk_prod_cat FOREIGN KEY (category_id) REFERENCES product_categories(id)
) ENGINE=InnoDB;

-- 供应商-货品关联
-- 每个货品关联3-5个供应商，系统自动选择最优供应商
-- 选择逻辑: 综合评分 = 价格分(30%) + 距离分(25%) + 退货率分(20%) + 质量分(15%) + 交期分(10%)
-- shipping_cost_per_km: 该供应商每公里物流费率(元/km)
-- return_rate: 该供应商该货品的历史退货率
-- quality_score: 该供应商该货品的质检评分(0-100)
-- lead_time_days: 供货周期(天)
CREATE TABLE supplier_products (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    supplier_id BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    supplier_price DECIMAL(12,2) DEFAULT 0.00 COMMENT '供应商报价',
    lead_time_days INT DEFAULT 0 COMMENT '供货周期(天)',
    min_order_qty INT DEFAULT 1 COMMENT '最小起订量',
    shipping_cost_per_km DECIMAL(8,4) DEFAULT 0.50 COMMENT '每公里物流费率(元/km)',
    return_rate DECIMAL(5,4) DEFAULT 0.00 COMMENT '该货品历史退货率(0-1)',
    quality_score DECIMAL(5,2) DEFAULT 100.00 COMMENT '质检评分(0-100)',
    is_preferred BOOLEAN DEFAULT FALSE COMMENT '是否优选供应商',
    last_order_date DATE NULL COMMENT '最近采购日期',
    total_order_count INT DEFAULT 0 COMMENT '累计采购次数',
    total_order_qty INT DEFAULT 0 COMMENT '累计采购数量',
    UNIQUE KEY uk_supplier_product (supplier_id, product_id),
    INDEX idx_product_price (product_id, supplier_price),
    INDEX idx_quality (quality_score),
    CONSTRAINT fk_sp_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id) ON DELETE CASCADE,
    CONSTRAINT fk_sp_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 批号管理
CREATE TABLE product_batches (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT UNSIGNED NOT NULL,
    batch_no VARCHAR(50) NOT NULL,
    production_date DATE NULL COMMENT '生产日期',
    expiry_date DATE NULL COMMENT '过期日期',
    supplier_id BIGINT UNSIGNED NULL,
    purchase_price DECIMAL(12,2) DEFAULT 0.00,
    initial_qty INT NOT NULL DEFAULT 0 COMMENT '初始数量',
    current_qty INT NOT NULL DEFAULT 0 COMMENT '当前数量',
    status ENUM('active','locked','exhausted','expired') DEFAULT 'active',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_product_batch (product_id, batch_no),
    INDEX idx_expiry (expiry_date),
    INDEX idx_status (status),
    INDEX idx_supplier (supplier_id),
    CONSTRAINT fk_batch_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_batch_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ============================================================
-- 4. 库存模块
-- ============================================================

-- 仓库/门店表
-- 地理信息: 用于计算与供应商的距离，估算物流费用和时效
CREATE TABLE warehouses (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(20) NOT NULL UNIQUE,
    address VARCHAR(200) NULL,
    -- 地理信息
    province VARCHAR(50) NULL COMMENT '省份',
    city VARCHAR(50) NULL COMMENT '城市',
    district VARCHAR(50) NULL COMMENT '区县',
    latitude DECIMAL(10,7) NULL COMMENT '纬度',
    longitude DECIMAL(10,7) NULL COMMENT '经度',
    manager_id BIGINT UNSIGNED NULL,
    type ENUM('main','transit','returns','cold') DEFAULT 'main',
    capacity_m3 DECIMAL(10,3) DEFAULT 0.000,
    status ENUM('active','inactive','maintenance') DEFAULT 'active',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_type (type),
    INDEX idx_city (city),
    INDEX idx_province (province),
    CONSTRAINT fk_wh_manager FOREIGN KEY (manager_id) REFERENCES employees(id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE inventory (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT UNSIGNED NOT NULL,
    batch_id BIGINT UNSIGNED NULL,
    warehouse_id BIGINT UNSIGNED NOT NULL,
    shelf_location VARCHAR(50) NULL COMMENT '货架位置',
    quantity INT NOT NULL DEFAULT 0,
    locked_quantity INT DEFAULT 0 COMMENT '锁定库存',
    available_quantity INT GENERATED ALWAYS AS (quantity - locked_quantity) STORED,
    last_stocktake_date DATE NULL,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_product_batch_wh (product_id, batch_id, warehouse_id),
    INDEX idx_warehouse (warehouse_id),
    INDEX idx_quantity (quantity),
    INDEX idx_available (available_quantity),
    CONSTRAINT fk_inv_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_inv_batch FOREIGN KEY (batch_id) REFERENCES product_batches(id) ON DELETE SET NULL,
    CONSTRAINT fk_inv_wh FOREIGN KEY (warehouse_id) REFERENCES warehouses(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 库存变动日志
CREATE TABLE inventory_transactions (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT UNSIGNED NOT NULL,
    batch_id BIGINT UNSIGNED NULL,
    warehouse_id BIGINT UNSIGNED NOT NULL,
    transaction_type ENUM('purchase_in','sales_out','return_in','return_out','transfer_in','transfer_out',
                          'stocktake_adjust','damage_out','scrap_out','lock','unlock','production_in') NOT NULL,
    quantity_change INT NOT NULL COMMENT '正数=入库，负数=出库',
    before_qty INT NOT NULL,
    after_qty INT NOT NULL,
    reference_type VARCHAR(50) NULL,
    reference_id BIGINT NULL,
    operator_id BIGINT UNSIGNED NULL,
    remark VARCHAR(500) NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_product (product_id),
    INDEX idx_warehouse (warehouse_id),
    INDEX idx_type (transaction_type),
    INDEX idx_reference (reference_type, reference_id),
    INDEX idx_created_at (created_at),
    CONSTRAINT fk_it_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_it_batch FOREIGN KEY (batch_id) REFERENCES product_batches(id) ON DELETE SET NULL,
    CONSTRAINT fk_it_wh FOREIGN KEY (warehouse_id) REFERENCES warehouses(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================================
-- 5. 采购模块
-- ============================================================

-- 请购单
CREATE TABLE purchase_requisitions (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    requisition_no VARCHAR(30) NOT NULL UNIQUE,
    department_id BIGINT UNSIGNED NOT NULL,
    requester_id BIGINT UNSIGNED NOT NULL,
    requisition_date DATE NOT NULL,
    required_date DATE NULL,
    urgency ENUM('normal','urgent','emergency') DEFAULT 'normal',
    total_amount DECIMAL(18,2) DEFAULT 0.00,
    status ENUM('draft','pending','approved','rejected','ordered','cancelled') DEFAULT 'draft',
    remark VARCHAR(500) NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_dept (department_id),
    INDEX idx_requester (requester_id),
    INDEX idx_status (status),
    INDEX idx_date (requisition_date),
    CONSTRAINT fk_pr_dept FOREIGN KEY (department_id) REFERENCES departments(id),
    CONSTRAINT fk_pr_requester FOREIGN KEY (requester_id) REFERENCES employees(id)
) ENGINE=InnoDB;

CREATE TABLE purchase_requisition_items (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    requisition_id BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    quantity INT NOT NULL,
    estimated_price DECIMAL(12,2) DEFAULT 0.00,
    amount DECIMAL(18,2) GENERATED ALWAYS AS (quantity * estimated_price) STORED,
    remark VARCHAR(200) NULL,
    INDEX idx_product (product_id),
    CONSTRAINT fk_pri_req FOREIGN KEY (requisition_id) REFERENCES purchase_requisitions(id) ON DELETE CASCADE,
    CONSTRAINT fk_pri_product FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB;

-- 采购单
CREATE TABLE purchase_orders (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(30) NOT NULL UNIQUE,
    supplier_id BIGINT UNSIGNED NOT NULL,
    requisition_id BIGINT UNSIGNED NULL,
    department_id BIGINT UNSIGNED NOT NULL,
    purchaser_id BIGINT UNSIGNED NOT NULL,
    order_date DATE NOT NULL,
    expected_delivery_date DATE NULL,
    actual_delivery_date DATE NULL,
    total_amount DECIMAL(18,2) DEFAULT 0.00,
    paid_amount DECIMAL(18,2) DEFAULT 0.00,
    payment_terms VARCHAR(100) NULL COMMENT '付款条件',
    status ENUM('draft','pending','approved','ordered','partially_received','received','cancelled','returned') DEFAULT 'draft',
    remark VARCHAR(500) NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_supplier (supplier_id),
    INDEX idx_requisition (requisition_id),
    INDEX idx_purchaser (purchaser_id),
    INDEX idx_status (status),
    INDEX idx_date (order_date),
    CONSTRAINT fk_po_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id),
    CONSTRAINT fk_po_req FOREIGN KEY (requisition_id) REFERENCES purchase_requisitions(id) ON DELETE SET NULL,
    CONSTRAINT fk_po_purchaser FOREIGN KEY (purchaser_id) REFERENCES employees(id)
) ENGINE=InnoDB;

CREATE TABLE purchase_order_items (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    amount DECIMAL(18,2) GENERATED ALWAYS AS (quantity * unit_price) STORED,
    received_qty INT DEFAULT 0,
    returned_qty INT DEFAULT 0,
    remark VARCHAR(200) NULL,
    INDEX idx_product (product_id),
    CONSTRAINT fk_poi_order FOREIGN KEY (order_id) REFERENCES purchase_orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_poi_product FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB;

-- 进货单（入库单）
CREATE TABLE purchase_receipts (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    receipt_no VARCHAR(30) NOT NULL UNIQUE,
    order_id BIGINT UNSIGNED NOT NULL,
    warehouse_id BIGINT UNSIGNED NOT NULL,
    receiver_id BIGINT UNSIGNED NOT NULL,
    receipt_date DATE NOT NULL,
    batch_no VARCHAR(50) NULL COMMENT '统一批号',
    total_qty INT DEFAULT 0,
    total_amount DECIMAL(18,2) DEFAULT 0.00,
    status ENUM('pending','received','inspected','rejected','partial') DEFAULT 'pending',
    inspection_result VARCHAR(500) NULL COMMENT '质检结果',
    remark VARCHAR(500) NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order (order_id),
    INDEX idx_warehouse (warehouse_id),
    INDEX idx_date (receipt_date),
    CONSTRAINT fk_prec_order FOREIGN KEY (order_id) REFERENCES purchase_orders(id),
    CONSTRAINT fk_prec_wh FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT fk_prec_receiver FOREIGN KEY (receiver_id) REFERENCES employees(id)
) ENGINE=InnoDB;

CREATE TABLE purchase_receipt_items (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    receipt_id BIGINT UNSIGNED NOT NULL,
    order_item_id BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    batch_id BIGINT UNSIGNED NULL,
    received_qty INT NOT NULL,
    accepted_qty INT DEFAULT 0 COMMENT '合格数量',
    rejected_qty INT DEFAULT 0 COMMENT '不合格数量',
    unit_price DECIMAL(12,2) NOT NULL,
    production_date DATE NULL,
    expiry_date DATE NULL,
    remark VARCHAR(200) NULL,
    INDEX idx_order_item (order_item_id),
    INDEX idx_product (product_id),
    INDEX idx_batch (batch_id),
    CONSTRAINT fk_preci_receipt FOREIGN KEY (receipt_id) REFERENCES purchase_receipts(id) ON DELETE CASCADE,
    CONSTRAINT fk_preci_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_preci_batch FOREIGN KEY (batch_id) REFERENCES product_batches(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ============================================================
-- 6. 销售模块
-- ============================================================

CREATE TABLE customers (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    type ENUM('individual','company','government') DEFAULT 'individual',
    contact_person VARCHAR(50) NULL,
    phone VARCHAR(20) NULL,
    email VARCHAR(100) NULL,
    address VARCHAR(300) NULL,
    credit_limit DECIMAL(18,2) DEFAULT 0.00 COMMENT '信用额度',
    credit_days INT DEFAULT 30 COMMENT '账期天数',
    balance DECIMAL(18,2) DEFAULT 0.00 COMMENT '账户余额/欠款',
    membership_level ENUM('normal','silver','gold','platinum','diamond') DEFAULT 'normal',
    status ENUM('active','frozen','blacklist') DEFAULT 'active',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_type (type),
    INDEX idx_status (status),
    INDEX idx_membership (membership_level)
) ENGINE=InnoDB;

-- 销售单
CREATE TABLE sales_orders (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(30) NOT NULL UNIQUE,
    customer_id BIGINT UNSIGNED NOT NULL,
    salesperson_id BIGINT UNSIGNED NOT NULL,
    warehouse_id BIGINT UNSIGNED NOT NULL,
    order_date DATE NOT NULL,
    delivery_date DATE NULL,
    total_amount DECIMAL(18,2) DEFAULT 0.00,
    discount_amount DECIMAL(18,2) DEFAULT 0.00,
    paid_amount DECIMAL(18,2) DEFAULT 0.00,
    tax_amount DECIMAL(18,2) DEFAULT 0.00,
    payment_method ENUM('cash','card','transfer','credit','wechat','alipay') DEFAULT 'transfer',
    status ENUM('draft','confirmed','delivering','delivered','returned','cancelled') DEFAULT 'draft',
    invoice_no VARCHAR(50) NULL,
    remark VARCHAR(500) NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_customer (customer_id),
    INDEX idx_salesperson (salesperson_id),
    INDEX idx_date (order_date),
    INDEX idx_status (status),
    INDEX idx_payment (payment_method),
    CONSTRAINT fk_so_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_so_salesperson FOREIGN KEY (salesperson_id) REFERENCES employees(id),
    CONSTRAINT fk_so_wh FOREIGN KEY (warehouse_id) REFERENCES warehouses(id)
) ENGINE=InnoDB;

CREATE TABLE sales_order_items (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    batch_id BIGINT UNSIGNED NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    discount DECIMAL(12,2) DEFAULT 0.00,
    amount DECIMAL(18,2) NOT NULL COMMENT '折后金额',
    returned_qty INT DEFAULT 0,
    INDEX idx_order (order_id),
    INDEX idx_product (product_id),
    INDEX idx_batch (batch_id),
    CONSTRAINT fk_soi_order FOREIGN KEY (order_id) REFERENCES sales_orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_soi_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_soi_batch FOREIGN KEY (batch_id) REFERENCES product_batches(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- 退库单
-- 流程: 客户申请退货 -> 审批(approved_by) -> 收货验货 -> 退款 -> 财务记账(refund_voucher_id)
-- 退货类型: quality=质量问题, damage=运输损坏, wrong_item=错发, customer_reject=客户拒收, expiry=临期过期
-- 财务影响: 退款金额冲减销售收入, 退货入库恢复库存, 如需报废则走报损流程
CREATE TABLE sales_returns (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    return_no VARCHAR(30) NOT NULL UNIQUE,
    order_id BIGINT UNSIGNED NOT NULL,
    customer_id BIGINT UNSIGNED NOT NULL,
    warehouse_id BIGINT UNSIGNED NOT NULL,
    handler_id BIGINT UNSIGNED NOT NULL COMMENT '处理人',
    return_date DATE NOT NULL,
    return_reason VARCHAR(500) NOT NULL,
    return_type ENUM('quality','damage','wrong_item','customer_reject','expiry','other') DEFAULT 'other',
    total_amount DECIMAL(18,2) DEFAULT 0.00,
    refund_amount DECIMAL(18,2) DEFAULT 0.00 COMMENT '实际退款金额',
    restock_fee DECIMAL(12,2) DEFAULT 0.00 COMMENT '退货折旧费/手续费',
    status ENUM('pending','approved','rejected','received','inspected','refunded','closed') DEFAULT 'pending',
    approved_by BIGINT UNSIGNED NULL COMMENT '审批人',
    approved_at DATETIME NULL COMMENT '审批时间',
    refund_voucher_id BIGINT UNSIGNED NULL COMMENT '退款凭证ID(关联vouchers)',
    return_shipping_fee DECIMAL(12,2) DEFAULT 0.00 COMMENT '退货运费',
    remark VARCHAR(500) NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_order (order_id),
    INDEX idx_customer (customer_id),
    INDEX idx_date (return_date),
    INDEX idx_status (status),
    INDEX idx_approved (approved_by),
    INDEX idx_type (return_type),
    CONSTRAINT fk_sr_order FOREIGN KEY (order_id) REFERENCES sales_orders(id),
    CONSTRAINT fk_sr_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_sr_wh FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT fk_sr_handler FOREIGN KEY (handler_id) REFERENCES employees(id),
    CONSTRAINT fk_sr_approved FOREIGN KEY (approved_by) REFERENCES employees(id),
    CONSTRAINT fk_sr_refund_voucher FOREIGN KEY (refund_voucher_id) REFERENCES vouchers(id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE sales_return_items (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    return_id BIGINT UNSIGNED NOT NULL,
    order_item_id BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    batch_id BIGINT UNSIGNED NULL,
    return_qty INT NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    status ENUM('pending','received','restocked','scrapped') DEFAULT 'pending',
    INDEX idx_order_item (order_item_id),
    CONSTRAINT fk_sri_return FOREIGN KEY (return_id) REFERENCES sales_returns(id) ON DELETE CASCADE,
    CONSTRAINT fk_sri_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_sri_batch FOREIGN KEY (batch_id) REFERENCES product_batches(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ============================================================
-- 6B. 退货给供应商模块 (采购退货)
-- 流程: 发现质量问题/过期 -> 申请退货 -> 审批 -> 出库退回供应商 -> 供应商退款/换货 -> 财务记账
-- 财务影响: 减少应付账款, 减少库存, 如已付款则产生应收
-- 类型: quality=质量问题, expiry=过期, overstock=库存过剩, wrong_delivery=错发
-- ============================================================

CREATE TABLE purchase_returns (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    return_no VARCHAR(30) NOT NULL UNIQUE,
    purchase_order_id BIGINT UNSIGNED NOT NULL,
    purchase_receipt_id BIGINT UNSIGNED NULL,
    supplier_id BIGINT UNSIGNED NOT NULL,
    warehouse_id BIGINT UNSIGNED NOT NULL,
    handler_id BIGINT UNSIGNED NOT NULL,
    return_date DATE NOT NULL,
    return_reason VARCHAR(500) NOT NULL,
    return_type ENUM('quality','expiry','overstock','wrong_delivery','other') DEFAULT 'quality',
    total_amount DECIMAL(18,2) DEFAULT 0.00 COMMENT '退货总金额',
    refund_received DECIMAL(18,2) DEFAULT 0.00 COMMENT '已收到退款',
    shipping_fee DECIMAL(12,2) DEFAULT 0.00 COMMENT '退货运费',
    status ENUM('draft','pending','approved','returned','refunded','rejected','closed') DEFAULT 'draft',
    approved_by BIGINT UNSIGNED NULL,
    approved_at DATETIME NULL,
    refund_voucher_id BIGINT UNSIGNED NULL COMMENT '退款凭证',
    remark VARCHAR(500) NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_po (purchase_order_id),
    INDEX idx_supplier (supplier_id),
    INDEX idx_status (status),
    INDEX idx_date (return_date),
    INDEX idx_type (return_type),
    CONSTRAINT fk_prt_po FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id),
    CONSTRAINT fk_prt_receipt FOREIGN KEY (purchase_receipt_id) REFERENCES purchase_receipts(id) ON DELETE SET NULL,
    CONSTRAINT fk_prt_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id),
    CONSTRAINT fk_prt_wh FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT fk_prt_handler FOREIGN KEY (handler_id) REFERENCES employees(id),
    CONSTRAINT fk_prt_approved FOREIGN KEY (approved_by) REFERENCES employees(id),
    CONSTRAINT fk_prt_voucher FOREIGN KEY (refund_voucher_id) REFERENCES vouchers(id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE purchase_return_items (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    return_id BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    batch_id BIGINT UNSIGNED NULL,
    return_qty INT NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL COMMENT '退货单价(采购价)',
    amount DECIMAL(18,2) GENERATED ALWAYS AS (return_qty * unit_price) STORED,
    reason VARCHAR(200) NULL,
    INDEX idx_product (product_id),
    INDEX idx_batch (batch_id),
    CONSTRAINT fk_pri_return FOREIGN KEY (return_id) REFERENCES purchase_returns(id) ON DELETE CASCADE,
    CONSTRAINT fk_pri_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_pri_batch FOREIGN KEY (batch_id) REFERENCES product_batches(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ============================================================
-- 6C. 报损/报废模块
-- 报损类型: damage=破损, expired=过期, lost=丢失, obsolescence=淘汰
-- 审批流程: 申请 -> 主管审批 -> 财务审批(>500元) -> 执行报废 -> 财务记账
-- 财务影响: 借: 管理费用-报损损失, 贷: 库存商品
-- ============================================================

CREATE TABLE damage_reports (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    report_no VARCHAR(30) NOT NULL UNIQUE,
    warehouse_id BIGINT UNSIGNED NOT NULL,
    report_type ENUM('damage','expired','lost','obsolescence','other') NOT NULL,
    report_date DATE NOT NULL,
    reported_by BIGINT UNSIGNED NOT NULL,
    total_quantity INT DEFAULT 0,
    total_loss_amount DECIMAL(18,2) DEFAULT 0.00 COMMENT '报损总金额(成本价)',
    status ENUM('draft','pending','approved','executed','rejected','closed') DEFAULT 'draft',
    approved_by BIGINT UNSIGNED NULL,
    approved_at DATETIME NULL,
    executed_by BIGINT UNSIGNED NULL,
    executed_at DATETIME NULL,
    voucher_id BIGINT UNSIGNED NULL COMMENT '财务凭证',
    description TEXT NULL,
    remark VARCHAR(500) NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_warehouse (warehouse_id),
    INDEX idx_type (report_type),
    INDEX idx_date (report_date),
    INDEX idx_status (status),
    CONSTRAINT fk_dr_wh FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT fk_dr_reported FOREIGN KEY (reported_by) REFERENCES employees(id),
    CONSTRAINT fk_dr_approved FOREIGN KEY (approved_by) REFERENCES employees(id),
    CONSTRAINT fk_dr_executed FOREIGN KEY (executed_by) REFERENCES employees(id),
    CONSTRAINT fk_dr_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers(id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE damage_report_items (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    report_id BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    batch_id BIGINT UNSIGNED NULL,
    quantity INT NOT NULL,
    unit_cost DECIMAL(12,2) NOT NULL COMMENT '单位成本',
    loss_amount DECIMAL(18,2) GENERATED ALWAYS AS (quantity * unit_cost) STORED,
    reason VARCHAR(200) NULL,
    INDEX idx_product (product_id),
    INDEX idx_batch (batch_id),
    CONSTRAINT fk_dri_report FOREIGN KEY (report_id) REFERENCES damage_reports(id) ON DELETE CASCADE,
    CONSTRAINT fk_dri_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_dri_batch FOREIGN KEY (batch_id) REFERENCES product_batches(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ============================================================
-- 7. 财务模块
-- ============================================================

-- 财务账户
CREATE TABLE accounts (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE COMMENT '科目编码',
    parent_id BIGINT UNSIGNED NULL,
    name VARCHAR(100) NOT NULL,
    account_type ENUM('asset','liability','equity','revenue','expense','cost') NOT NULL,
    balance_direction ENUM('debit','credit') NOT NULL COMMENT '余额方向',
    is_cash BOOLEAN DEFAULT FALSE COMMENT '是否现金科目',
    is_bank BOOLEAN DEFAULT FALSE COMMENT '是否银行科目',
    bank_name VARCHAR(100) NULL,
    bank_account VARCHAR(50) NULL,
    current_balance DECIMAL(18,2) DEFAULT 0.00,
    status ENUM('active','frozen','closed') DEFAULT 'active',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_parent (parent_id),
    INDEX idx_type (account_type),
    CONSTRAINT fk_acct_parent FOREIGN KEY (parent_id) REFERENCES accounts(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- 财务凭证
CREATE TABLE vouchers (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    voucher_no VARCHAR(30) NOT NULL UNIQUE,
    voucher_date DATE NOT NULL,
    voucher_type ENUM('receipt','payment','transfer','journal') NOT NULL,
    reference_type VARCHAR(50) NULL,
    reference_id BIGINT NULL,
    total_debit DECIMAL(18,2) DEFAULT 0.00,
    total_credit DECIMAL(18,2) DEFAULT 0.00,
    prepared_by BIGINT UNSIGNED NOT NULL,
    reviewed_by BIGINT UNSIGNED NULL,
    posted_by BIGINT UNSIGNED NULL,
    status ENUM('draft','reviewed','posted','cancelled') DEFAULT 'draft',
    summary VARCHAR(500) NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_date (voucher_date),
    INDEX idx_type (voucher_type),
    INDEX idx_reference (reference_type, reference_id),
    INDEX idx_status (status),
    CONSTRAINT fk_v_prepared FOREIGN KEY (prepared_by) REFERENCES employees(id),
    CONSTRAINT fk_v_reviewed FOREIGN KEY (reviewed_by) REFERENCES employees(id),
    CONSTRAINT fk_v_posted FOREIGN KEY (posted_by) REFERENCES employees(id)
) ENGINE=InnoDB;

CREATE TABLE voucher_items (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    voucher_id BIGINT UNSIGNED NOT NULL,
    account_id BIGINT UNSIGNED NOT NULL,
    line_no INT NOT NULL,
    direction ENUM('debit','credit') NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    summary VARCHAR(500) NULL,
    INDEX idx_voucher (voucher_id),
    INDEX idx_account (account_id),
    CONSTRAINT fk_vi_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers(id) ON DELETE CASCADE,
    CONSTRAINT fk_vi_account FOREIGN KEY (account_id) REFERENCES accounts(id)
) ENGINE=InnoDB;

-- 出纳日记账
CREATE TABLE cashier_journals (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    journal_no VARCHAR(30) NOT NULL UNIQUE,
    journal_date DATE NOT NULL,
    account_id BIGINT UNSIGNED NOT NULL,
    cashier_id BIGINT UNSIGNED NOT NULL,
    journal_type ENUM('cash_in','cash_out','bank_in','bank_out','transfer') NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    counterparty VARCHAR(200) NULL COMMENT '对方单位/个人',
    reference_type VARCHAR(50) NULL,
    reference_id BIGINT NULL,
    voucher_id BIGINT UNSIGNED NULL,
    bank_account VARCHAR(50) NULL,
    check_no VARCHAR(50) NULL,
    status ENUM('pending','verified','reconciled') DEFAULT 'pending',
    remark VARCHAR(500) NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_date (journal_date),
    INDEX idx_account (account_id),
    INDEX idx_cashier (cashier_id),
    INDEX idx_status (status),
    INDEX idx_voucher (voucher_id),
    CONSTRAINT fk_cj_account FOREIGN KEY (account_id) REFERENCES accounts(id),
    CONSTRAINT fk_cj_cashier FOREIGN KEY (cashier_id) REFERENCES employees(id),
    CONSTRAINT fk_cj_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- 工资发放表
CREATE TABLE salary_payments (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    payment_no VARCHAR(30) NOT NULL UNIQUE,
    employee_id BIGINT UNSIGNED NOT NULL,
    payment_date DATE NOT NULL,
    salary_month VARCHAR(7) NOT NULL COMMENT 'YYYY-MM',
    base_salary DECIMAL(12,2) NOT NULL,
    overtime_pay DECIMAL(12,2) DEFAULT 0.00,
    bonus DECIMAL(12,2) DEFAULT 0.00,
    deduction DECIMAL(12,2) DEFAULT 0.00 COMMENT '扣款',
    social_security_personal DECIMAL(12,2) DEFAULT 0.00 COMMENT '个人社保',
    housing_fund_personal DECIMAL(12,2) DEFAULT 0.00 COMMENT '个人公积金',
    income_tax DECIMAL(12,2) DEFAULT 0.00 COMMENT '个税',
    net_pay DECIMAL(12,2) NOT NULL COMMENT '实发工资',
    social_security_company DECIMAL(12,2) DEFAULT 0.00 COMMENT '公司社保',
    housing_fund_company DECIMAL(12,2) DEFAULT 0.00 COMMENT '公司公积金',
    payment_method ENUM('bank_transfer','cash','check') DEFAULT 'bank_transfer',
    status ENUM('pending','paid','failed') DEFAULT 'pending',
    paid_at DATETIME NULL,
    voucher_id BIGINT UNSIGNED NULL,
    remark VARCHAR(200) NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_emp_month (employee_id, salary_month),
    INDEX idx_date (payment_date),
    INDEX idx_month (salary_month),
    INDEX idx_status (status),
    CONSTRAINT fk_sp_emp FOREIGN KEY (employee_id) REFERENCES employees(id),
    CONSTRAINT fk_sp_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- 对账表
CREATE TABLE reconciliations (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    recon_no VARCHAR(30) NOT NULL UNIQUE,
    account_id BIGINT UNSIGNED NOT NULL,
    recon_date DATE NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    book_balance DECIMAL(18,2) NOT NULL COMMENT '账面余额',
    bank_balance DECIMAL(18,2) DEFAULT 0.00 COMMENT '银行余额',
    difference DECIMAL(18,2) DEFAULT 0.00 COMMENT '差异',
    unreconciled_income DECIMAL(18,2) DEFAULT 0.00 COMMENT '未达账-收入',
    unreconciled_expense DECIMAL(18,2) DEFAULT 0.00 COMMENT '未达账-支出',
    adjusted_balance DECIMAL(18,2) DEFAULT 0.00 COMMENT '调整后余额',
    prepared_by BIGINT UNSIGNED NOT NULL,
    reviewed_by BIGINT UNSIGNED NULL,
    status ENUM('draft','prepared','reviewed','approved','disputed') DEFAULT 'draft',
    remark VARCHAR(500) NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_account (account_id),
    INDEX idx_period (period_start, period_end),
    INDEX idx_status (status),
    CONSTRAINT fk_recon_account FOREIGN KEY (account_id) REFERENCES accounts(id),
    CONSTRAINT fk_recon_prepared FOREIGN KEY (prepared_by) REFERENCES employees(id),
    CONSTRAINT fk_recon_reviewed FOREIGN KEY (reviewed_by) REFERENCES employees(id)
) ENGINE=InnoDB;

-- 对账明细
CREATE TABLE reconciliation_items (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    reconciliation_id BIGINT UNSIGNED NOT NULL,
    journal_id BIGINT UNSIGNED NULL,
    transaction_date DATE NOT NULL,
    description VARCHAR(500) NOT NULL,
    debit_amount DECIMAL(18,2) DEFAULT 0.00,
    credit_amount DECIMAL(18,2) DEFAULT 0.00,
    is_matched BOOLEAN DEFAULT FALSE,
    matched_item_id BIGINT UNSIGNED NULL,
    difference_reason VARCHAR(500) NULL,
    INDEX idx_recon (reconciliation_id),
    INDEX idx_journal (journal_id),
    CONSTRAINT fk_ri_recon FOREIGN KEY (reconciliation_id) REFERENCES reconciliations(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 结算单
CREATE TABLE settlements (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    settlement_no VARCHAR(30) NOT NULL UNIQUE,
    settlement_type ENUM('supplier','customer','internal','salary','tax') NOT NULL,
    party_id BIGINT UNSIGNED NOT NULL COMMENT '对方ID（供应商/客户/员工等）',
    settlement_date DATE NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_amount DECIMAL(18,2) NOT NULL,
    settled_amount DECIMAL(18,2) DEFAULT 0.00,
    unpaid_amount DECIMAL(18,2) GENERATED ALWAYS AS (total_amount - settled_amount) STORED,
    payment_due_date DATE NULL,
    payment_method ENUM('bank_transfer','cash','check','credit','offset') DEFAULT 'bank_transfer',
    status ENUM('pending','partial','settled','overdue','disputed') DEFAULT 'pending',
    voucher_id BIGINT UNSIGNED NULL,
    prepared_by BIGINT UNSIGNED NOT NULL,
    approved_by BIGINT UNSIGNED NULL,
    remark VARCHAR(500) NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_party (settlement_type, party_id),
    INDEX idx_date (settlement_date),
    INDEX idx_status (status),
    INDEX idx_due_date (payment_due_date),
    CONSTRAINT fk_settle_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers(id) ON DELETE SET NULL,
    CONSTRAINT fk_settle_prepared FOREIGN KEY (prepared_by) REFERENCES employees(id),
    CONSTRAINT fk_settle_approved FOREIGN KEY (approved_by) REFERENCES employees(id)
) ENGINE=InnoDB;

-- 结算明细
CREATE TABLE settlement_items (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    settlement_id BIGINT UNSIGNED NOT NULL,
    reference_type VARCHAR(50) NOT NULL COMMENT '关联单据类型',
    reference_id BIGINT NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    settled_amount DECIMAL(18,2) DEFAULT 0.00,
    remark VARCHAR(200) NULL,
    INDEX idx_settlement (settlement_id),
    INDEX idx_reference (reference_type, reference_id),
    CONSTRAINT fk_si_settlement FOREIGN KEY (settlement_id) REFERENCES settlements(id) ON DELETE CASCADE
) ENGINE=InnoDB;