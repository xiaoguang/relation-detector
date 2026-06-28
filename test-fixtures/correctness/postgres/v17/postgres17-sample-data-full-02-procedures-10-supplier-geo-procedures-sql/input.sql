-- relation-detector-fixture-source: ROUTINE:public.fn_haversine_distance
CREATE OR REPLACE FUNCTION fn_haversine_distance(
    p_lat1 NUMERIC(10,7),
    p_lon1 NUMERIC(10,7),
    p_lat2 NUMERIC(10,7),
    p_lon2 NUMERIC(10,7)
)
RETURNS NUMERIC(10,2)
LANGUAGE plpgsql
IMMUTABLE
AS $$
DECLARE
    v_r        NUMERIC(10,2)  DEFAULT 6371.0;
    v_dlat     NUMERIC(15,10);
    v_dlon     NUMERIC(15,10);
    v_a        NUMERIC(15,10);
    v_c        NUMERIC(15,10);
    v_distance NUMERIC(10,2);
BEGIN
    IF p_lat1 IS NULL OR p_lon1 IS NULL OR p_lat2 IS NULL OR p_lon2 IS NULL THEN
        RETURN 9999.00;
    END IF;

    v_dlat := RADIANS(p_lat2 - p_lat1);
    v_dlon := RADIANS(p_lon2 - p_lon1);
    v_a := SIN(v_dlat / 2) * SIN(v_dlat / 2)
         + COS(RADIANS(p_lat1)) * COS(RADIANS(p_lat2))
         * SIN(v_dlon / 2) * SIN(v_dlon / 2);
    v_c := 2 * ATAN2(SQRT(v_a), SQRT(1 - v_a));
    v_distance := ROUND(v_r * v_c, 2);

    RETURN v_distance;
END;
$$;


-- ============================================================
-- 70. 估算物流费用
-- 调用方: 供应商选择、采购成本估算
-- 计算原理: 物流费 = 距离(km) * 每公里费率 * 重量系数
--           重量系数基于货品重量和数量
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:public.fn_estimate_shipping_cost
CREATE OR REPLACE FUNCTION fn_estimate_shipping_cost(
    p_supplier_id  BIGINT,
    p_warehouse_id BIGINT,
    p_product_id   BIGINT,
    p_quantity     INTEGER
)
RETURNS NUMERIC(12,2)
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
    v_distance      NUMERIC(10,2);
    v_rate_per_km   NUMERIC(8,4);
    v_weight_kg     NUMERIC(8,3);
    v_shipping_cost NUMERIC(12,2);
    v_supplier_lat  NUMERIC(10,7);
    v_supplier_lon  NUMERIC(10,7);
    v_wh_lat        NUMERIC(10,7);
    v_wh_lon        NUMERIC(10,7);
BEGIN
    SELECT s.latitude, s.longitude INTO v_supplier_lat, v_supplier_lon
    FROM suppliers s WHERE s.id = p_supplier_id;

    SELECT w.latitude, w.longitude INTO v_wh_lat, v_wh_lon
    FROM warehouses w WHERE w.id = p_warehouse_id;

    SELECT sp.shipping_cost_per_km, p.weight_kg
    INTO v_rate_per_km, v_weight_kg
    FROM supplier_products sp
    JOIN products p ON sp.product_id = p.id
    WHERE sp.supplier_id = p_supplier_id AND sp.product_id = p_product_id;

    v_distance := fn_haversine_distance(v_supplier_lat, v_supplier_lon, v_wh_lat, v_wh_lon);
    v_shipping_cost := ROUND(v_distance * COALESCE(v_rate_per_km, 0.5) * (1 + COALESCE(v_weight_kg, 0) * p_quantity / 100.0), 2);

    RETURN v_shipping_cost;
END;
$$;


