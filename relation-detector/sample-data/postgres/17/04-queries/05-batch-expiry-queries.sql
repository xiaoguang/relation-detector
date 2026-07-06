-- ============================================================
-- 第五批超复杂SQL: 批号保质期 + 类别销售/临期深度分析
-- 覆盖: 门店批号追踪、类别临期热力图、保质期预警、
--        类别动销对比、临期vs销售健康度、FIFO执行检查
-- ============================================================


-- ============================================================
-- Q61: 全门店批号保质期热力图 - 门店x类别交叉
-- 语法: CROSS JOIN + 条件聚合 + 多级CASE颜色标签
-- 业务场景: 高管一眼看清哪些门店哪些类别的临期问题最严重
-- ============================================================

SELECT
    w.name AS store_name,
    pc.name AS category_name,
    COUNT(DISTINCT pb.id) AS batch_count,
    SUM(i.quantity) AS total_stock,
    ROUND(SUM(i.quantity * p.purchase_price), 2) AS total_value,
    -- 已过期
    SUM(CASE WHEN pb.expiry_date < CURRENT_DATE THEN i.quantity ELSE 0 END) AS expired_qty,
    -- 7天内
    SUM(CASE WHEN pb.expiry_date BETWEEN CURRENT_DATE AND CURRENT_DATE + INTERVAL '7 days' THEN i.quantity ELSE 0 END) AS expiry_7d,
    -- 30天内
    SUM(CASE WHEN pb.expiry_date BETWEEN CURRENT_DATE + INTERVAL '8 days' AND CURRENT_DATE + INTERVAL '30 days' THEN i.quantity ELSE 0 END) AS expiry_30d,
    -- 60天内
    SUM(CASE WHEN pb.expiry_date BETWEEN CURRENT_DATE + INTERVAL '31 days' AND CURRENT_DATE + INTERVAL '60 days' THEN i.quantity ELSE 0 END) AS expiry_60d,
    -- 临期率
    ROUND(
        SUM(CASE WHEN pb.expiry_date <= CURRENT_DATE + INTERVAL '60 days' THEN i.quantity ELSE 0 END) * 100.0
        / NULLIF(SUM(i.quantity), 0), 2
    ) AS near_expiry_rate_pct,
    -- 近3月销售额
    COALESCE((
        SELECT SUM(soi.amount)
        FROM sales_order_items soi
        JOIN sales_orders so ON soi.order_id = so.id
        JOIN products pp ON soi.product_id = pp.id
        WHERE so.warehouse_id = w.id
          AND pp.category_id = pc.id
          AND so.order_date >= CURRENT_DATE - INTERVAL '3 months'
          AND so.status NOT IN ('draft', 'cancelled')
    ), 0) AS sales_3m,
    -- 临期库存可售天数(基于日均销量)
    ROUND(
        SUM(CASE WHEN pb.expiry_date <= CURRENT_DATE + INTERVAL '60 days' THEN i.quantity ELSE 0 END)
        / NULLIF(COALESCE((
            SELECT SUM(soi.quantity) / 90.0
            FROM sales_order_items soi
            JOIN sales_orders so ON soi.order_id = so.id
            JOIN products pp ON soi.product_id = pp.id
            WHERE so.warehouse_id = w.id
              AND pp.category_id = pc.id
              AND so.order_date >= CURRENT_DATE - INTERVAL '90 days'
              AND so.status NOT IN ('draft', 'cancelled')
        ), 0), 0)
    , 0) AS days_to_sell_near_expiry,
    -- 严重程度
    CASE
        WHEN SUM(CASE WHEN pb.expiry_date < CURRENT_DATE THEN i.quantity ELSE 0 END) > 0 THEN '已过期'
        WHEN SUM(CASE WHEN pb.expiry_date <= CURRENT_DATE + INTERVAL '60 days' THEN i.quantity ELSE 0 END) * 100.0
            / NULLIF(SUM(i.quantity), 0) > 30 THEN '高危'
        WHEN SUM(CASE WHEN pb.expiry_date <= CURRENT_DATE + INTERVAL '60 days' THEN i.quantity ELSE 0 END) * 100.0
            / NULLIF(SUM(i.quantity), 0) > 15 THEN '警告'
        WHEN SUM(CASE WHEN pb.expiry_date <= CURRENT_DATE + INTERVAL '60 days' THEN i.quantity ELSE 0 END) * 100.0
            / NULLIF(SUM(i.quantity), 0) > 5 THEN '关注'
        ELSE '正常'
    END AS severity,
    MIN(pb.expiry_date) AS earliest_expiry,
    -- 最早过期批号
    (SELECT pb2.batch_no FROM product_batches pb2
     JOIN inventory i2 ON pb2.id = i2.batch_id
     JOIN products p2 ON pb2.product_id = p2.id
     WHERE i2.warehouse_id = w.id
       AND p2.category_id = pc.id
       AND pb2.status = 'active' AND pb2.current_qty > 0
     ORDER BY pb2.expiry_date ASC LIMIT 1
    ) AS earliest_batch_no
