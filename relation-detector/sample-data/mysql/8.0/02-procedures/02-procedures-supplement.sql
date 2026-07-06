-- ============================================================
-- ERP系统补充存储过程
-- 库存调拨、盘点、批号管理、客户信用、供应商评估、
-- 预算控制、损益计算、销售分析、权限管理
-- ============================================================

USE erp_system;

DELIMITER //

-- ============================================================
-- 16. 库存调拨
-- ============================================================

CREATE PROCEDURE sp_transfer_inventory(
    IN p_product_id BIGINT UNSIGNED,
    IN p_batch_id BIGINT UNSIGNED,
    IN p_from_warehouse_id BIGINT UNSIGNED,
    IN p_to_warehouse_id BIGINT UNSIGNED,
    IN p_quantity INT,
    IN p_operator_id BIGINT UNSIGNED,
    IN p_remark VARCHAR(500)
)
BEGIN
    DECLARE v_available INT;
    DECLARE v_from_before INT;
    DECLARE v_to_before INT;

    SELECT COALESCE(available_quantity, 0) INTO v_available
    FROM inventory
    WHERE product_id = p_product_id
      AND (batch_id = p_batch_id OR (p_batch_id IS NULL AND batch_id IS NULL))
      AND warehouse_id = p_from_warehouse_id;

    IF v_available < p_quantity THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = CONCAT('调出仓库库存不足: 可用=', v_available, ', 调拨=', p_quantity);
    END IF;

    START TRANSACTION;

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
    ON DUPLICATE KEY UPDATE quantity = quantity + p_quantity;

    INSERT INTO inventory_transactions (product_id, batch_id, warehouse_id,
        transaction_type, quantity_change, before_qty, after_qty,
        reference_type, operator_id, remark)
    VALUES (p_product_id, p_batch_id, p_to_warehouse_id, 'transfer_in',
        p_quantity, v_to_before, v_to_before + p_quantity,
        'transfer', p_operator_id, p_remark);

    INSERT INTO audit_log (action, target_type, employee_id, new_value)
    VALUES ('transfer_inventory', 'inventory', p_operator_id,
            JSON_OBJECT('product_id', p_product_id, 'quantity', p_quantity,
                       'from_wh', p_from_warehouse_id, 'to_wh', p_to_warehouse_id));

    COMMIT;

    SELECT '调拨成功' AS result;
END//


-- ============================================================
-- 17. 库存盘点
-- ============================================================

CREATE PROCEDURE sp_stocktake(
    IN p_warehouse_id BIGINT UNSIGNED,
    IN p_product_id BIGINT UNSIGNED,
    IN p_batch_id BIGINT UNSIGNED,
    IN p_actual_qty INT,
    IN p_operator_id BIGINT UNSIGNED,
    IN p_remark VARCHAR(500)
)
BEGIN
    DECLARE v_book_qty INT;
    DECLARE v_diff INT;

    SELECT COALESCE(quantity, 0) INTO v_book_qty
    FROM inventory
    WHERE product_id = p_product_id
      AND (batch_id = p_batch_id OR (p_batch_id IS NULL AND batch_id IS NULL))
      AND warehouse_id = p_warehouse_id;

    SET v_diff = p_actual_qty - v_book_qty;

    IF v_diff = 0 THEN
        -- 仅更新盘点日期
        UPDATE inventory
        SET last_stocktake_date = CURDATE()
        WHERE product_id = p_product_id
          AND (batch_id = p_batch_id OR (p_batch_id IS NULL AND batch_id IS NULL))
          AND warehouse_id = p_warehouse_id;

        SELECT '盘点无差异' AS result;
    ELSE
        START TRANSACTION;

        UPDATE inventory
        SET quantity = p_actual_qty,
            last_stocktake_date = CURDATE()
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
                JSON_OBJECT('quantity', v_book_qty),
                JSON_OBJECT('quantity', p_actual_qty, 'diff', v_diff));

        COMMIT;

        SELECT CONCAT('盘点调整完成，差异: ', v_diff) AS result;
    END IF;
