-- relation-detector-fixture-source: ROUTINE:erp_system.sp_mysql57_supplier_product_score
CREATE PROCEDURE sp_mysql57_supplier_product_score(IN p_supplier_id BIGINT UNSIGNED)
BEGIN
    SELECT
        sp.supplier_id,
        sp.product_id,
        AVG(sp.supplier_price) AS avg_supplier_price,
        MIN(sp.lead_time_days) AS best_lead_time_days,
        MAX(sp.quality_score) AS best_quality_score
    FROM supplier_products sp
    JOIN suppliers s ON s.id = sp.supplier_id
    WHERE sp.supplier_id = p_supplier_id
    GROUP BY sp.supplier_id, sp.product_id;
END
-- relation-detector-fixture-end
