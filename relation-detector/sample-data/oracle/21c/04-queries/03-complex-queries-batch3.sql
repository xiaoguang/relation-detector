-- ============================================================
-- 第三批超复杂SQL查询: 客户消费分析 + 合同/税务/质检/项目/审批 (Oracle 21c)
-- 重点覆盖客户消费状态全方位分析
-- ============================================================


-- ============================================================
-- Q39: 客户消费分层全景图 - 五维消费画像
-- 语法: 多CTE + 窗口函数 + NTILE分桶 + 条件聚合 + 多维度交叉
-- 统计原理: 从消费金额(R)、购买频次(F)、客单价(M)、品类宽度、活跃度
--           五个维度构建客户完整消费画像
-- ============================================================

WITH customer_metrics AS (
    SELECT
        so.customer_id,
        c.name AS customer_name,
        c.membership_level,
        c.type AS customer_type,
        -- 金额维度
        SUM(so.total_amount) AS total_spent,
        COUNT(DISTINCT so.id) AS order_count,
        -- 时间维度
        (CURRENT_DATE - MAX(so.order_date)) AS days_since_last,
        (CURRENT_DATE - MIN(so.order_date)) AS days_since_first,
        -- 客单价
        ROUND(SUM(so.total_amount) / NULLIF(COUNT(DISTINCT so.id), 0), 2) AS avg_order_value,
        -- 品类宽度
        COUNT(DISTINCT pc.id) AS category_count,
        -- 退货率
        COALESCE(COUNT(DISTINCT sr.id), 0) AS return_count,
        -- 折扣使用率
        COUNT(DISTINCT pu.id) AS promotion_usage_count,
        COALESCE(SUM(pu.discount_applied), 0) AS total_discount_saved
    FROM sales_orders so
    JOIN customers c ON so.customer_id = c.id
    LEFT JOIN sales_order_items soi ON so.id = soi.order_id
    LEFT JOIN products p ON soi.product_id = p.id
    LEFT JOIN product_categories pc ON p.category_id = pc.id
    LEFT JOIN sales_returns sr ON so.customer_id = sr.customer_id
    LEFT JOIN promotion_usages pu ON so.id = pu.order_id
    WHERE so.status NOT IN ('draft', 'cancelled')
    GROUP BY so.customer_id, c.name, c.membership_level, c.type
),
scored_customers AS (
    SELECT
        *,
        -- 金额分桶 (NTILE 5)
        NTILE(5) OVER (ORDER BY total_spent ASC) AS monetary_tier,
        -- 频次分桶
        NTILE(5) OVER (ORDER BY order_count ASC) AS frequency_tier,
        -- 活跃度分桶
        NTILE(5) OVER (ORDER BY days_since_last DESC) AS recency_tier,
        -- 客单价分桶
        NTILE(5) OVER (ORDER BY avg_order_value ASC) AS aov_tier,
        -- 品类宽度分桶
        NTILE(5) OVER (ORDER BY category_count ASC) AS breadth_tier,
        -- 月均消费
        ROUND(total_spent / NULLIF(TRUNC(MONTHS_BETWEEN(CURRENT_DATE, CURRENT_DATE - days_since_first * INTERVAL '1' DAY)) + 1, 0), 2) AS monthly_avg_spent,
        -- 月均频次
        ROUND(order_count / NULLIF(TRUNC(MONTHS_BETWEEN(CURRENT_DATE, CURRENT_DATE - days_since_first * INTERVAL '1' DAY)) + 1, 0), 2) AS monthly_avg_orders,
        -- 退货率
        ROUND(return_count * 100.0 / NULLIF(order_count, 0), 2) AS return_rate_pct,
        -- 折扣依赖度
        ROUND(total_discount_saved * 100.0 / NULLIF(total_spent + total_discount_saved, 0), 2) AS discount_dependency_pct
    FROM customer_metrics
)
SELECT
    customer_name,
    membership_level,
    customer_type,
    total_spent,
    order_count,
    days_since_last,
    avg_order_value,
    category_count,
    monthly_avg_spent,
    monthly_avg_orders,
    return_rate_pct,
    discount_dependency_pct,
    -- 五维综合评分
    (monetary_tier + frequency_tier + recency_tier + aov_tier + breadth_tier) AS composite_score,
    -- 客户状态
    CASE
        WHEN days_since_last <= 30 THEN '活跃'
        WHEN days_since_last <= 90 THEN '稳定'
        WHEN days_since_last <= 180 THEN '沉睡'
        WHEN days_since_last <= 365 THEN '流失风险'
        ELSE '已流失'
    END AS activity_status,
    -- 消费层级
    CASE
        WHEN (monetary_tier + frequency_tier + recency_tier + aov_tier + breadth_tier) >= 20 THEN '顶级客户'
        WHEN (monetary_tier + frequency_tier + recency_tier + aov_tier + breadth_tier) >= 15 THEN '高价值客户'
        WHEN (monetary_tier + frequency_tier + recency_tier + aov_tier + breadth_tier) >= 10 THEN '中价值客户'
        WHEN (monetary_tier + frequency_tier + recency_tier + aov_tier + breadth_tier) >= 5 THEN '低价值客户'
        ELSE '边缘客户'
    END AS value_segment,
    -- 折扣依赖度风险
    CASE
        WHEN discount_dependency_pct > 30 THEN '高折扣依赖'
        WHEN discount_dependency_pct > 15 THEN '中度折扣依赖'
        ELSE '正常'
    END AS discount_risk,
    fn_get_customer_clv(customer_id) AS predicted_clv,
    fn_get_customer_status(customer_id) AS customer_status,
    fn_get_customer_credit_score(customer_id) AS credit_score
FROM scored_customers
ORDER BY composite_score DESC;


