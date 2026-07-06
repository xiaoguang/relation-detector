-- ============================================================
-- ERP系统超复杂SQL查询集合 - 第二批
-- 覆盖: 递归CTE, 派生表JOIN, 窗口函数全系列,
--       UNION ALL 多维汇总, UNION/INTERSECT/EXCEPT模拟,
--       相关子查询嵌套, 派生表多层嵌套, 条件聚合嵌套,
--       动态分桶, 漏斗分析, 同期群分析, 留存分析
-- ============================================================

USE erp_system;


-- ============================================================
-- Q26: 客户同期群分析 (Cohort Analysis) - 留存率
-- 语法: CTE + LEFT JOIN多层 + 条件聚合 + 留存率矩阵
-- 统计原理: 按首购月份分组(cohort)，追踪后续每月回购率
--           留存率 = N月后仍购买的客户数 / 首购月客户数
-- ============================================================

WITH first_purchase AS (
    -- 每个客户的首购月份
    SELECT
        customer_id,
        DATE_FORMAT(MIN(order_date), '%Y-%m') AS cohort_month
    FROM sales_orders
    WHERE status NOT IN ('draft', 'cancelled')
    GROUP BY customer_id
),
cohort_size AS (
    SELECT
        cohort_month,
        COUNT(DISTINCT customer_id) AS cohort_customers
    FROM first_purchase
    GROUP BY cohort_month
),
monthly_active AS (
    -- 每个客户每月的购买记录
    SELECT DISTINCT
        fp.cohort_month,
        fp.customer_id,
        DATE_FORMAT(so.order_date, '%Y-%m') AS active_month,
        TIMESTAMPDIFF(MONTH,
            STR_TO_DATE(CONCAT(fp.cohort_month, '-01'), '%Y-%m-%d'),
            STR_TO_DATE(CONCAT(DATE_FORMAT(so.order_date, '%Y-%m'), '-01'), '%Y-%m-%d')
        ) AS month_number
    FROM first_purchase fp
    JOIN sales_orders so ON fp.customer_id = so.customer_id
        AND so.status NOT IN ('draft', 'cancelled')
)
SELECT
    cs.cohort_month,
    cs.cohort_customers,
    ROUND(COUNT(DISTINCT CASE WHEN ma.month_number = 0 THEN ma.customer_id END) * 100.0 / cs.cohort_customers, 1) AS month_0,
    ROUND(COUNT(DISTINCT CASE WHEN ma.month_number = 1 THEN ma.customer_id END) * 100.0 / cs.cohort_customers, 1) AS month_1,
    ROUND(COUNT(DISTINCT CASE WHEN ma.month_number = 2 THEN ma.customer_id END) * 100.0 / cs.cohort_customers, 1) AS month_2,
    ROUND(COUNT(DISTINCT CASE WHEN ma.month_number = 3 THEN ma.customer_id END) * 100.0 / cs.cohort_customers, 1) AS month_3,
    ROUND(COUNT(DISTINCT CASE WHEN ma.month_number = 4 THEN ma.customer_id END) * 100.0 / cs.cohort_customers, 1) AS month_4,
    ROUND(COUNT(DISTINCT CASE WHEN ma.month_number = 5 THEN ma.customer_id END) * 100.0 / cs.cohort_customers, 1) AS month_5,
    ROUND(COUNT(DISTINCT CASE WHEN ma.month_number = 6 THEN ma.customer_id END) * 100.0 / cs.cohort_customers, 1) AS month_6,
    -- 平均生命周期价值预估
    ROUND(
        (SELECT COALESCE(SUM(so2.total_amount), 0) FROM sales_orders so2
         WHERE so2.customer_id IN (SELECT customer_id FROM first_purchase WHERE cohort_month = cs.cohort_month)
           AND so2.status NOT IN ('draft', 'cancelled'))
        / NULLIF(cs.cohort_customers, 0)
    , 2) AS avg_ltv
FROM cohort_size cs
LEFT JOIN monthly_active ma ON cs.cohort_month = ma.cohort_month
GROUP BY cs.cohort_month, cs.cohort_customers
ORDER BY cs.cohort_month DESC;


-- ============================================================
-- Q27: 采购价格趋势分析 - 多维度同比环比
-- 语法: 多层CTE + LAG多期 + 窗口函数嵌套 + UNION ALL多维汇总
-- 统计原理: 环比增长率 = (本期-上期)/上期*100%
--           通过UNION ALL实现多维度汇总
-- ============================================================

