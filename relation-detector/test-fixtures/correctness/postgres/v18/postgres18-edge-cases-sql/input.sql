-- ============================================================================
-- SQL解析器边界测试用例
-- 目标: 各种tricky语法、边界条件、易混淆模式
-- 适用: 所有PostgreSQL版本
-- ============================================================================

-- ============================================================================
-- Part 1: 复杂JOIN语法 - 各种JOIN类型混合
-- ============================================================================

SELECT
    a.id,
    b.id,
    c.id,
    d.id,
    e.id,
    f.id,
    g.id,
    h.id
FROM pg10_accounts a
NATURAL LEFT JOIN pg10_accounts b
LEFT OUTER JOIN pg10_accounts c ON a.account_id = c.account_id
RIGHT OUTER JOIN pg10_accounts d ON c.account_id = d.account_id
FULL OUTER JOIN pg10_accounts e ON d.account_id = e.account_id
CROSS JOIN pg10_accounts f
INNER JOIN pg10_accounts g ON f.account_id = g.account_id
LEFT JOIN LATERAL (
    SELECT h.account_id AS id
    FROM pg10_accounts h
    WHERE h.account_id = a.account_id
    LIMIT 1
) h ON true
WHERE a.status = 'ACTIVE'
  AND EXISTS (
    SELECT 1 FROM pg10_accounts i
    WHERE i.account_id = a.account_id
    UNION ALL
    SELECT 1 FROM pg10_accounts j
    WHERE j.account_id = b.account_id
  );

-- ============================================================================
-- Part 2: 复杂WHERE条件 - 混合AND/OR/NOT
-- ============================================================================

SELECT
    a.account_id,
    a.customer_name,
    a.balance,
    a.risk_score,
    a.account_type,
    a.branch_code
FROM pg10_accounts a
WHERE (
    (
        a.balance > 1000000
        AND a.risk_score <= 2
        AND a.account_type IN ('SAVINGS', 'CHECKING', 'MONEY_MARKET')
        AND a.branch_code LIKE 'BJ%'
        AND a.status = 'ACTIVE'
    )
    OR (
        a.balance > 100000
        AND a.risk_score = 1
        AND a.account_type = 'PREMIUM'
        AND a.metadata->>'vip_tier' IN ('GOLD', 'PLATINUM', 'DIAMOND')
        AND EXISTS (
            SELECT 1 FROM pg10_transactions t
            WHERE t.account_id = a.account_id
              AND t.amount > 50000
              AND t.txn_date >= CURRENT_DATE - INTERVAL '30 days'
        )
    )
    OR (
        a.balance > 50000
        AND a.risk_score <= 2
        AND NOT (a.account_type = 'BASIC')
        AND a.branch_code IN (
            SELECT branch_code FROM pg10_accounts
            WHERE risk_score = 1
            GROUP BY branch_code
            HAVING count(*) > 10
        )
    )
)
AND NOT (
    a.status = 'FROZEN'
    OR a.status = 'CLOSED'
    OR a.metadata->>'blacklisted' = 'true'
    OR a.account_id IN (
        SELECT account_id FROM pg10_ledger
        WHERE entry_type = 'FRAUD_FLAG'
          AND entry_date >= CURRENT_DATE - INTERVAL '90 days'
    )
)
AND (
    a.metadata->>'kyc_verified' = 'true'
    OR a.metadata->>'legacy_account' = 'true'
)
AND (
    SELECT count(*) FROM pg10_transactions t
    WHERE t.account_id = a.account_id
      AND t.txn_date >= CURRENT_DATE - INTERVAL '1 year'
) >= 12
ORDER BY a.balance DESC;

-- ============================================================================
-- Part 3: 复杂子查询模式 - 标量子查询/关联子查询/EXISTS/IN/ANY/ALL
-- ============================================================================

