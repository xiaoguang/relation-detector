-- relation-detector-fixture-source: ROUTINE:erp_system.sp_create_shipment
CREATE PROCEDURE sp_create_shipment(
    IN p_order_id BIGINT UNSIGNED,
    IN p_carrier VARCHAR(100),
    IN p_shipping_method ENUM('express','truck','air','sea','self_pickup'),
    IN p_shipping_fee DECIMAL(12,2),
    IN p_to_address VARCHAR(300),
    IN p_receiver_name VARCHAR(50),
    IN p_receiver_phone VARCHAR(20)
)
BEGIN
    DECLARE v_shipment_no VARCHAR(30);
    DECLARE v_warehouse_id BIGINT;
    DECLARE v_order_status VARCHAR(20);

    SELECT status, warehouse_id INTO v_order_status, v_warehouse_id
    FROM sales_orders WHERE id = p_order_id;

    IF v_order_status NOT IN ('confirmed', 'delivering') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '销售单状态不允许发货';
    END IF;

    SET v_shipment_no = CONCAT('SH-', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-', LPAD(FLOOR(RAND() * 9999) + 1, 4, '0'));

    INSERT INTO shipments (shipment_no, order_id, warehouse_id, carrier,
        shipping_method, shipping_fee, to_address, receiver_name, receiver_phone,
        status, estimated_delivery_date)
    VALUES (v_shipment_no, p_order_id, v_warehouse_id, p_carrier,
        p_shipping_method, p_shipping_fee, p_to_address, p_receiver_name, p_receiver_phone,
        'pending', DATE_ADD(CURDATE(), INTERVAL 3 DAY));

    UPDATE sales_orders SET status = 'delivering' WHERE id = p_order_id;

    SELECT LAST_INSERT_ID() AS shipment_id, v_shipment_no AS shipment_no;
END//


-- ============================================================
-- 30. 拣货打包流程
-- 分配拣货员和打包员，更新发货状态
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:erp_system.sp_pick_and_pack
CREATE PROCEDURE sp_pick_and_pack(
    IN p_shipment_id BIGINT UNSIGNED,
    IN p_picker_id BIGINT UNSIGNED,
    IN p_packer_id BIGINT UNSIGNED
)
BEGIN
    UPDATE shipments
    SET picker_id = p_picker_id,
        packer_id = p_packer_id,
        status = 'packed',
        shipped_at = NOW()
    WHERE id = p_shipment_id AND status = 'pending';

    -- 添加物流轨迹
    INSERT INTO shipping_tracks (shipment_id, track_time, location, status_desc, operator)
    VALUES (p_shipment_id, NOW(), '仓库', '已打包完成，等待揽收', fn_employee_full_name(p_packer_id));

    SELECT ROW_COUNT() > 0 AS success;
END//


-- ============================================================
-- 31. 销售提成计算
-- 调用关系: 被月度结算流程调用
-- 计算原理:
--   1. 按员工分组汇总当月销售额
--   2. 匹配提成规则(产品分类+金额阶梯)
--   3. 计算提成金额 = 销售额 * 提成率 + 额外奖金
--   4. 超额奖励: 总销售额超过30万，额外2%提成+5000奖金
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:erp_system.sp_calculate_commission
CREATE PROCEDURE sp_calculate_commission(
    IN p_period VARCHAR(7)
)
BEGIN
    DECLARE v_start_date DATE;
    DECLARE v_end_date DATE;

    SET v_start_date = CONCAT(p_period, '-01');
    SET v_end_date = LAST_DAY(v_start_date);

    -- 删除已计算的当月提成
    DELETE FROM sales_commissions WHERE period = p_period;

    -- 按订单明细计算提成
    INSERT INTO sales_commissions (employee_id, order_id, order_item_id, period,
        base_amount, commission_rate, commission_amount, bonus, status, calculated_at)
    SELECT
        so.salesperson_id,
        so.id,
        soi.id,
        p_period,
        soi.amount,
        COALESCE(cr.commission_rate, 0.02),
        ROUND(soi.amount * COALESCE(cr.commission_rate, 0.02), 2),
        COALESCE(cr.bonus, 0),
        'calculated',
        NOW()
    FROM sales_order_items soi
    JOIN sales_orders so ON soi.order_id = so.id
    JOIN products p ON soi.product_id = p.id
    LEFT JOIN commission_rules cr ON (
        (cr.product_category_id IS NULL OR cr.product_category_id = p.category_id)
        AND soi.amount >= cr.min_amount
        AND soi.amount < cr.max_amount
        AND cr.status = 'active'
        AND cr.effective_date <= so.order_date
        AND (cr.expiry_date IS NULL OR cr.expiry_date >= so.order_date)
    )
    WHERE so.order_date BETWEEN v_start_date AND v_end_date
      AND so.status NOT IN ('draft', 'cancelled');

    -- 超额奖励: 总销售额超过30万的员工
    UPDATE sales_commissions sc
    JOIN (
        SELECT employee_id, SUM(base_amount) AS total_sales
        FROM sales_commissions
        WHERE period = p_period
        GROUP BY employee_id
        HAVING SUM(base_amount) > 300000
    ) top_sales ON sc.employee_id = top_sales.employee_id AND sc.period = p_period
    SET sc.commission_amount = sc.commission_amount + ROUND(sc.base_amount * 0.02, 2),
        sc.bonus = sc.bonus + 5000;

    SELECT CONCAT('提成计算完成: ', p_period, ', 共', COUNT(*), '条') AS result
    FROM sales_commissions WHERE period = p_period;
END//


-- ============================================================
-- 32. 促销验证
-- 验证促销码是否可用，返回折扣金额
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:erp_system.sp_validate_promotion
CREATE PROCEDURE sp_validate_promotion(
    IN p_promo_code VARCHAR(30),
    IN p_customer_id BIGINT UNSIGNED,
    IN p_order_amount DECIMAL(18,2)
)
BEGIN
    DECLARE v_promo_id BIGINT;
    DECLARE v_type VARCHAR(20);
    DECLARE v_discount_value DECIMAL(12,2);
    DECLARE v_min_purchase DECIMAL(18,2);
    DECLARE v_max_discount DECIMAL(12,2);
    DECLARE v_usage_limit INT;
    DECLARE v_used_count INT;
    DECLARE v_status VARCHAR(20);
    DECLARE v_start_date DATE;
    DECLARE v_end_date DATE;
    DECLARE v_discount_amount DECIMAL(12,2) DEFAULT 0.00;

    SELECT id, promotion_type, discount_value, min_purchase_amount, max_discount_amount,
           usage_limit, used_count, status, start_date, end_date
    INTO v_promo_id, v_type, v_discount_value, v_min_purchase, v_max_discount,
         v_usage_limit, v_used_count, v_status, v_start_date, v_end_date
    FROM promotions
    WHERE code = p_promo_code;

    IF v_promo_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '促销码不存在';
    END IF;

    IF v_status != 'active' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '促销活动未激活';
    END IF;

    IF CURDATE() < v_start_date OR CURDATE() > v_end_date THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '促销活动不在有效期内';
    END IF;

    IF v_usage_limit > 0 AND v_used_count >= v_usage_limit THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '促销活动已达使用上限';
    END IF;

    IF p_order_amount < v_min_purchase THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = CONCAT('未达到最低消费金额: ', v_min_purchase);
    END IF;

    -- 计算折扣金额
    CASE v_type
        WHEN 'discount_pct' THEN
            SET v_discount_amount = LEAST(ROUND(p_order_amount * v_discount_value / 100.0, 2), v_max_discount);
        WHEN 'discount_amount' THEN
            SET v_discount_amount = LEAST(v_discount_value, v_max_discount);
        ELSE
            SET v_discount_amount = 0;
    END CASE;

    SELECT
        v_promo_id AS promotion_id,
        p_promo_code AS promo_code,
        v_type AS promotion_type,
        v_discount_amount AS discount_amount,
        p_order_amount - v_discount_amount AS after_discount,
        'VALID' AS validation_result;
END//


-- ============================================================
-- 33. 三单匹配
-- 匹配条件: 采购单数量=入库单数量=发票数量 且 单价一致
-- 调用关系: 被财务审核流程调用
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:erp_system.sp_three_way_matching
CREATE PROCEDURE sp_three_way_matching(
    IN p_invoice_id BIGINT UNSIGNED,
    IN p_purchase_order_id BIGINT UNSIGNED,
    IN p_purchase_receipt_id BIGINT UNSIGNED,
    IN p_matched_by BIGINT UNSIGNED
)
BEGIN
    DECLARE v_done INT DEFAULT FALSE;
    DECLARE v_product_id BIGINT;
    DECLARE v_po_qty INT;
    DECLARE v_po_price DECIMAL(12,2);
    DECLARE v_receipt_qty INT;
    DECLARE v_receipt_price DECIMAL(12,2);
    DECLARE v_invoice_qty INT;
    DECLARE v_invoice_price DECIMAL(12,2);
    DECLARE v_qty_match BOOLEAN;
    DECLARE v_price_match BOOLEAN;
    DECLARE v_match_status VARCHAR(30);
    DECLARE v_all_matched BOOLEAN DEFAULT TRUE;

    -- 采购单明细
    DECLARE cur_po CURSOR FOR
        SELECT product_id, quantity, unit_price FROM purchase_order_items WHERE order_id = p_purchase_order_id;

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = TRUE;

    START TRANSACTION;

    OPEN cur_po;

    po_loop: LOOP
        FETCH cur_po INTO v_product_id, v_po_qty, v_po_price;
        IF v_done THEN LEAVE po_loop; END IF;

        -- 获取入库数量
        SELECT COALESCE(SUM(accepted_qty), 0), COALESCE(AVG(unit_price), 0)
        INTO v_receipt_qty, v_receipt_price
        FROM purchase_receipt_items
        WHERE product_id = v_product_id AND receipt_id = p_purchase_receipt_id;

        -- 获取发票数量
        SET v_invoice_qty = v_po_qty;  -- 简化: 发票数量=采购数量
        SET v_invoice_price = v_po_price;

        SET v_qty_match = (v_po_qty = v_receipt_qty AND v_receipt_qty = v_invoice_qty);
        SET v_price_match = (v_po_price = v_receipt_price AND v_receipt_price = v_invoice_price);

        IF v_qty_match AND v_price_match THEN
            SET v_match_status = 'matched';
        ELSEIF NOT v_qty_match AND NOT v_price_match THEN
            SET v_match_status = 'both_mismatch';
            SET v_all_matched = FALSE;
        ELSEIF NOT v_qty_match THEN
            SET v_match_status = 'quantity_mismatch';
            SET v_all_matched = FALSE;
        ELSE
            SET v_match_status = 'price_mismatch';
            SET v_all_matched = FALSE;
        END IF;

        INSERT INTO three_way_matching (invoice_id, purchase_order_id, purchase_receipt_id,
            product_id, po_quantity, receipt_quantity, invoice_quantity,
            po_price, receipt_price, invoice_price, match_status, match_result, matched_by, matched_at)
        VALUES (p_invoice_id, p_purchase_order_id, p_purchase_receipt_id,
            v_product_id, v_po_qty, v_receipt_qty, v_invoice_qty,
            v_po_price, v_receipt_price, v_invoice_price,
            v_match_status,
            CASE
                WHEN v_qty_match AND v_price_match THEN '三单匹配成功'
                ELSE CONCAT('差异: 采购(', v_po_qty, ',', v_po_price, ') 入库(', v_receipt_qty, ',', v_receipt_price, ') 发票(', v_invoice_qty, ',', v_invoice_price, ')')
            END,
            p_matched_by, NOW());

    END LOOP;

    CLOSE cur_po;

    -- 更新发票状态
    IF v_all_matched THEN
        UPDATE invoices SET status = 'matched' WHERE id = p_invoice_id;
    ELSE
        UPDATE invoices SET status = 'disputed' WHERE id = p_invoice_id;
    END IF;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('three_way_matching', 'invoice', p_invoice_id, p_matched_by,
            JSON_OBJECT('result', IF(v_all_matched, 'matched', 'disputed')));

    COMMIT;

    SELECT IF(v_all_matched, '三单匹配成功', '三单匹配存在差异') AS result;
END//


-- ============================================================
-- 34. 固定资产折旧
-- 调用关系: 被月度结算流程调用
-- 折旧原理: 直线法，月折旧额 = (原值 - 残值) / 使用月数
-- 分录: 借: 管理费用/制造费用  贷: 累计折旧
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:erp_system.sp_depreciate_assets
CREATE PROCEDURE sp_depreciate_assets(
    IN p_depreciation_date DATE,
    IN p_processed_by BIGINT UNSIGNED
)
BEGIN
    DECLARE v_done INT DEFAULT FALSE;
    DECLARE v_asset_id BIGINT;
    DECLARE v_amount DECIMAL(12,2);
    DECLARE v_before_acc DECIMAL(18,2);
    DECLARE v_before_net DECIMAL(18,2);

    DECLARE cur CURSOR FOR
        SELECT id, monthly_depreciation, accumulated_depreciation, net_book_value
        FROM fixed_assets
        WHERE status IN ('in_use', 'idle')
          AND (last_depreciation_date IS NULL OR last_depreciation_date < p_depreciation_date);

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = TRUE;

    START TRANSACTION;

    OPEN cur;

    dep_loop: LOOP
        FETCH cur INTO v_asset_id, v_amount, v_before_acc, v_before_net;
        IF v_done THEN LEAVE dep_loop; END IF;

        INSERT INTO depreciation_log (asset_id, depreciation_date, depreciation_amount,
            before_accumulated, after_accumulated, before_net_value, after_net_value)
        VALUES (v_asset_id, p_depreciation_date, v_amount,
            v_before_acc, v_before_acc + v_amount,
            v_before_net, v_before_net - v_amount);

        UPDATE fixed_assets
        SET accumulated_depreciation = accumulated_depreciation + v_amount,
            last_depreciation_date = p_depreciation_date
        WHERE id = v_asset_id;

    END LOOP;

    CLOSE cur;

    INSERT INTO audit_log (action, target_type, employee_id, new_value)
    VALUES ('depreciate_assets', 'fixed_asset', p_processed_by,
            JSON_OBJECT('depreciation_date', p_depreciation_date));

    COMMIT;

    SELECT CONCAT('折旧计提完成: ', p_depreciation_date) AS result;
END//


-- ============================================================
-- 35. 工单发料
-- 从库存中扣减原料，发放到工单
-- 调用关系: 被生产管理流程调用
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:erp_system.sp_issue_work_order_materials
CREATE PROCEDURE sp_issue_work_order_materials(
    IN p_work_order_id BIGINT UNSIGNED,
    IN p_issued_by BIGINT UNSIGNED
)
BEGIN
    DECLARE v_done INT DEFAULT FALSE;
    DECLARE v_material_id BIGINT;
    DECLARE v_product_id BIGINT;
    DECLARE v_batch_id BIGINT;
    DECLARE v_required_qty DECIMAL(10,3);
    DECLARE v_issued_qty DECIMAL(10,3);
    DECLARE v_available INT;
    DECLARE v_before_qty INT;
    DECLARE v_warehouse_id BIGINT;
    DECLARE v_issue_qty DECIMAL(10,3);

    DECLARE cur CURSOR FOR
        SELECT id, product_id, batch_id, required_qty, issued_qty
        FROM work_order_materials
        WHERE work_order_id = p_work_order_id AND status IN ('pending', 'partial');

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = TRUE;

    SELECT warehouse_id INTO v_warehouse_id FROM work_orders WHERE id = p_work_order_id;

    START TRANSACTION;

    OPEN cur;

    issue_loop: LOOP
        FETCH cur INTO v_material_id, v_product_id, v_batch_id, v_required_qty, v_issued_qty;
        IF v_done THEN LEAVE issue_loop; END IF;

        SET v_issue_qty = v_required_qty - v_issued_qty;

        SELECT COALESCE(available_quantity, 0) INTO v_available
        FROM inventory
        WHERE product_id = v_product_id AND warehouse_id = v_warehouse_id;

        IF v_available >= v_issue_qty THEN
            SELECT quantity INTO v_before_qty
            FROM inventory
            WHERE product_id = v_product_id AND warehouse_id = v_warehouse_id;

            UPDATE inventory SET quantity = quantity - v_issue_qty
            WHERE product_id = v_product_id AND warehouse_id = v_warehouse_id;

            INSERT INTO inventory_transactions (product_id, batch_id, warehouse_id,
                transaction_type, quantity_change, before_qty, after_qty,
                reference_type, reference_id, operator_id, remark)
            VALUES (v_product_id, v_batch_id, v_warehouse_id, 'production_in',
                -v_issue_qty, v_before_qty, v_before_qty - v_issue_qty,
                'work_order', p_work_order_id, p_issued_by,
                CONCAT('工单发料: work_order_id=', p_work_order_id));

            UPDATE work_order_materials
            SET issued_qty = issued_qty + v_issue_qty,
                status = 'issued'
            WHERE id = v_material_id;
        ELSE
            UPDATE work_order_materials
            SET issued_qty = issued_qty + v_available,
                status = 'partial'
            WHERE id = v_material_id;
        END IF;
    END LOOP;

    CLOSE cur;

    -- 更新工单状态
    IF NOT EXISTS (SELECT 1 FROM work_order_materials WHERE work_order_id = p_work_order_id AND status IN ('pending', 'partial')) THEN
        UPDATE work_orders SET status = 'in_progress' WHERE id = p_work_order_id;
    END IF;

    COMMIT;

    SELECT '工单发料完成' AS result;
END//


-- ============================================================
-- 36. 客服工单分配
-- 根据工单类型和优先级自动分配处理人
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:erp_system.sp_assign_service_ticket
CREATE PROCEDURE sp_assign_service_ticket(
    IN p_ticket_id BIGINT UNSIGNED,
    IN p_assigned_to BIGINT UNSIGNED
)
BEGIN
    DECLARE v_current_status VARCHAR(20);

    SELECT status INTO v_current_status FROM service_tickets WHERE id = p_ticket_id;

    IF v_current_status != 'open' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '只能分配状态为open的工单';
    END IF;

    UPDATE service_tickets
    SET assigned_to = p_assigned_to,
        status = 'processing'
    WHERE id = p_ticket_id;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('assign_ticket', 'service_ticket', p_ticket_id, p_assigned_to,
            JSON_OBJECT('status', 'processing'));

    SELECT ROW_COUNT() > 0 AS success;
END//


-- ============================================================
-- 37. 批量生成完整业务数据
-- 调用关系: 一键生成考勤+销售+采购+发货数据
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:erp_system.sp_generate_all_business_data
CREATE PROCEDURE sp_generate_all_business_data(
    IN p_days INT
)
BEGIN
    DECLARE v_done INT DEFAULT FALSE;
    DECLARE v_emp_id BIGINT;
    DECLARE v_day INT DEFAULT 0;
    DECLARE v_att_date DATE;
    DECLARE v_day_of_week INT;
    DECLARE v_clock_in TIME;
    DECLARE v_clock_out TIME;
    DECLARE v_status VARCHAR(20);
    DECLARE v_late_min INT;
    DECLARE v_rand_val DECIMAL(5,4);
    DECLARE v_days_in_month INT;
    DECLARE v_target_month VARCHAR(7);

    DECLARE cur CURSOR FOR
        SELECT id FROM employees WHERE status IN ('active', 'probation');

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = TRUE;

    -- 生成考勤数据
    SET v_target_month = DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 MONTH), '%Y-%m');
    SET v_days_in_month = DAY(LAST_DAY(CONCAT(v_target_month, '-01')));

    OPEN cur;

    emp_loop: LOOP
        FETCH cur INTO v_emp_id;
        IF v_done THEN LEAVE emp_loop; END IF;

        SET v_day = 1;
        day_loop: WHILE v_day <= v_days_in_month DO
            SET v_att_date = STR_TO_DATE(CONCAT(v_target_month, '-', LPAD(v_day, 2, '0')), '%Y-%m-%d');
            SET v_day_of_week = DAYOFWEEK(v_att_date);

            IF v_day_of_week IN (1, 7) THEN
                SET v_day = v_day + 1;
                ITERATE day_loop;
            END IF;

            SET v_rand_val = RAND();

            IF v_rand_val < 0.04 THEN
                SET v_status = 'absent';
                SET v_clock_in = NULL;
                SET v_clock_out = NULL;
                SET v_late_min = 0;
            ELSEIF v_rand_val < 0.12 THEN
                SET v_status = 'late';
                SET v_late_min = FLOOR(RAND() * 45) + 1;
                SET v_clock_in = ADDTIME('08:00:00', SEC_TO_TIME(v_late_min * 60));
                SET v_clock_out = '17:30:00';
            ELSEIF v_rand_val < 0.16 THEN
                SET v_status = 'early';
                SET v_late_min = 0;
                SET v_clock_in = '08:00:00';
                SET v_clock_out = SUBTIME('17:00:00', SEC_TO_TIME((FLOOR(RAND() * 30) + 1) * 60));
            ELSE
                SET v_status = 'normal';
                SET v_late_min = 0;
                SET v_clock_in = ADDTIME('08:00:00', SEC_TO_TIME(FLOOR(RAND() * 8) * 60));
                SET v_clock_out = ADDTIME('17:00:00', SEC_TO_TIME(FLOOR(RAND() * 30) * 60));
            END IF;

            INSERT INTO attendance (employee_id, attendance_date, clock_in, clock_out, status, late_minutes)
            VALUES (v_emp_id, v_att_date,
                IF(v_clock_in IS NULL, NULL, TIMESTAMP(v_att_date, v_clock_in)),
                IF(v_clock_out IS NULL, NULL, TIMESTAMP(v_att_date, v_clock_out)),
                v_status, v_late_min)
            ON DUPLICATE KEY UPDATE
                clock_in = VALUES(clock_in), clock_out = VALUES(clock_out),
                status = VALUES(status), late_minutes = VALUES(late_minutes);

            SET v_day = v_day + 1;
        END WHILE;
    END LOOP;

    CLOSE cur;

    -- 调用销售和采购数据生成
    CALL sp_generate_sales_data(p_days);
    CALL sp_generate_purchase_data(p_days);

    SELECT CONCAT('业务数据生成完成: 考勤(', v_target_month, ') + 销售(', p_days, '天) + 采购(', p_days, '天)') AS result;
END
-- relation-detector-fixture-end
