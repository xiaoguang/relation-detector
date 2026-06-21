-- ============================================================================
-- 极端嵌套SQL测试用例 (跨版本通用)
-- 目标: 10层嵌套深度, 压测SQL解析器极限
-- 适用: PG 12+ (使用了JSON_PATH, 但可以调整为通用语法)
-- ============================================================================

-- ============================================================================
-- 10层嵌套子查询 + 递归CTE + 窗口函数 + LATERAL + JSON
-- ============================================================================

WITH RECURSIVE
-- 第1层CTE: 基础数据
level_1 AS (
    SELECT
        generate_series(1, 100) AS id,
        random() * 1000 AS value,
        (ARRAY['A', 'B', 'C', 'D', 'E'])[floor(random() * 5 + 1)] AS category,
        now() - (random() * INTERVAL '365 days') AS created_at
),
-- 第2层CTE: 聚合
level_2 AS (
    SELECT
        l1.category,
        date_trunc('month', l1.created_at) AS month,
        count(*) AS cnt,
        sum(l1.value) AS total_value,
        avg(l1.value) AS avg_value,
        stddev(l1.value) AS stddev_value,
        array_agg(l1.id ORDER BY l1.value DESC) AS top_ids,
        jsonb_agg(
            jsonb_build_object('id', l1.id, 'value', l1.value)
            ORDER BY l1.value DESC
        ) AS detail_json
    FROM level_1 l1
    GROUP BY l1.category, date_trunc('month', l1.created_at)
),
-- 第3层CTE: 窗口函数
level_3 AS (
    SELECT
        l2.*,
        row_number() OVER (PARTITION BY l2.category ORDER BY l2.total_value DESC) AS rn,
        sum(l2.total_value) OVER (
            PARTITION BY l2.category
            ORDER BY l2.month
            ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
        ) AS cumulative_value,
        lag(l2.total_value, 1) OVER (
            PARTITION BY l2.category ORDER BY l2.month
        ) AS prev_month_value,
        lead(l2.total_value, 1) OVER (
            PARTITION BY l2.category ORDER BY l2.month
        ) AS next_month_value,
        avg(l2.avg_value) OVER (
            PARTITION BY l2.category
            ORDER BY l2.month
            ROWS BETWEEN 2 PRECEDING AND 2 FOLLOWING
        ) AS moving_avg_5
    FROM level_2 l2
),
-- 第4层CTE: 递归
level_4 AS (
    SELECT
        l3.category,
        l3.month,
        l3.cnt,
        l3.total_value,
        l3.rn,
        l3.cumulative_value,
        l3.moving_avg_5,
        1 AS recursion_depth,
        ARRAY[l3.month::text] AS recursion_path
    FROM level_3 l3
    WHERE l3.rn = 1
    UNION ALL
    SELECT
        l4.category,
        l3.month,
        l3.cnt,
        l3.total_value,
        l3.rn,
        l3.cumulative_value,
        l3.moving_avg_5,
        l4.recursion_depth + 1,
        l4.recursion_path || l3.month::text
    FROM level_4 l4
    JOIN level_3 l3
        ON l4.category = l3.category
       AND l3.rn = l4.recursion_depth + 1
    WHERE l4.recursion_depth < 10
),
-- 第5层CTE: LATERAL关联
level_5 AS (
    SELECT
        l4.*,
        lat_1.next_category,
        lat_1.cross_category_avg,
        lat_2.top_performer,
        lat_3.seasonality_index
    FROM level_4 l4
    LEFT JOIN LATERAL (
        -- LATERAL 1: 跨类别分析
        SELECT
            l3_other.category AS next_category,
            avg(l3_other.total_value) AS cross_category_avg
        FROM level_3 l3_other
        WHERE l3_other.category != l4.category
          AND l3_other.month = l4.month
        GROUP BY l3_other.category
        ORDER BY avg(l3_other.total_value) DESC
        LIMIT 1
    ) lat_1 ON true
    LEFT JOIN LATERAL (
        -- LATERAL 2: 最佳月份
        SELECT
            l3_best.month AS top_performer,
            l3_best.total_value AS best_value
        FROM level_3 l3_best
        WHERE l3_best.category = l4.category
        ORDER BY l3_best.total_value DESC
        LIMIT 1
    ) lat_2 ON true
    LEFT JOIN LATERAL (
        -- LATERAL 3: 季节性
        SELECT
            CASE
                WHEN l3_q1.avg_value > l3_q3.avg_value * 1.2 THEN 'Q1_PEAK'
                WHEN l3_q3.avg_value > l3_q1.avg_value * 1.2 THEN 'Q3_PEAK'
                WHEN l3_q2.avg_value > l3_q4.avg_value * 1.2 THEN 'Q2_PEAK'
                WHEN l3_q4.avg_value > l3_q2.avg_value * 1.2 THEN 'Q4_PEAK'
                ELSE 'NO_SEASONALITY'
            END AS seasonality_index
        FROM
            (SELECT avg(l3.avg_value) AS avg_value FROM level_3 l3
             WHERE l3.category = l4.category
               AND extract(QUARTER FROM l3.month) = 1) l3_q1,
            (SELECT avg(l3.avg_value) AS avg_value FROM level_3 l3
             WHERE l3.category = l4.category
               AND extract(QUARTER FROM l3.month) = 2) l3_q2,
            (SELECT avg(l3.avg_value) AS avg_value FROM level_3 l3
             WHERE l3.category = l4.category
               AND extract(QUARTER FROM l3.month) = 3) l3_q3,
            (SELECT avg(l3.avg_value) AS avg_value FROM level_3 l3
             WHERE l3.category = l4.category
               AND extract(QUARTER FROM l3.month) = 4) l3_q4
    ) lat_3 ON true
),
-- 第6层CTE: 聚合rank
level_6 AS (
    SELECT
        l5.category,
        count(*) AS months_active,
        sum(l5.total_value) AS total_value,
        avg(l5.cross_category_avg) AS avg_cross_avg,
        mode() WITHIN GROUP (ORDER BY l5.seasonality_index) AS dominant_seasonality,
        jsonb_object_agg(
            l5.month::text,
            jsonb_build_object(
                'cnt', l5.cnt,
                'total', l5.total_value,
                'cumulative', l5.cumulative_value,
                'moving_avg', l5.moving_avg_5,
                'cross_avg', l5.cross_category_avg
            )
        ) AS monthly_detail,
        array_agg(
            l5.month::text ORDER BY l5.month
        ) FILTER (WHERE l5.cnt > 10) AS high_activity_months
    FROM level_5 l5
    GROUP BY l5.category
)
-- 第7层: 主查询 - 6层嵌套子查询
SELECT
    l6.category,
    l6.months_active,
    l6.total_value,
    l6.dominant_seasonality,
    l6.high_activity_months,
    -- 第7层嵌套: 子查询
    (
        -- 第8层嵌套
        SELECT jsonb_build_object(
            'rank', (
                -- 第9层嵌套
                SELECT count(*) + 1
                FROM level_6 l6_inner_1
                WHERE l6_inner_1.total_value > l6.total_value
            ),
            'percentile', (
                -- 第9层嵌套
                SELECT round(
                    (count(*) FILTER (
                        WHERE l6_inner_2.total_value <= l6.total_value
                    )::numeric / NULLIF(count(*), 0) * 100)::numeric, 2
                )
                FROM level_6 l6_inner_2
            ),
            'vs_best', (
                -- 第9层嵌套
                SELECT round(
                    (l6.total_value / NULLIF(max(l6_inner_3.total_value), 0) * 100)::numeric, 2
                )
                FROM level_6 l6_inner_3
            ),
            'contribution', (
                -- 第9层嵌套
                SELECT round(
                    (l6.total_value / NULLIF((
                        -- 第10层嵌套!!!
                        SELECT sum(l6_inner_4.total_value)
                        FROM level_6 l6_inner_4
                    ), 0) * 100)::numeric, 2
                )
            )
        )
    ) AS ranking_stats,
    -- 第7层嵌套: 月度趋势
    (
        -- 第8层嵌套
        SELECT jsonb_agg(
            jsonb_build_object(
                'month', month_key,
                'value', (l6.monthly_detail->>month_key)::jsonb->>'total',
                'trend', CASE
                    WHEN (l6.monthly_detail->>month_key)::jsonb->>'total' >
                         lag((l6.monthly_detail->>month_key)::jsonb->>'total') OVER (
                            ORDER BY month_key
                         ) THEN 'UP'
                    ELSE 'DOWN'
                END
            )
            ORDER BY month_key
        )
        FROM jsonb_object_keys(l6.monthly_detail) AS month_key
    ) AS monthly_trend,
    -- 第7层嵌套: 关联分析
    (
        -- 第8层嵌套
        SELECT jsonb_build_object(
            'correlation_with_avg', (
                -- 第9层嵌套
                SELECT corr(
                    (l6.monthly_detail->>mt)::jsonb->>'total',
                    (l6.monthly_detail->>mt)::jsonb->>'moving_avg'
                )
                FROM jsonb_object_keys(l6.monthly_detail) AS mt
            )
        )
    ) AS correlation_analysis
