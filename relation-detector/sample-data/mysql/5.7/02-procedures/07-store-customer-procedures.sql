USE erp_system;

DELIMITER //

CREATE PROCEDURE sp_mysql57_customer_store_summary(IN p_customer_id BIGINT UNSIGNED)
BEGIN
    SELECT
        so.customer_id,
        so.warehouse_id,
        SUM(soi.quantity) AS quantity_sold,
        SUM(soi.amount) AS sales_amount
    FROM sales_orders so
    JOIN sales_order_items soi ON soi.order_id = so.id
    WHERE so.customer_id = p_customer_id
    GROUP BY so.customer_id, so.warehouse_id;
END//

CREATE PROCEDURE sp_mysql57_store_product_affinity(IN p_warehouse_id BIGINT UNSIGNED)
BEGIN
    SELECT
        soi.product_id,
        p.category_id,
        SUM(soi.quantity) AS quantity_sold,
        SUM(soi.amount) AS sales_amount
    FROM sales_orders so
    JOIN sales_order_items soi ON soi.order_id = so.id
    JOIN products p ON p.id = soi.product_id
    WHERE so.warehouse_id = p_warehouse_id
    GROUP BY soi.product_id, p.category_id;
END//

DELIMITER ;