END//


-- ============================================================
-- 18. 批号过期处理
-- ============================================================

CREATE PROCEDURE sp_process_expired_batches(
    IN p_operator_id BIGINT UNSIGNED
)
BEGIN
    DECLARE v_done INT DEFAULT FALSE;
    DECLARE v_batch_id BIGINT;
    DECLARE v_product_id BIGINT;
    DECLARE v_current_qty INT;
    DECLARE v_warehouse_id BIGINT;
    DECLARE v_before_qty INT;

    DECLARE cur CURSOR FOR
        SELECT pb.id, pb.product_id, pb.current_qty, i.warehouse_id
        FROM product_batches pb
        JOIN inventory i ON pb.id = i.batch_id
        WHERE pb.expiry_date <= CURDATE()
          AND pb.status = 'active'
          AND pb.current_qty > 0;

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = TRUE;

    START TRANSACTION;

    OPEN cur;

    batch_loop: LOOP
        FETCH cur INTO v_batch_id, v_product_id, v_current_qty, v_warehouse_id;
        IF v_done THEN LEAVE batch_loop; END IF;

        SELECT quantity INTO v_before_qty
        FROM inventory
        WHERE product_id = v_product_id AND batch_id = v_batch_id AND warehouse_id = v_warehouse_id;

        -- 锁定并标记过期
        UPDATE product_batches SET status = 'expired' WHERE id = v_batch_id;

        -- 过期库存出库（报废）
        UPDATE inventory SET quantity = 0
        WHERE product_id = v_product_id AND batch_id = v_batch_id AND warehouse_id = v_warehouse_id;

        INSERT INTO inventory_transactions (product_id, batch_id, warehouse_id,
            transaction_type, quantity_change, before_qty, after_qty,
            reference_type, operator_id, remark)
        VALUES (v_product_id, v_batch_id, v_warehouse_id, 'scrap_out',
            -v_current_qty, v_before_qty, 0, 'batch_expiry', p_operator_id,
            CONCAT('批号过期报废: batch_id=', v_batch_id));

    END LOOP;

    CLOSE cur;

    INSERT INTO audit_log (action, target_type, employee_id, new_value)
    VALUES ('process_expired_batches', 'batch', p_operator_id,
            JSON_OBJECT('processed_date', CURDATE()));

    COMMIT;

    SELECT '过期批号处理完成' AS result;
END//


-- ============================================================
-- 19. 客户信用额度管理
-- ============================================================

CREATE PROCEDURE sp_check_customer_credit(
    IN p_customer_id BIGINT UNSIGNED,
    IN p_order_amount DECIMAL(18,2)
)
BEGIN
    DECLARE v_credit_limit DECIMAL(18,2);
    DECLARE v_balance DECIMAL(18,2);
    DECLARE v_status VARCHAR(20);
    DECLARE v_unpaid_total DECIMAL(18,2);

    SELECT credit_limit, balance, status
    INTO v_credit_limit, v_balance, v_status
    FROM customers WHERE id = p_customer_id;

    IF v_status = 'frozen' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '客户已被冻结，无法交易';
    END IF;

    IF v_status = 'blacklist' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '客户在黑名单中，无法交易';
    END IF;

    -- 计算未结清的销售金额
    SELECT COALESCE(SUM(so.total_amount - so.paid_amount), 0) INTO v_unpaid_total
    FROM sales_orders so
    WHERE so.customer_id = p_customer_id
      AND so.status IN ('confirmed', 'delivering', 'delivered');

    SELECT
        v_credit_limit AS credit_limit,
        v_balance AS current_balance,
        v_unpaid_total AS unpaid_total,
        v_credit_limit - v_balance - v_unpaid_total AS available_credit,
        CASE
            WHEN (v_balance + v_unpaid_total + p_order_amount) > v_credit_limit THEN 'OVER_LIMIT'
            WHEN (v_balance + v_unpaid_total + p_order_amount) > v_credit_limit * 0.8 THEN 'WARNING'
            ELSE 'OK'
        END AS credit_status;
