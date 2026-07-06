-- ============================================================
-- 第六批超复杂SQL: 退货退款 + 报损分析 + 财务影响
-- ============================================================

USE erp_system;


-- ============================================================
-- Q68: 退货原因根因分析 - 按品类/门店/供应商交叉
-- 语法: CTE + 多维度交叉 + 原因占比 + 饼图数据
-- 业务场景: 哪个品类退货最严重？什么原因？哪个供应商质量问题最多？
-- ============================================================

WITH return_detail AS (
    SELECT
        sr.return_type,
        sr.return_reason,
        pc.name AS category_name,
        w.name AS store_name,
        p.sku,
        p.name AS product_name,
        (SELECT s.name FROM suppliers s
         JOIN product_batches pb ON s.id = pb.supplier_id
         WHERE pb.product_id = p.id LIMIT 1
        ) AS supplier_name,
        sri.return_qty,
        sri.amount,
        sri.status AS item_status,
        sr.return_date
    FROM sales_returns sr
    JOIN sales_return_items sri ON sr.id = sri.return_id
    JOIN products p ON sri.product_id = p.id
    JOIN product_categories pc ON p.category_id = pc.id
    JOIN warehouses w ON sr.warehouse_id = w.id
    WHERE sr.return_date >= DATE_SUB(CURDATE(), INTERVAL 6 MONTH)
)
SELECT
    category_name,
    return_type,
    COUNT(*) AS return_count,
    SUM(return_qty) AS total_return_qty,
    SUM(amount) AS total_return_amount,
    ROUND(SUM(amount) * 100.0 / NULLIF(SUM(SUM(amount)) OVER (), 0), 2) AS return_pct,
    COUNT(DISTINCT sku) AS affected_products,
    COUNT(DISTINCT store_name) AS affected_stores,
    COUNT(DISTINCT supplier_name) AS affected_suppliers,
    -- 报废率
    SUM(CASE WHEN item_status = 'scrapped' THEN return_qty ELSE 0 END) AS scrapped_qty,
    ROUND(SUM(CASE WHEN item_status = 'scrapped' THEN return_qty ELSE 0 END) * 100.0
        / NULLIF(SUM(return_qty), 0), 2) AS scrap_rate_pct,
    GROUP_CONCAT(DISTINCT supplier_name ORDER BY supplier_name SEPARATOR ', ') AS suppliers,
    RANK() OVER (ORDER BY SUM(amount) DESC) AS return_rank
FROM return_detail
GROUP BY category_name, return_type
ORDER BY total_return_amount DESC;


-- ============================================================
-- Q69: 退货与客户流失关联分析
-- 语法: CTE + 窗口函数 + 行为对比
-- 业务场景: 退货的客户是否更容易流失？
-- ============================================================

WITH customer_return_stats AS (
    SELECT
        sr.customer_id,
        COUNT(DISTINCT sr.id) AS return_count,
        SUM(sr.total_amount) AS total_return_amount,
        MAX(sr.return_date) AS last_return_date,
        MIN(sr.return_date) AS first_return_date
    FROM sales_returns sr
    WHERE sr.status NOT IN ('rejected')
    GROUP BY sr.customer_id
),
customer_purchase_after_return AS (
    SELECT
        crs.customer_id,
        COUNT(DISTINCT so.id) AS orders_after_first_return,
        SUM(so.total_amount) AS spent_after_first_return,
        MAX(so.order_date) AS last_order_date
    FROM customer_return_stats crs
    JOIN sales_orders so ON crs.customer_id = so.customer_id
    WHERE so.order_date > crs.first_return_date
      AND so.status NOT IN ('draft', 'cancelled')
    GROUP BY crs.customer_id
),
customer_all_stats AS (
    SELECT
        c.id AS customer_id,
        c.name AS customer_name,
        c.membership_level,
        COUNT(DISTINCT so.id) AS total_orders,
        SUM(so.total_amount) AS total_spent,
        MAX(so.order_date) AS last_order_date_all
    FROM customers c
    LEFT JOIN sales_orders so ON c.id = so.customer_id
        AND so.status NOT IN ('draft', 'cancelled')
    GROUP BY c.id, c.name, c.membership_level
)
SELECT
    cas.customer_name,
    cas.membership_level,
    cas.total_orders,
    COALESCE(crs.return_count, 0) AS return_count,
    COALESCE(crs.total_return_amount, 0) AS total_return_amount,
    ROUND(COALESCE(crs.total_return_amount, 0) * 100.0 / NULLIF(cas.total_spent, 0), 2) AS return_rate_pct,
    COALESCE(cpar.orders_after_first_return, 0) AS orders_after_return,
    DATEDIFF(CURDATE(), cas.last_order_date_all) AS days_since_last_order,
    -- 流失判断
    CASE
        WHEN crs.return_count > 0 AND DATEDIFF(CURDATE(), cas.last_order_date_all) > 90 THEN '退货后流失'
        WHEN crs.return_count > 0 AND DATEDIFF(CURDATE(), cas.last_order_date_all) > 30 THEN '退货后活跃度下降'
        WHEN crs.return_count = 0 AND DATEDIFF(CURDATE(), cas.last_order_date_all) > 90 THEN '无退货但流失'
        WHEN crs.return_count > 0 THEN '退货但仍在购买'
        ELSE '正常'
    END AS customer_status,
    CASE
        WHEN crs.return_count > 2 AND COALESCE(crs.total_return_amount, 0) * 100.0 / NULLIF(cas.total_spent, 0) > 30
        THEN '高退货率客户-需关注'
        WHEN crs.return_count > 0 AND COALESCE(crs.total_return_amount, 0) * 100.0 / NULLIF(cas.total_spent, 0) > 15
        THEN '中退货率客户'
        ELSE '正常'
    END AS return_risk_label
