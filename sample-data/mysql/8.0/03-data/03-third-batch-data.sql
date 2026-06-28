-- ============================================================
-- 第三批补充数据: 合同、汇率、审批流、KPI、质检标准、项目、序列号、寄售
-- ============================================================

USE erp_system;

-- 合同数据
INSERT INTO contracts (contract_no, contract_type, party_type, party_id, subject,
    total_amount, signed_date, start_date, end_date, payment_terms, status, prepared_by, approved_by)
SELECT
    CONCAT('CT-', DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL seq.num MONTH), '%Y%m'), '-', LPAD(seq.num, 3, '0')),
    ELT(FLOOR(RAND() * 3) + 1, 'sales', 'purchase', 'service'),
    ELT(FLOOR(RAND() * 2) + 1, 'customer', 'supplier'),
    FLOOR(RAND() * 30) + 1,
    ELT(FLOOR(RAND() * 5) + 1,
        '年度供货框架协议', '销售合同-电子产品', 'IT服务合同', '物流配送合同', '设备采购合同'),
    ROUND(100000 + RAND() * 900000, 2),
    DATE_SUB(CURDATE(), INTERVAL seq.num MONTH),
    DATE_SUB(CURDATE(), INTERVAL seq.num MONTH),
    DATE_ADD(DATE_SUB(CURDATE(), INTERVAL seq.num MONTH), INTERVAL 12 MONTH),
    '{"payment_method":"bank_transfer","credit_days":30,"milestones":[]}',
    IF(seq.num < 3, 'active', IF(seq.num < 8, 'completed', 'active')),
    FLOOR(RAND() * 10) + 1,
    FLOOR(RAND() * 5) + 1
FROM (SELECT 0 AS num UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
      UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9
      UNION SELECT 10 UNION SELECT 11 UNION SELECT 12) seq;

-- 合同里程碑
INSERT INTO contract_milestones (contract_id, milestone_name, milestone_type, planned_date, amount, status)
SELECT
    c.id,
    ELT(seq.num, '合同签订', '首付款', '中期验收', '尾款', '质保期满'),
    ELT(seq.num, 'delivery', 'payment', 'acceptance', 'payment', 'other'),
    DATE_ADD(c.start_date, INTERVAL seq.num * 2 MONTH),
    ROUND(c.total_amount * ELT(seq.num, 0.1, 0.3, 0.3, 0.25, 0.05), 2),
    IF(seq.num < 3, 'completed', IF(seq.num < 4, 'in_progress', 'pending'))
FROM contracts c
CROSS JOIN (SELECT 1 AS num UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5) seq
WHERE c.status = 'active';

-- 汇率数据 (近6个月)
INSERT INTO exchange_rates (from_currency, to_currency, rate_date, rate, rate_source)
SELECT 'USD', 'CNY', DATE_SUB(CURDATE(), INTERVAL seq.num DAY),
       7.15 + RAND() * 0.3, 'BOC'
FROM (SELECT 0 AS num UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
      UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9
      UNION SELECT 10 UNION SELECT 15 UNION SELECT 20 UNION SELECT 25 UNION SELECT 30
      UNION SELECT 40 UNION SELECT 50 UNION SELECT 60 UNION SELECT 70 UNION SELECT 80
      UNION SELECT 90 UNION SELECT 100 UNION SELECT 110 UNION SELECT 120
      UNION SELECT 130 UNION SELECT 140 UNION SELECT 150 UNION SELECT 160 UNION SELECT 170 UNION SELECT 180) seq
UNION ALL
SELECT 'EUR', 'CNY', DATE_SUB(CURDATE(), INTERVAL seq.num DAY),
       7.80 + RAND() * 0.3, 'BOC'
FROM (SELECT 0 AS num UNION SELECT 10 UNION SELECT 20 UNION SELECT 30
      UNION SELECT 60 UNION SELECT 90 UNION SELECT 120 UNION SELECT 150 UNION SELECT 180) seq
UNION ALL
SELECT 'JPY', 'CNY', DATE_SUB(CURDATE(), INTERVAL seq.num DAY),
       0.048 + RAND() * 0.003, 'BOC'
