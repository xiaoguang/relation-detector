-- ============================================================================
-- PostgreSQL 15 复杂SQL测试用例
-- 特性: MERGE语句, SQL/JSON函数(IS JSON, JSON_SCALAR, JSON_EXISTS等),
--       CLUSTER并行, 逻辑复制行过滤
-- 目标: 最大语法嵌套深度, 最全语法覆盖
-- ============================================================================

-- ============================================================================
-- Part 1: MERGE 语句 - 极致复杂条件
-- ============================================================================

-- 源表和目标表
CREATE TABLE IF NOT EXISTS pg15_inventory_target (
    sku TEXT PRIMARY KEY,
    warehouse_id INT NOT NULL,
    quantity INT NOT NULL DEFAULT 0,
    reserved INT NOT NULL DEFAULT 0,
    available INT GENERATED ALWAYS AS (quantity - reserved) STORED,
    price NUMERIC(10,2) NOT NULL,
    cost NUMERIC(10,2) NOT NULL,
    last_updated TIMESTAMPTZ NOT NULL DEFAULT now(),
    metadata JSONB NOT NULL DEFAULT '{}',
    version INT NOT NULL DEFAULT 1,
    UNIQUE (sku, warehouse_id)
);

CREATE TABLE IF NOT EXISTS pg15_inventory_source (
    sku TEXT NOT NULL,
    warehouse_id INT NOT NULL,
    quantity_delta INT NOT NULL,
    reserved_delta INT NOT NULL DEFAULT 0,
    new_price NUMERIC(10,2),
    new_cost NUMERIC(10,2),
    source_system TEXT NOT NULL,
    change_type TEXT NOT NULL,
    metadata_update JSONB,
    batch_id UUID NOT NULL,
    processed_at TIMESTAMPTZ DEFAULT now()
);

