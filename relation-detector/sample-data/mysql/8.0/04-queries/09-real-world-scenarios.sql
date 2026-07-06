-- ============================================================
-- ERP系统真实业务场景SQL查询 - 第九批
-- 覆盖: Procure-to-Pay全链路、Order-to-Cash全链路、
--       产品真实利润、员工人效、库存持有成本、资金周转周期、
--       信用风险监控、批号全链路追溯、毛利瀑布、预算滚动预测、
--       供应商集中度风险、月度关账核对、需求预测准确率、
--       仓库库容利用率、提成核对、价格弹性分析
-- ============================================================

USE erp_system;


-- ============================================================
-- Q97: Procure-to-Pay 全链路追踪 - 从请购到付款
-- 语法: 多表LEFT JOIN + 窗口函数 + 日期计算 + 条件聚合
-- 业务: 采购总监追踪每笔采购从请购到付款的完整周期
-- 统计原理: 各环节耗时 = 下一环节日期 - 当前环节日期
--          总周期 = 付款日期 - 请购日期
-- ============================================================

WITH pr_chain AS (
    SELECT
        pr.id AS pr_id,
        pr.requisition_no,
        pr.requisition_date,
        pr.status AS pr_status,
        pr.total_amount AS pr_amount,
        d.name AS dept_name,
        req.name AS requester_name
    FROM purchase_requisitions pr
    JOIN departments d ON pr.department_id = d.id
    JOIN employees req ON pr.requester_id = req.id
),
po_chain AS (
    SELECT
        po.id AS po_id,
        po.order_no,
        po.requisition_id,
        po.order_date,
        po.expected_delivery_date,
        po.actual_delivery_date,
        po.status AS po_status,
        po.total_amount AS po_amount,
        po.paid_amount,
        s.name AS supplier_name,
        pur.name AS purchaser_name,
        po.payment_terms
    FROM purchase_orders po
    JOIN suppliers s ON po.supplier_id = s.id
    JOIN employees pur ON po.purchaser_id = pur.id
),
receipt_chain AS (
    SELECT
        prec.order_id,
        MIN(prec.receipt_date) AS first_receipt_date,
        MAX(prec.receipt_date) AS last_receipt_date,
        SUM(prec.total_qty) AS total_received_qty,
        SUM(prec.total_amount) AS total_received_amount,
        COUNT(DISTINCT prec.id) AS receipt_count,
        GROUP_CONCAT(DISTINCT prec.receipt_no ORDER BY prec.receipt_date SEPARATOR ', ') AS receipt_nos,
        MAX(prec.status) AS receipt_status
    FROM purchase_receipts prec
    GROUP BY prec.order_id
),
payment_chain AS (
    SELECT
        cj.reference_type,
        cj.reference_id,
        MIN(cj.journal_date) AS first_payment_date,
        MAX(cj.journal_date) AS last_payment_date,
        SUM(cj.amount) AS total_paid,
        COUNT(DISTINCT cj.id) AS payment_count,
        GROUP_CONCAT(DISTINCT cj.journal_no ORDER BY cj.journal_date SEPARATOR ', ') AS payment_nos
    FROM cashier_journals cj
    WHERE cj.reference_type = 'purchase_order'
    GROUP BY cj.reference_type, cj.reference_id
)
SELECT
    pr.requisition_no,
    pr.requisition_date,
    po.order_no,
    po.order_date,
    po.supplier_name,
    po.purchaser_name,
    pr.dept_name,
    po.po_amount,
    COALESCE(rc.total_received_amount, 0) AS received_amount,
    COALESCE(pc.total_paid, 0) AS paid_amount,
    po.po_amount - COALESCE(pc.total_paid, 0) AS unpaid_amount,
    DATEDIFF(po.order_date, pr.requisition_date) AS pr_to_po_days,
    DATEDIFF(COALESCE(rc.first_receipt_date, CURDATE()), po.order_date) AS po_to_receipt_days,
    DATEDIFF(COALESCE(pc.first_payment_date, CURDATE()), COALESCE(rc.first_receipt_date, po.order_date)) AS receipt_to_pay_days,
    DATEDIFF(COALESCE(pc.last_payment_date, CURDATE()), pr.requisition_date) AS total_cycle_days,
    po.expected_delivery_date,
    po.actual_delivery_date,
    CASE
        WHEN po.actual_delivery_date > po.expected_delivery_date
        THEN DATEDIFF(po.actual_delivery_date, po.expected_delivery_date)
        ELSE 0
    END AS delivery_delay_days,
    rc.receipt_nos,
    COALESCE(pc.payment_nos, '未付款') AS payment_nos,
    po.po_status,
    CASE
        WHEN po.paid_amount >= po.po_amount THEN '已结清'
        WHEN po.paid_amount > 0 THEN '部分付款'
        WHEN rc.total_received_amount > 0 THEN '已收货待付款'
        WHEN po.order_date IS NOT NULL THEN '已下单'
        ELSE '待处理'
    END AS payment_status,
    -- 各环节耗时占比
    ROUND(DATEDIFF(po.order_date, pr.requisition_date) * 100.0
        / NULLIF(DATEDIFF(COALESCE(pc.last_payment_date, CURDATE()), pr.requisition_date), 0), 1) AS pr_to_po_pct,
    ROUND(DATEDIFF(COALESCE(rc.first_receipt_date, CURDATE()), po.order_date) * 100.0
        / NULLIF(DATEDIFF(COALESCE(pc.last_payment_date, CURDATE()), pr.requisition_date), 0), 1) AS po_to_receipt_pct,
    ROUND(DATEDIFF(COALESCE(pc.first_payment_date, CURDATE()), COALESCE(rc.first_receipt_date, po.order_date)) * 100.0
        / NULLIF(DATEDIFF(COALESCE(pc.last_payment_date, CURDATE()), pr.requisition_date), 0), 1) AS receipt_to_pay_pct
FROM pr_chain pr
LEFT JOIN po_chain po ON pr.pr_id = po.requisition_id
LEFT JOIN receipt_chain rc ON po.po_id = rc.order_id
LEFT JOIN payment_chain pc ON pc.reference_type = 'purchase_order' AND pc.reference_id = po.po_id
WHERE pr.requisition_date >= DATE_SUB(CURDATE(), INTERVAL 6 MONTH)
ORDER BY pr.requisition_date DESC
LIMIT 100;


-- ============================================================
-- Q98: Order-to-Cash 全链路 - 从下单到回款
-- 语法: 多表LEFT JOIN + 窗口函数 + 账龄分桶
-- 业务: 销售总监/财务经理追踪每笔订单的回款进度
-- 统计原理: 回款周期 = 最后回款日 - 下单日
--          回款率 = 已收金额 / 订单金额
-- ============================================================

SELECT
    so.order_no,
    so.order_date,
    c.name AS customer_name,
    c.membership_level,
    c.credit_days,
    e.name AS salesperson_name,
    w.name AS warehouse_name,
    so.total_amount,
    so.discount_amount,
    so.paid_amount,
    so.total_amount - so.paid_amount AS outstanding,
    ROUND(so.paid_amount * 100.0 / NULLIF(so.total_amount, 0), 1) AS collection_rate_pct,
    -- 发货信息
    sh.shipment_no,
    sh.carrier,
    sh.tracking_no,
    sh.status AS shipment_status,
    sh.delivered_at,
    DATEDIFF(COALESCE(sh.delivered_at, CURDATE()), so.order_date) AS order_to_deliver_days,
    -- 回款信息
    DATEDIFF(CURDATE(), so.order_date) AS aging_days,
    DATE_ADD(so.order_date, INTERVAL c.credit_days DAY) AS due_date,
    CASE
        WHEN so.paid_amount >= so.total_amount THEN '已结清'
        WHEN CURDATE() > DATE_ADD(so.order_date, INTERVAL c.credit_days DAY) THEN '已逾期'
        WHEN CURDATE() > DATE_ADD(so.order_date, INTERVAL c.credit_days - 7 DAY) THEN '即将到期'
        ELSE '正常账期'
    END AS collection_status,
    CASE
        WHEN so.paid_amount >= so.total_amount THEN 0
        WHEN CURDATE() <= DATE_ADD(so.order_date, INTERVAL c.credit_days DAY) THEN 0
        ELSE DATEDIFF(CURDATE(), DATE_ADD(so.order_date, INTERVAL c.credit_days DAY))
    END AS overdue_days,
    -- 累计回款金额
    SUM(so.paid_amount) OVER (PARTITION BY so.customer_id ORDER BY so.order_date
        ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS customer_cumulative_paid,
    -- 是否有退货
    (SELECT COUNT(*) FROM sales_returns sr WHERE sr.order_id = so.id) AS return_count,
    COALESCE((SELECT SUM(sr.refund_amount) FROM sales_returns sr WHERE sr.order_id = so.id), 0) AS refund_amount,
    so.payment_method,
    -- 回款效率评分
    CASE
        WHEN so.paid_amount >= so.total_amount AND DATEDIFF(COALESCE(sh.delivered_at, so.order_date), so.order_date) <= c.credit_days + 7
        THEN '优秀'
        WHEN so.paid_amount >= so.total_amount * 0.8 THEN '良好'
        WHEN so.paid_amount >= so.total_amount * 0.5 THEN '一般'
        ELSE '差'
    END AS collection_efficiency
FROM sales_orders so
JOIN customers c ON so.customer_id = c.id
JOIN employees e ON so.salesperson_id = e.id
JOIN warehouses w ON so.warehouse_id = w.id
LEFT JOIN shipments sh ON so.id = sh.order_id
WHERE so.status IN ('confirmed', 'delivering', 'delivered')
  AND so.total_amount > so.paid_amount
ORDER BY overdue_days DESC, outstanding DESC
LIMIT 100;


-- ============================================================
-- Q99: 产品真实利润分析 - 收入-成本-费用全口径
-- 语法: 多CTE + 多表JOIN + 条件聚合 + 窗口函数
-- 业务: 财务/运营分析每个SKU的真实利润
-- 统计原理: 毛利 = 销售收入 - 销售成本(进货价)
--          净利 = 毛利 - 退货损失 - 报损 - 促销折扣分摊
--          利润率 = 净利 / 销售收入
-- ============================================================

WITH sales_stats AS (
    SELECT
        soi.product_id,
        SUM(soi.quantity) AS total_sold_qty,
        SUM(soi.amount) AS total_revenue,
        SUM(soi.quantity * p.purchase_price) AS total_cost,
        SUM(soi.discount) AS total_line_discount,
        COUNT(DISTINCT so.id) AS order_count,
        COUNT(DISTINCT so.customer_id) AS customer_count
    FROM sales_order_items soi
    JOIN sales_orders so ON soi.order_id = so.id
    JOIN products p ON soi.product_id = p.id
    WHERE so.status NOT IN ('draft', 'cancelled')
      AND so.order_date >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH)
    GROUP BY soi.product_id
),
return_stats AS (
    SELECT
        sri.product_id,
        SUM(sri.return_qty) AS total_return_qty,
        SUM(sri.amount) AS total_return_amount,
        COUNT(DISTINCT sr.id) AS return_count
    FROM sales_return_items sri
    JOIN sales_returns sr ON sri.return_id = sr.id
    WHERE sr.return_date >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH)
      AND sr.status NOT IN ('rejected')
    GROUP BY sri.product_id
),
damage_stats AS (
    SELECT
        dri.product_id,
        SUM(dri.quantity) AS total_scrapped_qty,
        SUM(dri.loss_amount) AS total_loss_amount
    FROM damage_report_items dri
    JOIN damage_reports dr ON dri.report_id = dr.id
    WHERE dr.report_date >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH)
      AND dr.status IN ('approved', 'executed')
    GROUP BY dri.product_id
),
promo_cost AS (
    SELECT
        pp.product_id,
        SUM(pu.discount_applied) AS total_promo_discount,
        COUNT(DISTINCT pu.promotion_id) AS promo_count
    FROM promotion_usages pu
    JOIN promotion_products pp ON pu.promotion_id = pp.promotion_id
    JOIN sales_orders so ON pu.order_id = so.id
    WHERE so.order_date >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH)
    GROUP BY pp.product_id
)
SELECT
    p.sku,
    p.name AS product_name,
    pc.name AS category_name,
    p.retail_price,
    p.purchase_price,
    ROUND(p.retail_price - p.purchase_price, 2) AS unit_gross_margin,
    ROUND((p.retail_price - p.purchase_price) * 100.0 / NULLIF(p.retail_price, 0), 1) AS unit_margin_pct,
    COALESCE(ss.total_sold_qty, 0) AS sold_qty,
    COALESCE(ss.total_revenue, 0) AS revenue,
    COALESCE(ss.total_cost, 0) AS cost_of_goods,
    COALESCE(ss.total_revenue, 0) - COALESCE(ss.total_cost, 0) AS gross_profit,
    COALESCE(ss.total_line_discount, 0) AS line_discount,
    COALESCE(rs.total_return_amount, 0) AS return_loss,
    COALESCE(ds.total_loss_amount, 0) AS damage_loss,
    COALESCE(pc2.total_promo_discount, 0) AS promo_discount_allocated,
    -- 净利 = 毛利 - 退货 - 报损 - 促销
    (COALESCE(ss.total_revenue, 0) - COALESCE(ss.total_cost, 0))
        - COALESCE(rs.total_return_amount, 0)
        - COALESCE(ds.total_loss_amount, 0)
        - COALESCE(pc2.total_promo_discount, 0) AS net_profit,
    ROUND(
        ((COALESCE(ss.total_revenue, 0) - COALESCE(ss.total_cost, 0))
            - COALESCE(rs.total_return_amount, 0)
            - COALESCE(ds.total_loss_amount, 0)
            - COALESCE(pc2.total_promo_discount, 0))
        * 100.0 / NULLIF(COALESCE(ss.total_revenue, 0), 0), 1
    ) AS net_margin_pct,
    COALESCE(ss.customer_count, 0) AS customer_count,
    COALESCE(ss.order_count, 0) AS order_count,
    COALESCE(rs.return_count, 0) AS return_count,
    ROUND(COALESCE(rs.total_return_qty, 0) * 100.0 / NULLIF(COALESCE(ss.total_sold_qty, 0), 0), 1) AS return_rate_pct,
    -- 库存周转
    ROUND(COALESCE(ss.total_cost, 0) / NULLIF(
        (SELECT AVG(quantity) FROM inventory WHERE product_id = p.id), 0), 1
    ) AS inventory_turnover,
    -- 利润排名
    RANK() OVER (ORDER BY
        (COALESCE(ss.total_revenue, 0) - COALESCE(ss.total_cost, 0))
        - COALESCE(rs.total_return_amount, 0)
        - COALESCE(ds.total_loss_amount, 0)
        - COALESCE(pc2.total_promo_discount, 0) DESC
    ) AS profit_rank
