-- ============================================================================
-- PostgreSQL 13 复杂SQL测试用例
-- 特性: 增量排序, 并行哈希连接, 分区改进, LATERAL增强
-- 目标: 最大语法嵌套深度, 最全语法覆盖
-- ============================================================================

-- ============================================================================
-- Part 1: 增量排序与并行查询 (PG13优化器特性)
-- ============================================================================

WITH RECURSIVE
-- 深度递归CTE
category_tree AS (
    SELECT
        id,
        parent_id,
        name,
        path_text AS path,
        0 AS depth,
        ARRAY[id] AS breadcrumb,
        false AS is_cycle
    FROM categories
    WHERE parent_id IS NULL
    UNION ALL
    SELECT
        c.id,
        c.parent_id,
        c.name,
        ct.path || ' > ' || c.name,
        ct.depth + 1,
        ct.breadcrumb || c.id,
        c.id = ANY(ct.breadcrumb)
    FROM categories c
    JOIN category_tree ct ON c.parent_id = ct.id
    WHERE NOT ct.is_cycle AND ct.depth < 20
),
-- 第二层CTE: 收集子节点
subtree_stats AS (
    SELECT
        ct.id AS root_id,
        ct.name AS root_name,
        ct.depth AS root_depth,
        count(DISTINCT sub.id) AS total_descendants,
        max(sub.depth) AS max_sub_depth,
        array_agg(DISTINCT sub.name ORDER BY sub.name) AS descendant_names,
        (
            SELECT jsonb_agg(
                jsonb_build_object(
                    'id', leaf.id,
                    'name', leaf.name,
                    'depth', leaf.depth,
                    'path', leaf.path,
                    'is_leaf', NOT EXISTS (
                        SELECT 1 FROM categories child
                        WHERE child.parent_id = leaf.id
                    )
                )
                ORDER BY leaf.depth, leaf.name
            )
            FROM category_tree leaf
            WHERE leaf.id = ANY(
                SELECT sub2.id
                FROM category_tree sub2
                WHERE sub2.breadcrumb @> ARRAY[ct.id]
                  AND sub2.id != ct.id
            )
        ) AS subtree_json
    FROM category_tree ct
    GROUP BY ct.id, ct.name, ct.depth
),
-- 第三层CTE: 窗口函数与增量排序
ranked_categories AS (
    SELECT
        ss.*,
        row_number() OVER (
            ORDER BY ss.root_depth ASC, ss.total_descendants DESC
        ) AS complexity_rank,
        dense_rank() OVER (
            PARTITION BY ss.root_depth
            ORDER BY ss.total_descendants DESC
        ) AS depth_rank,
        count(*) FILTER (WHERE ss.total_descendants > 5) OVER (
            PARTITION BY ss.root_depth
            ORDER BY ss.total_descendants DESC
        ) AS large_subtree_count,
        first_value(ss.root_name) OVER (
            PARTITION BY ss.root_depth
            ORDER BY ss.total_descendants DESC
            ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING
        ) AS largest_in_depth,
        nth_value(ss.root_name, 3) OVER (
            PARTITION BY ss.root_depth
            ORDER BY ss.total_descendants DESC
            ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING
        ) AS third_largest_in_depth
    FROM subtree_stats ss
    WHERE ss.total_descendants > 0
),
-- 第四层CTE: LATERAL深度嵌套
enriched_data AS (
    SELECT
        rc.*,
        lv.parent_chain,
        lv.sibling_count,
        lv.avg_depth_of_descendants
    FROM ranked_categories rc
    LEFT JOIN LATERAL (
        SELECT
            string_agg(anc.name, ' < ' ORDER BY anc.depth DESC) AS parent_chain,
            (
                SELECT count(*)
                FROM categories sib
                WHERE sib.parent_id = (
                    SELECT parent_id FROM categories WHERE id = rc.root_id
                )
            ) AS sibling_count,
            (
                SELECT avg(leaf.depth)
                FROM category_tree leaf
                WHERE leaf.breadcrumb @> ARRAY[rc.root_id]
                  AND leaf.id != rc.root_id
            ) AS avg_depth_of_descendants
        FROM category_tree anc
        WHERE rc.root_id = ANY(anc.breadcrumb)
          AND anc.depth <= rc.root_depth
    ) lv ON true
    LEFT JOIN LATERAL (
        SELECT
            jsonb_agg(
                jsonb_build_object(
                    'sibling', sib.name,
                    'descendants', (
                        SELECT count(*) FROM category_tree sub
                        WHERE sub.breadcrumb @> ARRAY[sib.id]
                          AND sub.id != sib.id
                    )
                )
                ORDER BY (
                    SELECT count(*) FROM category_tree sub
                    WHERE sub.breadcrumb @> ARRAY[sib.id]
                      AND sub.id != sib.id
                ) DESC
            )
        FROM categories sib
        WHERE sib.parent_id = (
            SELECT parent_id FROM categories WHERE id = rc.root_id
        )
          AND sib.id != rc.root_id
    ) sibling_info ON true
)
-- 主查询: 多层嵌套聚合
SELECT
    ed.root_depth,
    count(*) AS category_count,
    sum(ed.total_descendants) AS total_descendants_sum,
    round(avg(ed.avg_depth_of_descendants)::numeric, 2) AS avg_sub_depth,
    max(ed.complexity_rank) AS max_complexity_rank,
    array_agg(
        DISTINCT jsonb_build_object(
            'name', ed.root_name,
            'rank', ed.depth_rank,
            'descendants', ed.total_descendants,
            'siblings', ed.sibling_count,
            'parent_chain', ed.parent_chain,
            'sibling_info', ed.sibling_info
        )
        ORDER BY ed.depth_rank
    ) FILTER (WHERE ed.depth_rank <= 5) AS top_categories,
    (
        SELECT jsonb_object_agg(
            depth_group,
            category_array
        )
        FROM (
            SELECT
                'depth_' || ed2.root_depth::text AS depth_group,
                jsonb_agg(
                    jsonb_build_object(
                        'name', ed2.root_name,
                        'descendants', ed2.total_descendants
                    )
                    ORDER BY ed2.total_descendants DESC
                ) AS category_array
            FROM enriched_data ed2
            GROUP BY ed2.root_depth
        ) depth_summary
    ) AS depth_breakdown