-- ============================================================
-- 71. 供应商综合评分
-- 调用方: 供应商选择、采购决策
-- 评分原理: 每项满分100分，加权求和
--   价格分 = (1 - 供应商价格/市场均价) * 100 (价格越低分越高)
--   距离分 = (1 - 距离/5000km) * 100 (距离越近分越高)
--   退货率分 = (1 - 退货率) * 100 (退货率越低分越高)
--   质量分 = 质检评分(直接使用)
--   交期分 = (1 - 交期/30天) * 100 (交期越短分越高)
-- 综合分 = 价格*30% + 距离*25% + 退货率*20% + 质量*15% + 交期*10%
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:public.fn_supplier_score
CREATE OR REPLACE FUNCTION fn_supplier_score(
    p_supplier_id  BIGINT,
    p_product_id   BIGINT,
    p_warehouse_id BIGINT
)
RETURNS NUMERIC(5,2)
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
    v_price_score      NUMERIC(5,2);
    v_distance_score   NUMERIC(5,2);
    v_return_score     NUMERIC(5,2);
    v_quality_score    NUMERIC(5,2);
    v_lead_time_score  NUMERIC(5,2);
    v_total_score      NUMERIC(5,2);
    v_supplier_price   NUMERIC(12,2);
    v_avg_price        NUMERIC(12,2);
    v_distance         NUMERIC(10,2);
    v_return_rate      NUMERIC(5,4);
    v_quality          NUMERIC(5,2);
    v_lead_time        INTEGER;
    v_supplier_lat     NUMERIC(10,7);
    v_supplier_lon     NUMERIC(10,7);
    v_wh_lat           NUMERIC(10,7);
    v_wh_lon           NUMERIC(10,7);
BEGIN
    -- 供应商价格
    SELECT sp.supplier_price, sp.return_rate, sp.quality_score, sp.lead_time_days
    INTO v_supplier_price, v_return_rate, v_quality, v_lead_time
    FROM supplier_products sp
    WHERE sp.supplier_id = p_supplier_id AND sp.product_id = p_product_id;

    -- 市场均价(所有供应商)
    SELECT AVG(sp.supplier_price) INTO v_avg_price
    FROM supplier_products sp WHERE sp.product_id = p_product_id;

    -- 距离
    SELECT s.latitude, s.longitude INTO v_supplier_lat, v_supplier_lon
    FROM suppliers s WHERE s.id = p_supplier_id;

    SELECT w.latitude, w.longitude INTO v_wh_lat, v_wh_lon
    FROM warehouses w WHERE w.id = p_warehouse_id;

    v_distance := fn_haversine_distance(v_supplier_lat, v_supplier_lon, v_wh_lat, v_wh_lon);

    -- 价格分: 价格越低越好
    IF v_avg_price > 0 THEN
        v_price_score := GREATEST(0, LEAST(100, ROUND((1 - v_supplier_price / v_avg_price) * 100, 2)));
    ELSE
        v_price_score := 50;
    END IF;

    -- 距离分: 距离越近越好
    v_distance_score := GREATEST(0, LEAST(100, ROUND((1 - v_distance / 5000.0) * 100, 2)));

    -- 退货率分: 退货率越低越好
    v_return_score := GREATEST(0, LEAST(100, ROUND((1 - COALESCE(v_return_rate, 0)) * 100, 2)));

    -- 质量分: 直接使用
    v_quality_score := COALESCE(v_quality, 80);

    -- 交期分: 交期越短越好
    v_lead_time_score := GREATEST(0, LEAST(100, ROUND((1 - COALESCE(v_lead_time, 15) / 30.0) * 100, 2)));

    -- 综合评分
    v_total_score := ROUND(
        v_price_score * 0.30
        + v_distance_score * 0.25
        + v_return_score * 0.20
        + v_quality_score * 0.15
        + v_lead_time_score * 0.10
    , 2);

    RETURN v_total_score;
END;
$$;


