-- ============================================================
-- 常用系统查询 - 模拟真实ERP系统日常使用的SQL
-- 覆盖: 多表JOIN、员工/门店/商品/客户/订单/库存/财务
--       日常查询、审批待办、报表导出、数据核对等
-- ============================================================


-- ============================================================
-- Q83: 员工出勤率不达标名单（本月）
-- 语法: 多表JOIN + 条件聚合 + HAVING
-- 业务: HR每月查看哪些员工出勤异常
-- ============================================================

SELECT
    e.employee_no,
    e.name AS employee_name,
    d.name AS department_name,
    w.name AS store_name,
    COUNT(DISTINCT a.attendance_date) AS workdays,
    COUNT(CASE WHEN a.status = 'absent' THEN 1 END) AS absent_days,
    COUNT(CASE WHEN a.status = 'late' THEN 1 END) AS late_days,
    SUM(a.late_minutes) AS late_minutes_total,
    ROUND(COUNT(CASE WHEN a.status IN ('normal','late','early','overtime') THEN 1 END) * 100.0
        / NULLIF(COUNT(*), 0), 2) AS attendance_rate,
    CASE
        WHEN COUNT(CASE WHEN a.status IN ('normal','late','early','overtime') THEN 1 END) * 100.0 / NULLIF(COUNT(*), 0) < 75 THEN '严重-需约谈'
        WHEN COUNT(CASE WHEN a.status IN ('normal','late','early','overtime') THEN 1 END) * 100.0 / NULLIF(COUNT(*), 0) < 85 THEN '注意-需提醒'
        WHEN COUNT(CASE WHEN a.status = 'late' THEN 1 END) * 100.0 / NULLIF(COUNT(*), 0) > 15 THEN '迟到过多'
        ELSE '正常'
    END AS alert_level
FROM employees e
JOIN departments d ON e.department_id = d.id
LEFT JOIN warehouses w ON w.manager_id = e.id OR w.manager_id = e.manager_id
JOIN attendance a ON e.id = a.employee_id
    AND TO_CHAR(a.attendance_date, 'YYYY-MM') = TO_CHAR(CURRENT_DATE, 'YYYY-MM')
WHERE e.status IN ('active', 'probation')
GROUP BY e.id, e.employee_no, e.name, d.name, w.name
HAVING COUNT(CASE WHEN a.status IN ('normal','late','early','overtime') THEN 1 END) * 100.0 / NULLIF(COUNT(*), 0) < 85 OR COUNT(CASE WHEN a.status = 'late' THEN 1 END) > 5
ORDER BY attendance_rate ASC;


-- ============================================================
-- Q84: 门店完整收支明细（审计用）
-- 语法: 多表UNION ALL + 标量子查询
-- 业务: 审计某家店的所有收入和支出
-- ============================================================

SELECT '收入-销售' AS category, so.order_no AS ref_no, so.order_date AS trans_date,
    c.name AS party, so.total_amount AS amount, '借' AS direction
FROM sales_orders so
JOIN customers c ON so.customer_id = c.id
WHERE so.warehouse_id = 1 AND so.order_date >= CURRENT_DATE - INTERVAL '30 days'
  AND so.status NOT IN ('draft', 'cancelled')

UNION ALL

SELECT '收入-退货冲减', sr.return_no, sr.return_date,
    c.name, -sr.refund_amount, '贷'
FROM sales_returns sr
JOIN customers c ON sr.customer_id = c.id
WHERE sr.warehouse_id = 1 AND sr.return_date >= CURRENT_DATE - INTERVAL '30 days'
  AND sr.status = 'refunded'

UNION ALL

SELECT '支出-工资', sp.payment_no, sp.payment_date,
    e.name, sp.net_pay + sp.social_security_company + sp.housing_fund_company, '贷'
FROM salary_payments sp
JOIN employees e ON sp.employee_id = e.id
WHERE sp.salary_month >= TO_CHAR(CURRENT_DATE - INTERVAL '1 month', 'YYYY-MM')
  AND (e.id IN (SELECT manager_id FROM warehouses WHERE id = 1)
    OR e.manager_id = (SELECT manager_id FROM warehouses WHERE id = 1))

