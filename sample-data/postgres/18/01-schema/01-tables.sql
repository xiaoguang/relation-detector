-- ============================================================
-- ERP系统完整数据库设计 - PostgreSQL 18
-- 模块: HR, 权限, 货品, 批号, 库存, 采购, 销售, 财务
-- 数据库: PostgreSQL 18
-- ============================================================

-- ============================================================
-- 0. 自定义ENUM类型
-- ============================================================

-- 通用状态
CREATE TYPE active_status AS ENUM ('active', 'inactive');

-- 部门状态
CREATE TYPE dept_status AS ENUM ('active', 'frozen', 'dissolved');

-- 职位状态
CREATE TYPE position_status AS ENUM ('active', 'frozen', 'closed');

-- 性别
CREATE TYPE gender_type AS ENUM ('M', 'F');

-- 员工状态
CREATE TYPE emp_status AS ENUM ('active', 'probation', 'leave', 'resigned', 'terminated');

-- 考勤状态
CREATE TYPE attendance_status AS ENUM ('normal', 'late', 'early', 'absent', 'overtime', 'leave');

-- 请假类型
CREATE TYPE leave_type AS ENUM ('annual', 'sick', 'personal', 'maternity', 'bereavement', 'marriage', 'other');

-- 请假审批状态
CREATE TYPE leave_req_status AS ENUM ('pending', 'approved', 'rejected', 'cancelled');

-- 货品状态
CREATE TYPE product_status AS ENUM ('active', 'discontinued', 'seasonal');

-- 供应商信用等级
CREATE TYPE credit_level_type AS ENUM ('A', 'B', 'C', 'D');

-- 供应商合作状态
CREATE TYPE coop_status AS ENUM ('active', 'suspended', 'blacklist');

-- 批号状态
CREATE TYPE batch_status AS ENUM ('active', 'locked', 'exhausted', 'expired');

-- 仓库类型
CREATE TYPE warehouse_type AS ENUM ('main', 'transit', 'returns', 'cold');

-- 仓库状态
CREATE TYPE wh_status AS ENUM ('active', 'inactive', 'maintenance');

-- 库存交易类型
CREATE TYPE inv_transaction_type AS ENUM (
    'purchase_in', 'sales_out', 'return_in', 'return_out',
    'transfer_in', 'transfer_out', 'stocktake_adjust',
    'damage_out', 'scrap_out', 'lock', 'unlock', 'production_in'
);

-- 请购紧急程度
CREATE TYPE urgency_type AS ENUM ('normal', 'urgent', 'emergency');

-- 请购单状态
CREATE TYPE req_status AS ENUM ('draft', 'pending', 'approved', 'rejected', 'ordered', 'cancelled');

-- 采购单状态
CREATE TYPE po_status AS ENUM ('draft', 'pending', 'approved', 'ordered', 'partially_received', 'received', 'cancelled', 'returned');

-- 收货单状态
CREATE TYPE receipt_status AS ENUM ('pending', 'received', 'inspected', 'rejected', 'partial');

-- 客户类型
CREATE TYPE customer_type AS ENUM ('individual', 'company', 'government');

-- 会员等级
CREATE TYPE membership_level AS ENUM ('normal', 'silver', 'gold', 'platinum', 'diamond');

-- 客户状态
CREATE TYPE customer_status AS ENUM ('active', 'frozen', 'blacklist');

-- 支付方式
CREATE TYPE payment_method AS ENUM ('cash', 'card', 'transfer', 'credit', 'wechat', 'alipay');

-- 销售单状态
CREATE TYPE so_status AS ENUM ('draft', 'confirmed', 'delivering', 'delivered', 'returned', 'cancelled');

-- 销售退货类型
CREATE TYPE sales_return_type AS ENUM ('quality', 'damage', 'wrong_item', 'customer_reject', 'expiry', 'other');

-- 销售退货状态
CREATE TYPE sales_return_status AS ENUM ('pending', 'approved', 'rejected', 'received', 'inspected', 'refunded', 'closed');

-- 退货明细状态
CREATE TYPE return_item_status AS ENUM ('pending', 'received', 'restocked', 'scrapped');

-- 采购退货类型
CREATE TYPE purchase_return_type AS ENUM ('quality', 'expiry', 'overstock', 'wrong_delivery', 'other');

-- 采购退货状态
CREATE TYPE purchase_return_status AS ENUM ('draft', 'pending', 'approved', 'returned', 'refunded', 'rejected', 'closed');

-- 报损类型
CREATE TYPE damage_report_type AS ENUM ('damage', 'expired', 'lost', 'obsolescence', 'other');

-- 报损状态
CREATE TYPE damage_report_status AS ENUM ('draft', 'pending', 'approved', 'executed', 'rejected', 'closed');

-- 会计科目类型
CREATE TYPE account_type AS ENUM ('asset', 'liability', 'equity', 'revenue', 'expense', 'cost');

-- 余额方向
CREATE TYPE balance_direction AS ENUM ('debit', 'credit');

-- 科目状态
CREATE TYPE account_status AS ENUM ('active', 'frozen', 'closed');

-- 凭证类型
CREATE TYPE voucher_type AS ENUM ('receipt', 'payment', 'transfer', 'journal');

-- 凭证状态
CREATE TYPE voucher_status AS ENUM ('draft', 'reviewed', 'posted', 'cancelled');

-- 分录方向
CREATE TYPE entry_direction AS ENUM ('debit', 'credit');

-- 出纳日记账类型
CREATE TYPE journal_type AS ENUM ('cash_in', 'cash_out', 'bank_in', 'bank_out', 'transfer');

-- 出纳日记账状态
CREATE TYPE journal_status AS ENUM ('pending', 'verified', 'reconciled');

-- 工资发放方式
CREATE TYPE salary_payment_method AS ENUM ('bank_transfer', 'cash', 'check');

-- 工资发放状态
CREATE TYPE salary_payment_status AS ENUM ('pending', 'paid', 'failed');

-- 对账状态
CREATE TYPE recon_status AS ENUM ('draft', 'prepared', 'reviewed', 'approved', 'disputed');

-- 结算类型
CREATE TYPE settlement_type AS ENUM ('supplier', 'customer', 'internal', 'salary', 'tax');

-- 结算支付方式
CREATE TYPE settlement_payment_method AS ENUM ('bank_transfer', 'cash', 'check', 'credit', 'offset');

-- 结算状态
CREATE TYPE settlement_status AS ENUM ('pending', 'partial', 'settled', 'overdue', 'disputed');


-- ============================================================
-- 触发器辅助函数: 自动更新updated_at
-- ============================================================

CREATE OR REPLACE FUNCTION update_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


-- ============================================================
-- 1. HR模块 - 组织架构
-- ============================================================

-- 部门表
CREATE TABLE departments (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    parent_id BIGINT CHECK (parent_id IS NULL OR parent_id >= 0),
    name VARCHAR(100) NOT NULL,
    code VARCHAR(20) NOT NULL UNIQUE,
    manager_id BIGINT CHECK (manager_id IS NULL OR manager_id >= 0),
    budget DECIMAL(18,2) DEFAULT 0.00,
    headcount_plan INTEGER CHECK (headcount_plan IS NULL OR headcount_plan >= 0) DEFAULT 0,
    status dept_status DEFAULT 'active',
    created_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dept_parent FOREIGN KEY (parent_id) REFERENCES departments(id) ON DELETE SET NULL
);

CREATE INDEX idx_dept_parent ON departments(parent_id);
CREATE INDEX idx_dept_status ON departments(status);

COMMENT ON TABLE departments IS '部门表';
COMMENT ON COLUMN departments.manager_id IS '部门负责人';
COMMENT ON COLUMN departments.budget IS '部门预算';
COMMENT ON COLUMN departments.headcount_plan IS '计划编制人数';

