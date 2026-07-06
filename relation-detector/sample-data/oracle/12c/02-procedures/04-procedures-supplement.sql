-- ============================================================
-- ERP系统补充存储过程（Oracle 12c 版本）
-- 发货管理、提成计算、促销验证、
--   三单匹配、固定资产折旧、工单管理、工单发料
-- ============================================================

-- ============================================================
-- 29. 创建发货单
-- 流程: 确认销售单 -> 分配拣货员 -> 拣货 -> 打包 -> 发货
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_create_shipment(
    p_order_id IN NUMBER,
    p_carrier IN VARCHAR2,
    p_shipping_method IN VARCHAR2,
    p_shipping_fee IN NUMBER,
    p_to_address IN VARCHAR2,
    p_receiver_name IN VARCHAR2,
    p_receiver_phone IN VARCHAR2
)
AS
    v_shipment_no VARCHAR2(30);
    v_warehouse_id NUMBER(19);
    v_order_status VARCHAR2(20);
    v_shipment_id NUMBER(19);
BEGIN
    SELECT status, warehouse_id INTO v_order_status, v_warehouse_id
    FROM sales_orders WHERE id = p_order_id;

    IF v_order_status NOT IN ('confirmed', 'delivering') THEN
        RAISE_APPLICATION_ERROR(-20000, '销售单状态不允许发货');
    END IF;

    v_shipment_no := ('SH-' || TO_CHAR(CURRENT_DATE, 'YYYYMMDD') || '-' || lpad((FLOOR(DBMS_RANDOM.VALUE * 9999) + 1), 4, '0'));

    INSERT INTO shipments (shipment_no, order_id, warehouse_id, carrier,
        shipping_method, shipping_fee, to_address, receiver_name, receiver_phone,
        status, estimated_delivery_date)
    VALUES (v_shipment_no, p_order_id, v_warehouse_id, p_carrier,
        p_shipping_method, p_shipping_fee, p_to_address, p_receiver_name, p_receiver_phone,
        'pending', CURRENT_DATE + INTERVAL '3' DAY)
    RETURNING id INTO v_shipment_id;

    UPDATE sales_orders SET status = 'delivering' WHERE id = p_order_id;
END;
/


-- ============================================================
-- 30. 拣货打包流程
-- 分配拣货员和打包员，更新发货状态
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_pick_and_pack(
    p_shipment_id IN NUMBER,
    p_picker_id IN NUMBER,
    p_packer_id IN NUMBER
)
AS
BEGIN
    UPDATE shipments
    SET picker_id = p_picker_id,
        packer_id = p_packer_id,
        status = 'packed',
        shipped_at = SYSTIMESTAMP
    WHERE id = p_shipment_id AND status = 'pending';

    -- 添加物流轨迹
    INSERT INTO shipping_tracks (shipment_id, track_time, location, status_desc, operator)
    VALUES (p_shipment_id, SYSTIMESTAMP, '仓库', '已打包完成，等待揽收', fn_employee_full_name(p_packer_id));
END;
/


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
    p_period IN VARCHAR2
)
AS
    v_start_date DATE;
    v_end_date DATE;
BEGIN
    v_start_date := TO_DATE(p_period || '-01', 'YYYY-MM-DD');
    v_end_date := LAST_DAY(v_start_date);

    -- 删除已计算的当月提成
    DELETE FROM sales_commissions WHERE period = p_period;

    -- 按订单明细计算提成
    -- relation-detector-fixture-source: ROUTINE:oracle.sp_calculate_commission
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
        SYSTIMESTAMP
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
    MERGE INTO sales_commissions sc
    USING (
        SELECT employee_id, SUM(base_amount) AS total_sales
        FROM sales_commissions
        WHERE period = p_period
        GROUP BY employee_id
        HAVING SUM(base_amount) > 300000
    ) top_sales
    ON (sc.employee_id = top_sales.employee_id AND sc.period = p_period)
    WHEN MATCHED THEN UPDATE SET
        commission_amount = sc.commission_amount + ROUND(sc.base_amount * 0.02, 2),
        bonus = sc.bonus + 5000;
-- relation-detector-fixture-end
END;
/


-- ============================================================
-- 32. 促销验证
-- 验证促销码是否可用，返回折扣金额
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_validate_promotion(
    p_promo_code IN VARCHAR2,
    p_customer_id IN NUMBER,
    p_order_amount IN NUMBER
)
AS
    v_promo_id NUMBER(19);
    v_type VARCHAR2(20);
    v_discount_value NUMBER(12,2);
    v_min_purchase NUMBER(18,2);
    v_max_discount NUMBER(12,2);
    v_usage_limit NUMBER(10);
    v_used_count NUMBER(10);
    v_status VARCHAR2(20);
    v_start_date DATE;
    v_end_date DATE;
    v_discount_amount NUMBER(12,2) DEFAULT 0.00;
