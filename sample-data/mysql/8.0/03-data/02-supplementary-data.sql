-- ============================================================
-- ERP系统补充数据: 发货物流、促销、提成规则、发票、
--   固定资产、BOM、工单、客服工单
-- ============================================================

USE erp_system;

-- ============================================================
-- 1. 发货数据 (为每个已交付的销售单生成发货记录)
-- 关系: shipments -> sales_orders
-- ============================================================

INSERT INTO shipments (shipment_no, order_id, warehouse_id, carrier, tracking_no, shipping_method,
    shipping_fee, package_count, weight_kg, status, picker_id, packer_id,
    shipped_at, delivered_at, estimated_delivery_date, actual_delivery_date,
    from_address, to_address, receiver_name, receiver_phone)
SELECT
    CONCAT('SH-', so.order_date, '-', LPAD(so.id, 4, '0')),
    so.id,
    so.warehouse_id,
    ELT(FLOOR(RAND() * 4) + 1, '顺丰速运', '中通快递', '圆通速递', '京东物流'),
    CONCAT('TN', DATE_FORMAT(so.order_date, '%Y%m%d'), LPAD(so.id, 6, '0')),
    ELT(FLOOR(RAND() * 3) + 1, 'express', 'truck', 'express'),
    ROUND(10 + RAND() * 50, 2),
    FLOOR(RAND() * 3) + 1,
    ROUND(1 + RAND() * 10, 3),
    IF(so.status = 'delivered', 'delivered', 'shipped'),
    FLOOR(RAND() * 10) + 45,  -- picker: 仓库员工
    FLOOR(RAND() * 10) + 45,  -- packer: 仓库员工
    DATE_ADD(so.order_date, INTERVAL FLOOR(RAND() * 2) DAY),
    IF(so.status = 'delivered', DATE_ADD(so.order_date, INTERVAL FLOOR(RAND() * 3) + 2 DAY), NULL),
    DATE_ADD(so.order_date, INTERVAL 3 DAY),
    IF(so.status = 'delivered', DATE_ADD(so.order_date, INTERVAL FLOOR(RAND() * 3) + 2 DAY), NULL),
    '上海市松江区物流园A区',
    (SELECT address FROM customers WHERE id = so.customer_id),
    (SELECT contact_person FROM customers WHERE id = so.customer_id),
    (SELECT phone FROM customers WHERE id = so.customer_id)
FROM sales_orders so
WHERE so.status IN ('delivered', 'delivering', 'confirmed')
LIMIT 200;

-- 物流轨迹 (为已发货的生成轨迹)
INSERT INTO shipping_tracks (shipment_id, track_time, location, status_desc, operator)
SELECT
    s.id,
    DATE_ADD(s.shipped_at, INTERVAL seq.hour HOUR),
    CASE seq.hour
        WHEN 0 THEN '上海分拣中心'
        WHEN 4 THEN '上海转运中心'
        WHEN 12 THEN ELT(FLOOR(RAND() * 3) + 1, '南京转运中心', '杭州转运中心', '合肥转运中心')
        WHEN 24 THEN CONCAT((SELECT SUBSTRING_INDEX(address, '市', 1) FROM customers WHERE id = so.customer_id), '配送站')
        ELSE CONCAT((SELECT SUBSTRING_INDEX(address, '市', 1) FROM customers WHERE id = so.customer_id), '营业点')
    END,
    CASE seq.hour
        WHEN 0 THEN '已揽收'
        WHEN 4 THEN '运输中'
        WHEN 12 THEN '到达转运中心'
        WHEN 24 THEN '到达配送站'
        ELSE '派送中'
    END,
    '系统'
FROM shipments s
JOIN sales_orders so ON s.order_id = so.id
CROSS JOIN (SELECT 0 AS hour UNION SELECT 4 UNION SELECT 12 UNION SELECT 24 UNION SELECT 36) seq
WHERE s.status IN ('shipped', 'delivered');


-- ============================================================
-- 2. 销售提成规则
-- 计算原理: 不同分类不同阶梯不同提成率
-- ============================================================

