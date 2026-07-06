-- MySQL 5.7 semantic rewrite: JSON_TABLE/CTE/window input is represented by a physical staging table.
-- relation-detector-fixture-source: PROCEDURE:finance.sp_cross_border_reconciliation_engine
CREATE PROCEDURE sp_cross_border_reconciliation_engine(
    IN p_target_currency VARCHAR(10),
    IN p_risk_threshold DECIMAL(5,2)
)
BEGIN
    UPDATE reconciliations r
    INNER JOIN sales_orders so ON r.reference_id = so.id
    INNER JOIN reconciliation_input_items rii ON rii.input_order_id = so.id
    INNER JOIN master_skus ms ON rii.cleaned_sku = ms.sku_ref
    LEFT JOIN currency_exchange_rates er ON ms.native_currency = er.source_currency
        AND er.target_currency = p_target_currency
        AND er.effective_date = CURRENT_DATE()
    LEFT JOIN customer_profiles cp ON so.customer_id = cp.id
    SET
        r.difference = ABS((rii.item_qty * rii.base_unit_price * COALESCE(er.rate, 1.000000)) - so.paid_amount),
        r.status = CASE
            WHEN cp.risk_score > p_risk_threshold THEN 'disputed'
            ELSE 'prepared'
        END,
        r.remark = LOWER(CONCAT('sku=', rii.cleaned_sku, '; merchant=', ms.merchant_id))
    WHERE so.status <> 'cancelled';
END
-- relation-detector-fixture-end
