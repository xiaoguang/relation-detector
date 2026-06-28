-- relation-detector-fixture-source: ROUTINE:erp_system.sp_customer_store_purchase_history
CREATE PROCEDURE sp_customer_store_purchase_history(
    IN p_customer_id BIGINT UNSIGNED,
    IN p_start_date DATE,
    IN p_end_date DATE
)
BEGIN
    SELECT
        so.order_date AS purchase_date,
        so.order_no,
        w.name AS store_name,
        w.code AS store_code,
        e.name AS salesperson,
        p.sku AS product_sku,
        p.name AS product_name,
        pc.name AS category_name,
        soi.quantity,
        soi.unit_price,
        soi.discount,
        soi.amount AS line_amount,
        pb.batch_no,
        pb.expiry_date,
        so.payment_method,
        so.status AS order_status,
        so.total_amount AS order_total,
        so.discount_amount AS order_discount,
        so.paid_amount AS order_paid,
        -- 是否退货
        CASE WHEN sri.id IS NOT NULL THEN '已退货' ELSE '正常' END AS return_flag,
        COALESCE(sri.return_qty, 0) AS returned_qty,
        sr.return_reason,
        -- 累计消费
        SUM(soi.amount) OVER (PARTITION BY so.customer_id ORDER BY so.order_date
            ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS cumulative_spent,
        -- 距上次购买天数
        DATEDIFF(so.order_date, LAG(so.order_date) OVER (PARTITION BY so.customer_id ORDER BY so.order_date)) AS days_since_last_purchase
    FROM sales_orders so
    JOIN warehouses w ON so.warehouse_id = w.id
    JOIN employees e ON so.salesperson_id = e.id
    JOIN sales_order_items soi ON so.id = soi.order_id
    JOIN products p ON soi.product_id = p.id
    JOIN product_categories pc ON p.category_id = pc.id
    LEFT JOIN product_batches pb ON soi.batch_id = pb.id
    LEFT JOIN sales_return_items sri ON soi.id = sri.order_item_id
    LEFT JOIN sales_returns sr ON sri.return_id = sr.id
    WHERE so.customer_id = p_customer_id
      AND so.order_date BETWEEN p_start_date AND p_end_date
      AND so.status NOT IN ('draft', 'cancelled')
    ORDER BY so.order_date DESC, so.order_no, soi.id;
END//


-- ============================================================
-- 49. 门店畅销品排行 (支持多周期)
-- 参数: p_warehouse_id门店ID(NULL=所有), p_period周期(day/week/month/quarter/year)
-- 输出: 指定周期内门店畅销品TOP 20
-- 统计原理: 按销售额+销售量双重排名
--           环比增长 = (本期-上期)/上期*100%
--           销售占比 = 该商品销售额/门店总销售额*100%
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:erp_system.sp_store_bestsellers
CREATE PROCEDURE sp_store_bestsellers(
    IN p_warehouse_id BIGINT UNSIGNED,
    IN p_period ENUM('day','week','month','quarter','year'),
    IN p_top_n INT
)
BEGIN
    DECLARE v_start_date DATE;
    DECLARE v_end_date DATE;
    DECLARE v_prev_start_date DATE;
    DECLARE v_prev_end_date DATE;

    -- 当前周期
    CASE p_period
        WHEN 'day' THEN
            SET v_start_date = CURDATE();
            SET v_end_date = CURDATE();
            SET v_prev_start_date = DATE_SUB(CURDATE(), INTERVAL 1 DAY);
            SET v_prev_end_date = DATE_SUB(CURDATE(), INTERVAL 1 DAY);
        WHEN 'week' THEN
            SET v_start_date = DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY);
            SET v_end_date = DATE_ADD(v_start_date, INTERVAL 6 DAY);
            SET v_prev_start_date = DATE_SUB(v_start_date, INTERVAL 7 DAY);
            SET v_prev_end_date = DATE_SUB(v_start_date, INTERVAL 1 DAY);
        WHEN 'month' THEN
            SET v_start_date = DATE_FORMAT(CURDATE(), '%Y-%m-01');
            SET v_end_date = LAST_DAY(CURDATE());
            SET v_prev_start_date = DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 MONTH), '%Y-%m-01');
            SET v_prev_end_date = LAST_DAY(DATE_SUB(CURDATE(), INTERVAL 1 MONTH));
        WHEN 'quarter' THEN
            SET v_start_date = MAKEDATE(YEAR(CURDATE()), 1) + INTERVAL QUARTER(CURDATE()) - 1 QUARTER;
            SET v_end_date = LAST_DAY(v_start_date + INTERVAL 2 MONTH);
            SET v_prev_start_date = v_start_date - INTERVAL 3 MONTH;
            SET v_prev_end_date = v_start_date - INTERVAL 1 DAY;
        WHEN 'year' THEN
            SET v_start_date = MAKEDATE(YEAR(CURDATE()), 1);
            SET v_end_date = CURDATE();
            SET v_prev_start_date = MAKEDATE(YEAR(CURDATE()) - 1, 1);
            SET v_prev_end_date = DATE_SUB(v_start_date, INTERVAL 1 DAY);
    END CASE;

    WITH current_period AS (
        SELECT
            soi.product_id,
            SUM(soi.quantity) AS cur_qty,
            SUM(soi.amount) AS cur_amount,
            COUNT(DISTINCT so.id) AS cur_orders,
            COUNT(DISTINCT so.customer_id) AS cur_customers
        FROM sales_order_items soi
        JOIN sales_orders so ON soi.order_id = so.id
        WHERE so.order_date BETWEEN v_start_date AND v_end_date
          AND so.status NOT IN ('draft', 'cancelled')
          AND (p_warehouse_id IS NULL OR so.warehouse_id = p_warehouse_id)
        GROUP BY soi.product_id
    ),
    prev_period AS (
        SELECT
            soi.product_id,
            SUM(soi.amount) AS prev_amount
        FROM sales_order_items soi
        JOIN sales_orders so ON soi.order_id = so.id
        WHERE so.order_date BETWEEN v_prev_start_date AND v_prev_end_date
          AND so.status NOT IN ('draft', 'cancelled')
          AND (p_warehouse_id IS NULL OR so.warehouse_id = p_warehouse_id)
        GROUP BY soi.product_id
    ),
    store_total AS (
        SELECT SUM(cur_amount) AS store_total_amount FROM current_period
    )
    SELECT
        p.sku,
        p.name AS product_name,
        pc.name AS category_name,
        p.retail_price,
        cp.cur_qty,
        cp.cur_amount,
        cp.cur_orders,
        cp.cur_customers,
        ROUND(cp.cur_amount / NULLIF(cp.cur_orders, 0), 2) AS avg_order_value,
        COALESCE(pp.prev_amount, 0) AS prev_period_amount,
        ROUND((cp.cur_amount - COALESCE(pp.prev_amount, 0)) / NULLIF(COALESCE(pp.prev_amount, 0), 0) * 100, 2) AS growth_pct,
        ROUND(cp.cur_amount * 100.0 / NULLIF(st.store_total_amount, 0), 2) AS sales_contribution_pct,
        RANK() OVER (ORDER BY cp.cur_amount DESC) AS amount_rank,
        RANK() OVER (ORDER BY cp.cur_qty DESC) AS qty_rank,
        RANK() OVER (ORDER BY cp.cur_customers DESC) AS customers_rank,
        COALESCE(fn_get_product_stock(p.id, p_warehouse_id), 0) AS current_store_stock,
        CASE
            WHEN RANK() OVER (ORDER BY cp.cur_amount DESC) <= 3 THEN '明星产品'
            WHEN RANK() OVER (ORDER BY cp.cur_amount DESC) <= 10 THEN '热销产品'
            WHEN RANK() OVER (ORDER BY cp.cur_amount DESC) <= 20 THEN '正常产品'
            ELSE '平销产品'
        END AS product_level
    FROM current_period cp
    JOIN products p ON cp.product_id = p.id
    JOIN product_categories pc ON p.category_id = pc.id
    LEFT JOIN prev_period pp ON cp.product_id = pp.product_id
    CROSS JOIN store_total st
    ORDER BY cp.cur_amount DESC
    LIMIT p_top_n;
