-- ============================================================================
-- PostgreSQL 14 复杂SQL测试用例
-- 特性: 多范围类型, JSON下标访问, 存储过程OUT参数, 扩展查询管道
-- 目标: 最大语法嵌套深度, 最全语法覆盖
-- ============================================================================

-- ============================================================================
-- Part 1: 多范围类型 (multirange) 复杂操作
-- ============================================================================

-- 创建多范围类型测试表
CREATE TABLE IF NOT EXISTS pg14_room_bookings (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    room_id INT NOT NULL,
    booked_during TSRANGE NOT NULL,
    attendees JSONB NOT NULL DEFAULT '[]',
    EXCLUDE USING gist (room_id WITH =, booked_during WITH &&)
);

CREATE TABLE IF NOT EXISTS pg14_price_windows (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id INT NOT NULL,
    price NUMERIC(10,2) NOT NULL,
    date_ranges INT4MULTIRANGE NOT NULL,
    applicable_days INT4RANGE NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'
);

-- 多范围聚合查询
WITH
-- 基础CTE: 展开多范围
range_expansion AS (
    SELECT
        pw.id,
        pw.product_id,
        pw.price,
        unnest(pw.date_ranges) AS single_range,
        pw.date_ranges AS full_multirange,
        pw.applicable_days,
        pw.metadata,
        lower(unnest(pw.date_ranges)) AS range_start,
        upper(unnest(pw.date_ranges)) AS range_end,
        upper(unnest(pw.date_ranges)) - lower(unnest(pw.date_ranges)) AS range_length
    FROM pg14_price_windows pw
),
-- 第二层CTE: 范围聚合
range_aggregation AS (
    SELECT
        re.product_id,
        re.price,
        count(DISTINCT re.single_range) AS range_count,
        range_agg(re.single_range) AS merged_ranges,
        int4multirange(
            VARIADIC array_agg(DISTINCT re.single_range ORDER BY re.single_range)
        ) AS reconstructed_multirange,
        min(re.range_start) AS earliest_start,
        max(re.range_end) AS latest_end,
        sum(re.range_length) AS total_days_covered,
        re.full_multirange,
        re.applicable_days,
        re.metadata
    FROM range_expansion re
    GROUP BY re.product_id, re.price, re.full_multirange, re.applicable_days, re.metadata
),
-- 第三层CTE: 窗口函数与范围运算
range_analysis AS (
    SELECT
        ra.*,
        ra.merged_ranges * ra.full_multirange AS range_intersection,
        ra.merged_ranges + ra.full_multirange AS range_union,
        ra.merged_ranges - ra.full_multirange AS range_difference,
        isempty(ra.merged_ranges * ra.full_multirange) AS has_no_overlap,
        ra.merged_ranges @> ra.full_multirange AS merged_contains_full,
        ra.merged_ranges <@ ra.full_multirange AS merged_contained_by_full,
        row_number() OVER (
            PARTITION BY ra.product_id
            ORDER BY ra.total_days_covered DESC
        ) AS coverage_rank,
        sum(ra.total_days_covered) OVER (
            PARTITION BY ra.product_id
            ORDER BY ra.price
        ) AS cumulative_days,
        first_value(ra.price) OVER (
            PARTITION BY ra.product_id
            ORDER BY ra.total_days_covered DESC
        ) AS best_coverage_price,
        nth_value(ra.price, 2) OVER (
            PARTITION BY ra.product_id
            ORDER BY ra.total_days_covered DESC
        ) AS second_best_price
    FROM range_aggregation ra
),
-- 第四层CTE: 多范围复杂比较
overlap_analysis AS (
    SELECT
        ra1.product_id AS product_1,
        ra2.product_id AS product_2,
        ra1.merged_ranges AS ranges_1,
        ra2.merged_ranges AS ranges_2,
        ra1.merged_ranges && ra2.merged_ranges AS ranges_overlap,
        ra1.merged_ranges -|- ra2.merged_ranges AS ranges_adjacent,
        ra1.merged_ranges & ra2.merged_ranges AS ranges_intersection,
        cardinality(ra1.merged_ranges & ra2.merged_ranges) AS overlap_ranges_count,
        (
            SELECT jsonb_agg(
                jsonb_build_object(
                    'range', r,
                    'starts', lower(r),
                    'ends', upper(r)
                )
                ORDER BY lower(r)
            )
            FROM unnest(ra1.merged_ranges & ra2.merged_ranges) AS r
        ) AS overlap_details
    FROM range_aggregation ra1
    CROSS JOIN range_aggregation ra2
    WHERE ra1.product_id < ra2.product_id
      AND ra1.merged_ranges && ra2.merged_ranges
)
-- 主查询
SELECT
    oa.product_1,
    oa.product_2,
    oa.overlap_ranges_count,
    oa.ranges_adjacent,
    oa.overlap_details,
    (
        SELECT jsonb_build_object(
            'product_1_total_days', sum(ra1.total_days_covered),
            'product_2_total_days', sum(ra2.total_days_covered),
            'product_1_best_price', min(ra1.best_coverage_price),
            'product_2_best_price', min(ra2.best_coverage_price)
        )
        FROM range_analysis ra1
        CROSS JOIN range_analysis ra2
        WHERE ra1.product_id = oa.product_1
          AND ra2.product_id = oa.product_2
    ) AS summary_stats
