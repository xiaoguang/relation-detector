-- relation-detector-fixture-source: ROUTINE:public.fn_employee_full_name
CREATE OR REPLACE FUNCTION fn_employee_full_name(p_employee_id BIGINT)
RETURNS VARCHAR(100)
STABLE
LANGUAGE plpgsql
AS $$
DECLARE
    v_result VARCHAR(100);
BEGIN
    SELECT employee_no || ' - ' || name INTO v_result
    FROM employees WHERE id = p_employee_id;
    RETURN COALESCE(v_result, '未知员工');
END;
$$;


-- ============================================================
-- 个税计算 (累进税率)
-- 调用方: sp_process_salary (工资发放存储过程)
-- 计算原理:
--   应纳税所得额 = 税前收入 - 社保个人 - 公积金个人 - 起征点(5000)
--   税率表: 0-3000(3%), 3000-12000(10%), 12000-25000(20%),
--           25000-35000(25%), 35000-55000(30%), 55000-80000(35%), >80000(45%)
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:public.fn_calculate_income_tax
CREATE OR REPLACE FUNCTION fn_calculate_income_tax(
    p_taxable_income DECIMAL(12,2)
)
RETURNS DECIMAL(12,2)
IMMUTABLE
LANGUAGE plpgsql
AS $$
DECLARE
    v_tax DECIMAL(12,2) DEFAULT 0.00;
BEGIN
    IF p_taxable_income <= 0 THEN
        v_tax := 0.00;
    ELSIF p_taxable_income <= 3000 THEN
        v_tax := ROUND(p_taxable_income * 0.03, 2);
    ELSIF p_taxable_income <= 12000 THEN
        v_tax := ROUND(p_taxable_income * 0.10 - 210, 2);
    ELSIF p_taxable_income <= 25000 THEN
        v_tax := ROUND(p_taxable_income * 0.20 - 1410, 2);
    ELSIF p_taxable_income <= 35000 THEN
        v_tax := ROUND(p_taxable_income * 0.25 - 2660, 2);
    ELSIF p_taxable_income <= 55000 THEN
        v_tax := ROUND(p_taxable_income * 0.30 - 4410, 2);
    ELSIF p_taxable_income <= 80000 THEN
        v_tax := ROUND(p_taxable_income * 0.35 - 7160, 2);
    ELSE
        v_tax := ROUND(p_taxable_income * 0.45 - 15160, 2);
    END IF;

    RETURN v_tax;
END;
$$;


-- ============================================================
-- 获取货品当前总库存
-- 调用方: 库存查询、补货建议、缺货分析
-- 参数: p_product_id - 货品ID, p_warehouse_id - 仓库ID(可选,NULL=所有仓库)
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:public.fn_get_product_stock
CREATE OR REPLACE FUNCTION fn_get_product_stock(
    p_product_id BIGINT,
    p_warehouse_id BIGINT
)
RETURNS INTEGER
STABLE
LANGUAGE plpgsql
AS $$
DECLARE
    v_stock INTEGER;
BEGIN
    SELECT COALESCE(SUM(available_quantity), 0) INTO v_stock
    FROM inventory
    WHERE product_id = p_product_id
      AND (p_warehouse_id IS NULL OR warehouse_id = p_warehouse_id);

    RETURN v_stock;
END;
$$;


-- ============================================================
-- 获取客户可用信用额度
-- 调用方: sp_check_customer_credit, 销售流程
-- 计算原理: 可用额度 = 信用额度 - 账户余额 - 未结清订单金额
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:public.fn_get_customer_credit_available
CREATE OR REPLACE FUNCTION fn_get_customer_credit_available(
    p_customer_id BIGINT
)
RETURNS DECIMAL(18,2)
STABLE
LANGUAGE plpgsql
AS $$
DECLARE
    v_credit_limit DECIMAL(18,2);
    v_balance DECIMAL(18,2);
    v_unpaid DECIMAL(18,2);
BEGIN
    SELECT credit_limit, COALESCE(balance, 0) INTO v_credit_limit, v_balance
    FROM customers WHERE id = p_customer_id;

    SELECT COALESCE(SUM(total_amount - paid_amount), 0) INTO v_unpaid
    FROM sales_orders
    WHERE customer_id = p_customer_id
      AND status IN ('confirmed', 'delivering', 'delivered');

    RETURN v_credit_limit - v_balance - v_unpaid;