-- ============================================================
-- 72. 智能选择最优供应商
-- 调用方: 采购流程中自动推荐供应商
-- 参数: p_product_id, p_warehouse_id, p_quantity
-- 输出: 按综合评分排名的供应商列表(含价格/距离/物流费/退货率/质量/交期)
-- 选择逻辑:
--   1. 计算每个供应商的综合评分
--   2. 计算到门店的距离和物流费用
--   3. 计算总成本 = 报价*数量 + 物流费
--   4. 标记黑名单/暂停供应商为不可用
--   5. 按综合评分降序排列
-- 注意: PostgreSQL过程不能直接返回结果集，改为函数返回TABLE
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:public.sp_find_best_supplier
CREATE OR REPLACE FUNCTION sp_find_best_supplier(
    p_product_id   BIGINT,
    p_warehouse_id BIGINT,
    p_quantity     INTEGER
)
RETURNS TABLE(
    supplier_id          BIGINT,
    supplier_name        VARCHAR(255),
    supplier_code        VARCHAR(100),
    supplier_city        VARCHAR(100),
    supplier_province    VARCHAR(100),
    credit_level         VARCHAR(50),
    supplier_price       NUMERIC(12,2),
    lead_time_days       INTEGER,
    min_order_qty        INTEGER,
    return_rate          NUMERIC(5,4),
    return_rate_pct      NUMERIC(10,2),
    quality_score        NUMERIC(5,2),
    shipping_cost_per_km NUMERIC(8,4),
    total_order_count    INTEGER,
    last_order_date      DATE,
    distance_km          NUMERIC(10,2),
    estimated_shipping   NUMERIC(12,2),
    product_total        NUMERIC(12,2),
    total_cost           NUMERIC(12,2),
    composite_score      NUMERIC(5,2),
    price_score          NUMERIC(10,2),
    distance_score       NUMERIC(10,2),
    return_score         NUMERIC(10,2),
    quality_score_raw    NUMERIC(5,2),
    lead_time_score      NUMERIC(10,2),
    availability         TEXT,
    cooperation_status   VARCHAR(50),
    target_warehouse     VARCHAR(255),
    warehouse_city       VARCHAR(100)
)
LANGUAGE plpgsql
STABLE
AS $$
BEGIN
    RETURN QUERY
    SELECT
        s.id AS supplier_id,
        s.name AS supplier_name,
        s.code AS supplier_code,
        s.city AS supplier_city,
        s.province AS supplier_province,
        s.credit_level,
        sp.supplier_price,
        sp.lead_time_days,
        sp.min_order_qty,
        sp.return_rate,
        ROUND(COALESCE(sp.return_rate, 0) * 100, 2) AS return_rate_pct,
        sp.quality_score,
        sp.shipping_cost_per_km,
        sp.total_order_count,
        sp.last_order_date,
        -- 距离
        fn_haversine_distance(s.latitude, s.longitude, w.latitude, w.longitude) AS distance_km,
        -- 物流费
        fn_estimate_shipping_cost(s.id, p_warehouse_id, p_product_id, p_quantity) AS estimated_shipping,
        -- 货品总价
        ROUND(sp.supplier_price * p_quantity, 2) AS product_total,
        -- 总成本 = 货品总价 + 物流费
        ROUND(sp.supplier_price * p_quantity
            + fn_estimate_shipping_cost(s.id, p_warehouse_id, p_product_id, p_quantity), 2) AS total_cost,
        -- 综合评分
        fn_supplier_score(s.id, p_product_id, p_warehouse_id) AS composite_score,
        -- 各维度评分
        ROUND((1 - sp.supplier_price / NULLIF((SELECT AVG(sp2.supplier_price) FROM supplier_products sp2 WHERE sp2.product_id = p_product_id), 0)) * 100, 2) AS price_score,
        ROUND((1 - fn_haversine_distance(s.latitude, s.longitude, w.latitude, w.longitude) / 5000.0) * 100, 2) AS distance_score,
        ROUND((1 - COALESCE(sp.return_rate, 0)) * 100, 2) AS return_score,
        sp.quality_score AS quality_score_raw,
        ROUND((1 - COALESCE(sp.lead_time_days, 15) / 30.0) * 100, 2) AS lead_time_score,
        -- 是否可用
        CASE
            WHEN s.cooperation_status = 'blacklist' THEN '黑名单-不可用'
            WHEN s.cooperation_status = 'suspended' THEN '暂停-不可用'
            WHEN COALESCE(sp.return_rate, 0) > 0.3 THEN '退货率过高-慎用'
            WHEN sp.quality_score < 60 THEN '质量评分低-慎用'
            ELSE '可用'
        END AS availability,
        s.cooperation_status,
        w.name AS target_warehouse,
        w.city AS warehouse_city
    FROM suppliers s
    JOIN supplier_products sp ON s.id = sp.supplier_id AND sp.product_id = p_product_id
    CROSS JOIN warehouses w
    WHERE w.id = p_warehouse_id
    ORDER BY composite_score DESC;
END;
$$;


-- ============================================================
-- 73. 供应商对比分析 - 同产品多供应商PK
-- 参数: p_product_id
-- 输出: 该产品所有供应商的价格/距离/退货率/质量/交期/评分对比
-- 注意: PostgreSQL过程不能直接返回结果集，改为函数返回TABLE
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:public.sp_compare_suppliers_for_product
CREATE OR REPLACE FUNCTION sp_compare_suppliers_for_product(
    p_product_id BIGINT
)
RETURNS TABLE(
    supplier_name              VARCHAR(255),
    supplier_city              VARCHAR(100),
    supplier_province          VARCHAR(100),
    credit_level               VARCHAR(50),
    supplier_price             NUMERIC(12,2),
    price_vs_avg_pct           NUMERIC(10,2),
    price_flag                 TEXT,
    lead_time_days             INTEGER,
    min_order_qty              INTEGER,
    return_rate                NUMERIC(5,4),
    return_rate_pct            NUMERIC(10,2),
    quality_score              NUMERIC(5,2),
    shipping_cost_per_km       NUMERIC(8,4),
    total_order_count          INTEGER,
    total_order_qty            INTEGER,
    last_order_date            DATE,
    is_preferred               BOOLEAN,
    avg_distance_to_stores_km  NUMERIC(10,0),
    cooperation_status         VARCHAR(50),
    price_rank                 BIGINT,
    return_rank                BIGINT,
    quality_rank               BIGINT,
    lead_time_rank             BIGINT
)
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
    v_avg_price    NUMERIC(12,2);
    v_lowest_price NUMERIC(12,2);