WITH monthly_purchase AS (
    SELECT
        p.category_id,
        pc.name AS category_name,
        s.id AS supplier_id,
        s.name AS supplier_name,
        DATE_FORMAT(po.order_date, '%Y-%m') AS purchase_month,
        AVG(poi.unit_price) AS avg_unit_price,
        SUM(poi.quantity) AS total_qty,
        SUM(poi.amount) AS total_amount,
        COUNT(DISTINCT po.id) AS order_count
    FROM purchase_order_items poi
    JOIN purchase_orders po ON poi.order_id = po.id
    JOIN products p ON poi.product_id = p.id
    JOIN product_categories pc ON p.category_id = pc.id
    JOIN suppliers s ON po.supplier_id = s.id
    WHERE po.order_date >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH)
    GROUP BY p.category_id, pc.name, s.id, s.name, DATE_FORMAT(po.order_date, '%Y-%m')
),
price_trend AS (
    SELECT
        category_name,
        supplier_name,
        purchase_month,
        avg_unit_price,
        total_qty,
        total_amount,
        order_count,
        LAG(avg_unit_price, 1) OVER (PARTITION BY category_id, supplier_id ORDER BY purchase_month) AS prev_month_price,
        LAG(avg_unit_price, 3) OVER (PARTITION BY category_id, supplier_id ORDER BY purchase_month) AS prev_3m_price,
        LAG(avg_unit_price, 12) OVER (PARTITION BY category_id, supplier_id ORDER BY purchase_month) AS prev_year_price,
        AVG(avg_unit_price) OVER (PARTITION BY category_id, supplier_id ORDER BY purchase_month
            ROWS BETWEEN 2 PRECEDING AND CURRENT ROW) AS ma_3m_price,
        ROW_NUMBER() OVER (PARTITION BY category_id, supplier_id ORDER BY purchase_month DESC) AS month_rank
    FROM monthly_purchase
)
SELECT
    category_name,
    supplier_name,
    purchase_month,
    avg_unit_price,
    total_qty,
    total_amount,
    prev_month_price,
    ma_3m_price,
    ROUND((avg_unit_price - prev_month_price) / NULLIF(prev_month_price, 0) * 100, 2) AS mom_change_pct,
    ROUND((avg_unit_price - prev_3m_price) / NULLIF(prev_3m_price, 0) * 100, 2) AS qoq_change_pct,
    ROUND((avg_unit_price - prev_year_price) / NULLIF(prev_year_price, 0) * 100, 2) AS yoy_change_pct,
    CASE
        WHEN (avg_unit_price - prev_month_price) / NULLIF(prev_month_price, 0) > 0.05 THEN '价格上涨'
        WHEN (avg_unit_price - prev_month_price) / NULLIF(prev_month_price, 0) < -0.05 THEN '价格下跌'
        ELSE '价格稳定'
    END AS price_trend_flag,
    CASE
        WHEN (avg_unit_price - prev_3m_price) / NULLIF(prev_3m_price, 0) > 0.1 THEN '需关注-持续上涨'
        WHEN (avg_unit_price - prev_3m_price) / NULLIF(prev_3m_price, 0) < -0.1 THEN '利好-持续下跌'
        ELSE '正常波动'
    END AS trend_alert
FROM price_trend
WHERE month_rank <= 6
ORDER BY category_name, supplier_name, purchase_month DESC;


-- ============================================================
-- Q28: 销售漏斗转化分析
-- 语法: CTE + 多步LEFT JOIN + 转化率计算 + 累计汇总
-- 统计原理: 漏斗各阶段 = 报价 -> 确认 -> 发货 -> 交付 -> 回款
--           转化率 = 下一阶段数 / 上一阶段数 * 100%
-- ============================================================

WITH funnel_stages AS (
    SELECT
        DATE_FORMAT(so.order_date, '%Y-%m') AS month,
        COUNT(DISTINCT so.id) AS stage_confirmed,
        COUNT(DISTINCT CASE WHEN so.status IN ('delivering', 'delivered', 'returned') THEN so.id END) AS stage_shipped,
        COUNT(DISTINCT CASE WHEN so.status IN ('delivered', 'returned') THEN so.id END) AS stage_delivered,
        COUNT(DISTINCT CASE WHEN so.status IN ('delivered', 'returned')
            AND so.paid_amount >= so.total_amount * 0.9 THEN so.id END) AS stage_paid,
        COUNT(DISTINCT CASE WHEN so.status = 'returned' THEN so.id END) AS stage_returned
    FROM sales_orders so
    WHERE so.order_date >= DATE_SUB(CURDATE(), INTERVAL 6 MONTH)
    GROUP BY DATE_FORMAT(so.order_date, '%Y-%m')
)
SELECT
    month,
    stage_confirmed AS confirm_count,
    stage_shipped AS shipped_count,
    stage_delivered AS delivered_count,
    stage_paid AS paid_count,
    stage_returned AS returned_count,
    ROUND(stage_shipped * 100.0 / NULLIF(stage_confirmed, 0), 1) AS confirm_to_ship_pct,
    ROUND(stage_delivered * 100.0 / NULLIF(stage_shipped, 0), 1) AS ship_to_deliver_pct,
    ROUND(stage_paid * 100.0 / NULLIF(stage_delivered, 0), 1) AS deliver_to_paid_pct,
    ROUND(stage_returned * 100.0 / NULLIF(stage_delivered, 0), 1) AS return_rate_pct,
    -- 整体转化率
    ROUND(stage_paid * 100.0 / NULLIF(stage_confirmed, 0), 1) AS overall_conversion_pct,
    -- 环比变化
    ROUND(stage_confirmed - LAG(stage_confirmed) OVER (ORDER BY month), 0) AS confirm_change,
    ROUND(
        (stage_paid * 100.0 / NULLIF(stage_confirmed, 0))
        - LAG(stage_paid * 100.0 / NULLIF(stage_confirmed, 0)) OVER (ORDER BY month)
    , 1) AS conversion_change_pts
FROM funnel_stages
ORDER BY month DESC;


-- ============================================================
-- Q29: 多维度费用分摊 - 按部门/分类/仓库
-- 语法: CTE + CROSS JOIN + 多维度分摊比例 + 递归分摊
-- 统计原理: 间接费用按比例分摊到各部门
--           分摊基准: 仓储费按库存量, 管理费按人数, 销售费按销售额
-- ============================================================

