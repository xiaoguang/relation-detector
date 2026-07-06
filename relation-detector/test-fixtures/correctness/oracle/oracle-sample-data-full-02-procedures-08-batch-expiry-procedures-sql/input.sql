-- ============================================================
-- 第五批: 批号保质期追踪 + 类别销售/临期分析
-- 覆盖角色: 店长(看本店), 员工(看自己负责的), 高管(看全局)
-- Oracle 26ai 翻译
-- ============================================================

CREATE SCHEMA  erp_system;
ALTER SESSION SET CURRENT_SCHEMA = erp_system;

-- ============================================================
-- 55. 批号保质期全景查询 (支持角色视角)
-- 参数: p_role CLOB('store_manager','employee','senior_mgmt','all')
--       p_user_id 当前用户employee_id
--       p_warehouse_id 门店ID(店长/员工限定)
--       p_expiry_days 临期天数阈值(默认30天)
-- 输出: 完整的批号-货品-门店-类别-保质期信息
-- 统计原理:
--   临期等级: 已过期>30天, 临期<=30天, 临期<=60天, 临期<=90天, 正常
--   库存金额 = 当前库存 * 进货价
--   临期损失预估 = 临期库存 * 进货价 * 折价系数
-- 调用方式: SELECT * FROM sp_batch_expiry_tracking('senior_mgmt', 1, 1, 30);
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_batch_expiry_tracking(
p_role CLOB,
    p_user_id NUMBER,
    p_warehouse_id NUMBER,
    p_expiry_days NUMBER,
    p_result OUT SYS_REFCURSOR
)
AS
BEGIN
    -- 根据角色确定数据范围
    -- store_manager: 只能看自己管理的仓库
    -- employee: 只能看自己所在仓库
    -- senior_mgmt/all: 看全部

    IF p_role = 'store_manager' THEN
        -- 验证该用户是否是该仓库的管理者
         (
            SELECT 1 FROM warehouses WHERE id = p_warehouse_id AND manager_id = p_user_id
        ) AND NOT EXISTS (
            SELECT 1 FROM departments WHERE manager_id = p_user_id
        ) THEN
            RAISE_APPLICATION_ERROR(-20000, '您不是该门店的管理者，无权查看');
        END IF;
    END IF;

    OPEN p_result FOR
    SELECT
        w.name AS store_name,
        w.code AS store_code,
        pc.name AS category_name,
        p.sku,
        p.name AS product_name,
        p.shelf_life_days(10),
        pb.batch_no,
        pb.production_date,
        pb.expiry_date,
        pb.current_qty AS batch_stock_qty,
        pb.purchase_price,
        ROUND(pb.current_qty * pb.purchase_price, 2) AS batch_stock_value,
        i.shelf_location,
        -- 保质期状态
        (pb.expiry_date - CURRENT_DATE)(10) AS days_to_expiry,
        CASE
            WHEN pb.expiry_date < CURRENT_DATE THEN '已过期'
            WHEN pb.expiry_date <= CURRENT_DATE + INTERVAL '7' DAY THEN '7天内过期'
            WHEN pb.expiry_date <= CURRENT_DATE + INTERVAL '30' DAY THEN '30天内过期'
            WHEN pb.expiry_date <= CURRENT_DATE + INTERVAL '60' DAY THEN '60天内过期'
            WHEN pb.expiry_date <= CURRENT_DATE + INTERVAL '90' DAY THEN '90天内过期'
            ELSE '正常'
        END AS expiry_status,
        -- 紧急程度
        CASE
            WHEN pb.expiry_date < CURRENT_DATE THEN 1
            WHEN pb.expiry_date <= CURRENT_DATE + INTERVAL '7' DAY THEN 2
            WHEN pb.expiry_date <= CURRENT_DATE + INTERVAL '30' DAY THEN 3
            WHEN pb.expiry_date <= CURRENT_DATE + INTERVAL '60' DAY THEN 4
            WHEN pb.expiry_date <= CURRENT_DATE + INTERVAL '90' DAY THEN 5
            ELSE 6
        END(10) AS urgency_level,
        -- 已过期天数
        CASE WHEN pb.expiry_date < CURRENT_DATE
            THEN (CURRENT_DATE - pb.expiry_date)(10)
            ELSE 0
        END AS days_expired,
        -- 保质期使用率
        CASE WHEN p.shelf_life_days > 0
            THEN ROUND((CURRENT_DATE - pb.production_date) * 100.0 / p.shelf_life_days, 1)
            ELSE 0
        END AS shelf_life_used_pct,
        -- 预估折价损失
        CASE
            WHEN pb.expiry_date < CURRENT_DATE THEN ROUND(pb.current_qty * pb.purchase_price * 0.8, 2)
            WHEN pb.expiry_date <= CURRENT_DATE + INTERVAL '30' DAY THEN ROUND(pb.current_qty * pb.purchase_price * 0.3, 2)
            WHEN pb.expiry_date <= CURRENT_DATE + INTERVAL '60' DAY THEN ROUND(pb.current_qty * pb.purchase_price * 0.1, 2)
            ELSE 0
        END AS estimated_loss,
        -- 该产品近30天日均销量
        (SELECT COALESCE(ROUND(SUM(soi.quantity) / 30.0, 1), 0)
         FROM sales_order_items soi
         JOIN sales_orders so ON soi.order_id = so.id
         WHERE soi.product_id = p.id
           AND so.order_date >= CURRENT_DATE - INTERVAL '30' DAY
           AND so.status NOT IN ('draft', 'cancelled')
        ) AS avg_daily_sales_30d,
        -- 按当前销售速度的可售天数
        CASE
            WHEN (SELECT COALESCE(ROUND(SUM(soi.quantity) / 30.0, 1), 0)
                  FROM sales_order_items soi
                  JOIN sales_orders so ON soi.order_id = so.id
                  WHERE soi.product_id = p.id
                    AND so.order_date >= CURRENT_DATE - INTERVAL '30' DAY
                    AND so.status NOT IN ('draft', 'cancelled')) > 0
            THEN ROUND(pb.current_qty / (SELECT COALESCE(ROUND(SUM(soi.quantity) / 30.0, 1), 0)
                  FROM sales_order_items soi
                  JOIN sales_orders so ON soi.order_id = so.id
                  WHERE soi.product_id = p.id
                    AND so.order_date >= CURRENT_DATE - INTERVAL '30' DAY
                    AND so.status NOT IN ('draft', 'cancelled')), 0)
            ELSE 999
        END AS estimated_sellable_days,
        -- 是否会过期卖不完
        CASE
            WHEN pb.expiry_date < CURRENT_DATE THEN '已过期-需立即处理'
            WHEN pb.expiry_date <= CURRENT_DATE + INTERVAL '30' DAY
                AND pb.current_qty > (SELECT COALESCE(SUM(soi.quantity), 0) * 2
                    FROM sales_order_items soi
                    JOIN sales_orders so ON soi.order_id = so.id
                    WHERE soi.product_id = p.id
                      AND so.order_date >= CURRENT_DATE - INTERVAL '30' DAY
                      AND so.status NOT IN ('draft', 'cancelled'))
            THEN '临期且库存过高-需促销处理'
            WHEN pb.expiry_date <= CURRENT_DATE + INTERVAL '30' DAY THEN '临期但可售完'
            ELSE '正常'
        END AS action_required,
        -- 供应商信息
        (SELECT s.name FROM suppliers s
         JOIN product_batches pb2 ON s.id = pb2.supplier_id
         WHERE pb2.id = pb.id FETCH FIRST 1 ROWS ONLY
        ) AS supplier_name
    FROM product_batches pb
    JOIN products p ON pb.product_id = p.id
    JOIN product_categories pc ON p.category_id = pc.id
    JOIN inventory i ON pb.id = i.batch_id AND p.id = i.product_id
    JOIN warehouses w ON i.warehouse_id = w.id
    WHERE pb.status = 'active'
      AND pb.current_qty > 0
      AND (p_role IN ('senior_mgmt', 'all') OR w.id = p_warehouse_id)
      AND (pb.expiry_date <= CURRENT_DATE + NUMTODSINTERVAL(p_expiry_days, 'DAY') OR pb.expiry_date < CURRENT_DATE)
    ORDER BY urgency_level ASC, pb.expiry_date ASC;
