-- PostgreSQL business function case: nested CTEs, array unnesting, FULL/LEFT joins,
-- and a correlated subquery inside a returned set function.
-- relation-detector-fixture-source: FUNCTION:finance.sp_cross_border_reconciliation_engine
CREATE TYPE order_reconciliation_row AS (
    reconciliation_id   UUID,
    merchant_id         INT,
    sku_code            VARCHAR(50),
    original_amount     NUMERIC(16,4),
    settlement_amount   NUMERIC(16,4),
    fx_rate_applied     NUMERIC(12,6),
    local_currency      VARCHAR(10),
    risk_level          VARCHAR(20),
    processing_notes    TEXT
);

CREATE OR REPLACE FUNCTION sp_cross_border_reconciliation_engine(
    p_order_ids        UUID[],
    p_sku_codes        VARCHAR(50)[],
    p_quantities       INT[],
    p_base_prices      NUMERIC(16,4)[],
    p_target_currency  VARCHAR(10),
    p_risk_threshold   NUMERIC(5,2)
)
RETURNS SETOF order_reconciliation_row AS $$
DECLARE
    v_batch_id UUID := gen_random_uuid();
BEGIN
    IF CARDINALITY(p_order_ids) = 0 OR
       CARDINALITY(p_order_ids) != CARDINALITY(p_sku_codes) OR
       CARDINALITY(p_sku_codes) != CARDINALITY(p_quantities) OR
       CARDINALITY(p_quantities) != CARDINALITY(p_base_prices) THEN
        RAISE EXCEPTION 'Input arrays dimension mismatch! All parameter arrays must have identical length.';
    END IF;

    RETURN QUERY
    WITH RECURSIVE memory_input_cargo AS (
        SELECT
            o.id AS input_order_id,
            UPPER(REGEXP_REPLACE(s.sku, '[^a-zA-Z0-9_-]', '', 'g')) AS cleaned_sku,
            q.qty AS item_qty,
            p.price AS base_unit_price,
            o.idx AS array_position
        FROM unnest(p_order_ids) WITH ORDINALITY AS o(id, idx)
        JOIN unnest(p_sku_codes) WITH ORDINALITY AS s(sku, idx) ON o.idx = s.idx
        JOIN unnest(p_quantities) WITH ORDINALITY AS q(qty, idx) ON o.idx = q.idx
        JOIN unnest(p_base_prices) WITH ORDINALITY AS p(price, idx) ON o.idx = p.idx
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
            er.source_currency = ms.native_currency
            AND er.target_currency = p_target_currency
            AND er.effective_date = CURRENT_DATE
    ),
    reconciliation_gap_analysis AS (
        SELECT
            COALESCE(fx.input_order_id, db_o.id) AS final_order_id,
            fx.cleaned_sku,
            fx.merchant_id,
            fx.raw_total_foreign,
            fx.conversion_rate,
            db_o.payment_status AS db_payment_status,
            db_o.customer_id AS db_customer_id,
            CASE
                WHEN fx.input_order_id IS NULL THEN 'MISSING_IN_INPUT'
                WHEN db_o.id IS NULL THEN 'MISSING_IN_DATABASE'
                WHEN ABS((fx.raw_total_foreign * fx.conversion_rate) - db_o.pay_amount) > 0.01 THEN 'AMOUNT_MISMATCH'
                ELSE 'MATCHED'
            END AS audit_status
        FROM fx_valuation fx
        FULL OUTER JOIN orders db_o ON fx.input_order_id = db_o.id
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
                SELECT STRING_AGG(DISTINCT tl.merchant_category, '; ')
                FROM transaction_ledgers tl
                WHERE tl.user_id = rga.db_customer_id AND tl.status = 'FAILED'
            ) AS historic_failed_categories
        FROM reconciliation_gap_analysis rga
        LEFT JOIN customer_profiles cp ON rga.db_customer_id = cp.id
        WHERE rga.final_order_id IS NOT NULL
    )
    SELECT
        v_batch_id AS reconciliation_id,
        cra.merchant_id,
        cra.cleaned_sku AS sku_code,
        ROUND(cra.raw_total_foreign::numeric, 4) AS original_amount,
        ROUND((cra.raw_total_foreign * cra.conversion_rate)::numeric, 4) AS settlement_amount,
        cra.conversion_rate AS fx_rate_applied,
        p_target_currency AS local_currency,
        CASE
            WHEN cra.audit_status = 'AMOUNT_MISMATCH' THEN 'CRITICAL_ERROR'
            WHEN cra.audit_status = 'MISSING_IN_DATABASE' THEN 'SECURITY_ALERT'
            WHEN cra.customer_base_risk > p_risk_threshold OR cra.merchant_risk_density > 0.85 THEN 'HIGH_RISK'
            WHEN cra.historic_failed_categories IS NOT NULL THEN 'MEDIUM_RISK'
            ELSE 'APPROVED'
        END AS risk_level,
        LOWER(FORMAT('Status: %s | BaseRisk: %s | Density: %s | HistoricFailedCats: [%s] | Checked at: %s',
            cra.audit_status,
            COALESCE(cra.customer_base_risk::text, 'N/A'),
            ROUND(cra.merchant_risk_density::numeric, 4),
            COALESCE(cra.historic_failed_categories, 'NONE'),
            (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::text
        )) AS processing_notes
    FROM customer_risk_analytics cra, global_compliance_policies gcp
    WHERE gcp.region_code = 'GLOBAL'
      AND gcp.policy_status = 'ENFORCED'
      AND (cra.raw_total_foreign > 0 OR cra.audit_status != 'MATCHED')
    ORDER BY risk_level DESC, settlement_amount DESC;
END;
$$ LANGUAGE plpgsql;
-- relation-detector-fixture-end