WITH dept_base AS (
    SELECT
        d.id AS dept_id,
        d.name AS dept_name,
        COUNT(DISTINCT e.id) AS headcount,
        COALESCE(SUM(i.quantity), 0) AS total_inventory,
        COALESCE(SUM(so.total_amount), 0) AS total_sales
    FROM departments d
    LEFT JOIN employees e ON d.id = e.department_id AND e.status IN ('active', 'probation', 'leave')
    LEFT JOIN sales_orders so ON e.id = so.salesperson_id
        AND DATE_FORMAT(so.order_date, '%Y-%m') = DATE_FORMAT(CURDATE(), '%Y-%m')
        AND so.status NOT IN ('draft', 'cancelled')
    LEFT JOIN inventory i ON EXISTS (
        SELECT 1 FROM warehouses w WHERE w.manager_id IN (
            SELECT id FROM employees WHERE department_id = d.id
        ) AND i.warehouse_id = w.id
    )
    GROUP BY d.id, d.name
),
total_metrics AS (
    SELECT
        SUM(headcount) AS total_headcount,
        SUM(total_inventory) AS total_inventory,
        SUM(total_sales) AS total_sales
    FROM dept_base
),
expense_items AS (
    SELECT '仓储管理费' AS expense_name, 50000.00 AS expense_amount, 'inventory' AS allocation_key
    UNION ALL
    SELECT '行政管理费', 80000.00, 'headcount'
    UNION ALL
    SELECT 'IT服务费', 30000.00, 'headcount'
    UNION ALL
    SELECT '销售推广费', 60000.00, 'sales'
    UNION ALL
    SELECT '物流配送费', 45000.00, 'inventory'
)
SELECT
    db.dept_name,
    ei.expense_name,
    ei.expense_amount AS total_expense,
    CASE ei.allocation_key
        WHEN 'headcount' THEN ROUND(ei.expense_amount * db.headcount / NULLIF(tm.total_headcount, 0), 2)
        WHEN 'inventory' THEN ROUND(ei.expense_amount * db.total_inventory / NULLIF(tm.total_inventory, 0), 2)
        WHEN 'sales' THEN ROUND(ei.expense_amount * db.total_sales / NULLIF(tm.total_sales, 0), 2)
        ELSE 0
    END AS allocated_amount,
    CASE ei.allocation_key
        WHEN 'headcount' THEN ROUND(db.headcount * 100.0 / NULLIF(tm.total_headcount, 0), 2)
        WHEN 'inventory' THEN ROUND(db.total_inventory * 100.0 / NULLIF(tm.total_inventory, 0), 2)
        WHEN 'sales' THEN ROUND(db.total_sales * 100.0 / NULLIF(tm.total_sales, 0), 2)
        ELSE 0
    END AS allocation_pct,
    ROUND(
        SUM(CASE ei.allocation_key
            WHEN 'headcount' THEN ROUND(ei.expense_amount * db.headcount / NULLIF(tm.total_headcount, 0), 2)
            WHEN 'inventory' THEN ROUND(ei.expense_amount * db.total_inventory / NULLIF(tm.total_inventory, 0), 2)
            WHEN 'sales' THEN ROUND(ei.expense_amount * db.total_sales / NULLIF(tm.total_sales, 0), 2)
            ELSE 0
        END) OVER (PARTITION BY db.dept_id)
    , 2) AS dept_total_allocated
FROM dept_base db
CROSS JOIN expense_items ei
CROSS JOIN total_metrics tm
ORDER BY db.dept_name, ei.expense_name;


-- ============================================================
-- Q30: 异常交易检测 - 多规则引擎
-- 语法: CTE + 多条独立规则 + UNION ALL + 窗口函数异常检测
-- 统计原理: 规则1=大额交易(>3倍标准差), 规则2=异常频次, 规则3=异常折扣
--           规则4=异常退货率, 规则5=异常回款延迟
-- ============================================================

WITH sales_stats AS (
    SELECT
        AVG(so.total_amount) AS avg_amount,
        STDDEV(so.total_amount) AS stddev_amount,
        AVG(DATEDIFF(COALESCE(so.delivery_date, so.order_date), so.order_date)) AS avg_delivery_days
    FROM sales_orders so
    WHERE so.order_date >= DATE_SUB(CURDATE(), INTERVAL 3 MONTH)
),
rule1_large_transaction AS (
    SELECT so.id AS order_id, so.order_no, so.order_date, so.total_amount,
           '大额交易' AS alert_type,
           CONCAT('金额=', so.total_amount, ' 超出均值+3σ=', ROUND(ss.avg_amount + 3 * ss.stddev_amount, 2)) AS alert_detail
    FROM sales_orders so
    CROSS JOIN sales_stats ss
    WHERE so.total_amount > ss.avg_amount + 3 * ss.stddev_amount
      AND so.order_date >= DATE_SUB(CURDATE(), INTERVAL 3 MONTH)
),
rule2_abnormal_frequency AS (
    SELECT so.id AS order_id, so.order_no, so.order_date, so.total_amount,
           '异常购买频次' AS alert_type,
           CONCAT('同日订单数=', freq.cnt) AS alert_detail
    FROM sales_orders so
    JOIN (
        SELECT customer_id, order_date, COUNT(*) AS cnt
        FROM sales_orders
        WHERE order_date >= DATE_SUB(CURDATE(), INTERVAL 3 MONTH)
        GROUP BY customer_id, order_date
        HAVING COUNT(*) > 3
    ) freq ON so.customer_id = freq.customer_id AND so.order_date = freq.order_date
),
rule3_abnormal_discount AS (
    SELECT so.id AS order_id, so.order_no, so.order_date, so.total_amount,
           '异常折扣' AS alert_type,
           CONCAT('折扣率=', ROUND(so.discount_amount * 100.0 / NULLIF(so.total_amount + so.discount_amount, 0), 2), '%') AS alert_detail
    FROM sales_orders so
    WHERE so.discount_amount > 0
      AND so.discount_amount * 100.0 / NULLIF(so.total_amount + so.discount_amount, 0) > 30
      AND so.order_date >= DATE_SUB(CURDATE(), INTERVAL 3 MONTH)
),
rule4_high_return AS (
    SELECT so.id AS order_id, so.order_no, so.order_date, so.total_amount,
           '高退货率' AS alert_type,
           CONCAT('退货次数=', ret.return_count) AS alert_detail
    FROM sales_orders so
    JOIN (
        SELECT customer_id, COUNT(*) AS return_count
        FROM sales_returns
        WHERE return_date >= DATE_SUB(CURDATE(), INTERVAL 3 MONTH)
        GROUP BY customer_id
        HAVING COUNT(*) > 2
    ) ret ON so.customer_id = ret.customer_id
    WHERE so.order_date >= DATE_SUB(CURDATE(), INTERVAL 3 MONTH)
),
rule5_slow_payment AS (
    SELECT so.id AS order_id, so.order_no, so.order_date, so.total_amount,
           '回款延迟' AS alert_type,
           CONCAT('已超期=', DATEDIFF(CURDATE(), DATE_ADD(so.order_date, INTERVAL c.credit_days DAY)), '天') AS alert_detail
    FROM sales_orders so
    JOIN customers c ON so.customer_id = c.id
    WHERE so.status = 'delivered'
      AND so.paid_amount < so.total_amount
      AND DATE_ADD(so.order_date, INTERVAL c.credit_days DAY) < CURDATE()
)
SELECT * FROM rule1_large_transaction
UNION ALL SELECT * FROM rule2_abnormal_frequency
UNION ALL SELECT * FROM rule3_abnormal_discount
UNION ALL SELECT * FROM rule4_high_return
UNION ALL SELECT * FROM rule5_slow_payment
ORDER BY order_date DESC, alert_type;