END;
/


-- ============================================================
-- 56. 类别销售与临期对比分析 (高管视角)
-- 参数: p_expiry_days 临期天数
-- 输出: 每个类别的销售表现 vs 临期风险对比
-- 统计原理:
--   销售贡献 = 类别销售额/总销售额
--   临期风险 = 类别临期库存金额/总临期库存金额
--   健康度 = 销售贡献 - 临期风险 (正值=健康, 负值=风险)
--   动销比 = 月销量/当前库存
-- 调用方式: SELECT * FROM sp_category_sales_vs_expiry(30);
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_category_sales_vs_expiry(
p_expiry_days NUMBER,
    p_result OUT SYS_REFCURSOR
)
AS
BEGIN
    OPEN p_result FOR
    WITH category_sales AS (
        -- 各类别近3个月销售
        SELECT
            pc.id AS category_id,
            pc.name AS category_name,
            COALESCE(SUM(soi.amount), 0) AS total_sales_3m,
            COALESCE(SUM(soi.quantity), 0) AS total_qty_sold_3m,
            COUNT(DISTINCT soi.product_id) AS active_products,
            COUNT(DISTINCT so.customer_id) AS customer_count,
            ROUND(COALESCE(SUM(soi.amount), 0) / 3.0, 2) AS avg_monthly_sales
        FROM product_categories pc
        LEFT JOIN products p ON pc.id = p.category_id
        LEFT JOIN sales_order_items soi ON p.id = soi.product_id
        LEFT JOIN sales_orders so ON soi.order_id = so.id
            AND so.order_date >= CURRENT_DATE - INTERVAL '3' MONTH
            AND so.status NOT IN ('draft', 'cancelled')
        GROUP BY pc.id, pc.name
    ),
    category_inventory AS (
        -- 各类别当前库存(含临期)
        SELECT
            pc.id AS category_id,
            SUM(i.quantity) AS total_stock_qty,
            SUM(i.quantity * p.purchase_price) AS total_stock_value,
            -- 临期库存
            SUM(CASE WHEN pb.expiry_date <= CURRENT_DATE + NUMTODSINTERVAL(p_expiry_days, 'DAY') THEN i.quantity ELSE 0 END) AS near_expiry_qty,
            SUM(CASE WHEN pb.expiry_date <= CURRENT_DATE + NUMTODSINTERVAL(p_expiry_days, 'DAY') THEN i.quantity * p.purchase_price ELSE 0 END) AS near_expiry_value,
            -- 已过期
            SUM(CASE WHEN pb.expiry_date < CURRENT_DATE THEN i.quantity ELSE 0 END) AS expired_qty,
            SUM(CASE WHEN pb.expiry_date < CURRENT_DATE THEN i.quantity * p.purchase_price ELSE 0 END) AS expired_value,
            -- 批号数量
            COUNT(DISTINCT CASE WHEN pb.expiry_date <= CURRENT_DATE + NUMTODSINTERVAL(p_expiry_days, 'DAY') THEN pb.id END) AS near_expiry_batch_count,
            -- 最早过期日期
            MIN(pb.expiry_date) AS earliest_expiry
        FROM product_categories pc
        LEFT JOIN products p ON pc.id = p.category_id
        LEFT JOIN inventory i ON p.id = i.product_id
        LEFT JOIN product_batches pb ON i.batch_id = pb.id
        GROUP BY pc.id
    ),
    category_bestseller AS (
        -- 各类别最畅销产品
        SELECT
            p.category_id,
            p.sku AS bestseller_sku,
            p.name AS bestseller_name,
            ROW_NUMBER() OVER (PARTITION BY p.category_id ORDER BY SUM(soi.amount) DESC) AS rn
        FROM products p
        JOIN sales_order_items soi ON p.id = soi.product_id
        JOIN sales_orders so ON soi.order_id = so.id
        WHERE so.order_date >= CURRENT_DATE - INTERVAL '3' MONTH
          AND so.status NOT IN ('draft', 'cancelled')
        GROUP BY p.category_id, p.sku, p.name
    ),
    category_worst_expiry AS (
        -- 各类别临期最严重的产品
        SELECT
            p.category_id,
            p.sku AS worst_sku,
            p.name AS worst_name,
            SUM(i.quantity * p.purchase_price) AS worst_expiry_value,
            ROW_NUMBER() OVER (PARTITION BY p.category_id ORDER BY SUM(i.quantity * p.purchase_price) DESC) AS rn
        FROM products p
        JOIN inventory i ON p.id = i.product_id
        JOIN product_batches pb ON i.batch_id = pb.id
        WHERE pb.expiry_date <= CURRENT_DATE + NUMTODSINTERVAL(p_expiry_days, 'DAY')
        GROUP BY p.category_id, p.sku, p.name
    )
    SELECT
        cs.category_name,
        cs.active_products,
        cs.customer_count,
        cs.total_sales_3m,
        cs.total_qty_sold_3m,
        cs.avg_monthly_sales,
        ci.total_stock_qty,
        ci.total_stock_value,
        ci.near_expiry_qty,
        ci.near_expiry_value,
        ci.expired_qty,
        ci.expired_value,
        ci.near_expiry_batch_count,
        ci.earliest_expiry,
        -- 销售贡献占比
        ROUND(cs.total_sales_3m * 100.0 / NULLIF(SUM(cs.total_sales_3m) OVER (), 0), 2) AS sales_contribution_pct,
        -- 临期风险占比
        ROUND(ci.near_expiry_value * 100.0 / NULLIF(SUM(ci.near_expiry_value) OVER (), 0), 2) AS expiry_risk_pct,
        -- 临期率
        ROUND(ci.near_expiry_qty * 100.0 / NULLIF(ci.total_stock_qty, 0), 2) AS near_expiry_rate_pct,
        -- 过期率
        ROUND(ci.expired_qty * 100.0 / NULLIF(ci.total_stock_qty, 0), 2) AS expired_rate_pct,
        -- 动销比 (月销量/当前库存)
        ROUND(cs.avg_monthly_sales / NULLIF(ci.total_stock_value, 0), 2) AS turnover_ratio,
        -- 库存可售月数
        ROUND(ci.total_stock_qty / NULLIF(cs.total_qty_sold_3m / 3.0, 0), 1) AS months_of_stock,
        -- 健康度评分 (销售贡献 - 临期风险)
        ROUND(
            cs.total_sales_3m * 100.0 / NULLIF(SUM(cs.total_sales_3m) OVER (), 0)
            - ci.near_expiry_value * 100.0 / NULLIF(SUM(ci.near_expiry_value) OVER (), 0)
        , 2) AS health_score,
        -- 健康度标签
        CASE
            WHEN ROUND(
                cs.total_sales_3m * 100.0 / NULLIF(SUM(cs.total_sales_3m) OVER (), 0)
                - ci.near_expiry_value * 100.0 / NULLIF(SUM(ci.near_expiry_value) OVER (), 0)
            , 2) > 10 THEN '优秀-销售好库存健康'
            WHEN ROUND(
                cs.total_sales_3m * 100.0 / NULLIF(SUM(cs.total_sales_3m) OVER (), 0)
                - ci.near_expiry_value * 100.0 / NULLIF(SUM(ci.near_expiry_value) OVER (), 0)
            , 2) > 0 THEN '良好-销售覆盖临期风险'
            WHEN ROUND(
                cs.total_sales_3m * 100.0 / NULLIF(SUM(cs.total_sales_3m) OVER (), 0)
                - ci.near_expiry_value * 100.0 / NULLIF(SUM(ci.near_expiry_value) OVER (), 0)
            , 2) > -5 THEN '关注-临期风险略高'
            WHEN ROUND(
                cs.total_sales_3m * 100.0 / NULLIF(SUM(cs.total_sales_3m) OVER (), 0)
                - ci.near_expiry_value * 100.0 / NULLIF(SUM(ci.near_expiry_value) OVER (), 0)
            , 2) > -10 THEN '警告-临期风险较高'
            ELSE '严重-临期库存远超销售能力'
        END AS health_status,
        -- 畅销品
        (SELECT bestseller_sku FROM category_bestseller cb WHERE cb.category_id = cs.category_id AND cb.rn = 1) AS top_seller_sku,
        (SELECT bestseller_name FROM category_bestseller cb WHERE cb.category_id = cs.category_id AND cb.rn = 1) AS top_seller_name,
        -- 临期最严重产品
        (SELECT worst_sku FROM category_worst_expiry cw WHERE cw.category_id = cs.category_id AND cw.rn = 1) AS worst_expiry_sku,
        (SELECT worst_name FROM category_worst_expiry cw WHERE cw.category_id = cs.category_id AND cw.rn = 1) AS worst_expiry_name,
        (SELECT ROUND(worst_expiry_value, 2) FROM category_worst_expiry cw WHERE cw.category_id = cs.category_id AND cw.rn = 1) AS worst_expiry_value
    FROM category_sales cs
    JOIN category_inventory ci ON cs.category_id = ci.category_id
    ORDER BY health_score ASC;