FROM products p
JOIN product_categories pc ON p.category_id = pc.id
LEFT JOIN sales_stats ss ON p.id = ss.product_id
LEFT JOIN return_stats rs ON p.id = rs.product_id
LEFT JOIN damage_stats ds ON p.id = ds.product_id
LEFT JOIN promo_cost pc2 ON p.id = pc2.product_id
WHERE p.status = 'active'
HAVING revenue > 0
ORDER BY net_profit DESC
LIMIT 50;


-- ============================================================
-- Q100: 员工人效分析 - 人均产出+人均利润+订单处理效率
-- 语法: 多CTE + 多维度聚合 + 窗口排名
-- 业务: HR/运营分析每个员工的人效贡献
-- 统计原理: 人均销售额 = 销售总额 / 员工数
--          人均利润 = (销售额-成本) / 员工数
--          订单处理效率 = 订单数 / 工作日天数
-- ============================================================

WITH salesperson_stats AS (
    SELECT
        so.salesperson_id,
        COUNT(DISTINCT so.id) AS order_count,
        SUM(so.total_amount) AS total_sales,
        SUM(soi.quantity) AS total_qty_sold,
        COUNT(DISTINCT so.customer_id) AS unique_customers,
        SUM(so.paid_amount) AS total_collected,
        AVG(so.total_amount) AS avg_order_value,
        -- 订单成本
        SUM(soi.quantity * p.purchase_price) AS total_cost,
        -- 退货
        COUNT(DISTINCT sr.id) AS return_order_count,
        COALESCE(SUM(sr.refund_amount), 0) AS total_refund
    FROM sales_orders so
    JOIN sales_order_items soi ON so.id = soi.order_id
    JOIN products p ON soi.product_id = p.id
    LEFT JOIN sales_returns sr ON so.id = sr.order_id AND sr.status != 'rejected'
    WHERE so.order_date >= DATE_SUB(CURDATE(), INTERVAL 3 MONTH)
      AND so.status NOT IN ('draft', 'cancelled')
    GROUP BY so.salesperson_id
),
purchaser_stats AS (
    SELECT
        po.purchaser_id,
        COUNT(DISTINCT po.id) AS po_count,
        SUM(po.total_amount) AS total_po_amount,
        COUNT(DISTINCT po.supplier_id) AS unique_suppliers,
        AVG(DATEDIFF(COALESCE(po.actual_delivery_date, CURDATE()), po.order_date)) AS avg_delivery_cycle
    FROM purchase_orders po
    WHERE po.order_date >= DATE_SUB(CURDATE(), INTERVAL 3 MONTH)
    GROUP BY po.purchaser_id
),
attendance_stats AS (
    SELECT
        employee_id,
        COUNT(DISTINCT attendance_date) AS actual_workdays,
        SUM(CASE WHEN status = 'late' THEN late_minutes ELSE 0 END) AS total_late_minutes,
        COUNT(CASE WHEN status = 'absent' THEN 1 END) AS absent_days
    FROM attendance
    WHERE attendance_date >= DATE_SUB(CURDATE(), INTERVAL 3 MONTH)
    GROUP BY employee_id
)
SELECT
    e.employee_no,
    e.name AS employee_name,
    d.name AS department_name,
    p.name AS position_name,
    COALESCE(sps.order_count, 0) AS sales_order_count,
    COALESCE(sps.total_sales, 0) AS total_sales_amount,
    COALESCE(sps.total_sales, 0) - COALESCE(sps.total_cost, 0) AS gross_profit_contributed,
    ROUND(COALESCE(sps.total_collected, 0), 2) AS total_collected,
    ROUND(COALESCE(sps.total_collected, 0) * 100.0 / NULLIF(COALESCE(sps.total_sales, 0), 0), 1) AS collection_rate,
    COALESCE(sps.unique_customers, 0) AS unique_customers,
    ROUND(COALESCE(sps.avg_order_value, 0), 2) AS avg_order_value,
    ROUND(COALESCE(sps.total_sales, 0) / NULLIF(COALESCE(ats.actual_workdays, 1), 0), 2) AS daily_sales_avg,
    -- 采购指标
    COALESCE(pus.po_count, 0) AS purchase_order_count,
    COALESCE(pus.total_po_amount, 0) AS total_po_amount,
    COALESCE(pus.unique_suppliers, 0) AS managed_suppliers,
    -- 考勤
    COALESCE(ats.actual_workdays, 0) AS actual_workdays,
    COALESCE(ats.absent_days, 0) AS absent_days,
    ROUND(COALESCE(ats.total_late_minutes, 0) / 60.0, 1) AS total_late_hours,
    -- 退货表现
    COALESCE(sps.return_order_count, 0) AS return_count,
    ROUND(COALESCE(sps.total_refund, 0) * 100.0 / NULLIF(COALESCE(sps.total_sales, 0), 0), 1) AS return_rate_pct,
    -- 综合评分
    ROUND(
        (COALESCE(sps.total_sales, 0) / NULLIF((SELECT AVG(total_sales) FROM salesperson_stats), 0)) * 40
        + (COALESCE(sps.total_collected, 0) * 100.0 / NULLIF(COALESCE(sps.total_sales, 0), 0)) * 30
        + (100 - COALESCE(sps.total_refund, 0) * 100.0 / NULLIF(COALESCE(sps.total_sales, 0), 0)) * 30
    , 1) AS composite_score,
    RANK() OVER (ORDER BY COALESCE(sps.total_sales, 0) DESC) AS sales_rank,
    RANK() OVER (ORDER BY
        (COALESCE(sps.total_sales, 0) - COALESCE(sps.total_cost, 0)) DESC) AS profit_rank
FROM employees e
JOIN departments d ON e.department_id = d.id
JOIN positions p ON e.position_id = p.id
LEFT JOIN salesperson_stats sps ON e.id = sps.salesperson_id
LEFT JOIN purchaser_stats pus ON e.id = pus.purchaser_id
LEFT JOIN attendance_stats ats ON e.id = ats.employee_id
WHERE e.status IN ('active', 'probation')
  AND (sps.total_sales > 0 OR pus.total_po_amount > 0)
ORDER BY composite_score DESC;


-- ============================================================
-- Q101: 库存持有成本分析 - 仓储+资金占用+过期+保险
-- 语法: 多表JOIN + 条件聚合 + 财务估算
-- 业务: 财务/供应链分析库存的真实持有成本
-- 统计原理: 持有成本 = 资金占用成本(年化5%) + 仓储成本 +
--           过期损失 + 保险成本 + 人工成本
--          库存持有成本率 = 持有成本 / 库存价值
-- ============================================================

