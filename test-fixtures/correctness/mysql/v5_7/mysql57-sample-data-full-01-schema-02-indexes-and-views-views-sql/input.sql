-- relation-detector-fixture-source: VIEW:erp_system.v_employee_full
SELECT
    e.id,
    e.employee_no,
    e.name,
    e.gender,
    e.phone,
    e.email,
    e.hire_date,
    e.salary,
    e.status,
    CASE e.status
        WHEN 'active' THEN '在职'
        WHEN 'probation' THEN '试用期'
        WHEN 'leave' THEN '请假中'
        WHEN 'resigned' THEN '已离职'
        WHEN 'terminated' THEN '已辞退'
    END AS status_name,
    e.resignation_date,
    d.name AS department_name,
    d.code AS department_code,
    p.name AS position_name,
    p.level AS position_level,
    m.name AS manager_name,
    TIMESTAMPDIFF(YEAR, e.hire_date, CURDATE()) AS years_of_service,
    TIMESTAMPDIFF(YEAR, e.birth_date, CURDATE()) AS age
FROM employees e
JOIN departments d ON e.department_id = d.id
JOIN positions p ON e.position_id = p.id
LEFT JOIN employees m ON e.manager_id = m.id
-- relation-detector-fixture-end

-- relation-detector-fixture-source: VIEW:erp_system.v_inventory_summary
SELECT
    i.product_id,
    p.sku,
    p.name AS product_name,
    pc.name AS category_name,
    p.unit,
    p.retail_price,
    p.min_stock,
    p.max_stock,
    w.id AS warehouse_id,
    w.name AS warehouse_name,
    SUM(i.quantity) AS total_quantity,
    SUM(i.locked_quantity) AS total_locked,
    SUM(i.available_quantity) AS total_available,
    CASE
        WHEN SUM(i.available_quantity) <= p.min_stock AND p.min_stock > 0 THEN '缺货'
        WHEN SUM(i.available_quantity) <= p.min_stock * 2 AND p.min_stock > 0 THEN '低库存'
        WHEN SUM(i.available_quantity) >= p.max_stock THEN '超储'
        ELSE '正常'
    END AS stock_status,
    COUNT(DISTINCT i.batch_id) AS batch_count,
    MIN(pb.expiry_date) AS nearest_expiry
FROM inventory i
JOIN products p ON i.product_id = p.id
JOIN product_categories pc ON p.category_id = pc.id
JOIN warehouses w ON i.warehouse_id = w.id
LEFT JOIN product_batches pb ON i.batch_id = pb.id
GROUP BY i.product_id, p.sku, p.name, pc.name, p.unit, p.retail_price, p.min_stock, p.max_stock, w.id, w.name
-- relation-detector-fixture-end

-- relation-detector-fixture-source: VIEW:erp_system.v_sales_summary
SELECT
    so.id,
    so.order_no,
    so.order_date,
    so.status,
    c.name AS customer_name,
    c.type AS customer_type,
    e.name AS salesperson_name,
    d.name AS department_name,
    w.name AS warehouse_name,
    so.total_amount,
    so.discount_amount,
    so.paid_amount,
    so.tax_amount,
    so.total_amount - so.paid_amount AS unpaid_amount,
    so.payment_method,
    COUNT(soi.id) AS item_count,
    SUM(soi.quantity) AS total_qty
FROM sales_orders so
JOIN customers c ON so.customer_id = c.id
JOIN employees e ON so.salesperson_id = e.id
JOIN departments d ON e.department_id = d.id
JOIN warehouses w ON so.warehouse_id = w.id
LEFT JOIN sales_order_items soi ON so.id = soi.order_id
GROUP BY so.id, so.order_no, so.order_date, so.status, c.name, c.type,
         e.name, d.name, w.name, so.total_amount, so.discount_amount,
         so.paid_amount, so.tax_amount, so.payment_method
-- relation-detector-fixture-end