BEGIN
    SELECT id, promotion_type, discount_value, min_purchase_amount, max_discount_amount,
           usage_limit, used_count, status, start_date, end_date
    INTO v_promo_id, v_type, v_discount_value, v_min_purchase, v_max_discount,
         v_usage_limit, v_used_count, v_status, v_start_date, v_end_date
    FROM promotions
    WHERE code = p_promo_code;

    IF v_promo_id IS NULL THEN
        RAISE_APPLICATION_ERROR(-20000, '促销码不存在');
    END IF;

    IF v_status != 'active' THEN
        RAISE_APPLICATION_ERROR(-20000, '促销活动未激活');
    END IF;

    IF CURRENT_DATE < v_start_date OR CURRENT_DATE > v_end_date THEN
        RAISE_APPLICATION_ERROR(-20000, '促销活动不在有效期内');
    END IF;

    IF v_usage_limit > 0 AND v_used_count >= v_usage_limit THEN
        RAISE_APPLICATION_ERROR(-20000, '促销活动已达使用上限');
    END IF;

    IF p_order_amount < v_min_purchase THEN
        RAISE_APPLICATION_ERROR(-20000, '未达到最低消费金额: %');
    END IF;

    -- 计算折扣金额
    CASE v_promotion_type
        WHEN 'discount_pct' THEN
            v_discount_amount := LEAST(ROUND(p_order_amount * v_discount_value / 100.0, 2), v_max_discount);
        WHEN 'discount_amount' THEN
            v_discount_amount := LEAST(v_discount_value, v_max_discount);
        ELSE
            v_discount_amount := 0;
    END CASE;
END;
/


-- ============================================================
-- 33. 三单匹配
-- 匹配条件: 采购单数量=入库单数量=发票数量 且 单价一致
-- 调用关系: 被财务审核流程调用
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_three_way_matching(
    p_invoice_id IN NUMBER,
    p_purchase_order_id IN NUMBER,
    p_purchase_receipt_id IN NUMBER,
    p_matched_by IN NUMBER
)
AS
    v_receipt_qty NUMBER(10);
    v_receipt_price NUMBER(12,2);
    v_invoice_qty NUMBER(10);
    v_invoice_price NUMBER(12,2);
    v_qty_match NUMBER(1);
    v_price_match NUMBER(1);
    v_match_status VARCHAR2(30);
    v_all_matched NUMBER(1) DEFAULT 1;
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
            v_all_matched := 0;
        ELSIF NOT v_qty_match THEN
            v_match_status := 'quantity_mismatch';
            v_all_matched := 0;
        ELSE
            v_match_status := 'price_mismatch';
            v_all_matched := 0;
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
                ELSE ('差异: 采购(' || rec.quantity || ',' || rec.unit_price || ') 入库(' || v_receipt_qty || ',' || v_receipt_price || ') 发票(' || v_invoice_qty || ',' || v_invoice_price || ')')
            END,
            p_matched_by, SYSTIMESTAMP);

    END LOOP;

    -- 更新发票状态
    IF v_all_matched THEN
        UPDATE invoices SET status = 'matched' WHERE id = p_invoice_id;
    ELSE
        UPDATE invoices SET status = 'disputed' WHERE id = p_invoice_id;
    END IF;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('three_way_matching', 'invoice', p_invoice_id, p_matched_by,
            JSON_OBJECT('result' VALUE CASE WHEN v_all_matched THEN 'matched' ELSE 'disputed' END));

    COMMIT;
END;
/


-- ============================================================
-- 34. 固定资产折旧
-- 调用关系: 被月度结算流程调用
-- 折旧原理: 直线法，月折旧额 = (原值 - 残值) / 使用月数
-- 分录: 借: 管理费用/制造费用  贷: 累计折旧
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_depreciate_assets(
    p_depreciation_date IN DATE,
    p_processed_by IN NUMBER
)
AS
    v_amount NUMBER(12,2);
    v_before_acc NUMBER(18,2);
    v_before_net NUMBER(18,2);
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
            JSON_OBJECT('depreciation_date' VALUE p_depreciation_date));

    COMMIT;
END;
/


-- ============================================================
-- 35. 工单发料
-- 从库存中扣减原料，发放到工单
-- 调用关系: 被生产管理流程调用
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_issue_work_order_materials(
    p_work_order_id IN NUMBER,
    p_issued_by IN NUMBER
)
AS
    v_available NUMBER(10);
    v_before_qty NUMBER(10);
    v_warehouse_id NUMBER(19);
    v_issue_qty NUMBER(10,3);
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
                ('工单发料: work_order_id=' || p_work_order_id));

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
     (SELECT 1 FROM work_order_materials WHERE work_order_id = p_work_order_id AND status IN ('pending', 'partial')) THEN
        UPDATE work_orders SET status = 'in_progress' WHERE id = p_work_order_id;
    END IF;

    COMMIT;
