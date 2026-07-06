-- ============================================================
-- ERP深业务场景分析查询
-- 覆盖: MRP短缺、工单成本、库存估值、AR/AP、WMS、维修、
--       预算执行、主数据治理、销售毛利和生产效率
-- 数据库: MySQL 8.0
-- ============================================================

USE erp_system;

-- Q1: MRP物料短缺和建议采购
SELECT
    pp.plan_no,
    mr.run_no,
    parent.sku AS parent_sku,
    component.sku AS component_sku,
    component.name AS component_name,
    mri.gross_requirement,
    mri.on_hand_qty,
    mri.reserved_qty,
    mri.planned_receipt_qty,
    mri.net_requirement,
    mri.suggested_order_qty,
    s.name AS suggested_supplier_name,
    mri.suggested_due_date
FROM mrp_runs mr
JOIN production_plans pp ON pp.id = mr.plan_id
JOIN mrp_run_items mri ON mri.run_id = mr.id
JOIN products parent ON parent.id = mri.parent_product_id
JOIN products component ON component.id = mri.component_product_id
LEFT JOIN suppliers s ON s.id = mri.suggested_supplier_id
WHERE mr.status = 'completed'
  AND mri.net_requirement > 0
ORDER BY pp.plan_month, component.sku;

-- Q2: 工单实际成本、标准成本和差异
SELECT
    wo.order_no,
    p.sku,
    p.name AS product_name,
    woc.material_cost,
    woc.labor_cost,
    woc.overhead_cost,
    woc.finished_qty,
    woc.unit_cost AS actual_unit_cost,
    sc.material_cost + sc.labor_cost + sc.overhead_cost AS standard_unit_cost,
    woc.variance_amount,
    ROUND(woc.variance_amount / NULLIF(woc.finished_qty, 0), 4) AS unit_variance
FROM work_orders wo
JOIN products p ON p.id = wo.product_id
JOIN work_order_costs woc ON woc.work_order_id = wo.id
LEFT JOIN standard_costs sc ON sc.product_id = wo.product_id
    AND sc.status = 'active'
    AND sc.effective_from <= CURRENT_DATE
    AND (sc.effective_to IS NULL OR sc.effective_to >= CURRENT_DATE)
WHERE wo.status IN ('in_progress', 'completed', 'released')
ORDER BY ABS(woc.variance_amount) DESC;

-- Q3: 库存估值按仓库和品类汇总
SELECT
    wh.name AS warehouse_name,
    pc.name AS category_name,
    COUNT(DISTINCT ivs.product_id) AS product_count,
    SUM(ivs.quantity) AS total_quantity,
    SUM(ivs.inventory_value) AS inventory_value,
    ROUND(SUM(ivs.inventory_value) / NULLIF(SUM(ivs.quantity), 0), 4) AS avg_unit_cost
FROM inventory_valuation_snapshots ivs
JOIN products p ON p.id = ivs.product_id
JOIN product_categories pc ON pc.id = p.category_id
JOIN warehouses wh ON wh.id = ivs.warehouse_id
WHERE ivs.snapshot_date = '2026-02-28'
GROUP BY wh.name, pc.name
ORDER BY inventory_value DESC;

-- Q4: 应收和应付现金敞口
SELECT
    'AR' AS exposure_type,
    c.name AS party_name,
    ar.ar_no AS document_no,
    ar.due_date,
    ar.invoice_amount,
    ar.paid_amount,
    ar.invoice_amount - ar.paid_amount - ar.writeoff_amount AS open_amount,
    DATEDIFF(CURRENT_DATE, ar.due_date) AS overdue_days
FROM ar_invoices ar
JOIN customers c ON c.id = ar.customer_id
WHERE ar.status IN ('open', 'partially_paid')

UNION ALL

SELECT
    'AP' AS exposure_type,
    s.name AS party_name,
    ap.ap_no AS document_no,
    ap.due_date,
    ap.invoice_amount,
    ap.paid_amount,
    ap.invoice_amount - ap.paid_amount AS open_amount,
    DATEDIFF(CURRENT_DATE, ap.due_date) AS overdue_days
