-- MySQL business procedure case: JSON_TABLE input, CTEs, simulated FULL OUTER JOIN
-- via LEFT/RIGHT UNION ALL, correlated subquery, and final comma rowset filtering.
-- Parameter JSON, literal filters, dynamic result set columns, and Data Lineage are
-- semantic references only; formal relationship gold records physical table relationships.
-- relation-detector-fixture-source: PROCEDURE:finance.sp_cross_border_reconciliation_engine
CREATE PROCEDURE sp_cross_border_reconciliation_engine(
    IN p_input_matrix_json JSON,
    IN p_target_currency VARCHAR(10),
    IN p_risk_threshold NUMERIC(5,2)
)
BEGIN
    DECLARE v_batch_id VARCHAR(36);
    SET v_batch_id = UUID();

    WITH memory_input_cargo AS (
        SELECT
            jt.input_order_id,
            UPPER(REGEXP_REPLACE(jt.input_sku, '[^a-zA-Z0-9_-]', '')) AS cleaned_sku,
            jt.item_qty,
            jt.base_unit_price
        FROM JSON_TABLE(
            p_input_matrix_json,
            '$[*]' COLUMNS(
                input_order_id VARCHAR(64) PATH '$.order_id',
                input_sku VARCHAR(50) PATH '$.sku',
                item_qty INT PATH '$.qty',
                base_unit_price NUMERIC(16,4) PATH '$.price'
            )
        ) AS jt
    ),
    fx_valuation AS (
        SELECT
            mic.input_order_id,
            mic.cleaned_sku,
            mic.item_qty,
            mic.base_unit_price,
            ms.merchant_id,
            ms.is_perishable,
            COALESCE(er.rate, 1.000000) AS conversion_rate,
            (mic.item_qty * mic.base_unit_price) AS raw_total_foreign
        FROM memory_input_cargo mic
        INNER JOIN master_skus ms ON mic.cleaned_sku = ms.sku_ref
        LEFT JOIN currency_exchange_rates er ON
            ms.native_currency = er.source_currency
            AND er.target_currency = p_target_currency
            AND er.effective_date = CURRENT_DATE()
    ),
    reconciliation_gap_analysis AS (
        SELECT
            fx.input_order_id AS final_order_id,
            fx.cleaned_sku,
            fx.merchant_id,
            fx.raw_total_foreign,
            fx.conversion_rate,
            db_o.payment_status AS db_payment_status,
            db_o.customer_id AS db_customer_id,
            CASE
                WHEN db_o.id IS NULL THEN 'MISSING_IN_DATABASE'
                WHEN ABS((fx.raw_total_foreign * fx.conversion_rate) - db_o.pay_amount) > 0.01 THEN 'AMOUNT_MISMATCH'
                ELSE 'MATCHED'
            END AS audit_status
        FROM fx_valuation fx
        LEFT JOIN orders db_o ON fx.input_order_id = db_o.id

        UNION ALL

        SELECT
            db_o.id AS final_order_id,
            NULL AS cleaned_sku,
            NULL AS merchant_id,
            NULL AS raw_total_foreign,
            NULL AS conversion_rate,
            db_o.payment_status AS db_payment_status,
            db_o.customer_id AS db_customer_id,
            'MISSING_IN_INPUT' AS audit_status
        FROM fx_valuation fx
        RIGHT JOIN orders db_o ON fx.input_order_id = db_o.id
        WHERE fx.input_order_id IS NULL
    ),
    customer_risk_analytics AS (
        SELECT
            rga.final_order_id,
            rga.cleaned_sku,
            rga.merchant_id,
            rga.raw_total_foreign,
            rga.conversion_rate,
            rga.audit_status,
            cp.risk_score AS customer_base_risk,
            PERCENT_RANK() OVER (
                PARTITION BY rga.merchant_id
                ORDER BY COALESCE(cp.risk_score, 0) ASC
            ) AS merchant_risk_density,
            (
                SELECT GROUP_CONCAT(DISTINCT tl.merchant_category SEPARATOR '; ')
                FROM transaction_ledgers tl
                WHERE rga.db_customer_id = tl.user_id AND tl.status = 'FAILED'
            ) AS historic_failed_categories
        FROM reconciliation_gap_analysis rga
        LEFT JOIN customer_profiles cp ON rga.db_customer_id = cp.id
        WHERE rga.final_order_id IS NOT NULL
    )
    SELECT
        v_batch_id AS reconciliation_id,
        cra.merchant_id,
        cra.cleaned_sku AS sku_code,
        ROUND(cra.raw_total_foreign, 4) AS original_amount,
        ROUND((cra.raw_total_foreign * cra.conversion_rate), 4) AS settlement_amount,
        cra.conversion_rate AS fx_rate_applied,
        p_target_currency AS local_currency,
        CASE
            WHEN cra.audit_status = 'AMOUNT_MISMATCH' THEN 'CRITICAL_ERROR'
            WHEN cra.audit_status = 'MISSING_IN_DATABASE' THEN 'SECURITY_ALERT'
            WHEN cra.customer_base_risk > p_risk_threshold OR cra.merchant_risk_density > 0.85 THEN 'HIGH_RISK'
            WHEN cra.historic_failed_categories IS NOT NULL THEN 'MEDIUM_RISK'
            ELSE 'APPROVED'
        END AS risk_level,
        LOWER(CONCAT(
            'Status: ', cra.audit_status,
            ' | BaseRisk: ', COALESCE(cra.customer_base_risk, 'N/A'),
            ' | Density: ', ROUND(cra.merchant_risk_density, 4),
            ' | HistoricFailedCats: [', COALESCE(cra.historic_failed_categories, 'NONE'),
            '] | Checked at (UTC): ', UTC_TIMESTAMP()
        )) AS processing_notes
    FROM customer_risk_analytics cra,
    global_compliance_policies gcp
    WHERE gcp.region_code = 'GLOBAL'
      AND gcp.policy_status = 'ENFORCED'
      AND (cra.raw_total_foreign > 0 OR cra.audit_status != 'MATCHED')
    ORDER BY risk_level DESC, settlement_amount DESC;
END
-- relation-detector-fixture-end