END//


-- ============================================================
-- 20. 供应商评估
-- ============================================================

CREATE PROCEDURE sp_evaluate_supplier(
    IN p_supplier_id BIGINT UNSIGNED,
    IN p_start_date DATE,
    IN p_end_date DATE
)
BEGIN
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
            THEN DATEDIFF(po.actual_delivery_date, po.expected_delivery_date)
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
                THEN DATEDIFF(po.actual_delivery_date, po.expected_delivery_date) END) <= 0
                AND SUM(COALESCE(pri.rejected_qty, 0)) * 100.0 / NULLIF(SUM(COALESCE(pri.received_qty, 0)), 0) <= 2
            THEN 'A'
            WHEN AVG(CASE WHEN po.actual_delivery_date IS NOT NULL
                THEN DATEDIFF(po.actual_delivery_date, po.expected_delivery_date) END) <= 3
            THEN 'B'
            WHEN AVG(CASE WHEN po.actual_delivery_date IS NOT NULL
                THEN DATEDIFF(po.actual_delivery_date, po.expected_delivery_date) END) <= 7
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
END//


-- ============================================================
-- 21. 部门预算控制
-- ============================================================

CREATE PROCEDURE sp_check_department_budget(
    IN p_department_id BIGINT UNSIGNED,
    IN p_amount DECIMAL(18,2)
)
BEGIN
    DECLARE v_budget DECIMAL(18,2);
    DECLARE v_spent DECIMAL(18,2);
    DECLARE v_remaining DECIMAL(18,2);

    SELECT budget INTO v_budget FROM departments WHERE id = p_department_id;

    -- 已使用预算 = 已审批请购 + 已下单采购
    SELECT COALESCE(SUM(pr.total_amount), 0) INTO v_spent
    FROM purchase_requisitions pr
    WHERE pr.department_id = p_department_id
      AND pr.status IN ('approved', 'ordered')
      AND YEAR(pr.requisition_date) = YEAR(CURDATE());

    SET v_remaining = v_budget - v_spent;

    SELECT
        v_budget AS budget,
        v_spent AS spent,
        v_remaining AS remaining,
        ROUND(v_spent * 100.0 / NULLIF(v_budget, 0), 2) AS usage_pct,
        CASE
            WHEN p_amount > v_remaining THEN 'OVER_BUDGET'
            WHEN p_amount > v_remaining * 0.5 THEN 'WARNING'
            ELSE 'OK'
        END AS budget_status;
END//


-- ============================================================
-- 22. 月度损益计算
-- ============================================================

CREATE PROCEDURE sp_calculate_monthly_pl(
    IN p_year_month VARCHAR(7)
)
BEGIN
    DECLARE v_start_date DATE;
    DECLARE v_end_date DATE;
    DECLARE v_total_revenue DECIMAL(18,2);
    DECLARE v_total_cost DECIMAL(18,2);
    DECLARE v_total_salary DECIMAL(18,2);
    DECLARE v_total_expense DECIMAL(18,2);
    DECLARE v_gross_profit DECIMAL(18,2);
    DECLARE v_net_profit DECIMAL(18,2);

    SET v_start_date = CONCAT(p_year_month, '-01');
    SET v_end_date = LAST_DAY(v_start_date);

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

    SET v_gross_profit = v_total_revenue - v_total_cost;
    SET v_net_profit = v_gross_profit - v_total_salary;

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
END//


-- ============================================================
-- 23. 销售业绩排名
-- ============================================================

CREATE PROCEDURE sp_sales_performance_ranking(
    IN p_start_date DATE,
    IN p_end_date DATE
)
BEGIN
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
END//


