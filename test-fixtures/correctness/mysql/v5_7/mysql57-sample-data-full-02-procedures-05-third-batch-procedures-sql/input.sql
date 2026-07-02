-- relation-detector-fixture-source: ROUTINE:erp_system.sp_generate_ar_aging
CREATE PROCEDURE sp_generate_ar_aging()
BEGIN
    -- 清理当天快照
    DELETE FROM ar_aging_snapshots WHERE snapshot_date = CURDATE();

    INSERT INTO ar_aging_snapshots (snapshot_date, customer_id, order_id,
        invoice_amount, paid_amount, due_date)
    SELECT
        CURDATE(),
        so.customer_id,
        so.id,
        so.total_amount,
        so.paid_amount,
        DATE_ADD(so.order_date, INTERVAL c.credit_days DAY)
    FROM sales_orders so
    JOIN customers c ON so.customer_id = c.id
    WHERE so.status IN ('confirmed', 'delivering', 'delivered')
      AND so.total_amount > so.paid_amount;

    SELECT CONCAT('AR账龄生成完成: ', COUNT(*), '条') AS result FROM ar_aging_snapshots WHERE snapshot_date = CURDATE();
END//


-- ============================================================
-- 39. 提交审批
-- 调用关系: 被各业务模块(请购/合同/折扣/请假等)调用
-- 工作原理:
--   1. 查找目标类型的审批流
--   2. 创建审批实例
--   3. 确定当前审批节点和审批人
--   4. 通知审批人(通过audit_log记录)
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:erp_system.sp_submit_approval
CREATE PROCEDURE sp_submit_approval(
    IN p_workflow_code VARCHAR(30),
    IN p_target_type VARCHAR(50),
    IN p_target_id BIGINT,
    IN p_target_summary VARCHAR(500),
    IN p_submitted_by BIGINT UNSIGNED
)
BEGIN
    DECLARE v_workflow_id BIGINT;
    DECLARE v_instance_no VARCHAR(30);
    DECLARE v_instance_id BIGINT;
    DECLARE v_total_nodes INT;
    DECLARE v_first_node_id BIGINT;
    DECLARE v_first_approver_id BIGINT;
    DECLARE v_first_approver_type VARCHAR(30);
    DECLARE v_employee_dept_id BIGINT;
    DECLARE v_employee_manager_id BIGINT;

    -- 查找审批流
    SELECT id INTO v_workflow_id FROM approval_workflows WHERE workflow_code = p_workflow_code AND is_active = TRUE;
    IF v_workflow_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '审批流不存在或未激活';
    END IF;

    -- 计算总节点数
    SELECT COUNT(*) INTO v_total_nodes FROM approval_nodes WHERE workflow_id = v_workflow_id;

    SET v_instance_no = CONCAT('APP-', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-', LPAD(FLOOR(RAND() * 9999) + 1, 4, '0'));

    -- 获取第一个审批节点
    SELECT id, approver_type, approver_id INTO v_first_node_id, v_first_approver_type, v_first_approver_id
    FROM approval_nodes WHERE workflow_id = v_workflow_id AND node_level = 1;

    -- 确定审批人
    SELECT department_id, manager_id INTO v_employee_dept_id, v_employee_manager_id
    FROM employees WHERE id = p_submitted_by;

    IF v_first_approver_type = 'department_manager' THEN
        SELECT manager_id INTO v_first_approver_id FROM departments WHERE id = v_employee_dept_id;
    ELSEIF v_first_approver_type = 'direct_manager' THEN
        SET v_first_approver_id = v_employee_manager_id;
    END IF;

    IF v_first_approver_id IS NULL THEN
        SET v_first_approver_id = 1; -- 默认总经理
    END IF;

    START TRANSACTION;

    INSERT INTO approval_instances (instance_no, workflow_id, target_type, target_id,
        target_summary, current_node_level, total_nodes, submitted_by, status)
    VALUES (v_instance_no, v_workflow_id, p_target_type, p_target_id,
        p_target_summary, 1, v_total_nodes, p_submitted_by, 'in_progress');

    SET v_instance_id = LAST_INSERT_ID();

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('submit_approval', 'approval_instance', v_instance_id, p_submitted_by,
            JSON_OBJECT('instance_no', v_instance_no, 'workflow_code', p_workflow_code, 'target', p_target_summary));

    COMMIT;

    SELECT v_instance_id AS instance_id, v_instance_no AS instance_no,
           v_first_approver_id AS first_approver_id, v_total_nodes AS total_nodes;
END//


-- ============================================================
-- 40. 处理审批
-- 调用关系: 被审批人操作调用
-- 流转逻辑:
--   approve: 当前节点通过 -> 流转到下一节点 或 整体通过
--   reject: 驳回 -> 整体驳回
--   delegate: 转授权给他人
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:erp_system.sp_process_approval
CREATE PROCEDURE sp_process_approval(
    IN p_instance_id BIGINT UNSIGNED,
    IN p_node_id BIGINT UNSIGNED,
    IN p_approver_id BIGINT UNSIGNED,
    IN p_action ENUM('approve','reject','delegate','return'),
    IN p_comment VARCHAR(500),
    IN p_delegated_to BIGINT UNSIGNED
)
BEGIN
    DECLARE v_current_node_level INT;
    DECLARE v_total_nodes INT;
    DECLARE v_next_node_id BIGINT;
    DECLARE v_next_approver_id BIGINT;
    DECLARE v_next_approver_type VARCHAR(30);
    DECLARE v_status VARCHAR(20);

    SELECT current_node_level, total_nodes, status
    INTO v_current_node_level, v_total_nodes, v_status
    FROM approval_instances WHERE id = p_instance_id;

    IF v_status != 'in_progress' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '审批实例状态不允许操作';
    END IF;

    START TRANSACTION;

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
            JSON_OBJECT('action', p_action, 'comment', p_comment));

    COMMIT;

    SELECT ROW_COUNT() > 0 AS success;
