-- ============================================================================
-- PostgreSQL 10 复杂SQL测试用例
-- 特性: 声明式分区, Identity列, 逻辑复制, 并行查询增强, 哈希索引
-- 目标: 最大语法嵌套深度, 最全语法覆盖
-- ============================================================================

-- ============================================================================
-- Part 1: 声明式分区 - 多层分区 + 子分区
-- ============================================================================

CREATE TABLE IF NOT EXISTS pg10_transactions (
    txn_id BIGINT NOT NULL,
    account_id INT NOT NULL,
    txn_type TEXT NOT NULL,
    amount NUMERIC(18,4) NOT NULL,
    currency TEXT NOT NULL DEFAULT 'CNY',
    txn_date DATE NOT NULL,
    posted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    metadata JSONB,
    PRIMARY KEY (txn_id, txn_date)
) PARTITION BY RANGE (txn_date);

-- 按年份创建主分区
CREATE TABLE pg10_txn_2022 PARTITION OF pg10_transactions
    FOR VALUES FROM ('2022-01-01') TO ('2023-01-01')
    PARTITION BY LIST (txn_type);

CREATE TABLE pg10_txn_2023 PARTITION OF pg10_transactions
    FOR VALUES FROM ('2023-01-01') TO ('2024-01-01')
    PARTITION BY LIST (txn_type);

CREATE TABLE pg10_txn_2024 PARTITION OF pg10_transactions
    FOR VALUES FROM ('2024-01-01') TO ('2025-01-01')
    PARTITION BY LIST (txn_type);

-- 2022年子分区
CREATE TABLE pg10_txn_2022_credit PARTITION OF pg10_txn_2022
    FOR VALUES IN ('CREDIT', 'CREDIT_ADJUSTMENT', 'CREDIT_REVERSAL');
CREATE TABLE pg10_txn_2022_debit PARTITION OF pg10_txn_2022
    FOR VALUES IN ('DEBIT', 'DEBIT_ADJUSTMENT', 'DEBIT_REVERSAL');
CREATE TABLE pg10_txn_2022_transfer PARTITION OF pg10_txn_2022
    FOR VALUES IN ('INTERNAL_TRANSFER', 'EXTERNAL_TRANSFER', 'WIRE_TRANSFER');
CREATE TABLE pg10_txn_2022_other PARTITION OF pg10_txn_2022
    DEFAULT;

-- 2023年子分区
CREATE TABLE pg10_txn_2023_credit PARTITION OF pg10_txn_2023
    FOR VALUES IN ('CREDIT', 'CREDIT_ADJUSTMENT', 'CREDIT_REVERSAL');
CREATE TABLE pg10_txn_2023_debit PARTITION OF pg10_txn_2023
    FOR VALUES IN ('DEBIT', 'DEBIT_ADJUSTMENT', 'DEBIT_REVERSAL');
CREATE TABLE pg10_txn_2023_transfer PARTITION OF pg10_txn_2023
    FOR VALUES IN ('INTERNAL_TRANSFER', 'EXTERNAL_TRANSFER', 'WIRE_TRANSFER');
CREATE TABLE pg10_txn_2023_other PARTITION OF pg10_txn_2023
    DEFAULT;

-- ============================================================================
-- Part 2: Identity列 + 复杂跨分区查询
-- ============================================================================

CREATE TABLE IF NOT EXISTS pg10_accounts (
    account_id INT GENERATED ALWAYS AS IDENTITY (START WITH 1000000 INCREMENT BY 1) PRIMARY KEY,
    account_number TEXT GENERATED ALWAYS AS (
        'ACCT-' || lpad(account_id::text, 10, '0')
    ) STORED,
    customer_name TEXT NOT NULL,
    account_type TEXT NOT NULL,
    opened_date DATE NOT NULL DEFAULT CURRENT_DATE,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    balance NUMERIC(18,4) NOT NULL DEFAULT 0,
    credit_limit NUMERIC(18,4),
    interest_rate NUMERIC(6,4) DEFAULT 0.0000,
    branch_code TEXT NOT NULL,
    risk_score INT GENERATED ALWAYS AS (
        CASE
            WHEN balance > 1000000 THEN 1
            WHEN balance > 100000 THEN 2
            WHEN balance > 10000 THEN 3
            ELSE 4
        END
    ) STORED,
    metadata JSONB
);