-- ============================================================
-- Q40: 客户消费行为变化趋势 - 环比/同比/移动平均
-- 语法: 多层CTE + LAG多期对比 + 窗口移动平均 + 趋势判断
-- 统计原理: 追踪每个客户的月度消费金额变化
--           预警: 连续2月下降或月消费低于历史均值50%
-- ============================================================

WITH monthly_customer_spend AS (
    SELECT
        so.customer_id,
        c.name AS customer_name,
        TO_CHAR(so.order_date, 'YYYY-MM') AS spend_month,
        SUM(so.total_amount) AS monthly_spend,
        COUNT(DISTINCT so.id) AS monthly_orders,
        AVG(so.total_amount) AS avg_order_value_this_month
    FROM sales_orders so
    JOIN customers c ON so.customer_id = c.id
    WHERE so.order_date >= CURRENT_DATE - INTERVAL '12' MONTH
      AND so.status NOT IN ('draft', 'cancelled')
    GROUP BY so.customer_id, c.name, TO_CHAR(so.order_date, 'YYYY-MM')
),
trend_analysis AS (
    SELECT
        customer_id,
        customer_name,
        spend_month,
        monthly_spend,
        monthly_orders,
        avg_order_value_this_month,
        LAG(monthly_spend, 1) OVER (PARTITION BY customer_id ORDER BY spend_month) AS prev_month_spend,
        LAG(monthly_spend, 2) OVER (PARTITION BY customer_id ORDER BY spend_month) AS prev_2m_spend,
        LAG(monthly_spend, 3) OVER (PARTITION BY customer_id ORDER BY spend_month) AS prev_3m_spend,
        AVG(monthly_spend) OVER (PARTITION BY customer_id ORDER BY spend_month
            ROWS BETWEEN 2 PRECEDING AND CURRENT ROW) AS ma_3m_spend,
        AVG(monthly_spend) OVER (PARTITION BY customer_id) AS historical_avg_spend,
        AVG(monthly_spend) OVER (PARTITION BY customer_id) * 0.5 AS half_avg_spend,
        ROW_NUMBER() OVER (PARTITION BY customer_id ORDER BY spend_month DESC) AS month_rank
    FROM monthly_customer_spend
)
SELECT
    customer_name,
    spend_month,
    monthly_spend,
    monthly_orders,
    avg_order_value_this_month,
    prev_month_spend,
    ma_3m_spend,
    historical_avg_spend,
    ROUND((monthly_spend - prev_month_spend) / NULLIF(prev_month_spend, 0) * 100, 2) AS mom_change_pct,
    ROUND((monthly_spend - prev_3m_spend) / NULLIF(prev_3m_spend, 0) * 100, 2) AS vs_3m_ago_pct,
    ROUND(monthly_spend / NULLIF(historical_avg_spend, 0) * 100, 2) AS vs_avg_pct,
    CASE
        WHEN monthly_spend < half_avg_spend AND prev_month_spend < half_avg_spend THEN '连续低迷'
        WHEN monthly_spend < half_avg_spend THEN '本月异常低'
        WHEN monthly_spend < prev_month_spend AND prev_month_spend < prev_2m_spend THEN '连续下滑'
        WHEN monthly_spend > historical_avg_spend * 1.5 THEN '异常增长'
        WHEN monthly_spend > prev_month_spend * 1.3 THEN '显著增长'
        ELSE '正常波动'
    END AS trend_signal,
    CASE
        WHEN monthly_spend < half_avg_spend AND prev_month_spend < half_avg_spend THEN '流失高风险'
        WHEN monthly_spend < prev_month_spend AND prev_month_spend < prev_2m_spend THEN '流失中风险'
        ELSE '正常'
    END AS churn_alert
FROM trend_analysis
WHERE month_rank <= 6
ORDER BY customer_id, spend_month DESC;


-- ============================================================
-- Q41: 客户品类偏好与交叉购买分析
-- 语法: CTE + 自连接 + 条件聚合 + LISTAGG + 偏好度计算
-- 统计原理: 品类偏好度 = 客户购买某品类金额 / 客户总购买金额
--           交叉购买: 计算客户购买品类组合
-- ============================================================

WITH customer_category_spend AS (
    SELECT
        so.customer_id,
        c.name AS customer_name,
        pc.id AS category_id,
        pc.name AS category_name,
        SUM(soi.amount) AS category_spend,
        COUNT(DISTINCT so.id) AS category_orders
    FROM sales_order_items soi
    JOIN sales_orders so ON soi.order_id = so.id
    JOIN customers c ON so.customer_id = c.id
    JOIN products p ON soi.product_id = p.id
    JOIN product_categories pc ON p.category_id = pc.id
    WHERE so.status NOT IN ('draft', 'cancelled')
    GROUP BY so.customer_id, c.name, pc.id, pc.name
),
customer_total AS (
    SELECT customer_id, SUM(category_spend) AS total_spend
    FROM customer_category_spend
    GROUP BY customer_id
),
customer_preference AS (
    SELECT
        ccs.*,
        ct.total_spend,
        ROUND(ccs.category_spend * 100.0 / NULLIF(ct.total_spend, 0), 2) AS preference_pct,
        RANK() OVER (PARTITION BY ccs.customer_id ORDER BY ccs.category_spend DESC) AS preference_rank
    FROM customer_category_spend ccs
    JOIN customer_total ct ON ccs.customer_id = ct.customer_id
),
customer_category_mix AS (
    SELECT
        customer_id,
        customer_name,
        LISTAGG(CASE WHEN preference_rank <= 3 THEN category_name END, ' + ') WITHIN GROUP (ORDER BY preference_rank) AS top3_categories,
        LISTAGG(category_name || ':' || preference_pct || '%', ' | ') WITHIN GROUP (ORDER BY preference_rank) AS category_distribution,
        SUM(CASE WHEN preference_rank = 1 THEN preference_pct ELSE 0 END) AS top1_pct,
        COUNT(DISTINCT category_id) AS category_count,
        -- 品类集中度 (HHI指数，越高越集中)
        ROUND(SUM(POWER(preference_pct / 100.0, 2)) * 10000, 0) AS hhi_index
    FROM customer_preference
    GROUP BY customer_id, customer_name
)
SELECT
    customer_name,
    top3_categories,
    category_count,
    top1_pct,
    hhi_index,
    category_distribution,
    CASE
        WHEN top1_pct > 70 THEN '单一品类依赖'
        WHEN category_count >= 5 THEN '品类多元化'
        WHEN hhi_index > 5000 THEN '品类集中'
        ELSE '品类适中'
    END AS category_diversity,
    CASE
        WHEN top3_categories LIKE '%电子%' AND top3_categories LIKE '%办公%' THEN '科技办公型'
        WHEN top3_categories LIKE '%食品%' AND top3_categories LIKE '%日用%' THEN '生活日用型'
        WHEN top3_categories LIKE '%服装%' THEN '时尚消费型'
        ELSE '综合消费型'
    END AS customer_persona