END;
/


-- ============================================================
-- 57. 门店批号临期仪表盘 (店长视角)
-- 参数: p_warehouse_id 门店ID
-- 输出: 该门店所有临期批号汇总 + 分类汇总 + 处理建议
-- 调用方式: SELECT * FROM sp_store_expiry_dashboard(1);
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_store_expiry_dashboard(
p_warehouse_id NUMBER,
    p_result OUT SYS_REFCURSOR
)
AS
BEGIN
    OPEN p_result FOR
    -- 汇总部分
    SELECT
        'SUMMARY' AS section,
        COUNT(DISTINCT pb.id) AS total_batch_count,
        COUNT(DISTINCT pb.product_id) AS total_product_count,
        SUM(pb.current_qty) AS total_qty,
        ROUND(SUM(pb.current_qty * pb.purchase_price), 2) AS total_value,
        SUM(CASE WHEN pb.expiry_date < CURRENT_DATE THEN pb.current_qty ELSE 0 END) AS expired_qty,
        ROUND(SUM(CASE WHEN pb.expiry_date < CURRENT_DATE THEN pb.current_qty * pb.purchase_price ELSE 0 END), 2) AS expired_value,
        SUM(CASE WHEN pb.expiry_date BETWEEN CURRENT_DATE AND CURRENT_DATE + INTERVAL '7' DAY THEN pb.current_qty ELSE 0 END) AS expiry_7d_qty,
        SUM(CASE WHEN pb.expiry_date BETWEEN CURRENT_DATE AND CURRENT_DATE + INTERVAL '30' DAY THEN pb.current_qty ELSE 0 END) AS expiry_30d_qty,
        SUM(CASE WHEN pb.expiry_date BETWEEN CURRENT_DATE AND CURRENT_DATE + INTERVAL '60' DAY THEN pb.current_qty ELSE 0 END) AS expiry_60d_qty,
        MIN(pb.expiry_date) AS earliest_expiry,
        (SELECT pc.name FROM product_categories pc
         JOIN products p ON pc.id = p.category_id
         JOIN product_batches pb2 ON p.id = pb2.product_id
         JOIN inventory i ON pb2.id = i.batch_id
         WHERE i.warehouse_id = p_warehouse_id AND pb2.status = 'active' AND pb2.current_qty > 0
         GROUP BY pc.id, pc.name
         ORDER BY SUM(pb2.current_qty * pb2.purchase_price) DESC FETCH FIRST 1 ROWS ONLY
        ) AS most_at_risk_category
    FROM product_batches pb
    JOIN inventory i ON pb.id = i.batch_id
    WHERE i.warehouse_id = p_warehouse_id
      AND pb.status = 'active'
      AND pb.current_qty > 0
      AND pb.expiry_date <= CURRENT_DATE + INTERVAL '60' DAY

    UNION ALL

    -- 按分类汇总
    SELECT
        ('CATEGORY: ' || pc.name) AS section,
        COUNT(DISTINCT pb.id),
        COUNT(DISTINCT pb.product_id),
        SUM(pb.current_qty),
        ROUND(SUM(pb.current_qty * pb.purchase_price), 2),
        SUM(CASE WHEN pb.expiry_date < CURRENT_DATE THEN pb.current_qty ELSE 0 END),
        ROUND(SUM(CASE WHEN pb.expiry_date < CURRENT_DATE THEN pb.current_qty * pb.purchase_price ELSE 0 END), 2),
        SUM(CASE WHEN pb.expiry_date BETWEEN CURRENT_DATE AND CURRENT_DATE + INTERVAL '7' DAY THEN pb.current_qty ELSE 0 END),
        SUM(CASE WHEN pb.expiry_date BETWEEN CURRENT_DATE AND CURRENT_DATE + INTERVAL '30' DAY THEN pb.current_qty ELSE 0 END),
        SUM(CASE WHEN pb.expiry_date BETWEEN CURRENT_DATE AND CURRENT_DATE + INTERVAL '60' DAY THEN pb.current_qty ELSE 0 END),
        MIN(pb.expiry_date),
        NULL
    FROM product_batches pb
    JOIN products p ON pb.product_id = p.id
    JOIN product_categories pc ON p.category_id = pc.id
    JOIN inventory i ON pb.id = i.batch_id
    WHERE i.warehouse_id = p_warehouse_id
      AND pb.status = 'active'
      AND pb.current_qty > 0
      AND pb.expiry_date <= CURRENT_DATE + INTERVAL '60' DAY
    GROUP BY pc.name

    UNION ALL

    -- 具体处理建议
    SELECT
        ('ACTION: ' || p.sku || ' ' || p.name || ' 批号:' || pb.batch_no || ' 数量:' || pb.current_qty || ' 到期:' || pb.expiry_date ||
            CASE
                WHEN pb.expiry_date < CURRENT_DATE THEN ' 【立即报废处理】'
                WHEN pb.expiry_date <= CURRENT_DATE + INTERVAL '7' DAY THEN ' 【紧急促销/退货】'
                WHEN pb.expiry_date <= CURRENT_DATE + INTERVAL '30' DAY THEN ' 【打折促销】'
                WHEN pb.expiry_date <= CURRENT_DATE + INTERVAL '60' DAY THEN ' 【优先出库】'
                ELSE ''
            END
        ) AS section,
        NULL(19), NULL(19), NULL, NULL,
        NULL, NULL, NULL, NULL, NULL,
        NULL, NULL
    FROM product_batches pb
    JOIN products p ON pb.product_id = p.id
    JOIN inventory i ON pb.id = i.batch_id
    WHERE i.warehouse_id = p_warehouse_id
      AND pb.status = 'active'
      AND pb.current_qty > 0
      AND pb.expiry_date <= CURRENT_DATE + INTERVAL '60' DAY
    ORDER BY pb.expiry_date ASC
    FETCH FIRST 20 ROWS ONLY;