FROM enriched_data ed
GROUP BY ed.root_depth
ORDER BY ed.root_depth;

-- ============================================================================
-- Part 2: 哈希分区与并行聚合 (PG13增强)
-- ============================================================================

CREATE TABLE IF NOT EXISTS pg13_hash_metrics (
    id BIGINT GENERATED ALWAYS AS IDENTITY,
    tenant_id INT NOT NULL,
    metric_name TEXT NOT NULL,
    metric_value DOUBLE PRECISION NOT NULL,
    tags JSONB NOT NULL DEFAULT '{}',
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    hour_bucket INT GENERATED ALWAYS AS (
        extract(HOUR FROM recorded_at)::INT
    ) STORED
) PARTITION BY HASH (tenant_id);

-- 创建8个哈希分区
CREATE TABLE pg13_metrics_p0 PARTITION OF pg13_hash_metrics
    FOR VALUES WITH (MODULUS 8, REMAINDER 0);
CREATE TABLE pg13_metrics_p1 PARTITION OF pg13_hash_metrics
    FOR VALUES WITH (MODULUS 8, REMAINDER 1);
CREATE TABLE pg13_metrics_p2 PARTITION OF pg13_hash_metrics
    FOR VALUES WITH (MODULUS 8, REMAINDER 2);
CREATE TABLE pg13_metrics_p3 PARTITION OF pg13_hash_metrics
    FOR VALUES WITH (MODULUS 8, REMAINDER 3);
CREATE TABLE pg13_metrics_p4 PARTITION OF pg13_hash_metrics
    FOR VALUES WITH (MODULUS 8, REMAINDER 4);
CREATE TABLE pg13_metrics_p5 PARTITION OF pg13_hash_metrics
    FOR VALUES WITH (MODULUS 8, REMAINDER 5);
