-- ============================================================
-- 第三批函数: 客户消费分析、汇率换算、项目进度、
--   合同到期、质检合格率、信用评分（Oracle 21c 版本）
-- 调用关系:
--   fn_get_customer_clv: 被客户分析报表调用
--   fn_convert_currency: 被外币交易处理调用
--   fn_get_project_completion_pct: 被项目监控调用
--   fn_get_customer_credit_score: 被信用评估调用
-- ============================================================

-- ============================================================
-- 客户生命周期价值 (CLV)
-- 计算原理: CLV = 平均客单价 * 年均购买频次 * 平均客户生命周期(年)
-- 调用方: 客户价值分析、营销决策
-- ============================================================

CREATE OR REPLACE FUNCTION fn_get_customer_clv(
p_customer_id NUMBER
)
RETURN NUMBER
AS
v_avg_order_value NUMBER(12,2);
    v_total_orders NUMBER(10);
    v_first_order_date DATE;
    v_last_order_date DATE;
    v_active_months NUMBER(10);
    v_annual_frequency NUMBER(8,2);
    v_avg_lifetime_years NUMBER(5,2) DEFAULT 3.0;
    v_clv NUMBER(18,2);
BEGIN
    SELECT
        AVG(total_amount),
        COUNT(*),
        MIN(order_date),
        MAX(order_date)
    INTO
        v_avg_order_value,
        v_total_orders,
        v_first_order_date,
        v_last_order_date
    FROM sales_orders
    WHERE customer_id = p_customer_id
      AND status NOT IN ('draft', 'cancelled');

    IF v_total_orders = 0 THEN
        RETURN 0.00;
    END IF;

    v_active_months := TRUNC(MONTHS_BETWEEN(COALESCE(v_last_order_date, v_first_order_date), v_first_order_date)) + 1;
    v_annual_frequency := v_total_orders / NULLIF(v_active_months, 0) * 12.0;

    -- 根据会员等级调整预估生命周期
    SELECT CASE loyalty_level
        WHEN 'diamond' THEN 5.0
        WHEN 'platinum' THEN 4.0
        WHEN 'gold' THEN 3.0
        WHEN 'silver' THEN 2.0
        ELSE 1.5
    END INTO v_avg_lifetime_years
    FROM customers WHERE id = p_customer_id;

    v_clv := ROUND(v_avg_order_value * v_annual_frequency * v_avg_lifetime_years, 2);

    RETURN v_clv;
END;
/


-- ============================================================
-- 汇率换算
-- 调用方: 外币交易、财务报表折算
-- 换算原理: amount * 当日汇率(取最近汇率)
-- ============================================================

CREATE OR REPLACE FUNCTION fn_convert_currency(
p_amount NUMBER,
    p_from_currency VARCHAR2,
    p_to_currency VARCHAR2,
    p_rate_date DATE
)
RETURN NUMBER
AS
v_rate NUMBER(12,6);
BEGIN
    IF p_from_currency = p_to_currency THEN
        RETURN p_amount;
    END IF;

    SELECT rate INTO v_rate
    FROM exchange_rates
    WHERE from_currency = p_from_currency
      AND to_currency = p_to_currency
      AND rate_date <= p_rate_date
    ORDER BY rate_date DESC
    FETCH FIRST 1 ROWS ONLY;

    IF v_rate IS NULL THEN
        SELECT rate INTO v_rate
        FROM exchange_rates
        WHERE from_currency = p_to_currency
          AND to_currency = p_from_currency
          AND rate_date <= p_rate_date
        ORDER BY rate_date DESC
        FETCH FIRST 1 ROWS ONLY;

        IF v_rate IS NOT NULL THEN
            RETURN ROUND(p_amount / v_rate, 2);
        END IF;
    END IF;

    RETURN ROUND(p_amount * COALESCE(v_rate, 1.0), 2);
END;
/