SELECT
    a.account_id,
    a.customer_name,
    a.balance,
    -- 标量子查询
    (SELECT max(t.amount) FROM pg10_transactions t WHERE t.account_id = a.account_id) AS max_txn,
    -- 关联子查询
    (SELECT string_agg(t.txn_type, ', ' ORDER BY t.txn_type)
     FROM pg10_transactions t
     WHERE t.account_id = a.account_id
       AND t.txn_date >= CURRENT_DATE - INTERVAL '30 days') AS recent_types,
    -- EXISTS
    EXISTS (SELECT 1 FROM pg10_ledger l WHERE l.account_id = a.account_id AND l.entry_type = 'DEBIT') AS has_debit,
    -- IN
    a.account_id IN (SELECT account_id FROM pg10_accounts WHERE risk_score = 1) AS is_low_risk,
    -- NOT IN
    a.account_id NOT IN (SELECT account_id FROM pg10_ledger WHERE entry_type = 'FRAUD_FLAG') AS is_clean,
    -- ANY
    a.balance > ANY (SELECT balance FROM pg10_accounts WHERE account_type = 'PREMIUM') AS above_any_premium,
    -- ALL
    a.balance > ALL (SELECT balance FROM pg10_accounts WHERE account_type = 'BASIC') AS above_all_basic,
    -- 多层嵌套
    (SELECT count(*) FROM pg10_transactions
     WHERE account_id IN (
        SELECT account_id FROM pg10_accounts
        WHERE branch_code = a.branch_code
          AND risk_score <= a.risk_score
     )
     AND txn_date >= CURRENT_DATE - INTERVAL '7 days'
    ) AS peer_branch_weekly_txns,
    -- 相关子查询 + 聚合
    (SELECT round(avg(inner_avg)::numeric, 2)
     FROM (
        SELECT avg(t.amount) AS inner_avg
        FROM pg10_transactions t
        WHERE t.account_id = a.account_id
        GROUP BY t.txn_date
        HAVING count(*) > 1
     ) daily_avgs
    ) AS avg_daily_amount_when_active
FROM pg10_accounts a
WHERE a.status = 'ACTIVE'
  AND a.balance > (
    SELECT avg(balance) FROM pg10_accounts WHERE risk_score = a.risk_score
  )
  AND EXISTS (
    SELECT 1 FROM pg10_transactions t1
    WHERE t1.account_id = a.account_id
      AND t1.amount > (
        SELECT avg(t2.amount) * 2
        FROM pg10_transactions t2
        WHERE t2.account_id = a.account_id
          AND t2.txn_date >= CURRENT_DATE - INTERVAL '90 days'
      )
  )
ORDER BY a.balance DESC;

-- ============================================================================
-- Part 4: 别名歧义测试 - 同名表多次引用
-- ============================================================================

SELECT
    t1.account_id,
    t1.amount AS amount_1,
    t2.amount AS amount_2,
    t3.amount AS amount_3,
    t4.amount AS amount_4,
    t5.amount AS amount_5
FROM pg10_transactions t1
JOIN pg10_transactions t2 ON t1.account_id = t2.account_id AND t2.txn_id != t1.txn_id
JOIN pg10_transactions t3 ON t2.account_id = t3.account_id AND t3.txn_id NOT IN (t1.txn_id, t2.txn_id)
JOIN pg10_transactions t4 ON t3.account_id = t4.account_id AND t4.txn_id NOT IN (t1.txn_id, t2.txn_id, t3.txn_id)
JOIN pg10_transactions t5 ON t4.account_id = t5.account_id AND t5.txn_id NOT IN (t1.txn_id, t2.txn_id, t3.txn_id, t4.txn_id)
WHERE t1.txn_date = t2.txn_date
  AND t2.txn_date = t3.txn_date
  AND t3.txn_date = t4.txn_date
  AND t4.txn_date = t5.txn_date
  AND t1.amount > t2.amount
  AND t2.amount > t3.amount
  AND t3.amount > t4.amount
  AND t4.amount > t5.amount
LIMIT 100;

-- ============================================================================
-- Part 5: NULL处理边界
-- ============================================================================

