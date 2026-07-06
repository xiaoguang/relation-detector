-- relation-detector-fixture-source: ROUTINE:erp_system.fn_haversine_distance
CREATE FUNCTION fn_haversine_distance(
    p_lat1 DECIMAL(10,7),
    p_lon1 DECIMAL(10,7),
    p_lat2 DECIMAL(10,7),
    p_lon2 DECIMAL(10,7)
)
RETURNS DECIMAL(10,2)
DETERMINISTIC
NO SQL
BEGIN
    DECLARE v_r DECIMAL(10,2) DEFAULT 6371.0;
    DECLARE v_dlat DECIMAL(15,10);
    DECLARE v_dlon DECIMAL(15,10);
    DECLARE v_a DECIMAL(15,10);
    DECLARE v_c DECIMAL(15,10);
    DECLARE v_distance DECIMAL(10,2);

    IF p_lat1 IS NULL OR p_lon1 IS NULL OR p_lat2 IS NULL OR p_lon2 IS NULL THEN
        RETURN 9999.00;
    END IF;

    SET v_dlat = RADIANS(p_lat2 - p_lat1);
    SET v_dlon = RADIANS(p_lon2 - p_lon1);
    SET v_a = SIN(v_dlat / 2) * SIN(v_dlat / 2)
            + COS(RADIANS(p_lat1)) * COS(RADIANS(p_lat2))
            * SIN(v_dlon / 2) * SIN(v_dlon / 2);
    SET v_c = 2 * ATAN2(SQRT(v_a), SQRT(1 - v_a));
    SET v_distance = ROUND(v_r * v_c, 2);

    RETURN v_distance;
END//


-- ============================================================
-- 70. 估算物流费用
-- 调用方: 供应商选择、采购成本估算
-- 计算原理: 物流费 = 距离(km) * 每公里费率 * 重量系数
--           重量系数基于货品重量和数量
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:erp_system.fn_estimate_shipping_cost
CREATE FUNCTION fn_estimate_shipping_cost(
    p_supplier_id BIGINT UNSIGNED,
    p_warehouse_id BIGINT UNSIGNED,
    p_product_id BIGINT UNSIGNED,
    p_quantity INT
)
RETURNS DECIMAL(12,2)
DETERMINISTIC
READS SQL DATA
BEGIN
    DECLARE v_distance DECIMAL(10,2);
    DECLARE v_rate_per_km DECIMAL(8,4);
    DECLARE v_weight_kg DECIMAL(8,3);
    DECLARE v_shipping_cost DECIMAL(12,2);
    DECLARE v_supplier_lat DECIMAL(10,7);
    DECLARE v_supplier_lon DECIMAL(10,7);
    DECLARE v_wh_lat DECIMAL(10,7);
    DECLARE v_wh_lon DECIMAL(10,7);

    SELECT s.latitude, s.longitude INTO v_supplier_lat, v_supplier_lon
    FROM suppliers s WHERE s.id = p_supplier_id;

    SELECT w.latitude, w.longitude INTO v_wh_lat, v_wh_lon
    FROM warehouses w WHERE w.id = p_warehouse_id;

    SELECT sp.shipping_cost_per_km, p.weight_kg
    INTO v_rate_per_km, v_weight_kg
    FROM supplier_products sp
    JOIN products p ON sp.product_id = p.id
    WHERE sp.supplier_id = p_supplier_id AND sp.product_id = p_product_id;

    SET v_distance = fn_haversine_distance(v_supplier_lat, v_supplier_lon, v_wh_lat, v_wh_lon);
    SET v_shipping_cost = ROUND(v_distance * COALESCE(v_rate_per_km, 0.5) * (1 + COALESCE(v_weight_kg, 0) * p_quantity / 100.0), 2);

    RETURN v_shipping_cost;
END//


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