CREATE TRIGGER trg_departments_updated_at
    BEFORE UPDATE ON departments
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

-- 职位表
CREATE TABLE positions (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    department_id BIGINT NOT NULL CHECK (department_id >= 0),
    name VARCHAR(100) NOT NULL,
    code VARCHAR(20) NOT NULL,
    level SMALLINT CHECK (level IS NULL OR (level >= 0 AND level <= 15)) DEFAULT 1,
    min_salary DECIMAL(12,2) DEFAULT 0.00,
    max_salary DECIMAL(12,2) DEFAULT 0.00,
    headcount INTEGER DEFAULT 1 CHECK (headcount >= 0),
    status position_status DEFAULT 'active',
    created_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (department_id, code),
    CONSTRAINT fk_pos_dept FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE CASCADE
);

CREATE INDEX idx_pos_level ON positions(level);

COMMENT ON TABLE positions IS '职位表';
COMMENT ON COLUMN positions.level IS '职级 1-15';
COMMENT ON COLUMN positions.headcount IS '编制人数';

-- 员工表
CREATE TABLE employees (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    employee_no VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(50) NOT NULL,
    gender gender_type NOT NULL,
    id_card VARCHAR(18) NOT NULL UNIQUE,
    phone VARCHAR(20) NOT NULL,
    email VARCHAR(100),
    birth_date DATE NOT NULL,
    hire_date DATE NOT NULL,
    department_id BIGINT NOT NULL CHECK (department_id >= 0),
    position_id BIGINT NOT NULL CHECK (position_id >= 0),
    manager_id BIGINT CHECK (manager_id IS NULL OR manager_id >= 0),
    salary DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    social_security_base DECIMAL(12,2) DEFAULT 0.00,
    housing_fund_base DECIMAL(12,2) DEFAULT 0.00,
    bank_name VARCHAR(100),
    bank_account VARCHAR(50),
    status emp_status DEFAULT 'probation',
    resignation_date DATE,
    resignation_reason VARCHAR(500),
    address VARCHAR(200),
    emergency_contact VARCHAR(50),
    emergency_phone VARCHAR(20),
    created_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_emp_dept FOREIGN KEY (department_id) REFERENCES departments(id),
    CONSTRAINT fk_emp_pos FOREIGN KEY (position_id) REFERENCES positions(id),
    CONSTRAINT fk_emp_manager FOREIGN KEY (manager_id) REFERENCES employees(id) ON DELETE SET NULL
);

CREATE INDEX idx_emp_dept ON employees(department_id);
CREATE INDEX idx_emp_position ON employees(position_id);
CREATE INDEX idx_emp_manager ON employees(manager_id);
CREATE INDEX idx_emp_status ON employees(status);
CREATE INDEX idx_emp_hire_date ON employees(hire_date);
CREATE INDEX idx_emp_resignation ON employees(resignation_date);

COMMENT ON TABLE employees IS '员工表';
COMMENT ON COLUMN employees.employee_no IS '工号';
COMMENT ON COLUMN employees.id_card IS '身份证号';
COMMENT ON COLUMN employees.social_security_base IS '社保基数';
COMMENT ON COLUMN employees.housing_fund_base IS '公积金基数';
COMMENT ON COLUMN employees.bank_name IS '开户银行';
COMMENT ON COLUMN employees.bank_account IS '银行账号';

CREATE TRIGGER trg_employees_updated_at
    BEFORE UPDATE ON employees
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

-- 更新部门负责人外键
ALTER TABLE departments ADD CONSTRAINT fk_dept_manager FOREIGN KEY (manager_id) REFERENCES employees(id) ON DELETE SET NULL;

-- 薪资变动记录
CREATE TABLE employee_salary_log (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    employee_id BIGINT NOT NULL CHECK (employee_id >= 0),
    old_salary DECIMAL(12,2) NOT NULL,
    new_salary DECIMAL(12,2) NOT NULL,
    change_reason VARCHAR(200) NOT NULL,
    effective_date DATE NOT NULL,
    approved_by BIGINT CHECK (approved_by IS NULL OR approved_by >= 0),
    created_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sal_emp FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE
);

CREATE INDEX idx_sal_employee ON employee_salary_log(employee_id);
CREATE INDEX idx_sal_effective_date ON employee_salary_log(effective_date);

-- 考勤记录
CREATE TABLE attendance (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    employee_id BIGINT NOT NULL CHECK (employee_id >= 0),
    attendance_date DATE NOT NULL,
    clock_in TIMESTAMP(0),
    clock_out TIMESTAMP(0),
    status attendance_status DEFAULT 'normal',
    late_minutes INTEGER DEFAULT 0,
    early_minutes INTEGER DEFAULT 0,
    overtime_hours DECIMAL(4,1) DEFAULT 0.0,
    remark VARCHAR(200),
    UNIQUE (employee_id, attendance_date),
    CONSTRAINT fk_att_emp FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE
);

CREATE INDEX idx_att_date ON attendance(attendance_date);

-- 请假记录
CREATE TABLE leave_records (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    employee_id BIGINT NOT NULL CHECK (employee_id >= 0),
    leave_type leave_type NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    days DECIMAL(4,1) NOT NULL,
    reason VARCHAR(500),
    status leave_req_status DEFAULT 'pending',
    approved_by BIGINT CHECK (approved_by IS NULL OR approved_by >= 0),
    approved_at TIMESTAMP(0),
    created_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_leave_emp FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE
);

CREATE INDEX idx_leave_emp ON leave_records(employee_id);
CREATE INDEX idx_leave_date_range ON leave_records(start_date, end_date);
CREATE INDEX idx_leave_status ON leave_records(status);

-- ============================================================
-- 2. 权限模块
-- ============================================================

