-- ============================================================
-- 第四批超复杂SQL查询: 门店/客户消费深度分析
-- 覆盖: 客户门店消费明细、门店畅销品、门店对比、
--        客户门店偏好、门店商品关联、门店销售预测
-- ============================================================


-- ============================================================
-- Q52: 客户全渠道消费明细 - 门店+商品+时间+金额全景
-- 语法: 多表JOIN + 窗口函数 + 累计汇总 + 分组排名
-- 业务场景: 查询某个客户在所有门店的完整消费记录
-- ============================================================

SELECT
    c.name AS customer_name,
    c.membership_level,
    w.name AS store_name,
    so.order_no,
    so.order_date,
    so.payment_method,
    p.sku AS product_sku,
    p.name AS product_name,
    pc.name AS category_name,
    soi.quantity,
    soi.unit_price,
    soi.discount AS line_discount,
    soi.amount AS line_total,
    so.total_amount AS order_total,
    so.paid_amount AS order_paid,
    ROUND(soi.amount * 100.0 / NULLIF(so.total_amount, 0), 2) AS line_share_in_order,
    -- 客户在该门店的累计消费
    SUM(soi.amount) OVER (PARTITION BY so.customer_id, so.warehouse_id ORDER BY so.order_date
        ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS cumulative_store_spend,
    -- 客户总累计消费
    SUM(soi.amount) OVER (PARTITION BY so.customer_id ORDER BY so.order_date
        ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS cumulative_total_spend,
    -- 距上次购买天数
    (so.order_date - LAG(so.order_date) OVER (PARTITION BY so.customer_id ORDER BY so.order_date)) AS days_since_last,
    -- 该门店该品类排名
    RANK() OVER (PARTITION BY so.warehouse_id, pc.id ORDER BY soi.amount DESC) AS category_rank_in_store,
    -- 该客户购买该品类次数
    COUNT(*) OVER (PARTITION BY so.customer_id, pc.id) AS category_purchase_count
FROM sales_orders so
JOIN customers c ON so.customer_id = c.id
JOIN warehouses w ON so.warehouse_id = w.id
JOIN sales_order_items soi ON so.id = soi.order_id
JOIN products p ON soi.product_id = p.id
JOIN product_categories pc ON p.category_id = pc.id
WHERE so.status NOT IN ('draft', 'cancelled')
ORDER BY so.customer_id, so.order_date DESC, soi.amount DESC
LIMIT 500;


-- ============================================================
-- Q53: 门店每日/每周/每月畅销品TOP10 - 多周期对比
-- 语法: CTE + 窗口函数 + 多周期UNION + 排名变化追踪
-- 业务场景: 对比本周vs上周各门店畅销品变化
-- ============================================================

WITH weekly_sales AS (
    SELECT
        w.name AS store_name,
        p.sku,
        p.name AS product_name,
        pc.name AS category_name,
        CASE
            WHEN so.order_date >= date_trunc('week', CURRENT_DATE)::DATE THEN '本周'
            ELSE '上周'
        END AS week_label,
        SUM(soi.quantity) AS total_qty,
        SUM(soi.amount) AS total_amount,
        COUNT(DISTINCT so.id) AS order_count,
        COUNT(DISTINCT so.customer_id) AS customer_count
    FROM sales_orders so
    JOIN warehouses w ON so.warehouse_id = w.id
    JOIN sales_order_items soi ON so.id = soi.order_id
    JOIN products p ON soi.product_id = p.id
    JOIN product_categories pc ON p.category_id = pc.id
    WHERE so.order_date >= date_trunc('week', CURRENT_DATE)::DATE - INTERVAL '7 days'
      AND so.status NOT IN ('draft', 'cancelled')
    GROUP BY w.name, p.sku, p.name, pc.name, week_label
),
ranked AS (
    SELECT
        *,
        RANK() OVER (PARTITION BY store_name, week_label ORDER BY total_amount DESC) AS amount_rank,
        RANK() OVER (PARTITION BY store_name, week_label ORDER BY total_qty DESC) AS qty_rank
    FROM weekly_sales
),
this_week AS (
    SELECT * FROM ranked WHERE week_label = '本周' AND amount_rank <= 10
),
last_week AS (
    SELECT * FROM ranked WHERE week_label = '上周'
)
SELECT
    tw.store_name,
    tw.amount_rank AS this_week_rank,
    tw.sku,
    tw.product_name,
    tw.category_name,
    tw.total_amount AS this_week_amount,
    tw.total_qty AS this_week_qty,
    tw.customer_count AS this_week_customers,
    COALESCE(lw.amount_rank, 999) AS last_week_rank,
    COALESCE(lw.total_amount, 0) AS last_week_amount,
    CASE
        WHEN lw.amount_rank IS NULL THEN '新进榜'
        WHEN tw.amount_rank < lw.amount_rank THEN '上升' || (lw.amount_rank - tw.amount_rank) || '位'
        WHEN tw.amount_rank > lw.amount_rank THEN '下降' || (tw.amount_rank - lw.amount_rank) || '位'
        ELSE '持平'
    END AS rank_change,
    ROUND((tw.total_amount - COALESCE(lw.total_amount, 0)) / NULLIF(COALESCE(lw.total_amount, 0), 0) * 100, 2) AS amount_change_pct,
    ROUND(tw.total_amount * 100.0 / NULLIF(SUM(tw.total_amount) OVER (PARTITION BY tw.store_name), 0), 2) AS store_share_pct
FROM this_week tw
LEFT JOIN last_week lw ON tw.store_name = lw.store_name AND tw.sku = lw.sku
ORDER BY tw.store_name, tw.amount_rank;


-- ============================================================
-- Q54: 门店销售小时级热力图 - 营业时段分析
-- 语法: CTE + 小时维度聚合 + 条件聚合 + 时段标签
-- 业务场景: 分析各门店不同时段的销售热度
-- ============================================================

WITH hourly_sales AS (
    SELECT
        w.name AS store_name,
        w.id AS store_id,
        EXTRACT(HOUR FROM so.created_at) AS sale_hour,
        EXTRACT(DOW FROM so.order_date) AS dow,
        COUNT(DISTINCT so.id) AS order_count,
        SUM(so.total_amount) AS total_sales,
        AVG(so.total_amount) AS avg_order_value
    FROM sales_orders so
    JOIN warehouses w ON so.warehouse_id = w.id
    WHERE so.order_date >= CURRENT_DATE - INTERVAL '90 days'
      AND so.status NOT IN ('draft', 'cancelled')
    GROUP BY w.name, w.id, EXTRACT(HOUR FROM so.created_at), EXTRACT(DOW FROM so.order_date)
),
time_segment AS (
    SELECT
        store_name,
        sale_hour,
        CASE
            WHEN sale_hour BETWEEN 8 AND 11 THEN '上午(8-12)'
            WHEN sale_hour BETWEEN 12 AND 13 THEN '午间(12-14)'
            WHEN sale_hour BETWEEN 14 AND 17 THEN '下午(14-18)'
            WHEN sale_hour BETWEEN 18 AND 21 THEN '晚间(18-22)'
            ELSE '夜间(22-8)'
        END AS time_segment,
        CASE dow::INT
            WHEN 0 THEN '周日' WHEN 1 THEN '周一' WHEN 2 THEN '周二'
            WHEN 3 THEN '周三' WHEN 4 THEN '周四' WHEN 5 THEN '周五'
            ELSE '周六'
        END AS day_of_week,
        SUM(order_count) AS orders,
        SUM(total_sales) AS sales,
        ROUND(AVG(avg_order_value), 2) AS avg_order_val
    FROM hourly_sales
    GROUP BY store_name, sale_hour, dow
)
SELECT
    store_name,
    time_segment,
    day_of_week,
    SUM(orders) AS total_orders,
    SUM(sales) AS total_sales,
    AVG(avg_order_val) AS avg_order_value,
    ROUND(SUM(sales) * 100.0 / NULLIF(SUM(SUM(sales)) OVER (PARTITION BY store_name), 0), 2) AS time_share_pct,
    RANK() OVER (PARTITION BY store_name ORDER BY SUM(sales) DESC) AS time_rank
FROM time_segment
GROUP BY store_name, time_segment, day_of_week
ORDER BY store_name,
    CASE day_of_week
        WHEN '周一' THEN 1 WHEN '周二' THEN 2 WHEN '周三' THEN 3
        WHEN '周四' THEN 4 WHEN '周五' THEN 5 WHEN '周六' THEN 6
        WHEN '周日' THEN 7
    END,
    CASE time_segment
        WHEN '上午(8-12)' THEN 1 WHEN '午间(12-14)' THEN 2 WHEN '下午(14-18)' THEN 3
        WHEN '晚间(18-22)' THEN 4 WHEN '夜间(22-8)' THEN 5
    END;


-- ============================================================
-- Q55: 门店品类结构分析 - 各门店品类销售占比+趋势
-- 语法: CTE + 窗口函数 + 条件聚合 + 品类集中度
-- 业务场景: 对比各门店的品类结构差异，优化品类布局
-- ============================================================

WITH store_category_sales AS (
    SELECT
        w.id AS store_id,
        w.name AS store_name,
        pc.id AS category_id,
        pc.name AS category_name,
        TO_CHAR(so.order_date, 'YYYY-MM') AS month,
        SUM(soi.amount) AS category_sales,
        SUM(soi.quantity) AS category_qty,
        COUNT(DISTINCT soi.product_id) AS distinct_products,
        COUNT(DISTINCT so.customer_id) AS distinct_customers
    FROM sales_orders so
    JOIN warehouses w ON so.warehouse_id = w.id
    JOIN sales_order_items soi ON so.id = soi.order_id
    JOIN products p ON soi.product_id = p.id
    JOIN product_categories pc ON p.category_id = pc.id
    WHERE so.order_date >= CURRENT_DATE - INTERVAL '6 months'
      AND so.status NOT IN ('draft', 'cancelled')
    GROUP BY w.id, w.name, pc.id, pc.name, TO_CHAR(so.order_date, 'YYYY-MM')
),
store_total AS (
    SELECT store_id, month, SUM(category_sales) AS store_total_sales
    FROM store_category_sales
    GROUP BY store_id, month
)
SELECT
    scs.store_name,
    scs.category_name,
    scs.month,
    scs.category_sales,
    scs.category_qty,
    scs.distinct_products,
    scs.distinct_customers,
    ROUND(scs.category_sales * 100.0 / NULLIF(st.store_total_sales, 0), 2) AS category_share_pct,
    ROUND(AVG(scs.category_sales) OVER (PARTITION BY scs.store_id, scs.category_id ORDER BY scs.month
        ROWS BETWEEN 2 PRECEDING AND CURRENT ROW), 2) AS ma_3m_sales,
    LAG(scs.category_sales) OVER (PARTITION BY scs.store_id, scs.category_id ORDER BY scs.month) AS prev_month_sales,
    ROUND((scs.category_sales - LAG(scs.category_sales) OVER (PARTITION BY scs.store_id, scs.category_id ORDER BY scs.month))
        / NULLIF(LAG(scs.category_sales) OVER (PARTITION BY scs.store_id, scs.category_id ORDER BY scs.month), 0) * 100, 2) AS mom_change_pct,
    RANK() OVER (PARTITION BY scs.store_id, scs.month ORDER BY scs.category_sales DESC) AS category_rank,
    ROUND(scs.category_sales / NULLIF(scs.distinct_customers, 0), 2) AS revenue_per_customer
FROM store_category_sales scs
JOIN store_total st ON scs.store_id = st.store_id AND scs.month = st.month
ORDER BY scs.store_name, scs.month DESC, scs.category_sales DESC;


-- ============================================================
-- Q56: 门店客户价值分层 - 按门店统计客户RFM
-- 语法: CTE + NTILE + 条件聚合 + 门店维度交叉
-- 业务场景: 各门店客户价值分布，识别高价值客户
-- ============================================================

WITH store_customer_rfm AS (
    SELECT
        so.warehouse_id,
        w.name AS store_name,
        so.customer_id,
        c.name AS customer_name,
        c.membership_level,
        (CURRENT_DATE - MAX(so.order_date)) AS recency,
        COUNT(DISTINCT so.id) AS frequency,
        SUM(so.total_amount) AS monetary
    FROM sales_orders so
    JOIN warehouses w ON so.warehouse_id = w.id
    JOIN customers c ON so.customer_id = c.id
    WHERE so.status NOT IN ('draft', 'cancelled')
    GROUP BY so.warehouse_id, w.name, so.customer_id, c.name, c.membership_level
),
scored AS (
    SELECT
        *,
        NTILE(4) OVER (PARTITION BY warehouse_id ORDER BY recency ASC) AS r_score,
        NTILE(4) OVER (PARTITION BY warehouse_id ORDER BY frequency DESC) AS f_score,
        NTILE(4) OVER (PARTITION BY warehouse_id ORDER BY monetary DESC) AS m_score
    FROM store_customer_rfm
)
SELECT
    store_name,
    CASE
        WHEN r_score = 4 AND f_score = 4 AND m_score = 4 THEN '顶级客户'
        WHEN r_score >= 3 AND f_score >= 3 AND m_score >= 3 THEN '高价值客户'
        WHEN r_score >= 3 AND (f_score <= 2 OR m_score <= 2) THEN '潜力客户'
        WHEN r_score <= 2 AND f_score >= 3 AND m_score >= 3 THEN '流失风险客户'
        WHEN r_score <= 2 AND f_score <= 2 AND m_score <= 2 THEN '低价值客户'
        ELSE '普通客户'
    END AS customer_segment,
    COUNT(*) AS customer_count,
    ROUND(COUNT(*) * 100.0 / NULLIF(SUM(COUNT(*)) OVER (PARTITION BY store_name), 0), 2) AS segment_share_pct,
    SUM(monetary) AS segment_total_revenue,
    ROUND(AVG(monetary), 2) AS segment_avg_revenue,
    ROUND(AVG(frequency), 1) AS segment_avg_frequency,
    ROUND(AVG(recency), 0) AS segment_avg_recency_days
FROM scored
GROUP BY store_name, customer_segment, r_score, f_score, m_score
ORDER BY store_name,
    CASE customer_segment
        WHEN '顶级客户' THEN 1 WHEN '高价值客户' THEN 2
        WHEN '潜力客户' THEN 3 WHEN '流失风险客户' THEN 4
        WHEN '普通客户' THEN 5 ELSE 6
    END;


-- ============================================================
-- Q57: 门店交叉销售机会 - 买了A没买B的客户
-- 语法: CTE + NOT EXISTS + 相关子查询 + 交叉推荐
-- 业务场景: 找出在门店买了A品类但没买B品类的客户，推送交叉销售
-- ============================================================

WITH customer_category_purchase AS (
    SELECT DISTINCT
        so.warehouse_id,
        w.name AS store_name,
        so.customer_id,
        c.name AS customer_name,
        pc.id AS category_id,
        pc.name AS category_name
    FROM sales_orders so
    JOIN warehouses w ON so.warehouse_id = w.id
    JOIN customers c ON so.customer_id = c.id
    JOIN sales_order_items soi ON so.id = soi.order_id
    JOIN products p ON soi.product_id = p.id
    JOIN product_categories pc ON p.category_id = pc.id
    WHERE so.status NOT IN ('draft', 'cancelled')
),
category_pairs AS (
    SELECT
        a.warehouse_id,
        a.store_name,
        a.category_id AS purchased_category_id,
        a.category_name AS purchased_category,
        b.category_id AS recommended_category_id,
        b.category_name AS recommended_category,
        a.customer_id,
        a.customer_name
    FROM customer_category_purchase a
    CROSS JOIN (
        SELECT DISTINCT category_id, category_name FROM customer_category_purchase
    ) b
    WHERE a.category_id != b.category_id
      AND NOT EXISTS (
        SELECT 1 FROM customer_category_purchase ccp
        WHERE ccp.customer_id = a.customer_id
          AND ccp.warehouse_id = a.warehouse_id
          AND ccp.category_id = b.category_id
      )
)
SELECT
    store_name,
    purchased_category,
    recommended_category,
    COUNT(DISTINCT customer_id) AS opportunity_customer_count,
    string_agg(DISTINCT customer_name, ', ' ORDER BY customer_name) AS sample_customers,
    -- 推荐品类在门店的畅销品
    (SELECT p.sku FROM products p
     JOIN sales_order_items soi ON p.id = soi.product_id
     JOIN sales_orders so ON soi.order_id = so.id
     JOIN product_categories pc ON p.category_id = pc.id
     WHERE pc.id = cp.recommended_category_id AND so.warehouse_id = cp.warehouse_id
       AND so.status NOT IN ('draft', 'cancelled')
     GROUP BY p.id, p.sku
     ORDER BY SUM(soi.amount) DESC LIMIT 1
    ) AS recommended_product_sku,
    (SELECT p.name FROM products p
     JOIN sales_order_items soi ON p.id = soi.product_id
     JOIN sales_orders so ON soi.order_id = so.id
     JOIN product_categories pc ON p.category_id = pc.id
     WHERE pc.id = cp.recommended_category_id AND so.warehouse_id = cp.warehouse_id
       AND so.status NOT IN ('draft', 'cancelled')
     GROUP BY p.id, p.name
     ORDER BY SUM(soi.amount) DESC LIMIT 1
    ) AS recommended_product_name
FROM category_pairs cp
GROUP BY store_name, purchased_category, recommended_category,
         warehouse_id, purchased_category_id, recommended_category_id
HAVING COUNT(DISTINCT customer_id) >= 3
ORDER BY opportunity_customer_count DESC;


-- ============================================================
-- Q58: 门店新老客户销售额贡献趋势
-- 语法: CTE + 窗口函数 + 新老客标签 + 月度趋势
-- 业务场景: 分析各门店新客vs老客的销售贡献变化
-- ============================================================

WITH customer_first_purchase AS (
    SELECT
        customer_id,
        warehouse_id,
        MIN(order_date) AS first_purchase_date
    FROM sales_orders
    WHERE status NOT IN ('draft', 'cancelled')
    GROUP BY customer_id, warehouse_id
),
monthly_store_sales AS (
    SELECT
        so.warehouse_id,
        w.name AS store_name,
        TO_CHAR(so.order_date, 'YYYY-MM') AS month,
        so.customer_id,
        CASE
            WHEN cfp.first_purchase_date >= date_trunc('month', so.order_date)::DATE THEN '新客'
            ELSE '老客'
        END AS customer_type,
        so.total_amount
    FROM sales_orders so
    JOIN warehouses w ON so.warehouse_id = w.id
    JOIN customer_first_purchase cfp ON so.customer_id = cfp.customer_id
        AND so.warehouse_id = cfp.warehouse_id
    WHERE so.order_date >= CURRENT_DATE - INTERVAL '12 months'
      AND so.status NOT IN ('draft', 'cancelled')
)
SELECT
    store_name,
    month,
    COUNT(DISTINCT CASE WHEN customer_type = '新客' THEN customer_id END) AS new_customers,
    COUNT(DISTINCT CASE WHEN customer_type = '老客' THEN customer_id END) AS returning_customers,
    SUM(CASE WHEN customer_type = '新客' THEN total_amount ELSE 0 END) AS new_customer_sales,
    SUM(CASE WHEN customer_type = '老客' THEN total_amount ELSE 0 END) AS returning_customer_sales,
    ROUND(SUM(CASE WHEN customer_type = '新客' THEN total_amount ELSE 0 END) * 100.0
        / NULLIF(SUM(total_amount), 0), 2) AS new_customer_sales_pct,
    ROUND(AVG(CASE WHEN customer_type = '新客' THEN total_amount END), 2) AS avg_new_order_value,
    ROUND(AVG(CASE WHEN customer_type = '老客' THEN total_amount END), 2) AS avg_returning_order_value,
    -- 新客留存率 (下月仍购买)
    ROUND(
        COUNT(DISTINCT CASE WHEN customer_type = '新客' THEN customer_id END) * 100.0
        / NULLIF(
            LAG(COUNT(DISTINCT CASE WHEN customer_type = '新客' THEN customer_id END))
            OVER (PARTITION BY store_name ORDER BY month), 0
        ), 2
    ) AS new_customer_retention_pct
FROM monthly_store_sales
GROUP BY store_name, month
ORDER BY store_name, month DESC;


-- ============================================================
-- Q59: 门店退货率与客户满意度关联分析
-- 语法: CTE + 多表JOIN + 条件聚合 + 相关性分析
-- 业务场景: 退货率是否与客户流失相关
-- ============================================================

WITH store_return_stats AS (
    SELECT
        w.id AS store_id,
        w.name AS store_name,
        COUNT(DISTINCT sr.id) AS return_count,
        COUNT(DISTINCT sr.customer_id) AS returning_customers,
        SUM(sr.total_amount) AS total_return_amount,
        AVG((sr.return_date - so.order_date)) AS avg_return_days,
        -- 退货原因分布
        COUNT(CASE WHEN sr.return_type = 'quality' THEN 1 END) AS quality_returns,
        COUNT(CASE WHEN sr.return_type = 'damage' THEN 1 END) AS damage_returns,
        COUNT(CASE WHEN sr.return_type = 'wrong_item' THEN 1 END) AS wrong_item_returns,
        COUNT(CASE WHEN sr.return_type = 'customer_reject' THEN 1 END) AS customer_reject_returns
    FROM sales_returns sr
    JOIN sales_orders so ON sr.order_id = so.id
    JOIN warehouses w ON sr.warehouse_id = w.id
    WHERE sr.return_date >= CURRENT_DATE - INTERVAL '6 months'
    GROUP BY w.id, w.name
),
store_sales_stats AS (
    SELECT
        w.id AS store_id,
        COUNT(DISTINCT so.id) AS total_orders,
        SUM(so.total_amount) AS total_sales,
        COUNT(DISTINCT so.customer_id) AS total_customers
    FROM sales_orders so
    JOIN warehouses w ON so.warehouse_id = w.id
    WHERE so.order_date >= CURRENT_DATE - INTERVAL '6 months'
      AND so.status NOT IN ('draft', 'cancelled')
    GROUP BY w.id
),
store_churn AS (
    SELECT
        so.warehouse_id,
        COUNT(DISTINCT CASE WHEN (CURRENT_DATE - MAX(so.order_date)) > 90
            THEN so.customer_id END) AS churned_customers
    FROM sales_orders so
    WHERE so.status NOT IN ('draft', 'cancelled')
    GROUP BY so.warehouse_id
)
SELECT
    srs.store_name,
    sss.total_orders,
    sss.total_sales,
    sss.total_customers,
    srs.return_count,
    srs.total_return_amount,
    ROUND(srs.return_count * 100.0 / NULLIF(sss.total_orders, 0), 2) AS return_rate_pct,
    ROUND(srs.total_return_amount * 100.0 / NULLIF(sss.total_sales, 0), 2) AS return_amount_pct,
    ROUND(srs.avg_return_days, 1) AS avg_return_days,
    srs.quality_returns,
    srs.damage_returns,
    srs.wrong_item_returns,
    srs.customer_reject_returns,
    COALESCE(sc.churned_customers, 0) AS churned_customers,
    ROUND(COALESCE(sc.churned_customers, 0) * 100.0 / NULLIF(sss.total_customers, 0), 2) AS churn_rate_pct,
    -- 退货率与流失率的关系
    CASE
        WHEN srs.return_count * 100.0 / NULLIF(sss.total_orders, 0) > 10
            AND COALESCE(sc.churned_customers, 0) * 100.0 / NULLIF(sss.total_customers, 0) > 20
        THEN '高退货率高流失'
        WHEN srs.return_count * 100.0 / NULLIF(sss.total_orders, 0) > 5
        THEN '退货率偏高'
        ELSE '正常'
    END AS quality_alert
FROM store_return_stats srs
JOIN store_sales_stats sss ON srs.store_id = sss.store_id
LEFT JOIN store_churn sc ON srs.store_id = sc.warehouse_id
ORDER BY return_rate_pct DESC;


-- ============================================================
-- Q60: 门店地理分布销售热力图 (模拟)
-- 语法: CTE + 客户地址解析 + 区域聚合 + 排名
-- 业务场景: 基于客户地址分析门店辐射范围
-- ============================================================

WITH customer_region AS (
    SELECT
        so.warehouse_id,
        w.name AS store_name,
        split_part(c.address, '市', 1) AS city,
        COUNT(DISTINCT so.customer_id) AS customer_count,
        COUNT(DISTINCT so.id) AS order_count,
        SUM(so.total_amount) AS total_sales,
        AVG(so.total_amount) AS avg_order_value,
        AVG((COALESCE(s.actual_delivery_date, s.estimated_delivery_date) - s.shipped_at)) AS avg_delivery_days
    FROM sales_orders so
    JOIN warehouses w ON so.warehouse_id = w.id
    JOIN customers c ON so.customer_id = c.id
    LEFT JOIN shipments s ON so.id = s.order_id
    WHERE so.status NOT IN ('draft', 'cancelled')
    GROUP BY so.warehouse_id, w.name, split_part(c.address, '市', 1)
)
SELECT
    store_name,
    city AS customer_city,
    customer_count,
    order_count,
    total_sales,
    ROUND(avg_order_value, 2) AS avg_order_value,
    ROUND(avg_delivery_days, 1) AS avg_delivery_days,
    ROUND(total_sales * 100.0 / NULLIF(SUM(total_sales) OVER (PARTITION BY store_name), 0), 2) AS city_share_in_store,
    RANK() OVER (PARTITION BY store_name ORDER BY total_sales DESC) AS city_rank,
    CASE
        WHEN RANK() OVER (PARTITION BY store_name ORDER BY total_sales DESC) = 1 THEN '核心城市'
        WHEN RANK() OVER (PARTITION BY store_name ORDER BY total_sales DESC) <= 3 THEN '重点城市'
        WHEN RANK() OVER (PARTITION BY store_name ORDER BY total_sales DESC) <= 5 THEN '辐射城市'
        ELSE '边缘城市'
    END AS city_level
FROM customer_region
ORDER BY store_name, city_rank;