FROM customer_category_mix
WHERE total_spend > 0
ORDER BY hhi_index DESC;


-- ============================================================
-- Q42: 客户付款行为分析 - 信用利用率+付款准时率+欠款趋势
-- 语法: CTE + 条件聚合 + 窗口LAG + 欠款趋势
-- 统计原理: 信用利用率 = 未付金额/信用额度
--           付款准时率 = 按时付款订单数/总订单数
--           欠款天数 = 实际付款日 - 到期日
-- ============================================================

WITH customer_payment AS (
    SELECT
        so.customer_id,
        c.name AS customer_name,
        c.credit_limit,
        c.credit_days,
        so.id AS order_id,
        so.order_no,
        so.order_date,
        so.total_amount,
        so.paid_amount,
        so.total_amount - so.paid_amount AS unpaid_amount,
        so.order_date + c.credit_days * INTERVAL '1' DAY AS due_date,
        CASE
            WHEN so.paid_amount >= so.total_amount * 0.99 AND so.status = 'delivered' THEN '已付清'
            WHEN so.paid_amount > 0 AND so.status = 'delivered' THEN '部分付款'
            WHEN so.status = 'delivered' AND so.paid_amount = 0 THEN '未付款'
            WHEN so.status IN ('confirmed', 'delivering') THEN '未到期'
            ELSE '其他'
        END AS payment_status,
        (CURRENT_DATE - (so.order_date + c.credit_days * INTERVAL '1' DAY)) AS overdue_days,
        ROUND(so.total_amount * 100.0 / NULLIF(c.credit_limit, 0), 2) AS credit_utilization_pct_single
    FROM sales_orders so
    JOIN customers c ON so.customer_id = c.id
    WHERE so.status IN ('confirmed', 'delivering', 'delivered')
      AND so.total_amount > so.paid_amount
),
customer_credit_summary AS (
    SELECT
        customer_id,
        customer_name,
        credit_limit,
        credit_days,
        COUNT(*) AS unpaid_order_count,
        SUM(unpaid_amount) AS total_unpaid,
        SUM(CASE WHEN overdue_days > 0 THEN unpaid_amount ELSE 0 END) AS total_overdue,
        MAX(overdue_days) AS max_overdue_days,
        AVG(overdue_days) AS avg_overdue_days,
        ROUND(SUM(unpaid_amount) * 100.0 / NULLIF(credit_limit, 0), 2) AS credit_utilization_pct,
        -- 严重逾期订单(>90天)
        SUM(CASE WHEN overdue_days > 90 THEN unpaid_amount ELSE 0 END) AS severe_overdue_amount,
        COUNT(CASE WHEN overdue_days > 90 THEN 1 END) AS severe_overdue_count
    FROM customer_payment
    GROUP BY customer_id, customer_name, credit_limit, credit_days
)
SELECT
    customer_name,
    credit_limit,
    credit_days,
    unpaid_order_count,
    total_unpaid,
    total_overdue,
    max_overdue_days,
    ROUND(avg_overdue_days, 1) AS avg_overdue_days,
    credit_utilization_pct,
    severe_overdue_amount,
    severe_overdue_count,
    -- 风险等级
    CASE
        WHEN credit_utilization_pct > 90 OR severe_overdue_count > 3 THEN '极高风险'
        WHEN credit_utilization_pct > 70 OR max_overdue_days > 90 THEN '高风险'
        WHEN credit_utilization_pct > 50 OR max_overdue_days > 60 THEN '中风险'
        WHEN credit_utilization_pct > 30 OR max_overdue_days > 30 THEN '关注'
        ELSE '正常'
    END AS status_label,
    -- 建议信用额度调整
    CASE
        WHEN credit_utilization_pct > 90 AND severe_overdue_count > 3
        THEN ROUND(credit_limit * 0.5, 0)
        WHEN credit_utilization_pct > 70
        THEN ROUND(credit_limit * 0.8, 0)
        ELSE credit_limit
    END AS suggested_credit_limit,
    fn_get_customer_credit_score(customer_id) AS credit_score,
    fn_get_customer_status(customer_id) AS customer_status
FROM customer_credit_summary
ORDER BY total_unpaid DESC;


-- ============================================================
-- Q43: 客户复购率与购买间隔分析
-- 语法: CTE + 窗口函数 + 间隔分布 + 复购周期
-- 统计原理: 复购率 = 购买>=2次客户/总客户
--           平均购买间隔 = 客户总活跃天数/(购买次数-1)
--           购买间隔分布: 7天/14天/30天/60天/90天+
-- ============================================================