FROM (SELECT 0 AS num UNION SELECT 30 UNION SELECT 60 UNION SELECT 90
      UNION SELECT 120 UNION SELECT 150 UNION SELECT 180) seq;

-- 审批流定义
INSERT INTO approval_workflows (workflow_name, workflow_code, target_type, description) VALUES
('采购审批流程', 'PURCHASE_APPROVAL', 'purchase_requisition', '请购单->部门经理->采购总监->总经理(>10万)'),
('销售折扣审批', 'DISCOUNT_APPROVAL', 'sales_order', '折扣>10%需审批'),
('合同审批流程', 'CONTRACT_APPROVAL', 'contract', '合同->部门经理->法务->总经理'),
('请假审批', 'LEAVE_APPROVAL', 'leave_record', '请假->直属上级->部门经理'),
('报销审批', 'EXPENSE_APPROVAL', 'expense', '报销->直属上级->财务经理'),
('价格变更审批', 'PRICE_APPROVAL', 'price_change', '价格变更->部门经理->财务总监');

-- 审批节点
INSERT INTO approval_nodes (workflow_id, node_name, node_level, approver_type, approver_id, approval_mode, timeout_hours) VALUES
(1, '部门经理审批', 1, 'department_manager', NULL, 'single', 24),
(1, '采购总监审批', 2, 'position', 14, 'single', 24),
(1, '总经理审批(>10万)', 3, 'position', 1, 'single', 48),
(2, '销售经理审批', 1, 'department_manager', NULL, 'single', 24),
(3, '部门经理审批', 1, 'department_manager', NULL, 'single', 24),
(3, '法务审批', 2, 'role', 1, 'single', 48),
(3, '总经理审批', 3, 'position', 1, 'single', 48),
(4, '直属上级审批', 1, 'direct_manager', NULL, 'single', 24),
(4, '部门经理审批', 2, 'department_manager', NULL, 'single', 24),
(5, '直属上级审批', 1, 'direct_manager', NULL, 'single', 24),
(5, '财务经理审批', 2, 'position', 19, 'single', 24),
(6, '部门经理审批', 1, 'department_manager', NULL, 'single', 24),
(6, '财务总监审批', 2, 'position', 18, 'single', 48);

-- KPI指标
INSERT INTO kpi_indicators (name, indicator_type, unit, target_direction, target_value, weight, applicable_role_id) VALUES
('月销售额达成率', 'sales', '%', 'higher_better', 100, 0.30, 3),
('客户回款率', 'financial', '%', 'higher_better', 95, 0.15, 3),
('新客户开发数', 'sales', '个', 'higher_better', 5, 0.15, 3),
('采购成本节约率', 'financial', '%', 'higher_better', 5, 0.25, 4),
('供应商准时交货率', 'operational', '%', 'higher_better', 95, 0.15, 4),
('库存周转天数', 'operational', '天', 'lower_better', 30, 0.20, 8),
('盘点准确率', 'operational', '%', 'higher_better', 99, 0.15, 8),
('客户满意度', 'customer', '分', 'higher_better', 4.5, 0.20, 3),
('员工离职率', 'hr', '%', 'lower_better', 10, 0.20, 9),
('培训完成率', 'hr', '%', 'higher_better', 100, 0.15, 9);

-- 质检标准
INSERT INTO inspection_standards (product_id, standard_name, sampling_method, sample_size, aql_level) VALUES
(1, '蓝牙耳机检验标准', 'gb2828', 20, 1.0),
(4, '机械键盘检验标准', 'gb2828', 10, 1.0),
(6, '显示器检验标准', 'fixed', 5, 0.65),
(9, '食品包装检验标准', 'gb2828', 30, 2.5),
(15, '冷冻食品检验标准', 'gb2828', 20, 1.5),
(20, '打印机检验标准', 'fixed', 3, 0.65),
(29, '服装检验标准', 'gb2828', 20, 2.5),
(35, '运动鞋检验标准', 'gb2828', 15, 1.5);