FROM ap_invoices ap
JOIN suppliers s ON s.id = ap.supplier_id
WHERE ap.status IN ('open', 'partially_paid')
ORDER BY overdue_days DESC, open_amount DESC;

-- Q5: WMS拣货任务履约和库位占用
SELECT
    pt.task_no,
    so.order_no,
    c.name AS customer_name,
    wh.name AS warehouse_name,
    wz.zone_name,
    wl.location_code,
    p.sku,
    pti.required_qty,
    pti.picked_qty,
    ilb.quantity AS location_quantity,
    ilb.locked_quantity,
    e.name AS picker_name,
    pt.status
FROM picking_tasks pt
JOIN picking_task_items pti ON pti.picking_task_id = pt.id
JOIN sales_orders so ON so.id = pt.sales_order_id
JOIN customers c ON c.id = so.customer_id
JOIN warehouses wh ON wh.id = pt.warehouse_id
JOIN products p ON p.id = pti.product_id
JOIN warehouse_locations wl ON wl.id = pti.location_id
JOIN warehouse_zones wz ON wz.id = wl.zone_id
LEFT JOIN inventory_location_balances ilb ON ilb.location_id = pti.location_id
    AND ilb.product_id = pti.product_id
    AND (ilb.batch_id <=> pti.batch_id)
LEFT JOIN employees e ON e.id = pt.assigned_to
WHERE pt.status IN ('allocated', 'picked', 'packed')
ORDER BY pt.task_no, p.sku;

-- Q6: 售后维修备件消耗和成本
SELECT
    ro.repair_no,
    st.ticket_no,
    c.name AS customer_name,
    finished.sku AS repaired_product_sku,
    part.sku AS part_sku,
    rop.quantity,
    rop.unit_cost,
    rop.quantity * rop.unit_cost AS part_cost,
    wh.name AS issued_warehouse,
    tech.name AS technician_name,
    ro.status
FROM repair_orders ro
JOIN service_tickets st ON st.id = ro.service_ticket_id
JOIN customers c ON c.id = ro.customer_id
JOIN products finished ON finished.id = ro.product_id
JOIN repair_order_parts rop ON rop.repair_order_id = ro.id
JOIN products part ON part.id = rop.product_id
JOIN warehouses wh ON wh.id = rop.issued_from_warehouse_id
LEFT JOIN employees tech ON tech.id = ro.technician_id
ORDER BY ro.repair_no, part.sku;

-- Q7: 部门预算执行率
SELECT
    bv.version_code,
    d.name AS department_name,
    subj.subject_code,
    subj.subject_name,
    bi.period_code,
    bi.budget_amount,
    bi.used_amount,
    bi.budget_amount - bi.used_amount AS remaining_amount,
    ROUND(bi.used_amount / NULLIF(bi.budget_amount, 0) * 100, 2) AS usage_pct
FROM budget_versions bv
JOIN budget_items bi ON bi.version_id = bv.id
JOIN departments d ON d.id = bi.department_id
JOIN account_subjects subj ON subj.id = bi.subject_id
WHERE bv.status = 'approved'
ORDER BY bi.period_code, usage_pct DESC;

-- Q8: 主数据变更和敏感字段访问审计
SELECT
    mdc.request_no,
    mdc.master_type,
    mdc.master_id,
    requester.name AS requested_by_name,
    approver.name AS approved_by_name,
    mdi.field_name,
    mdi.old_value,
    mdi.new_value,
    sal.field_name AS accessed_field,
    sal.access_reason,
    sal.accessed_at
FROM master_data_change_requests mdc
JOIN master_data_change_items mdi ON mdi.request_id = mdc.id
JOIN employees requester ON requester.id = mdc.requested_by
LEFT JOIN employees approver ON approver.id = mdc.approved_by
LEFT JOIN sensitive_access_logs sal ON sal.object_type = mdc.master_type
    AND sal.object_id = mdc.master_id
WHERE mdc.status IN ('approved', 'applied', 'submitted')
ORDER BY mdc.requested_at DESC, mdi.field_name;