FROM customer_all_stats cas
LEFT JOIN customer_return_stats crs ON cas.customer_id = crs.customer_id
LEFT JOIN customer_purchase_after_return cpar ON cas.customer_id = cpar.customer_id
ORDER BY COALESCE(crs.total_return_amount, 0) DESC;


-- ============================================================
-- Q70: 退货审批效率分析 - 审批时长分布
-- 语法: CTE + 条件聚合 + 百分位数
-- 业务场景: 退货审批流程耗时分析，找瓶颈
-- ============================================================

SELECT
    sr.return_type,
    COUNT(*) AS total_returns,
    COUNT(CASE WHEN sr.status IN ('approved', 'received', 'inspected', 'refunded', 'closed') THEN 1 END) AS approved_count,
    COUNT(CASE WHEN sr.status = 'rejected' THEN 1 END) AS rejected_count,
    COUNT(CASE WHEN sr.status = 'pending' THEN 1 END) AS pending_count,
    AVG(CASE WHEN sr.approved_at IS NOT NULL
        THEN TIMESTAMPDIFF(HOUR, sr.created_at, sr.approved_at) END) AS avg_approval_hours,
    AVG(CASE WHEN sr.status = 'refunded'
        THEN TIMESTAMPDIFF(HOUR, sr.approved_at, sr.updated_at) END) AS avg_refund_processing_hours,
    AVG(CASE WHEN sr.status = 'refunded'
        THEN TIMESTAMPDIFF(HOUR, sr.created_at, sr.updated_at) END) AS avg_total_turnaround_hours,
    ROUND(COUNT(CASE WHEN sr.status IN ('approved', 'received', 'inspected', 'refunded', 'closed') THEN 1 END) * 100.0
        / NULLIF(COUNT(*), 0), 2) AS approval_rate_pct,
    SUM(sr.refund_amount) AS total_refunded,
    AVG(sr.refund_amount) AS avg_refund_amount,
    AVG(sr.restock_fee) AS avg_restock_fee,
    SUM(sr.return_shipping_fee) AS total_shipping_fee
FROM sales_returns sr
WHERE sr.return_date >= DATE_SUB(CURDATE(), INTERVAL 6 MONTH)
GROUP BY sr.return_type
ORDER BY total_returns DESC;


-- ============================================================
-- Q71: 供应商质量评分 - 基于退货和质检
-- 语法: CTE + 多表JOIN + 加权评分
-- 业务场景: 综合退货率+质检不合格率+交货延迟率评分供应商
-- ============================================================

