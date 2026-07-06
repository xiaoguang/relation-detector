-- ============================================================
-- 常用系统查询 (Oracle 26ai)
-- 覆盖: 员工出勤率、门店收支审计、员工工资历史、
--        常用业务JOIN查询、审批待办、系统仪表盘等
-- 注意: SELECT 型存储过程在 Oracle 中转换为
--   TABLE-returning 函数，因为 PG 的 PROCEDURE 不支持返回结果集。
-- 调用方式: SELECT * FROM sp_xxx(...) 代替 CALL sp_xxx(...)
-- ============================================================

ALTER SESSION SET CURRENT_SCHEMA = erp_system;

-- ============================================================
-- 76. 员工出勤率不达标报告
-- 业务场景: HR/店长查看哪些员工出勤率低，需要关注
-- 统计原理: 出勤率 = (应出勤-缺勤)/应出勤*100%
--           不达标标准: 出勤率<85% 或 迟到率>15% 或 缺勤>5天/月
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_poor_attendance_report(
p_year_month VARCHAR2,
    p_department_id NUMBER,
    p_result OUT SYS_REFCURSOR
)
AS
v_start_date DATE;
    v_end_date DATE;
    v_workdays NUMBER(10);
BEGIN
    v_start_date := (p_year_month || '-01');
    v_end_date := (TRUNC(v_start_date, 'MM') + INTERVAL '1' MONTH - INTERVAL '1' DAY);
    v_workdays := EXTRACT(DAY FROM v_end_date)(10) - FLOOR(EXTRACT(DAY FROM v_end_date)(10) / 7) * 2;

    OPEN p_result FOR
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
               AND TO_CHAR(a2.attendance_date, 'YYYY-MM') = TO_CHAR(v_start_date - INTERVAL '1' MONTH, 'YYYY-MM')
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
/


