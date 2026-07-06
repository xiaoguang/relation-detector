-- PostgreSQL business function case: comma-rowset equivalent of the users join.
-- Expected fingerprints must match postgres-business-risk-settlement-function-sql.
-- relation-detector-fixture-source: FUNCTION:finance.fn_risk_settlement_engine_comma
CREATE OR REPLACE FUNCTION fn_risk_settlement_engine_comma(
    p_user_ids INT[],
    p_amounts NUMERIC[],
    p_risk_flags TEXT[]
)
RETURNS TABLE (
    result_user_id INT,
    country_code VARCHAR,
    total_inflow_amount NUMERIC,
    fraud_score_pct INT,
    all_triggered_categories TEXT,
    settlement_status TEXT
)
LANGUAGE plpgsql
AS $$
BEGIN
    CREATE TEMP TABLE IF NOT EXISTS temp_risk_inputs (
        idx INT GENERATED ALWAYS AS IDENTITY,
        user_id INT,
        raw_amount NUMERIC,
        flag_tag TEXT
    ) ON COMMIT DROP;

    INSERT INTO temp_risk_inputs (user_id, raw_amount, flag_tag)
    SELECT
        u_id,
        COALESCE(amt, 0.00),
        LOWER(TRIM(flag))
    FROM UNNEST(p_user_ids, p_amounts, p_risk_flags) AS arr(u_id, amt, flag);

    RETURN QUERY
    WITH cte_user_aggregated_metrics AS (
        SELECT
            tmp.user_id,
            COUNT(DISTINCT tmp.flag_tag) AS unique_flags,
            SUM(tmp.raw_amount) AS input_sum_amount,
            STRING_AGG(DISTINCT tmp.flag_tag, ' | ' ORDER BY tmp.flag_tag) AS flag_chain
        FROM temp_risk_inputs tmp
        GROUP BY tmp.user_id
        HAVING COUNT(tmp.idx) >= 1
    ),
    cte_fraud_scoring AS (
        SELECT
            m.user_id,
            u.country,
            m.input_sum_amount,
            m.flag_chain,
            NTILE(100) OVER (PARTITION BY u.country ORDER BY m.input_sum_amount DESC) AS risk_tile
        FROM cte_user_aggregated_metrics m, users u
        WHERE m.user_id = u.id
          AND u.is_active = true
    )
    SELECT
        fs.user_id,
        fs.country,
        ROUND(fs.input_sum_amount, 2) AS total_inflow_amount,
        fs.risk_tile AS fraud_score_pct,
        UPPER(fs.flag_chain) AS all_triggered_categories,
        CASE
            WHEN fs.risk_tile >= 90 THEN 'IMMEDIATE_BLOCK'
            WHEN fs.risk_tile >= 70 THEN 'HOLD_FOR_MANUAL_AUDIT'
            ELSE 'PROCESSED_WITH_WARNING'
        END AS settlement_status
    FROM cte_fraud_scoring fs
    ORDER BY fs.risk_tile DESC, fs.input_sum_amount DESC;
END;
$$;
-- relation-detector-fixture-end
