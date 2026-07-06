-- MySQL 5.7-compatible ERP analysis queries.
-- CTE/window based MySQL 8.0 examples are rewritten as derived tables and grouped joins.

SELECT
    c.id AS customer_id,
    c.name AS customer_name,
    w.id AS warehouse_id,
    w.name AS warehouse_name,
    pc.id AS category_id,
    pc.name AS category_name,
    SUM(soi.quantity) AS quantity_sold,
    SUM(soi.amount) AS sales_amount
FROM sales_orders so
JOIN customers c ON so.customer_id = c.id
JOIN warehouses w ON so.warehouse_id = w.id
JOIN sales_order_items soi ON soi.order_id = so.id
JOIN products p ON soi.product_id = p.id
JOIN product_categories pc ON p.category_id = pc.id
WHERE so.status NOT IN ('draft', 'cancelled')
GROUP BY c.id, c.name, w.id, w.name, pc.id, pc.name
HAVING SUM(soi.amount) > 0;

SELECT
    p.id AS product_id,
    p.sku,
    p.name,
    inv.total_quantity,
    sales.total_quantity AS sold_quantity,
    sales.total_amount AS sold_amount
FROM products p
JOIN (
    SELECT product_id, SUM(quantity) AS total_quantity
    FROM inventory
    GROUP BY product_id
) inv ON inv.product_id = p.id
LEFT JOIN (
    SELECT product_id, SUM(quantity) AS total_quantity, SUM(amount) AS total_amount
    FROM sales_order_items
    GROUP BY product_id
) sales ON sales.product_id = p.id
WHERE inv.total_quantity > 0;

SELECT
    po.id AS purchase_order_id,
    s.id AS supplier_id,
    s.name AS supplier_name,
    poi.product_id,
    SUM(poi.quantity) AS ordered_quantity,
    SUM(poi.amount) AS ordered_amount
FROM purchase_orders po
JOIN suppliers s ON po.supplier_id = s.id
JOIN purchase_order_items poi ON poi.order_id = po.id
WHERE po.status <> 'cancelled'
GROUP BY po.id, s.id, s.name, poi.product_id;

SELECT
    sf.fiscal_month,
    r.sales_region,
    cat.level1_name AS category_name,
    SUM(sf.sales_amount) AS sales_amount,
    COUNT(DISTINCT sf.order_id) AS order_count,
    SUM(sf.quantity_sold) AS quantity_sold
FROM sales_fact sf
JOIN region_dim r ON sf.region_id = r.id
JOIN category_dim cat ON sf.category_id = cat.id
JOIN fiscal_calendar fc ON sf.fiscal_date = fc.calendar_date
WHERE fc.current_fiscal_year = 1
GROUP BY sf.fiscal_month, r.sales_region, cat.level1_name;

SELECT
    parent.id AS parent_product_id,
    child.id AS component_product_id,
    b.quantity AS component_quantity
FROM boms b
JOIN products parent ON b.product_id = parent.id
JOIN products child ON b.component_product_id = child.id
WHERE b.is_active = 1;

SELECT
    sr.customer_id,
    sri.product_id,
    SUM(sri.return_qty) AS return_qty,
    SUM(sri.amount) AS return_amount
FROM sales_returns sr
JOIN sales_return_items sri ON sri.return_id = sr.id
GROUP BY sr.customer_id, sri.product_id;

SELECT
    dr.warehouse_id,
    dri.product_id,
    SUM(dri.loss_amount) AS loss_amount
FROM damage_reports dr
JOIN damage_report_items dri ON dri.report_id = dr.id
GROUP BY dr.warehouse_id, dri.product_id;