SELECT
    a.account_id,
    COALESCE(a.credit_limit, 0) AS safe_credit_limit,
    NULLIF(a.balance, 0) AS balance_or_null,
    GREATEST(a.balance, COALESCE(a.credit_limit, 0), 0) AS max_financial,
    LEAST(a.risk_score, 5) AS capped_risk,
    a.balance IS NULL AS is_null_balance,
    a.balance IS NOT NULL AS has_balance,
    a.balance IS DISTINCT FROM 0 AS has_non_zero,
    a.balance IS NOT DISTINCT FROM a.balance AS always_true,
    CASE
        WHEN a.balance IS NULL THEN 'UNKNOWN'
        WHEN a.balance > 1000000 THEN 'HIGH'
        WHEN a.balance > 100000 THEN 'MEDIUM'
        WHEN a.balance > 0 THEN 'LOW'
        WHEN a.balance = 0 THEN 'ZERO'
        ELSE 'NEGATIVE'
    END AS balance_category,
    CASE
        WHEN a.credit_limit IS NULL AND a.balance > 50000 THEN 'UNSECURED_HIGH'
        WHEN a.credit_limit IS NULL THEN 'UNSECURED'
        WHEN a.balance > a.credit_limit THEN 'OVER_LIMIT'
        WHEN a.balance > a.credit_limit * 0.8 THEN 'NEAR_LIMIT'
        ELSE 'NORMAL'
    END AS credit_status
FROM pg10_accounts a
WHERE (
    a.credit_limit IS NULL
    OR a.balance IS NULL
    OR a.balance > a.credit_limit
    OR a.balance > a.credit_limit * 0.8
)
ORDER BY
    a.credit_limit NULLS FIRST,
    a.balance NULLS LAST;

-- ============================================================================
-- Part 6: 类型转换与CAST
-- ============================================================================

SELECT
    a.account_id,
    a.account_id::text AS id_text,
    a.account_id::bigint AS id_bigint,
    a.account_id::numeric(18,0) AS id_numeric,
    a.balance::int AS balance_int,
    a.balance::text AS balance_text,
    a.balance::varchar(50) AS balance_varchar,
    CAST(a.account_id AS text) AS cast_id_text,
    CAST(a.balance AS numeric(10,2)) AS cast_balance,
    CAST(a.created_at AS date) AS cast_date_only,
    CAST(a.metadata AS jsonb) AS cast_jsonb,
    a.metadata::jsonb AS metadata_jsonb,
    -- 复杂CAST
    CAST(CAST(a.account_id AS text) || '-' || CAST(a.balance AS text) AS text) AS compound_key,
    CAST(date_trunc('month', a.created_at) AS date) AS month_start,
    CAST(extract(YEAR FROM a.created_at) AS int) AS year_int,
    -- 类型转换 + 计算
    CAST(a.balance * 100 AS bigint) AS balance_cents,
    CAST(round(a.balance / NULLIF(CAST(a.credit_limit AS numeric), 0) * 100, 2) AS numeric(6,2)) AS utilization_pct
FROM pg10_accounts a
WHERE
    CAST(a.balance AS text) LIKE '1%'
    OR CAST(a.account_id AS text) LIKE '100%'
ORDER BY a.account_id;

-- ============================================================================
-- Part 7: 字符串函数嵌套
-- ============================================================================

SELECT
    a.account_id,
    a.customer_name,
    -- 深度嵌套字符串函数
    UPPER(TRIM(BOTH ' ' FROM a.customer_name)) AS cleaned_name,
    REVERSE(SPLIT_PART(a.customer_name, ' ', 1)) AS reversed_first_name,
    REPLACE(REPLACE(REPLACE(
        LOWER(TRIM(a.customer_name)),
        ' ', '_'),
        '-', '_'),
        '.', '') AS normalized_name,
    SUBSTRING(a.customer_name FROM 1 FOR 1) || REPEAT('*', LENGTH(a.customer_name) - 1) AS masked_name,
    CONCAT(
        LEFT(a.customer_name, 1),
        REPEAT('*', GREATEST(LENGTH(a.customer_name) - 2, 0)),
        CASE WHEN LENGTH(a.customer_name) > 1 THEN RIGHT(a.customer_name, 1) ELSE '' END
    ) AS partially_masked,
    FORMAT(
        'Account[%s] Name=%s Balance=%s Risk=%s',
        a.account_id,
        a.customer_name,
        to_char(a.balance, 'FM999,999,999.00'),
        a.risk_score
    ) AS formatted_description,
    REGEXP_REPLACE(a.customer_name, '[^a-zA-Z ]', '', 'g') AS alpha_only,
    REGEXP_REPLACE(a.customer_name, '\s+', ' ', 'g') AS single_spaced,
    REGEXP_MATCHES(a.customer_name, '^([A-Z][a-z]+) ([A-Z][a-z]+)$') AS name_parts,
    OVERLAY(a.customer_name PLACING '***' FROM 2 FOR 3) AS overlay_masked,
    TRANSLATE(LOWER(a.customer_name), 'abcdefghijklmnopqrstuvwxyz', 'nopqrstuvwxyzabcdefghijklm') AS rot13_name
