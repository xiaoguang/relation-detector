-- ============================================================
-- ERP系统补充表: 发货物流、销售提成、促销活动、
--   三单匹配、固定资产、BOM生产工单、客服工单
-- 关系说明:
--   shipments -> sales_orders (1:1), 通过tracking_no追踪物流
--   sales_commissions -> sales_orders + employees (N:1:1), 按销售额计算提成
--   promotions -> sales_order_items (N:M), 通过promotion_items关联
--   invoices -> purchase_orders + purchase_receipts (三单匹配)
--   fixed_assets + depreciation_log (固定资产+折旧)
--   boms -> products (自引用), 生产工单用料展开
--   work_orders -> boms + products (生产工单)
-- ============================================================

USE erp_system;

-- ============================================================
-- 1. 发货物流模块
-- shipments: 发货单 -> 关联sales_orders
-- shipping_tracks: 物流轨迹 -> 关联shipments
-- ============================================================

CREATE TABLE shipments (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    shipment_no VARCHAR(30) NOT NULL UNIQUE,
    order_id BIGINT UNSIGNED NOT NULL,
    warehouse_id BIGINT UNSIGNED NOT NULL,
    carrier VARCHAR(100) NOT NULL COMMENT '物流公司',
    tracking_no VARCHAR(50) NULL COMMENT '物流单号',
    shipping_method ENUM('express','truck','air','sea','self_pickup') DEFAULT 'express',
    shipping_fee DECIMAL(12,2) DEFAULT 0.00 COMMENT '运费',
    package_count INT DEFAULT 1 COMMENT '包裹数',
    weight_kg DECIMAL(8,3) DEFAULT 0.000,
    status ENUM('pending','picking','packed','shipped','in_transit','delivered','returned','lost') DEFAULT 'pending',
    picker_id BIGINT UNSIGNED NULL COMMENT '拣货员',
    packer_id BIGINT UNSIGNED NULL COMMENT '打包员',
    shipped_at DATETIME NULL,
    delivered_at DATETIME NULL,
    estimated_delivery_date DATE NULL,
    actual_delivery_date DATE NULL,
    from_address VARCHAR(300) NULL,
    to_address VARCHAR(300) NOT NULL,
    receiver_name VARCHAR(50) NULL,
    receiver_phone VARCHAR(20) NULL,
    remark VARCHAR(500) NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_order (order_id),
    INDEX idx_tracking (tracking_no),
    INDEX idx_status (status),
    INDEX idx_carrier (carrier),
    INDEX idx_shipped_at (shipped_at),
    CONSTRAINT fk_ship_order FOREIGN KEY (order_id) REFERENCES sales_orders(id),
    CONSTRAINT fk_ship_wh FOREIGN KEY (warehouse_id) REFERENCES warehouses(id)
) ENGINE=InnoDB;