FROM level_6 l6
ORDER BY l6.total_value DESC;

-- ============================================================================
-- 极限复杂UNION + INTERSECT + EXCEPT 组合 (8路集合操作)
-- ============================================================================

WITH
set_a AS (
    SELECT id, value, category FROM level_1 WHERE value > 500
),
set_b AS (
    SELECT id, value, category FROM level_1 WHERE value > 300 AND value < 700
),
set_c AS (
    SELECT id, value, category FROM level_1 WHERE category IN ('A', 'B')
),
set_d AS (
    SELECT id, value, category FROM level_1 WHERE category IN ('B', 'C', 'D')
),
set_e AS (
    SELECT id, value, category FROM level_1 WHERE value > 800
),
set_f AS (
    SELECT id, value, category FROM level_1 WHERE value < 200
),
set_g AS (
    SELECT id, value, category FROM level_1 WHERE category = 'E'
),
set_h AS (
    SELECT id, value, category FROM level_1 WHERE value BETWEEN 400 AND 600
)
-- 8路集合操作嵌套
SELECT id, value, category, 'A_INTERSECT_B_MINUS_C' AS source FROM (
    SELECT id, value, category FROM set_a
    INTERSECT
    SELECT id, value, category FROM set_b
    EXCEPT
    SELECT id, value, category FROM set_c
) sub_1
UNION ALL
SELECT id, value, category, 'D_UNION_E_INTERSECT_F' AS source FROM (
    SELECT id, value, category FROM set_d
    UNION
    SELECT id, value, category FROM set_e
    INTERSECT
    SELECT id, value, category FROM set_f
) sub_2
UNION ALL
SELECT id, value, category, 'C_INTERSECT_D_UNION_G' AS source FROM (
    SELECT id, value, category FROM set_c
    INTERSECT
    SELECT id, value, category FROM set_d
    UNION ALL
    SELECT id, value, category FROM set_g
) sub_3
UNION ALL
SELECT id, value, category, 'A_EXCEPT_B_EXCEPT_H' AS source FROM (
    SELECT id, value, category FROM set_a
    EXCEPT
    SELECT id, value, category FROM set_b
    EXCEPT
    SELECT id, value, category FROM set_h
) sub_4
UNION ALL
SELECT id, value, category, 'COMPLEX_MIX' AS source FROM (
    (
        SELECT id, value, category FROM set_a
        INTERSECT
        SELECT id, value, category FROM set_d
    )
    UNION ALL
    (
        SELECT id, value, category FROM set_b
        EXCEPT
        SELECT id, value, category FROM set_h
    )
    INTERSECT
    (
        SELECT id, value, category FROM set_c
        UNION ALL
        SELECT id, value, category FROM set_e
    )
) sub_5
ORDER BY source, id;

