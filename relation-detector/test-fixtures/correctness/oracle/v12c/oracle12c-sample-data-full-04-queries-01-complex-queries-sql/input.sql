-- ============================================================
-- ERP系统超复杂SQL查询集合 (Oracle 12c)
-- 覆盖: 多表JOIN, CTE(递归/非递归), 窗口函数, 嵌套子查询,
--       GROUP BY + HAVING, 复杂聚合组合, ROLLUP, UNION,
--       相关子查询, EXISTS, LATERAL, 条件聚合, 派生表
-- ============================================================


-- ============================================================
-- 一、库存分析类查询
-- ============================================================

-- Q1: 缺货预警 - 多维度缺货分析
-- 语法: 多表JOIN + 条件聚合 + HAVING + 子查询 + CASE WHEN + 窗口函数
-- 统计原理: 当前可用库存 <= 最低库存 = 缺货
--           日均销量 = 近30天销量 / 30
--           可售天数 = 当前库存 / 日均销量
--           紧急程度: 可售天数<3=紧急, 3-7=警告, >7=正常
SELECT
    p.sku,
    p.name AS product_name,
    pc.name AS category_name,
    p.min_stock,
    p.max_stock,
    stock.current_qty,
    stock.locked_qty,
    stock.available_qty,
    p.min_stock - stock.available_qty AS shortage_qty,
    ROUND(sales.avg_daily_sales, 2) AS avg_daily_sales_30d,
    CASE
        WHEN sales.avg_daily_sales > 0
        THEN ROUND(stock.available_qty / sales.avg_daily_sales, 1)
        ELSE 9999
    END AS days_of_stock,
    RANK() OVER (ORDER BY (p.min_stock - stock.available_qty) DESC) AS shortage_rank,
    DENSE_RANK() OVER (PARTITION BY pc.name ORDER BY stock.available_qty ASC) AS category_shortage_rank,
    ROUND(stock.available_qty * 100.0 / NULLIF(p.max_stock, 0), 2) AS stock_fill_rate_pct,
    CASE
        WHEN stock.available_qty <= 0 THEN '缺货'
        WHEN sales.avg_daily_sales > 0 AND stock.available_qty / sales.avg_daily_sales < 3 THEN '紧急'
        WHEN sales.avg_daily_sales > 0 AND stock.available_qty / sales.avg_daily_sales < 7 THEN '警告'
        WHEN stock.available_qty <= p.min_stock THEN '低库存'
        ELSE '正常'
    END AS urgency_level,
    (SELECT LISTAGG(w.name, ', ') WITHIN GROUP (ORDER BY w.name)
     FROM inventory i2
     JOIN warehouses w ON i2.warehouse_id = w.id
     WHERE i2.product_id = p.id AND i2.available_quantity > 0
    ) AS available_warehouses
FROM products p
JOIN product_categories pc ON p.category_id = pc.id
LEFT JOIN (
    -- 派生表: 当前库存汇总
    SELECT
        product_id,
        SUM(quantity) AS current_qty,
        SUM(locked_quantity) AS locked_qty,
        SUM(available_quantity) AS available_qty
    FROM inventory
    GROUP BY product_id
) stock ON p.id = stock.product_id
LEFT JOIN (
    -- 派生表: 近30天日均销量
    SELECT
        soi.product_id,
        SUM(soi.quantity) / 30.0 AS avg_daily_sales
    FROM sales_order_items soi
    JOIN sales_orders so ON soi.order_id = so.id
    WHERE so.order_date >= CURRENT_DATE - INTERVAL '30' DAY
      AND so.status NOT IN ('draft', 'cancelled')
    GROUP BY soi.product_id
) sales ON p.id = sales.product_id
WHERE p.status = 'active'
HAVING urgency_level IN ('缺货', '紧急', '低库存')
ORDER BY shortage_rank ASC
FETCH FIRST 50 ROWS ONLY;


-- Q2: 批号过期风险分析
-- 语法: 多表JOIN + 条件聚合 + 多层CASE + 日期计算
-- 统计原理: 按过期时间段分组统计库存金额
--           过期金额 = 即将过期数量 * 进货价
SELECT
    CASE
        WHEN pb.expiry_date < CURRENT_DATE THEN '已过期'
        WHEN pb.expiry_date <= CURRENT_DATE + INTERVAL '30' DAY THEN '30天内过期'
        WHEN pb.expiry_date <= CURRENT_DATE + INTERVAL '60' DAY THEN '60天内过期'
        WHEN pb.expiry_date <= CURRENT_DATE + INTERVAL '90' DAY THEN '90天内过期'
        ELSE '90天后过期'
    END AS expiry_bucket,
    COUNT(DISTINCT pb.id) AS batch_count,
    COUNT(DISTINCT pb.product_id) AS product_count,
    SUM(pb.current_qty) AS total_qty_at_risk,
    ROUND(SUM(pb.current_qty * pb.purchase_price), 2) AS total_value_at_risk,
    ROUND(AVG(pb.current_qty), 0) AS avg_qty_per_batch,
    LISTAGG(p.sku || ':' || pb.batch_no || '(' || pb.current_qty || ')', '; ') WITHIN GROUP (ORDER BY pb.expiry_date) AS batch_details
FROM product_batches pb
JOIN products p ON pb.product_id = p.id
WHERE pb.status = 'active'
  AND pb.current_qty > 0
GROUP BY expiry_bucket
ORDER BY
    CASE expiry_bucket
        WHEN '已过期' THEN 1
        WHEN '30天内过期' THEN 2
        WHEN '60天内过期' THEN 3
        WHEN '90天内过期' THEN 4
        ELSE 5
    END;