FROM warehouses w
CROSS JOIN product_categories pc
LEFT JOIN products p ON pc.id = p.category_id
LEFT JOIN inventory i ON p.id = i.product_id AND i.warehouse_id = w.id
LEFT JOIN product_batches pb ON i.batch_id = pb.id AND pb.status = 'active' AND pb.current_qty > 0
WHERE pc.parent_id IS NOT NULL
GROUP BY w.id, w.name, pc.id, pc.name
HAVING SUM(i.quantity) > 0
ORDER BY
    CASE severity
        WHEN '已过期' THEN 1 WHEN '高危' THEN 2
        WHEN '警告' THEN 3 WHEN '关注' THEN 4 ELSE 5
    END, near_expiry_rate_pct DESC;


-- ============================================================
-- Q62: 类别综合健康度 - 销售+库存+临期+利润四维分析
-- 语法: 多CTE + 窗口函数 + 四维加权评分
-- 业务场景: 每个类别综合评分，识别需要重点关注的类别
-- ============================================================

WITH cat_sales AS (
    SELECT
        p.category_id,
        SUM(soi.amount) AS total_sales_6m,
        SUM(soi.quantity) AS total_qty_6m,
        SUM(soi.amount - soi.quantity * p.purchase_price) AS total_profit_6m,
        COUNT(DISTINCT so.customer_id) AS customer_count,
        COUNT(DISTINCT soi.product_id) AS active_skus
    FROM sales_order_items soi
    JOIN sales_orders so ON soi.order_id = so.id
    JOIN products p ON soi.product_id = p.id
    WHERE so.order_date >= CURRENT_DATE - INTERVAL '6 months'
      AND so.status NOT IN ('draft', 'cancelled')
    GROUP BY p.category_id
),
cat_inventory AS (
    SELECT
        p.category_id,
        SUM(i.quantity) AS total_stock,
        SUM(i.quantity * p.purchase_price) AS total_stock_value,
        SUM(CASE WHEN pb.expiry_date <= CURRENT_DATE + INTERVAL '60 days' AND pb.status = 'active'
            THEN i.quantity * p.purchase_price ELSE 0 END) AS near_expiry_value,
        COUNT(DISTINCT pb.id) AS batch_count,
        AVG(p.shelf_life_days) AS avg_shelf_life,
        AVG((CURRENT_DATE - pb.production_date)) AS avg_age_days
    FROM products p
    LEFT JOIN inventory i ON p.id = i.product_id
    LEFT JOIN product_batches pb ON i.batch_id = pb.id AND pb.status = 'active'
    GROUP BY p.category_id
),
cat_returns AS (
    SELECT
        p.category_id,
        COUNT(DISTINCT sri.id) AS return_count,
        SUM(sri.amount) AS total_return_amount
    FROM sales_return_items sri
    JOIN products p ON sri.product_id = p.id
    JOIN sales_returns sr ON sri.return_id = sr.id
    WHERE sr.return_date >= CURRENT_DATE - INTERVAL '6 months'
    GROUP BY p.category_id
)
SELECT
    pc.name AS category_name,
    cs.total_sales_6m,
    cs.total_profit_6m,
    ROUND(cs.total_profit_6m * 100.0 / NULLIF(cs.total_sales_6m, 0), 2) AS profit_margin_pct,
    cs.customer_count,
    cs.active_skus,
    ci.total_stock_value,
    ci.near_expiry_value,
    ROUND(ci.near_expiry_value * 100.0 / NULLIF(ci.total_stock_value, 0), 2) AS near_expiry_value_pct,
    ci.batch_count,
    ROUND(ci.avg_age_days, 0) AS avg_age_days,
    ci.avg_shelf_life,
    COALESCE(cr.return_count, 0) AS return_count,
    ROUND(COALESCE(cr.total_return_amount, 0) * 100.0 / NULLIF(cs.total_sales_6m, 0), 2) AS return_rate_pct,
    -- 库存周转率
    ROUND(cs.total_sales_6m / NULLIF(ci.total_stock_value, 0), 2) AS inventory_turnover_6m,
    -- 四维评分
    RANK() OVER (ORDER BY cs.total_sales_6m DESC) AS sales_rank,
    RANK() OVER (ORDER BY cs.total_profit_6m * 100.0 / NULLIF(cs.total_sales_6m, 0) DESC) AS margin_rank,
    RANK() OVER (ORDER BY ci.near_expiry_value * 100.0 / NULLIF(ci.total_stock_value, 0) ASC) AS expiry_rank,
    RANK() OVER (ORDER BY COALESCE(cr.total_return_amount, 0) * 100.0 / NULLIF(cs.total_sales_6m, 0) ASC) AS quality_rank,
    -- 综合排名(越低越好)
    RANK() OVER (ORDER BY
        RANK() OVER (ORDER BY cs.total_sales_6m DESC) * 0.35
        + RANK() OVER (ORDER BY cs.total_profit_6m * 100.0 / NULLIF(cs.total_sales_6m, 0) DESC) * 0.25
        + RANK() OVER (ORDER BY ci.near_expiry_value * 100.0 / NULLIF(ci.total_stock_value, 0) ASC) * 0.25
        + RANK() OVER (ORDER BY COALESCE(cr.total_return_amount, 0) * 100.0 / NULLIF(cs.total_sales_6m, 0) ASC) * 0.15
    ASC) AS composite_rank