WITH customer_orders_sequence AS (
    SELECT
        so.customer_id,
        so.order_date,
        ROW_NUMBER() OVER (PARTITION BY so.customer_id ORDER BY so.order_date) AS order_seq,
        LAG(so.order_date) OVER (PARTITION BY so.customer_id ORDER BY so.order_date) AS prev_order_date
    FROM sales_orders so
    WHERE so.status NOT IN ('draft', 'cancelled')
),
purchase_intervals AS (
    SELECT
        customer_id,
        order_date,
        order_seq,
        prev_order_date,
        (order_date - prev_order_date) AS days_since_last_purchase
    FROM customer_orders_sequence
    WHERE prev_order_date IS NOT NULL
),
customer_repurchase_stats AS (
    SELECT
        customer_id,
        COUNT(*) + 1 AS total_orders,
        MIN(order_date) AS first_order,
        MAX(order_date) AS last_order,
        AVG(days_since_last_purchase) AS avg_purchase_interval,
        STDDEV(days_since_last_purchase) AS stddev_interval,
        MIN(days_since_last_purchase) AS min_interval,
        MAX(days_since_last_purchase) AS max_interval,
        -- 最近一次间隔
        (SELECT days_since_last_purchase FROM purchase_intervals pi2
         WHERE pi2.customer_id = pi.customer_id ORDER BY order_date DESC FETCH FIRST 1 ROWS ONLY) AS latest_interval,
        -- 间隔趋势 (最近3次平均 vs 总体平均)
        (SELECT AVG(days_since_last_purchase) FROM (
            SELECT days_since_last_purchase FROM purchase_intervals pi3
            WHERE pi3.customer_id = pi.customer_id ORDER BY order_date DESC FETCH FIRST 3 ROWS ONLY
        ) t) AS recent_3_avg_interval
    FROM purchase_intervals pi
    GROUP BY customer_id
    HAVING total_orders >= 2
)
SELECT
    c.name AS customer_name,
    c.membership_level,
    rs.total_orders,
    rs.first_order,
    rs.last_order,
    (CURRENT_DATE - rs.last_order) AS days_since_last,
    ROUND(rs.avg_purchase_interval, 1) AS avg_purchase_interval_days,
    ROUND(rs.latest_interval, 0) AS latest_interval_days,
    ROUND(rs.recent_3_avg_interval, 1) AS recent_3_avg_interval,
    -- 间隔趋势
    CASE
        WHEN rs.recent_3_avg_interval > rs.avg_purchase_interval * 1.5 THEN '购买间隔拉长'
        WHEN rs.recent_3_avg_interval < rs.avg_purchase_interval * 0.7 THEN '购买频率加快'
        ELSE '购买频率稳定'
    END AS interval_trend,
    -- 购买间隔分布标签
    CASE
        WHEN rs.avg_purchase_interval <= 7 THEN '高频(周购)'
        WHEN rs.avg_purchase_interval <= 14 THEN '次高频(双周购)'
        WHEN rs.avg_purchase_interval <= 30 THEN '中频(月购)'
        WHEN rs.avg_purchase_interval <= 60 THEN '低频(双月购)'
        WHEN rs.avg_purchase_interval <= 90 THEN '低频(季购)'
        ELSE '极低频'
    END AS purchase_frequency_label,
    -- 预计下次购买日期
    rs.last_order + ROUND(rs.avg_purchase_interval, 0) * INTERVAL '1' DAY AS predicted_next_purchase,
    -- 是否已超期未购
    CASE
        WHEN (CURRENT_DATE - rs.last_order) > rs.avg_purchase_interval * 1.5 THEN '超期未购-需关注'
        WHEN (CURRENT_DATE - rs.last_order) > rs.avg_purchase_interval THEN '已达预期间隔'
        ELSE '正常'
    END AS repurchase_alert
FROM customer_repurchase_stats rs
JOIN customers c ON rs.customer_id = c.id
ORDER BY rs.avg_purchase_interval ASC;


-- ============================================================
-- Q44: 客户季节性与周期性消费分析
-- 语法: CTE + 窗口函数 + 季节性分解 + 同比对比
-- 统计原理: 按月汇总消费，计算季节指数
--           季节指数 = 当月平均/总体月平均
--           >1.0表示旺季，<1.0表示淡季
-- ============================================================

WITH monthly_total AS (
    SELECT
        TO_CHAR(so.order_date, 'YYYY-MM') AS month,
        SUM(so.total_amount) AS total_sales,
        COUNT(DISTINCT so.customer_id) AS active_customers,
        COUNT(DISTINCT so.id) AS total_orders,
        ROUND(SUM(so.total_amount) / NULLIF(COUNT(DISTINCT so.id), 0), 2) AS avg_order_value
    FROM sales_orders so
    WHERE so.order_date >= CURRENT_DATE - INTERVAL '24' MONTH
      AND so.status NOT IN ('draft', 'cancelled')
    GROUP BY TO_CHAR(so.order_date, 'YYYY-MM')
),
seasonal_decomposition AS (
    SELECT
        month,
        total_sales,
        active_customers,
        total_orders,
        avg_order_value,
        AVG(total_sales) OVER () AS overall_monthly_avg,
        ROUND(total_sales / NULLIF(AVG(total_sales) OVER (), 0), 2) AS seasonal_index,
        LAG(total_sales, 12) OVER (ORDER BY month) AS same_month_last_year,
        ROUND((total_sales - LAG(total_sales, 12) OVER (ORDER BY month))
            / NULLIF(LAG(total_sales, 12) OVER (ORDER BY month), 0) * 100, 2) AS yoy_growth_pct,
        AVG(total_sales) OVER (ORDER BY month ROWS BETWEEN 2 PRECEDING AND CURRENT ROW) AS ma_3m,
        AVG(total_sales) OVER (ORDER BY month ROWS BETWEEN 5 PRECEDING AND CURRENT ROW) AS ma_6m
    FROM monthly_total
)
SELECT
    month,
    total_sales,
    active_customers,
    total_orders,
    avg_order_value,
    seasonal_index,
    same_month_last_year,
    yoy_growth_pct,
    ma_3m,
    ma_6m,
    CASE
        WHEN seasonal_index > 1.3 THEN '旺季'
        WHEN seasonal_index > 1.1 THEN '偏旺'
        WHEN seasonal_index > 0.9 THEN '正常'
        WHEN seasonal_index > 0.7 THEN '偏淡'
        ELSE '淡季'
    END AS season_label,
    -- 趋势判断
    CASE
        WHEN ma_3m > ma_6m * 1.1 THEN '上升趋势'
        WHEN ma_3m < ma_6m * 0.9 THEN '下降趋势'
        ELSE '平稳'
    END AS trend_direction,
    -- 预测下月
    ROUND(ma_3m * seasonal_index, 2) AS forecast_next_month
