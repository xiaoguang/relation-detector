-- relation-detector-fixture-source: ROUTINE:erp_system.sp_mysql57_salesperson_performance
CREATE PROCEDURE sp_mysql57_salesperson_performance(IN p_start_date DATE, IN p_end_date DATE)
BEGIN
    INSERT INTO sales_commissions (employee_id, commission_month, sales_amount, commission_amount, status)
    SELECT
        so.salesperson_id,
        DATE_FORMAT(so.order_date, '%Y-%m'),
        SUM(so.total_amount),
        SUM(so.total_amount * cr.rate),
        'calculated'
    FROM sales_orders so
    JOIN commission_rules cr ON so.salesperson_id = cr.employee_id
    WHERE so.order_date BETWEEN p_start_date AND p_end_date
    GROUP BY so.salesperson_id, DATE_FORMAT(so.order_date, '%Y-%m');
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
    SET i.reserved_quantity = COALESCE(i.reserved_quantity, 0) + sold.sold_quantity
    WHERE i.warehouse_id = p_warehouse_id;
END
-- relation-detector-fixture-end