FROM product_categories pc
LEFT JOIN cat_sales cs ON pc.id = cs.category_id
LEFT JOIN cat_inventory ci ON pc.id = ci.category_id
LEFT JOIN cat_returns cr ON pc.id = cr.category_id
WHERE pc.parent_id IS NOT NULL
ORDER BY composite_rank ASC;


-- ============================================================
-- Q63: FIFO先进先出执行检查 - 出库批号是否按生产日期顺序
-- 语法: CTE + 窗口函数(LAG) + 异常检测
-- 业务场景: 检查销售出库是否按FIFO原则，即先生产的批号先出库
-- 统计原理: 如果某批号的生产日期晚于之前出库的批号，但被优先出库，则违反FIFO
-- ============================================================

WITH sales_batch_sequence AS (
    SELECT
        soi.product_id,
        p.sku,
        p.name AS product_name,
        soi.batch_id,
        pb.batch_no,
        pb.production_date,
        pb.expiry_date,
        so.order_date AS sale_date,
        so.order_no,
        soi.quantity AS sale_qty,
        ROW_NUMBER() OVER (PARTITION BY soi.product_id ORDER BY so.order_date, so.id) AS sale_seq
    FROM sales_order_items soi
    JOIN sales_orders so ON soi.order_id = so.id
    JOIN products p ON soi.product_id = p.id
    JOIN product_batches pb ON soi.batch_id = pb.id
    WHERE soi.batch_id IS NOT NULL
      AND so.status NOT IN ('draft', 'cancelled')
),
fifo_check AS (
    SELECT
        *,
        LAG(production_date) OVER (PARTITION BY product_id ORDER BY sale_seq) AS prev_sale_production_date,
        LAG(batch_no) OVER (PARTITION BY product_id ORDER BY sale_seq) AS prev_sale_batch_no,
        LAG(sale_date) OVER (PARTITION BY product_id ORDER BY sale_seq) AS prev_sale_date
    FROM sales_batch_sequence
)
SELECT
    sku,
    product_name,
    sale_date,
    order_no,
    batch_no,
    production_date,
    expiry_date,
    sale_qty,
    prev_sale_date,
    prev_sale_batch_no,
    prev_sale_production_date,
    CASE
        WHEN prev_sale_production_date IS NOT NULL
            AND production_date < prev_sale_production_date
        THEN 'FIFO违规-后生产批号先出库'
        ELSE '正常'
    END AS fifo_status,
    -- 违规严重程度(天数差)
    CASE
        WHEN prev_sale_production_date IS NOT NULL
            AND production_date < prev_sale_production_date
        THEN (prev_sale_production_date - production_date)
        ELSE 0
    END AS fifo_violation_days
