-- Semantic equivalent scenario: generate MRP run items from production plan and BOM.
INSERT INTO mrp_run_items (
    plan_id,
    parent_product_id,
    component_product_id,
    required_qty,
    available_purchase_qty
)
SELECT
    pp.id,
    pp.product_id,
    b.child_product_id,
    b.quantity * pp.planned_quantity,
    poi.quantity
FROM production_plans pp
JOIN boms b ON b.parent_product_id = pp.product_id
LEFT JOIN purchase_order_items poi ON poi.product_id = b.child_product_id
WHERE pp.status = 'approved';
