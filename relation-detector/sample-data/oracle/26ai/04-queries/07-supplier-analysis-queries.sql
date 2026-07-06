-- ============================================================
-- 第七批超复杂SQL: 供应商地理分析 + 智能选择 + 对比
-- 覆盖: 供应商PK、地理距离优化、物流成本、退货率、综合评分
-- ============================================================


-- ============================================================
-- Q76: 全产品供应商覆盖率分析 - 哪些产品缺供应商
-- 语法: CTE + LEFT JOIN + 条件聚合
-- 业务场景: 检查每个产品是否有3-5个供应商，哪些产品供应商不足
-- ============================================================

WITH product_supplier_count AS (
    SELECT
        p.id AS product_id,
        p.sku,
        p.name AS product_name,
        pc.name AS category_name,
        p.purchase_price,
        COUNT(DISTINCT sp.supplier_id) AS supplier_count,
        MIN(sp.supplier_price) AS lowest_price,
        MAX(sp.supplier_price) AS highest_price,
        ROUND(AVG(sp.supplier_price), 2) AS avg_price,
        ROUND((MAX(sp.supplier_price) - MIN(sp.supplier_price)) / NULLIF(MIN(sp.supplier_price), 0) * 100, 2) AS price_spread_pct,
        AVG(sp.lead_time_days) AS avg_lead_time,
        AVG(sp.quality_score) AS avg_quality,
        AVG(COALESCE(sp.return_rate, 0)) AS avg_return_rate,
        LISTAGG((s.name || '(' || s.city || '):' || sp.supplier_price), ' | ') WITHIN GROUP (ORDER BY sp.supplier_price) AS supplier_details
    FROM products p
    JOIN product_categories pc ON p.category_id = pc.id
    LEFT JOIN supplier_products sp ON p.id = sp.product_id
    LEFT JOIN suppliers s ON sp.supplier_id = s.id AND s.cooperation_status = 'active'
    WHERE p.status = 'active'
    GROUP BY p.id, p.sku, p.name, pc.name, p.purchase_price
)
SELECT
    sku,
    product_name,
    category_name,
    purchase_price,
    supplier_count,
    lowest_price,
    highest_price,
    avg_price,
    price_spread_pct,
    avg_lead_time,
    ROUND(avg_quality, 1) AS avg_quality,
    ROUND(avg_return_rate * 100, 2) AS avg_return_rate_pct,
    supplier_details,
    CASE
        WHEN supplier_count = 0 THEN '无供应商-严重'
        WHEN supplier_count < 3 THEN ('供应商不足(' || supplier_count || '/3)-需拓展')
        WHEN supplier_count >= 5 THEN ('供应商充足(' || supplier_count || '家)')
        ELSE ('正常(' || supplier_count || '家)')
    END AS coverage_status,
    CASE
        WHEN price_spread_pct > 20 THEN '价格差异大-可优化'
        WHEN supplier_count >= 3 AND price_spread_pct < 5 THEN '价格竞争充分'
        ELSE '正常'
    END AS price_competition
FROM product_supplier_count
ORDER BY supplier_count ASC, price_spread_pct DESC;


-- ============================================================
-- Q77: 门店-供应商最优匹配推荐
-- 语法: CTE + CROSS JOIN + Haversine + 窗口函数排名
-- 业务场景: 为每个门店每个产品推荐最优供应商(综合评分最高)
-- ============================================================

