-- ============================================================
-- 第六批: 退货退款全流程 + 报损报废 + 财务影响追踪
-- 流程覆盖:
--   销售退货: 客户申请 -> 审批 -> 收货验货 -> 退款 -> 财务记账
--   采购退货: 发现异常 -> 申请 -> 审批 -> 出库退回供应商 -> 供应商退款
--   报损报废: 发现 -> 申请 -> 审批 -> 执行 -> 财务记账
-- 财务影响:
--   销售退货: 借:主营业务收入 贷:应收账款/银行存款 (冲减收入)
--            借:库存商品 贷:主营业务成本 (恢复库存)
--   采购退货: 借:应付账款 贷:库存商品 (减少库存和应付)
--   报损报废: 借:管理费用-报损损失 贷:库存商品
-- ============================================================
-- Translated from MySQL 8.0 to PostgreSQL 16
-- Key changes:
--   - SIGNAL SQLSTATE → RAISE EXCEPTION
--   - JSON_OBJECT → jsonb_build_object
--   - JSON_EXTRACT/JSON_UNQUOTE → jsonb ->/->>
--   - JSON_LENGTH → jsonb_array_length
--   - RAND() → RANDOM()
--   - CURDATE() → CURRENT_DATE, NOW() → CURRENT_TIMESTAMP
--   - DATE_FORMAT → TO_CHAR
--   - LPAD → lpad
--   - MySQL auto-increment id retrieval → RETURNING id INTO var
--   - GROUP_CONCAT → string_agg
--   - IF(cond, a, b) → CASE WHEN cond THEN a ELSE b END
--   - MySQL upsert idiom → handled via IS NOT DISTINCT FROM check
--   - DECLARE CONTINUE HANDLER → FOR rec IN SELECT ... LOOP
--   - Procedures returning data: OUT parameters for DML procs, FUNCTION RETURNS TABLE for read-only
--   - ENUM params → TEXT
--   - BIGINT UNSIGNED → BIGINT
--   - JSON → JSONB
-- ============================================================