UNION ALL

SELECT '支出-报损', dr.report_no, dr.report_date,
    dr.report_type, dr.total_loss_amount, '贷'
FROM damage_reports dr
WHERE dr.warehouse_id = 1 AND dr.report_date >= CURRENT_DATE - INTERVAL '30 days'
  AND dr.status = 'executed'

UNION ALL

SELECT '支出-采购收货', po.order_no, po.order_date,
    s.name, po.total_amount, '贷'
FROM purchase_orders po
JOIN suppliers s ON po.supplier_id = s.id
WHERE po.status = 'received' AND po.order_date >= CURRENT_DATE - INTERVAL '30 days'
  AND po.purchaser_id IN (SELECT id FROM employees WHERE manager_id = (SELECT manager_id FROM warehouses WHERE id = 1))

ORDER BY trans_date DESC;


-- ============================================================
-- Q85: 员工工资历史+趋势（完整视图）
-- 语法: 窗口函数LAG + 累计SUM + 多表JOIN
-- 业务: 员工查看自己工资历史，HR做薪资分析
-- ============================================================

SELECT
    sp.salary_month,
    sp.payment_date,
    e.name AS employee_name,
    d.name AS department_name,
    sp.base_salary,
    sp.overtime_pay,
    sp.bonus,
    sp.deduction,
    sp.social_security_personal AS ss_personal,
    sp.housing_fund_personal AS hf_personal,
    sp.income_tax,
    sp.net_pay AS take_home,
    sp.social_security_company AS ss_company,
    sp.housing_fund_company AS hf_company,
    sp.net_pay + sp.social_security_company + sp.housing_fund_company AS total_cost,
    LAG(sp.net_pay) OVER (PARTITION BY sp.employee_id ORDER BY sp.salary_month) AS prev_net,
    ROUND((sp.net_pay - LAG(sp.net_pay) OVER (PARTITION BY sp.employee_id ORDER BY sp.salary_month))
        / NULLIF(LAG(sp.net_pay) OVER (PARTITION BY sp.employee_id ORDER BY sp.salary_month), 0) * 100, 2) AS change_pct,
    SUM(sp.net_pay) OVER (PARTITION BY sp.employee_id ORDER BY sp.salary_month
        ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS cumulative_net,
    -- 12个月滚动平均
    ROUND(AVG(sp.net_pay) OVER (PARTITION BY sp.employee_id ORDER BY sp.salary_month
        ROWS BETWEEN 11 PRECEDING AND CURRENT ROW), 2) AS rolling_12m_avg
FROM salary_payments sp
JOIN employees e ON sp.employee_id = e.id
JOIN departments d ON e.department_id = d.id
WHERE sp.employee_id = 1
ORDER BY sp.salary_month DESC;


-- ============================================================
-- Q86: 待审批事项列表（按用户角色）
-- 语法: 多表JOIN + 条件过滤
-- 业务: 用户登录后看到的待审批列表
-- ============================================================

SELECT
    ai.instance_no AS approval_no,
    aw.workflow_name,
    ai.target_summary,
    ai.submitted_at,
    EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - ai.submitted_at)) / 3600 AS pending_hours,
    ai.current_node_level,
    ai.total_nodes,
    CONCAT(ai.current_node_level, '/', ai.total_nodes) AS progress,
    an.node_name AS current_node,
    sub.name AS submitted_by_name,
    CASE
        WHEN EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - ai.submitted_at)) / 3600 > 48 THEN '超时'
        WHEN EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - ai.submitted_at)) / 3600 > 24 THEN '即将超时'
        ELSE '正常'
    END AS urgency
FROM approval_instances ai
JOIN approval_workflows aw ON ai.workflow_id = aw.id
JOIN approval_nodes an ON ai.workflow_id = an.workflow_id AND ai.current_node_level = an.node_level
JOIN employees sub ON ai.submitted_by = sub.id
WHERE ai.status = 'in_progress'
ORDER BY ai.submitted_at ASC;