END;
$$;


-- ============================================================
-- 计算DSO (Days Sales Outstanding) - 应收账款周转天数
-- 调用方: 财务分析报表
-- 统计原理: DSO = (期末应收账款 / 期间销售额) * 期间天数
--           DSO越低，说明回款速度越快
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:public.fn_get_days_sales_outstanding
CREATE OR REPLACE FUNCTION fn_get_days_sales_outstanding(
    p_start_date DATE,
    p_end_date DATE
)
RETURNS DECIMAL(10,2)
STABLE
LANGUAGE plpgsql
AS $$
DECLARE
    v_receivables DECIMAL(18,2);
    v_total_sales DECIMAL(18,2);
    v_days INTEGER;
    v_dso DECIMAL(10,2);
BEGIN
    v_days := (p_end_date - p_start_date) + 1;

    -- 期末应收账款
    SELECT COALESCE(SUM(so.total_amount - so.paid_amount), 0) INTO v_receivables
    FROM sales_orders so
    WHERE so.status IN ('confirmed', 'delivering', 'delivered');

    -- 期间销售额
    SELECT COALESCE(SUM(total_amount), 0) INTO v_total_sales
    FROM sales_orders
    WHERE order_date BETWEEN p_start_date AND p_end_date
      AND status NOT IN ('draft', 'cancelled');

    IF v_total_sales > 0 THEN
        v_dso := ROUND((v_receivables / v_total_sales) * v_days, 2);
    ELSE
        v_dso := 0;
    END IF;

    RETURN v_dso;
END;
$$;


-- ============================================================
-- 计算库存周转天数
-- 调用方: 库存分析报表
-- 统计原理: 周转天数 = 平均库存 / 日均销售成本
--           周转天数越短，库存管理效率越高
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:public.fn_get_inventory_turnover_days
CREATE OR REPLACE FUNCTION fn_get_inventory_turnover_days(
    p_product_id BIGINT,
    p_start_date DATE,
    p_end_date DATE
)
RETURNS DECIMAL(10,2)
STABLE
LANGUAGE plpgsql
AS $$
DECLARE
    v_avg_inventory DECIMAL(12,2);
    v_total_cost DECIMAL(18,2);
    v_days INTEGER;
    v_turnover_days DECIMAL(10,2);
BEGIN
    v_days := (p_end_date - p_start_date) + 1;

    -- 平均库存: 简化计算，取当前库存
    SELECT COALESCE(AVG(quantity), 0) INTO v_avg_inventory
    FROM inventory
    WHERE product_id = p_product_id;

    -- 期间销售成本
    SELECT COALESCE(SUM(soi.quantity * p.purchase_price), 0) INTO v_total_cost
    FROM sales_order_items soi
    JOIN sales_orders so ON soi.order_id = so.id
    JOIN products p ON soi.product_id = p.id
    WHERE soi.product_id = p_product_id
      AND so.order_date BETWEEN p_start_date AND p_end_date
      AND so.status NOT IN ('draft', 'cancelled');

    IF v_total_cost > 0 AND v_avg_inventory > 0 THEN
        v_turnover_days := ROUND(v_avg_inventory / (v_total_cost / v_days), 2);
    ELSE
        v_turnover_days := NULL;
    END IF;

    RETURN v_turnover_days;
END;
$$;


-- ============================================================
-- 获取员工在职年限
-- 调用方: HR报表、工龄工资计算
-- 计算原理: 计算从入职到当前日期(或离职日期)的年数
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:public.fn_get_employee_tenure
CREATE OR REPLACE FUNCTION fn_get_employee_tenure(
    p_employee_id BIGINT
)
RETURNS DECIMAL(4,1)
STABLE
LANGUAGE plpgsql
AS $$
DECLARE
    v_hire_date DATE;
    v_resignation_date DATE;
    v_end_date DATE;
    v_tenure DECIMAL(4,1);