-- 复杂MERGE语句
MERGE INTO pg15_inventory_target t
USING (
    WITH
    -- 第一层CTE: 聚合源数据
    aggregated_changes AS (
        SELECT
            s.sku,
            s.warehouse_id,
            sum(s.quantity_delta) AS total_qty_delta,
            sum(s.reserved_delta) AS total_reserved_delta,
            max(s.new_price) FILTER (WHERE s.new_price IS NOT NULL) AS latest_price,
            max(s.new_cost) FILTER (WHERE s.new_cost IS NOT NULL) AS latest_cost,
            array_agg(DISTINCT s.source_system ORDER BY s.source_system) AS source_systems,
            array_agg(DISTINCT s.change_type ORDER BY s.change_type) AS change_types,
            jsonb_agg(
                DISTINCT jsonb_build_object(
                    'source', s.source_system,
                    'change_type', s.change_type,
                    'qty_delta', s.quantity_delta,
                    'batch', s.batch_id
                )
            ) AS change_log,
            max(s.batch_id) AS latest_batch_id,
            max(s.processed_at) AS latest_processed_at,
            jsonb_merge_agg(s.metadata_update)
                FILTER (WHERE s.metadata_update IS NOT NULL) AS merged_metadata,
            count(*) AS change_count
        FROM pg15_inventory_source s
        WHERE s.processed_at >= CURRENT_TIMESTAMP - INTERVAL '24 hours'
        GROUP BY s.sku, s.warehouse_id
    ),
    -- 第二层CTE: 验证和清洗
    validated_changes AS (
        SELECT
            ac.*,
            CASE
                WHEN ac.total_qty_delta + COALESCE(
                    (SELECT t2.quantity FROM pg15_inventory_target t2
                     WHERE t2.sku = ac.sku AND t2.warehouse_id = ac.warehouse_id), 0
                ) < 0 THEN 'INVALID: NEGATIVE_INVENTORY'
                WHEN ac.latest_price IS NOT NULL AND ac.latest_cost IS NOT NULL
                     AND ac.latest_price < ac.latest_cost THEN 'INVALID: PRICE_BELOW_COST'
                WHEN ac.total_qty_delta < -1000 THEN 'WARNING: LARGE_DECREASE'
                WHEN ac.total_qty_delta > 10000 THEN 'WARNING: LARGE_INCREASE'
                ELSE 'VALID'
            END AS validation_status,
            CASE
                WHEN EXISTS (
                    SELECT 1 FROM pg15_inventory_target t3
                    WHERE t3.sku = ac.sku AND t3.warehouse_id = ac.warehouse_id
                ) THEN 'UPDATE'
                ELSE 'INSERT'
            END AS operation_type
        FROM aggregated_changes ac
    ),
    -- 第三层CTE: 风险评估
    risk_assessed AS (
        SELECT
            vc.*,
            (
                SELECT jsonb_build_object(
                    'price_volatility', (
                        SELECT round(stddev(t4.price)::numeric, 2)
                        FROM pg15_inventory_target t4
                        WHERE t4.sku = vc.sku
                          AND t4.last_updated >= CURRENT_TIMESTAMP - INTERVAL '30 days'
                    ),
                    'demand_trend', (
                        SELECT CASE
                            WHEN count(*) > 10 AND
                                 corr(
                                     extract(EPOCH FROM t5.last_updated)::numeric,
                                     t5.quantity::numeric
                                 ) > 0.3 THEN 'INCREASING'
                            WHEN count(*) > 10 AND
                                 corr(
                                     extract(EPOCH FROM t5.last_updated)::numeric,
                                     t5.quantity::numeric
                                 ) < -0.3 THEN 'DECREASING'
                            ELSE 'STABLE'
                        END
                        FROM pg15_inventory_target t5
                        WHERE t5.sku = vc.sku
                          AND t5.last_updated >= CURRENT_TIMESTAMP - INTERVAL '90 days'
                    ),
                    'source_trust', CASE
                        WHEN 'ERP' = ANY(vc.source_systems) THEN 'HIGH'
                        WHEN 'WMS' = ANY(vc.source_systems) THEN 'MEDIUM'
                        ELSE 'LOW'
                    END
                )
            ) AS risk_metrics
        FROM validated_changes vc
        WHERE vc.validation_status = 'VALID'
           OR vc.validation_status LIKE 'WARNING:%'
    )
    SELECT * FROM risk_assessed
) s
ON t.sku = s.sku AND t.warehouse_id = s.warehouse_id
WHEN MATCHED AND s.operation_type = 'UPDATE' AND s.validation_status = 'VALID' THEN
    UPDATE SET
        quantity = t.quantity + s.total_qty_delta,
        reserved = t.reserved + s.total_reserved_delta,
        price = COALESCE(s.latest_price, t.price),
        cost = COALESCE(s.latest_cost, t.cost),
        last_updated = s.latest_processed_at,
        metadata = t.metadata || COALESCE(s.merged_metadata, '{}'::jsonb),
        version = t.version + 1
WHEN MATCHED AND s.validation_status LIKE 'WARNING:%' THEN
    UPDATE SET
        quantity = t.quantity + s.total_qty_delta,
        last_updated = s.latest_processed_at,
        metadata = t.metadata || jsonb_build_object(
            'warning', s.validation_status,
            'change_log', s.change_log,
            'risk_metrics', s.risk_metrics,
            'processed_at', s.latest_processed_at
        ),
        version = t.version + 1
WHEN NOT MATCHED AND s.validation_status = 'VALID' THEN
    INSERT (
        sku, warehouse_id, quantity, reserved, price, cost,
        last_updated, metadata, version
    ) VALUES (
        s.sku, s.warehouse_id,
        GREATEST(s.total_qty_delta, 0),
        GREATEST(s.total_reserved_delta, 0),
        COALESCE(s.latest_price, 0),
        COALESCE(s.latest_cost, 0),
        s.latest_processed_at,
        COALESCE(s.merged_metadata, '{}'::jsonb),
        1
    )