WITH store_product_supplier AS (
    SELECT
        w.id AS store_id,
        w.name AS store_name,
        w.city AS store_city,
        p.id AS product_id,
        p.sku,
        p.name AS product_name,
        pc.name AS category_name,
        s.id AS supplier_id,
        s.name AS supplier_name,
        s.city AS supplier_city,
        sp.supplier_price,
        sp.lead_time_days,
        sp.quality_score,
        COALESCE(sp.return_rate, 0) AS return_rate,
        fn_haversine_distance(s.latitude, s.longitude, w.latitude, w.longitude) AS distance_km,
        fn_estimate_shipping_cost(s.id, w.id, p.id, 100) AS estimated_shipping_per_100,
        fn_supplier_score(s.id, p.id, w.id) AS composite_score,
        ROW_NUMBER() OVER (PARTITION BY w.id, p.id ORDER BY fn_supplier_score(s.id, p.id, w.id) DESC) AS rank_by_score
    FROM warehouses w
    CROSS JOIN products p
    JOIN supplier_products sp ON p.id = sp.product_id
    JOIN suppliers s ON sp.supplier_id = s.id
    JOIN product_categories pc ON p.category_id = pc.id
    WHERE w.status = 'active'
      AND p.status = 'active'
      AND s.cooperation_status = 'active'
)
SELECT
    store_name,
    store_city,
    sku,
    product_name,
    category_name,
    supplier_name,
    supplier_city,
    supplier_price,
    lead_time_days,
    quality_score,
    ROUND(return_rate * 100, 2) AS return_rate_pct,
    distance_km,
    estimated_shipping_per_100,
    composite_score,
    rank_by_score,
    CASE
        WHEN rank_by_score = 1 THEN '首选供应商'
        WHEN rank_by_score = 2 THEN '备选供应商'
        ELSE ('第' || rank_by_score || '选择')
    END AS recommendation
FROM store_product_supplier
WHERE rank_by_score <= 3
ORDER BY store_name, sku, rank_by_score;


-- ============================================================
-- Q78: 供应商物流成本对比 - 同产品不同供应商到同门店
-- 语法: CTE + 自连接 + 成本差异计算
-- 业务场景: 对比不同供应商到同一门店的总成本(货价+运费)
-- ============================================================

WITH total_cost_comparison AS (
    SELECT
        p.sku,
        p.name AS product_name,
        w.name AS store_name,
        w.city AS store_city,
        s.name AS supplier_name,
        s.city AS supplier_city,
        sp.supplier_price,
        sp.lead_time_days,
        fn_haversine_distance(s.latitude, s.longitude, w.latitude, w.longitude) AS distance_km,
        sp.shipping_cost_per_km,
        ROUND(fn_haversine_distance(s.latitude, s.longitude, w.latitude, w.longitude) * sp.shipping_cost_per_km, 2) AS base_shipping_cost,
        ROUND(sp.supplier_price * 100 + fn_haversine_distance(s.latitude, s.longitude, w.latitude, w.longitude) * sp.shipping_cost_per_km * 2, 2) AS total_cost_100units,
        sp.quality_score,
        ROUND(COALESCE(sp.return_rate, 0) * 100, 2) AS return_rate_pct,
        -- 含退货损失的总成本
        ROUND(
            (sp.supplier_price * 100 + fn_haversine_distance(s.latitude, s.longitude, w.latitude, w.longitude) * sp.shipping_cost_per_km * 2)
            * (1 + COALESCE(sp.return_rate, 0))
        , 2) AS total_cost_with_return_risk,
        fn_supplier_score(s.id, p.id, w.id) AS composite_score
    FROM products p
    JOIN supplier_products sp ON p.id = sp.product_id
    JOIN suppliers s ON sp.supplier_id = s.id
    CROSS JOIN warehouses w
    WHERE w.status = 'active' AND s.cooperation_status = 'active'
      AND p.status = 'active'
    FETCH FIRST 500 ROWS ONLY
),
cost_ranked AS (
    SELECT
        *,
        RANK() OVER (PARTITION BY sku, store_name ORDER BY total_cost_100units ASC) AS cost_rank,
        RANK() OVER (PARTITION BY sku, store_name ORDER BY composite_score DESC) AS score_rank
    FROM total_cost_comparison
)
SELECT
    sku,
    product_name,
    store_name,
    supplier_name,
    supplier_city,
    supplier_price,
    distance_km,
    base_shipping_cost,
    total_cost_100units,
    total_cost_with_return_risk,
    composite_score,
    cost_rank,
    score_rank,
    CASE
        WHEN cost_rank = 1 AND score_rank = 1 THEN '最优选择'
        WHEN cost_rank = 1 THEN '成本最低'
        WHEN score_rank = 1 THEN '综合评分最高'
        WHEN ABS(total_cost_100units - FIRST_VALUE(total_cost_100units) OVER (PARTITION BY sku, store_name ORDER BY total_cost_100units ASC)) /
            NULLIF(FIRST_VALUE(total_cost_100units) OVER (PARTITION BY sku, store_name ORDER BY total_cost_100units ASC), 0) < 0.05
        THEN '成本接近最优'
        ELSE '可选'
    END AS selection_advice