CREATE TABLE pg13_metrics_p6 PARTITION OF pg13_hash_metrics
    FOR VALUES WITH (MODULUS 8, REMAINDER 6);
CREATE TABLE pg13_metrics_p7 PARTITION OF pg13_hash_metrics
    FOR VALUES WITH (MODULUS 8, REMAINDER 7);

-- 跨分区复杂聚合查询
WITH
partition_stats AS (
    SELECT
        tableoid::regclass AS partition_name,
        tenant_id,
        metric_name,
        count(*) AS metric_count,
        min(metric_value) AS min_val,
        max(metric_value) AS max_val,
        avg(metric_value) AS avg_val,
        stddev(metric_value) AS stddev_val,
        percentile_cont(0.5) WITHIN GROUP (ORDER BY metric_value) AS median_val,
        percentile_cont(0.95) WITHIN GROUP (ORDER BY metric_value) AS p95_val,
        percentile_cont(0.99) WITHIN GROUP (ORDER BY metric_value) AS p99_val
    FROM pg13_hash_metrics
    WHERE recorded_at >= CURRENT_TIMESTAMP - INTERVAL '24 hours'
    GROUP BY tableoid, tenant_id, metric_name
),
cross_partition_analysis AS (
    SELECT
        ps.tenant_id,
        ps.metric_name,
        count(DISTINCT ps.partition_name) AS partitions_touched,
        sum(ps.metric_count) AS total_samples,
        avg(ps.avg_val) AS global_avg,
        stddev(ps.avg_val) AS cross_partition_stddev,
        max(ps.p99_val) AS global_p99,
        corr(ps.avg_val, ps.metric_count) AS correlation_avg_count,
        array_agg(
            DISTINCT jsonb_build_object(
                'partition', ps.partition_name::text,
                'count', ps.metric_count,
                'avg', round(ps.avg_val::numeric, 4),
                'p95', round(ps.p95_val::numeric, 4),
                'p99', round(ps.p99_val::numeric, 4)
            )
            ORDER BY ps.partition_name::text
        ) AS partition_detail
    FROM partition_stats ps
    GROUP BY ps.tenant_id, ps.metric_name
    HAVING sum(ps.metric_count) > 100
)
SELECT
    cpa.tenant_id,
    cpa.metric_name,
    cpa.total_samples,
    cpa.partitions_touched,
    round(cpa.global_avg::numeric, 4) AS global_avg,
    round(cpa.cross_partition_stddev::numeric, 4) AS cross_partition_stddev,
    round(cpa.global_p99::numeric, 4) AS global_p99,
    round(cpa.correlation_avg_count::numeric, 4) AS correlation_avg_count,
    cpa.partition_detail,
    -- 嵌套子查询: 检测异常分区
    (
        SELECT jsonb_agg(
            jsonb_build_object(
                'partition', inner_ps.partition_name,
                'avg', round(inner_ps.avg_val::numeric, 4),
                'deviation', round(
                    (inner_ps.avg_val - cpa.global_avg) / NULLIF(cpa.cross_partition_stddev, 0)::numeric,
                    4
                )
            )
        )
        FROM partition_stats inner_ps
        WHERE inner_ps.tenant_id = cpa.tenant_id
          AND inner_ps.metric_name = cpa.metric_name
          AND abs(inner_ps.avg_val - cpa.global_avg) > 2 * NULLIF(cpa.cross_partition_stddev, 0)
    ) AS anomalous_partitions
FROM cross_partition_analysis cpa
ORDER BY cpa.total_samples DESC, cpa.tenant_id, cpa.metric_name;

-- ============================================================================
-- Part 3: LATERAL深度嵌套与并行查询
-- ============================================================================

SELECT
    m.tenant_id,
    m.metric_name,
    m.metric_value,
    m.recorded_at,
    lateral_1.hourly_stats,
    lateral_2.rank_in_tenant,
    lateral_3.anomaly_score,
    lateral_4.correlation_metrics,
    lateral_5.forecast_bounds