END//


-- ============================================================
-- 50. 门店销售业绩对比
-- 参数: p_start_date/p_end_date
-- 输出: 各门店销售额/订单数/客单价/客户数/畅销品/退货率
-- 统计原理: 多维度对比，含环比和占比
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:erp_system.sp_store_performance_compare
CREATE PROCEDURE sp_store_performance_compare(
    IN p_start_date DATE,
    IN p_end_date DATE
)
BEGIN
    WITH store_sales AS (
        SELECT
            w.id AS warehouse_id,
            w.name AS warehouse_name,
            w.type AS warehouse_type,
            COUNT(DISTINCT so.id) AS total_orders,
            SUM(so.total_amount) AS total_sales,
            SUM(so.paid_amount) AS total_collected,
            SUM(so.total_amount - so.paid_amount) AS total_outstanding,
            COUNT(DISTINCT so.customer_id) AS unique_customers,
            COUNT(DISTINCT soi.product_id) AS unique_products_sold,
            SUM(soi.quantity) AS total_qty_sold,
            ROUND(SUM(so.total_amount) / NULLIF(COUNT(DISTINCT so.id), 0), 2) AS avg_order_value,
            ROUND(SUM(so.total_amount) / NULLIF(COUNT(DISTINCT so.customer_id), 0), 2) AS revenue_per_customer,
            ROUND(SUM(so.paid_amount) * 100.0 / NULLIF(SUM(so.total_amount), 0), 2) AS collection_rate
        FROM sales_orders so
        JOIN warehouses w ON so.warehouse_id = w.id
        LEFT JOIN sales_order_items soi ON so.id = soi.order_id
        WHERE so.order_date BETWEEN p_start_date AND p_end_date
          AND so.status NOT IN ('draft', 'cancelled')
        GROUP BY w.id, w.name, w.type
    ),
    store_returns AS (
        SELECT
            sr.warehouse_id,
            COUNT(DISTINCT sr.id) AS return_count,
            SUM(sr.total_amount) AS total_return_amount
        FROM sales_returns sr
        WHERE sr.return_date BETWEEN p_start_date AND p_end_date
        GROUP BY sr.warehouse_id
    ),
    store_bestseller AS (
        SELECT
            warehouse_id,
            product_id,
            amount,
            ROW_NUMBER() OVER (PARTITION BY warehouse_id ORDER BY amount DESC) AS rn
        FROM (
            SELECT
                so.warehouse_id,
                soi.product_id,
                SUM(soi.amount) AS amount
            FROM sales_order_items soi
            JOIN sales_orders so ON soi.order_id = so.id
            WHERE so.order_date BETWEEN p_start_date AND p_end_date
              AND so.status NOT IN ('draft', 'cancelled')
            GROUP BY so.warehouse_id, soi.product_id
        ) t
    )
    SELECT
        ss.warehouse_name,
        ss.warehouse_type,
        ss.total_orders,
        ss.total_sales,
        ss.total_collected,
        ss.total_outstanding,
        ss.collection_rate,
        ss.unique_customers,
        ss.unique_products_sold,
        ss.total_qty_sold,
        ss.avg_order_value,
        ss.revenue_per_customer,
        COALESCE(sr.return_count, 0) AS return_count,
        COALESCE(sr.total_return_amount, 0) AS total_return_amount,
        ROUND(COALESCE(sr.total_return_amount, 0) * 100.0 / NULLIF(ss.total_sales, 0), 2) AS return_rate_pct,
        ROUND(ss.total_sales * 100.0 / NULLIF(SUM(ss.total_sales) OVER (), 0), 2) AS sales_share_pct,
        -- 畅销品
        (SELECT p.sku FROM store_bestseller sb
         JOIN products p ON sb.product_id = p.id
         WHERE sb.warehouse_id = ss.warehouse_id AND sb.rn = 1
        ) AS top1_product_sku,
        (SELECT p.name FROM store_bestseller sb
         JOIN products p ON sb.product_id = p.id
         WHERE sb.warehouse_id = ss.warehouse_id AND sb.rn = 1
        ) AS top1_product_name,
        (SELECT ROUND(sb.amount, 2) FROM store_bestseller sb
         WHERE sb.warehouse_id = ss.warehouse_id AND sb.rn = 1
        ) AS top1_product_sales,
        RANK() OVER (ORDER BY ss.total_sales DESC) AS sales_rank,
        RANK() OVER (ORDER BY ss.collection_rate DESC) AS collection_rank
    FROM store_sales ss
    LEFT JOIN store_returns sr ON ss.warehouse_id = sr.warehouse_id
    ORDER BY ss.total_sales DESC;
