-- relation-detector-fixture-source: ROUTINE:public.sp_poor_attendance_report
CREATE OR REPLACE FUNCTION sp_poor_attendance_report(
    p_year_month VARCHAR(7),
    p_department_id BIGINT
)
RETURNS TABLE(
    employee_id BIGINT,
    employee_name VARCHAR(100),
    employee_no VARCHAR(50),
    department_name VARCHAR(200),
    position_name VARCHAR(200),
    employee_status VARCHAR(50),
    manager_id BIGINT,
    manager_name VARCHAR(100),
    total_workdays_recorded BIGINT,
    normal_days BIGINT,
    late_days BIGINT,
    early_days BIGINT,
    absent_days BIGINT,
    overtime_days BIGINT,
    total_late_minutes NUMERIC,
    total_overtime_hours NUMERIC,
    attendance_rate_pct NUMERIC,
    late_rate_pct NUMERIC,
    prev_month_rate NUMERIC,
    issue_reason TEXT,
    action_level TEXT
)
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
    v_start_date DATE;
    v_end_date DATE;
    v_workdays INT;
BEGIN
    v_start_date := (p_year_month || '-01')::DATE;
    v_end_date := (date_trunc('month', v_start_date) + INTERVAL '1 month' - INTERVAL '1 day')::DATE;
    v_workdays := EXTRACT(DAY FROM v_end_date)::INT - FLOOR(EXTRACT(DAY FROM v_end_date)::INT / 7) * 2;

    RETURN QUERY
    SELECT * FROM (
        SELECT
            e.id AS employee_id,
            e.name AS employee_name,
            e.employee_no,
            d.name AS department_name,
            p.name AS position_name,
            e.status AS employee_status,
            e.manager_id,
            (SELECT name FROM employees WHERE id = e.manager_id) AS manager_name,
            COUNT(DISTINCT a.attendance_date) AS total_workdays_recorded,
            COUNT(CASE WHEN a.status = 'normal' THEN 1 END) AS normal_days,
            COUNT(CASE WHEN a.status = 'late' THEN 1 END) AS late_days,
            COUNT(CASE WHEN a.status = 'early' THEN 1 END) AS early_days,
            COUNT(CASE WHEN a.status = 'absent' THEN 1 END) AS absent_days,
            COUNT(CASE WHEN a.status = 'overtime' THEN 1 END) AS overtime_days,
            SUM(a.late_minutes) AS total_late_minutes,
            ROUND(SUM(COALESCE(a.overtime_hours, 0)), 1) AS total_overtime_hours,
            ROUND(
                COUNT(CASE WHEN a.status IN ('normal','late','early','overtime') THEN 1 END)
                * 100.0 / NULLIF(COUNT(*), 0), 2
            ) AS attendance_rate_pct,
            ROUND(COUNT(CASE WHEN a.status = 'late' THEN 1 END) * 100.0 / NULLIF(COUNT(*), 0), 2) AS late_rate_pct,
            -- 历史对比(上月)
            (SELECT ROUND(COUNT(CASE WHEN a2.status IN ('normal','late','early','overtime') THEN 1 END) * 100.0 / NULLIF(COUNT(*), 0), 2)
             FROM attendance a2 WHERE a2.employee_id = e.id
               AND TO_CHAR(a2.attendance_date, 'YYYY-MM') = TO_CHAR(v_start_date - INTERVAL '1 MONTH', 'YYYY-MM')
            ) AS prev_month_rate,
            -- 不达标原因
            CASE
                WHEN COUNT(CASE WHEN a.status IN ('normal','late','early','overtime') THEN 1 END) * 100.0 / NULLIF(COUNT(*), 0) < 75 THEN '严重缺勤'
                WHEN COUNT(CASE WHEN a.status IN ('normal','late','early','overtime') THEN 1 END) * 100.0 / NULLIF(COUNT(*), 0) < 85 THEN '出勤率偏低'
                WHEN COUNT(CASE WHEN a.status = 'late' THEN 1 END) * 100.0 / NULLIF(COUNT(*), 0) > 15 THEN '迟到严重'
                WHEN COUNT(CASE WHEN a.status = 'absent' THEN 1 END) > 5 THEN '缺勤过多'
                WHEN SUM(COALESCE(a.overtime_hours, 0)) > 40 THEN '加班过多(过劳风险)'
                ELSE '正常'
            END AS issue_reason,
            CASE
                WHEN COUNT(CASE WHEN a.status IN ('normal','late','early','overtime') THEN 1 END) * 100.0 / NULLIF(COUNT(*), 0) < 75 THEN '严重-需面谈'
                WHEN COUNT(CASE WHEN a.status IN ('normal','late','early','overtime') THEN 1 END) * 100.0 / NULLIF(COUNT(*), 0) < 85 THEN '关注-需提醒'
                WHEN COUNT(CASE WHEN a.status = 'late' THEN 1 END) * 100.0 / NULLIF(COUNT(*), 0) > 15 THEN '关注-需提醒'
                ELSE '正常'
            END AS action_level
        FROM employees e
        JOIN departments d ON e.department_id = d.id
        JOIN positions p ON e.position_id = p.id
        LEFT JOIN attendance a ON e.id = a.employee_id
            AND a.attendance_date BETWEEN v_start_date AND v_end_date
        WHERE e.status IN ('active', 'probation')
          AND (p_department_id IS NULL OR e.department_id = p_department_id)
        GROUP BY e.id, e.name, e.employee_no, d.name, p.name, e.status, e.manager_id
    ) sub
    WHERE sub.attendance_rate_pct < 85
        OR sub.late_rate_pct > 15
        OR sub.absent_days > 5
        OR sub.total_overtime_hours > 40
    ORDER BY sub.attendance_rate_pct ASC;