SELECT
    w.name AS warehouse_name,
    w.type AS warehouse_type,
    w.capacity_m3,
    COUNT(DISTINCT i.product_id) AS product_count,
    SUM(i.quantity) AS total_units,
    ROUND(SUM(i.quantity * p.purchase_price), 2) AS total_inventory_value,
    ROUND(SUM(i.quantity * p.retail_price), 2) AS total_retail_value,
    -- 资金占用成本 (年化5%，按当前库存价值)
    ROUND(SUM(i.quantity * p.purchase_price) * 0.05 / 12, 2) AS monthly_capital_cost,
    -- 仓储成本估算 (每立方米每月20元)
    ROUND(w.capacity_m3 * 20, 2) AS monthly_storage_cost,
    -- 过期损失 (临期60天内库存按成本价30%计提)
    ROUND(SUM(
        CASE WHEN pb.expiry_date <= DATE_ADD(CURDATE(), INTERVAL 60 DAY)
             AND pb.expiry_date > CURDATE()
        THEN i.quantity * p.purchase_price * 0.30
        WHEN pb.expiry_date <= CURDATE()
        THEN i.quantity * p.purchase_price
        ELSE 0
        END
    ), 2) AS expiry_risk_cost,
    -- 保险成本 (库存价值的0.1%)
    ROUND(SUM(i.quantity * p.purchase_price) * 0.001, 2) AS monthly_insurance_cost,
    -- 人工成本 (每个仓库管理员月均8000)
    ROUND(COUNT(DISTINCT e.id) * 8000.0 / NULLIF(COUNT(DISTINCT w.id), 0), 2) AS allocated_labor_cost,
    -- 持有成本合计
    ROUND(
        SUM(i.quantity * p.purchase_price) * 0.05 / 12
        + w.capacity_m3 * 20
        + SUM(CASE WHEN pb.expiry_date <= DATE_ADD(CURDATE(), INTERVAL 60 DAY) AND pb.expiry_date > CURDATE()
              THEN i.quantity * p.purchase_price * 0.30
              WHEN pb.expiry_date <= CURDATE() THEN i.quantity * p.purchase_price
              ELSE 0 END)
        + SUM(i.quantity * p.purchase_price) * 0.001
        + COUNT(DISTINCT e.id) * 8000.0 / NULLIF(COUNT(DISTINCT w.id), 0)
    , 2) AS total_monthly_holding_cost,
    -- 持有成本率
    ROUND(
        (SUM(i.quantity * p.purchase_price) * 0.05 / 12
        + w.capacity_m3 * 20
        + SUM(CASE WHEN pb.expiry_date <= DATE_ADD(CURDATE(), INTERVAL 60 DAY) AND pb.expiry_date > CURDATE()
              THEN i.quantity * p.purchase_price * 0.30
              WHEN pb.expiry_date <= CURDATE() THEN i.quantity * p.purchase_price
              ELSE 0 END)
        + SUM(i.quantity * p.purchase_price) * 0.001
        + COUNT(DISTINCT e.id) * 8000.0 / NULLIF(COUNT(DISTINCT w.id), 0))
        * 100.0 / NULLIF(SUM(i.quantity * p.purchase_price), 0), 2
    ) AS holding_cost_rate_pct,
    -- 库存周转天数
    ROUND(
        SUM(i.quantity * p.purchase_price)
        / NULLIF((
            SELECT SUM(soi2.quantity * p2.purchase_price)
            FROM sales_order_items soi2
            JOIN sales_orders so2 ON soi2.order_id = so2.id
            JOIN products p2 ON soi2.product_id = p2.id
            WHERE so2.warehouse_id = w.id
              AND so2.order_date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
              AND so2.status NOT IN ('draft', 'cancelled')
        ), 0) * 30, 1
    ) AS inventory_days,
    -- 库容利用率
    ROUND(SUM(i.quantity * p.volume_m3) * 100.0 / NULLIF(w.capacity_m3, 0), 1) AS volume_utilization_pct
FROM warehouses w
JOIN inventory i ON w.id = i.warehouse_id
JOIN products p ON i.product_id = p.id
LEFT JOIN product_batches pb ON i.batch_id = pb.id
LEFT JOIN employees e ON w.manager_id = e.id OR e.department_id = 5
WHERE w.status = 'active'
GROUP BY w.id, w.name, w.type, w.capacity_m3
ORDER BY total_monthly_holding_cost DESC;


-- ============================================================
-- Q102: 资金周转周期分析 - DIO + DSO - DPO
-- 语法: 多CTE + 财务公式 + 月度趋势
-- 业务: CFO/财务总监监控资金周转效率
-- 统计原理: DIO(库存周转天数) = 平均库存/日均销售成本
--          DSO(应收周转天数) = 应收余额/日均销售额
--          DPO(应付周转天数) = 应付余额/日均采购额
--          现金周期 = DIO + DSO - DPO
-- ============================================================

WITH monthly_metrics AS (
    SELECT
        DATE_FORMAT(order_date, '%Y-%m') AS month,
        -- 日均销售额
        SUM(total_amount) / DAY(LAST_DAY(MAX(order_date))) AS daily_sales,
        SUM(total_amount) AS monthly_sales
    FROM sales_orders
    WHERE order_date >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH)
      AND status NOT IN ('draft', 'cancelled')
    GROUP BY DATE_FORMAT(order_date, '%Y-%m')
),
monthly_purchase AS (
    SELECT
        DATE_FORMAT(order_date, '%Y-%m') AS month,
        SUM(total_amount) / DAY(LAST_DAY(MAX(order_date))) AS daily_purchase,
        SUM(total_amount) AS monthly_purchase
    FROM purchase_orders
    WHERE order_date >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH)
      AND status NOT IN ('draft', 'cancelled')
    GROUP BY DATE_FORMAT(order_date, '%Y-%m')
),
monthly_inventory AS (
    SELECT
        DATE_FORMAT(v_calc_date, '%Y-%m') AS month,
        AVG(daily_inventory_value) AS avg_inventory_value
    FROM (
        SELECT
            DATE_SUB(CURDATE(), INTERVAL seq.num DAY) AS v_calc_date,
            (SELECT SUM(i.quantity * p.purchase_price)
             FROM inventory i JOIN products p ON i.product_id = p.id) AS daily_inventory_value
        FROM (SELECT 0 AS num UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
              UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9
              UNION SELECT 10 UNION SELECT 15 UNION SELECT 20 UNION SELECT 25 UNION SELECT 30
              UNION SELECT 40 UNION SELECT 50 UNION SELECT 60 UNION SELECT 70 UNION SELECT 80
              UNION SELECT 90 UNION SELECT 100 UNION SELECT 110 UNION SELECT 120
              UNION SELECT 130 UNION SELECT 150 UNION SELECT 170 UNION SELECT 190
              UNION SELECT 210 UNION SELECT 240 UNION SELECT 270 UNION SELECT 300
              UNION SELECT 330 UNION SELECT 360) seq
        WHERE DATE_SUB(CURDATE(), INTERVAL seq.num DAY) >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH)
    ) t
    GROUP BY DATE_FORMAT(v_calc_date, '%Y-%m')
)
SELECT
    mm.month,
    ROUND(mm.monthly_sales, 2) AS sales,
    ROUND(COALESCE(mp.monthly_purchase, 0), 2) AS purchase,
    ROUND(COALESCE(mi.avg_inventory_value, 0), 2) AS avg_inventory,
    -- 月末应收余额
    COALESCE((
        SELECT SUM(so2.total_amount - so2.paid_amount)
        FROM sales_orders so2
        WHERE DATE_FORMAT(so2.order_date, '%Y-%m') <= mm.month
          AND so2.status IN ('confirmed', 'delivering', 'delivered')
    ), 0) AS ar_balance,
    -- 月末应付余额
    COALESCE((
        SELECT SUM(po2.total_amount - po2.paid_amount)
        FROM purchase_orders po2
        WHERE DATE_FORMAT(po2.order_date, '%Y-%m') <= mm.month
          AND po2.status IN ('ordered', 'partially_received', 'received')
    ), 0) AS ap_balance,
    -- DIO = 平均库存 / 日均销售成本
    ROUND(COALESCE(mi.avg_inventory_value, 0) / NULLIF(mm.daily_sales * 0.6, 0), 1) AS DIO_days,
    -- DSO = 应收余额 / 日均销售额
    ROUND(COALESCE((
        SELECT SUM(so2.total_amount - so2.paid_amount)
        FROM sales_orders so2
        WHERE DATE_FORMAT(so2.order_date, '%Y-%m') <= mm.month
          AND so2.status IN ('confirmed', 'delivering', 'delivered')
    ), 0) / NULLIF(mm.daily_sales, 0), 1) AS DSO_days,
    -- DPO = 应付余额 / 日均采购额
    ROUND(COALESCE((
        SELECT SUM(po2.total_amount - po2.paid_amount)
        FROM purchase_orders po2
        WHERE DATE_FORMAT(po2.order_date, '%Y-%m') <= mm.month
          AND po2.status IN ('ordered', 'partially_received', 'received')
    ), 0) / NULLIF(COALESCE(mp.daily_purchase, 0), 0), 1) AS DPO_days,
    -- 现金周期
    ROUND(
        COALESCE(mi.avg_inventory_value, 0) / NULLIF(mm.daily_sales * 0.6, 0)
        + COALESCE((
            SELECT SUM(so2.total_amount - so2.paid_amount)
            FROM sales_orders so2
            WHERE DATE_FORMAT(so2.order_date, '%Y-%m') <= mm.month
              AND so2.status IN ('confirmed', 'delivering', 'delivered')
        ), 0) / NULLIF(mm.daily_sales, 0)
        - COALESCE((
            SELECT SUM(po2.total_amount - po2.paid_amount)
            FROM purchase_orders po2
            WHERE DATE_FORMAT(po2.order_date, '%Y-%m') <= mm.month
              AND po2.status IN ('ordered', 'partially_received', 'received')
        ), 0) / NULLIF(COALESCE(mp.daily_purchase, 0), 0)
    , 1) AS cash_conversion_cycle_days
FROM monthly_metrics mm
LEFT JOIN monthly_purchase mp ON mm.month = mp.month
LEFT JOIN monthly_inventory mi ON mm.month = mi.month
ORDER BY mm.month DESC;


-- ============================================================
-- Q103: 信用风险实时监控 - 客户信用额度使用率+逾期趋势
-- 语法: 多表JOIN + 条件聚合 + 风险评级
-- 业务: 财务/风控实时监控客户信用风险敞口
-- 统计原理: 信用使用率 = (余额+未结清) / 信用额度
--          风险 = 使用率>80%警告, >100%超额, 逾期>30天严重
-- ============================================================

SELECT
    c.id AS customer_id,
    c.code AS customer_code,
    c.name AS customer_name,
    c.membership_level,
    c.credit_limit,
    c.credit_days,
    c.balance AS account_balance,
    -- 未结清订单
    COALESCE(open_orders.unpaid_total, 0) AS unpaid_orders_total,
    COALESCE(open_orders.order_count, 0) AS open_order_count,
    -- 总风险敞口
    c.balance + COALESCE(open_orders.unpaid_total, 0) AS total_exposure,
    -- 信用使用率
    ROUND((c.balance + COALESCE(open_orders.unpaid_total, 0)) * 100.0
        / NULLIF(c.credit_limit, 0), 1) AS credit_utilization_pct,
    -- 逾期信息
    COALESCE(overdue.overdue_count, 0) AS overdue_order_count,
    COALESCE(overdue.overdue_amount, 0) AS overdue_amount,
    COALESCE(overdue.max_overdue_days, 0) AS max_overdue_days,
    -- 最近交易
    (SELECT MAX(order_date) FROM sales_orders WHERE customer_id = c.id AND status NOT IN ('draft', 'cancelled')) AS last_order_date,
    DATEDIFF(CURDATE(), COALESCE(
        (SELECT MAX(order_date) FROM sales_orders WHERE customer_id = c.id AND status NOT IN ('draft', 'cancelled')),
        '2020-01-01')) AS days_since_last_order,
    -- 最近付款
    (SELECT MAX(journal_date) FROM cashier_journals WHERE reference_type = 'sales_order'
     AND reference_id IN (SELECT id FROM sales_orders WHERE customer_id = c.id)) AS last_payment_date,
    -- 风险等级
    CASE
        WHEN c.status IN ('frozen', 'blacklist') THEN 'BLOCKED'
        WHEN COALESCE(overdue.max_overdue_days, 0) > 90 THEN 'SEVERE'
        WHEN COALESCE(overdue.max_overdue_days, 0) > 30 THEN 'HIGH'
        WHEN (c.balance + COALESCE(open_orders.unpaid_total, 0)) > c.credit_limit * 1.2 THEN 'CRITICAL'
        WHEN (c.balance + COALESCE(open_orders.unpaid_total, 0)) > c.credit_limit THEN 'OVER_LIMIT'
        WHEN (c.balance + COALESCE(open_orders.unpaid_total, 0)) > c.credit_limit * 0.8 THEN 'WARNING'
        WHEN DATEDIFF(CURDATE(), COALESCE(
            (SELECT MAX(order_date) FROM sales_orders WHERE customer_id = c.id AND status NOT IN ('draft', 'cancelled')),
            '2020-01-01')) > 180 THEN 'DORMANT'
        ELSE 'NORMAL'
    END AS risk_level,
    -- 建议行动
    CASE
        WHEN c.status IN ('frozen', 'blacklist') THEN '立即停止交易'
        WHEN COALESCE(overdue.max_overdue_days, 0) > 90 THEN '启动法律催收'
        WHEN COALESCE(overdue.max_overdue_days, 0) > 30 THEN '暂停赊销，发送催款函'
        WHEN (c.balance + COALESCE(open_orders.unpaid_total, 0)) > c.credit_limit THEN '暂停新订单，要求预付款'
        WHEN (c.balance + COALESCE(open_orders.unpaid_total, 0)) > c.credit_limit * 0.8 THEN '限制新订单额度'
        ELSE '正常交易'
    END AS recommended_action
