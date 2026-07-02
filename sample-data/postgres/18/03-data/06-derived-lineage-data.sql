-- ============================================================
-- Derived lineage data cases translated from MySQL 8.0 sample-data
-- PostgreSQL 16/17/18 compatible
-- ============================================================

INSERT INTO shipments (
    shipment_no, order_id, warehouse_id, carrier, tracking_no, shipping_method,
    shipping_fee, package_count, weight_kg, status, picker_id, packer_id,
    shipped_at, delivered_at, estimated_delivery_date, actual_delivery_date,
    from_address, to_address, receiver_name, receiver_phone
)
SELECT
    CONCAT('SH-DERIVED-', CAST(so.id AS text)),
    so.id,
    so.warehouse_id,
    '顺丰速运',
    CONCAT('TN-DERIVED-', CAST(so.id AS text)),
    'express',
    ROUND(so.total_amount * 0.01, 2),
    1,
    1.000,
    CASE WHEN so.status = 'delivered' THEN 'delivered' ELSE 'shipped' END,
    so.salesperson_id,
    so.salesperson_id,
    so.order_date,
    CASE WHEN so.status = 'delivered' THEN so.order_date + INTERVAL '2 days' ELSE NULL END,
    so.order_date + INTERVAL '3 days',
    CASE WHEN so.status = 'delivered' THEN so.order_date + INTERVAL '2 days' ELSE NULL END,
    '上海市松江区物流园A区',
    c.address,
    c.contact_person,
    c.phone
FROM sales_orders so
JOIN customers c ON c.id = so.customer_id
WHERE so.status IN ('delivered', 'delivering', 'confirmed');

INSERT INTO shipping_tracks (shipment_id, track_time, location, status_desc, operator)
SELECT
    s.id,
    s.shipped_at + INTERVAL '4 hours',
    CONCAT('配送至-', c.address),
    CASE WHEN s.status = 'delivered' THEN '已签收' ELSE '运输中' END,
    '系统'
FROM shipments s
JOIN sales_orders so ON so.id = s.order_id
JOIN customers c ON c.id = so.customer_id
WHERE s.status IN ('shipped', 'delivered');

INSERT INTO invoices (
    invoice_no, invoice_type, supplier_id, invoice_date, due_date,
    total_amount, tax_amount, tax_rate, status, verified_by, verified_at
)
SELECT
    CONCAT('INV-DERIVED-', CAST(po.id AS text)),
    'purchase',
    po.supplier_id,
    po.order_date + INTERVAL '1 day',
    po.order_date + INTERVAL '30 days',
    po.total_amount,
    ROUND(po.total_amount * 0.13, 2),
    0.13,
    CASE WHEN po.status = 'received' THEN 'verified' ELSE 'received' END,
    po.purchaser_id,
    po.order_date + INTERVAL '3 days'
FROM purchase_orders po
WHERE po.status IN ('received', 'partially_received');

INSERT INTO depreciation_log (
    asset_id, depreciation_date, depreciation_amount,
    before_accumulated, after_accumulated, before_net_value, after_net_value
)
SELECT
    fa.id,
    DATE '2026-06-01',
    fa.monthly_depreciation,
    fa.accumulated_depreciation,
    fa.accumulated_depreciation + fa.monthly_depreciation,
    fa.purchase_amount - fa.accumulated_depreciation,
    fa.purchase_amount - fa.accumulated_depreciation - fa.monthly_depreciation
FROM fixed_assets fa
WHERE fa.status = 'in_use';

INSERT INTO work_orders (
    order_no, product_id, bom_id, planned_quantity, completed_quantity,
    rejected_quantity, warehouse_id, start_date, due_date, completed_date,
    status, priority, released_by
)
SELECT
    CONCAT('WO-DERIVED-', CAST(b.id AS text)),
    b.parent_product_id,
    b.id,
    100,
    80,
    2,
    1,
    CURRENT_DATE,
    CURRENT_DATE + INTERVAL '7 days',
    CURRENT_DATE + INTERVAL '5 days',
    'completed',
    'normal',
    42
FROM boms b
WHERE b.status = 'active';

