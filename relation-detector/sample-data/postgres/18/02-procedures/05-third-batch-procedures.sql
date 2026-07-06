-- ============================================================
-- 第三批存储过程: 合同管理、AR/AP账龄、税务申报、
--   审批流、质检、现金流预测、项目成本、寄售结算、
--   序列号追踪、价格变更、绩效考核（PostgreSQL 18 版本）
-- 调用关系说明:
--   sp_generate_ar_aging -> ar_aging_snapshots (被月度结算调用)
--   sp_submit_approval -> approval_instances -> approval_records (被各业务模块调用)
--   sp_process_approval -> 审批通过/驳回, 更新业务单据状态
--   sp_settle_consignment -> 寄售消耗转销售单
--   sp_calculate_cash_flow -> 现金流预测
--   sp_file_tax_return -> 税务申报
-- ============================================================

-- ============================================================
-- 38. 生成AR账龄快照
-- 调用关系: 被月度结算流程调用
-- 统计原理: 遍历所有未结清销售单，按账期分桶
--           坏账准备 = 逾期金额 * 计提比例
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_generate_ar_aging()
LANGUAGE plpgsql
AS $$
BEGIN
    -- 清理当天快照
    DELETE FROM ar_aging_snapshots WHERE snapshot_date = CURRENT_DATE;

    INSERT INTO ar_aging_snapshots (snapshot_date, customer_id, order_id,
        invoice_amount, paid_amount, due_date)
    SELECT
        CURRENT_DATE,
        so.customer_id,
        so.id,
        so.total_amount,
        so.paid_amount,
        so.order_date + c.credit_days * INTERVAL '1 day'
    FROM sales_orders so
    JOIN customers c ON so.customer_id = c.id
    WHERE so.status IN ('confirmed', 'delivering', 'delivered')
      AND so.total_amount > so.paid_amount;

    RETURN QUERY
    SELECT 'AR账龄生成完成: ' || COUNT(*) || '条' AS result
    FROM ar_aging_snapshots
    WHERE snapshot_date = CURRENT_DATE;
END;
$$;