FROM cost_ranked
WHERE cost_rank <= 3
ORDER BY sku, store_name, cost_rank;


-- ============================================================
-- Q79: 供应商综合健康度排名 - 全维度评分
-- 语法: CTE + 多表JOIN + 加权评分 + 排名
-- 业务场景: 对供应商进行全维度健康度排名
-- ============================================================

WITH supplier_all_metrics AS (
    SELECT
        s.id AS supplier_id,
        s.name AS supplier_name,
        s.city AS supplier_city,
        s.province AS supplier_province,
        s.credit_level,
        s.cooperation_status,
        COUNT(DISTINCT sp.product_id) AS supplied_products,
        AVG(sp.supplier_price) AS avg_price,
        AVG(sp.lead_time_days) AS avg_lead_time,
        AVG(sp.quality_score) AS avg_quality,
        AVG(COALESCE(sp.return_rate, 0)) AS avg_return_rate,
        AVG(sp.shipping_cost_per_km) AS avg_shipping_rate,
        SUM(sp.total_order_count) AS total_orders,
        SUM(sp.total_order_qty) AS total_qty,
        -- 到最近门店的距离
        MIN(fn_haversine_distance(s.latitude, s.longitude, w.latitude, w.longitude)) AS min_distance_to_store,
        -- 到最远门店的距离
        MAX(fn_haversine_distance(s.latitude, s.longitude, w.latitude, w.longitude)) AS max_distance_to_store,
        -- 覆盖门店数(距离<500km)
        COUNT(DISTINCT CASE WHEN fn_haversine_distance(s.latitude, s.longitude, w.latitude, w.longitude) < 500 THEN w.id END) AS stores_within_500km
    FROM suppliers s
    LEFT JOIN supplier_products sp ON s.id = sp.supplier_id
    LEFT JOIN warehouses w ON w.status = 'active'
    GROUP BY s.id, s.name, s.city, s.province, s.credit_level, s.cooperation_status
)
SELECT
    supplier_name,
    supplier_city,
    supplier_province,
    credit_level,
    cooperation_status,
    supplied_products,
    ROUND(avg_price, 2) AS avg_price,
    ROUND(avg_lead_time, 1) AS avg_lead_time_days,
    ROUND(avg_quality, 1) AS avg_quality_score,
    ROUND(avg_return_rate * 100, 2) AS avg_return_rate_pct,
    avg_shipping_rate,
    total_orders,
    total_qty,
    ROUND(min_distance_to_store, 0) AS nearest_store_km,
    stores_within_500km,
    -- 综合健康度评分
    ROUND(
        (100 - LEAST(COALESCE(avg_return_rate, 0) * 100, 50) * 2) * 0.25
        + COALESCE(avg_quality, 80) * 0.25
        + (100 - LEAST(COALESCE(avg_lead_time, 15) / 30.0 * 100, 50)) * 0.15
        + LEAST(supplied_products * 2, 100) * 0.15
        + LEAST(stores_within_500km * 5, 100) * 0.10
        + (CASE WHEN cooperation_status = 'active' THEN 100 ELSE 0 END) * 0.10
    , 0) AS health_score,
    RANK() OVER (ORDER BY
        (100 - LEAST(COALESCE(avg_return_rate, 0) * 100, 50) * 2) * 0.25
        + COALESCE(avg_quality, 80) * 0.25
        + (100 - LEAST(COALESCE(avg_lead_time, 15) / 30.0 * 100, 50)) * 0.15
        + LEAST(supplied_products * 2, 100) * 0.15
        + LEAST(stores_within_500km * 5, 100) * 0.10
        + (CASE WHEN cooperation_status = 'active' THEN 100 ELSE 0 END) * 0.10
    DESC) AS health_rank