-- 跨分区复杂查询
WITH RECURSIVE
-- 递归CTE: 账户层级关系
account_hierarchy AS (
    SELECT
        a.account_id,
        a.customer_name,
        a.account_type,
        a.balance,
        a.risk_score,
        NULL::INT AS parent_account_id,
        0 AS depth_level,
        ARRAY[a.account_id] AS hierarchy_path,
        a.account_id::text AS path_display
    FROM pg10_accounts a
    WHERE NOT (a.metadata ? 'parent_account_id')
    UNION ALL
    SELECT
        child.account_id,
        child.customer_name,
        child.account_type,
        child.balance,
        child.risk_score,
        (child.metadata->>'parent_account_id')::INT,
        ah.depth_level + 1,
        ah.hierarchy_path || child.account_id,
        ah.path_display || ' -> ' || child.account_id::text
    FROM pg10_accounts child
    JOIN account_hierarchy ah
        ON (child.metadata->>'parent_account_id')::INT = ah.account_id
    WHERE NOT child.account_id = ANY(ah.hierarchy_path)
      AND ah.depth_level < 10
),
-- 第二层CTE: 交易汇总（跨所有分区）
account_transactions AS (
    SELECT
        ah.account_id,
        ah.customer_name,
        ah.depth_level,
        ah.path_display,
        ah.hierarchy_path,
        count(DISTINCT t.txn_id) AS total_txns,
        count(DISTINCT t.txn_id) FILTER (WHERE t.txn_type LIKE 'CREDIT%') AS credit_txns,
        count(DISTINCT t.txn_id) FILTER (WHERE t.txn_type LIKE 'DEBIT%') AS debit_txns,
        sum(t.amount) FILTER (WHERE t.txn_type LIKE 'CREDIT%') AS total_credits,
        sum(t.amount) FILTER (WHERE t.txn_type LIKE 'DEBIT%') AS total_debits,
        sum(t.amount) FILTER (WHERE t.txn_type LIKE 'CREDIT%') -
        sum(t.amount) FILTER (WHERE t.txn_type LIKE 'DEBIT%') AS net_flow,
        avg(t.amount) AS avg_txn_amount,
        stddev(t.amount) AS stddev_txn_amount,
        count(DISTINCT t.txn_date) AS active_days,
        array_agg(DISTINCT t.currency ORDER BY t.currency) AS currencies_used,
        min(t.txn_date) AS first_txn_date,
        max(t.txn_date) AS last_txn_date
    FROM account_hierarchy ah
    LEFT JOIN pg10_transactions t
        ON ah.account_id = t.account_id
       AND t.txn_date >= CURRENT_DATE - INTERVAL '2 years'
    GROUP BY
        ah.account_id, ah.customer_name, ah.depth_level,
        ah.path_display, ah.hierarchy_path
),
-- 第三层CTE: 滚动窗口分析
rolling_analysis AS (
    SELECT
        at.*,
        -- 账户内排名
        row_number() OVER (
            PARTITION BY at.depth_level
            ORDER BY at.net_flow DESC
        ) AS flow_rank_in_level,
        -- 累计
        sum(at.net_flow) OVER (
            PARTITION BY at.depth_level
            ORDER BY at.net_flow DESC
            ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
        ) AS cumulative_net_flow,
        -- 移动平均
        avg(at.net_flow) OVER (
            ORDER BY at.total_txns
            ROWS BETWEEN 5 PRECEDING AND 5 FOLLOWING
        ) AS moving_avg_net_flow,
        -- 百分位
        percent_rank() OVER (ORDER BY at.total_txns) AS txn_percentile,
        cume_dist() OVER (ORDER BY abs(at.net_flow)) AS net_flow_cume_dist,
        -- 统计
        at.net_flow - avg(at.net_flow) OVER () AS deviation_from_mean,
        (at.net_flow - avg(at.net_flow) OVER ()) /
            NULLIF(stddev(at.net_flow) OVER (), 0) AS z_score,
        -- 分组
        ntile(10) OVER (ORDER BY at.total_txns DESC) AS activity_decile
    FROM account_transactions at
),
-- 第四层CTE: 异常检测
anomaly_detection AS (
    SELECT
        ra.*,
        CASE
            WHEN abs(ra.z_score) > 3 THEN 'EXTREME_OUTLIER'
            WHEN abs(ra.z_score) > 2 THEN 'MODERATE_OUTLIER'
            WHEN abs(ra.z_score) > 1 THEN 'MILD_OUTLIER'
            ELSE 'NORMAL'
        END AS anomaly_level,
        CASE
            WHEN ra.net_flow > 0 AND ra.flow_rank_in_level <= 5
                THEN 'TOP_INFLOW'
            WHEN ra.net_flow < 0 AND ra.flow_rank_in_level <= 5
                THEN 'TOP_OUTFLOW'
            WHEN ra.total_txns = 0 THEN 'DORMANT'
            WHEN ra.activity_decile <= 3 THEN 'HIGH_ACTIVITY'
            WHEN ra.activity_decile >= 8 THEN 'LOW_ACTIVITY'
            ELSE 'NORMAL_ACTIVITY'
        END AS account_behavior
    FROM rolling_analysis ra
),
-- 第五层CTE: 层级聚合
hierarchy_aggregation AS (
    SELECT
        ad.depth_level,
        ad.account_behavior,
        ad.anomaly_level,
        count(*) AS account_count,
        sum(ad.total_txns) AS total_txns,
        sum(ad.net_flow) AS total_net_flow,
        round(avg(ad.avg_txn_amount)::numeric, 4) AS avg_txn_amount,
        round(avg(ad.z_score)::numeric, 4) AS avg_z_score,
        array_agg(
            DISTINCT jsonb_build_object(
                'account_id', ad.account_id,
                'name', ad.customer_name,
                'net_flow', ad.net_flow,
                'txns', ad.total_txns,
                'z_score', round(ad.z_score::numeric, 4),
                'path', ad.path_display
            )
            ORDER BY ad.net_flow DESC
        ) FILTER (WHERE ad.anomaly_level = 'EXTREME_OUTLIER') AS extreme_accounts,
        -- 使用JSON聚合
        jsonb_object_agg(
            ad.anomaly_level,
            count(*)::text
        ) AS anomaly_distribution,
        -- 路径分析
        string_agg(
            DISTINCT ad.path_display,
            CHR(10)
            ORDER BY ad.path_display
        ) FILTER (WHERE ad.depth_level > 2) AS deep_hierarchy_paths
    FROM anomaly_detection ad
    GROUP BY
        GROUPING SETS (
            (ad.depth_level, ad.account_behavior, ad.anomaly_level),
            (ad.depth_level, ad.account_behavior),
            (ad.depth_level),
            ()
        )
)
-- 主查询
SELECT
    COALESCE(ha.depth_level::text, 'ALL') AS depth,
    COALESCE(ha.account_behavior, 'ALL') AS behavior,
    COALESCE(ha.anomaly_level, 'ALL') AS anomaly,
    ha.account_count,
    ha.total_txns,
    ha.total_net_flow,
    ha.avg_txn_amount,
    ha.avg_z_score,
    ha.anomaly_distribution,
    ha.extreme_accounts,
    ha.deep_hierarchy_paths,
    -- 嵌套子查询
    (
        SELECT jsonb_build_object(
            'total_accounts', sum(ha2.account_count),
            'total_net_flow', sum(ha2.total_net_flow),
            'extreme_count', sum(ha2.account_count)
                FILTER (WHERE ha2.anomaly_level = 'EXTREME_OUTLIER'),
            'dormant_count', sum(ha2.account_count)
                FILTER (WHERE ha2.account_behavior = 'DORMANT'),
            'top_inflow_count', sum(ha2.account_count)
                FILTER (WHERE ha2.account_behavior = 'TOP_INFLOW')
        )
        FROM hierarchy_aggregation ha2
        WHERE ha2.depth_level = ha.depth_level
    ) AS level_summary