END//


-- ============================================================
-- 51. 客户门店偏好分析
-- 参数: p_customer_id客户ID(NULL=所有)
-- 输出: 客户在各门店的消费分布和偏好
-- 统计原理: 门店消费占比 = 客户在某门店消费/客户总消费
--           偏好度 = 客户门店占比/全体客户门店占比
--           >1表示偏好该门店
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:erp_system.sp_customer_store_preference
CREATE PROCEDURE sp_customer_store_preference(
    IN p_customer_id BIGINT UNSIGNED
)
BEGIN
    WITH customer_store_spend AS (
        SELECT
            so.customer_id,
            c.name AS customer_name,
            w.id AS store_id,
            w.name AS store_name,
            SUM(so.total_amount) AS store_spend,
            COUNT(DISTINCT so.id) AS store_orders,
            MAX(so.order_date) AS last_visit,
            DATEDIFF(CURDATE(), MAX(so.order_date)) AS days_since_last_visit
        FROM sales_orders so
        JOIN customers c ON so.customer_id = c.id
        JOIN warehouses w ON so.warehouse_id = w.id
        WHERE so.status NOT IN ('draft', 'cancelled')
          AND (p_customer_id IS NULL OR so.customer_id = p_customer_id)
        GROUP BY so.customer_id, c.name, w.id, w.name
    ),
    customer_total AS (
        SELECT customer_id, SUM(store_spend) AS total_spend
        FROM customer_store_spend
        GROUP BY customer_id
    ),
    overall_store_distribution AS (
        SELECT
            store_id,
            SUM(store_spend) * 100.0 / NULLIF((SELECT SUM(store_spend) FROM customer_store_spend), 0) AS overall_share_pct
        FROM customer_store_spend
        GROUP BY store_id
    )
    SELECT
        css.customer_id,
        css.customer_name,
        css.store_name,
        css.store_spend,
        css.store_orders,
        css.last_visit,
        css.days_since_last_visit,
        ROUND(css.store_spend * 100.0 / NULLIF(ct.total_spend, 0), 2) AS store_share_pct,
        osd.overall_share_pct,
        ROUND((css.store_spend * 100.0 / NULLIF(ct.total_spend, 0))
            / NULLIF(osd.overall_share_pct, 0), 2) AS preference_index,
        CASE
            WHEN (css.store_spend * 100.0 / NULLIF(ct.total_spend, 0))
                / NULLIF(osd.overall_share_pct, 0) > 2.0 THEN '强偏好'
            WHEN (css.store_spend * 100.0 / NULLIF(ct.total_spend, 0))
                / NULLIF(osd.overall_share_pct, 0) > 1.2 THEN '偏好'
            WHEN (css.store_spend * 100.0 / NULLIF(ct.total_spend, 0))
                / NULLIF(osd.overall_share_pct, 0) > 0.8 THEN '正常'
            ELSE '弱偏好'
        END AS preference_level,
        ct.total_spend AS customer_total_spend,
        RANK() OVER (PARTITION BY css.customer_id ORDER BY css.store_spend DESC) AS store_rank
    FROM customer_store_spend css
    JOIN customer_total ct ON css.customer_id = ct.customer_id
    LEFT JOIN overall_store_distribution osd ON css.store_id = osd.store_id
    ORDER BY css.customer_id, css.store_spend DESC;