FROM fifo_check
WHERE prev_sale_production_date IS NOT NULL
  AND production_date < prev_sale_production_date
ORDER BY fifo_violation_days DESC;


-- ============================================================
-- Q64: 类别保质期利用率分析 - 各类别货品在库年龄分布
-- 语法: CTE + 条件聚合 + 年龄分桶 + 利用率计算
-- 业务场景: 分析各类别货品在库时间占保质期的比例
-- 统计原理: 保质期利用率 = 在库天数/保质期天数
--           利用率>80%=高陈旧风险, 50-80%=中等, <50%=健康
-- ============================================================

WITH batch_age AS (
    SELECT
        pc.name AS category_name,
        p.sku,
        p.name AS product_name,
        p.shelf_life_days,
        pb.batch_no,
        pb.production_date,
        pb.expiry_date,
        pb.current_qty,
        (CURRENT_DATE - pb.production_date) AS age_days,
        CASE WHEN p.shelf_life_days > 0
            THEN ROUND((CURRENT_DATE - pb.production_date) * 100.0 / p.shelf_life_days, 1)
            ELSE 0
        END AS shelf_life_used_pct,
        i.warehouse_id,
        w.name AS store_name
    FROM product_batches pb
    JOIN products p ON pb.product_id = p.id
    JOIN product_categories pc ON p.category_id = pc.id
    JOIN inventory i ON pb.id = i.batch_id
    JOIN warehouses w ON i.warehouse_id = w.id
    WHERE pb.status = 'active' AND pb.current_qty > 0
)
SELECT
    category_name,
    CASE
        WHEN shelf_life_used_pct > 100 THEN '已过期'
        WHEN shelf_life_used_pct > 80 THEN '80-100%'
        WHEN shelf_life_used_pct > 50 THEN '50-80%'
        WHEN shelf_life_used_pct > 30 THEN '30-50%'
        WHEN shelf_life_used_pct > 10 THEN '10-30%'
        ELSE '0-10%'
    END AS age_bucket,
    COUNT(DISTINCT batch_no) AS batch_count,
    SUM(current_qty) AS total_qty,
    ROUND(SUM(current_qty * (SELECT purchase_price FROM products WHERE sku = ba.sku)), 2) AS total_value,
    ROUND(AVG(shelf_life_used_pct), 1) AS avg_used_pct,
    ROUND(AVG(age_days), 0) AS avg_age_days,
    AVG(shelf_life_days) AS avg_shelf_life,
    string_agg(DISTINCT store_name, ', ' ORDER BY store_name) AS stores
FROM batch_age ba
GROUP BY category_name, age_bucket
ORDER BY category_name,
    CASE age_bucket
        WHEN '已过期' THEN 1 WHEN '80-100%' THEN 2 WHEN '50-80%' THEN 3
        WHEN '30-50%' THEN 4 WHEN '10-30%' THEN 5 ELSE 6
    END;


