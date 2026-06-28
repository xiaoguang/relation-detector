-- ============================================================
-- ERP系统存储过程
-- 包含: 部门管理, 货品管理, 员工管理, 请购, 采购, 进货入库,
--       销售, 退库, 工资发放, 审计, 对账, 出纳, 结算
-- ============================================================

USE erp_system;

DELIMITER //

-- ============================================================
-- 1. 部门管理
-- ============================================================

-- 插入部门
CREATE PROCEDURE sp_create_department(
    IN p_parent_id BIGINT UNSIGNED,
    IN p_name VARCHAR(100),
    IN p_code VARCHAR(20),
    IN p_budget DECIMAL(18,2),
    IN p_headcount_plan INT UNSIGNED
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '创建部门失败';
    END;

    START TRANSACTION;
    INSERT INTO departments (parent_id, name, code, budget, headcount_plan)
    VALUES (p_parent_id, p_name, p_code, p_budget, p_headcount_plan);

    INSERT INTO audit_log (action, target_type, target_id, new_value)
    VALUES ('create_department', 'department', LAST_INSERT_ID(),
            JSON_OBJECT('name', p_name, 'code', p_code, 'budget', p_budget));
    COMMIT;

    SELECT LAST_INSERT_ID() AS new_department_id;
END//

-- 更新部门经理
CREATE PROCEDURE sp_assign_department_manager(
    IN p_department_id BIGINT UNSIGNED,
    IN p_manager_id BIGINT UNSIGNED
)
BEGIN
    DECLARE v_old_manager_id BIGINT UNSIGNED;

    SELECT manager_id INTO v_old_manager_id FROM departments WHERE id = p_department_id;

    UPDATE departments SET manager_id = p_manager_id WHERE id = p_department_id;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, old_value, new_value)
    VALUES ('assign_manager', 'department', p_department_id, p_manager_id,
            JSON_OBJECT('manager_id', v_old_manager_id),
            JSON_OBJECT('manager_id', p_manager_id));

    SELECT ROW_COUNT() > 0 AS success;
END//


-- ============================================================
-- 2. 门店/仓库管理
-- ============================================================

CREATE PROCEDURE sp_create_warehouse(
    IN p_name VARCHAR(100),
    IN p_code VARCHAR(20),
    IN p_address VARCHAR(200),
    IN p_manager_id BIGINT UNSIGNED,
    IN p_type ENUM('main','transit','returns','cold'),
    IN p_capacity_m3 DECIMAL(10,3)
)
BEGIN
    INSERT INTO warehouses (name, code, address, manager_id, type, capacity_m3)
    VALUES (p_name, p_code, p_address, p_manager_id, p_type, p_capacity_m3);

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('create_warehouse', 'warehouse', LAST_INSERT_ID(), p_manager_id,
            JSON_OBJECT('name', p_name, 'code', p_code, 'type', p_type));

    SELECT LAST_INSERT_ID() AS new_warehouse_id;
END//


-- ============================================================
-- 3. 货品管理
-- ============================================================

CREATE PROCEDURE sp_create_product(
    IN p_sku VARCHAR(50),
    IN p_name VARCHAR(200),
    IN p_category_id BIGINT UNSIGNED,
    IN p_unit VARCHAR(20),
    IN p_spec VARCHAR(100),
    IN p_brand VARCHAR(100),
    IN p_barcode VARCHAR(50),
    IN p_purchase_price DECIMAL(12,2),
    IN p_wholesale_price DECIMAL(12,2),
    IN p_retail_price DECIMAL(12,2),
    IN p_min_stock INT,
    IN p_max_stock INT,
    IN p_batch_managed BOOLEAN,
    IN p_shelf_life_days INT
)
BEGIN
    INSERT INTO products (sku, name, category_id, unit, spec, brand, barcode,
        purchase_price, wholesale_price, retail_price, min_stock, max_stock,
        batch_managed, shelf_life_days)
    VALUES (p_sku, p_name, p_category_id, p_unit, p_spec, p_brand, p_barcode,
        p_purchase_price, p_wholesale_price, p_retail_price, p_min_stock, p_max_stock,
        p_batch_managed, p_shelf_life_days);

    INSERT INTO audit_log (action, target_type, target_id, new_value)
    VALUES ('create_product', 'product', LAST_INSERT_ID(),
            JSON_OBJECT('sku', p_sku, 'name', p_name, 'retail_price', p_retail_price));

    SELECT LAST_INSERT_ID() AS new_product_id;
END//

-- 批量创建批号
CREATE PROCEDURE sp_create_batch(
    IN p_product_id BIGINT UNSIGNED,
    IN p_batch_no VARCHAR(50),
    IN p_production_date DATE,
    IN p_expiry_date DATE,
    IN p_supplier_id BIGINT UNSIGNED,
    IN p_purchase_price DECIMAL(12,2),
    IN p_initial_qty INT
)
BEGIN
    INSERT INTO product_batches (product_id, batch_no, production_date, expiry_date,
        supplier_id, purchase_price, initial_qty, current_qty)
    VALUES (p_product_id, p_batch_no, p_production_date, p_expiry_date,
        p_supplier_id, p_purchase_price, p_initial_qty, p_initial_qty);

    SELECT LAST_INSERT_ID() AS new_batch_id;
END//


-- ============================================================
-- 4. 员工管理
-- ============================================================

-- 生成员工
CREATE PROCEDURE sp_hire_employee(
    IN p_name VARCHAR(50),
    IN p_gender ENUM('M','F'),
    IN p_id_card VARCHAR(18),
    IN p_phone VARCHAR(20),
    IN p_email VARCHAR(100),
    IN p_birth_date DATE,
    IN p_hire_date DATE,
    IN p_department_id BIGINT UNSIGNED,
    IN p_position_id BIGINT UNSIGNED,
    IN p_manager_id BIGINT UNSIGNED,
    IN p_salary DECIMAL(12,2),
    IN p_bank_name VARCHAR(100),
    IN p_bank_account VARCHAR(50),
    IN p_address VARCHAR(200)
)
BEGIN
    DECLARE v_employee_no VARCHAR(20);
    DECLARE v_new_id BIGINT UNSIGNED;
    DECLARE v_pos_headcount INT;
    DECLARE v_current_count INT;

    -- 检查编制
    SELECT headcount INTO v_pos_headcount FROM positions WHERE id = p_position_id;
    SELECT COUNT(*) INTO v_current_count FROM employees
    WHERE position_id = p_position_id AND status IN ('active','probation','leave');

    IF v_current_count >= v_pos_headcount THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '该岗位编制已满，无法入职';
    END IF;

    -- 生成工号: YYYYMMDD + 4位序号
    SET v_employee_no = CONCAT(
        DATE_FORMAT(CURDATE(), '%Y%m%d'),
        LPAD(FLOOR(RAND() * 9999) + 1, 4, '0')
    );

    INSERT INTO employees (employee_no, name, gender, id_card, phone, email,
        birth_date, hire_date, department_id, position_id, manager_id, salary,
        social_security_base, housing_fund_base, bank_name, bank_account, address, status)
    VALUES (v_employee_no, p_name, p_gender, p_id_card, p_phone, p_email,
        p_birth_date, p_hire_date, p_department_id, p_position_id, p_manager_id, p_salary,
        p_salary, p_salary, p_bank_name, p_bank_account, p_address, 'probation');

    SET v_new_id = LAST_INSERT_ID();

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('hire_employee', 'employee', v_new_id, v_new_id,
            JSON_OBJECT('name', p_name, 'employee_no', v_employee_no, 'department_id', p_department_id, 'salary', p_salary));

    SELECT v_new_id AS new_employee_id, v_employee_no AS employee_no;