WITH supplier_return_stats AS (
    SELECT
        pb.supplier_id,
        COUNT(DISTINCT pr.id) AS purchase_return_count,
        SUM(pri.return_qty * pri.unit_price) AS total_return_amount,
        COUNT(DISTINCT CASE WHEN pr.return_type = 'quality' THEN pr.id END) AS quality_return_count
    FROM purchase_returns pr
    JOIN purchase_return_items pri ON pr.id = pri.return_id
    JOIN product_batches pb ON pri.batch_id = pb.id
    WHERE pr.return_date >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH)
    GROUP BY pb.supplier_id
),
supplier_purchase_stats AS (
    SELECT
        po.supplier_id,
        COUNT(DISTINCT po.id) AS total_orders,
        SUM(po.total_amount) AS total_purchase_amount,
        COUNT(DISTINCT CASE WHEN po.actual_delivery_date > po.expected_delivery_date THEN po.id END) AS delayed_orders
    FROM purchase_orders po
    WHERE po.order_date >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH)
    GROUP BY po.supplier_id
),
supplier_inspection_stats AS (
    SELECT
        pb.supplier_id,
        COUNT(DISTINCT ir.id) AS total_inspections,
        COUNT(DISTINCT CASE WHEN ir.inspection_result = 'qualified' THEN ir.id END) AS qualified_count,
        COUNT(DISTINCT CASE WHEN ir.inspection_result = 'rejected' THEN ir.id END) AS rejected_count
    FROM inspection_reports ir
    JOIN product_batches pb ON ir.batch_id = pb.id
    WHERE ir.inspection_date >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH)
    GROUP BY pb.supplier_id
)
SELECT
    s.name AS supplier_name,
    s.credit_level AS current_level,
    sps.total_orders,
    sps.total_purchase_amount,
    sps.delayed_orders,
    ROUND(sps.delayed_orders * 100.0 / NULLIF(sps.total_orders, 0), 2) AS delay_rate_pct,
    COALESCE(srs.purchase_return_count, 0) AS return_count,
    COALESCE(srs.total_return_amount, 0) AS return_amount,
    ROUND(COALESCE(srs.total_return_amount, 0) * 100.0 / NULLIF(sps.total_purchase_amount, 0), 2) AS return_rate_pct,
    COALESCE(sis.total_inspections, 0) AS inspection_count,
    COALESCE(sis.qualified_count, 0) AS qualified_count,
    ROUND(COALESCE(sis.qualified_count, 0) * 100.0 / NULLIF(sis.total_inspections, 0), 2) AS inspection_pass_rate,
    -- 综合质量评分
    ROUND(
        (100 - COALESCE(sps.delayed_orders * 100.0 / NULLIF(sps.total_orders, 0), 0) * 2) * 0.30
        + (100 - COALESCE(srs.total_return_amount, 0) * 100.0 / NULLIF(sps.total_purchase_amount, 0) * 10) * 0.35
        + COALESCE(sis.qualified_count * 100.0 / NULLIF(sis.total_inspections, 0), 100) * 0.35
    , 0) AS quality_score,
    RANK() OVER (ORDER BY
        (100 - COALESCE(sps.delayed_orders * 100.0 / NULLIF(sps.total_orders, 0), 0) * 2) * 0.30
        + (100 - COALESCE(srs.total_return_amount, 0) * 100.0 / NULLIF(sps.total_purchase_amount, 0) * 10) * 0.35
        + COALESCE(sis.qualified_count * 100.0 / NULLIF(sis.total_inspections, 0), 100) * 0.35
    DESC) AS quality_rank
FROM suppliers s
LEFT JOIN supplier_purchase_stats sps ON s.id = sps.supplier_id
LEFT JOIN supplier_return_stats srs ON s.id = srs.supplier_id
LEFT JOIN supplier_inspection_stats sis ON s.id = sis.supplier_id
WHERE s.cooperation_status = 'active'
ORDER BY quality_score ASC;


-- ============================================================
-- Q72: 报损损失趋势分析 - 按类型/门店/月份
-- 语法: CTE + 窗口函数 + 按月汇总 + 累计
-- 业务场景: 报损金额月度趋势，哪类报损最多
-- ============================================================