-- ============================================================
-- Q31: 动态安全库存计算 - 基于需求波动
-- 语法: CTE + 窗口函数(STDDEV) + 服务水平系数
-- 统计原理: 安全库存 = Z * σ * √LT
--           Z=1.65(95%服务水平), σ=日需求标准差, LT=提前期(天)
--           再订货点 = 日均需求*提前期 + 安全库存
-- ============================================================

WITH daily_demand AS (
    SELECT
        soi.product_id,
        so.order_date,
        SUM(soi.quantity) AS daily_qty
    FROM sales_order_items soi
    JOIN sales_orders so ON soi.order_id = so.id
    WHERE so.order_date >= DATE_SUB(CURDATE(), INTERVAL 90 DAY)
      AND so.status NOT IN ('draft', 'cancelled')
    GROUP BY soi.product_id, so.order_date
),
demand_stats AS (
    SELECT
        product_id,
        AVG(daily_qty) AS avg_daily_demand,
        STDDEV(daily_qty) AS stddev_daily_demand,
        COUNT(*) AS active_days,
        MAX(daily_qty) AS max_daily_demand,
        -- 需求变异系数 CV = σ/μ
        ROUND(STDDEV(daily_qty) / NULLIF(AVG(daily_qty), 0), 2) AS cv
    FROM daily_demand
    GROUP BY product_id
    HAVING COUNT(*) >= 10
)
SELECT
    p.sku,
    p.name AS product_name,
    pc.name AS category_name,
    p.purchase_price,
    ds.avg_daily_demand,
    ds.stddev_daily_demand,
    ds.cv,
    ds.active_days,
    ds.max_daily_demand,
    COALESCE(sp.lead_time_days, 7) AS lead_time_days,
    -- 安全库存(95%服务水平 Z=1.65)
    ROUND(1.65 * ds.stddev_daily_demand * SQRT(COALESCE(sp.lead_time_days, 7)), 0) AS safety_stock,
    -- 再订货点
    ROUND(ds.avg_daily_demand * COALESCE(sp.lead_time_days, 7)
        + 1.65 * ds.stddev_daily_demand * SQRT(COALESCE(sp.lead_time_days, 7)), 0) AS reorder_point,
    -- 最大库存
    ROUND(ds.avg_daily_demand * COALESCE(sp.lead_time_days, 7) * 2
        + 1.65 * ds.stddev_daily_demand * SQRT(COALESCE(sp.lead_time_days, 7)), 0) AS max_stock_suggested,
    p.min_stock AS current_min_stock,
    p.max_stock AS current_max_stock,
    COALESCE(fn_get_product_stock(p.id, NULL), 0) AS current_stock,
    CASE
        WHEN COALESCE(fn_get_product_stock(p.id, NULL), 0) <
            ROUND(1.65 * ds.stddev_daily_demand * SQRT(COALESCE(sp.lead_time_days, 7)), 0)
        THEN '低于安全库存'
        WHEN COALESCE(fn_get_product_stock(p.id, NULL), 0) <
            ROUND(ds.avg_daily_demand * COALESCE(sp.lead_time_days, 7)
                + 1.65 * ds.stddev_daily_demand * SQRT(COALESCE(sp.lead_time_days, 7)), 0)
        THEN '低于再订货点'
        ELSE '库存充足'
    END AS stock_signal
FROM demand_stats ds
JOIN products p ON ds.product_id = p.id
JOIN product_categories pc ON p.category_id = pc.id
LEFT JOIN supplier_products sp ON p.id = sp.product_id AND sp.is_preferred = TRUE
WHERE p.status = 'active'
ORDER BY cv DESC;


-- ============================================================
-- Q32: 财务指标杜邦分析
-- 语法: 多层CTE嵌套 + 标量子查询 + 比率计算链
-- 统计原理: ROE = 净利率 * 资产周转率 * 权益乘数
--           净利率 = 净利润/销售收入
--           资产周转率 = 销售收入/总资产
--           权益乘数 = 总资产/净资产
-- ============================================================