END;
/


-- ============================================================
-- 36. 客服工单分配
-- 根据工单类型和优先级自动分配处理人
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_assign_service_ticket(
    p_ticket_id IN NUMBER,
    p_assigned_to IN NUMBER
)
AS
    v_current_status VARCHAR2(20);
BEGIN
    SELECT status INTO v_current_status FROM service_tickets WHERE id = p_ticket_id;

    IF v_current_status != 'open' THEN
        RAISE_APPLICATION_ERROR(-20000, '只能分配状态为open的工单');
    END IF;

    UPDATE service_tickets
    SET assigned_to = p_assigned_to,
        status = 'processing'
    WHERE id = p_ticket_id;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('assign_ticket', 'service_ticket', p_ticket_id, p_assigned_to,
            JSON_OBJECT('status' VALUE 'processing'));
END;
/


-- ============================================================
-- 37. 批量生成完整业务数据
-- 调用关系: 一键生成考勤+销售+采购+发货数据
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_generate_all_business_data(
    p_days IN NUMBER
)
AS
    v_day NUMBER(10) DEFAULT 0;
    v_att_date DATE;
    v_day_of_week NUMBER(10);
    v_clock_in TIMESTAMP;
    v_clock_out TIMESTAMP;
    v_status VARCHAR2(20);
    v_late_min NUMBER(10);
    v_rand_val BINARY_DOUBLE;
    v_days_in_month NUMBER(10);
    v_target_month VARCHAR2(7);
BEGIN
    -- 生成考勤数据
    v_target_month := TO_CHAR(CURRENT_DATE - INTERVAL '1' MONTH, 'YYYY-MM');
    v_days_in_month := TO_NUMBER(TO_CHAR(LAST_DAY(TO_DATE(v_target_month || '-01', 'YYYY-MM-DD')), 'DD'));

    FOR emp_rec IN
        SELECT id FROM employees WHERE status IN ('active', 'probation')
    LOOP
        v_day := 1;
        WHILE v_day <= v_days_in_month LOOP
            v_att_date := (v_target_month || '-' || lpad(v_day, 2, '0'));
            v_day_of_week := (TRUNC(v_att_date) - TRUNC(v_att_date, 'IW') + 1);

            IF v_day_of_week IN (6, 7) THEN
                v_day := v_day + 1;
                CONTINUE;
            END IF;

            v_rand_val := DBMS_RANDOM.VALUE;

            IF v_rand_val < 0.04 THEN
                v_status := 'absent';
                v_clock_in := NULL;
                v_clock_out := NULL;
                v_late_min := 0;
            ELSIF v_rand_val < 0.12 THEN
                v_status := 'late';
                v_late_min := FLOOR(DBMS_RANDOM.VALUE * 45) + 1;
                v_clock_in := INTERVAL '8' HOUR + (v_late_min * INTERVAL '1 minute');
                v_clock_out := INTERVAL '17' HOUR + INTERVAL '30' MINUTE;
            ELSIF v_rand_val < 0.16 THEN
                v_status := 'early';
                v_late_min := 0;
                v_clock_in := INTERVAL '8' HOUR;
                v_clock_out := INTERVAL '17' HOUR - ((FLOOR(DBMS_RANDOM.VALUE * 30) + 1) * INTERVAL '1 minute');
            ELSE
                v_status := 'normal';
                v_late_min := 0;
                v_clock_in := INTERVAL '8' HOUR + (FLOOR(DBMS_RANDOM.VALUE * 8) * INTERVAL '1 minute');
                v_clock_out := INTERVAL '17' HOUR + (FLOOR(DBMS_RANDOM.VALUE * 30) * INTERVAL '1 minute');
            END IF;

            INSERT INTO attendance (employee_id, attendance_date, clock_in, clock_out, status, late_minutes)
            VALUES (emp_rec.id, v_att_date,
                CASE WHEN v_clock_in IS NULL THEN CAST(NULL AS TIMESTAMP) ELSE v_att_date + v_clock_in END,
                CASE WHEN v_clock_out IS NULL THEN CAST(NULL AS TIMESTAMP) ELSE v_att_date + v_clock_out END,
                v_status, v_late_min)
            ;

            v_day := v_day + 1;
        END LOOP;
    END LOOP;

    -- 调用销售和采购数据生成
    CALL sp_generate_sales_data(p_days);
    CALL sp_generate_purchase_data(p_days);
END;
/