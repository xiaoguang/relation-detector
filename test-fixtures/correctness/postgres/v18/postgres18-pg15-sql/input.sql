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
-- Part 2: SQL/JSON 函数 - IS JSON, JSON_SCALAR, JSON_EXISTS
-- ============================================================================

WITH
-- 基础CTE: 使用PG15新增JSON函数
json_validation AS (
    SELECT
        it.sku,
        it.metadata,
        it.metadata IS JSON AS is_valid_json,
        it.metadata IS JSON OBJECT AS is_json_object,
        it.metadata IS JSON ARRAY AS is_json_array,
        it.metadata IS JSON SCALAR AS is_json_scalar,
        JSON_EXISTS(it.metadata, '$.supplier_info') AS has_supplier_info,
        JSON_EXISTS(it.metadata, '$.dimensions[*] ? (@.unit == "cm")') AS has_cm_dimensions,
        JSON_QUERY(it.metadata, '$.categories[*]' RETURNING TEXT ARRAY) AS categories,
        JSON_QUERY(it.metadata, '$.supplier_info.name' RETURNING TEXT) AS supplier_name,
        JSON_VALUE(it.metadata, '$.weight_kg' RETURNING NUMERIC DEFAULT 0.0 ON ERROR) AS weight_kg,
        JSON_VALUE(it.metadata, '$.supplier_info.rating' RETURNING NUMERIC DEFAULT 3.0 ON ERROR) AS supplier_rating,
        JSON_VALUE(it.metadata, '$.last_audit.timestamp' RETURNING TIMESTAMPTZ) AS last_audit,
        it.quantity,
        it.price,
        it.cost
    FROM pg15_inventory_target it
    WHERE it.metadata IS JSON OBJECT
),
-- 第二层CTE: JSON嵌套查询
json_enriched AS (
    SELECT
        jv.*,
        JSON_EXISTS(jv.metadata, '$.certifications[*] ? (@ == "ISO9001" || @ == "ISO14001")')
            AS has_quality_cert,
        JSON_QUERY(
            jv.metadata,
            '$.supplier_info.contacts[*] ? (@.primary == true)'
            RETURNING JSONB
        ) AS primary_contact,
        JSON_QUERY(
            jv.metadata,
            '$.pricing_history[*] ? (@.date.datetime() > "2024-01-01".datetime())'
            RETURNING JSONB
        ) AS recent_pricing,
        JSON_VALUE(
            jv.metadata,
            '$.supplier_info.address.country'
            RETURNING TEXT DEFAULT 'UNKNOWN' ON EMPTY
        ) AS supplier_country,
        JSON_VALUE(
            jv.metadata,
            '$.dimensions.height'
            RETURNING NUMERIC DEFAULT 0 ON ERROR
        ) * JSON_VALUE(
            jv.metadata,
            '$.dimensions.width'
            RETURNING NUMERIC DEFAULT 0 ON ERROR
        ) * JSON_VALUE(
            jv.metadata,
            '$.dimensions.depth'
            RETURNING NUMERIC DEFAULT 0 ON ERROR
        ) AS volume_cm3,
        (
            SELECT jsonb_agg(
                jsonb_build_object(
                    'category', cat,
                    'product_count', (
                        SELECT count(*)
                        FROM pg15_inventory_target inner_it
                        WHERE JSON_EXISTS(
                            inner_it.metadata,
                            format('$.categories[*] ? (@ == "%s")', cat)::jsonpath
                        )
                    ),
                    'total_value', (
                        SELECT sum(inner_it2.price * inner_it2.quantity)
                        FROM pg15_inventory_target inner_it2
                        WHERE JSON_EXISTS(
                            inner_it2.metadata,
                            format('$.categories[*] ? (@ == "%s")', cat)::jsonpath
                        )
                    )
                )
                ORDER BY cat
            )
            FROM jsonb_array_elements_text(
                JSON_QUERY(jv.metadata, '$.categories[*]' RETURNING JSONB)
            ) AS cat
        ) AS category_breakdown
    FROM json_validation jv
),
-- 第三层CTE: 窗口函数与JSON聚合
category_analysis AS (
    SELECT
        je.supplier_country,
        je.supplier_name,
        je.supplier_rating,
        je.has_quality_cert,
        count(*) AS product_count,
        sum(je.quantity) AS total_quantity,
        sum(je.quantity * je.price) AS total_inventory_value,
        sum(je.quantity * je.cost) AS total_inventory_cost,
        round(
            (sum(je.quantity * je.price) - sum(je.quantity * je.cost))::numeric,
            2
        ) AS potential_profit,
        round(avg(je.weight_kg)::numeric, 2) AS avg_weight_kg,
        round(avg(je.volume_cm3)::numeric, 2) AS avg_volume_cm3,
        jsonb_object_agg(
            je.sku,
            jsonb_build_object(
                'qty', je.quantity,
                'price', je.price,
                'cost', je.cost,
                'margin_pct', round(
                    ((je.price - je.cost) / NULLIF(je.price, 0) * 100)::numeric, 2
                ),
                'weight', je.weight_kg,
                'volume', je.volume_cm3,
                'categories', je.categories,
                'primary_contact', je.primary_contact
            )
        ) FILTER (WHERE je.sku IS NOT NULL) AS product_details,
        row_number() OVER (
            PARTITION BY je.supplier_country
            ORDER BY sum(je.quantity * je.price) DESC
        ) AS supplier_rank_in_country
    FROM json_enriched je
    GROUP BY
        je.supplier_country,
        je.supplier_name,
        je.supplier_rating,
        je.has_quality_cert
)
-- 主查询
SELECT
    ca.supplier_country,
    ca.supplier_name,
    ca.supplier_rating,
    ca.product_count,
    ca.total_inventory_value,
    ca.potential_profit,
    ca.avg_weight_kg,
    ca.avg_volume_cm3,
    ca.product_details,
    ca.supplier_rank_in_country,
    (
        SELECT jsonb_build_object(
            'total_country_inventory', sum(ca2.total_inventory_value),
            'total_country_profit', sum(ca2.potential_profit),
            'supplier_count', count(DISTINCT ca2.supplier_name),
            'avg_supplier_rating', round(avg(ca2.supplier_rating)::numeric, 2)
        )
        FROM category_analysis ca2
        WHERE ca2.supplier_country = ca.supplier_country
    ) AS country_summary