FROM hierarchy_aggregation ha
ORDER BY
    ha.depth_level NULLS LAST,
    ha.account_behavior NULLS LAST,
    ha.anomaly_level NULLS LAST;

-- ============================================================================
-- Part 3: 极致子查询嵌套 - 跨分区并行查询
-- ============================================================================

SELECT
    outer_a.account_id,
    outer_a.customer_name,
    outer_a.balance,
    outer_a.risk_score,
    (
        -- 第1层
        SELECT jsonb_build_object(
            'current_year', (
                -- 第2层
                SELECT jsonb_build_object(
                    'total_credits', (
                        -- 第3层
                        SELECT coalesce(sum(t.amount), 0)
                        FROM pg10_transactions t
                        WHERE t.account_id = outer_a.account_id
                          AND t.txn_type LIKE 'CREDIT%'
                          AND t.txn_date >= '2024-01-01'
                    ),
                    'total_debits', (
                        -- 第3层
                        SELECT coalesce(sum(t.amount), 0)
                        FROM pg10_transactions t
                        WHERE t.account_id = outer_a.account_id
                          AND t.txn_type LIKE 'DEBIT%'
                          AND t.txn_date >= '2024-01-01'
                    ),
                    'largest_txn', (
                        -- 第3层
                        SELECT jsonb_build_object(
                            'amount', t.amount,
                            'type', t.txn_type,
                            'date', t.txn_date,
                            'currency', t.currency,
                            'metadata', t.metadata
                        )
                        FROM pg10_transactions t
                        WHERE t.account_id = outer_a.account_id
                          AND t.txn_date >= '2024-01-01'
                        ORDER BY t.amount DESC
                        LIMIT 1
                    ),
                    'daily_breakdown', (
                        -- 第3层
                        SELECT jsonb_object_agg(
                            txn_date::text,
                            jsonb_build_object(
                                'count', cnt,
                                'total', total,
                                'net', net
                            )
                        )
                        FROM (
                            -- 第4层
                            SELECT
                                t.txn_date,
                                count(*) AS cnt,
                                sum(t.amount) AS total,
                                sum(t.amount) FILTER (WHERE t.txn_type LIKE 'CREDIT%') -
                                sum(t.amount) FILTER (WHERE t.txn_type LIKE 'DEBIT%') AS net
                            FROM pg10_transactions t
                            WHERE t.account_id = outer_a.account_id
                              AND t.txn_date >= '2024-01-01'
                            GROUP BY t.txn_date
                            HAVING count(*) > 1
                            ORDER BY t.txn_date
                        ) daily
                    ),
                    'peer_comparison', (
                        -- 第3层
                        SELECT jsonb_build_object(
                            'peer_avg_balance', (
                                -- 第4层
                                SELECT round(avg(a2.balance)::numeric, 2)
                                FROM pg10_accounts a2
                                WHERE a2.risk_score = outer_a.risk_score
                                  AND a2.account_type = outer_a.account_type
                                  AND a2.account_id != outer_a.account_id
                            ),
                            'peer_avg_txns', (
                                -- 第4层
                                SELECT round(avg(txn_count)::numeric, 2)
                                FROM (
                                    -- 第5层
                                    SELECT count(*) AS txn_count
                                    FROM pg10_transactions t2
                                    WHERE t2.account_id IN (
                                        -- 第6层
                                        SELECT a3.account_id
                                        FROM pg10_accounts a3
                                        WHERE a3.risk_score = outer_a.risk_score
                                          AND a3.account_type = outer_a.account_type
                                          AND a3.account_id != outer_a.account_id
                                    )
                                    AND t2.txn_date >= '2024-01-01'
                                    GROUP BY t2.account_id
                                ) peer_txns
                            ),
                            'percentile_in_peer', (
                                -- 第4层
                                SELECT round(
                                    (count(*) FILTER (
                                        WHERE a4.balance <= outer_a.balance
                                    )::numeric / NULLIF(count(*), 0) * 100)::numeric, 2
                                )
                                FROM pg10_accounts a4
                                WHERE a4.risk_score = outer_a.risk_score
                                  AND a4.account_type = outer_a.account_type
                            )
                        )
                    ),
                    'related_accounts', (
                        -- 第3层
                        SELECT jsonb_agg(
                            jsonb_build_object(
                                'account_id', related.account_id,
                                'name', related.customer_name,
                                'shared_txns', (
                                    -- 第4层
                                    SELECT count(DISTINCT t3.txn_date)
                                    FROM pg10_transactions t3
                                    WHERE t3.account_id = outer_a.account_id
                                    INTERSECT
                                    SELECT count(DISTINCT t4.txn_date)
                                    FROM pg10_transactions t4
                                    WHERE t4.account_id = related.account_id
                                )
                            )
                        )
                        FROM pg10_accounts related
                        WHERE related.account_id != outer_a.account_id
                          AND related.metadata->>'linked_to' = outer_a.account_id::text
                        LIMIT 10
                    )
                )
            ),
            'previous_year', (
                -- 第2层
                SELECT jsonb_build_object(
                    'total_credits', (
                        SELECT coalesce(sum(t.amount), 0)
                        FROM pg10_transactions t
                        WHERE t.account_id = outer_a.account_id
                          AND t.txn_type LIKE 'CREDIT%'
                          AND t.txn_date BETWEEN '2023-01-01' AND '2023-12-31'
                    ),
                    'total_debits', (
                        SELECT coalesce(sum(t.amount), 0)
                        FROM pg10_transactions t
                        WHERE t.account_id = outer_a.account_id
                          AND t.txn_type LIKE 'DEBIT%'
                          AND t.txn_date BETWEEN '2023-01-01' AND '2023-12-31'
                    )
                )
            ),
            'yoy_change', (
                -- 第2层
                SELECT jsonb_build_object(
                    'credit_change_pct', (
                        SELECT round(
                            ((coalesce(
                                (SELECT sum(t.amount) FROM pg10_transactions t
                                 WHERE t.account_id = outer_a.account_id
                                   AND t.txn_type LIKE 'CREDIT%'
                                   AND t.txn_date >= '2024-01-01'), 0) -
                              coalesce(
                                (SELECT sum(t.amount) FROM pg10_transactions t
                                 WHERE t.account_id = outer_a.account_id
                                   AND t.txn_type LIKE 'CREDIT%'
                                   AND t.txn_date BETWEEN '2023-01-01' AND '2023-12-31'), 0)) /
                             NULLIF(
                                (SELECT sum(t.amount) FROM pg10_transactions t
                                 WHERE t.account_id = outer_a.account_id
                                   AND t.txn_type LIKE 'CREDIT%'
                                   AND t.txn_date BETWEEN '2023-01-01' AND '2023-12-31'), 0) * 100
                            )::numeric, 2
                        )
                    )
                )
            )
        )
    ) AS account_analysis