-- ============================================================
-- Q87: 商品详情页查询（全门店库存+批号+供应商）
-- 语法: 多表LEFT JOIN + 条件聚合 + string_agg
-- 业务: 点击商品进入详情页看到的数据
-- ============================================================

SELECT
    p.sku,
    p.name AS product_name,
    pc.name AS category_name,
    p.spec,
    p.brand,
    p.barcode,
    p.purchase_price,
    p.wholesale_price,
    p.retail_price,
    ROUND((p.retail_price - p.purchase_price) / NULLIF(p.retail_price, 0) * 100, 2) AS margin_pct,
    p.min_stock,
    p.max_stock,
    p.shelf_life_days,
    p.weight_kg,
    -- 各门店库存
    (SELECT string_agg(CONCAT(w.name, ':', COALESCE(i.quantity, 0)), ' | ')
     FROM warehouses w
     LEFT JOIN inventory i ON w.id = i.warehouse_id AND i.product_id = p.id
     WHERE w.status = 'active') AS stock_by_store,
    -- 总库存
    COALESCE((SELECT SUM(quantity) FROM inventory WHERE product_id = p.id), 0) AS total_stock,
    -- 批号列表(含过期日期)
    (SELECT string_agg(CONCAT(pb.batch_no, '(', pb.current_qty, ',到期:', pb.expiry_date, ')'), '; ' ORDER BY pb.expiry_date)
     FROM product_batches pb WHERE pb.product_id = p.id AND pb.current_qty > 0 AND pb.status = 'active'
    ) AS batch_list,
    -- 供应商列表(含价格)
    (SELECT string_agg(CONCAT(s.name, '(', s.city, '):¥', sp.supplier_price, ' 交期:', sp.lead_time_days, '天'), ' | ' ORDER BY sp.supplier_price)
     FROM supplier_products sp JOIN suppliers s ON sp.supplier_id = s.id
     WHERE sp.product_id = p.id AND s.cooperation_status = 'active'
    ) AS supplier_list,
    -- 近30天销量
    (SELECT COALESCE(SUM(soi.quantity), 0)
     FROM sales_order_items soi JOIN sales_orders so ON soi.order_id = so.id
     WHERE soi.product_id = p.id AND so.order_date >= CURRENT_DATE - INTERVAL '30 days'
       AND so.status NOT IN ('draft', 'cancelled')
    ) AS sales_30d,
    p.status
FROM products p
JOIN product_categories pc ON p.category_id = pc.id
WHERE p.id = 1;


-- ============================================================
-- Q88: 门店库存总览（一张表看全门店所有商品库存）
-- 语法: 条件聚合PIVOT + 多表JOIN
-- 业务: 店长/仓管查看本店所有商品库存状态
-- ============================================================

SELECT
    p.sku,
    p.name AS product_name,
    pc.name AS category_name,
    p.retail_price,
    p.min_stock,
    COALESCE(i.quantity, 0) AS current_stock,
    COALESCE(i.locked_quantity, 0) AS locked,
    COALESCE(i.available_quantity, 0) AS available,
    i.shelf_location,
    pb.batch_no,
    pb.expiry_date,
    (pb.expiry_date - CURRENT_DATE) AS days_to_expiry,
    CASE
        WHEN COALESCE(i.available_quantity, 0) <= 0 THEN '缺货'
        WHEN COALESCE(i.available_quantity, 0) <= p.min_stock THEN '低库存'
        WHEN pb.expiry_date <= CURRENT_DATE + INTERVAL '30 days' THEN '临期'
        ELSE '正常'
    END AS stock_status,
    -- 近30天销量
    COALESCE((SELECT SUM(soi.quantity) FROM sales_order_items soi
     JOIN sales_orders so ON soi.order_id = so.id
     WHERE soi.product_id = p.id AND so.warehouse_id = i.warehouse_id
       AND so.order_date >= CURRENT_DATE - INTERVAL '30 days'
       AND so.status NOT IN ('draft', 'cancelled')), 0) AS sales_30d