FROM seasonal_decomposition
ORDER BY month DESC;


-- ============================================================
-- Q45: 客户消费金额分布 - 长尾分析
-- 语法: CTE + 累计分布 + 帕累托 + 基尼系数近似
-- 统计原理: 头部20%客户贡献80%收入 = 帕累托分布
--           基尼系数 = 收入分布不均度
-- ============================================================

WITH customer_revenue AS (
    SELECT
        customer_id,
        c.name AS customer_name,
        SUM(so.total_amount) AS total_revenue,
        COUNT(DISTINCT so.id) AS order_count
    FROM sales_orders so
    JOIN customers c ON so.customer_id = c.id
    WHERE so.status NOT IN ('draft', 'cancelled')
    GROUP BY customer_id, c.name
),
ranked_revenue AS (
    SELECT
        *,
        ROW_NUMBER() OVER (ORDER BY total_revenue DESC) AS revenue_rank,
        total_revenue * 100.0 / SUM(total_revenue) OVER () AS revenue_pct,
        SUM(total_revenue) OVER (ORDER BY total_revenue DESC
            ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS cumulative_revenue,
        SUM(total_revenue) OVER (ORDER BY total_revenue DESC
            ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) * 100.0
            / SUM(total_revenue) OVER () AS cumulative_revenue_pct,
        ROW_NUMBER() OVER (ORDER BY total_revenue DESC) * 100.0
            / COUNT(*) OVER () AS customer_pct
    FROM customer_revenue
)
SELECT
    customer_name,
    total_revenue,
    order_count,
    revenue_pct,
    ROUND(cumulative_revenue_pct, 2) AS cumulative_revenue_pct,
    ROUND(customer_pct, 2) AS customer_pct,
    CASE
        WHEN cumulative_revenue_pct <= 50 THEN '头部(贡献50%收入)'
        WHEN cumulative_revenue_pct <= 80 THEN '中坚(贡献30%收入)'
        WHEN cumulative_revenue_pct <= 95 THEN '长尾(贡献15%收入)'
        ELSE '极长尾(贡献5%收入)'
    END AS revenue_segment,
    -- 基尼系数贡献
    ROUND(revenue_pct - 100.0 / COUNT(*) OVER (), 2) AS gini_deviation
FROM ranked_revenue
ORDER BY total_revenue DESC;


-- ============================================================
-- Q46: AR/AP账龄综合分析
-- 语法: CTE + UNION ALL + 条件聚合 + 账龄分桶PIVOT
-- 统计原理: 按账龄分桶汇总应收应付
--           净现金流缺口 = 应收 - 应付 (按账期匹配)
-- ============================================================

WITH ar_buckets AS (
    SELECT
        'AR' AS type,
        aging_bucket,
        COUNT(*) AS count,
        SUM(outstanding_amount) AS total_amount,
        SUM(bad_debt_provision) AS total_provision
    FROM ar_aging_snapshots
    WHERE snapshot_date = CURRENT_DATE
    GROUP BY aging_bucket
),
ap_buckets AS (
    SELECT
        'AP' AS type,
        aging_bucket,
        COUNT(*) AS count,
        SUM(outstanding_amount) AS total_amount,
        0 AS total_provision
    FROM ap_aging_snapshots
    WHERE snapshot_date = CURRENT_DATE
    GROUP BY aging_bucket
)
SELECT
    aging_bucket,
    COALESCE(ar.total_amount, 0) AS ar_amount,
    COALESCE(ar.count, 0) AS ar_count,
    COALESCE(ar.total_provision, 0) AS ar_bad_debt_provision,
    COALESCE(ap.total_amount, 0) AS ap_amount,
    COALESCE(ap.count, 0) AS ap_count,
    COALESCE(ar.total_amount, 0) - COALESCE(ap.total_amount, 0) AS net_gap,
    CASE
        WHEN COALESCE(ar.total_amount, 0) > COALESCE(ap.total_amount, 0) * 2 THEN '应收远大于应付'
        WHEN COALESCE(ar.total_amount, 0) > COALESCE(ap.total_amount, 0) THEN '应收大于应付'
        WHEN COALESCE(ap.total_amount, 0) > COALESCE(ar.total_amount, 0) * 2 THEN '应付远大于应收'
        WHEN COALESCE(ap.total_amount, 0) > COALESCE(ar.total_amount, 0) THEN '应付大于应收'
        ELSE '基本平衡'
    END AS balance_status
FROM (
    SELECT aging_bucket FROM ar_buckets
    UNION
    SELECT aging_bucket FROM ap_buckets
) buckets
LEFT JOIN ar_buckets ar ON buckets.aging_bucket = ar.aging_bucket
LEFT JOIN ap_buckets ap ON buckets.aging_bucket = ap.aging_bucket
ORDER BY
    CASE buckets.aging_bucket
        WHEN '未到期' THEN 1 WHEN '1-30天' THEN 2 WHEN '31-60天' THEN 3
        WHEN '61-90天' THEN 4 WHEN '91-180天' THEN 5 WHEN '181-365天' THEN 6
        ELSE 7
    END;


-- ============================================================
-- Q47: 税务分析 - 增值税税负率与进销项匹配
-- 语法: CTE + 条件聚合 + 税负率计算 + 同比对比
-- 统计原理: 税负率 = 应纳税额/销售收入
--           进销项比 = 进项税额/销项税额
--           正常范围: 税负率1-3%, 进销项比70-90%
-- ============================================================

WITH monthly_tax AS (
    SELECT
        ti.tax_period,
        SUM(CASE WHEN ti.tax_direction = 'output' THEN ti.tax_amount ELSE 0 END) AS output_tax,
        SUM(CASE WHEN ti.tax_direction = 'input' THEN ti.tax_amount ELSE 0 END) AS input_tax,
        SUM(CASE WHEN ti.tax_direction = 'output' THEN ti.amount_excluding_tax ELSE 0 END) AS output_revenue,
        COUNT(CASE WHEN ti.tax_direction = 'output' THEN 1 END) AS output_invoice_count,
        COUNT(CASE WHEN ti.tax_direction = 'input' AND ti.verification_status = 'certified' THEN 1 END) AS certified_input_count,
        COUNT(CASE WHEN ti.tax_direction = 'input' AND ti.verification_status = 'pending' THEN 1 END) AS pending_input_count
    FROM tax_invoices ti
    WHERE ti.tax_period >= TO_CHAR(CURRENT_DATE - INTERVAL '12' MONTH, 'YYYY-MM')
      AND ti.status IN ('issued', 'verified')
    GROUP BY ti.tax_period
)
SELECT
    tax_period,
    output_revenue,
    output_tax,
    input_tax,
    output_tax - input_tax AS tax_payable,
    output_invoice_count,
    certified_input_count,
    pending_input_count,
    ROUND(input_tax * 100.0 / NULLIF(output_tax, 0), 2) AS input_output_ratio_pct,
    ROUND((output_tax - input_tax) * 100.0 / NULLIF(output_revenue, 0), 2) AS tax_burden_rate_pct,
    LAG(output_tax - input_tax) OVER (ORDER BY tax_period) AS prev_period_tax_payable,
    ROUND(((output_tax - input_tax) - LAG(output_tax - input_tax) OVER (ORDER BY tax_period))
        / NULLIF(ABS(LAG(output_tax - input_tax) OVER (ORDER BY tax_period)), 0) * 100, 2) AS tax_change_pct,
    CASE
        WHEN (output_tax - input_tax) * 100.0 / NULLIF(output_revenue, 0) > 5 THEN '税负偏高-需关注'
        WHEN (output_tax - input_tax) * 100.0 / NULLIF(output_revenue, 0) < 0.5 THEN '税负偏低-核查风险'
        WHEN input_tax * 100.0 / NULLIF(output_tax, 0) > 95 THEN '进销项比异常高'
        WHEN input_tax * 100.0 / NULLIF(output_tax, 0) < 50 THEN '进销项比偏低'
        ELSE '正常'
    END AS tax_alert
FROM monthly_tax
ORDER BY tax_period DESC;


-- ============================================================
-- Q48: 审批效率分析 - 流程瓶颈识别
-- 语法: CTE + 条件聚合 + 百分位数 + 瓶颈分析
-- 统计原理: 审批时效 = 审批完成时间 - 提交时间
--           瓶颈 = 平均耗时最长的节点
-- ============================================================

WITH approval_timeline AS (
    SELECT
        ai.workflow_id,
        aw.workflow_name,
        ai.id AS instance_id,
        ai.instance_no,
        ai.target_summary,
        ai.submitted_at,
        ai.completed_at,
        EXTRACT(EPOCH FROM (COALESCE(ai.completed_at, CURRENT_TIMESTAMP) - ai.submitted_at)) / 3600 AS total_hours,
        ai.status,
        ai.current_node_level,
        ai.total_nodes,
        (SELECT AVG(EXTRACT(EPOCH FROM (COALESCE(ai2.completed_at, CURRENT_TIMESTAMP) - ai2.submitted_at)) / 3600)
         FROM approval_instances ai2
         WHERE ai2.workflow_id = ai.workflow_id AND ai2.status = 'approved'
        ) AS workflow_avg_hours
    FROM approval_instances ai
    JOIN approval_workflows aw ON ai.workflow_id = aw.id
    WHERE ai.submitted_at >= CURRENT_DATE - INTERVAL '3' MONTH
),
node_efficiency AS (
    SELECT
        an.workflow_id,
        an.node_name,
        an.node_level,
        an.approval_mode,
        COUNT(ar.id) AS total_approvals,
        AVG(EXTRACT(EPOCH FROM (ar.action_at - ai.submitted_at)) / 3600) AS avg_node_hours,
        COUNT(CASE WHEN ar.action = 'reject' THEN 1 END) AS reject_count,
        COUNT(CASE WHEN ar.action = 'delegate' THEN 1 END) AS delegate_count,
        ROUND(COUNT(CASE WHEN ar.action = 'reject' THEN 1 END) * 100.0 / NULLIF(COUNT(ar.id), 0), 2) AS reject_rate
    FROM approval_nodes an
    JOIN approval_records ar ON an.id = ar.node_id
    JOIN approval_instances ai ON ar.instance_id = ai.id
    WHERE ai.submitted_at >= CURRENT_DATE - INTERVAL '3' MONTH
    GROUP BY an.workflow_id, an.node_name, an.node_level, an.approval_mode
)
SELECT
    at.workflow_name,
    COUNT(DISTINCT at.instance_id) AS total_instances,
    COUNT(DISTINCT CASE WHEN at.status = 'approved' THEN at.instance_id END) AS approved_count,
    COUNT(DISTINCT CASE WHEN at.status = 'rejected' THEN at.instance_id END) AS rejected_count,
    COUNT(DISTINCT CASE WHEN at.status = 'in_progress' THEN at.instance_id END) AS in_progress_count,
    ROUND(AVG(at.total_hours), 1) AS avg_total_hours,
    ROUND(at.workflow_avg_hours, 1) AS workflow_avg_hours,
    ROUND(COUNT(DISTINCT CASE WHEN at.status = 'approved' THEN at.instance_id END) * 100.0
        / NULLIF(COUNT(DISTINCT at.instance_id), 0), 2) AS approval_rate,
    -- 瓶颈节点
    (SELECT ne.node_name FROM node_efficiency ne
     WHERE ne.workflow_id = at.workflow_id
     ORDER BY ne.avg_node_hours DESC FETCH FIRST 1 ROWS ONLY
    ) AS bottleneck_node,
    (SELECT ROUND(ne.avg_node_hours, 1) FROM node_efficiency ne
     WHERE ne.workflow_id = at.workflow_id
     ORDER BY ne.avg_node_hours DESC FETCH FIRST 1 ROWS ONLY
    ) AS bottleneck_hours,
    -- 驳回率最高节点
    (SELECT ne.node_name FROM node_efficiency ne
     WHERE ne.workflow_id = at.workflow_id
     ORDER BY ne.reject_rate DESC FETCH FIRST 1 ROWS ONLY
    ) AS most_rejected_node
FROM approval_timeline at
GROUP BY at.workflow_id, at.workflow_name, at.workflow_avg_hours
ORDER BY avg_total_hours DESC;


-- ============================================================
-- Q49: 项目成本与进度综合监控
-- 语法: CTE + 子查询 + 条件聚合 + 挣值分析(EVM)
-- 统计原理: EV(挣值) = 预算 * 实际完成%
--           PV(计划值) = 预算 * 计划完成%
--           AC(实际成本) = 已发生成本
--           SPI = EV/PV (进度绩效)
--           CPI = EV/AC (成本绩效)
-- ============================================================

WITH project_metrics AS (
    SELECT
        p.id,
        p.project_no,
        p.name,
        p.project_type,
        p.budget,
        p.start_date,
        p.planned_end_date,
        p.actual_end_date,
        p.status,
        (COALESCE(p.actual_end_date, CURRENT_DATE) - p.start_date) AS elapsed_days,
        (p.planned_end_date - p.start_date) AS planned_days,
        ROUND((COALESCE(p.actual_end_date, CURRENT_DATE) - p.start_date) * 100.0
            / NULLIF((p.planned_end_date - p.start_date), 0), 2) AS time_elapsed_pct,
        COALESCE(SUM(pc.amount), 0) AS actual_cost,
        COUNT(DISTINCT pc.id) AS cost_record_count
    FROM projects p
    LEFT JOIN project_costs pc ON p.id = pc.project_id
    GROUP BY p.id, p.project_no, p.name, p.project_type, p.budget,
             p.start_date, p.planned_end_date, p.actual_end_date, p.status
),
evm_analysis AS (
    SELECT
        *,
        -- EV: 假设完成百分比 = 实际成本/预算 和 时间进度百分比 的较大值
        LEAST(ROUND(budget * actual_cost / NULLIF(budget, 0), 2), budget) AS earned_value,
        ROUND(budget * LEAST(time_elapsed_pct / 100.0, 1.0), 2) AS planned_value,
        ROUND(actual_cost / NULLIF(budget, 0) * 100, 2) AS cost_pct,
        ROUND(LEAST(budget * actual_cost / NULLIF(budget, 0), budget)
            / NULLIF(ROUND(budget * LEAST(time_elapsed_pct / 100.0, 1.0), 2), 0), 2) AS spi,
        ROUND(LEAST(budget * actual_cost / NULLIF(budget, 0), budget)
            / NULLIF(actual_cost, 0), 2) AS cpi
    FROM project_metrics
)
SELECT
    project_no,
    name,
    project_type,
    budget,
    actual_cost,
    cost_pct,
    time_elapsed_pct,
    earned_value,
    planned_value,
    spi,
    cpi,
    budget - actual_cost AS budget_remaining,
    CASE
        WHEN status = 'completed' THEN '已完成'
        WHEN spi >= 1 AND cpi >= 1 THEN '进度超前+成本节约'
        WHEN spi >= 1 AND cpi < 1 THEN '进度超前+成本超支'
        WHEN spi < 1 AND cpi >= 1 THEN '进度滞后+成本节约'
        WHEN spi < 0.7 OR cpi < 0.7 THEN '严重偏离'
        ELSE '进度滞后+成本超支'
    END AS evm_status,
    CASE
        WHEN cpi < 0.8 THEN ROUND(actual_cost + (budget - earned_value) / NULLIF(cpi, 0), 2)
        ELSE ROUND(actual_cost + (budget - earned_value), 2)
    END AS eac_estimate,
    DENSE_RANK() OVER (ORDER BY spi ASC, cpi ASC) AS risk_rank
FROM evm_analysis
ORDER BY risk_rank ASC;


-- ============================================================
-- Q50: 序列号全生命周期追踪
-- 语法: 多表JOIN + 递归查询 + 生命周期状态链
-- 统计原理: 追踪每个序列号从采购入库到销售/退货/报废的完整链路
-- ============================================================

SELECT
    sn.serial_no,
    p.sku AS product_sku,
    p.name AS product_name,
    sn.status AS current_status,
    -- 采购入库信息
    (SELECT pr.receipt_no FROM purchase_receipts pr WHERE pr.id = sn.purchase_receipt_id) AS purchase_receipt,
    (SELECT pri.created_at FROM purchase_receipt_items pri WHERE pri.product_id = sn.product_id FETCH FIRST 1 ROWS ONLY) AS inbound_time,
    -- 销售出库信息
    (SELECT so.order_no FROM sales_orders so WHERE so.id = sn.sales_order_id) AS sales_order,
    (SELECT so.order_date FROM sales_orders so WHERE so.id = sn.sales_order_id) AS sales_date,
    -- 退货信息
    (SELECT sr.return_no FROM sales_returns sr WHERE sr.id = sn.return_id) AS return_order,
    -- 保修状态
    sn.warranty_start,
    sn.warranty_end,
    CASE
        WHEN sn.warranty_end IS NOT NULL AND sn.warranty_end < CURRENT_DATE THEN '已过保'
        WHEN sn.warranty_end IS NOT NULL THEN '保修中(剩余' || (sn.warranty_end - CURRENT_DATE) || '天)'
        ELSE '无保修'
    END AS warranty_status,
    -- 流转次数
    (SELECT COUNT(*) FROM serial_number_logs snl WHERE snl.serial_number_id = sn.id) AS event_count,
    -- 最近流转
    (SELECT snl.event_type FROM serial_number_logs snl
     WHERE snl.serial_number_id = sn.id ORDER BY snl.event_time DESC FETCH FIRST 1 ROWS ONLY) AS last_event,
    (SELECT snl.event_time FROM serial_number_logs snl
     WHERE snl.serial_number_id = sn.id ORDER BY snl.event_time DESC FETCH FIRST 1 ROWS ONLY) AS last_event_time,
    -- 流转历史摘要
    (SELECT LISTAGG(TO_CHAR(snl.event_time, 'YYYY-MM-DD') || ':' || snl.event_type, ' -> ') WITHIN GROUP (ORDER BY snl.event_time) FROM serial_number_logs snl WHERE snl.serial_number_id = sn.id
    ) AS lifecycle_path
FROM serial_numbers sn
JOIN products p ON sn.product_id = p.id
ORDER BY sn.serial_no;


-- ============================================================
-- Q51: 综合查询 - 带薪休假余额+出勤+绩效联动
-- 语法: CTE + 多表JOIN + 条件聚合 + 函数调用
-- ============================================================

WITH leave_balance AS (
    SELECT
        e.id AS employee_id,
        e.name,
        d.name AS dept_name,
        COUNT(CASE WHEN lr.leave_type = 'annual' AND lr.status = 'approved' THEN 1 END) AS annual_leave_used,
        COUNT(CASE WHEN lr.leave_type = 'sick' AND lr.status = 'approved' THEN 1 END) AS sick_leave_used,
        COUNT(CASE WHEN lr.leave_type = 'personal' AND lr.status = 'approved' THEN 1 END) AS personal_leave_used,
        15 - COUNT(CASE WHEN lr.leave_type = 'annual' AND lr.status = 'approved' THEN 1 END) AS annual_leave_remaining
    FROM employees e
    LEFT JOIN departments d ON e.department_id = d.id
    LEFT JOIN leave_records lr ON e.id = lr.employee_id
        AND EXTRACT(YEAR FROM lr.start_date) = EXTRACT(YEAR FROM CURRENT_DATE)
    WHERE e.status IN ('active', 'probation')
    GROUP BY e.id, e.name, d.name
),
attendance_summary AS (
    SELECT
        employee_id,
        COUNT(CASE WHEN status = 'absent' THEN 1 END) AS absent_days_ytd,
        COUNT(CASE WHEN status = 'late' THEN 1 END) AS late_days_ytd,
        ROUND(COUNT(CASE WHEN status IN ('normal', 'late', 'early', 'overtime') THEN 1 END) * 100.0
            / NULLIF(COUNT(*), 0), 2) AS attendance_rate_ytd
    FROM attendance
    WHERE EXTRACT(YEAR FROM attendance_date) = EXTRACT(YEAR FROM CURRENT_DATE)
    GROUP BY employee_id
),
latest_performance AS (
    SELECT
        employee_id,
        total_score,
        grade,
        review_period
    FROM (
        SELECT
            employee_id, total_score, grade, review_period,
            ROW_NUMBER() OVER (PARTITION BY employee_id ORDER BY review_period DESC) AS rn
        FROM performance_reviews
        WHERE status = 'confirmed'
    ) t WHERE rn = 1
)
SELECT
    lb.employee_id,
    lb.name,
    lb.dept_name,
    lb.annual_leave_used,
    lb.annual_leave_remaining,
    lb.sick_leave_used,
    lb.personal_leave_used,
    COALESCE(ats.absent_days_ytd, 0) AS absent_days_ytd,
    COALESCE(ats.late_days_ytd, 0) AS late_days_ytd,
    COALESCE(ats.attendance_rate_ytd, 100) AS attendance_rate_ytd,
    COALESCE(lp.total_score, 0) AS latest_performance_score,
    COALESCE(lp.grade, 'N/A') AS latest_grade,
    lp.review_period AS latest_review_period,
    fn_get_attendance_rate(lb.employee_id, TO_CHAR(CURRENT_DATE, 'YYYY-MM')) AS attendance_rate_this_month,
    CASE
        WHEN lb.annual_leave_remaining < 3 AND EXTRACT(MONTH FROM CURRENT_DATE) < 10 THEN '年假余额偏低'
        WHEN COALESCE(ats.absent_days_ytd, 0) > 10 THEN '缺勤过多'
        WHEN COALESCE(ats.attendance_rate_ytd, 100) < 85 THEN '出勤率低'
        WHEN COALESCE(lp.total_score, 0) < 60 THEN '绩效不达标'
        WHEN COALESCE(lp.total_score, 0) >= 90 AND COALESCE(ats.attendance_rate_ytd, 100) >= 95
            THEN '优秀员工候选'
        ELSE '正常'
    END AS hr_alert
FROM leave_balance lb
LEFT JOIN attendance_summary ats ON lb.employee_id = ats.employee_id
LEFT JOIN latest_performance lp ON lb.employee_id = lp.employee_id
ORDER BY
    CASE
        WHEN COALESCE(lp.total_score, 0) < 60 THEN 1
        WHEN COALESCE(ats.attendance_rate_ytd, 100) < 85 THEN 2
        ELSE 3
    END, lb.annual_leave_remaining ASC;