-- ============================================================
-- 项目完成百分比
-- 调用方: 项目监控仪表盘
-- 计算原理: 实际成本/预算 或 里程碑完成率
-- ============================================================

CREATE OR REPLACE FUNCTION fn_get_project_completion_pct(
p_project_id NUMBER
)
RETURN NUMBER
AS
v_budget NUMBER(18,2);
    v_actual_cost NUMBER(18,2);
    v_completed_milestones NUMBER(10);
    v_total_milestones NUMBER(10);
    v_pct NUMBER(5,2);
BEGIN
    SELECT budget INTO v_budget FROM projects WHERE id = p_project_id;

    SELECT COALESCE(SUM(amount), 0) INTO v_actual_cost
    FROM project_costs WHERE project_id = p_project_id;

    SELECT
        COUNT(CASE WHEN status = 'completed' THEN 1 END),
        COUNT(*)
    INTO v_completed_milestones, v_total_milestones
    FROM contract_milestones cm
    JOIN contracts c ON cm.contract_id = c.id
    WHERE c.id IN (SELECT id FROM contracts WHERE subject LIKE '%' || (SELECT name FROM projects WHERE id = p_project_id) || '%');

    -- 综合: 成本占比50% + 里程碑占比50%
    v_pct := ROUND(
        COALESCE(v_actual_cost / NULLIF(v_budget, 0) * 50, 0)
        + COALESCE(v_completed_milestones / NULLIF(v_total_milestones, 0) * 50, 0)
    , 2);

    RETURN LEAST(v_pct, 100.00);
END;
/


-- ============================================================
-- 客户信用评分
-- 调用方: 信用额度审批、销售风险评估
-- 评分原理: 付款及时性(40%) + 购买稳定性(30%) + 退货率(20%) + 合作年限(10%)
-- 满分100分
-- ============================================================

CREATE OR REPLACE FUNCTION fn_get_customer_credit_score(
p_customer_id NUMBER
)
RETURN NUMBER
AS
v_ontime_payment_pct NUMBER(5,2);
    v_total_orders NUMBER(10);
    v_return_rate NUMBER(5,2);
    v_customer_years NUMBER(4,1);
    v_order_regularity NUMBER(5,2);
    v_score NUMBER(10);
BEGIN
    -- 付款及时性: 按时付款的订单占比
    SELECT
        COUNT(CASE WHEN so.paid_amount >= so.total_amount * 0.9
            AND (so.delivery_date - so.order_date) <= c.credit_days THEN 1 END) * 100.0
        / NULLIF(COUNT(*), 0)
    INTO v_ontime_payment_pct
    FROM sales_orders so
    JOIN customers c ON so.customer_id = c.id
    WHERE so.customer_id = p_customer_id
      AND so.status = 'delivered';

    -- 订单总数
    SELECT COUNT(*) INTO v_total_orders
    FROM sales_orders WHERE customer_id = p_customer_id AND status NOT IN ('draft', 'cancelled');

    -- 退货率
    SELECT COUNT(*) * 100.0 / NULLIF(v_total_orders, 0) INTO v_return_rate
    FROM sales_returns WHERE customer_id = p_customer_id;

    -- 合作年限
    SELECT TRUNC(MONTHS_BETWEEN(CURRENT_DATE, MIN(order_date) / 12)) INTO v_customer_years
    FROM sales_orders WHERE customer_id = p_customer_id;

    -- 购买规律性: 各月订单数的标准差越小越好
    SELECT 100 - LEAST(COALESCE(STDDEV(monthly_orders), 0) * 20, 50) INTO v_order_regularity
    FROM (
        SELECT TO_CHAR(order_date, 'YYYY-MM') AS month, COUNT(*) AS monthly_orders
        FROM sales_orders
        WHERE customer_id = p_customer_id AND status NOT IN ('draft', 'cancelled')
        GROUP BY TO_CHAR(order_date, 'YYYY-MM')
    ) t;

    v_score := ROUND(
        COALESCE(v_ontime_payment_pct, 0) * 0.40
        + COALESCE(v_order_regularity, 50) * 0.30
        + (100 - COALESCE(v_return_rate, 0)) * 0.20
        + LEAST(COALESCE(v_customer_years, 0) * 10, 10) * 0.10
    , 0);

    RETURN LEAST(GREATEST(v_score, 0), 100);
