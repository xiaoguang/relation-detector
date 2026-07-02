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

-- 复杂JSON构造函数等价查询 - 使用 jsonb_build_object/jsonb_agg
WITH event_construction AS (
    SELECT
        ae.user_id,
        ae.session_id,
        jsonb_build_object(
            'session_id', ae.session_id::text,
            'user_id', ae.user_id::text,
            'event_count', count(*),
            'first_event', min(ae.created_at)::text,
            'last_event', max(ae.created_at)::text
        ) AS session_summary
    FROM pg16_analytics_events ae
    GROUP BY ae.user_id, ae.session_id
)
SELECT
    ec.user_id,
    count(*) AS total_sessions,
    jsonb_agg(ec.session_summary ORDER BY ec.session_id) AS sessions_summary
FROM event_construction ec
GROUP BY ec.user_id;

-- Part 2: JSON 类型检查等价查询 - 使用 jsonb_typeof
-- ============================================================================

WITH json_type_check AS (
    SELECT
        ae.event_id,
        ae.event_data,
        jsonb_typeof(ae.event_data) AS event_data_type,
        ae.event_data->'metadata' AS metadata,
        ae.event_data->>'user_agent' AS user_agent,
        COALESCE(ae.event_data->'geo'->>'country', 'XX') AS country
    FROM pg16_analytics_events ae
    WHERE jsonb_typeof(ae.event_data) = 'object'
),
quality_analysis AS (
    SELECT
        COALESCE(jtc.metadata->>'source', 'unknown') AS source,
        jtc.country,
        count(*) AS event_count,
        count(*) FILTER (WHERE jsonb_typeof(jtc.metadata) = 'object') AS valid_metadata_events
    FROM json_type_check jtc
    GROUP BY COALESCE(jtc.metadata->>'source', 'unknown'), jtc.country
)
SELECT
    qa.source,
    qa.country,
    qa.event_count,
    round((qa.valid_metadata_events::numeric / NULLIF(qa.event_count, 0) * 100)::numeric, 2) AS data_quality_pct
FROM quality_analysis qa
ORDER BY qa.event_count DESC;

-- Part 3: JSON 聚合等价查询 - 使用 jsonb_agg/jsonb_build_object
-- ============================================================================

WITH event_sequences AS (
    SELECT
        ae.user_id,
        ae.session_id,
        jsonb_agg(
            jsonb_build_object(
                'event_id', ae2.event_id::text,
                'type', ae2.event_type,
                'timestamp', ae2.created_at::text
            )
            ORDER BY ae2.created_at
        ) AS event_sequence
    FROM pg16_analytics_events ae
    JOIN pg16_analytics_events ae2 ON ae.session_id = ae2.session_id
    WHERE ae.event_type = 'session_start'
    GROUP BY ae.user_id, ae.session_id
),
funnel_stats AS (
    SELECT
        count(*) AS total_sessions,
        count(*) FILTER (WHERE jsonb_array_length(es.event_sequence) > 0) AS sessions_with_events
    FROM event_sequences es
)
SELECT
    fs.total_sessions,
    fs.sessions_with_events,
    jsonb_build_object(
        'total_sessions', fs.total_sessions,
        'sessions_with_events', fs.sessions_with_events
    ) AS funnel_report
FROM funnel_stats fs;