FROM pg10_accounts a
WHERE
    a.customer_name ~ '^[A-Z][a-z]+ [A-Z][a-z]+$'
    AND CHAR_LENGTH(TRIM(a.customer_name)) > 5
    AND POSITION(' ' IN a.customer_name) > 0
ORDER BY a.customer_name;

-- ============================================================================
-- Part 8: 数组操作
-- ============================================================================

SELECT
    a.account_id,
    ARRAY[a.account_type, a.branch_code, a.status] AS info_array,
    ARRAY_APPEND(ARRAY[a.account_type, a.branch_code], a.status) AS appended_array,
    ARRAY_PREPEND(a.status, ARRAY[a.account_type, a.branch_code]) AS prepended_array,
    ARRAY_CAT(
        ARRAY[a.account_type, a.branch_code],
        ARRAY[a.status, a.risk_score::text]
    ) AS concatenated_array,
    ARRAY[a.account_type] || ARRAY[a.branch_code] || ARRAY[a.status] AS pipe_concat,
    ARRAY[a.account_type, a.branch_code] @> ARRAY[a.account_type] AS contains_account_type,
    ARRAY[a.account_type] <@ ARRAY[a.account_type, a.branch_code, a.status] AS contained_in,
    a.account_type = ANY(ARRAY['SAVINGS', 'CHECKING', 'PREMIUM']) AS is_main_type,
    a.account_type = ALL(ARRAY[a.account_type]) AS tautology,
    CARDINALITY(ARRAY[a.account_type, a.branch_code, a.status]) AS array_size,
    ARRAY_DIMS(ARRAY[[1,2],[3,4]]) AS dims,
    ARRAY_FILL(0, ARRAY[3,3]) AS zero_matrix,
    UNNEST(ARRAY[a.account_type, a.branch_code, a.status]) AS unnest_example,
    (
        SELECT array_agg(DISTINCT elem ORDER BY elem)
        FROM UNNEST(ARRAY[a.account_type, a.branch_code, a.status, a.account_type]) AS elem
    ) AS unique_sorted_array
FROM pg10_accounts a
WHERE
    a.account_type = ANY(ARRAY['SAVINGS', 'CHECKING', 'PREMIUM', 'MONEY_MARKET'])
    AND CARDINALITY(ARRAY[a.account_type, a.branch_code]) > 0
ORDER BY a.account_id;

-- ============================================================================
-- Part 9: 时间日期函数
-- ============================================================================