END;
/


-- ============================================================
-- 质检合格率
-- 调用方: 供应商评估、质量报告
-- ============================================================

CREATE OR REPLACE FUNCTION fn_get_inspection_pass_rate(
p_product_id NUMBER,
    p_start_date DATE,
    p_end_date DATE
)
RETURN NUMBER
AS
v_total NUMBER(10);
    v_qualified NUMBER(10);
    v_rate NUMBER(5,2);
BEGIN
    SELECT COUNT(*), COUNT(CASE WHEN inspection_result = 'qualified' THEN 1 END)
    INTO v_total, v_qualified
    FROM inspection_reports
    WHERE product_id = p_product_id
      AND inspection_date BETWEEN p_start_date AND p_end_date;

    IF v_total > 0 THEN
        v_rate := ROUND(v_qualified * 100.0 / v_total, 2);
    ELSE
        v_rate := 0;
    END IF;

    RETURN v_rate;
END;
/


-- ============================================================
-- 客户消费状态判定
-- 调用方: 客户分层、营销自动化
-- 状态定义:
--   active(活跃): 近30天有购买
--   stable(稳定): 30-90天有购买
--   sleeping(沉睡): 90-180天有购买
--   at_risk(流失风险): 180-365天有购买
--   churned(已流失): >365天无购买
--   new(新客): 首次购买在30天内
-- ============================================================

CREATE OR REPLACE FUNCTION fn_get_customer_status(
p_customer_id NUMBER
)
RETURN VARCHAR2
AS
v_days_since_last NUMBER(10);
    v_first_order_date DATE;
    v_status VARCHAR2(20);
BEGIN
    SELECT CURRENT_DATE - MAX(order_date),
           MIN(order_date)
    INTO v_days_since_last, v_first_order_date
    FROM sales_orders
    WHERE customer_id = p_customer_id
      AND status NOT IN ('draft', 'cancelled');

    IF v_first_order_date IS NULL THEN
        RETURN 'no_purchase';
    END IF;

    IF CURRENT_DATE - v_first_order_date <= 30 THEN
        RETURN 'new';
    END IF;

    IF v_days_since_last <= 30 THEN
        v_status := 'active';
    ELSIF v_days_since_last <= 90 THEN
        v_status := 'stable';
    ELSIF v_days_since_last <= 180 THEN
        v_status := 'sleeping';
    ELSIF v_days_since_last <= 365 THEN
        v_status := 'at_risk';
    ELSE
        v_status := 'churned';
    END IF;

    RETURN v_status;
END;
/


-- ============================================================
-- 客户复购率
-- 计算原理: 购买>=2次的客户占比
-- ============================================================

CREATE OR REPLACE FUNCTION fn_get_customer_repurchase_rate(
p_start_date DATE,
    p_end_date DATE
)
RETURN NUMBER
AS
v_total NUMBER(10);
    v_repurchased NUMBER(10);
    v_rate NUMBER(5,2);
BEGIN
    SELECT COUNT(DISTINCT customer_id),
           COUNT(DISTINCT CASE WHEN order_count >= 2 THEN customer_id END)
    INTO v_total, v_repurchased
    FROM (
        SELECT customer_id, COUNT(*) AS order_count
        FROM sales_orders
        WHERE order_date BETWEEN p_start_date AND p_end_date
          AND status NOT IN ('draft', 'cancelled')
        GROUP BY customer_id
    ) t;

    IF v_total > 0 THEN
        v_rate := ROUND(v_repurchased * 100.0 / v_total, 2);
    ELSE
        v_rate := 0;
    END IF;

    RETURN v_rate;
END;
/