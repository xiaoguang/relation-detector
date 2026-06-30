-- ============================================================
-- ERP企业级扩展分析查询 - PostgreSQL 17
-- 覆盖: 盘点差异、调拨履约、收付款核销、会计期间、
--       工艺路线、地址与税率
-- ============================================================

-- Q1: 库存盘点差异分析
SELECT
    st.stocktake_no,
    w.name AS warehouse_name,
    p.sku,
    p.name AS product_name,
    sti.book_quantity,
    sti.counted_quantity,
    sti.variance_quantity,
    ROUND(sti.variance_quantity * p.purchase_price, 2) AS variance_amount,
    sti.variance_reason,
    e.name AS created_by_name
FROM stocktakes st
JOIN stocktake_items sti ON sti.stocktake_id = st.id
JOIN warehouses w ON st.warehouse_id = w.id
JOIN products p ON sti.product_id = p.id
JOIN employees e ON st.created_by = e.id
WHERE st.stocktake_date >= CURRENT_DATE - INTERVAL '90 days'
ORDER BY ABS(sti.variance_quantity) DESC;

-- Q2: 调拨履约和在途库存
SELECT
    tr.transfer_no,
    wf.name AS from_warehouse,
    wt.name AS to_warehouse,
    p.sku,
    ti.quantity,
    ti.received_quantity,
    ti.quantity - ti.received_quantity AS in_transit_quantity,
    tr.status,
    req.name AS requested_by_name
FROM stock_transfers tr
JOIN stock_transfer_items ti ON ti.transfer_id = tr.id
JOIN warehouses wf ON tr.from_warehouse_id = wf.id
JOIN warehouses wt ON tr.to_warehouse_id = wt.id
JOIN products p ON ti.product_id = p.id
JOIN employees req ON tr.requested_by = req.id
WHERE tr.transfer_date >= CURRENT_DATE - INTERVAL '180 days'
ORDER BY tr.transfer_date DESC, tr.transfer_no;

-- Q3: 客户回款与订单核销
SELECT
    pr.receipt_no,
    c.name AS customer_name,
    pr.receipt_date,
    pr.amount AS receipt_amount,
    SUM(pra.allocated_amount) AS allocated_amount,
    pr.amount - SUM(pra.allocated_amount) AS unallocated_amount,
    string_agg(pra.reference_type || '#' || pra.reference_id, ', ' ORDER BY pra.reference_type) AS allocated_refs
FROM payment_receipts pr
JOIN customers c ON pr.party_type = 'customer' AND pr.party_id = c.id
LEFT JOIN payment_receipt_allocations pra ON pra.receipt_id = pr.id
WHERE pr.receipt_type = 'customer_receipt'
GROUP BY pr.id, pr.receipt_no, c.name, pr.receipt_date, pr.amount
ORDER BY pr.receipt_date DESC;

-- Q4: 会计期间关闭进度
SELECT
    lb.book_name,
    ap.period_code,
    ap.status AS period_status,
    COUNT(pcj.id) AS job_count,
    SUM(CASE WHEN pcj.status = 'success' THEN 1 ELSE 0 END) AS success_jobs,
    SUM(CASE WHEN pcj.status IN ('failed', 'pending') THEN 1 ELSE 0 END) AS pending_or_failed_jobs,
    MAX(pcj.finished_at) AS last_finished_at
FROM accounting_periods ap
JOIN ledger_books lb ON ap.ledger_book_id = lb.id
LEFT JOIN period_close_jobs pcj ON pcj.period_id = ap.id
GROUP BY lb.book_name, ap.period_code, ap.status
ORDER BY ap.period_code DESC, lb.book_name;

-- Q5: 工艺路线和工序依赖链
WITH RECURSIVE operation_chain AS (
    SELECT
        pr.product_id,
        pr.route_code,
        po.id AS operation_id,
        po.operation_no,
        po.operation_name,
        po.predecessor_operation_id,
        po.operation_name::VARCHAR(500) AS operation_path,
        po.standard_minutes
    FROM production_routes pr
    JOIN production_operations po ON po.route_id = pr.id
    WHERE po.predecessor_operation_id IS NULL

    UNION ALL

    SELECT
        oc.product_id,
        oc.route_code,
        po.id AS operation_id,
        po.operation_no,
        po.operation_name,
        po.predecessor_operation_id,
        (oc.operation_path || ' -> ' || po.operation_name)::VARCHAR(500) AS operation_path,
        oc.standard_minutes + po.standard_minutes AS standard_minutes
    FROM operation_chain oc
    JOIN production_operations po ON po.predecessor_operation_id = oc.operation_id
)
SELECT
    p.sku,
    p.name AS product_name,
    route_code,
    operation_path,
    standard_minutes AS cumulative_standard_minutes
FROM operation_chain oc
JOIN products p ON oc.product_id = p.id
ORDER BY p.sku, cumulative_standard_minutes;

-- Q6: 地址、税率和区域销售回款视图
SELECT
    c.name AS customer_name,
    ca.province,
    ca.city,
    tr.tax_name,
    tr.rate AS vat_rate,
    SUM(pr.amount) AS confirmed_receipt_amount
FROM customers c
JOIN customer_addresses ca ON ca.customer_id = c.id AND ca.is_default = TRUE
JOIN payment_receipts pr ON pr.party_type = 'customer' AND pr.party_id = c.id AND pr.status = 'confirmed'
JOIN tax_rates tr ON tr.tax_code = 'VAT13'
GROUP BY c.name, ca.province, ca.city, tr.tax_name, tr.rate
ORDER BY confirmed_receipt_amount DESC;