BEGIN
    SELECT hire_date, resignation_date
    INTO v_hire_date, v_resignation_date
    FROM employees WHERE id = p_employee_id;

    v_end_date := COALESCE(v_resignation_date, CURRENT_DATE);
    v_tenure := ROUND(
        (EXTRACT(YEAR FROM AGE(v_end_date, v_hire_date)) * 12
         + EXTRACT(MONTH FROM AGE(v_end_date, v_hire_date)))::NUMERIC / 12.0,
        1
    );

    RETURN v_tenure;
END;
$$;


-- ============================================================
-- 获取月销售额
-- 调用方: 销售报表、仪表盘
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:public.fn_get_monthly_sales
CREATE OR REPLACE FUNCTION fn_get_monthly_sales(
    p_year_month VARCHAR(7)
)
RETURNS DECIMAL(18,2)
STABLE
LANGUAGE plpgsql
AS $$
DECLARE
    v_start_date DATE;
    v_end_date DATE;
    v_total DECIMAL(18,2);
BEGIN
    v_start_date := (p_year_month || '-01')::DATE;
    v_end_date := (date_trunc('month', v_start_date) + INTERVAL '1 month' - INTERVAL '1 day')::DATE;

    SELECT COALESCE(SUM(total_amount), 0) INTO v_total
    FROM sales_orders
    WHERE order_date BETWEEN v_start_date AND v_end_date
      AND status NOT IN ('draft', 'cancelled');

    RETURN v_total;
END;
$$;


-- ============================================================
-- 计算毛利率
-- 调用方: 财务分析
-- 计算原理: 毛利率 = (销售额 - 成本) / 销售额 * 100%
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:public.fn_get_gross_margin
CREATE OR REPLACE FUNCTION fn_get_gross_margin(
    p_product_id BIGINT,
    p_start_date DATE,
    p_end_date DATE
)
RETURNS DECIMAL(5,2)
STABLE
LANGUAGE plpgsql
AS $$
DECLARE
    v_revenue DECIMAL(18,2);
    v_cost DECIMAL(18,2);
    v_margin DECIMAL(5,2);
BEGIN
    SELECT COALESCE(SUM(soi.amount), 0),
           COALESCE(SUM(soi.quantity * p.purchase_price), 0)
    INTO v_revenue, v_cost
    FROM sales_order_items soi
    JOIN sales_orders so ON soi.order_id = so.id
    JOIN products p ON soi.product_id = p.id
    WHERE soi.product_id = p_product_id
      AND so.order_date BETWEEN p_start_date AND p_end_date
      AND so.status NOT IN ('draft', 'cancelled');

    IF v_revenue > 0 THEN
        v_margin := ROUND((v_revenue - v_cost) / v_revenue * 100, 2);
    ELSE
        v_margin := 0;
    END IF;

    RETURN v_margin;
END;
$$;


-- ============================================================
-- 计算员工出勤率
-- 调用方: HR报表
-- 统计原理: 出勤率 = (应出勤天数 - 缺勤天数) / 应出勤天数 * 100%
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:public.fn_get_attendance_rate
CREATE OR REPLACE FUNCTION fn_get_attendance_rate(
    p_employee_id BIGINT,
    p_year_month VARCHAR(7)
)
RETURNS DECIMAL(5,2)
STABLE
LANGUAGE plpgsql
AS $$
DECLARE
    v_total_workdays INTEGER;
    v_absent_days INTEGER;
    v_rate DECIMAL(5,2);
BEGIN
    -- 计算当月工作日(简化: 总天数 - 8天周末)
    v_total_workdays := EXTRACT(DAY FROM (
        date_trunc('month', (p_year_month || '-01')::DATE) + INTERVAL '1 month' - INTERVAL '1 day'
    ))::INTEGER - 8;

    SELECT COUNT(*) INTO v_absent_days
    FROM attendance
    WHERE employee_id = p_employee_id
      AND TO_CHAR(attendance_date, 'YYYY-MM') = p_year_month
      AND status = 'absent';

    IF v_total_workdays > 0 THEN
        v_rate := ROUND((v_total_workdays - v_absent_days)::NUMERIC / v_total_workdays * 100, 2);
    ELSE
        v_rate := 100.00;
    END IF;

    RETURN v_rate;
END;
$$;
-- relation-detector-fixture-end