WITH income_statement AS (
    SELECT
        DATE_FORMAT(so.order_date, '%Y') AS fiscal_year,
        COALESCE(SUM(so.total_amount), 0) AS revenue,
        COALESCE(SUM(soi.quantity * p.purchase_price), 0) AS cogs,
        (SELECT COALESCE(SUM(sp.net_pay + sp.social_security_company + sp.housing_fund_company), 0)
         FROM salary_payments sp
         WHERE DATE_FORMAT(sp.salary_month, '%Y') = DATE_FORMAT(so.order_date, '%Y')
        ) AS salary_expense,
        (SELECT COALESCE(SUM(expense_amount), 0) FROM (
            SELECT 50000 AS expense_amount UNION ALL SELECT 80000 UNION ALL
            SELECT 30000 UNION ALL SELECT 60000 UNION ALL SELECT 45000
        ) exp) AS other_expense
    FROM sales_orders so
    LEFT JOIN sales_order_items soi ON so.id = soi.order_id
    LEFT JOIN products p ON soi.product_id = p.id
    WHERE so.status NOT IN ('draft', 'cancelled')
      AND so.order_date >= '2024-01-01'
    GROUP BY DATE_FORMAT(so.order_date, '%Y')
),
balance_sheet AS (
    SELECT
        '2024' AS fiscal_year,
        (SELECT SUM(current_balance) FROM accounts WHERE account_type = 'asset') AS total_assets,
        (SELECT SUM(current_balance) FROM accounts WHERE account_type = 'liability') AS total_liabilities,
        (SELECT SUM(current_balance) FROM accounts WHERE account_type = 'equity') AS total_equity
)
SELECT
    is_.fiscal_year,
    is_.revenue,
    is_.cogs,
    is_.revenue - is_.cogs AS gross_profit,
    ROUND((is_.revenue - is_.cogs) * 100.0 / NULLIF(is_.revenue, 0), 2) AS gross_margin_pct,
    is_.salary_expense,
    is_.other_expense,
    is_.revenue - is_.cogs - is_.salary_expense - is_.other_expense AS net_profit,
    ROUND((is_.revenue - is_.cogs - is_.salary_expense - is_.other_expense) * 100.0 / NULLIF(is_.revenue, 0), 2) AS net_margin_pct,
    bs.total_assets,
    bs.total_liabilities,
    bs.total_equity,
    -- 资产周转率
    ROUND(is_.revenue / NULLIF(bs.total_assets, 0), 2) AS asset_turnover,
    -- 权益乘数
    ROUND(bs.total_assets / NULLIF(bs.total_equity, 0), 2) AS equity_multiplier,
    -- ROE (杜邦分解)
    ROUND(
        (is_.revenue - is_.cogs - is_.salary_expense - is_.other_expense) * 100.0 / NULLIF(is_.revenue, 0) / 100
        * (is_.revenue / NULLIF(bs.total_assets, 0))
        * (bs.total_assets / NULLIF(bs.total_equity, 0))
        * 100
    , 2) AS roe_pct,
    -- ROA
    ROUND((is_.revenue - is_.cogs - is_.salary_expense - is_.other_expense) * 100.0 / NULLIF(bs.total_assets, 0), 2) AS roa_pct
FROM income_statement is_
JOIN balance_sheet bs ON is_.fiscal_year = bs.fiscal_year;


-- ============================================================
-- Q33: 工资总额与人力成本分析 - MySQL 8 UNION ALL多维汇总
-- 语法: CTE + UNION ALL + 条件聚合 + 多层汇总 + 预算对比
-- 统计原理: 按部门+月份交叉汇总，同时计算ROLLUP总合计
-- ============================================================

WITH salary_base AS (
    SELECT
        d.name AS department_name,
        sp.salary_month,
        sp.employee_id,
        sp.base_salary,
        sp.overtime_pay,
        sp.bonus,
        sp.deduction,
        sp.social_security_personal,
        sp.social_security_company,
        sp.housing_fund_personal,
        sp.housing_fund_company,
        sp.income_tax,
        sp.net_pay
    FROM salary_payments sp
    JOIN employees e ON sp.employee_id = e.id
    JOIN departments d ON e.department_id = d.id
    WHERE sp.salary_month >= DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 6 MONTH), '%Y-%m')
),
salary_rollup AS (
    SELECT
        department_name,
        salary_month,
        COUNT(DISTINCT employee_id) AS employee_count,
        SUM(base_salary) AS total_base_salary,
        SUM(overtime_pay) AS total_overtime,
        SUM(bonus) AS total_bonus,
        SUM(deduction) AS total_deduction,
        SUM(social_security_personal) AS total_ss_personal,
        SUM(social_security_company) AS total_ss_company,
        SUM(housing_fund_personal) AS total_hf_personal,
        SUM(housing_fund_company) AS total_hf_company,
        SUM(income_tax) AS total_income_tax,
        SUM(net_pay) AS total_net_pay,
        SUM(net_pay + social_security_company + housing_fund_company) AS total_company_cost,
        0 AS is_dept_total,
        0 AS is_month_total
    FROM salary_base
    GROUP BY department_name, salary_month

    UNION ALL

    SELECT
        department_name,
        '【全部月份】' AS salary_month,
        COUNT(DISTINCT employee_id) AS employee_count,
        SUM(base_salary) AS total_base_salary,
        SUM(overtime_pay) AS total_overtime,
        SUM(bonus) AS total_bonus,
        SUM(deduction) AS total_deduction,
        SUM(social_security_personal) AS total_ss_personal,
        SUM(social_security_company) AS total_ss_company,
        SUM(housing_fund_personal) AS total_hf_personal,
        SUM(housing_fund_company) AS total_hf_company,
        SUM(income_tax) AS total_income_tax,
        SUM(net_pay) AS total_net_pay,
        SUM(net_pay + social_security_company + housing_fund_company) AS total_company_cost,
        0 AS is_dept_total,
        1 AS is_month_total
    FROM salary_base
    GROUP BY department_name

    UNION ALL

    SELECT
        '【全部部门】' AS department_name,
        salary_month,
        COUNT(DISTINCT employee_id) AS employee_count,
        SUM(base_salary) AS total_base_salary,
        SUM(overtime_pay) AS total_overtime,
        SUM(bonus) AS total_bonus,
        SUM(deduction) AS total_deduction,
        SUM(social_security_personal) AS total_ss_personal,
        SUM(social_security_company) AS total_ss_company,
        SUM(housing_fund_personal) AS total_hf_personal,
        SUM(housing_fund_company) AS total_hf_company,
        SUM(income_tax) AS total_income_tax,
        SUM(net_pay) AS total_net_pay,
        SUM(net_pay + social_security_company + housing_fund_company) AS total_company_cost,
        1 AS is_dept_total,
        0 AS is_month_total
    FROM salary_base
    GROUP BY salary_month

    UNION ALL

    SELECT
        '【全部部门】' AS department_name,
        '【全部月份】' AS salary_month,
        COUNT(DISTINCT employee_id) AS employee_count,
        SUM(base_salary) AS total_base_salary,
        SUM(overtime_pay) AS total_overtime,
        SUM(bonus) AS total_bonus,
        SUM(deduction) AS total_deduction,
        SUM(social_security_personal) AS total_ss_personal,
        SUM(social_security_company) AS total_ss_company,
        SUM(housing_fund_personal) AS total_hf_personal,
        SUM(housing_fund_company) AS total_hf_company,
        SUM(income_tax) AS total_income_tax,
        SUM(net_pay) AS total_net_pay,
        SUM(net_pay + social_security_company + housing_fund_company) AS total_company_cost,
        1 AS is_dept_total,
        1 AS is_month_total
    FROM salary_base
)
SELECT
    department_name,
    salary_month,
    employee_count,
    total_base_salary,
    total_overtime,
    total_bonus,
    total_deduction,
    total_ss_personal,
    total_ss_company,
    total_hf_personal,
    total_hf_company,
    total_income_tax,
    total_net_pay,
    total_company_cost,
    ROUND(total_company_cost / NULLIF(employee_count, 0), 2) AS avg_cost_per_head,
    ROUND((total_ss_company + total_hf_company) * 100.0 / NULLIF(total_base_salary, 0), 2) AS company_benefit_rate_pct,
    is_dept_total,
    is_month_total