-- relation-detector-fixture-source: ROUTINE:erp_system.fn_supplier_score
CREATE FUNCTION fn_supplier_score(
    p_supplier_id BIGINT UNSIGNED,
    p_product_id BIGINT UNSIGNED,
    p_warehouse_id BIGINT UNSIGNED
)
RETURNS DECIMAL(5,2)
DETERMINISTIC
READS SQL DATA
BEGIN
    DECLARE v_price_score DECIMAL(5,2);
    DECLARE v_distance_score DECIMAL(5,2);
    DECLARE v_return_score DECIMAL(5,2);
    DECLARE v_quality_score DECIMAL(5,2);
    DECLARE v_lead_time_score DECIMAL(5,2);
    DECLARE v_total_score DECIMAL(5,2);
    DECLARE v_supplier_price DECIMAL(12,2);
    DECLARE v_avg_price DECIMAL(12,2);
    DECLARE v_distance DECIMAL(10,2);
    DECLARE v_return_rate DECIMAL(5,4);
    DECLARE v_quality DECIMAL(5,2);
    DECLARE v_lead_time INT;
    DECLARE v_supplier_lat DECIMAL(10,7);
    DECLARE v_supplier_lon DECIMAL(10,7);
    DECLARE v_wh_lat DECIMAL(10,7);
    DECLARE v_wh_lon DECIMAL(10,7);

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

    SET v_distance = fn_haversine_distance(v_supplier_lat, v_supplier_lon, v_wh_lat, v_wh_lon);

    -- 价格分: 价格越低越好
    IF v_avg_price > 0 THEN
        SET v_price_score = GREATEST(0, LEAST(100, ROUND((1 - v_supplier_price / v_avg_price) * 100, 2)));
    ELSE
        SET v_price_score = 50;
    END IF;

    -- 距离分: 距离越近越好
    SET v_distance_score = GREATEST(0, LEAST(100, ROUND((1 - v_distance / 5000.0) * 100, 2)));

    -- 退货率分: 退货率越低越好
    SET v_return_score = GREATEST(0, LEAST(100, ROUND((1 - COALESCE(v_return_rate, 0)) * 100, 2)));

    -- 质量分: 直接使用
    SET v_quality_score = COALESCE(v_quality, 80);

    -- 交期分: 交期越短越好
    SET v_lead_time_score = GREATEST(0, LEAST(100, ROUND((1 - COALESCE(v_lead_time, 15) / 30.0) * 100, 2)));

    -- 综合评分
    SET v_total_score = ROUND(
        v_price_score * 0.30
        + v_distance_score * 0.25
        + v_return_score * 0.20
        + v_quality_score * 0.15
        + v_lead_time_score * 0.10
    , 2);

    RETURN v_total_score;
END//


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
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:erp_system.sp_find_best_supplier
CREATE PROCEDURE sp_find_best_supplier(
    IN p_product_id BIGINT UNSIGNED,
    IN p_warehouse_id BIGINT UNSIGNED,
    IN p_quantity INT
)
BEGIN
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
        ROUND((1 - sp.supplier_price / NULLIF((SELECT AVG(supplier_price) FROM supplier_products WHERE product_id = p_product_id), 0)) * 100, 2) AS price_score,
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
END//


-- ============================================================
-- 73. 供应商对比分析 - 同产品多供应商PK
-- 参数: p_product_id
-- 输出: 该产品所有供应商的价格/距离/退货率/质量/交期/评分对比
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:erp_system.sp_compare_suppliers_for_product
CREATE PROCEDURE sp_compare_suppliers_for_product(
    IN p_product_id BIGINT UNSIGNED
)
BEGIN
    DECLARE v_avg_price DECIMAL(12,2);
    DECLARE v_lowest_price DECIMAL(12,2);

    SELECT AVG(supplier_price), MIN(supplier_price)
    INTO v_avg_price, v_lowest_price
    FROM supplier_products WHERE product_id = p_product_id;

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
END//


-- ============================================================
-- 74. 供应商地理分布分析 - 门店辐射范围
-- 参数: p_warehouse_id
-- 输出: 该门店周边供应商分布，按距离排序
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:erp_system.sp_supplier_geographic_analysis
CREATE PROCEDURE sp_supplier_geographic_analysis(
    IN p_warehouse_id BIGINT UNSIGNED
)
BEGIN
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
END//


-- ============================================================
-- 75. 批量更新供应商综合指标
-- 调用方: 定期任务，根据历史数据更新供应商评分
-- 更新内容: return_rate(基于采购退货), quality_score(基于质检),
--           total_order_count, total_order_qty, last_order_date
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:erp_system.sp_update_supplier_metrics
CREATE PROCEDURE sp_update_supplier_metrics()
BEGIN
    -- 更新退货率: 基于近12个月采购退货
    UPDATE supplier_products sp
    SET return_rate = COALESCE((
        SELECT SUM(pri.return_qty) * 1.0 / NULLIF(SUM(pri.return_qty) + (
            SELECT SUM(poi.received_qty)
            FROM purchase_order_items poi
            JOIN purchase_orders po ON poi.order_id = po.id
            WHERE poi.product_id = sp.product_id AND po.supplier_id = sp.supplier_id
              AND po.order_date >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH)
        ), 0)
        FROM purchase_returns pr
        JOIN purchase_return_items pri ON pr.id = pri.return_id
        WHERE pr.supplier_id = sp.supplier_id
          AND pri.product_id = sp.product_id
          AND pr.return_date >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH)
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
          AND ir.inspection_date >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH)
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

    SELECT CONCAT('供应商指标更新完成: ', ROW_COUNT(), '条') AS result;
END
-- relation-detector-fixture-end