-- 质检报告
INSERT INTO inspection_reports (report_no, inspection_type, reference_type, reference_id,
    product_id, batch_id, standard_id, sample_size, inspected_qty, qualified_qty, defective_qty,
    inspection_result, inspector_id, inspection_date, status)
SELECT
    CONCAT('QC-', DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL seq.num DAY), '%Y%m%d'), '-', LPAD(seq.num, 3, '0')),
    'IQC',
    'purchase_receipt',
    FLOOR(RAND() * 50) + 1,
    FLOOR(RAND() * 50) + 1,
    FLOOR(RAND() * 100) + 1,
    FLOOR(RAND() * 8) + 1,
    FLOOR(10 + RAND() * 30),
    FLOOR(10 + RAND() * 30),
    FLOOR(10 + RAND() * 25),
    FLOOR(RAND() * 5),
    IF(RAND() > 0.15, 'qualified', IF(RAND() > 0.5, 'conditionally_accepted', 'rejected')),
    FLOOR(RAND() * 10) + 51,
    DATE_SUB(CURDATE(), INTERVAL seq.num DAY),
    'completed'
FROM (SELECT 0 AS num UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
      UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9
      UNION SELECT 10 UNION SELECT 11 UNION SELECT 12 UNION SELECT 13 UNION SELECT 14
      UNION SELECT 15 UNION SELECT 16 UNION SELECT 17 UNION SELECT 18 UNION SELECT 19
      UNION SELECT 20 UNION SELECT 21 UNION SELECT 22 UNION SELECT 23 UNION SELECT 24) seq;

-- 项目数据
INSERT INTO projects (project_no, name, project_type, department_id, manager_id,
    budget, start_date, planned_end_date, actual_end_date, status, priority)
VALUES
('PRJ-2024-001', 'ERP系统升级项目', 'rd', 6, 54, 500000.00, '2024-01-15', '2024-06-30', '2024-06-15', 'completed', 'critical'),
('PRJ-2024-002', '华东仓扩建项目', 'engineering', 5, 42, 2000000.00, '2024-03-01', '2024-09-30', NULL, 'in_progress', 'high'),
('PRJ-2024-003', '618大促营销活动', 'marketing', 2, 4, 300000.00, '2024-05-01', '2024-06-30', '2024-06-25', 'completed', 'high'),
('PRJ-2024-004', '智能仓储系统研发', 'rd', 6, 54, 800000.00, '2024-04-01', '2024-12-31', NULL, 'in_progress', 'critical'),
('PRJ-2024-005', '华南市场拓展', 'marketing', 9, 20, 400000.00, '2024-06-01', '2024-12-31', NULL, 'in_progress', 'high'),
('PRJ-2024-006', '员工培训体系建设', 'internal', 7, 62, 150000.00, '2024-02-01', '2024-08-31', NULL, 'in_progress', 'normal'),
('PRJ-2024-007', '冷链物流升级', 'engineering', 5, 42, 600000.00, '2024-07-01', '2025-03-31', NULL, 'planning', 'high'),
('PRJ-2024-008', '数据中台建设', 'rd', 6, 55, 1200000.00, '2024-08-01', '2025-06-30', NULL, 'planning', 'critical');

-- 项目成本
INSERT INTO project_costs (project_id, cost_type, cost_date, amount, description)
SELECT
    p.id,
    ELT(FLOOR(RAND() * 6) + 1, 'labor', 'material', 'equipment', 'travel', 'outsource', 'other'),
    DATE_ADD(p.start_date, INTERVAL FLOOR(RAND() * DATEDIFF(COALESCE(p.actual_end_date, CURDATE()), p.start_date)) DAY),
    ROUND(5000 + RAND() * 50000, 2),
    CONCAT('项目成本-', p.name)
FROM projects p
CROSS JOIN (SELECT 1 AS num UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
             UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) seq
WHERE p.status != 'planning';