CREATE TABLE roles (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    code VARCHAR(30) NOT NULL UNIQUE,
    description VARCHAR(200),
    is_system BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON COLUMN roles.is_system IS '系统内置角色';

CREATE TABLE permissions (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    parent_id BIGINT CHECK (parent_id IS NULL OR parent_id >= 0),
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    resource_type VARCHAR(50) NOT NULL,
    resource_path VARCHAR(200),
    action VARCHAR(50) NOT NULL,
    created_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_perm_parent FOREIGN KEY (parent_id) REFERENCES permissions(id) ON DELETE SET NULL
);

CREATE INDEX idx_perm_parent ON permissions(parent_id);
CREATE INDEX idx_perm_resource ON permissions(resource_type);

COMMENT ON COLUMN permissions.code IS '权限标识符';
COMMENT ON COLUMN permissions.resource_type IS '资源类型: menu, api, button, data';
COMMENT ON COLUMN permissions.resource_path IS '资源路径';
COMMENT ON COLUMN permissions.action IS '操作: create,read,update,delete,approve,export,import';

CREATE TABLE role_permissions (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    role_id BIGINT NOT NULL CHECK (role_id >= 0),
    permission_id BIGINT NOT NULL CHECK (permission_id >= 0),
    UNIQUE (role_id, permission_id),
    CONSTRAINT fk_rp_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    CONSTRAINT fk_rp_perm FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
);

CREATE TABLE employee_roles (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    employee_id BIGINT NOT NULL CHECK (employee_id >= 0),
    role_id BIGINT NOT NULL CHECK (role_id >= 0),
    granted_by BIGINT CHECK (granted_by IS NULL OR granted_by >= 0),
    granted_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP(0),
    UNIQUE (employee_id, role_id),
    CONSTRAINT fk_er_emp FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
    CONSTRAINT fk_er_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

-- 操作审计日志
CREATE TABLE audit_log (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    employee_id BIGINT CHECK (employee_id IS NULL OR employee_id >= 0),
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id BIGINT,
    old_value JSONB,
    new_value JSONB,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    remark VARCHAR(500),
    created_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_employee ON audit_log(employee_id);
CREATE INDEX idx_audit_target ON audit_log(target_type, target_id);
CREATE INDEX idx_audit_created_at ON audit_log(created_at);
CREATE INDEX idx_audit_action ON audit_log(action);

COMMENT ON COLUMN audit_log.target_type IS '操作对象类型';
COMMENT ON COLUMN audit_log.target_id IS '操作对象ID';

-- ============================================================
-- 3. 货品与批号模块
-- ============================================================

CREATE TABLE product_categories (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    parent_id BIGINT CHECK (parent_id IS NULL OR parent_id >= 0),
    name VARCHAR(100) NOT NULL,
    code VARCHAR(20) NOT NULL UNIQUE,
    description VARCHAR(500),
    sort_order INTEGER DEFAULT 0,
    status active_status DEFAULT 'active',
    created_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cat_parent FOREIGN KEY (parent_id) REFERENCES product_categories(id) ON DELETE SET NULL
);

CREATE INDEX idx_cat_parent ON product_categories(parent_id);

-- 供应商表
CREATE TABLE suppliers (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    code VARCHAR(20) NOT NULL UNIQUE,
    contact_person VARCHAR(50),
    phone VARCHAR(20),
    email VARCHAR(100),
    address VARCHAR(300),
    province VARCHAR(50),
    city VARCHAR(50),
    district VARCHAR(50),
    latitude DECIMAL(10,7),
    longitude DECIMAL(10,7),
    bank_name VARCHAR(100),
    bank_account VARCHAR(50),
    tax_id VARCHAR(50),
    credit_level credit_level_type DEFAULT 'B',
    cooperation_status coop_status DEFAULT 'active',
    created_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_supplier_status ON suppliers(cooperation_status);
CREATE INDEX idx_supplier_city ON suppliers(city);
CREATE INDEX idx_supplier_province ON suppliers(province);

COMMENT ON TABLE suppliers IS '供应商表';
COMMENT ON COLUMN suppliers.province IS '省份';
COMMENT ON COLUMN suppliers.city IS '城市';
COMMENT ON COLUMN suppliers.district IS '区县';
COMMENT ON COLUMN suppliers.latitude IS '纬度';
COMMENT ON COLUMN suppliers.longitude IS '经度';
COMMENT ON COLUMN suppliers.tax_id IS '税号';

CREATE TRIGGER trg_suppliers_updated_at
    BEFORE UPDATE ON suppliers
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

CREATE TABLE products (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    sku VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    category_id BIGINT NOT NULL CHECK (category_id >= 0),
    unit VARCHAR(20) NOT NULL DEFAULT '件',
    spec VARCHAR(100),
    brand VARCHAR(100),
    barcode VARCHAR(50),
    purchase_price DECIMAL(12,2) DEFAULT 0.00,
    wholesale_price DECIMAL(12,2) DEFAULT 0.00,
    retail_price DECIMAL(12,2) DEFAULT 0.00,
    min_stock INTEGER DEFAULT 0,
    max_stock INTEGER DEFAULT 99999,
    batch_managed BOOLEAN DEFAULT TRUE,
    shelf_life_days INTEGER DEFAULT 0,
    weight_kg DECIMAL(8,3) DEFAULT 0.000,
    volume_m3 DECIMAL(8,6) DEFAULT 0.000000,
    status product_status DEFAULT 'active',
    created_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_prod_cat FOREIGN KEY (category_id) REFERENCES product_categories(id)
);

CREATE INDEX idx_prod_category ON products(category_id);
CREATE INDEX idx_prod_status ON products(status);
CREATE INDEX idx_prod_barcode ON products(barcode);

COMMENT ON COLUMN products.sku IS 'SKU编码';
COMMENT ON COLUMN products.unit IS '计量单位';
COMMENT ON COLUMN products.spec IS '规格型号';
COMMENT ON COLUMN products.purchase_price IS '进货价';
COMMENT ON COLUMN products.wholesale_price IS '批发价';
COMMENT ON COLUMN products.retail_price IS '零售价';
COMMENT ON COLUMN products.min_stock IS '最低库存警戒';
COMMENT ON COLUMN products.max_stock IS '最高库存';
COMMENT ON COLUMN products.batch_managed IS '是否批号管理';
COMMENT ON COLUMN products.shelf_life_days IS '保质期天数';

CREATE TRIGGER trg_products_updated_at
    BEFORE UPDATE ON products
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

-- 供应商-货品关联
CREATE TABLE supplier_products (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    supplier_id BIGINT NOT NULL CHECK (supplier_id >= 0),
    product_id BIGINT NOT NULL CHECK (product_id >= 0),
    supplier_price DECIMAL(12,2) DEFAULT 0.00,
    lead_time_days INTEGER DEFAULT 0,
    min_order_qty INTEGER DEFAULT 1,
    shipping_cost_per_km DECIMAL(8,4) DEFAULT 0.50,
    return_rate DECIMAL(5,4) DEFAULT 0.00,
    quality_score DECIMAL(5,2) DEFAULT 100.00,
    is_preferred BOOLEAN DEFAULT FALSE,
    last_order_date DATE,
    total_order_count INTEGER DEFAULT 0,
    total_order_qty INTEGER DEFAULT 0,
    UNIQUE (supplier_id, product_id),
    CONSTRAINT fk_sp_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id) ON DELETE CASCADE,
    CONSTRAINT fk_sp_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);

CREATE INDEX idx_sp_product_price ON supplier_products(product_id, supplier_price);
CREATE INDEX idx_sp_quality ON supplier_products(quality_score);

COMMENT ON COLUMN supplier_products.supplier_price IS '供应商报价';
COMMENT ON COLUMN supplier_products.lead_time_days IS '供货周期(天)';
COMMENT ON COLUMN supplier_products.min_order_qty IS '最小起订量';
COMMENT ON COLUMN supplier_products.shipping_cost_per_km IS '每公里物流费率(元/km)';
COMMENT ON COLUMN supplier_products.return_rate IS '该货品历史退货率(0-1)';
COMMENT ON COLUMN supplier_products.quality_score IS '质检评分(0-100)';
COMMENT ON COLUMN supplier_products.is_preferred IS '是否优选供应商';
COMMENT ON COLUMN supplier_products.last_order_date IS '最近采购日期';
COMMENT ON COLUMN supplier_products.total_order_count IS '累计采购次数';
COMMENT ON COLUMN supplier_products.total_order_qty IS '累计采购数量';

-- 批号管理
CREATE TABLE product_batches (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    product_id BIGINT NOT NULL CHECK (product_id >= 0),
    batch_no VARCHAR(50) NOT NULL,
    production_date DATE,
    expiry_date DATE,
    supplier_id BIGINT CHECK (supplier_id IS NULL OR supplier_id >= 0),
    purchase_price DECIMAL(12,2) DEFAULT 0.00,
    initial_qty INTEGER NOT NULL DEFAULT 0,
    current_qty INTEGER NOT NULL DEFAULT 0,
    status batch_status DEFAULT 'active',
    created_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (product_id, batch_no),
    CONSTRAINT fk_batch_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_batch_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id) ON DELETE SET NULL
);

CREATE INDEX idx_batch_expiry ON product_batches(expiry_date);
CREATE INDEX idx_batch_status ON product_batches(status);
CREATE INDEX idx_batch_supplier ON product_batches(supplier_id);

COMMENT ON COLUMN product_batches.production_date IS '生产日期';
COMMENT ON COLUMN product_batches.expiry_date IS '过期日期';
COMMENT ON COLUMN product_batches.initial_qty IS '初始数量';
COMMENT ON COLUMN product_batches.current_qty IS '当前数量';

-- ============================================================
-- 4. 库存模块
-- ============================================================

CREATE TABLE warehouses (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(20) NOT NULL UNIQUE,
    address VARCHAR(200),
    province VARCHAR(50),
    city VARCHAR(50),
    district VARCHAR(50),
    latitude DECIMAL(10,7),
    longitude DECIMAL(10,7),
    manager_id BIGINT CHECK (manager_id IS NULL OR manager_id >= 0),
    type warehouse_type DEFAULT 'main',
    capacity_m3 DECIMAL(10,3) DEFAULT 0.000,
    status wh_status DEFAULT 'active',
    created_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_wh_manager FOREIGN KEY (manager_id) REFERENCES employees(id) ON DELETE SET NULL
);

CREATE INDEX idx_wh_type ON warehouses(type);
CREATE INDEX idx_wh_city ON warehouses(city);
CREATE INDEX idx_wh_province ON warehouses(province);

COMMENT ON TABLE warehouses IS '仓库/门店表';
COMMENT ON COLUMN warehouses.province IS '省份';
COMMENT ON COLUMN warehouses.city IS '城市';
COMMENT ON COLUMN warehouses.district IS '区县';

CREATE TABLE inventory (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    product_id BIGINT NOT NULL CHECK (product_id >= 0),
    batch_id BIGINT CHECK (batch_id IS NULL OR batch_id >= 0),
    warehouse_id BIGINT NOT NULL CHECK (warehouse_id >= 0),
    shelf_location VARCHAR(50),
    quantity INTEGER NOT NULL DEFAULT 0,
    locked_quantity INTEGER DEFAULT 0,
    available_quantity INTEGER GENERATED ALWAYS AS (quantity - locked_quantity) STORED,
    last_stocktake_date DATE,
    updated_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (product_id, batch_id, warehouse_id),
    CONSTRAINT fk_inv_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_inv_batch FOREIGN KEY (batch_id) REFERENCES product_batches(id) ON DELETE SET NULL,
    CONSTRAINT fk_inv_wh FOREIGN KEY (warehouse_id) REFERENCES warehouses(id) ON DELETE CASCADE
);

CREATE INDEX idx_inv_warehouse ON inventory(warehouse_id);
CREATE INDEX idx_inv_quantity ON inventory(quantity);
CREATE INDEX idx_inv_available ON inventory(available_quantity);

COMMENT ON COLUMN inventory.shelf_location IS '货架位置';
COMMENT ON COLUMN inventory.locked_quantity IS '锁定库存';

CREATE TRIGGER trg_inventory_updated_at
    BEFORE UPDATE ON inventory
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

-- 库存变动日志
CREATE TABLE inventory_transactions (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    product_id BIGINT NOT NULL CHECK (product_id >= 0),
    batch_id BIGINT CHECK (batch_id IS NULL OR batch_id >= 0),
    warehouse_id BIGINT NOT NULL CHECK (warehouse_id >= 0),
    transaction_type inv_transaction_type NOT NULL,
    quantity_change INTEGER NOT NULL,
    before_qty INTEGER NOT NULL,
    after_qty INTEGER NOT NULL,
    reference_type VARCHAR(50),
    reference_id BIGINT,
    operator_id BIGINT CHECK (operator_id IS NULL OR operator_id >= 0),
    remark VARCHAR(500),
    created_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_it_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_it_batch FOREIGN KEY (batch_id) REFERENCES product_batches(id) ON DELETE SET NULL,
    CONSTRAINT fk_it_wh FOREIGN KEY (warehouse_id) REFERENCES warehouses(id) ON DELETE CASCADE
);

CREATE INDEX idx_it_product ON inventory_transactions(product_id);
CREATE INDEX idx_it_warehouse ON inventory_transactions(warehouse_id);
CREATE INDEX idx_it_type ON inventory_transactions(transaction_type);
CREATE INDEX idx_it_reference ON inventory_transactions(reference_type, reference_id);
CREATE INDEX idx_it_created_at ON inventory_transactions(created_at);

COMMENT ON COLUMN inventory_transactions.quantity_change IS '正数=入库，负数=出库';

-- ============================================================
-- 5. 采购模块
-- ============================================================

-- 请购单
CREATE TABLE purchase_requisitions (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    requisition_no VARCHAR(30) NOT NULL UNIQUE,
    department_id BIGINT NOT NULL CHECK (department_id >= 0),
    requester_id BIGINT NOT NULL CHECK (requester_id >= 0),
    requisition_date DATE NOT NULL,
    required_date DATE,
    urgency urgency_type DEFAULT 'normal',
    total_amount DECIMAL(18,2) DEFAULT 0.00,
    status req_status DEFAULT 'draft',
    remark VARCHAR(500),
    created_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pr_dept FOREIGN KEY (department_id) REFERENCES departments(id),
    CONSTRAINT fk_pr_requester FOREIGN KEY (requester_id) REFERENCES employees(id)
);

CREATE INDEX idx_pr_dept ON purchase_requisitions(department_id);
CREATE INDEX idx_pr_requester ON purchase_requisitions(requester_id);
CREATE INDEX idx_pr_status ON purchase_requisitions(status);
CREATE INDEX idx_pr_date ON purchase_requisitions(requisition_date);

CREATE TRIGGER trg_pr_updated_at
    BEFORE UPDATE ON purchase_requisitions
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

CREATE TABLE purchase_requisition_items (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    requisition_id BIGINT NOT NULL CHECK (requisition_id >= 0),
    product_id BIGINT NOT NULL CHECK (product_id >= 0),
    quantity INTEGER NOT NULL,
    estimated_price DECIMAL(12,2) DEFAULT 0.00,
    amount DECIMAL(18,2) GENERATED ALWAYS AS (quantity * estimated_price) STORED,
    remark VARCHAR(200),
    CONSTRAINT fk_pri_req FOREIGN KEY (requisition_id) REFERENCES purchase_requisitions(id) ON DELETE CASCADE,
    CONSTRAINT fk_pri_product FOREIGN KEY (product_id) REFERENCES products(id)
);

CREATE INDEX idx_pri_product ON purchase_requisition_items(product_id);

-- 采购单
CREATE TABLE purchase_orders (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    order_no VARCHAR(30) NOT NULL UNIQUE,
    supplier_id BIGINT NOT NULL CHECK (supplier_id >= 0),
    requisition_id BIGINT CHECK (requisition_id IS NULL OR requisition_id >= 0),
    department_id BIGINT NOT NULL CHECK (department_id >= 0),
    purchaser_id BIGINT NOT NULL CHECK (purchaser_id >= 0),
    order_date DATE NOT NULL,
    expected_delivery_date DATE,
    actual_delivery_date DATE,
    total_amount DECIMAL(18,2) DEFAULT 0.00,
    paid_amount DECIMAL(18,2) DEFAULT 0.00,
    payment_terms VARCHAR(100),
    status po_status DEFAULT 'draft',
    remark VARCHAR(500),
    created_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_po_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id),
    CONSTRAINT fk_po_req FOREIGN KEY (requisition_id) REFERENCES purchase_requisitions(id) ON DELETE SET NULL,
    CONSTRAINT fk_po_purchaser FOREIGN KEY (purchaser_id) REFERENCES employees(id)
);

CREATE INDEX idx_po_supplier ON purchase_orders(supplier_id);
CREATE INDEX idx_po_requisition ON purchase_orders(requisition_id);
CREATE INDEX idx_po_purchaser ON purchase_orders(purchaser_id);
CREATE INDEX idx_po_status ON purchase_orders(status);
CREATE INDEX idx_po_date ON purchase_orders(order_date);

COMMENT ON COLUMN purchase_orders.payment_terms IS '付款条件';

CREATE TRIGGER trg_po_updated_at
    BEFORE UPDATE ON purchase_orders
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

CREATE TABLE purchase_order_items (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    order_id BIGINT NOT NULL CHECK (order_id >= 0),
    product_id BIGINT NOT NULL CHECK (product_id >= 0),
    quantity INTEGER NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    amount DECIMAL(18,2) GENERATED ALWAYS AS (quantity * unit_price) STORED,
    received_qty INTEGER DEFAULT 0,
    returned_qty INTEGER DEFAULT 0,
    remark VARCHAR(200),
    CONSTRAINT fk_poi_order FOREIGN KEY (order_id) REFERENCES purchase_orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_poi_product FOREIGN KEY (product_id) REFERENCES products(id)
);

CREATE INDEX idx_poi_product ON purchase_order_items(product_id);

-- 进货单（入库单）
CREATE TABLE purchase_receipts (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    receipt_no VARCHAR(30) NOT NULL UNIQUE,
    order_id BIGINT NOT NULL CHECK (order_id >= 0),
    warehouse_id BIGINT NOT NULL CHECK (warehouse_id >= 0),
    receiver_id BIGINT NOT NULL CHECK (receiver_id >= 0),
    receipt_date DATE NOT NULL,
    batch_no VARCHAR(50),
    total_qty INTEGER DEFAULT 0,
    total_amount DECIMAL(18,2) DEFAULT 0.00,
    status receipt_status DEFAULT 'pending',
    inspection_result VARCHAR(500),
    remark VARCHAR(500),
    created_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_prec_order FOREIGN KEY (order_id) REFERENCES purchase_orders(id),
    CONSTRAINT fk_prec_wh FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT fk_prec_receiver FOREIGN KEY (receiver_id) REFERENCES employees(id)
);

CREATE INDEX idx_prec_order ON purchase_receipts(order_id);
CREATE INDEX idx_prec_warehouse ON purchase_receipts(warehouse_id);
CREATE INDEX idx_prec_date ON purchase_receipts(receipt_date);

COMMENT ON COLUMN purchase_receipts.batch_no IS '统一批号';
COMMENT ON COLUMN purchase_receipts.inspection_result IS '质检结果';

CREATE TABLE purchase_receipt_items (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    receipt_id BIGINT NOT NULL CHECK (receipt_id >= 0),
    order_item_id BIGINT NOT NULL CHECK (order_item_id >= 0),
    product_id BIGINT NOT NULL CHECK (product_id >= 0),
    batch_id BIGINT CHECK (batch_id IS NULL OR batch_id >= 0),
    received_qty INTEGER NOT NULL,
    accepted_qty INTEGER DEFAULT 0,
    rejected_qty INTEGER DEFAULT 0,
    unit_price DECIMAL(12,2) NOT NULL,
    production_date DATE,
    expiry_date DATE,
    remark VARCHAR(200),
    CONSTRAINT fk_preci_receipt FOREIGN KEY (receipt_id) REFERENCES purchase_receipts(id) ON DELETE CASCADE,
    CONSTRAINT fk_preci_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_preci_batch FOREIGN KEY (batch_id) REFERENCES product_batches(id) ON DELETE SET NULL
);

CREATE INDEX idx_preci_order_item ON purchase_receipt_items(order_item_id);
CREATE INDEX idx_preci_product ON purchase_receipt_items(product_id);
CREATE INDEX idx_preci_batch ON purchase_receipt_items(batch_id);

COMMENT ON COLUMN purchase_receipt_items.accepted_qty IS '合格数量';
COMMENT ON COLUMN purchase_receipt_items.rejected_qty IS '不合格数量';

-- ============================================================
-- 6. 销售模块
-- ============================================================

CREATE TABLE customers (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    type customer_type DEFAULT 'individual',
    contact_person VARCHAR(50),
    phone VARCHAR(20),
    email VARCHAR(100),
    address VARCHAR(300),
    credit_limit DECIMAL(18,2) DEFAULT 0.00,
    credit_days INTEGER DEFAULT 30,
    balance DECIMAL(18,2) DEFAULT 0.00,
    membership_level membership_level DEFAULT 'normal',
    status customer_status DEFAULT 'active',
    created_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_customer_type ON customers(type);
CREATE INDEX idx_customer_status ON customers(status);
CREATE INDEX idx_customer_membership ON customers(membership_level);

COMMENT ON COLUMN customers.credit_limit IS '信用额度';
COMMENT ON COLUMN customers.credit_days IS '账期天数';
COMMENT ON COLUMN customers.balance IS '账户余额/欠款';

CREATE TRIGGER trg_customers_updated_at
    BEFORE UPDATE ON customers
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

-- 销售单
CREATE TABLE sales_orders (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    order_no VARCHAR(30) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL CHECK (customer_id >= 0),
    salesperson_id BIGINT NOT NULL CHECK (salesperson_id >= 0),
    warehouse_id BIGINT NOT NULL CHECK (warehouse_id >= 0),
    order_date DATE NOT NULL,
    delivery_date DATE,
    total_amount DECIMAL(18,2) DEFAULT 0.00,
    discount_amount DECIMAL(18,2) DEFAULT 0.00,
    paid_amount DECIMAL(18,2) DEFAULT 0.00,
    tax_amount DECIMAL(18,2) DEFAULT 0.00,
    payment_method payment_method DEFAULT 'transfer',
    status so_status DEFAULT 'draft',
    invoice_no VARCHAR(50),
    remark VARCHAR(500),
    created_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_so_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_so_salesperson FOREIGN KEY (salesperson_id) REFERENCES employees(id),
    CONSTRAINT fk_so_wh FOREIGN KEY (warehouse_id) REFERENCES warehouses(id)
);

CREATE INDEX idx_so_customer ON sales_orders(customer_id);
CREATE INDEX idx_so_salesperson ON sales_orders(salesperson_id);
CREATE INDEX idx_so_date ON sales_orders(order_date);
CREATE INDEX idx_so_status ON sales_orders(status);
CREATE INDEX idx_so_payment ON sales_orders(payment_method);

CREATE TRIGGER trg_so_updated_at
    BEFORE UPDATE ON sales_orders
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

CREATE TABLE sales_order_items (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    order_id BIGINT NOT NULL CHECK (order_id >= 0),
    product_id BIGINT NOT NULL CHECK (product_id >= 0),
    batch_id BIGINT CHECK (batch_id IS NULL OR batch_id >= 0),
    quantity INTEGER NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    discount DECIMAL(12,2) DEFAULT 0.00,
    amount DECIMAL(18,2) NOT NULL,
    returned_qty INTEGER DEFAULT 0,
    CONSTRAINT fk_soi_order FOREIGN KEY (order_id) REFERENCES sales_orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_soi_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_soi_batch FOREIGN KEY (batch_id) REFERENCES product_batches(id) ON DELETE SET NULL
);

CREATE INDEX idx_soi_order ON sales_order_items(order_id);
CREATE INDEX idx_soi_product ON sales_order_items(product_id);
CREATE INDEX idx_soi_batch ON sales_order_items(batch_id);

COMMENT ON COLUMN sales_order_items.amount IS '折后金额';

-- 退库单
CREATE TABLE sales_returns (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    return_no VARCHAR(30) NOT NULL UNIQUE,
    order_id BIGINT NOT NULL CHECK (order_id >= 0),
    customer_id BIGINT NOT NULL CHECK (customer_id >= 0),
    warehouse_id BIGINT NOT NULL CHECK (warehouse_id >= 0),
    handler_id BIGINT NOT NULL CHECK (handler_id >= 0),
    return_date DATE NOT NULL,
    return_reason VARCHAR(500) NOT NULL,
    return_type sales_return_type DEFAULT 'other',
    total_amount DECIMAL(18,2) DEFAULT 0.00,
    refund_amount DECIMAL(18,2) DEFAULT 0.00,
    restock_fee DECIMAL(12,2) DEFAULT 0.00,
    status sales_return_status DEFAULT 'pending',
    approved_by BIGINT CHECK (approved_by IS NULL OR approved_by >= 0),
    approved_at TIMESTAMP(0),
    refund_voucher_id BIGINT CHECK (refund_voucher_id IS NULL OR refund_voucher_id >= 0),
    return_shipping_fee DECIMAL(12,2) DEFAULT 0.00,
    remark VARCHAR(500),
    created_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sr_order FOREIGN KEY (order_id) REFERENCES sales_orders(id),
    CONSTRAINT fk_sr_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_sr_wh FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT fk_sr_handler FOREIGN KEY (handler_id) REFERENCES employees(id),
    CONSTRAINT fk_sr_approved FOREIGN KEY (approved_by) REFERENCES employees(id)
);

CREATE INDEX idx_sr_order ON sales_returns(order_id);
CREATE INDEX idx_sr_customer ON sales_returns(customer_id);
CREATE INDEX idx_sr_date ON sales_returns(return_date);
CREATE INDEX idx_sr_status ON sales_returns(status);
CREATE INDEX idx_sr_approved ON sales_returns(approved_by);
CREATE INDEX idx_sr_type ON sales_returns(return_type);

COMMENT ON COLUMN sales_returns.handler_id IS '处理人';
COMMENT ON COLUMN sales_returns.refund_amount IS '实际退款金额';
COMMENT ON COLUMN sales_returns.restock_fee IS '退货折旧费/手续费';
COMMENT ON COLUMN sales_returns.approved_by IS '审批人';
COMMENT ON COLUMN sales_returns.approved_at IS '审批时间';
COMMENT ON COLUMN sales_returns.return_shipping_fee IS '退货运费';

CREATE TRIGGER trg_sr_updated_at
    BEFORE UPDATE ON sales_returns
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

CREATE TABLE sales_return_items (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    return_id BIGINT NOT NULL CHECK (return_id >= 0),
    order_item_id BIGINT NOT NULL CHECK (order_item_id >= 0),
    product_id BIGINT NOT NULL CHECK (product_id >= 0),
    batch_id BIGINT CHECK (batch_id IS NULL OR batch_id >= 0),
    return_qty INTEGER NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    status return_item_status DEFAULT 'pending',
    CONSTRAINT fk_sri_return FOREIGN KEY (return_id) REFERENCES sales_returns(id) ON DELETE CASCADE,
    CONSTRAINT fk_sri_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_sri_batch FOREIGN KEY (batch_id) REFERENCES product_batches(id) ON DELETE SET NULL
);

CREATE INDEX idx_sri_order_item ON sales_return_items(order_item_id);

-- ============================================================
-- 6B. 退货给供应商模块 (采购退货)
-- ============================================================

CREATE TABLE purchase_returns (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    return_no VARCHAR(30) NOT NULL UNIQUE,
    purchase_order_id BIGINT NOT NULL CHECK (purchase_order_id >= 0),
    purchase_receipt_id BIGINT CHECK (purchase_receipt_id IS NULL OR purchase_receipt_id >= 0),
    supplier_id BIGINT NOT NULL CHECK (supplier_id >= 0),
    warehouse_id BIGINT NOT NULL CHECK (warehouse_id >= 0),
    handler_id BIGINT NOT NULL CHECK (handler_id >= 0),
    return_date DATE NOT NULL,
    return_reason VARCHAR(500) NOT NULL,
    return_type purchase_return_type DEFAULT 'quality',
    total_amount DECIMAL(18,2) DEFAULT 0.00,
    refund_received DECIMAL(18,2) DEFAULT 0.00,
    shipping_fee DECIMAL(12,2) DEFAULT 0.00,
    status purchase_return_status DEFAULT 'draft',
    approved_by BIGINT CHECK (approved_by IS NULL OR approved_by >= 0),
    approved_at TIMESTAMP(0),
    refund_voucher_id BIGINT CHECK (refund_voucher_id IS NULL OR refund_voucher_id >= 0),
    remark VARCHAR(500),
    created_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_prt_po FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id),
    CONSTRAINT fk_prt_receipt FOREIGN KEY (purchase_receipt_id) REFERENCES purchase_receipts(id) ON DELETE SET NULL,
    CONSTRAINT fk_prt_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id),
    CONSTRAINT fk_prt_wh FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT fk_prt_handler FOREIGN KEY (handler_id) REFERENCES employees(id),
    CONSTRAINT fk_prt_approved FOREIGN KEY (approved_by) REFERENCES employees(id)
);