-- ============================================================================
-- 极致CASE嵌套 (8层CASE)
-- ============================================================================

SELECT
    l1.id,
    l1.value,
    l1.category,
    CASE
        WHEN l1.value > 900 THEN
            CASE
                WHEN l1.category = 'A' THEN
                    CASE
                        WHEN l1.id % 2 = 0 THEN
                            CASE
                                WHEN l1.id % 3 = 0 THEN
                                    CASE
                                        WHEN l1.id % 5 = 0 THEN 'A-EVEN-MOD3-MOD5'
                                        ELSE 'A-EVEN-MOD3-NOT5'
                                    END
                                ELSE
                                    CASE
                                        WHEN l1.id % 7 = 0 THEN 'A-EVEN-NOT3-MOD7'
                                        ELSE 'A-EVEN-NOT3-NOT7'
                                    END
                            END
                        ELSE
                            CASE
                                WHEN l1.id % 3 = 0 THEN
                                    CASE
                                        WHEN l1.id % 5 = 0 THEN
                                            CASE
                                                WHEN l1.id % 11 = 0 THEN 'A-ODD-MOD3-MOD5-MOD11'
                                                ELSE 'A-ODD-MOD3-MOD5-NOT11'
                                            END
                                        ELSE 'A-ODD-MOD3-NOT5'
                                    END
                                ELSE
                                    CASE
                                        WHEN l1.id % 7 = 0 THEN
                                            CASE
                                                WHEN l1.id % 13 = 0 THEN 'A-ODD-NOT3-MOD7-MOD13'
                                                ELSE 'A-ODD-NOT3-MOD7-NOT13'
                                            END
                                        ELSE 'A-ODD-NOT3-NOT7'
                                    END
                            END
                    END
                WHEN l1.category = 'B' THEN
                    CASE
                        WHEN l1.value > 950 THEN 'B-SUPER-HIGH'
                        WHEN l1.value > 920 THEN 'B-VERY-HIGH'
                        ELSE 'B-HIGH'
                    END
                ELSE
                    CASE
                        WHEN l1.category = 'C' THEN 'C-HIGH-VALUE'
                        WHEN l1.category = 'D' THEN 'D-HIGH-VALUE'
                        ELSE 'E-HIGH-VALUE'
                    END
            END
        WHEN l1.value > 500 THEN
            CASE
                WHEN l1.category = 'A' THEN 'A-MEDIUM'
                WHEN l1.category = 'B' THEN 'B-MEDIUM'
                WHEN l1.category = 'C' THEN
                    CASE
                        WHEN l1.id % 2 = 0 THEN 'C-MEDIUM-EVEN'
                        ELSE 'C-MEDIUM-ODD'
                    END
                ELSE 'OTHER-MEDIUM'
            END
        WHEN l1.value > 100 THEN
            CASE
                WHEN l1.category IN ('A', 'B') THEN 'AB-LOW'
                WHEN l1.category IN ('C', 'D') THEN 'CD-LOW'
                ELSE 'E-LOW'
            END
        ELSE
            CASE
                WHEN l1.value > 10 THEN 'VERY-LOW'
                WHEN l1.value > 1 THEN 'MINIMAL'
                ELSE 'NEGLIGIBLE'
            END
    END AS ultra_nested_segment,
    -- 第二维度: 8层CASE嵌套
    CASE
        WHEN l1.created_at > now() - INTERVAL '30 days' THEN
            CASE
                WHEN extract(DOW FROM l1.created_at) IN (0, 6) THEN
                    CASE
                        WHEN extract(HOUR FROM l1.created_at) BETWEEN 9 AND 17 THEN
                            CASE
                                WHEN l1.category = 'A' THEN
                                    CASE
                                        WHEN l1.value > 700 THEN 'RECENT-WEEKEND-BUSINESS-A-HIGH'
                                        ELSE 'RECENT-WEEKEND-BUSINESS-A-LOW'
                                    END
                                ELSE 'RECENT-WEEKEND-BUSINESS-OTHER'
                            END
                        ELSE 'RECENT-WEEKEND-AFTERHOURS'
                    END
                ELSE 'RECENT-WEEKDAY'
            END
        ELSE 'OLD'
    END AS time_segment