END//


-- ============================================================
-- 41. 寄售消耗结算
-- 调用关系: 被月度/按需结算流程调用
-- 工作原理:
--   1. 汇总寄售消耗记录
--   2. 生成销售单
--   3. 扣减寄售库存
--   4. 确认收入
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:erp_system.sp_settle_consignment
CREATE PROCEDURE sp_settle_consignment(
    IN p_customer_id BIGINT UNSIGNED,
    IN p_settlement_period VARCHAR(7)
)
BEGIN
    DECLARE v_done INT DEFAULT FALSE;
    DECLARE v_consignment_id BIGINT;
    DECLARE v_total_consumed INT;
    DECLARE v_product_id BIGINT;
    DECLARE v_unit_price DECIMAL(12,2);
    DECLARE v_order_no VARCHAR(30);
    DECLARE v_order_id BIGINT;
    DECLARE v_total_amount DECIMAL(18,2) DEFAULT 0.00;
    DECLARE v_salesperson_id BIGINT;

    DECLARE cur CURSOR FOR
        SELECT ci.id, ci.product_id, ci.unit_price, COALESCE(SUM(cc.consumed_qty), 0)
        FROM consignment_inventory ci
        LEFT JOIN consignment_consumptions cc ON ci.id = cc.consignment_id
            AND cc.confirmed_by_customer = FALSE
        WHERE ci.customer_id = p_customer_id AND ci.status = 'active' AND ci.available_qty < ci.consigned_qty
        GROUP BY ci.id, ci.product_id, ci.unit_price
        HAVING COALESCE(SUM(cc.consumed_qty), 0) > 0;

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = TRUE;

    -- 查找客户对应的销售员
    SELECT salesperson_id INTO v_salesperson_id
    FROM sales_orders WHERE customer_id = p_customer_id LIMIT 1;

    SET v_salesperson_id = COALESCE(v_salesperson_id, 9);

    SET v_order_no = CONCAT('SO-CON-', p_settlement_period, '-', LPAD(FLOOR(RAND() * 9999) + 1, 4, '0'));

    START TRANSACTION;

    INSERT INTO sales_orders (order_no, customer_id, salesperson_id, warehouse_id,
        order_date, payment_method, status, remark)
    VALUES (v_order_no, p_customer_id, v_salesperson_id, 1, CURDATE(), 'transfer', 'confirmed',
        CONCAT('寄售结算: ', p_settlement_period));

    SET v_order_id = LAST_INSERT_ID();

    OPEN cur;

    consume_loop: LOOP
        FETCH cur INTO v_consignment_id, v_product_id, v_unit_price, v_total_consumed;
        IF v_done THEN LEAVE consume_loop; END IF;

        INSERT INTO sales_order_items (order_id, product_id, quantity, unit_price, amount)
        VALUES (v_order_id, v_product_id, v_total_consumed, v_unit_price, v_total_consumed * v_unit_price);

        SET v_total_amount = v_total_amount + (v_total_consumed * v_unit_price);

        -- 更新寄售库存
        UPDATE consignment_inventory
        SET consumed_qty = consumed_qty + v_total_consumed,
            last_consumed_date = CURDATE()
        WHERE id = v_consignment_id;

        -- 确认消耗记录
        UPDATE consignment_consumptions
        SET confirmed_by_customer = TRUE, sales_order_id = v_order_id
        WHERE consignment_id = v_consignment_id AND confirmed_by_customer = FALSE;

    END LOOP;

    CLOSE cur;

    UPDATE sales_orders SET total_amount = v_total_amount WHERE id = v_order_id;

    COMMIT;

    SELECT v_order_id AS order_id, v_order_no AS order_no, v_total_amount AS total_amount;