-- Q9: 销售毛利和销售成本追踪
SELECT
    so.order_no,
    c.name AS customer_name,
    p.sku,
    p.name AS product_name,
    soi.quantity,
    soi.amount AS sales_amount,
    COALESCE(ce.cogs_amount, 0.00) AS cogs_amount,
    soi.amount - COALESCE(ce.cogs_amount, 0.00) AS gross_margin,
    ROUND((soi.amount - COALESCE(ce.cogs_amount, 0.00)) / NULLIF(soi.amount, 0) * 100, 2) AS gross_margin_pct,
    ce.posted_at AS cogs_posted_at
FROM sales_orders so
JOIN sales_order_items soi ON soi.order_id = so.id
JOIN customers c ON c.id = so.customer_id
JOIN products p ON p.id = soi.product_id
LEFT JOIN cogs_entries ce ON ce.sales_order_item_id = soi.id
WHERE so.order_date >= DATE_SUB(CURRENT_DATE, INTERVAL 180 DAY)
ORDER BY gross_margin DESC;

-- Q10: 工序效率、报废和返工分析
SELECT
    wo.order_no,
    po.operation_no,
    po.operation_name,
    e.name AS operator_name,
    woo.qualified_qty,
    woo.scrapped_qty,
    woo.rework_qty,
    SUM(opr.labor_minutes) AS actual_labor_minutes,
    po.standard_minutes,
    ROUND(SUM(opr.labor_minutes) / NULLIF(po.standard_minutes, 0), 2) AS labor_efficiency_ratio
FROM work_order_operations woo
JOIN work_orders wo ON wo.id = woo.work_order_id
JOIN production_operations po ON po.id = woo.operation_id
LEFT JOIN operation_reports opr ON opr.work_order_operation_id = woo.id
LEFT JOIN employees e ON e.id = woo.assigned_employee_id
GROUP BY wo.order_no, po.operation_no, po.operation_name, e.name,
         woo.qualified_qty, woo.scrapped_qty, woo.rework_qty, po.standard_minutes
ORDER BY wo.order_no, po.operation_no;

-- Q11: 采购、入库、应付发票和付款申请闭环
SELECT
    po.order_no,
    s.name AS supplier_name,
    pr.receipt_no,
    ap.ap_no,
    pay.request_no,
    po.total_amount AS purchase_amount,
    pr.total_amount AS receipt_amount,
    ap.invoice_amount,
    ap.paid_amount,
    pri.requested_amount,
    pay.status AS payment_status
FROM purchase_orders po
JOIN suppliers s ON s.id = po.supplier_id
LEFT JOIN purchase_receipts pr ON pr.order_id = po.id
LEFT JOIN ap_invoices ap ON ap.purchase_order_id = po.id
LEFT JOIN payment_request_items pri ON pri.ap_invoice_id = ap.id
LEFT JOIN payment_requests pay ON pay.id = pri.request_id
WHERE po.order_date >= DATE_SUB(CURRENT_DATE, INTERVAL 365 DAY)
ORDER BY po.order_date DESC, po.order_no;

-- Q12: 管理层总览: 收入、成本、库存、应收、应付和维修成本
SELECT
    metrics.metric_name,
    metrics.metric_amount
FROM (
    SELECT 'sales_amount_180d' AS metric_name, SUM(soi.amount) AS metric_amount
    FROM sales_orders so
    JOIN sales_order_items soi ON soi.order_id = so.id
    WHERE so.order_date >= DATE_SUB(CURRENT_DATE, INTERVAL 180 DAY)

    UNION ALL

    SELECT 'cogs_amount_180d' AS metric_name, SUM(ce.cogs_amount) AS metric_amount
    FROM cogs_entries ce
    WHERE ce.posted_at >= DATE_SUB(NOW(), INTERVAL 180 DAY)

    UNION ALL

    SELECT 'inventory_value' AS metric_name, SUM(ivs.inventory_value) AS metric_amount
    FROM inventory_valuation_snapshots ivs
    WHERE ivs.snapshot_date = '2026-02-28'

    UNION ALL

    SELECT 'open_ar_amount' AS metric_name, SUM(ar.invoice_amount - ar.paid_amount - ar.writeoff_amount) AS metric_amount
    FROM ar_invoices ar
    WHERE ar.status IN ('open', 'partially_paid')

    UNION ALL

    SELECT 'open_ap_amount' AS metric_name, SUM(ap.invoice_amount - ap.paid_amount) AS metric_amount
    FROM ap_invoices ap
    WHERE ap.status IN ('open', 'partially_paid')

    UNION ALL

    SELECT 'repair_parts_cost' AS metric_name, SUM(rop.quantity * rop.unit_cost) AS metric_amount
    FROM repair_order_parts rop
) metrics
ORDER BY metrics.metric_name;