FROM supplier_all_metrics
ORDER BY health_score DESC;


-- ============================================================
-- Q80: 供应商地域分布热力图 - 哪个城市供应商最密集
-- 语法: CTE + 条件聚合 + 地理密集度
-- 业务场景: 供应商地域分布，为选址和物流规划提供参考
-- ============================================================

SELECT
    s.province,
    s.city,
    COUNT(DISTINCT s.id) AS supplier_count,
    COUNT(DISTINCT sp.product_id) AS product_coverage,
    ROUND(AVG(sp.supplier_price), 2) AS avg_price,
    ROUND(AVG(sp.quality_score), 1) AS avg_quality,
    ROUND(AVG(COALESCE(sp.return_rate, 0)) * 100, 2) AS avg_return_rate_pct,
    -- 该城市周边500km内的门店数
    (SELECT COUNT(DISTINCT w.id) FROM warehouses w
     WHERE w.status = 'active'
       AND fn_haversine_distance(s.latitude, s.longitude, w.latitude, w.longitude) < 500
    ) AS stores_within_500km,
    -- 供应品类覆盖
    COUNT(DISTINCT pc.id) AS category_coverage,
    LISTAGG(pc.name, ', ') WITHIN GROUP (ORDER BY pc.name) AS categories,
    -- 综合评价
    ROUND(AVG(
        (100 - LEAST(COALESCE(sp.return_rate, 0) * 100, 50) * 2) * 0.3
        + COALESCE(sp.quality_score, 80) * 0.4
        + (100 - LEAST(COALESCE(sp.lead_time_days, 15) / 30.0 * 100, 50)) * 0.3
    ), 0) AS city_avg_score,
    RANK() OVER (ORDER BY COUNT(DISTINCT s.id) DESC) AS density_rank
FROM suppliers s
JOIN supplier_products sp ON s.id = sp.supplier_id
JOIN products p ON sp.product_id = p.id
JOIN product_categories pc ON p.category_id = pc.id
WHERE s.cooperation_status = 'active'
GROUP BY s.province, s.city, s.latitude, s.longitude
HAVING COUNT(DISTINCT s.id) >= 1
ORDER BY supplier_count DESC;


-- ============================================================
-- Q81: 供应商价格波动监控 - 同产品价格离散度
-- 语法: CTE + 窗口函数 + 变异系数 + 异常检测
-- 业务场景: 监控各产品供应商报价的离散度，发现异常报价
-- ============================================================