-- 序列号 (高价值电子产品)
INSERT INTO serial_numbers (product_id, batch_id, serial_no, status, warehouse_id, warranty_start, warranty_end)
SELECT
    p.id,
    (SELECT id FROM product_batches WHERE product_id = p.id LIMIT 1),
    CONCAT('SN-', p.sku, '-', LPAD(seq.num, 5, '0')),
    IF(RAND() > 0.3, 'in_stock', IF(RAND() > 0.5, 'sold', 'in_stock')),
    FLOOR(RAND() * 2) + 1,
    DATE_SUB(CURDATE(), INTERVAL FLOOR(RAND() * 365) DAY),
    DATE_ADD(CURDATE(), INTERVAL FLOOR(RAND() * 365) + 180 DAY)
FROM products p
CROSS JOIN (SELECT 1 AS num UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
             UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) seq
WHERE p.id IN (1, 4, 5, 6, 7, 8, 20, 21, 38, 39)
  AND p.status = 'active';

-- 寄售库存
INSERT INTO consignment_inventory (product_id, batch_id, customer_id, consigned_qty, consumed_qty, unit_price, consigned_date, status)
SELECT
    FLOOR(RAND() * 20) + 1,
    (SELECT id FROM product_batches WHERE product_id = FLOOR(RAND() * 20) + 1 LIMIT 1),
    FLOOR(RAND() * 10) + 1,
    FLOOR(50 + RAND() * 200),
    FLOOR(RAND() * 50),
    (SELECT retail_price * 0.8 FROM products WHERE id = FLOOR(RAND() * 20) + 1),
    DATE_SUB(CURDATE(), INTERVAL FLOOR(RAND() * 90) DAY),
    'active'
FROM (SELECT 1 AS num UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
      UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) seq;

-- 价格变更历史
INSERT INTO price_change_logs (product_id, price_type, old_price, new_price, change_reason, effective_date, changed_by)
SELECT
    p.id,
    'retail',
    p.retail_price * (0.9 + RAND() * 0.1),
    p.retail_price,
    ELT(FLOOR(RAND() * 4) + 1, '市场调价', '成本上涨', '促销结束恢复原价', '季节性调价'),
    DATE_SUB(CURDATE(), INTERVAL FLOOR(RAND() * 180) DAY),
    FLOOR(RAND() * 10) + 26
FROM products p
WHERE p.id <= 20;

-- 绩效考核
INSERT INTO performance_reviews (review_no, employee_id, reviewer_id, review_period, review_type,
    performance_score, competency_score, attitude_score, attendance_score,
    self_assessment, reviewer_comment, status)
SELECT
    CONCAT('PR-', DATE_FORMAT(CURDATE(), '%Y%m'), '-', LPAD(e.id, 3, '0')),
    e.id,
    COALESCE(e.manager_id, 1),
    '2024-Q2',
    'quarterly',
    ROUND(60 + RAND() * 40, 2),
    ROUND(60 + RAND() * 40, 2),
    ROUND(60 + RAND() * 40, 2),
    ROUND(70 + RAND() * 30, 2),
    '本季度工作完成情况良好，达到了预期目标。',
    '继续保持，注意提升团队协作能力。',
    'confirmed'
FROM employees e
WHERE e.status IN ('active', 'probation')
LIMIT 30;

-- AR账龄快照
INSERT INTO ar_aging_snapshots (snapshot_date, customer_id, order_id, invoice_amount, paid_amount, due_date)
SELECT
    CURDATE(),
    so.customer_id,
    so.id,
    so.total_amount,
    so.paid_amount,
    DATE_ADD(so.order_date, INTERVAL c.credit_days DAY)
FROM sales_orders so
JOIN customers c ON so.customer_id = c.id
WHERE so.status IN ('confirmed', 'delivering', 'delivered')
  AND so.total_amount > so.paid_amount;

-- AP账龄快照
INSERT INTO ap_aging_snapshots (snapshot_date, supplier_id, order_id, invoice_amount, paid_amount, due_date)
SELECT
    CURDATE(),
    po.supplier_id,
    po.id,
    po.total_amount,
    po.paid_amount,
    DATE_ADD(po.order_date, INTERVAL 30 DAY)
FROM purchase_orders po
WHERE po.status IN ('ordered', 'partially_received', 'received')
  AND po.total_amount > po.paid_amount;