FROM pg13_hash_metrics m
LEFT JOIN LATERAL (
    -- LATERAL 1: 当前小时统计
    SELECT
        count(*) AS hour_count,
        avg(metric_value) AS hour_avg,
        stddev(metric_value) AS hour_stddev,
        min(metric_value) AS hour_min,
        max(metric_value) AS hour_max
    FROM pg13_hash_metrics h
    WHERE h.tenant_id = m.tenant_id
      AND h.metric_name = m.metric_name
      AND h.hour_bucket = m.hour_bucket
      AND h.recorded_at::date = m.recorded_at::date
) lateral_1 ON true
LEFT JOIN LATERAL (
    -- LATERAL 2: 排名 (嵌套窗口函数)
    SELECT
        row_number() OVER (
            PARTITION BY m.tenant_id
            ORDER BY m.metric_value DESC
        ) AS rank_by_value,
        percent_rank() OVER (
            PARTITION BY m.tenant_id, m.metric_name
            ORDER BY m.metric_value
        ) AS percent_in_metric
) lateral_2 ON true
LEFT JOIN LATERAL (
    -- LATERAL 3: 异常检测 (深度嵌套子查询)
    SELECT
        CASE
            WHEN lateral_1.hour_stddev > 0 THEN
                (m.metric_value - lateral_1.hour_avg) / lateral_1.hour_stddev
            ELSE 0
        END AS z_score,
        CASE
            WHEN abs(
                (m.metric_value - lateral_1.hour_avg) / NULLIF(lateral_1.hour_stddev, 0)
            ) > 3 THEN 'CRITICAL_OUTLIER'
            WHEN abs(
                (m.metric_value - lateral_1.hour_avg) / NULLIF(lateral_1.hour_stddev, 0)
            ) > 2 THEN 'WARNING_OUTLIER'
            ELSE 'NORMAL'
        END AS anomaly_level,
        (
            SELECT jsonb_build_object(
                'rolling_avg_7d', (
                    SELECT round(avg(inner_m.metric_value)::numeric, 4)
                    FROM pg13_hash_metrics inner_m
                    WHERE inner_m.tenant_id = m.tenant_id
                      AND inner_m.metric_name = m.metric_name
                      AND inner_m.recorded_at BETWEEN
                          m.recorded_at - INTERVAL '7 days'
                          AND m.recorded_at
                ),
                'rolling_max_30d', (
                    SELECT max(inner_m.metric_value)
                    FROM pg13_hash_metrics inner_m
                    WHERE inner_m.tenant_id = m.tenant_id
                      AND inner_m.metric_name = m.metric_name
                      AND inner_m.recorded_at BETWEEN
                          m.recorded_at - INTERVAL '30 days'
                          AND m.recorded_at
                ),
                'same_hour_yesterday', (
                    SELECT avg(inner_m.metric_value)
                    FROM pg13_hash_metrics inner_m
                    WHERE inner_m.tenant_id = m.tenant_id
                      AND inner_m.metric_name = m.metric_name
                      AND inner_m.hour_bucket = m.hour_bucket
                      AND inner_m.recorded_at::date = m.recorded_at::date - 1
                )
            )
        ) AS historical_context
) lateral_3 ON true
LEFT JOIN LATERAL (
    -- LATERAL 4: 相关性分析
    SELECT
        jsonb_object_agg(
            correlated_metric,
            round(correlation_coefficient::numeric, 4)
        ) AS correlation_metrics
    FROM (
        SELECT
            m2.metric_name AS correlated_metric,
            corr(m.metric_value, m2.metric_value) AS correlation_coefficient
        FROM pg13_hash_metrics m2
        WHERE m2.tenant_id = m.tenant_id
          AND m2.metric_name != m.metric_name
          AND m2.recorded_at BETWEEN
              m.recorded_at - INTERVAL '1 hour'
              AND m.recorded_at + INTERVAL '1 hour'
        GROUP BY m2.metric_name
        HAVING count(*) > 10
           AND abs(corr(m.metric_value, m2.metric_value)) > 0.5
    ) corr_results
) lateral_4 ON true
LEFT JOIN LATERAL (
    -- LATERAL 5: 简单预测边界
    SELECT
        jsonb_build_object(
            'lower_bound', lateral_1.hour_avg - 2 * lateral_1.hour_stddev,
            'upper_bound', lateral_1.hour_avg + 2 * lateral_1.hour_stddev,
            'trend_direction', CASE
                WHEN lateral_3.historical_context->>'rolling_avg_7d' IS NOT NULL
                 AND lateral_1.hour_avg > (lateral_3.historical_context->>'rolling_avg_7d')::numeric
                THEN 'INCREASING'
                WHEN lateral_3.historical_context->>'rolling_avg_7d' IS NOT NULL
                 AND lateral_1.hour_avg < (lateral_3.historical_context->>'rolling_avg_7d')::numeric
                THEN 'DECREASING'
                ELSE 'STABLE'
            END,
            'confidence', CASE
                WHEN lateral_1.hour_count > 100 THEN 'HIGH'
                WHEN lateral_1.hour_count > 30 THEN 'MEDIUM'
                ELSE 'LOW'
            END
        ) AS forecast_bounds
) lateral_5 ON true
WHERE m.recorded_at >= CURRENT_TIMESTAMP - INTERVAL '7 days'
  AND lateral_3.anomaly_level IN ('CRITICAL_OUTLIER', 'WARNING_OUTLIER')