-- ============================================================
-- 24. 库存周转率计算
-- ============================================================

CREATE PROCEDURE sp_inventory_turnover(
    IN p_year_month VARCHAR(7)
)
BEGIN
    DECLARE v_start_date DATE;
    DECLARE v_end_date DATE;

    SET v_start_date = CONCAT(p_year_month, '-01');
    SET v_end_date = LAST_DAY(v_start_date);

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
END//


-- ============================================================
-- 25. 自动补货建议
-- ============================================================

CREATE PROCEDURE sp_auto_replenishment_suggestion()
BEGIN
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
        WHERE so.order_date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
          AND so.status NOT IN ('draft', 'cancelled')
        GROUP BY soi.product_id
    ) avg_daily ON p.id = avg_daily.product_id
    LEFT JOIN supplier_products sp ON p.id = sp.product_id AND sp.is_preferred = TRUE
    LEFT JOIN suppliers s ON sp.supplier_id = s.id
    WHERE p.status = 'active'
    GROUP BY p.id, p.sku, p.name, p.min_stock, p.max_stock, p.purchase_price,
             avg_daily.sold_avg, s.name, sp.lead_time_days
    HAVING current_stock <= p.min_stock * 2
    ORDER BY priority ASC, shortage_qty DESC;
END//


-- ============================================================
-- 26. 权限授予
-- ============================================================

CREATE PROCEDURE sp_grant_role_to_employee(
    IN p_employee_id BIGINT UNSIGNED,
    IN p_role_id BIGINT UNSIGNED,
    IN p_granted_by BIGINT UNSIGNED,
    IN p_expires_at DATETIME
)
BEGIN
    INSERT INTO employee_roles (employee_id, role_id, granted_by, expires_at)
    VALUES (p_employee_id, p_role_id, p_granted_by, p_expires_at)
    ON DUPLICATE KEY UPDATE
        granted_by = VALUES(granted_by),
        expires_at = VALUES(expires_at),
        granted_at = NOW();

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('grant_role', 'employee_role', p_employee_id, p_granted_by,
            JSON_OBJECT('role_id', p_role_id, 'expires_at', p_expires_at));

    SELECT ROW_COUNT() > 0 AS success;
END//


-- ============================================================
-- 27. 批量生成销售数据
-- ============================================================