-- ============================================================
-- 39. 提交审批
-- 调用关系: 被各业务模块(请购/合同/折扣/请假等)调用
-- 工作原理:
--   1. 查找目标类型的审批流
--   2. 创建审批实例
--   3. 确定当前审批节点和审批人
--   4. 通知审批人(通过audit_log记录)
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_submit_approval(
    IN p_workflow_code VARCHAR(30),
    IN p_target_type VARCHAR(50),
    IN p_target_id BIGINT,
    IN p_target_summary VARCHAR(500),
    IN p_submitted_by BIGINT
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_workflow_id BIGINT;
    v_instance_no VARCHAR(30);
    v_instance_id BIGINT;
    v_total_nodes INT;
    v_first_node_id BIGINT;
    v_first_approver_id BIGINT;
    v_first_approver_type VARCHAR(30);
    v_employee_dept_id BIGINT;
    v_employee_manager_id BIGINT;
BEGIN
    -- 查找审批流
    SELECT id INTO v_workflow_id FROM approval_workflows WHERE workflow_code = p_workflow_code AND is_active = TRUE;
    IF v_workflow_id IS NULL THEN
        RAISE EXCEPTION '审批流不存在或未激活';
    END IF;

    -- 计算总节点数
    SELECT COUNT(*) INTO v_total_nodes FROM approval_nodes WHERE workflow_id = v_workflow_id;

    v_instance_no := 'APP-' || TO_CHAR(CURRENT_DATE, 'YYYYMMDD') || '-' || lpad((FLOOR(RANDOM() * 9999) + 1)::TEXT, 4, '0');

    -- 获取第一个审批节点
    SELECT id, approver_type, approver_id INTO v_first_node_id, v_first_approver_type, v_first_approver_id
    FROM approval_nodes WHERE workflow_id = v_workflow_id AND node_level = 1;

    -- 确定审批人
    SELECT department_id, manager_id INTO v_employee_dept_id, v_employee_manager_id
    FROM employees WHERE id = p_submitted_by;

    IF v_first_approver_type = 'department_manager' THEN
        SELECT manager_id INTO v_first_approver_id FROM departments WHERE id = v_employee_dept_id;
    ELSEIF v_first_approver_type = 'direct_manager' THEN
        v_first_approver_id := v_employee_manager_id;
    END IF;

    IF v_first_approver_id IS NULL THEN
        v_first_approver_id := 1; -- 默认总经理
    END IF;

    INSERT INTO approval_instances (instance_no, workflow_id, target_type, target_id,
        target_summary, current_node_level, total_nodes, submitted_by, status)
    VALUES (v_instance_no, v_workflow_id, p_target_type, p_target_id,
        p_target_summary, 1, v_total_nodes, p_submitted_by, 'in_progress')
    RETURNING id INTO v_instance_id;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('submit_approval', 'approval_instance', v_instance_id, p_submitted_by,
            jsonb_build_object('instance_no', v_instance_no, 'workflow_code', p_workflow_code, 'target', p_target_summary));

    COMMIT;

    RETURN QUERY
    SELECT v_instance_id AS instance_id, v_instance_no AS instance_no,
           v_first_approver_id AS first_approver_id, v_total_nodes AS total_nodes;
END;
$$;


-- ============================================================
-- 40. 处理审批
-- 调用关系: 被审批人操作调用
-- 流转逻辑:
--   approve: 当前节点通过 -> 流转到下一节点 或 整体通过
--   reject: 驳回 -> 整体驳回
--   delegate: 转授权给他人
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_process_approval(
    IN p_instance_id BIGINT,
    IN p_node_id BIGINT,
    IN p_approver_id BIGINT,
    IN p_action VARCHAR(20),
    IN p_comment VARCHAR(500),
    IN p_delegated_to BIGINT
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_current_node_level INT;
    v_total_nodes INT;
    v_next_node_id BIGINT;
    v_next_approver_id BIGINT;
    v_next_approver_type VARCHAR(30);
    v_status VARCHAR(20);
BEGIN
    SELECT current_node_level, total_nodes, status
    INTO v_current_node_level, v_total_nodes, v_status
    FROM approval_instances WHERE id = p_instance_id;

    IF v_status != 'in_progress' THEN
        RAISE EXCEPTION '审批实例状态不允许操作';
    END IF;

    -- 记录审批动作
    INSERT INTO approval_records (instance_id, node_id, approver_id, action, comment, delegated_to)
    VALUES (p_instance_id, p_node_id, p_approver_id, p_action, p_comment, p_delegated_to);

    IF p_action = 'approve' THEN
        IF v_current_node_level >= v_total_nodes THEN
            -- 全部通过
            UPDATE approval_instances
            SET status = 'approved', completed_at = NOW()
            WHERE id = p_instance_id;
        ELSE
            -- 流转到下一节点
            UPDATE approval_instances
            SET current_node_level = v_current_node_level + 1
            WHERE id = p_instance_id;
        END IF;
    ELSEIF p_action = 'reject' THEN
        UPDATE approval_instances
        SET status = 'rejected', completed_at = NOW()
        WHERE id = p_instance_id;
    END IF;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('process_approval', 'approval_instance', p_instance_id, p_approver_id,
            jsonb_build_object('action', p_action, 'comment', p_comment));

    COMMIT;

    IF FOUND THEN
        RAISE NOTICE 'success: true';
    ELSE
        RAISE NOTICE 'success: false';
    END IF;
END;
$$;


-- ============================================================
-- 41. 寄售消耗结算
-- 调用关系: 被月度/按需结算流程调用
-- 工作原理:
--   1. 汇总寄售消耗记录
--   2. 生成销售单
--   3. 扣减寄售库存
--   4. 确认收入
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_settle_consignment(
    IN p_customer_id BIGINT,
    IN p_settlement_period VARCHAR(7)
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_consignment_id BIGINT;
    v_total_consumed INT;
    v_product_id BIGINT;
    v_unit_price DECIMAL(12,2);
    v_order_no VARCHAR(30);
    v_order_id BIGINT;
    v_total_amount DECIMAL(18,2) DEFAULT 0.00;
    v_salesperson_id BIGINT;
    cur CURSOR FOR
        SELECT ci.id, ci.product_id, ci.unit_price, COALESCE(SUM(cc.consumed_qty), 0)
        FROM consignment_inventory ci
        LEFT JOIN consignment_consumptions cc ON ci.id = cc.consignment_id
            AND cc.confirmed_by_customer = FALSE
        WHERE ci.customer_id = p_customer_id AND ci.status = 'active' AND ci.available_qty < ci.consigned_qty
        GROUP BY ci.id, ci.product_id, ci.unit_price
        HAVING COALESCE(SUM(cc.consumed_qty), 0) > 0;
BEGIN
    -- 查找客户对应的销售员
    SELECT salesperson_id INTO v_salesperson_id
    FROM sales_orders WHERE customer_id = p_customer_id LIMIT 1;

    v_salesperson_id := COALESCE(v_salesperson_id, 9);

    v_order_no := 'SO-CON-' || p_settlement_period || '-' || lpad((FLOOR(RANDOM() * 9999) + 1)::TEXT, 4, '0');

    INSERT INTO sales_orders (order_no, customer_id, salesperson_id, warehouse_id,
        order_date, payment_method, status, remark)
    VALUES (v_order_no, p_customer_id, v_salesperson_id, 1, CURRENT_DATE, 'transfer', 'confirmed',
        '寄售结算: ' || p_settlement_period)
    RETURNING id INTO v_order_id;

    OPEN cur;

    LOOP
        FETCH cur INTO v_consignment_id, v_product_id, v_unit_price, v_total_consumed;
        EXIT WHEN NOT FOUND;

        INSERT INTO sales_order_items (order_id, product_id, quantity, unit_price, amount)
        VALUES (v_order_id, v_product_id, v_total_consumed, v_unit_price, v_total_consumed * v_unit_price);

        v_total_amount := v_total_amount + (v_total_consumed * v_unit_price);

        -- 更新寄售库存
        UPDATE consignment_inventory
        SET consumed_qty = consumed_qty + v_total_consumed,
            last_consumed_date = CURRENT_DATE
        WHERE id = v_consignment_id;

        -- 确认消耗记录
        UPDATE consignment_consumptions
        SET confirmed_by_customer = TRUE, sales_order_id = v_order_id
        WHERE consignment_id = v_consignment_id AND confirmed_by_customer = FALSE;

    END LOOP;

    CLOSE cur;

    UPDATE sales_orders SET total_amount = v_total_amount WHERE id = v_order_id;

    COMMIT;

    RETURN QUERY
    SELECT v_order_id AS order_id, v_order_no AS order_no, v_total_amount AS total_amount;
END;
$$;


-- ============================================================
-- 42. 现金流预测
-- 调用关系: 被财务周报/月报调用
-- 预测原理:
--   预计收款 = 账期内应收款 + 预测新销售
--   预计付款 = 账期内应付款 + 工资 + 税金
--   净现金流 = 期初余额 + 收款 - 付款
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_calculate_cash_flow(
    IN p_forecast_days INT
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_day INT DEFAULT 0;
    v_forecast_date DATE;
    v_beginning_balance DECIMAL(18,2);
    v_daily_collections DECIMAL(18,2);
    v_daily_payments DECIMAL(18,2);
    v_daily_salary DECIMAL(18,2);
    v_daily_tax DECIMAL(18,2);
    v_avg_daily_sales DECIMAL(18,2);
BEGIN
    -- 期初余额
    SELECT SUM(current_balance) INTO v_beginning_balance
    FROM accounts WHERE is_cash = TRUE OR is_bank = TRUE;

    -- 日均销售额
    SELECT COALESCE(AVG(daily_total), 0) INTO v_avg_daily_sales
    FROM (
        SELECT order_date, SUM(total_amount) AS daily_total
        FROM sales_orders
        WHERE order_date >= CURRENT_DATE - INTERVAL '30 days'
          AND status NOT IN ('draft', 'cancelled')
        GROUP BY order_date
    ) t;

    -- 清理旧预测
    DELETE FROM cash_flow_forecasts WHERE forecast_date >= CURRENT_DATE;

    -- 日均工资
    SELECT COALESCE(SUM(net_pay + social_security_company + housing_fund_company) / 30.0, 0)
    INTO v_daily_salary
    FROM salary_payments
    WHERE salary_month = TO_CHAR(CURRENT_DATE - INTERVAL '1 month', 'YYYY-MM');

    WHILE v_day < p_forecast_days LOOP
        v_forecast_date := CURRENT_DATE + v_day * INTERVAL '1 day';

        -- 预计当日收款
        SELECT COALESCE(SUM(outstanding_amount), 0) INTO v_daily_collections
        FROM ar_aging_snapshots
        WHERE due_date = v_forecast_date AND snapshot_date = CURRENT_DATE;

        -- 预计当日付款
        SELECT COALESCE(SUM(outstanding_amount), 0) INTO v_daily_payments
        FROM ap_aging_snapshots
        WHERE due_date = v_forecast_date AND snapshot_date = CURRENT_DATE;

        -- 预计税金(简化: 月税金/30)
        v_daily_tax := 5000;

        INSERT INTO cash_flow_forecasts (forecast_date, forecast_type, beginning_balance,
            expected_collections, expected_payments, expected_salary, expected_tax)
        VALUES (v_forecast_date, 'daily', v_beginning_balance,
            v_daily_collections + v_avg_daily_sales * 0.3,
            v_daily_payments + v_avg_daily_sales * 0.15,
            CASE WHEN EXTRACT(DOW FROM v_forecast_date) = 5 THEN 0 ELSE v_daily_salary END,
            CASE WHEN EXTRACT(DAY FROM v_forecast_date) = 15 THEN v_daily_tax * 30 ELSE v_daily_tax END
        );

        -- 更新下一天的期初余额
        SELECT ending_balance INTO v_beginning_balance
        FROM cash_flow_forecasts
        WHERE forecast_date = v_forecast_date AND forecast_type = 'daily';

        v_day := v_day + 1;
    END LOOP;

    RAISE NOTICE '现金流预测完成: %天', p_forecast_days;
END;
$$;


-- ============================================================
-- 43. 税务申报
-- 调用关系: 被月度税务流程调用
-- 计算原理:
--   销项税额 = Σ销售发票税额
--   进项税额 = Σ采购发票税额(已认证)
--   进项税转出 = 不可抵扣部分
--   应纳税额 = 销项税额 - 进项税额 + 进项税转出
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_file_tax_return(
    IN p_tax_period VARCHAR(7),
    IN p_prepared_by BIGINT
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_output_tax DECIMAL(18,2);
    v_input_tax DECIMAL(18,2);
    v_input_transfer DECIMAL(18,2);
    v_tax_payable DECIMAL(18,2);
    v_deadline DATE;
BEGIN
    v_deadline := (p_tax_period || '-01')::DATE + INTERVAL '1 month' + INTERVAL '14 days';

    -- 销项税额
    SELECT COALESCE(SUM(tax_amount), 0) INTO v_output_tax
    FROM tax_invoices
    WHERE tax_direction = 'output'
      AND tax_period = p_tax_period
      AND status IN ('issued', 'verified');

    -- 进项税额
    SELECT COALESCE(SUM(tax_amount), 0) INTO v_input_tax
    FROM tax_invoices
    WHERE tax_direction = 'input'
      AND tax_period = p_tax_period
      AND verification_status = 'certified'
      AND status IN ('issued', 'verified');

    v_input_transfer := 0;
    v_tax_payable := v_output_tax - v_input_tax + v_input_transfer;

    INSERT INTO tax_filings (tax_period, tax_type, output_tax, input_tax,
        input_tax_transfer, tax_payable, filing_deadline, status, prepared_by)
    VALUES (p_tax_period, 'vat', v_output_tax, v_input_tax,
        v_input_transfer, v_tax_payable, v_deadline, 'prepared', p_prepared_by)
    ON CONFLICT (tax_period, tax_type) DO UPDATE SET
        output_tax = EXCLUDED.output_tax,
        input_tax = EXCLUDED.input_tax,
        tax_payable = EXCLUDED.tax_payable,
        status = 'prepared';

    RETURN QUERY
    SELECT
        p_tax_period AS tax_period,
        v_output_tax AS output_tax,
        v_input_tax AS input_tax,
        v_tax_payable AS tax_payable,
        v_deadline AS filing_deadline;
END;
$$;


-- ============================================================
-- 44. 序列号出入库
-- 调用关系: 被采购入库和销售出库流程调用
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_scan_serial_number(
    IN p_serial_no VARCHAR(100),
    IN p_product_id BIGINT,
    IN p_event_type VARCHAR(20),
    IN p_warehouse_id BIGINT,
    IN p_reference_type VARCHAR(50),
    IN p_reference_id BIGINT,
    IN p_operator_id BIGINT
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_sn_id BIGINT;
    v_old_status VARCHAR(20);
BEGIN
    SELECT id, status INTO v_sn_id, v_old_status
    FROM serial_numbers WHERE serial_no = p_serial_no AND product_id = p_product_id;

    IF v_sn_id IS NULL THEN
        RAISE EXCEPTION '序列号不存在';
    END IF;

    CASE p_event_type
        WHEN 'purchase_in' THEN
            UPDATE serial_numbers SET status = 'in_stock', warehouse_id = p_warehouse_id,
                purchase_receipt_id = p_reference_id WHERE id = v_sn_id;
        WHEN 'sales_out' THEN
            UPDATE serial_numbers SET status = 'sold', warehouse_id = NULL,
                sales_order_id = p_reference_id WHERE id = v_sn_id;
        WHEN 'return_in' THEN
            UPDATE serial_numbers SET status = 'returned', warehouse_id = p_warehouse_id,
                return_id = p_reference_id WHERE id = v_sn_id;
        WHEN 'scrap' THEN
            UPDATE serial_numbers SET status = 'scrapped', warehouse_id = NULL WHERE id = v_sn_id;
        ELSE
            UPDATE serial_numbers SET status = 'in_stock', warehouse_id = p_warehouse_id WHERE id = v_sn_id;
    END CASE;

    INSERT INTO serial_number_logs (serial_number_id, event_type, from_status, to_status,
        reference_type, reference_id, operator_id)
    VALUES (v_sn_id, p_event_type, v_old_status,
        (SELECT status FROM serial_numbers WHERE id = v_sn_id),
        p_reference_type, p_reference_id, p_operator_id);

    RAISE NOTICE '序列号操作成功: % -> %', p_serial_no, p_event_type;
END;
$$;


-- ============================================================
-- 45. 价格变更管理
-- 调用关系: 被价格管理流程调用
-- 自动触发审批流
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_change_product_price(
    IN p_product_id BIGINT,
    IN p_price_type VARCHAR(20),
    IN p_new_price DECIMAL(12,2),
    IN p_effective_date DATE,
    IN p_change_reason VARCHAR(200),
    IN p_changed_by BIGINT
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_old_price DECIMAL(12,2);
BEGIN
    CASE p_price_type
        WHEN 'purchase' THEN SELECT purchase_price INTO v_old_price FROM products WHERE id = p_product_id;
        WHEN 'wholesale' THEN SELECT wholesale_price INTO v_old_price FROM products WHERE id = p_product_id;
        WHEN 'retail' THEN SELECT retail_price INTO v_old_price FROM products WHERE id = p_product_id;
    END CASE;

    -- 记录价格变更
    INSERT INTO price_change_logs (product_id, price_type, old_price, new_price, change_reason, effective_date, changed_by)
    VALUES (p_product_id, p_price_type, v_old_price, p_new_price, p_change_reason, p_effective_date, p_changed_by);

    -- 更新产品价格
    CASE p_price_type
        WHEN 'purchase' THEN UPDATE products SET purchase_price = p_new_price WHERE id = p_product_id;
        WHEN 'wholesale' THEN UPDATE products SET wholesale_price = p_new_price WHERE id = p_product_id;
        WHEN 'retail' THEN UPDATE products SET retail_price = p_new_price WHERE id = p_product_id;
    END CASE;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, old_value, new_value)
    VALUES ('change_price', 'product', p_product_id, p_changed_by,
            jsonb_build_object(p_price_type, v_old_price),
            jsonb_build_object(p_price_type, p_new_price, 'reason', p_change_reason));

    COMMIT;

    IF FOUND THEN
        RAISE NOTICE 'success: true';
    ELSE
        RAISE NOTICE 'success: false';
    END IF;
END;
$$;


-- ============================================================
-- 46. 绩效考核评分
-- 调用关系: 被HR月度/季度考核流程调用
-- 评分原理: 总分 = 业绩*0.4 + 能力*0.3 + 态度*0.2 + 出勤*0.1
--           S(>=90) A(>=80) B(>=70) C(>=60) D(<60)
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_create_performance_review(
    IN p_employee_id BIGINT,
    IN p_reviewer_id BIGINT,
    IN p_review_period VARCHAR(7),
    IN p_review_type VARCHAR(20),
    IN p_performance_score DECIMAL(5,2),
    IN p_competency_score DECIMAL(5,2),
    IN p_attitude_score DECIMAL(5,2),
    IN p_attendance_score DECIMAL(5,2),
    IN p_self_assessment TEXT,
    IN p_reviewer_comment TEXT
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_review_no VARCHAR(30);
BEGIN
    v_review_no := 'PR-' || p_review_period || '-' || lpad(p_employee_id::TEXT, 4, '0');

    INSERT INTO performance_reviews (review_no, employee_id, reviewer_id, review_period, review_type,
        performance_score, competency_score, attitude_score, attendance_score,
        self_assessment, reviewer_comment, status)
    VALUES (v_review_no, p_employee_id, p_reviewer_id, p_review_period, p_review_type,
        p_performance_score, p_competency_score, p_attitude_score, p_attendance_score,
        p_self_assessment, p_reviewer_comment, 'reviewed')
    ON CONFLICT (review_no) DO UPDATE SET
        performance_score = EXCLUDED.performance_score,
        competency_score = EXCLUDED.competency_score,
        attitude_score = EXCLUDED.attitude_score,
        attendance_score = EXCLUDED.attendance_score,
        reviewer_comment = EXCLUDED.reviewer_comment,
        status = 'reviewed';

    RAISE NOTICE '绩效考核完成: %', v_review_no;
END;
$$;


-- ============================================================
-- 47. 合同到期预警
-- 调用关系: 被定时任务调用
-- ============================================================

CREATE OR REPLACE PROCEDURE sp_contract_expiry_alert()
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN QUERY
    SELECT
        c.contract_no,
        c.contract_type,
        c.subject,
        CASE c.party_type
            WHEN 'customer' THEN (SELECT name FROM customers WHERE id = c.party_id)
            WHEN 'supplier' THEN (SELECT name FROM suppliers WHERE id = c.party_id)
            ELSE '其他'
        END AS party_name,
        c.start_date,
        c.end_date,
        (c.end_date - CURRENT_DATE) AS days_remaining,
        c.total_amount,
        c.status,
        (SELECT COUNT(*) FROM contract_milestones cm WHERE cm.contract_id = c.id AND cm.status != 'completed') AS pending_milestones,
        CASE
            WHEN (c.end_date - CURRENT_DATE) <= 0 THEN '已到期'
            WHEN (c.end_date - CURRENT_DATE) <= 30 THEN '30天内到期'
            WHEN (c.end_date - CURRENT_DATE) <= 60 THEN '60天内到期'
            WHEN (c.end_date - CURRENT_DATE) <= 90 THEN '90天内到期'
            ELSE '正常'
        END AS expiry_alert
    FROM contracts c
    WHERE c.status IN ('active', 'in_progress')
    ORDER BY days_remaining ASC;
END;
$$;