ORDER BY
    abs(
        (m.metric_value - lateral_1.hour_avg) / NULLIF(lateral_1.hour_stddev, 0)
    ) DESC,
    m.recorded_at DESC
LIMIT 1000;

-- ============================================================================
-- Part 4: 极致嵌套子查询 - 7层深度
-- ============================================================================

SELECT
    outer_1.tenant_id,
    outer_1.anomaly_count,
    outer_1.top_anomaly_metric,
    (
        -- 第7层嵌套
        SELECT jsonb_agg(
            jsonb_build_object(
                'metric', extreme.metric_name,
                'max_zscore', extreme.max_zscore,
                'occurrences', extreme.occurrences,
                'related_metrics', (
                    -- 第6层嵌套
                    SELECT jsonb_agg(
                        jsonb_build_object(
                            'name', correlated_2.metric_name,
                            'correlation', correlated_2.corr_value
                        )
                    )
                    FROM (
                        -- 第5层嵌套
                        SELECT
                            m5.metric_name,
                            round(corr(m5.metric_value, extreme.metric_value)::numeric, 4) AS corr_value
                        FROM pg13_hash_metrics m5
                        WHERE m5.tenant_id = outer_1.tenant_id
                          AND m5.metric_name != extreme.metric_name
                          AND m5.recorded_at IN (
                            -- 第4层嵌套
                            SELECT m4.recorded_at
                            FROM pg13_hash_metrics m4
                            WHERE m4.tenant_id = outer_1.tenant_id
                              AND m4.metric_name = extreme.metric_name
                              AND m4.recorded_at IN (
                                -- 第3层嵌套
                                SELECT m3.recorded_at
                                FROM pg13_hash_metrics m3
                                WHERE m3.tenant_id = outer_1.tenant_id
                                  AND m3.metric_name = 'cpu_usage'
                                  AND m3.metric_value > (
                                    -- 第2层嵌套
                                    SELECT avg(m2.metric_value) * 1.5
                                    FROM pg13_hash_metrics m2
                                    WHERE m2.tenant_id = outer_1.tenant_id
                                      AND m2.metric_name = 'cpu_usage'
                                      AND m2.recorded_at >= (
                                        -- 第1层嵌套
                                        SELECT min(m1.recorded_at)
                                        FROM pg13_hash_metrics m1
                                        WHERE m1.tenant_id = outer_1.tenant_id
                                          AND m1.metric_name = 'cpu_usage'
                                          AND m1.recorded_at >= CURRENT_TIMESTAMP - INTERVAL '1 hour'
                                      )
                                  )
                              )
                          )
                        GROUP BY m5.metric_name
                        HAVING abs(corr(m5.metric_value, extreme.metric_value)) > 0.7
                        ORDER BY abs(corr(m5.metric_value, extreme.metric_value)) DESC
                        LIMIT 5
                    ) correlated_2
                )
            )
        )
        FROM (
            SELECT
                inner_agg.metric_name,
                max(inner_agg.z_score) AS max_zscore,
                count(*) AS occurrences,
                inner_agg.metric_value
            FROM (
                SELECT
                    metric_name,
                    (metric_value - avg(metric_value) OVER w) /
                        NULLIF(stddev(metric_value) OVER w, 0) AS z_score,
                    metric_value
                FROM pg13_hash_metrics
                WHERE tenant_id = outer_1.tenant_id
                  AND recorded_at >= CURRENT_TIMESTAMP - INTERVAL '24 hours'
                WINDOW w AS (
                    PARTITION BY metric_name
                    ORDER BY recorded_at
                    ROWS BETWEEN 50 PRECEDING AND 50 FOLLOWING
                )
            ) inner_agg
            WHERE abs(inner_agg.z_score) > 4
            GROUP BY inner_agg.metric_name, inner_agg.metric_value
            ORDER BY max(inner_agg.z_score) DESC
            LIMIT 10
        ) extreme
    ) AS extreme_anomalies