END//


-- ============================================================
-- 52. 门店商品关联规则 (购物篮分析)
-- 参数: p_warehouse_id门店ID, p_min_support最小支持度, p_min_lift最小提升度
-- 输出: 在指定门店中经常一起购买的商品组合
-- 统计原理: 支持度 = 同时购买AB的订单/总订单
--           置信度 = 同时购买AB/购买A
--           提升度 = 置信度/(购买B/总订单)
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:erp_system.sp_store_product_affinity
CREATE PROCEDURE sp_store_product_affinity(
    IN p_warehouse_id BIGINT UNSIGNED,
    IN p_min_support DECIMAL(5,4),
    IN p_min_lift DECIMAL(5,2)
)
BEGIN
    WITH store_orders AS (
        SELECT DISTINCT soi.order_id, soi.product_id
        FROM sales_order_items soi
        JOIN sales_orders so ON soi.order_id = so.id
        WHERE so.warehouse_id = p_warehouse_id
          AND so.status NOT IN ('draft', 'cancelled')
    ),
    total_orders AS (
        SELECT COUNT(DISTINCT order_id) AS total FROM store_orders
    ),
    product_order_count AS (
        SELECT product_id, COUNT(DISTINCT order_id) AS order_count
        FROM store_orders
        GROUP BY product_id
    ),
    product_pairs AS (
        SELECT
            a.product_id AS product_a,
            b.product_id AS product_b,
            COUNT(DISTINCT a.order_id) AS pair_count
        FROM store_orders a
        JOIN store_orders b ON a.order_id = b.order_id AND a.product_id < b.product_id
        GROUP BY a.product_id, b.product_id
    )
    SELECT
        pa.sku AS sku_a,
        pa.name AS product_a,
        pb.sku AS sku_b,
        pb.name AS product_b,
        pp.pair_count,
        ROUND(pp.pair_count * 100.0 / t.total, 2) AS support_pct,
        ROUND(pp.pair_count * 100.0 / NULLIF(poc_a.order_count, 0), 2) AS confidence_a_to_b,
        ROUND(pp.pair_count * 100.0 / NULLIF(poc_b.order_count, 0), 2) AS confidence_b_to_a,
        ROUND((pp.pair_count * 1.0 / NULLIF(poc_a.order_count, 0))
            / NULLIF(poc_b.order_count * 1.0 / t.total, 0), 2) AS lift,
        CASE
            WHEN (pp.pair_count * 1.0 / NULLIF(poc_a.order_count, 0))
                / NULLIF(poc_b.order_count * 1.0 / t.total, 0) > 3 THEN '强关联'
            WHEN (pp.pair_count * 1.0 / NULLIF(poc_a.order_count, 0))
                / NULLIF(poc_b.order_count * 1.0 / t.total, 0) > 1.5 THEN '正关联'
            ELSE '弱关联'
        END AS association_strength
    FROM product_pairs pp
    JOIN products pa ON pp.product_a = pa.id
    JOIN products pb ON pp.product_b = pb.id
    JOIN product_order_count poc_a ON pp.product_a = poc_a.product_id
    JOIN product_order_count poc_b ON pp.product_b = poc_b.product_id
    CROSS JOIN total_orders t
    WHERE pp.pair_count * 1.0 / t.total >= p_min_support
      AND (pp.pair_count * 1.0 / NULLIF(poc_a.order_count, 0))
        / NULLIF(poc_b.order_count * 1.0 / t.total, 0) >= p_min_lift
    ORDER BY lift DESC, support_pct DESC
    LIMIT 50;
