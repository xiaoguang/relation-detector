-- ============================================================================
-- PostgreSQL 17 复杂SQL测试用例
-- 特性: JSON_TABLE, MERGE增强 (RETURNING, 多动作), 增量JSON解析,
--       COPY性能改进, 系统信息函数增强
-- 目标: 最大语法嵌套深度, 最全语法覆盖
-- ============================================================================

-- ============================================================================
-- Part 1: JSON_TABLE - 将JSON展开为关系表 (PG17重磅特性)
-- ============================================================================

CREATE TABLE IF NOT EXISTS pg17_order_documents (
    doc_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_data JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 插入复杂嵌套JSON
INSERT INTO pg17_order_documents (order_data) VALUES
('{
    "order_id": "ORD-2024-001",
    "customer": {
        "id": "CUST-1001",
        "name": "张三",
        "type": "VIP",
        "contacts": [
            {"type": "email", "value": "zhangsan@example.com", "primary": true},
            {"type": "phone", "value": "+86-13800138000", "primary": false}
        ]
    },
    "items": [
        {
            "sku": "SKU-A001",
            "name": "笔记本电脑",
            "qty": 2,
            "unit_price": 5999.00,
            "discount": 0.10,
            "variants": [
                {"attr": "color", "value": "银色"},
                {"attr": "ram", "value": "16GB"}
            ],
            "warranty": {"type": "extended", "years": 3, "price": 299.00}
        },
        {
            "sku": "SKU-B002",
            "name": "机械键盘",
            "qty": 1,
            "unit_price": 899.00,
            "discount": 0.05,
            "variants": [
                {"attr": "switch", "value": "红轴"},
                {"attr": "layout", "value": "87键"}
            ],
            "warranty": {"type": "standard", "years": 1, "price": 0}
        },
        {
            "sku": "SKU-C003",
            "name": "显示器",
            "qty": 1,
            "unit_price": 2499.00,
            "discount": 0.15,
            "variants": [
                {"attr": "size", "value": "27寸"},
                {"attr": "resolution", "value": "4K"}
            ],
            "warranty": {"type": "extended", "years": 2, "price": 199.00}
        }
    ],
    "shipping": {
        "address": {
            "province": "北京",
            "city": "北京市",
            "district": "海淀区",
            "street": "中关村大街1号",
            "zipcode": "100080"
        },
        "method": "EXPRESS",
        "cost": 25.00,
        "estimated_days": 2
    },
    "payment": {
        "method": "CREDIT_CARD",
        "installments": 6,
        "currency": "CNY",
        "subtotal": 14396.00,
        "tax": 1871.48,
        "total": 16292.48
    },
    "timeline": [
        {"status": "created", "timestamp": "2024-01-15T09:30:00+08:00"},
        {"status": "paid", "timestamp": "2024-01-15T09:32:15+08:00"},
        {"status": "processing", "timestamp": "2024-01-15T10:00:00+08:00"},
        {"status": "shipped", "timestamp": "2024-01-16T14:20:00+08:00"}
    ]
}');

-- ============================================================================
-- JSON_TABLE 核心查询: 展开所有嵌套结构
-- ============================================================================

WITH
-- 使用JSON_TABLE展平订单数据
order_flat AS (
    SELECT
        jt.*
    FROM pg17_order_documents od
    CROSS JOIN JSON_TABLE(
        od.order_data,
        '$' COLUMNS (
            -- 顶层字段
            order_id      TEXT PATH '$.order_id',
            customer_id   TEXT PATH '$.customer.id',
            customer_name TEXT PATH '$.customer.name',
            customer_type TEXT PATH '$.customer.type',
            -- 嵌套JSON_TABLE: 展开items数组
            NESTED PATH '$.items[*]' AS item COLUMNS (
                item_sku        TEXT PATH '$.sku',
                item_name       TEXT PATH '$.name',
                item_qty        INT PATH '$.qty',
                item_unit_price NUMERIC(10,2) PATH '$.unit_price',
                item_discount   NUMERIC(5,4) PATH '$.discount',
                -- 再嵌套: 展开variants数组
                NESTED PATH '$.variants[*]' AS variant COLUMNS (
                    variant_attr  TEXT PATH '$.attr',
                    variant_value TEXT PATH '$.value'
                ),
                -- 保修信息
                warranty_type  TEXT PATH '$.warranty.type',
                warranty_years INT PATH '$.warranty.years',
                warranty_price NUMERIC(10,2) PATH '$.warranty.price'
            ),
            -- 收货地址
            shipping_province TEXT PATH '$.shipping.address.province',
            shipping_city     TEXT PATH '$.shipping.address.city',
            shipping_district TEXT PATH '$.shipping.address.district',
            shipping_method   TEXT PATH '$.shipping.method',
            shipping_cost     NUMERIC(10,2) PATH '$.shipping.cost',
            -- 支付信息
            payment_method     TEXT PATH '$.payment.method',
            payment_installments INT PATH '$.payment.installments',
            payment_subtotal   NUMERIC(12,2) PATH '$.payment.subtotal',
            payment_tax        NUMERIC(12,2) PATH '$.payment.tax',
            payment_total      NUMERIC(12,2) PATH '$.payment.total',
            -- 嵌套: timeline
            NESTED PATH '$.timeline[*]' AS status COLUMNS (
                status_type      TEXT PATH '$.status',
                status_timestamp TIMESTAMPTZ PATH '$.timestamp'
            ),
            -- 嵌套: 联系人
            NESTED PATH '$.customer.contacts[*]' AS contact COLUMNS (
                contact_type   TEXT PATH '$.type',
                contact_value  TEXT PATH '$.value',
                contact_primary BOOLEAN PATH '$.primary'
            )
        )
    ) AS jt
),
-- 第二层CTE: 商品级别聚合
item_aggregation AS (
    SELECT
        of.order_id,
        of.customer_id,
        of.customer_name,
        of.customer_type,
        of.shipping_province,
        of.shipping_city,
        of.shipping_method,
        of.payment_method,
        of.payment_total,
        of.item_sku,
        of.item_name,
        of.item_qty,
        of.item_unit_price,
        of.item_discount,
        round(
            (of.item_unit_price * of.item_qty * (1 - of.item_discount))::numeric, 2
        ) AS item_net_price,
        of.warranty_type,
        of.warranty_years,
        of.warranty_price,
        -- 聚合变体
        string_agg(
            DISTINCT of.variant_attr || ':' || of.variant_value,
            ', '
            ORDER BY of.variant_attr
        ) FILTER (WHERE of.variant_attr IS NOT NULL) AS variant_summary,
        -- 聚合联系人
        string_agg(
            DISTINCT of.contact_type || ':' || of.contact_value,
            ', '
            ORDER BY of.contact_type
        ) FILTER (WHERE of.contact_type IS NOT NULL) AS contact_summary,
        -- 状态时间线
        min(of.status_timestamp) FILTER (WHERE of.status_type = 'created') AS created_at,
        min(of.status_timestamp) FILTER (WHERE of.status_type = 'paid') AS paid_at,
        min(of.status_timestamp) FILTER (WHERE of.status_type = 'shipped') AS shipped_at
    FROM order_flat of
    GROUP BY
        of.order_id, of.customer_id, of.customer_name, of.customer_type,
        of.shipping_province, of.shipping_city, of.shipping_method,
        of.payment_method, of.payment_total,
        of.item_sku, of.item_name, of.item_qty, of.item_unit_price,
        of.item_discount, of.warranty_type, of.warranty_years, of.warranty_price
),
-- 第三层CTE: 订单级别计算
order_calculations AS (
    SELECT
        ia.order_id,
        ia.customer_id,
        ia.customer_name,
        ia.customer_type,
        ia.shipping_province,
        ia.shipping_city,
        ia.shipping_method,
        ia.payment_method,
        ia.payment_total,
        ia.created_at,
        ia.paid_at,
        ia.shipped_at,
        count(DISTINCT ia.item_sku) AS unique_items,
        sum(ia.item_qty) AS total_qty,
        sum(ia.item_net_price) AS computed_subtotal,
        sum(ia.warranty_price) AS total_warranty_cost,
        round(ia.payment_total - sum(ia.item_net_price) - sum(ia.warranty_price), 2)
            AS computed_remainder,
        array_agg(
            DISTINCT jsonb_build_object(
                'sku', ia.item_sku,
                'name', ia.item_name,
                'qty', ia.item_qty,
                'unit_price', ia.item_unit_price,
                'discount', ia.item_discount,
                'net_price', ia.item_net_price,
                'warranty', jsonb_build_object(
                    'type', ia.warranty_type,
                    'years', ia.warranty_years,
                    'price', ia.warranty_price
                ),
                'variants', ia.variant_summary
            )
            ORDER BY ia.item_sku
        ) AS items_detail,
        string_agg(DISTINCT ia.contact_summary, '; ') AS contacts,
        round(
            extract(EPOCH FROM ia.paid_at - ia.created_at)::numeric / 60, 2
        ) AS payment_minutes,
        round(
            extract(EPOCH FROM ia.shipped_at - ia.paid_at)::numeric / 3600, 2
        ) AS processing_hours
    FROM item_aggregation ia
    GROUP BY
        ia.order_id, ia.customer_id, ia.customer_name, ia.customer_type,
        ia.shipping_province, ia.shipping_city, ia.shipping_method,
        ia.payment_method, ia.payment_total, ia.created_at, ia.paid_at, ia.shipped_at
)
-- 主查询
SELECT
    oc.order_id,
    oc.customer_name,
    oc.customer_type,
    oc.shipping_province || ' ' || oc.shipping_city AS shipping_location,
    oc.unique_items,
    oc.total_qty,
    oc.computed_subtotal,
    oc.total_warranty_cost,
    oc.payment_total,
    oc.computed_remainder,
    oc.payment_minutes,
    oc.processing_hours,
    oc.items_detail,
    oc.contacts,
    -- 嵌套: 检查数据完整性
    CASE
        WHEN abs(oc.computed_remainder) > 1.00 THEN 'DISCREPANCY_DETECTED'
        WHEN abs(oc.computed_remainder) > 0.01 THEN 'ROUNDING_DIFFERENCE'
        ELSE 'MATCHED'
    END AS reconciliation_status,
    -- 嵌套: 客户分段
    (
        SELECT jsonb_build_object(
            'total_orders', count(*),
            'total_spent', sum(oc2.payment_total),
            'avg_order_value', round(avg(oc2.payment_total)::numeric, 2),
            'preferred_method', mode() WITHIN GROUP (ORDER BY oc2.payment_method)
        )
        FROM order_calculations oc2
        WHERE oc2.customer_id = oc.customer_id
    ) AS customer_lifetime_stats
FROM order_calculations oc
ORDER BY oc.payment_total DESC;

-- ============================================================================
-- Part 2: JSON_TABLE 多层级NESTED PATH - 极致嵌套
-- ============================================================================

SELECT
    jt2.order_id,
    jt2.customer_id,
    jt2.customer_name,
    jt2.item_sku,
    jt2.item_name,
    jt2.item_qty,
    jt2.item_unit_price,
    jt2.item_discount,
    jt2.item_net_price,
    jt2.variant_attr,
    jt2.variant_value,
    jt2.warranty_type,
    jt2.warranty_years,
    jt2.warranty_price,
    jt2.warranty_total,
    jt2.shipping_address,
    jt2.contact_info,
    jt2.status_history,
    jt2.payment_summary,
    jt2.item_category,
    jt2.item_tags,
    -- 计算字段
    round(
        (jt2.item_unit_price * jt2.item_qty * (1 - jt2.item_discount) + jt2.warranty_total)::numeric, 2
    ) AS line_total
FROM pg17_order_documents od
CROSS JOIN JSON_TABLE(
    od.order_data,
    '$' COLUMNS (
        order_id      TEXT PATH '$.order_id',
        customer_id   TEXT PATH '$.customer.id',
        customer_name TEXT PATH '$.customer.name',
        -- 第一层NESTED: items
        NESTED PATH '$.items[*]' AS items COLUMNS (
            item_sku        TEXT PATH '$.sku',
            item_name       TEXT PATH '$.name',
            item_qty        INT PATH '$.qty',
            item_unit_price NUMERIC(10,2) PATH '$.unit_price',
            item_discount   NUMERIC(5,4) DEFAULT 0 ON EMPTY PATH '$.discount',
            item_net_price  NUMERIC(12,2) PATH '$.net_price',
            -- 第二层NESTED: variants
            NESTED PATH '$.variants[*]' AS variants COLUMNS (
                variant_attr  TEXT PATH '$.attr',
                variant_value TEXT PATH '$.value'
            ),
            -- 保修展开
            warranty_type  TEXT DEFAULT 'none' ON EMPTY PATH '$.warranty.type',
            warranty_years INT DEFAULT 0 ON EMPTY PATH '$.warranty.years',
            warranty_price NUMERIC(10,2) DEFAULT 0 ON EMPTY PATH '$.warranty.price',
            warranty_total NUMERIC(10,2) DEFAULT 0 ON EMPTY PATH '$.warranty.total_price',
            -- 第三层NESTED: 商品标签
            NESTED PATH '$.tags[*]' AS tags COLUMNS (
                item_tag TEXT PATH '$'
            ),
            item_category TEXT DEFAULT 'uncategorized' ON EMPTY PATH '$.category'
        ),
        -- 收货地址展开
        shipping_address TEXT PATH '$.shipping.address.street',
        shipping_city    TEXT PATH '$.shipping.address.city',
        shipping_zip     TEXT PATH '$.shipping.address.zipcode',
        -- 联系人NESTED
        NESTED PATH '$.customer.contacts[*]' AS contacts COLUMNS (
            contact_type   TEXT PATH '$.type',
            contact_value  TEXT PATH '$.value',
            contact_primary BOOLEAN DEFAULT false ON EMPTY PATH '$.primary'
        ),
        -- 状态时间线NESTED
        NESTED PATH '$.timeline[*]' AS timeline COLUMNS (
            status_type      TEXT PATH '$.status',
            status_timestamp TIMESTAMPTZ PATH '$.timestamp'
        ),
        -- 支付信息
        payment_method TEXT PATH '$.payment.method',
        payment_total  NUMERIC(12,2) PATH '$.payment.total',
        payment_currency TEXT DEFAULT 'CNY' ON EMPTY PATH '$.payment.currency'
    )
) AS jt2
WHERE jt2.item_sku IS NOT NULL
ORDER BY jt2.order_id, jt2.item_sku, jt2.variant_attr;

-- ============================================================================
-- Part 3: MERGE增强 - RETURNING子句 + 多WHEN子句 (PG17)
-- ============================================================================

CREATE TABLE IF NOT EXISTS pg17_product_catalog (
    sku TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    category TEXT NOT NULL,
    base_price NUMERIC(10,2) NOT NULL,
    current_price NUMERIC(10,2) NOT NULL,
    stock_level INT NOT NULL DEFAULT 0,
    attributes JSONB NOT NULL DEFAULT '{}',
    version INT NOT NULL DEFAULT 1,
    last_updated TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by TEXT NOT NULL DEFAULT 'system'
);

CREATE TABLE IF NOT EXISTS pg17_price_updates (
    batch_id UUID NOT NULL DEFAULT gen_random_uuid(),
    sku TEXT NOT NULL,
    new_price NUMERIC(10,2),
    price_change_pct NUMERIC(5,2),
    stock_adjustment INT DEFAULT 0,
    attribute_updates JSONB,
    reason TEXT NOT NULL,
    approver TEXT,
    processed BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT now()
);

-- MERGE with RETURNING
WITH
-- 准备合并数据
update_batch AS (
    SELECT
        pu.sku,
        pu.new_price,
        pu.price_change_pct,
        pu.stock_adjustment,
        pu.attribute_updates,
        pu.reason,
        pu.approver,
        pu.batch_id
    FROM pg17_price_updates pu
    WHERE pu.processed = false
      AND pu.created_at >= CURRENT_TIMESTAMP - INTERVAL '1 hour'
),
-- 执行MERGE并捕获结果
merge_results AS (
    MERGE INTO pg17_product_catalog pc
    USING update_batch ub
    ON pc.sku = ub.sku
    -- 场景1: 价格大幅上涨 (>20%) 需要审批
    WHEN MATCHED AND ub.price_change_pct > 20 AND ub.approver IS NOT NULL THEN
        UPDATE SET
            current_price = ub.new_price,
            last_updated = now(),
            updated_by = ub.approver,
            version = pc.version + 1
    -- 场景2: 价格小幅调整 + 库存变动
    WHEN MATCHED AND ub.price_change_pct <= 20 AND ub.price_change_pct >= -10 THEN
        UPDATE SET
            current_price = ub.new_price,
            stock_level = pc.stock_level + COALESCE(ub.stock_adjustment, 0),
            attributes = pc.attributes || COALESCE(ub.attribute_updates, '{}'::jsonb),
            last_updated = now(),
            updated_by = COALESCE(ub.approver, 'auto'),
            version = pc.version + 1
    -- 场景3: 价格大幅下降 (>10%) - 记录但不更新属性
    WHEN MATCHED AND ub.price_change_pct < -10 THEN
        UPDATE SET
            current_price = ub.new_price,
            last_updated = now(),
            updated_by = COALESCE(ub.approver, 'system'),
            version = pc.version + 1
    -- 场景4: 新产品 - 只在有审批时插入
    WHEN NOT MATCHED AND ub.approver IS NOT NULL THEN
        INSERT (sku, name, category, base_price, current_price, stock_level,
                attributes, version, last_updated, updated_by)
        VALUES (
            ub.sku,
            'NEW_PRODUCT_' || ub.sku,
            'UNCATEGORIZED',
            ub.new_price,
            ub.new_price,
            COALESCE(ub.stock_adjustment, 0),
            COALESCE(ub.attribute_updates, '{}'::jsonb),
            1,
            now(),
            ub.approver
        )
    -- PG17新增: RETURNING
    RETURNING
        pc.sku,
        pc.name,
        pc.current_price,
        pc.stock_level,
        pc.version,
        pc.last_updated,
        pc.updated_by,
        merge_action() AS action
)
-- 使用MERGE返回结果
SELECT
    mr.action,
    count(*) AS affected_count,
    array_agg(
        jsonb_build_object(
            'sku', mr.sku,
            'name', mr.name,
            'new_price', mr.current_price,
            'stock', mr.stock_level,
            'version', mr.version,
            'updated_by', mr.updated_by
        )
        ORDER BY mr.sku
    ) AS affected_products,
    jsonb_object_agg(
        mr.action,
        count(*)::text
    ) AS action_summary
FROM merge_results mr
GROUP BY mr.action
ORDER BY mr.action;

-- ============================================================================
-- Part 4: JSON_TABLE 与窗口函数深度集成
-- ============================================================================

WITH
-- 使用JSON_TABLE展开所有订单
all_orders_flat AS (
    SELECT
        jt_all.*
    FROM pg17_order_documents od
    CROSS JOIN JSON_TABLE(
        od.order_data,
        '$' COLUMNS (
            order_id         TEXT PATH '$.order_id',
            customer_id      TEXT PATH '$.customer.id',
            customer_type    TEXT PATH '$.customer.type',
            order_date       DATE PATH '$.timeline[0].timestamp',
            NESTED PATH '$.items[*]' AS items COLUMNS (
                sku      TEXT PATH '$.sku',
                name     TEXT PATH '$.name',
                qty      INT PATH '$.qty',
                price    NUMERIC(10,2) PATH '$.unit_price',
                discount NUMERIC(5,4) DEFAULT 0 ON EMPTY PATH '$.discount',
                NESTED PATH '$.variants[*]' AS vars COLUMNS (
                    attr  TEXT PATH '$.attr',
                    value TEXT PATH '$.value'
                )
            ),
            shipping_city TEXT PATH '$.shipping.address.city',
            payment_total NUMERIC(12,2) PATH '$.payment.total',
            NESTED PATH '$.timeline[*]' AS tl COLUMNS (
                status    TEXT PATH '$.status',
                ts        TIMESTAMPTZ PATH '$.timestamp'
            )
        )
    ) AS jt_all
),
-- 商品级别统计
product_analytics AS (
    SELECT
        aof.sku,
        aof.name,
        count(DISTINCT aof.order_id) AS order_count,
        sum(aof.qty) AS total_units_sold,
        round(sum(aof.qty * aof.price * (1 - aof.discount))::numeric, 2) AS total_revenue,
        round(avg(aof.price)::numeric, 2) AS avg_price,
        round(avg(aof.discount)::numeric, 4) AS avg_discount,
        count(DISTINCT aof.shipping_city) AS cities_reached,
        array_agg(DISTINCT aof.attr || ':' || aof.value ORDER BY aof.attr, aof.value)
            FILTER (WHERE aof.attr IS NOT NULL) AS all_variants,
        -- 窗口排名
        row_number() OVER (ORDER BY sum(aof.qty * aof.price * (1 - aof.discount)) DESC) AS revenue_rank,
        dense_rank() OVER (ORDER BY count(DISTINCT aof.order_id) DESC) AS popularity_rank,
        percent_rank() OVER (ORDER BY sum(aof.qty)) AS quantity_percentile,
        -- 与"自己"对比
        round(
            (sum(aof.qty * aof.price * (1 - aof.discount)) /
             NULLIF(sum(sum(aof.qty * aof.price * (1 - aof.discount))) OVER(), 0) * 100)::numeric, 2
        ) AS revenue_share_pct,
        -- 累积
        sum(sum(aof.qty * aof.price * (1 - aof.discount))) OVER (
            ORDER BY sum(aof.qty * aof.price * (1 - aof.discount)) DESC
            ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
        ) AS cumulative_revenue
    FROM all_orders_flat aof
    WHERE aof.sku IS NOT NULL
    GROUP BY aof.sku, aof.name
),
-- 客户分段
customer_segments AS (
    SELECT
        aof.customer_id,
        aof.customer_type,
        count(DISTINCT aof.order_id) AS customer_orders,
        sum(aof.payment_total) AS customer_total_spent,
        round(avg(aof.payment_total)::numeric, 2) AS customer_avg_order,
        array_agg(DISTINCT aof.shipping_city ORDER BY aof.shipping_city) AS customer_cities,
        -- 客户商品偏好
        mode() WITHIN GROUP (ORDER BY aof.sku) AS favorite_sku,
        mode() WITHIN GROUP (ORDER BY aof.name) AS favorite_product
    FROM all_orders_flat aof
    WHERE aof.customer_id IS NOT NULL
    GROUP BY aof.customer_id, aof.customer_type
)
-- 主查询: 交叉分析
SELECT
    pa.sku,
    pa.name,
    pa.revenue_rank,
    pa.popularity_rank,
    pa.total_revenue,
    pa.revenue_share_pct,
    pa.total_units_sold,
    pa.cities_reached,
    pa.avg_discount,
    pa.all_variants,
    -- 客户分析嵌套
    (
        SELECT jsonb_agg(
            jsonb_build_object(
                'customer_id', cs.customer_id,
                'customer_type', cs.customer_type,
                'orders', cs.customer_orders,
                'total_spent', cs.customer_total_spent,
                'is_favorite', cs.favorite_sku = pa.sku
            )
            ORDER BY cs.customer_total_spent DESC
        )
        FROM customer_segments cs
        WHERE cs.favorite_sku = pa.sku
        LIMIT 10
    ) AS top_customers_for_sku,
    -- 排名变化
    pa.revenue_rank - pa.popularity_rank AS rank_divergence,
    CASE
        WHEN pa.revenue_rank <= 5 AND pa.popularity_rank <= 5 THEN 'STAR_PRODUCT'
        WHEN pa.revenue_rank <= 5 AND pa.popularity_rank > 5 THEN 'HIGH_VALUE_NICHE'
        WHEN pa.revenue_rank > 5 AND pa.popularity_rank <= 5 THEN 'POPULAR_LOW_VALUE'
        ELSE 'STANDARD'
    END AS product_segment,
    -- 累计
    pa.cumulative_revenue,
    round(
        (pa.cumulative_revenue / NULLIF(
            (SELECT sum(pa2.total_revenue) FROM product_analytics pa2), 0) * 100
        )::numeric, 2
    ) AS cumulative_revenue_pct
FROM product_analytics pa
ORDER BY pa.revenue_rank;

-- ============================================================================
-- Part 5: 极致嵌套 - JSON_TABLE + CTE + 窗口 + LATERAL + 子查询
-- ============================================================================

WITH RECURSIVE
-- 递归CTE: 构建商品组合树
product_bundles AS (
    SELECT
        sku AS root_sku,
        sku AS component_sku,
        1 AS quantity,
        0 AS depth,
        ARRAY[sku] AS bundle_path,
        false AS has_cycle
    FROM pg17_product_catalog
    WHERE attributes->>'bundle_type' IS NULL
    UNION ALL
    SELECT
        pb.root_sku,
        comp.sku,
        pb.quantity * COALESCE(
            (comp.attributes->'bundle_config'->pb.component_sku->>'qty')::int, 1
        ),
        pb.depth + 1,
        pb.bundle_path || comp.sku,
        comp.sku = ANY(pb.bundle_path)
    FROM product_bundles pb
    JOIN pg17_product_catalog comp
        ON comp.attributes->'bundle_config' ? pb.component_sku
    WHERE NOT pb.has_cycle
      AND pb.depth < 10
),
-- 订单展开
order_products AS (
    SELECT
        jt_items.order_id,
        jt_items.customer_id,
        jt_items.sku,
        jt_items.name,
        jt_items.qty,
        jt_items.price,
        jt_items.discount,
        jt_items.shipping_city,
        jt_items.order_status,
        jt_items.order_date
    FROM pg17_order_documents od
    CROSS JOIN JSON_TABLE(
        od.order_data,
        '$' COLUMNS (
            order_id    TEXT PATH '$.order_id',
            customer_id TEXT PATH '$.customer.id',
            NESTED PATH '$.items[*]' AS item COLUMNS (
                sku      TEXT PATH '$.sku',
                name     TEXT PATH '$.name',
                qty      INT PATH '$.qty',
                price    NUMERIC(10,2) PATH '$.unit_price',
                discount NUMERIC(5,4) DEFAULT 0 ON EMPTY PATH '$.discount'
            ),
            shipping_city TEXT PATH '$.shipping.address.city',
            NESTED PATH '$.timeline[*]' AS tl COLUMNS (
                order_status TEXT PATH '$.status',
                order_date   DATE PATH '$.timestamp'
            )
        )
    ) AS jt_items
    WHERE jt_items.order_status = 'created'
),
-- 交叉分析
bundle_sales AS (
    SELECT
        pb.root_sku,
        pb.component_sku,
        pb.quantity AS bundle_qty,
        pb.depth,
        count(DISTINCT op.order_id) AS orders_containing,
        sum(op.qty * pb.quantity) AS implied_units,
        sum(op.qty * op.price * (1 - op.discount) * pb.quantity) AS implied_revenue,
        array_agg(DISTINCT op.shipping_city ORDER BY op.shipping_city) AS cities,
        avg(op.discount) AS avg_order_discount
    FROM product_bundles pb
    JOIN order_products op ON pb.component_sku = op.sku
    WHERE pb.depth > 0
    GROUP BY pb.root_sku, pb.component_sku, pb.quantity, pb.depth
)
SELECT
    bs.root_sku,
    bs.component_sku,
    bs.depth AS bundle_depth,
    bs.orders_containing,
    bs.implied_units,
    bs.implied_revenue,
    bs.cities,
    bs.avg_order_discount,
    (
        SELECT jsonb_build_object(
            'root_product', pc.name,
            'root_price', pc.current_price,
            'component_name', (
                SELECT pc2.name FROM pg17_product_catalog pc2
                WHERE pc2.sku = bs.component_sku
            ),
            'component_price', (
                SELECT pc3.current_price FROM pg17_product_catalog pc3
                WHERE pc3.sku = bs.component_sku
            ),
            'price_ratio', round(
                ((SELECT pc4.current_price FROM pg17_product_catalog pc4
                  WHERE pc4.sku = bs.component_sku) /
                 NULLIF(pc.current_price, 0))::numeric, 4
            )
        )
        FROM pg17_product_catalog pc
        WHERE pc.sku = bs.root_sku
    ) AS product_info
FROM bundle_sales bs
ORDER BY bs.implied_revenue DESC;