CREATE INDEX idx_prt_po ON purchase_returns(purchase_order_id);
CREATE INDEX idx_prt_supplier ON purchase_returns(supplier_id);
CREATE INDEX idx_prt_status ON purchase_returns(status);
CREATE INDEX idx_prt_date ON purchase_returns(return_date);
CREATE INDEX idx_prt_type ON purchase_returns(return_type);

COMMENT ON COLUMN purchase_returns.total_amount IS '退货总金额';
COMMENT ON COLUMN purchase_returns.refund_received IS '已收到退款';
COMMENT ON COLUMN purchase_returns.shipping_fee IS '退货运费';
COMMENT ON COLUMN purchase_returns.refund_voucher_id IS '退款凭证';

CREATE TRIGGER trg_prt_updated_at
    BEFORE UPDATE ON purchase_returns
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

CREATE TABLE purchase_return_items (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    return_id BIGINT NOT NULL CHECK (return_id >= 0),
    product_id BIGINT NOT NULL CHECK (product_id >= 0),
    batch_id BIGINT CHECK (batch_id IS NULL OR batch_id >= 0),
    return_qty INTEGER NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    amount DECIMAL(18,2) GENERATED ALWAYS AS (return_qty * unit_price) STORED,
    reason VARCHAR(200),
    CONSTRAINT fk_pri_return FOREIGN KEY (return_id) REFERENCES purchase_returns(id) ON DELETE CASCADE,
    CONSTRAINT fk_pri_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_pri_batch FOREIGN KEY (batch_id) REFERENCES product_batches(id) ON DELETE SET NULL
);