INSERT INTO work_order_materials (
    work_order_id, product_id, required_qty, issued_qty, actual_consumed, unit, status
)
SELECT
    wo.id,
    b.child_product_id,
    b.quantity * wo.planned_quantity,
    b.quantity * wo.completed_quantity * 1.1,
    b.quantity * wo.completed_quantity,
    b.unit,
    CASE WHEN wo.status = 'completed' THEN 'completed' ELSE 'issued' END
FROM work_orders wo
JOIN boms b ON b.id = wo.bom_id;

INSERT INTO contract_milestones (
    contract_id, milestone_name, milestone_type, planned_date, amount, status
)
SELECT
    c.id,
    '首付款',
    'payment',
    c.start_date + INTERVAL '2 months',
    ROUND(c.total_amount * 0.30, 2),
    CASE WHEN c.status = 'active' THEN 'in_progress' ELSE 'pending' END
FROM contracts c
WHERE c.status = 'active';

INSERT INTO performance_reviews (
    review_no, employee_id, reviewer_id, review_period, review_type,
    performance_score, competency_score, attitude_score, attendance_score,
    self_assessment, reviewer_comment, status
)
SELECT
    CONCAT('PR-DERIVED-', CAST(e.id AS text)),
    e.id,
    COALESCE(e.manager_id, e.id),
    '2026-Q2',
    'quarterly',
    85.00,
    86.00,
    87.00,
    88.00,
    '本季度工作完成情况良好。',
    '继续保持。',
    'confirmed'
FROM employees e
WHERE e.status IN ('active', 'probation');

INSERT INTO ar_aging_snapshots (
    snapshot_date, customer_id, order_id, invoice_amount, paid_amount, due_date
)
SELECT
    CURRENT_DATE,
    so.customer_id,
    so.id,
    so.total_amount,
    so.paid_amount,
    so.order_date + (c.credit_days * INTERVAL '1 day')
FROM sales_orders so
JOIN customers c ON c.id = so.customer_id
WHERE so.total_amount > so.paid_amount;

INSERT INTO ap_aging_snapshots (
    snapshot_date, supplier_id, order_id, invoice_amount, paid_amount, due_date
)
SELECT
    CURRENT_DATE,
    po.supplier_id,
    po.id,
    po.total_amount,
    po.paid_amount,
    po.order_date + INTERVAL '30 days'
FROM purchase_orders po
WHERE po.total_amount > po.paid_amount;

INSERT INTO positions (department_id, name, code, level, min_salary, max_salary, headcount)
SELECT
    d.id,
    CONCAT(d.name, '经理'),
    CONCAT('MGR_', d.code),
    9,
    12000,
    30000,
    1
FROM departments d
WHERE d.id > 1;

INSERT INTO tax_invoices (
    invoice_no, invoice_code, invoice_type, tax_direction, party_type, party_id,
    invoice_date, amount_excluding_tax, tax_rate, tax_period, status
)
SELECT
    CONCAT('TAX-DERIVED-', CAST(po.id AS text)),
    CONCAT('044002', CAST(po.id AS text)),
    'vat_special',
    'input',
    'supplier',
    po.supplier_id,
    po.order_date + INTERVAL '1 day',
    ROUND(po.total_amount / 1.13, 2),
    0.13,
    TO_CHAR(po.order_date, 'YYYY-MM'),
    'issued'
FROM purchase_orders po
WHERE po.status = 'received';

INSERT INTO salary_payments (
    payment_no, employee_id, payment_date, salary_month,
    base_salary, overtime_pay, bonus, deduction,
    social_security_personal, housing_fund_personal, income_tax,
    net_pay, social_security_company, housing_fund_company, status, paid_at
)
SELECT
    CONCAT('SAL-DERIVED-', CAST(e.id AS text)),
    e.id,
    CURRENT_DATE,
    TO_CHAR(CURRENT_DATE, 'YYYY-MM'),
    e.salary,
    0,
    500,
    0,
    ROUND(e.salary * 0.08, 2),
    ROUND(e.salary * 0.12, 2),
    ROUND(GREATEST(e.salary - 5000 - e.salary * 0.20, 0) * 0.03, 2),
    e.salary + 500 - ROUND(e.salary * 0.08, 2) - ROUND(e.salary * 0.12, 2),
    ROUND(e.salary * 0.16, 2),
    ROUND(e.salary * 0.12, 2),
    'paid',
    CURRENT_TIMESTAMP
FROM employees e
WHERE e.status IN ('active', 'probation');