FROM pg10_accounts outer_a
WHERE outer_a.status = 'ACTIVE'
  AND EXISTS (
    SELECT 1 FROM pg10_transactions t
    WHERE t.account_id = outer_a.account_id
      AND t.txn_date >= '2024-01-01'
      AND t.amount > 10000
  )
ORDER BY outer_a.balance DESC
LIMIT 100;

-- ============================================================================
-- Part 4: 复杂聚合 - FILTER + GROUPING SETS + 窗口函数深度组合
-- ============================================================================

WITH
daily_stats AS (
    SELECT
        t.txn_date,
        t.txn_type,
        t.currency,
        t.account_id,
        a.account_type,
        a.branch_code,
        a.risk_score,
        count(*) AS txn_count,
        sum(t.amount) AS total_amount,
        avg(t.amount) AS avg_amount,
        min(t.amount) AS min_amount,
        max(t.amount) AS max_amount
    FROM pg10_transactions t
    JOIN pg10_accounts a ON t.account_id = a.account_id
    WHERE t.txn_date >= CURRENT_DATE - INTERVAL '90 days'
    GROUP BY
        t.txn_date, t.txn_type, t.currency,
        t.account_id, a.account_type, a.branch_code, a.risk_score
),
multi_dim_agg AS (
    SELECT
        ds.txn_date,
        ds.txn_type,
        ds.currency,
        ds.account_type,
        ds.branch_code,
        ds.risk_score,
        sum(ds.txn_count) AS total_txns,
        sum(ds.total_amount) AS total_amount,
        round(avg(ds.avg_amount)::numeric, 4) AS avg_amount,
        count(DISTINCT ds.account_id) AS unique_accounts,
        array_agg(DISTINCT ds.account_id ORDER BY ds.account_id)
            FILTER (WHERE ds.total_amount > 50000) AS high_value_accounts,
        string_agg(
            DISTINCT ds.currency,
            ', ' ORDER BY ds.currency
        ) FILTER (WHERE ds.total_amount > 100000) AS major_currencies,
        jsonb_object_agg(
            COALESCE(ds.currency, 'unknown'),
            jsonb_build_object(
                'count', sum(ds.txn_count),
                'total', sum(ds.total_amount)
            )
        ) FILTER (WHERE ds.currency IS NOT NULL) AS currency_breakdown
    FROM daily_stats ds
    GROUP BY CUBE (
        ds.txn_date,
        ds.txn_type,
        ds.currency,
        ds.account_type,
        ds.branch_code,
        ds.risk_score
    )
),
ranked AS (
    SELECT
        mda.*,
        row_number() OVER (
            PARTITION BY mda.txn_type, mda.account_type
            ORDER BY mda.total_amount DESC
        ) AS amount_rank,
        rank() OVER (
            PARTITION BY mda.branch_code
            ORDER BY mda.total_txns DESC
        ) AS txn_rank,
        dense_rank() OVER (
            PARTITION BY mda.risk_score, mda.currency
            ORDER BY mda.unique_accounts DESC
        ) AS account_rank,
        sum(mda.total_amount) OVER (
            PARTITION BY mda.txn_type
            ORDER BY mda.txn_date
            ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
        ) AS cumulative_type_amount,
        avg(mda.avg_amount) OVER (
            PARTITION BY mda.account_type, mda.risk_score
            ORDER BY mda.txn_date
            ROWS BETWEEN 7 PRECEDING AND 7 FOLLOWING
        ) AS rolling_15d_avg
    FROM multi_dim_agg mda
    WHERE mda.total_txns > 0
)
SELECT
    r.txn_date,
    r.txn_type,
    r.currency,
    r.account_type,
    r.branch_code,
    r.risk_score,
    r.total_txns,
    r.total_amount,
    r.unique_accounts,
    r.amount_rank,
    r.txn_rank,
    r.account_rank,
    r.cumulative_type_amount,
    round(r.rolling_15d_avg::numeric, 4) AS rolling_15d_avg,
    r.high_value_accounts,
    r.major_currencies,
    r.currency_breakdown
FROM ranked r
WHERE r.amount_rank <= 10
   OR r.txn_rank <= 5
   OR r.account_rank <= 3
ORDER BY
    r.txn_date NULLS LAST,
    r.total_amount DESC;