SELECT
    a.account_id,
    a.created_at,
    date_trunc('hour', a.created_at) AS truncated_hour,
    date_trunc('day', a.created_at) AS truncated_day,
    date_trunc('week', a.created_at) AS truncated_week,
    date_trunc('month', a.created_at) AS truncated_month,
    date_trunc('quarter', a.created_at) AS truncated_quarter,
    date_trunc('year', a.created_at) AS truncated_year,
    extract(YEAR FROM a.created_at) AS year,
    extract(MONTH FROM a.created_at) AS month,
    extract(DAY FROM a.created_at) AS day,
    extract(DOW FROM a.created_at) AS day_of_week,
    extract(DOY FROM a.created_at) AS day_of_year,
    extract(QUARTER FROM a.created_at) AS quarter,
    extract(EPOCH FROM a.created_at) AS epoch_seconds,
    extract(EPOCH FROM now() - a.created_at) AS age_seconds,
    age(now(), a.created_at) AS full_age,
    justify_hours(INTERVAL '100 hours') AS justified_hours,
    justify_days(INTERVAL '100 days') AS justified_days,
    justify_interval(INTERVAL '100 days 100 hours') AS justified_interval,
    date_part('decade', a.created_at) AS decade,
    date_part('century', a.created_at) AS century,
    date_part('isodow', a.created_at) AS iso_day_of_week,
    date_part('isoyear', a.created_at) AS iso_year,
    date_bin('15 minutes', a.created_at, '2000-01-01'::timestamp) AS binned_15min,
    date_bin('1 hour', a.created_at, '2000-01-01'::timestamp) AS binned_1hour,
    to_char(a.created_at, 'YYYY-MM-DD HH24:MI:SS') AS formatted_1,
    to_char(a.created_at, 'FMDay, FMMonth DD, YYYY') AS formatted_2,
    to_char(a.created_at, 'Q"Q"YYYY') AS formatted_quarter,
    to_char(a.created_at, 'IW') AS iso_week_number,
    make_date(
        extract(YEAR FROM a.created_at)::int,
        extract(MONTH FROM a.created_at)::int,
        1
    ) AS first_of_month,
    make_timestamptz(
        extract(YEAR FROM a.created_at)::int,
        extract(MONTH FROM a.created_at)::int,
        extract(DAY FROM a.created_at)::int,
        0, 0, 0,
        'Asia/Shanghai'
    ) AS start_of_day_tz,
    now() AT TIME ZONE 'UTC' AS utc_now,
    now() AT TIME ZONE 'Asia/Shanghai' AS shanghai_now,
    now() AT TIME ZONE 'America/New_York' AS ny_now
FROM pg10_accounts a
WHERE a.created_at >= CURRENT_DATE - INTERVAL '2 years'
ORDER BY a.created_at DESC;

-- ============================================================================
-- Part 10: 数学函数与统计
-- ============================================================================

SELECT
    a.account_id,
    a.balance,
    ABS(a.balance) AS abs_balance,
    CEIL(a.balance) AS ceil_balance,
    FLOOR(a.balance) AS floor_balance,
    ROUND(a.balance, 2) AS rounded_balance,
    TRUNC(a.balance, 2) AS truncated_balance,
    MOD(a.account_id, 10) AS mod_10,
    POWER(a.balance, 0.5) AS sqrt_balance,
    SQRT(ABS(a.balance)) AS safe_sqrt,
    EXP(LN(NULLIF(ABS(a.balance), 0) + 1)) AS exp_ln_balance,
    LOG(10, NULLIF(ABS(a.balance), 0) + 1) AS log10_balance,
    SIGN(a.balance) AS sign_balance,
    WIDTH_BUCKET(a.balance, 0, 1000000, 10) AS balance_bucket,
    -- 三角函数
    SIN(a.balance / 1000) AS sin_val,
    COS(a.balance / 1000) AS cos_val,
    TAN(a.balance / 1000) AS tan_val,
    ATAN2(a.balance, COALESCE(a.credit_limit, 1)) AS atan2_val,
    -- 随机与哈希
    SETSEED(0.5) AS dummy_seed,
    RANDOM() AS rand_val,
    MD5(a.customer_name) AS md5_name,
    SHA256(a.customer_name::bytea) AS sha256_name,
    -- 统计聚合子查询
    (
        SELECT round(
            (a.balance - avg(balance)) / NULLIF(stddev(balance), 0)::numeric, 4
        )
        FROM pg10_accounts
        WHERE account_type = a.account_type
    ) AS z_score_in_type,
    (
        SELECT round(
            percentile_cont(0.5) WITHIN GROUP (ORDER BY balance)::numeric, 2
        )
        FROM pg10_accounts
        WHERE account_type = a.account_type
    ) AS median_balance_in_type
FROM pg10_accounts a
WHERE a.balance IS NOT NULL
  AND a.balance != 0
ORDER BY a.balance DESC;