END;
/


-- ============================================================
-- 58. 全品类临期热力图 (高管视角)
-- 参数: 无
-- 输出: 门店x品类矩阵，展示每个门店每个品类的临期严重程度
-- 统计原理: 临期严重度 = 临期库存金额/总库存金额
--           红色=严重(>30%), 橙色=关注(>15%), 黄色=轻微(>5%), 绿色=正常
-- 调用方式: SELECT * FROM sp_expiry_heatmap();
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_expiry_heatmap(
p_result OUT SYS_REFCURSOR
)
AS
BEGIN
    OPEN p_result FOR
    SELECT
        w.name AS store_name,
        pc.name AS category_name,
        SUM(i.quantity) AS total_stock,
        SUM(CASE WHEN pb.expiry_date <= CURRENT_DATE + INTERVAL '60' DAY THEN i.quantity ELSE 0 END) AS near_expiry_stock,
        ROUND(SUM(i.quantity * p.purchase_price), 2) AS total_stock_value,
        ROUND(SUM(CASE WHEN pb.expiry_date <= CURRENT_DATE + INTERVAL '60' DAY THEN i.quantity * p.purchase_price ELSE 0 END), 2) AS near_expiry_value,
        ROUND(
            SUM(CASE WHEN pb.expiry_date <= CURRENT_DATE + INTERVAL '60' DAY THEN i.quantity ELSE 0 END) * 100.0
            / NULLIF(SUM(i.quantity), 0), 2
        ) AS near_expiry_rate_pct,
        -- 最近3个月该门店该品类销售额
        (SELECT COALESCE(SUM(soi.amount), 0)
         FROM sales_order_items soi
         JOIN sales_orders so ON soi.order_id = so.id
         JOIN products pp ON soi.product_id = pp.id
         WHERE so.warehouse_id = w.id
           AND pp.category_id = pc.id
           AND so.order_date >= CURRENT_DATE - INTERVAL '3' MONTH
           AND so.status NOT IN ('draft', 'cancelled')
        ) AS sales_3m,
        -- 临期严重程度标签
        CASE
            WHEN SUM(CASE WHEN pb.expiry_date <= CURRENT_DATE + INTERVAL '60' DAY THEN i.quantity ELSE 0 END) * 100.0
                / NULLIF(SUM(i.quantity), 0) > 30 THEN '严重'
            WHEN SUM(CASE WHEN pb.expiry_date <= CURRENT_DATE + INTERVAL '60' DAY THEN i.quantity ELSE 0 END) * 100.0
                / NULLIF(SUM(i.quantity), 0) > 15 THEN '关注'
            WHEN SUM(CASE WHEN pb.expiry_date <= CURRENT_DATE + INTERVAL '60' DAY THEN i.quantity ELSE 0 END) * 100.0
                / NULLIF(SUM(i.quantity), 0) > 5 THEN '轻微'
            ELSE '正常'
        END AS severity_level,
        -- 最早过期日期
        MIN(pb.expiry_date) AS earliest_expiry
    FROM warehouses w
    CROSS JOIN product_categories pc
    LEFT JOIN products p ON pc.id = p.category_id
    LEFT JOIN inventory i ON p.id = i.product_id AND i.warehouse_id = w.id
    LEFT JOIN product_batches pb ON i.batch_id = pb.id AND pb.status = 'active' AND pb.current_qty > 0
    WHERE pc.parent_id IS NOT NULL  -- 只取子分类
    GROUP BY w.id, w.name, pc.id, pc.name
    HAVING SUM(i.quantity) > 0
    ORDER BY near_expiry_rate_pct DESC;