END//

-- 生成经理（从现有员工提拔）
CREATE PROCEDURE sp_promote_to_manager(
    IN p_employee_id BIGINT UNSIGNED,
    IN p_new_position_id BIGINT UNSIGNED,
    IN p_new_salary DECIMAL(12,2),
    IN p_effective_date DATE
)
BEGIN
    DECLARE v_old_salary DECIMAL(12,2);
    DECLARE v_old_position_id BIGINT UNSIGNED;
    DECLARE v_department_id BIGINT UNSIGNED;

    SELECT salary, position_id, department_id
    INTO v_old_salary, v_old_position_id, v_department_id
    FROM employees WHERE id = p_employee_id;

    IF v_old_salary IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '员工不存在';
    END IF;

    START TRANSACTION;

    -- 更新员工职位和薪资
    UPDATE employees
    SET position_id = p_new_position_id,
        salary = p_new_salary,
        social_security_base = p_new_salary,
        housing_fund_base = p_new_salary,
        status = 'active'
    WHERE id = p_employee_id;

    -- 如果是管理岗位，更新部门经理
    IF EXISTS (SELECT 1 FROM positions WHERE id = p_new_position_id AND name LIKE '%经理%') THEN
        UPDATE departments SET manager_id = p_employee_id WHERE id = v_department_id;
    END IF;

    -- 记录薪资变动
    INSERT INTO employee_salary_log (employee_id, old_salary, new_salary, change_reason, effective_date)
    VALUES (p_employee_id, v_old_salary, p_new_salary, '晋升为经理', p_effective_date);

    INSERT INTO audit_log (action, target_type, target_id, employee_id, old_value, new_value)
    VALUES ('promote_to_manager', 'employee', p_employee_id, p_employee_id,
            JSON_OBJECT('position_id', v_old_position_id, 'salary', v_old_salary),
            JSON_OBJECT('position_id', p_new_position_id, 'salary', p_new_salary));

    COMMIT;

    SELECT ROW_COUNT() > 0 AS success;
END//

-- 员工离职
CREATE PROCEDURE sp_resign_employee(
    IN p_employee_id BIGINT UNSIGNED,
    IN p_resignation_date DATE,
    IN p_reason VARCHAR(500)
)
BEGIN
    DECLARE v_old_status ENUM('active','probation','leave','resigned','terminated');

    SELECT status INTO v_old_status FROM employees WHERE id = p_employee_id;

    UPDATE employees
    SET status = 'resigned',
        resignation_date = p_resignation_date,
        resignation_reason = p_reason
    WHERE id = p_employee_id;

    -- 如果离职员工是部门经理，清除部门经理引用
    UPDATE departments SET manager_id = NULL WHERE manager_id = p_employee_id;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, old_value, new_value)
    VALUES ('resign_employee', 'employee', p_employee_id, p_employee_id,
            JSON_OBJECT('status', v_old_status),
            JSON_OBJECT('status', 'resigned', 'resignation_date', p_resignation_date, 'reason', p_reason));

    SELECT ROW_COUNT() > 0 AS success;
END//


-- ============================================================
-- 5. 请购流程
-- ============================================================

CREATE PROCEDURE sp_create_purchase_requisition(
    IN p_department_id BIGINT UNSIGNED,
    IN p_requester_id BIGINT UNSIGNED,
    IN p_required_date DATE,
    IN p_urgency ENUM('normal','urgent','emergency'),
    IN p_items_json JSON,
    IN p_remark VARCHAR(500)
)
BEGIN
    DECLARE v_req_no VARCHAR(30);
    DECLARE v_req_id BIGINT UNSIGNED;
    DECLARE v_total DECIMAL(18,2) DEFAULT 0.00;
    DECLARE v_idx INT DEFAULT 0;
    DECLARE v_item_count INT;
    DECLARE v_product_id BIGINT UNSIGNED;
    DECLARE v_qty INT;
    DECLARE v_price DECIMAL(12,2);

    SET v_req_no = CONCAT('PR-', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-', LPAD(FLOOR(RAND() * 9999) + 1, 4, '0'));
    SET v_item_count = JSON_LENGTH(p_items_json);

    IF v_item_count = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '请购明细不能为空';
    END IF;

    START TRANSACTION;

    INSERT INTO purchase_requisitions (requisition_no, department_id, requester_id,
        requisition_date, required_date, urgency, status, remark)
    VALUES (v_req_no, p_department_id, p_requester_id, CURDATE(), p_required_date,
        p_urgency, 'pending', p_remark);

    SET v_req_id = LAST_INSERT_ID();

    WHILE v_idx < v_item_count DO
        SET v_product_id = JSON_UNQUOTE(JSON_EXTRACT(p_items_json, CONCAT('$[', v_idx, '].product_id')));
        SET v_qty = JSON_UNQUOTE(JSON_EXTRACT(p_items_json, CONCAT('$[', v_idx, '].quantity')));
        SET v_price = JSON_UNQUOTE(JSON_EXTRACT(p_items_json, CONCAT('$[', v_idx, '].estimated_price')));

        INSERT INTO purchase_requisition_items (requisition_id, product_id, quantity, estimated_price)
        VALUES (v_req_id, v_product_id, v_qty, v_price);

        SET v_total = v_total + (v_qty * v_price);
        SET v_idx = v_idx + 1;
    END WHILE;

    UPDATE purchase_requisitions SET total_amount = v_total WHERE id = v_req_id;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('create_requisition', 'purchase_requisition', v_req_id, p_requester_id,
            JSON_OBJECT('requisition_no', v_req_no, 'total_amount', v_total, 'urgency', p_urgency));

    COMMIT;

    SELECT v_req_id AS requisition_id, v_req_no AS requisition_no, v_total AS total_amount;
END//

-- 审批请购单
CREATE PROCEDURE sp_approve_requisition(
    IN p_requisition_id BIGINT UNSIGNED,
    IN p_approver_id BIGINT UNSIGNED,
    IN p_approved BOOLEAN,
    IN p_remark VARCHAR(500)
)
BEGIN
    DECLARE v_old_status VARCHAR(20);

    SELECT status INTO v_old_status FROM purchase_requisitions WHERE id = p_requisition_id;

    IF v_old_status != 'pending' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '只能审批待审批状态的请购单';
    END IF;

    UPDATE purchase_requisitions
    SET status = IF(p_approved, 'approved', 'rejected'),
        remark = CONCAT(COALESCE(remark, ''), ' | 审批意见: ', p_remark)
    WHERE id = p_requisition_id;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, old_value, new_value)
    VALUES ('approve_requisition', 'purchase_requisition', p_requisition_id, p_approver_id,
            JSON_OBJECT('status', v_old_status),
            JSON_OBJECT('status', IF(p_approved, 'approved', 'rejected'), 'remark', p_remark));

    SELECT ROW_COUNT() > 0 AS success;
END//


-- ============================================================
-- 6. 采购流程
-- ============================================================