CREATE INDEX idx_pri_product ON purchase_return_items(product_id);
CREATE INDEX idx_pri_batch ON purchase_return_items(batch_id);

COMMENT ON COLUMN purchase_return_items.unit_price IS '退货单价(采购价)';

-- ============================================================
-- 6C. 报损/报废模块
-- ============================================================

CREATE TABLE damage_reports (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    report_no VARCHAR(30) NOT NULL UNIQUE,
    warehouse_id BIGINT NOT NULL CHECK (warehouse_id >= 0),
    report_type damage_report_type NOT NULL,
    report_date DATE NOT NULL,
    reported_by BIGINT NOT NULL CHECK (reported_by >= 0),
    total_quantity INTEGER DEFAULT 0,
    total_loss_amount DECIMAL(18,2) DEFAULT 0.00,
    status damage_report_status DEFAULT 'draft',
    approved_by BIGINT CHECK (approved_by IS NULL OR approved_by >= 0),
    approved_at TIMESTAMP(0),
    executed_by BIGINT CHECK (executed_by IS NULL OR executed_by >= 0),
    executed_at TIMESTAMP(0),
    voucher_id BIGINT CHECK (voucher_id IS NULL OR voucher_id >= 0),
    description TEXT,
    remark VARCHAR(500),
    created_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dr_wh FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT fk_dr_reported FOREIGN KEY (reported_by) REFERENCES employees(id),
    CONSTRAINT fk_dr_approved FOREIGN KEY (approved_by) REFERENCES employees(id),
    CONSTRAINT fk_dr_executed FOREIGN KEY (executed_by) REFERENCES employees(id)
);