FROM customers c
LEFT JOIN (
    SELECT
        customer_id,
        SUM(total_amount - paid_amount) AS unpaid_total,
        COUNT(*) AS order_count
    FROM sales_orders
    WHERE status IN ('confirmed', 'delivering', 'delivered')
      AND total_amount > paid_amount
    GROUP BY customer_id
) open_orders ON c.id = open_orders.customer_id
LEFT JOIN (
    SELECT
        customer_id,
        COUNT(*) AS overdue_count,
        SUM(total_amount - paid_amount) AS overdue_amount,
        MAX(DATEDIFF(CURDATE(), DATE_ADD(order_date, INTERVAL c2.credit_days DAY))) AS max_overdue_days
    FROM sales_orders so
    JOIN customers c2 ON so.customer_id = c2.id
    WHERE so.status IN ('confirmed', 'delivering', 'delivered')
      AND so.total_amount > so.paid_amount
      AND CURDATE() > DATE_ADD(so.order_date, INTERVAL c2.credit_days DAY)
    GROUP BY customer_id
) overdue ON c.id = overdue.customer_id
WHERE c.status != 'blacklist'
HAVING risk_level != 'NORMAL'
ORDER BY
    CASE risk_level
        WHEN 'BLOCKED' THEN 1
        WHEN 'SEVERE' THEN 2
        WHEN 'CRITICAL' THEN 3
        WHEN 'HIGH' THEN 4
        WHEN 'OVER_LIMIT' THEN 5
        WHEN 'WARNING' THEN 6
        WHEN 'DORMANT' THEN 7
        ELSE 8
    END, total_exposure DESC;


-- ============================================================
-- Q104: 批号全链路追溯 - 从供应商到客户的完整轨迹
-- 语法: 多表LEFT JOIN + UNION信息聚合
-- 业务: 质量召回/审计时追踪某批号的所有流转记录
-- 统计原理: 输入 = 采购入库，输出 = 销售出库+退货+报损+调拨
--          平衡校验 = 当前库存 = 入库 - 出库 + 退货 - 报损
-- ============================================================

SELECT
    pb.batch_no,
    p.sku,
    p.name AS product_name,
    s.name AS supplier_name,
    pb.production_date,
    pb.expiry_date,
    CASE
        WHEN pb.expiry_date < CURDATE() THEN '已过期'
        WHEN pb.expiry_date <= DATE_ADD(CURDATE(), INTERVAL 30 DAY) THEN '30天内到期'
        WHEN pb.expiry_date <= DATE_ADD(CURDATE(), INTERVAL 90 DAY) THEN '90天内到期'
        ELSE '正常'
    END AS expiry_status,
    pb.initial_qty,
    pb.current_qty,
    pb.initial_qty - pb.current_qty AS consumed_qty,
    ROUND((pb.initial_qty - pb.current_qty) * 100.0 / NULLIF(pb.initial_qty, 0), 1) AS consumption_rate_pct,
    pb.purchase_price,
    pb.status AS batch_status,
    -- 入库来源
    (SELECT GROUP_CONCAT(DISTINCT CONCAT(prec.receipt_no, '(', pri.accepted_qty, ')') SEPARATOR ', ')
        FROM purchase_receipt_items pri
        JOIN purchase_receipts prec ON pri.receipt_id = prec.id
        WHERE pri.batch_id = pb.id
    ) AS receipt_sources,
    -- 销售去向
    (SELECT GROUP_CONCAT(DISTINCT CONCAT(so.order_no, '->', c.name, '(', soi.quantity, ')') SEPARATOR ', ')
        FROM sales_order_items soi
        JOIN sales_orders so ON soi.order_id = so.id
        JOIN customers c ON so.customer_id = c.id
        WHERE soi.batch_id = pb.id
    ) AS sales_destinations,
    -- 退货记录
    (SELECT GROUP_CONCAT(DISTINCT CONCAT(sr.return_no, '(', sri.return_qty, ')') SEPARATOR ', ')
        FROM sales_return_items sri
        JOIN sales_returns sr ON sri.return_id = sr.id
        WHERE sri.batch_id = pb.id
    ) AS return_records,
    -- 当前库存分布
    (SELECT GROUP_CONCAT(DISTINCT CONCAT(w.name, ':', i.quantity) SEPARATOR ', ')
        FROM inventory i
        JOIN warehouses w ON i.warehouse_id = w.id
        WHERE i.batch_id = pb.id AND i.quantity > 0
    ) AS current_stock_distribution,
    -- 库存交易次数
    (SELECT COUNT(*) FROM inventory_transactions WHERE batch_id = pb.id) AS transaction_count,
    -- 质量检验
    (SELECT GROUP_CONCAT(DISTINCT CONCAT(ir.report_no, ':', ir.inspection_result) SEPARATOR ', ')
        FROM inspection_reports ir
        WHERE ir.batch_id = pb.id
    ) AS inspection_results
FROM product_batches pb
JOIN products p ON pb.product_id = p.id
LEFT JOIN suppliers s ON pb.supplier_id = s.id
WHERE pb.initial_qty > 0
ORDER BY pb.expiry_date ASC
LIMIT 50;


-- ============================================================
-- Q105: 毛利瀑布图 - 从标价到净利的层层拆解
-- 语法: CTE + 条件聚合 + 瀑布数据格式
-- 业务: 财务分析收入到净利的每一层损耗
-- 统计原理: 标价收入 -> 折扣 -> 净收入 -> 成本 -> 毛利
--          -> 退货 -> 报损 -> 促销 -> 仓储 -> 物流 -> 净利
-- ============================================================

WITH waterfall_data AS (
    SELECT
        '1.标价收入' AS layer, 1 AS sort_order,
        ROUND(SUM(soi.quantity * p.retail_price), 2) AS amount
    FROM sales_order_items soi
    JOIN sales_orders so ON soi.order_id = so.id
    JOIN products p ON soi.product_id = p.id
    WHERE so.order_date >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH)
      AND so.status NOT IN ('draft', 'cancelled')
    UNION ALL
    SELECT '2.折扣折让', 2,
        ROUND(-SUM(soi.discount) - SUM(so.discount_amount), 2)
    FROM sales_order_items soi
    JOIN sales_orders so ON soi.order_id = so.id
    WHERE so.order_date >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH)
      AND so.status NOT IN ('draft', 'cancelled')
    UNION ALL
    SELECT '3.净收入', 3,
        ROUND(SUM(soi.amount) - SUM(so.discount_amount), 2)
    FROM sales_order_items soi
    JOIN sales_orders so ON soi.order_id = so.id
    WHERE so.order_date >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH)
      AND so.status NOT IN ('draft', 'cancelled')
    UNION ALL
    SELECT '4.销售成本', 4,
        ROUND(-SUM(soi.quantity * p.purchase_price), 2)
    FROM sales_order_items soi
    JOIN sales_orders so ON soi.order_id = so.id
    JOIN products p ON soi.product_id = p.id
    WHERE so.order_date >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH)
      AND so.status NOT IN ('draft', 'cancelled')
    UNION ALL
    SELECT '5.毛利', 5,
        ROUND(SUM(soi.amount - soi.quantity * p.purchase_price) - SUM(so.discount_amount), 2)
    FROM sales_order_items soi
    JOIN sales_orders so ON soi.order_id = so.id
    JOIN products p ON soi.product_id = p.id
    WHERE so.order_date >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH)
      AND so.status NOT IN ('draft', 'cancelled')
    UNION ALL
    SELECT '6.退货净损失', 6,
        ROUND(-COALESCE(SUM(sr.refund_amount - sr.restock_fee), 0), 2)
    FROM sales_returns sr
    WHERE sr.return_date >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH)
      AND sr.status NOT IN ('rejected')
    UNION ALL
    SELECT '7.报损损失', 7,
        ROUND(-COALESCE(SUM(dri.loss_amount), 0), 2)
    FROM damage_report_items dri
    JOIN damage_reports dr ON dri.report_id = dr.id
    WHERE dr.report_date >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH)
      AND dr.status IN ('approved', 'executed')
    UNION ALL
    SELECT '8.促销费用', 8,
        ROUND(-COALESCE(SUM(pu.discount_applied), 0), 2)
    FROM promotion_usages pu
    WHERE pu.used_at >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH)
    UNION ALL
    SELECT '9.仓储成本', 9,
        ROUND(-SUM(w.capacity_m3 * 20 * 12), 2)
    FROM warehouses w WHERE w.status = 'active'
    UNION ALL
    SELECT '10.物流费用', 10,
        ROUND(-COALESCE(SUM(sh.shipping_fee), 0), 2)
    FROM shipments sh
    WHERE sh.shipped_at >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH)
    UNION ALL
    SELECT '11.净利', 11,
        ROUND(
            (SELECT SUM(soi.amount - soi.quantity * p.purchase_price) - SUM(so.discount_amount)
             FROM sales_order_items soi
             JOIN sales_orders so ON soi.order_id = so.id
             JOIN products p ON soi.product_id = p.id
             WHERE so.order_date >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH)
               AND so.status NOT IN ('draft', 'cancelled'))
            - COALESCE((SELECT SUM(sr.refund_amount - sr.restock_fee) FROM sales_returns sr
              WHERE sr.return_date >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH) AND sr.status NOT IN ('rejected')), 0)
            - COALESCE((SELECT SUM(dri.loss_amount) FROM damage_report_items dri
              JOIN damage_reports dr ON dri.report_id = dr.id
              WHERE dr.report_date >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH) AND dr.status IN ('approved', 'executed')), 0)
            - COALESCE((SELECT SUM(pu.discount_applied) FROM promotion_usages pu
              WHERE pu.used_at >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH)), 0)
            - (SELECT SUM(w2.capacity_m3 * 20 * 12) FROM warehouses w2 WHERE w2.status = 'active')
            - COALESCE((SELECT SUM(sh2.shipping_fee) FROM shipments sh2
              WHERE sh2.shipped_at >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH)), 0)
        , 2)
)
SELECT
    layer,
    amount,
    SUM(amount) OVER (ORDER BY sort_order) AS running_total,
    ROUND(amount * 100.0 / NULLIF(ABS(FIRST_VALUE(amount) OVER (ORDER BY sort_order)), 0), 1) AS pct_of_list_price,
    CASE WHEN amount >= 0 THEN '+' ELSE '' END AS direction
