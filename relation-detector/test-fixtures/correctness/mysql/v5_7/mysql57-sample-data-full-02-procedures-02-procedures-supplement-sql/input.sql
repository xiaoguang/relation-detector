-- relation-detector-fixture-source: ROUTINE:erp_system.sp_mysql57_salesperson_performance
CREATE PROCEDURE sp_mysql57_salesperson_performance(IN p_start_date DATE, IN p_end_date DATE)
BEGIN
    INSERT INTO sales_commissions (employee_id, order_id, order_item_id, period,
        base_amount, commission_rate, commission_amount, bonus, status, calculated_at)
    SELECT
        so.salesperson_id,
        so.id,
        soi.id,
        DATE_FORMAT(so.order_date, '%Y-%m'),
        soi.amount,
        COALESCE(cr.commission_rate, 0.02),
        ROUND(soi.amount * COALESCE(cr.commission_rate, 0.02), 2),
        COALESCE(cr.bonus, 0),
        'calculated',
        NOW()
    FROM sales_order_items soi
    JOIN sales_orders so ON soi.order_id = so.id
    JOIN products p ON soi.product_id = p.id
    LEFT JOIN commission_rules cr ON
        (cr.product_category_id IS NULL OR cr.product_category_id = p.category_id)
        AND soi.amount >= cr.min_amount
        AND soi.amount < cr.max_amount
        AND cr.status = 'active'
        AND cr.effective_date <= so.order_date
        AND (cr.expiry_date IS NULL OR cr.expiry_date >= so.order_date)
    WHERE so.order_date BETWEEN p_start_date AND p_end_date
      AND so.status NOT IN ('draft', 'cancelled');
END
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:erp_system.sp_mysql57_inventory_turnover
CREATE PROCEDURE sp_mysql57_inventory_turnover(IN p_warehouse_id BIGINT UNSIGNED)
BEGIN
    UPDATE inventory i
    JOIN (
        SELECT product_id, SUM(quantity) AS sold_quantity
        FROM sales_order_items
        GROUP BY product_id
    ) sold ON sold.product_id = i.product_id
    SET i.locked_quantity = COALESCE(i.locked_quantity, 0) + sold.sold_quantity
    WHERE i.warehouse_id = p_warehouse_id;
END
-- relation-detector-fixture-end