CREATE INDEX idx_dr_warehouse ON damage_reports(warehouse_id);
CREATE INDEX idx_dr_type ON damage_reports(report_type);
CREATE INDEX idx_dr_date ON damage_reports(report_date);
CREATE INDEX idx_dr_status ON damage_reports(status);

COMMENT ON COLUMN damage_reports.total_loss_amount IS '报损总金额(成本价)';
COMMENT ON COLUMN damage_reports.voucher_id IS '财务凭证';

CREATE TRIGGER trg_dr_updated_at
    BEFORE UPDATE ON damage_reports
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

CREATE TABLE damage_report_items (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    report_id BIGINT NOT NULL CHECK (report_id >= 0),
    product_id BIGINT NOT NULL CHECK (product_id >= 0),
    batch_id BIGINT CHECK (batch_id IS NULL OR batch_id >= 0),
    quantity INTEGER NOT NULL,
    unit_cost DECIMAL(12,2) NOT NULL,
    loss_amount DECIMAL(18,2) GENERATED ALWAYS AS (quantity * unit_cost) STORED,
    reason VARCHAR(200),
    CONSTRAINT fk_dri_report FOREIGN KEY (report_id) REFERENCES damage_reports(id) ON DELETE CASCADE,
    CONSTRAINT fk_dri_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_dri_batch FOREIGN KEY (batch_id) REFERENCES product_batches(id) ON DELETE SET NULL
);