FROM products p
JOIN product_categories pc ON p.category_id = pc.id
LEFT JOIN inventory i ON p.id = i.product_id AND i.warehouse_id = 1
LEFT JOIN product_batches pb ON i.batch_id = pb.id AND pb.status = 'active'
WHERE p.status = 'active'
ORDER BY stock_status ASC, days_to_expiry ASC;


-- ============================================================
-- Q89: 客户对账单（含每笔订单+付款+退货）
-- 语法: 多表UNION ALL + 累计余额
-- 业务: 财务/销售给客户发对账单
-- ============================================================

SELECT
    so.order_date AS trans_date,
    so.order_no AS trans_no,
    '销售' AS trans_type,
    so.total_amount AS debit,
    0 AS credit,
    so.total_amount - so.paid_amount AS unpaid,
    so.paid_amount,
    so.status
FROM sales_orders so
WHERE so.customer_id = 1 AND so.status NOT IN ('draft', 'cancelled')

UNION ALL

SELECT sr.return_date, sr.return_no, '退货',
    0, sr.refund_amount, 0, sr.refund_amount, sr.status
FROM sales_returns sr
WHERE sr.customer_id = 1 AND sr.status = 'refunded'

ORDER BY trans_date DESC;


-- ============================================================
-- Q90: 供应商对账单（含每笔采购+付款+退货）
-- 语法: 多表UNION ALL
-- 业务: 财务给供应商的对账单
-- ============================================================

SELECT
    po.order_date AS trans_date,
    po.order_no AS trans_no,
    '采购' AS trans_type,
    0 AS debit,
    po.total_amount AS credit,
    po.total_amount - po.paid_amount AS unpaid,
    po.paid_amount,
    po.status
FROM purchase_orders po
WHERE po.supplier_id = 1

UNION ALL

SELECT pr.return_date, pr.return_no, '退货给供应商',
    pr.total_amount, 0, 0, pr.refund_received, pr.status
FROM purchase_returns pr
WHERE pr.supplier_id = 1 AND pr.status IN ('returned', 'refunded')

ORDER BY trans_date DESC;


-- ============================================================
-- Q91: 近7天销售趋势+同比上周
-- 语法: 日期序列 + LEFT JOIN + 条件聚合
-- 业务: 运营看板上的销售趋势图
-- ============================================================

SELECT
    dates.d AS order_date,
    TO_CHAR(dates.d, 'Day') AS day_name,
    COALESCE(sales.total, 0) AS daily_sales,
    COALESCE(sales.orders, 0) AS daily_orders,
    COALESCE(sales.customers, 0) AS daily_customers,
    COALESCE(last_week.total, 0) AS last_week_sales,
    ROUND((COALESCE(sales.total, 0) - COALESCE(last_week.total, 0))
        / NULLIF(COALESCE(last_week.total, 0), 0) * 100, 2) AS vs_last_week_pct
FROM (
    SELECT CURRENT_DATE - seq.num * INTERVAL '1 day' AS d
    FROM generate_series(0, 6) AS seq(num)
) dates
LEFT JOIN (
    SELECT order_date, SUM(total_amount) AS total, COUNT(*) AS orders,
           COUNT(DISTINCT customer_id) AS customers
    FROM sales_orders
    WHERE order_date >= CURRENT_DATE - INTERVAL '7 days'
      AND status NOT IN ('draft', 'cancelled')
    GROUP BY order_date
) sales ON dates.d = sales.order_date
LEFT JOIN (
    SELECT order_date, SUM(total_amount) AS total
    FROM sales_orders
    WHERE order_date BETWEEN CURRENT_DATE - INTERVAL '14 days' AND CURRENT_DATE - INTERVAL '8 days'
      AND status NOT IN ('draft', 'cancelled')
    GROUP BY order_date
) last_week ON dates.d = last_week.order_date + INTERVAL '7 days'
ORDER BY dates.d ASC;