END;
/


-- ============================================================
-- 59. 单个货品批号明细查询 (员工视角)
-- 参数: p_product_id or p_sku
-- 输出: 该货品所有批号在不同门店的库存和保质期
-- 调用方式: SELECT * FROM sp_product_batch_detail(NULL, 'SKU-001');
--            SELECT * FROM sp_product_batch_detail(123, NULL);
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_product_batch_detail(
p_product_id NUMBER,
    p_sku VARCHAR2,
    p_result OUT SYS_REFCURSOR
)
AS
v_product_id NUMBER(19);
BEGIN
    IF p_product_id IS NULL AND p_sku IS NOT NULL THEN
        SELECT id INTO v_product_id FROM products WHERE sku = p_sku;
    ELSE
        v_product_id := p_product_id;
    END IF;

    OPEN p_result FOR
    SELECT
        p.sku,
        p.name AS product_name,
        pc.name AS category_name,
        p.shelf_life_days(10),
        p.purchase_price,
        p.retail_price,
        pb.batch_no,
        pb.production_date,
        pb.expiry_date,
        (pb.expiry_date - CURRENT_DATE)(10) AS days_to_expiry,
        pb.current_qty AS batch_qty,
        pb.initial_qty,
        ROUND(pb.current_qty * 100.0 / NULLIF(pb.initial_qty, 0), 2) AS remaining_pct,
        w.name AS store_name,
        w.code AS store_code,
        i.shelf_location,
        i.quantity AS store_qty,
        i.locked_quantity AS locked_qty,
        i.available_quantity AS available_qty,
        pb.status AS batch_status,
        CASE
            WHEN pb.expiry_date < CURRENT_DATE THEN '已过期-立即下架'
            WHEN pb.expiry_date <= CURRENT_DATE + INTERVAL '7' DAY THEN '紧急-7天内过期'
            WHEN pb.expiry_date <= CURRENT_DATE + INTERVAL '30' DAY THEN '关注-30天内过期'
            WHEN pb.expiry_date <= CURRENT_DATE + INTERVAL '60' DAY THEN '提示-60天内过期'
            ELSE '正常'
        END AS expiry_alert,
        -- 该批号在该门店的销售速度
        (SELECT COALESCE(ROUND(SUM(soi.quantity) / 30.0, 1), 0)
         FROM sales_order_items soi
         JOIN sales_orders so ON soi.order_id = so.id
         WHERE soi.product_id = p.id
           AND soi.batch_id = pb.id
           AND so.warehouse_id = w.id
           AND so.order_date >= CURRENT_DATE - INTERVAL '30' DAY
           AND so.status NOT IN ('draft', 'cancelled')
        ) AS avg_daily_sales_in_store,
        -- 供应商
        (SELECT s.name FROM suppliers s WHERE s.id = pb.supplier_id) AS supplier_name
    FROM products p
    JOIN product_categories pc ON p.category_id = pc.id
    JOIN product_batches pb ON p.id = pb.product_id
    LEFT JOIN inventory i ON pb.id = i.batch_id
    LEFT JOIN warehouses w ON i.warehouse_id = w.id
    WHERE p.id = v_product_id
      AND pb.current_qty > 0
    ORDER BY pb.expiry_date ASC, w.name;