-- Q13: 语义层例子 - 每个客户最近30天支付金额
SELECT
    c.id AS customer_id,
    c.code AS customer_code,
    c.name AS customer_name,
    COUNT(p.id) AS payment_count_30d,
    SUM(CASE WHEN p.payment_status = 'paid' THEN p.amount ELSE 0 END) AS paid_amount_30d,
    SUM(CASE WHEN p.payment_status = 'failed' THEN 1 ELSE 0 END) AS failed_payment_count
FROM customers c
LEFT JOIN payments p ON p.customer_id = c.id
    AND p.payment_date >= DATE_SUB(CURRENT_DATE, INTERVAL 30 DAY)
GROUP BY c.id, c.code, c.name
HAVING payment_count_30d > 0
ORDER BY paid_amount_30d DESC;

-- Q14: 语义层例子 - 活跃客户候选口径对照
SELECT
    c.id AS customer_id,
    c.name AS customer_name,
    c.status AS customer_status,
    MAX(so.order_date) AS last_order_date,
    MAX(p.payment_date) AS last_payment_date,
    COUNT(DISTINCT so.id) AS order_count,
    SUM(CASE WHEN p.payment_status = 'paid' THEN p.amount ELSE 0 END) AS total_paid_amount,
    CASE
        WHEN c.status = 'active'
             AND (MAX(so.order_date) >= DATE_SUB(CURRENT_DATE, INTERVAL 90 DAY)
                  OR MAX(p.payment_date) >= DATE_SUB(CURRENT_DATE, INTERVAL 90 DAY))
        THEN 'ACTIVE_BY_STATUS_AND_ACTIVITY'
        WHEN c.status = 'active' THEN 'ACTIVE_BY_STATUS_ONLY'
        ELSE 'INACTIVE_OR_REVIEW'
    END AS active_customer_candidate
FROM customers c
LEFT JOIN sales_orders so ON so.customer_id = c.id
LEFT JOIN payments p ON p.customer_id = c.id
GROUP BY c.id, c.name, c.status
ORDER BY total_paid_amount DESC;

-- Q15: 语义层例子 - 这个财年华东地区女装销售情况
SELECT
    fc.fiscal_year,
    fc.fiscal_month,
    rd.sales_region,
    cd.level2_name AS product_category,
    COUNT(DISTINCT sf.order_id) AS order_count,
    SUM(sf.quantity_sold) AS quantity_sold,
    SUM(sf.sales_amount) AS sales_amount,
    SUM(sf.paid_amount) AS paid_amount,
    SUM(sf.refund_amount) AS refund_amount,
    SUM(sf.net_sales_amount) AS net_sales_amount,
    ROUND(SUM(sf.sales_amount) / NULLIF(COUNT(DISTINCT sf.order_id), 0), 2) AS avg_order_value
FROM sales_fact sf
JOIN fiscal_calendar fc ON fc.calendar_date = sf.fiscal_date
JOIN region_dim rd ON rd.id = sf.region_dim_id
JOIN category_dim cd ON cd.id = sf.category_dim_id
WHERE fc.is_current_fiscal_year = TRUE
  AND rd.sales_region = '华东'
  AND cd.is_womenwear = TRUE
GROUP BY fc.fiscal_year, fc.fiscal_month, rd.sales_region, cd.level2_name
ORDER BY fc.fiscal_year, fc.fiscal_month;