-- ============================================================
-- Q92: 员工绩效+考勤+工资联动查询
-- 语法: 多表JOIN + 条件聚合
-- 业务: HR综合评估员工(绩效+出勤+工资)
-- ============================================================

SELECT
    e.employee_no,
    e.name AS employee_name,
    d.name AS department_name,
    e.salary AS current_salary,
    e.hire_date,
    EXTRACT(YEAR FROM AGE(CURRENT_DATE, e.hire_date)) AS tenure_years,
    -- 最近绩效
    (SELECT total_score FROM performance_reviews
     WHERE employee_id = e.id ORDER BY review_period DESC LIMIT 1) AS latest_performance,
    (SELECT grade FROM performance_reviews
     WHERE employee_id = e.id ORDER BY review_period DESC LIMIT 1) AS latest_grade,
    -- 本月出勤率
    fn_get_attendance_rate(e.id, TO_CHAR(CURRENT_DATE, 'YYYY-MM')) AS this_month_attendance,
    -- 近3月平均工资
    (SELECT ROUND(AVG(net_pay), 2) FROM salary_payments
     WHERE employee_id = e.id ORDER BY salary_month DESC LIMIT 3) AS avg_salary_3m,
    -- 最近一次调薪
    (SELECT CONCAT(TO_CHAR(effective_date, 'YYYY-MM-DD'), ': ', old_salary, '->', new_salary)
     FROM employee_salary_log WHERE employee_id = e.id ORDER BY effective_date DESC LIMIT 1
    ) AS last_salary_change,
    -- 待审批请假
    (SELECT COUNT(*) FROM leave_records WHERE employee_id = e.id AND status = 'pending') AS pending_leaves
FROM employees e
JOIN departments d ON e.department_id = d.id
WHERE e.status IN ('active', 'probation')
ORDER BY latest_performance DESC, this_month_attendance DESC;


-- ============================================================
-- Q93: 采购订单全链路追踪（请购→采购→收货→质检→付款）
-- 语法: 多表LEFT JOIN链
-- 业务: 采购部追踪采购单的完整状态
-- ============================================================

SELECT
    po.order_no AS purchase_order_no,
    po.order_date,
    s.name AS supplier_name,
    po.status AS po_status,
    po.total_amount,
    po.paid_amount,
    po.total_amount - po.paid_amount AS unpaid,
    pr.receipt_no,
    pr.receipt_date,
    pr.status AS receipt_status,
    pr.inspection_result,
    inv.invoice_no,
    inv.status AS invoice_status,
    twm.match_status AS three_way_match,
    -- 质检结果
    (SELECT string_agg(CONCAT(ir.inspection_result, '(', ROUND(ir.defect_rate, 1), '%)'), '; ')
     FROM inspection_reports ir WHERE ir.reference_id = po.id AND ir.reference_type = 'purchase_order'
    ) AS qc_results,
    -- 退货
    (SELECT string_agg(CONCAT(prt.return_no, ':', prt.status), '; ')
     FROM purchase_returns prt WHERE prt.purchase_order_id = po.id
    ) AS purchase_returns
FROM purchase_orders po
JOIN suppliers s ON po.supplier_id = s.id
LEFT JOIN purchase_receipts pr ON po.id = pr.order_id
LEFT JOIN three_way_matching twm ON po.id = twm.purchase_order_id
LEFT JOIN invoices inv ON twm.invoice_id = inv.id
WHERE po.order_date >= CURRENT_DATE - INTERVAL '90 days'
ORDER BY po.order_date DESC
LIMIT 50;


-- ============================================================
-- Q94: 审计日志查询（某时间段所有操作记录）
-- 语法: LEFT JOIN + 时间范围过滤
-- 业务: 审计员追踪系统操作记录
-- ============================================================

SELECT
    al.created_at AS operation_time,
    COALESCE(e.name, '系统') AS operator,
    al.action,
    al.target_type,
    al.target_id,
    al.old_value,
    al.new_value,
    al.remark,
    al.ip_address