END;
$$;


-- ============================================================
-- 77. 门店收支审计 - 单店损益表
-- 业务场景: 审计某家门店的所有收入和支出
-- 统计原理: 门店收入(销售) - 门店成本(商品成本+工资+租金估算+物流+报损)
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:public.sp_store_audit_pl
CREATE OR REPLACE FUNCTION sp_store_audit_pl(
    p_warehouse_id BIGINT,
    p_start_date DATE,
    p_end_date DATE
)
RETURNS TABLE(
    section TEXT,
    item TEXT,
    amount NUMERIC,
    count_or_qty BIGINT
)
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
    v_store_name VARCHAR(100);
BEGIN
    SELECT name INTO v_store_name FROM warehouses WHERE id = p_warehouse_id;

    RETURN QUERY
    -- 收入部分
    SELECT '【营业收入】'::TEXT AS section, '销售收入'::TEXT AS item,
        COALESCE((SELECT SUM(so.total_amount) FROM sales_orders so
         WHERE so.warehouse_id = p_warehouse_id
           AND so.order_date BETWEEN p_start_date AND p_end_date
           AND so.status NOT IN ('draft', 'cancelled')), 0) AS amount,
        COALESCE((SELECT COUNT(*) FROM sales_orders so
         WHERE so.warehouse_id = p_warehouse_id
           AND so.order_date BETWEEN p_start_date AND p_end_date
           AND so.status NOT IN ('draft', 'cancelled')), 0)::BIGINT AS count_or_qty
    UNION ALL
    SELECT '【营业收入】'::TEXT, '退货冲减'::TEXT,
        COALESCE((SELECT SUM(sr.refund_amount) FROM sales_returns sr
         WHERE sr.warehouse_id = p_warehouse_id
           AND sr.return_date BETWEEN p_start_date AND p_end_date
           AND sr.status = 'refunded'), 0),
        COALESCE((SELECT COUNT(*) FROM sales_returns sr
         WHERE sr.warehouse_id = p_warehouse_id
           AND sr.return_date BETWEEN p_start_date AND p_end_date
           AND sr.status = 'refunded'), 0)::BIGINT
    UNION ALL
    SELECT '【营业收入】'::TEXT, '净收入(销售-退货)'::TEXT,
        COALESCE((SELECT SUM(so.total_amount) FROM sales_orders so
         WHERE so.warehouse_id = p_warehouse_id
           AND so.order_date BETWEEN p_start_date AND p_end_date
           AND so.status NOT IN ('draft', 'cancelled')), 0)
        - COALESCE((SELECT SUM(sr.refund_amount) FROM sales_returns sr
         WHERE sr.warehouse_id = p_warehouse_id
           AND sr.return_date BETWEEN p_start_date AND p_end_date
           AND sr.status = 'refunded'), 0), NULL::BIGINT
    -- 成本部分
    UNION ALL
    SELECT '【营业成本】'::TEXT, '商品销售成本(进货价)'::TEXT,
        COALESCE((SELECT SUM(soi.quantity * p.purchase_price)
         FROM sales_order_items soi
         JOIN sales_orders so ON soi.order_id = so.id
         JOIN products p ON soi.product_id = p.id
         WHERE so.warehouse_id = p_warehouse_id
           AND so.order_date BETWEEN p_start_date AND p_end_date
           AND so.status NOT IN ('draft', 'cancelled')), 0), NULL::BIGINT
    UNION ALL
    SELECT '【营业成本】'::TEXT, '退货恢复成本'::TEXT,
        COALESCE((SELECT SUM(sri.return_qty * p.purchase_price)
         FROM sales_return_items sri
         JOIN sales_returns sr ON sri.return_id = sr.id
         JOIN products p ON sri.product_id = p.id
         WHERE sr.warehouse_id = p_warehouse_id
           AND sr.return_date BETWEEN p_start_date AND p_end_date
           AND sri.status = 'restocked'), 0), NULL::BIGINT
    -- 毛利
    UNION ALL
    SELECT '【毛利】'::TEXT, '毛利(净收入-净成本)'::TEXT,
        (COALESCE((SELECT SUM(so.total_amount) FROM sales_orders so
         WHERE so.warehouse_id = p_warehouse_id
           AND so.order_date BETWEEN p_start_date AND p_end_date
           AND so.status NOT IN ('draft', 'cancelled')), 0)
        - COALESCE((SELECT SUM(sr.refund_amount) FROM sales_returns sr
         WHERE sr.warehouse_id = p_warehouse_id
           AND sr.return_date BETWEEN p_start_date AND p_end_date
           AND sr.status = 'refunded'), 0))
        - (COALESCE((SELECT SUM(soi.quantity * p.purchase_price)
         FROM sales_order_items soi JOIN sales_orders so ON soi.order_id = so.id
         JOIN products p ON soi.product_id = p.id
         WHERE so.warehouse_id = p_warehouse_id
           AND so.order_date BETWEEN p_start_date AND p_end_date
           AND so.status NOT IN ('draft', 'cancelled')), 0)
        - COALESCE((SELECT SUM(sri.return_qty * p.purchase_price)
         FROM sales_return_items sri JOIN sales_returns sr ON sri.return_id = sr.id
         JOIN products p ON sri.product_id = p.id
         WHERE sr.warehouse_id = p_warehouse_id
           AND sr.return_date BETWEEN p_start_date AND p_end_date
           AND sri.status = 'restocked'), 0)), NULL::BIGINT
    -- 费用部分
    UNION ALL
    SELECT '【费用】'::TEXT, '门店员工工资'::TEXT,
        COALESCE((SELECT SUM(sp.net_pay + sp.social_security_company + sp.housing_fund_company)
         FROM salary_payments sp
         JOIN employees e ON sp.employee_id = e.id
         WHERE e.id IN (SELECT manager_id FROM warehouses WHERE id = p_warehouse_id)
            OR e.id IN (SELECT id FROM employees WHERE manager_id = (SELECT manager_id FROM warehouses WHERE id = p_warehouse_id))
           AND sp.salary_month BETWEEN TO_CHAR(p_start_date, 'YYYY-MM') AND TO_CHAR(p_end_date, 'YYYY-MM')), 0),
        (SELECT COUNT(DISTINCT e.id) FROM employees e
         WHERE e.id IN (SELECT manager_id FROM warehouses WHERE id = p_warehouse_id)
            OR e.id IN (SELECT id FROM employees WHERE manager_id = (SELECT manager_id FROM warehouses WHERE id = p_warehouse_id)))::BIGINT
    UNION ALL
    SELECT '【费用】'::TEXT, '报损损失'::TEXT,
        COALESCE((SELECT SUM(total_loss_amount) FROM damage_reports
         WHERE warehouse_id = p_warehouse_id
           AND report_date BETWEEN p_start_date AND p_end_date
           AND status = 'executed'), 0), NULL::BIGINT
    UNION ALL
    SELECT '【费用】'::TEXT, '退货运费'::TEXT,
        COALESCE((SELECT SUM(return_shipping_fee) FROM sales_returns
         WHERE warehouse_id = p_warehouse_id
           AND return_date BETWEEN p_start_date AND p_end_date), 0), NULL::BIGINT
    -- 净利润
    UNION ALL
    SELECT '【净利润】'::TEXT, '门店净利润'::TEXT, NULL::NUMERIC, NULL::BIGINT;