-- ============================================================
-- 77. 门店收支审计 - 单店损益表
-- 业务场景: 审计某家门店的所有收入和支出
-- 统计原理: 门店收入(销售) - 门店成本(商品成本+工资+租金估算+物流+报损)
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_store_audit_pl(
p_warehouse_id NUMBER,
    p_start_date DATE,
    p_end_date DATE,
    p_result OUT SYS_REFCURSOR
)
AS
v_store_name VARCHAR2(100);
BEGIN
    SELECT name INTO v_store_name FROM warehouses WHERE id = p_warehouse_id;

    OPEN p_result FOR
    -- 收入部分
    SELECT '【营业收入】' AS section, '销售收入' AS item,
        COALESCE((SELECT SUM(so.total_amount) FROM sales_orders so
         WHERE so.warehouse_id = p_warehouse_id
           AND so.order_date BETWEEN p_start_date AND p_end_date
           AND so.status NOT IN ('draft', 'cancelled')), 0) AS amount,
        COALESCE((SELECT COUNT(*) FROM sales_orders so
         WHERE so.warehouse_id = p_warehouse_id
           AND so.order_date BETWEEN p_start_date AND p_end_date
           AND so.status NOT IN ('draft', 'cancelled')), 0)(19) AS count_or_qty
    UNION ALL
    SELECT '【营业收入】', '退货冲减',
        COALESCE((SELECT SUM(sr.refund_amount) FROM sales_returns sr
         WHERE sr.warehouse_id = p_warehouse_id
           AND sr.return_date BETWEEN p_start_date AND p_end_date
           AND sr.status = 'refunded'), 0),
        COALESCE((SELECT COUNT(*) FROM sales_returns sr
         WHERE sr.warehouse_id = p_warehouse_id
           AND sr.return_date BETWEEN p_start_date AND p_end_date
           AND sr.status = 'refunded'), 0)(19)
    UNION ALL
    SELECT '【营业收入】', '净收入(销售-退货)',
        COALESCE((SELECT SUM(so.total_amount) FROM sales_orders so
         WHERE so.warehouse_id = p_warehouse_id
           AND so.order_date BETWEEN p_start_date AND p_end_date
           AND so.status NOT IN ('draft', 'cancelled')), 0)
        - COALESCE((SELECT SUM(sr.refund_amount) FROM sales_returns sr
         WHERE sr.warehouse_id = p_warehouse_id
           AND sr.return_date BETWEEN p_start_date AND p_end_date
           AND sr.status = 'refunded'), 0), NULL(19)
    -- 成本部分
    UNION ALL
    SELECT '【营业成本】', '商品销售成本(进货价)',
        COALESCE((SELECT SUM(soi.quantity * p.purchase_price)
         FROM sales_order_items soi
         JOIN sales_orders so ON soi.order_id = so.id
         JOIN products p ON soi.product_id = p.id
         WHERE so.warehouse_id = p_warehouse_id
           AND so.order_date BETWEEN p_start_date AND p_end_date
           AND so.status NOT IN ('draft', 'cancelled')), 0), NULL(19)
    UNION ALL
    SELECT '【营业成本】', '退货恢复成本',
        COALESCE((SELECT SUM(sri.return_qty * p.purchase_price)
         FROM sales_return_items sri
         JOIN sales_returns sr ON sri.return_id = sr.id
         JOIN products p ON sri.product_id = p.id
         WHERE sr.warehouse_id = p_warehouse_id
           AND sr.return_date BETWEEN p_start_date AND p_end_date
           AND sri.status = 'restocked'), 0), NULL(19)
    -- 毛利
    UNION ALL
    SELECT '【毛利】', '毛利(净收入-净成本)',
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
           AND sri.status = 'restocked'), 0)), NULL(19)
    -- 费用部分
    UNION ALL
    SELECT '【费用】', '门店员工工资',
        COALESCE((SELECT SUM(sp.net_pay + sp.social_security_company + sp.housing_fund_company)
         FROM salary_payments sp
         JOIN employees e ON sp.employee_id = e.id
         WHERE e.id IN (SELECT manager_id FROM warehouses WHERE id = p_warehouse_id)
            OR e.id IN (SELECT id FROM employees WHERE manager_id = (SELECT manager_id FROM warehouses WHERE id = p_warehouse_id))
           AND sp.salary_month BETWEEN TO_CHAR(p_start_date, 'YYYY-MM') AND TO_CHAR(p_end_date, 'YYYY-MM')), 0),
        (SELECT COUNT(DISTINCT e.id) FROM employees e
         WHERE e.id IN (SELECT manager_id FROM warehouses WHERE id = p_warehouse_id)
            OR e.id IN (SELECT id FROM employees WHERE manager_id = (SELECT manager_id FROM warehouses WHERE id = p_warehouse_id)))(19)
    UNION ALL
    SELECT '【费用】', '报损损失',
        COALESCE((SELECT SUM(total_loss_amount) FROM damage_reports
         WHERE warehouse_id = p_warehouse_id
           AND report_date BETWEEN p_start_date AND p_end_date
           AND status = 'executed'), 0), NULL(19)
    UNION ALL
    SELECT '【费用】', '退货运费',
        COALESCE((SELECT SUM(return_shipping_fee) FROM sales_returns
         WHERE warehouse_id = p_warehouse_id
           AND return_date BETWEEN p_start_date AND p_end_date), 0), NULL(19)
    -- 净利润
    UNION ALL
    SELECT '【净利润】', '门店净利润', NULL, NULL(19);
END;
/


-- ============================================================
-- 78a. 员工工资历史查询 - 工资明细
-- 业务场景: 查看某个员工的所有历史工资记录
-- 输出: 每月工资明细 + 薪资变动记录 + 趋势图数据
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_employee_salary_history(
p_employee_id NUMBER,
    p_result OUT SYS_REFCURSOR
)
AS
BEGIN
    OPEN p_result FOR
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
/


-- ============================================================
-- 78b. 员工工资历史查询 - 薪资变动记录
-- 输出: 每次调薪的详情(生效日期/旧薪资/新薪资/变动幅度/原因/审批人)
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_employee_salary_changes(
p_employee_id NUMBER,
    p_result OUT SYS_REFCURSOR
)
AS
BEGIN
    OPEN p_result FOR
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
/