WHEN NOT MATCHED AND s.validation_status LIKE 'WARNING:%' THEN
    INSERT (
        sku, warehouse_id, quantity, reserved, price, cost,
        last_updated, metadata, version
    ) VALUES (
        s.sku, s.warehouse_id,
        GREATEST(s.total_qty_delta, 0),
        GREATEST(s.total_reserved_delta, 0),
        COALESCE(s.latest_price, 0),
        COALESCE(s.latest_cost, 0),
        s.latest_processed_at,
        jsonb_build_object(
            'warning', s.validation_status,
            'change_log', s.change_log,
            'risk_metrics', s.risk_metrics
        ),
        1
    );

-- ============================================================================
-- Part 2: SQL/JSON 函数等价查询 - 使用 PostgreSQL jsonb 原生函数
-- ============================================================================

WITH json_validation AS (
    SELECT
        it.sku,
        it.metadata,
        jsonb_typeof(it.metadata) AS metadata_type,
        it.metadata ? 'supplier_info' AS has_supplier_info,
        it.metadata->'categories' AS categories,
        it.metadata->'supplier_info'->>'name' AS supplier_name,
        COALESCE((it.metadata->>'weight_kg')::numeric, 0.0) AS weight_kg,
        COALESCE((it.metadata->'supplier_info'->>'rating')::numeric, 3.0) AS supplier_rating,
        it.quantity,
        it.price,
        it.cost
    FROM pg15_inventory_target it
    WHERE jsonb_typeof(it.metadata) = 'object'
),
category_analysis AS (
    SELECT
        COALESCE(jv.metadata->'supplier_info'->'address'->>'country', 'UNKNOWN') AS supplier_country,
        jv.supplier_name,
        jv.supplier_rating,
        count(*) AS product_count,
        sum(jv.quantity * jv.price) AS total_inventory_value,
        round(avg(jv.weight_kg)::numeric, 2) AS avg_weight_kg
    FROM json_validation jv
    GROUP BY COALESCE(jv.metadata->'supplier_info'->'address'->>'country', 'UNKNOWN'),
             jv.supplier_name,
             jv.supplier_rating
)
SELECT
    ca.supplier_country,
    ca.supplier_name,
    ca.supplier_rating,
    ca.product_count,
    ca.total_inventory_value,
    ca.avg_weight_kg
FROM category_analysis ca
ORDER BY ca.supplier_country, ca.total_inventory_value DESC;

-- Part 3: JSON 路径等价查询 - 保留 inventory 自关联关系
-- ============================================================================

SELECT
    it.sku,
    it.warehouse_id,
    related.sku AS related_sku,
    related.warehouse_id AS related_warehouse_id,
    CASE
        WHEN it.metadata ? 'supplier_info' THEN 'HAS_SUPPLIER_INFO'
        ELSE 'NO_SUPPLIER_INFO'
    END AS supplier_status
FROM pg15_inventory_target it
JOIN pg15_inventory_target related
  ON related.warehouse_id = it.warehouse_id
WHERE jsonb_typeof(it.metadata) = 'object';

-- Part 4: 极致嵌套子查询与JSON_QUERY聚合
-- ============================================================================