END;
$$;


-- ============================================================
-- 78a. 员工工资历史查询 - 工资明细
-- 业务场景: 查看某个员工的所有历史工资记录
-- 输出: 每月工资明细 + 薪资变动记录 + 趋势图数据
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:public.sp_employee_salary_history
CREATE OR REPLACE FUNCTION sp_employee_salary_history(
    p_employee_id BIGINT
)
RETURNS TABLE(
    salary_month VARCHAR(7),
    payment_date DATE,
    base_salary NUMERIC,
    overtime_pay NUMERIC,
    bonus NUMERIC,
    deduction NUMERIC,
    social_security_personal NUMERIC,
    housing_fund_personal NUMERIC,
    income_tax NUMERIC,
    net_pay NUMERIC,
    social_security_company NUMERIC,
    housing_fund_company NUMERIC,
    total_company_cost NUMERIC,
    status VARCHAR(50),
    paid_at TIMESTAMP,
    prev_net_pay NUMERIC,
    net_pay_change_pct NUMERIC,
    cumulative_net_pay NUMERIC,
    rolling_annual_estimate NUMERIC
)
LANGUAGE plpgsql
STABLE
AS $$
BEGIN
    RETURN QUERY
    SELECT
        sp.salary_month,
        sp.payment_date,
        sp.base_salary,
        sp.overtime_pay,
        sp.bonus,
        sp.deduction,
        sp.social_security_personal,
        sp.housing_fund_personal,
        sp.income_tax,
        sp.net_pay,
        sp.social_security_company,
        sp.housing_fund_company,
        sp.net_pay + sp.social_security_company + sp.housing_fund_company AS total_company_cost,
        sp.status,
        sp.paid_at,
        -- 环比变化
        LAG(sp.net_pay) OVER (ORDER BY sp.salary_month) AS prev_net_pay,
        ROUND((sp.net_pay - LAG(sp.net_pay) OVER (ORDER BY sp.salary_month))
            / NULLIF(LAG(sp.net_pay) OVER (ORDER BY sp.salary_month), 0) * 100, 2) AS net_pay_change_pct,
        -- 累计收入
        SUM(sp.net_pay) OVER (ORDER BY sp.salary_month ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS cumulative_net_pay,
        -- 年薪预估
        ROUND(AVG(sp.net_pay) OVER (ORDER BY sp.salary_month ROWS BETWEEN 11 PRECEDING AND CURRENT ROW) * 12, 2) AS rolling_annual_estimate
    FROM salary_payments sp
    WHERE sp.employee_id = p_employee_id
    ORDER BY sp.salary_month DESC;
END;
$$;


-- ============================================================
-- 78b. 员工工资历史查询 - 薪资变动记录
-- 输出: 每次调薪的详情(生效日期/旧薪资/新薪资/变动幅度/原因/审批人)
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:public.sp_employee_salary_changes
CREATE OR REPLACE FUNCTION sp_employee_salary_changes(
    p_employee_id BIGINT
)
RETURNS TABLE(
    effective_date DATE,
    old_salary NUMERIC,
    new_salary NUMERIC,
    change_amount NUMERIC,
    change_pct NUMERIC,
    change_reason VARCHAR(500),
    approved_by BIGINT,
    approver_name VARCHAR(100)
)
LANGUAGE plpgsql
STABLE
AS $$
BEGIN
    RETURN QUERY
    SELECT
        esl.effective_date,
        esl.old_salary,
        esl.new_salary,
        esl.new_salary - esl.old_salary AS change_amount,
        ROUND((esl.new_salary - esl.old_salary) / NULLIF(esl.old_salary, 0) * 100, 2) AS change_pct,
        esl.change_reason,
        esl.approved_by,
        (SELECT name FROM employees WHERE id = esl.approved_by) AS approver_name
    FROM employee_salary_log esl
    WHERE esl.employee_id = p_employee_id
    ORDER BY esl.effective_date DESC;
END;
$$;


-- ============================================================
-- 79. 门店实时仪表盘数据
-- 业务场景: 店长打开系统首页看到的数据
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:public.sp_store_dashboard
CREATE OR REPLACE FUNCTION sp_store_dashboard(
    p_warehouse_id BIGINT
)
RETURNS TABLE(
    metric TEXT,
    value NUMERIC,
    sub_value NUMERIC
)
LANGUAGE plpgsql
STABLE
AS $$
BEGIN
    RETURN QUERY
    -- 今日销售
    SELECT '今日销售'::TEXT AS metric, COALESCE(SUM(total_amount), 0) AS value,
           COUNT(*)::NUMERIC AS sub_value
    FROM sales_orders WHERE warehouse_id = p_warehouse_id
      AND order_date = CURRENT_DATE AND status NOT IN ('draft', 'cancelled')
    UNION ALL
    -- 本月销售
    SELECT '本月销售'::TEXT, COALESCE(SUM(total_amount), 0), COUNT(*)::NUMERIC
    FROM sales_orders WHERE warehouse_id = p_warehouse_id
      AND TO_CHAR(order_date, 'YYYY-MM') = TO_CHAR(CURRENT_DATE, 'YYYY-MM')
      AND status NOT IN ('draft', 'cancelled')
    UNION ALL
    -- 待处理退货
    SELECT '待处理退货'::TEXT, COALESCE(COUNT(*), 0)::NUMERIC, COALESCE(SUM(total_amount), 0)
    FROM sales_returns WHERE warehouse_id = p_warehouse_id AND status = 'pending'
    UNION ALL
    -- 缺货商品数
    SELECT '缺货SKU'::TEXT, COUNT(DISTINCT i.product_id)::NUMERIC, NULL::NUMERIC
    FROM inventory i JOIN products p ON i.product_id = p.id
    WHERE i.warehouse_id = p_warehouse_id AND i.available_quantity <= p.min_stock
    UNION ALL
    -- 临期批号数
    SELECT '临期批号(<30天)'::TEXT, COUNT(DISTINCT pb.id)::NUMERIC, SUM(pb.current_qty)
    FROM product_batches pb JOIN inventory i ON pb.id = i.batch_id
    WHERE i.warehouse_id = p_warehouse_id
      AND pb.status = 'active' AND pb.current_qty > 0
      AND pb.expiry_date <= CURRENT_DATE + INTERVAL '30 DAYS'
    UNION ALL
    -- 今日在岗员工
    SELECT '今日在岗员工'::TEXT,
        (SELECT COUNT(*) FROM employees e
         JOIN attendance a ON e.id = a.employee_id AND a.attendance_date = CURRENT_DATE
         WHERE a.status != 'absent'
           AND (e.id = (SELECT manager_id FROM warehouses WHERE id = p_warehouse_id)
             OR e.manager_id = (SELECT manager_id FROM warehouses WHERE id = p_warehouse_id)))::NUMERIC,
        (SELECT COUNT(*) FROM employees e
         WHERE e.id = (SELECT manager_id FROM warehouses WHERE id = p_warehouse_id)
           OR e.manager_id = (SELECT manager_id FROM warehouses WHERE id = p_warehouse_id))::NUMERIC
    UNION ALL
    -- 待审批事项
    SELECT '待审批'::TEXT, COUNT(*)::NUMERIC, NULL::NUMERIC
    FROM approval_instances WHERE status = 'in_progress';
END;
$$;


-- ============================================================
-- 80. 客户最近订单查询
-- 业务场景: 客服/销售快速查看某客户最近订单
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:public.sp_customer_recent_orders
CREATE OR REPLACE FUNCTION sp_customer_recent_orders(
    p_customer_id BIGINT,
    p_limit INT
)
RETURNS TABLE(
    order_no VARCHAR(50),
    order_date DATE,
    status VARCHAR(50),
    store_name VARCHAR(200),
    total_amount NUMERIC,
    paid_amount NUMERIC,
    unpaid NUMERIC,
    payment_method VARCHAR(50),
    item_count BIGINT,
    items_summary TEXT
)
LANGUAGE plpgsql
STABLE
AS $$
BEGIN
    RETURN QUERY
    SELECT
        so.order_no,
        so.order_date,
        so.status,
        w.name AS store_name,
        so.total_amount,
        so.paid_amount,
        so.total_amount - so.paid_amount AS unpaid,
        so.payment_method,
        COUNT(soi.id) AS item_count,
        string_agg(CONCAT(p.sku, '(', soi.quantity, '×', soi.unit_price, ')'), ', ') AS items_summary
    FROM sales_orders so
    JOIN warehouses w ON so.warehouse_id = w.id
    LEFT JOIN sales_order_items soi ON so.id = soi.order_id
    LEFT JOIN products p ON soi.product_id = p.id
    WHERE so.customer_id = p_customer_id
    GROUP BY so.id, so.order_no, so.order_date, so.status, w.name,
             so.total_amount, so.paid_amount, so.payment_method
    ORDER BY so.order_date DESC
    LIMIT p_limit;
END;
$$;


-- ============================================================
-- 81. 月度门店销售排行榜
-- 业务场景: 高管查看所有门店月度销售排名
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:public.sp_monthly_store_ranking
CREATE OR REPLACE FUNCTION sp_monthly_store_ranking(
    p_year_month VARCHAR(7)
)
RETURNS TABLE(
    rank BIGINT,
    store_name VARCHAR(200),
    store_city VARCHAR(100),
    store_province VARCHAR(100),
    monthly_sales NUMERIC,
    order_count BIGINT,
    customer_count BIGINT,
    avg_order_value NUMERIC,
    return_amount NUMERIC,
    return_rate_pct NUMERIC,
    staff_count BIGINT,
    store_status VARCHAR(50),
    rank_change BIGINT
)
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
    v_prev_month VARCHAR(7);
BEGIN
    v_prev_month := TO_CHAR((p_year_month || '-01')::DATE - INTERVAL '1 MONTH', 'YYYY-MM');

    RETURN QUERY
    SELECT
        RANK() OVER (ORDER BY COALESCE(sales.total, 0) DESC) AS rank,
        w.name AS store_name,
        w.city AS store_city,
        w.province AS store_province,
        COALESCE(sales.total, 0) AS monthly_sales,
        COALESCE(sales.order_count, 0) AS order_count,
        COALESCE(sales.customer_count, 0) AS customer_count,
        ROUND(COALESCE(sales.total, 0) / NULLIF(COALESCE(sales.order_count, 0), 0), 2) AS avg_order_value,
        COALESCE(returns.return_amount, 0) AS return_amount,
        ROUND(COALESCE(returns.return_amount, 0) * 100.0 / NULLIF(COALESCE(sales.total, 0), 0), 2) AS return_rate_pct,
        (SELECT COUNT(*) FROM employees e
         WHERE e.id = w.manager_id OR e.manager_id = w.manager_id) AS staff_count,
        w.status AS store_status,
        -- 排名变化(本月排名 - 上月排名)
        RANK() OVER (ORDER BY COALESCE(sales.total, 0) DESC) -
        COALESCE((SELECT RANK() OVER (ORDER BY COALESCE(SUM(so2.total_amount), 0) DESC)
         FROM sales_orders so2
         WHERE so2.warehouse_id = w.id
           AND TO_CHAR(so2.order_date, 'YYYY-MM') = v_prev_month
           AND so2.status NOT IN ('draft', 'cancelled')), 0) AS rank_change
    FROM warehouses w
    LEFT JOIN (
        SELECT warehouse_id, SUM(total_amount) AS total, COUNT(*) AS order_count,
               COUNT(DISTINCT customer_id) AS customer_count
        FROM sales_orders
        WHERE TO_CHAR(order_date, 'YYYY-MM') = p_year_month
          AND status NOT IN ('draft', 'cancelled')
        GROUP BY warehouse_id
    ) sales ON w.id = sales.warehouse_id
    LEFT JOIN (
        SELECT warehouse_id, SUM(refund_amount) AS return_amount
        FROM sales_returns
        WHERE TO_CHAR(return_date, 'YYYY-MM') = p_year_month AND status = 'refunded'
        GROUP BY warehouse_id
    ) returns ON w.id = returns.warehouse_id
    WHERE w.status = 'active'
    ORDER BY monthly_sales DESC;
END;
$$;
-- relation-detector-fixture-end