FROM level_1 l1
ORDER BY l1.id;

-- ============================================================================
-- 极致窗口函数组合 (10个窗口函数同时使用)
-- ============================================================================

SELECT
    l2.category,
    l2.month,
    l2.cnt,
    l2.total_value,
    -- 10个窗口函数
    row_number() OVER w_category AS rn,
    rank() OVER w_category AS rank_val,
    dense_rank() OVER w_category AS dense_rank_val,
    percent_rank() OVER w_category AS percent_rank_val,
    cume_dist() OVER w_category AS cume_dist_val,
    ntile(4) OVER w_category AS quartile,
    first_value(l2.total_value) OVER w_category AS first_val,
    last_value(l2.total_value) OVER (
        PARTITION BY l2.category ORDER BY l2.month
        ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING
    ) AS last_val,
    nth_value(l2.total_value, 3) OVER w_category AS third_val,
    lag(l2.total_value, 1, 0) OVER w_category AS prev_val,
    lead(l2.total_value, 1, 0) OVER w_category AS next_val,
    sum(l2.total_value) OVER w_category AS running_sum,
    avg(l2.total_value) OVER (
        PARTITION BY l2.category ORDER BY l2.month
        ROWS BETWEEN 2 PRECEDING AND 2 FOLLOWING
    ) AS moving_avg_5,
    stddev(l2.total_value) OVER w_category AS running_stddev
FROM level_2 l2
WINDOW w_category AS (
    PARTITION BY l2.category
    ORDER BY l2.month
    ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
)
ORDER BY l2.category, l2.month;