FROM category_analysis ca
WHERE ca.supplier_rank_in_country <= 10
ORDER BY ca.supplier_country, ca.total_inventory_value DESC;

-- ============================================================================
-- Part 3: JSON_EXISTS 深度嵌套路径查询
-- ============================================================================

SELECT
    it.sku,
    it.warehouse_id,
    it.quantity,
    it.price,
    it.metadata,
    -- 多层JSON路径检查
    CASE
        WHEN JSON_EXISTS(it.metadata, '$.supplier_info') THEN
            CASE
                WHEN JSON_EXISTS(
                    it.metadata,
                    '$.supplier_info.contacts[*] ? (@.primary == true && @.email like_regex "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")'
                ) THEN 'HAS_PRIMARY_CONTACT'
                WHEN JSON_EXISTS(
                    it.metadata,
                    '$.supplier_info.contacts[*] ? (@.email like_regex "@")'
                ) THEN 'HAS_CONTACT_NO_PRIMARY'
                ELSE 'NO_VALID_CONTACT'
            END
        ELSE 'NO_SUPPLIER_INFO'
    END AS contact_status,
    -- 检查认证
    CASE
        WHEN JSON_EXISTS(it.metadata, '$.certifications')
         AND JSON_EXISTS(it.metadata, '$.certifications[*] ? (@ == "ISO9001")')
         AND JSON_EXISTS(it.metadata, '$.certifications[*] ? (@ == "ISO14001")')
         AND JSON_EXISTS(it.metadata, '$.certifications[*] ? (@ == "ISO45001")')
        THEN 'FULLY_CERTIFIED'
        WHEN JSON_EXISTS(it.metadata, '$.certifications')
         AND (
            JSON_EXISTS(it.metadata, '$.certifications[*] ? (@ == "ISO9001")')
            OR JSON_EXISTS(it.metadata, '$.certifications[*] ? (@ == "ISO14001")')
         )
        THEN 'PARTIALLY_CERTIFIED'
        WHEN JSON_EXISTS(it.metadata, '$.certifications')
        THEN 'MINIMALLY_CERTIFIED'
        ELSE 'UNCERTIFIED'
    END AS certification_level,
    -- 价格历史分析
    JSON_QUERY(
        it.metadata,
        '$.pricing_history[*] ? (@.date.datetime() >= "2024-06-01".datetime() && @.price < $price)'
        PASSING it.price AS price
        RETURNING JSONB
    ) AS recent_lower_prices,
    -- 维度检查
    JSON_VALUE(it.metadata, '$.dimensions.height' RETURNING NUMERIC DEFAULT 0 ON ERROR) AS height,
    JSON_VALUE(it.metadata, '$.dimensions.width' RETURNING NUMERIC DEFAULT 0 ON ERROR) AS width,
    JSON_VALUE(it.metadata, '$.dimensions.depth' RETURNING NUMERIC DEFAULT 0 ON ERROR) AS depth,
    -- 嵌套JSON子查询
    (
        SELECT jsonb_agg(
            jsonb_build_object(
                'sku', related.sku,
                'category_match', (
                    SELECT count(*)
                    FROM jsonb_array_elements_text(
                        JSON_QUERY(it.metadata, '$.categories[*]' RETURNING JSONB)
                    ) AS cat1
                    WHERE cat1 IN (
                        SELECT cat2
                        FROM jsonb_array_elements_text(
                            JSON_QUERY(related.metadata, '$.categories[*]' RETURNING JSONB)
                        ) AS cat2
                    )
                ),
                'supplier_match', CASE
                    WHEN JSON_VALUE(it.metadata, '$.supplier_info.name' RETURNING TEXT)
                         = JSON_VALUE(related.metadata, '$.supplier_info.name' RETURNING TEXT)
                    THEN true ELSE false
                END
            )
        )
        FROM pg15_inventory_target related
        WHERE related.sku != it.sku
          AND related.warehouse_id = it.warehouse_id
          AND JSON_EXISTS(
            related.metadata,
            '$.categories[*] ? (@ == $cat)'
            PASSING JSON_VALUE(it.metadata, '$.categories[0]' RETURNING TEXT) AS cat
          )
        LIMIT 10
    ) AS related_products
FROM pg15_inventory_target it
WHERE it.metadata IS JSON OBJECT
  AND JSON_EXISTS(it.metadata, '$.supplier_info')
  AND JSON_VALUE(it.metadata, '$.weight_kg' RETURNING NUMERIC DEFAULT 0 ON ERROR) > 0
ORDER BY
    JSON_VALUE(it.metadata, '$.supplier_info.rating' RETURNING NUMERIC DEFAULT 0 ON ERROR) DESC,
    it.price DESC;

-- ============================================================================
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