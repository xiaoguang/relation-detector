-- ============================================================================
-- PostgreSQL 16 复杂SQL测试用例
-- 特性: SQL/JSON构造函数(JSON_OBJECT, JSON_ARRAY, JSON_OBJECTAGG, JSON_ARRAYAGG),
--       IS JSON增强, 聚合函数增强, 并行哈希全连接
-- 目标: 最大语法嵌套深度, 最全语法覆盖
-- ============================================================================

-- ============================================================================
-- Part 1: SQL/JSON 构造函数 - JSON_OBJECT, JSON_ARRAY 深度嵌套
-- ============================================================================

CREATE TABLE IF NOT EXISTS pg16_analytics_events (
    event_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id UUID NOT NULL,
    session_id UUID NOT NULL,
    event_type TEXT NOT NULL,
    event_data JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- PG16新特性: GENERATED列中使用JSON构造函数
    event_summary JSONB GENERATED ALWAYS AS (
        JSON_OBJECT(
            'id': event_id::text,
            'type': event_type,
            'user': user_id::text,
            'timestamp': created_at::text,
            'page': JSON_VALUE(event_data, '$.page' RETURNING TEXT),
            'duration_ms': JSON_VALUE(event_data, '$.duration_ms' RETURNING NUMERIC)
        )
    ) STORED
);

