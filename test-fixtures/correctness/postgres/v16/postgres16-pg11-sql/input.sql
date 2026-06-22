-- ============================================================================
-- PostgreSQL 11 复杂SQL测试用例
-- 特性: 存储过程(CREATE PROCEDURE + 事务控制), 分区表增强(PRIMARY KEY,
--       DEFAULT分区, 自动索引), 哈希分区, 覆盖索引(INCLUDE)
-- 目标: 最大语法嵌套深度, 最全语法覆盖
-- ============================================================================

-- ============================================================================
-- Part 1: 存储过程 - 事务控制 + 深度嵌套SQL
-- ============================================================================

CREATE TABLE IF NOT EXISTS pg11_ledger (
    entry_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id INT NOT NULL,
    entry_type TEXT NOT NULL,
    amount NUMERIC(18,4) NOT NULL,
    currency TEXT NOT NULL DEFAULT 'CNY',
    entry_date DATE NOT NULL,
    description TEXT,
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
) PARTITION BY RANGE (entry_date);

CREATE TABLE pg11_ledger_2023 PARTITION OF pg11_ledger
    FOR VALUES FROM ('2023-01-01') TO ('2024-01-01');
CREATE TABLE pg11_ledger_2024 PARTITION OF pg11_ledger
    FOR VALUES FROM ('2024-01-01') TO ('2025-01-01');
CREATE TABLE pg11_ledger_default PARTITION OF pg11_ledger
    DEFAULT;