CREATE TABLE shipping_tracks (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    shipment_id BIGINT UNSIGNED NOT NULL,
    track_time DATETIME NOT NULL,
    location VARCHAR(200) NOT NULL,
    status_desc VARCHAR(200) NOT NULL,
    operator VARCHAR(50) NULL,
    remark VARCHAR(200) NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_shipment (shipment_id),
    INDEX idx_track_time (track_time),
    CONSTRAINT fk_st_shipment FOREIGN KEY (shipment_id) REFERENCES shipments(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================================
-- 2. 销售提成模块
-- 提成规则: 不同产品/分类不同提成比例
-- 销售额阶梯提成，超过目标有额外奖励
-- ============================================================

CREATE TABLE commission_rules (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    product_category_id BIGINT UNSIGNED NULL COMMENT 'NULL=所有分类',
    min_amount DECIMAL(18,2) DEFAULT 0.00 COMMENT '销售额区间下限',
    max_amount DECIMAL(18,2) DEFAULT 99999999.99 COMMENT '销售额区间上限',
    commission_rate DECIMAL(5,4) NOT NULL COMMENT '提成比例(0-1)',
    bonus DECIMAL(12,2) DEFAULT 0.00 COMMENT '额外奖金',
    effective_date DATE NOT NULL,
    expiry_date DATE NULL,
    status ENUM('active','inactive') DEFAULT 'active',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_category (product_category_id),
    INDEX idx_effective (effective_date, expiry_date),
    CONSTRAINT fk_cr_category FOREIGN KEY (product_category_id) REFERENCES product_categories(id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE sales_commissions (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT UNSIGNED NOT NULL,
    order_id BIGINT UNSIGNED NOT NULL,
    order_item_id BIGINT UNSIGNED NULL,
    period VARCHAR(7) NOT NULL COMMENT 'YYYY-MM',
    base_amount DECIMAL(18,2) NOT NULL COMMENT '销售额基数',
    commission_rate DECIMAL(5,4) NOT NULL,
    commission_amount DECIMAL(12,2) NOT NULL,
    bonus DECIMAL(12,2) DEFAULT 0.00,
    total_commission DECIMAL(12,2) GENERATED ALWAYS AS (commission_amount + bonus) STORED,
    status ENUM('pending','calculated','paid','cancelled') DEFAULT 'pending',
    calculated_at DATETIME NULL,
    paid_at DATETIME NULL,
    settlement_id BIGINT UNSIGNED NULL,
    INDEX idx_employee (employee_id),
    INDEX idx_period (period),
    INDEX idx_status (status),
    UNIQUE KEY uk_order_item (order_id, order_item_id),
    CONSTRAINT fk_sc_emp FOREIGN KEY (employee_id) REFERENCES employees(id),
    CONSTRAINT fk_sc_order FOREIGN KEY (order_id) REFERENCES sales_orders(id)
) ENGINE=InnoDB;

-- ============================================================
-- 3. 促销活动模块
-- ============================================================

CREATE TABLE promotions (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    code VARCHAR(30) NOT NULL UNIQUE COMMENT '促销代码',
    promotion_type ENUM('discount_pct','discount_amount','buy_x_get_y','bundle','coupon','flash_sale') NOT NULL,
    discount_value DECIMAL(12,2) NOT NULL COMMENT '折扣值(百分比或金额)',
    min_purchase_amount DECIMAL(18,2) DEFAULT 0.00 COMMENT '最低消费门槛',
    max_discount_amount DECIMAL(12,2) DEFAULT 0.00 COMMENT '最高折扣金额',
    usage_limit INT DEFAULT 0 COMMENT '0=不限',
    used_count INT DEFAULT 0,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status ENUM('draft','active','paused','ended','cancelled') DEFAULT 'draft',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_date_range (start_date, end_date),
    INDEX idx_status (status),
    INDEX idx_code (code)
) ENGINE=InnoDB;

CREATE TABLE promotion_products (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    promotion_id BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NULL COMMENT 'NULL=适用所有商品',
    category_id BIGINT UNSIGNED NULL COMMENT 'NULL=不限分类',
    UNIQUE KEY uk_promo_product (promotion_id, product_id, category_id),
    CONSTRAINT fk_pp_promo FOREIGN KEY (promotion_id) REFERENCES promotions(id) ON DELETE CASCADE,
    CONSTRAINT fk_pp_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_pp_category FOREIGN KEY (category_id) REFERENCES product_categories(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 促销使用记录
CREATE TABLE promotion_usages (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    promotion_id BIGINT UNSIGNED NOT NULL,
    order_id BIGINT UNSIGNED NOT NULL,
    customer_id BIGINT UNSIGNED NOT NULL,
    discount_applied DECIMAL(12,2) NOT NULL,
    used_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_promo_order (promotion_id, order_id),
    INDEX idx_customer (customer_id),
    CONSTRAINT fk_pu_promo FOREIGN KEY (promotion_id) REFERENCES promotions(id),
    CONSTRAINT fk_pu_order FOREIGN KEY (order_id) REFERENCES sales_orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_pu_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
) ENGINE=InnoDB;

-- ============================================================
-- 4. 发票与三单匹配模块
-- 三单匹配: 采购单(PO) vs 入库单(Receipt) vs 发票(Invoice)
-- 匹配条件: 数量、单价、金额一致才可付款
-- ============================================================

CREATE TABLE invoices (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    invoice_no VARCHAR(50) NOT NULL UNIQUE,
    invoice_type ENUM('purchase','sales','expense') NOT NULL,
    supplier_id BIGINT UNSIGNED NULL COMMENT '采购发票的供应商',
    customer_id BIGINT UNSIGNED NULL COMMENT '销售发票的客户',
    invoice_date DATE NOT NULL,
    due_date DATE NULL,
    total_amount DECIMAL(18,2) NOT NULL,
    tax_amount DECIMAL(18,2) DEFAULT 0.00,
    tax_rate DECIMAL(5,4) DEFAULT 0.13 COMMENT '默认13%增值税',
    status ENUM('draft','received','verified','matched','paid','partial_paid','disputed','cancelled') DEFAULT 'draft',
    verified_by BIGINT UNSIGNED NULL,
    verified_at DATETIME NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_supplier (supplier_id),
    INDEX idx_customer (customer_id),
    INDEX idx_date (invoice_date),
    INDEX idx_status (status),
    CONSTRAINT fk_inv_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id) ON DELETE SET NULL,
    CONSTRAINT fk_inv_customer FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- 三单匹配明细
CREATE TABLE three_way_matching (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    invoice_id BIGINT UNSIGNED NOT NULL,
    purchase_order_id BIGINT UNSIGNED NOT NULL,
    purchase_receipt_id BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    po_quantity INT NOT NULL COMMENT '采购单数量',
    receipt_quantity INT NOT NULL COMMENT '入库数量',
    invoice_quantity INT NOT NULL COMMENT '发票数量',
    po_price DECIMAL(12,2) NOT NULL,
    receipt_price DECIMAL(12,2) NOT NULL,
    invoice_price DECIMAL(12,2) NOT NULL,
    quantity_match BOOLEAN GENERATED ALWAYS AS (po_quantity = receipt_quantity AND receipt_quantity = invoice_quantity) STORED,
    price_match BOOLEAN GENERATED ALWAYS AS (po_price = receipt_price AND receipt_price = invoice_price) STORED,
    match_status ENUM('pending','matched','quantity_mismatch','price_mismatch','both_mismatch') DEFAULT 'pending',
    match_result VARCHAR(500) NULL,
    matched_by BIGINT UNSIGNED NULL,
    matched_at DATETIME NULL,
    INDEX idx_invoice (invoice_id),
    INDEX idx_po (purchase_order_id),
    INDEX idx_receipt (purchase_receipt_id),
    CONSTRAINT fk_twm_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE CASCADE,
    CONSTRAINT fk_twm_po FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id),
    CONSTRAINT fk_twm_receipt FOREIGN KEY (purchase_receipt_id) REFERENCES purchase_receipts(id),
    CONSTRAINT fk_twm_product FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB;

-- ============================================================
-- 5. 固定资产模块
-- 折旧方法: 直线法(straight_line)
-- 折旧公式: 月折旧额 = (原值 - 残值) / 使用月数
-- 净值 = 原值 - 累计折旧
-- ============================================================

CREATE TABLE fixed_assets (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    asset_no VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    category ENUM('building','equipment','vehicle','furniture','computer','software','other') NOT NULL,
    purchase_date DATE NOT NULL,
    purchase_amount DECIMAL(18,2) NOT NULL COMMENT '原值',
    salvage_value DECIMAL(18,2) DEFAULT 0.00 COMMENT '残值',
    useful_life_months INT NOT NULL COMMENT '使用月数',
    monthly_depreciation DECIMAL(12,2) GENERATED ALWAYS AS (
        ROUND((purchase_amount - salvage_value) / useful_life_months, 2)
    ) STORED COMMENT '月折旧额',
    accumulated_depreciation DECIMAL(18,2) DEFAULT 0.00 COMMENT '累计折旧',
    net_book_value DECIMAL(18,2) GENERATED ALWAYS AS (
        purchase_amount - accumulated_depreciation
    ) STORED COMMENT '账面净值',
    department_id BIGINT UNSIGNED NOT NULL,
    custodian_id BIGINT UNSIGNED NULL COMMENT '保管人',
    location VARCHAR(200) NULL,
    status ENUM('in_use','idle','maintenance','scrapped','disposed') DEFAULT 'in_use',
    last_depreciation_date DATE NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_dept (department_id),
    INDEX idx_category (category),
    INDEX idx_status (status),
    CONSTRAINT fk_fa_dept FOREIGN KEY (department_id) REFERENCES departments(id),
    CONSTRAINT fk_fa_custodian FOREIGN KEY (custodian_id) REFERENCES employees(id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE depreciation_log (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    asset_id BIGINT UNSIGNED NOT NULL,
    depreciation_date DATE NOT NULL,
    depreciation_amount DECIMAL(12,2) NOT NULL,
    before_accumulated DECIMAL(18,2) NOT NULL,
    after_accumulated DECIMAL(18,2) NOT NULL,
    before_net_value DECIMAL(18,2) NOT NULL,
    after_net_value DECIMAL(18,2) NOT NULL,
    voucher_id BIGINT UNSIGNED NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_asset_date (asset_id, depreciation_date),
    INDEX idx_date (depreciation_date),
    CONSTRAINT fk_dl_asset FOREIGN KEY (asset_id) REFERENCES fixed_assets(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================================
-- 6. BOM与生产工单模块
-- BOM: 物料清单，定义产品组成关系
-- 自引用: parent_product_id -> products.id (成品), child_product_id -> products.id (原料/半成品)
-- ============================================================

CREATE TABLE boms (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    parent_product_id BIGINT UNSIGNED NOT NULL COMMENT '成品/父件',
    child_product_id BIGINT UNSIGNED NOT NULL COMMENT '原料/子件',
    quantity DECIMAL(10,3) NOT NULL COMMENT '用量',
    unit VARCHAR(20) NOT NULL,
    scrap_rate DECIMAL(5,4) DEFAULT 0.00 COMMENT '损耗率',
    sort_order INT DEFAULT 0,
    effective_date DATE NOT NULL,
    expiry_date DATE NULL,
    status ENUM('active','inactive','obsolete') DEFAULT 'active',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_parent_child (parent_product_id, child_product_id, effective_date),
    INDEX idx_child (child_product_id),
    INDEX idx_status (status),
    CONSTRAINT fk_bom_parent FOREIGN KEY (parent_product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_bom_child FOREIGN KEY (child_product_id) REFERENCES products(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE work_orders (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(30) NOT NULL UNIQUE,
    product_id BIGINT UNSIGNED NOT NULL COMMENT '生产成品',
    bom_id BIGINT UNSIGNED NULL COMMENT '使用的BOM',
    planned_quantity INT NOT NULL COMMENT '计划生产数量',
    completed_quantity INT DEFAULT 0 COMMENT '已完成数量',
    rejected_quantity INT DEFAULT 0 COMMENT '不合格数量',
    warehouse_id BIGINT UNSIGNED NOT NULL COMMENT '生产仓库',
    start_date DATE NULL,
    due_date DATE NULL,
    completed_date DATE NULL,
    status ENUM('draft','released','in_progress','completed','cancelled','on_hold') DEFAULT 'draft',
    priority ENUM('low','normal','high','urgent') DEFAULT 'normal',
    released_by BIGINT UNSIGNED NULL,
    remark VARCHAR(500) NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_product (product_id),
    INDEX idx_status (status),
    INDEX idx_due_date (due_date),
    INDEX idx_priority (priority),
    CONSTRAINT fk_wo_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_wo_bom FOREIGN KEY (bom_id) REFERENCES boms(id) ON DELETE SET NULL,
    CONSTRAINT fk_wo_wh FOREIGN KEY (warehouse_id) REFERENCES warehouses(id)
) ENGINE=InnoDB;

-- 工单用料明细
CREATE TABLE work_order_materials (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    work_order_id BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL COMMENT '原料',
    batch_id BIGINT UNSIGNED NULL,
    required_qty DECIMAL(10,3) NOT NULL COMMENT '需求数量',
    issued_qty DECIMAL(10,3) DEFAULT 0 COMMENT '已发料数量',
    returned_qty DECIMAL(10,3) DEFAULT 0 COMMENT '退料数量',
    actual_consumed DECIMAL(10,3) DEFAULT 0 COMMENT '实际消耗',
    unit VARCHAR(20) NOT NULL,
    status ENUM('pending','partial','issued','completed') DEFAULT 'pending',
    INDEX idx_wo (work_order_id),
    INDEX idx_product (product_id),
    CONSTRAINT fk_wom_wo FOREIGN KEY (work_order_id) REFERENCES work_orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_wom_product FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB;

-- ============================================================
-- 7. 客服工单模块
-- ============================================================

CREATE TABLE service_tickets (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    ticket_no VARCHAR(30) NOT NULL UNIQUE,
    customer_id BIGINT UNSIGNED NOT NULL,
    order_id BIGINT UNSIGNED NULL,
    product_id BIGINT UNSIGNED NULL,
    ticket_type ENUM('complaint','return','exchange','inquiry','maintenance','other') NOT NULL,
    priority ENUM('low','normal','high','urgent') DEFAULT 'normal',
    subject VARCHAR(200) NOT NULL,
    description TEXT NULL,
    status ENUM('open','processing','waiting_customer','resolved','closed','escalated') DEFAULT 'open',
    assigned_to BIGINT UNSIGNED NULL,
    resolution VARCHAR(500) NULL,
    resolved_at DATETIME NULL,
    satisfaction_score TINYINT UNSIGNED NULL COMMENT '1-5分',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_customer (customer_id),
    INDEX idx_order (order_id),
    INDEX idx_status (status),
    INDEX idx_priority (priority),
    INDEX idx_assigned (assigned_to),
    CONSTRAINT fk_st_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_st_order FOREIGN KEY (order_id) REFERENCES sales_orders(id) ON DELETE SET NULL,
    CONSTRAINT fk_st_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE SET NULL,
    CONSTRAINT fk_st_assigned FOREIGN KEY (assigned_to) REFERENCES employees(id) ON DELETE SET NULL
) ENGINE=InnoDB;