-- ============================================================
-- Q65: 临期vs畅销矛盾分析 - 哪些货品临期但仍在热销
-- 语法: CTE + 多维度排名 + 矛盾检测
-- 业务场景: 找出"销售很好但库存即将过期"的矛盾货品
--           这种情况说明采购过量或需求预测不准
-- ============================================================

WITH product_sales_rank AS (
    SELECT
        soi.product_id,
        SUM(soi.amount) AS total_sales_90d,
        SUM(soi.quantity) AS total_qty_90d,
        RANK() OVER (ORDER BY SUM(soi.amount) DESC) AS sales_rank
    FROM sales_order_items soi
    JOIN sales_orders so ON soi.order_id = so.id
    WHERE so.order_date >= CURRENT_DATE - INTERVAL '90 days'
      AND so.status NOT IN ('draft', 'cancelled')
    GROUP BY soi.product_id
),
product_expiry_risk AS (
    SELECT
        p.id AS product_id,
        SUM(CASE WHEN pb.expiry_date <= CURRENT_DATE + INTERVAL '60 days' THEN i.quantity ELSE 0 END) AS near_expiry_qty,
        SUM(i.quantity) AS total_stock,
        ROUND(
            SUM(CASE WHEN pb.expiry_date <= CURRENT_DATE + INTERVAL '60 days' THEN i.quantity ELSE 0 END) * 100.0
            / NULLIF(SUM(i.quantity), 0), 2
        ) AS near_expiry_rate,
        RANK() OVER (ORDER BY
            SUM(CASE WHEN pb.expiry_date <= CURRENT_DATE + INTERVAL '60 days' THEN i.quantity ELSE 0 END) * 100.0
            / NULLIF(SUM(i.quantity), 0) DESC
        ) AS expiry_risk_rank
    FROM products p
    JOIN inventory i ON p.id = i.product_id
    LEFT JOIN product_batches pb ON i.batch_id = pb.id AND pb.status = 'active'
    GROUP BY p.id
    HAVING SUM(i.quantity) > 0
)
SELECT
    p.sku,
    p.name AS product_name,
    pc.name AS category_name,
    psr.total_sales_90d,
    psr.total_qty_90d,
    psr.sales_rank,
    per.near_expiry_qty,
    per.total_stock,
    per.near_expiry_rate,
    per.expiry_risk_rank,
    -- 矛盾程度 = 销售排名(越好) + 临期排名(越差)
    (psr.sales_rank + per.expiry_risk_rank) AS contradiction_score,
    -- 日均销量
    ROUND(psr.total_qty_90d / 90.0, 1) AS avg_daily_sales,
    -- 临期库存可售天数
    ROUND(per.near_expiry_qty / NULLIF(ROUND(psr.total_qty_90d / 90.0, 1), 0), 0) AS days_to_sell_near_expiry,
    CASE
        WHEN psr.sales_rank <= 20 AND per.expiry_risk_rank <= 10 THEN '严重矛盾-热销但临期严重'
        WHEN psr.sales_rank <= 20 AND per.expiry_risk_rank <= 30 THEN '需关注-热销但有临期风险'
        WHEN per.expiry_risk_rank <= 10 AND psr.sales_rank > 30 THEN '滞销临期-需清仓'
        ELSE '正常'
    END AS contradiction_type,
    -- 建议处理
    CASE
        WHEN psr.sales_rank <= 20 AND per.expiry_risk_rank <= 10
            AND per.near_expiry_qty / NULLIF(ROUND(psr.total_qty_90d / 90.0, 1), 0) > 60
        THEN '销量无法消化临期库存，建议打折促销或退货'
        WHEN psr.sales_rank <= 20 AND per.expiry_risk_rank <= 10
        THEN '加速出库临期批次，正常销售可消化'
        WHEN per.expiry_risk_rank <= 10 AND psr.sales_rank > 30
        THEN '滞销临期，建议立即清仓处理'
        ELSE '正常管理'
    END AS suggested_action