-- 复杂存储过程: 包含事务控制、异常处理、动态SQL、深度嵌套
CREATE OR REPLACE PROCEDURE pg11_complex_transfer(
    IN p_from_account INT,
    IN p_to_account INT,
    IN p_amount NUMERIC,
    IN p_currency TEXT DEFAULT 'CNY',
    IN p_description TEXT DEFAULT NULL,
    IN p_metadata JSONB DEFAULT '{}',
    OUT p_transfer_id BIGINT,
    OUT p_status TEXT,
    OUT p_audit_log JSONB
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_from_balance NUMERIC(18,4);
    v_to_balance NUMERIC(18,4);
    v_from_entry_id BIGINT;
    v_to_entry_id BIGINT;
    v_exchange_rate NUMERIC(12,6) := 1.0;
    v_converted_amount NUMERIC(18,4);
    v_risk_score INT;
    v_daily_total NUMERIC(18,4);
    v_audit_entries JSONB := '[]'::jsonb;
    v_retry_count INT := 0;
    v_max_retries INT := 3;
    v_batch_id UUID := gen_random_uuid();
BEGIN
    -- 参数验证
    IF p_amount <= 0 THEN
        RAISE EXCEPTION 'Transfer amount must be positive: %', p_amount
            USING HINT = 'Check the amount value',
                  ERRCODE = 'check_violation';
    END IF;

    IF p_from_account = p_to_account THEN
        RAISE EXCEPTION 'Cannot transfer to the same account: %', p_from_account
            USING ERRCODE = 'check_violation';
    END IF;

    -- 重试循环
    <<retry_block>>
    LOOP
        BEGIN
            -- 开始事务（PG11存储过程特有）
            -- 获取汇率
            SELECT COALESCE(
                (metadata->'exchange_rates'->>p_currency)::numeric,
                1.0
            ) INTO v_exchange_rate
            FROM pg11_ledger
            WHERE metadata->>'rate_type' = 'DAILY'
              AND entry_date = CURRENT_DATE
            LIMIT 1;

            v_converted_amount := round(p_amount * v_exchange_rate, 4);

            -- 风险评估
            SELECT
                COALESCE(sum(amount), 0),
                MAX(risk_score) INTO v_daily_total, v_risk_score
            FROM (
                SELECT amount,
                       (metadata->>'risk_score')::int AS risk_score
                FROM pg11_ledger
                WHERE account_id = p_from_account
                  AND entry_date = CURRENT_DATE
                  AND entry_type = 'DEBIT'
                UNION ALL
                SELECT p_amount, 0
            ) daily_agg;

            -- 借记分录
            INSERT INTO pg11_ledger (
                account_id, entry_type, amount, currency,
                entry_date, description, metadata
            ) VALUES (
                p_from_account, 'DEBIT', p_amount, p_currency,
                CURRENT_DATE, p_description,
                p_metadata || jsonb_build_object(
                    'transfer_id', v_batch_id,
                    'counterparty', p_to_account,
                    'risk_score', v_risk_score,
                    'daily_total', v_daily_total,
                    'exchange_rate', v_exchange_rate,
                    'converted_amount', v_converted_amount
                )
            ) RETURNING entry_id INTO v_from_entry_id;

            -- 贷记分录
            INSERT INTO pg11_ledger (
                account_id, entry_type, amount, currency,
                entry_date, description, metadata
            ) VALUES (
                p_to_account, 'CREDIT', v_converted_amount, p_currency,
                CURRENT_DATE, p_description,
                p_metadata || jsonb_build_object(
                    'transfer_id', v_batch_id,
                    'counterparty', p_from_account,
                    'original_amount', p_amount,
                    'exchange_rate', v_exchange_rate
                )
            ) RETURNING entry_id INTO v_to_entry_id;

            -- 审计日志
            v_audit_entries := v_audit_entries || jsonb_build_object(
                'attempt', v_retry_count + 1,
                'from_entry', v_from_entry_id,
                'to_entry', v_to_entry_id,
                'amount', p_amount,
                'converted', v_converted_amount,
                'rate', v_exchange_rate,
                'timestamp', now()
            );

            -- 更新账户余额（假设有账户表）
            UPDATE pg10_accounts
            SET balance = balance - p_amount,
                metadata = metadata || jsonb_build_object(
                    'last_debit', now(),
                    'last_transfer_id', v_batch_id
                )
            WHERE account_id = p_from_account;

            UPDATE pg10_accounts
            SET balance = balance + v_converted_amount,
                metadata = metadata || jsonb_build_object(
                    'last_credit', now(),
                    'last_transfer_id', v_batch_id
                )
            WHERE account_id = p_to_account;

            -- 成功退出
            p_transfer_id := v_from_entry_id;
            p_status := 'COMPLETED';
            p_audit_log := jsonb_build_object(
                'batch_id', v_batch_id,
                'status', 'SUCCESS',
                'from_entry', v_from_entry_id,
                'to_entry', v_to_entry_id,
                'amount', p_amount,
                'converted', v_converted_amount,
                'rate', v_exchange_rate,
                'attempts', v_retry_count + 1,
                'audit_trail', v_audit_entries
            );
            EXIT retry_block;

        EXCEPTION
            WHEN OTHERS THEN
                v_retry_count := v_retry_count + 1;
                v_audit_entries := v_audit_entries || jsonb_build_object(
                    'attempt', v_retry_count,
                    'error', SQLERRM,
                    'sqlstate', SQLSTATE,
                    'timestamp', now()
                );

                IF v_retry_count >= v_max_retries THEN
                    p_transfer_id := NULL;
                    p_status := 'FAILED';
                    p_audit_log := jsonb_build_object(
                        'batch_id', v_batch_id,
                        'status', 'FAILED',
                        'error', SQLERRM,
                        'sqlstate', SQLSTATE,
                        'attempts', v_retry_count,
                        'audit_trail', v_audit_entries
                    );
                    EXIT retry_block;
                END IF;

                -- 指数退避
                PERFORM pg_sleep(power(2, v_retry_count) * 0.1);
        END;
    END LOOP retry_block;
END;
$$;

-- 调用存储过程
CALL pg11_complex_transfer(
    1000001, 1000002, 5000.00, 'CNY',
    '跨账户转账测试',
    '{"channel": "MOBILE", "ip": "192.168.1.1"}',
    NULL, NULL, NULL
);

-- ============================================================================
-- Part 2: 覆盖索引(INCLUDE) + 复杂分析查询
-- ============================================================================

CREATE TABLE IF NOT EXISTS pg11_sensor_readings (
    reading_id BIGINT GENERATED ALWAYS AS IDENTITY,
    sensor_id INT NOT NULL,
    metric_name TEXT NOT NULL,
    metric_value DOUBLE PRECISION NOT NULL,
    unit TEXT NOT NULL,
    quality TEXT NOT NULL DEFAULT 'GOOD',
    tags TEXT[] NOT NULL DEFAULT '{}',
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    metadata JSONB DEFAULT '{}',
    PRIMARY KEY (reading_id, recorded_at)
) PARTITION BY RANGE (recorded_at);

-- 创建覆盖索引 (PG11 INCLUDE特性)
CREATE INDEX idx_sensor_metric_covering ON pg11_sensor_readings (
    sensor_id, metric_name, recorded_at DESC
) INCLUDE (metric_value, quality, unit);

-- 复杂传感器分析查询
WITH RECURSIVE
-- 递归CTE: 时间窗口生成
time_windows AS (
    SELECT
        date_trunc('hour', now()) - INTERVAL '168 hours' AS window_start,
        date_trunc('hour', now()) - INTERVAL '167 hours' AS window_end,
        0 AS window_id
    UNION ALL
    SELECT
        tw.window_end,
        tw.window_end + INTERVAL '1 hour',
        tw.window_id + 1
    FROM time_windows tw
    WHERE tw.window_end < date_trunc('hour', now())
),
-- 传感器数据聚合
sensor_hourly AS (
    SELECT
        sr.sensor_id,
        sr.metric_name,
        tw.window_id,
        tw.window_start,
        tw.window_end,
        count(*) AS reading_count,
        avg(sr.metric_value) AS avg_value,
        stddev(sr.metric_value) AS stddev_value,
        min(sr.metric_value) AS min_value,
        max(sr.metric_value) AS max_value,
        percentile_cont(0.25) WITHIN GROUP (ORDER BY sr.metric_value) AS p25,
        percentile_cont(0.50) WITHIN GROUP (ORDER BY sr.metric_value) AS p50,
        percentile_cont(0.75) WITHIN GROUP (ORDER BY sr.metric_value) AS p75,
        percentile_cont(0.95) WITHIN GROUP (ORDER BY sr.metric_value) AS p95,
        count(*) FILTER (WHERE sr.quality = 'BAD') AS bad_readings,
        count(*) FILTER (WHERE sr.quality = 'SUSPECT') AS suspect_readings,
        array_agg(DISTINCT sr.unit ORDER BY sr.unit) AS units,
        array_agg(DISTINCT tag ORDER BY tag)
            FILTER (WHERE sr.tags IS NOT NULL) AS all_tags
    FROM time_windows tw
    LEFT JOIN pg11_sensor_readings sr
        ON sr.recorded_at >= tw.window_start
       AND sr.recorded_at < tw.window_end
       AND sr.metric_name IS NOT NULL
    GROUP BY
        sr.sensor_id, sr.metric_name,
        tw.window_id, tw.window_start, tw.window_end
),
-- 异常检测
anomaly_scores AS (
    SELECT
        sh.*,
        -- 计算z-score
        CASE
            WHEN sh.stddev_value > 0
            THEN (sh.avg_value - avg(sh.avg_value) OVER (
                PARTITION BY sh.sensor_id, sh.metric_name
                ORDER BY sh.window_id
                ROWS BETWEEN 24 PRECEDING AND 24 FOLLOWING
            )) / NULLIF(stddev(sh.avg_value) OVER (
                PARTITION BY sh.sensor_id, sh.metric_name
                ORDER BY sh.window_id
                ROWS BETWEEN 24 PRECEDING AND 24 FOLLOWING
            ), 0)
            ELSE 0
        END AS z_score,
        -- 趋势检测
        avg(sh.avg_value) OVER (
            PARTITION BY sh.sensor_id, sh.metric_name
            ORDER BY sh.window_id
            ROWS BETWEEN 6 PRECEDING AND CURRENT ROW
        ) AS short_term_avg,
        avg(sh.avg_value) OVER (
            PARTITION BY sh.sensor_id, sh.metric_name
            ORDER BY sh.window_id
            ROWS BETWEEN 24 PRECEDING AND CURRENT ROW
        ) AS long_term_avg,
        -- 变化率
        (sh.avg_value - lag(sh.avg_value, 1) OVER (
            PARTITION BY sh.sensor_id, sh.metric_name
            ORDER BY sh.window_id
        )) / NULLIF(lag(sh.avg_value, 1) OVER (
            PARTITION BY sh.sensor_id, sh.metric_name
            ORDER BY sh.window_id
        ), 0) * 100 AS pct_change
    FROM sensor_hourly sh
    WHERE sh.reading_count > 0
),
-- 持续性分析
persistence_analysis AS (
    SELECT
        ascores.*,
        count(*) FILTER (WHERE ascores.z_score > 2) OVER (
            PARTITION BY ascores.sensor_id, ascores.metric_name
            ORDER BY ascores.window_id
            ROWS BETWEEN 5 PRECEDING AND CURRENT ROW
        ) AS consecutive_high_z,
        count(*) FILTER (WHERE ascores.pct_change > 20) OVER (
            PARTITION BY ascores.sensor_id, ascores.metric_name
            ORDER BY ascores.window_id
            ROWS BETWEEN 3 PRECEDING AND CURRENT ROW
        ) AS consecutive_spikes,
        -- 关联分析
        corr(ascores.avg_value, ascores.bad_readings) OVER (
            PARTITION BY ascores.sensor_id, ascores.metric_name
        ) AS quality_value_correlation
    FROM anomaly_scores ascores
),
-- 分类
classified AS (
    SELECT
        pa.*,
        CASE
            WHEN pa.consecutive_high_z >= 5 AND pa.consecutive_spikes >= 3
                THEN 'CRITICAL_ANOMALY'
            WHEN pa.consecutive_high_z >= 3
                THEN 'WARNING_ANOMALY'
            WHEN abs(pa.z_score) > 3
                THEN 'SINGLE_ANOMALY'
            WHEN abs(pa.pct_change) > 50
                THEN 'SUDDEN_CHANGE'
            WHEN pa.bad_readings > pa.reading_count * 0.3
                THEN 'QUALITY_ISSUE'
            ELSE 'NORMAL'
        END AS classification,
        CASE
            WHEN pa.short_term_avg > pa.long_term_avg * 1.2 THEN 'RISING'
            WHEN pa.short_term_avg < pa.long_term_avg * 0.8 THEN 'FALLING'
            ELSE 'STABLE'
        END AS trend
    FROM persistence_analysis pa
)
-- 主查询
SELECT
    c.sensor_id,
    c.metric_name,
    c.window_start,
    c.window_end,
    c.reading_count,
    round(c.avg_value::numeric, 4) AS avg_value,
    round(c.stddev_value::numeric, 4) AS stddev,
    round(c.z_score::numeric, 4) AS z_score,
    round(c.pct_change::numeric, 2) AS pct_change,
    c.classification,
    c.trend,
    c.consecutive_high_z,
    c.consecutive_spikes,
    round(c.quality_value_correlation::numeric, 4) AS quality_correlation,
    c.bad_readings,
    c.suspect_readings,
    c.all_tags,
    (
        SELECT jsonb_build_object(
            'p25', round(c.p25::numeric, 4),
            'p50', round(c.p50::numeric, 4),
            'p75', round(c.p75::numeric, 4),
            'p95', round(c.p95::numeric, 4),
            'iqr', round((c.p75 - c.p25)::numeric, 4),
            'outlier_threshold_upper', round((c.p75 + 1.5 * (c.p75 - c.p25))::numeric, 4),
            'outlier_threshold_lower', round((c.p25 - 1.5 * (c.p75 - c.p25))::numeric, 4)
        )
    ) AS distribution_stats,
    (
        SELECT jsonb_agg(
            jsonb_build_object(
                'window', c2.window_start::text,
                'value', round(c2.avg_value::numeric, 4),
                'z_score', round(c2.z_score::numeric, 4),
                'classification', c2.classification
            )
            ORDER BY c2.window_start
        )
        FROM classified c2
        WHERE c2.sensor_id = c.sensor_id
          AND c2.metric_name = c.metric_name
          AND c2.classification != 'NORMAL'
        LIMIT 20
    ) AS recent_anomalies
FROM classified c
WHERE c.classification != 'NORMAL'
   OR c.trend != 'STABLE'
ORDER BY
    CASE c.classification
        WHEN 'CRITICAL_ANOMALY' THEN 1
        WHEN 'WARNING_ANOMALY' THEN 2
        WHEN 'SINGLE_ANOMALY' THEN 3
        WHEN 'SUDDEN_CHANGE' THEN 4
        WHEN 'QUALITY_ISSUE' THEN 5
        ELSE 6
    END,
    c.sensor_id,
    c.window_start;

-- ============================================================================
-- Part 3: 哈希分区 + 并行聚合
-- ============================================================================

CREATE TABLE IF NOT EXISTS pg11_events_hash (
    event_id BIGINT GENERATED ALWAYS AS IDENTITY,
    tenant_id INT NOT NULL,
    event_type TEXT NOT NULL,
    event_data JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
) PARTITION BY HASH (tenant_id);

CREATE TABLE pg11_events_h0 PARTITION OF pg11_events_hash
    FOR VALUES WITH (MODULUS 16, REMAINDER 0);
CREATE TABLE pg11_events_h1 PARTITION OF pg11_events_hash
    FOR VALUES WITH (MODULUS 16, REMAINDER 1);
CREATE TABLE pg11_events_h2 PARTITION OF pg11_events_hash
    FOR VALUES WITH (MODULUS 16, REMAINDER 2);
CREATE TABLE pg11_events_h3 PARTITION OF pg11_events_hash
    FOR VALUES WITH (MODULUS 16, REMAINDER 3);
CREATE TABLE pg11_events_h4 PARTITION OF pg11_events_hash
    FOR VALUES WITH (MODULUS 16, REMAINDER 4);
CREATE TABLE pg11_events_h5 PARTITION OF pg11_events_hash
    FOR VALUES WITH (MODULUS 16, REMAINDER 5);
CREATE TABLE pg11_events_h6 PARTITION OF pg11_events_hash
    FOR VALUES WITH (MODULUS 16, REMAINDER 6);
CREATE TABLE pg11_events_h7 PARTITION OF pg11_events_hash
    FOR VALUES WITH (MODULUS 16, REMAINDER 7);
CREATE TABLE pg11_events_h8 PARTITION OF pg11_events_hash
    FOR VALUES WITH (MODULUS 16, REMAINDER 8);
CREATE TABLE pg11_events_h9 PARTITION OF pg11_events_hash
    FOR VALUES WITH (MODULUS 16, REMAINDER 9);
CREATE TABLE pg11_events_h10 PARTITION OF pg11_events_hash
    FOR VALUES WITH (MODULUS 16, REMAINDER 10);
CREATE TABLE pg11_events_h11 PARTITION OF pg11_events_hash
    FOR VALUES WITH (MODULUS 16, REMAINDER 11);
CREATE TABLE pg11_events_h12 PARTITION OF pg11_events_hash
    FOR VALUES WITH (MODULUS 16, REMAINDER 12);
CREATE TABLE pg11_events_h13 PARTITION OF pg11_events_hash
    FOR VALUES WITH (MODULUS 16, REMAINDER 13);
CREATE TABLE pg11_events_h14 PARTITION OF pg11_events_hash
    FOR VALUES WITH (MODULUS 16, REMAINDER 14);
CREATE TABLE pg11_events_h15 PARTITION OF pg11_events_hash
    FOR VALUES WITH (MODULUS 16, REMAINDER 15);

-- 跨哈希分区并行查询
WITH
partition_info AS (
    SELECT
        tableoid::regclass AS partition_name,
        tenant_id,
        event_type,
        count(*) AS event_count,
        min(created_at) AS earliest,
        max(created_at) AS latest
    FROM pg11_events_hash
    WHERE created_at >= CURRENT_TIMESTAMP - INTERVAL '7 days'
    GROUP BY tableoid, tenant_id, event_type
),
cross_partition_stats AS (
    SELECT
        pi.tenant_id,
        pi.event_type,
        count(DISTINCT pi.partition_name) AS partitions_touched,
        sum(pi.event_count) AS total_events,
        min(pi.earliest) AS first_event,
        max(pi.latest) AS last_event,
        avg(pi.event_count) AS avg_events_per_partition,
        stddev(pi.event_count) AS stddev_events_per_partition,
        array_agg(
            DISTINCT pi.partition_name::text
            ORDER BY pi.partition_name::text
        ) AS partition_list,
        jsonb_agg(
            jsonb_build_object(
                'partition', pi.partition_name::text,
                'count', pi.event_count,
                'earliest', pi.earliest,
                'latest', pi.latest
            )
            ORDER BY pi.partition_name::text
        ) AS partition_details
    FROM partition_info pi
    GROUP BY pi.tenant_id, pi.event_type
)
SELECT
    cps.tenant_id,
    cps.event_type,
    cps.partitions_touched,
    cps.total_events,
    cps.first_event,
    cps.last_event,
    round(cps.avg_events_per_partition::numeric, 2) AS avg_per_partition,
    round(cps.stddev_events_per_partition::numeric, 2) AS stddev_per_partition,
    cps.partition_details,
    CASE
        WHEN cps.stddev_events_per_partition > cps.avg_events_per_partition * 0.5
        THEN 'SKEWED_DISTRIBUTION'
        WHEN cps.partitions_touched > 10
        THEN 'HIGHLY_DISTRIBUTED'
        WHEN cps.partitions_touched > 5
        THEN 'MODERATELY_DISTRIBUTED'
        ELSE 'CONCENTRATED'
    END AS distribution_pattern,
    (
        SELECT jsonb_build_object(
            'total_partitions', count(DISTINCT tableoid::regclass),
            'total_events', count(*),
            'active_tenants', count(DISTINCT tenant_id),
            'active_event_types', count(DISTINCT event_type)
        )
        FROM pg11_events_hash
        WHERE created_at >= CURRENT_TIMESTAMP - INTERVAL '7 days'
    ) AS global_stats
FROM cross_partition_stats cps
ORDER BY cps.total_events DESC;