END//


-- ============================================================
-- 42. 现金流预测
-- 调用关系: 被财务周报/月报调用
-- 预测原理:
--   预计收款 = 账期内应收款 + 预测新销售
--   预计付款 = 账期内应付款 + 工资 + 税金
--   净现金流 = 期初余额 + 收款 - 付款
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:erp_system.sp_calculate_cash_flow
CREATE PROCEDURE sp_calculate_cash_flow(
    IN p_forecast_days INT
)
BEGIN
    DECLARE v_day INT DEFAULT 0;
    DECLARE v_forecast_date DATE;
    DECLARE v_beginning_balance DECIMAL(18,2);
    DECLARE v_daily_collections DECIMAL(18,2);
    DECLARE v_daily_payments DECIMAL(18,2);
    DECLARE v_daily_salary DECIMAL(18,2);
    DECLARE v_daily_tax DECIMAL(18,2);
    DECLARE v_avg_daily_sales DECIMAL(18,2);

    -- 期初余额
    SELECT SUM(current_balance) INTO v_beginning_balance
    FROM accounts WHERE is_cash = TRUE OR is_bank = TRUE;

    -- 日均销售额
    SELECT COALESCE(AVG(daily_total), 0) INTO v_avg_daily_sales
    FROM (
        SELECT order_date, SUM(total_amount) AS daily_total
        FROM sales_orders
        WHERE order_date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
          AND status NOT IN ('draft', 'cancelled')
        GROUP BY order_date
    ) t;

    -- 清理旧预测
    DELETE FROM cash_flow_forecasts WHERE forecast_date >= CURDATE();

    -- 日均工资
    SELECT COALESCE(SUM(net_pay + social_security_company + housing_fund_company) / 30.0, 0)
    INTO v_daily_salary
    FROM salary_payments
    WHERE salary_month = DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 MONTH), '%Y-%m');

    WHILE v_day < p_forecast_days DO
        SET v_forecast_date = DATE_ADD(CURDATE(), INTERVAL v_day DAY);

        -- 预计当日收款
        SELECT COALESCE(SUM(outstanding_amount), 0) INTO v_daily_collections
        FROM ar_aging_snapshots
        WHERE due_date = v_forecast_date AND snapshot_date = CURDATE();

        -- 预计当日付款
        SELECT COALESCE(SUM(outstanding_amount), 0) INTO v_daily_payments
        FROM ap_aging_snapshots
        WHERE due_date = v_forecast_date AND snapshot_date = CURDATE();

        -- 预计税金(简化: 月税金/30)
        SET v_daily_tax = 5000;

        INSERT INTO cash_flow_forecasts (forecast_date, forecast_type, beginning_balance,
            expected_collections, expected_payments, expected_salary, expected_tax)
        VALUES (v_forecast_date, 'daily', v_beginning_balance,
            v_daily_collections + v_avg_daily_sales * 0.3,
            v_daily_payments + v_avg_daily_sales * 0.15,
            IF(DAYOFWEEK(v_forecast_date) = 6, 0, v_daily_salary),
            IF(DAY(v_forecast_date) = 15, v_daily_tax * 30, v_daily_tax)
        );

        -- 更新下一天的期初余额
        SELECT ending_balance INTO v_beginning_balance
        FROM cash_flow_forecasts
        WHERE forecast_date = v_forecast_date AND forecast_type = 'daily';

        SET v_day = v_day + 1;
    END WHILE;

    SELECT CONCAT('现金流预测完成: ', p_forecast_days, '天') AS result;