FROM salary_rollup
ORDER BY is_dept_total, department_name, is_month_total, salary_month DESC;


-- ============================================================
-- Q34: 物流时效与配送质量分析
-- 语法: 多表JOIN + 条件聚合 + 百分位数计算 + 多维度对比
-- 统计原理: 配送时效 = 签收时间 - 发货时间
--           准时率 = 按时送达订单数 / 总订单数
--           承运商对比分析
-- ============================================================

WITH shipping_metrics AS (
    SELECT
        s.carrier,
        s.shipping_method,
        DATE_FORMAT(s.shipped_at, '%Y-%m') AS ship_month,
        COUNT(*) AS total_shipments,
        AVG(TIMESTAMPDIFF(HOUR, s.shipped_at, COALESCE(s.delivered_at, NOW()))) AS avg_delivery_hours,
        AVG(DATEDIFF(COALESCE(s.actual_delivery_date, CURDATE()), s.estimated_delivery_date)) AS avg_delay_days,
        COUNT(CASE WHEN s.actual_delivery_date <= s.estimated_delivery_date THEN 1 END) AS on_time_count,
        COUNT(CASE WHEN s.status = 'lost' THEN 1 END) AS lost_count,
        COUNT(CASE WHEN s.status = 'returned' THEN 1 END) AS return_count,
        COUNT(DISTINCT st.id) AS total_track_events,
        AVG(st_count.track_count) AS avg_tracks_per_shipment
    FROM shipments s
    LEFT JOIN (
        SELECT shipment_id, COUNT(*) AS track_count
        FROM shipping_tracks
        GROUP BY shipment_id
    ) st_count ON s.id = st_count.shipment_id
    LEFT JOIN shipping_tracks st ON s.id = st.shipment_id
    WHERE s.shipped_at >= DATE_SUB(CURDATE(), INTERVAL 6 MONTH)
    GROUP BY s.carrier, s.shipping_method, DATE_FORMAT(s.shipped_at, '%Y-%m')
)
SELECT
    carrier,
    shipping_method,
    ship_month,
    total_shipments,
    ROUND(avg_delivery_hours, 1) AS avg_delivery_hours,
    ROUND(avg_delay_days, 1) AS avg_delay_days,
    on_time_count,
    ROUND(on_time_count * 100.0 / NULLIF(total_shipments, 0), 2) AS on_time_rate_pct,
    lost_count,
    ROUND(lost_count * 100.0 / NULLIF(total_shipments, 0), 2) AS loss_rate_pct,
    return_count,
    ROUND(avg_tracks_per_shipment, 1) AS avg_tracks_per_shipment,
    RANK() OVER (PARTITION BY ship_month ORDER BY on_time_count * 100.0 / NULLIF(total_shipments, 0) DESC) AS carrier_rank_this_month,
    ROUND(AVG(on_time_count * 100.0 / NULLIF(total_shipments, 0)) OVER (PARTITION BY carrier), 2) AS carrier_avg_on_time_rate,
    CASE
        WHEN (on_time_count * 100.0 / NULLIF(total_shipments, 0))
            < AVG(on_time_count * 100.0 / NULLIF(total_shipments, 0)) OVER (PARTITION BY carrier) * 0.9
        THEN '本月表现下降'
        ELSE '正常'
    END AS performance_flag
FROM shipping_metrics
ORDER BY ship_month DESC, on_time_rate_pct DESC;


-- ============================================================
-- Q35: 预算执行偏差分析 - 部门年度预算vs实际
-- 语法: CTE + 多表关联 + 条件聚合 + 预算执行率
-- 统计原理: 预算执行率 = 实际支出/预算
--           偏差 = 实际 - 预算
--           预警: 执行率超过时间进度比例
-- ============================================================

WITH budget_actual AS (
    SELECT
        d.id AS dept_id,
        d.name AS dept_name,
        d.budget AS annual_budget,
        d.budget / 12 AS monthly_budget,
        MONTH(CURDATE()) AS months_passed,
        ROUND(d.budget * MONTH(CURDATE()) / 12.0, 2) AS prorated_budget_ytd,
        -- 实际支出: 采购+工资
        COALESCE((
            SELECT SUM(po.total_amount)
            FROM purchase_orders po
            WHERE po.department_id = d.id
              AND YEAR(po.order_date) = YEAR(CURDATE())
        ), 0) AS actual_purchase_ytd,
        COALESCE((
            SELECT SUM(sp.net_pay + sp.social_security_company + sp.housing_fund_company)
            FROM salary_payments sp
            JOIN employees e ON sp.employee_id = e.id
            WHERE e.department_id = d.id
              AND DATE_FORMAT(sp.salary_month, '%Y') = YEAR(CURDATE())
        ), 0) AS actual_salary_ytd
    FROM departments d
    WHERE d.budget > 0
)
SELECT
    dept_name,
    annual_budget,
    monthly_budget,
    prorated_budget_ytd,
    actual_purchase_ytd,
    actual_salary_ytd,
    actual_purchase_ytd + actual_salary_ytd AS actual_total_ytd,
    ROUND((actual_purchase_ytd + actual_salary_ytd) * 100.0 / NULLIF(annual_budget, 0), 2) AS annual_budget_usage_pct,
    ROUND((actual_purchase_ytd + actual_salary_ytd) * 100.0 / NULLIF(prorated_budget_ytd, 0), 2) AS ytd_budget_usage_pct,
    ROUND(MONTH(CURDATE()) * 100.0 / 12, 2) AS time_elapsed_pct,
    annual_budget - (actual_purchase_ytd + actual_salary_ytd) AS remaining_budget,
    CASE
        WHEN (actual_purchase_ytd + actual_salary_ytd) > prorated_budget_ytd * 1.2 THEN '严重超预算'
        WHEN (actual_purchase_ytd + actual_salary_ytd) > prorated_budget_ytd THEN '略超预算'
        WHEN (actual_purchase_ytd + actual_salary_ytd) < prorated_budget_ytd * 0.5 THEN '预算执行偏低'
        ELSE '正常'
    END AS budget_status,
    RANK() OVER (ORDER BY (actual_purchase_ytd + actual_salary_ytd) * 100.0 / NULLIF(prorated_budget_ytd, 0) DESC) AS overspend_rank
