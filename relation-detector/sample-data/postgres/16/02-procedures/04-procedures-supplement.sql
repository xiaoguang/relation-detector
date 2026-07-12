-- ============================================================
-- ERP系统补充存储过程（PostgreSQL 16 版本）
-- 发货管理、提成计算、促销验证、
--   三单匹配、固定资产折旧、工单管理、工单发料
-- ============================================================

-- ============================================================
-- 29. 创建发货单
-- 流程: 确认销售单 -> 分配拣货员 -> 拣货 -> 打包 -> 发货
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_create_shipment(
    IN p_order_id BIGINT,
    IN p_carrier VARCHAR(100),
    IN p_shipping_method VARCHAR(20),
    IN p_shipping_fee DECIMAL(12,2),
    IN p_to_address VARCHAR(300),
    IN p_receiver_name VARCHAR(50),
    IN p_receiver_phone VARCHAR(20),
    OUT shipment_id BIGINT,
    OUT shipment_no VARCHAR(30)
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_shipment_no VARCHAR(30);
    v_warehouse_id BIGINT;
    v_order_status VARCHAR(20);
    v_shipment_id BIGINT;
BEGIN
    SELECT status, warehouse_id INTO v_order_status, v_warehouse_id
    FROM sales_orders WHERE id = p_order_id;

    IF v_order_status NOT IN ('confirmed', 'delivering') THEN
        RAISE EXCEPTION '销售单状态不允许发货';
    END IF;

    v_shipment_no := CONCAT('SH-', TO_CHAR(CURRENT_DATE, 'YYYYMMDD'), '-', lpad((FLOOR(RANDOM() * 9999) + 1)::TEXT, 4, '0'));

    INSERT INTO shipments (shipment_no, order_id, warehouse_id, carrier,
        shipping_method, shipping_fee, to_address, receiver_name, receiver_phone,
        status, estimated_delivery_date)
    VALUES (v_shipment_no, p_order_id, v_warehouse_id, p_carrier,
        p_shipping_method, p_shipping_fee, p_to_address, p_receiver_name, p_receiver_phone,
        'pending', CURRENT_DATE + INTERVAL '3 days')
    RETURNING id INTO v_shipment_id;

    UPDATE sales_orders SET status = 'delivering' WHERE id = p_order_id;

    shipment_id := v_shipment_id;
    shipment_no := v_shipment_no;
END;
$$;


-- ============================================================
-- 30. 拣货打包流程
-- 分配拣货员和打包员，更新发货状态
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_pick_and_pack(
    IN p_shipment_id BIGINT,
    IN p_picker_id BIGINT,
    IN p_packer_id BIGINT,
    OUT success BOOLEAN
)
LANGUAGE plpgsql
AS $$
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

    success := FOUND;
END;
$$;


-- ============================================================
-- 31. 销售提成计算
-- 调用关系: 被月度结算流程调用
-- 计算原理:
--   1. 按员工分组汇总当月销售额
--   2. 匹配提成规则(产品分类+金额阶梯)
--   3. 计算提成金额 = 销售额 * 提成率 + 额外奖金
--   4. 超额奖励: 总销售额超过30万，额外2%提成+5000奖金
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_calculate_commission(
    IN p_period VARCHAR(7),
    OUT result TEXT
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_start_date DATE;
    v_end_date DATE;
BEGIN
    v_start_date := (p_period || '-01')::DATE;
    v_end_date := (date_trunc('month', v_start_date) + INTERVAL '1 month' - INTERVAL '1 day')::DATE;

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
    SET commission_amount = sc.commission_amount + ROUND(sc.base_amount * 0.02, 2),
        bonus = sc.bonus + 5000
    FROM (
        SELECT employee_id, SUM(base_amount) AS total_sales
        FROM sales_commissions
        WHERE period = p_period
        GROUP BY employee_id
        HAVING SUM(base_amount) > 300000
    ) top_sales
    WHERE sc.employee_id = top_sales.employee_id AND sc.period = p_period;

    SELECT CONCAT('提成计算完成: ', p_period, ', 共', COUNT(*), '条')
    INTO result
    FROM sales_commissions WHERE period = p_period;
END;
$$;


-- ============================================================
-- 32. 促销验证
-- 验证促销码是否可用，返回折扣金额
-- ============================================================

CREATE OR REPLACE FUNCTION sp_validate_promotion(
    IN p_promo_code VARCHAR(30),
    IN p_customer_id BIGINT,
    IN p_order_amount DECIMAL(18,2)
)
RETURNS TABLE (
    promotion_id BIGINT,
    promo_code VARCHAR(30),
    promotion_type VARCHAR(20),
    discount_amount DECIMAL(12,2),
    after_discount DECIMAL(18,2),
    validation_result TEXT
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_promo_id BIGINT;
    v_type VARCHAR(20);
    v_discount_value DECIMAL(12,2);
    v_min_purchase DECIMAL(18,2);
    v_max_discount DECIMAL(12,2);
    v_usage_limit INT;
    v_used_count INT;
    v_status VARCHAR(20);
    v_start_date DATE;
    v_end_date DATE;
    v_discount_amount DECIMAL(12,2) DEFAULT 0.00;
BEGIN
    SELECT p.id, p.promotion_type, p.discount_value, p.min_purchase_amount, p.max_discount_amount,
           p.usage_limit, p.used_count, p.status, p.start_date, p.end_date
    INTO v_promo_id, v_type, v_discount_value, v_min_purchase, v_max_discount,
         v_usage_limit, v_used_count, v_status, v_start_date, v_end_date
    FROM promotions p
    WHERE p.code = p_promo_code;

    IF v_promo_id IS NULL THEN
        RAISE EXCEPTION '促销码不存在';
    END IF;

    IF v_status != 'active' THEN
        RAISE EXCEPTION '促销活动未激活';
    END IF;

    IF CURRENT_DATE < v_start_date OR CURRENT_DATE > v_end_date THEN
        RAISE EXCEPTION '促销活动不在有效期内';
    END IF;

    IF v_usage_limit > 0 AND v_used_count >= v_usage_limit THEN
        RAISE EXCEPTION '促销活动已达使用上限';
    END IF;

    IF p_order_amount < v_min_purchase THEN
        RAISE EXCEPTION '未达到最低消费金额: %', v_min_purchase;
    END IF;

    -- 计算折扣金额
    CASE v_type
        WHEN 'discount_pct' THEN
            v_discount_amount := LEAST(ROUND(p_order_amount * v_discount_value / 100.0, 2), v_max_discount);
        WHEN 'discount_amount' THEN
            v_discount_amount := LEAST(v_discount_value, v_max_discount);
        ELSE
            v_discount_amount := 0;
    END CASE;

    RETURN QUERY
    SELECT
        v_promo_id AS promotion_id,
        p_promo_code AS promo_code,
        v_type AS promotion_type,
        v_discount_amount AS discount_amount,
        p_order_amount - v_discount_amount AS after_discount,
        'VALID'::TEXT AS validation_result;
END;
$$;


-- ============================================================
-- 33. 三单匹配
-- 匹配条件: 采购单数量=入库单数量=发票数量 且 单价一致
-- 调用关系: 被财务审核流程调用
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_three_way_matching(
    IN p_invoice_id BIGINT,
    IN p_purchase_order_id BIGINT,
    IN p_purchase_receipt_id BIGINT,
    IN p_matched_by BIGINT,
    OUT result TEXT
)
LANGUAGE plpgsql
AS $$
DECLARE
    rec RECORD;
    v_receipt_qty INT;
    v_receipt_price DECIMAL(12,2);
    v_invoice_qty INT;
    v_invoice_price DECIMAL(12,2);
    v_qty_match BOOLEAN;
    v_price_match BOOLEAN;
    v_match_status VARCHAR(30);
    v_all_matched BOOLEAN DEFAULT TRUE;
BEGIN
    FOR rec IN
        SELECT product_id, quantity, unit_price FROM purchase_order_items WHERE order_id = p_purchase_order_id
    LOOP
        -- 获取入库数量
        SELECT COALESCE(SUM(accepted_qty), 0), COALESCE(AVG(unit_price), 0)
        INTO v_receipt_qty, v_receipt_price
        FROM purchase_receipt_items
        WHERE product_id = rec.product_id AND receipt_id = p_purchase_receipt_id;

        -- 获取发票数量
        v_invoice_qty := rec.quantity;  -- 简化: 发票数量=采购数量
        v_invoice_price := rec.unit_price;

        v_qty_match := (rec.quantity = v_receipt_qty AND v_receipt_qty = v_invoice_qty);
        v_price_match := (rec.unit_price = v_receipt_price AND v_receipt_price = v_invoice_price);

        IF v_qty_match AND v_price_match THEN
            v_match_status := 'matched';
        ELSIF NOT v_qty_match AND NOT v_price_match THEN
            v_match_status := 'both_mismatch';
            v_all_matched := FALSE;
        ELSIF NOT v_qty_match THEN
            v_match_status := 'quantity_mismatch';
            v_all_matched := FALSE;
        ELSE
            v_match_status := 'price_mismatch';
            v_all_matched := FALSE;
        END IF;

        INSERT INTO three_way_matching (invoice_id, purchase_order_id, purchase_receipt_id,
            product_id, po_quantity, receipt_quantity, invoice_quantity,
            po_price, receipt_price, invoice_price, match_status, match_result, matched_by, matched_at)
        VALUES (p_invoice_id, p_purchase_order_id, p_purchase_receipt_id,
            rec.product_id, rec.quantity, v_receipt_qty, v_invoice_qty,
            rec.unit_price, v_receipt_price, v_invoice_price,
            v_match_status,
            CASE
                WHEN v_qty_match AND v_price_match THEN '三单匹配成功'
                ELSE CONCAT('差异: 采购(', rec.quantity, ',', rec.unit_price, ') 入库(', v_receipt_qty, ',', v_receipt_price, ') 发票(', v_invoice_qty, ',', v_invoice_price, ')')
            END,
            p_matched_by, NOW());

    END LOOP;

    -- 更新发票状态
    IF v_all_matched THEN
        UPDATE invoices SET status = 'matched' WHERE id = p_invoice_id;
    ELSE
        UPDATE invoices SET status = 'disputed' WHERE id = p_invoice_id;
    END IF;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('three_way_matching', 'invoice', p_invoice_id, p_matched_by,
            jsonb_build_object('result', CASE WHEN v_all_matched THEN 'matched' ELSE 'disputed' END));

    COMMIT;

    result := CASE WHEN v_all_matched THEN '三单匹配成功' ELSE '三单匹配存在差异' END;
END;
$$;


-- ============================================================
-- 34. 固定资产折旧
-- 调用关系: 被月度结算流程调用
-- 折旧原理: 直线法，月折旧额 = (原值 - 残值) / 使用月数
-- 分录: 借: 管理费用/制造费用  贷: 累计折旧
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_depreciate_assets(
    IN p_depreciation_date DATE,
    IN p_processed_by BIGINT,
    OUT result TEXT
)
LANGUAGE plpgsql
AS $$
DECLARE
    rec RECORD;
    v_amount DECIMAL(12,2);
    v_before_acc DECIMAL(18,2);
    v_before_net DECIMAL(18,2);
BEGIN
    FOR rec IN
        SELECT id, monthly_depreciation, accumulated_depreciation, net_book_value
        FROM fixed_assets
        WHERE status IN ('in_use', 'idle')
          AND (last_depreciation_date IS NULL OR last_depreciation_date < p_depreciation_date)
    LOOP
        v_amount := rec.monthly_depreciation;
        v_before_acc := rec.accumulated_depreciation;
        v_before_net := rec.net_book_value;

        INSERT INTO depreciation_log (asset_id, depreciation_date, depreciation_amount,
            before_accumulated, after_accumulated, before_net_value, after_net_value)
        VALUES (rec.id, p_depreciation_date, v_amount,
            v_before_acc, v_before_acc + v_amount,
            v_before_net, v_before_net - v_amount);

        UPDATE fixed_assets
        SET accumulated_depreciation = accumulated_depreciation + v_amount,
            last_depreciation_date = p_depreciation_date
        WHERE id = rec.id;

    END LOOP;

    INSERT INTO audit_log (action, target_type, employee_id, new_value)
    VALUES ('depreciate_assets', 'fixed_asset', p_processed_by,
            jsonb_build_object('depreciation_date', p_depreciation_date));

    COMMIT;

    result := CONCAT('折旧计提完成: ', p_depreciation_date);
END;
$$;


-- ============================================================
-- 35. 工单发料
-- 从库存中扣减原料，发放到工单
-- 调用关系: 被生产管理流程调用
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_issue_work_order_materials(
    IN p_work_order_id BIGINT,
    IN p_issued_by BIGINT,
    OUT result TEXT
)
LANGUAGE plpgsql
AS $$
DECLARE
    rec RECORD;
    v_available INT;
    v_before_qty INT;
    v_warehouse_id BIGINT;
    v_issue_qty DECIMAL(10,3);
BEGIN
    SELECT warehouse_id INTO v_warehouse_id FROM work_orders WHERE id = p_work_order_id;

    FOR rec IN
        SELECT id, product_id, batch_id, required_qty, issued_qty
        FROM work_order_materials
        WHERE work_order_id = p_work_order_id AND status IN ('pending', 'partial')
    LOOP
        v_issue_qty := rec.required_qty - rec.issued_qty;

        SELECT COALESCE(available_quantity, 0) INTO v_available
        FROM inventory
        WHERE product_id = rec.product_id AND warehouse_id = v_warehouse_id;

        IF v_available >= v_issue_qty THEN
            SELECT quantity INTO v_before_qty
            FROM inventory
            WHERE product_id = rec.product_id AND warehouse_id = v_warehouse_id;

            UPDATE inventory SET quantity = quantity - v_issue_qty
            WHERE product_id = rec.product_id AND warehouse_id = v_warehouse_id;

            INSERT INTO inventory_transactions (product_id, batch_id, warehouse_id,
                transaction_type, quantity_change, before_qty, after_qty,
                reference_type, reference_id, operator_id, remark)
            VALUES (rec.product_id, rec.batch_id, v_warehouse_id, 'production_in',
                -v_issue_qty, v_before_qty, v_before_qty - v_issue_qty,
                'work_order', p_work_order_id, p_issued_by,
                CONCAT('工单发料: work_order_id=', p_work_order_id));

            UPDATE work_order_materials
            SET issued_qty = issued_qty + v_issue_qty,
                status = 'issued'
            WHERE id = rec.id;
        ELSE
            UPDATE work_order_materials
            SET issued_qty = issued_qty + v_available,
                status = 'partial'
            WHERE id = rec.id;
        END IF;
    END LOOP;

    -- 更新工单状态
    IF NOT EXISTS (SELECT 1 FROM work_order_materials WHERE work_order_id = p_work_order_id AND status IN ('pending', 'partial')) THEN
        UPDATE work_orders SET status = 'in_progress' WHERE id = p_work_order_id;
    END IF;

    COMMIT;

    result := '工单发料完成';
END;
$$;


-- ============================================================
-- 36. 客服工单分配
-- 根据工单类型和优先级自动分配处理人
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_assign_service_ticket(
    IN p_ticket_id BIGINT,
    IN p_assigned_to BIGINT,
    OUT success BOOLEAN
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_current_status VARCHAR(20);
BEGIN
    SELECT status INTO v_current_status FROM service_tickets WHERE id = p_ticket_id;

    IF v_current_status != 'open' THEN
        RAISE EXCEPTION '只能分配状态为open的工单';
    END IF;

    UPDATE service_tickets
    SET assigned_to = p_assigned_to,
        status = 'processing'
    WHERE id = p_ticket_id;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('assign_ticket', 'service_ticket', p_ticket_id, p_assigned_to,
            jsonb_build_object('status', 'processing'));

    success := FOUND;
END;
$$;


-- ============================================================
-- 37. 批量生成完整业务数据
-- 调用关系: 一键生成考勤+销售+采购+发货数据
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_generate_all_business_data(
    IN p_days INT,
    OUT result TEXT
)
LANGUAGE plpgsql
AS $$
DECLARE
    emp_rec RECORD;
    v_day INT DEFAULT 0;
    v_att_date DATE;
    v_day_of_week INT;
    v_clock_in TIME;
    v_clock_out TIME;
    v_status VARCHAR(20);
    v_late_min INT;
    v_rand_val DOUBLE PRECISION;
    v_days_in_month INT;
    v_target_month VARCHAR(7);
BEGIN
    -- 生成考勤数据
    v_target_month := TO_CHAR(CURRENT_DATE - INTERVAL '1 month', 'YYYY-MM');
    v_days_in_month := EXTRACT(DAY FROM (date_trunc('month', (v_target_month || '-01')::DATE) + INTERVAL '1 month' - INTERVAL '1 day'))::INT;

    FOR emp_rec IN
        SELECT id FROM employees WHERE status IN ('active', 'probation')
    LOOP
        v_day := 1;
        WHILE v_day <= v_days_in_month LOOP
            v_att_date := (v_target_month || '-' || lpad(v_day::TEXT, 2, '0'))::DATE;
            v_day_of_week := EXTRACT(ISODOW FROM v_att_date);

            IF v_day_of_week IN (6, 7) THEN
                v_day := v_day + 1;
                CONTINUE;
            END IF;

            v_rand_val := RANDOM();

            IF v_rand_val < 0.04 THEN
                v_status := 'absent';
                v_clock_in := NULL;
                v_clock_out := NULL;
                v_late_min := 0;
            ELSIF v_rand_val < 0.12 THEN
                v_status := 'late';
                v_late_min := FLOOR(RANDOM() * 45) + 1;
                v_clock_in := '08:00:00'::TIME + (v_late_min * INTERVAL '1 minute');
                v_clock_out := '17:30:00'::TIME;
            ELSIF v_rand_val < 0.16 THEN
                v_status := 'early';
                v_late_min := 0;
                v_clock_in := '08:00:00'::TIME;
                v_clock_out := '17:00:00'::TIME - ((FLOOR(RANDOM() * 30) + 1) * INTERVAL '1 minute');
            ELSE
                v_status := 'normal';
                v_late_min := 0;
                v_clock_in := '08:00:00'::TIME + (FLOOR(RANDOM() * 8) * INTERVAL '1 minute');
                v_clock_out := '17:00:00'::TIME + (FLOOR(RANDOM() * 30) * INTERVAL '1 minute');
            END IF;

            INSERT INTO attendance (employee_id, attendance_date, clock_in, clock_out, status, late_minutes)
            VALUES (emp_rec.id, v_att_date,
                CASE WHEN v_clock_in IS NULL THEN NULL::TIMESTAMP ELSE v_att_date + v_clock_in END,
                CASE WHEN v_clock_out IS NULL THEN NULL::TIMESTAMP ELSE v_att_date + v_clock_out END,
                v_status, v_late_min)
            ON CONFLICT (employee_id, attendance_date) DO UPDATE
            SET clock_in = EXCLUDED.clock_in, clock_out = EXCLUDED.clock_out,
                status = EXCLUDED.status, late_minutes = EXCLUDED.late_minutes;

            v_day := v_day + 1;
        END LOOP;
    END LOOP;

    -- 调用销售和采购数据生成
    CALL sp_generate_sales_data(p_days);
    CALL sp_generate_purchase_data(p_days);

    result := CONCAT('业务数据生成完成: 考勤(', v_target_month, ') + 销售(', p_days, '天) + 采购(', p_days, '天)');
END;
$$;