BEGIN
    SELECT AVG(supplier_price), MIN(supplier_price)
    INTO v_avg_price, v_lowest_price
    FROM supplier_products WHERE product_id = p_product_id;

    RETURN QUERY
    SELECT
        s.name AS supplier_name,
        s.city AS supplier_city,
        s.province AS supplier_province,
        s.credit_level,
        sp.supplier_price,
        ROUND((sp.supplier_price - v_avg_price) / NULLIF(v_avg_price, 0) * 100, 2) AS price_vs_avg_pct,
        CASE WHEN sp.supplier_price = v_lowest_price THEN '最低价' ELSE '' END AS price_flag,
        sp.lead_time_days,
        sp.min_order_qty,
        sp.return_rate,
        ROUND(COALESCE(sp.return_rate, 0) * 100, 2) AS return_rate_pct,
        sp.quality_score,
        sp.shipping_cost_per_km,
        sp.total_order_count,
        sp.total_order_qty,
        sp.last_order_date,
        sp.is_preferred,
        -- 到各仓库的平均距离
        ROUND(AVG(fn_haversine_distance(s.latitude, s.longitude, w.latitude, w.longitude)), 0) AS avg_distance_to_stores_km,
        s.cooperation_status,
        -- 对比排名
        RANK() OVER (ORDER BY sp.supplier_price ASC) AS price_rank,
        RANK() OVER (ORDER BY COALESCE(sp.return_rate, 0) ASC) AS return_rank,
        RANK() OVER (ORDER BY sp.quality_score DESC) AS quality_rank,
        RANK() OVER (ORDER BY sp.lead_time_days ASC) AS lead_time_rank
    FROM suppliers s
    JOIN supplier_products sp ON s.id = sp.supplier_id AND sp.product_id = p_product_id
    LEFT JOIN warehouses w ON w.status = 'active'
    GROUP BY s.id, s.name, s.city, s.province, s.credit_level,
             sp.supplier_price, sp.lead_time_days, sp.min_order_qty,
             sp.return_rate, sp.quality_score, sp.shipping_cost_per_km,
             sp.total_order_count, sp.total_order_qty, sp.last_order_date,
             sp.is_preferred, s.cooperation_status
    ORDER BY sp.supplier_price ASC;
END;
$$;


-- ============================================================
-- 74. 供应商地理分布分析 - 门店辐射范围
-- 参数: p_warehouse_id
-- 输出: 该门店周边供应商分布，按距离排序
-- 注意: PostgreSQL过程不能直接返回结果集，改为函数返回TABLE
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:public.sp_supplier_geographic_analysis
CREATE OR REPLACE FUNCTION sp_supplier_geographic_analysis(
    p_warehouse_id BIGINT
)
RETURNS TABLE(
    warehouse_name          VARCHAR(255),
    warehouse_city          VARCHAR(100),
    supplier_name           VARCHAR(255),
    supplier_city           VARCHAR(100),
    supplier_province       VARCHAR(100),
    distance_km             NUMERIC(10,2),
    supplied_products       BIGINT,
    avg_price               NUMERIC(12,2),
    avg_quality             NUMERIC(5,2),
    avg_return_rate         NUMERIC,
    estimated_base_shipping NUMERIC(12,2),
    distance_zone           TEXT,
    credit_level            VARCHAR(50),
    cooperation_status      VARCHAR(50)
)
LANGUAGE plpgsql
STABLE
AS $$
BEGIN
    RETURN QUERY
    SELECT
        w.name AS warehouse_name,
        w.city AS warehouse_city,
        s.name AS supplier_name,
        s.city AS supplier_city,
        s.province AS supplier_province,
        fn_haversine_distance(s.latitude, s.longitude, w.latitude, w.longitude) AS distance_km,
        COUNT(DISTINCT sp.product_id) AS supplied_products,
        AVG(sp.supplier_price) AS avg_price,
        AVG(sp.quality_score) AS avg_quality,
        AVG(COALESCE(sp.return_rate, 0)) AS avg_return_rate,
        ROUND(fn_haversine_distance(s.latitude, s.longitude, w.latitude, w.longitude) * COALESCE(AVG(sp.shipping_cost_per_km), 0.5), 2) AS estimated_base_shipping,
        CASE
            WHEN fn_haversine_distance(s.latitude, s.longitude, w.latitude, w.longitude) <= 50 THEN '同城'
            WHEN fn_haversine_distance(s.latitude, s.longitude, w.latitude, w.longitude) <= 200 THEN '省内'
            WHEN fn_haversine_distance(s.latitude, s.longitude, w.latitude, w.longitude) <= 500 THEN '邻省'
            WHEN fn_haversine_distance(s.latitude, s.longitude, w.latitude, w.longitude) <= 1000 THEN '远距离'
            ELSE '超远距离'
        END AS distance_zone,
        s.credit_level,
        s.cooperation_status
    FROM warehouses w
    JOIN supplier_products sp ON TRUE
    JOIN suppliers s ON sp.supplier_id = s.id
    WHERE w.id = p_warehouse_id
      AND s.cooperation_status = 'active'
    GROUP BY w.name, w.city, s.id, s.name, s.city, s.province, s.latitude, s.longitude, w.latitude, w.longitude, s.credit_level, s.cooperation_status
    ORDER BY distance_km ASC;