-- Q16: 语义层例子 - 退款率多口径
SELECT
    fc.period_code,
    COUNT(DISTINCT sf.order_id) AS order_count,
    SUM(sf.sales_amount) AS sales_amount,
    SUM(sf.refund_amount) AS refund_amount,
    ROUND(SUM(sf.refund_amount) * 100.0 / NULLIF(SUM(sf.sales_amount), 0), 2) AS refund_rate_by_amount,
    ROUND(COUNT(DISTINCT CASE WHEN sf.refund_amount > 0 THEN sf.order_id END) * 100.0
        / NULLIF(COUNT(DISTINCT sf.order_id), 0), 2) AS refund_rate_by_order
FROM sales_fact sf
JOIN fiscal_calendar fc ON fc.calendar_date = sf.fiscal_date
GROUP BY fc.period_code
ORDER BY fc.period_code;

-- Q17: 语义层例子 - 商品库存风险
SELECT
    p.id AS product_id,
    p.sku,
    p.name AS product_name,
    wh.name AS warehouse_name,
    inv.available_quantity,
    p.min_stock,
    p.max_stock,
    COALESCE(SUM(sf.quantity_sold), 0) AS fiscal_quantity_sold,
    CASE
        WHEN inv.available_quantity < p.min_stock THEN 'SHORTAGE_RISK'
        WHEN inv.available_quantity > p.max_stock THEN 'OVERSTOCK_RISK'
        WHEN COALESCE(SUM(sf.quantity_sold), 0) = 0 THEN 'SLOW_MOVING_RISK'
        ELSE 'NORMAL'
    END AS inventory_risk_candidate
FROM inventory inv
JOIN products p ON p.id = inv.product_id
JOIN warehouses wh ON wh.id = inv.warehouse_id
LEFT JOIN sales_fact sf ON sf.product_id = p.id
    AND sf.warehouse_id = inv.warehouse_id
GROUP BY p.id, p.sku, p.name, wh.name, inv.available_quantity, p.min_stock, p.max_stock
HAVING inventory_risk_candidate <> 'NORMAL'
ORDER BY inventory_risk_candidate, p.sku;

-- Q18: 语义层例子 - RFM客户分层草稿
WITH customer_rfm AS (
    SELECT
        c.id AS customer_id,
        c.name AS customer_name,
        DATEDIFF(CURRENT_DATE, MAX(sf.fiscal_date)) AS recency_days,
        COUNT(DISTINCT sf.order_id) AS frequency_orders,
        SUM(sf.net_sales_amount) AS monetary_amount
    FROM customers c
    JOIN sales_fact sf ON sf.customer_id = c.id
    GROUP BY c.id, c.name
)
SELECT
    customer_id,
    customer_name,
    recency_days,
    frequency_orders,
    monetary_amount,
    CASE
        WHEN recency_days <= 30 AND frequency_orders >= 2 AND monetary_amount >= 30000 THEN 'HIGH_VALUE'
        WHEN recency_days <= 90 AND monetary_amount >= 10000 THEN 'GROWTH'
        ELSE 'OBSERVE'
    END AS rfm_segment_candidate
FROM customer_rfm
ORDER BY monetary_amount DESC;

-- Q19: 语义层例子 - 失败支付记录
SELECT
    p.payment_no,
    c.name AS customer_name,
    so.order_no,
    p.payment_date,
    p.amount,
    p.payment_method,
    p.failure_reason
FROM payments p
JOIN customers c ON c.id = p.customer_id
LEFT JOIN sales_orders so ON so.id = p.order_id
WHERE p.payment_status = 'failed'
ORDER BY p.payment_date DESC;

-- Q20: 语义层支撑层与OLTP订单金额对账
SELECT
    so.order_no,
    so.total_amount AS order_total_amount,
    SUM(sf.sales_amount) AS fact_sales_amount,
    so.paid_amount AS order_paid_amount,
    SUM(sf.paid_amount) AS fact_paid_amount,
    SUM(sf.refund_amount) AS fact_refund_amount,
    CASE
        WHEN ABS(so.total_amount - SUM(sf.sales_amount)) <= 1.00 THEN 'MATCHED'
        ELSE 'REVIEW'
    END AS reconciliation_status
FROM sales_orders so
JOIN sales_fact sf ON sf.order_id = so.id
GROUP BY so.id, so.order_no, so.total_amount, so.paid_amount
ORDER BY so.order_no;