END//


-- ============================================================
-- 53. 客户品类消费趋势 (按门店)
-- 参数: p_customer_id, p_start_date, p_end_date
-- 输出: 客户在各门店各品类的消费金额和趋势
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:erp_system.sp_customer_category_trend_by_store
CREATE PROCEDURE sp_customer_category_trend_by_store(
    IN p_customer_id BIGINT UNSIGNED,
    IN p_start_date DATE,
    IN p_end_date DATE
)
BEGIN
    SELECT
        w.name AS store_name,
        pc.name AS category_name,
        DATE_FORMAT(so.order_date, '%Y-%m') AS month,
        SUM(soi.amount) AS monthly_spend,
        SUM(soi.quantity) AS monthly_qty,
        COUNT(DISTINCT so.id) AS monthly_orders,
        AVG(soi.unit_price) AS avg_price,
        ROUND(SUM(soi.amount) * 100.0 / NULLIF(
            SUM(SUM(soi.amount)) OVER (PARTITION BY w.id, DATE_FORMAT(so.order_date, '%Y-%m')), 0
        ), 2) AS category_share_in_store,
        ROUND(SUM(soi.amount) * 100.0 / NULLIF(
            SUM(SUM(soi.amount)) OVER (PARTITION BY pc.id, DATE_FORMAT(so.order_date, '%Y-%m')), 0
        ), 2) AS store_share_in_category,
        LAG(SUM(soi.amount)) OVER (PARTITION BY w.id, pc.id ORDER BY DATE_FORMAT(so.order_date, '%Y-%m')) AS prev_month_spend,
        ROUND(
            (SUM(soi.amount) - LAG(SUM(soi.amount)) OVER (PARTITION BY w.id, pc.id ORDER BY DATE_FORMAT(so.order_date, '%Y-%m')))
            / NULLIF(LAG(SUM(soi.amount)) OVER (PARTITION BY w.id, pc.id ORDER BY DATE_FORMAT(so.order_date, '%Y-%m')), 0) * 100
        , 2) AS mom_change_pct
    FROM sales_order_items soi
    JOIN sales_orders so ON soi.order_id = so.id
    JOIN warehouses w ON so.warehouse_id = w.id
    JOIN products p ON soi.product_id = p.id
    JOIN product_categories pc ON p.category_id = pc.id
    WHERE so.customer_id = p_customer_id
      AND so.order_date BETWEEN p_start_date AND p_end_date
      AND so.status NOT IN ('draft', 'cancelled')
    GROUP BY w.name, pc.name, w.id, pc.id, DATE_FORMAT(so.order_date, '%Y-%m')
    ORDER BY month DESC, store_name, category_name;