CREATE PROCEDURE sp_generate_sales_data(
    IN p_days INT
)
BEGIN
    DECLARE v_day INT DEFAULT 0;
    DECLARE v_order_date DATE;
    DECLARE v_customer_id BIGINT UNSIGNED;
    DECLARE v_salesperson_id BIGINT UNSIGNED;
    DECLARE v_warehouse_id BIGINT UNSIGNED;
    DECLARE v_product_id BIGINT UNSIGNED;
    DECLARE v_qty INT;
    DECLARE v_price DECIMAL(12,2);
    DECLARE v_order_no VARCHAR(30);
    DECLARE v_order_id BIGINT UNSIGNED;
    DECLARE v_total DECIMAL(18,2);
    DECLARE v_items_json JSON;
    DECLARE v_customer_count INT;
    DECLARE v_salesperson_count INT;
    DECLARE v_product_count INT;
    DECLARE v_item_count INT;
    DECLARE v_i INT;
    DECLARE v_batch_id BIGINT;
    DECLARE v_available INT;
    DECLARE v_before_qty INT;

    SELECT COUNT(*) INTO v_customer_count FROM customers WHERE status = 'active';
    SELECT COUNT(*) INTO v_salesperson_count FROM employees WHERE status IN ('active', 'probation');
    SELECT COUNT(*) INTO v_product_count FROM products WHERE status = 'active';

    WHILE v_day < p_days DO
        SET v_order_date = DATE_SUB(CURDATE(), INTERVAL v_day DAY);

        -- 每天生成3-8个订单
        SET v_i = 0;
        WHILE v_i < FLOOR(RAND() * 6) + 3 DO
            SET v_customer_id = FLOOR(RAND() * v_customer_count) + 1;
            SET v_salesperson_id = FLOOR(RAND() * v_salesperson_count) + 1;
            SET v_warehouse_id = FLOOR(RAND() * 3) + 1;
            SET v_order_no = CONCAT('SO-', DATE_FORMAT(v_order_date, '%Y%m%d'), '-', LPAD(FLOOR(RAND() * 9999) + 1, 4, '0'));

            START TRANSACTION;

            INSERT INTO sales_orders (order_no, customer_id, salesperson_id, warehouse_id,
                order_date, discount_amount, payment_method, status)
            VALUES (v_order_no, v_customer_id, v_salesperson_id, v_warehouse_id,
                v_order_date, ROUND(RAND() * 50, 2),
                ELT(FLOOR(RAND() * 6) + 1, 'cash', 'card', 'transfer', 'credit', 'wechat', 'alipay'),
                ELT(FLOOR(RAND() * 3) + 1, 'confirmed', 'delivered', 'delivered'));

            SET v_order_id = LAST_INSERT_ID();
            SET v_total = 0;
            SET v_item_count = FLOOR(RAND() * 5) + 1;

            SET v_i = v_i + 1;
            SET v_item_count = FLOOR(RAND() * 5) + 1;

            WHILE v_item_count > 0 DO
                SET v_product_id = FLOOR(RAND() * v_product_count) + 1;
                SET v_qty = FLOOR(RAND() * 10) + 1;
                SET v_price = (SELECT retail_price FROM products WHERE id = v_product_id);

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

                    SET v_total = v_total + (v_qty * v_price);
                END IF;

                SET v_item_count = v_item_count - 1;
            END WHILE;

            UPDATE sales_orders
            SET total_amount = v_total,
                paid_amount = IF(status = 'delivered', v_total * (0.8 + RAND() * 0.2), 0)
            WHERE id = v_order_id;

            COMMIT;
        END WHILE;

        SET v_day = v_day + 1;
    END WHILE;

    SELECT CONCAT('销售数据生成完成，共', p_days, '天') AS result;
END//


-- ============================================================
-- 28. 批量生成采购数据
-- ============================================================