INSERT INTO commission_rules (name, product_category_id, min_amount, max_amount, commission_rate, bonus, effective_date) VALUES
('电子产品-基础提成', 1, 0, 50000, 0.03, 0, '2024-01-01'),
('电子产品-中级提成', 1, 50000, 200000, 0.05, 500, '2024-01-01'),
('电子产品-高级提成', 1, 200000, 99999999, 0.08, 2000, '2024-01-01'),
('食品饮料-基础提成', 2, 0, 30000, 0.02, 0, '2024-01-01'),
('食品饮料-高级提成', 2, 30000, 99999999, 0.04, 300, '2024-01-01'),
('办公用品-统一提成', 3, 0, 99999999, 0.03, 0, '2024-01-01'),
('日用百货-统一提成', 4, 0, 99999999, 0.025, 0, '2024-01-01'),
('服装鞋帽-基础提成', 5, 0, 80000, 0.03, 0, '2024-01-01'),
('服装鞋帽-高级提成', 5, 80000, 99999999, 0.06, 1000, '2024-01-01'),
('全品类-超额奖励', NULL, 300000, 99999999, 0.02, 5000, '2024-01-01');


-- ============================================================
-- 3. 促销活动
-- ============================================================

INSERT INTO promotions (name, code, promotion_type, discount_value, min_purchase_amount,
    max_discount_amount, usage_limit, start_date, end_date, status) VALUES
('618年中大促-全场9折', 'PROMO618', 'discount_pct', 10.00, 100.00, 500.00, 1000, '2024-06-01', '2024-06-30', 'active'),
('新人首单-满200减30', 'NEWUSER30', 'discount_amount', 30.00, 200.00, 30.00, 500, '2024-01-01', '2024-12-31', 'active'),
('食品饮料-买二送一', 'FOOD2G1', 'buy_x_get_y', 0.00, 0.00, 0.00, 200, '2024-03-01', '2024-09-30', 'active'),
('办公用品-满500减80', 'OFFICE80', 'discount_amount', 80.00, 500.00, 80.00, 300, '2024-04-01', '2024-08-31', 'active'),
('会员日-全场8.5折', 'VIP15OFF', 'discount_pct', 15.00, 500.00, 1000.00, 100, '2024-06-15', '2024-06-20', 'active'),
('夏季清仓-服装5折', 'SUMMER50', 'discount_pct', 50.00, 0.00, 500.00, 500, '2024-07-01', '2024-08-31', 'active'),
('双11预售-定金膨胀', 'PROMO1111', 'discount_pct', 20.00, 1000.00, 2000.00, 2000, '2024-10-20', '2024-11-11', 'draft');

-- 促销商品关联
INSERT INTO promotion_products (promotion_id, product_id, category_id) VALUES
(1, NULL, NULL),  -- 全场
(2, NULL, NULL),  -- 全场
(3, NULL, 2),     -- 食品饮料
(4, NULL, 3),     -- 办公用品
(5, NULL, NULL),  -- 全场
(6, NULL, 5),     -- 服装鞋帽
(7, NULL, NULL);  -- 全场


-- ============================================================
-- 4. 发票数据
-- ============================================================

INSERT INTO invoices (invoice_no, invoice_type, supplier_id, invoice_date, due_date,
    total_amount, tax_amount, tax_rate, status, verified_by, verified_at)
SELECT
    CONCAT('INV-', DATE_FORMAT(po.order_date, '%Y%m%d'), '-', LPAD(po.id, 4, '0')),
    'purchase',
    po.supplier_id,
    DATE_ADD(po.order_date, INTERVAL FLOOR(RAND() * 5) DAY),
    DATE_ADD(po.order_date, INTERVAL 30 DAY),
    po.total_amount,
    ROUND(po.total_amount * 0.13, 2),
    0.13,
    IF(po.status = 'received', 'verified', 'received'),
    35,  -- 财务经理马超
    DATE_ADD(po.order_date, INTERVAL FLOOR(RAND() * 5) + 3 DAY)
FROM purchase_orders po
WHERE po.status IN ('received', 'partially_received')
LIMIT 100;


-- ============================================================
-- 5. 固定资产
-- 折旧原理: 直线法 = (原值 - 残值) / 使用月数
-- 月折旧额通过GENERATED ALWAYS列自动计算
-- ============================================================

INSERT INTO fixed_assets (asset_no, name, category, purchase_date, purchase_amount,
    salvage_value, useful_life_months, department_id, custodian_id, location, status, last_depreciation_date) VALUES