END//


-- ============================================================
-- 43. 税务申报
-- 调用关系: 被月度税务流程调用
-- 计算原理:
--   销项税额 = Σ销售发票税额
--   进项税额 = Σ采购发票税额(已认证)
--   进项税转出 = 不可抵扣部分
--   应纳税额 = 销项税额 - 进项税额 + 进项税转出
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:erp_system.sp_file_tax_return
CREATE PROCEDURE sp_file_tax_return(
    IN p_tax_period VARCHAR(7),
    IN p_prepared_by BIGINT UNSIGNED
)
BEGIN
    DECLARE v_output_tax DECIMAL(18,2);
    DECLARE v_input_tax DECIMAL(18,2);
    DECLARE v_input_transfer DECIMAL(18,2);
    DECLARE v_tax_payable DECIMAL(18,2);
    DECLARE v_deadline DATE;

    SET v_deadline = DATE_ADD(CONCAT(p_tax_period, '-01'), INTERVAL 1 MONTH) + INTERVAL 14 DAY;

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

    SET v_input_transfer = 0;
    SET v_tax_payable = v_output_tax - v_input_tax + v_input_transfer;

    INSERT INTO tax_filings (tax_period, tax_type, output_tax, input_tax,
        input_tax_transfer, tax_payable, filing_deadline, status, prepared_by)
    VALUES (p_tax_period, 'vat', v_output_tax, v_input_tax,
        v_input_transfer, v_tax_payable, v_deadline, 'prepared', p_prepared_by)
    ON DUPLICATE KEY UPDATE
        output_tax = VALUES(output_tax),
        input_tax = VALUES(input_tax),
        tax_payable = VALUES(tax_payable),
        status = 'prepared';

    SELECT
        p_tax_period AS tax_period,
        v_output_tax AS output_tax,
        v_input_tax AS input_tax,
        v_tax_payable AS tax_payable,
        v_deadline AS filing_deadline;
END//


-- ============================================================
-- 44. 序列号出入库
-- 调用关系: 被采购入库和销售出库流程调用
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:erp_system.sp_scan_serial_number
CREATE PROCEDURE sp_scan_serial_number(
    IN p_serial_no VARCHAR(100),
    IN p_product_id BIGINT UNSIGNED,
    IN p_event_type ENUM('purchase_in','sales_out','return_in','transfer','scrap'),
    IN p_warehouse_id BIGINT UNSIGNED,
    IN p_reference_type VARCHAR(50),
    IN p_reference_id BIGINT,
    IN p_operator_id BIGINT UNSIGNED
)
BEGIN
    DECLARE v_sn_id BIGINT;
    DECLARE v_old_status VARCHAR(20);

    SELECT id, status INTO v_sn_id, v_old_status
    FROM serial_numbers WHERE serial_no = p_serial_no AND product_id = p_product_id;

    IF v_sn_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '序列号不存在';
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

    SELECT CONCAT('序列号操作成功: ', p_serial_no, ' -> ', p_event_type) AS result;
END//