END;
$$;


-- ============================================================
-- 75. 批量更新供应商综合指标
-- 调用方: 定期任务，根据历史数据更新供应商评分
-- 更新内容: return_rate(基于采购退货), quality_score(基于质检),
--           total_order_count, total_order_qty, last_order_date
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:public.sp_update_supplier_metrics
CREATE OR REPLACE PROCEDURE sp_update_supplier_metrics()
LANGUAGE plpgsql
AS $$
DECLARE
    v_row_count INTEGER;
BEGIN
    -- 更新退货率: 基于近12个月采购退货
    UPDATE supplier_products sp
    SET return_rate = COALESCE((
        SELECT SUM(pri.return_qty) * 1.0 / NULLIF(SUM(pri.return_qty) + (
            SELECT SUM(poi.received_qty)
            FROM purchase_order_items poi
            JOIN purchase_orders po ON poi.order_id = po.id
            WHERE poi.product_id = sp.product_id AND po.supplier_id = sp.supplier_id
              AND po.order_date >= CURRENT_DATE - INTERVAL '12 months'
        ), 0)
        FROM purchase_returns pr
        JOIN purchase_return_items pri ON pr.id = pri.return_id
        WHERE pr.supplier_id = sp.supplier_id
          AND pri.product_id = sp.product_id
          AND pr.return_date >= CURRENT_DATE - INTERVAL '12 months'
    ), 0);

    -- 更新质量评分: 基于近12个月质检
    UPDATE supplier_products sp
    SET quality_score = COALESCE((
        SELECT ROUND(COUNT(CASE WHEN ir.inspection_result = 'qualified' THEN 1 END) * 100.0
            / NULLIF(COUNT(*), 0), 2)
        FROM inspection_reports ir
        JOIN product_batches pb ON ir.batch_id = pb.id
        WHERE pb.supplier_id = sp.supplier_id
          AND ir.product_id = sp.product_id
          AND ir.inspection_date >= CURRENT_DATE - INTERVAL '12 months'
    ), 100);

    -- 更新累计采购次数和数量
    UPDATE supplier_products sp
    SET
        total_order_count = (
            SELECT COUNT(DISTINCT po.id)
            FROM purchase_order_items poi
            JOIN purchase_orders po ON poi.order_id = po.id
            WHERE poi.product_id = sp.product_id AND po.supplier_id = sp.supplier_id
        ),
        total_order_qty = (
            SELECT COALESCE(SUM(poi.received_qty), 0)
            FROM purchase_order_items poi
            JOIN purchase_orders po ON poi.order_id = po.id
            WHERE poi.product_id = sp.product_id AND po.supplier_id = sp.supplier_id
        ),
        last_order_date = (
            SELECT MAX(po.order_date)
            FROM purchase_order_items poi
            JOIN purchase_orders po ON poi.order_id = po.id
            WHERE poi.product_id = sp.product_id AND po.supplier_id = sp.supplier_id
        );

    GET DIAGNOSTICS v_row_count = ROW_COUNT;
    RAISE NOTICE '供应商指标更新完成: %条', v_row_count;
END;
$$;
-- relation-detector-fixture-end