FROM overlap_analysis oa
WHERE oa.overlap_ranges_count > 3
ORDER BY oa.overlap_ranges_count DESC;

-- ============================================================================
-- Part 2: JSON下标访问 (PG14 新特性) 深度嵌套
-- ============================================================================

WITH
-- 基础CTE: JSON下标操作
json_subscript_data AS (
    SELECT
        rb.id,
        rb.room_id,
        rb.attendees['0'] AS first_attendee,
        rb.attendees['0']['name'] AS first_attendee_name,
        rb.attendees['0']['contact'] AS first_attendee_contact,
        rb.attendees['0']['contact']['email'] AS first_attendee_email,
        rb.attendees AS attendees_slice,
        jsonb_array_length(rb.attendees) AS total_attendees,
        rb.booked_during,
        lower(rb.booked_during) AS booking_start,
        upper(rb.booked_during) AS booking_end
    FROM pg14_room_bookings rb
    WHERE rb.attendees IS NOT NULL
      AND jsonb_array_length(rb.attendees) > 0
),
-- 第二层CTE: 展开JSON数组下标
expanded_attendees AS (
    SELECT
        jsd.id,
        jsd.room_id,
        jsd.booking_start,
        jsd.booking_end,
        jsd.total_attendees,
        att.pos AS attendee_index,
        att.value AS attendee_data,
        att.value['name'] AS attendee_name,
        att.value['email'] AS attendee_email,
        att.value['contact']['phone'] AS attendee_phone,
        att.value['preferences'] AS attendee_preferences,
        att.value['preferences']['dietary'] AS dietary_requirements,
        att.value['preferences']['seating'] AS seating_preference,
        att.value['preferences']['av_requirements'] AS av_requirements
    FROM json_subscript_data jsd
    LEFT JOIN LATERAL (
        SELECT pos, value
        FROM jsonb_array_elements(jsd.attendees_slice) WITH ORDINALITY AS elem(value, pos)
    ) att ON true
),
-- 第三层CTE: 聚合JSON下标结果
room_attendee_summary AS (
    SELECT
        ea.room_id,
        count(DISTINCT ea.id) AS total_bookings,
        count(DISTINCT ea.attendee_name) AS unique_attendees,
        array_agg(DISTINCT ea.attendee_name ORDER BY ea.attendee_name) AS attendee_list,
        string_agg(DISTINCT ea.dietary_requirements, ', ' ORDER BY ea.dietary_requirements)
            FILTER (WHERE ea.dietary_requirements IS NOT NULL AND ea.dietary_requirements::text != '""')
            AS all_dietary_needs,
        jsonb_object_agg(
            coalesce(ea.seating_preference::text, '"unspecified"'),
            count(*)::text
        ) AS seating_distribution,
        jsonb_agg(
            DISTINCT jsonb_build_object(
                'booking_id', ea.id,
                'attendee', ea.attendee_name,
                'email', ea.attendee_email,
                'phone', ea.attendee_phone,
                'dietary', ea.dietary_requirements,
                'seating', ea.seating_preference,
                'av', ea.av_requirements
            )
            ORDER BY ea.booking_start, ea.attendee_index
        ) FILTER (WHERE ea.attendee_name IS NOT NULL) AS detailed_attendees,
        tsrange(
            min(ea.booking_start),
            max(ea.booking_end),
            '[]'
        ) AS total_booking_window
    FROM expanded_attendees ea
    GROUP BY ea.room_id
)
-- 主查询: JSON下标与多范围结合
SELECT
    ras.room_id,
    ras.total_bookings,
    ras.unique_attendees,
    ras.all_dietary_needs,
    ras.seating_distribution,
    ras.total_booking_window,
    ras.detailed_attendees,
    -- 嵌套子查询: 检查冲突预订
    (
        SELECT jsonb_agg(
            jsonb_build_object(
                'booking_1', conflict.id,
                'booking_2', conflict2.id,
                'overlap_period', conflict.booked_during * conflict2.booked_during
            )
        )
        FROM pg14_room_bookings conflict
        JOIN pg14_room_bookings conflict2
            ON conflict.room_id = conflict2.room_id
           AND conflict.id < conflict2.id
           AND conflict.booked_during && conflict2.booked_during
        WHERE conflict.room_id = ras.room_id
        LIMIT 10
    ) AS booking_conflicts,
    -- 嵌套: 价格窗口匹配
    (
        SELECT jsonb_agg(
            jsonb_build_object(
                'price', pw.price,
                'overlap_days', upper(pw.date_ranges * int4multirange(
                    int4range(
                        lower(ras.total_booking_window)::date - '2000-01-01'::date,
                        upper(ras.total_booking_window)::date - '2000-01-01'::date
                    )::int4range
                )) - lower(pw.date_ranges * int4multirange(
                    int4range(
                        lower(ras.total_booking_window)::date - '2000-01-01'::date,
                        upper(ras.total_booking_window)::date - '2000-01-01'::date
                    )::int4range
                ))
            )
            ORDER BY pw.price
        )
        FROM pg14_price_windows pw
        WHERE pw.date_ranges && int4multirange(
            int4range(
                lower(ras.total_booking_window)::date - '2000-01-01'::date,
                upper(ras.total_booking_window)::date - '2000-01-01'::date
            )::int4range
        )
    ) AS applicable_prices