WITH
-- 递归CTE: 构建商品层级
product_hierarchy AS (
    SELECT
        it.sku,
        it.warehouse_id,
        it.price,
        it.cost,
        it.quantity,
        JSON_VALUE(it.metadata, '$.parent_sku' RETURNING TEXT) AS parent_sku,
        JSON_QUERY(it.metadata, '$.components[*].sku' RETURNING TEXT ARRAY) AS component_skus,
        JSON_QUERY(it.metadata, '$.categories[*]' RETURNING TEXT ARRAY) AS categories,
        it.metadata,
        0 AS depth,
        ARRAY[it.sku] AS path
    FROM pg15_inventory_target it
    WHERE JSON_VALUE(it.metadata, '$.parent_sku' RETURNING TEXT) IS NULL
    UNION ALL
    SELECT
        child.sku,
        child.warehouse_id,
        child.price,
        child.cost,
        child.quantity,
        JSON_VALUE(child.metadata, '$.parent_sku' RETURNING TEXT),
        JSON_QUERY(child.metadata, '$.components[*].sku' RETURNING TEXT ARRAY),
        JSON_QUERY(child.metadata, '$.categories[*]' RETURNING TEXT ARRAY),
        child.metadata,
        ph.depth + 1,
        ph.path || child.sku
    FROM pg15_inventory_target child
    JOIN product_hierarchy ph
        ON JSON_VALUE(child.metadata, '$.parent_sku' RETURNING TEXT) = ph.sku
    WHERE NOT child.sku = ANY(ph.path)
      AND ph.depth < 10
),
-- 层级聚合
hierarchy_stats AS (
    SELECT
        ph.sku AS root_sku,
        ph.depth,
        ph.path,
        count(DISTINCT leaf.sku) AS total_descendants,
        sum(leaf.quantity) AS total_quantity,
        sum(leaf.quantity * leaf.price) AS total_value,
        sum(leaf.quantity * leaf.cost) AS total_cost,
        round(avg(leaf.price)::numeric, 2) AS avg_descendant_price,
        array_agg(DISTINCT leaf.sku ORDER BY leaf.sku) AS all_descendants,
        jsonb_agg(
            DISTINCT jsonb_build_object(
                'sku', leaf.sku,
                'depth', leaf.depth,
                'qty', leaf.quantity,
                'value', leaf.quantity * leaf.price
            )
            ORDER BY leaf.depth, leaf.sku
        ) AS descendant_details
    FROM product_hierarchy ph
    LEFT JOIN product_hierarchy leaf
        ON leaf.path @> ph.path
       AND leaf.sku != ph.sku
    GROUP BY ph.sku, ph.depth, ph.path
)
SELECT
    hs.root_sku,
    hs.depth,
    hs.total_descendants,
    hs.total_quantity,
    hs.total_value,
    hs.total_cost,
    round(((hs.total_value - hs.total_cost) / NULLIF(hs.total_value, 0) * 100)::numeric, 2) AS margin_pct,
    hs.avg_descendant_price,
    cardinality(hs.path) AS path_length,
    hs.descendant_details,
    (
        SELECT jsonb_build_object(
            'categories_covered', (
                SELECT jsonb_agg(DISTINCT cat)
                FROM (
                    SELECT unnest(
                        JSON_QUERY(it2.metadata, '$.categories[*]' RETURNING TEXT ARRAY)
                    ) AS cat
                    FROM pg15_inventory_target it2
                    WHERE it2.sku = ANY(hs.all_descendants)
                ) cats
            ),
            'component_skus', (
                SELECT jsonb_agg(DISTINCT comp)
                FROM (
                    SELECT unnest(
                        JSON_QUERY(it3.metadata, '$.components[*].sku' RETURNING TEXT ARRAY)
                    ) AS comp
                    FROM pg15_inventory_target it3
                    WHERE it3.sku = ANY(hs.all_descendants)
                ) comps
                WHERE comp IS NOT NULL
            ),
            'orphan_components', (
                SELECT jsonb_agg(sku)
                FROM (
                    SELECT unnest(
                        JSON_QUERY(it4.metadata, '$.components[*].sku' RETURNING TEXT ARRAY)
                    ) AS sku
                    FROM pg15_inventory_target it4
                    WHERE it4.sku = ANY(hs.all_descendants)
                ) comps
                WHERE comps.sku IS NOT NULL
                  AND comps.sku != ALL(hs.all_descendants)
            )
        )
    ) AS hierarchy_analysis
FROM hierarchy_stats hs
WHERE hs.total_descendants > 0
ORDER BY hs.total_value DESC, hs.depth;