END;
/


-- ============================================================
-- 60. 每日临期预警推送 (定时任务调用)
-- 输出: 需要立即处理的临期/过期货品清单
-- 调用方式: SELECT * FROM sp_daily_expiry_alert();
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_daily_expiry_alert(
p_result OUT SYS_REFCURSOR
)
AS
BEGIN
    OPEN p_result FOR
    -- 已过期未处理
    SELECT
        '已过期未处理' AS alert_type,
        w.name AS store_name,
        p.sku,
        p.name AS product_name,
        pb.batch_no,
        pb.expiry_date,
        (CURRENT_DATE - pb.expiry_date)(10) AS days_metric,
        pb.current_qty AS stock_qty,
        ROUND(pb.current_qty * pb.purchase_price, 2) AS value_or_loss,
        ('请立即下架报废处理，损失预估: ' || ROUND(pb.current_qty * pb.purchase_price, 2) || '元') AS suggested_action
    FROM product_batches pb
    JOIN products p ON pb.product_id = p.id
    JOIN inventory i ON pb.id = i.batch_id
    JOIN warehouses w ON i.warehouse_id = w.id
    WHERE pb.status = 'active'
      AND pb.current_qty > 0
      AND pb.expiry_date < CURRENT_DATE

    UNION ALL

    -- 7天内过期
    SELECT
        '7天内过期-紧急' AS alert_type,
        w.name,
        p.sku,
        p.name,
        pb.batch_no,
        pb.expiry_date,
        (pb.expiry_date - CURRENT_DATE)(10) AS days_metric,
        pb.current_qty AS stock_qty,
        ROUND(pb.current_qty * pb.purchase_price * 0.3, 2) AS value_or_loss,
        ('建议立即打折促销(建议7折)，或联系供应商退货。预估损失: ' ||
            ROUND(pb.current_qty * pb.purchase_price * 0.3, 2) || '元') AS suggested_action
    FROM product_batches pb
    JOIN products p ON pb.product_id = p.id
    JOIN inventory i ON pb.id = i.batch_id
    JOIN warehouses w ON i.warehouse_id = w.id
    WHERE pb.status = 'active'
      AND pb.current_qty > 0
      AND pb.expiry_date BETWEEN CURRENT_DATE AND CURRENT_DATE + INTERVAL '7' DAY

    UNION ALL

    -- 30天内过期且库存过高
    SELECT
        '30天内过期+库存过高' AS alert_type,
        w.name,
        p.sku,
        p.name,
        pb.batch_no,
        pb.expiry_date,
        (pb.expiry_date - CURRENT_DATE)(10) AS days_metric,
        pb.current_qty AS stock_qty,
        ROUND(pb.current_qty * pb.purchase_price * 0.1, 2) AS value_or_loss,
        ('库存量(' || pb.current_qty || ')超过30天预计销量，建议优先出库或促销') AS suggested_action
    FROM product_batches pb
    JOIN products p ON pb.product_id = p.id
    JOIN inventory i ON pb.id = i.batch_id
    JOIN warehouses w ON i.warehouse_id = w.id
    WHERE pb.status = 'active'
      AND pb.current_qty > 0
      AND pb.expiry_date BETWEEN CURRENT_DATE AND CURRENT_DATE + INTERVAL '30' DAY
      AND pb.current_qty > (
        SELECT COALESCE(SUM(soi.quantity), 0)
        FROM sales_order_items soi
        JOIN sales_orders so ON soi.order_id = so.id
        WHERE soi.product_id = p.id
          AND so.order_date >= CURRENT_DATE - INTERVAL '30' DAY
          AND so.status NOT IN ('draft', 'cancelled')
      ) * 2

    ORDER BY alert_type, days_metric DESC, value_or_loss DESC
    FETCH FIRST 50 ROWS ONLY;
END;
/