WITH price_stats AS (
    SELECT
        p.id AS product_id,
        p.sku,
        p.name AS product_name,
        pc.name AS category_name,
        COUNT(DISTINCT sp.supplier_id) AS supplier_count,
        MIN(sp.supplier_price) AS min_price,
        MAX(sp.supplier_price) AS max_price,
        AVG(sp.supplier_price) AS avg_price,
        STDDEV(sp.supplier_price) AS stddev_price,
        -- 变异系数 CV = σ/μ
        ROUND(STDDEV(sp.supplier_price) / NULLIF(AVG(sp.supplier_price), 0), 4) AS cv,
        -- 价格范围比率
        ROUND((MAX(sp.supplier_price) - MIN(sp.supplier_price)) / NULLIF(MIN(sp.supplier_price), 0) * 100, 2) AS price_range_pct
    FROM products p
    JOIN product_categories pc ON p.category_id = pc.id
    JOIN supplier_products sp ON p.id = sp.product_id
    JOIN suppliers s ON sp.supplier_id = s.id AND s.cooperation_status = 'active'
    WHERE p.status = 'active'
    GROUP BY p.id, p.sku, p.name, pc.name
    HAVING COUNT(DISTINCT sp.supplier_id) >= 2
)
SELECT
    sku,
    product_name,
    category_name,
    supplier_count,
    min_price,
    max_price,
    avg_price,
    ROUND(stddev_price, 2) AS stddev_price,
    cv,
    price_range_pct,
    -- 异常检测
    CASE
        WHEN cv > 0.15 THEN '价格离散度高-需审查'
        WHEN cv > 0.10 THEN '价格离散度中等'
        WHEN cv > 0.05 THEN '价格离散度低'
        ELSE '价格非常集中'
    END AS dispersion_level,
    -- 最高价供应商
    (SELECT (s.name || '(' || sp.supplier_price || ')')
     FROM supplier_products sp JOIN suppliers s ON sp.supplier_id = s.id
     WHERE sp.product_id = ps.product_id ORDER BY sp.supplier_price DESC FETCH FIRST 1 ROWS ONLY
    ) AS most_expensive_supplier,
    -- 最低价供应商
    (SELECT (s.name || '(' || sp.supplier_price || ')')
     FROM supplier_products sp JOIN suppliers s ON sp.supplier_id = s.id
     WHERE sp.product_id = ps.product_id ORDER BY sp.supplier_price ASC FETCH FIRST 1 ROWS ONLY
    ) AS cheapest_supplier,
    RANK() OVER (ORDER BY cv DESC) AS dispersion_rank
FROM price_stats ps
ORDER BY cv DESC;


-- ============================================================
-- Q82: 供应商交期/质量/退货率 三维气泡图数据
-- 语法: CTE + 多维度聚合 + 归一化
-- 业务场景: 为BI可视化提供数据，交期vs质量vs退货率三维分析
-- ============================================================

WITH supplier_3d_metrics AS (
    SELECT
        s.id AS supplier_id,
        s.name AS supplier_name,
        s.city AS supplier_city,
        COUNT(DISTINCT sp.product_id) AS product_count,
        AVG(sp.lead_time_days) AS avg_lead_time,
        AVG(sp.quality_score) AS avg_quality,
        AVG(COALESCE(sp.return_rate, 0)) AS avg_return_rate,
        AVG(sp.supplier_price) AS avg_price,
        SUM(sp.total_order_qty) AS total_qty_supplied
    FROM suppliers s
    JOIN supplier_products sp ON s.id = sp.supplier_id
    WHERE s.cooperation_status = 'active'
    GROUP BY s.id, s.name, s.city
    HAVING COUNT(DISTINCT sp.product_id) >= 3
)
SELECT
    supplier_name,
    supplier_city,
    product_count AS bubble_size,
    ROUND(avg_lead_time, 1) AS x_lead_time_days,
    ROUND(avg_quality, 1) AS y_quality_score,
    ROUND(avg_return_rate * 100, 2) AS z_return_rate_pct,
    ROUND(avg_price, 2) AS avg_price,
    total_qty_supplied,
    -- 象限标签
    CASE
        WHEN avg_lead_time <= 7 AND avg_quality >= 85 THEN '优质供应商(快+好)'
        WHEN avg_lead_time <= 7 AND avg_quality < 85 THEN '快速但质量待提升'
        WHEN avg_lead_time > 7 AND avg_quality >= 85 THEN '质量好但交期慢'
        ELSE '需改进(慢+差)'
    END AS quadrant,
    -- 综合评级
    CASE
        WHEN avg_lead_time <= 7 AND avg_quality >= 85 AND COALESCE(avg_return_rate, 0) < 0.05 THEN 'A级'
        WHEN avg_quality >= 80 AND COALESCE(avg_return_rate, 0) < 0.10 THEN 'B级'
        WHEN avg_quality >= 70 THEN 'C级'
        ELSE 'D级'
    END AS supplier_grade
FROM supplier_3d_metrics
ORDER BY avg_quality DESC, avg_lead_time ASC;