CREATE TABLE IF NOT EXISTS pg16_user_profiles (
    user_id UUID PRIMARY KEY,
    profile_data JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 复杂JSON构造函数查询
WITH
-- 基础CTE: 使用JSON_OBJECT和JSON_ARRAY构造
event_construction AS (
    SELECT
        ae.user_id,
        ae.session_id,
        JSON_OBJECT(
            'session_id': ae.session_id::text,
            'user_id': ae.user_id::text,
            'event_count': count(*),
            'event_types': JSON_ARRAY(
                SELECT DISTINCT ae2.event_type
                FROM pg16_analytics_events ae2
                WHERE ae2.session_id = ae.session_id
                ORDER BY ae2.event_type
            ),
            'first_event': min(ae.created_at)::text,
            'last_event': max(ae.created_at)::text,
            'duration_seconds': round(
                extract(EPOCH FROM max(ae.created_at) - min(ae.created_at))::numeric, 2
            ),
            'pages_visited': JSON_ARRAY(
                SELECT DISTINCT JSON_VALUE(ae3.event_data, '$.page' RETURNING TEXT)
                FROM pg16_analytics_events ae3
                WHERE ae3.session_id = ae.session_id
                  AND JSON_VALUE(ae3.event_data, '$.page' RETURNING TEXT) IS NOT NULL
                ORDER BY JSON_VALUE(ae3.event_data, '$.page' RETURNING TEXT)
            ),
            'is_bounce': CASE WHEN count(*) = 1 THEN true ELSE false END,
            'conversion_events': JSON_ARRAY(
                SELECT JSON_OBJECT(
                    'type': ae4.event_type,
                    'timestamp': ae4.created_at::text,
                    'value': JSON_VALUE(ae4.event_data, '$.conversion_value' RETURNING NUMERIC)
                )
                FROM pg16_analytics_events ae4
                WHERE ae4.session_id = ae.session_id
                  AND ae4.event_type IN ('purchase', 'signup', 'subscribe', 'add_to_cart')
                ORDER BY ae4.created_at
            )
        ) AS session_summary
    FROM pg16_analytics_events ae
    GROUP BY ae.user_id, ae.session_id
),
-- 第二层CTE: JSON_OBJECTAGG聚合
user_aggregation AS (
    SELECT
        ec.user_id,
        count(*) AS total_sessions,
        JSON_OBJECTAGG(
            'session_' || row_number() OVER (
                PARTITION BY ec.user_id ORDER BY (ec.session_summary->>'first_event')::timestamptz
            )::text:
            ec.session_summary
        ) AS sessions_detail,
        JSON_OBJECTAGG(
            ec.session_id::text: ec.session_summary
        ) FILTER (WHERE (ec.session_summary->>'is_bounce')::boolean = false) AS non_bounce_sessions,
        JSON_ARRAYAGG(
            ec.session_summary
            ORDER BY (ec.session_summary->>'first_event')::timestamptz
        ) AS sessions_ordered,
        JSON_ARRAYAGG(
            JSON_OBJECT(
                'session': ec.session_id::text,
                'events': (ec.session_summary->>'event_count')::int,
                'duration': (ec.session_summary->>'duration_seconds')::numeric,
                'converted': CASE
                    WHEN jsonb_array_length(ec.session_summary->'conversion_events') > 0
                    THEN true ELSE false
                END
            )
            ORDER BY (ec.session_summary->>'first_event')::timestamptz
        ) AS sessions_summary
    FROM event_construction ec
    GROUP BY ec.user_id
),
-- 第三层CTE: 用户画像关联
enriched_users AS (
    SELECT
        ua.*,
        up.profile_data,
        JSON_OBJECT(
            'user_id': ua.user_id::text,
            'profile': up.profile_data,
            'segment': CASE
                WHEN ua.total_sessions > 100 THEN 'POWER_USER'
                WHEN ua.total_sessions > 20 THEN 'ACTIVE_USER'
                WHEN ua.total_sessions > 5 THEN 'CASUAL_USER'
                ELSE 'NEW_USER'
            END,
            'lifetime_stats': JSON_OBJECT(
                'total_sessions': ua.total_sessions,
                'bounce_rate': round(
                    (
                        (SELECT count(*) FROM event_construction ec2
                         WHERE ec2.user_id = ua.user_id
                           AND (ec2.session_summary->>'is_bounce')::boolean = true
                        )::numeric / NULLIF(ua.total_sessions, 0) * 100
                    )::numeric, 2
                ),
                'conversion_rate': round(
                    (
                        (SELECT count(*) FROM event_construction ec3
                         WHERE ec3.user_id = ua.user_id
                           AND jsonb_array_length(ec3.session_summary->'conversion_events') > 0
                        )::numeric / NULLIF(ua.total_sessions, 0) * 100
                    )::numeric, 2
                ),
                'avg_session_duration': (
                    SELECT round(avg(
                        (ec4.session_summary->>'duration_seconds')::numeric
                    )::numeric, 2)
                    FROM event_construction ec4
                    WHERE ec4.user_id = ua.user_id
                ),
                'favorite_pages': (
                    SELECT JSON_ARRAYAGG(
                        JSON_OBJECT(
                            'page': page,
                            'visits': visit_count
                        )
                        ORDER BY visit_count DESC
                    )
                    FROM (
                        SELECT
                            JSON_VALUE(ae5.event_data, '$.page' RETURNING TEXT) AS page,
                            count(*) AS visit_count
                        FROM pg16_analytics_events ae5
                        WHERE ae5.user_id = ua.user_id
                          AND JSON_VALUE(ae5.event_data, '$.page' RETURNING TEXT) IS NOT NULL
                        GROUP BY JSON_VALUE(ae5.event_data, '$.page' RETURNING TEXT)
                        ORDER BY count(*) DESC
                        LIMIT 10
                    ) page_stats
                )
            ),
            'created_at': up.created_at::text,
            'last_activity': (
                SELECT max(ae6.created_at)::text
                FROM pg16_analytics_events ae6
                WHERE ae6.user_id = ua.user_id
            )
        ) AS user_report
    FROM user_aggregation ua
    JOIN pg16_user_profiles up ON ua.user_id = up.user_id
),
-- 第四层CTE: 跨用户分析
cohort_analysis AS (
    SELECT
        eu.user_report->>'segment' AS user_segment,
        count(*) AS user_count,
        round(avg((eu.user_report->'lifetime_stats'->>'total_sessions')::numeric)::numeric, 2)
            AS avg_sessions,
        round(avg((eu.user_report->'lifetime_stats'->>'bounce_rate')::numeric)::numeric, 2)
            AS avg_bounce_rate,
        round(avg((eu.user_report->'lifetime_stats'->>'conversion_rate')::numeric)::numeric, 2)
            AS avg_conversion_rate,
        round(avg((eu.user_report->'lifetime_stats'->>'avg_session_duration')::numeric)::numeric, 2)
            AS avg_session_duration,
        JSON_OBJECTAGG(
            eu.user_report->>'segment':
            JSON_OBJECT(
                'count': count(*),
                'avg_sessions': round(avg(
                    (eu.user_report->'lifetime_stats'->>'total_sessions')::numeric
                )::numeric, 2),
                'avg_conversion': round(avg(
                    (eu.user_report->'lifetime_stats'->>'conversion_rate')::numeric
                )::numeric, 2)
            )
        ) AS segment_summary,
        JSON_ARRAYAGG(
            JSON_OBJECT(
                'user_id': eu.user_id::text,
                'sessions': (eu.user_report->'lifetime_stats'->>'total_sessions')::int,
                'conversion_rate': (eu.user_report->'lifetime_stats'->>'conversion_rate')::numeric,
                'favorite_pages': eu.user_report->'lifetime_stats'->'favorite_pages'
            )
            ORDER BY (eu.user_report->'lifetime_stats'->>'total_sessions')::int DESC
        ) FILTER (
            WHERE (eu.user_report->'lifetime_stats'->>'conversion_rate')::numeric > 10
        ) AS high_converting_users
    FROM enriched_users eu
    GROUP BY eu.user_report->>'segment'
)
-- 主查询
SELECT
    ca.user_segment,
    ca.user_count,
    ca.avg_sessions,
    ca.avg_bounce_rate,
    ca.avg_conversion_rate,
    ca.avg_session_duration,
    ca.high_converting_users,
    (
        SELECT JSON_OBJECT(
            'total_users': sum(ca2.user_count),
            'overall_avg_conversion': round(avg(ca2.avg_conversion_rate)::numeric, 2),
            'best_segment': (
                SELECT ca3.user_segment
                FROM cohort_analysis ca3
                ORDER BY ca3.avg_conversion_rate DESC
                LIMIT 1
            ),
            'segment_distribution': JSON_OBJECTAGG(
                ca2.user_segment: JSON_OBJECT(
                    'count': ca2.user_count,
                    'percentage': round(
                        (ca2.user_count::numeric / NULLIF(sum(ca2.user_count) OVER(), 0) * 100)::numeric,
                        2
                    )
                )
            )
        )
        FROM cohort_analysis ca2
    ) AS global_summary
FROM cohort_analysis ca
ORDER BY ca.avg_conversion_rate DESC;

-- ============================================================================
-- Part 2: IS JSON 增强 - 多种JSON类型检查 + 嵌套
-- ============================================================================

WITH
json_type_check AS (
    SELECT
        ae.event_id,
        ae.event_data,
        ae.event_data IS JSON AS is_json,
        ae.event_data IS JSON OBJECT AS is_object,
        ae.event_data IS JSON ARRAY AS is_array,
        ae.event_data IS JSON SCALAR AS is_scalar,
        ae.event_data IS JSON OBJECT WITH UNIQUE KEYS AS has_unique_keys,
        -- 嵌套检查
        JSON_QUERY(ae.event_data, '$.metadata' RETURNING JSONB) IS JSON AS metadata_is_json,
        JSON_QUERY(ae.event_data, '$.metadata' RETURNING JSONB) IS JSON OBJECT AS metadata_is_object,
        JSON_QUERY(ae.event_data, '$.items' RETURNING JSONB) IS JSON ARRAY AS items_is_array,
        JSON_QUERY(ae.event_data, '$.tags' RETURNING JSONB) IS JSON ARRAY AS tags_is_array,
        JSON_QUERY(ae.event_data, '$.metadata' RETURNING JSONB)
            IS JSON OBJECT WITH UNIQUE KEYS AS metadata_unique_keys,
        -- 值提取
        JSON_VALUE(ae.event_data, '$.metadata.version' RETURNING TEXT DEFAULT '1.0') AS metadata_version,
        JSON_VALUE(ae.event_data, '$.metadata.source' RETURNING TEXT DEFAULT 'unknown') AS source,
        JSON_VALUE(ae.event_data, '$.user_agent' RETURNING TEXT) AS user_agent,
        JSON_VALUE(ae.event_data, '$.geo.country' RETURNING TEXT DEFAULT 'XX') AS country,
        JSON_QUERY(ae.event_data, '$.metadata.custom_fields' RETURNING JSONB) AS custom_fields
    FROM pg16_analytics_events ae
    WHERE ae.event_data IS JSON OBJECT
),
-- 数据质量分析
quality_analysis AS (
    SELECT
        jtc.source,
        jtc.country,
        count(*) AS event_count,
        count(*) FILTER (WHERE jtc.has_unique_keys) AS unique_key_events,
        count(*) FILTER (WHERE jtc.is_object) AS object_events,
        count(*) FILTER (WHERE jtc.metadata_is_json AND jtc.metadata_is_object) AS valid_metadata_events,
        count(*) FILTER (WHERE jtc.tags_is_array) AS events_with_tags,
        count(*) FILTER (WHERE jtc.items_is_array) AS events_with_items,
        count(*) FILTER (
            WHERE jtc.metadata_unique_keys
        ) AS unique_metadata_events,
        count(*) FILTER (
            WHERE jtc.custom_fields IS JSON OBJECT
              AND jtc.custom_fields IS JSON OBJECT WITH UNIQUE KEYS
        ) AS valid_custom_fields,
        JSON_OBJECTAGG(
            coalesce(jtc.metadata_version, 'unknown'):
            JSON_OBJECT(
                'count': count(*),
                'pct': round(
                    (count(*)::numeric / NULLIF(
                        sum(count(*)) OVER (PARTITION BY jtc.source), 0
                    ) * 100)::numeric, 2
                )
            )
        ) FILTER (WHERE jtc.metadata_version IS NOT NULL) AS version_distribution
    FROM json_type_check jtc
    GROUP BY jtc.source, jtc.country
)
-- 主查询
SELECT
    qa.source,
    qa.country,
    qa.event_count,
    round(
        (qa.valid_metadata_events::numeric / NULLIF(qa.event_count, 0) * 100)::numeric, 2
    ) AS data_quality_pct,
    round(
        (qa.events_with_tags::numeric / NULLIF(qa.event_count, 0) * 100)::numeric, 2
    ) AS tag_coverage_pct,
    qa.version_distribution,
    (
        SELECT JSON_OBJECT(
            'total_events': sum(qa2.event_count),
            'sources': count(DISTINCT qa2.source),
            'countries': count(DISTINCT qa2.country),
            'overall_quality': round(
                (sum(qa2.valid_metadata_events)::numeric /
                 NULLIF(sum(qa2.event_count), 0) * 100)::numeric, 2
            ),
            'sources_with_issues': JSON_ARRAY(
                SELECT DISTINCT qa3.source
                FROM quality_analysis qa3
                WHERE (qa3.valid_metadata_events::numeric / NULLIF(qa3.event_count, 0) * 100) < 80
                ORDER BY qa3.source
            )
        )
        FROM quality_analysis qa2
    ) AS global_quality_report
FROM quality_analysis qa
ORDER BY qa.event_count DESC;

-- ============================================================================
-- Part 3: JSON_ARRAYAGG 与 JSON_OBJECTAGG 深度嵌套聚合
-- ============================================================================

WITH
-- 基础CTE: 事件序列化
event_sequences AS (
    SELECT
        ae.user_id,
        ae.session_id,
        JSON_ARRAYAGG(
            JSON_OBJECT(
                'event_id': ae2.event_id::text,
                'type': ae2.event_type,
                'timestamp': ae2.created_at::text,
                'page': JSON_VALUE(ae2.event_data, '$.page' RETURNING TEXT),
                'duration_ms': JSON_VALUE(ae2.event_data, '$.duration_ms' RETURNING NUMERIC),
                'scroll_depth': JSON_VALUE(ae2.event_data, '$.scroll_depth' RETURNING NUMERIC),
                'interactions': JSON_QUERY(ae2.event_data, '$.interactions' RETURNING JSONB),
                'next_event': lead(ae2.event_type) OVER (
                    PARTITION BY ae2.session_id ORDER BY ae2.created_at
                ),
                'time_to_next_ms': round(
                    extract(EPOCH FROM
                        lead(ae2.created_at) OVER (
                            PARTITION BY ae2.session_id ORDER BY ae2.created_at
                        ) - ae2.created_at
                    ) * 1000::numeric, 2
                )
            )
            ORDER BY ae2.created_at
        ) AS event_sequence
    FROM pg16_analytics_events ae
    JOIN pg16_analytics_events ae2 ON ae.session_id = ae2.session_id
    WHERE ae.event_type = 'session_start'
    GROUP BY ae.user_id, ae.session_id
),
-- 第二层CTE: session漏斗分析
funnel_analysis AS (
    SELECT
        es.user_id,
        es.session_id,
        es.event_sequence,
        jsonb_array_length(es.event_sequence) AS event_count,
        -- 漏斗步骤
        EXISTS (
            SELECT 1 FROM jsonb_array_elements(es.event_sequence) AS ev
            WHERE ev->>'type' = 'page_view'
        ) AS has_page_view,
        EXISTS (
            SELECT 1 FROM jsonb_array_elements(es.event_sequence) AS ev
            WHERE ev->>'type' = 'product_view'
        ) AS has_product_view,
        EXISTS (
            SELECT 1 FROM jsonb_array_elements(es.event_sequence) AS ev
            WHERE ev->>'type' = 'add_to_cart'
        ) AS has_add_to_cart,
        EXISTS (
            SELECT 1 FROM jsonb_array_elements(es.event_sequence) AS ev
            WHERE ev->>'type' = 'checkout_start'
        ) AS has_checkout_start,
        EXISTS (
            SELECT 1 FROM jsonb_array_elements(es.event_sequence) AS ev
            WHERE ev->>'type' = 'purchase'
        ) AS has_purchase,
        -- 时间度量
        (
            SELECT min((ev->>'timestamp')::timestamptz)
            FROM jsonb_array_elements(es.event_sequence) AS ev
        ) AS session_start,
        (
            SELECT max((ev->>'timestamp')::timestamptz)
            FROM jsonb_array_elements(es.event_sequence) AS ev
        ) AS session_end
    FROM event_sequences es
),
-- 第三层CTE: 漏斗统计
funnel_stats AS (
    SELECT
        count(*) AS total_sessions,
        count(*) FILTER (WHERE has_page_view) AS page_view_sessions,
        count(*) FILTER (WHERE has_product_view) AS product_view_sessions,
        count(*) FILTER (WHERE has_add_to_cart) AS add_to_cart_sessions,
        count(*) FILTER (WHERE has_checkout_start) AS checkout_sessions,
        count(*) FILTER (WHERE has_purchase) AS purchase_sessions,
        round(
            (count(*) FILTER (WHERE has_product_view)::numeric /
             NULLIF(count(*) FILTER (WHERE has_page_view), 0) * 100)::numeric, 2
        ) AS page_to_product_pct,
        round(
            (count(*) FILTER (WHERE has_add_to_cart)::numeric /
             NULLIF(count(*) FILTER (WHERE has_product_view), 0) * 100)::numeric, 2
        ) AS product_to_cart_pct,
        round(
            (count(*) FILTER (WHERE has_checkout_start)::numeric /
             NULLIF(count(*) FILTER (WHERE has_add_to_cart), 0) * 100)::numeric, 2
        ) AS cart_to_checkout_pct,
        round(
            (count(*) FILTER (WHERE has_purchase)::numeric /
             NULLIF(count(*) FILTER (WHERE has_checkout_start), 0) * 100)::numeric, 2
        ) AS checkout_to_purchase_pct,
        round(
            (count(*) FILTER (WHERE has_purchase)::numeric /
             NULLIF(count(*), 0) * 100)::numeric, 2
        ) AS overall_conversion_pct
    FROM funnel_analysis
)
-- 主查询
SELECT
    JSON_OBJECT(
        'funnel': JSON_OBJECT(
            'total_sessions': fs.total_sessions,
            'page_view': JSON_OBJECT(
                'count': fs.page_view_sessions,
                'pct': 100.00
            ),
            'product_view': JSON_OBJECT(
                'count': fs.product_view_sessions,
                'pct': fs.page_to_product_pct
            ),
            'add_to_cart': JSON_OBJECT(
                'count': fs.add_to_cart_sessions,
                'pct': fs.product_to_cart_pct
            ),
            'checkout_start': JSON_OBJECT(
                'count': fs.checkout_sessions,
                'pct': fs.cart_to_checkout_pct
            ),
            'purchase': JSON_OBJECT(
                'count': fs.purchase_sessions,
                'pct': fs.checkout_to_purchase_pct
            ),
            'overall_conversion': fs.overall_conversion_pct
        ),
        'drop_off_analysis': JSON_OBJECT(
            'page_to_product_drop': 100.00 - fs.page_to_product_pct,
            'product_to_cart_drop': 100.00 - fs.product_to_cart_pct,
            'cart_to_checkout_drop': 100.00 - fs.cart_to_checkout_pct,
            'checkout_to_purchase_drop': 100.00 - fs.checkout_to_purchase_pct,
            'biggest_drop_point': (
                SELECT CASE
                    WHEN (100.00 - fs.page_to_product_pct) >=
                         GREATEST(100.00 - fs.product_to_cart_pct,
                                  100.00 - fs.cart_to_checkout_pct,
                                  100.00 - fs.checkout_to_purchase_pct)
                    THEN 'page_view -> product_view'
                    WHEN (100.00 - fs.product_to_cart_pct) >=
                         GREATEST(100.00 - fs.page_to_product_pct,
                                  100.00 - fs.cart_to_checkout_pct,
                                  100.00 - fs.checkout_to_purchase_pct)
                    THEN 'product_view -> add_to_cart'
                    WHEN (100.00 - fs.cart_to_checkout_pct) >=
                         GREATEST(100.00 - fs.page_to_product_pct,
                                  100.00 - fs.product_to_cart_pct,
                                  100.00 - fs.checkout_to_purchase_pct)
                    THEN 'add_to_cart -> checkout'
                    ELSE 'checkout -> purchase'
                END
            )
        ),
        'sample_sessions': JSON_ARRAY(
            SELECT JSON_OBJECT(
                'user_id': fa.user_id::text,
                'session_id': fa.session_id::text,
                'events': fa.event_sequence,
                'converted': fa.has_purchase
            )
            FROM funnel_analysis fa
            WHERE fa.has_purchase
            ORDER BY fa.session_start
            LIMIT 5
        )
    ) AS funnel_report
FROM funnel_stats fs;