FROM audit_log al
LEFT JOIN employees e ON al.employee_id = e.id
WHERE al.created_at >= CURRENT_DATE - INTERVAL '7 days'
ORDER BY al.created_at DESC
LIMIT 100;


-- ============================================================
-- Q95: 销售退货原因分布（饼图数据）
-- 语法: 条件聚合 + 百分比
-- 业务: 质量分析看退货原因分布
-- ============================================================

SELECT
    sr.return_type,
    sr.return_reason,
    COUNT(*) AS count,
    SUM(sr.total_amount) AS total_amount,
    ROUND(COUNT(*) * 100.0 / NULLIF(SUM(COUNT(*)) OVER (), 0), 2) AS count_pct,
    ROUND(SUM(sr.total_amount) * 100.0 / NULLIF(SUM(SUM(sr.total_amount)) OVER (), 0), 2) AS amount_pct,
    COUNT(DISTINCT sr.customer_id) AS affected_customers,
    COUNT(DISTINCT sri.product_id) AS affected_products
FROM sales_returns sr
JOIN sales_return_items sri ON sr.id = sri.return_id
WHERE sr.return_date >= CURRENT_DATE - INTERVAL '90 days'
GROUP BY sr.return_type, sr.return_reason
ORDER BY total_amount DESC;


-- ============================================================
-- Q96: 低库存预警（需要补货的商品清单）
-- 语法: 多表JOIN + HAVING + 子查询
-- 业务: 采购/仓管每天查看需要补货的商品
-- ============================================================

SELECT
    p.sku,
    p.name AS product_name,
    pc.name AS category_name,
    p.min_stock,
    p.max_stock,
    SUM(i.available_quantity) AS total_stock,
    p.min_stock - SUM(i.available_quantity) AS shortage,
    p.max_stock - SUM(i.available_quantity) AS suggested_order,
    ROUND(p.purchase_price * (p.max_stock - SUM(i.available_quantity)), 2) AS estimated_cost,
    -- 日均销量
    COALESCE((SELECT ROUND(SUM(soi.quantity) / 30.0, 1)
     FROM sales_order_items soi JOIN sales_orders so ON soi.order_id = so.id
     WHERE soi.product_id = p.id AND so.order_date >= CURRENT_DATE - INTERVAL '30 days'
       AND so.status NOT IN ('draft', 'cancelled')), 0) AS avg_daily_sales,
    -- 可售天数
    CASE WHEN COALESCE((SELECT ROUND(SUM(soi.quantity) / 30.0, 1)
     FROM sales_order_items soi JOIN sales_orders so ON soi.order_id = so.id
     WHERE soi.product_id = p.id AND so.order_date >= CURRENT_DATE - INTERVAL '30 days'
       AND so.status NOT IN ('draft', 'cancelled')), 0) > 0
    THEN ROUND(SUM(i.available_quantity) / (SELECT ROUND(SUM(soi.quantity) / 30.0, 1)
     FROM sales_order_items soi JOIN sales_orders so ON soi.order_id = so.id
     WHERE soi.product_id = p.id AND so.order_date >= CURRENT_DATE - INTERVAL '30 days'
       AND so.status NOT IN ('draft', 'cancelled')), 0)
    ELSE 999 END AS days_of_stock,
    -- 推荐供应商
    (SELECT CONCAT(s.name, '(¥', sp.supplier_price, '/交期', sp.lead_time_days, '天)')
     FROM supplier_products sp JOIN suppliers s ON sp.supplier_id = s.id
     WHERE sp.product_id = p.id AND s.cooperation_status = 'active'
     ORDER BY sp.supplier_price ASC LIMIT 1
    ) AS recommended_supplier
FROM products p
JOIN product_categories pc ON p.category_id = pc.id
JOIN inventory i ON p.id = i.product_id
WHERE p.status = 'active'
GROUP BY p.id, p.sku, p.name, pc.name, p.min_stock, p.max_stock, p.purchase_price
HAVING SUM(i.available_quantity) <= p.min_stock
ORDER BY shortage DESC, days_of_stock ASC
LIMIT 30;