-- ============================================================
-- 79. 门店实时仪表盘数据
-- 业务场景: 店长打开系统首页看到的数据
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_store_dashboard(
p_warehouse_id NUMBER,
    p_result OUT SYS_REFCURSOR
)
AS
BEGIN
    OPEN p_result FOR
    -- 今日销售
    SELECT '今日销售' AS metric, COALESCE(SUM(total_amount), 0) AS value,
           COUNT(*) AS sub_value
    FROM sales_orders WHERE warehouse_id = p_warehouse_id
      AND order_date = CURRENT_DATE AND status NOT IN ('draft', 'cancelled')
    UNION ALL
    -- 本月销售
    SELECT '本月销售', COALESCE(SUM(total_amount), 0), COUNT(*)
    FROM sales_orders WHERE warehouse_id = p_warehouse_id
      AND TO_CHAR(order_date, 'YYYY-MM') = TO_CHAR(CURRENT_DATE, 'YYYY-MM')
      AND status NOT IN ('draft', 'cancelled')
    UNION ALL
    -- 待处理退货
    SELECT '待处理退货', COALESCE(COUNT(*), 0), COALESCE(SUM(total_amount), 0)
    FROM sales_returns WHERE warehouse_id = p_warehouse_id AND status = 'pending'
    UNION ALL
    -- 缺货商品数
    SELECT '缺货SKU', COUNT(DISTINCT i.product_id), NULL
    FROM inventory i JOIN products p ON i.product_id = p.id
    WHERE i.warehouse_id = p_warehouse_id AND i.available_quantity <= p.min_stock
    UNION ALL
    -- 临期批号数
    SELECT '临期批号(<30天)', COUNT(DISTINCT pb.id), SUM(pb.current_qty)
    FROM product_batches pb JOIN inventory i ON pb.id = i.batch_id
    WHERE i.warehouse_id = p_warehouse_id
      AND pb.status = 'active' AND pb.current_qty > 0
      AND pb.expiry_date <= CURRENT_DATE + INTERVAL '30' DAY
    UNION ALL
    -- 今日在岗员工
    SELECT '今日在岗员工',
        (SELECT COUNT(*) FROM employees e
         JOIN attendance a ON e.id = a.employee_id AND a.attendance_date = CURRENT_DATE
         WHERE a.status != 'absent'
           AND (e.id = (SELECT manager_id FROM warehouses WHERE id = p_warehouse_id)
             OR e.manager_id = (SELECT manager_id FROM warehouses WHERE id = p_warehouse_id))),
        (SELECT COUNT(*) FROM employees e
         WHERE e.id = (SELECT manager_id FROM warehouses WHERE id = p_warehouse_id)
           OR e.manager_id = (SELECT manager_id FROM warehouses WHERE id = p_warehouse_id))
    UNION ALL
    -- 待审批事项
    SELECT '待审批', COUNT(*), NULL
    FROM approval_instances WHERE status = 'in_progress';
END;
/


-- ============================================================
-- 80. 客户最近订单查询
-- 业务场景: 客服/销售快速查看某客户最近订单
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_customer_recent_orders(
p_customer_id NUMBER,
    p_limit NUMBER,
    p_result OUT SYS_REFCURSOR
)
AS
BEGIN
    OPEN p_result FOR
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
        LISTAGG((p.sku || '(' || soi.quantity || '×' || soi.unit_price || ')'), ', ') WITHIN GROUP (ORDER BY (p.sku || '(' || soi.quantity || '×' || soi.unit_price || ')')) AS items_summary
    FROM sales_orders so
    JOIN warehouses w ON so.warehouse_id = w.id
    LEFT JOIN sales_order_items soi ON so.id = soi.order_id
    LEFT JOIN products p ON soi.product_id = p.id
    WHERE so.customer_id = p_customer_id
    GROUP BY so.id, so.order_no, so.order_date, so.status, w.name,
             so.total_amount, so.paid_amount, so.payment_method
    ORDER BY so.order_date DESC
    FETCH FIRST p_limit ROWS ONLY;
END;
/


-- ============================================================
-- 81. 月度门店销售排行榜
-- 业务场景: 高管查看所有门店月度销售排名
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_monthly_store_ranking(
p_year_month VARCHAR2,
    p_result OUT SYS_REFCURSOR
)
AS
v_prev_month VARCHAR2(7);
BEGIN
    v_prev_month := TO_CHAR((p_year_month || '-01') - INTERVAL '1' MONTH, 'YYYY-MM');

    OPEN p_result FOR
    SELECT
        RANK() OVER (ORDER BY COALESCE(sales.total, 0) DESC) AS rank,
        w.name AS store_name,
        w.city AS store_city,
        w.province AS store_province,
        COALESCE(sales.total, 0) AS monthly_sales,
        COALESCE(sales.order_count, 0) AS order_count,
        COALESCE(sales.customer_count, 0) AS customer_count,
        ROUND(COALESCE(sales.total, 0) / NULLIF(COALESCE(sales.order_count, 0), 0), 2) AS avg_order_value,
        COALESCE(return_stats.return_amount, 0) AS return_amount,
        ROUND(COALESCE(return_stats.return_amount, 0) * 100.0 / NULLIF(COALESCE(sales.total, 0), 0), 2) AS return_rate_pct,
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
    ) return_stats ON w.id = return_stats.warehouse_id
    WHERE w.status = 'active'
    ORDER BY monthly_sales DESC;
END;
/