-- ============================================================
-- 61. 销售退货审批处理
-- 调用关系: 被售后/客服流程调用
-- 业务流程:
--   1. 审批通过 -> 状态变为approved -> 通知仓库收货
--   2. 审批拒绝 -> 状态变为rejected -> 通知客户
--   3. 审批通过后安排退货物流
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_approve_sales_return(
    IN p_return_id BIGINT,
    IN p_approved BOOLEAN,
    IN p_approver_id BIGINT,
    IN p_approval_comment VARCHAR(500),
    OUT p_result TEXT
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_old_status VARCHAR(20);
    v_order_id BIGINT;
    v_customer_id BIGINT;
BEGIN
    SELECT status, order_id, customer_id INTO v_old_status, v_order_id, v_customer_id
    FROM sales_returns WHERE id = p_return_id;

    IF v_old_status != 'pending' THEN
        RAISE EXCEPTION '只能审批待处理状态的退货单';
    END IF;

    IF p_approved THEN
        UPDATE sales_returns
        SET status = 'approved',
            approved_by = p_approver_id,
            approved_at = CURRENT_TIMESTAMP,
            remark = COALESCE(remark, '') || ' | 审批通过: ' || p_approval_comment
        WHERE id = p_return_id;

        -- 更新销售单状态
        UPDATE sales_orders SET status = 'returned' WHERE id = v_order_id;
    ELSE
        UPDATE sales_returns
        SET status = 'rejected',
            approved_by = p_approver_id,
            approved_at = CURRENT_TIMESTAMP,
            remark = COALESCE(remark, '') || ' | 审批拒绝: ' || p_approval_comment
        WHERE id = p_return_id;
    END IF;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('approve_sales_return', 'sales_return', p_return_id, p_approver_id,
            jsonb_build_object('approved', p_approved, 'comment', p_approval_comment));

    p_result := CASE WHEN p_approved THEN '退货审批通过' ELSE '退货审批拒绝' END;
END;
$$;


-- ============================================================
-- 62. 销售退货收货验货 + 退款处理
-- 业务流程:
--   1. 仓库收到退货 -> 验货(合格/不合格)
--   2. 合格品 -> 恢复库存 -> 计算退款金额
--   3. 不合格品 -> 拒收或部分退款
--   4. 退款 -> 生成财务凭证 -> 更新客户余额
-- 财务影响:
--   借: 主营业务收入 (退款金额)
--   贷: 应收账款/银行存款
--   借: 库存商品 (合格品成本)
--   贷: 主营业务成本
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_process_sales_return_refund(
    IN p_return_id BIGINT,
    IN p_inspector_id BIGINT,
    IN p_inspection_result TEXT,
    IN p_actual_refund NUMERIC(18,2),
    IN p_items_json JSONB,
    OUT p_result TEXT
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_status VARCHAR(20);
    v_order_id BIGINT;
    v_customer_id BIGINT;
    v_warehouse_id BIGINT;
    v_idx INT DEFAULT 0;
    v_item_count INT;
    v_order_item_id BIGINT;
    v_product_id BIGINT;
    v_batch_id BIGINT;
    v_accepted_qty INT;
    v_scrapped_qty INT;
    v_before_qty INT;
    v_voucher_id BIGINT;
    v_item JSONB;
BEGIN
    SELECT status, order_id, customer_id, warehouse_id
    INTO v_status, v_order_id, v_customer_id, v_warehouse_id
    FROM sales_returns WHERE id = p_return_id;

    IF v_status != 'approved' THEN
        RAISE EXCEPTION '只能处理已审批的退货单';
    END IF;

    v_item_count := jsonb_array_length(p_items_json);

    -- 验货入库
    WHILE v_idx < v_item_count LOOP
        v_item := p_items_json->v_idx;
        v_order_item_id := (v_item->>'order_item_id')::BIGINT;
        v_accepted_qty := (v_item->>'accepted_qty')::INT;
        v_scrapped_qty := (v_item->>'scrapped_qty')::INT;

        SELECT soi.product_id, soi.batch_id
        INTO v_product_id, v_batch_id
        FROM sales_order_items soi WHERE soi.id = v_order_item_id;

        -- 合格品恢复库存
        IF v_accepted_qty > 0 THEN
            SELECT COALESCE(quantity, 0) INTO v_before_qty
            FROM inventory
            WHERE product_id = v_product_id AND warehouse_id = v_warehouse_id
              AND (batch_id IS NOT DISTINCT FROM v_batch_id);

            -- PostgreSQL: MySQL upsert idiom 替代方案
            -- 使用 IS NOT DISTINCT FROM 处理 NULL 比较
            IF EXISTS (
                SELECT 1 FROM inventory
                WHERE product_id = v_product_id
                  AND warehouse_id = v_warehouse_id
                  AND batch_id IS NOT DISTINCT FROM v_batch_id
            ) THEN
                UPDATE inventory
                SET quantity = quantity + v_accepted_qty
                WHERE product_id = v_product_id
                  AND warehouse_id = v_warehouse_id
                  AND batch_id IS NOT DISTINCT FROM v_batch_id;
            ELSE
                INSERT INTO inventory (product_id, batch_id, warehouse_id, quantity)
                VALUES (v_product_id, v_batch_id, v_warehouse_id, v_accepted_qty);
            END IF;

            INSERT INTO inventory_transactions (product_id, batch_id, warehouse_id,
                transaction_type, quantity_change, before_qty, after_qty,
                reference_type, reference_id, operator_id, remark)
            VALUES (v_product_id, v_batch_id, v_warehouse_id, 'return_in',
                v_accepted_qty, v_before_qty, v_before_qty + v_accepted_qty,
                'sales_return', p_return_id, p_inspector_id,
                '退货验收合格入库: return_id=' || p_return_id);

            IF v_batch_id IS NOT NULL THEN
                UPDATE product_batches SET current_qty = current_qty + v_accepted_qty WHERE id = v_batch_id;
            END IF;

            -- 更新销售明细退货数量
            UPDATE sales_order_items
            SET returned_qty = returned_qty + v_accepted_qty
            WHERE id = v_order_item_id;
        END IF;

        -- 不合格品报废
        IF v_scrapped_qty > 0 THEN
            INSERT INTO inventory_transactions (product_id, batch_id, warehouse_id,
                transaction_type, quantity_change, before_qty, after_qty,
                reference_type, reference_id, operator_id, remark)
            VALUES (v_product_id, v_batch_id, v_warehouse_id, 'scrap_out',
                -v_scrapped_qty, 0, 0, 'sales_return', p_return_id, p_inspector_id,
                '退货验收不合格报废: return_id=' || p_return_id);
        END IF;

        v_idx := v_idx + 1;
    END LOOP;

    -- 生成退款凭证
    INSERT INTO vouchers (voucher_no, voucher_date, voucher_type,
        reference_type, reference_id, prepared_by, summary, status)
    VALUES ('V-REF-' || TO_CHAR(CURRENT_DATE, 'YYYYMMDD') || '-' || lpad(p_return_id::text, 4, '0'),
        CURRENT_DATE, 'payment', 'sales_return', p_return_id, p_inspector_id,
        '销售退货退款: return_no=' || (SELECT return_no FROM sales_returns WHERE id = p_return_id),
        'posted')
    RETURNING id INTO v_voucher_id;

    -- 退款分录: 借:主营业务收入 贷:银行存款
    INSERT INTO voucher_items (voucher_id, account_id, line_no, direction, amount, summary) VALUES
    (v_voucher_id, 15, 1, 'debit', p_actual_refund, '冲减销售收入-退货退款'),
    (v_voucher_id, 3, 2, 'credit', p_actual_refund, '银行存款退款');

    UPDATE vouchers SET total_debit = p_actual_refund, total_credit = p_actual_refund WHERE id = v_voucher_id;

    -- 更新退货单
    UPDATE sales_returns
    SET status = CASE WHEN p_inspection_result = 'rejected' THEN 'rejected' ELSE 'refunded' END,
        refund_amount = p_actual_refund,
        refund_voucher_id = v_voucher_id
    WHERE id = p_return_id;

    -- 更新客户余额
    UPDATE customers SET balance = balance - p_actual_refund WHERE id = v_customer_id;

    -- 更新销售单实收
    UPDATE sales_orders
    SET paid_amount = GREATEST(paid_amount - p_actual_refund, 0)
    WHERE id = v_order_id;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('process_return_refund', 'sales_return', p_return_id, p_inspector_id,
            jsonb_build_object('refund_amount', p_actual_refund, 'inspection', p_inspection_result,
                               'voucher_id', v_voucher_id));

    p_result := '退货退款处理完成: 退款' || p_actual_refund || '元, 凭证号:' || v_voucher_id;
END;
$$;


-- ============================================================
-- 63. 采购退货处理 (退货给供应商)
-- 业务流程:
--   1. 发现质量问题/过期 -> 创建采购退货单
--   2. 审批 -> 仓库出库退回供应商
--   3. 供应商确认退款 -> 财务记账
-- 财务影响: 借:应付账款 贷:库存商品
--   如已付款: 借:其他应收款-供应商 贷:库存商品
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_create_purchase_return(
    IN p_purchase_order_id BIGINT,
    IN p_purchase_receipt_id BIGINT,
    IN p_warehouse_id BIGINT,
    IN p_handler_id BIGINT,
    IN p_return_type TEXT,
    IN p_return_reason VARCHAR(500),
    IN p_items_json JSONB,
    OUT p_return_id BIGINT,
    OUT p_return_no TEXT,
    OUT p_total_amount NUMERIC
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_supplier_id BIGINT;
    v_total NUMERIC(18,2) DEFAULT 0.00;
    v_idx INT DEFAULT 0;
    v_item_count INT;
    v_product_id BIGINT;
    v_batch_id BIGINT;
    v_qty INT;
    v_price NUMERIC(12,2);
    v_before_qty INT;
    v_item JSONB;
BEGIN
    SELECT supplier_id INTO v_supplier_id FROM purchase_orders WHERE id = p_purchase_order_id;

    p_return_no := 'PRT-' || TO_CHAR(CURRENT_DATE, 'YYYYMMDD') || '-' || lpad((FLOOR(RANDOM() * 9999) + 1)::text, 4, '0');
    v_item_count := jsonb_array_length(p_items_json);

    INSERT INTO purchase_returns (return_no, purchase_order_id, purchase_receipt_id,
        supplier_id, warehouse_id, handler_id, return_date, return_reason, return_type, status)
    VALUES (p_return_no, p_purchase_order_id, p_purchase_receipt_id,
        v_supplier_id, p_warehouse_id, p_handler_id, CURRENT_DATE, p_return_reason, p_return_type, 'pending')
    RETURNING id INTO p_return_id;

    WHILE v_idx < v_item_count LOOP
        v_item := p_items_json->v_idx;
        v_product_id := (v_item->>'product_id')::BIGINT;
        v_batch_id := (v_item->>'batch_id')::BIGINT;
        v_qty := (v_item->>'quantity')::INT;
        v_price := (v_item->>'unit_price')::NUMERIC(12,2);

        INSERT INTO purchase_return_items (return_id, product_id, batch_id, return_qty, unit_price)
        VALUES (p_return_id, v_product_id, v_batch_id, v_qty, v_price);

        -- 出库退回供应商
        SELECT COALESCE(quantity, 0) INTO v_before_qty
        FROM inventory
        WHERE product_id = v_product_id AND warehouse_id = p_warehouse_id
          AND (batch_id IS NOT DISTINCT FROM v_batch_id);

        UPDATE inventory SET quantity = quantity - v_qty
        WHERE product_id = v_product_id AND warehouse_id = p_warehouse_id
          AND (batch_id IS NOT DISTINCT FROM v_batch_id);

        INSERT INTO inventory_transactions (product_id, batch_id, warehouse_id,
            transaction_type, quantity_change, before_qty, after_qty,
            reference_type, reference_id, operator_id, remark)
        VALUES (v_product_id, v_batch_id, p_warehouse_id, 'return_out',
            -v_qty, v_before_qty, v_before_qty - v_qty,
            'purchase_return', p_return_id, p_handler_id,
            '退货给供应商: ' || p_return_reason);

        IF v_batch_id IS NOT NULL THEN
            UPDATE product_batches SET current_qty = current_qty - v_qty WHERE id = v_batch_id;
        END IF;

        v_total := v_total + (v_qty * v_price);
        v_idx := v_idx + 1;
    END LOOP;

    UPDATE purchase_returns SET total_amount = v_total WHERE id = p_return_id;

    -- 提交审批
    CALL sp_submit_approval('PURCHASE_APPROVAL', 'purchase_return', p_return_id,
        '采购退货-' || p_return_reason, p_handler_id);

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('create_purchase_return', 'purchase_return', p_return_id, p_handler_id,
            jsonb_build_object('return_no', p_return_no, 'type', p_return_type, 'total', v_total));

    p_total_amount := v_total;
END;
$$;


-- ============================================================
-- 64. 报损报废处理
-- 业务流程:
--   1. 仓库发现损坏/过期 -> 创建报损单
--   2. 主管审批 -> 财务审批(>500元)
--   3. 执行报废 -> 库存扣减 -> 财务记账
-- 财务影响: 借:管理费用-报损损失 贷:库存商品
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_create_damage_report(
    IN p_warehouse_id BIGINT,
    IN p_report_type TEXT,
    IN p_reported_by BIGINT,
    IN p_description TEXT,
    IN p_items_json JSONB,
    OUT p_report_id BIGINT,
    OUT p_report_no TEXT,
    OUT p_total_loss_amount NUMERIC
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_total_qty INT DEFAULT 0;
    v_total_loss NUMERIC(18,2) DEFAULT 0.00;
    v_idx INT DEFAULT 0;
    v_item_count INT;
    v_product_id BIGINT;
    v_batch_id BIGINT;
    v_qty INT;
    v_cost NUMERIC(12,2);
    v_item JSONB;
BEGIN
    p_report_no := 'DMG-' || TO_CHAR(CURRENT_DATE, 'YYYYMMDD') || '-' || lpad((FLOOR(RANDOM() * 9999) + 1)::text, 4, '0');
    v_item_count := jsonb_array_length(p_items_json);

    INSERT INTO damage_reports (report_no, warehouse_id, report_type, report_date,
        reported_by, description, status)
    VALUES (p_report_no, p_warehouse_id, p_report_type, CURRENT_DATE, p_reported_by, p_description, 'pending')
    RETURNING id INTO p_report_id;

    WHILE v_idx < v_item_count LOOP
        v_item := p_items_json->v_idx;
        v_product_id := (v_item->>'product_id')::BIGINT;
        v_batch_id := (v_item->>'batch_id')::BIGINT;
        v_qty := (v_item->>'quantity')::INT;
        v_cost := (v_item->>'unit_cost')::NUMERIC(12,2);

        INSERT INTO damage_report_items (report_id, product_id, batch_id, quantity, unit_cost)
        VALUES (p_report_id, v_product_id, v_batch_id, v_qty, v_cost);

        v_total_qty := v_total_qty + v_qty;
        v_total_loss := v_total_loss + (v_qty * v_cost);
        v_idx := v_idx + 1;
    END LOOP;

    UPDATE damage_reports SET total_quantity = v_total_qty, total_loss_amount = v_total_loss WHERE id = p_report_id;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('create_damage_report', 'damage_report', p_report_id, p_reported_by,
            jsonb_build_object('report_no', p_report_no, 'type', p_report_type, 'total_loss', v_total_loss));

    p_total_loss_amount := v_total_loss;
END;
$$;


-- ============================================================
-- 65. 执行报损报废
-- 业务流程: 审批后执行报废，扣减库存，生成财务凭证
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_execute_damage_report(
    IN p_report_id BIGINT,
    IN p_executed_by BIGINT,
    OUT p_result TEXT
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_status VARCHAR(20);
    v_warehouse_id BIGINT;
    v_total_loss NUMERIC(18,2);
    v_voucher_id BIGINT;
    v_before_qty INT;
    rec RECORD;
BEGIN
    SELECT status, warehouse_id, total_loss_amount INTO v_status, v_warehouse_id, v_total_loss
    FROM damage_reports WHERE id = p_report_id;

    IF v_status != 'approved' THEN
        RAISE EXCEPTION '只能执行已审批的报损单';
    END IF;

    FOR rec IN
        SELECT product_id, batch_id, quantity FROM damage_report_items WHERE report_id = p_report_id
    LOOP
        SELECT COALESCE(quantity, 0) INTO v_before_qty
        FROM inventory
        WHERE product_id = rec.product_id AND warehouse_id = v_warehouse_id
          AND (batch_id IS NOT DISTINCT FROM rec.batch_id);

        UPDATE inventory SET quantity = quantity - rec.quantity
        WHERE product_id = rec.product_id AND warehouse_id = v_warehouse_id
          AND (batch_id IS NOT DISTINCT FROM rec.batch_id);

        INSERT INTO inventory_transactions (product_id, batch_id, warehouse_id,
            transaction_type, quantity_change, before_qty, after_qty,
            reference_type, reference_id, operator_id, remark)
        VALUES (rec.product_id, rec.batch_id, v_warehouse_id, 'scrap_out',
            -rec.quantity, v_before_qty, v_before_qty - rec.quantity,
            'damage_report', p_report_id, p_executed_by,
            '报损报废执行: report_id=' || p_report_id);

        IF rec.batch_id IS NOT NULL THEN
            UPDATE product_batches SET current_qty = current_qty - rec.quantity WHERE id = rec.batch_id;
        END IF;
    END LOOP;

    -- 财务凭证: 借:管理费用-报损损失 贷:库存商品
    INSERT INTO vouchers (voucher_no, voucher_date, voucher_type,
        reference_type, reference_id, prepared_by, summary, status)
    VALUES ('V-DMG-' || TO_CHAR(CURRENT_DATE, 'YYYYMMDD') || '-' || lpad(p_report_id::text, 4, '0'),
        CURRENT_DATE, 'journal', 'damage_report', p_report_id, p_executed_by,
        '报损报废: report_no=' || (SELECT report_no FROM damage_reports WHERE id = p_report_id),
        'posted')
    RETURNING id INTO v_voucher_id;

    INSERT INTO voucher_items (voucher_id, account_id, line_no, direction, amount, summary) VALUES
    (v_voucher_id, 19, 1, 'debit', v_total_loss, '管理费用-报损损失'),
    (v_voucher_id, 8, 2, 'credit', v_total_loss, '库存商品-报废减少');

    UPDATE vouchers SET total_debit = v_total_loss, total_credit = v_total_loss WHERE id = v_voucher_id;

    UPDATE damage_reports
    SET status = 'executed', executed_by = p_executed_by, executed_at = CURRENT_TIMESTAMP, voucher_id = v_voucher_id
    WHERE id = p_report_id;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('execute_damage_report', 'damage_report', p_report_id, p_executed_by,
            jsonb_build_object('total_loss', v_total_loss, 'voucher_id', v_voucher_id));

    p_result := '报损执行完成: 损失金额' || v_total_loss || '元, 凭证号:' || v_voucher_id;
END;
$$;


-- ============================================================
-- 66. 退货退款全流程追踪
-- 参数: p_return_id 销售退货单ID
-- 输出: 从退货申请到退款完成的完整链路
-- 注意: 转换为 FUNCTION，因为仅返回结果集，不涉及事务控制
-- ============================================================

CREATE OR REPLACE FUNCTION sp_return_full_trace(
    IN p_return_id BIGINT
)
RETURNS TABLE(
    section TEXT,
    document_no TEXT,
    doc_type TEXT,
    doc_date DATE,
    status TEXT,
    customer_name TEXT,
    original_order TEXT,
    store_name TEXT,
    return_type TEXT,
    return_reason TEXT,
    total_amount NUMERIC,
    refund_amount NUMERIC,
    restock_fee NUMERIC,
    return_shipping_fee NUMERIC,
    approval_status TEXT,
    approved_at TIMESTAMP,
    refund_status TEXT,
    financial_entries TEXT
)
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN QUERY
    SELECT
        '退货全流程追踪'::TEXT AS section,
        sr.return_no::TEXT AS document_no,
        '销售退货单'::TEXT AS doc_type,
        sr.return_date AS doc_date,
        sr.status::TEXT,
        c.name::TEXT AS customer_name,
        so.order_no::TEXT AS original_order,
        w.name::TEXT AS store_name,
        sr.return_type::TEXT,
        sr.return_reason::TEXT,
        sr.total_amount,
        sr.refund_amount,
        sr.restock_fee,
        sr.return_shipping_fee,
        CASE WHEN sr.approved_by IS NOT NULL THEN '已审批-' || e1.name ELSE '未审批' END AS approval_status,
        sr.approved_at,
        CASE WHEN sr.refund_voucher_id IS NOT NULL THEN '已退款-凭证:' || v.voucher_no ELSE '未退款' END AS refund_status,
        CASE WHEN sr.refund_voucher_id IS NOT NULL
            THEN (SELECT string_agg(vi.direction || ':' || a.name || '=' || vi.amount, '; ')
                  FROM voucher_items vi JOIN accounts a ON vi.account_id = a.id
                  WHERE vi.voucher_id = sr.refund_voucher_id)
            ELSE NULL
        END AS financial_entries
    FROM sales_returns sr
    JOIN customers c ON sr.customer_id = c.id
    JOIN sales_orders so ON sr.order_id = so.id
    JOIN warehouses w ON sr.warehouse_id = w.id
    LEFT JOIN employees e1 ON sr.approved_by = e1.id
    LEFT JOIN vouchers v ON sr.refund_voucher_id = v.id
    WHERE sr.id = p_return_id

    UNION ALL

    -- 退货明细
    SELECT
        '退货明细'::TEXT,
        p.sku::TEXT,
        '商品'::TEXT,
        NULL::DATE,
        sri.status::TEXT,
        p.name::TEXT,
        '数量:' || sri.return_qty || ', 单价:' || sri.unit_price,
        '金额:' || sri.amount,
        sri.status::TEXT AS item_status,
        CASE WHEN sri.status = 'restocked' THEN '已重新入库' WHEN sri.status = 'scrapped' THEN '已报废' ELSE '待处理' END,
        NULL::NUMERIC, NULL::NUMERIC, NULL::NUMERIC, NULL::NUMERIC,
        NULL::TEXT, NULL::TIMESTAMP, NULL::TEXT, NULL::TEXT
    FROM sales_return_items sri
    JOIN products p ON sri.product_id = p.id
    WHERE sri.return_id = p_return_id

    UNION ALL

    -- 库存变动
    SELECT
        '库存变动:' || it.transaction_type,
        p.sku::TEXT,
        '库存'::TEXT,
        it.created_at::DATE,
        NULL::TEXT,
        p.name::TEXT,
        '变动:' || it.quantity_change,
        '前:' || it.before_qty || ' -> 后:' || it.after_qty,
        NULL::TEXT, NULL::TEXT, NULL::NUMERIC, NULL::NUMERIC, NULL::NUMERIC, NULL::NUMERIC,
        NULL::TEXT, NULL::TIMESTAMP, NULL::TEXT, NULL::TEXT
    FROM inventory_transactions it
    JOIN products p ON it.product_id = p.id
    WHERE it.reference_type = 'sales_return' AND it.reference_id = p_return_id;
END;
$$;


-- ============================================================
-- 67. 退货率综合统计 (多维度)
-- 参数: p_start_date, p_end_date
-- 输出: 按门店/品类/客户/产品 多维度的退货率
-- 注意: 转换为 FUNCTION，因为仅返回结果集，不涉及事务控制
-- ============================================================

CREATE OR REPLACE FUNCTION sp_return_rate_analysis(
    IN p_start_date DATE,
    IN p_end_date DATE
)
RETURNS TABLE(
    dimension TEXT,
    category_name TEXT,
    return_type TEXT,
    return_count BIGINT,
    return_amount NUMERIC,
    total_orders BIGINT,
    return_rate_pct NUMERIC
)
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN QUERY

    -- 按品类+退货原因
    SELECT
        '品类退货率'::TEXT AS dimension,
        pc.name::TEXT AS category_name,
        sr.return_type::TEXT,
        COUNT(DISTINCT sr.id)::BIGINT AS return_count,
        SUM(sr.total_amount) AS return_amount,
        (SELECT COUNT(DISTINCT so.id)::BIGINT FROM sales_orders so
         JOIN sales_order_items soi ON so.id = soi.order_id
         JOIN products p ON soi.product_id = p.id
         WHERE p.category_id = pc.id AND so.order_date BETWEEN p_start_date AND p_end_date
           AND so.status NOT IN ('draft', 'cancelled')
        ) AS total_orders,
        ROUND(SUM(sr.total_amount) * 100.0 / NULLIF(
            (SELECT SUM(soi.amount) FROM sales_order_items soi
             JOIN sales_orders so ON soi.order_id = so.id
             JOIN products p ON soi.product_id = p.id
             WHERE p.category_id = pc.id AND so.order_date BETWEEN p_start_date AND p_end_date
               AND so.status NOT IN ('draft', 'cancelled')), 0
        ), 2) AS return_rate_pct
    FROM sales_returns sr
    JOIN sales_return_items sri ON sr.id = sri.return_id
    JOIN products p ON sri.product_id = p.id
    JOIN product_categories pc ON p.category_id = pc.id
    WHERE sr.return_date BETWEEN p_start_date AND p_end_date
    GROUP BY pc.name, sr.return_type

    UNION ALL

    -- 按门店
    SELECT
        '门店退货率: ' || w.name,
        w.name::TEXT,
        NULL::TEXT,
        COUNT(DISTINCT sr.id)::BIGINT,
        SUM(sr.total_amount),
        (SELECT COUNT(DISTINCT so.id)::BIGINT FROM sales_orders so WHERE so.warehouse_id = w.id
         AND so.order_date BETWEEN p_start_date AND p_end_date AND so.status NOT IN ('draft', 'cancelled')),
        ROUND(SUM(sr.total_amount) * 100.0 / NULLIF(
            (SELECT SUM(so.total_amount) FROM sales_orders so WHERE so.warehouse_id = w.id
             AND so.order_date BETWEEN p_start_date AND p_end_date AND so.status NOT IN ('draft', 'cancelled')), 0
        ), 2)
    FROM sales_returns sr
    JOIN warehouses w ON sr.warehouse_id = w.id
    WHERE sr.return_date BETWEEN p_start_date AND p_end_date
    GROUP BY w.name

    UNION ALL

    -- 按客户
    SELECT
        '客户退货率: ' || c.name,
        c.name::TEXT,
        NULL::TEXT,
        COUNT(DISTINCT sr.id)::BIGINT,
        SUM(sr.total_amount),
        (SELECT COUNT(DISTINCT so.id)::BIGINT FROM sales_orders so WHERE so.customer_id = c.id
         AND so.order_date BETWEEN p_start_date AND p_end_date AND so.status NOT IN ('draft', 'cancelled')),
        ROUND(SUM(sr.total_amount) * 100.0 / NULLIF(
            (SELECT SUM(so.total_amount) FROM sales_orders so WHERE so.customer_id = c.id
             AND so.order_date BETWEEN p_start_date AND p_end_date AND so.status NOT IN ('draft', 'cancelled')), 0
        ), 2)
    FROM sales_returns sr
    JOIN customers c ON sr.customer_id = c.id
    WHERE sr.return_date BETWEEN p_start_date AND p_end_date
    GROUP BY c.name
    HAVING COUNT(DISTINCT sr.id) >= 2;
END;
$$;


-- ============================================================
-- 68. 退货退款财务影响汇总
-- 参数: p_start_date, p_end_date
-- 输出: 退货对收入/成本/库存/应收款的影响
-- 统计原理:
--   收入冲减 = 退款金额合计
--   成本恢复 = 退货入库商品成本合计
--   净损失 = 收入冲减 - 成本恢复 + 报废损失 + 运费
-- 注意: 转换为 FUNCTION，因为仅返回结果集，不涉及事务控制
-- ============================================================

CREATE OR REPLACE FUNCTION sp_return_financial_impact(
    IN p_start_date DATE,
    IN p_end_date DATE
)
RETURNS TABLE(
    period_start DATE,
    period_end DATE,
    sales_return_orders BIGINT,
    sales_return_amount NUMERIC,
    actual_refund NUMERIC,
    cost_recovered_to_inventory NUMERIC,
    scrapped_cost NUMERIC,
    handling_fees NUMERIC,
    net_loss_from_sales_returns NUMERIC,
    purchase_return_orders BIGINT,
    purchase_return_amount NUMERIC,
    refund_received_from_supplier NUMERIC,
    damage_reports BIGINT,
    total_damage_loss NUMERIC,
    total_net_financial_impact NUMERIC
)
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN QUERY
    WITH sales_return_impact AS (
        SELECT
            COUNT(DISTINCT sr.id) AS return_order_count,
            SUM(sr.total_amount) AS total_return_amount,
            SUM(sr.refund_amount) AS total_refund_amount,
            SUM(sr.restock_fee) AS total_restock_fee,
            SUM(sr.return_shipping_fee) AS total_shipping_fee,
            SUM(sri.return_qty * p.purchase_price) AS total_cost_recovered,
            SUM(CASE WHEN sri.status = 'scrapped' THEN sri.return_qty * p.purchase_price ELSE 0 END) AS scrapped_cost
        FROM sales_returns sr
        JOIN sales_return_items sri ON sr.id = sri.return_id
        JOIN products p ON sri.product_id = p.id
        WHERE sr.return_date BETWEEN p_start_date AND p_end_date
    ),
    purchase_return_impact AS (
        SELECT
            COUNT(DISTINCT pr.id) AS return_count,
            SUM(pr.total_amount) AS total_return_amount,
            SUM(pr.refund_received) AS total_refund_received
        FROM purchase_returns pr
        WHERE pr.return_date BETWEEN p_start_date AND p_end_date
          AND pr.status IN ('returned', 'refunded')
    ),
    damage_impact AS (
        SELECT
            COUNT(DISTINCT dr.id) AS report_count,
            SUM(dr.total_loss_amount) AS total_loss
        FROM damage_reports dr
        WHERE dr.report_date BETWEEN p_start_date AND p_end_date
          AND dr.status = 'executed'
    )
    SELECT
        p_start_date AS period_start,
        p_end_date AS period_end,
        -- 销售退货
        COALESCE(sri.return_order_count, 0)::BIGINT AS sales_return_orders,
        COALESCE(sri.total_return_amount, 0) AS sales_return_amount,
        COALESCE(sri.total_refund_amount, 0) AS actual_refund,
        COALESCE(sri.total_cost_recovered, 0) AS cost_recovered_to_inventory,
        COALESCE(sri.scrapped_cost, 0) AS scrapped_cost,
        COALESCE(sri.total_restock_fee, 0) + COALESCE(sri.total_shipping_fee, 0) AS handling_fees,
        -- 净损失 = 退款 - 成本恢复 + 报废 + 手续费
        COALESCE(sri.total_refund_amount, 0) - COALESCE(sri.total_cost_recovered, 0)
            + COALESCE(sri.scrapped_cost, 0) + COALESCE(sri.total_restock_fee, 0)
            + COALESCE(sri.total_shipping_fee, 0) AS net_loss_from_sales_returns,
        -- 采购退货
        COALESCE(pri.return_count, 0)::BIGINT AS purchase_return_orders,
        COALESCE(pri.total_return_amount, 0) AS purchase_return_amount,
        COALESCE(pri.total_refund_received, 0) AS refund_received_from_supplier,
        -- 报损
        COALESCE(di.report_count, 0)::BIGINT AS damage_reports,
        COALESCE(di.total_loss, 0) AS total_damage_loss,
        -- 综合净影响
        (COALESCE(sri.total_refund_amount, 0) - COALESCE(sri.total_cost_recovered, 0)
            + COALESCE(sri.scrapped_cost, 0) + COALESCE(sri.total_restock_fee, 0)
            + COALESCE(sri.total_shipping_fee, 0))
        + COALESCE(di.total_loss, 0)
        - COALESCE(pri.total_refund_received, 0) AS total_net_financial_impact
    FROM (SELECT 1 AS dummy) dummy
    LEFT JOIN sales_return_impact sri ON TRUE
    LEFT JOIN purchase_return_impact pri ON TRUE
    LEFT JOIN damage_impact di ON TRUE;
END;
$$;