-- Q3: 库存周转率ABC分析 (帕累托分析)
-- 语法: CTE + 窗口函数(SUM OVER, ROW_NUMBER) + 累计百分比
-- 统计原理: A类=前70%销售额, B类=70-90%, C类=后10%
--           按销售额降序排列，计算累计销售额占比
WITH product_sales AS (
    SELECT
        soi.product_id,
        p.sku,
        p.name AS product_name,
        pc.name AS category_name,
        SUM(soi.amount) AS total_sales_amount,
        SUM(soi.quantity) AS total_sales_qty,
        ROUND(SUM(soi.amount) * 100.0 / SUM(SUM(soi.amount)) OVER (), 2) AS sales_pct
    FROM sales_order_items soi
    JOIN sales_orders so ON soi.order_id = so.id
    JOIN products p ON soi.product_id = p.id
    JOIN product_categories pc ON p.category_id = pc.id
    WHERE so.order_date >= CURRENT_DATE - INTERVAL '180' DAY
      AND so.status NOT IN ('draft', 'cancelled')
    GROUP BY soi.product_id, p.sku, p.name, pc.name
),
ranked_products AS (
    SELECT
        *,
        ROW_NUMBER() OVER (ORDER BY total_sales_amount DESC) AS sales_rank,
        SUM(sales_pct) OVER (ORDER BY total_sales_amount DESC
            ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS cumulative_pct,
        SUM(total_sales_qty) OVER (ORDER BY total_sales_amount DESC
            ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS cumulative_qty
    FROM product_sales
)
SELECT
    sales_rank,
    sku,
    product_name,
    category_name,
    total_sales_amount,
    total_sales_qty,
    sales_pct,
    ROUND(cumulative_pct, 2) AS cumulative_pct,
    CASE
        WHEN cumulative_pct <= 70 THEN 'A类-畅销'
        WHEN cumulative_pct <= 90 THEN 'B类-平销'
        ELSE 'C类-滞销'
    END AS abc_class,
    (SELECT SUM(i.available_quantity) FROM inventory i WHERE i.product_id = rp.product_id) AS current_stock,
    (SELECT AVG(sp.lead_time_days) FROM supplier_products sp WHERE sp.product_id = rp.product_id AND sp.is_preferred = 1) AS lead_time_days
FROM ranked_products rp
ORDER BY sales_rank ASC;


-- ============================================================
-- 二、销售分析类查询
-- ============================================================

-- Q4: 销售漏斗分析 - 客户购买行为分层
-- 语法: CTE + 多表JOIN + 条件聚合 + LAG窗口函数 + 环比计算
-- 统计原理: 按客户分层(新客/活跃/沉睡/流失)
--           环比增长率 = (本期-上期)/上期*100%
WITH monthly_customer_sales AS (
    SELECT
        so.customer_id,
        c.name AS customer_name,
        c.membership_level,
        TO_CHAR(so.order_date, 'YYYY-MM') AS sale_month,
        COUNT(DISTINCT so.id) AS order_count,
        SUM(so.total_amount) AS monthly_amount,
        COUNT(DISTINCT soi.product_id) AS distinct_products
    FROM sales_orders so
    JOIN customers c ON so.customer_id = c.id
    LEFT JOIN sales_order_items soi ON so.id = soi.order_id
    WHERE so.order_date >= CURRENT_DATE - INTERVAL '6' MONTH
      AND so.status NOT IN ('draft', 'cancelled')
    GROUP BY so.customer_id, c.name, c.membership_level, TO_CHAR(so.order_date, 'YYYY-MM')
),
customer_with_lag AS (
    SELECT
        *,
        LAG(monthly_amount) OVER (PARTITION BY customer_id ORDER BY sale_month) AS prev_month_amount,
        LAG(sale_month) OVER (PARTITION BY customer_id ORDER BY sale_month) AS prev_month,
        ROW_NUMBER() OVER (PARTITION BY customer_id ORDER BY sale_month DESC) AS month_rank,
        COUNT(*) OVER (PARTITION BY customer_id) AS active_months
    FROM monthly_customer_sales
)
SELECT
    customer_id,
    customer_name,
    status_label,
    sale_month,
    monthly_amount,
    prev_month_amount,
    active_months,
    CASE
        WHEN prev_month_amount IS NOT NULL AND prev_month_amount > 0
        THEN ROUND((monthly_amount - prev_month_amount) / prev_month_amount * 100, 2)
        ELSE NULL
    END AS mom_growth_pct,
    order_count,
    distinct_products,
    ROUND(monthly_amount / NULLIF(order_count, 0), 2) AS avg_order_value,
    CASE
        WHEN month_rank = 1 AND active_months = 1 THEN '新客'
        WHEN month_rank = 1 AND active_months >= 3 THEN '活跃客户'
        WHEN month_rank > 1 AND month_rank <= 2 THEN '近期活跃'
        WHEN month_rank > 2 THEN '沉睡客户'
        ELSE '流失风险'
    END AS customer_segment
FROM customer_with_lag
WHERE month_rank <= 3
ORDER BY customer_id, sale_month DESC;


-- Q5: RFM客户价值分析
-- 语法: CTE + 嵌套子查询 + NTILE窗口函数 + PERCENT_RANK
-- 统计原理: R(Recency)=最近购买距今天数, F(Frequency)=购买频次, M(Monetary)=购买金额
--           R/F/M各分5档，综合RFM得分 = R*100 + F*10 + M
WITH rfm_base AS (
    SELECT
        so.customer_id,
        c.name AS customer_name,
        (CURRENT_DATE - MAX(so.order_date)) AS recency_days,
        COUNT(DISTINCT so.id) AS frequency,
        SUM(so.total_amount) AS monetary,
        (CURRENT_DATE - MIN(so.order_date)) AS customer_lifetime_days
    FROM sales_orders so
    JOIN customers c ON so.customer_id = c.id
    WHERE so.status NOT IN ('draft', 'cancelled')
    GROUP BY so.customer_id, c.name
),
rfm_scored AS (
    SELECT
        *,
        NTILE(5) OVER (ORDER BY recency_days ASC) AS r_score,
        NTILE(5) OVER (ORDER BY frequency DESC) AS f_score,
        NTILE(5) OVER (ORDER BY monetary DESC) AS m_score,
        ROUND(monetary / NULLIF(frequency, 0), 2) AS avg_order_value,
        ROUND(frequency * 365.0 / NULLIF(customer_lifetime_days, 0), 2) AS annual_purchase_rate,
        PERCENT_RANK() OVER (ORDER BY monetary DESC) AS monetary_percentile
    FROM rfm_base
    WHERE customer_lifetime_days > 0
)
SELECT
    customer_id,
    customer_name,
    recency_days,
    frequency,
    monetary,
    r_score,
    f_score,
    m_score,
    (r_score * 100 + f_score * 10 + m_score) AS rfm_total_score,
    avg_order_value,
    ROUND(annual_purchase_rate, 2) AS annual_purchase_rate,
    r_score || f_score || m_score AS rfm_segment,
    CASE
        WHEN r_score >= 4 AND f_score >= 4 AND m_score >= 4 THEN '高价值客户'
        WHEN r_score >= 4 AND f_score <= 2 THEN '新客户/潜力客户'
        WHEN r_score <= 2 AND f_score >= 4 AND m_score >= 4 THEN '重要挽回客户'
        WHEN r_score <= 2 AND f_score <= 2 AND m_score <= 2 THEN '流失客户'
        WHEN f_score >= 3 AND m_score >= 3 THEN '重要发展客户'
        WHEN r_score >= 3 AND f_score <= 2 THEN '一般维持客户'
        ELSE '一般客户'
    END AS customer_value_segment,
    ROUND(monetary_percentile * 100, 1) AS monetary_percentile_pct
FROM rfm_scored
ORDER BY rfm_total_score DESC;


-- Q6: 滞销产品采样分析
-- 语法: CTE + NOT EXISTS + 相关子查询 + 库存金额计算
-- 统计原理: 滞销=近90天无销售但有库存，按库存金额排序
--           滞销金额 = 当前库存 * 进货价
--           滞销天数 = 距最后一次销售的天数
WITH no_sales_90d AS (
    SELECT
        p.id AS product_id,
        p.sku,
        p.name AS product_name,
        pc.name AS category_name,
        p.purchase_price,
        p.retail_price,
        COALESCE(SUM(i.quantity), 0) AS total_stock,
        COALESCE(SUM(i.quantity) * p.purchase_price, 0) AS stock_value,
        p.min_stock,
        (SELECT MAX(so.order_date)
         FROM sales_order_items soi
         JOIN sales_orders so ON soi.order_id = so.id
         WHERE soi.product_id = p.id AND so.status NOT IN ('draft', 'cancelled')
        ) AS last_sale_date,
        (SELECT COALESCE(SUM(soi.quantity), 0)
         FROM sales_order_items soi
         JOIN sales_orders so ON soi.order_id = so.id
         WHERE soi.product_id = p.id
           AND so.order_date >= CURRENT_DATE - INTERVAL '90' DAY
           AND so.status NOT IN ('draft', 'cancelled')
        ) AS recent_90d_sales_qty
    FROM products p
    JOIN product_categories pc ON p.category_id = pc.id
    LEFT JOIN inventory i ON p.id = i.product_id
    WHERE p.status = 'active'
    GROUP BY p.id, p.sku, p.name, pc.name, p.purchase_price, p.retail_price, p.min_stock
    HAVING recent_90d_sales_qty = 0 AND total_stock > 0
)
SELECT
    sku,
    product_name,
    category_name,
    total_stock,
    stock_value,
    purchase_price,
    retail_price,
    ROUND((retail_price - purchase_price) / NULLIF(retail_price, 0) * 100, 1) AS margin_pct,
    last_sale_date,
    (CURRENT_DATE - last_sale_date) AS days_since_last_sale,
    ROUND(stock_value * 0.05 / 12, 2) AS monthly_holding_cost_est,
    CASE
        WHEN (CURRENT_DATE - last_sale_date) > 180 THEN '严重滞销(>180天)'
        WHEN (CURRENT_DATE - last_sale_date) > 90 THEN '滞销(>90天)'
        ELSE '近期滞销'
    END AS stagnation_level,
    RANK() OVER (ORDER BY stock_value DESC) AS stagnation_rank
FROM no_sales_90d
WHERE total_stock > 0
ORDER BY stock_value DESC
FETCH FIRST 30 ROWS ONLY;


-- Q7: 热销产品Top-N分析（带环比和同比）
-- 语法: 多CTE + 全外连接模拟(LEFT JOIN + UNION + RIGHT JOIN) + 同比环比
-- 统计原理: 环比=(本期-上期)/上期*100%, 同比=(本期-去年同期)/去年同期*100%
WITH current_month AS (
    SELECT
        soi.product_id,
        SUM(soi.quantity) AS cur_qty,
        SUM(soi.amount) AS cur_amount,
        COUNT(DISTINCT so.id) AS cur_orders,
        COUNT(DISTINCT so.customer_id) AS cur_customers
    FROM sales_order_items soi
    JOIN sales_orders so ON soi.order_id = so.id
    WHERE TO_CHAR(so.order_date, 'YYYY-MM') = TO_CHAR(CURRENT_DATE, 'YYYY-MM')
      AND so.status NOT IN ('draft', 'cancelled')
    GROUP BY soi.product_id
),
last_month AS (
    SELECT
        soi.product_id,
        SUM(soi.quantity) AS last_qty,
        SUM(soi.amount) AS last_amount
    FROM sales_order_items soi
    JOIN sales_orders so ON soi.order_id = so.id
    WHERE TO_CHAR(so.order_date, 'YYYY-MM') = TO_CHAR(CURRENT_DATE - INTERVAL '1' MONTH, 'YYYY-MM')
      AND so.status NOT IN ('draft', 'cancelled')
    GROUP BY soi.product_id
),
same_month_last_year AS (
    SELECT
        soi.product_id,
        SUM(soi.amount) AS yoy_amount
    FROM sales_order_items soi
    JOIN sales_orders so ON soi.order_id = so.id
    WHERE TO_CHAR(so.order_date, 'YYYY-MM') = TO_CHAR(CURRENT_DATE - INTERVAL '1' YEAR, 'YYYY-MM')
      AND so.status NOT IN ('draft', 'cancelled')
    GROUP BY soi.product_id
)
SELECT
    p.sku,
    p.name AS product_name,
    pc.name AS category_name,
    cm.cur_qty,
    cm.cur_amount,
    cm.cur_orders,
    cm.cur_customers,
    ROUND(cm.cur_amount / NULLIF(cm.cur_orders, 0), 2) AS avg_order_value,
    COALESCE(lm.last_amount, 0) AS last_month_amount,
    COALESCE(smly.yoy_amount, 0) AS last_year_same_month_amount,
    ROUND((cm.cur_amount - COALESCE(lm.last_amount, 0)) / NULLIF(COALESCE(lm.last_amount, 0), 0) * 100, 2) AS mom_growth_pct,
    ROUND((cm.cur_amount - COALESCE(smly.yoy_amount, 0)) / NULLIF(COALESCE(smly.yoy_amount, 0), 0) * 100, 2) AS yoy_growth_pct,
    RANK() OVER (ORDER BY cm.cur_amount DESC) AS hot_rank,
    DENSE_RANK() OVER (ORDER BY cm.cur_qty DESC) AS hot_rank_by_qty
FROM current_month cm
JOIN products p ON cm.product_id = p.id
JOIN product_categories pc ON p.category_id = pc.id
LEFT JOIN last_month lm ON cm.product_id = lm.product_id
LEFT JOIN same_month_last_year smly ON cm.product_id = smly.product_id
ORDER BY cm.cur_amount DESC
FETCH FIRST 20 ROWS ONLY;


-- ============================================================
-- 三、HR分析类查询
-- ============================================================

-- Q8: 员工离职率分析 - 多维度交叉
-- 语法: CTE + 多表JOIN + 条件聚合 + 窗口函数 + 环比
-- 统计原理: 离职率 = 期间离职人数 / 期初期末平均人数 * 100%
--           按部门、职级、工龄段、性别交叉分析
WITH monthly_turnover AS (
    SELECT
        e.department_id,
        d.name AS dept_name,
        p.level AS position_level,
        e.gender,
        CASE
            WHEN TRUNC(MONTHS_BETWEEN(e.resignation_date, e.hire_date) / 12) <= 1 THEN '1年内'
            WHEN TRUNC(MONTHS_BETWEEN(e.resignation_date, e.hire_date) / 12) <= 3 THEN '1-3年'
            WHEN TRUNC(MONTHS_BETWEEN(e.resignation_date, e.hire_date) / 12) <= 5 THEN '3-5年'
            WHEN TRUNC(MONTHS_BETWEEN(e.resignation_date, e.hire_date) / 12) <= 10 THEN '5-10年'
            ELSE '10年以上'
        END AS tenure_group,
        TO_CHAR(e.resignation_date, 'YYYY-MM') AS resign_month,
        COUNT(*) AS resigned_count
    FROM employees e
    JOIN departments d ON e.department_id = d.id
    JOIN positions p ON e.position_id = p.id
    WHERE e.status = 'resigned'
      AND e.resignation_date >= CURRENT_DATE - INTERVAL '12' MONTH
    GROUP BY e.department_id, d.name, p.level, e.gender, tenure_group, TO_CHAR(e.resignation_date, 'YYYY-MM')
),
monthly_headcount AS (
    SELECT
        department_id,
        TO_CHAR(hire_date, 'YYYY-MM') AS month,
        COUNT(*) AS headcount
    FROM employees
    WHERE status IN ('active', 'probation', 'leave')
    GROUP BY department_id, TO_CHAR(hire_date, 'YYYY-MM')
)
SELECT
    mt.dept_name,
    mt.position_level,
    mt.gender,
    mt.tenure_group,
    mt.resign_month,
    mt.resigned_count,
    COALESCE(mh.headcount, 0) AS total_headcount,
    ROUND(mt.resigned_count * 100.0 / NULLIF(COALESCE(mh.headcount, 0), 0), 2) AS turnover_rate_pct,
    SUM(mt.resigned_count) OVER (PARTITION BY mt.department_id ORDER BY mt.resign_month
        ROWS BETWEEN 2 PRECEDING AND CURRENT ROW) AS rolling_3m_resigned,
    AVG(mt.resigned_count) OVER (PARTITION BY mt.department_id) AS avg_monthly_resigned,
    CASE
        WHEN mt.resigned_count > AVG(mt.resigned_count) OVER (PARTITION BY mt.department_id) * 1.5
        THEN '异常高离职'
        WHEN mt.resigned_count > AVG(mt.resigned_count) OVER (PARTITION BY mt.department_id)
        THEN '高于平均'
        ELSE '正常'
    END AS turnover_alert
FROM monthly_turnover mt
LEFT JOIN monthly_headcount mh ON mt.department_id = mh.department_id AND mt.resign_month = mh.month
ORDER BY mt.resign_month DESC, mt.resigned_count DESC;


-- Q9: 经理离职风险评估
-- 语法: 相关子查询 + 条件聚合 + 多维度评分
-- 统计原理: 风险评分 = 缺勤率(30%) + 团队离职率(25%) + 业绩下滑(20%) + 工龄段(15%) + 薪资竞争力(10%)
SELECT
    e.id AS manager_id,
    e.name AS manager_name,
    d.name AS department_name,
    e.salary,
    e.hire_date,
    TRUNC(MONTHS_BETWEEN(CURRENT_DATE, e.hire_date) / 12) AS tenure_years,
    -- 缺勤率(近3个月)
    (SELECT ROUND(COUNT(CASE WHEN a.status = 'absent' THEN 1 END) * 100.0 / NULLIF(COUNT(*), 0), 2)
     FROM attendance a
     WHERE a.employee_id = e.id
       AND a.attendance_date >= CURRENT_DATE - INTERVAL '3' MONTH
    ) AS recent_absent_rate,
    -- 团队离职人数(近6个月)
    (SELECT COUNT(*)
     FROM employees sub
     WHERE sub.manager_id = e.id
       AND sub.status = 'resigned'
       AND sub.resignation_date >= CURRENT_DATE - INTERVAL '6' MONTH
    ) AS team_resigned_6m,
    -- 团队总人数
    (SELECT COUNT(*)
     FROM employees sub
     WHERE sub.manager_id = e.id AND sub.status IN ('active', 'probation', 'leave')
    ) AS team_size,
    -- 部门销售额(本月)
    (SELECT COALESCE(SUM(so.total_amount), 0)
     FROM sales_orders so
     JOIN employees sub ON so.salesperson_id = sub.id
     WHERE sub.department_id = e.department_id
       AND TO_CHAR(so.order_date, 'YYYY-MM') = TO_CHAR(CURRENT_DATE, 'YYYY-MM')
       AND so.status NOT IN ('draft', 'cancelled')
    ) AS dept_monthly_sales,
    -- 部门销售额(上月)
    (SELECT COALESCE(SUM(so.total_amount), 0)
     FROM sales_orders so
     JOIN employees sub ON so.salesperson_id = sub.id
     WHERE sub.department_id = e.department_id
       AND TO_CHAR(so.order_date, 'YYYY-MM') = TO_CHAR(CURRENT_DATE - INTERVAL '1' MONTH, 'YYYY-MM')
       AND so.status NOT IN ('draft', 'cancelled')
    ) AS dept_last_month_sales
FROM employees e
JOIN departments d ON e.department_id = d.id
JOIN positions p ON e.position_id = p.id
WHERE p.name LIKE '%经理%'
  AND e.status IN ('active', 'probation')
HAVING
    (recent_absent_rate > 10)
    OR (team_resigned_6m > team_size * 0.3)
    OR (dept_monthly_sales < dept_last_month_sales * 0.7)
ORDER BY
    (COALESCE(recent_absent_rate, 0) * 0.3
     + COALESCE(team_resigned_6m / NULLIF(team_size, 0), 0) * 100 * 0.25
     + CASE WHEN dept_monthly_sales < dept_last_month_sales * 0.7 THEN 20 ELSE 0 END
     + CASE WHEN tenure_years BETWEEN 2 AND 4 THEN 15 ELSE 0 END) DESC;


-- Q10: 员工不足分析 - 部门编制对比
-- 语法: CTE + 条件聚合 + 多表JOIN + 工作量分析
-- 统计原理: 缺口 = 编制数 - 在职人数
--           超负荷 = 在职人数 < 编制数*0.6
--           人均销售额 = 部门销售额 / 在职销售人数
WITH dept_staffing AS (
    SELECT
        d.id AS dept_id,
        d.name AS dept_name,
        d.headcount_plan,
        COUNT(CASE WHEN e.status IN ('active', 'probation', 'leave') THEN 1 END) AS current_headcount,
        COUNT(CASE WHEN e.status = 'probation' THEN 1 END) AS probation_count,
        COUNT(CASE WHEN e.status = 'resigned' AND e.resignation_date >= CURRENT_DATE - INTERVAL '30' DAY THEN 1 END) AS recent_resigned,
        SUM(e.salary) AS total_salary_cost,
        ROUND(AVG(e.salary), 2) AS avg_salary
    FROM departments d
    LEFT JOIN employees e ON d.id = e.department_id
    GROUP BY d.id, d.name, d.headcount_plan
),
dept_workload AS (
    SELECT
        d.id AS dept_id,
        COALESCE(SUM(so.total_amount), 0) AS monthly_sales,
        COUNT(DISTINCT so.id) AS monthly_orders,
        COUNT(DISTINCT CASE WHEN st.status = 'open' THEN st.id END) AS open_tickets
    FROM departments d
    LEFT JOIN employees e ON d.id = e.department_id
    LEFT JOIN sales_orders so ON e.id = so.salesperson_id
        AND TO_CHAR(so.order_date, 'YYYY-MM') = TO_CHAR(CURRENT_DATE, 'YYYY-MM')
        AND so.status NOT IN ('draft', 'cancelled')
    LEFT JOIN service_tickets st ON e.id = st.assigned_to AND st.status = 'open'
    GROUP BY d.id
)
SELECT
    ds.dept_name,
    ds.headcount_plan,
    ds.current_headcount,
    ds.headcount_plan - ds.current_headcount AS vacancy,
    ROUND(ds.current_headcount * 100.0 / NULLIF(ds.headcount_plan, 0), 2) AS fill_rate_pct,
    ds.probation_count,
    ds.recent_resigned,
    ds.total_salary_cost,
    ds.avg_salary,
    dw.monthly_sales,
    dw.monthly_orders,
    dw.open_tickets,
    ROUND(dw.monthly_sales / NULLIF(ds.current_headcount, 0), 2) AS revenue_per_head,
    ROUND(dw.monthly_orders / NULLIF(ds.current_headcount, 0), 1) AS orders_per_head,
    CASE
        WHEN ds.current_headcount < ds.headcount_plan * 0.5 THEN '严重不足'
        WHEN ds.current_headcount < ds.headcount_plan * 0.7 THEN '人员不足'
        WHEN ds.current_headcount < ds.headcount_plan * 0.85 THEN '略不足'
        WHEN ds.current_headcount > ds.headcount_plan THEN '超编'
        ELSE '正常'
    END AS staffing_status,
    RANK() OVER (ORDER BY (ds.headcount_plan - ds.current_headcount) DESC) AS vacancy_rank
FROM dept_staffing ds
LEFT JOIN dept_workload dw ON ds.dept_id = dw.dept_id
ORDER BY vacancy DESC;


-- ============================================================
-- 四、财务分析类查询
-- ============================================================

-- Q11: 对账差异分析 - 银行vs账面
-- 语法: CTE + FULL OUTER JOIN模拟 + 条件聚合 + 逐笔比对
-- 统计原理: 银行余额调节表 = 账面余额 + 银行已收企业未收 - 银行已付企业未付
--                               = 银行余额 + 企业已收银行未收 - 企业已付银行未付
WITH book_transactions AS (
    SELECT
        cj.id,
        cj.journal_date,
        cj.journal_type,
        cj.amount,
        cj.counterparty,
        cj.reference_type,
        cj.status,
        CASE WHEN cj.journal_type IN ('cash_in', 'bank_in') THEN cj.amount ELSE 0 END AS debit,
        CASE WHEN cj.journal_type IN ('cash_out', 'bank_out') THEN cj.amount ELSE 0 END AS credit
    FROM cashier_journals cj
    WHERE cj.account_id = 3  -- 工商银行基本户
      AND cj.journal_date >= CURRENT_DATE - INTERVAL '30' DAY
),
recon_summary AS (
    SELECT
        COALESCE(SUM(debit), 0) AS total_book_debit,
        COALESCE(SUM(credit), 0) AS total_book_credit,
        COALESCE(SUM(debit), 0) - COALESCE(SUM(credit), 0) AS net_book_change,
        COUNT(CASE WHEN status = 'pending' THEN 1 END) AS unreconciled_count,
        COALESCE(SUM(CASE WHEN status = 'pending' THEN debit ELSE 0 END), 0) AS unreconciled_in,
        COALESCE(SUM(CASE WHEN status = 'pending' THEN credit ELSE 0 END), 0) AS unreconciled_out
    FROM book_transactions
)
SELECT
    (SELECT current_balance FROM accounts WHERE id = 3) AS book_balance,
    rs.total_book_debit,
    rs.total_book_credit,
    rs.net_book_change,
    rs.unreconciled_count,
    rs.unreconciled_in,
    rs.unreconciled_out,
    (SELECT current_balance FROM accounts WHERE id = 3) + rs.unreconciled_in - rs.unreconciled_out AS adjusted_book_balance,
    -- 模拟银行余额(假设银行有5%的时差)
    ROUND((SELECT current_balance FROM accounts WHERE id = 3) * 0.95, 2) AS estimated_bank_balance,
    ROUND((SELECT current_balance FROM accounts WHERE id = 3) * 0.05, 2) AS estimated_timing_difference,
    CASE
        WHEN rs.unreconciled_count > 10 THEN '差异较大，需重点关注'
        WHEN rs.unreconciled_count > 5 THEN '存在差异，需跟进'
        ELSE '基本匹配'
    END AS status_label
FROM recon_summary rs;


-- Q12: 月度损益表 (P&L) - ROLLUP多维汇总
-- 语法: CTE + GROUP BY ROLLUP + 复杂条件聚合
-- 统计原理: 毛利 = 收入 - 成本
--           营业利润 = 毛利 - 费用
--           净利润 = 营业利润 - 税费
WITH revenue AS (
    SELECT
        TO_CHAR(so.order_date, 'YYYY-MM') AS period,
        d.name AS dept_name,
        SUM(so.total_amount) AS sales_revenue,
        SUM(so.tax_amount) AS sales_tax,
        COUNT(DISTINCT so.id) AS order_count
    FROM sales_orders so
    JOIN employees e ON so.salesperson_id = e.id
    JOIN departments d ON e.department_id = d.id
    WHERE so.order_date >= CURRENT_DATE - INTERVAL '6' MONTH
      AND so.status NOT IN ('draft', 'cancelled')
    GROUP BY TO_CHAR(so.order_date, 'YYYY-MM'), d.name
),
cost AS (
    SELECT
        TO_CHAR(so.order_date, 'YYYY-MM') AS period,
        d.name AS dept_name,
        SUM(soi.quantity * p.purchase_price) AS cogs
    FROM sales_order_items soi
    JOIN sales_orders so ON soi.order_id = so.id
    JOIN employees e ON so.salesperson_id = e.id
    JOIN departments d ON e.department_id = d.id
    JOIN products p ON soi.product_id = p.id
    WHERE so.order_date >= CURRENT_DATE - INTERVAL '6' MONTH
      AND so.status NOT IN ('draft', 'cancelled')
    GROUP BY TO_CHAR(so.order_date, 'YYYY-MM'), d.name
),
salary_cost AS (
    SELECT
        sp.salary_month AS period,
        d.name AS dept_name,
        SUM(sp.net_pay + sp.social_security_company + sp.housing_fund_company) AS total_salary_cost
    FROM salary_payments sp
    JOIN employees e ON sp.employee_id = e.id
    JOIN departments d ON e.department_id = d.id
    WHERE sp.salary_month >= TO_CHAR(CURRENT_DATE - INTERVAL '6' MONTH, 'YYYY-MM')
    GROUP BY sp.salary_month, d.name
)
SELECT
    COALESCE(r.period, c.period, sc.period) AS period,
    COALESCE(r.dept_name, c.dept_name, sc.dept_name) AS department,
    COALESCE(r.sales_revenue, 0) AS revenue,
    COALESCE(c.cogs, 0) AS cost_of_goods,
    COALESCE(r.sales_revenue, 0) - COALESCE(c.cogs, 0) AS gross_profit,
    ROUND((COALESCE(r.sales_revenue, 0) - COALESCE(c.cogs, 0)) * 100.0
        / NULLIF(COALESCE(r.sales_revenue, 0), 0), 2) AS gross_margin_pct,
    COALESCE(sc.total_salary_cost, 0) AS salary_cost,
    COALESCE(r.sales_revenue, 0) - COALESCE(c.cogs, 0) - COALESCE(sc.total_salary_cost, 0) AS operating_profit,
    COALESCE(r.order_count, 0) AS order_count,
    ROUND(COALESCE(r.sales_revenue, 0) / NULLIF(COALESCE(r.order_count, 0), 0), 2) AS avg_order_value
FROM revenue r
LEFT JOIN cost c ON r.period = c.period AND r.dept_name = c.dept_name
LEFT JOIN salary_cost sc ON r.period = sc.period AND r.dept_name = sc.dept_name
ORDER BY period DESC, gross_profit DESC;


-- Q13: 三单匹配差异追踪
-- 语法: 多表JOIN + 条件聚合 + 差异金额计算
SELECT
    twm.id AS matching_id,
    inv.invoice_no,
    po.order_no AS po_no,
    pr.receipt_no,
    p.sku,
    p.name AS product_name,
    twm.po_quantity,
    twm.receipt_quantity,
    twm.invoice_quantity,
    twm.po_price,
    twm.receipt_price,
    twm.invoice_price,
    CASE WHEN twm.quantity_match THEN 'OK' ELSE '差异' END AS qty_check,
    CASE WHEN twm.price_match THEN 'OK' ELSE '差异' END AS price_check,
    twm.match_status,
    (twm.po_quantity - twm.receipt_quantity) AS qty_diff_po_vs_receipt,
    (twm.receipt_quantity - twm.invoice_quantity) AS qty_diff_receipt_vs_invoice,
    (twm.po_price - twm.receipt_price) AS price_diff_po_vs_receipt,
    ABS(twm.po_quantity * twm.po_price - twm.receipt_quantity * twm.receipt_price) AS amount_diff_po_vs_receipt,
    ABS(twm.receipt_quantity * twm.receipt_price - twm.invoice_quantity * twm.invoice_price) AS amount_diff_receipt_vs_invoice
FROM three_way_matching twm
JOIN invoices inv ON twm.invoice_id = inv.id
JOIN purchase_orders po ON twm.purchase_order_id = po.id
JOIN purchase_receipts pr ON twm.purchase_receipt_id = pr.id
JOIN products p ON twm.product_id = p.id
WHERE twm.match_status != 'matched'
ORDER BY amount_diff_po_vs_receipt DESC;


-- Q14: 固定资产折旧预测
-- 语法: 递归CTE + CROSS JOIN + 累计计算
-- 统计原理: 预测未来12个月每个资产的折旧和净值
--           累计折旧 = 历史累计 + 预测月折旧*月数
--           净值 = 原值 - 累计折旧
WITH date_series AS (
    SELECT (TRUNC(CURRENT_DATE, 'MM') + INTERVAL '1' MONTH) AS forecast_date
    UNION ALL
    SELECT (forecast_date + INTERVAL '1' MONTH)
    FROM date_series
    WHERE forecast_date < (CURRENT_DATE + INTERVAL '12' MONTH)
),
asset_forecast AS (
    SELECT
        fa.id,
        fa.asset_no,
        fa.name,
        fa.category,
        fa.purchase_amount,
        fa.salvage_value,
        fa.useful_life_months,
        fa.monthly_depreciation,
        fa.accumulated_depreciation AS current_accumulated,
        fa.net_book_value AS current_net_value,
        fa.status,
        ds.forecast_date
    FROM fixed_assets fa
    CROSS JOIN date_series ds
    WHERE fa.status = 'in_use'
      AND fa.accumulated_depreciation < fa.purchase_amount - fa.salvage_value
)
SELECT
    asset_no,
    name,
    category,
    purchase_amount,
    monthly_depreciation,
    current_accumulated,
    current_net_value,
    TO_CHAR(forecast_date, 'YYYY-MM') AS forecast_month,
    current_accumulated + monthly_depreciation * (
        TRUNC(MONTHS_BETWEEN(forecast_date, CURRENT_DATE)) + 1
    ) AS forecast_accumulated,
    purchase_amount - (
        current_accumulated + monthly_depreciation * (
            TRUNC(MONTHS_BETWEEN(forecast_date, CURRENT_DATE)) + 1
        )
    ) AS forecast_net_value,
    CASE
        WHEN purchase_amount - (
            current_accumulated + monthly_depreciation * (
                TRUNC(MONTHS_BETWEEN(forecast_date, CURRENT_DATE)) + 1
            )
        ) <= salvage_value THEN '已提足折旧'
        ELSE '折旧中'
    END AS depreciation_status
FROM asset_forecast
ORDER BY asset_no, forecast_date;


-- ============================================================
-- 五、综合分析类查询
-- ============================================================

-- Q15: 供应商综合评分 - 价格+交期+质量多维度
-- 语法: 多CTE + 标准化评分(0-100) + 加权汇总
-- 统计原理: 综合评分 = 价格分*30% + 交期分*25% + 质量分*25% + 配合度*20%
--           价格分: 价格越低分越高(标准化)
--           交期分: 延迟越少分越高
--           质量分: 合格率越高分越高
WITH supplier_price_score AS (
    SELECT
        sp.supplier_id,
        AVG(sp.supplier_price) AS avg_price,
        AVG(p.purchase_price) AS avg_market_price,
        ROUND(AVG(sp.supplier_price / NULLIF(p.purchase_price, 0)), 4) AS price_ratio
    FROM supplier_products sp
    JOIN products p ON sp.product_id = p.id
    WHERE sp.is_preferred = 1
    GROUP BY sp.supplier_id
),
supplier_delivery_score AS (
    SELECT
        po.supplier_id,
        COUNT(DISTINCT po.id) AS order_count,
        AVG((COALESCE(po.actual_delivery_date, po.expected_delivery_date) - po.expected_delivery_date)) AS avg_delay_days,
        COUNT(CASE WHEN po.actual_delivery_date > po.expected_delivery_date THEN 1 END) AS delayed_count
    FROM purchase_orders po
    WHERE po.order_date >= CURRENT_DATE - INTERVAL '12' MONTH
    GROUP BY po.supplier_id
),
supplier_quality_score AS (
    SELECT
        po.supplier_id,
        COALESCE(SUM(pri.accepted_qty), 0) AS total_accepted,
        COALESCE(SUM(pri.rejected_qty), 0) AS total_rejected,
        ROUND(COALESCE(SUM(pri.accepted_qty), 0) * 100.0
            / NULLIF(COALESCE(SUM(pri.accepted_qty), 0) + COALESCE(SUM(pri.rejected_qty), 0), 0), 2) AS acceptance_rate
    FROM purchase_orders po
    JOIN purchase_receipts pr ON po.id = pr.order_id
    JOIN purchase_receipt_items pri ON pr.id = pri.receipt_id
    WHERE po.order_date >= CURRENT_DATE - INTERVAL '12' MONTH
    GROUP BY po.supplier_id
)
SELECT
    s.name AS supplier_name,
    s.credit_level AS current_credit_level,
    sps.price_ratio,
    ROUND((1 - LEAST(sps.price_ratio, 1.5)) * 100, 0) AS price_score,
    COALESCE(sds.avg_delay_days, 0) AS avg_delivery_delay,
    ROUND(100 - LEAST(COALESCE(sds.avg_delay_days, 0) * 10, 100), 0) AS delivery_score,
    COALESCE(sqs.acceptance_rate, 100) AS quality_acceptance_rate,
    ROUND(COALESCE(sqs.acceptance_rate, 100), 0) AS quality_score,
    sds.order_count AS total_orders,
    sds.delayed_count,
    COALESCE(sqs.total_rejected, 0) AS total_rejected_qty,
    ROUND(
        (1 - LEAST(sps.price_ratio, 1.5)) * 100 * 0.30
        + (100 - LEAST(COALESCE(sds.avg_delay_days, 0) * 10, 100)) * 0.25
        + COALESCE(sqs.acceptance_rate, 100) * 0.25
        + 80 * 0.20
    , 0) AS composite_score,
    RANK() OVER (ORDER BY
        (1 - LEAST(sps.price_ratio, 1.5)) * 100 * 0.30
        + (100 - LEAST(COALESCE(sds.avg_delay_days, 0) * 10, 100)) * 0.25
        + COALESCE(sqs.acceptance_rate, 100) * 0.25
        + 80 * 0.20
    DESC) AS supplier_rank
FROM suppliers s
LEFT JOIN supplier_price_score sps ON s.id = sps.supplier_id
LEFT JOIN supplier_delivery_score sds ON s.id = sds.supplier_id
LEFT JOIN supplier_quality_score sqs ON s.id = sqs.supplier_id
WHERE s.cooperation_status = 'active'
ORDER BY composite_score DESC;


-- Q16: 多仓库库存分布 - PIVOT透视
-- 语法: 条件聚合PIVOT + 多表JOIN + 库存占比
-- 统计原理: 按仓库做列转行，展示每个产品在各仓库的分布
SELECT
    p.sku,
    p.name AS product_name,
    pc.name AS category_name,
    p.min_stock,
    COALESCE(SUM(i.quantity), 0) AS total_stock,
    COALESCE(SUM(CASE WHEN w.code = 'WH001' THEN i.quantity ELSE 0 END), 0) AS wh_main,
    COALESCE(SUM(CASE WHEN w.code = 'WH002' THEN i.quantity ELSE 0 END), 0) AS wh_dist,
    COALESCE(SUM(CASE WHEN w.code = 'WH003' THEN i.quantity ELSE 0 END), 0) AS wh_returns,
    COALESCE(SUM(CASE WHEN w.code = 'WH004' THEN i.quantity ELSE 0 END), 0) AS wh_cold,
    ROUND(COALESCE(SUM(CASE WHEN w.code = 'WH001' THEN i.quantity ELSE 0 END), 0) * 100.0
        / NULLIF(COALESCE(SUM(i.quantity), 0), 0), 1) AS wh_main_pct,
    ROUND(COALESCE(SUM(CASE WHEN w.code = 'WH002' THEN i.quantity ELSE 0 END), 0) * 100.0
        / NULLIF(COALESCE(SUM(i.quantity), 0), 0), 1) AS wh_dist_pct,
    CASE
        WHEN COALESCE(SUM(i.quantity), 0) = 0 THEN '全仓缺货'
        WHEN COALESCE(SUM(i.quantity), 0) <= p.min_stock THEN '低于安全库存'
        WHEN COALESCE(SUM(CASE WHEN w.code = 'WH001' THEN i.quantity ELSE 0 END), 0) > COALESCE(SUM(i.quantity), 0) * 0.8
        THEN '主仓集中'
        WHEN COALESCE(SUM(CASE WHEN w.code = 'WH002' THEN i.quantity ELSE 0 END), 0) > COALESCE(SUM(i.quantity), 0) * 0.5
        THEN '配送中心集中'
        ELSE '分布合理'
    END AS distribution_status
FROM products p
JOIN product_categories pc ON p.category_id = pc.id
LEFT JOIN inventory i ON p.id = i.product_id
LEFT JOIN warehouses w ON i.warehouse_id = w.id
WHERE p.status = 'active'
GROUP BY p.id, p.sku, p.name, pc.name, p.min_stock
HAVING total_stock < p.min_stock * 2
ORDER BY total_stock ASC
FETCH FIRST 30 ROWS ONLY;


-- Q17: 销售员综合业绩仪表盘
-- 语法: 多CTE + 多窗口函数 + 多维度排名 + 同比环比
-- 统计原理: 综合排名 = 销售额排名*0.4 + 回款率排名*0.3 + 客户数排名*0.2 + 利润率排名*0.1
WITH sales_person_metrics AS (
    SELECT
        e.id,
        e.name,
        d.name AS dept_name,
        COUNT(DISTINCT so.id) AS order_count,
        SUM(so.total_amount) AS total_sales,
        SUM(so.paid_amount) AS total_collected,
        SUM(so.total_amount - so.paid_amount) AS outstanding,
        COUNT(DISTINCT so.customer_id) AS unique_customers,
        ROUND(SUM(so.paid_amount) * 100.0 / NULLIF(SUM(so.total_amount), 0), 2) AS collection_rate,
        ROUND(SUM(so.total_amount) / NULLIF(COUNT(DISTINCT so.id), 0), 2) AS avg_order_value
    FROM employees e
    JOIN departments d ON e.department_id = d.id
    JOIN sales_orders so ON e.id = so.salesperson_id
    WHERE so.order_date >= CURRENT_DATE - INTERVAL '3' MONTH
      AND so.status NOT IN ('draft', 'cancelled')
    GROUP BY e.id, e.name, d.name
),
sales_profit AS (
    SELECT
        so.salesperson_id,
        SUM(soi.amount - soi.quantity * p.purchase_price) AS gross_profit,
        ROUND(SUM(soi.amount - soi.quantity * p.purchase_price) * 100.0
            / NULLIF(SUM(soi.amount), 0), 2) AS profit_margin
    FROM sales_order_items soi
    JOIN sales_orders so ON soi.order_id = so.id
    JOIN products p ON soi.product_id = p.id
    WHERE so.order_date >= CURRENT_DATE - INTERVAL '3' MONTH
      AND so.status NOT IN ('draft', 'cancelled')
    GROUP BY so.salesperson_id
),
commissions AS (
    SELECT
        employee_id,
        SUM(commission_amount) AS total_commission,
        SUM(bonus) AS total_bonus
    FROM sales_commissions
    WHERE period >= TO_CHAR(CURRENT_DATE - INTERVAL '3' MONTH, 'YYYY-MM')
    GROUP BY employee_id
)
SELECT
    spm.name,
    spm.dept_name,
    spm.total_sales,
    spm.total_collected,
    spm.outstanding,
    spm.collection_rate,
    spm.order_count,
    spm.unique_customers,
    spm.avg_order_value,
    COALESCE(sp.gross_profit, 0) AS gross_profit,
    COALESCE(sp.profit_margin, 0) AS profit_margin,
    COALESCE(c.total_commission, 0) AS total_commission,
    COALESCE(c.total_bonus, 0) AS total_bonus,
    RANK() OVER (ORDER BY spm.total_sales DESC) AS sales_rank,
    RANK() OVER (ORDER BY spm.collection_rate DESC) AS collection_rank,
    RANK() OVER (ORDER BY spm.unique_customers DESC) AS customer_rank,
    RANK() OVER (ORDER BY COALESCE(sp.profit_margin, 0) DESC) AS margin_rank,
    RANK() OVER (ORDER BY
        spm.total_sales * 0.4
        + spm.collection_rate * 0.3
        + spm.unique_customers * 0.2
        + COALESCE(sp.profit_margin, 0) * 0.1
    DESC) AS composite_rank,
    ROUND(spm.total_sales * 100.0 / NULLIF(SUM(spm.total_sales) OVER (), 0), 2) AS sales_contribution_pct,
    AVG(spm.total_sales) OVER (PARTITION BY spm.dept_name) AS dept_avg_sales,
    spm.total_sales - AVG(spm.total_sales) OVER (PARTITION BY spm.dept_name) AS vs_dept_avg
FROM sales_person_metrics spm
LEFT JOIN sales_profit sp ON spm.id = sp.salesperson_id
LEFT JOIN commissions c ON spm.id = c.employee_id
ORDER BY composite_rank ASC
FETCH FIRST 20 ROWS ONLY;


-- Q18: 促销活动ROI分析
-- 语法: CTE + LEFT JOIN + 条件聚合 + 增量计算
-- 统计原理: ROI = (促销期间增量销售额 - 折扣成本) / 折扣成本
--           增量销售额 = 促销期销售额 - 基准期日均销售额*促销天数
WITH promo_orders AS (
    SELECT
        pu.promotion_id,
        p.name AS promo_name,
        p.promotion_type,
        p.discount_value,
        p.start_date,
        p.end_date,
        COUNT(DISTINCT pu.order_id) AS promo_order_count,
        SUM(pu.discount_applied) AS total_discount_cost,
        SUM(so.total_amount) AS promo_total_sales,
        COUNT(DISTINCT pu.customer_id) AS promo_customers
    FROM promotion_usages pu
    JOIN promotions p ON pu.promotion_id = p.id
    JOIN sales_orders so ON pu.order_id = so.id
    GROUP BY pu.promotion_id, p.name, p.promotion_type, p.discount_value, p.start_date, p.end_date
),
baseline_sales AS (
    SELECT
        po.promotion_id,
        ROUND(AVG(daily.daily_sales), 2) AS avg_daily_sales_baseline
    FROM promo_orders po
    CROSS JOIN (
        SELECT so.order_date, SUM(so.total_amount) AS daily_sales
        FROM sales_orders so
        WHERE so.order_date BETWEEN CURRENT_DATE - INTERVAL '60' DAY AND CURRENT_DATE - INTERVAL '30' DAY
          AND so.status NOT IN ('draft', 'cancelled')
        GROUP BY so.order_date
    ) daily
    GROUP BY po.promotion_id
)
SELECT
    po.promo_name,
    po.promotion_type,
    po.start_date || ' ~ ' || po.end_date AS promo_period,
    (po.end_date - po.start_date) + 1 AS promo_days,
    po.promo_order_count,
    po.promo_customers,
    po.promo_total_sales,
    po.total_discount_cost,
    bs.avg_daily_sales_baseline,
    ROUND(bs.avg_daily_sales_baseline * ((po.end_date - po.start_date) + 1), 2) AS estimated_baseline_sales,
    ROUND(po.promo_total_sales - bs.avg_daily_sales_baseline * ((po.end_date - po.start_date) + 1), 2) AS incremental_sales,
    ROUND(po.promo_total_sales / NULLIF(po.total_discount_cost, 0), 2) AS sales_per_discount_yuan,
    ROUND((po.promo_total_sales - bs.avg_daily_sales_baseline * ((po.end_date - po.start_date) + 1) - po.total_discount_cost)
        / NULLIF(po.total_discount_cost, 0) * 100, 2) AS roi_pct,
    CASE
        WHEN (po.promo_total_sales - bs.avg_daily_sales_baseline * ((po.end_date - po.start_date) + 1) - po.total_discount_cost)
            / NULLIF(po.total_discount_cost, 0) > 2 THEN '高效促销'
        WHEN (po.promo_total_sales - bs.avg_daily_sales_baseline * ((po.end_date - po.start_date) + 1) - po.total_discount_cost)
            / NULLIF(po.total_discount_cost, 0) > 0 THEN '持平'
        ELSE '亏损促销'
    END AS roi_assessment
FROM promo_orders po
LEFT JOIN baseline_sales bs ON po.promotion_id = bs.promotion_id
ORDER BY roi_pct DESC;


-- Q19: 工单生产效率分析
-- 语法: 多表JOIN + 条件聚合 + 计划vs实际对比
-- 统计原理: 完成率 = 实际产量/计划产量
--           合格率 = (实际产量-不合格)/实际产量
--           物料利用率 = 实际消耗/发料量
SELECT
    wo.order_no,
    p.sku AS product_sku,
    p.name AS product_name,
    wo.planned_quantity,
    wo.completed_quantity,
    wo.rejected_quantity,
    ROUND(wo.completed_quantity * 100.0 / NULLIF(wo.planned_quantity, 0), 2) AS completion_rate,
    ROUND((wo.completed_quantity - wo.rejected_quantity) * 100.0 / NULLIF(wo.completed_quantity, 0), 2) AS yield_rate,
    wo.start_date,
    wo.due_date,
    wo.completed_date,
    (COALESCE(wo.completed_date, CURRENT_DATE) - wo.start_date) AS actual_days,
    (wo.due_date - wo.start_date) AS planned_days,
    CASE
        WHEN wo.completed_date IS NOT NULL AND wo.completed_date > wo.due_date THEN '延期'
        WHEN wo.completed_date IS NOT NULL THEN '按时完成'
        WHEN wo.due_date < CURRENT_DATE THEN '已逾期'
        ELSE '进行中'
    END AS schedule_status,
    wo.priority,
    wo.status,
    (SELECT ROUND(SUM(wom.actual_consumed) / NULLIF(SUM(wom.issued_qty), 0) * 100, 2)
     FROM work_order_materials wom WHERE wom.work_order_id = wo.id
    ) AS material_utilization_pct,
    (SELECT LISTAGG(pp.name || ':' || wom.actual_consumed || wom.unit, ', ') WITHIN GROUP (ORDER BY pp.name)
     FROM work_order_materials wom
     JOIN products pp ON wom.product_id = pp.id
     WHERE wom.work_order_id = wo.id
    ) AS material_consumption
FROM work_orders wo
JOIN products p ON wo.product_id = p.id
ORDER BY wo.start_date DESC
FETCH FIRST 30 ROWS ONLY;


-- Q20: 客服工单SLA分析 - 按优先级和类型统计响应时间
-- 语法: CTE + 条件聚合 + 百分位数 + SLA达标率
-- 统计原理: 响应时间 = 解决时间 - 创建时间
--           达标标准: 紧急<4h, 高<8h, 正常<24h, 低<48h
--           满意度 = 已解决工单的平均评分
WITH ticket_metrics AS (
    SELECT
        st.ticket_type,
        st.priority,
        COUNT(*) AS total_tickets,
        COUNT(CASE WHEN st.status IN ('resolved', 'closed') THEN 1 END) AS resolved_tickets,
        COUNT(CASE WHEN st.status = 'open' THEN 1 END) AS open_tickets,
        COUNT(CASE WHEN st.status = 'escalated' THEN 1 END) AS escalated_tickets,
        AVG(CASE WHEN st.resolved_at IS NOT NULL
            THEN EXTRACT(EPOCH FROM (st.resolved_at - st.created_at)) / 3600
        END) AS avg_resolution_hours,
        AVG(CASE WHEN st.status IN ('resolved', 'closed') THEN st.satisfaction_score END) AS avg_satisfaction,
        COUNT(CASE
            WHEN st.priority = 'urgent' AND EXTRACT(EPOCH FROM (COALESCE(st.resolved_at, CURRENT_TIMESTAMP) - st.created_at)) / 3600 <= 4 THEN 1
            WHEN st.priority = 'high' AND EXTRACT(EPOCH FROM (COALESCE(st.resolved_at, CURRENT_TIMESTAMP) - st.created_at)) / 3600 <= 8 THEN 1
            WHEN st.priority = 'normal' AND EXTRACT(EPOCH FROM (COALESCE(st.resolved_at, CURRENT_TIMESTAMP) - st.created_at)) / 3600 <= 24 THEN 1
            WHEN st.priority = 'low' AND EXTRACT(EPOCH FROM (COALESCE(st.resolved_at, CURRENT_TIMESTAMP) - st.created_at)) / 3600 <= 48 THEN 1
        END) AS sla_met_tickets
    FROM service_tickets st
    WHERE st.created_at >= CURRENT_DATE - INTERVAL '3' MONTH
    GROUP BY st.ticket_type, st.priority
)
SELECT
    status_label,
    priority,
    total_tickets,
    resolved_tickets,
    open_tickets,
    escalated_tickets,
    ROUND(resolved_tickets * 100.0 / NULLIF(total_tickets, 0), 2) AS resolution_rate_pct,
    ROUND(avg_resolution_hours, 1) AS avg_resolution_hours,
    ROUND(avg_satisfaction, 2) AS avg_satisfaction_score,
    ROUND(sla_met_tickets * 100.0 / NULLIF(total_tickets, 0), 2) AS sla_compliance_pct,
    CASE
        WHEN avg_resolution_hours IS NULL THEN '无数据'
        WHEN priority = 'urgent' AND avg_resolution_hours <= 4 THEN '达标'
        WHEN priority = 'high' AND avg_resolution_hours <= 8 THEN '达标'
        WHEN priority = 'normal' AND avg_resolution_hours <= 24 THEN '达标'
        WHEN priority = 'low' AND avg_resolution_hours <= 48 THEN '达标'
        ELSE '不达标'
    END AS sla_status,
    RANK() OVER (PARTITION BY ticket_type ORDER BY avg_resolution_hours ASC) AS efficiency_rank
FROM ticket_metrics
ORDER BY ticket_type,
    CASE priority
        WHEN 'urgent' THEN 1
        WHEN 'high' THEN 2
        WHEN 'normal' THEN 3
        WHEN 'low' THEN 4
        ELSE 5
    END;


-- ============================================================
-- 六、高级分析查询
-- ============================================================

-- Q21: 销售预测 - 移动平均+趋势
-- 语法: 递归CTE + 窗口函数(移动平均) + 趋势计算
-- 统计原理: 7日移动平均平滑波动
--           趋势 = 近期移动平均 - 远期移动平均
--           预测 = 最新移动平均 + 趋势
WITH daily_sales AS (
    SELECT
        so.order_date,
        SUM(so.total_amount) AS daily_total,
        COUNT(DISTINCT so.id) AS daily_orders,
        COUNT(DISTINCT so.customer_id) AS daily_customers
    FROM sales_orders so
    WHERE so.order_date >= CURRENT_DATE - INTERVAL '60' DAY
      AND so.status NOT IN ('draft', 'cancelled')
    GROUP BY so.order_date
),
moving_averages AS (
    SELECT
        order_date,
        daily_total,
        daily_orders,
        daily_customers,
        ROUND(AVG(daily_total) OVER (ORDER BY order_date ROWS BETWEEN 6 PRECEDING AND CURRENT ROW), 2) AS ma_7d,
        ROUND(AVG(daily_total) OVER (ORDER BY order_date ROWS BETWEEN 13 PRECEDING AND 7 PRECEDING), 2) AS ma_7d_prev,
        ROUND(AVG(daily_total) OVER (ORDER BY order_date ROWS BETWEEN 29 PRECEDING AND CURRENT ROW), 2) AS ma_30d
    FROM daily_sales
)
SELECT
    order_date,
    daily_total,
    daily_orders,
    daily_customers,
    ma_7d,
    ma_7d_prev,
    ma_30d,
    ROUND(ma_7d - ma_7d_prev, 2) AS trend_7d,
    ROUND(daily_total - ma_7d, 2) AS deviation_from_ma,
    ROUND((daily_total - ma_7d) * 100.0 / NULLIF(ma_7d, 0), 2) AS deviation_pct,
    CASE
        WHEN daily_total > ma_7d * 1.2 THEN '异常高'
        WHEN daily_total > ma_7d * 1.1 THEN '偏高'
        WHEN daily_total < ma_7d * 0.8 THEN '异常低'
        WHEN daily_total < ma_7d * 0.9 THEN '偏低'
        ELSE '正常'
    END AS anomaly_flag
FROM moving_averages
ORDER BY order_date DESC;


-- Q22: 客户流失预警
-- 语法: CTE + 相关子查询 + 多维度评分 + LAG
-- 统计原理: 流失风险 = 距上次购买天数(30%) + 购买频次下降(30%) + 金额下降(25%) + 投诉次数(15%)
WITH customer_behavior AS (
    SELECT
        c.id AS customer_id,
        c.name AS customer_name,
        c.membership_level,
        (CURRENT_DATE - MAX(so.order_date)) AS days_since_last_order,
        COUNT(DISTINCT CASE WHEN so.order_date >= CURRENT_DATE - INTERVAL '3' MONTH THEN so.id END) AS orders_3m,
        COUNT(DISTINCT CASE WHEN so.order_date >= CURRENT_DATE - INTERVAL '6' MONTH
            AND so.order_date < CURRENT_DATE - INTERVAL '3' MONTH THEN so.id END) AS orders_3_6m,
        COALESCE(SUM(CASE WHEN so.order_date >= CURRENT_DATE - INTERVAL '3' MONTH THEN so.total_amount END), 0) AS amount_3m,
        COALESCE(SUM(CASE WHEN so.order_date >= CURRENT_DATE - INTERVAL '6' MONTH
            AND so.order_date < CURRENT_DATE - INTERVAL '3' MONTH THEN so.total_amount END), 0) AS amount_3_6m,
        (SELECT COUNT(*) FROM service_tickets st
         WHERE st.customer_id = c.id AND st.ticket_type = 'complaint'
           AND st.created_at >= CURRENT_DATE - INTERVAL '3' MONTH
        ) AS complaint_count_3m
    FROM customers c
    LEFT JOIN sales_orders so ON c.id = so.customer_id
        AND so.status NOT IN ('draft', 'cancelled')
    GROUP BY c.id, c.name, c.membership_level
)
SELECT
    customer_name,
    status_label,
    days_since_last_order,
    orders_3m,
    orders_3_6m,
    amount_3m,
    amount_3_6m,
    complaint_count_3m,
    CASE
        WHEN orders_3_6m > 0 AND orders_3m = 0 THEN '已流失'
        WHEN orders_3_6m > 0 AND orders_3m < orders_3_6m * 0.5 THEN '大幅下降'
        WHEN orders_3_6m > 0 AND orders_3m < orders_3_6m THEN '下降'
        WHEN orders_3m > orders_3_6m THEN '增长'
        ELSE '新客户'
    END AS purchase_trend,
    CASE
        WHEN amount_3_6m > 0 AND amount_3m = 0 THEN '已流失'
        WHEN amount_3_6m > 0 AND amount_3m < amount_3_6m * 0.5 THEN '大幅下降'
        WHEN amount_3_6m > 0 AND amount_3m < amount_3_6m THEN '下降'
        WHEN amount_3m > amount_3_6m THEN '增长'
        ELSE '新客户'
    END AS amount_trend,
    ROUND(
        CASE WHEN days_since_last_order > 90 THEN 30 ELSE days_since_last_order / 90.0 * 30 END
        + CASE WHEN orders_3_6m > 0 AND orders_3m < orders_3_6m * 0.5 THEN 30
               WHEN orders_3_6m > 0 AND orders_3m < orders_3_6m THEN 15 ELSE 0 END
        + CASE WHEN amount_3_6m > 0 AND amount_3m < amount_3_6m * 0.5 THEN 25
               WHEN amount_3_6m > 0 AND amount_3m < amount_3_6m THEN 12 ELSE 0 END
        + complaint_count_3m * 5
    , 0) AS churn_risk_score,
    CASE
        WHEN (CASE WHEN days_since_last_order > 90 THEN 30 ELSE days_since_last_order / 90.0 * 30 END
            + CASE WHEN orders_3_6m > 0 AND orders_3m < orders_3_6m * 0.5 THEN 30
                   WHEN orders_3_6m > 0 AND orders_3m < orders_3_6m THEN 15 ELSE 0 END
            + CASE WHEN amount_3_6m > 0 AND amount_3m < amount_3_6m * 0.5 THEN 25
                   WHEN amount_3_6m > 0 AND amount_3m < amount_3_6m THEN 12 ELSE 0 END
            + complaint_count_3m * 5) >= 50 THEN '高流失风险'
        WHEN (CASE WHEN days_since_last_order > 90 THEN 30 ELSE days_since_last_order / 90.0 * 30 END
            + CASE WHEN orders_3_6m > 0 AND orders_3m < orders_3_6m * 0.5 THEN 30
                   WHEN orders_3_6m > 0 AND orders_3m < orders_3_6m THEN 15 ELSE 0 END
            + CASE WHEN amount_3_6m > 0 AND amount_3m < amount_3_6m * 0.5 THEN 25
                   WHEN amount_3_6m > 0 AND amount_3m < amount_3_6m THEN 12 ELSE 0 END
            + complaint_count_3m * 5) >= 30 THEN '中流失风险'
        ELSE '低流失风险'
    END AS churn_risk_level
FROM customer_behavior
WHERE days_since_last_order IS NOT NULL
ORDER BY churn_risk_score DESC;


-- ============================================================
-- 七、极端复杂组合查询
-- ============================================================

-- Q23: 全维度业务健康度仪表盘
-- 语法: 多个标量子查询组合 + UNION ALL + 跨模块汇总
-- 统计原理: 综合评分 = 销售健康(25%) + 库存健康(25%) + 财务健康(25%) + 人力健康(25%)
SELECT '销售健康度' AS metric_category, '月销售额' AS metric_name,
    ROUND(COALESCE((SELECT SUM(total_amount) FROM sales_orders WHERE TO_CHAR(order_date, 'YYYY-MM') = TO_CHAR(CURRENT_DATE, 'YYYY-MM') AND status NOT IN ('draft','cancelled')), 0), 2) AS current_value,
    ROUND(COALESCE((SELECT SUM(total_amount) FROM sales_orders WHERE TO_CHAR(order_date, 'YYYY-MM') = TO_CHAR(CURRENT_DATE - INTERVAL '1' MONTH, 'YYYY-MM') AND status NOT IN ('draft','cancelled')), 0), 2) AS last_value,
    '元' AS unit, 'higher_better' AS direction
UNION ALL
SELECT '销售健康度', '订单数',
    (SELECT COUNT(*) FROM sales_orders WHERE TO_CHAR(order_date, 'YYYY-MM') = TO_CHAR(CURRENT_DATE, 'YYYY-MM') AND status NOT IN ('draft','cancelled')),
    (SELECT COUNT(*) FROM sales_orders WHERE TO_CHAR(order_date, 'YYYY-MM') = TO_CHAR(CURRENT_DATE - INTERVAL '1' MONTH, 'YYYY-MM') AND status NOT IN ('draft','cancelled')),
    '个', 'higher_better'
UNION ALL
SELECT '销售健康度', '平均客单价',
    ROUND((SELECT AVG(total_amount) FROM sales_orders WHERE TO_CHAR(order_date, 'YYYY-MM') = TO_CHAR(CURRENT_DATE, 'YYYY-MM') AND status NOT IN ('draft','cancelled')), 2),
    ROUND((SELECT AVG(total_amount) FROM sales_orders WHERE TO_CHAR(order_date, 'YYYY-MM') = TO_CHAR(CURRENT_DATE - INTERVAL '1' MONTH, 'YYYY-MM') AND status NOT IN ('draft','cancelled')), 2),
    '元', 'higher_better'
UNION ALL
SELECT '库存健康度', '缺货SKU数',
    (SELECT COUNT(*) FROM (SELECT p.id FROM products p LEFT JOIN inventory i ON p.id = i.product_id WHERE p.status = 'active' GROUP BY p.id, p.min_stock HAVING COALESCE(SUM(i.available_quantity), 0) <= p.min_stock) t),
    NULL, '个', 'lower_better'
UNION ALL
SELECT '库存健康度', '过期库存金额',
    ROUND(COALESCE((SELECT SUM(pb.current_qty * pb.purchase_price) FROM product_batches pb WHERE pb.expiry_date < CURRENT_DATE AND pb.current_qty > 0 AND pb.status = 'active'), 0), 2),
    NULL, '元', 'lower_better'
UNION ALL
SELECT '财务健康度', '应收账款',
    ROUND(COALESCE((SELECT SUM(total_amount - paid_amount) FROM sales_orders WHERE status IN ('confirmed','delivering','delivered')), 0), 2),
    NULL, '元', 'lower_better'
UNION ALL
SELECT '财务健康度', '应付账款',
    ROUND(COALESCE((SELECT SUM(total_amount - paid_amount) FROM purchase_orders WHERE status IN ('ordered','partially_received','received')), 0), 2),
    NULL, '元', 'lower_better'
UNION ALL
SELECT '财务健康度', '现金余额',
    (SELECT SUM(current_balance) FROM accounts WHERE is_cash = 1 OR is_bank = 1),
    NULL, '元', 'higher_better'
UNION ALL
SELECT '人力健康度', '总人数',
    (SELECT COUNT(*) FROM employees WHERE status IN ('active','probation','leave')),
    NULL, '人', 'neutral'
UNION ALL
SELECT '人力健康度', '缺编率',
    ROUND((SELECT SUM(headcount_plan - COALESCE(hc.cnt, 0)) * 100.0 / NULLIF(SUM(headcount_plan), 0)
     FROM departments d
     LEFT JOIN (SELECT department_id, COUNT(*) AS cnt FROM employees WHERE status IN ('active','probation','leave') GROUP BY department_id) hc ON d.id = hc.department_id
    ), 2),
    NULL, '%', 'lower_better'
UNION ALL
SELECT '人力健康度', '本月离职人数',
    (SELECT COUNT(*) FROM employees WHERE status = 'resigned' AND resignation_date >= TO_CHAR(CURRENT_DATE, 'YYYY-MM-01')),
    NULL, '人', 'lower_better';


-- Q24: 递归BOM展开 - 物料需求计算
-- 语法: 递归CTE + 多层BOM展开 + 库存扣减
-- 统计原理: 从成品出发，递归展开所有子件
--           净需求 = 毛需求*(1+损耗率) - 可用库存
--           层级展开: Level 0=成品, Level 1=直接子件, Level 2=子件的子件...
WITH bom_explosion AS (
    -- 基础: 直接子件
    SELECT
        b.parent_product_id,
        b.child_product_id,
        b.quantity AS qty_per_unit,
        b.unit,
        b.scrap_rate,
        1 AS bom_level,
        CAST(b.parent_product_id AS VARCHAR2(200)) AS bom_path,
        CAST(p_parent.sku || ' -> ' || p_child.sku AS VARCHAR2(500)) AS bom_path_names
    FROM boms b
    JOIN products p_parent ON b.parent_product_id = p_parent.id
    JOIN products p_child ON b.child_product_id = p_child.id
    WHERE b.status = 'active'

    UNION ALL

    -- 递归: 子件的子件
    SELECT
        be.parent_product_id,
        b2.child_product_id,
        be.qty_per_unit * b2.quantity AS qty_per_unit,
        b2.unit,
        be.scrap_rate + b2.scrap_rate AS scrap_rate,
        be.bom_level + 1,
        be.bom_path || ' -> ' || b2.child_product_id,
        be.bom_path_names || ' -> ' || p_child.sku
    FROM bom_explosion be
    JOIN boms b2 ON be.child_product_id = b2.parent_product_id AND b2.status = 'active'
    JOIN products p_child ON b2.child_product_id = p_child.id
    WHERE be.bom_level < 5  -- 最多展开5层
)
SELECT
    be.parent_product_id,
    pp.sku AS parent_sku,
    pp.name AS parent_name,
    be.child_product_id,
    pc.sku AS child_sku,
    pc.name AS child_name,
    be.bom_level,
    be.bom_path_names,
    be.qty_per_unit,
    be.unit,
    ROUND(be.scrap_rate * 100, 2) AS total_scrap_rate_pct,
    ROUND(be.qty_per_unit * (1 + be.scrap_rate), 3) AS qty_with_scrap,
    COALESCE(fn_get_product_stock(be.child_product_id, NULL), 0) AS current_stock,
    GREATEST(ROUND(be.qty_per_unit * (1 + be.scrap_rate) * 100 - COALESCE(fn_get_product_stock(be.child_product_id, NULL), 0), 0), 0) AS net_requirement_for_100_units
FROM bom_explosion be
JOIN products pp ON be.parent_product_id = pp.id
JOIN products pc ON be.child_product_id = pc.id
ORDER BY be.parent_product_id, be.bom_level, be.child_product_id;


-- Q25: 交叉销售分析 - 商品关联规则
-- 语法: 自连接 + 条件聚合 + 支持度/置信度/提升度
-- 统计原理: 支持度 = 同时购买A和B的订单数 / 总订单数
--           置信度 = 同时购买A和B的订单数 / 购买A的订单数
--           提升度 = 置信度 / (购买B的订单数/总订单数)
--           提升度>1表示正相关，<1表示负相关
WITH order_products AS (
    SELECT DISTINCT soi.order_id, soi.product_id
    FROM sales_order_items soi
    JOIN sales_orders so ON soi.order_id = so.id
    WHERE so.status NOT IN ('draft', 'cancelled')
),
total_orders AS (
    SELECT COUNT(DISTINCT order_id) AS total FROM order_products
),
product_order_count AS (
    SELECT product_id, COUNT(DISTINCT order_id) AS order_count
    FROM order_products
    GROUP BY product_id
),
product_pairs AS (
    SELECT
        a.product_id AS product_a,
        b.product_id AS product_b,
        COUNT(DISTINCT a.order_id) AS pair_order_count
    FROM order_products a
    JOIN order_products b ON a.order_id = b.order_id AND a.product_id < b.product_id
    GROUP BY a.product_id, b.product_id
    HAVING pair_order_count >= 3
)
SELECT
    pa.sku AS sku_a,
    pa.name AS product_a,
    pb.sku AS sku_b,
    pb.name AS product_b,
    pp.pair_order_count,
    ROUND(pp.pair_order_count * 100.0 / t.total, 2) AS support_pct,
    ROUND(pp.pair_order_count * 100.0 / NULLIF(poc_a.order_count, 0), 2) AS confidence_a_to_b_pct,
    ROUND(pp.pair_order_count * 100.0 / NULLIF(poc_b.order_count, 0), 2) AS confidence_b_to_a_pct,
    ROUND((pp.pair_order_count * 1.0 / NULLIF(poc_a.order_count, 0))
        / NULLIF(poc_b.order_count * 1.0 / t.total, 0), 2) AS lift,
    CASE
        WHEN (pp.pair_order_count * 1.0 / NULLIF(poc_a.order_count, 0))
            / NULLIF(poc_b.order_count * 1.0 / t.total, 0) > 2 THEN '强关联'
        WHEN (pp.pair_order_count * 1.0 / NULLIF(poc_a.order_count, 0))
            / NULLIF(poc_b.order_count * 1.0 / t.total, 0) > 1 THEN '正关联'
        ELSE '弱关联'
    END AS association_strength
FROM product_pairs pp
JOIN products pa ON pp.product_a = pa.id
JOIN products pb ON pp.product_b = pb.id
JOIN product_order_count poc_a ON pp.product_a = poc_a.product_id
JOIN product_order_count poc_b ON pp.product_b = poc_b.product_id
CROSS JOIN total_orders t
ORDER BY lift DESC, support_pct DESC
FETCH FIRST 30 ROWS ONLY;