FROM waterfall_data
ORDER BY sort_order;


-- ============================================================
-- Q106: 供应商集中度风险分析 - 单一供应商依赖度
-- 语法: CTE + 窗口函数 + 集中度指标
-- 业务: 采购/供应链识别对单一供应商过度依赖的风险
-- 统计原理: 集中度 = 该供应商采购额 / 总采购额
--          HHI指数 = Σ(每家供应商份额^2)
--          >0.25=高度集中风险, >0.15=中度集中
-- ============================================================

WITH supplier_purchase AS (
    SELECT
        po.supplier_id,
        s.name AS supplier_name,
        COUNT(DISTINCT po.id) AS order_count,
        SUM(po.total_amount) AS total_purchase,
        COUNT(DISTINCT poi.product_id) AS product_count,
        AVG(po.total_amount) AS avg_order_amount,
        SUM(po.paid_amount) AS total_paid,
        SUM(po.total_amount) - SUM(po.paid_amount) AS unpaid
    FROM purchase_orders po
    JOIN suppliers s ON po.supplier_id = s.id
    JOIN purchase_order_items poi ON po.id = poi.order_id
    WHERE po.order_date >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH)
      AND po.status NOT IN ('draft', 'cancelled')
    GROUP BY po.supplier_id, s.name
),
total_purchase AS (
    SELECT SUM(total_purchase) AS grand_total FROM supplier_purchase
)
SELECT
    sp.supplier_name,
    sp.order_count,
    sp.product_count,
    sp.total_purchase,
    ROUND(sp.total_purchase * 100.0 / NULLIF(tp.grand_total, 0), 1) AS purchase_share_pct,
    ROUND(SUM(sp.total_purchase * 100.0 / NULLIF(tp.grand_total, 0))
        OVER (ORDER BY sp.total_purchase DESC), 1) AS cumulative_share_pct,
    ROUND(sp.total_purchase * 100.0 / NULLIF(tp.grand_total, 0) * sp.total_purchase * 100.0 / NULLIF(tp.grand_total, 0), 2) AS hhi_contribution,
    sp.avg_order_amount,
    ROUND(sp.total_paid * 100.0 / NULLIF(sp.total_purchase, 0), 1) AS payment_rate_pct,
    sp.unpaid,
    -- 供应商品类覆盖
    (SELECT GROUP_CONCAT(DISTINCT pc.name ORDER BY pc.name SEPARATOR ', ')
     FROM purchase_order_items poi2
     JOIN products p ON poi2.product_id = p.id
     JOIN product_categories pc ON p.category_id = pc.id
     JOIN purchase_orders po2 ON poi2.order_id = po2.id
     WHERE po2.supplier_id = sp.supplier_id
       AND po2.order_date >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH))
    AS supplied_categories,
    -- 风险等级
    CASE
        WHEN sp.total_purchase * 100.0 / NULLIF(tp.grand_total, 0) > 30 THEN '严重依赖'
        WHEN sp.total_purchase * 100.0 / NULLIF(tp.grand_total, 0) > 20 THEN '高度依赖'
        WHEN sp.total_purchase * 100.0 / NULLIF(tp.grand_total, 0) > 10 THEN '中度依赖'
        ELSE '正常'
    END AS concentration_risk,
    -- 建议
    CASE
        WHEN sp.total_purchase * 100.0 / NULLIF(tp.grand_total, 0) > 30
        THEN '紧急开发替代供应商'
        WHEN sp.total_purchase * 100.0 / NULLIF(tp.grand_total, 0) > 20
        THEN '建议引入第二供应商分流'
        ELSE '正常维护'
    END AS recommendation
FROM supplier_purchase sp, total_purchase tp
ORDER BY sp.total_purchase DESC;


-- ============================================================
-- Q107: 月度关账核对清单 - 收入/成本/库存/应收/应付一致性
-- 语法: 多子查询 + 交叉验证
-- 业务: 财务月末关账时核对各模块数据一致性
-- 统计原理: 销售收入 = 销售单汇总
--          销售成本 = 销售出库量 * 进货价
--          库存余额 = 期初 + 入库 - 出库 + 退货 - 报损
-- ============================================================

SELECT
    '模块' AS check_item,
    '子系统金额' AS subsystem_amount,
    '总账金额' AS gl_amount,
    '差异' AS difference,
    '状态' AS status
UNION ALL
-- 1. 销售收入核对
SELECT
    '1.销售收入',
    ROUND(COALESCE((SELECT SUM(total_amount) FROM sales_orders
     WHERE order_date BETWEEN DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 MONTH), '%Y-%m-01')
     AND LAST_DAY(DATE_SUB(CURDATE(), INTERVAL 1 MONTH))
     AND status NOT IN ('draft', 'cancelled')), 0), 2),
    ROUND(COALESCE((SELECT SUM(amount) FROM voucher_items vi
     JOIN vouchers v ON vi.voucher_id = v.id
     JOIN accounts a ON vi.account_id = a.id
     WHERE v.voucher_date BETWEEN DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 MONTH), '%Y-%m-01')
     AND LAST_DAY(DATE_SUB(CURDATE(), INTERVAL 1 MONTH))
     AND a.account_type = 'revenue' AND v.status = 'posted' AND vi.direction = 'credit'), 0), 2),
    ROUND(COALESCE((SELECT SUM(total_amount) FROM sales_orders
     WHERE order_date BETWEEN DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 MONTH), '%Y-%m-01')
     AND LAST_DAY(DATE_SUB(CURDATE(), INTERVAL 1 MONTH))
     AND status NOT IN ('draft', 'cancelled')), 0) -
    COALESCE((SELECT SUM(amount) FROM voucher_items vi
     JOIN vouchers v ON vi.voucher_id = v.id
     JOIN accounts a ON vi.account_id = a.id
     WHERE v.voucher_date BETWEEN DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 MONTH), '%Y-%m-01')
     AND LAST_DAY(DATE_SUB(CURDATE(), INTERVAL 1 MONTH))
     AND a.account_type = 'revenue' AND v.status = 'posted' AND vi.direction = 'credit'), 0), 2),
    IF(ABS(COALESCE((SELECT SUM(total_amount) FROM sales_orders
     WHERE order_date BETWEEN DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 MONTH), '%Y-%m-01')
     AND LAST_DAY(DATE_SUB(CURDATE(), INTERVAL 1 MONTH))
     AND status NOT IN ('draft', 'cancelled')), 0) -
    COALESCE((SELECT SUM(amount) FROM voucher_items vi
     JOIN vouchers v ON vi.voucher_id = v.id
     JOIN accounts a ON vi.account_id = a.id
     WHERE v.voucher_date BETWEEN DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 MONTH), '%Y-%m-01')
     AND LAST_DAY(DATE_SUB(CURDATE(), INTERVAL 1 MONTH))
     AND a.account_type = 'revenue' AND v.status = 'posted' AND vi.direction = 'credit'), 0)) < 1, '通过', '差异')
UNION ALL
-- 2. 采购入库核对
SELECT
    '2.采购入库',
    ROUND(COALESCE((SELECT SUM(total_amount) FROM purchase_receipts
     WHERE receipt_date BETWEEN DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 MONTH), '%Y-%m-01')
     AND LAST_DAY(DATE_SUB(CURDATE(), INTERVAL 1 MONTH))), 0), 2),
    ROUND(COALESCE((SELECT SUM(amount) FROM voucher_items vi
     JOIN vouchers v ON vi.voucher_id = v.id
     JOIN accounts a ON vi.account_id = a.id
     WHERE v.voucher_date BETWEEN DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 MONTH), '%Y-%m-01')
     AND LAST_DAY(DATE_SUB(CURDATE(), INTERVAL 1 MONTH))
     AND a.code = '1405' AND v.status = 'posted' AND vi.direction = 'debit'), 0), 2),
    ROUND(COALESCE((SELECT SUM(total_amount) FROM purchase_receipts
     WHERE receipt_date BETWEEN DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 MONTH), '%Y-%m-01')
     AND LAST_DAY(DATE_SUB(CURDATE(), INTERVAL 1 MONTH))), 0) -
    COALESCE((SELECT SUM(amount) FROM voucher_items vi
     JOIN vouchers v ON vi.voucher_id = v.id
     JOIN accounts a ON vi.account_id = a.id
     WHERE v.voucher_date BETWEEN DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 MONTH), '%Y-%m-01')
     AND LAST_DAY(DATE_SUB(CURDATE(), INTERVAL 1 MONTH))
     AND a.code = '1405' AND v.status = 'posted' AND vi.direction = 'debit'), 0), 2),
    IF(ABS(COALESCE((SELECT SUM(total_amount) FROM purchase_receipts
     WHERE receipt_date BETWEEN DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 MONTH), '%Y-%m-01')
     AND LAST_DAY(DATE_SUB(CURDATE(), INTERVAL 1 MONTH))), 0) -
    COALESCE((SELECT SUM(amount) FROM voucher_items vi
     JOIN vouchers v ON vi.voucher_id = v.id
     JOIN accounts a ON vi.account_id = a.id
     WHERE v.voucher_date BETWEEN DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 MONTH), '%Y-%m-01')
     AND LAST_DAY(DATE_SUB(CURDATE(), INTERVAL 1 MONTH))
     AND a.code = '1405' AND v.status = 'posted' AND vi.direction = 'debit'), 0)) < 1, '通过', '差异')
UNION ALL
-- 3. 工资支出核对
SELECT
    '3.工资支出',
    ROUND(COALESCE((SELECT SUM(net_pay + social_security_company + housing_fund_company)
     FROM salary_payments
     WHERE salary_month = DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 MONTH), '%Y-%m')), 0), 2),
    ROUND(COALESCE((SELECT SUM(amount) FROM voucher_items vi
     JOIN vouchers v ON vi.voucher_id = v.id
     JOIN accounts a ON vi.account_id = a.id
     WHERE v.voucher_date BETWEEN DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 MONTH), '%Y-%m-01')
     AND LAST_DAY(DATE_SUB(CURDATE(), INTERVAL 1 MONTH))
     AND a.account_type = 'expense' AND a.name LIKE '%工资%' AND v.status = 'posted' AND vi.direction = 'debit'), 0), 2),
    ROUND(COALESCE((SELECT SUM(net_pay + social_security_company + housing_fund_company)
     FROM salary_payments
     WHERE salary_month = DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 MONTH), '%Y-%m')), 0) -
    COALESCE((SELECT SUM(amount) FROM voucher_items vi
     JOIN vouchers v ON vi.voucher_id = v.id
     JOIN accounts a ON vi.account_id = a.id
     WHERE v.voucher_date BETWEEN DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 MONTH), '%Y-%m-01')
     AND LAST_DAY(DATE_SUB(CURDATE(), INTERVAL 1 MONTH))
     AND a.account_type = 'expense' AND a.name LIKE '%工资%' AND v.status = 'posted' AND vi.direction = 'debit'), 0), 2),
    IF(ABS(COALESCE((SELECT SUM(net_pay + social_security_company + housing_fund_company)
     FROM salary_payments
     WHERE salary_month = DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 MONTH), '%Y-%m')), 0) -
    COALESCE((SELECT SUM(amount) FROM voucher_items vi
     JOIN vouchers v ON vi.voucher_id = v.id
     JOIN accounts a ON vi.account_id = a.id
     WHERE v.voucher_date BETWEEN DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 MONTH), '%Y-%m-01')
     AND LAST_DAY(DATE_SUB(CURDATE(), INTERVAL 1 MONTH))
     AND a.account_type = 'expense' AND a.name LIKE '%工资%' AND v.status = 'posted' AND vi.direction = 'debit'), 0)) < 1, '通过', '差异')
