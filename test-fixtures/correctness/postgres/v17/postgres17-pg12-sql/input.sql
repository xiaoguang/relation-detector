-- ============================================================================
-- PostgreSQL 12 复杂SQL测试用例
-- 特性: 生成列, JSON_PATH, 物化CTE, 分区性能改进
-- 目标: 最大语法嵌套深度, 最全语法覆盖
-- ============================================================================

-- ============================================================================
-- Part 1: 生成列与复杂表定义
-- ============================================================================

CREATE TABLE IF NOT EXISTS pg12_complex_schema (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    data JSONB NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now(),
    year_month TEXT GENERATED ALWAYS AS (to_char(created_at, 'YYYY-MM')) STORED,
    category_path TEXT[] NOT NULL DEFAULT '{}',
    -- 深层嵌套JSON表达式生成列
    profile_first_name TEXT GENERATED ALWAYS AS (data #>> '{profile,firstName}') STORED,
    profile_last_name TEXT GENERATED ALWAYS AS (data #>> '{profile,lastName}') STORED,
    computed_hash TEXT GENERATED ALWAYS AS (
        md5(
            coalesce(data #>> '{profile,firstName}', '') ||
            coalesce(data #>> '{profile,lastName}', '') ||
            to_char(created_at, 'YYYY-MM-DD HH24:MI:SS')
        )
    ) STORED,
    full_name TEXT GENERATED ALWAYS AS (
        trim(
            coalesce(data #>> '{profile,title}', '') || ' ' ||
            coalesce(data #>> '{profile,firstName}', '') || ' ' ||
            coalesce(data #>> '{profile,lastName}', '')
        )
    ) STORED
);

CREATE TABLE IF NOT EXISTS pg12_orders (
    order_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES pg12_complex_schema(id),
    order_data JSONB NOT NULL,
    order_date DATE NOT NULL DEFAULT CURRENT_DATE,
    total_amount NUMERIC(12,2) GENERATED ALWAYS AS (
        (order_data #>> '{total,amount}')::NUMERIC(12,2)
    ) STORED,
    status TEXT GENERATED ALWAYS AS (
        coalesce(order_data #>> '{status}', 'pending')
    ) STORED
);

CREATE TABLE IF NOT EXISTS pg12_partitioned_orders (
    order_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    order_date DATE NOT NULL,
    amount NUMERIC(12,2) NOT NULL,
    metadata JSONB NOT NULL
) PARTITION BY RANGE (order_date);

CREATE TABLE pg12_orders_2023 PARTITION OF pg12_partitioned_orders
    FOR VALUES FROM ('2023-01-01') TO ('2024-01-01');

CREATE TABLE pg12_orders_2024 PARTITION OF pg12_partitioned_orders
    FOR VALUES FROM ('2024-01-01') TO ('2025-01-01');

-- ============================================================================
-- Part 2: JSON_PATH 极致嵌套查询
-- ============================================================================

WITH RECURSIVE
-- 基础CTE: JSON_PATH查询
json_data AS (
    SELECT
        id,
        data,
        jsonb_path_query_array(
            data,
            '$.items[*] ? (@.price > 100 && @.qty > 0).id'
        ) AS expensive_items,
        jsonb_path_query_first(
            data,
            '$.profile.address ? (@.country == "CN").city'
        )#>>'{}' AS city,
        jsonb_path_exists(
            data,
            '$.tags[*] ? (@ like_regex "^(urgent|critical)$" flag "i")'
        ) AS is_priority,
        jsonb_path_query(
            data,
            '$.history.events[*] ? (@.timestamp.datetime() > "2024-01-01T00:00:00".datetime())'
        ) AS recent_events
    FROM pg12_complex_schema
    WHERE jsonb_path_exists(
        data,
        '$.items[*] ? (@.price > 0 && @.category == "electronics")'
    )
),
-- 第二层CTE: 展开JSON数组
flattened_items AS (
    SELECT
        jd.id,
        jd.city,
        je.event,
        jsonb_path_query_first(
            je.event,
            '$.details.changes[*] ? (@.field == "status").new_value'
        )#>>'{}' AS status_change
    FROM json_data jd
    LEFT JOIN LATERAL (
        SELECT jsonb_array_elements(jd.recent_events) AS event
    ) je ON true
    WHERE jd.is_priority = true
),
-- 第三层CTE: 聚合统计
item_stats AS (
    SELECT
        fi.id,
        fi.city,
        count(DISTINCT fi.status_change) FILTER (
            WHERE fi.status_change IS NOT NULL
        ) AS distinct_status_changes,
        array_agg(DISTINCT fi.status_change ORDER BY fi.status_change)
            FILTER (WHERE fi.status_change IS NOT NULL) AS status_list,
        jsonb_object_agg(
            coalesce(fi.status_change, 'unknown'),
            count(*)::text
        ) FILTER (WHERE fi.status_change IS NOT NULL) AS status_distribution
    FROM flattened_items fi
    GROUP BY GROUPING SETS (
        (fi.id, fi.city),
        (fi.id),
        (fi.city),
        ()
    )
),
-- 第四层CTE: 窗口函数深度嵌套
windowed_stats AS (
    SELECT
        its.id,
        its.city,
        its.distinct_status_changes,
        its.status_list,
        its.status_distribution,
        row_number() OVER (
            PARTITION BY its.city
            ORDER BY its.distinct_status_changes DESC NULLS LAST
        ) AS city_rank,
        dense_rank() OVER (
            ORDER BY its.distinct_status_changes DESC
        ) AS global_rank,
        ntile(10) OVER (
            PARTITION BY its.city
            ORDER BY its.distinct_status_changes DESC
        ) AS decile,
        first_value(its.status_list) OVER (
            PARTITION BY its.city
            ORDER BY its.distinct_status_changes DESC
            ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING
        ) AS top_city_status_list,
        lag(its.distinct_status_changes, 1, 0) OVER (
            PARTITION BY its.city
            ORDER BY its.distinct_status_changes DESC
        ) AS prev_count,
        lead(its.distinct_status_changes, 1, 0) OVER (
            PARTITION BY its.city
            ORDER BY its.distinct_status_changes DESC
        ) AS next_count,
        percent_rank() OVER (
            ORDER BY its.distinct_status_changes
        ) AS percentile,
        cume_dist() OVER (
            ORDER BY its.distinct_status_changes
        ) AS cumulative_dist
    FROM item_stats its
),
-- 第五层CTE: 统计计算
statistical_analysis AS (
    SELECT
        ws.city,
        ws.global_rank,
        ws.decile,
        ws.city_rank,
        ws.distinct_status_changes,
        ws.top_city_status_list,
        ws.prev_count,
        ws.next_count,
        ws.percentile,
        ws.cumulative_dist,
        avg(ws.distinct_status_changes) OVER (
            PARTITION BY ws.city
            ORDER BY ws.global_rank
            ROWS BETWEEN 3 PRECEDING AND 3 FOLLOWING
        ) AS moving_avg_7,
        stddev(ws.distinct_status_changes) OVER (
            PARTITION BY ws.city
        ) AS city_stddev,
        corr(ws.distinct_status_changes, ws.global_rank) OVER (
            PARTITION BY ws.city
        ) AS correlation_rank_changes,
        string_agg(
            coalesce(ws.status_list::TEXT, '[]'),
            ' -> '
            ORDER BY ws.global_rank
        ) OVER (
            PARTITION BY ws.city
            ORDER BY ws.global_rank
            ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
        ) AS cumulative_status_path
    FROM windowed_stats ws
    WHERE ws.city_rank <= 10
)
-- 主查询: 多层聚合
SELECT
    sa.city,
    count(*) AS entry_count,
    round(avg(sa.distinct_status_changes)::numeric, 4) AS avg_changes,
    round(max(sa.moving_avg_7)::numeric, 4) AS peak_moving_avg,
    round(min(sa.correlation_rank_changes)::numeric, 4) AS min_correlation,
    round(max(sa.cumulative_dist)::numeric, 4) AS max_cumulative,
    array_agg(
        DISTINCT jsonb_build_object(
            'rank', sa.global_rank,
            'decile', sa.decile,
            'changes', sa.distinct_status_changes,
            'percentile', round(sa.percentile::numeric, 4),
            'path', sa.cumulative_status_path
        )
        ORDER BY sa.global_rank
    ) FILTER (WHERE sa.city_rank = 1) AS top_entries_detail
FROM statistical_analysis sa
GROUP BY sa.city
HAVING count(*) > 3
   AND round(avg(sa.distinct_status_changes)::numeric, 4) > 1.0
ORDER BY avg_changes DESC, sa.city ASC;

-- ============================================================================
-- Part 3: 深度嵌套子查询与LATERAL
-- ============================================================================

SELECT
    cs.id,
    cs.full_name,
    cs.city,
    (
        SELECT jsonb_agg(
            jsonb_build_object(
                'order_id', o.order_id,
                'amount', o.total_amount,
                'items', (
                    SELECT count(*)::int
                    FROM jsonb_array_elements(
                        CASE
                            WHEN o.order_data ? 'items' THEN o.order_data->'items'
                            ELSE '[]'::jsonb
                        END
                    ) AS items
                ),
                'category_breakdown', (
                    SELECT jsonb_object_agg(
                        coalesce(cat.key, 'unknown'),
                        cat.count
                    )
                    FROM (
                        SELECT
                            item->>'category' AS key,
                            count(*) AS count
                        FROM jsonb_array_elements(
                            o.order_data->'items'
                        ) AS item
                        GROUP BY item->>'category'
                    ) cat
                ),
                'related_customers', (
                    SELECT json_agg(
                        DISTINCT jsonb_build_object(
                            'id', rel.id,
                            'name', rel.full_name,
                            'common_orders', (
                                SELECT count(*)
                                FROM pg12_orders o2
                                WHERE o2.customer_id = rel.id
                                  AND o2.order_data->>'region' = o.order_data->>'region'
                                  AND o2.order_date BETWEEN
                                      o.order_date - INTERVAL '30 days'
                                      AND o.order_date + INTERVAL '30 days'
                            )
                        )
                    )
                    FROM pg12_complex_schema rel
                    WHERE rel.id != cs.id
                      AND EXISTS (
                        SELECT 1
                        FROM pg12_orders o3
                        WHERE o3.customer_id = rel.id
                          AND o3.order_data->>'region' = o.order_data->>'region'
                          AND o3.order_date BETWEEN
                              o.order_date - INTERVAL '90 days'
                              AND o.order_date + INTERVAL '90 days'
                      )
                    LIMIT 5
                )
            )
        )
        FROM pg12_orders o
        WHERE o.customer_id = cs.id
          AND o.order_date >= CURRENT_DATE - INTERVAL '1 year'
          AND o.order_data @? '$.items[*] ? (@.price > 50)'
    ) AS order_summary,
    (
        SELECT jsonb_path_query_array(
            cs.data,
            format(
                '$.history.events[*] ? (@.timestamp > "%s" && @.type == "purchase")',
                (CURRENT_DATE - INTERVAL '6 months')::text
            )::jsonpath
        )
    ) AS recent_purchase_events
FROM pg12_complex_schema cs
LEFT JOIN LATERAL (
    SELECT
        o.order_id,
        o.total_amount,
        o.order_date,
        row_number() OVER (
            PARTITION BY o.customer_id
            ORDER BY o.total_amount DESC
        ) AS amount_rank
    FROM pg12_orders o
    WHERE o.customer_id = cs.id
) top_orders ON true
LEFT JOIN LATERAL (
    SELECT
        jsonb_path_query_first(
            cs.data,
            '$.preferences.alerts[*] ? (@.enabled == true)'
        ) AS active_alert
) alerts ON true
CROSS JOIN LATERAL (
    SELECT
        count(*) FILTER (WHERE amount_rank <= 3) AS top3_count,
        sum(total_amount) FILTER (WHERE amount_rank <= 10) AS top10_sum,
        avg(total_amount) AS overall_avg
    FROM (
        SELECT o2.total_amount,
               row_number() OVER (ORDER BY o2.total_amount DESC) AS amount_rank
        FROM pg12_orders o2
        WHERE o2.customer_id = cs.id
    ) sub
) stats ON true
WHERE cs.data @? '$.profile.address.country ? (@ == "CN" || @ == "US")'
  AND (
    SELECT count(*)
    FROM jsonb_path_query(
        cs.data,
        '$.items[*] ? (@.price > 1000)'
    )
) > 0
ORDER BY stats.top10_sum DESC NULLS LAST, cs.full_name ASC;

-- ============================================================================
-- Part 4: 复杂UNION/INTERSECT/EXCEPT嵌套
-- ============================================================================

WITH
set_a AS (
    SELECT
        cs.id,
        cs.full_name,
        cs.city,
        'HIGH_VALUE' AS segment,
        o.total_amount AS metric
    FROM pg12_complex_schema cs
    JOIN pg12_orders o ON cs.id = o.customer_id
    WHERE o.total_amount > 1000
      AND cs.data @? '$.profile ? (@.vip == true)'
),
set_b AS (
    SELECT
        cs.id,
        cs.full_name,
        cs.city,
        'FREQUENT' AS segment,
        count(*)::numeric AS metric
    FROM pg12_complex_schema cs
    JOIN pg12_orders o ON cs.id = o.customer_id
    WHERE o.order_date >= CURRENT_DATE - INTERVAL '3 months'
    GROUP BY cs.id, cs.full_name, cs.city
    HAVING count(*) > 10
),
set_c AS (
    SELECT
        cs.id,
        cs.full_name,
        cs.city,
        'INTERNATIONAL' AS segment,
        o.total_amount AS metric
    FROM pg12_complex_schema cs
    JOIN pg12_orders o ON cs.id = o.customer_id
    WHERE cs.data #>> '{profile,address,country}' != 'CN'
),
-- INTERSECT嵌套
intersection_ab AS (
    SELECT id, full_name, city FROM set_a
    INTERSECT
    SELECT id, full_name, city FROM set_b
),
-- EXCEPT嵌套
exclusive_c AS (
    SELECT id, full_name, city FROM set_c
    EXCEPT
    (
        SELECT id, full_name, city FROM set_a
        UNION ALL
        SELECT id, full_name, city FROM set_b
    )
),
-- 多层集合操作
combined AS (
    SELECT * FROM intersection_ab
    UNION ALL
    SELECT * FROM exclusive_c
    UNION ALL
    (
        SELECT id, full_name, city FROM set_a
        INTERSECT
        SELECT id, full_name, city FROM set_c
    )
)
SELECT
    c.id,
    c.full_name,
    c.city,
    (
        SELECT string_agg(DISTINCT segment, ', ' ORDER BY segment)
        FROM (
            SELECT segment FROM set_a WHERE id = c.id
            UNION
            SELECT segment FROM set_b WHERE id = c.id
            UNION
            SELECT segment FROM set_c WHERE id = c.id
        ) seg
    ) AS all_segments,
    (
        SELECT json_build_object(
            'high_value', (SELECT metric FROM set_a WHERE id = c.id LIMIT 1),
            'frequent', (SELECT metric FROM set_b WHERE id = c.id LIMIT 1),
            'international', (SELECT metric FROM set_c WHERE id = c.id LIMIT 1)
        )
    ) AS segment_metrics
FROM combined c
ORDER BY c.id;

-- ============================================================================
-- Part 5: 极致嵌套CASE表达式与类型转换
-- ============================================================================

SELECT
    cs.id,
    cs.full_name,
    CASE
        WHEN cs.data @? '$.profile ? (@.vip == true)' THEN
            CASE
                WHEN (
                    SELECT count(*) FROM pg12_orders o
                    WHERE o.customer_id = cs.id
                      AND o.total_amount > 10000
                ) > 5 THEN 'PLATINUM_VIP'
                WHEN (
                    SELECT count(*) FROM pg12_orders o
                    WHERE o.customer_id = cs.id
                      AND o.total_amount > 5000
                ) > 3 THEN 'GOLD_VIP'
                ELSE 'SILVER_VIP'
            END
        WHEN cs.data #>> '{profile,address,country}' IN (
            SELECT DISTINCT jsonb_path_query_first(
                o.order_data,
                '$.shipping.restricted_countries[*]'
            )#>>'{}'
            FROM pg12_orders o
            WHERE o.customer_id = cs.id
            LIMIT 5
        ) THEN 'RESTRICTED_ZONE'
        WHEN (
            SELECT count(*) FILTER (
                WHERE o.order_data @? '$.items[*] ? (@.category == "luxury")'
            )
            FROM pg12_orders o
            WHERE o.customer_id = cs.id
        ) > 0 THEN 'LUXURY_BUYER'
        ELSE
            CASE
                WHEN (
                    SELECT avg(total_amount) FROM pg12_orders o2
                    WHERE o2.customer_id = cs.id
                ) > 2000 THEN 'HIGH_SPENDER'
                WHEN (
                    SELECT count(*) FROM pg12_orders o2
                    WHERE o2.customer_id = cs.id
                      AND o2.order_date >= CURRENT_DATE - INTERVAL '1 month'
                ) > 2 THEN 'ACTIVE'
                ELSE 'STANDARD'
            END
    END AS customer_tier,
    CASE
        WHEN cs.data #>> '{profile,preferences,language}' = 'zh' THEN
            jsonb_path_query_first(
                cs.data,
                '$.localization.zh.*'
            )::TEXT
        WHEN cs.data #>> '{profile,preferences,language}' = 'en' THEN
            jsonb_path_query_first(
                cs.data,
                '$.localization.en.*'
            )::TEXT
        ELSE NULL
    END AS localized_content
FROM pg12_complex_schema cs
WHERE cs.id IN (
    SELECT DISTINCT o.customer_id
    FROM pg12_orders o
    WHERE o.order_data @? '$.items[*] ? (@.price >= 100 && @.qty >= 2)'
      AND o.order_date >= CURRENT_DATE - INTERVAL '6 months'
    EXCEPT
    SELECT DISTINCT o2.customer_id
    FROM pg12_orders o2
    WHERE o2.order_data @? '$.returns[*] ? (@.reason == "defective")'
      AND o2.order_date >= CURRENT_DATE - INTERVAL '3 months'
)
ORDER BY
    CASE customer_tier
        WHEN 'PLATINUM_VIP' THEN 1
        WHEN 'GOLD_VIP' THEN 2
        WHEN 'SILVER_VIP' THEN 3
        WHEN 'LUXURY_BUYER' THEN 4
        WHEN 'HIGH_SPENDER' THEN 5
        WHEN 'ACTIVE' THEN 6
        WHEN 'RESTRICTED_ZONE' THEN 7
        ELSE 8
    END,
    cs.full_name ASC;

-- ============================================================================
-- Part 6: 复杂聚合与GROUPING SETS/ROLLUP/CUBE
-- ============================================================================

SELECT
    coalesce(cs.city, '(ALL)') AS city,
    coalesce(
        extract(YEAR FROM o.order_date)::TEXT,
        '(ALL)'
    ) AS order_year,
    coalesce(
        o.order_data->>'region',
        '(ALL)'
    ) AS region,
    coalesce(
        CASE
            WHEN o.total_amount < 100 THEN 'MICRO'
            WHEN o.total_amount < 1000 THEN 'SMALL'
            WHEN o.total_amount < 5000 THEN 'MEDIUM'
            WHEN o.total_amount < 10000 THEN 'LARGE'
            ELSE 'ENTERPRISE'
        END,
        '(ALL)'
    ) AS order_size,
    count(*) AS order_count,
    sum(o.total_amount) AS total_revenue,
    round(avg(o.total_amount)::numeric, 2) AS avg_revenue,
    round(stddev(o.total_amount)::numeric, 2) AS stddev_revenue,
    percentile_cont(0.5) WITHIN GROUP (ORDER BY o.total_amount) AS median_revenue,
    percentile_cont(0.25) WITHIN GROUP (ORDER BY o.total_amount) AS p25_revenue,
    percentile_cont(0.75) WITHIN GROUP (ORDER BY o.total_amount) AS p75_revenue,
    count(*) FILTER (WHERE o.order_data @? '$.items[*] ? (@.category == "electronics")')
        AS electronics_count,
    sum(
        (o.order_data #>> '{tax,amount}')::numeric
    ) FILTER (WHERE o.order_data ? 'tax') AS total_tax,
    array_agg(DISTINCT o.order_data->>'currency')
        FILTER (WHERE o.order_data ? 'currency') AS currencies,
    jsonb_object_agg(
        coalesce(o.order_data->>'payment_method', 'unknown'),
        count(*)::text
    ) FILTER (WHERE o.order_data ? 'payment_method') AS payment_distribution
FROM pg12_complex_schema cs
JOIN pg12_orders o ON cs.id = o.customer_id
WHERE o.order_date >= CURRENT_DATE - INTERVAL '2 years'
GROUP BY CUBE (
    cs.city,
    extract(YEAR FROM o.order_date),
    o.order_data->>'region',
    CASE
        WHEN o.total_amount < 100 THEN 'MICRO'
        WHEN o.total_amount < 1000 THEN 'SMALL'
        WHEN o.total_amount < 5000 THEN 'MEDIUM'
        WHEN o.total_amount < 10000 THEN 'LARGE'
        ELSE 'ENTERPRISE'
    END
)
HAVING count(*) > 5
   AND sum(o.total_amount) > 1000
ORDER BY
    city NULLS LAST,
    order_year NULLS LAST,
    region NULLS LAST,
    order_size NULLS LAST,
    total_revenue DESC;