-- ============================================================
-- 45. 价格变更管理
-- 调用关系: 被价格管理流程调用
-- 自动触发审批流
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:erp_system.sp_change_product_price
CREATE PROCEDURE sp_change_product_price(
    IN p_product_id BIGINT UNSIGNED,
    IN p_price_type ENUM('purchase','wholesale','retail'),
    IN p_new_price DECIMAL(12,2),
    IN p_effective_date DATE,
    IN p_change_reason VARCHAR(200),
    IN p_changed_by BIGINT UNSIGNED
)
BEGIN
    DECLARE v_old_price DECIMAL(12,2);

    CASE p_price_type
        WHEN 'purchase' THEN SELECT purchase_price INTO v_old_price FROM products WHERE id = p_product_id;
        WHEN 'wholesale' THEN SELECT wholesale_price INTO v_old_price FROM products WHERE id = p_product_id;
        WHEN 'retail' THEN SELECT retail_price INTO v_old_price FROM products WHERE id = p_product_id;
    END CASE;

    START TRANSACTION;

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
            JSON_OBJECT(p_price_type, v_old_price),
            JSON_OBJECT(p_price_type, p_new_price, 'reason', p_change_reason));

    COMMIT;

    SELECT ROW_COUNT() > 0 AS success;
END//


-- ============================================================
-- 46. 绩效考核评分
-- 调用关系: 被HR月度/季度考核流程调用
-- 评分原理: 总分 = 业绩*0.4 + 能力*0.3 + 态度*0.2 + 出勤*0.1
--           S(>=90) A(>=80) B(>=70) C(>=60) D(<60)
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:erp_system.sp_create_performance_review
CREATE PROCEDURE sp_create_performance_review(
    IN p_employee_id BIGINT UNSIGNED,
    IN p_reviewer_id BIGINT UNSIGNED,
    IN p_review_period VARCHAR(7),
    IN p_review_type ENUM('monthly','quarterly','annual','probation','promotion'),
    IN p_performance_score DECIMAL(5,2),
    IN p_competency_score DECIMAL(5,2),
    IN p_attitude_score DECIMAL(5,2),
    IN p_attendance_score DECIMAL(5,2),
    IN p_self_assessment TEXT,
    IN p_reviewer_comment TEXT
)
BEGIN
    DECLARE v_review_no VARCHAR(30);

    SET v_review_no = CONCAT('PR-', p_review_period, '-', LPAD(p_employee_id, 4, '0'));

    INSERT INTO performance_reviews (review_no, employee_id, reviewer_id, review_period, review_type,
        performance_score, competency_score, attitude_score, attendance_score,
        self_assessment, reviewer_comment, status)
    VALUES (v_review_no, p_employee_id, p_reviewer_id, p_review_period, p_review_type,
        p_performance_score, p_competency_score, p_attitude_score, p_attendance_score,
        p_self_assessment, p_reviewer_comment, 'reviewed')
    ON DUPLICATE KEY UPDATE
        performance_score = VALUES(performance_score),
        competency_score = VALUES(competency_score),
        attitude_score = VALUES(attitude_score),
        attendance_score = VALUES(attendance_score),
        reviewer_comment = VALUES(reviewer_comment),
        status = 'reviewed';

    SELECT CONCAT('绩效考核完成: ', v_review_no) AS result;
END//


-- ============================================================
-- 47. 合同到期预警
-- 调用关系: 被定时任务调用
-- ============================================================
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:erp_system.sp_contract_expiry_alert
CREATE PROCEDURE sp_contract_expiry_alert()
BEGIN
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
        DATEDIFF(c.end_date, CURDATE()) AS days_remaining,
        c.total_amount,
        c.status,
        (SELECT COUNT(*) FROM contract_milestones cm WHERE cm.contract_id = c.id AND cm.status != 'completed') AS pending_milestones,
        CASE
            WHEN DATEDIFF(c.end_date, CURDATE()) <= 0 THEN '已到期'
            WHEN DATEDIFF(c.end_date, CURDATE()) <= 30 THEN '30天内到期'
            WHEN DATEDIFF(c.end_date, CURDATE()) <= 60 THEN '60天内到期'
            WHEN DATEDIFF(c.end_date, CURDATE()) <= 90 THEN '90天内到期'
            ELSE '正常'
        END AS expiry_alert
    FROM contracts c
    WHERE c.status IN ('active', 'in_progress')
    ORDER BY days_remaining ASC;
END
-- relation-detector-fixture-end