CREATE PROCEDURE sp_generate_purchase_data(
    IN p_days INT
)
BEGIN
    DECLARE v_day INT DEFAULT 0;
    DECLARE v_order_date DATE;
    DECLARE v_supplier_id BIGINT UNSIGNED;
    DECLARE v_purchaser_id BIGINT UNSIGNED;
    DECLARE v_dept_id BIGINT UNSIGNED;
    DECLARE v_product_id BIGINT UNSIGNED;
    DECLARE v_qty INT;
    DECLARE v_price DECIMAL(12,2);
    DECLARE v_order_no VARCHAR(30);
    DECLARE v_order_id BIGINT UNSIGNED;
    DECLARE v_total DECIMAL(18,2);
    DECLARE v_item_count INT;
    DECLARE v_batch_no VARCHAR(50);
    DECLARE v_batch_id BIGINT;
    DECLARE v_receipt_no VARCHAR(30);
    DECLARE v_before_qty INT;
    DECLARE v_supplier_count INT;
    DECLARE v_product_count INT;
    DECLARE v_warehouse_id BIGINT;

    SELECT COUNT(*) INTO v_supplier_count FROM suppliers WHERE cooperation_status = 'active';
    SELECT COUNT(*) INTO v_product_count FROM products WHERE status = 'active';

    WHILE v_day < p_days DO
        SET v_order_date = DATE_SUB(CURDATE(), INTERVAL v_day DAY);

        -- 每天2-5个采购单
        SET v_item_count = FLOOR(RAND() * 4) + 2;
        WHILE v_item_count > 0 DO
            SET v_supplier_id = FLOOR(RAND() * v_supplier_count) + 1;
            SET v_purchaser_id = FLOOR(RAND() * 10) + 1;
            SET v_dept_id = FLOOR(RAND() * 5) + 1;
            SET v_order_no = CONCAT('PO-', DATE_FORMAT(v_order_date, '%Y%m%d'), '-', LPAD(FLOOR(RAND() * 9999) + 1, 4, '0'));

            START TRANSACTION;

            INSERT INTO purchase_orders (order_no, supplier_id, department_id,
                purchaser_id, order_date, expected_delivery_date, status)
            VALUES (v_order_no, v_supplier_id, v_dept_id,
                v_purchaser_id, v_order_date,
                DATE_ADD(v_order_date, INTERVAL FLOOR(RAND() * 10) + 3 DAY),
                'received');

            SET v_order_id = LAST_INSERT_ID();
            SET v_total = 0;

            SET v_product_id = FLOOR(RAND() * v_product_count) + 1;
            SET v_qty = FLOOR(RAND() * 50) + 10;
            SET v_price = (SELECT purchase_price FROM products WHERE id = v_product_id);

            INSERT INTO purchase_order_items (order_id, product_id, quantity, unit_price, received_qty)
            VALUES (v_order_id, v_product_id, v_qty, v_price, v_qty);

            SET v_total = v_total + (v_qty * v_price);

            -- 生成批号和入库
            SET v_batch_no = CONCAT('BT-', DATE_FORMAT(v_order_date, '%Y%m%d'), '-', LPAD(FLOOR(RAND() * 999) + 1, 3, '0'));

            INSERT INTO product_batches (product_id, batch_no, production_date, expiry_date,
                purchase_price, initial_qty, current_qty)
            VALUES (v_product_id, v_batch_no, v_order_date,
                DATE_ADD(v_order_date, INTERVAL (SELECT shelf_life_days FROM products WHERE id = v_product_id) DAY),
                v_price, v_qty, v_qty);

            SET v_batch_id = LAST_INSERT_ID();
            SET v_warehouse_id = FLOOR(RAND() * 3) + 1;

            -- 入库
            SELECT COALESCE(SUM(quantity), 0) INTO v_before_qty
            FROM inventory WHERE product_id = v_product_id AND warehouse_id = v_warehouse_id;

            INSERT INTO inventory (product_id, batch_id, warehouse_id, quantity)
            VALUES (v_product_id, v_batch_id, v_warehouse_id, v_qty)
            ON DUPLICATE KEY UPDATE quantity = quantity + v_qty;

            INSERT INTO inventory_transactions (product_id, batch_id, warehouse_id,
                transaction_type, quantity_change, before_qty, after_qty,
                reference_type, reference_id, operator_id)
            VALUES (v_product_id, v_batch_id, v_warehouse_id, 'purchase_in',
                v_qty, v_before_qty, v_before_qty + v_qty,
                'purchase_order', v_order_id, v_purchaser_id);

            -- 生成入库单
            SET v_receipt_no = CONCAT('RC-', DATE_FORMAT(v_order_date, '%Y%m%d'), '-', LPAD(FLOOR(RAND() * 9999) + 1, 4, '0'));
            INSERT INTO purchase_receipts (receipt_no, order_id, warehouse_id, receiver_id,
                receipt_date, total_qty, total_amount, status)
            VALUES (v_receipt_no, v_order_id, v_warehouse_id, v_purchaser_id,
                v_order_date, v_qty, v_total, 'received');

            UPDATE purchase_orders SET total_amount = v_total, paid_amount = v_total * 0.7 WHERE id = v_order_id;

            COMMIT;
            SET v_item_count = v_item_count - 1;
        END WHILE;

        SET v_day = v_day + 1;
    END WHILE;

    SELECT CONCAT('采购数据生成完成，共', p_days, '天') AS result;
END//


DELIMITER ;