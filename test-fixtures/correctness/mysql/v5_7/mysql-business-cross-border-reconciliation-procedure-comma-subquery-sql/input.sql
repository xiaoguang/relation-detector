-- MySQL 5.7 semantic rewrite using comma rowsets instead of CTE/JSON_TABLE.
-- relation-detector-fixture-source: PROCEDURE:finance.sp_cross_border_reconciliation_engine
CREATE PROCEDURE sp_cross_border_reconciliation_engine(
    IN p_target_currency VARCHAR(10),
    IN p_risk_threshold DECIMAL(5,2)
)
BEGIN
    UPDATE reconciliations r, sales_orders so, reconciliation_input_items rii, master_skus ms
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
    WHERE r.reference_id = so.id
      AND rii.input_order_id = so.id
      AND rii.cleaned_sku = ms.sku_ref
      AND so.status <> 'cancelled';
END
-- relation-detector-fixture-end
