-- relation-detector-fixture-source: ROUTINE:erp_system.sp_mysql57_batch_expiry_risk
CREATE PROCEDURE sp_mysql57_batch_expiry_risk(IN p_days INT)
BEGIN
    SELECT
        pb.product_id,
        p.category_id,
        SUM(i.quantity) AS at_risk_quantity,
        SUM(i.quantity * p.purchase_price) AS at_risk_value
    FROM product_batches pb
    JOIN products p ON p.id = pb.product_id
    JOIN inventory i ON i.batch_id = pb.id
    WHERE pb.expiry_date <= DATE_ADD(CURDATE(), INTERVAL p_days DAY)
    GROUP BY pb.product_id, p.category_id;
END
-- relation-detector-fixture-end