END//


-- ============================================================
-- 54. 门店销售预测 (基于历史趋势)
-- 参数: p_warehouse_id, p_forecast_days
-- 输出: 未来N天各门店销售额预测
-- 统计原理: 使用7日移动平均 + 周几系数 + 趋势因子
--           预测值 = MA7 * 星期系数 * (1 + 趋势因子)
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:erp_system.sp_store_sales_forecast
CREATE PROCEDURE sp_store_sales_forecast(
    IN p_warehouse_id BIGINT UNSIGNED,
    IN p_forecast_days INT
)
BEGIN
    WITH daily_sales AS (
        SELECT
            so.warehouse_id,
            so.order_date,
            DAYOFWEEK(so.order_date) AS dow,
            SUM(so.total_amount) AS daily_total
        FROM sales_orders so
        WHERE so.order_date >= DATE_SUB(CURDATE(), INTERVAL 60 DAY)
          AND so.status NOT IN ('draft', 'cancelled')
          AND (p_warehouse_id IS NULL OR so.warehouse_id = p_warehouse_id)
        GROUP BY so.warehouse_id, so.order_date, DAYOFWEEK(so.order_date)
    ),
    moving_avg AS (
        SELECT
            warehouse_id,
            order_date,
            dow,
            daily_total,
            AVG(daily_total) OVER (PARTITION BY warehouse_id ORDER BY order_date
                ROWS BETWEEN 6 PRECEDING AND CURRENT ROW) AS ma_7d,
            AVG(daily_total) OVER (PARTITION BY warehouse_id, dow) AS avg_by_dow
        FROM daily_sales
    ),
    dow_coefficient AS (
        SELECT
            warehouse_id,
            dow,
            ROUND(AVG(daily_total) / NULLIF(AVG(AVG(daily_total)) OVER (PARTITION BY warehouse_id), 0), 2) AS dow_coef
        FROM daily_sales
        GROUP BY warehouse_id, dow
    ),
    latest_ma AS (
        SELECT
            warehouse_id,
            daily_total AS latest_sales,
            ma_7d AS latest_ma_7d,
            (ma_7d - LAG(ma_7d, 7) OVER (PARTITION BY warehouse_id ORDER BY order_date)) / NULLIF(LAG(ma_7d, 7) OVER (PARTITION BY warehouse_id ORDER BY order_date), 0) AS trend_factor
        FROM moving_avg
        WHERE order_date = (SELECT MAX(order_date) FROM moving_avg WHERE warehouse_id = moving_avg.warehouse_id)
    )
    SELECT
        w.name AS store_name,
        DATE_ADD(CURDATE(), INTERVAL seq.num DAY) AS forecast_date,
        DAYOFWEEK(DATE_ADD(CURDATE(), INTERVAL seq.num DAY)) AS forecast_dow,
        ROUND(lm.latest_ma_7d * dc.dow_coef * (1 + COALESCE(lm.trend_factor, 0)), 2) AS forecast_sales,
        dc.dow_coef AS day_of_week_coefficient,
        ROUND(lm.latest_ma_7d, 2) AS base_ma_7d,
        ROUND(COALESCE(lm.trend_factor, 0) * 100, 2) AS trend_pct
    FROM latest_ma lm
    JOIN warehouses w ON lm.warehouse_id = w.id
    JOIN dow_coefficient dc ON lm.warehouse_id = dc.warehouse_id
    CROSS JOIN (SELECT 0 AS num UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
                 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9
                 UNION SELECT 10 UNION SELECT 11 UNION SELECT 12 UNION SELECT 13 UNION SELECT 14
                 UNION SELECT 15 UNION SELECT 16 UNION SELECT 17 UNION SELECT 18 UNION SELECT 19
                 UNION SELECT 20 UNION SELECT 21 UNION SELECT 22 UNION SELECT 23 UNION SELECT 24
                 UNION SELECT 25 UNION SELECT 26 UNION SELECT 27 UNION SELECT 28 UNION SELECT 29) seq
    WHERE dc.dow = DAYOFWEEK(DATE_ADD(CURDATE(), INTERVAL seq.num DAY))
      AND seq.num < p_forecast_days
    ORDER BY lm.warehouse_id, forecast_date;
END
-- relation-detector-fixture-end