CREATE INDEX idx_dri_product ON damage_report_items(product_id);
CREATE INDEX idx_dri_batch ON damage_report_items(batch_id);

COMMENT ON COLUMN damage_report_items.unit_cost IS '单位成本';

-- ============================================================
-- 7. 财务模块
-- ============================================================

-- 财务账户
CREATE TABLE accounts (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    parent_id BIGINT CHECK (parent_id IS NULL OR parent_id >= 0),
    name VARCHAR(100) NOT NULL,
    account_type account_type NOT NULL,
    balance_direction balance_direction NOT NULL,
    is_cash BOOLEAN DEFAULT FALSE,
    is_bank BOOLEAN DEFAULT FALSE,
    bank_name VARCHAR(100),
    bank_account VARCHAR(50),
    current_balance DECIMAL(18,2) DEFAULT 0.00,
    status account_status DEFAULT 'active',
    created_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_acct_parent FOREIGN KEY (parent_id) REFERENCES accounts(id) ON DELETE SET NULL
);

CREATE INDEX idx_acct_parent ON accounts(parent_id);
CREATE INDEX idx_acct_type ON accounts(account_type);

COMMENT ON COLUMN accounts.code IS '科目编码';
COMMENT ON COLUMN accounts.balance_direction IS '余额方向';
COMMENT ON COLUMN accounts.is_cash IS '是否现金科目';
COMMENT ON COLUMN accounts.is_bank IS '是否银行科目';

-- 财务凭证
CREATE TABLE vouchers (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    voucher_no VARCHAR(30) NOT NULL UNIQUE,
    voucher_date DATE NOT NULL,
    voucher_type voucher_type NOT NULL,
    reference_type VARCHAR(50),
    reference_id BIGINT,
    total_debit DECIMAL(18,2) DEFAULT 0.00,
    total_credit DECIMAL(18,2) DEFAULT 0.00,
    prepared_by BIGINT NOT NULL CHECK (prepared_by >= 0),
    reviewed_by BIGINT CHECK (reviewed_by IS NULL OR reviewed_by >= 0),
    posted_by BIGINT CHECK (posted_by IS NULL OR posted_by >= 0),
    status voucher_status DEFAULT 'draft',
    summary VARCHAR(500),
    created_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_v_prepared FOREIGN KEY (prepared_by) REFERENCES employees(id),
    CONSTRAINT fk_v_reviewed FOREIGN KEY (reviewed_by) REFERENCES employees(id),
    CONSTRAINT fk_v_posted FOREIGN KEY (posted_by) REFERENCES employees(id)
);

CREATE INDEX idx_v_date ON vouchers(voucher_date);
CREATE INDEX idx_v_type ON vouchers(voucher_type);
CREATE INDEX idx_v_reference ON vouchers(reference_type, reference_id);
CREATE INDEX idx_v_status ON vouchers(status);

CREATE TRIGGER trg_v_updated_at
    BEFORE UPDATE ON vouchers
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

CREATE TABLE voucher_items (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    voucher_id BIGINT NOT NULL CHECK (voucher_id >= 0),
    account_id BIGINT NOT NULL CHECK (account_id >= 0),
    line_no INTEGER NOT NULL,
    direction entry_direction NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    summary VARCHAR(500),
    CONSTRAINT fk_vi_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers(id) ON DELETE CASCADE,
    CONSTRAINT fk_vi_account FOREIGN KEY (account_id) REFERENCES accounts(id)
);

CREATE INDEX idx_vi_voucher ON voucher_items(voucher_id);
CREATE INDEX idx_vi_account ON voucher_items(account_id);

-- 出纳日记账
CREATE TABLE cashier_journals (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    journal_no VARCHAR(30) NOT NULL UNIQUE,
    journal_date DATE NOT NULL,
    account_id BIGINT NOT NULL CHECK (account_id >= 0),
    cashier_id BIGINT NOT NULL CHECK (cashier_id >= 0),
    journal_type journal_type NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    counterparty VARCHAR(200),
    reference_type VARCHAR(50),
    reference_id BIGINT,
    voucher_id BIGINT CHECK (voucher_id IS NULL OR voucher_id >= 0),
    bank_account VARCHAR(50),
    check_no VARCHAR(50),
    status journal_status DEFAULT 'pending',
    remark VARCHAR(500),
    created_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cj_account FOREIGN KEY (account_id) REFERENCES accounts(id),
    CONSTRAINT fk_cj_cashier FOREIGN KEY (cashier_id) REFERENCES employees(id),
    CONSTRAINT fk_cj_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers(id) ON DELETE SET NULL
);

CREATE INDEX idx_cj_date ON cashier_journals(journal_date);
CREATE INDEX idx_cj_account ON cashier_journals(account_id);
CREATE INDEX idx_cj_cashier ON cashier_journals(cashier_id);
CREATE INDEX idx_cj_status ON cashier_journals(status);
CREATE INDEX idx_cj_voucher ON cashier_journals(voucher_id);

COMMENT ON COLUMN cashier_journals.counterparty IS '对方单位/个人';

-- 工资发放表
CREATE TABLE salary_payments (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    payment_no VARCHAR(30) NOT NULL UNIQUE,
    employee_id BIGINT NOT NULL CHECK (employee_id >= 0),
    payment_date DATE NOT NULL,
    salary_month VARCHAR(7) NOT NULL,
    base_salary DECIMAL(12,2) NOT NULL,
    overtime_pay DECIMAL(12,2) DEFAULT 0.00,
    bonus DECIMAL(12,2) DEFAULT 0.00,
    deduction DECIMAL(12,2) DEFAULT 0.00,
    social_security_personal DECIMAL(12,2) DEFAULT 0.00,
    housing_fund_personal DECIMAL(12,2) DEFAULT 0.00,
    income_tax DECIMAL(12,2) DEFAULT 0.00,
    net_pay DECIMAL(12,2) NOT NULL,
    social_security_company DECIMAL(12,2) DEFAULT 0.00,
    housing_fund_company DECIMAL(12,2) DEFAULT 0.00,
    payment_method salary_payment_method DEFAULT 'bank_transfer',
    status salary_payment_status DEFAULT 'pending',
    paid_at TIMESTAMP(0),
    voucher_id BIGINT CHECK (voucher_id IS NULL OR voucher_id >= 0),
    remark VARCHAR(200),
    created_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (employee_id, salary_month),
    CONSTRAINT fk_sp_emp FOREIGN KEY (employee_id) REFERENCES employees(id),
    CONSTRAINT fk_sp_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers(id) ON DELETE SET NULL
);

