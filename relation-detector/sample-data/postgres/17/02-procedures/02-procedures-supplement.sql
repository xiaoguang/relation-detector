-- ============================================================
-- ERP系统补充存储过程（PostgreSQL 17 版本）
-- 库存调拨、盘点、批号管理、客户信用、供应商评估、
-- 预算控制、损益计算、销售分析、权限管理
-- ============================================================

-- ============================================================
-- 16. 库存调拨
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_transfer_inventory(
    IN p_product_id BIGINT,
    IN p_batch_id BIGINT,
    IN p_from_warehouse_id BIGINT,
    IN p_to_warehouse_id BIGINT,
    IN p_quantity INT,
    IN p_operator_id BIGINT,
    IN p_remark VARCHAR(500)
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_available INT;
    v_from_before INT;
    v_to_before INT;
BEGIN
    SELECT COALESCE(available_quantity, 0) INTO v_available
    FROM inventory
    WHERE product_id = p_product_id
      AND (batch_id = p_batch_id OR (p_batch_id IS NULL AND batch_id IS NULL))
      AND warehouse_id = p_from_warehouse_id;

    IF v_available < p_quantity THEN
        RAISE EXCEPTION '调出仓库库存不足: 可用=%, 调拨=%', v_available, p_quantity;
    END IF;

    -- 调出
    SELECT quantity INTO v_from_before
    FROM inventory
    WHERE product_id = p_product_id
      AND (batch_id = p_batch_id OR (p_batch_id IS NULL AND batch_id IS NULL))
      AND warehouse_id = p_from_warehouse_id;

    UPDATE inventory SET quantity = quantity - p_quantity
    WHERE product_id = p_product_id
      AND (batch_id = p_batch_id OR (p_batch_id IS NULL AND batch_id IS NULL))
      AND warehouse_id = p_from_warehouse_id;

    INSERT INTO inventory_transactions (product_id, batch_id, warehouse_id,
        transaction_type, quantity_change, before_qty, after_qty,
        reference_type, operator_id, remark)
    VALUES (p_product_id, p_batch_id, p_from_warehouse_id, 'transfer_out',
        -p_quantity, v_from_before, v_from_before - p_quantity,
        'transfer', p_operator_id, p_remark);

    -- 调入
    SELECT COALESCE(quantity, 0) INTO v_to_before
    FROM inventory
    WHERE product_id = p_product_id
      AND (batch_id = p_batch_id OR (p_batch_id IS NULL AND batch_id IS NULL))
      AND warehouse_id = p_to_warehouse_id;

    INSERT INTO inventory (product_id, batch_id, warehouse_id, quantity)
    VALUES (p_product_id, p_batch_id, p_to_warehouse_id, p_quantity)
    ON CONFLICT (product_id, batch_id, warehouse_id) DO UPDATE
    SET quantity = inventory.quantity + p_quantity;

    INSERT INTO inventory_transactions (product_id, batch_id, warehouse_id,
        transaction_type, quantity_change, before_qty, after_qty,
        reference_type, operator_id, remark)
    VALUES (p_product_id, p_batch_id, p_to_warehouse_id, 'transfer_in',
        p_quantity, v_to_before, v_to_before + p_quantity,
        'transfer', p_operator_id, p_remark);

    INSERT INTO audit_log (action, target_type, employee_id, new_value)
    VALUES ('transfer_inventory', 'inventory', p_operator_id,
            jsonb_build_object('product_id', p_product_id, 'quantity', p_quantity,
                               'from_wh', p_from_warehouse_id, 'to_wh', p_to_warehouse_id));

    COMMIT;

    -- 返回结果
    RAISE NOTICE '调拨成功';
END;
$$;


-- ============================================================
-- 17. 库存盘点
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_stocktake(
    IN p_warehouse_id BIGINT,
    IN p_product_id BIGINT,
    IN p_batch_id BIGINT,
    IN p_actual_qty INT,
    IN p_operator_id BIGINT,
    IN p_remark VARCHAR(500)
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_book_qty INT;
    v_diff INT;
BEGIN
    SELECT COALESCE(quantity, 0) INTO v_book_qty
    FROM inventory
    WHERE product_id = p_product_id
      AND (batch_id = p_batch_id OR (p_batch_id IS NULL AND batch_id IS NULL))
      AND warehouse_id = p_warehouse_id;

    v_diff := p_actual_qty - v_book_qty;

    IF v_diff = 0 THEN
        -- 仅更新盘点日期
        UPDATE inventory
        SET last_stocktake_date = CURRENT_DATE
        WHERE product_id = p_product_id
          AND (batch_id = p_batch_id OR (p_batch_id IS NULL AND batch_id IS NULL))
          AND warehouse_id = p_warehouse_id;

        RAISE NOTICE '盘点无差异';
    ELSE
        UPDATE inventory
        SET quantity = p_actual_qty,
            last_stocktake_date = CURRENT_DATE
        WHERE product_id = p_product_id
          AND (batch_id = p_batch_id OR (p_batch_id IS NULL AND batch_id IS NULL))
          AND warehouse_id = p_warehouse_id;

        IF p_batch_id IS NOT NULL THEN
            UPDATE product_batches
            SET current_qty = current_qty + v_diff
            WHERE id = p_batch_id;
        END IF;

        INSERT INTO inventory_transactions (product_id, batch_id, warehouse_id,
            transaction_type, quantity_change, before_qty, after_qty,
            reference_type, operator_id, remark)
        VALUES (p_product_id, p_batch_id, p_warehouse_id, 'stocktake_adjust',
            v_diff, v_book_qty, p_actual_qty, 'stocktake', p_operator_id,
            CONCAT('盘点调整: ', p_remark));

        INSERT INTO audit_log (action, target_type, employee_id, old_value, new_value)
        VALUES ('stocktake', 'inventory', p_operator_id,
                jsonb_build_object('quantity', v_book_qty),
                jsonb_build_object('quantity', p_actual_qty, 'diff', v_diff));

        COMMIT;

        RAISE NOTICE '盘点调整完成，差异: %', v_diff;
    END IF;
END;
$$;


-- ============================================================
-- 18. 批号过期处理
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_process_expired_batches(
    IN p_operator_id BIGINT
)
LANGUAGE plpgsql
AS $$
DECLARE
    rec RECORD;
    v_before_qty INT;
BEGIN
    FOR rec IN
        SELECT pb.id AS batch_id, pb.product_id, pb.current_qty, i.warehouse_id
        FROM product_batches pb
        JOIN inventory i ON pb.id = i.batch_id
        WHERE pb.expiry_date <= CURRENT_DATE
          AND pb.status = 'active'
          AND pb.current_qty > 0
    LOOP
        SELECT quantity INTO v_before_qty
        FROM inventory
        WHERE product_id = rec.product_id AND batch_id = rec.batch_id AND warehouse_id = rec.warehouse_id;

        -- 锁定并标记过期
        UPDATE product_batches SET status = 'expired' WHERE id = rec.batch_id;

        -- 过期库存出库（报废）
        UPDATE inventory SET quantity = 0
        WHERE product_id = rec.product_id AND batch_id = rec.batch_id AND warehouse_id = rec.warehouse_id;

        INSERT INTO inventory_transactions (product_id, batch_id, warehouse_id,
            transaction_type, quantity_change, before_qty, after_qty,
            reference_type, operator_id, remark)
        VALUES (rec.product_id, rec.batch_id, rec.warehouse_id, 'scrap_out',
            -rec.current_qty, v_before_qty, 0, 'batch_expiry', p_operator_id,
            CONCAT('批号过期报废: batch_id=', rec.batch_id));

    END LOOP;

    INSERT INTO audit_log (action, target_type, employee_id, new_value)
    VALUES ('process_expired_batches', 'batch', p_operator_id,
            jsonb_build_object('processed_date', CURRENT_DATE));

    COMMIT;

    RAISE NOTICE '过期批号处理完成';
END;
$$;


-- ============================================================
-- 19. 客户信用额度管理
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_check_customer_credit(
    IN p_customer_id BIGINT,
    IN p_order_amount DECIMAL(18,2)
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_credit_limit DECIMAL(18,2);
    v_balance DECIMAL(18,2);
    v_status VARCHAR(20);
    v_unpaid_total DECIMAL(18,2);
BEGIN
    SELECT credit_limit, balance, status
    INTO v_credit_limit, v_balance, v_status
    FROM customers WHERE id = p_customer_id;

    IF v_status = 'frozen' THEN
        RAISE EXCEPTION '客户已被冻结，无法交易';
    END IF;

    IF v_status = 'blacklist' THEN
        RAISE EXCEPTION '客户在黑名单中，无法交易';
    END IF;

    -- 计算未结清的销售金额
    SELECT COALESCE(SUM(so.total_amount - so.paid_amount), 0) INTO v_unpaid_total
    FROM sales_orders so
    WHERE so.customer_id = p_customer_id
      AND so.status IN ('confirmed', 'delivering', 'delivered');

    -- 返回结果集
    RETURN QUERY
    SELECT
        v_credit_limit AS credit_limit,
        v_balance AS current_balance,
        v_unpaid_total AS unpaid_total,
        v_credit_limit - v_balance - v_unpaid_total AS available_credit,
        CASE
            WHEN (v_balance + v_unpaid_total + p_order_amount) > v_credit_limit THEN 'OVER_LIMIT'::TEXT
            WHEN (v_balance + v_unpaid_total + p_order_amount) > v_credit_limit * 0.8 THEN 'WARNING'::TEXT
            ELSE 'OK'::TEXT
        END AS credit_status;
END;
$$;


-- ============================================================
-- 20. 供应商评估
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_evaluate_supplier(
    IN p_supplier_id BIGINT,
    IN p_start_date DATE,
    IN p_end_date DATE
)
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN QUERY
    SELECT
        s.id AS supplier_id,
        s.name AS supplier_name,
        s.credit_level,
        COUNT(DISTINCT po.id) AS total_orders,
        SUM(po.total_amount) AS total_purchase_amount,
        SUM(po.paid_amount) AS total_paid_amount,
        SUM(po.total_amount - po.paid_amount) AS total_unpaid,
        ROUND(AVG(po.total_amount), 2) AS avg_order_amount,
        AVG(CASE
            WHEN po.actual_delivery_date IS NOT NULL
            THEN (po.actual_delivery_date - po.expected_delivery_date)
            ELSE NULL
        END) AS avg_delivery_delay_days,
        COUNT(CASE
            WHEN po.actual_delivery_date > po.expected_delivery_date THEN 1
        END) AS delayed_orders,
        ROUND(
            COUNT(CASE WHEN po.actual_delivery_date > po.expected_delivery_date THEN 1 END) * 100.0
            / NULLIF(COUNT(DISTINCT po.id), 0), 2
        ) AS delay_rate_pct,
        SUM(COALESCE(pri.rejected_qty, 0)) AS total_rejected_qty,
        SUM(COALESCE(pri.accepted_qty, 0)) AS total_accepted_qty,
        ROUND(
            SUM(COALESCE(pri.rejected_qty, 0)) * 100.0
            / NULLIF(SUM(COALESCE(pri.received_qty, 0)), 0), 2
        ) AS reject_rate_pct,
        CASE
            WHEN AVG(CASE WHEN po.actual_delivery_date IS NOT NULL
                THEN (po.actual_delivery_date - po.expected_delivery_date) END) <= 0
                AND SUM(COALESCE(pri.rejected_qty, 0)) * 100.0 / NULLIF(SUM(COALESCE(pri.received_qty, 0)), 0) <= 2
            THEN 'A'
            WHEN AVG(CASE WHEN po.actual_delivery_date IS NOT NULL
                THEN (po.actual_delivery_date - po.expected_delivery_date) END) <= 3
            THEN 'B'
            WHEN AVG(CASE WHEN po.actual_delivery_date IS NOT NULL
                THEN (po.actual_delivery_date - po.expected_delivery_date) END) <= 7
            THEN 'C'
            ELSE 'D'
        END AS evaluated_level
    FROM suppliers s
    LEFT JOIN purchase_orders po ON s.id = po.supplier_id
        AND po.order_date BETWEEN p_start_date AND p_end_date
    LEFT JOIN purchase_order_items poi ON po.id = poi.order_id
    LEFT JOIN purchase_receipt_items pri ON poi.id = pri.order_item_id
    WHERE s.id = p_supplier_id
    GROUP BY s.id, s.name, s.credit_level;
END;
$$;


-- ============================================================
-- 21. 部门预算控制
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_check_department_budget(
    IN p_department_id BIGINT,
    IN p_amount DECIMAL(18,2)
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_budget DECIMAL(18,2);
    v_spent DECIMAL(18,2);
    v_remaining DECIMAL(18,2);
BEGIN
    SELECT budget INTO v_budget FROM departments WHERE id = p_department_id;

    -- 已使用预算 = 已审批请购 + 已下单采购
    SELECT COALESCE(SUM(pr.total_amount), 0) INTO v_spent
    FROM purchase_requisitions pr
    WHERE pr.department_id = p_department_id
      AND pr.status IN ('approved', 'ordered')
      AND EXTRACT(YEAR FROM pr.requisition_date) = EXTRACT(YEAR FROM CURRENT_DATE);

    v_remaining := v_budget - v_spent;

    RETURN QUERY
    SELECT
        v_budget AS budget,
        v_spent AS spent,
        v_remaining AS remaining,
        ROUND(v_spent * 100.0 / NULLIF(v_budget, 0), 2) AS usage_pct,
        CASE
            WHEN p_amount > v_remaining THEN 'OVER_BUDGET'::TEXT
            WHEN p_amount > v_remaining * 0.5 THEN 'WARNING'::TEXT
            ELSE 'OK'::TEXT
        END AS budget_status;
END;
$$;


-- ============================================================
-- 22. 月度损益计算
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_calculate_monthly_pl(
    IN p_year_month VARCHAR(7)
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_start_date DATE;
    v_end_date DATE;
    v_total_revenue DECIMAL(18,2);
    v_total_cost DECIMAL(18,2);
    v_total_salary DECIMAL(18,2);
    v_total_expense DECIMAL(18,2);
    v_gross_profit DECIMAL(18,2);
    v_net_profit DECIMAL(18,2);
BEGIN
    v_start_date := (p_year_month || '-01')::DATE;
    v_end_date := (date_trunc('month', v_start_date) + INTERVAL '1 month' - INTERVAL '1 day')::DATE;

    -- 销售收入
    SELECT COALESCE(SUM(so.total_amount), 0) INTO v_total_revenue
    FROM sales_orders so
    WHERE so.order_date BETWEEN v_start_date AND v_end_date
      AND so.status NOT IN ('draft', 'cancelled');

    -- 销售成本（基于进货价）
    SELECT COALESCE(SUM(soi.quantity * p.purchase_price), 0) INTO v_total_cost
    FROM sales_order_items soi
    JOIN sales_orders so ON soi.order_id = so.id
    JOIN products p ON soi.product_id = p.id
    WHERE so.order_date BETWEEN v_start_date AND v_end_date
      AND so.status NOT IN ('draft', 'cancelled');

    -- 工资支出
    SELECT COALESCE(SUM(net_pay + social_security_company + housing_fund_company), 0) INTO v_total_salary
    FROM salary_payments
    WHERE salary_month = p_year_month;

    -- 采购支出
    SELECT COALESCE(SUM(po.total_amount), 0) INTO v_total_expense
    FROM purchase_orders po
    WHERE po.order_date BETWEEN v_start_date AND v_end_date;

    v_gross_profit := v_total_revenue - v_total_cost;
    v_net_profit := v_gross_profit - v_total_salary;

    RETURN QUERY
    SELECT
        p_year_month AS period,
        v_total_revenue AS revenue,
        v_total_cost AS cost_of_goods,
        v_gross_profit AS gross_profit,
        ROUND(v_gross_profit * 100.0 / NULLIF(v_total_revenue, 0), 2) AS gross_margin_pct,
        v_total_salary AS salary_expense,
        v_total_expense AS purchase_expense,
        v_net_profit AS net_profit,
        ROUND(v_net_profit * 100.0 / NULLIF(v_total_revenue, 0), 2) AS net_margin_pct;
END;
$$;


-- ============================================================
-- 23. 销售业绩排名
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_sales_performance_ranking(
    IN p_start_date DATE,
    IN p_end_date DATE
)
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN QUERY
    SELECT
        e.id AS employee_id,
        e.name AS salesperson_name,
        d.name AS department_name,
        COUNT(DISTINCT so.id) AS order_count,
        SUM(so.total_amount) AS total_sales,
        SUM(so.paid_amount) AS total_collected,
        SUM(so.total_amount - so.paid_amount) AS total_outstanding,
        ROUND(AVG(so.total_amount), 2) AS avg_order_value,
        SUM(soi.quantity) AS total_qty_sold,
        COUNT(DISTINCT so.customer_id) AS unique_customers,
        ROUND(SUM(so.paid_amount) * 100.0 / NULLIF(SUM(so.total_amount), 0), 2) AS collection_rate,
        RANK() OVER (ORDER BY SUM(so.total_amount) DESC) AS sales_rank,
        DENSE_RANK() OVER (ORDER BY SUM(so.paid_amount) DESC) AS collection_rank,
        ROUND(
            SUM(so.total_amount) * 100.0 / NULLIF(SUM(SUM(so.total_amount)) OVER (), 0), 2
        ) AS sales_contribution_pct,
        ROUND(AVG(so.total_amount) OVER (PARTITION BY e.department_id), 2) AS dept_avg_sales,
        ROUND(SUM(so.total_amount) - AVG(so.total_amount) OVER (PARTITION BY e.department_id), 2) AS vs_dept_avg
    FROM employees e
    JOIN departments d ON e.department_id = d.id
    JOIN sales_orders so ON e.id = so.salesperson_id
    LEFT JOIN sales_order_items soi ON so.id = soi.order_id
    WHERE so.order_date BETWEEN p_start_date AND p_end_date
      AND so.status NOT IN ('draft', 'cancelled')
    GROUP BY e.id, e.name, d.name, e.department_id
    ORDER BY total_sales DESC;
END;
$$;


-- ============================================================
-- 24. 库存周转率计算
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_inventory_turnover(
    IN p_year_month VARCHAR(7)
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_start_date DATE;
    v_end_date DATE;
BEGIN
    v_start_date := (p_year_month || '-01')::DATE;
    v_end_date := (date_trunc('month', v_start_date) + INTERVAL '1 month' - INTERVAL '1 day')::DATE;

    RETURN QUERY
    SELECT
        p.id AS product_id,
        p.sku,
        p.name AS product_name,
        pc.name AS category_name,
        p.purchase_price,
        p.retail_price,
        COALESCE(begin_inv.total_qty, 0) AS beginning_inventory,
        COALESCE(end_inv.total_qty, 0) AS ending_inventory,
        COALESCE(sold.total_sold, 0) AS monthly_sales_qty,
        COALESCE(sold.total_sold_amount, 0) AS monthly_sales_amount,
        COALESCE(purch.total_purchased, 0) AS monthly_purchase_qty,
        ROUND((COALESCE(begin_inv.total_qty, 0) + COALESCE(end_inv.total_qty, 0)) / 2.0, 0) AS avg_inventory,
        CASE
            WHEN (COALESCE(begin_inv.total_qty, 0) + COALESCE(end_inv.total_qty, 0)) > 0
            THEN ROUND(COALESCE(sold.total_sold, 0) * 2.0 / (COALESCE(begin_inv.total_qty, 0) + COALESCE(end_inv.total_qty, 0)), 2)
            ELSE 0
        END AS turnover_rate,
        CASE
            WHEN COALESCE(sold.total_sold, 0) > 0 AND (COALESCE(begin_inv.total_qty, 0) + COALESCE(end_inv.total_qty, 0)) > 0
            THEN ROUND(30.0 / NULLIF(COALESCE(sold.total_sold, 0) * 2.0 / (COALESCE(begin_inv.total_qty, 0) + COALESCE(end_inv.total_qty, 0)), 0), 1)
            ELSE NULL
        END AS days_sales_of_inventory,
        ROUND((p.retail_price - p.purchase_price) * COALESCE(sold.total_sold, 0), 2) AS gross_profit
    FROM products p
    JOIN product_categories pc ON p.category_id = pc.id
    LEFT JOIN (
        SELECT product_id, SUM(quantity) AS total_qty
        FROM inventory
        GROUP BY product_id
    ) begin_inv ON p.id = begin_inv.product_id
    LEFT JOIN (
        SELECT product_id, SUM(quantity) AS total_qty
        FROM inventory
        GROUP BY product_id
    ) end_inv ON p.id = end_inv.product_id
    LEFT JOIN (
        SELECT soi.product_id, SUM(soi.quantity) AS total_sold, SUM(soi.amount) AS total_sold_amount
        FROM sales_order_items soi
        JOIN sales_orders so ON soi.order_id = so.id
        WHERE so.order_date BETWEEN v_start_date AND v_end_date
          AND so.status NOT IN ('draft', 'cancelled')
        GROUP BY soi.product_id
    ) sold ON p.id = sold.product_id
    LEFT JOIN (
        SELECT poi.product_id, SUM(poi.received_qty) AS total_purchased
        FROM purchase_order_items poi
        JOIN purchase_orders po ON poi.order_id = po.id
        WHERE po.order_date BETWEEN v_start_date AND v_end_date
        GROUP BY poi.product_id
    ) purch ON p.id = purch.product_id
    WHERE p.status = 'active'
    ORDER BY turnover_rate DESC;
END;
$$;


-- ============================================================
-- 25. 自动补货建议
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_auto_replenishment_suggestion()
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN QUERY
    SELECT
        p.id AS product_id,
        p.sku,
        p.name AS product_name,
        p.min_stock,
        p.max_stock,
        SUM(i.available_quantity) AS current_stock,
        p.min_stock - SUM(i.available_quantity) AS shortage_qty,
        p.max_stock - SUM(i.available_quantity) AS suggested_order_qty,
        p.purchase_price,
        (p.max_stock - SUM(i.available_quantity)) * p.purchase_price AS estimated_cost,
        COALESCE(avg_daily.sold_avg, 0) AS avg_daily_sales,
        CASE
            WHEN COALESCE(avg_daily.sold_avg, 0) > 0
            THEN ROUND(SUM(i.available_quantity) / avg_daily.sold_avg, 1)
            ELSE 999
        END AS days_of_stock_remaining,
        s.name AS preferred_supplier,
        sp.lead_time_days,
        CASE
            WHEN SUM(i.available_quantity) <= p.min_stock THEN 'URGENT'
            WHEN SUM(i.available_quantity) <= p.min_stock * 2 THEN 'LOW'
            ELSE 'OK'
        END AS priority
    FROM products p
    JOIN inventory i ON p.id = i.product_id
    LEFT JOIN (
        SELECT soi.product_id,
               ROUND(SUM(soi.quantity) / 30.0, 1) AS sold_avg
        FROM sales_order_items soi
        JOIN sales_orders so ON soi.order_id = so.id
        WHERE so.order_date >= CURRENT_DATE - INTERVAL '30 days'
          AND so.status NOT IN ('draft', 'cancelled')
        GROUP BY soi.product_id
    ) avg_daily ON p.id = avg_daily.product_id
    LEFT JOIN supplier_products sp ON p.id = sp.product_id AND sp.is_preferred = TRUE
    LEFT JOIN suppliers s ON sp.supplier_id = s.id
    WHERE p.status = 'active'
    GROUP BY p.id, p.sku, p.name, p.min_stock, p.max_stock, p.purchase_price,
             avg_daily.sold_avg, s.name, sp.lead_time_days
    HAVING SUM(i.available_quantity) <= p.min_stock * 2
    ORDER BY priority ASC, shortage_qty DESC;
END;
$$;


-- ============================================================
-- 26. 权限授予
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_grant_role_to_employee(
    IN p_employee_id BIGINT,
    IN p_role_id BIGINT,
    IN p_granted_by BIGINT,
    IN p_expires_at TIMESTAMP
)
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO employee_roles (employee_id, role_id, granted_by, expires_at)
    VALUES (p_employee_id, p_role_id, p_granted_by, p_expires_at)
    ON CONFLICT (employee_id, role_id) DO UPDATE
    SET granted_by = EXCLUDED.granted_by,
        expires_at = EXCLUDED.expires_at,
        granted_at = NOW();

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('grant_role', 'employee_role', p_employee_id, p_granted_by,
            jsonb_build_object('role_id', p_role_id, 'expires_at', p_expires_at));

    -- 返回是否成功（FOUND 表示上一条 DML 影响了行）
    IF FOUND THEN
        RAISE NOTICE 'success: true';
    ELSE
        RAISE NOTICE 'success: false';
    END IF;
END;
$$;


-- ============================================================
-- 27. 批量生成销售数据
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_generate_sales_data(
    IN p_days INT
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_day INT DEFAULT 0;
    v_order_date DATE;
    v_customer_id BIGINT;
    v_salesperson_id BIGINT;
    v_warehouse_id BIGINT;
    v_product_id BIGINT;
    v_qty INT;
    v_price DECIMAL(12,2);
    v_order_no VARCHAR(30);
    v_order_id BIGINT;
    v_total DECIMAL(18,2);
    v_items_json JSONB;
    v_customer_count INT;
    v_salesperson_count INT;
    v_product_count INT;
    v_item_count INT;
    v_i INT;
    v_batch_id BIGINT;
    v_available INT;
    v_before_qty INT;
BEGIN
    SELECT COUNT(*) INTO v_customer_count FROM customers WHERE status = 'active';
    SELECT COUNT(*) INTO v_salesperson_count FROM employees WHERE status IN ('active', 'probation');
    SELECT COUNT(*) INTO v_product_count FROM products WHERE status = 'active';

    WHILE v_day < p_days LOOP
        v_order_date := CURRENT_DATE - v_day * INTERVAL '1 day';

        -- 每天生成3-8个订单
        v_i := 0;
        WHILE v_i < FLOOR(RANDOM() * 6) + 3 LOOP
            v_customer_id := FLOOR(RANDOM() * v_customer_count) + 1;
            v_salesperson_id := FLOOR(RANDOM() * v_salesperson_count) + 1;
            v_warehouse_id := FLOOR(RANDOM() * 3) + 1;
            v_order_no := CONCAT('SO-', TO_CHAR(v_order_date, 'YYYYMMDD'), '-', lpad((FLOOR(RANDOM() * 9999) + 1)::TEXT, 4, '0'));

            INSERT INTO sales_orders (order_no, customer_id, salesperson_id, warehouse_id,
                order_date, discount_amount, payment_method, status)
            VALUES (v_order_no, v_customer_id, v_salesperson_id, v_warehouse_id,
                v_order_date, ROUND(RANDOM() * 50, 2),
                CASE FLOOR(RANDOM() * 6) + 1
                    WHEN 1 THEN 'cash'
                    WHEN 2 THEN 'card'
                    WHEN 3 THEN 'transfer'
                    WHEN 4 THEN 'credit'
                    WHEN 5 THEN 'wechat'
                    WHEN 6 THEN 'alipay'
                END,
                CASE FLOOR(RANDOM() * 3) + 1
                    WHEN 1 THEN 'confirmed'
                    WHEN 2 THEN 'delivered'
                    WHEN 3 THEN 'delivered'
                END)
            RETURNING id INTO v_order_id;

            v_total := 0;
            v_i := v_i + 1;
            v_item_count := FLOOR(RANDOM() * 5) + 1;

            WHILE v_item_count > 0 LOOP
                v_product_id := FLOOR(RANDOM() * v_product_count) + 1;
                v_qty := FLOOR(RANDOM() * 10) + 1;
                v_price := (SELECT retail_price FROM products WHERE id = v_product_id);

                -- 获取一个有库存的批号
                SELECT id INTO v_batch_id
                FROM product_batches
                WHERE product_id = v_product_id AND current_qty >= v_qty AND status = 'active'
                LIMIT 1;

                SELECT COALESCE(available_quantity, 0) INTO v_available
                FROM inventory
                WHERE product_id = v_product_id AND warehouse_id = v_warehouse_id
                  AND (batch_id = v_batch_id OR v_batch_id IS NULL);

                IF v_available >= v_qty THEN
                    INSERT INTO sales_order_items (order_id, product_id, batch_id, quantity, unit_price, amount)
                    VALUES (v_order_id, v_product_id, v_batch_id, v_qty, v_price, v_qty * v_price);

                    -- 扣库存
                    SELECT quantity INTO v_before_qty
                    FROM inventory
                    WHERE product_id = v_product_id AND warehouse_id = v_warehouse_id
                      AND (batch_id = v_batch_id OR v_batch_id IS NULL);

                    UPDATE inventory SET quantity = quantity - v_qty
                    WHERE product_id = v_product_id AND warehouse_id = v_warehouse_id
                      AND (batch_id = v_batch_id OR v_batch_id IS NULL);

                    IF v_batch_id IS NOT NULL THEN
                        UPDATE product_batches SET current_qty = current_qty - v_qty WHERE id = v_batch_id;
                    END IF;

                    INSERT INTO inventory_transactions (product_id, batch_id, warehouse_id,
                        transaction_type, quantity_change, before_qty, after_qty,
                        reference_type, reference_id, operator_id)
                    VALUES (v_product_id, v_batch_id, v_warehouse_id, 'sales_out',
                        -v_qty, v_before_qty, v_before_qty - v_qty,
                        'sales_order', v_order_id, v_salesperson_id);

                    v_total := v_total + (v_qty * v_price);
                END IF;

                v_item_count := v_item_count - 1;
            END LOOP;

            UPDATE sales_orders
            SET total_amount = v_total,
                paid_amount = CASE WHEN status = 'delivered' THEN v_total * (0.8 + RANDOM() * 0.2) ELSE 0 END
            WHERE id = v_order_id;

            COMMIT;
        END LOOP;

        v_day := v_day + 1;
    END LOOP;

    RAISE NOTICE '销售数据生成完成，共%天', p_days;
END;
$$;


-- ============================================================
-- 28. 批量生成采购数据
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_generate_purchase_data(
    IN p_days INT
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_day INT DEFAULT 0;
    v_order_date DATE;
    v_supplier_id BIGINT;
    v_purchaser_id BIGINT;
    v_dept_id BIGINT;
    v_product_id BIGINT;
    v_qty INT;
    v_price DECIMAL(12,2);
    v_order_no VARCHAR(30);
    v_order_id BIGINT;
    v_total DECIMAL(18,2);
    v_item_count INT;
    v_batch_no VARCHAR(50);
    v_batch_id BIGINT;
    v_receipt_no VARCHAR(30);
    v_before_qty INT;
    v_supplier_count INT;
    v_product_count INT;
    v_warehouse_id BIGINT;
BEGIN
    SELECT COUNT(*) INTO v_supplier_count FROM suppliers WHERE cooperation_status = 'active';
    SELECT COUNT(*) INTO v_product_count FROM products WHERE status = 'active';

    WHILE v_day < p_days LOOP
        v_order_date := CURRENT_DATE - v_day * INTERVAL '1 day';

        -- 每天2-5个采购单
        v_item_count := FLOOR(RANDOM() * 4) + 2;
        WHILE v_item_count > 0 LOOP
            v_supplier_id := FLOOR(RANDOM() * v_supplier_count) + 1;
            v_purchaser_id := FLOOR(RANDOM() * 10) + 1;
            v_dept_id := FLOOR(RANDOM() * 5) + 1;
            v_order_no := CONCAT('PO-', TO_CHAR(v_order_date, 'YYYYMMDD'), '-', lpad((FLOOR(RANDOM() * 9999) + 1)::TEXT, 4, '0'));

            INSERT INTO purchase_orders (order_no, supplier_id, department_id,
                purchaser_id, order_date, expected_delivery_date, status)
            VALUES (v_order_no, v_supplier_id, v_dept_id,
                v_purchaser_id, v_order_date,
                v_order_date + (FLOOR(RANDOM() * 10) + 3) * INTERVAL '1 day',
                'received')
            RETURNING id INTO v_order_id;

            v_total := 0;

            v_product_id := FLOOR(RANDOM() * v_product_count) + 1;
            v_qty := FLOOR(RANDOM() * 50) + 10;
            v_price := (SELECT purchase_price FROM products WHERE id = v_product_id);

            INSERT INTO purchase_order_items (order_id, product_id, quantity, unit_price, received_qty)
            VALUES (v_order_id, v_product_id, v_qty, v_price, v_qty);

            v_total := v_total + (v_qty * v_price);

            -- 生成批号和入库
            v_batch_no := CONCAT('BT-', TO_CHAR(v_order_date, 'YYYYMMDD'), '-', lpad((FLOOR(RANDOM() * 999) + 1)::TEXT, 3, '0'));

            INSERT INTO product_batches (product_id, batch_no, production_date, expiry_date,
                purchase_price, initial_qty, current_qty)
            VALUES (v_product_id, v_batch_no, v_order_date,
                v_order_date + (SELECT shelf_life_days FROM products WHERE id = v_product_id) * INTERVAL '1 day',
                v_price, v_qty, v_qty)
            RETURNING id INTO v_batch_id;

            v_warehouse_id := FLOOR(RANDOM() * 3) + 1;

            -- 入库
            SELECT COALESCE(SUM(quantity), 0) INTO v_before_qty
            FROM inventory WHERE product_id = v_product_id AND warehouse_id = v_warehouse_id;

            INSERT INTO inventory (product_id, batch_id, warehouse_id, quantity)
            VALUES (v_product_id, v_batch_id, v_warehouse_id, v_qty)
            ON CONFLICT (product_id, batch_id, warehouse_id) DO UPDATE
            SET quantity = inventory.quantity + v_qty;

            INSERT INTO inventory_transactions (product_id, batch_id, warehouse_id,
                transaction_type, quantity_change, before_qty, after_qty,
                reference_type, reference_id, operator_id)
            VALUES (v_product_id, v_batch_id, v_warehouse_id, 'purchase_in',
                v_qty, v_before_qty, v_before_qty + v_qty,
                'purchase_order', v_order_id, v_purchaser_id);

            -- 生成入库单
            v_receipt_no := CONCAT('RC-', TO_CHAR(v_order_date, 'YYYYMMDD'), '-', lpad((FLOOR(RANDOM() * 9999) + 1)::TEXT, 4, '0'));
            INSERT INTO purchase_receipts (receipt_no, order_id, warehouse_id, receiver_id,
                receipt_date, total_qty, total_amount, status)
            VALUES (v_receipt_no, v_order_id, v_warehouse_id, v_purchaser_id,
                v_order_date, v_qty, v_total, 'received');

            UPDATE purchase_orders SET total_amount = v_total, paid_amount = v_total * 0.7 WHERE id = v_order_id;

            COMMIT;
            v_item_count := v_item_count - 1;
        END LOOP;

        v_day := v_day + 1;
    END LOOP;

    RAISE NOTICE '采购数据生成完成，共%天', p_days;
END;
$$;