UNION ALL
-- 4. 库存余额核对
SELECT
    '4.库存余额(成本)',
    ROUND(COALESCE((SELECT SUM(i.quantity * p.purchase_price) FROM inventory i JOIN products p ON i.product_id = p.id), 0), 2),
    ROUND(COALESCE((SELECT SUM(amount) FROM voucher_items vi
     JOIN vouchers v ON vi.voucher_id = v.id
     JOIN accounts a ON vi.account_id = a.id
     WHERE a.code = '1405' AND v.status = 'posted'), 0), 2),
    ROUND(COALESCE((SELECT SUM(i.quantity * p.purchase_price) FROM inventory i JOIN products p ON i.product_id = p.id), 0) -
    COALESCE((SELECT SUM(amount) FROM voucher_items vi
     JOIN vouchers v ON vi.voucher_id = v.id
     JOIN accounts a ON vi.account_id = a.id
     WHERE a.code = '1405' AND v.status = 'posted'), 0), 2),
    IF(ABS(COALESCE((SELECT SUM(i.quantity * p.purchase_price) FROM inventory i JOIN products p ON i.product_id = p.id), 0) -
    COALESCE((SELECT SUM(amount) FROM voucher_items vi
     JOIN vouchers v ON vi.voucher_id = v.id
     JOIN accounts a ON vi.account_id = a.id
     WHERE a.code = '1405' AND v.status = 'posted'), 0)) < 100, '通过', '差异')
UNION ALL
-- 5. 应收余额核对
SELECT
    '5.应收账款',
    ROUND(COALESCE((SELECT SUM(total_amount - paid_amount) FROM sales_orders
     WHERE status IN ('confirmed', 'delivering', 'delivered')), 0), 2),
    ROUND(COALESCE((SELECT SUM(current_balance) FROM accounts WHERE code = '112201'), 0), 2),
    ROUND(COALESCE((SELECT SUM(total_amount - paid_amount) FROM sales_orders
     WHERE status IN ('confirmed', 'delivering', 'delivered')), 0) -
    COALESCE((SELECT SUM(current_balance) FROM accounts WHERE code = '112201'), 0), 2),
    IF(ABS(COALESCE((SELECT SUM(total_amount - paid_amount) FROM sales_orders
     WHERE status IN ('confirmed', 'delivering', 'delivered')), 0) -
    COALESCE((SELECT SUM(current_balance) FROM accounts WHERE code = '112201'), 0)) < 100, '通过', '差异')
UNION ALL
-- 6. 应付余额核对
SELECT
    '6.应付账款',
    ROUND(COALESCE((SELECT SUM(total_amount - paid_amount) FROM purchase_orders
     WHERE status IN ('ordered', 'partially_received', 'received')), 0), 2),
    ROUND(COALESCE((SELECT SUM(current_balance) FROM accounts WHERE code = '220201'), 0), 2),
    ROUND(COALESCE((SELECT SUM(total_amount - paid_amount) FROM purchase_orders
     WHERE status IN ('ordered', 'partially_received', 'received')), 0) -
    COALESCE((SELECT SUM(current_balance) FROM accounts WHERE code = '220201'), 0), 2),
    IF(ABS(COALESCE((SELECT SUM(total_amount - paid_amount) FROM purchase_orders
     WHERE status IN ('ordered', 'partially_received', 'received')), 0) -
    COALESCE((SELECT SUM(current_balance) FROM accounts WHERE code = '220201'), 0)) < 100, '通过', '差异');


-- ============================================================
-- Q108: 仓库库容利用率与预警 - 多仓库容量管理
-- 语法: 派生表 + 条件聚合 + 利用率计算
-- 业务: 仓储经理监控各仓库库容使用情况
-- 统计原理: 体积利用率 = 库存体积 / 仓库容量
--          SKU密度 = SKU数 / 仓库容量
--          预警: >80%需关注, >90%需扩容, >95%紧急
-- ============================================================

SELECT
    w.name AS warehouse_name,
    w.type AS warehouse_type,
    w.city,
    w.capacity_m3,
    -- 库存体积
    ROUND(COALESCE(SUM(i.quantity * p.volume_m3), 0), 3) AS used_volume_m3,
    -- 剩余容量
    ROUND(w.capacity_m3 - COALESCE(SUM(i.quantity * p.volume_m3), 0), 3) AS free_volume_m3,
    -- 体积利用率
    ROUND(COALESCE(SUM(i.quantity * p.volume_m3), 0) * 100.0 / NULLIF(w.capacity_m3, 0), 1) AS volume_util_pct,
    -- SKU数
    COUNT(DISTINCT i.product_id) AS sku_count,
    -- 总件数
    SUM(i.quantity) AS total_units,
    -- 库存价值
    ROUND(SUM(i.quantity * p.purchase_price), 2) AS total_value,
    -- 批号数
    COUNT(DISTINCT i.batch_id) AS batch_count,
    -- SKU密度 (每立方米SKU数)
    ROUND(COUNT(DISTINCT i.product_id) / NULLIF(w.capacity_m3, 1), 2) AS sku_density,
    -- 平均每SKU占据空间
    ROUND(COALESCE(SUM(i.quantity * p.volume_m3), 0) / NULLIF(COUNT(DISTINCT i.product_id), 0), 3) AS avg_volume_per_sku,
    -- 临期库存占比
    ROUND(SUM(CASE WHEN pb.expiry_date <= DATE_ADD(CURDATE(), INTERVAL 60 DAY) THEN i.quantity ELSE 0 END)
        * 100.0 / NULLIF(SUM(i.quantity), 0), 1) AS near_expiry_pct,
    -- 呆滞库存(>180天无销售)
    ROUND(SUM(CASE WHEN last_sold.last_sale_date IS NULL
        OR last_sold.last_sale_date < DATE_SUB(CURDATE(), INTERVAL 180 DAY)
        THEN i.quantity * p.purchase_price ELSE 0 END), 2) AS slow_moving_value,
    -- 利用率等级
    CASE
        WHEN COALESCE(SUM(i.quantity * p.volume_m3), 0) * 100.0 / NULLIF(w.capacity_m3, 0) > 95 THEN '紧急-需扩容'
        WHEN COALESCE(SUM(i.quantity * p.volume_m3), 0) * 100.0 / NULLIF(w.capacity_m3, 0) > 90 THEN '警告-即将满仓'
        WHEN COALESCE(SUM(i.quantity * p.volume_m3), 0) * 100.0 / NULLIF(w.capacity_m3, 0) > 80 THEN '关注-高利用率'
        WHEN COALESCE(SUM(i.quantity * p.volume_m3), 0) * 100.0 / NULLIF(w.capacity_m3, 0) > 50 THEN '正常'
        ELSE '低利用率'
    END AS capacity_status,
    -- 预计可接收货物体积(按90%目标)
    ROUND(w.capacity_m3 * 0.9 - COALESCE(SUM(i.quantity * p.volume_m3), 0), 3) AS remaining_to_90pct
FROM warehouses w
JOIN inventory i ON w.id = i.warehouse_id
JOIN products p ON i.product_id = p.id
LEFT JOIN product_batches pb ON i.batch_id = pb.id
LEFT JOIN (
    SELECT soi.product_id, MAX(so.order_date) AS last_sale_date
    FROM sales_order_items soi
    JOIN sales_orders so ON soi.order_id = so.id
    WHERE so.status NOT IN ('draft', 'cancelled')
    GROUP BY soi.product_id
) last_sold ON i.product_id = last_sold.product_id
WHERE w.status = 'active'
GROUP BY w.id, w.name, w.type, w.city, w.capacity_m3
ORDER BY volume_util_pct DESC;


-- ============================================================
-- Q109: 销售提成核对 - 提成计算与发放一致性
-- 语法: CTE + LEFT JOIN + 差异分析
-- 业务: 财务/HR核对提成计算是否正确，发放是否准确
-- 统计原理: 应发提成 = 销售额 * 提成率 + 超额奖金
--          差异 = 已发提成 - 应发提成
-- ============================================================

WITH computed_commission AS (
    SELECT
        sc.employee_id,
        sc.period,
        SUM(sc.base_amount) AS total_sales_base,
        AVG(sc.commission_rate) AS avg_commission_rate,
        SUM(sc.commission_amount) AS computed_commission_amount,
        SUM(sc.bonus) AS computed_bonus,
        SUM(sc.total_commission) AS computed_total,
        COUNT(*) AS detail_count,
        SUM(CASE WHEN sc.status = 'paid' THEN sc.total_commission ELSE 0 END) AS paid_commission
    FROM sales_commissions sc
    WHERE sc.period >= DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 6 MONTH), '%Y-%m')
    GROUP BY sc.employee_id, sc.period
),
salary_paid AS (
    SELECT
        sp.employee_id,
        sp.salary_month,
        SUM(sp.bonus) AS actual_bonus_paid
    FROM salary_payments sp
    WHERE sp.salary_month >= DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 6 MONTH), '%Y-%m')
    GROUP BY sp.employee_id, sp.salary_month
)
SELECT
    e.name AS employee_name,
    d.name AS department_name,
    cc.period,
    cc.total_sales_base,
    cc.avg_commission_rate,
    cc.computed_commission_amount,
    cc.computed_bonus,
    cc.computed_total AS computed_total_commission,
    COALESCE(sp.actual_bonus_paid, 0) AS actual_bonus_paid,
    cc.paid_commission,
    cc.computed_total - cc.paid_commission AS unpaid_commission,
    cc.computed_total - COALESCE(sp.actual_bonus_paid, 0) AS commission_vs_salary_diff,
    cc.detail_count AS commission_lines,
    CASE
        WHEN ABS(cc.computed_total - COALESCE(sp.actual_bonus_paid, 0)) > 100 THEN '差异较大'
        WHEN ABS(cc.computed_total - COALESCE(sp.actual_bonus_paid, 0)) > 10 THEN '轻微差异'
        ELSE '一致'
    END AS reconciliation_status,
    -- 提成率是否合理
    CASE
        WHEN cc.avg_commission_rate > 0.15 THEN '提成率偏高'
        WHEN cc.avg_commission_rate < 0.01 THEN '提成率偏低'
        ELSE '正常'
    END AS rate_check
FROM computed_commission cc
JOIN employees e ON cc.employee_id = e.id
JOIN departments d ON e.department_id = d.id
LEFT JOIN salary_paid sp ON cc.employee_id = sp.employee_id AND cc.period = sp.salary_month
ORDER BY cc.period DESC, computed_total_commission DESC;