CREATE INDEX idx_sp_date ON salary_payments(payment_date);
CREATE INDEX idx_sp_month ON salary_payments(salary_month);
CREATE INDEX idx_sp_status ON salary_payments(status);

COMMENT ON COLUMN salary_payments.salary_month IS 'YYYY-MM';
COMMENT ON COLUMN salary_payments.deduction IS '扣款';
COMMENT ON COLUMN salary_payments.social_security_personal IS '个人社保';
COMMENT ON COLUMN salary_payments.housing_fund_personal IS '个人公积金';
COMMENT ON COLUMN salary_payments.income_tax IS '个税';
COMMENT ON COLUMN salary_payments.net_pay IS '实发工资';
COMMENT ON COLUMN salary_payments.social_security_company IS '公司社保';
COMMENT ON COLUMN salary_payments.housing_fund_company IS '公司公积金';

-- 对账表
CREATE TABLE reconciliations (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    recon_no VARCHAR(30) NOT NULL UNIQUE,
    account_id BIGINT NOT NULL CHECK (account_id >= 0),
    recon_date DATE NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    book_balance DECIMAL(18,2) NOT NULL,
    bank_balance DECIMAL(18,2) DEFAULT 0.00,
    difference DECIMAL(18,2) DEFAULT 0.00,
    unreconciled_income DECIMAL(18,2) DEFAULT 0.00,
    unreconciled_expense DECIMAL(18,2) DEFAULT 0.00,
    adjusted_balance DECIMAL(18,2) DEFAULT 0.00,
    prepared_by BIGINT NOT NULL CHECK (prepared_by >= 0),
    reviewed_by BIGINT CHECK (reviewed_by IS NULL OR reviewed_by >= 0),
    status recon_status DEFAULT 'draft',
    remark VARCHAR(500),
    created_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_recon_account FOREIGN KEY (account_id) REFERENCES accounts(id),
    CONSTRAINT fk_recon_prepared FOREIGN KEY (prepared_by) REFERENCES employees(id),
    CONSTRAINT fk_recon_reviewed FOREIGN KEY (reviewed_by) REFERENCES employees(id)
);

CREATE INDEX idx_recon_account ON reconciliations(account_id);
CREATE INDEX idx_recon_period ON reconciliations(period_start, period_end);
CREATE INDEX idx_recon_status ON reconciliations(status);

COMMENT ON COLUMN reconciliations.book_balance IS '账面余额';
COMMENT ON COLUMN reconciliations.bank_balance IS '银行余额';
COMMENT ON COLUMN reconciliations.difference IS '差异';
COMMENT ON COLUMN reconciliations.unreconciled_income IS '未达账-收入';
COMMENT ON COLUMN reconciliations.unreconciled_expense IS '未达账-支出';
COMMENT ON COLUMN reconciliations.adjusted_balance IS '调整后余额';

CREATE TRIGGER trg_recon_updated_at
    BEFORE UPDATE ON reconciliations
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

-- 对账明细
CREATE TABLE reconciliation_items (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    reconciliation_id BIGINT NOT NULL CHECK (reconciliation_id >= 0),
    journal_id BIGINT CHECK (journal_id IS NULL OR journal_id >= 0),
    transaction_date DATE NOT NULL,
    description VARCHAR(500) NOT NULL,
    debit_amount DECIMAL(18,2) DEFAULT 0.00,
    credit_amount DECIMAL(18,2) DEFAULT 0.00,
    is_matched BOOLEAN DEFAULT FALSE,
    matched_item_id BIGINT CHECK (matched_item_id IS NULL OR matched_item_id >= 0),
    difference_reason VARCHAR(500),
    CONSTRAINT fk_ri_recon FOREIGN KEY (reconciliation_id) REFERENCES reconciliations(id) ON DELETE CASCADE
);

CREATE INDEX idx_ri_recon ON reconciliation_items(reconciliation_id);
CREATE INDEX idx_ri_journal ON reconciliation_items(journal_id);

-- 结算单
CREATE TABLE settlements (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    settlement_no VARCHAR(30) NOT NULL UNIQUE,
    settlement_type settlement_type NOT NULL,
    party_id BIGINT NOT NULL CHECK (party_id >= 0),
    settlement_date DATE NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_amount DECIMAL(18,2) NOT NULL,
    settled_amount DECIMAL(18,2) DEFAULT 0.00,
    unpaid_amount DECIMAL(18,2) GENERATED ALWAYS AS (total_amount - settled_amount) STORED,
    payment_due_date DATE,
    payment_method settlement_payment_method DEFAULT 'bank_transfer',
    status settlement_status DEFAULT 'pending',
    voucher_id BIGINT CHECK (voucher_id IS NULL OR voucher_id >= 0),
    prepared_by BIGINT NOT NULL CHECK (prepared_by >= 0),
    approved_by BIGINT CHECK (approved_by IS NULL OR approved_by >= 0),
    remark VARCHAR(500),
    created_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_settle_prepared FOREIGN KEY (prepared_by) REFERENCES employees(id),
    CONSTRAINT fk_settle_approved FOREIGN KEY (approved_by) REFERENCES employees(id)
);

CREATE INDEX idx_settle_party ON settlements(settlement_type, party_id);
CREATE INDEX idx_settle_date ON settlements(settlement_date);
CREATE INDEX idx_settle_status ON settlements(status);
CREATE INDEX idx_settle_due_date ON settlements(payment_due_date);

COMMENT ON COLUMN settlements.party_id IS '对方ID（供应商/客户/员工等）';

CREATE TRIGGER trg_settle_updated_at
    BEFORE UPDATE ON settlements
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

-- 结算明细
CREATE TABLE settlement_items (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    settlement_id BIGINT NOT NULL CHECK (settlement_id >= 0),
    reference_type VARCHAR(50) NOT NULL,
    reference_id BIGINT NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    settled_amount DECIMAL(18,2) DEFAULT 0.00,
    remark VARCHAR(200),
    CONSTRAINT fk_si_settlement FOREIGN KEY (settlement_id) REFERENCES settlements(id) ON DELETE CASCADE
);

CREATE INDEX idx_si_settlement ON settlement_items(settlement_id);
CREATE INDEX idx_si_reference ON settlement_items(reference_type, reference_id);

COMMENT ON COLUMN settlement_items.reference_type IS '关联单据类型';

-- 延迟添加 sales_returns 和 purchase_returns 对 vouchers 的外键
ALTER TABLE sales_returns ADD CONSTRAINT fk_sr_refund_voucher
    FOREIGN KEY (refund_voucher_id) REFERENCES vouchers(id) ON DELETE SET NULL;

ALTER TABLE purchase_returns ADD CONSTRAINT fk_prt_voucher
    FOREIGN KEY (refund_voucher_id) REFERENCES vouchers(id) ON DELETE SET NULL;

ALTER TABLE damage_reports ADD CONSTRAINT fk_dr_voucher
    FOREIGN KEY (voucher_id) REFERENCES vouchers(id) ON DELETE SET NULL;

ALTER TABLE settlements ADD CONSTRAINT fk_settle_voucher
    FOREIGN KEY (voucher_id) REFERENCES vouchers(id) ON DELETE SET NULL;