-- ============================================================
-- ERP系统第三批补充表: 合同管理、AR/AP账龄、税务管理、
--   质检、审批流引擎、现金流预测、项目成本、多币种汇率、
--   绩效考核、序列号追踪、寄售库存、价格变更历史
-- 关系说明:
--   contracts -> sales_orders/purchase_orders (1:1), 管理合同条款和里程碑
--   ar_aging / ap_aging: 账龄分析用，按月计算应收账款/应付账款
--   tax_invoices: 增值税发票管理，进项税/销项税
--   inspection_reports: 质检报告，关联采购入库和销售退货
--   approval_workflows: 通用审批流引擎，支持多级审批
--   cash_flow_forecasts: 现金流预测，基于应收应付和计划收支
--   projects + project_costs: 项目成本核算
--   exchange_rates: 多币种汇率管理
--   performance_reviews: 绩效考核
--   serial_numbers: 序列号追踪
--   consignment_inventory: 寄售库存
--   price_change_logs: 价格变更历史
-- ============================================================

USE erp_system;

-- ============================================================
-- 1. 合同管理模块
-- 合同类型: 销售合同、采购合同、服务合同
-- 合同状态流转: draft -> pending_approval -> active -> in_progress -> completed -> expired -> terminated
-- ============================================================

CREATE TABLE contracts (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    contract_no VARCHAR(30) NOT NULL UNIQUE,
    contract_type ENUM('sales','purchase','service','lease','other') NOT NULL,
    party_type ENUM('customer','supplier','employee','other') NOT NULL COMMENT '对方类型',
    party_id BIGINT UNSIGNED NOT NULL COMMENT '对方ID',
    subject VARCHAR(300) NOT NULL COMMENT '合同标题',
    total_amount DECIMAL(18,2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'CNY',
    signed_date DATE NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    payment_terms TEXT NULL COMMENT '付款条款JSON',
    delivery_terms TEXT NULL COMMENT '交付条款JSON',
    status ENUM('draft','pending_approval','active','in_progress','completed','expired','terminated','cancelled') DEFAULT 'draft',
    prepared_by BIGINT UNSIGNED NOT NULL,
    approved_by BIGINT UNSIGNED NULL,
    signed_by VARCHAR(100) NULL COMMENT '签约人',
    attachment_path VARCHAR(500) NULL,
    remark TEXT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_party (party_type, party_id),
    INDEX idx_status (status),
    INDEX idx_date_range (start_date, end_date),
    INDEX idx_type (contract_type),
    CONSTRAINT fk_ct_prepared FOREIGN KEY (prepared_by) REFERENCES employees(id),
    CONSTRAINT fk_ct_approved FOREIGN KEY (approved_by) REFERENCES employees(id)
) ENGINE=InnoDB;

-- 合同里程碑/付款计划
CREATE TABLE contract_milestones (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    contract_id BIGINT UNSIGNED NOT NULL,
    milestone_name VARCHAR(200) NOT NULL,
    milestone_type ENUM('delivery','payment','acceptance','renewal','other') NOT NULL,
    planned_date DATE NOT NULL,
    actual_date DATE NULL,
    amount DECIMAL(18,2) DEFAULT 0.00 COMMENT '里程碑金额',
    completion_pct DECIMAL(5,2) DEFAULT 0.00 COMMENT '完成百分比',
    status ENUM('pending','in_progress','completed','delayed','cancelled') DEFAULT 'pending',
    responsible_person BIGINT UNSIGNED NULL,
    remark VARCHAR(500) NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_contract (contract_id),
    INDEX idx_planned_date (planned_date),
    INDEX idx_status (status),
    CONSTRAINT fk_cm_contract FOREIGN KEY (contract_id) REFERENCES contracts(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================================
-- 2. 应收账款账龄 (AR Aging)
-- 计算原理: 按账期分桶: 0-30天, 31-60天, 61-90天, 91-180天, 181-365天, >365天
-- 坏账准备: >365天按50%计提, >180天按20%, >90天按5%
-- ============================================================

CREATE TABLE ar_aging_snapshots (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    snapshot_date DATE NOT NULL,
    customer_id BIGINT UNSIGNED NOT NULL,
    order_id BIGINT UNSIGNED NOT NULL,
    invoice_amount DECIMAL(18,2) NOT NULL COMMENT '应收金额',
    paid_amount DECIMAL(18,2) DEFAULT 0.00,
    outstanding_amount DECIMAL(18,2) GENERATED ALWAYS AS (invoice_amount - paid_amount) STORED,
    due_date DATE NOT NULL,
    aging_days INT GENERATED ALWAYS AS (DATEDIFF(CURDATE(), due_date)) STORED,
    aging_bucket VARCHAR(20) GENERATED ALWAYS AS (
        CASE
            WHEN DATEDIFF(CURDATE(), due_date) <= 0 THEN '未到期'
            WHEN DATEDIFF(CURDATE(), due_date) <= 30 THEN '1-30天'
            WHEN DATEDIFF(CURDATE(), due_date) <= 60 THEN '31-60天'
            WHEN DATEDIFF(CURDATE(), due_date) <= 90 THEN '61-90天'
            WHEN DATEDIFF(CURDATE(), due_date) <= 180 THEN '91-180天'
            WHEN DATEDIFF(CURDATE(), due_date) <= 365 THEN '181-365天'
            ELSE '365天以上'
        END
    ) STORED,
    bad_debt_provision DECIMAL(18,2) GENERATED ALWAYS AS (
        ROUND((invoice_amount - paid_amount) *
        CASE
            WHEN DATEDIFF(CURDATE(), due_date) <= 90 THEN 0.00
            WHEN DATEDIFF(CURDATE(), due_date) <= 180 THEN 0.05
            WHEN DATEDIFF(CURDATE(), due_date) <= 365 THEN 0.20
            ELSE 0.50
        END, 2)
    ) STORED COMMENT '坏账准备',
    last_collection_date DATE NULL COMMENT '最近催收日期',
    collection_notes VARCHAR(500) NULL,
    INDEX idx_customer (customer_id),
    INDEX idx_aging_bucket (aging_bucket),
    INDEX idx_snapshot_date (snapshot_date),
    INDEX idx_due_date (due_date),
    CONSTRAINT fk_ar_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_ar_order FOREIGN KEY (order_id) REFERENCES sales_orders(id)
) ENGINE=InnoDB;

-- 应付账款账龄 (AP Aging)
CREATE TABLE ap_aging_snapshots (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    snapshot_date DATE NOT NULL,
    supplier_id BIGINT UNSIGNED NOT NULL,
    order_id BIGINT UNSIGNED NOT NULL,
    invoice_amount DECIMAL(18,2) NOT NULL,
    paid_amount DECIMAL(18,2) DEFAULT 0.00,
    outstanding_amount DECIMAL(18,2) GENERATED ALWAYS AS (invoice_amount - paid_amount) STORED,
    due_date DATE NOT NULL,
    aging_bucket VARCHAR(20) GENERATED ALWAYS AS (
        CASE
            WHEN DATEDIFF(CURDATE(), due_date) <= 0 THEN '未到期'
            WHEN DATEDIFF(CURDATE(), due_date) <= 30 THEN '1-30天'
            WHEN DATEDIFF(CURDATE(), due_date) <= 60 THEN '31-60天'
            WHEN DATEDIFF(CURDATE(), due_date) <= 90 THEN '61-90天'
            WHEN DATEDIFF(CURDATE(), due_date) <= 180 THEN '91-180天'
            WHEN DATEDIFF(CURDATE(), due_date) <= 365 THEN '181-365天'
            ELSE '365天以上'
        END
    ) STORED,
    planned_payment_date DATE NULL,
    INDEX idx_supplier (supplier_id),
    INDEX idx_aging_bucket (aging_bucket),
    INDEX idx_due_date (due_date),
    CONSTRAINT fk_ap_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id),
    CONSTRAINT fk_ap_order FOREIGN KEY (order_id) REFERENCES purchase_orders(id)
) ENGINE=InnoDB;

-- ============================================================
-- 3. 税务管理模块 (增值税)
-- 税率: 13%(一般货物), 9%(农产品/图书), 6%(服务), 0%(出口)
-- 进项税 = 采购发票税额, 销项税 = 销售发票税额
-- 应交增值税 = 销项税 - 进项税
-- ============================================================

CREATE TABLE tax_invoices (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    invoice_no VARCHAR(50) NOT NULL UNIQUE,
    invoice_code VARCHAR(50) NULL COMMENT '发票代码',
    invoice_type ENUM('vat_special','vat_normal','electronic','customs') NOT NULL COMMENT '发票类型',
    tax_direction ENUM('input','output') NOT NULL COMMENT '进项税/销项税',
    party_type ENUM('supplier','customer') NOT NULL,
    party_id BIGINT UNSIGNED NOT NULL,
    invoice_date DATE NOT NULL,
    amount_excluding_tax DECIMAL(18,2) NOT NULL COMMENT '不含税金额',
    tax_rate DECIMAL(5,4) NOT NULL COMMENT '税率',
    tax_amount DECIMAL(18,2) GENERATED ALWAYS AS (ROUND(amount_excluding_tax * tax_rate, 2)) STORED,
    amount_including_tax DECIMAL(18,2) GENERATED ALWAYS AS (amount_excluding_tax + ROUND(amount_excluding_tax * tax_rate, 2)) STORED,
    verification_status ENUM('pending','verified','certified','failed') DEFAULT 'pending',
    verified_at DATETIME NULL,
    verified_by BIGINT UNSIGNED NULL,
    tax_period VARCHAR(7) NOT NULL COMMENT '所属期 YYYY-MM',
    deduction_period VARCHAR(7) NULL COMMENT '抵扣期',
    reference_type VARCHAR(50) NULL,
    reference_id BIGINT NULL,
    status ENUM('draft','issued','verified','cancelled','red_issued') DEFAULT 'draft',
    remark VARCHAR(500) NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_party (party_type, party_id),
    INDEX idx_tax_period (tax_period),
    INDEX idx_direction (tax_direction),
    INDEX idx_status (status),
    INDEX idx_invoice_date (invoice_date),
    CONSTRAINT fk_ti_verified FOREIGN KEY (verified_by) REFERENCES employees(id)
) ENGINE=InnoDB;

-- 税务申报记录
CREATE TABLE tax_filings (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    tax_period VARCHAR(7) NOT NULL,
    tax_type ENUM('vat','income_tax','surtax','stamp','property','other') NOT NULL,
    output_tax DECIMAL(18,2) DEFAULT 0.00 COMMENT '销项税额',
    input_tax DECIMAL(18,2) DEFAULT 0.00 COMMENT '进项税额',
    input_tax_transfer DECIMAL(18,2) DEFAULT 0.00 COMMENT '进项税转出',
    tax_payable DECIMAL(18,2) DEFAULT 0.00 COMMENT '应纳税额',
    tax_paid DECIMAL(18,2) DEFAULT 0.00,
    filing_date DATE NULL,
    filing_deadline DATE NOT NULL,
    status ENUM('draft','prepared','filed','paid','overdue','amended') DEFAULT 'draft',
    prepared_by BIGINT UNSIGNED NULL,
    voucher_id BIGINT UNSIGNED NULL,
    remark VARCHAR(500) NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_period_type (tax_period, tax_type),
    INDEX idx_status (status),
    INDEX idx_deadline (filing_deadline),
    CONSTRAINT fk_tf_prepared FOREIGN KEY (prepared_by) REFERENCES employees(id)
) ENGINE=InnoDB;

-- ============================================================
-- 4. 质量检验模块
-- 检验类型: 来料检验(IQC), 过程检验(IPQC), 出货检验(OQC), 退货检验
-- 检验结果: 合格/不合格/让步接收/退货
-- ============================================================

CREATE TABLE inspection_standards (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT UNSIGNED NOT NULL,
    standard_name VARCHAR(200) NOT NULL,
    inspection_items JSON NULL COMMENT '检验项目JSON',
    sampling_method ENUM('full','gb2828','fixed','none') DEFAULT 'gb2828',
    sample_size INT DEFAULT 0,
    aql_level DECIMAL(5,2) DEFAULT 1.0 COMMENT 'AQL合格质量水平',
    status ENUM('active','inactive') DEFAULT 'active',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_product_standard (product_id, standard_name),
    CONSTRAINT fk_is_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE inspection_reports (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    report_no VARCHAR(30) NOT NULL UNIQUE,
    inspection_type ENUM('IQC','IPQC','OQC','return_inspection','spot_check') NOT NULL,
    reference_type VARCHAR(50) NOT NULL COMMENT '关联单据类型',
    reference_id BIGINT NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    batch_id BIGINT UNSIGNED NULL,
    standard_id BIGINT UNSIGNED NULL,
    sample_size INT NOT NULL,
    inspected_qty INT NOT NULL,
    qualified_qty INT DEFAULT 0,
    defective_qty INT DEFAULT 0,
    defect_rate DECIMAL(5,2) GENERATED ALWAYS AS (
        ROUND(defective_qty * 100.0 / NULLIF(inspected_qty, 0), 2)
    ) STORED,
    inspection_result ENUM('qualified','conditionally_accepted','rejected','rework') NOT NULL,
    inspector_id BIGINT UNSIGNED NOT NULL,
    inspection_date DATE NOT NULL,
    defect_description TEXT NULL,
    disposition VARCHAR(200) NULL COMMENT '处理意见',
    status ENUM('draft','completed','reviewed','closed') DEFAULT 'draft',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_product (product_id),
    INDEX idx_batch (batch_id),
    INDEX idx_reference (reference_type, reference_id),
    INDEX idx_result (inspection_result),
    INDEX idx_date (inspection_date),
    CONSTRAINT fk_ir_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_ir_batch FOREIGN KEY (batch_id) REFERENCES product_batches(id) ON DELETE SET NULL,
    CONSTRAINT fk_ir_standard FOREIGN KEY (standard_id) REFERENCES inspection_standards(id) ON DELETE SET NULL,
    CONSTRAINT fk_ir_inspector FOREIGN KEY (inspector_id) REFERENCES employees(id)
) ENGINE=InnoDB;

-- ============================================================
-- 5. 通用审批流引擎
-- 支持多级审批: 部门经理 -> 总监 -> 总经理
-- 审批节点可配置，支持会签/或签
-- ============================================================

CREATE TABLE approval_workflows (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    workflow_name VARCHAR(100) NOT NULL,
    workflow_code VARCHAR(30) NOT NULL UNIQUE,
    target_type VARCHAR(50) NOT NULL COMMENT '审批对象类型',
    description VARCHAR(500) NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE approval_nodes (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    workflow_id BIGINT UNSIGNED NOT NULL,
    node_name VARCHAR(100) NOT NULL,
    node_level INT NOT NULL COMMENT '审批层级 1,2,3...',
    approver_type ENUM('role','position','employee','department_manager','direct_manager') NOT NULL,
    approver_id BIGINT UNSIGNED NULL COMMENT '审批人ID(根据approver_type解释)',
    approval_mode ENUM('single','countersign','or_sign') DEFAULT 'single' COMMENT '单人/会签/或签',
    timeout_hours INT DEFAULT 48 COMMENT '超时时间',
    can_delegate BOOLEAN DEFAULT TRUE COMMENT '是否可转授权',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_workflow_level (workflow_id, node_level),
    CONSTRAINT fk_an_workflow FOREIGN KEY (workflow_id) REFERENCES approval_workflows(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE approval_instances (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    instance_no VARCHAR(30) NOT NULL UNIQUE,
    workflow_id BIGINT UNSIGNED NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id BIGINT NOT NULL,
    target_summary VARCHAR(500) NULL,
    current_node_level INT DEFAULT 1,
    total_nodes INT DEFAULT 1,
    submitted_by BIGINT UNSIGNED NOT NULL,
    submitted_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    status ENUM('pending','in_progress','approved','rejected','recalled','timeout') DEFAULT 'pending',
    completed_at DATETIME NULL,
    remark VARCHAR(500) NULL,
    INDEX idx_target (target_type, target_id),
    INDEX idx_status (status),
    INDEX idx_submitted (submitted_by, submitted_at),
    CONSTRAINT fk_ai_workflow FOREIGN KEY (workflow_id) REFERENCES approval_workflows(id),
    CONSTRAINT fk_ai_submitted FOREIGN KEY (submitted_by) REFERENCES employees(id)
) ENGINE=InnoDB;

CREATE TABLE approval_records (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    instance_id BIGINT UNSIGNED NOT NULL,
    node_id BIGINT UNSIGNED NOT NULL,
    approver_id BIGINT UNSIGNED NOT NULL,
    action ENUM('approve','reject','delegate','return','withdraw') NOT NULL,
    comment VARCHAR(500) NULL,
    action_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    delegated_to BIGINT UNSIGNED NULL,
    INDEX idx_instance (instance_id),
    CONSTRAINT fk_ar_instance FOREIGN KEY (instance_id) REFERENCES approval_instances(id) ON DELETE CASCADE,
    CONSTRAINT fk_ar_node FOREIGN KEY (node_id) REFERENCES approval_nodes(id),
    CONSTRAINT fk_ar_approver FOREIGN KEY (approver_id) REFERENCES employees(id)
) ENGINE=InnoDB;

-- ============================================================
-- 6. 现金流预测
-- 预测来源: 应收款到期日 + 应付款到期日 + 工资发放计划 + 计划收支
-- 按日/周/月汇总预测净现金流
-- ============================================================

CREATE TABLE cash_flow_forecasts (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    forecast_date DATE NOT NULL,
    forecast_type ENUM('daily','weekly','monthly') NOT NULL,
    beginning_balance DECIMAL(18,2) DEFAULT 0.00 COMMENT '期初余额',
    expected_collections DECIMAL(18,2) DEFAULT 0.00 COMMENT '预计收款',
    expected_payments DECIMAL(18,2) DEFAULT 0.00 COMMENT '预计付款',
    expected_salary DECIMAL(18,2) DEFAULT 0.00 COMMENT '预计工资',
    expected_tax DECIMAL(18,2) DEFAULT 0.00 COMMENT '预计税金',
    other_income DECIMAL(18,2) DEFAULT 0.00,
    other_expense DECIMAL(18,2) DEFAULT 0.00,
    net_cash_flow DECIMAL(18,2) GENERATED ALWAYS AS (
        expected_collections - expected_payments - expected_salary - expected_tax + other_income - other_expense
    ) STORED,
    ending_balance DECIMAL(18,2) GENERATED ALWAYS AS (
        beginning_balance + expected_collections - expected_payments - expected_salary - expected_tax + other_income - other_expense
    ) STORED,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_forecast_date_type (forecast_date, forecast_type),
    INDEX idx_forecast_date (forecast_date)
) ENGINE=InnoDB;

-- ============================================================
-- 7. 项目成本核算
-- 项目类型: 工程项目、研发项目、营销活动等
-- 成本归集: 人工成本+材料成本+费用+外包
-- ============================================================

CREATE TABLE projects (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    project_no VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    project_type ENUM('engineering','rd','marketing','internal','other') NOT NULL,
    department_id BIGINT UNSIGNED NOT NULL,
    manager_id BIGINT UNSIGNED NOT NULL,
    budget DECIMAL(18,2) NOT NULL,
    start_date DATE NOT NULL,
    planned_end_date DATE NOT NULL,
    actual_end_date DATE NULL,
    status ENUM('planning','in_progress','paused','completed','cancelled') DEFAULT 'planning',
    priority ENUM('low','normal','high','critical') DEFAULT 'normal',
    description TEXT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_dept (department_id),
    INDEX idx_manager (manager_id),
    INDEX idx_status (status),
    CONSTRAINT fk_proj_dept FOREIGN KEY (department_id) REFERENCES departments(id),
    CONSTRAINT fk_proj_manager FOREIGN KEY (manager_id) REFERENCES employees(id)
) ENGINE=InnoDB;

CREATE TABLE project_costs (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT UNSIGNED NOT NULL,
    cost_type ENUM('labor','material','equipment','travel','outsource','other') NOT NULL,
    cost_date DATE NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    description VARCHAR(500) NULL,
    reference_type VARCHAR(50) NULL,
    reference_id BIGINT NULL,
    recorded_by BIGINT UNSIGNED NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_project (project_id),
    INDEX idx_date (cost_date),
    INDEX idx_type (cost_type),
    CONSTRAINT fk_pc_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================================
-- 8. 多币种与汇率管理
-- ============================================================

CREATE TABLE exchange_rates (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    from_currency VARCHAR(3) NOT NULL,
    to_currency VARCHAR(3) NOT NULL DEFAULT 'CNY',
    rate_date DATE NOT NULL,
    rate DECIMAL(12,6) NOT NULL COMMENT '1 from_currency = rate to_currency',
    rate_source VARCHAR(50) DEFAULT 'BOC' COMMENT '中国银行/央行/手工',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_currency_date (from_currency, to_currency, rate_date),
    INDEX idx_date (rate_date)
) ENGINE=InnoDB;

-- 外币账户
CREATE TABLE foreign_currency_accounts (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT UNSIGNED NOT NULL,
    currency VARCHAR(3) NOT NULL,
    original_balance DECIMAL(18,2) DEFAULT 0.00 COMMENT '原币余额',
    cny_equivalent DECIMAL(18,2) DEFAULT 0.00 COMMENT '人民币等值',
    last_revaluation_date DATE NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_account_currency (account_id, currency),
    CONSTRAINT fk_fca_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================================
-- 9. 绩效考核模块
-- 考核周期: 月度/季度/年度
-- 评分维度: 业绩(40%) + 能力(30%) + 态度(20%) + 出勤(10%)
-- ============================================================

CREATE TABLE performance_reviews (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    review_no VARCHAR(30) NOT NULL UNIQUE,
    employee_id BIGINT UNSIGNED NOT NULL,
    reviewer_id BIGINT UNSIGNED NOT NULL,
    review_period VARCHAR(7) NOT NULL COMMENT 'YYYY-MM / YYYY-Q1 / YYYY',
    review_type ENUM('monthly','quarterly','annual','probation','promotion') NOT NULL,
    -- 评分维度
    performance_score DECIMAL(5,2) DEFAULT 0.00 COMMENT '业绩评分(0-100)',
    competency_score DECIMAL(5,2) DEFAULT 0.00 COMMENT '能力评分(0-100)',
    attitude_score DECIMAL(5,2) DEFAULT 0.00 COMMENT '态度评分(0-100)',
    attendance_score DECIMAL(5,2) DEFAULT 0.00 COMMENT '出勤评分(0-100)',
    -- 加权总分 (业绩40% + 能力30% + 态度20% + 出勤10%)
    total_score DECIMAL(5,2) GENERATED ALWAYS AS (
        ROUND(performance_score * 0.40 + competency_score * 0.30 + attitude_score * 0.20 + attendance_score * 0.10, 2)
    ) STORED,
    grade CHAR(1) GENERATED ALWAYS AS (
        CASE
            WHEN ROUND(performance_score * 0.40 + competency_score * 0.30 + attitude_score * 0.20 + attendance_score * 0.10, 2) >= 90 THEN 'S'
            WHEN ROUND(performance_score * 0.40 + competency_score * 0.30 + attitude_score * 0.20 + attendance_score * 0.10, 2) >= 80 THEN 'A'
            WHEN ROUND(performance_score * 0.40 + competency_score * 0.30 + attitude_score * 0.20 + attendance_score * 0.10, 2) >= 70 THEN 'B'
            WHEN ROUND(performance_score * 0.40 + competency_score * 0.30 + attitude_score * 0.20 + attendance_score * 0.10, 2) >= 60 THEN 'C'
            ELSE 'D'
        END
    ) STORED,
    self_assessment TEXT NULL COMMENT '自评',
    reviewer_comment TEXT NULL COMMENT '考评人评语',
    improvement_plan TEXT NULL COMMENT '改进计划',
    salary_adjustment DECIMAL(12,2) DEFAULT 0.00 COMMENT '建议调薪',
    promotion_recommendation BOOLEAN DEFAULT FALSE,
    status ENUM('draft','self_reviewed','reviewed','confirmed','appealed') DEFAULT 'draft',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_emp_period_type (employee_id, review_period, review_type),
    INDEX idx_reviewer (reviewer_id),
    INDEX idx_grade (grade),
    INDEX idx_period (review_period),
    CONSTRAINT fk_pr_employee FOREIGN KEY (employee_id) REFERENCES employees(id),
    CONSTRAINT fk_pr_reviewer FOREIGN KEY (reviewer_id) REFERENCES employees(id)
) ENGINE=InnoDB;

-- KPI指标定义
CREATE TABLE kpi_indicators (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    indicator_type ENUM('sales','financial','operational','hr','customer','other') NOT NULL,
    unit VARCHAR(20) NOT NULL,
    target_direction ENUM('higher_better','lower_better','range') NOT NULL,
    target_value DECIMAL(12,2) NOT NULL,
    target_min DECIMAL(12,2) NULL,
    target_max DECIMAL(12,2) NULL,
    weight DECIMAL(5,4) DEFAULT 1.0,
    applicable_role_id BIGINT UNSIGNED NULL COMMENT '适用角色',
    department_id BIGINT UNSIGNED NULL COMMENT '适用部门',
    status ENUM('active','inactive') DEFAULT 'active',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_role (applicable_role_id),
    INDEX idx_dept (department_id)
) ENGINE=InnoDB;

-- ============================================================
-- 10. 序列号追踪 (单品追踪)
-- 用于高价值商品(电子产品/设备)的单品全生命周期追踪
-- 生命周期: 采购入库 -> 库存 -> 销售出库 -> 客户 -> 退货 -> 翻新 -> 重新入库
-- ============================================================

CREATE TABLE serial_numbers (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT UNSIGNED NOT NULL,
    batch_id BIGINT UNSIGNED NULL,
    serial_no VARCHAR(100) NOT NULL,
    status ENUM('in_stock','in_transit','sold','returned','rma','scrapped','lost') DEFAULT 'in_stock',
    warehouse_id BIGINT UNSIGNED NULL,
    purchase_receipt_id BIGINT UNSIGNED NULL COMMENT '采购入库单',
    sales_order_id BIGINT UNSIGNED NULL COMMENT '销售出库单',
    return_id BIGINT UNSIGNED NULL COMMENT '退货单',
    current_owner_id BIGINT UNSIGNED NULL COMMENT '当前持有者(客户/员工)',
    warranty_start DATE NULL,
    warranty_end DATE NULL,
    last_scan_date DATETIME NULL,
    last_scan_location VARCHAR(200) NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_serial (product_id, serial_no),
    INDEX idx_status (status),
    INDEX idx_batch (batch_id),
    INDEX idx_warehouse (warehouse_id),
    INDEX idx_warranty (warranty_end),
    CONSTRAINT fk_sn_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_sn_batch FOREIGN KEY (batch_id) REFERENCES product_batches(id) ON DELETE SET NULL,
    CONSTRAINT fk_sn_wh FOREIGN KEY (warehouse_id) REFERENCES warehouses(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- 序列号流转日志
CREATE TABLE serial_number_logs (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    serial_number_id BIGINT UNSIGNED NOT NULL,
    event_type ENUM('purchase_in','sales_out','return_in','transfer','scrap','rma_out','rma_in','location_change') NOT NULL,
    from_status VARCHAR(20) NULL,
    to_status VARCHAR(20) NOT NULL,
    from_location VARCHAR(200) NULL,
    to_location VARCHAR(200) NULL,
    reference_type VARCHAR(50) NULL,
    reference_id BIGINT NULL,
    operator_id BIGINT UNSIGNED NULL,
    event_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    remark VARCHAR(500) NULL,
    INDEX idx_sn (serial_number_id),
    INDEX idx_event_time (event_time),
    CONSTRAINT fk_snl_sn FOREIGN KEY (serial_number_id) REFERENCES serial_numbers(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================================
-- 11. 寄售库存
-- 寄售: 货品存放在客户处，客户使用时才结算
-- 所有权仍属于我方，不确认收入
-- ============================================================

CREATE TABLE consignment_inventory (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT UNSIGNED NOT NULL,
    batch_id BIGINT UNSIGNED NULL,
    customer_id BIGINT UNSIGNED NOT NULL,
    consigned_qty INT NOT NULL COMMENT '寄售数量',
    consumed_qty INT DEFAULT 0 COMMENT '已消耗数量',
    available_qty INT GENERATED ALWAYS AS (consigned_qty - consumed_qty) STORED,
    unit_price DECIMAL(12,2) NOT NULL COMMENT '结算单价',
    consigned_date DATE NOT NULL,
    last_consumed_date DATE NULL,
    settlement_period VARCHAR(7) NULL COMMENT '结算周期',
    status ENUM('active','settling','settled','returned') DEFAULT 'active',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_product_batch_customer (product_id, batch_id, customer_id),
    INDEX idx_customer (customer_id),
    INDEX idx_status (status),
    CONSTRAINT fk_ci_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_ci_batch FOREIGN KEY (batch_id) REFERENCES product_batches(id) ON DELETE SET NULL,
    CONSTRAINT fk_ci_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
) ENGINE=InnoDB;

-- 寄售消耗记录
CREATE TABLE consignment_consumptions (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    consignment_id BIGINT UNSIGNED NOT NULL,
    consumed_qty INT NOT NULL,
    consumed_date DATE NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    amount DECIMAL(18,2) GENERATED ALWAYS AS (consumed_qty * unit_price) STORED,
    confirmed_by_customer BOOLEAN DEFAULT FALSE,
    sales_order_id BIGINT UNSIGNED NULL COMMENT '结算生成的销售单',
    remark VARCHAR(200) NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_consignment (consignment_id),
    INDEX idx_date (consumed_date),
    CONSTRAINT fk_cc_consignment FOREIGN KEY (consignment_id) REFERENCES consignment_inventory(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================================
-- 12. 价格变更历史
-- 记录所有价格变更，用于价格分析和审计
-- ============================================================

CREATE TABLE price_change_logs (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT UNSIGNED NOT NULL,
    price_type ENUM('purchase','wholesale','retail') NOT NULL,
    old_price DECIMAL(12,2) NOT NULL,
    new_price DECIMAL(12,2) NOT NULL,
    change_reason VARCHAR(200) NOT NULL,
    effective_date DATE NOT NULL,
    changed_by BIGINT UNSIGNED NOT NULL,
    approved_by BIGINT UNSIGNED NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_product (product_id),
    INDEX idx_effective_date (effective_date),
    INDEX idx_price_type (price_type),
    CONSTRAINT fk_pcl_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_pcl_changed FOREIGN KEY (changed_by) REFERENCES employees(id)
) ENGINE=InnoDB;