('FA-001', '总部办公楼', 'building', '2020-01-15', 5000000.00, 500000.00, 360, 1, 1, '上海市浦东新区', 'in_use', '2024-05-31'),
('FA-002', '华东仓库', 'building', '2020-06-01', 3000000.00, 300000.00, 360, 5, 42, '上海市松江区', 'in_use', '2024-05-31'),
('FA-003', '配送货车-沪A001', 'vehicle', '2022-03-15', 250000.00, 25000.00, 96, 5, 43, '车队', 'in_use', '2024-05-31'),
('FA-004', '配送货车-沪A002', 'vehicle', '2022-03-15', 250000.00, 25000.00, 96, 5, 44, '车队', 'in_use', '2024-05-31'),
('FA-005', '配送货车-沪A003', 'vehicle', '2023-06-01', 280000.00, 28000.00, 96, 5, 43, '车队', 'in_use', '2024-05-31'),
('FA-006', '服务器集群', 'computer', '2022-01-10', 450000.00, 0.00, 60, 6, 54, '机房', 'in_use', '2024-05-31'),
('FA-007', '办公电脑-50台', 'computer', '2023-01-15', 250000.00, 0.00, 48, 6, 55, '各办公室', 'in_use', '2024-05-31'),
('FA-008', 'ERP系统软件', 'software', '2022-06-01', 800000.00, 0.00, 120, 6, 54, '服务器', 'in_use', '2024-05-31'),
('FA-009', '办公家具-总经理室', 'furniture', '2020-03-01', 120000.00, 0.00, 96, 1, 1, '总经理办公室', 'in_use', '2024-05-31'),
('FA-010', '办公家具-各部门', 'furniture', '2020-03-01', 380000.00, 0.00, 96, 2, 4, '各部门办公室', 'in_use', '2024-05-31'),
('FA-011', '叉车-电动', 'equipment', '2021-09-15', 180000.00, 18000.00, 120, 5, 45, '仓库', 'in_use', '2024-05-31'),
('FA-012', '货架系统', 'equipment', '2020-06-01', 500000.00, 50000.00, 180, 5, 42, '华东仓库', 'in_use', '2024-05-31'),
('FA-013', '冷链设备', 'equipment', '2021-03-01', 350000.00, 35000.00, 120, 5, 44, '冷链仓库', 'in_use', '2024-05-31'),
('FA-014', '会议系统', 'equipment', '2022-01-15', 150000.00, 0.00, 72, 1, 3, '大会议室', 'in_use', '2024-05-31'),
('FA-015', '监控系统', 'equipment', '2020-06-01', 200000.00, 0.00, 96, 5, 42, '全仓库', 'in_use', '2024-05-31');

-- 折旧记录 (2024年1-5月)
INSERT INTO depreciation_log (asset_id, depreciation_date, depreciation_amount,
    before_accumulated, after_accumulated, before_net_value, after_net_value)
SELECT
    fa.id,
    CONCAT('2024-0', m.month, '-01'),
    fa.monthly_depreciation,
    fa.monthly_depreciation * (m.month - 1),
    fa.monthly_depreciation * m.month,
    fa.purchase_amount - fa.monthly_depreciation * (m.month - 1),
    fa.purchase_amount - fa.monthly_depreciation * m.month
FROM fixed_assets fa
CROSS JOIN (SELECT 1 AS month UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5) m;

-- 更新累计折旧
UPDATE fixed_assets fa
SET accumulated_depreciation = (SELECT COALESCE(SUM(depreciation_amount), 0) FROM depreciation_log WHERE asset_id = fa.id);


-- ============================================================
-- 6. BOM数据 (物料清单)
-- 关系: 成品 -> 原料/半成品 的组成关系
-- 例如: 坚果混合装(成品) <- 核桃仁(半成品) + 腰果(半成品) + 包装袋(原料)
-- ============================================================

INSERT INTO boms (parent_product_id, child_product_id, quantity, unit, scrap_rate, effective_date) VALUES
-- 坚果混合装(10)的BOM
(10, 47, 0.2, 'kg', 0.02, '2024-01-01'),  -- 坚果混合装 <- 红枣
(10, 9, 0.1, '袋', 0.01, '2024-01-01'),   -- 坚果混合装 <- 参考薯片(包装参考)
-- 巧克力礼盒(11)的BOM
(11, 41, 0.1, 'kg', 0.03, '2024-01-01'),  -- 巧克力礼盒 <- 蜂蜜(夹心)
-- 男士羽绒服(31)的BOM
(31, 33, 0.05, '条', 0.02, '2024-01-01'), -- 羽绒服 <- 真丝围巾(装饰)
(31, 36, 0.3, '件', 0.01, '2024-01-01'),  -- 羽绒服 <- 运动T恤(内衬)
-- 女士羊绒大衣(34)的BOM
(34, 33, 0.1, '条', 0.02, '2024-01-01'),  -- 大衣 <- 围巾(装饰)
(34, 32, 0.2, '条', 0.01, '2024-01-01'),  -- 大衣 <- 连衣裙(内衬)
-- 办公椅(22)的BOM
(22, 18, 0.5, '盒', 0.00, '2024-01-01'),  -- 办公椅 <- 中性笔(配件)
(22, 19, 0.2, '本', 0.00, '2024-01-01'),  -- 办公椅 <- 笔记本(配件)
-- 运动跑鞋(35)的BOM
(35, 36, 0.2, '件', 0.03, '2024-01-01'),  -- 跑鞋 <- 运动T恤(鞋面材料)
(35, 37, 0.15, '条', 0.02, '2024-01-01'); -- 跑鞋 <- 运动短裤(鞋面材料)