CREATE PROCEDURE sp_create_purchase_order(
    IN p_supplier_id BIGINT UNSIGNED,
    IN p_requisition_id BIGINT UNSIGNED,
    IN p_department_id BIGINT UNSIGNED,
    IN p_purchaser_id BIGINT UNSIGNED,
    IN p_expected_delivery_date DATE,
    IN p_payment_terms VARCHAR(100),
    IN p_items_json JSON,
    IN p_remark VARCHAR(500)
)
BEGIN
    DECLARE v_order_no VARCHAR(30);
    DECLARE v_order_id BIGINT UNSIGNED;
    DECLARE v_total DECIMAL(18,2) DEFAULT 0.00;
    DECLARE v_idx INT DEFAULT 0;
    DECLARE v_item_count INT;
    DECLARE v_product_id BIGINT UNSIGNED;
    DECLARE v_qty INT;
    DECLARE v_price DECIMAL(12,2);

    SET v_order_no = CONCAT('PO-', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-', LPAD(FLOOR(RAND() * 9999) + 1, 4, '0'));
    SET v_item_count = JSON_LENGTH(p_items_json);

    IF v_item_count = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '采购明细不能为空';
    END IF;

    START TRANSACTION;

    INSERT INTO purchase_orders (order_no, supplier_id, requisition_id, department_id,
        purchaser_id, order_date, expected_delivery_date, payment_terms, status, remark)
    VALUES (v_order_no, p_supplier_id, p_requisition_id, p_department_id,
        p_purchaser_id, CURDATE(), p_expected_delivery_date, p_payment_terms, 'ordered', p_remark);

    SET v_order_id = LAST_INSERT_ID();

    WHILE v_idx < v_item_count DO
        SET v_product_id = JSON_UNQUOTE(JSON_EXTRACT(p_items_json, CONCAT('$[', v_idx, '].product_id')));
        SET v_qty = JSON_UNQUOTE(JSON_EXTRACT(p_items_json, CONCAT('$[', v_idx, '].quantity')));
        SET v_price = JSON_UNQUOTE(JSON_EXTRACT(p_items_json, CONCAT('$[', v_idx, '].unit_price')));

        INSERT INTO purchase_order_items (order_id, product_id, quantity, unit_price)
        VALUES (v_order_id, v_product_id, v_qty, v_price);

        SET v_total = v_total + (v_qty * v_price);
        SET v_idx = v_idx + 1;
    END WHILE;

    UPDATE purchase_orders SET total_amount = v_total WHERE id = v_order_id;

    -- 更新请购单状态
    IF p_requisition_id IS NOT NULL THEN
        UPDATE purchase_requisitions SET status = 'ordered' WHERE id = p_requisition_id;
    END IF;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('create_purchase_order', 'purchase_order', v_order_id, p_purchaser_id,
            JSON_OBJECT('order_no', v_order_no, 'supplier_id', p_supplier_id, 'total_amount', v_total));

    COMMIT;

    SELECT v_order_id AS order_id, v_order_no AS order_no, v_total AS total_amount;
END//


-- ============================================================
-- 7. 进货入库流程
-- ============================================================

CREATE PROCEDURE sp_receive_purchase(
    IN p_order_id BIGINT UNSIGNED,
    IN p_warehouse_id BIGINT UNSIGNED,
    IN p_receiver_id BIGINT UNSIGNED,
    IN p_items_json JSON,
    IN p_inspection_result VARCHAR(500),
    IN p_remark VARCHAR(500)
)
BEGIN
    DECLARE v_receipt_no VARCHAR(30);
    DECLARE v_receipt_id BIGINT UNSIGNED;
    DECLARE v_order_status VARCHAR(50);
    DECLARE v_total_qty INT DEFAULT 0;
    DECLARE v_total_amount DECIMAL(18,2) DEFAULT 0.00;
    DECLARE v_idx INT DEFAULT 0;
    DECLARE v_item_count INT;
    DECLARE v_order_item_id BIGINT UNSIGNED;
    DECLARE v_product_id BIGINT UNSIGNED;
    DECLARE v_received_qty INT;
    DECLARE v_accepted_qty INT;
    DECLARE v_rejected_qty INT;
    DECLARE v_unit_price DECIMAL(12,2);
    DECLARE v_batch_no VARCHAR(50);
    DECLARE v_production_date DATE;
    DECLARE v_expiry_date DATE;
    DECLARE v_batch_id BIGINT UNSIGNED;
    DECLARE v_before_qty INT;

    SELECT status INTO v_order_status FROM purchase_orders WHERE id = p_order_id;
    IF v_order_status NOT IN ('ordered', 'partially_received') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '采购单状态不允许收货';
    END IF;

    SET v_receipt_no = CONCAT('RC-', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-', LPAD(FLOOR(RAND() * 9999) + 1, 4, '0'));
    SET v_item_count = JSON_LENGTH(p_items_json);

    START TRANSACTION;

    INSERT INTO purchase_receipts (receipt_no, order_id, warehouse_id, receiver_id,
        receipt_date, status, inspection_result, remark)
    VALUES (v_receipt_no, p_order_id, p_warehouse_id, p_receiver_id, CURDATE(),
        IF(p_inspection_result IS NULL, 'received', 'inspected'), p_inspection_result, p_remark);

    SET v_receipt_id = LAST_INSERT_ID();

    WHILE v_idx < v_item_count DO
        SET v_order_item_id = JSON_UNQUOTE(JSON_EXTRACT(p_items_json, CONCAT('$[', v_idx, '].order_item_id')));
        SET v_product_id = JSON_UNQUOTE(JSON_EXTRACT(p_items_json, CONCAT('$[', v_idx, '].product_id')));
        SET v_received_qty = JSON_UNQUOTE(JSON_EXTRACT(p_items_json, CONCAT('$[', v_idx, '].received_qty')));
        SET v_accepted_qty = JSON_UNQUOTE(JSON_EXTRACT(p_items_json, CONCAT('$[', v_idx, '].accepted_qty')));
        SET v_rejected_qty = JSON_UNQUOTE(JSON_EXTRACT(p_items_json, CONCAT('$[', v_idx, '].rejected_qty')));
        SET v_unit_price = JSON_UNQUOTE(JSON_EXTRACT(p_items_json, CONCAT('$[', v_idx, '].unit_price')));
        SET v_batch_no = JSON_UNQUOTE(JSON_EXTRACT(p_items_json, CONCAT('$[', v_idx, '].batch_no')));
        SET v_production_date = JSON_UNQUOTE(JSON_EXTRACT(p_items_json, CONCAT('$[', v_idx, '].production_date')));
        SET v_expiry_date = JSON_UNQUOTE(JSON_EXTRACT(p_items_json, CONCAT('$[', v_idx, '].expiry_date')));

        -- 创建批号
        INSERT INTO product_batches (product_id, batch_no, production_date, expiry_date,
            purchase_price, initial_qty, current_qty)
        VALUES (v_product_id, v_batch_no, v_production_date, v_expiry_date,
            v_unit_price, v_accepted_qty, v_accepted_qty);

        SET v_batch_id = LAST_INSERT_ID();

        -- 入库明细
        INSERT INTO purchase_receipt_items (receipt_id, order_item_id, product_id, batch_id,
            received_qty, accepted_qty, rejected_qty, unit_price, production_date, expiry_date)
        VALUES (v_receipt_id, v_order_item_id, v_product_id, v_batch_id,
            v_received_qty, v_accepted_qty, v_rejected_qty, v_unit_price, v_production_date, v_expiry_date);

        -- 更新库存
        SELECT COALESCE(SUM(quantity), 0) INTO v_before_qty
        FROM inventory WHERE product_id = v_product_id AND warehouse_id = p_warehouse_id;

        INSERT INTO inventory (product_id, batch_id, warehouse_id, quantity)
        VALUES (v_product_id, v_batch_id, p_warehouse_id, v_accepted_qty)
        ON DUPLICATE KEY UPDATE quantity = quantity + v_accepted_qty;

        -- 库存变动日志
        INSERT INTO inventory_transactions (product_id, batch_id, warehouse_id,
            transaction_type, quantity_change, before_qty, after_qty,
            reference_type, reference_id, operator_id)
        VALUES (v_product_id, v_batch_id, p_warehouse_id, 'purchase_in',
            v_accepted_qty, v_before_qty, v_before_qty + v_accepted_qty,
            'purchase_receipt', v_receipt_id, p_receiver_id);

        -- 更新采购单明细的收货数量
        UPDATE purchase_order_items
        SET received_qty = received_qty + v_accepted_qty
        WHERE id = v_order_item_id;

        SET v_total_qty = v_total_qty + v_accepted_qty;
        SET v_total_amount = v_total_amount + (v_accepted_qty * v_unit_price);
        SET v_idx = v_idx + 1;
    END WHILE;

    UPDATE purchase_receipts
    SET total_qty = v_total_qty, total_amount = v_total_amount
    WHERE id = v_receipt_id;

    -- 更新采购单状态
    UPDATE purchase_orders
    SET status = 'partially_received',
        actual_delivery_date = CURDATE()
    WHERE id = p_order_id;

    -- 检查是否全部收货完成
    IF NOT EXISTS (
        SELECT 1 FROM purchase_order_items
        WHERE order_id = p_order_id AND received_qty < quantity
    ) THEN
        UPDATE purchase_orders SET status = 'received' WHERE id = p_order_id;
    END IF;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('receive_purchase', 'purchase_receipt', v_receipt_id, p_receiver_id,
            JSON_OBJECT('receipt_no', v_receipt_no, 'order_id', p_order_id, 'total_qty', v_total_qty, 'total_amount', v_total_amount));

    COMMIT;

    SELECT v_receipt_id AS receipt_id, v_receipt_no AS receipt_no, v_total_qty AS total_qty, v_total_amount AS total_amount;
END//


-- ============================================================
-- 8. 销售流程
-- ============================================================

CREATE PROCEDURE sp_create_sales_order(
    IN p_customer_id BIGINT UNSIGNED,
    IN p_salesperson_id BIGINT UNSIGNED,
    IN p_warehouse_id BIGINT UNSIGNED,
    IN p_discount_amount DECIMAL(18,2),
    IN p_payment_method ENUM('cash','card','transfer','credit','wechat','alipay'),
    IN p_items_json JSON,
    IN p_remark VARCHAR(500)
)
BEGIN
    DECLARE v_order_no VARCHAR(30);
    DECLARE v_order_id BIGINT UNSIGNED;
    DECLARE v_total_amount DECIMAL(18,2) DEFAULT 0.00;
    DECLARE v_idx INT DEFAULT 0;
    DECLARE v_item_count INT;
    DECLARE v_product_id BIGINT UNSIGNED;
    DECLARE v_batch_id BIGINT UNSIGNED;
    DECLARE v_qty INT;
    DECLARE v_price DECIMAL(12,2);
    DECLARE v_discount DECIMAL(12,2);
    DECLARE v_line_amount DECIMAL(18,2);
    DECLARE v_available INT;
    DECLARE v_before_qty INT;
    DECLARE v_credit_limit DECIMAL(18,2);
    DECLARE v_balance DECIMAL(18,2);

    SET v_order_no = CONCAT('SO-', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-', LPAD(FLOOR(RAND() * 9999) + 1, 4, '0'));
    SET v_item_count = JSON_LENGTH(p_items_json);

    -- 检查信用额度
    SELECT credit_limit, balance INTO v_credit_limit, v_balance FROM customers WHERE id = p_customer_id;

    START TRANSACTION;

    INSERT INTO sales_orders (order_no, customer_id, salesperson_id, warehouse_id,
        order_date, discount_amount, payment_method, status, remark)
    VALUES (v_order_no, p_customer_id, p_salesperson_id, p_warehouse_id,
        CURDATE(), p_discount_amount, p_payment_method, 'confirmed', p_remark);

    SET v_order_id = LAST_INSERT_ID();

    WHILE v_idx < v_item_count DO
        SET v_product_id = JSON_UNQUOTE(JSON_EXTRACT(p_items_json, CONCAT('$[', v_idx, '].product_id')));
        SET v_batch_id = JSON_UNQUOTE(JSON_EXTRACT(p_items_json, CONCAT('$[', v_idx, '].batch_id')));
        SET v_qty = JSON_UNQUOTE(JSON_EXTRACT(p_items_json, CONCAT('$[', v_idx, '].quantity')));
        SET v_price = JSON_UNQUOTE(JSON_EXTRACT(p_items_json, CONCAT('$[', v_idx, '].unit_price')));
        SET v_discount = COALESCE(JSON_UNQUOTE(JSON_EXTRACT(p_items_json, CONCAT('$[', v_idx, '].discount'))), 0);

        -- 检查库存
        SELECT COALESCE(available_quantity, 0) INTO v_available
        FROM inventory
        WHERE product_id = v_product_id
          AND (batch_id = v_batch_id OR (v_batch_id IS NULL AND batch_id IS NULL))
          AND warehouse_id = p_warehouse_id;

        IF v_available < v_qty THEN
            SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = CONCAT('库存不足: 产品ID=', v_product_id, ', 可用=', v_available, ', 需求=', v_qty);
        END IF;

        SET v_line_amount = (v_qty * v_price) - v_discount;

        INSERT INTO sales_order_items (order_id, product_id, batch_id, quantity, unit_price, discount, amount)
        VALUES (v_order_id, v_product_id, v_batch_id, v_qty, v_price, v_discount, v_line_amount);

        -- 扣减库存
        SELECT quantity INTO v_before_qty
        FROM inventory
        WHERE product_id = v_product_id
          AND (batch_id = v_batch_id OR (v_batch_id IS NULL AND batch_id IS NULL))
          AND warehouse_id = p_warehouse_id;

        UPDATE inventory
        SET quantity = quantity - v_qty
        WHERE product_id = v_product_id
          AND (batch_id = v_batch_id OR (v_batch_id IS NULL AND batch_id IS NULL))
          AND warehouse_id = p_warehouse_id;

        -- 更新批号库存
        IF v_batch_id IS NOT NULL THEN
            UPDATE product_batches SET current_qty = current_qty - v_qty WHERE id = v_batch_id;
        END IF;

        -- 库存变动日志
        INSERT INTO inventory_transactions (product_id, batch_id, warehouse_id,
            transaction_type, quantity_change, before_qty, after_qty,
            reference_type, reference_id, operator_id)
        VALUES (v_product_id, v_batch_id, p_warehouse_id, 'sales_out',
            -v_qty, v_before_qty, v_before_qty - v_qty,
            'sales_order', v_order_id, p_salesperson_id);

        SET v_total_amount = v_total_amount + v_line_amount;
        SET v_idx = v_idx + 1;
    END WHILE;

    UPDATE sales_orders
    SET total_amount = v_total_amount - p_discount_amount
    WHERE id = v_order_id;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('create_sales_order', 'sales_order', v_order_id, p_salesperson_id,
            JSON_OBJECT('order_no', v_order_no, 'customer_id', p_customer_id, 'total_amount', v_total_amount - p_discount_amount));

    COMMIT;

    SELECT v_order_id AS order_id, v_order_no AS order_no, v_total_amount - p_discount_amount AS total_amount;
END//


-- ============================================================
-- 9. 退库流程
-- ============================================================

CREATE PROCEDURE sp_create_sales_return(
    IN p_order_id BIGINT UNSIGNED,
    IN p_warehouse_id BIGINT UNSIGNED,
    IN p_handler_id BIGINT UNSIGNED,
    IN p_return_reason VARCHAR(500),
    IN p_return_type ENUM('quality','damage','wrong_item','customer_reject','other'),
    IN p_restock_fee DECIMAL(12,2),
    IN p_items_json JSON
)
BEGIN
    DECLARE v_return_no VARCHAR(30);
    DECLARE v_return_id BIGINT UNSIGNED;
    DECLARE v_customer_id BIGINT UNSIGNED;
    DECLARE v_total_amount DECIMAL(18,2) DEFAULT 0.00;
    DECLARE v_idx INT DEFAULT 0;
    DECLARE v_item_count INT;
    DECLARE v_order_item_id BIGINT UNSIGNED;
    DECLARE v_product_id BIGINT UNSIGNED;
    DECLARE v_batch_id BIGINT UNSIGNED;
    DECLARE v_return_qty INT;
    DECLARE v_unit_price DECIMAL(12,2);
    DECLARE v_before_qty INT;

    SELECT customer_id INTO v_customer_id FROM sales_orders WHERE id = p_order_id;

    SET v_return_no = CONCAT('SR-', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-', LPAD(FLOOR(RAND() * 9999) + 1, 4, '0'));
    SET v_item_count = JSON_LENGTH(p_items_json);

    START TRANSACTION;

    INSERT INTO sales_returns (return_no, order_id, customer_id, warehouse_id, handler_id,
        return_date, return_reason, return_type, restock_fee, status)
    VALUES (v_return_no, p_order_id, v_customer_id, p_warehouse_id, p_handler_id,
        CURDATE(), p_return_reason, p_return_type, p_restock_fee, 'approved');

    SET v_return_id = LAST_INSERT_ID();

    WHILE v_idx < v_item_count DO
        SET v_order_item_id = JSON_UNQUOTE(JSON_EXTRACT(p_items_json, CONCAT('$[', v_idx, '].order_item_id')));
        SET v_product_id = JSON_UNQUOTE(JSON_EXTRACT(p_items_json, CONCAT('$[', v_idx, '].product_id')));
        SET v_batch_id = JSON_UNQUOTE(JSON_EXTRACT(p_items_json, CONCAT('$[', v_idx, '].batch_id')));
        SET v_return_qty = JSON_UNQUOTE(JSON_EXTRACT(p_items_json, CONCAT('$[', v_idx, '].return_qty')));
        SET v_unit_price = JSON_UNQUOTE(JSON_EXTRACT(p_items_json, CONCAT('$[', v_idx, '].unit_price')));

        INSERT INTO sales_return_items (return_id, order_item_id, product_id, batch_id,
            return_qty, unit_price, amount, status)
        VALUES (v_return_id, v_order_item_id, v_product_id, v_batch_id,
            v_return_qty, v_unit_price, v_return_qty * v_unit_price, 'received');

        -- 更新销售单明细退货数量
        UPDATE sales_order_items
        SET returned_qty = returned_qty + v_return_qty
        WHERE id = v_order_item_id;

        -- 恢复库存
        SELECT COALESCE(quantity, 0) INTO v_before_qty
        FROM inventory
        WHERE product_id = v_product_id
          AND (batch_id = v_batch_id OR (v_batch_id IS NULL AND batch_id IS NULL))
          AND warehouse_id = p_warehouse_id;

        INSERT INTO inventory (product_id, batch_id, warehouse_id, quantity)
        VALUES (v_product_id, v_batch_id, p_warehouse_id, v_return_qty)
        ON DUPLICATE KEY UPDATE quantity = quantity + v_return_qty;

        IF v_batch_id IS NOT NULL THEN
            UPDATE product_batches SET current_qty = current_qty + v_return_qty WHERE id = v_batch_id;
        END IF;

        INSERT INTO inventory_transactions (product_id, batch_id, warehouse_id,
            transaction_type, quantity_change, before_qty, after_qty,
            reference_type, reference_id, operator_id)
        VALUES (v_product_id, v_batch_id, p_warehouse_id, 'return_in',
            v_return_qty, v_before_qty, v_before_qty + v_return_qty,
            'sales_return', v_return_id, p_handler_id);

        SET v_total_amount = v_total_amount + (v_return_qty * v_unit_price);
        SET v_idx = v_idx + 1;
    END WHILE;

    UPDATE sales_returns
    SET total_amount = v_total_amount, refund_amount = v_total_amount - p_restock_fee
    WHERE id = v_return_id;

    -- 更新销售单状态
    UPDATE sales_orders SET status = 'returned' WHERE id = p_order_id;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('create_sales_return', 'sales_return', v_return_id, p_handler_id,
            JSON_OBJECT('return_no', v_return_no, 'order_id', p_order_id, 'total_amount', v_total_amount, 'reason', p_return_reason));

    COMMIT;

    SELECT v_return_id AS return_id, v_return_no AS return_no, v_total_amount AS total_amount,
           v_total_amount - p_restock_fee AS refund_amount;
END//


-- ============================================================
-- 10. 工资发放
-- ============================================================

CREATE PROCEDURE sp_process_salary(
    IN p_salary_month VARCHAR(7),
    IN p_processed_by BIGINT UNSIGNED
)
BEGIN
    DECLARE v_done INT DEFAULT FALSE;
    DECLARE v_emp_id BIGINT;
    DECLARE v_base_salary DECIMAL(12,2);
    DECLARE v_overtime_pay DECIMAL(12,2);
    DECLARE v_bonus DECIMAL(12,2);
    DECLARE v_deduction DECIMAL(12,2);
    DECLARE v_ss_personal DECIMAL(12,2);
    DECLARE v_hf_personal DECIMAL(12,2);
    DECLARE v_income_tax DECIMAL(12,2);
    DECLARE v_net_pay DECIMAL(12,2);
    DECLARE v_ss_company DECIMAL(12,2);
    DECLARE v_hf_company DECIMAL(12,2);
    DECLARE v_payment_no VARCHAR(30);
    DECLARE v_taxable DECIMAL(12,2);
    DECLARE v_overtime_hours DECIMAL(4,1);
    DECLARE v_late_count INT;
    DECLARE v_payment_date DATE;

    DECLARE cur CURSOR FOR
        SELECT id, salary FROM employees WHERE status IN ('active', 'probation');

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = TRUE;

    -- 检查是否已发放
    IF EXISTS (SELECT 1 FROM salary_payments WHERE salary_month = p_salary_month LIMIT 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = CONCAT(p_salary_month, ' 工资已发放，不能重复处理');
    END IF;

    SET v_payment_date = CURDATE();

    START TRANSACTION;

    OPEN cur;

    read_loop: LOOP
        FETCH cur INTO v_emp_id, v_base_salary;
        IF v_done THEN LEAVE read_loop; END IF;

        -- 计算加班费
        SELECT COALESCE(SUM(overtime_hours), 0) INTO v_overtime_hours
        FROM attendance
        WHERE employee_id = v_emp_id
          AND DATE_FORMAT(attendance_date, '%Y-%m') = p_salary_month;

        SET v_overtime_pay = ROUND(v_base_salary / 21.75 / 8 * v_overtime_hours * 1.5, 2);

        -- 迟到扣款
        SELECT COUNT(*) INTO v_late_count
        FROM attendance
        WHERE employee_id = v_emp_id
          AND DATE_FORMAT(attendance_date, '%Y-%m') = p_salary_month
          AND status = 'late';

        SET v_deduction = v_late_count * 50; -- 每次迟到扣50

        -- 社保和公积金（个人部分）
        SET v_ss_personal = ROUND(v_base_salary * 0.08, 2);  -- 养老8%
        SET v_hf_personal = ROUND(v_base_salary * 0.12, 2);  -- 公积金12%

        -- 个税计算（简化）
        SET v_taxable = v_base_salary + v_overtime_pay - v_ss_personal - v_hf_personal - 5000;
        IF v_taxable <= 0 THEN
            SET v_income_tax = 0;
        ELSEIF v_taxable <= 3000 THEN
            SET v_income_tax = ROUND(v_taxable * 0.03, 2);
        ELSEIF v_taxable <= 12000 THEN
            SET v_income_tax = ROUND(v_taxable * 0.10 - 210, 2);
        ELSEIF v_taxable <= 25000 THEN
            SET v_income_tax = ROUND(v_taxable * 0.20 - 1410, 2);
        ELSE
            SET v_income_tax = ROUND(v_taxable * 0.25 - 2660, 2);
        END IF;

        SET v_bonus = 0; -- 默认无奖金
        SET v_net_pay = v_base_salary + v_overtime_pay + v_bonus - v_deduction
                      - v_ss_personal - v_hf_personal - v_income_tax;

        -- 公司部分
        SET v_ss_company = ROUND(v_base_salary * 0.16, 2);  -- 养老16%
        SET v_hf_company = ROUND(v_base_salary * 0.12, 2);  -- 公积金12%

        SET v_payment_no = CONCAT('SAL-', REPLACE(p_salary_month, '-', ''), '-', LPAD(v_emp_id, 5, '0'));

        INSERT INTO salary_payments (payment_no, employee_id, payment_date, salary_month,
            base_salary, overtime_pay, bonus, deduction,
            social_security_personal, housing_fund_personal, income_tax,
            net_pay, social_security_company, housing_fund_company,
            status, paid_at)
        VALUES (v_payment_no, v_emp_id, v_payment_date, p_salary_month,
            v_base_salary, v_overtime_pay, v_bonus, v_deduction,
            v_ss_personal, v_hf_personal, v_income_tax,
            v_net_pay, v_ss_company, v_hf_company,
            'paid', NOW());

    END LOOP;

    CLOSE cur;

    INSERT INTO audit_log (action, target_type, employee_id, new_value)
    VALUES ('process_salary', 'salary', p_processed_by,
            JSON_OBJECT('salary_month', p_salary_month, 'payment_date', v_payment_date));

    COMMIT;

    SELECT CONCAT('工资发放完成: ', p_salary_month) AS result;
END//


-- ============================================================
-- 11. 出纳日记账
-- ============================================================

CREATE PROCEDURE sp_create_cashier_journal(
    IN p_account_id BIGINT UNSIGNED,
    IN p_cashier_id BIGINT UNSIGNED,
    IN p_journal_type ENUM('cash_in','cash_out','bank_in','bank_out','transfer'),
    IN p_amount DECIMAL(18,2),
    IN p_counterparty VARCHAR(200),
    IN p_reference_type VARCHAR(50),
    IN p_reference_id BIGINT,
    IN p_bank_account VARCHAR(50),
    IN p_check_no VARCHAR(50),
    IN p_remark VARCHAR(500)
)
BEGIN
    DECLARE v_journal_no VARCHAR(30);
    DECLARE v_journal_id BIGINT UNSIGNED;
    DECLARE v_old_balance DECIMAL(18,2);

    SET v_journal_no = CONCAT('CJ-', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-', LPAD(FLOOR(RAND() * 9999) + 1, 4, '0'));

    START TRANSACTION;

    INSERT INTO cashier_journals (journal_no, journal_date, account_id, cashier_id,
        journal_type, amount, counterparty, reference_type, reference_id, bank_account, check_no, remark)
    VALUES (v_journal_no, CURDATE(), p_account_id, p_cashier_id,
        p_journal_type, p_amount, p_counterparty, p_reference_type, p_reference_id, p_bank_account, p_check_no, p_remark);

    SET v_journal_id = LAST_INSERT_ID();

    -- 更新账户余额
    SELECT current_balance INTO v_old_balance FROM accounts WHERE id = p_account_id;

    IF p_journal_type IN ('cash_in', 'bank_in') THEN
        UPDATE accounts SET current_balance = current_balance + p_amount WHERE id = p_account_id;
    ELSEIF p_journal_type IN ('cash_out', 'bank_out') THEN
        UPDATE accounts SET current_balance = current_balance - p_amount WHERE id = p_account_id;
    END IF;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('create_journal', 'cashier_journal', v_journal_id, p_cashier_id,
            JSON_OBJECT('journal_no', v_journal_no, 'type', p_journal_type, 'amount', p_amount));

    COMMIT;

    SELECT v_journal_id AS journal_id, v_journal_no AS journal_no;
END//


-- ============================================================
-- 12. 对账
-- ============================================================

CREATE PROCEDURE sp_create_reconciliation(
    IN p_account_id BIGINT UNSIGNED,
    IN p_period_start DATE,
    IN p_period_end DATE,
    IN p_bank_balance DECIMAL(18,2),
    IN p_prepared_by BIGINT UNSIGNED
)
BEGIN
    DECLARE v_recon_no VARCHAR(30);
    DECLARE v_recon_id BIGINT UNSIGNED;
    DECLARE v_book_balance DECIMAL(18,2);
    DECLARE v_unrec_income DECIMAL(18,2) DEFAULT 0.00;
    DECLARE v_unrec_expense DECIMAL(18,2) DEFAULT 0.00;
    DECLARE v_difference DECIMAL(18,2);
    DECLARE v_adjusted DECIMAL(18,2);

    SET v_recon_no = CONCAT('REC-', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-', LPAD(FLOOR(RAND() * 9999) + 1, 4, '0'));

    -- 获取账面余额
    SELECT current_balance INTO v_book_balance FROM accounts WHERE id = p_account_id;

    -- 计算未达账项
    -- 未达收入: 日记账已记但银行未入账
    SELECT COALESCE(SUM(amount), 0) INTO v_unrec_income
    FROM cashier_journals
    WHERE account_id = p_account_id
      AND journal_date BETWEEN p_period_start AND p_period_end
      AND journal_type IN ('cash_in', 'bank_in')
      AND status = 'pending';

    -- 未达支出: 日记账已记但银行未支付
    SELECT COALESCE(SUM(amount), 0) INTO v_unrec_expense
    FROM cashier_journals
    WHERE account_id = p_account_id
      AND journal_date BETWEEN p_period_start AND p_period_end
      AND journal_type IN ('cash_out', 'bank_out')
      AND status = 'pending';

    SET v_difference = v_bank_balance - v_book_balance;
    SET v_adjusted = v_book_balance + v_unrec_income - v_unrec_expense;

    START TRANSACTION;

    INSERT INTO reconciliations (recon_no, account_id, recon_date, period_start, period_end,
        book_balance, bank_balance, difference, unreconciled_income, unreconciled_expense,
        adjusted_balance, prepared_by, status)
    VALUES (v_recon_no, p_account_id, CURDATE(), p_period_start, p_period_end,
        v_book_balance, p_bank_balance, v_difference, v_unrec_income, v_unrec_expense,
        v_adjusted, p_prepared_by, 'prepared');

    SET v_recon_id = LAST_INSERT_ID();

    -- 插入对账明细
    INSERT INTO reconciliation_items (reconciliation_id, journal_id, transaction_date,
        description, debit_amount, credit_amount, is_matched)
    SELECT
        v_recon_id, cj.id, cj.journal_date,
        CONCAT(cj.journal_type, ' - ', COALESCE(cj.counterparty, ''), ' ', COALESCE(cj.remark, '')),
        CASE WHEN cj.journal_type IN ('bank_in', 'cash_in') THEN cj.amount ELSE 0 END,
        CASE WHEN cj.journal_type IN ('bank_out', 'cash_out') THEN cj.amount ELSE 0 END,
        FALSE
    FROM cashier_journals cj
    WHERE cj.account_id = p_account_id
      AND cj.journal_date BETWEEN p_period_start AND p_period_end;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('create_reconciliation', 'reconciliation', v_recon_id, p_prepared_by,
            JSON_OBJECT('recon_no', v_recon_no, 'book_balance', v_book_balance, 'bank_balance', p_bank_balance, 'difference', v_difference));

    COMMIT;

    SELECT v_recon_id AS reconciliation_id, v_recon_no AS recon_no,
           v_book_balance AS book_balance, p_bank_balance AS bank_balance,
           v_difference AS difference, v_adjusted AS adjusted_balance;
END//


-- ============================================================
-- 13. 结算
-- ============================================================

CREATE PROCEDURE sp_create_settlement(
    IN p_settlement_type ENUM('supplier','customer','internal','salary','tax'),
    IN p_party_id BIGINT UNSIGNED,
    IN p_period_start DATE,
    IN p_period_end DATE,
    IN p_payment_due_date DATE,
    IN p_payment_method ENUM('bank_transfer','cash','check','credit','offset'),
    IN p_items_json JSON,
    IN p_prepared_by BIGINT UNSIGNED,
    IN p_remark VARCHAR(500)
)
BEGIN
    DECLARE v_settle_no VARCHAR(30);
    DECLARE v_settle_id BIGINT UNSIGNED;
    DECLARE v_total_amount DECIMAL(18,2) DEFAULT 0.00;
    DECLARE v_idx INT DEFAULT 0;
    DECLARE v_item_count INT;
    DECLARE v_ref_type VARCHAR(50);
    DECLARE v_ref_id BIGINT;
    DECLARE v_amount DECIMAL(18,2);

    SET v_settle_no = CONCAT('SET-', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-', LPAD(FLOOR(RAND() * 9999) + 1, 4, '0'));
    SET v_item_count = JSON_LENGTH(p_items_json);

    START TRANSACTION;

    INSERT INTO settlements (settlement_no, settlement_type, party_id, settlement_date,
        period_start, period_end, total_amount, payment_due_date, payment_method,
        prepared_by, status, remark)
    VALUES (v_settle_no, p_settlement_type, p_party_id, CURDATE(),
        p_period_start, p_period_end, 0, p_payment_due_date, p_payment_method,
        p_prepared_by, 'pending', p_remark);

    SET v_settle_id = LAST_INSERT_ID();

    WHILE v_idx < v_item_count DO
        SET v_ref_type = JSON_UNQUOTE(JSON_EXTRACT(p_items_json, CONCAT('$[', v_idx, '].reference_type')));
        SET v_ref_id = JSON_UNQUOTE(JSON_EXTRACT(p_items_json, CONCAT('$[', v_idx, '].reference_id')));
        SET v_amount = JSON_UNQUOTE(JSON_EXTRACT(p_items_json, CONCAT('$[', v_idx, '].amount')));

        INSERT INTO settlement_items (settlement_id, reference_type, reference_id, amount)
        VALUES (v_settle_id, v_ref_type, v_ref_id, v_amount);

        SET v_total_amount = v_total_amount + v_amount;
        SET v_idx = v_idx + 1;
    END WHILE;

    UPDATE settlements SET total_amount = v_total_amount WHERE id = v_settle_id;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('create_settlement', 'settlement', v_settle_id, p_prepared_by,
            JSON_OBJECT('settlement_no', v_settle_no, 'type', p_settlement_type, 'total_amount', v_total_amount));

    COMMIT;

    SELECT v_settle_id AS settlement_id, v_settle_no AS settlement_no, v_total_amount AS total_amount;
END//


-- ============================================================
-- 14. 财务凭证
-- ============================================================

CREATE PROCEDURE sp_create_voucher(
    IN p_voucher_date DATE,
    IN p_voucher_type ENUM('receipt','payment','transfer','journal'),
    IN p_reference_type VARCHAR(50),
    IN p_reference_id BIGINT,
    IN p_prepared_by BIGINT UNSIGNED,
    IN p_summary VARCHAR(500),
    IN p_items_json JSON
)
BEGIN
    DECLARE v_voucher_no VARCHAR(30);
    DECLARE v_voucher_id BIGINT UNSIGNED;
    DECLARE v_total_debit DECIMAL(18,2) DEFAULT 0.00;
    DECLARE v_total_credit DECIMAL(18,2) DEFAULT 0.00;
    DECLARE v_idx INT DEFAULT 0;
    DECLARE v_item_count INT;
    DECLARE v_account_id BIGINT UNSIGNED;
    DECLARE v_direction ENUM('debit','credit');
    DECLARE v_amount DECIMAL(18,2);
    DECLARE v_line_summary VARCHAR(500);

    SET v_voucher_no = CONCAT('V-', DATE_FORMAT(p_voucher_date, '%Y%m%d'), '-', LPAD(FLOOR(RAND() * 9999) + 1, 4, '0'));
    SET v_item_count = JSON_LENGTH(p_items_json);

    START TRANSACTION;

    INSERT INTO vouchers (voucher_no, voucher_date, voucher_type,
        reference_type, reference_id, prepared_by, summary)
    VALUES (v_voucher_no, p_voucher_date, p_voucher_type,
        p_reference_type, p_reference_id, p_prepared_by, p_summary);

    SET v_voucher_id = LAST_INSERT_ID();

    WHILE v_idx < v_item_count DO
        SET v_account_id = JSON_UNQUOTE(JSON_EXTRACT(p_items_json, CONCAT('$[', v_idx, '].account_id')));
        SET v_direction = JSON_UNQUOTE(JSON_EXTRACT(p_items_json, CONCAT('$[', v_idx, '].direction')));
        SET v_amount = JSON_UNQUOTE(JSON_EXTRACT(p_items_json, CONCAT('$[', v_idx, '].amount')));
        SET v_line_summary = JSON_UNQUOTE(JSON_EXTRACT(p_items_json, CONCAT('$[', v_idx, '].summary')));

        INSERT INTO voucher_items (voucher_id, account_id, line_no, direction, amount, summary)
        VALUES (v_voucher_id, v_account_id, v_idx + 1, v_direction, v_amount, v_line_summary);

        IF v_direction = 'debit' THEN
            SET v_total_debit = v_total_debit + v_amount;
        ELSE
            SET v_total_credit = v_total_credit + v_amount;
        END IF;

        SET v_idx = v_idx + 1;
    END WHILE;

    IF ABS(v_total_debit - v_total_credit) > 0.01 THEN
        ROLLBACK;
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = CONCAT('借贷不平衡: 借方=', v_total_debit, ', 贷方=', v_total_credit);
    END IF;

    UPDATE vouchers SET total_debit = v_total_debit, total_credit = v_total_credit WHERE id = v_voucher_id;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('create_voucher', 'voucher', v_voucher_id, p_prepared_by,
            JSON_OBJECT('voucher_no', v_voucher_no, 'total_debit', v_total_debit, 'total_credit', v_total_credit));

    COMMIT;

    SELECT v_voucher_id AS voucher_id, v_voucher_no AS voucher_no,
           v_total_debit AS total_debit, v_total_credit AS total_credit;
END//

-- 过账凭证
CREATE PROCEDURE sp_post_voucher(
    IN p_voucher_id BIGINT UNSIGNED,
    IN p_posted_by BIGINT UNSIGNED
)
BEGIN
    DECLARE v_status VARCHAR(20);
    DECLARE v_done INT DEFAULT FALSE;
    DECLARE v_account_id BIGINT;
    DECLARE v_direction ENUM('debit','credit');
    DECLARE v_amount DECIMAL(18,2);

    DECLARE cur CURSOR FOR
        SELECT account_id, direction, amount FROM voucher_items WHERE voucher_id = p_voucher_id;

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = TRUE;

    SELECT status INTO v_status FROM vouchers WHERE id = p_voucher_id;

    IF v_status != 'reviewed' AND v_status != 'draft' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '只能过账已审核或草稿状态的凭证';
    END IF;

    START TRANSACTION;

    OPEN cur;

    update_loop: LOOP
        FETCH cur INTO v_account_id, v_direction, v_amount;
        IF v_done THEN LEAVE update_loop; END IF;

        IF v_direction = 'debit' THEN
            UPDATE accounts SET current_balance = current_balance + v_amount WHERE id = v_account_id;
        ELSE
            UPDATE accounts SET current_balance = current_balance - v_amount WHERE id = v_account_id;
        END IF;
    END LOOP;

    CLOSE cur;

    UPDATE vouchers SET status = 'posted', posted_by = p_posted_by WHERE id = p_voucher_id;

    INSERT INTO audit_log (action, target_type, target_id, employee_id, new_value)
    VALUES ('post_voucher', 'voucher', p_voucher_id, p_posted_by,
            JSON_OBJECT('status', 'posted'));

    COMMIT;

    SELECT '凭证过账成功' AS result;
END//


-- ============================================================
-- 15. 批量生成测试数据辅助过程
-- ============================================================

-- 批量生成考勤数据
CREATE PROCEDURE sp_generate_attendance(
    IN p_year_month VARCHAR(7)
)
BEGIN
    DECLARE v_done INT DEFAULT FALSE;
    DECLARE v_emp_id BIGINT;
    DECLARE v_day INT DEFAULT 1;
    DECLARE v_days_in_month INT;
    DECLARE v_att_date DATE;
    DECLARE v_day_of_week INT;
    DECLARE v_clock_in TIME;
    DECLARE v_clock_out TIME;
    DECLARE v_status VARCHAR(20);
    DECLARE v_late_min INT;
    DECLARE v_rand_val DECIMAL(5,4);

    DECLARE cur CURSOR FOR
        SELECT id FROM employees WHERE status IN ('active', 'probation');

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = TRUE;

    SET v_days_in_month = DAY(LAST_DAY(CONCAT(p_year_month, '-01')));

    OPEN cur;

    emp_loop: LOOP
        FETCH cur INTO v_emp_id;
        IF v_done THEN LEAVE emp_loop; END IF;

        SET v_day = 1;
        WHILE v_day <= v_days_in_month DO
            SET v_att_date = STR_TO_DATE(CONCAT(p_year_month, '-', LPAD(v_day, 2, '0')), '%Y-%m-%d');
            SET v_day_of_week = DAYOFWEEK(v_att_date);

            -- 周末不上班
            IF v_day_of_week IN (1, 7) THEN
                SET v_day = v_day + 1;
                ITERATE;
            END IF;

            SET v_rand_val = RAND();

            IF v_rand_val < 0.05 THEN
                -- 5% 缺勤
                SET v_status = 'absent';
                SET v_clock_in = NULL;
                SET v_clock_out = NULL;
                SET v_late_min = 0;
            ELSEIF v_rand_val < 0.15 THEN
                -- 10% 迟到
                SET v_status = 'late';
                SET v_late_min = FLOOR(RAND() * 60) + 1;
                SET v_clock_in = ADDTIME(CONCAT('08:00:00'), SEC_TO_TIME(v_late_min * 60));
                SET v_clock_out = ADDTIME('17:00:00', SEC_TO_TIME(FLOOR(RAND() * 60) * 60));
            ELSEIF v_rand_val < 0.2 THEN
                -- 5% 早退
                SET v_status = 'early';
                SET v_late_min = 0;
                SET v_clock_in = '08:00:00';
                SET v_clock_out = SUBTIME('17:00:00', SEC_TO_TIME((FLOOR(RAND() * 60) + 1) * 60));
            ELSE
                -- 80% 正常
                SET v_status = 'normal';
                SET v_late_min = 0;
                SET v_clock_in = ADDTIME('08:00:00', SEC_TO_TIME(FLOOR(RAND() * 10) * 60));
                SET v_clock_out = ADDTIME('17:00:00', SEC_TO_TIME(FLOOR(RAND() * 60) * 60));
            END IF;

            INSERT INTO attendance (employee_id, attendance_date, clock_in, clock_out, status, late_minutes)
            VALUES (v_emp_id, v_att_date,
                IF(v_clock_in IS NULL, NULL, TIMESTAMP(v_att_date, v_clock_in)),
                IF(v_clock_out IS NULL, NULL, TIMESTAMP(v_att_date, v_clock_out)),
                v_status, v_late_min)
            ON DUPLICATE KEY UPDATE
                clock_in = VALUES(clock_in),
                clock_out = VALUES(clock_out),
                status = VALUES(status),
                late_minutes = VALUES(late_minutes);

            SET v_day = v_day + 1;
        END WHILE;
    END LOOP;

    CLOSE cur;

    SELECT CONCAT('考勤数据生成完成: ', p_year_month) AS result;
END//


DELIMITER ;