-- relation-detector-fixture-source: VIEW:erp_system.v_purchase_summary
SELECT
    po.id,
    po.order_no,
    po.order_date,
    po.status,
    s.name AS supplier_name,
    e.name AS purchaser_name,
    d.name AS department_name,
    po.total_amount,
    po.paid_amount,
    po.total_amount - po.paid_amount AS unpaid_amount,
    po.expected_delivery_date,
    po.actual_delivery_date,
    CASE
        WHEN po.actual_delivery_date IS NOT NULL AND po.actual_delivery_date > po.expected_delivery_date
        THEN DATEDIFF(po.actual_delivery_date, po.expected_delivery_date)
        ELSE 0
    END AS delay_days,
    COUNT(poi.id) AS item_count,
    SUM(poi.quantity) AS total_qty,
    SUM(poi.received_qty) AS total_received,
    SUM(poi.returned_qty) AS total_returned
FROM purchase_orders po
JOIN suppliers s ON po.supplier_id = s.id
JOIN employees e ON po.purchaser_id = e.id
JOIN departments d ON po.department_id = d.id
LEFT JOIN purchase_order_items poi ON po.id = poi.order_id
GROUP BY po.id, po.order_no, po.order_date, po.status, s.name, e.name,
         d.name, po.total_amount, po.paid_amount, po.expected_delivery_date, po.actual_delivery_date
-- relation-detector-fixture-end

-- relation-detector-fixture-source: VIEW:erp_system.v_dept_headcount
SELECT
    d.id AS dept_id,
    d.name AS dept_name,
    d.code AS dept_code,
    d.headcount_plan,
    COUNT(CASE WHEN e.status = 'active' THEN 1 END) AS active_count,
    COUNT(CASE WHEN e.status = 'probation' THEN 1 END) AS probation_count,
    COUNT(CASE WHEN e.status = 'leave' THEN 1 END) AS leave_count,
    COUNT(CASE WHEN e.status IN ('active','probation','leave') THEN 1 END) AS total_employed,
    COUNT(CASE WHEN e.status = 'resigned' AND e.resignation_date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) THEN 1 END) AS resigned_30d,
    COUNT(CASE WHEN e.status = 'resigned' AND e.resignation_date >= DATE_SUB(CURDATE(), INTERVAL 90 DAY) THEN 1 END) AS resigned_90d,
    d.headcount_plan - COUNT(CASE WHEN e.status IN ('active','probation','leave') THEN 1 END) AS vacancy,
    ROUND(AVG(e.salary), 2) AS avg_salary,
    SUM(e.salary) AS total_salary
FROM departments d
LEFT JOIN employees e ON d.id = e.department_id
GROUP BY d.id, d.name, d.code, d.headcount_plan
-- relation-detector-fixture-end

-- relation-detector-fixture-source: VIEW:erp_system.v_account_balance
SELECT
    a.id,
    a.code,
    a.name,
    a.account_type,
    a.balance_direction,
    a.current_balance,
    COALESCE(SUM(CASE WHEN vi.direction = 'debit' THEN vi.amount ELSE 0 END), 0) AS period_debit,
    COALESCE(SUM(CASE WHEN vi.direction = 'credit' THEN vi.amount ELSE 0 END), 0) AS period_credit,
    CASE a.balance_direction
        WHEN 'debit' THEN a.current_balance + COALESCE(SUM(CASE WHEN vi.direction = 'debit' THEN vi.amount ELSE -vi.amount END), 0)
        WHEN 'credit' THEN a.current_balance + COALESCE(SUM(CASE WHEN vi.direction = 'credit' THEN vi.amount ELSE -vi.amount END), 0)
    END AS calculated_balance
FROM accounts a
LEFT JOIN voucher_items vi ON a.id = vi.account_id
LEFT JOIN vouchers v ON vi.voucher_id = v.id AND v.status = 'posted'
GROUP BY a.id, a.code, a.name, a.account_type, a.balance_direction, a.current_balance
-- relation-detector-fixture-end