FROM (
    SELECT
        tenant_id,
        count(*) FILTER (
            WHERE abs(z_score) > 3
        ) AS anomaly_count,
        mode() WITHIN GROUP (ORDER BY metric_name) AS top_anomaly_metric,
        array_agg(DISTINCT metric_name ORDER BY metric_name) AS affected_metrics,
        jsonb_object_agg(
            severity,
            count
        ) AS severity_breakdown
    FROM (
        SELECT
            tenant_id,
            metric_name,
            (metric_value - avg(metric_value) OVER (
                PARTITION BY tenant_id, metric_name
                ORDER BY recorded_at
                ROWS BETWEEN 100 PRECEDING AND 100 FOLLOWING
            )) / NULLIF(stddev(metric_value) OVER (
                PARTITION BY tenant_id, metric_name
                ORDER BY recorded_at
                ROWS BETWEEN 100 PRECEDING AND 100 FOLLOWING
            ), 0) AS z_score,
            CASE
                WHEN abs(
                    (metric_value - avg(metric_value) OVER (
                        PARTITION BY tenant_id, metric_name
                        ORDER BY recorded_at
                        ROWS BETWEEN 100 PRECEDING AND 100 FOLLOWING
                    )) / NULLIF(stddev(metric_value) OVER (
                        PARTITION BY tenant_id, metric_name
                        ORDER BY recorded_at
                        ROWS BETWEEN 100 PRECEDING AND 100 FOLLOWING
                    ), 0)
                ) > 5 THEN 'EXTREME'
                WHEN abs(
                    (metric_value - avg(metric_value) OVER (
                        PARTITION BY tenant_id, metric_name
                        ORDER BY recorded_at
                        ROWS BETWEEN 100 PRECEDING AND 100 FOLLOWING
                    )) / NULLIF(stddev(metric_value) OVER (
                        PARTITION BY tenant_id, metric_name
                        ORDER BY recorded_at
                        ROWS BETWEEN 100 PRECEDING AND 100 FOLLOWING
                    ), 0)
                ) > 4 THEN 'SEVERE'
                WHEN abs(
                    (metric_value - avg(metric_value) OVER (
                        PARTITION BY tenant_id, metric_name
                        ORDER BY recorded_at
                        ROWS BETWEEN 100 PRECEDING AND 100 FOLLOWING
                    )) / NULLIF(stddev(metric_value) OVER (
                        PARTITION BY tenant_id, metric_name
                        ORDER BY recorded_at
                        ROWS BETWEEN 100 PRECEDING AND 100 FOLLOWING
                    ), 0)
                ) > 3 THEN 'MODERATE'
                ELSE 'NORMAL'
            END AS severity
        FROM pg13_hash_metrics
        WHERE recorded_at >= CURRENT_TIMESTAMP - INTERVAL '24 hours'
    ) anomaly_check
    WHERE severity != 'NORMAL'
    GROUP BY tenant_id
) outer_1
WHERE outer_1.anomaly_count > 5
ORDER BY outer_1.anomaly_count DESC;