FROM budget_actual
ORDER BY ytd_budget_usage_pct DESC;


-- ============================================================
-- Q36: 复杂考勤与加班分析
-- 语法: CTE + 条件聚合 + 多维度交叉 + 累积计算
-- 统计原理: 出勤率/加班率/迟到率 三维分析
--           加班费预估 = 加班小时 * 时薪 * 1.5
-- ============================================================

WITH monthly_attendance AS (
    SELECT
        e.id,
        e.name,
        e.department_id,
        d.name AS dept_name,
        DATE_FORMAT(a.attendance_date, '%Y-%m') AS att_month,
        COUNT(*) AS total_workdays,
        COUNT(CASE WHEN a.status = 'normal' THEN 1 END) AS normal_days,
        COUNT(CASE WHEN a.status = 'late' THEN 1 END) AS late_days,
        COUNT(CASE WHEN a.status = 'early' THEN 1 END) AS early_days,
        COUNT(CASE WHEN a.status = 'absent' THEN 1 END) AS absent_days,
        COUNT(CASE WHEN a.status = 'overtime' THEN 1 END) AS overtime_days,
        SUM(a.late_minutes) AS total_late_minutes,
        SUM(COALESCE(a.overtime_hours, 0)) AS total_overtime_hours,
        ROUND(COUNT(CASE WHEN a.status IN ('normal', 'late', 'early', 'overtime') THEN 1 END) * 100.0
            / NULLIF(COUNT(*), 0), 2) AS attendance_rate,
        ROUND(COUNT(CASE WHEN a.status = 'late' THEN 1 END) * 100.0
            / NULLIF(COUNT(*), 0), 2) AS late_rate
    FROM attendance a
    JOIN employees e ON a.employee_id = e.id
    JOIN departments d ON e.department_id = d.id
    WHERE a.attendance_date >= DATE_SUB(CURDATE(), INTERVAL 6 MONTH)
    GROUP BY e.id, e.name, e.department_id, d.name, DATE_FORMAT(a.attendance_date, '%Y-%m')
)
SELECT
    name,
    dept_name,
    att_month,
    total_workdays,
    normal_days,
    late_days,
    early_days,
    absent_days,
    overtime_days,
    attendance_rate,
    late_rate,
    total_late_minutes,
    ROUND(total_overtime_hours, 1) AS total_overtime_hours,
    ROUND(e.salary / 21.75 / 8 * total_overtime_hours * 1.5, 2) AS estimated_overtime_pay,
    -- 滚动3月平均出勤率
    ROUND(AVG(attendance_rate) OVER (PARTITION BY id ORDER BY att_month
        ROWS BETWEEN 2 PRECEDING AND CURRENT ROW), 2) AS rolling_3m_attendance_rate,
    -- 与部门平均对比
    ROUND(attendance_rate - AVG(attendance_rate) OVER (PARTITION BY department_id, att_month), 2) AS vs_dept_avg,
    CASE
        WHEN attendance_rate < 85 THEN '出勤异常'
        WHEN late_rate > 15 THEN '迟到严重'
        WHEN total_overtime_hours > 40 THEN '加班过多'
        WHEN attendance_rate < AVG(attendance_rate) OVER (PARTITION BY department_id, att_month) - 5 THEN '低于部门平均'
        ELSE '正常'
    END AS alert_flag
FROM monthly_attendance ma
JOIN employees e ON ma.id = e.id
ORDER BY att_month DESC, attendance_rate ASC;


-- ============================================================
-- Q37: 动态库存预留 - 按优先级分配
-- 语法: CTE + 窗口函数(累计分配) + 条件库存扣减
-- 统计原理: 按订单优先级/时间排序，累计分配可用库存
--           如果累计需求超过可用库存，则标记为"部分满足"
-- ============================================================