FROM room_attendee_summary ras
WHERE ras.total_bookings > 0
ORDER BY ras.total_bookings DESC, ras.room_id;

-- ============================================================================
-- Part 3: 存储过程与OUT参数 (PG14增强)
-- ============================================================================

CREATE OR REPLACE PROCEDURE pg14_complex_analysis(
    IN p_room_id INT,
    IN p_start_date DATE,
    IN p_end_date DATE,
    OUT p_total_bookings INT,
    OUT p_utilization_pct NUMERIC,
    OUT p_peak_attendees INT,
    OUT p_booking_summary JSONB,
    OUT p_revenue_estimate NUMERIC,
    OUT p_conflict_report JSONB,
    OUT p_recommendations JSONB
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_working_days INT;
    v_total_slots INT;
    v_conflicts INT;
    v_avg_attendees NUMERIC;
    v_price_range NUMERIC;
BEGIN
    -- 计算工作日
    SELECT count(*) INTO v_working_days
    FROM generate_series(p_start_date, p_end_date, '1 day'::interval) d
    WHERE extract(DOW FROM d) NOT IN (0, 6);

    -- 总预订数和利用率
    SELECT
        count(*),
        round(
            (count(*)::numeric / NULLIF(v_working_days * 8, 0) * 100)::numeric,
            2
        ),
        coalesce(max(jsonb_array_length(attendees)), 0),
        coalesce(round(avg(jsonb_array_length(attendees))::numeric, 2), 0)
    INTO
        p_total_bookings,
        p_utilization_pct,
        p_peak_attendees,
        v_avg_attendees
    FROM pg14_room_bookings
    WHERE room_id = p_room_id
      AND booked_during && tsrange(p_start_date::timestamp, p_end_date::timestamp);

    -- 预订摘要JSON
    SELECT jsonb_agg(
        jsonb_build_object(
            'booking_id', rb.id,
            'start', lower(rb.booked_during),
            'end', upper(rb.booked_during),
            'duration_hours', round(
                extract(EPOCH FROM upper(rb.booked_during) - lower(rb.booked_during)) / 3600::numeric,
                2
            ),
            'attendees_count', jsonb_array_length(rb.attendees),
            'attendees', rb.attendees
        )
        ORDER BY lower(rb.booked_during)
    ) INTO p_booking_summary
    FROM pg14_room_bookings rb
    WHERE rb.room_id = p_room_id
      AND rb.booked_during && tsrange(p_start_date::timestamp, p_end_date::timestamp);

    -- 冲突报告
    SELECT jsonb_agg(
        jsonb_build_object(
            'booking_a', c1.id,
            'booking_b', c2.id,
            'overlap', c1.booked_during * c2.booked_during,
            'overlap_hours', round(
                extract(EPOCH FROM upper(c1.booked_during * c2.booked_during) -
                    lower(c1.booked_during * c2.booked_during)) / 3600::numeric, 2
            )
        )
    ) INTO p_conflict_report
    FROM pg14_room_bookings c1
    JOIN pg14_room_bookings c2
        ON c1.room_id = c2.room_id
       AND c1.id < c2.id
       AND c1.booked_during && c2.booked_during
    WHERE c1.room_id = p_room_id
      AND c1.booked_during && tsrange(p_start_date::timestamp, p_end_date::timestamp);

    -- 收入估算
    SELECT coalesce(sum(pw.price), 0) INTO p_revenue_estimate
    FROM pg14_price_windows pw
    WHERE pw.date_ranges && int4multirange(
        int4range(
            p_start_date - '2000-01-01'::date,
            p_end_date - '2000-01-01'::date
        )::int4range
    );

    -- 建议
    p_recommendations := jsonb_build_object(
        'utilization_status', CASE
            WHEN p_utilization_pct > 80 THEN 'OVERBOOKED'
            WHEN p_utilization_pct > 50 THEN 'HEALTHY'
            WHEN p_utilization_pct > 20 THEN 'LOW'
            ELSE 'UNDERUTILIZED'
        END,
        'suggested_price_adjustment', CASE
            WHEN p_utilization_pct > 80 THEN '+15%'
            WHEN p_utilization_pct > 50 THEN '+5%'
            WHEN p_utilization_pct < 20 THEN '-10%'
            ELSE 'STABLE'
        END,
        'peak_hours', (
            SELECT array_agg(hour ORDER BY cnt DESC)
            FROM (
                SELECT extract(HOUR FROM lower(booked_during)) AS hour,
                       count(*) AS cnt
                FROM pg14_room_bookings
                WHERE room_id = p_room_id
                GROUP BY extract(HOUR FROM lower(booked_during))
                ORDER BY cnt DESC
                LIMIT 3
            ) peak
        ),
        'conflict_count', (
            SELECT count(*)
            FROM pg14_room_bookings c1
            JOIN pg14_room_bookings c2
                ON c1.room_id = c2.room_id
               AND c1.id < c2.id
               AND c1.booked_during && c2.booked_during
            WHERE c1.room_id = p_room_id
        )
    );
END;
$$;

-- 调用存储过程
CALL pg14_complex_analysis(
    101,
    '2024-01-01',
    '2024-12-31',
    NULL, NULL, NULL, NULL, NULL, NULL, NULL
);

-- ============================================================================
-- Part 4: 嵌套查询管道 - 多层CTE + 子查询 + 范围操作
-- ============================================================================

WITH RECURSIVE
-- 递归CTE: 时间槽生成
time_slots AS (
    SELECT
        '2024-01-01 00:00:00'::timestamp AS slot_start,
        '2024-01-01 01:00:00'::timestamp AS slot_end,
        0 AS depth
    UNION ALL
    SELECT
        ts.slot_end,
        ts.slot_end + INTERVAL '1 hour',
        ts.depth + 1
    FROM time_slots ts
    WHERE ts.slot_end < '2024-12-31 23:00:00'::timestamp
),
-- 第二层CTE: 每个时间槽的预订情况
slot_bookings AS (
    SELECT
        ts.slot_start,
        ts.slot_end,
        tsrange(ts.slot_start, ts.slot_end) AS slot_range,
        ts.depth,
        rb.room_id,
        rb.id AS booking_id,
        rb.booked_during,
        rb.attendees,
        CASE
            WHEN rb.booked_during && tsrange(ts.slot_start, ts.slot_end) THEN true
            ELSE false
        END AS is_booked,
        CASE
            WHEN rb.booked_during && tsrange(ts.slot_start, ts.slot_end)
            THEN upper(rb.booked_during * tsrange(ts.slot_start, ts.slot_end)) -
                 lower(rb.booked_during * tsrange(ts.slot_start, ts.slot_end))
            ELSE INTERVAL '0'
        END AS overlap_duration
    FROM time_slots ts
    CROSS JOIN pg14_room_bookings rb
    WHERE ts.depth <= 8760
),
-- 第三层CTE: 利用率统计
utilization_stats AS (
    SELECT
        sb.room_id,
        date_trunc('day', sb.slot_start) AS booking_day,
        extract(HOUR FROM sb.slot_start) AS booking_hour,
        count(*) FILTER (WHERE sb.is_booked) AS booked_slots,
        count(*) AS total_slots,
        round(
            (count(*) FILTER (WHERE sb.is_booked)::numeric /
             NULLIF(count(*), 0) * 100)::numeric,
            2
        ) AS utilization_pct,
        sum(extract(EPOCH FROM sb.overlap_duration) / 3600) AS total_booked_hours,
        avg(jsonb_array_length(sb.attendees))
            FILTER (WHERE sb.is_booked) AS avg_attendees
    FROM slot_bookings sb
    GROUP BY sb.room_id, date_trunc('day', sb.slot_start), extract(HOUR FROM sb.slot_start)
),
-- 第四层CTE: 窗口分析
utilization_patterns AS (
    SELECT
        us.room_id,
        us.booking_day,
        us.booking_hour,
        us.utilization_pct,
        us.total_booked_hours,
        us.avg_attendees,
        avg(us.utilization_pct) OVER (
            PARTITION BY us.room_id, us.booking_hour
            ORDER BY us.booking_day
            ROWS BETWEEN 7 PRECEDING AND 7 FOLLOWING
        ) AS rolling_weekly_avg,
        avg(us.utilization_pct) OVER (
            PARTITION BY us.room_id
            ORDER BY us.booking_day, us.booking_hour
            ROWS BETWEEN 24 PRECEDING AND 24 FOLLOWING
        ) AS rolling_daily_avg,
        row_number() OVER (
            PARTITION BY us.room_id
            ORDER BY us.utilization_pct DESC
        ) AS peak_rank,
        rank() OVER (
            PARTITION BY us.room_id, us.booking_hour
            ORDER BY us.utilization_pct DESC
        ) AS peak_hour_rank
    FROM utilization_stats us
)
-- 主查询: 最终聚合
SELECT
    up.room_id,
    count(*) AS total_observations,
    round(avg(up.utilization_pct)::numeric, 2) AS avg_utilization,
    round(max(up.utilization_pct)::numeric, 2) AS peak_utilization,
    round(stddev(up.utilization_pct)::numeric, 2) AS utilization_volatility,
    mode() WITHIN GROUP (ORDER BY up.booking_hour) AS most_booked_hour,
    array_agg(
        DISTINCT jsonb_build_object(
            'hour', up.booking_hour,
            'avg_util', round(up.rolling_weekly_avg::numeric, 2)
        )
        ORDER BY up.booking_hour
    ) FILTER (WHERE up.peak_hour_rank <= 3) AS peak_hours_detail,
    (
        SELECT jsonb_agg(
            jsonb_build_object(
                'day', up2.booking_day::date,
                'hour', up2.booking_hour,
                'util', up2.utilization_pct,
                'avg_attendees', round(up2.avg_attendees::numeric, 2)
            )
            ORDER BY up2.utilization_pct DESC
        )
        FROM utilization_patterns up2
        WHERE up2.room_id = up.room_id
          AND up2.peak_rank <= 10
    ) AS top_10_peaks,
    (
        SELECT jsonb_object_agg(
            'hour_' || h,
            round(avg_util::numeric, 2)
        )
        FROM (
            SELECT
                up3.booking_hour AS h,
                avg(up3.utilization_pct) AS avg_util
            FROM utilization_patterns up3
            WHERE up3.room_id = up.room_id
            GROUP BY up3.booking_hour
        ) hour_stats
    ) AS hourly_breakdown
FROM utilization_patterns up
GROUP BY up.room_id
ORDER BY avg_utilization DESC;