FROM product_sales_rank psr
JOIN product_expiry_risk per ON psr.product_id = per.product_id
JOIN products p ON psr.product_id = p.id
JOIN product_categories pc ON p.category_id = pc.id
WHERE per.near_expiry_rate > 0
ORDER BY contradiction_score ASC
LIMIT 30;


-- ============================================================
-- Q66: 门店批号管理效率 - 谁在认真执行FIFO
-- 语法: 多CTE + 窗口函数 + 门店对比 + 过期率排名
-- 业务场景: 对比各门店的过期率和临期处理效率
-- ============================================================

WITH store_expiry_stats AS (
    SELECT
        w.id AS store_id,
        w.name AS store_name,
        (SELECT name FROM employees WHERE id = w.manager_id) AS store_manager,
        COUNT(DISTINCT pb.id) AS total_batches,
        SUM(i.quantity) AS total_stock_qty,
        SUM(i.quantity * p.purchase_price) AS total_stock_value,
        SUM(CASE WHEN pb.expiry_date < CURRENT_DATE THEN i.quantity ELSE 0 END) AS expired_qty,
        ROUND(SUM(CASE WHEN pb.expiry_date < CURRENT_DATE THEN i.quantity * p.purchase_price ELSE 0 END), 2) AS expired_value,
        SUM(CASE WHEN pb.expiry_date BETWEEN CURRENT_DATE AND CURRENT_DATE + INTERVAL '30 days' THEN i.quantity ELSE 0 END) AS near_expiry_30d,
        ROUND(SUM(CASE WHEN pb.expiry_date BETWEEN CURRENT_DATE AND CURRENT_DATE + INTERVAL '30 days' THEN i.quantity * p.purchase_price ELSE 0 END), 2) AS near_expiry_30d_value,
        ROUND(
            SUM(CASE WHEN pb.expiry_date < CURRENT_DATE THEN i.quantity ELSE 0 END) * 100.0
            / NULLIF(SUM(i.quantity), 0), 2
        ) AS expired_rate_pct,
        ROUND(
            SUM(CASE WHEN pb.expiry_date <= CURRENT_DATE + INTERVAL '30 days' THEN i.quantity ELSE 0 END) * 100.0
            / NULLIF(SUM(i.quantity), 0), 2
        ) AS at_risk_rate_pct,
        MIN(pb.expiry_date) AS oldest_expiry_date,
        -- 最近一次盘点日期
        MAX(i.last_stocktake_date) AS last_stocktake
    FROM warehouses w
    JOIN inventory i ON w.id = i.warehouse_id
    JOIN product_batches pb ON i.batch_id = pb.id AND pb.status = 'active'
    JOIN products p ON pb.product_id = p.id
    WHERE i.quantity > 0
    GROUP BY w.id, w.name
),
store_recent_stocktake AS (
    SELECT
        warehouse_id,
        MAX(created_at) AS last_stocktake_activity,
        COUNT(CASE WHEN created_at >= CURRENT_DATE - INTERVAL '30 days' THEN 1 END) AS stocktake_count_30d
    FROM inventory_transactions
    WHERE transaction_type = 'stocktake_adjust'
    GROUP BY warehouse_id
)
SELECT
    ses.store_name,
    ses.store_manager,
    ses.total_batches,
    ses.total_stock_qty,
    ses.total_stock_value,
    ses.expired_qty,
    ses.expired_value,
    ses.expired_rate_pct,
    ses.near_expiry_30d,
    ses.near_expiry_30d_value,
    ses.at_risk_rate_pct,
    ses.oldest_expiry_date,
    ses.last_stocktake,
    COALESCE(srs.stocktake_count_30d, 0) AS recent_stocktake_count,
    -- 管理效率评分
    RANK() OVER (ORDER BY ses.expired_rate_pct ASC) AS expiry_control_rank,
    RANK() OVER (ORDER BY ses.at_risk_rate_pct ASC) AS risk_control_rank,
    RANK() OVER (ORDER BY COALESCE(srs.stocktake_count_30d, 0) DESC) AS diligence_rank,
    CASE
        WHEN ses.expired_rate_pct > 5 THEN '过期率超标-需整改'
        WHEN ses.at_risk_rate_pct > 20 THEN '临期率偏高-需加强管理'
        WHEN ses.last_stocktake < CURRENT_DATE - INTERVAL '30 days' THEN '盘点不及时-需安排盘点'
        WHEN COALESCE(srs.stocktake_count_30d, 0) = 0 THEN '近期无盘点记录'
        ELSE '管理良好'
    END AS management_alert