WITH pending_orders AS (
    SELECT
        so.id AS order_id,
        so.order_no,
        so.order_date,
        so.customer_id,
        c.name AS customer_name,
        c.membership_level,
        soi.product_id,
        soi.quantity AS required_qty,
        so.status,
        ROW_NUMBER() OVER (PARTITION BY soi.product_id ORDER BY
            CASE c.membership_level
                WHEN 'diamond' THEN 1 WHEN 'platinum' THEN 2
                WHEN 'gold' THEN 3 WHEN 'silver' THEN 4 ELSE 5
            END,
            so.order_date ASC
        ) AS allocation_priority
    FROM sales_order_items soi
    JOIN sales_orders so ON soi.order_id = so.id
    JOIN customers c ON so.customer_id = c.id
    WHERE so.status = 'confirmed'
),
stock_available AS (
    SELECT
        product_id,
        SUM(available_quantity) AS total_available
    FROM inventory
    GROUP BY product_id
),
cumulative_allocation AS (
    SELECT
        po.*,
        COALESCE(sa.total_available, 0) AS total_available,
        SUM(po.required_qty) OVER (PARTITION BY po.product_id ORDER BY po.allocation_priority
            ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS cumulative_required,
        COALESCE(sa.total_available, 0) - SUM(po.required_qty) OVER (PARTITION BY po.product_id ORDER BY po.allocation_priority
            ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS remaining_after_this_order
    FROM pending_orders po
    LEFT JOIN stock_available sa ON po.product_id = sa.product_id
)
SELECT
    order_no,
    order_date,
    customer_name,
    membership_level,
    p.sku,
    p.name AS product_name,
    required_qty,
    total_available,
    cumulative_required,
    allocation_priority,
    CASE
        WHEN cumulative_required <= total_available THEN '完全满足'
        WHEN cumulative_required - required_qty < total_available THEN '部分满足'
        ELSE '无法满足'
    END AS fulfillment_status,
    CASE
        WHEN cumulative_required <= total_available THEN required_qty
        WHEN cumulative_required - required_qty < total_available
        THEN total_available - (cumulative_required - required_qty)
        ELSE 0
    END AS allocatable_qty,
    CASE
        WHEN cumulative_required <= total_available THEN 0
        WHEN cumulative_required - required_qty < total_available
        THEN required_qty - (total_available - (cumulative_required - required_qty))
        ELSE required_qty
    END AS shortage_qty
FROM cumulative_allocation ca
JOIN products p ON ca.product_id = p.id
ORDER BY ca.product_id, ca.allocation_priority;


-- ============================================================
-- Q38: 门店/仓库PK对比 - 效率指标排名
-- 语法: 多CTE + 多维度指标 + 加权排名 + 雷达数据
-- 统计原理: 效率评分 = 吞吐量(30%) + 准确率(25%) + 周转(20%) + 成本(15%) + 安全(10%)
-- ============================================================

WITH wh_throughput AS (
    SELECT
        i.warehouse_id,
        COUNT(DISTINCT it.id) AS total_transactions,
        SUM(CASE WHEN it.transaction_type IN ('purchase_in', 'return_in', 'transfer_in') THEN it.quantity_change ELSE 0 END) AS total_inbound,
        SUM(CASE WHEN it.transaction_type IN ('sales_out', 'return_out', 'transfer_out') THEN ABS(it.quantity_change) ELSE 0 END) AS total_outbound
    FROM inventory_transactions it
    JOIN inventory i ON it.product_id = i.product_id AND it.warehouse_id = i.warehouse_id
    WHERE it.created_at >= DATE_SUB(CURDATE(), INTERVAL 3 MONTH)
    GROUP BY i.warehouse_id
),
wh_accuracy AS (
    SELECT
        i.warehouse_id,
        COUNT(CASE WHEN it.transaction_type = 'stocktake_adjust' THEN 1 END) AS stocktake_adjustments,
        SUM(CASE WHEN it.transaction_type = 'stocktake_adjust' THEN ABS(it.quantity_change) ELSE 0 END) AS total_adjustment_qty,
        AVG(CASE WHEN it.transaction_type = 'stocktake_adjust' THEN ABS(it.quantity_change) ELSE NULL END) AS avg_adjustment
    FROM inventory_transactions it
    JOIN inventory i ON it.product_id = i.product_id AND it.warehouse_id = i.warehouse_id
    WHERE it.created_at >= DATE_SUB(CURDATE(), INTERVAL 3 MONTH)
    GROUP BY i.warehouse_id
),
wh_shipments AS (
    SELECT
        warehouse_id,
        COUNT(*) AS total_shipments,
        AVG(TIMESTAMPDIFF(HOUR, shipped_at, COALESCE(delivered_at, NOW()))) AS avg_delivery_hours,
        COUNT(CASE WHEN actual_delivery_date <= estimated_delivery_date THEN 1 END) AS on_time_shipments
    FROM shipments
    WHERE shipped_at >= DATE_SUB(CURDATE(), INTERVAL 3 MONTH)
    GROUP BY warehouse_id
)
SELECT
    w.name AS warehouse_name,
    w.type AS warehouse_type,
    COALESCE(wt.total_transactions, 0) AS transactions_3m,
    COALESCE(wt.total_inbound, 0) AS inbound_qty,
    COALESCE(wt.total_outbound, 0) AS outbound_qty,
    COALESCE(wa.stocktake_adjustments, 0) AS adjustments,
    ROUND(COALESCE(wa.avg_adjustment, 0), 2) AS avg_adjustment_qty,
    COALESCE(ws.total_shipments, 0) AS shipments,
    ROUND(COALESCE(ws.avg_delivery_hours, 0), 1) AS avg_delivery_hours,
    ROUND(COALESCE(ws.on_time_shipments, 0) * 100.0 / NULLIF(COALESCE(ws.total_shipments, 0), 0), 2) AS on_time_rate,
    ROUND(
        COALESCE(wt.total_outbound, 0) * 100.0 / NULLIF(COALESCE(wt.total_inbound, 0), 0), 2
    ) AS outbound_inbound_ratio,
    RANK() OVER (ORDER BY COALESCE(wt.total_transactions, 0) DESC) AS throughput_rank,
    RANK() OVER (ORDER BY COALESCE(wa.avg_adjustment, 999) ASC) AS accuracy_rank,
    RANK() OVER (ORDER BY COALESCE(ws.on_time_shipments, 0) * 100.0 / NULLIF(COALESCE(ws.total_shipments, 0), 0) DESC) AS delivery_rank,
    RANK() OVER (ORDER BY
        COALESCE(wt.total_transactions, 0) * 0.30
        + (1.0 / NULLIF(COALESCE(wa.avg_adjustment, 0.1), 0)) * 100 * 0.25
        + COALESCE(ws.on_time_shipments, 0) * 100.0 / NULLIF(COALESCE(ws.total_shipments, 0), 0) * 0.25
        + COALESCE(wt.total_outbound, 0) * 100.0 / NULLIF(COALESCE(wt.total_inbound, 0), 0) * 0.20
    DESC) AS composite_rank
FROM warehouses w
LEFT JOIN wh_throughput wt ON w.id = wt.warehouse_id
LEFT JOIN wh_accuracy wa ON w.id = wa.warehouse_id
LEFT JOIN wh_shipments ws ON w.id = ws.warehouse_id
ORDER BY composite_rank ASC;