-- ============================================================
-- 7. 生产工单
-- ============================================================

INSERT INTO work_orders (order_no, product_id, bom_id, planned_quantity, completed_quantity,
    rejected_quantity, warehouse_id, start_date, due_date, completed_date, status, priority, released_by)
SELECT
    CONCAT('WO-', DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL seq.num DAY), '%Y%m%d'), '-', LPAD(seq.num, 3, '0')),
    b.parent_product_id,
    b.id,
    FLOOR(RAND() * 100) + 20,
    FLOOR(RAND() * 80) + 15,
    FLOOR(RAND() * 5),
    FLOOR(RAND() * 2) + 1,
    DATE_SUB(CURDATE(), INTERVAL seq.num DAY),
    DATE_ADD(DATE_SUB(CURDATE(), INTERVAL seq.num DAY), INTERVAL 7 DAY),
    DATE_ADD(DATE_SUB(CURDATE(), INTERVAL seq.num DAY), INTERVAL 5 DAY),
    IF(seq.num > 5, 'completed', IF(seq.num > 2, 'in_progress', 'released')),
    ELT(FLOOR(RAND() * 3) + 1, 'normal', 'high', 'normal'),
    42  -- 仓储总监史进
FROM boms b
CROSS JOIN (SELECT 0 AS num UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
             UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9
             UNION SELECT 10 UNION SELECT 11 UNION SELECT 12 UNION SELECT 13 UNION SELECT 14) seq
LIMIT 50;

-- 工单用料
INSERT INTO work_order_materials (work_order_id, product_id, required_qty, issued_qty, actual_consumed, unit, status)
SELECT
    wo.id,
    b.child_product_id,
    b.quantity * wo.planned_quantity,
    b.quantity * wo.completed_quantity * 1.1,
    b.quantity * wo.completed_quantity,
    b.unit,
    IF(wo.status = 'completed', 'completed', 'issued')
FROM work_orders wo
JOIN boms b ON wo.bom_id = b.id;


-- ============================================================
-- 8. 客服工单
-- ============================================================

INSERT INTO service_tickets (ticket_no, customer_id, order_id, product_id, ticket_type,
    priority, subject, description, status, assigned_to, resolution, resolved_at, satisfaction_score)
SELECT
    CONCAT('TK-', DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL seq.num DAY), '%Y%m%d'), '-', LPAD(seq.num, 3, '0')),
    FLOOR(RAND() * 30) + 1,
    FLOOR(RAND() * 249) + 1,
    FLOOR(RAND() * 50) + 1,
    ELT(FLOOR(RAND() * 5) + 1, 'complaint', 'return', 'inquiry', 'maintenance', 'other'),
    ELT(FLOOR(RAND() * 3) + 1, 'normal', 'high', 'low'),
    ELT(FLOOR(RAND() * 5) + 1,
        '产品质量问题', '物流延迟投诉', '退换货申请', '产品使用咨询', '维修申请'),
    '客户反馈的相关问题描述',
    IF(seq.num > 10, 'resolved', IF(seq.num > 5, 'processing', 'open')),
    FLOOR(RAND() * 10) + 45,
    IF(seq.num > 10, '已联系客户处理完成', NULL),
    IF(seq.num > 10, DATE_SUB(CURDATE(), INTERVAL seq.num - 5 DAY), NULL),
    IF(seq.num > 10, FLOOR(RAND() * 3) + 3, NULL)
FROM (SELECT 0 AS num UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
      UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9
      UNION SELECT 10 UNION SELECT 11 UNION SELECT 12 UNION SELECT 13 UNION SELECT 14
      UNION SELECT 15 UNION SELECT 16 UNION SELECT 17 UNION SELECT 18 UNION SELECT 19) seq;