FROM store_expiry_stats ses
LEFT JOIN store_recent_stocktake srs ON ses.store_id = srs.warehouse_id
ORDER BY ses.expired_rate_pct DESC;


-- ============================================================
-- Q67: 某类别所有货品批号全景 - 从生产到销售全链路
-- 语法: 多表JOIN + 窗口函数 + 子查询 + 全链路追踪
-- 业务场景: 输入类别名，查看该类别所有货品的批号库存和销售情况
-- ============================================================

SELECT
    pc.name AS category_name,
    p.sku,
    p.name AS product_name,
    p.shelf_life_days,
    pb.batch_no,
    pb.production_date,
    pb.expiry_date,
    (pb.expiry_date - CURRENT_DATE) AS days_to_expiry,
    pb.initial_qty,
    pb.current_qty,
    ROUND(pb.current_qty * 100.0 / NULLIF(pb.initial_qty, 0), 2) AS remaining_pct,
    w.name AS store_name,
    i.shelf_location,
    i.quantity AS store_specific_qty,
    -- 该批号总销售量
    (SELECT COALESCE(SUM(soi.quantity), 0)
     FROM sales_order_items soi
     JOIN sales_orders so ON soi.order_id = so.id
     WHERE soi.batch_id = pb.id AND so.status NOT IN ('draft', 'cancelled')
    ) AS total_sold_qty,
    -- 该批号最近一次销售日期
    (SELECT MAX(so.order_date)
     FROM sales_order_items soi
     JOIN sales_orders so ON soi.order_id = so.id
     WHERE soi.batch_id = pb.id AND so.status NOT IN ('draft', 'cancelled')
    ) AS last_sale_date,
    -- 该批号总销售额
    (SELECT COALESCE(SUM(soi.amount), 0)
     FROM sales_order_items soi
     WHERE soi.batch_id = pb.id
    ) AS total_sold_amount,
    -- 该产品所有批号总库存
    SUM(i.quantity) OVER (PARTITION BY p.id) AS product_total_stock,
    -- 该产品所有批号中最早过期
    MIN(pb.expiry_date) OVER (PARTITION BY p.id) AS product_earliest_expiry,
    -- 供应商
    (SELECT s.name FROM suppliers s WHERE s.id = pb.supplier_id) AS supplier,
    CASE
        WHEN pb.expiry_date < CURRENT_DATE THEN '已过期'
        WHEN pb.expiry_date <= CURRENT_DATE + INTERVAL '30 days' THEN '临期'
        WHEN pb.current_qty = pb.initial_qty AND (CURRENT_DATE - pb.production_date) > p.shelf_life_days * 0.5
            THEN '在库时间过长'
        WHEN pb.current_qty * 100.0 / NULLIF(pb.initial_qty, 0) < 10 THEN '基本售罄'
        ELSE '正常'
    END AS batch_status_label
FROM product_categories pc
JOIN products p ON pc.id = p.category_id
JOIN product_batches pb ON p.id = pb.product_id
LEFT JOIN inventory i ON pb.id = i.batch_id
LEFT JOIN warehouses w ON i.warehouse_id = w.id
WHERE pb.current_qty > 0
ORDER BY pc.name, p.sku, pb.expiry_date ASC;