-- ============================================================
-- Q110: 需求预测准确率分析 - 实际销量 vs 预测销量
-- 语法: CTE + 时间序列 + 准确率计算
-- 业务: 供应链评估销售预测准确率，优化采购计划
-- 统计原理: 预测准确率 = 1 - |实际-预测|/实际
--          MAPE = 平均绝对百分比误差
--          偏差方向: 正偏=预测过高(库存积压), 负偏=预测过低(缺货)
-- ============================================================

WITH actual_sales AS (
    SELECT
        soi.product_id,
        DATE_FORMAT(so.order_date, '%Y-%m') AS sale_month,
        SUM(soi.quantity) AS actual_qty,
        SUM(soi.amount) AS actual_amount
    FROM sales_order_items soi
    JOIN sales_orders so ON soi.order_id = so.id
    WHERE so.order_date >= DATE_SUB(CURDATE(), INTERVAL 6 MONTH)
      AND so.status NOT IN ('draft', 'cancelled')
    GROUP BY soi.product_id, DATE_FORMAT(so.order_date, '%Y-%m')
),
-- 简化预测: 3个月移动平均作为预测值
forecast AS (
    SELECT
        product_id,
        sale_month,
        actual_qty,
        actual_amount,
        ROUND(AVG(actual_qty) OVER (
            PARTITION BY product_id
            ORDER BY sale_month
            ROWS BETWEEN 3 PRECEDING AND 1 PRECEDING
        ), 0) AS forecast_qty_3ma,
        ROUND(AVG(actual_amount) OVER (
            PARTITION BY product_id
            ORDER BY sale_month
            ROWS BETWEEN 3 PRECEDING AND 1 PRECEDING
        ), 2) AS forecast_amount_3ma
    FROM actual_sales
)
SELECT
    p.sku,
    p.name AS product_name,
    pc.name AS category_name,
    f.sale_month,
    f.actual_qty,
    COALESCE(f.forecast_qty_3ma, 0) AS forecast_qty,
    f.actual_qty - COALESCE(f.forecast_qty_3ma, 0) AS absolute_error,
    ROUND(
        (1 - ABS(f.actual_qty - COALESCE(f.forecast_qty_3ma, 0))
        / NULLIF(f.actual_qty, 0)) * 100, 1
    ) AS accuracy_pct,
    CASE
        WHEN COALESCE(f.forecast_qty_3ma, 0) = 0 THEN '无预测'
        WHEN f.actual_qty > COALESCE(f.forecast_qty_3ma, 0) * 1.5 THEN '严重低估'
        WHEN f.actual_qty > COALESCE(f.forecast_qty_3ma, 0) * 1.2 THEN '低估'
        WHEN f.actual_qty < COALESCE(f.forecast_qty_3ma, 0) * 0.5 THEN '严重高估'
        WHEN f.actual_qty < COALESCE(f.forecast_qty_3ma, 0) * 0.8 THEN '高估'
        ELSE '正常'
    END AS bias_direction,
    -- 经济影响
    ROUND(
        (f.actual_qty - COALESCE(f.forecast_qty_3ma, 0)) * p.purchase_price,
        2
    ) AS inventory_impact,
    CASE
        WHEN (f.actual_qty - COALESCE(f.forecast_qty_3ma, 0)) > 0
        THEN '缺货风险:库存不足'
        WHEN (f.actual_qty - COALESCE(f.forecast_qty_3ma, 0)) < 0
        THEN '积压风险:库存过多'
        ELSE '匹配'
    END AS impact_description
FROM forecast f
JOIN products p ON f.product_id = p.id
JOIN product_categories pc ON p.category_id = pc.id
WHERE f.forecast_qty_3ma IS NOT NULL
ORDER BY ABS(f.actual_qty - COALESCE(f.forecast_qty_3ma, 0)) DESC
LIMIT 50;


-- ============================================================
-- Q111: 跨门店调拨优化建议 - 识别可调拨解决缺货的机会
-- 语法: CROSS JOIN + 派生表 + 条件过滤
-- 业务: 供应链自动识别哪些门店间调拨可以减少缺货
-- 统计原理: 门店A缺货 + 门店B积压 -> 调拨建议
--          调拨成本 vs 缺货损失 -> 净收益
-- ============================================================

WITH store_stock_status AS (
    SELECT
        w.id AS warehouse_id,
        w.name AS store_name,
        w.city,
        i.product_id,
        p.sku,
        p.name AS product_name,
        pc.name AS category_name,
        i.available_quantity AS stock_qty,
        p.min_stock,
        p.purchase_price,
        p.retail_price,
        -- 日均销量
        COALESCE(avg_sales.daily_avg, 0) AS daily_sales,
        CASE
            WHEN i.available_quantity <= 0 THEN '缺货'
            WHEN i.available_quantity <= p.min_stock THEN '低库存'
            WHEN i.available_quantity >= p.max_stock THEN '超储'
            ELSE '正常'
        END AS stock_status
    FROM inventory i
    JOIN warehouses w ON i.warehouse_id = w.id
    JOIN products p ON i.product_id = p.id
    JOIN product_categories pc ON p.category_id = pc.id
    LEFT JOIN (
        SELECT soi.product_id, so.warehouse_id,
            SUM(soi.quantity) / 30.0 AS daily_avg
        FROM sales_order_items soi
        JOIN sales_orders so ON soi.order_id = so.id
        WHERE so.order_date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
          AND so.status NOT IN ('draft', 'cancelled')
        GROUP BY soi.product_id, so.warehouse_id
    ) avg_sales ON i.product_id = avg_sales.product_id AND i.warehouse_id = avg_sales.warehouse_id
    WHERE w.status = 'active' AND p.status = 'active'
)
SELECT
    shortage.store_name AS shortage_store,
    shortage.city AS shortage_city,
    surplus.store_name AS surplus_store,
    surplus.city AS surplus_city,
    shortage.sku,
    shortage.product_name,
    shortage.category_name,
    shortage.stock_qty AS shortage_qty,
    shortage.min_stock,
    shortage.daily_sales AS shortage_daily_sales,
    surplus.stock_qty AS surplus_qty,
    surplus.daily_sales AS surplus_daily_sales,
    -- 建议调拨量
    LEAST(
        shortage.min_stock * 2 - shortage.stock_qty,
        surplus.stock_qty - surplus.min_stock * 2
    ) AS suggested_transfer_qty,
    -- 缺货损失(如果缺货持续)
    ROUND(shortage.daily_sales * shortage.retail_price * 3, 2) AS estimated_shortage_loss_3d,
    -- 调拨成本(估算)
    ROUND(ABS(shortage.stock_qty - surplus.stock_qty) * shortage.purchase_price * 0.02, 2) AS estimated_transfer_cost,
    -- 净收益
    ROUND(
        shortage.daily_sales * shortage.retail_price * 3
        - LEAST(shortage.min_stock * 2 - shortage.stock_qty, surplus.stock_qty - surplus.min_stock * 2) * shortage.purchase_price * 0.02
    , 2) AS net_benefit,
    -- 优先级
    CASE
        WHEN shortage.stock_qty <= 0 THEN '紧急'
        WHEN shortage.daily_sales > 10 THEN '高优先级'
        ELSE '普通'
    END AS priority
FROM store_stock_status shortage
JOIN store_stock_status surplus
    ON shortage.product_id = surplus.product_id
    AND shortage.warehouse_id != surplus.warehouse_id
WHERE shortage.stock_status IN ('缺货', '低库存')
  AND surplus.stock_status = '超储'
  AND surplus.stock_qty > surplus.min_stock * 2
  AND LEAST(
        shortage.min_stock * 2 - shortage.stock_qty,
        surplus.stock_qty - surplus.min_stock * 2
      ) > 0
ORDER BY
    CASE
        WHEN shortage.stock_qty <= 0 THEN 0
        WHEN shortage.daily_sales > 10 THEN 1
        ELSE 2
    END,
    net_benefit DESC
LIMIT 50;


-- ============================================================
-- Q112: 产品生命周期分析 - 增长/成熟/衰退阶段判定
-- 语法: CTE + 环比增长率 + 生命周期判定
-- 业务: 商品管理识别每个产品的生命周期阶段
-- 统计原理: 环比增长率 = (本月销量-上月销量)/上月销量
--          引入期: 销量<均值且增长>50%
--          成长期: 销量增长>20%
--          成熟期: 销量稳定(-10%~+20%)
--          衰退期: 销量持续下降(<-10%)
-- ============================================================

WITH monthly_product_sales AS (
    SELECT
        soi.product_id,
        DATE_FORMAT(so.order_date, '%Y-%m') AS sale_month,
        SUM(soi.quantity) AS monthly_qty,
        SUM(soi.amount) AS monthly_amount
    FROM sales_order_items soi
    JOIN sales_orders so ON soi.order_id = so.id
    WHERE so.order_date >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH)
      AND so.status NOT IN ('draft', 'cancelled')
    GROUP BY soi.product_id, DATE_FORMAT(so.order_date, '%Y-%m')
),
product_growth AS (
    SELECT
        product_id,
        sale_month,
        monthly_qty,
        monthly_amount,
        LAG(monthly_qty, 1) OVER (PARTITION BY product_id ORDER BY sale_month) AS prev_month_qty,
        LAG(monthly_qty, 3) OVER (PARTITION BY product_id ORDER BY sale_month) AS prev_qtr_qty,
        AVG(monthly_qty) OVER (PARTITION BY product_id) AS avg_monthly_qty,
        COUNT(*) OVER (PARTITION BY product_id) AS active_months
    FROM monthly_product_sales
)
SELECT
    p.sku,
    p.name AS product_name,
    pc.name AS category_name,
    pg.sale_month,
    pg.monthly_qty,
    pg.prev_month_qty,
    ROUND((pg.monthly_qty - pg.prev_month_qty) * 100.0 / NULLIF(pg.prev_month_qty, 0), 1) AS mom_growth_pct,
    ROUND((pg.monthly_qty - pg.prev_qtr_qty) * 100.0 / NULLIF(pg.prev_qtr_qty, 0), 1) AS qoq_growth_pct,
    pg.avg_monthly_qty,
    pg.active_months,
    -- 生命周期阶段
    CASE
        WHEN pg.active_months <= 1 THEN '新上市'
        WHEN pg.active_months <= 3 AND pg.monthly_qty < pg.avg_monthly_qty * 0.5 THEN '引入期'
        WHEN (pg.monthly_qty - pg.prev_qtr_qty) * 100.0 / NULLIF(pg.prev_qtr_qty, 0) > 30 THEN '成长期'
        WHEN (pg.monthly_qty - pg.prev_qtr_qty) * 100.0 / NULLIF(pg.prev_qtr_qty, 0) BETWEEN -10 AND 30 THEN '成熟期'
        WHEN (pg.monthly_qty - pg.prev_qtr_qty) * 100.0 / NULLIF(pg.prev_qtr_qty, 0) < -10
            AND pg.monthly_qty < pg.avg_monthly_qty * 0.5 THEN '衰退期'
        ELSE '波动期'
    END AS lifecycle_stage,
    -- 建议
    CASE
        WHEN pg.active_months <= 1 THEN '推广新品'
        WHEN pg.active_months <= 3 AND pg.monthly_qty < pg.avg_monthly_qty * 0.5 THEN '加大营销'
        WHEN (pg.monthly_qty - pg.prev_qtr_qty) * 100.0 / NULLIF(pg.prev_qtr_qty, 0) > 30 THEN '增加库存备货'
        WHEN (pg.monthly_qty - pg.prev_qtr_qty) * 100.0 / NULLIF(pg.prev_qtr_qty, 0) BETWEEN -10 AND 30 THEN '正常维护'
        WHEN (pg.monthly_qty - pg.prev_qtr_qty) * 100.0 / NULLIF(pg.prev_qtr_qty, 0) < -10
            AND pg.monthly_qty < pg.avg_monthly_qty * 0.5 THEN '考虑清仓退出'
        ELSE '密切观察'
    END AS action_recommendation