SELECT
    DATE_FORMAT(dr.report_date, '%Y-%m') AS month,
    dr.report_type,
    w.name AS store_name,
    COUNT(DISTINCT dr.id) AS report_count,
    SUM(dr.total_quantity) AS total_qty_lost,
    SUM(dr.total_loss_amount) AS total_loss,
    ROUND(SUM(dr.total_loss_amount) * 100.0 / NULLIF(SUM(SUM(dr.total_loss_amount)) OVER (PARTITION BY DATE_FORMAT(dr.report_date, '%Y-%m')), 0), 2) AS loss_share_in_month,
    SUM(SUM(dr.total_loss_amount)) OVER (PARTITION BY dr.report_type ORDER BY DATE_FORMAT(dr.report_date, '%Y-%m')
        ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS cumulative_loss_by_type,
    LAG(SUM(dr.total_loss_amount)) OVER (PARTITION BY dr.report_type, w.name ORDER BY DATE_FORMAT(dr.report_date, '%Y-%m')) AS prev_month_loss,
    ROUND((SUM(dr.total_loss_amount) - LAG(SUM(dr.total_loss_amount)) OVER (PARTITION BY dr.report_type, w.name ORDER BY DATE_FORMAT(dr.report_date, '%Y-%m')))
        / NULLIF(LAG(SUM(dr.total_loss_amount)) OVER (PARTITION BY dr.report_type, w.name ORDER BY DATE_FORMAT(dr.report_date, '%Y-%m')), 0) * 100, 2) AS mom_change_pct,
    CASE
        WHEN SUM(dr.total_loss_amount) > LAG(SUM(dr.total_loss_amount)) OVER (PARTITION BY dr.report_type, w.name ORDER BY DATE_FORMAT(dr.report_date, '%Y-%m')) * 1.5
        THEN '异常增长'
        ELSE '正常'
    END AS alert_flag
FROM damage_reports dr
JOIN warehouses w ON dr.warehouse_id = w.id
WHERE dr.status = 'executed'
GROUP BY DATE_FORMAT(dr.report_date, '%Y-%m'), dr.report_type, w.name
ORDER BY month DESC, total_loss DESC;


-- ============================================================
-- Q73: 退货入库vs报废 - 退货商品处理方式分析
-- 语法: 条件聚合 + 多维度对比
-- 业务场景: 退货商品有多少被重新入库，多少被报废
-- ============================================================

SELECT
    pc.name AS category_name,
    p.sku,
    p.name AS product_name,
    COUNT(DISTINCT sr.id) AS return_count,
    SUM(sri.return_qty) AS total_return_qty,
    SUM(CASE WHEN sri.status = 'restocked' THEN sri.return_qty ELSE 0 END) AS restocked_qty,
    SUM(CASE WHEN sri.status = 'scrapped' THEN sri.return_qty ELSE 0 END) AS scrapped_qty,
    SUM(CASE WHEN sri.status = 'pending' THEN sri.return_qty ELSE 0 END) AS pending_qty,
    ROUND(SUM(CASE WHEN sri.status = 'restocked' THEN sri.return_qty ELSE 0 END) * 100.0
        / NULLIF(SUM(sri.return_qty), 0), 2) AS restock_rate_pct,
    ROUND(SUM(CASE WHEN sri.status = 'scrapped' THEN sri.return_qty ELSE 0 END) * 100.0
        / NULLIF(SUM(sri.return_qty), 0), 2) AS scrap_rate_pct,
    SUM(sri.amount) AS total_return_amount,
    ROUND(SUM(CASE WHEN sri.status = 'scrapped' THEN sri.amount ELSE 0 END), 2) AS scrapped_amount,
    ROUND(SUM(CASE WHEN sri.status = 'restocked' THEN sri.amount ELSE 0 END), 2) AS restocked_amount,
    -- 净损失
    ROUND(SUM(CASE WHEN sri.status = 'scrapped' THEN sri.amount ELSE 0 END)
        + SUM(CASE WHEN sri.status = 'restocked' THEN sri.amount * 0.1 ELSE 0 END), 2) AS estimated_net_loss,
    RANK() OVER (ORDER BY SUM(sri.amount) DESC) AS return_amount_rank
FROM sales_return_items sri
JOIN sales_returns sr ON sri.return_id = sr.id
JOIN products p ON sri.product_id = p.id
JOIN product_categories pc ON p.category_id = pc.id
WHERE sr.return_date >= DATE_SUB(CURDATE(), INTERVAL 6 MONTH)
GROUP BY pc.name, p.sku, p.name
HAVING total_return_qty > 0
ORDER BY total_return_amount DESC
LIMIT 30;


-- ============================================================
-- Q74: 退货财务影响 - 收入冲减+成本恢复+净损益
-- 语法: CTE + 条件聚合 + 损益计算
-- 业务场景: 每笔退货对财务报表的影响
-- ============================================================

WITH return_pl AS (
    SELECT
        DATE_FORMAT(sr.return_date, '%Y-%m') AS month,
        sr.id AS return_id,
        sr.return_no,
        sr.return_type,
        -- 收入冲减
        sr.refund_amount AS revenue_reversal,
        -- 成本恢复(合格品)
        SUM(CASE WHEN sri.status = 'restocked' THEN sri.return_qty * p.purchase_price ELSE 0 END) AS cost_recovery,
        -- 报废损失
        SUM(CASE WHEN sri.status = 'scrapped' THEN sri.return_qty * p.purchase_price ELSE 0 END) AS scrap_loss,
        -- 手续费
        sr.restock_fee + sr.return_shipping_fee AS handling_fee,
        -- 净损益 = 收入冲减 - 成本恢复 + 报废损失 + 手续费
        sr.refund_amount
            - SUM(CASE WHEN sri.status = 'restocked' THEN sri.return_qty * p.purchase_price ELSE 0 END)
            + SUM(CASE WHEN sri.status = 'scrapped' THEN sri.return_qty * p.purchase_price ELSE 0 END)
            + sr.restock_fee + sr.return_shipping_fee AS net_pl_impact
    FROM sales_returns sr
    JOIN sales_return_items sri ON sr.id = sri.return_id
    JOIN products p ON sri.product_id = p.id
    WHERE sr.status = 'refunded'
    GROUP BY sr.id, sr.return_no, sr.return_type, sr.return_date, sr.refund_amount, sr.restock_fee, sr.return_shipping_fee
)
SELECT
    month,
    COUNT(*) AS return_count,
    SUM(revenue_reversal) AS total_revenue_reversal,
    SUM(cost_recovery) AS total_cost_recovery,
    SUM(scrap_loss) AS total_scrap_loss,
    SUM(handling_fee) AS total_handling_fee,
    SUM(net_pl_impact) AS total_net_pl_impact,
    ROUND(SUM(net_pl_impact) * 100.0 / NULLIF(SUM(revenue_reversal), 0), 2) AS net_loss_rate_pct,
    -- 累计年度损益影响
    SUM(SUM(net_pl_impact)) OVER (ORDER BY month ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS ytd_net_impact
FROM return_pl
GROUP BY month
ORDER BY month DESC;


-- ============================================================
-- Q75: 入库出库与退货联动分析 - 库存变动全貌
-- 语法: CTE + UNION ALL + 多视角汇总
-- 业务场景: 一段时间内入库/出库/退货/报损的库存变动全景
-- ============================================================

WITH inventory_movement AS (
    SELECT
        DATE_FORMAT(it.created_at, '%Y-%m') AS month,
        w.name AS store_name,
        it.transaction_type,
        CASE
            WHEN it.transaction_type IN ('purchase_in', 'return_in', 'transfer_in') THEN '入库'
            WHEN it.transaction_type IN ('sales_out', 'return_out', 'transfer_out') THEN '出库'
            WHEN it.transaction_type IN ('stocktake_adjust') THEN '盘点调整'
            WHEN it.transaction_type IN ('damage_out', 'scrap_out') THEN '报废'
            ELSE '其他'
        END AS movement_category,
        SUM(ABS(it.quantity_change)) AS total_qty,
        SUM(ABS(it.quantity_change) * p.purchase_price) AS total_value,
        COUNT(DISTINCT it.product_id) AS distinct_products
    FROM inventory_transactions it
    JOIN warehouses w ON it.warehouse_id = w.id
    JOIN products p ON it.product_id = p.id
    WHERE it.created_at >= DATE_SUB(CURDATE(), INTERVAL 6 MONTH)
    GROUP BY DATE_FORMAT(it.created_at, '%Y-%m'), w.name, it.transaction_type
)
SELECT
    month,
    store_name,
    movement_category,
    transaction_type,
    total_qty,
    total_value,
    distinct_products,
    ROUND(total_value * 100.0 / NULLIF(SUM(total_value) OVER (PARTITION BY month, store_name), 0), 2) AS category_share_pct,
    SUM(total_value) OVER (PARTITION BY store_name, movement_category ORDER BY month
        ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS cumulative_value,
    RANK() OVER (PARTITION BY month, store_name ORDER BY total_value DESC) AS category_rank
FROM inventory_movement
ORDER BY month DESC, store_name, total_value DESC;