FROM product_growth pg
JOIN products p ON pg.product_id = p.id
JOIN product_categories pc ON p.category_id = pc.id
WHERE pg.sale_month = DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 MONTH), '%Y-%m')
  AND pg.prev_month_qty IS NOT NULL
ORDER BY lifecycle_stage, mom_growth_pct DESC;


-- ============================================================
-- Q113: 门店综合业绩PK榜 - 多维指标加权排名
-- 语法: CTE + 多维度聚合 + 加权评分
-- 业务: 区域经理对比各门店的综合表现
-- 统计原理: 综合分 = 销售额(25%) + 利润率(20%) + 回款率(15%)
--          + 库存周转(15%) + 客户满意度(10%) + 人效(15%)
-- ============================================================

WITH store_sales AS (
    SELECT
        so.warehouse_id,
        SUM(so.total_amount) AS total_sales,
        SUM(so.paid_amount) AS total_collected,
        SUM(soi.quantity * p.purchase_price) AS total_cost,
        COUNT(DISTINCT so.id) AS order_count,
        COUNT(DISTINCT so.customer_id) AS customer_count,
        COUNT(DISTINCT so.salesperson_id) AS salesperson_count
    FROM sales_orders so
    JOIN sales_order_items soi ON so.id = soi.order_id
    JOIN products p ON soi.product_id = p.id
    WHERE so.order_date >= DATE_SUB(CURDATE(), INTERVAL 3 MONTH)
      AND so.status NOT IN ('draft', 'cancelled')
    GROUP BY so.warehouse_id
),
store_inventory AS (
    SELECT
        i.warehouse_id,
        SUM(i.quantity * p.purchase_price) AS inventory_value,
        SUM(i.quantity) AS total_units,
        AVG(i.quantity) AS avg_units_per_product
    FROM inventory i
    JOIN products p ON i.product_id = p.id
    GROUP BY i.warehouse_id
),
store_returns AS (
    SELECT
        sr.warehouse_id,
        COUNT(*) AS return_count,
        SUM(sr.refund_amount) AS total_refund,
        AVG(DATEDIFF(sr.return_date, so.order_date)) AS avg_return_days
    FROM sales_returns sr
    JOIN sales_orders so ON sr.order_id = so.id
    WHERE sr.return_date >= DATE_SUB(CURDATE(), INTERVAL 3 MONTH)
      AND sr.status NOT IN ('rejected')
    GROUP BY sr.warehouse_id
),
store_attendance AS (
    SELECT
        w.id AS warehouse_id,
        COUNT(DISTINCT e.id) AS staff_count,
        ROUND(AVG(CASE WHEN a.status IN ('normal','late','early','overtime') THEN 1 ELSE 0 END) * 100, 1) AS avg_attendance_rate
    FROM warehouses w
    JOIN employees e ON w.manager_id = e.manager_id OR e.department_id = 5
    LEFT JOIN attendance a ON e.id = a.employee_id
        AND a.attendance_date >= DATE_SUB(CURDATE(), INTERVAL 3 MONTH)
    WHERE w.status = 'active'
    GROUP BY w.id
)
SELECT
    w.name AS store_name,
    w.city,
    w.type AS store_type,
    COALESCE(ss.total_sales, 0) AS sales_3m,
    COALESCE(ss.total_cost, 0) AS cost_3m,
    COALESCE(ss.total_sales, 0) - COALESCE(ss.total_cost, 0) AS gross_profit_3m,
    ROUND((COALESCE(ss.total_sales, 0) - COALESCE(ss.total_cost, 0)) * 100.0
        / NULLIF(COALESCE(ss.total_sales, 0), 0), 1) AS margin_pct,
    ROUND(COALESCE(ss.total_collected, 0) * 100.0
        / NULLIF(COALESCE(ss.total_sales, 0), 0), 1) AS collection_rate,
    COALESCE(ss.customer_count, 0) AS customers,
    ROUND(COALESCE(ss.total_sales, 0) / NULLIF(COALESCE(ss.salesperson_count, 0), 0), 2) AS sales_per_head,
    ROUND(COALESCE(si.inventory_value, 0) * 90.0 / NULLIF(COALESCE(ss.total_cost, 0), 0), 1) AS inventory_days,
    COALESCE(sr.return_count, 0) AS return_count,
    ROUND(COALESCE(sr.total_refund, 0) * 100.0 / NULLIF(COALESCE(ss.total_sales, 0), 0), 1) AS return_rate_pct,
    COALESCE(sa.staff_count, 0) AS staff_count,
    COALESCE(sa.avg_attendance_rate, 0) AS attendance_rate,
    -- 综合评分
    ROUND(
        (COALESCE(ss.total_sales, 0) / NULLIF((SELECT MAX(total_sales) FROM store_sales), 0)) * 25
        + ((COALESCE(ss.total_sales, 0) - COALESCE(ss.total_cost, 0)) * 100.0
            / NULLIF(COALESCE(ss.total_sales, 0), 0) / 100) * 20
        + (COALESCE(ss.total_collected, 0) * 100.0
            / NULLIF(COALESCE(ss.total_sales, 0), 0) / 100) * 15
        + (1 - COALESCE(sr.return_count, 0) / NULLIF(COALESCE(ss.order_count, 0), 0)) * 15
        + (COALESCE(ss.total_sales, 0) / NULLIF(COALESCE(ss.salesperson_count, 0), 0)
            / NULLIF((SELECT MAX(sales_per_head) FROM (
                SELECT COALESCE(ss2.total_sales, 0) / NULLIF(COALESCE(ss2.salesperson_count, 0), 0) AS sales_per_head
                FROM store_sales ss2
            ) t), 0)) * 15
        + (COALESCE(sa.avg_attendance_rate, 0) / 100) * 10
    , 1) AS composite_score,
    RANK() OVER (ORDER BY
        (COALESCE(ss.total_sales, 0) / NULLIF((SELECT MAX(total_sales) FROM store_sales), 0)) * 25
        + ((COALESCE(ss.total_sales, 0) - COALESCE(ss.total_cost, 0)) * 100.0
            / NULLIF(COALESCE(ss.total_sales, 0), 0) / 100) * 20
        + (COALESCE(ss.total_collected, 0) * 100.0
            / NULLIF(COALESCE(ss.total_sales, 0), 0) / 100) * 15
        + (1 - COALESCE(sr.return_count, 0) / NULLIF(COALESCE(ss.order_count, 0), 0)) * 15
        + (COALESCE(ss.total_sales, 0) / NULLIF(COALESCE(ss.salesperson_count, 0), 0)
            / NULLIF((SELECT MAX(sales_per_head) FROM (
                SELECT COALESCE(ss3.total_sales, 0) / NULLIF(COALESCE(ss3.salesperson_count, 0), 0) AS sales_per_head
                FROM store_sales ss3
            ) t2), 0)) * 15
        + (COALESCE(sa.avg_attendance_rate, 0) / 100) * 10
    DESC) AS overall_rank
FROM warehouses w
LEFT JOIN store_sales ss ON w.id = ss.warehouse_id
LEFT JOIN store_inventory si ON w.id = si.warehouse_id
LEFT JOIN store_returns sr ON w.id = sr.warehouse_id
LEFT JOIN store_attendance sa ON w.id = sa.warehouse_id
WHERE w.status = 'active'
ORDER BY composite_score DESC;


-- ============================================================
-- Q114: 异常价格波动检测 - 同一产品不同供应商价格差异
-- 语法: CTE + 窗口函数 + 离群值检测
-- 业务: 采购监控价格异常，发现价格欺诈或错误定价
-- 统计原理: Z-score = (价格 - 均价) / 标准差
--          |Z-score| > 2 = 异常价格
--          价格变异系数 = 标准差 / 均价
-- ============================================================

WITH product_price_stats AS (
    SELECT
        sp.product_id,
        p.sku,
        p.name AS product_name,
        s.name AS supplier_name,
        sp.supplier_price,
        sp.lead_time_days,
        sp.quality_score,
        sp.return_rate,
        AVG(sp.supplier_price) OVER (PARTITION BY sp.product_id) AS avg_price,
        STDDEV(sp.supplier_price) OVER (PARTITION BY sp.product_id) AS stddev_price,
        MIN(sp.supplier_price) OVER (PARTITION BY sp.product_id) AS min_price,
        MAX(sp.supplier_price) OVER (PARTITION BY sp.product_id) AS max_price,
        COUNT(*) OVER (PARTITION BY sp.product_id) AS supplier_count
    FROM supplier_products sp
    JOIN products p ON sp.product_id = p.id
    JOIN suppliers s ON sp.supplier_id = s.id
    WHERE p.status = 'active' AND s.cooperation_status = 'active'
)
SELECT
    sku,
    product_name,
    supplier_name,
    supplier_price,
    avg_price,
    ROUND(stddev_price, 2) AS stddev_price,
    ROUND((supplier_price - avg_price) / NULLIF(stddev_price, 0), 2) AS z_score,
    ROUND((max_price - min_price) * 100.0 / NULLIF(avg_price, 0), 1) AS price_spread_pct,
    ROUND(stddev_price * 100.0 / NULLIF(avg_price, 0), 1) AS coefficient_of_variation_pct,
    lead_time_days,
    quality_score,
    ROUND(return_rate * 100, 2) AS return_rate_pct,
    supplier_count,
    CASE
        WHEN (supplier_price - avg_price) / NULLIF(stddev_price, 0) > 2 THEN '价格偏高异常'
        WHEN (supplier_price - avg_price) / NULLIF(stddev_price, 0) < -2 THEN '价格偏低异常'
        WHEN supplier_price = max_price AND max_price > avg_price * 1.2 THEN '最高价'
        WHEN supplier_price = min_price AND min_price < avg_price * 0.8 THEN '最低价'
        ELSE '正常'
    END AS price_anomaly,
    CASE
        WHEN (supplier_price - avg_price) / NULLIF(stddev_price, 0) > 2 THEN '建议重新议价或更换供应商'
        WHEN (supplier_price - avg_price) / NULLIF(stddev_price, 0) < -2 THEN '检查是否为促销价或质量差异'
        WHEN supplier_price = max_price AND max_price > avg_price * 1.2 THEN '关注价格合理性'
        ELSE ''
    END AS recommendation
FROM product_price_stats
WHERE supplier_count >= 2
  AND (ABS((supplier_price - avg_price) / NULLIF(stddev_price, 0)) > 1.5
       OR (max_price - min_price) * 100.0 / NULLIF(avg_price, 0) > 20)
ORDER BY ABS(z_score) DESC
LIMIT 50;
